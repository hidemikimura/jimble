package io.jimble.web.router;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 一生呼ばれないルートを見つける（要件 F-R-13 / D-10）
 *
 * <p>
 * 登録はできたのに<b>どんなリクエストでも他のルートが先に当たってしまう</b>ものがある。
 * 重複（同じメソッド・同じパス）は登録のその場で落ちるが、こちらは落ちない——
 * <b>書いた本人は「登録した」と思っていて、動かない理由が分からない</b>。
 * </p>
 *
 * <h2>判定を書き起こさない</h2>
 * <p>
 * <b>ここは「隠れる条件」を並べない。</b>試しのパスを1本作って
 * {@link RouteTree#probe} に流し、<b>返ってきたのが自分かどうかだけ</b>を見る。
 * </p>
 * <p>
 * 条件を書き起こすと、探索の順番（固定 &gt; 変数 &gt; ワイルドカード）を
 * <b>2か所に持つ</b>ことになる。片方を直したときにもう片方が黙って古くなり、
 * <b>「警告が出ないから大丈夫」が嘘になる</b>。本物のマッチャに聞いていれば、
 * 探索を変えても検出は勝手に追いつく。
 * </p>
 *
 * <h2>試しのパスの作り方</h2>
 * <p>
 * パターンの <b>変数のところに、どの固定セグメントとも重ならない語を置く</b>。
 * 適当な語ではなく<b>重ならない語</b>にするのが要点で、
 * たまたま固定セグメントと同じ語を選ぶと、そちらが先に当たって
 * <b>到達できるものを「到達不能」と言ってしまう</b>。
 * </p>
 * <p>
 * ワイルドカードは<b>長さを変えて何度か試す</b>。1つでは足りない：
 * {@code /a/{x}} と {@code /a/{x}/*} と {@code /a/*} が並んでいると、
 * {@code /a/*} は<b>どの長さでも他方に取られる</b>（1つなら {@code {x}}、
 * 2つ以上なら {@code {x}/*}）。これは<b>どちらか一方だけでは隠せない</b>ので、
 * 2つを見比べる形の判定では見つからない。
 * </p>
 *
 * <h2>見つからないもの</h2>
 * <p>
 * <b>「ほぼ届かない」は見つけない。</b>たとえば {@code /users/me} が
 * {@code /users/{id}} より先に当たるのは正しい動きで、
 * {@code /users/{id}} は {@code me} 以外のすべてで呼ばれる。
 * ここが見るのは<b>ただの1本も来ないもの</b>だけである。
 * </p>
 */
final class UnreachableRoutes {

	/** 試しのパスに使う語のもと */
	private static final String PROBE = "jimble_probe";

	private UnreachableRoutes () {
	}

	/**
	 * 見つけたもの
	 *
	 * @param route		呼ばれないルート
	 * @param winner	代わりに当たるルート
	 */
	record Finding (RouteInfo route, RouteInfo winner) {

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String toString () {

			return "%s は一生呼ばれません（%s が先に当たります）".formatted(route, winner);

		}

	}

	/**
	 * 探す
	 *
	 * @param tree	ルートツリー（一番外側）
	 * @return	見つけたもの。無ければ空
	 */
	static List<Finding> detect (RouteTree tree) {

		List<RouteInfo> all = new ArrayList<>();
		tree.collect("", all);

		if (all.size() < 2) {
			return List.of();
		}

		Set<String> statics = new HashSet<>();
		tree.collectStaticNames(statics);

		String probe = probeSegment(statics);

		/*
		 * ワイルドカードを何セグメントぶんまで伸ばすか。
		 * いちばん深いルートより1つ深ければ、そこから先は
		 * <b>ワイルドカードしか当たらない</b>ので、伸ばしても答えは変わらない。
		 */
		int depth = 1;

		for (RouteInfo info : all) {
			depth = Math.max(depth, PathSegments.ofPattern(info.path()).size());
		}

		// ルートから「どの登録か」を引くため（Route は同一性で見分ける）
		Map<Route, RouteInfo> names = new IdentityHashMap<>();

		for (RouteInfo info : all) {
			names.putIfAbsent(info.route(), info);
		}

		List<Finding> findings = new ArrayList<>();

		for (RouteInfo info : all) {

			Route winner = winner(tree, info, probe, depth);

			if (winner != null) {
				findings.add(new Finding(info, names.get(winner)));
			}

		}

		return findings;

	}

	/**
	 * このルートに届くパスがあるか調べ、無ければ代わりに当たるものを返す
	 *
	 * @param tree		ルートツリー
	 * @param info		調べるルート
	 * @param probe		変数のところに置く語
	 * @param depth		ワイルドカードを伸ばす上限
	 * @return	代わりに当たるルート。届くなら null
	 */
	private static Route winner (RouteTree tree, RouteInfo info, String probe, int depth) {

		List<String> pattern = segments(info.path());

		boolean wildcard = !pattern.isEmpty() && "*".equals(pattern.get(pattern.size() - 1));

		if (!wildcard) {

			Route found = tree.probe(info.method(), PathSegments.of(fill(pattern, probe)));

			return found == info.route() ? null : found;

		}

		List<String> prefix = fill(pattern.subList(0, pattern.size() - 1), probe);

		Route first = null;

		for (int length = 1; length <= depth + 1; length++) {

			List<String> path = new ArrayList<>(prefix);

			for (int i = 0; i < length; i++) {
				path.add(probe);
			}

			Route found = tree.probe(info.method(), PathSegments.of(path));

			if (found == info.route()) {
				return null;
			}

			if (first == null) {
				first = found;
			}

		}

		return first;

	}

	/**
	 * 変数のところを試しの語で埋める
	 *
	 * @param pattern	パターンのセグメント
	 * @param probe		置く語
	 * @return	試しのパスのセグメント
	 */
	private static List<String> fill (List<String> pattern, String probe) {

		List<String> result = new ArrayList<>(pattern.size());

		for (String segment : pattern) {
			result.add(isVariable(segment) ? probe : segment);
		}

		return result;

	}

	/**
	 * 変数か
	 *
	 * @param segment	セグメント
	 * @return	変数なら true
	 */
	private static boolean isVariable (String segment) {

		return segment.startsWith("{") && segment.endsWith("}");

	}

	/**
	 * パスをセグメントに割る
	 *
	 * @param path	絶対パス
	 * @return	セグメント
	 */
	private static List<String> segments (String path) {

		List<String> result = new ArrayList<>();

		for (String segment : path.split("/")) {
			if (!segment.isEmpty()) {
				result.add(segment);
			}
		}

		return result;

	}

	/**
	 * どの固定セグメントとも重ならない語を作る
	 *
	 * @param statics	固定セグメントの名前
	 * @return	語
	 */
	private static String probeSegment (Set<String> statics) {

		String candidate = PROBE;

		for (int suffix = 0; statics.contains(candidate); suffix++) {
			candidate = PROBE + suffix;
		}

		return candidate;

	}

}
