package io.jimble.web.router;

import java.util.Objects;

/**
 * ルート属性のキー
 *
 * <p>
 * <b>既定値を必ず持たせる。</b>認証スキップのように既定値を間違えると事故になる用途で使うため、
 * 「未設定のときどうなるか」をキーの定義時に必ず決めさせる。
 * </p>
 *
 * <pre>
 * public static final AttributeKey&lt;Boolean&gt; NO_AUTH = new AttributeKey&lt;&gt;("no_auth", false);
 *
 * post("/session", this::session).attribute(NO_AUTH, true);
 * </pre>
 *
 * @param name			キー名（ログ用）
 * @param defaultValue	未設定時の値
 * @param <T>			値の型
 */
public record AttributeKey<T> (String name, T defaultValue) {

	/**
	 * コンストラクタ
	 */
	public AttributeKey {

		Objects.requireNonNull(name, "name");

	}

}
