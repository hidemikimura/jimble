package io.jimble.web.validation;

import io.jimble.util.data.Data;
import io.jimble.db.DB;

/**
 * 複数の {@link ValidationRules} をまとめて走らせる
 *
 * <h2>戻りは必ず {@code Data} である（要件 D-164）</h2>
 * <p>
 * <b>エラーが1件も無くても {@code null} は返さない。</b>空の {@code Data} である。
 * 判定は<b>必ず {@code isEmpty()} で書くこと</b>——
 * </p>
 *
 * <pre>
 * Data errors = Validator.validate(db, req, rules);
 *
 * if (!errors.isEmpty()) {	// null 判定ではない
 *     …
 * }
 * </pre>
 *
 * <p>
 * <h2>束をまたいだエラーは、全部残る（要件 D-164）</h2>
 * <p>
 * <b>以前は、あとから重ねた束が前の束のエラーを丸ごと消していた。</b>
 * エラーは<b>テーブル名の下にまとまる</b>ので
 * （{@code {item: {name: …}}}）、素直に上書きすると
 * <b>{@code item} ごと入れ替わる</b>——
 * 「共通の規則」と「この画面だけの規則」を分けて渡した人は、
 * <b>共通側のエラーが1件も出ないことに気づけない</b>。
 * </p>
 *
 * <p>
 * <b>以前は {@code null} を返す顔をしていて、実際には返さなかった。</b>
 * 中で呼んでいる {@link ValidationRules#validate(DB, Data)} が
 * <b>エラーが無いとき空の {@code Data} を返す</b>ため、
 * {@code null} 判定は<b>常に「エラーあり」に倒れていた</b>——
 * <b>全部のリクエストが弾かれる</b>のに、例外もログも出ない。
 * </p>
 */
public class Validator {

	/**
	 * コンストラクタ
	 *
	 * <p>持ち物は無い。</p>
	 */
	public Validator () {
	}

	/**
	 * 複数の {@link ValidationRules} をまとめて走らせる
	 *
	 * @param db	DB
	 * @param req	リクエスト
	 * @param rules	規則の束
	 * @return	エラー情報（無ければ空。<b>{@code null} は返らない</b>）
	 */
	public static Data validate (DB db, Data req, ValidationRules...rules) {

		Data result = new Data();

		for (ValidationRules rule : rules) {

			Data validationResult = rule.validate(db, req);

			if (validationResult != null) {
				merge(result, validationResult);
			}

		}

		return result;

	}

	/**
	 * エラー情報を重ねる
	 *
	 * <p>
	 * <b>入れ子は入れ子のまま重ねる。</b>
	 * {@code putAllData} だけだと<b>テーブル名の階層ごと入れ替わる</b>ので、
	 * 束をまたいだエラーが消える。
	 * </p>
	 *
	 * @param into	重ねる先
	 * @param from	重ねるもの
	 */
	private static void merge (Data into, Data from) {

		for (String key : from.keySet()) {

			Object value = from.get(key);

			if (value instanceof Data nested && into.get(key) instanceof Data already) {
				merge(already, nested);
			} else {
				into.put(key, value);
			}

		}

	}

}
