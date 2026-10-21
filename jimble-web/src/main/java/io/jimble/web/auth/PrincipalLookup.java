package io.jimble.web.auth;

import io.jimble.web.context.WebContext;

/**
 * リクエスト・ログインの種別・利用者 ID から、いまの利用者を引く（D-305）
 *
 * <pre>
 * before(ApiToken.authenticate((context, realm, id) -&gt; {
 *     Tenant tenant = Tenants.byHost(context.request().host());
 *     Principal principal = App.findMember(id);
 *     // ほかのテナントの利用者なら null（「利用者がいません」の 401 になる。テナントが違うことは応答で分からない）
 *     return principal != null &amp;&amp; tenant != null &amp;&amp; tenant.id() == App.tenantOf(id) ? principal : null;
 * }));
 * </pre>
 *
 * <p>
 * <b>マルチテナントの SaaS で、トークンとテナント（Host）を照合する</b>ときに使う。
 * ID だけ・種別と ID だけを受け取る形では、<b>テナント A の利用者のトークンをテナント B のホストに送っても通った</b>
 * （照合を別の before に書くしかなく、書き忘れるとテナントをまたいで通る）。
 * </p>
 *
 * <h2>{@code context} で使ってよいもの・いけないもの</h2>
 * <p>
 * 呼ばれるのは、トークンを確かめた直後で、<b>このリクエストのログインやセッションを用意する前</b>である。
 * </p>
 * <ul>
 *   <li><b>使ってよい</b>：{@code context.request()}（{@code host()}・ヘッダー・パス）、{@code context.route()}（ルートの属性）</li>
 *   <li><b>使ってはいけない</b>：{@code context.session()}（呼ぶと {@link IllegalStateException}。トークンのリクエストにセッションの Cookie が出るのを防ぐ）、
 *       {@code context.response()}（応答を書かない。断るときは null を返す）、{@link Auth#principal}（まだログインしていない）</li>
 * </ul>
 *
 * <p>
 * <b>例外を投げると 500</b>（中身は返さない）。断るときは例外ではなく null を返す。
 * </p>
 */
@FunctionalInterface
public interface PrincipalLookup {

	/**
	 * いまの利用者を引く
	 *
	 * @param context	リクエスト（使ってよいものは {@link PrincipalLookup} を参照）
	 * @param realm		トークンを受けたルートの種別（{@link Auth#REALM}。付けていなければ空文字）
	 * @param userId	トークンの利用者 ID
	 * @return	いまの利用者。止めた・消した・ほかのテナントの人なら null
	 */
	Principal find (WebContext context, String realm, long userId);

}
