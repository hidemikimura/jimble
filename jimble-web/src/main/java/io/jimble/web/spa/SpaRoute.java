package io.jimble.web.spa;

import java.util.Objects;

/**
 * SPA のパスと書き換え処理
 *
 * <p>
 * {@code /items/{id}} のような書き方でパスパラメータを取る。
 * 書き方は本体のルート（{@link io.jimble.web.router.Router}）と同じで、
 * <b>マッチも本体と同じルートツリーがやる</b>（要件 D-75）。
 * </p>
 *
 * <h2>自前のマッチをやめた</h2>
 * <p>
 * もとはこのクラスがパスを正規表現に組み替えて自分でマッチしていた。
 * 移送元は<b>パスの文字をそのまま正規表現に埋めていた</b>ので
 * （{@code .} や {@code +} がメタ文字として効き、{@code /a.b/{id}} が
 * {@code /axb/1} に当たっていた）、{@code Pattern.quote} で直してあった。
 * </p>
 *
 * <p>
 * <b>そもそも2つ目のマッチ実装を持たないほうがよい。</b>
 * 直したのはこの1件だけだったが、優先順位・後戻り・パーセントエンコード・
 * 重複の扱いは本体と食い違ったままだった（{@link SpaRouter} 参照）。
 * いまはツリーに載せるだけで、このクラスは<b>パスと書き換え処理の組</b>である。
 * </p>
 *
 * @param path		パス（{@code /items/{id}} 形式）
 * @param rewriter	書き換え処理
 */
public record SpaRoute (String path, SpaRewriter rewriter) {

	/**
	 * コンストラクタ
	 */
	public SpaRoute {

		Objects.requireNonNull(path, "path");
		Objects.requireNonNull(rewriter, "rewriter");

	}

}
