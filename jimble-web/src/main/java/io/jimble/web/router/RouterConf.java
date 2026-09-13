package io.jimble.web.router;

import io.jimble.util.conf.ConfFlag;

/**
 * ルーティングの設定（要件 F-R-24 / D-166）
 *
 * <pre>
 * router {
 *   ignore_case           = false   # パスの大文字小文字を区別しないか
 *   redirect_to_canonical = false   # 正規の URL へ 301 で寄せるか
 * }
 * </pre>
 *
 * <h2>スラッシュはもともと見ていない</h2>
 * <p>
 * <b>末尾のスラッシュと連続したスラッシュは、前から無視している</b>（要件 F-R-24）——
 * {@code /a/b} {@code /a/b/} {@code /a//b} {@code //a/b} は<b>すべて同じルート</b>である。
 * {@link PathSegments} がパスを割るときに<b>空のセグメントを落とす</b>ためで、
 * ここの設定で変えられるものではない。
 * </p>
 *
 * <p>
 * ここで足したのは<b>「どれか1つに寄せるか」</b>である。
 * 無視するだけだと<b>同じ内容が複数の URL で 200 を返す</b>——
 * キャッシュも検索エンジンも前段の ACL も、<b>別の URL として数える</b>。
 * </p>
 */
public final class RouterConf {

	/** 設定キー：大文字小文字を区別しないか */
	public static final String KEY_IGNORE_CASE = "router.ignore_case";

	/** 設定キー：正規の URL へ寄せるか */
	public static final String KEY_REDIRECT_TO_CANONICAL = "router.redirect_to_canonical";

	/** リクエストごとに読むもの（要件 D-167） */
	private static final ConfFlag IGNORE_CASE = ConfFlag.of(KEY_IGNORE_CASE, false);

	/** リクエストごとに読むもの（要件 D-167） */
	private static final ConfFlag REDIRECT_TO_CANONICAL = ConfFlag.of(KEY_REDIRECT_TO_CANONICAL, false);

	private RouterConf () {}

	/**
	 * パスの大文字小文字を区別しないか（要件 D-166）
	 *
	 * <p>
	 * <b>見ないのは、ルートに書いた固定の部分だけである。</b>
	 * {@code /Users/{id}} なら {@code Users} のところだけを同一視し、
	 * <b>{@code {id}} に入る値はそのまま渡す</b>——
	 * 大文字を含む ID（Hashids など）を<b>勝手に小文字にしない</b>ためである。
	 * </p>
	 *
	 * <h4>前段と食い違いうる</h4>
	 * <p>
	 * <b>これを入れると {@code /ADMIN} が {@code /admin} のルートに当たる。</b>
	 * プロキシの ACL や {@code before} フックが<b>パス文字列で判定している</b>と、
	 * <b>そちらだけすり抜ける</b>。
	 * {@link #redirectToCanonical()} を一緒に入れると、
	 * <b>登録した綴りへ 301 で寄せる</b>ので食い違いが消える。
	 * </p>
	 *
	 * @return	区別しない場合 = true
	 */
	public static boolean ignoreCase () {

		return IGNORE_CASE.get();

	}

	/**
	 * 正規の URL へ 301 で寄せるか（要件 D-166）
	 *
	 * <p>
	 * <b>寄せるのは {@code GET} と {@code HEAD} だけである。</b>
	 * {@code POST} を 301 で返すと、<b>ブラウザが本文を落として {@code GET} に化ける</b>——
	 * 送ったつもりの登録が消える。
	 * </p>
	 *
	 * <p>寄せる先は</p>
	 * <ul>
	 *   <li><b>スラッシュ</b>——末尾を落とし、連続を1つにする（{@code /a//b/} → {@code /a/b}）</li>
	 *   <li><b>大文字小文字</b>——{@link #ignoreCase()} が有効なときだけ。
	 *       <b>ルートに書いた綴り</b>へ寄せる（{@code /ADMIN} → {@code /admin}）。
	 *       <b>パスパラメータの値は1文字も変えない</b></li>
	 * </ul>
	 *
	 * @return	寄せる場合 = true
	 */
	public static boolean redirectToCanonical () {

		return REDIRECT_TO_CANONICAL.get();

	}

}
