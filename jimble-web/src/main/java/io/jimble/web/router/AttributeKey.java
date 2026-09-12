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
 * <h2>キーはインスタンスそのものである（要件 D-157）</h2>
 * <p>
 * <b>1つ作ったら、それが1つのキーである。</b>同じ名前で別に作ったものは<b>別のキー</b>で、
 * 名前は<b>ログや診断に出すためだけ</b>に持っている。
 * </p>
 *
 * <p>
 * <b>record をやめたのはこのためである。</b>record の {@code equals} は
 * <b>名前と既定値の両方</b>を見るので、
 * </p>
 * <ul>
 *   <li><b>名前も既定値も同じなら、別に作っても同じキーになった。</b>
 *       アプリが偶然 {@code new AttributeKey<>("auth_public", false)} と書くと、
 *       <b>{@code Auth.PUBLIC} の枠を奪って、書いた覚えのないルートが公開になる</b></li>
 *   <li><b>名前が同じで既定値が違えば、別のキーになった。</b>
 *       片方に入れて、もう片方から読むと<b>既定値が返る</b>。
 *       ログに出る名前は同じなので、外から見分けが付かない</li>
 * </ul>
 *
 * <p>
 * どちらも<b>コンパイルは通り、例外も出ず、属性だけが効かない</b>形で外れる。
 * <b>外れ方が「開く側」に倒れる</b>ので、非推奨期間を置いても誰も気づけない。
 * </p>
 *
 * <p>
 * <b>同じ名前のキーが2つ登録されたら、{@link Router#seal()} が起動時に落とす。</b>
 * 振る舞いとしては無害になったが、<b>ログで見分けが付かないもの</b>は残さない。
 * </p>
 *
 * @param <T>	値の型
 */
public final class AttributeKey<T> {

	/* キー名（ログ用） */
	private final String name;

	/* 未設定時の値 */
	private final T defaultValue;

	/**
	 * コンストラクタ
	 *
	 * <p>
	 * <b>作るたびに別のキーになる。</b>アプリは
	 * {@code public static final} で1つだけ持つこと。
	 * </p>
	 *
	 * @param name			キー名（ログ用）
	 * @param defaultValue	未設定時の値
	 */
	public AttributeKey (String name, T defaultValue) {

		this.name = Objects.requireNonNull(name, "name");
		this.defaultValue = defaultValue;

	}

	/**
	 * キー名（ログ用）
	 *
	 * <p><b>同一性には使わない。</b>表示のためだけに持っている。</p>
	 *
	 * @return	キー名
	 */
	public String name () {

		return name;

	}

	/**
	 * 未設定時の値
	 *
	 * @return	既定値
	 */
	public T defaultValue () {

		return defaultValue;

	}

	/*
	 * <b>equals / hashCode は書かない。</b>
	 * Object のまま（インスタンスの同一性）が、ここで欲しい振る舞いである。
	 * 書き足すと上の事故が戻ってくるので、AttributeKeyIdentityTest が見張っている。
	 */

	@Override
	public String toString () {

		return "AttributeKey[" + name + "]";

	}

}
