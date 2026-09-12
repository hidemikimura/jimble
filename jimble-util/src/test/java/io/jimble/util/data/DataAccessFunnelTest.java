package io.jimble.util.data;

import io.jimble.util.data.async.AsyncData;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Data} が {@code LinkedHashMap} の口を1本にまとめているか（要件 D-157）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>{@code AsyncData} は読み取りを1つずつ上書きしていた。</b>
 * だから上書きし忘れたメソッド——{@code remove} / {@code putIfAbsent} /
 * {@code computeIfAbsent}——は<b>未読み込みの空マップをそのまま触り、
 * 「その項目は無い」と答えていた。</b>
 * </p>
 *
 * <p>
 * <b>誰も落ちない。</b>例外もログも出ない。
 * 出るのは<b>「DB には有るのに、コードからは無いように見える」</b>という結果だけである。
 * </p>
 *
 * <p>
 * <b>穴は放っておくと増える。</b>Java 21 で {@code LinkedHashMap} が
 * {@code SequencedMap} になり、{@code putFirst} / {@code pollLastEntry} などが
 * <b>誰も上書きしないまま生えた</b>。
 * <b>JDK が足したときに落ちる</b>のがこのテストの仕事である。
 * </p>
 */
class DataAccessFunnelTest {

	/**
	 * 口を通さないもの
	 *
	 * <p>
	 * <b>デバッガやログが触るところである。</b>
	 * 覗いただけでクエリが飛ぶと、<b>ログを1行足しただけで枝という枝に SQL が飛ぶ</b>
	 * （要件 F-D-27）。{@code AsyncData} はこの3つを
	 * 「未読み込み」と答える形で自分で持っている。
	 * </p>
	 */
	private static final Set<String> NOT_FUNNELED = Set.of("equals", "hashCode", "toString");

	@Test
	@DisplayName("D-157 LinkedHashMap の公開メソッドは、1つ残らず Data を通る")
	void everyMapMethodGoesThroughData () {

		List<String> missing = new ArrayList<>();

		for (Method method : LinkedHashMap.class.getMethods()) {

			if (method.getDeclaringClass() == Object.class) {
				continue;
			}

			if (Modifier.isStatic(method.getModifiers())) {
				continue;
			}

			if (NOT_FUNNELED.contains(method.getName())) {
				continue;
			}

			if (!declares(Data.class, method)) {
				missing.add(signature(method));
			}

		}

		/*
		 * <b>ここが空でなくなるのは、たいてい JDK が足したときである。</b>
		 * 足されたメソッドを Data に足して、beforeAccess() を通すこと——
		 * <b>通さないと、AsyncData がそこだけ空のまま答える。</b>
		 */
		assertTrue(missing.isEmpty()
			, "Data を通っていないメソッドがあります（AsyncData がそこだけ空を返します）: " + missing);

	}

	@Test
	@DisplayName("D-157 書き換える口も読み込みを起こす")
	void writesTriggerTheLoad () {

		/*
		 * <b>読み取りだけでは足りない。</b>
		 * 未読み込みのまま remove すると、<b>これから読む値を消したつもりになれる</b>——
		 * 実際には空のマップから消しているので、読み込みが起きた瞬間に値が戻ってくる。
		 */
		CountingData data = new CountingData();

		assertEquals(0, data.loadCount.get(), "何もしていないのに読んでいます");

		Object removed = data.remove("name");

		assertEquals(1, data.loadCount.get(), "remove が読み込みを起こしていません");
		assertEquals("値", removed, "読み込む前の空マップから消しています");

	}

	@Test
	@DisplayName("D-157 Java 21 で生えた口も読み込みを起こす")
	void sequencedMethodsTriggerTheLoad () {

		CountingData data = new CountingData();

		assertNotNull(data.pollFirstEntry(), "pollFirstEntry が読み込みを起こしていません");
		assertEquals(1, data.loadCount.get());

		CountingData another = new CountingData();

		another.putFirst("先頭", 1);

		assertEquals(1, another.loadCount.get(), "putFirst が読み込みを起こしていません");

	}

	@Test
	@DisplayName("読み込みの最中に触っても回らない")
	void reentrantAccessDoesNotLoop () {

		/*
		 * <b>load() が返した中身は put で入る。</b>
		 * put も口を通るので、<b>読み込みの中から読み込みが呼ばれる</b>。
		 * AsyncState が「読み込みの最中」を持っているので、そこで止まる。
		 */
		CountingData data = new CountingData();

		assertEquals("値", data.get("name"));
		assertEquals(1, data.loadCount.get(), "読み込みが1回で済んでいません");

	}

	@Test
	@DisplayName("覗くだけの3つは読み込みを起こさない")
	void peekingDoesNotLoad () {

		CountingData data = new CountingData();

		data.toString();
		data.hashCode();
		data.equals(new CountingData());

		assertEquals(0, data.loadCount.get()
			, "ログやデバッガが覗いただけでクエリが飛びます（要件 F-D-27）");

	}

	@Test
	@DisplayName("ふつうの Data は口を素通りする")
	void plainDataIsUnaffected () {

		Data data = new Data();

		data.put("a", 1);
		data.putIfAbsent("b", 2);
		data.merge("a", 10, (x, y) -> ((Integer) x) + ((Integer) y));

		assertEquals(11, data.get("a"));
		assertEquals(2, data.get("b"));
		assertFalse(data.isEmpty());

	}

	// region ここで固定していないこと

	/*
	 * - <b>速さ</b>は見ていない。口が1本増えるので呼び出しが1段深くなるが、
	 *   {@code Data} 側は空メソッドなので JIT が畳む。測ってはいない
	 * - <b>{@code clone()} の中身</b>も見ていない。読み込みは起こすようにしたが、
	 *   {@code AsyncData} を clone すると<b>読み込みの状態を共有する</b>のは直していない
	 *   （元からそうで、使っているところが無い）
	 * - <b>{@code Data.toString()} に {@code beforeAccess()} を足しても、ここは落ちない</b>
	 *   （仕込んだ13個のうち、落とせなかった1個）。{@code AsyncData} が
	 *   {@code toString()} を自分で持っていて<b>{@code Data} 側まで来ない</b>ためで、
	 *   仮に来ても {@code summary()} が {@code entrySet()} を通るので<b>結局読み込みは起きる</b>——
	 *   <b>足しても意味が変わらない</b>ので、落とせないほうが正しい。
	 *   ここで守りたいのは {@code AsyncData} 側の3つで、そちらは落ちる
	 */

	// endregion

	/**
	 * 同じ名前・同じ引数のメソッドを持っているか
	 *
	 * <p>
	 * <b>消去後の引数で見る。</b>{@code Data} が {@code put(String, Object)} と書けば、
	 * javac が {@code put(Object, Object)} の橋渡しを作るので、
	 * <b>そちらが見つかる</b>。
	 * </p>
	 *
	 * @param type		見る型
	 * @param method	探すメソッド
	 * @return	持っている場合 = true
	 */
	private static boolean declares (Class<?> type, Method method) {

		for (Method declared : type.getDeclaredMethods()) {

			if (!declared.getName().equals(method.getName())) {
				continue;
			}

			if (Arrays.equals(declared.getParameterTypes(), method.getParameterTypes())) {
				return true;
			}

		}

		return false;

	}

	/**
	 * 読める形にする
	 *
	 * @param method	メソッド
	 * @return	文字列
	 */
	private static String signature (Method method) {

		List<String> params = new ArrayList<>();

		for (Class<?> type : method.getParameterTypes()) {
			params.add(type.getSimpleName());
		}

		return method.getName() + "(" + String.join(", ", params) + ")";

	}

	/**
	 * 読んだ回数を数える遅延読み込みデータ
	 */
	private static final class CountingData extends AsyncData {

		/** load() が呼ばれた回数 */
		final AtomicInteger loadCount = new AtomicInteger();

		@Override
		protected Data load () {

			loadCount.incrementAndGet();

			return new Data().putData("name", "値").putData("count", 3);

		}

		@Override
		protected void setData (Data data) {

			putAll(data);

		}

		@Override
		protected String hashKey () {

			return "CountingData:" + System.identityHashCode(this);

		}

	}

}
