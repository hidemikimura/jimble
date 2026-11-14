package io.jimble.db.internal.sql.template;

import io.jimble.db.sql.SqlBuildException;
import io.jimble.db.sql.TemplateParam;

import java.util.concurrent.Callable;

/**
 * {@link io.jimble.db.sql.SelectTemplate} を組み立てている最中か（2.8.0）
 *
 * <p>
 * 値の印（{@link TemplateParam}）は、template を組み立てている間だけ値の場所に置ける。
 * それ以外で出会ったら（印を置いたビルダーをそのまま DB に投げたら）断る——
 * 印のオブジェクトそのものを値として送ると、DB はそれを文字列として受け、<b>黙って違う行を返す</b>。
 * </p>
 *
 * <p>
 * ScopedValue なので、組み立てたスレッドの、組み立てている間だけ立つ。ほかのスレッドや、そのあとの呼び出しには漏れない。
 * </p>
 */
public final class TemplateScope {

	/* 組み立てている最中の印 */
	private static final ScopedValue<Boolean> BUILDING = ScopedValue.newInstance();

	/* 値の並びの印を SQL の字面に埋めるときの区切り（識別子にも ? にも出ない文字） */
	public static final char TOKEN = '\u0000';

	private TemplateScope () {}

	/**
	 * template を組み立てている最中として呼ぶ
	 *
	 * @param <T>		戻り値の型
	 * @param action	組み立て
	 * @return	戻り値
	 */
	public static <T> T building (Callable<T> action) {

		try {
			return ScopedValue.where(BUILDING, Boolean.TRUE).call(action::call);
		} catch (RuntimeException ex) {
			throw ex;
		} catch (Exception ex) {
			// 組み立ては検査例外を投げない。来たら包んで投げ直す
			throw new IllegalStateException("template を組み立てられませんでした: " + ex.getMessage(), ex);
		}

	}

	/**
	 * 値の場所に置かれた印を通してよいか確かめる
	 *
	 * @param param	印
	 * @throws SqlBuildException	template の外で使われたとき
	 */
	public static void requireBuilding (TemplateParam param) {

		if (!BUILDING.isBound()) {
			throw new SqlBuildException(
				("値の印（%s）を置いたビルダーを、そのまま組み立てようとしました。"
					+ "Dsl.param / Dsl.params は SQL.template(...) で template にしてから、bind(...) で値を入れて使ってください")
					.formatted(param));
		}

	}

	/**
	 * {@code IN (…)} の中に、値の並びの印を書く（template が bind のときに {@code ?, ?, …} に置き換える）
	 *
	 * @param param	印
	 * @return	字面に埋める印
	 * @throws SqlBuildException	template の外で使われたとき
	 */
	public static String listToken (TemplateParam param) {

		requireBuilding(param);

		return TOKEN + param.name() + TOKEN;

	}

}
