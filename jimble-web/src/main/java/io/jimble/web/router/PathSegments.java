package io.jimble.web.router;

import java.util.ArrayList;
import java.util.List;

/**
 * パスのセグメント列
 *
 * <p>
 * <b>生のパスを "/" で分割してから、セグメントごとにデコードする。</b>
 * デコード済みのパスを分割すると、パスパラメータに含まれる %2F が
 * セグメント区切りに化けてルートを取り違える（要件 F-R-23）。
 * </p>
 *
 * <p>
 * 空セグメントは落とす。したがって {@code /a/b} と {@code /a/b/} と {@code /a//b} は
 * すべて同じセグメント列になる（要件 F-R-24）。
 * </p>
 */
public final class PathSegments {

	/* セグメント */
	private final List<String> segments;

	/**
	 * コンストラクタ
	 *
	 * @param segments	セグメント
	 */
	private PathSegments (List<String> segments) {

		this.segments = segments;

	}

	/**
	 * リクエストパスから作る
	 *
	 * @param rawPath	生のパス（パーセントエンコードされたまま）
	 * @return	セグメント列
	 */
	public static PathSegments ofRawPath (String rawPath) {

		return new PathSegments(split(rawPath, true));

	}

	/**
	 * ルート定義のパターンから作る
	 *
	 * <p>パターンはデコードしない（"{id}" や "*" をそのまま扱う）。</p>
	 *
	 * @param pattern	パターン
	 * @return	セグメント列
	 */
	public static PathSegments ofPattern (String pattern) {

		return new PathSegments(split(pattern, false));

	}

	/**
	 * 分割する
	 *
	 * @param path		パス
	 * @param decode	デコードするか
	 * @return	セグメント
	 */
	private static List<String> split (String path, boolean decode) {

		List<String> result = new ArrayList<>();

		if (path == null || path.isEmpty()) {
			return result;
		}

		int start = 0;
		int length = path.length();

		for (int index = 0; index <= length; index++) {
			if (index == length || path.charAt(index) == '/') {
				if (index > start) {
					String segment = path.substring(start, index);
					result.add(decode ? PercentDecoder.decode(segment) : segment);
				}
				start = index + 1;
			}
		}

		return result;

	}

	/**
	 * セグメント数
	 *
	 * @return	セグメント数
	 */
	public int size () {

		return segments.size();

	}

	/**
	 * セグメントを取得する
	 *
	 * @param index	位置
	 * @return	セグメント
	 */
	public String get (int index) {

		return segments.get(index);

	}

	/**
	 * 指定位置以降を "/" で連結する
	 *
	 * <p>ワイルドカードにマッチした残りを取り出すのに使う。</p>
	 *
	 * @param from	開始位置
	 * @return	連結結果
	 */
	public String joinFrom (int from) {

		StringBuilder sb = new StringBuilder();

		for (int index = from; index < segments.size(); index++) {
			if (!sb.isEmpty()) {
				sb.append('/');
			}
			sb.append(segments.get(index));
		}

		return sb.toString();

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString () {

		return "/" + String.join("/", segments);

	}

}
