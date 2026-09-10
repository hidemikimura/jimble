package approval.pages;

import io.jimble.util.data.Data;
import io.jimble.web.assets.AssetHandler;
import io.jimble.web.mpa.MpaHandler;
import io.jimble.web.proxy.ReverseProxy;
import io.jimble.web.server.JimbleApp;
import io.jimble.web.server.JimbleServer;
import io.jimble.web.spa.SpaHandler;

import java.util.List;

/**
 * サンプル：画面を返す（残件 N-3）
 *
 * <pre>
 * ./gradlew :examples:approval-pages:run
 * </pre>
 *
 * <p><b>DB を使わない。</b>これだけで起動する。</p>
 *
 * <h2>どのルートが何を通しているか</h2>
 * <table>
 *   <caption>ルートと機能</caption>
 *   <tr><td>{@code GET /}<br>{@code GET /requests}</td>
 *       <td><b>jte のレイアウト継承と部品化</b>（F-W-08 / F-W-10）。
 *       2枚とも同じ枠を使う</td></tr>
 *   <tr><td>{@code /assets/**}</td>
 *       <td><b>静的配信</b>（F-W-18）。Etag / 条件付き GET / jar の中からも出る</td></tr>
 *   <tr><td>{@code /app/**}</td>
 *       <td><b>SPA</b>（F-W-17）。どのパスでも {@code index.html}。title の差し替えつき</td></tr>
 *   <tr><td>{@code /guide/**}</td>
 *       <td><b>MPA</b>（F-W-20）。拡張子が無ければ {@code <パス>/index.html}</td></tr>
 *   <tr><td>{@code /api/**}</td>
 *       <td><b>リバースプロキシ</b>（F-R-14）。別ポートの {@link UpstreamApp} へ流す</td></tr>
 * </table>
 *
 * <h2>ここで見せたいこと</h2>
 *
 * <h3>1. 枠は1か所にしか無い</h3>
 * <p>
 * {@code examples/blog} のテンプレートは2枚とも独立した完結 HTML で、
 * <b>レイアウトを共有していなかった</b>。ヘッダを直すには全部を直す必要がある。
 * ここでは {@code layout/page.jte} の1枚が {@code <html>} から {@code </html>} までを持ち、
 * ページ側は<b>中身だけ</b>を書く。
 * </p>
 *
 * <h3>2. 配り方は3つあって、探し方が違う</h3>
 * <table>
 *   <caption>3つの配り方</caption>
 *   <tr><th></th><th>見つからないとき</th></tr>
 *   <tr><td>Asset</td><td><b>404</b></td></tr>
 *   <tr><td>SPA</td><td><b>上へ遡って</b>いちばん近い {@code index.html}</td></tr>
 *   <tr><td>MPA</td><td><b>そのパス直下</b>の {@code index.html} だけ。無ければ 404</td></tr>
 * </table>
 * <p>
 * <b>どれもルーティング本体とは別のハンドラ</b>である（要件 F-W-20）。
 * ルート定義に静的配信の分岐が混ざらない。
 * </p>
 *
 * <h3>3. キャッシュの既定が逆向きである</h3>
 * <p>
 * 動的な応答は既定で {@code Cache-Control: no-store}（要件 F-X-07）。
 * <b>静的配信だけがそれを上書きする。</b>
 * 逆にしていると、ログイン後の画面が中間のキャッシュに残る。
 * </p>
 */
public class PagesApp extends JimbleApp {

	/**
	 * ルート定義
	 *
	 * @param upstreamBaseUrl	転送先のベース URL
	 */
	public PagesApp (String upstreamBaseUrl) {

		error((context, cause, statusCode) -> {

			if (context.request().acceptJson()) {
				context.response().code(statusCode).json("error", cause.getMessage());
				return;
			}

			/*
			 * エラー画面も同じレイアウトを使う。
			 * 共有していないと、404 だけ見た目の違うサイトになる。
			 */
			/*
			 * putData() は Data を返す（Response ではない）ので、
			 * view() まで1本で繋げない。分けて書く
			 */
			context.response().code(statusCode);
			context.response().putData("title", "%d".formatted(statusCode));
			context.response().putData("nav", "");
			context.response().putData("message", cause.getMessage());
			context.response().view("pages/error.jte");

		});

		// region 画面（jte）

		get("/", context -> {

			context.response().putData("title", "ホーム");
			context.response().putData("nav", "home");
			context.response().view("pages/home.jte");

		});

		/*
		 * 同じレイアウトを使う2枚目。
		 *
		 * <b>DB を使わないサンプルなので、中身は決め打ちである。</b>
		 * ここで見たいのは中身ではなく、枠が共有されていることのほう。
		 */
		get("/requests", context -> {

			context.response().putData("title", "申請一覧");
			context.response().putData("nav", "requests");
			context.response().putData("requests", sampleRequests());
			context.response().view("pages/requests.jte");

		});

		// endregion

		// region 静的（要件 F-W-18 / F-W-20）

		/*
		 * クラスパスの assets/ を /assets/* で配る。
		 *
		 * <b>ファイルシステムのパスではない。</b>クラスパスから読むので、
		 * jar にしても展開しても同じように動く。
		 *
		 * <b>/assets 自身（末尾なし）には応えない。</b>
		 * 登録されるのは /assets/* の GET と HEAD の2本だけである
		 * （SPA / MPA は prefix 自身も登録するので、そこだけ形が違う）。
		 */
		install(() -> AssetHandler.mount("/assets", "assets"));

		/*
		 * SPA（要件 F-W-17）。
		 *
		 * 第3引数を渡すと、返す index.html を書き換えられる。
		 * <b>クローラは JavaScript を待たない</b>ので、
		 * title と OGP はサーバー側で入れないと拾われない。
		 *
		 * 渡すパスは<b>マウント先を含んだフルパス</b>（"/app/items/{id}"）である。
		 * マッチには本体と同じルートツリーを使う（別インスタンス）。
		 */
		install(() -> SpaHandler.mount("/app", "app", spa -> spa
			.route("/app/items/{id}", (context, html) -> html.replace(
				"<!--title-->"
				, "<title>申請 %s</title>".formatted(context.request().bodyPath().getString("id"))))
		));

		// MPA（要件 F-W-20）。静的サイトの出力をそのまま配る
		install(() -> MpaHandler.mount("/guide", "guide"));

		// endregion

		// region 転送（要件 F-R-14）

		/*
		 * /api/** を別のサービスへ流す。
		 *
		 * <b>WebSocket は通らない。</b>Upgrade は hop-by-hop なので落ちる。
		 * <b>リダイレクトも追わない。</b>302 はそのままクライアントへ返る。
		 */
		install(() -> ReverseProxy.mount("/api", upstreamBaseUrl));

		// endregion

	}

	/**
	 * 画面に出す申請（決め打ち）
	 *
	 * @return	申請
	 */
	private static List<Data> sampleRequests () {

		return List.of(
			new Data().putData("kind", "書籍").putData("amount", 3200L).putData("status", "承認済"),
			new Data().putData("kind", "備品").putData("amount", 48000L).putData("status", "申請中"),
			new Data().putData("kind", "研修").putData("amount", 120000L).putData("status", "差し戻し"));

	}

	/**
	 * 入口
	 *
	 * @param args	引数
	 */
	public static void main (String[] args) {

		/*
		 * 転送先を先に立てる。
		 *
		 * <b>本番では別のプロセスにある。</b>
		 * ここで一緒に立てているのは、サンプルを1コマンドで動かすためだけである。
		 */
		JimbleServer upstream = UpstreamApp.start();

		String upstreamBaseUrl = "http://127.0.0.1:" + upstream.port();

		JimbleServer.start(new PagesApp(upstreamBaseUrl));

	}

}
