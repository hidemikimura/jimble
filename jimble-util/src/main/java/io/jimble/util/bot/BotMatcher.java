package io.jimble.util.bot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 名乗り（User-Agent）を、たくさんの正規表現と一度に突き合わせる（D-287）
 *
 * <p>
 * <b>2.5.1 までは約 1,500 の正規表現を順に試していた</b>。初めて見る名乗りごとに 150µs ほどかかり、
 * 名乗りを毎回変えてくる巡回にはキャッシュが効かなかった。
 * </p>
 *
 * <p>
 * ほとんどの定義は<b>必ず含む文字列</b>を持つ（約 1,100 は文字列そのもの）。それらを Aho-Corasick の木にまとめ、
 * 名乗りを1回なめて候補を絞り、<b>候補にだけ正規表現をかける</b>。文字列そのものの定義は、見つかった時点で当たり。
 * 必ず含む文字列を取り出せない定義（一番外の {@code |}・{@code (?i)} など）は、これまでどおり毎回かける。
 * </p>
 */
final class BotMatcher {

	/* 定義 */
	private final List<Pattern> patterns;

	/* 定義が文字列そのものか（見つかれば当たり） */
	private final boolean[] literal;

	/* 必ず含む文字列を持たない定義（毎回かける） */
	private final int[] always;

	/* 木：節ごとの、次の文字と行き先（文字の順） */
	private final char[][] labels;
	private final int[][] targets;

	/* 木：失敗したときに戻る節 */
	private final int[] fail;

	/* 木：その節で見つかる定義 */
	private final int[][] outputs;

	BotMatcher (List<String> definitions) {

		this.patterns = new ArrayList<>(definitions.size());
		this.literal = new boolean[definitions.size()];

		List<Map<Character, Integer>> gotos = new ArrayList<>();
		List<List<Integer>> outs = new ArrayList<>();
		gotos.add(new HashMap<>());
		outs.add(new ArrayList<>());

		List<Integer> alwaysList = new ArrayList<>();

		for (int index = 0; index < definitions.size(); index++) {

			String definition = definitions.get(index);
			patterns.add(Pattern.compile(definition));

			String plain = literalOf(definition);
			String needle = plain != null ? plain : requiredLiteral(definition);

			if (needle == null || needle.isEmpty()) {
				alwaysList.add(index);
				continue;
			}

			literal[index] = plain != null;

			int node = 0;
			for (int i = 0; i < needle.length(); i++) {
				char c = needle.charAt(i);
				Integer next = gotos.get(node).get(c);
				if (next == null) {
					next = gotos.size();
					gotos.get(node).put(c, next);
					gotos.add(new HashMap<>());
					outs.add(new ArrayList<>());
				}
				node = next;
			}
			outs.get(node).add(index);

		}

		this.always = alwaysList.stream().mapToInt(Integer::intValue).toArray();

		int size = gotos.size();
		this.labels = new char[size][];
		this.targets = new int[size][];
		this.fail = new int[size];
		this.outputs = new int[size][];

		for (int node = 0; node < size; node++) {
			Character[] keys = gotos.get(node).keySet().toArray(new Character[0]);
			Arrays.sort(keys);
			labels[node] = new char[keys.length];
			targets[node] = new int[keys.length];
			for (int i = 0; i < keys.length; i++) {
				labels[node][i] = keys[i];
				targets[node][i] = gotos.get(node).get(keys[i]);
			}
		}

		// 失敗の行き先を、浅い節から順に決める（見つかる定義も、戻り先のものを足す）
		List<List<Integer>> merged = new ArrayList<>(outs);
		ArrayDeque<Integer> queue = new ArrayDeque<>();

		for (int child : targets[0]) {
			fail[child] = 0;
			queue.add(child);
		}

		while (!queue.isEmpty()) {
			int node = queue.poll();
			for (int i = 0; i < labels[node].length; i++) {
				char c = labels[node][i];
				int child = targets[node][i];
				int back = fail[node];
				while (back != 0 && step(back, c) < 0) {
					back = fail[back];
				}
				int to = step(back, c);
				fail[child] = to >= 0 && to != child ? to : 0;
				List<Integer> combined = new ArrayList<>(merged.get(child));
				combined.addAll(merged.get(fail[child]));
				merged.set(child, combined);
				queue.add(child);
			}
		}

		for (int node = 0; node < size; node++) {
			outputs[node] = merged.get(node).stream().mapToInt(Integer::intValue).toArray();
		}

	}

	/**
	 * どれかの定義に当たるか
	 *
	 * @param ua	名乗り
	 * @return	当たった場合 = true
	 */
	boolean matches (String ua) {

		BitSet candidates = null;
		int node = 0;

		for (int i = 0; i < ua.length(); i++) {

			char c = ua.charAt(i);
			int to;

			while ((to = step(node, c)) < 0 && node != 0) {
				node = fail[node];
			}

			node = Math.max(to, 0);

			for (int index : outputs[node]) {
				if (literal[index]) {
					return true;
				}
				if (candidates == null) {
					candidates = new BitSet(patterns.size());
				}
				candidates.set(index);
			}

		}

		if (candidates != null) {
			for (int index = candidates.nextSetBit(0); index >= 0; index = candidates.nextSetBit(index + 1)) {
				if (patterns.get(index).matcher(ua).find()) {
					return true;
				}
			}
		}

		for (int index : always) {
			if (patterns.get(index).matcher(ua).find()) {
				return true;
			}
		}

		return false;

	}

	/**
	 * 定義の数
	 */
	int size () {

		return patterns.size();

	}

	private int step (int node, char c) {

		char[] keys = labels[node];
		int found = Arrays.binarySearch(keys, c);

		return found >= 0 ? targets[node][found] : -1;

	}

	// region 定義を読む

	/* 正規表現で特別な意味を持つ文字 */
	private static final String META = "^$.|?*+()[]{}";

	/**
	 * 文字列そのものの定義なら、その文字列（{@code \/} のような記号のエスケープは外す）
	 *
	 * @param definition	定義
	 * @return	文字列。正規表現なら null
	 */
	static String literalOf (String definition) {

		StringBuilder plain = new StringBuilder();

		for (int i = 0; i < definition.length(); i++) {

			char c = definition.charAt(i);

			if (c == '\\') {
				if (i + 1 >= definition.length() || Character.isLetterOrDigit(definition.charAt(i + 1))) {
					return null;
				}
				plain.append(definition.charAt(++i));
				continue;
			}

			if (META.indexOf(c) >= 0) {
				return null;
			}

			plain.append(c);

		}

		return plain.toString();

	}

	/**
	 * 正規表現が当たるとき、名乗りが必ず含む文字列（いちばん長いもの）
	 *
	 * <p>
	 * <b>迷ったら null（毎回かける）</b>。一番外に {@code |} があるもの、{@code (?i)} のように
	 * 大文字・小文字の扱いを変えるものからは取らない。かっこと {@code [...]} の中は見ない。
	 * {@code ?}・{@code *}・{@code {..}} が付いた文字は、無いかもしれないので外す。
	 * </p>
	 *
	 * @param definition	定義
	 * @return	文字列（2 文字以上）。取れなければ null
	 */
	static String requiredLiteral (String definition) {

		if (definition.contains("(?")) {
			return null;
		}

		List<String> runs = new ArrayList<>();
		StringBuilder run = new StringBuilder();
		int depth = 0;

		for (int i = 0; i < definition.length(); i++) {

			char c = definition.charAt(i);

			if (c == '\\') {
				if (i + 1 >= definition.length()) {
					return null;
				}
				char escaped = definition.charAt(++i);
				if (depth > 0) {
					continue;
				}
				if (Character.isLetterOrDigit(escaped)) {
					// \d \s \b など。文字ではない
					endRun(runs, run);
				} else {
					run.append(escaped);
				}
				continue;
			}

			if (c == '[') {
				// 文字の集まり。閉じるまで飛ばす
				endRun(runs, run);
				int j = i + 1;
				if (j < definition.length() && definition.charAt(j) == '^') {
					j++;
				}
				if (j < definition.length() && definition.charAt(j) == ']') {
					j++;
				}
				while (j < definition.length() && definition.charAt(j) != ']') {
					if (definition.charAt(j) == '\\') {
						j++;
					}
					j++;
				}
				i = j;
				continue;
			}

			if (c == '(') {
				depth++;
				endRun(runs, run);
				continue;
			}

			if (c == ')') {
				depth--;
				endRun(runs, run);
				continue;
			}

			if (depth > 0) {
				continue;
			}

			if (c == '|') {
				return null;
			}

			if (c == '?' || c == '*' || c == '{') {
				// 直前の文字は無いかもしれない
				if (!run.isEmpty()) {
					run.setLength(run.length() - 1);
				}
				endRun(runs, run);
				if (c == '{') {
					int close = definition.indexOf('}', i);
					i = close < 0 ? definition.length() : close;
				}
				continue;
			}

			if (c == '+') {
				// 直前の文字は少なくとも1つある。そこで区切る
				endRun(runs, run);
				continue;
			}

			if (c == '.' || c == '^' || c == '$') {
				endRun(runs, run);
				continue;
			}

			run.append(c);

		}

		endRun(runs, run);

		String longest = null;
		for (String candidate : runs) {
			if (longest == null || candidate.length() > longest.length()) {
				longest = candidate;
			}
		}

		return longest == null || longest.length() < 2 ? null : longest;

	}

	private static void endRun (List<String> runs, StringBuilder run) {

		if (!run.isEmpty()) {
			runs.add(run.toString());
			run.setLength(0);
		}

	}

	// endregion

}
