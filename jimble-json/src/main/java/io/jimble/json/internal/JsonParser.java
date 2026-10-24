package io.jimble.json.internal;

import io.jimble.json.JsonException;
import io.jimble.json.JsonReadOptions;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * JSON を読む（D-307）
 *
 * <p>
 * <b>1回なめるだけで木（Map / List）を作る。</b>作り直す前の読み手は、
 * 読む前に括弧が閉じているかを確かめるために全文をもう1回なめ、文字を1つずつ StringBuilder に足し、
 * 数値は1つごとに BigDecimal を作っていた。
 * </p>
 *
 * <ul>
 *   <li>文字列は、エスケープの無い区間をまとめて写す</li>
 *   <li>キーの文字列は、上限つきの表で使い回す（配列の中のオブジェクトは同じキーが続く）</li>
 *   <li>整数は 18 桁までは long で組み立てる。BigDecimal にするのは、それより長いときと指数のときだけ</li>
 *   <li>上限（深さ・数値の長さ・文字列の長さ）を超えたら、位置を付けて例外にする</li>
 * </ul>
 *
 * <p>
 * <b>使い捨て。</b>1回の読みに1つ作る（スレッドをまたいで使わない）。
 * </p>
 */
public final class JsonParser {

	/* long で組み立てる桁数の上限（19 桁は long をあふれうる） */
	private static final int LONG_DIGITS = 18;

	/* Double の最大値（これを超える小数は BigDecimal で返す） */
	private static final BigDecimal DOUBLE_MAX_VALUE = new BigDecimal(Double.MAX_VALUE);

	/* int の範囲（BigDecimal で読んだ整数を振り分ける） */
	private static final BigDecimal INT_MIN = BigDecimal.valueOf(Integer.MIN_VALUE);
	private static final BigDecimal INT_MAX = BigDecimal.valueOf(Integer.MAX_VALUE);

	/* long の範囲 */
	private static final BigDecimal LONG_MIN = BigDecimal.valueOf(Long.MIN_VALUE);
	private static final BigDecimal LONG_MAX = BigDecimal.valueOf(Long.MAX_VALUE);

	/* キーを使い回す表の大きさ（2 の累乗） */
	private static final int KEY_CACHE_SIZE = 256;

	/* 使い回すキーの長さの上限（長いキーは覚えない） */
	private static final int KEY_CACHE_MAX_LENGTH = 32;

	/* この数のキーを読んでから、使い回しを始める */
	private static final int KEY_CACHE_AFTER = 32;

	/* 読む文字 */
	private final char[] buf;

	/* 読む範囲の終わり */
	private final int end;

	/* 読み方 */
	private final JsonReadOptions options;

	/* 厳密に読むか（よく見るので写しておく） */
	private final boolean strict;

	/* 作る Map */
	private final java.util.function.Supplier<? extends Map<String, Object>> mapFactory;

	/* 作る List */
	private final java.util.function.Supplier<? extends List<Object>> listFactory;

	/* 文字列の値を通すもの */
	private final Function<String, Object> stringValueHook;

	/* 文字列の "true" / "false" を Boolean にするか */
	private final boolean stringBooleans;

	/* 読んでいる位置 */
	private int pos;

	/* いまの入れ子の深さ */
	private int depth;

	/* 使い回すキー（必要になったら作る） */
	private String[] keyCache;

	/* 使い回すキーの文字（比べるため。keyCache と同じ位置） */
	private char[][] keyChars;

	/* 読んだキーの数（使い回しを始めるまで数える） */
	private int keysRead;

	/* エスケープのある文字列を組み立てる（必要になったら作り、使い回す） */
	private StringBuilder sb;

	/**
	 * コンストラクタ
	 *
	 * @param buf		読む文字
	 * @param start		読み始める位置
	 * @param end		読む範囲の終わり
	 * @param options	読み方
	 */
	public JsonParser (char[] buf, int start, int end, JsonReadOptions options) {

		this.buf = buf;
		this.pos = start;
		this.end = end;
		this.options = options;
		this.strict = options.strict();
		this.mapFactory = options.mapFactory();
		this.listFactory = options.listFactory();
		this.stringValueHook = options.stringValueHook();
		this.stringBooleans = options.stringBooleans();

	}

	/**
	 * 一番外の値を読む
	 *
	 * <p>空（空白とコメントだけ）なら {@code null} を返す。値のあとに余分なものがあれば例外。</p>
	 *
	 * @return	読んだ値（Map / List / 文字列 / 数値 / 真偽 / null）
	 * @throws JsonException	読めなかったとき
	 */
	public Object parseDocument () {

		// 先頭の BOM
		if (pos < end && buf[pos] == '\uFEFF') {
			pos++;
		}

		skipWhitespace();

		if (pos >= end) {
			return null;
		}

		Object value = parseValue();

		skipWhitespace();

		if (pos < end) {
			throw error("値のあとに余分なものがあります: " + describe(pos));
		}

		return value;

	}

	// region 値

	/**
	 * 値を1つ読む（前の空白は読み飛ばしてあること）
	 */
	private Object parseValue () {

		char c = buf[pos];

		switch (c) {
			case '{':
				return parseObject();
			case '[':
				return parseArray();
			case '"':
				return stringValue(parseString('"'));
			case '\'':
				if (strict) {
					throw error("シングルクォートの文字列は使えません（厳密な読み）");
				}
				return stringValue(parseString('\''));
			case ',':
			case ':':
			case '}':
			case ']':
				throw error("値がありません: " + describe(pos));
			default:
				return parseBareValue();
		}

	}

	/**
	 * オブジェクトを読む（{@code {} の上にいること）
	 */
	private Map<String, Object> parseObject () {

		enter();
		pos++;

		Map<String, Object> map = mapFactory.get();

		skipWhitespace();

		if (pos < end && buf[pos] == '}') {
			pos++;
			depth--;
			return map;
		}

		while (true) {

			if (pos >= end) {
				throw error("オブジェクトが閉じていません（「}」がありません）");
			}

			String key = parseKey();

			skipWhitespace();

			if (pos >= end || buf[pos] != ':') {
				throw error("キー「" + abbreviate(key) + "」のあとに「:」がありません: " + describe(pos));
			}
			pos++;

			skipWhitespace();

			if (pos >= end) {
				throw error("キー「" + abbreviate(key) + "」の値がありません");
			}

			map.put(key, parseValue());

			skipWhitespace();

			if (pos >= end) {
				throw error("オブジェクトが閉じていません（「}」がありません）");
			}

			char c = buf[pos];

			if (c == '}') {
				pos++;
				depth--;
				return map;
			}

			if (c != ',') {
				throw error("「,」か「}」がありません: " + describe(pos));
			}
			pos++;

			skipWhitespace();

			// 末尾のカンマ
			if (pos < end && buf[pos] == '}') {
				if (strict) {
					throw error("末尾のカンマは使えません（厳密な読み）");
				}
				pos++;
				depth--;
				return map;
			}

		}

	}

	/**
	 * 配列を読む（{@code [} の上にいること）
	 */
	private List<Object> parseArray () {

		enter();
		pos++;

		List<Object> list = listFactory.get();

		skipWhitespace();

		if (pos < end && buf[pos] == ']') {
			pos++;
			depth--;
			return list;
		}

		while (true) {

			if (pos >= end) {
				throw error("配列が閉じていません（「]」がありません）");
			}

			list.add(parseValue());

			skipWhitespace();

			if (pos >= end) {
				throw error("配列が閉じていません（「]」がありません）");
			}

			char c = buf[pos];

			if (c == ']') {
				pos++;
				depth--;
				return list;
			}

			if (c != ',') {
				throw error("「,」か「]」がありません: " + describe(pos));
			}
			pos++;

			skipWhitespace();

			// 末尾のカンマ
			if (pos < end && buf[pos] == ']') {
				if (strict) {
					throw error("末尾のカンマは使えません（厳密な読み）");
				}
				pos++;
				depth--;
				return list;
			}

		}

	}

	/**
	 * キーを読む（前の空白は読み飛ばしてあること）
	 */
	private String parseKey () {

		char c = buf[pos];

		if (c == '"') {
			return parseQuotedKey('"');
		}

		if (c == '\'') {
			if (strict) {
				throw error("シングルクォートのキーは使えません（厳密な読み）");
			}
			return parseQuotedKey('\'');
		}

		if (strict) {
			throw error("キーはダブルクォートで囲んでください（厳密な読み）: " + describe(pos));
		}

		int start = pos;
		int length = scanBareToken();

		if (length == 0) {
			throw error("キーがありません: " + describe(start));
		}

		// クォートの無いキーは、true / null / 数字でも書いたとおりの文字列にする
		return key(start, length);

	}

	/**
	 * クォートの無い値を読む（true / false / null / 数値 / NaN / ただの文字列）
	 */
	private Object parseBareValue () {

		// よくある短い数値は、1回なめるだけで読む
		char head = buf[pos];
		if (!strict && ((head >= '0' && head <= '9') || head == '-')) {
			Object number = quickNumber();
			if (number != null) {
				return number;
			}
		}

		int start = pos;
		int length = scanBareToken();

		if (length == 0) {
			throw error("値として読めない文字があります: " + describe(start));
		}

		// 決まった語
		if (length == 4 && matches(start, "true")) {
			return Boolean.TRUE;
		}
		if (length == 5 && matches(start, "false")) {
			return Boolean.FALSE;
		}
		if (length == 4 && matches(start, "null")) {
			return null;
		}

		char first = buf[start];

		if ((first >= '0' && first <= '9') || first == '-' || first == '+' || first == '.') {
			Object number = parseNumber(start, length);
			if (number != null) {
				return number;
			}
		}

		if (strict) {
			throw error("値として読めません（厳密な読み）: " + new String(buf, start, length), start);
		}

		Double special = specialDouble(start, length);
		if (special != null) {
			return special;
		}

		if (length > options.maxStringLength()) {
			throw error("文字列が長すぎます（上限 " + options.maxStringLength() + " 文字）", start);
		}

		// クォートの無い文字列
		return stringValue(new String(buf, start, length));

	}

	/**
	 * クォートの無い語の終わりまで進む
	 *
	 * <p>
	 * 語のすぐあとに {@code (} / {@code {} / {@code [} が来たら例外にする
	 * （{@code function(){...}} のような、どこで切れるか分からないもの）。
	 * </p>
	 *
	 * @return	語の長さ
	 */
	private int scanBareToken () {

		int start = pos;

		while (pos < end && !isTokenEnd(pos)) {
			pos++;
		}

		if (pos < end && pos > start) {
			char c = buf[pos];
			if (c == '(' || c == '{' || c == '[' || c == '"' || c == '\'') {
				throw error("値の書き方が正しくありません（クォートの無い語のすぐあとに「" + c + "」）: "
					+ new String(buf, start, Math.min(pos - start, 40)), start);
			}
		}

		return pos - start;

	}

	/**
	 * クォートの無い語が、この位置で終わるか（区切りの文字か、コメントの始まり）
	 *
	 * <p>{@code a/b} の「/」は語の一部。{@code 1//コメント} と {@code 1/*コメント*}{@code /} はそこで終わる。</p>
	 */
	private boolean isTokenEnd (int at) {

		char c = buf[at];

		if (c == '/') {
			return !strict && at + 1 < end && (buf[at + 1] == '/' || buf[at + 1] == '*');
		}

		return isBareTerminator(c);

	}

	/**
	 * クォートの無い語を終わらせる文字か
	 */
	private static boolean isBareTerminator (char c) {

		switch (c) {
			case ',':
			case ':':
			case '}':
			case ']':
			case '{':
			case '[':
			case '(':
			case ')':
			case '"':
			case '\'':
			case ' ':
			case '\t':
			case '\n':
			case '\r':
			case '\f':
			case '\b':
			case '\uFEFF':
				return true;
			default:
				return false;
		}

	}

	/**
	 * 文字列の値に、読み方の決まり（日付などの変換・文字列の真偽）を当てる
	 */
	private Object stringValue (String s) {

		if (stringValueHook != null) {
			Object converted = stringValueHook.apply(s);
			if (!(converted instanceof String)) {
				return converted;
			}
			s = (String) converted;
		}

		if (stringBooleans) {
			if ("true".equals(s)) {
				return Boolean.TRUE;
			}
			if ("false".equals(s)) {
				return Boolean.FALSE;
			}
		}

		return s;

	}

	// endregion

	// region 文字列

	/**
	 * クォートで囲んだ文字列を読む（開きのクォートの上にいること）
	 *
	 * @param quote	閉じのクォート
	 */
	private String parseString (char quote) {

		int start = ++pos;
		int max = options.maxStringLength();

		// エスケープの無い区間をまとめて写す
		while (pos < end) {

			char c = buf[pos];

			if (c == quote) {
				int length = pos - start;
				if (length > max) {
					throw error("文字列が長すぎます（上限 " + max + " 文字）", start);
				}
				pos++;
				return new String(buf, start, length);
			}

			if (c == '\\') {
				return parseEscapedString(quote, start);
			}

			if (c < 0x20 && strict) {
				throw error("文字列の中に制御文字があります（厳密な読み。\\n などでエスケープしてください）");
			}

			pos++;

		}

		throw error("文字列が閉じていません（「" + quote + "」がありません）", start - 1);

	}

	/**
	 * クォートで囲んだキーを読む（エスケープが無ければ、使い回しの表を通す）
	 *
	 * @param quote	閉じのクォート
	 */
	private String parseQuotedKey (char quote) {

		int start = pos + 1;
		int i = start;
		int hash = 0;

		// 閉じのクォートを探しながら、使い回しの表を引くハッシュも作る（1回なめるだけ）
		while (i < end) {
			char c = buf[i];
			if (c == quote) {
				if (i - start > options.maxStringLength()) {
					throw error("キーが長すぎます（上限 " + options.maxStringLength() + " 文字）", start);
				}
				pos = i + 1;
				return key(start, i - start, hash);
			}
			if (c == '\\' || (c < 0x20 && strict)) {
				break;
			}
			hash = 31 * hash + c;
			i++;
		}

		return parseString(quote);

	}

	/**
	 * エスケープのある文字列を読む（最初の {@code \} の上にいること）
	 */
	private String parseEscapedString (char quote, int start) {

		StringBuilder out = sb;
		if (out == null) {
			out = new StringBuilder(Math.max(16, (pos - start) * 2));
			sb = out;
		} else {
			out.setLength(0);
		}

		int max = options.maxStringLength();

		out.append(buf, start, pos - start);

		while (pos < end) {

			char c = buf[pos];

			if (c == quote) {
				pos++;
				return out.toString();
			}

			if (c == '\\') {
				readEscape(out);
			} else {
				if (c < 0x20 && strict) {
					throw error("文字列の中に制御文字があります（厳密な読み。\\n などでエスケープしてください）");
				}
				// エスケープの無い区間をまとめて足す
				int runStart = pos;
				while (pos < end && (c = buf[pos]) != quote && c != '\\' && !(c < 0x20 && strict)) {
					pos++;
				}
				out.append(buf, runStart, pos - runStart);
			}

			if (out.length() > max) {
				throw error("文字列が長すぎます（上限 " + max + " 文字）", start);
			}

		}

		throw error("文字列が閉じていません（「" + quote + "」がありません）", start - 1);

	}

	/**
	 * エスケープを1つ読む（{@code \} の上にいること）
	 *
	 * <p>
	 * <b>知らないエスケープは断る。</b>作り直す前の読み手は、知らないもの（<code>&#92;q</code>）を <code>&#92;u</code> と同じに扱い、
	 * 後ろの4文字を16進として飲み込んで、文字列の終わりがずれていた。
	 * </p>
	 */
	private void readEscape (StringBuilder out) {

		int at = pos;

		if (pos + 1 >= end) {
			throw error("文字列が閉じていません（「\\」で終わっています）", at);
		}

		char e = buf[pos + 1];
		pos += 2;

		switch (e) {
			case '"':
				out.append('"');
				return;
			case '\\':
				out.append('\\');
				return;
			case '/':
				out.append('/');
				return;
			case '\'':
				out.append('\'');
				return;
			case 'b':
				out.append('\b');
				return;
			case 'f':
				out.append('\f');
				return;
			case 'n':
				out.append('\n');
				return;
			case 'r':
				out.append('\r');
				return;
			case 't':
				out.append('\t');
				return;
			case 'u':
				if (pos + 4 > end) {
					throw error("\\u のあとに16進が4桁ありません", at);
				}
				int code = 0;
				for (int i = 0; i < 4; i++) {
					int hex = Character.digit(buf[pos + i], 16);
					if (hex < 0) {
						throw error("\\u のあとに16進が4桁ありません: \\u" + new String(buf, pos, 4), at);
					}
					code = (code << 4) | hex;
				}
				pos += 4;
				out.append((char) code);
				return;
			default:
				throw error("エスケープの書き方が正しくありません: \\" + e, at);
		}

	}

	/**
	 * キーの文字列を作る（短いものは使い回す）
	 */
	private String key (int start, int length) {

		int hash = 0;
		for (int i = start, last = start + length; i < last; i++) {
			hash = 31 * hash + buf[i];
		}

		return key(start, length, hash);

	}

	/**
	 * キーの文字列を作る（ハッシュは {@code 31 * h + c} で作ってあること）
	 */
	private String key (int start, int length, int hash) {

		// 小さい JSON では表を作るほうが高くつく。キーをいくつか読んでから使い始める
		if (length > KEY_CACHE_MAX_LENGTH || ++keysRead <= KEY_CACHE_AFTER) {
			return new String(buf, start, length);
		}

		String[] cache = keyCache;
		char[][] chars = keyChars;
		if (cache == null) {
			cache = new String[KEY_CACHE_SIZE];
			chars = new char[KEY_CACHE_SIZE][];
			keyCache = cache;
			keyChars = chars;
		}

		int slot = (hash ^ (hash >>> 16)) & (KEY_CACHE_SIZE - 1);
		char[] cached = chars[slot];

		if (cached != null && cached.length == length
			&& java.util.Arrays.equals(cached, 0, length, buf, start, start + length)) {
			return cache[slot];
		}

		String key = new String(buf, start, length);
		cache[slot] = key;
		chars[slot] = java.util.Arrays.copyOfRange(buf, start, start + length);
		return key;

	}

	// endregion

	// region 数値

	/**
	 * よくある短い数値を、1回なめるだけで読む
	 *
	 * <p>
	 * 18 桁までの整数と、指数の無い短い小数（仮数が 2^53 未満、小数の桁が 22 まで）だけを扱う。
	 * それ以外（長い数値・指数・数値の形でない語）は {@code pos} を戻して null を返し、
	 * 呼んだ側が遅い道（語を切り出してから振り分ける）で読む。
	 * </p>
	 *
	 * @return	読んだ値。速い道で読めなければ null（{@code pos} は動かさない）
	 */
	private Object quickNumber () {

		int start = pos;
		int i = start;

		boolean negative = buf[i] == '-';
		if (negative) {
			i++;
		}

		long mantissa = 0;
		int digits = 0;
		int fraction = -1;

		while (i < end) {
			char c = buf[i];
			if (c >= '0' && c <= '9') {
				mantissa = mantissa * 10 + (c - '0');
				if (++digits > LONG_DIGITS) {
					return null;
				}
				if (fraction >= 0) {
					fraction++;
				}
				i++;
				continue;
			}
			if (c == '.' && fraction < 0) {
				fraction = 0;
				i++;
				continue;
			}
			break;
		}

		// 数値のすぐあとが区切りでなければ（1e3 / 1d / 0x10 / 1.2.3 など）遅い道へ
		if (digits == 0 || (i < end && !isTokenEnd(i))) {
			return null;
		}

		if (fraction < 0) {
			pos = i;
			long value = negative ? -mantissa : mantissa;
			if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
				return (int) value;
			}
			return value;
		}

		if (mantissa >= EXACT_MANTISSA || fraction > 22) {
			return null;
		}

		pos = i;
		double value = fraction > 0 ? mantissa / POW10[fraction] : (double) mantissa;
		return negative ? -value : value;

	}

	/**
	 * 数値を読む
	 *
	 * <p>型の振り分けは作り直す前と同じ（D-298）：</p>
	 * <ul>
	 *   <li>整数は int に収まれば Integer、long に収まれば Long、それより大きければ BigDecimal</li>
	 *   <li>小数は Double。Double に収まらなければ BigDecimal</li>
	 *   <li>「.」の無い指数は値で決める（{@code 1e3} は整数の 1000、{@code 1e-3} は小数）</li>
	 * </ul>
	 *
	 * @return	数値。数値の形でなければ null（ただの文字列として読む）
	 */
	private Object parseNumber (int start, int length) {

		int last = start + length;
		int i = start;

		boolean negative = false;
		char c = buf[i];
		if (c == '-' || c == '+') {
			if (c == '+' && strict) {
				return null;
			}
			negative = c == '-';
			i++;
		}

		int intStart = i;
		while (i < last && isDigit(buf[i])) {
			i++;
		}
		int intDigits = i - intStart;

		boolean hasDot = false;
		int fracDigits = 0;
		if (i < last && buf[i] == '.') {
			hasDot = true;
			i++;
			int fracStart = i;
			while (i < last && isDigit(buf[i])) {
				i++;
			}
			fracDigits = i - fracStart;
		}

		if (intDigits == 0 && fracDigits == 0) {
			return null;
		}

		boolean hasExponent = false;
		if (i < last && (buf[i] == 'e' || buf[i] == 'E')) {
			hasExponent = true;
			i++;
			if (i < last && (buf[i] == '+' || buf[i] == '-')) {
				i++;
			}
			int expStart = i;
			while (i < last && isDigit(buf[i])) {
				i++;
			}
			if (i == expStart) {
				return null;
			}
		}

		if (i != last) {
			return null;
		}

		if (strict) {
			// 先頭の 0 と、「.」の前後の欠け（.5 / 1.）は JSON の決まりに無い
			if (intDigits == 0 || (hasDot && fracDigits == 0) || (intDigits > 1 && buf[intStart] == '0')) {
				return null;
			}
		}

		if (length > options.maxNumberLength()) {
			throw error("数値が長すぎます（上限 " + options.maxNumberLength() + " 文字）", start);
		}

		if (hasDot) {
			return decimal(start, length);
		}

		if (hasExponent) {
			return byValue(new BigDecimal(buf, start, length));
		}

		// 整数（先頭の 0 は数えない）
		int significant = intStart;
		while (significant < last - 1 && buf[significant] == '0') {
			significant++;
		}

		if (last - significant <= LONG_DIGITS) {
			long value = 0;
			for (int j = significant; j < last; j++) {
				value = value * 10 + (buf[j] - '0');
			}
			if (negative) {
				value = -value;
			}
			if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
				return (int) value;
			}
			return value;
		}

		// 19 桁は long に収まることがある（あふれを見ながら負の側で組み立てる。Long.MIN_VALUE も表せる）
		if (last - significant == LONG_DIGITS + 1) {
			long limit = negative ? Long.MIN_VALUE : -Long.MAX_VALUE;
			long value = 0;
			boolean fits = true;
			for (int j = significant; j < last; j++) {
				int digit = buf[j] - '0';
				if (value < limit / 10) {
					fits = false;
					break;
				}
				value *= 10;
				if (value < limit + digit) {
					fits = false;
					break;
				}
				value -= digit;
			}
			if (fits) {
				return negative ? value : -value;
			}
		}

		return integer(new BigDecimal(buf, start, length));

	}

	/**
	 * 「.」のある数値（Double か、収まらなければ BigDecimal）
	 */
	private Object decimal (int start, int length) {

		double fast = fastDecimal(start, length);
		if (!Double.isNaN(fast)) {
			return fast;
		}

		// 長い仮数・指数つき（Eisel–Lemire）。Double の最大値の近くは、下で BigDecimal と比べる
		fast = FastDoubleParser.parse(buf, start, length);
		if (!Double.isNaN(fast) && Math.abs(fast) != Double.MAX_VALUE) {
			return fast;
		}

		String text = new String(buf, start, length);
		double value = Double.parseDouble(text);

		if (Double.isInfinite(value) || Math.abs(value) == Double.MAX_VALUE) {
			BigDecimal exact = new BigDecimal(buf, start, length);
			if (exact.abs().compareTo(DOUBLE_MAX_VALUE) > 0) {
				return exact;
			}
		}

		return value;

	}

	/* 10 の累乗（double で正確に表せるのは 10^22 まで） */
	private static final double[] POW10 = {
		1e0, 1e1, 1e2, 1e3, 1e4, 1e5, 1e6, 1e7, 1e8, 1e9, 1e10, 1e11,
		1e12, 1e13, 1e14, 1e15, 1e16, 1e17, 1e18, 1e19, 1e20, 1e21, 1e22
	};

	/* 仮数をこれより小さく保てば、double で正確に表せる（2^53） */
	private static final long EXACT_MANTISSA = 1L << 53;

	/**
	 * 速い道で小数を読む（指数の無い、短い小数だけ）
	 *
	 * <p>
	 * 仮数が 2^53 未満で、小数の桁が 22 以下なら、{@code 仮数 / 10^桁} は正しく丸めた値になる
	 * （どちらも double で正確に表せ、割り算は1回で丸まる）。
	 * 多くの JSON の小数はここで済み、文字列を作って {@code Double.parseDouble} に渡さなくてよい。
	 * </p>
	 *
	 * @return	読んだ値。速い道で読めなければ NaN
	 */
	private double fastDecimal (int start, int length) {

		int i = start;
		int last = start + length;

		boolean negative = false;
		char c = buf[i];
		if (c == '-' || c == '+') {
			negative = c == '-';
			i++;
		}

		long mantissa = 0;
		int fraction = -1;

		for (; i < last; i++) {
			c = buf[i];
			if (c == '.') {
				fraction = 0;
				continue;
			}
			if (c < '0' || c > '9') {
				// 指数（e / E）は遅い道へ
				return Double.NaN;
			}
			mantissa = mantissa * 10 + (c - '0');
			if (mantissa >= EXACT_MANTISSA) {
				return Double.NaN;
			}
			if (fraction >= 0) {
				fraction++;
			}
		}

		if (fraction > 22) {
			return Double.NaN;
		}

		double value = fraction > 0 ? mantissa / POW10[fraction] : (double) mantissa;

		return negative ? -value : value;

	}

	/**
	 * 「.」の無い指数の数値（値が整数なら整数、そうでなければ小数）
	 */
	private static Object byValue (BigDecimal value) {

		if (value.scale() > 0 && value.stripTrailingZeros().scale() > 0) {
			if (value.abs().compareTo(DOUBLE_MAX_VALUE) <= 0) {
				return value.doubleValue();
			}
			return value;
		}

		return integer(value);

	}

	/**
	 * 整数を、収まる型に振り分ける
	 */
	private static Object integer (BigDecimal value) {

		if (value.compareTo(INT_MIN) >= 0 && value.compareTo(INT_MAX) <= 0) {
			return value.intValueExact();
		}

		if (value.compareTo(LONG_MIN) >= 0 && value.compareTo(LONG_MAX) <= 0) {
			return value.longValueExact();
		}

		return value;

	}

	/**
	 * NaN / Infinity
	 */
	private Double specialDouble (int start, int length) {

		if (length == 3 && matches(start, "NaN")) {
			return Double.NaN;
		}
		if (length == 8 && matches(start, "Infinity")) {
			return Double.POSITIVE_INFINITY;
		}
		if (length == 9 && matches(start, "+Infinity")) {
			return Double.POSITIVE_INFINITY;
		}
		if (length == 9 && matches(start, "-Infinity")) {
			return Double.NEGATIVE_INFINITY;
		}
		return null;

	}

	private static boolean isDigit (char c) {

		return c >= '0' && c <= '9';

	}

	// endregion

	// region 空白とコメント

	/**
	 * 空白とコメントを読み飛ばす
	 */
	private void skipWhitespace () {

		while (pos < end) {

			char c = buf[pos];

			// 空白でも「/」でもない（ほとんどの場合）なら、すぐ返す
			if (c > ' ' && c != '/' && c != '﻿') {
				return;
			}

			switch (c) {
				case ' ':
				case '\t':
				case '\n':
				case '\r':
					pos++;
					continue;
				case '\f':
				case '\b':
				case '\uFEFF':
					if (strict) {
						return;
					}
					pos++;
					continue;
				case '/':
					if (strict || pos + 1 >= end) {
						return;
					}
					char next = buf[pos + 1];
					if (next == '/') {
						skipLineComment();
						continue;
					}
					if (next == '*') {
						skipBlockComment();
						continue;
					}
					return;
				default:
					return;
			}

		}

	}

	private void skipLineComment () {

		pos += 2;
		while (pos < end && buf[pos] != '\n' && buf[pos] != '\r') {
			pos++;
		}

	}

	private void skipBlockComment () {

		int at = pos;
		pos += 2;
		while (pos + 1 < end) {
			if (buf[pos] == '*' && buf[pos + 1] == '/') {
				pos += 2;
				return;
			}
			pos++;
		}
		throw error("コメントが閉じていません（「*/」がありません）", at);

	}

	// endregion

	// region 補助

	/**
	 * 入れ子に入る
	 */
	private void enter () {

		if (++depth > options.maxDepth()) {
			throw error("入れ子が深すぎます（上限 " + options.maxDepth() + " 段）");
		}

	}

	private boolean matches (int start, String word) {

		for (int i = 0, n = word.length(); i < n; i++) {
			if (buf[start + i] != word.charAt(i)) {
				return false;
			}
		}
		return true;

	}

	/**
	 * いまの位置で例外を作る
	 */
	private JsonException error (String message) {

		return error(message, pos);

	}

	/**
	 * 指定した位置で例外を作る（行と文字の位置を数える）
	 */
	private JsonException error (String message, int at) {

		int line = 1;
		int column = 1;
		int limit = Math.min(at, end);
		for (int i = 0; i < limit; i++) {
			char c = buf[i];
			if (c == '\n') {
				line++;
				column = 1;
			} else if (c == '\r') {
				if (i + 1 < limit && buf[i + 1] == '\n') {
					continue;
				}
				line++;
				column = 1;
			} else {
				column++;
			}
		}

		return new JsonException(message, line, column);

	}

	/**
	 * 位置の文字を、例外に載せる形にする
	 */
	private String describe (int at) {

		if (at >= end) {
			return "（終わり）";
		}

		int stop = Math.min(end, at + 20);
		String around = new String(buf, at, stop - at).replace("\n", "\\n").replace("\r", "\\r");
		return "「" + around + (stop < end ? "…" : "") + "」";

	}

	private static String abbreviate (String s) {

		return s.length() > 40 ? s.substring(0, 40) + "…" : s;

	}

	// endregion

}
