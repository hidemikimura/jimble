package io.jimble.web.router;

import java.util.List;

/**
 * パスをセグメントに割らずに読む（要件 NF-P-04 / D-168）
 *
 * <h2>なぜ要るのか</h2>
 * <p>
 * <b>1回のマッチ 512 byte のうち 258 byte は、セグメントごとに {@code String} を
 * 作っていたぶんだった</b>（実測）。探索そのもの——区切りを探して突き合わせる——は
 * <b>52ns / ほぼ 0 byte</b> しかかかっていない。
 * </p>
 *
 * <p>
 * <b>だから割らない。</b>区切りの位置だけ覚えて、
 * 木の中の文字列とは {@link String#regionMatches} で直に比べる——
 * <b>当たったパスパラメータの値を取り出すときだけ</b>文字列を作る。
 * </p>
 *
 * <h2>パーセント記号があるときだけ、いままでどおり</h2>
 * <p>
 * ルートのパターンは<b>デコードされていない字面</b>で持っているのに対し、
 * リクエストのパスは<b>セグメントごとにデコードしてから</b>突き合わせる決まりである
 * （要件 F-R-23。{@code %2F} が区切りに化けないようにするため）。
 * </p>
 *
 * <p>
 * <b>{@code %} が無ければデコードは何もしない</b>ので、生のまま比べてよい。
 * 入っているセグメントだけ、そこで1つ文字列を作って<b>覚えておく</b>——
 * <b>ふつうのパスでは1つも作らない</b>。
 * </p>
 */
final class PathCursor {

	/** 覚えられるセグメント数の上限（これを超えたら、以降は遅いほうの道を通る） */
	private static final int MARK_LIMIT = 64;

	/** 空のときに使い回す */
	private static final int[] EMPTY_BOUNDS = new int[0];

	/* 生のパス（すでに割れているものから作ったときは null） */
	private final String path;

	/* 区切りの位置（[開始0, 終了0, 開始1, 終了1, …]） */
	private final int[] bounds;

	/* すでに割れているセグメント（生のパスから作ったときは null） */
	private final List<String> segments;

	/* セグメント数 */
	private final int size;

	/*
	 * {@code %} を含むセグメントの印（1 が立っていれば含む）。
	 *
	 * <b>{@link #MARK_LIMIT} を超えた位置は常に立っているものとして扱う</b>——
	 * 立っていれば遅いほうの道（デコードして比べる）を通るだけで、答えは変わらない。
	 */
	private final long escaped;

	/*
	 * デコード済みの文字列（要るところだけ）。
	 *
	 * <b>ふつうのパスでは null のままである。</b>
	 */
	private String[] decoded;

	/**
	 * コンストラクタ（生のパス）
	 *
	 * @param path		生のパス
	 * @param bounds	区切りの位置
	 * @param size		セグメント数
	 * @param escaped	{@code %} を含むセグメントの印
	 */
	private PathCursor (String path, int[] bounds, int size, long escaped) {

		this.path = path;
		this.bounds = bounds;
		this.segments = null;
		this.size = size;
		this.escaped = escaped;

	}

	/**
	 * コンストラクタ（すでに割れているもの）
	 *
	 * @param segments	セグメント
	 */
	private PathCursor (List<String> segments) {

		this.path = null;
		this.bounds = null;
		this.segments = segments;
		this.size = segments.size();
		this.escaped = 0L;

	}

	/**
	 * 生のパスから作る
	 *
	 * <p>
	 * <b>空のセグメントは落とす</b>（要件 F-R-24）——
	 * {@code /a/b} {@code /a/b/} {@code /a//b} は同じ並びになる。
	 * </p>
	 *
	 * @param rawPath	生のパス
	 * @return	カーソル
	 */
	static PathCursor ofRawPath (String rawPath) {

		if (rawPath == null || rawPath.isEmpty()) {
			return new PathCursor("", EMPTY_BOUNDS, 0, 0L);
		}

		int length = rawPath.length();

		/*
		 * <b>2周する。</b>1周目で数を数え、2周目で位置を書く。
		 *
		 * <b>先に多めに取ると、長いパスで取りすぎる</b>（長さ 2000 のパスに
		 * int[2000] を取ると、削ったぶんより大きくなる）。
		 * 短い文字列を2回なめる費用は、確保しすぎる費用より小さい。
		 */
		int count = 0;
		int start = 0;

		for (int index = 0; index <= length; index++) {

			if (index < length && rawPath.charAt(index) != '/') {
				continue;
			}

			if (index > start) {
				count++;
			}

			start = index + 1;

		}

		if (count == 0) {
			return new PathCursor(rawPath, EMPTY_BOUNDS, 0, 0L);
		}

		int[] marks = new int[count * 2];

		int at = 0;
		long escaped = 0L;
		boolean hasPercent = false;

		start = 0;

		for (int index = 0; index <= length; index++) {

			if (index < length) {

				char c = rawPath.charAt(index);

				if (c == '%') {
					hasPercent = true;
				}

				if (c != '/') {
					continue;
				}

			}

			if (index > start) {

				marks[at * 2] = start;
				marks[at * 2 + 1] = index;

				if (hasPercent && at < MARK_LIMIT) {
					escaped |= 1L << at;
				}

				at++;

			}

			hasPercent = false;
			start = index + 1;

		}

		/*
		 * 覚えきれなかった位置は、常に「デコードが要る」ものとして扱う。
		 * <b>そちらの道でも答えは同じ</b>で、遅いだけである。
		 */
		if (count > MARK_LIMIT) {
			escaped |= -1L << MARK_LIMIT;
		}

		return new PathCursor(rawPath, marks, count, escaped);

	}

	/**
	 * すでに割れているものから作る
	 *
	 * <p>起動時の到達不能ルートの検出（要件 F-R-13）で使う。</p>
	 *
	 * @param segments	セグメント（デコード済み）
	 * @return	カーソル
	 */
	static PathCursor of (List<String> segments) {

		return new PathCursor(segments);

	}

	/**
	 * セグメント数
	 *
	 * @return	セグメント数
	 */
	int size () {

		return size;

	}

	/**
	 * 位置 {@code index} のセグメントが {@code key} と同じか
	 *
	 * <p><b>文字列を作らずに比べる。</b></p>
	 *
	 * @param index	位置
	 * @param key	比べる文字列
	 * @return	同じ場合 = true
	 */
	boolean matches (int index, String key) {

		if (segments != null || isEscaped(index)) {
			return text(index).equals(key);
		}

		int start = bounds[index * 2];
		int end = bounds[index * 2 + 1];

		return key.length() == end - start && path.regionMatches(start, key, 0, key.length());

	}

	/**
	 * 位置 {@code index} のセグメントのハッシュ
	 *
	 * <p>
	 * <b>{@link String#hashCode()} と同じ値を返す。</b>
	 * 文字列を作らずに引けるようにするために要る。
	 * </p>
	 *
	 * @param index	位置
	 * @return	ハッシュ
	 */
	int hash (int index) {

		if (segments != null || isEscaped(index)) {
			return text(index).hashCode();
		}

		int start = bounds[index * 2];
		int end = bounds[index * 2 + 1];

		int hash = 0;

		for (int at = start; at < end; at++) {
			hash = 31 * hash + path.charAt(at);
		}

		return hash;

	}

	/**
	 * 位置 {@code index} のセグメント（文字列）
	 *
	 * <p>
	 * <b>ここで初めて文字列を作る。</b>パスパラメータに束縛するときと、
	 * {@code %} を含むセグメントを比べるときだけ通る。
	 * </p>
	 *
	 * @param index	位置
	 * @return	セグメント
	 */
	String text (int index) {

		if (segments != null) {
			return segments.get(index);
		}

		/*
		 * <b>{@code %} が無ければ覚えない（要件 D-168）。</b>
		 * 覚えるための配列そのものが、深さ4で 48 byte——
		 * <b>ここを通るのは束縛するとき（ふつう1つ）だけ</b>なので、
		 * 2度目が来ない値のために表を作ることになる。
		 */
		if (!isEscaped(index)) {
			return path.substring(bounds[index * 2], bounds[index * 2 + 1]);
		}

		if (decoded == null) {
			decoded = new String[size];
		}

		String already = decoded[index];

		if (already != null) {
			return already;
		}

		String value = PercentDecoder.decode(
			path.substring(bounds[index * 2], bounds[index * 2 + 1]));

		decoded[index] = value;

		return value;

	}

	/**
	 * 位置 {@code from} 以降を {@code "/"} でつなぐ
	 *
	 * <p>ワイルドカードにマッチした残りを取り出すのに使う。</p>
	 *
	 * @param from	開始位置
	 * @return	つないだもの
	 */
	String joinFrom (int from) {

		StringBuilder sb = new StringBuilder();

		for (int index = from; index < size; index++) {
			if (!sb.isEmpty()) {
				sb.append('/');
			}
			sb.append(text(index));
		}

		return sb.toString();

	}

	/**
	 * {@code %} を含むか
	 *
	 * @param index	位置
	 * @return	含む場合 = true
	 */
	private boolean isEscaped (int index) {

		return index >= MARK_LIMIT || (escaped & (1L << index)) != 0L;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString () {

		StringBuilder sb = new StringBuilder();

		for (int index = 0; index < size; index++) {
			sb.append('/').append(text(index));
		}

		return sb.isEmpty() ? "/" : sb.toString();

	}

}
