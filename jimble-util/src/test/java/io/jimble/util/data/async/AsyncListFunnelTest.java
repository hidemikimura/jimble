package io.jimble.util.data.async;

import io.jimble.util.data.Data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AsyncList} が {@code ArrayList} の口を1つ残らず塞いでいるか（要件 D-157）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>棚卸し（{@code docs/design-1.0.md} 3.2）は {@code AsyncData} だけを挙げていたが、
 * 同じ穴が {@code AsyncList} にも開いていた。</b>
 * 読む側は上書きしてあったのに、
 * {@code add} / {@code set} / {@code clear} / {@code addAll} と、
 * Java 21 で生えた {@code addFirst} / {@code removeLast} は
 * <b>未読み込みの空リストをそのまま触っていた</b>。
 * </p>
 *
 * <p>
 * <b>「まだ読んでいないリストに1件足す」は、黙って消える。</b>
 * 足したあとに誰かが読むと読み込みが起きて、
 * <b>足した1件は上書きされて無くなる</b>。落ちも警告も出ない。
 * </p>
 */
class AsyncListFunnelTest {

	/**
	 * 口を通さないもの（{@code DataAccessFunnelTest} と同じ理由）
	 */
	private static final Set<String> NOT_FUNNELED = Set.of("equals", "hashCode", "toString");

	@Test
	@DisplayName("D-157 ArrayList の公開メソッドは、1つ残らず AsyncList を通る")
	void everyListMethodIsCovered () {

		List<String> missing = new ArrayList<>();

		for (Method method : ArrayList.class.getMethods()) {

			if (method.getDeclaringClass() == Object.class) {
				continue;
			}

			if (Modifier.isStatic(method.getModifiers())) {
				continue;
			}

			if (NOT_FUNNELED.contains(method.getName())) {
				continue;
			}

			if (!declares(AsyncList.class, method)) {
				missing.add(signature(method));
			}

		}

		assertTrue(missing.isEmpty()
			, "AsyncList が塞いでいないメソッドがあります（未読み込みの空リストを触ります）: " + missing);

	}

	@Test
	@DisplayName("D-157 書き換える口も読み込みを起こす")
	void writesTriggerTheLoad () {

		CountingList list = new CountingList();

		assertEquals(0, list.loadCount.get(), "何もしていないのに読んでいます");

		list.add(new Data().putData("name", "足した"));

		assertEquals(1, list.loadCount.get(), "add が読み込みを起こしていません");

		/*
		 * <b>読み込んだ2件のあとに足されている。</b>
		 * 塞ぐ前は、空のリストに足したあとで読み込みが起きて<b>足したものが消えた</b>。
		 */
		assertEquals(3, list.size(), "足したものが消えています");

	}

	@Test
	@DisplayName("D-157 Java 21 で生えた口も読み込みを起こす")
	void sequencedMethodsTriggerTheLoad () {

		CountingList list = new CountingList();

		list.addFirst(new Data().putData("name", "先頭"));

		assertEquals(1, list.loadCount.get(), "addFirst が読み込みを起こしていません");
		assertEquals("先頭", ((Data) list.getFirst()).getString("name"));

	}

	@Test
	@DisplayName("覗くだけの3つは読み込みを起こさない")
	void peekingDoesNotLoad () {

		CountingList list = new CountingList();

		list.toString();
		list.hashCode();
		list.equals(new CountingList());

		assertEquals(0, list.loadCount.get()
			, "ログやデバッガが覗いただけでクエリが飛びます（要件 F-D-27）");

	}

	@Test
	@DisplayName("loadedValues は読み込みを起こさない")
	void loadedValuesDoesNotLoad () {

		CountingList list = new CountingList();

		assertTrue(list.loadedValues().isEmpty());
		assertEquals(0, list.loadCount.get(), "「読み込みを起こさない」口が読み込んでいます");

	}

	// region ここで固定していないこと

	/*
	 * - <b>先読み（{@code AsyncPrefetch}）との組み合わせ</b>は見ていない。
	 *   {@code putData} は読み込みの状態を通るので同じ仕掛けだが、
	 *   そちらは {@code AsyncPrefetchTest} が見ている
	 */

	// endregion

	/**
	 * 同じ名前・同じ引数のメソッドを持っているか
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
	 * 読んだ回数を数える遅延読み込みリスト
	 */
	private static final class CountingList extends AsyncList {

		/** load() が呼ばれた回数 */
		final AtomicInteger loadCount = new AtomicInteger();

		@Override
		protected List<Data> load () {

			loadCount.incrementAndGet();

			return List.of(
				new Data().putData("name", "1件目")
				, new Data().putData("name", "2件目"));

		}

		@Override
		protected void setData (Data data) {

			add(data);

		}

		@Override
		protected String hashKey () {

			return "CountingList:" + System.identityHashCode(this);

		}

	}

}
