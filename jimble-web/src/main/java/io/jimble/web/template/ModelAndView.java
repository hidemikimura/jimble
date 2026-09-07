package io.jimble.web.template;

import io.jimble.util.data.Data;

/**
 * テンプレート描画の指定
 *
 * <p>
 * <b>描画そのものは M5（テンプレート）で実装する。</b>
 * ここでは「どのテンプレートにどのデータを渡すか」だけを表す。
 * </p>
 *
 * @param view	テンプレート名
 * @param model	テンプレートに渡すデータ
 */
public record ModelAndView (String view, Data model) {

	/**
	 * コンストラクタ
	 *
	 * @param view	テンプレート名
	 */
	public ModelAndView (String view) {

		this(view, new Data());

	}

}
