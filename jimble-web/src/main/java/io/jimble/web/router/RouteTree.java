package io.jimble.web.router;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * ルートツリー
 *
 * <p>
 * {@code jooby_base} の {@code lib.base.web.router.RouteTree} を移植したもの。
 * 移植にあたって既存実装の次の2点を修正している。
 * </p>
 *
 * <ol>
 *     <li>
 *         <b>探索中はフックを蓄積しない。</b>
 *         既存実装はノードに入った瞬間に before / after / error を蓄積しており、
 *         マッチに失敗した枝のフックが最終結果に混ざっていた
 *         （index と variables は巻き戻していたが、フックだけ巻き戻していなかった）。
 *     </li>
 *     <li>
 *         <b>変数ノードは登録順に試す。</b>
 *         既存実装は {@code HashMap} を走査していたため、同一階層に複数の変数ノードがあると
 *         試行順が実行環境依存だった。
 *     </li>
 * </ol>
 *
 * <p>
 * 優先順位は <b>固定セグメント &gt; パスパラメータ（登録順） &gt; ワイルドカード</b>。
 * ワイルドカードは深い位置のものが優先される（深い枝を先に試すため自然にそうなる）。
 * </p>
 */
final class RouteTree {

	/* セグメント（ルートノードは null） */
	private final String segment;

	/* このノードで終わるルート（メソッド → ルート） */
	private final Map<String, Route> routes = new LinkedHashMap<>();

	/* 固定セグメントの子 */
	private final Map<String, RouteTree> statics = new LinkedHashMap<>();

	/* 変数セグメントの子（登録順を保つ） */
	private final Map<String, RouteTree> variables = new LinkedHashMap<>();

	/* ワイルドカード（メソッド → ルート） */
	private final Map<String, Route> wildcards = new LinkedHashMap<>();

	/**
	 * コンストラクタ（ルートノード）
	 */
	RouteTree () {

		this.segment = null;

	}

	/**
	 * コンストラクタ
	 *
	 * @param segment	セグメント
	 */
	private RouteTree (String segment) {

		this.segment = segment;

	}


	// region 登録

	/**
	 * パスの子ノードを取得する（無ければ作る）
	 *
	 * @param segments	セグメント列
	 * @return	子ノード
	 */
	RouteTree node (PathSegments segments) {

		RouteTree current = this;

		for (int index = 0; index < segments.size(); index++) {
			current = current.child(segments.get(index));
		}

		return current;

	}

	/**
	 * ルートを登録する
	 *
	 * @param method	メソッド
	 * @param segments	セグメント列
	 * @param route		ルート
	 */
	void add (String method, PathSegments segments, Route route) {

		RouteTree current = this;

		for (int index = 0; index < segments.size(); index++) {

			String segment = segments.get(index);

			if (segment.equals("*")) {
				if (index != segments.size() - 1) {
					throw new IllegalArgumentException(
						"ワイルドカード \"*\" はパスの末尾にしか置けません: " + segments);
				}
				current.putWildcard(method, route);
				return;
			}

			current = current.child(segment);

		}

		current.putRoute(method, route);

	}

	/**
	 * 子ノードを取得する（無ければ作る）
	 *
	 * @param segment	セグメント
	 * @return	子ノード
	 */
	private RouteTree child (String segment) {

		if (segment.startsWith("{") && segment.endsWith("}")) {
			String name = segment.substring(1, segment.length() - 1);
			return variables.computeIfAbsent(name, key -> new RouteTree(segment));
		}

		return statics.computeIfAbsent(segment, RouteTree::new);

	}

	/**
	 * ルートを設定する
	 *
	 * @param method	メソッド
	 * @param route		ルート
	 */
	private void putRoute (String method, Route route) {

		Route existing = routes.putIfAbsent(method, route);
		if (existing != null) {
			throw new IllegalStateException(
				"ルートが重複しています: %s（既存: %s / 追加: %s）".formatted(method, existing, route));
		}

	}

	/**
	 * ワイルドカードを設定する
	 *
	 * @param method	メソッド
	 * @param route		ルート
	 */
	private void putWildcard (String method, Route route) {

		Route existing = wildcards.putIfAbsent(method, route);
		if (existing != null) {
			throw new IllegalStateException(
				"ワイルドカードルートが重複しています: %s（既存: %s / 追加: %s）".formatted(method, existing, route));
		}

	}

	// endregion


	// region マージ

	/**
	 * 別のツリーを取り込む
	 *
	 * <p>
	 * 子コントローラの取り込み（{@code install}）で使う。
	 * 既存実装は static フィールド経由で親ルーターを渡していたが、
	 * この方式なら共有可変状態が要らない。
	 * </p>
	 *
	 * @param source	取り込むツリー
	 */
	void merge (RouteTree source) {

		for (Map.Entry<String, Route> entry : source.routes.entrySet()) {
			putRoute(entry.getKey(), entry.getValue());
		}

		for (Map.Entry<String, Route> entry : source.wildcards.entrySet()) {
			putWildcard(entry.getKey(), entry.getValue());
		}

		for (Map.Entry<String, RouteTree> entry : source.statics.entrySet()) {
			statics.computeIfAbsent(entry.getKey(), RouteTree::new).merge(entry.getValue());
		}

		for (Map.Entry<String, RouteTree> entry : source.variables.entrySet()) {
			variables.computeIfAbsent(entry.getKey(), key -> new RouteTree("{" + key + "}"))
				.merge(entry.getValue());
		}

	}

	// endregion


	// region 探索

	/**
	 * 探索状態
	 */
	private static final class State {

		/* 見つかったルート */
		private Route route;

		/* パス変数 */
		private final PathVariables variables = PathVariables.empty();

		/*
		 * パスは合っていたが、メソッドが違ったもの（要件 F-R-25）。
		 *
		 * <b>要るときだけ作る。</b>どのルートにも当たらない
		 * ——つまりパスそのものが無い——のがいちばん多く、
		 * そこで毎回1つ作ると<b>外れたときだけ高い</b>形になる。
		 * それは<b>叩かれると効く</b>（RouterBench が見ている）。
		 */
		private Set<String> allowed;

		/**
		 * 当たりうるメソッドを覚える
		 *
		 * @param methods	メソッド
		 */
		private void allow (Set<String> methods) {

			if (methods.isEmpty()) {
				return;
			}

			if (allowed == null) {
				allowed = new LinkedHashSet<>();
			}

			allowed.addAll(methods);

		}

	}

	/**
	 * マッチさせる
	 *
	 * <p>
	 * <b>フックはここでは組み立てない。</b>ルートごとに起動時に確定してある
	 * （{@link Route#seal()}）ので、そのまま渡すだけである。
	 * </p>
	 *
	 * @param method		メソッド
	 * @param segments		セグメント列
	 * @param rootErrors	未マッチのときに使う error（一番外側のスコープのもの）
	 * @return	マッチ結果
	 */
	RouteMatch match (String method, PathSegments segments, List<ErrorHandler> rootErrors) {

		State state = new State();

		if (!find(state, method, segments, 0)) {

			/*
			 * 未マッチでもトップレベルの error は適用する（アプリ全体の404ページ用）。
			 * <b>パスは合っていてメソッドだけ違う</b>のなら、それも一緒に返す（要件 F-R-25）。
			 */
			return new RouteMatch(null, PathVariables.empty(), List.of(), List.of(), rootErrors
				, allowed(state, method));

		}

		Route route = state.route;

		return new RouteMatch(
			route, state.variables, route.beforeHooks(), route.afterHooks(), route.errorHooks());

	}

	/**
	 * パスは合っていたのに使えなかったメソッド（要件 F-R-25）
	 *
	 * <p>
	 * <b>探索のついでに集めてある。</b>失敗してから木を舐め直すと、
	 * <b>外れたときだけ高い</b>形になる——それは叩かれると効く（{@code RouterBench}）。
	 * </p>
	 *
	 * @param state		探索状態
	 * @param method	求められたメソッド
	 * @return	当たりうるメソッド。無ければ空
	 */
	private static Set<String> allowed (State state, String method) {

		if (state.allowed == null) {
			return Set.of();
		}

		state.allowed.remove(method);

		/*
		 * <b>WebSocket は HTTP のメソッドではない。</b>
		 * 同じ木に載せているだけなので、Allow に出すと嘘になる
		 * （{@code Allow: WS} を見たクライアントはそれを試す）。
		 */
		state.allowed.remove(io.jimble.web.ws.WsRoutes.METHOD);

		return state.allowed;

	}

	/**
	 * 試しに1本流してみる（要件 F-R-13）
	 *
	 * <p>
	 * <b>到達不能ルートの検出は、判定を書き起こさずに本物のマッチャに聞く。</b>
	 * 別に書き起こすと、片方を直したときにもう片方が黙って古くなる
	 * ——そして<b>ずれていることは、ずれた分だけ気づけない</b>。
	 * </p>
	 *
	 * @param method	メソッド
	 * @param segments	セグメント列
	 * @return	当たったルート。当たらなければ null
	 */
	Route probe (String method, PathSegments segments) {

		State state = new State();

		return find(state, method, segments, 0) ? state.route : null;

	}

	/**
	 * 固定セグメントの名前を集める
	 *
	 * <p>試しのパスに使う語が、どの固定セグメントとも重ならないようにするため。</p>
	 *
	 * @param result	結果
	 */
	void collectStaticNames (Set<String> result) {

		result.addAll(statics.keySet());

		for (RouteTree child : statics.values()) {
			child.collectStaticNames(result);
		}

		for (RouteTree child : variables.values()) {
			child.collectStaticNames(result);
		}

	}

	/**
	 * 再帰的に探索する
	 *
	 * <p>
	 * <b>成功したときだけ</b>パス変数を記録する。
	 * これにより失敗した枝の変数が混ざらない。
	 * </p>
	 *
	 * @param state		探索状態
	 * @param method	メソッド
	 * @param segments	セグメント列
	 * @param index		現在位置
	 * @return	マッチしたら true
	 */
	private boolean find (State state, String method, PathSegments segments, int index) {

		// 終端
		if (index == segments.size()) {

			Route route = routes.get(method);

			if (route == null) {
				// パスはここまで合っている。メソッドが違うだけなら 405（要件 F-R-25）
				state.allow(routes.keySet());
				return false;
			}

			state.route = route;
			return true;

		}

		String segment = segments.get(index);

		// 1. 固定セグメント
		RouteTree staticChild = statics.get(segment);
		if (staticChild != null && staticChild.find(state, method, segments, index + 1)) {
			return true;
		}

		// 2. パスパラメータ（登録順）
		for (Map.Entry<String, RouteTree> entry : variables.entrySet()) {
			if (entry.getValue().find(state, method, segments, index + 1)) {
				state.variables.put(entry.getKey(), segment);
				return true;
			}
		}

		// 3. ワイルドカード
		Route wildcard = wildcards.get(method);

		if (wildcard != null) {
			state.route = wildcard;
			state.variables.put(PathVariables.WILDCARD, segments.joinFrom(index));
			return true;
		}

		// ワイルドカードはあるが、そのメソッドでは受けていない（要件 F-R-25）
		state.allow(wildcards.keySet());

		return false;

	}

	// endregion


	// region 一覧

	/**
	 * 全ルートに処理を適用する
	 *
	 * <p>起動時のフック確定（{@link Route#seal()}）に使う。</p>
	 *
	 * @param action	処理
	 */
	void forEachRoute (Consumer<Route> action) {

		routes.values().forEach(action);
		wildcards.values().forEach(action);

		for (RouteTree child : statics.values()) {
			child.forEachRoute(action);
		}

		for (RouteTree child : variables.values()) {
			child.forEachRoute(action);
		}

	}

	/**
	 * ルート一覧を集める
	 *
	 * @param prefix	親までのパス
	 * @param result	結果
	 */
	void collect (String prefix, List<RouteInfo> result) {

		for (Map.Entry<String, Route> entry : routes.entrySet()) {
			result.add(new RouteInfo(entry.getKey(), prefix.isEmpty() ? "/" : prefix, entry.getValue()));
		}

		for (Map.Entry<String, Route> entry : wildcards.entrySet()) {
			result.add(new RouteInfo(entry.getKey(), prefix + "/*", entry.getValue()));
		}

		for (Map.Entry<String, RouteTree> entry : statics.entrySet()) {
			entry.getValue().collect(prefix + "/" + entry.getKey(), result);
		}

		for (Map.Entry<String, RouteTree> entry : variables.entrySet()) {
			entry.getValue().collect(prefix + "/{" + entry.getKey() + "}", result);
		}

	}

	// endregion


	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString () {

		return segment == null ? "/" : segment;

	}

}
