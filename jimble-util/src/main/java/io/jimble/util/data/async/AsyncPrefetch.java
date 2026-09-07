package io.jimble.util.data.async;

import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 先読み（要件 F-A-06〜08）
 *
 * <p>
 * ツリーを走査して<b>未読み込みで、まとめて読める枝</b>を集め、
 * {@code batchKey} ごとに1回だけ {@code loadBatch} を呼んで埋める。
 * 20 件の記事それぞれにコメントの {@link AsyncList} がぶら下がっていれば、
 * <b>1 + 20 本だったクエリが 1 + 1 本になる。</b>
 * </p>
 *
 * <pre>
 * Data response = new Data().putData("posts", posts);
 *
 * AsyncPrefetch.run(response);   // ここでまとめて引く
 *
 * response.getJsonString();      // もう SQL は飛ばない
 * </pre>
 *
 * <h2>走査そのものが読み込みを起こさない（要件 F-A-04）</h2>
 * <p>
 * 未読み込みの {@link Async} は<b>中を見ない</b>。読み込み済みのものだけ
 * {@code loadedValues()} で降りる。走査した瞬間に SQL が飛ぶなら、
 * 先読みする意味が無い。
 * </p>
 *
 * <h2>止まること（要件 F-A-07）</h2>
 * <p>
 * 埋めると新しい枝が生えるので、生えなくなるまで繰り返す。無限に回らないよう、
 * 次の3つで止める。
 * </p>
 * <ol>
 *   <li><b>{@code (batchKey, batchId)} は1回しか展開しない。</b>
 *       A が B を、B が A を持つ形でも、2周目には候補が消える</li>
 *   <li><b>周回の上限</b>（{@code async.prefetch.max_depth}。既定 5）</li>
 *   <li><b>同じオブジェクトを二度たどらない</b>（1回の走査の中で、参照の同一性で判定）</li>
 * </ol>
 *
 * <h2>先読みしてもしなくても、出力は同じ（要件 F-A-08）</h2>
 * <p>
 * {@code loadBatch} が返さなかった id は<b>「空」として読み込み済みにする。</b>
 * 個別に {@code load()} して 0 件だったのと同じ状態になる。
 * <b>{@code loadBatch} が例外を投げたときは、そのまとまりを1つも読み込み済みにしない。</b>
 * それぞれが個別に読みにいくので、結果は変わらない（速さだけが戻る）。
 * </p>
 *
 * <h2>これは隠れた I/O ではない（原則5）</h2>
 * <p>
 * 呼んだところでしか走らない。レスポンスの直前で自動的に走らせることもできるが、
 * <b>それは設定で明示的に有効にしたときだけ</b>である
 * （{@code async.prefetch.on_response}。既定 false）。
 * </p>
 */
public final class AsyncPrefetch {

	// region 設定

	/** 周回の上限 */
	public static final String KEY_MAX_DEPTH = "async.prefetch.max_depth";

	/** レスポンスを送る前に自動で走らせるか */
	public static final String KEY_ON_RESPONSE = "async.prefetch.on_response";

	/** 周回の上限の既定値 */
	public static final int DEFAULT_MAX_DEPTH = 5;

	/**
	 * 周回の上限
	 *
	 * @return	上限
	 */
	public static int maxDepth () {

		return (int) Conf.conf().getLong(KEY_MAX_DEPTH, DEFAULT_MAX_DEPTH);

	}

	/**
	 * レスポンスを送る前に自動で走らせるか
	 *
	 * <p><b>既定は false。</b>入れたとたんに挙動が変わることがないようにする。</p>
	 *
	 * @return	走らせる場合 = true
	 */
	public static boolean isOnResponse () {

		return Conf.conf().getBoolean(KEY_ON_RESPONSE, false);

	}

	// endregion

	private AsyncPrefetch () {}

	/**
	 * 先読みする
	 *
	 * @param root	走査の起点（{@code Data} / {@code List} / {@code Map} など）
	 * @return	何をしたか
	 */
	public static Result run (Object root) {

		return run(root, maxDepth());

	}

	/**
	 * 先読みする（周回の上限を指定する）
	 *
	 * @param root		走査の起点
	 * @param maxDepth	周回の上限
	 * @return	何をしたか
	 */
	public static Result run (Object root, int maxDepth) {

		if (root == null || maxDepth <= 0) {
			return Result.NOTHING;
		}

		/* 展開したもの。key + id で1回だけ（要件 F-A-07） */
		Set<String> expanded = new HashSet<>();

		int rounds = 0;
		int groups = 0;
		int filled = 0;

		for (int round = 0; round < maxDepth; round++) {

			Map<String, List<Async>> found = collect(root, expanded);

			if (found.isEmpty()) {
				break;
			}

			rounds++;

			for (Map.Entry<String, List<Async>> entry : found.entrySet()) {

				/*
				 * 例外が出ても展開済みにする。
				 * しないと、落ちるクエリを上限まで投げ直すことになる。
				 */
				for (Async node : entry.getValue()) {
					expanded.add(mark(entry.getKey(), node.batchId()));
				}

				groups++;
				filled += fill(entry.getKey(), entry.getValue());

			}

		}

		if (groups > 0) {
			Log.debug("先読み: %d 周 / %d まとまり / %d 件".formatted(rounds, groups, filled));
		}

		return new Result(rounds, groups, filled);

	}

	// region 集める

	/**
	 * 未読み込みで、まとめて読める枝を集める
	 *
	 * @param root		起点
	 * @param expanded	展開済みの印
	 * @return	batchKey ごとの枝
	 */
	private static Map<String, List<Async>> collect (Object root, Set<String> expanded) {

		Map<String, List<Async>> found = new LinkedHashMap<>();

		/*
		 * 同じオブジェクトを二度たどらない。
		 * equals ではなく参照の同一性で見る。AsyncData の equals は hashKey だけを
		 * 見るので、別物なのに同じと判定されて取りこぼす。
		 */
		Set<Object> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

		walk(root, found, expanded, seen);

		return found;

	}

	/**
	 * たどる
	 *
	 * @param node		いま見ているもの
	 * @param found		見つけたもの
	 * @param expanded	展開済みの印
	 * @param seen		たどったもの
	 */
	private static void walk (
		Object node, Map<String, List<Async>> found, Set<String> expanded, Set<Object> seen) {

		if (node == null || !seen.add(node)) {
			return;
		}

		if (node instanceof Async async) {

			if (!async.isLoaded()) {

				/* 未読み込み。中は空なので降りない（降りても意味が無い） */
				candidate(async, found, expanded);
				return;

			}

			/* 読み込み済み。読み込みを起こさない口で降りる */
			walkAll(loadedChildren(async), found, expanded, seen);
			return;

		}

		if (node instanceof Map<?, ?> map) {
			walkAll(map.values(), found, expanded, seen);
			return;
		}

		if (node instanceof Collection<?> collection) {
			walkAll(collection, found, expanded, seen);
			return;
		}

		if (node instanceof Object[] array) {
			walkAll(List.of(array), found, expanded, seen);
		}

	}

	/**
	 * まとめてたどる
	 *
	 * @param nodes		いま見ているものたち
	 * @param found		見つけたもの
	 * @param expanded	展開済みの印
	 * @param seen		たどったもの
	 */
	private static void walkAll (
		Collection<?> nodes, Map<String, List<Async>> found, Set<String> expanded, Set<Object> seen) {

		if (nodes == null) {
			return;
		}

		/*
		 * 走査中に埋めることはしないが、別のスレッドが触ることはありうる。
		 * 写してからたどる。
		 */
		for (Object child : new ArrayList<>(nodes)) {
			walk(child, found, expanded, seen);
		}

	}

	/**
	 * 読み込み済みの中身（読み込みを起こさない）
	 *
	 * @param async	枝
	 * @return	中身
	 */
	private static Collection<?> loadedChildren (Async async) {

		if (async instanceof AsyncData data) {
			return data.loadedValues();
		}

		if (async instanceof AsyncList list) {
			return list.loadedValues();
		}

		return List.of();

	}

	/**
	 * まとめて読む候補に入れる
	 *
	 * @param async		枝
	 * @param found		見つけたもの
	 * @param expanded	展開済みの印
	 */
	private static void candidate (Async async, Map<String, List<Async>> found, Set<String> expanded) {

		String key = async.batchKey();

		if (key == null || key.isEmpty()) {
			// 先読みの対象外。個別に読まれる
			return;
		}

		Object id = async.batchId();

		if (id == null) {
			Log.warn("batchKey があるのに batchId が null です。先読みしません: %s"
				.formatted(async.getClass().getName()));
			return;
		}

		if (expanded.contains(mark(key, id))) {
			// もう1回展開した（要件 F-A-07）
			return;
		}

		found.computeIfAbsent(key, k -> new ArrayList<>()).add(async);

	}

	/**
	 * 展開済みの印
	 *
	 * @param key	batchKey
	 * @param id	batchId
	 * @return	印
	 */
	private static String mark (String key, Object id) {

		return key + " " + id;

	}

	// endregion

	// region 読む

	/**
	 * まとまりを1回で読んで埋める
	 *
	 * @param key	batchKey
	 * @param nodes	枝
	 * @return	埋めた件数
	 */
	private static int fill (String key, List<Async> nodes) {

		Async first = nodes.get(0);

		/*
		 * 同じ batchKey に別のクラスが混ざっていたら、まとめて読めない。
		 * 黙って片方を落とすと「一部だけ空になる」ので、言ってから諦める。
		 */
		for (Async node : nodes) {

			if (node.getClass() != first.getClass()) {

				Log.error(("batchKey が重なっています: %s（%s と %s）。"
					+ "先読みをやめて、それぞれ個別に読みます")
					.formatted(key, first.getClass().getName(), node.getClass().getName()));

				return 0;

			}

		}

		List<Object> ids = new ArrayList<>(new LinkedHashSet<>(nodes.stream().map(Async::batchId).toList()));

		try {

			if (first instanceof AsyncData) {
				return fillData(nodes, ids);
			}

			if (first instanceof AsyncList) {
				return fillList(nodes, ids);
			}

			return 0;

		} catch (Exception ex) {

			/*
			 * 1つも読み込み済みにしない。それぞれが個別に読みにいくので、
			 * 結果は変わらない（要件 F-A-08）。
			 */
			Log.error(ex, "先読みに失敗しました: %s（個別に読み直します）".formatted(key));

			return 0;

		}

	}

	/**
	 * {@link AsyncData} のまとまりを埋める
	 *
	 * @param nodes	枝
	 * @param ids	id
	 * @return	埋めた件数
	 * @throws Exception	読み込みに失敗した場合
	 */
	private static int fillData (List<Async> nodes, List<Object> ids) throws Exception {

		Map<Object, Data> loaded = ((AsyncData) nodes.get(0)).loadBatch(ids);

		if (loaded == null) {
			// このクラスは先読みに対応していない
			return 0;
		}

		for (Async node : nodes) {
			// 返ってこなかった id は「無かった」。null で読み込み済みにする
			((AsyncData) node).putData(loaded.get(node.batchId()));
		}

		return nodes.size();

	}

	/**
	 * {@link AsyncList} のまとまりを埋める
	 *
	 * @param nodes	枝
	 * @param ids	id
	 * @return	埋めた件数
	 * @throws Exception	読み込みに失敗した場合
	 */
	private static int fillList (List<Async> nodes, List<Object> ids) throws Exception {

		Map<Object, List<Data>> loaded = ((AsyncList) nodes.get(0)).loadBatch(ids);

		if (loaded == null) {
			return 0;
		}

		for (Async node : nodes) {
			// 返ってこなかった id は 0 件。個別に読んで 0 件だったのと同じ
			((AsyncList) node).putData(loaded.getOrDefault(node.batchId(), List.of()));
		}

		return nodes.size();

	}

	// endregion

	/**
	 * 何をしたか
	 *
	 * @param rounds	走査した回数
	 * @param groups	まとめて読んだ回数
	 * @param filled	埋めた枝の数
	 */
	public record Result (int rounds, int groups, int filled) {

		/** 何もしなかった */
		public static final Result NOTHING = new Result(0, 0, 0);

	}

}
