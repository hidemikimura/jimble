package io.jimble.web.router;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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

	/*
	 * 小文字にした固定セグメントの索引（要件 D-166）。
	 *
	 * <b>null なら大文字小文字を区別する</b>（既定）。
	 * seal() のときに1度だけ作るので、<b>リクエストごとに走査しない</b>。
	 */
	private Map<String, RouteTree> staticsIgnoreCase = null;

	/*
	 * 固定セグメントの子を、文字列を作らずに引くための表（要件 D-168）。
	 *
	 * <b>開番地法（linear probing）で、鍵は seal() のときに並べる。</b>
	 * {@code HashMap} は引くのに<b>鍵の {@code String} が要る</b>——
	 * それを作るのが、1回のマッチのいちばん大きい費用だった。
	 *
	 * <b>null なら seal() 前である。</b>そのときは {@link #statics} を引く（遅いだけ）。
	 */
	private String[] staticKeys = null;

	/* {@link #staticKeys} と同じ位置の子 */
	private RouteTree[] staticNodes = null;

	/*
	 * パスパラメータの子を並べたもの（要件 D-168）。
	 *
	 * <b>{@code entrySet()} を回すと、階層ごとに反復子を1つ作る。</b>
	 * 登録順は配列の並びで保つ。
	 */
	private String[] variableNames = null;

	/* {@link #variableNames} と同じ位置の子 */
	private RouteTree[] variableNodes = null;

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
	 * 引くための表を作る（要件 D-168）
	 *
	 * <p>
	 * <b>木そのものは変えない。</b>登録に使う {@link #statics} と
	 * {@link #variables} はそのまま残し、<b>引くための並びを横に作る</b>——
	 * こうしておくと、<b>登録のときの振る舞いが1バイトも変わらない</b>。
	 * </p>
	 *
	 * <p>
	 * 固定セグメントは<b>開番地法の表</b>にする。詰まりすぎないように
	 * <b>数の2倍以上</b>の長さ（2の冪）を取るので、
	 * <b>空き（{@code null}）に当たったところで打ち切れる</b>。
	 * </p>
	 */
	void index () {

		int capacity = tableSize(statics.size());

		if (capacity > 0) {

			/*
			 * <b>必ず空きが残ることを、ここで確かめる（要件 D-168）。</b>
			 * 満杯の表を線形に探すと、<b>無いものを探したときに1周してしまう</b>——
			 * 打ち切りは書いてあるが、<b>打ち切りに頼る状態にしない</b>。
			 * 起動時に1度だけの検査である。
			 */
			if (capacity < statics.size() * 2) {
				throw new IllegalStateException(
					"固定セグメントの表が小さすぎます: %d 個に対して %d"
						.formatted(statics.size(), capacity));
			}

			staticKeys = new String[capacity];
			staticNodes = new RouteTree[capacity];

			int mask = capacity - 1;

			for (Map.Entry<String, RouteTree> entry : statics.entrySet()) {

				int at = spread(entry.getKey().hashCode()) & mask;

				while (staticKeys[at] != null) {
					at = (at + 1) & mask;
				}

				staticKeys[at] = entry.getKey();
				staticNodes[at] = entry.getValue();

			}

		} else {
			staticKeys = null;
			staticNodes = null;
		}

		if (variables.isEmpty()) {
			variableNames = null;
			variableNodes = null;
		} else {
			variableNames = variables.keySet().toArray(new String[0]);
			variableNodes = variables.values().toArray(new RouteTree[0]);
		}

		for (RouteTree child : statics.values()) {
			child.index();
		}

		for (RouteTree child : variables.values()) {
			child.index();
		}

	}

	/**
	 * 表の長さ（2の冪、数の2倍以上）
	 *
	 * @param size	入れるものの数
	 * @return	長さ。入れるものが無ければ 0
	 */
	private static int tableSize (int size) {

		if (size == 0) {
			return 0;
		}

		int capacity = 2;

		while (capacity < size * 2) {
			capacity <<= 1;
		}

		return capacity;

	}

	/**
	 * ハッシュを散らす
	 *
	 * <p>
	 * <b>{@code String.hashCode()} は上の桁に偏る。</b>
	 * {@code HashMap} と同じように、上位を下位へ混ぜてから使う。
	 * </p>
	 *
	 * @param hash	ハッシュ
	 * @return	散らしたもの
	 */
	private static int spread (int hash) {

		return hash ^ (hash >>> 16);

	}

	/**
	 * 大文字小文字を見ない索引を作る（要件 D-166）
	 *
	 * <p>
	 * <b>登録のしかたは変えない。</b>木はそのままで、
	 * <b>小文字で引ける索引を1本だけ横に置く</b>——
	 * こうしておくと、<b>設定を切ったときの振る舞いが1バイトも変わらない</b>。
	 * </p>
	 *
	 * <p>
	 * <b>綴りだけ違う兄弟があったら落とす。</b>
	 * {@code /Admin} と {@code /admin} の両方を書いてあると、
	 * <b>どちらに当たるかを読み手が決められない</b>——
	 * しかも<b>片方にだけ認証を書いていたら、緩いほうに当たりうる</b>。
	 * </p>
	 *
	 * @param path	ここまでのパス（メッセージ用）
	 */
	void indexIgnoreCase (String path) {

		Map<String, RouteTree> index = new LinkedHashMap<>();

		for (Map.Entry<String, RouteTree> entry : statics.entrySet()) {

			String lower = entry.getKey().toLowerCase(Locale.ROOT);

			RouteTree already = index.put(lower, entry.getValue());

			if (already != null) {
				throw new IllegalStateException(
					("router.ignore_case を有効にしていますが、"
						+ "%s の下に綴りだけ違うパスが2つあります: \"%s\" と \"%s\"。"
						+ "どちらに当たるか決められないので、どちらかに寄せてください")
						.formatted(path.isEmpty() ? "/" : path, already.segment, entry.getKey()));
			}

		}

		staticsIgnoreCase = index;

		for (Map.Entry<String, RouteTree> entry : statics.entrySet()) {
			entry.getValue().indexIgnoreCase(path + "/" + entry.getKey());
		}

		for (Map.Entry<String, RouteTree> entry : variables.entrySet()) {
			entry.getValue().indexIgnoreCase(path + "/{" + entry.getKey() + "}");
		}

	}

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

		/*
		 * パス変数（要件 D-168）。
		 *
		 * <b>束縛するものがあって初めて作る。</b>
		 * 固定セグメントだけのルートでも {@code LinkedHashMap} を1つ作っていた——
		 * <b>いちばん多いのがその形</b>である。
		 */
		private Map<String, String> variables;

		/*
		 * 1つだけのときの名前と値（要件 D-168）。
		 *
		 * <b>ほとんどのルートは変数が1つである</b>（{@code /posts/{id}}）。
		 * そのために {@code LinkedHashMap} を1つ作ると<b>112 byte</b> かかる——
		 * {@code Map.of(k, v)} なら 32 byte で足りる。
		 */
		private String singleName;

		/* 1つだけのときの値 */
		private String singleValue;

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
		 * パス変数を束縛する
		 *
		 * @param name	変数名
		 * @param value	値
		 */
		private void bind (String name, String value) {

			if (variables != null) {
				variables.put(name, value);
				return;
			}

			if (singleName == null) {
				singleName = name;
				singleValue = value;
				return;
			}

			// 2つ目が来たら、そこで表に移す
			variables = new LinkedHashMap<>();
			variables.put(singleName, singleValue);
			variables.put(name, value);

		}

		/**
		 * 束縛したパス変数
		 *
		 * @return	パス変数
		 */
		private PathVariables variables () {

			if (variables != null) {
				return new PathVariables(variables);
			}

			return singleName == null
				? PathVariables.EMPTY
				: new PathVariables(Map.of(singleName, singleValue));

		}

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
	 * @param path			パス
	 * @param rootErrors	未マッチのときに使う error（一番外側のスコープのもの）
	 * @return	マッチ結果
	 */
	RouteMatch match (String method, PathCursor path, List<ErrorHandler> rootErrors) {

		State state = new State();

		if (!find(state, method, path, 0)) {

			/*
			 * 未マッチでもトップレベルの error は適用する（アプリ全体の404ページ用）。
			 * <b>パスは合っていてメソッドだけ違う</b>のなら、それも一緒に返す（要件 F-R-25）。
			 */
			return new RouteMatch(null, PathVariables.EMPTY, List.of(), List.of(), rootErrors
				, allowed(state, method));

		}

		Route route = state.route;

		return new RouteMatch(
			route, state.variables(), route.beforeHooks(), route.afterHooks(), route.errorHooks());

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
	 * @param path		パス
	 * @return	当たったルート。当たらなければ null
	 */
	Route probe (String method, PathCursor path) {

		State state = new State();

		return find(state, method, path, 0) ? state.route : null;

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
	 * @param path		パス
	 * @param index		現在位置
	 * @return	マッチしたら true
	 */
	private boolean find (State state, String method, PathCursor path, int index) {

		// 終端
		if (index == path.size()) {

			Route route = routes.get(method);

			if (route == null) {
				// パスはここまで合っている。メソッドが違うだけなら 405（要件 F-R-25）
				state.allow(routes.keySet());
				return false;
			}

			state.route = route;
			return true;

		}

		/*
		 * 1. 固定セグメント（要件 D-168）
		 *
		 * <b>鍵の文字列を作らずに引く。</b>seal() で並べた表を、
		 * パスの一部と直に突き合わせる。
		 */
		RouteTree staticChild = staticChild(path, index);

		if (staticChild != null && staticChild.find(state, method, path, index + 1)) {
			return true;
		}

		/*
		 * 1'. 綴りだけ違う固定セグメント（要件 D-166）
		 *
		 * <b>そのままの綴りを先に試したあとで見る。</b>
		 * こちらは文字列を作る——<b>{@code router.ignore_case} を入れた人だけの費用</b>である。
		 */
		if (staticsIgnoreCase != null) {

			RouteTree lowerChild = staticsIgnoreCase.get(
				path.text(index).toLowerCase(Locale.ROOT));

			if (lowerChild != null && lowerChild != staticChild
				&& lowerChild.find(state, method, path, index + 1)) {
				return true;
			}

		}

		// 2. パスパラメータ（登録順）
		if (variableNames != null) {

			for (int at = 0; at < variableNames.length; at++) {

				if (variableNodes[at].find(state, method, path, index + 1)) {
					/*
					 * <b>ここで初めて文字列を作る。</b>
					 * 当たった枝の、束縛する1つぶんだけである。
					 */
					state.bind(variableNames[at], path.text(index));
					return true;
				}

			}

		} else {

			/*
			 * <b>seal() 前は元の道を通る（要件 D-168）。</b>
			 * 並べた配列が無いときに<b>ここを飛ばすと、変数の枝が丸ごと消える</b>——
			 * 到達不能ルートの検出（要件 F-R-13）を {@code seal()} を呼ばずに使うと、
			 * <b>変数のルートが全部「一生呼ばれない」と出る</b>。
			 */
			for (Map.Entry<String, RouteTree> entry : variables.entrySet()) {

				if (entry.getValue().find(state, method, path, index + 1)) {
					state.bind(entry.getKey(), path.text(index));
					return true;
				}

			}

		}

		// 3. ワイルドカード
		Route wildcard = wildcards.get(method);

		if (wildcard != null) {
			state.route = wildcard;
			state.bind(PathVariables.WILDCARD, path.joinFrom(index));
			return true;
		}

		// ワイルドカードはあるが、そのメソッドでは受けていない（要件 F-R-25）
		state.allow(wildcards.keySet());

		return false;

	}

	/**
	 * 固定セグメントの子を、文字列を作らずに引く（要件 D-168）
	 *
	 * @param path	パス
	 * @param index	位置
	 * @return	子。無ければ null
	 */
	private RouteTree staticChild (PathCursor path, int index) {

		/*
		 * <b>seal() 前は遅いほうを引く。</b>
		 * 起動時の到達不能ルートの検出（要件 F-R-13）がここを通る。
		 */
		if (staticKeys == null) {
			return statics.isEmpty() ? null : statics.get(path.text(index));
		}

		int mask = staticKeys.length - 1;
		int at = spread(path.hash(index)) & mask;

		/*
		 * <b>回る回数を表の長さで止める。</b>
		 * 表は入れたものの2倍以上あるので<b>必ず空きがあり、ここには届かない</b>——
		 * それでも書いてあるのは、<b>届いたときの壊れ方が「返ってこない」</b>だからである。
		 * 誤った答えなら気づけるが、<b>止まらないスレッドは気づけない</b>。
		 */
		for (int step = 0; step < staticKeys.length; step++) {

			String key = staticKeys[at];

			// 空きに当たったら、そこで無い
			if (key == null) {
				return null;
			}

			if (path.matches(index, key)) {
				return staticNodes[at];
			}

			at = (at + 1) & mask;

		}

		return null;

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
