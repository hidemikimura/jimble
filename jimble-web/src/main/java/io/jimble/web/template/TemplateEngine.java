package io.jimble.web.template;

import io.jimble.util.data.Data;

import java.io.Writer;

/**
 * テンプレートエンジン（要件 F-W-08 / F-W-11）
 *
 * <p>
 * jimble の標準は jte（{@link JteEngine}）である。
 * <b>この差し込み口があるのは、アプリが「エンドユーザーの編集するテンプレート」機能を
 * 提供する場合のため</b>（要件 F-W-11）。その用途では実行時にコンパイルするエンジン
 * （Pebble など）が要るが、jimble は同梱しない。
 * </p>
 *
 * <p>
 * 原則4（実際に2つ目の実装が要るまで抽象化しない）に対しては、
 * <b>要件が明示的に2つ目を求めている</b>ことで説明がつく。
 * </p>
 */
public interface TemplateEngine {

	/**
	 * 描画して書き出す
	 *
	 * <p>
	 * 大きなページをメモリに全部載せないため、こちらが本体（要件 F-W-07）。
	 * </p>
	 *
	 * @param name	テンプレート名（テンプレート置き場からの相対パス）
	 * @param model	モデル
	 * @param out	書き出し先
	 * @throws TemplateException	描画に失敗した場合
	 */
	void render (String name, Data model, Writer out);

	/**
	 * 描画して文字列で返す
	 *
	 * <p>
	 * 描画結果をキャッシュに載せる、メールの本文を作る、といった
	 * <b>レスポンス以外の用途</b>のために持つ。
	 * </p>
	 *
	 * @param name	テンプレート名
	 * @param model	モデル
	 * @return	描画結果
	 * @throws TemplateException	描画に失敗した場合
	 */
	default String render (String name, Data model) {

		java.io.StringWriter out = new java.io.StringWriter();
		render(name, model, out);

		return out.toString();

	}

	/**
	 * テンプレートがあるか
	 *
	 * @param name	テンプレート名
	 * @return	ある場合 = true
	 */
	boolean has (String name);

}
