package io.jimble.db.dialect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Dialect} が閉じたままか（要件 D-157）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>{@code sealed} を外すのは、1行消すだけでできる。</b>
 * そして<b>外したことは、何も壊さない</b>——コンパイルは通り、テストも通る。
 * 効いてくるのは<b>1.0 を出したあと</b>で、そのときにはもう閉じられない。
 * </p>
 *
 * <p>
 * <b>閉じている理由は2つある。</b>
 * </p>
 * <ol>
 *   <li><b>{@link Dialects#of(String)} はべた書きの表で製品を選ぶ。</b>
 *       自前の方言を設定から選ぶ道はもともと無い——
 *       <b>開いている顔をして閉じていた</b></li>
 *   <li><b>抽象メソッドが 40 以上ある。</b>
 *       SQL 関数を1つ足すたびに、外の実装は壊れる</li>
 * </ol>
 *
 * <p>
 * <b>あとから開くのは互換を壊さない。あとから閉じるのは壊す。</b>
 * だから先に閉じてある——このテストは<b>その判断を消さないため</b>にある。
 * </p>
 */
class DialectSealedTest {

	@Test
	@DisplayName("D-157 Dialect は sealed で、実装は枠組みの2つだけ")
	void dialectIsSealed () {

		assertTrue(Dialect.class.isSealed()
			, "Dialect が開いています（1.0 後はもう閉じられません）");

		List<String> permitted = new ArrayList<>();

		for (Class<?> type : Dialect.class.getPermittedSubclasses()) {
			permitted.add(type.getSimpleName());
		}

		assertEquals(List.of("MySqlDialect", "PostgreSqlDialect"), permitted
			, "実装が増えています: " + permitted);

	}

	@Test
	@DisplayName("D-157 実装は継承もできない")
	void implementationsAreFinal () {

		/*
		 * <b>sealed だけでは足りない。</b>許した実装が非 final なら、
		 * そこを継承して<b>枠の外から入れる</b>。
		 */
		assertTrue(java.lang.reflect.Modifier.isFinal(MySqlDialect.class.getModifiers())
			, "MySqlDialect を継承できます");

		assertTrue(java.lang.reflect.Modifier.isFinal(PostgreSqlDialect.class.getModifiers())
			, "PostgreSqlDialect を継承できます");

	}

	@Test
	@DisplayName("知らない製品名は落ちる（黙って MySQL に倒さない）")
	void unknownProductFails () {

		/*
		 * <b>閉じていることの実際の意味がここである。</b>
		 * 「自前の方言を登録する」道が無いので、
		 * 知らない名前は<b>その場で落とす</b>しかない——
		 * 黙って MySQL に倒すと、PostgreSQL のつもりで書いたアプリに
		 * バッククォートの SQL が飛ぶ（要件 F-X-05）。
		 */
		assertThrows(DialectException.class, () -> Dialects.of("oracle"));

		assertEquals(MySqlDialect.INSTANCE, Dialects.of("mariadb"), "別名が畳めていません");
		assertEquals(PostgreSqlDialect.INSTANCE, Dialects.of("postgres"));
		assertEquals(MySqlDialect.INSTANCE, Dialects.of(""), "空は既定（mysql）");

	}

	// region ここで固定していないこと

	/*
	 * - <b>{@code Dialects.defaultDialect(Dialect)} が公開のまま</b>なのは直していない。
	 *   閉じたので<b>渡せるのは枠組みの2つだけ</b>になり、実害が無くなった。
	 *   非公開パッケージへ移すのは NF-C-04（1.0 のとき）の話である
	 * - <b>方言ごとの SQL の中身</b>は見ていない。そこは
	 *   {@code MySqlDialectTest} / {@code PostgreSqlDialectTest} が見ている
	 */

	// endregion

}
