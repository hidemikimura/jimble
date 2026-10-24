package io.jimble.json;

import io.jimble.json.internal.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Objects;

/**
 * JSON を読む（D-307）
 *
 * <pre>{@code
 * Object value = Json.read("{\"name\": \"jimble\", \"tags\": [\"web\", \"db\"]}");
 * // Map（キーは読んだ順）と List の木になる
 * }</pre>
 *
 * <h2>読んだ値の型</h2>
 * <ul>
 *   <li>オブジェクトは {@link JsonReadOptions#mapFactory()} が作る Map（既定 LinkedHashMap）</li>
 *   <li>配列は {@link JsonReadOptions#listFactory()} が作る List（既定 ArrayList）</li>
 *   <li>整数は int に収まれば Integer、long に収まれば Long、それより大きければ BigDecimal</li>
 *   <li>小数は Double。Double に収まらなければ BigDecimal</li>
 *   <li>文字列は String。ただし既定では {@code "true"} / {@code "false"} を Boolean にする
 *       （{@link JsonReadOptions.Builder#stringBooleans(boolean)}）</li>
 * </ul>
 *
 * <h2>空と壊れた JSON</h2>
 * <p>
 * 空（空白とコメントだけ）と {@code null} は {@code null} を返す。
 * 壊れていれば {@link JsonException}（行と文字の位置つき）。<b>途中までの値を返すことはしない。</b>
 * </p>
 */
public final class Json {

	/* Reader から読むときに一度に読む文字数 */
	private static final int READ_CHUNK = 8192;

	private Json () {
	}

	/**
	 * 文字の JSON を読む（既定の読み方）
	 *
	 * @param json	JSON（null なら null を返す）
	 * @return	読んだ値
	 * @throws JsonException	読めなかったとき
	 */
	public static Object read (String json) {

		return read(json, JsonReadOptions.DEFAULT);

	}

	/**
	 * 文字の JSON を読む
	 *
	 * @param json		JSON（null なら null を返す）
	 * @param options	読み方
	 * @return	読んだ値
	 * @throws JsonException	読めなかったとき
	 */
	public static Object read (String json, JsonReadOptions options) {

		Objects.requireNonNull(options, "options");

		if (json == null) {
			return null;
		}

		char[] chars = json.toCharArray();
		return new JsonParser(chars, 0, chars.length, options).parseDocument();

	}

	/**
	 * 文字の配列から JSON を読む
	 *
	 * @param chars		文字
	 * @param offset	読み始める位置
	 * @param length	読む文字数
	 * @param options	読み方
	 * @return	読んだ値
	 * @throws JsonException	読めなかったとき
	 */
	public static Object read (char[] chars, int offset, int length, JsonReadOptions options) {

		Objects.requireNonNull(chars, "chars");
		Objects.requireNonNull(options, "options");
		Objects.checkFromIndexSize(offset, length, chars.length);

		return new JsonParser(chars, offset, offset + length, options).parseDocument();

	}

	/**
	 * Reader から JSON を読む
	 *
	 * <p><b>Reader は閉じない</b>（開いた人が閉じる）。</p>
	 *
	 * @param reader	読み元
	 * @param options	読み方
	 * @return	読んだ値
	 * @throws JsonException	読めなかったとき（読み元の失敗も、原因を付けてこれにする）
	 */
	public static Object read (Reader reader, JsonReadOptions options) {

		Objects.requireNonNull(reader, "reader");
		Objects.requireNonNull(options, "options");

		char[] buf = new char[READ_CHUNK];
		int length = 0;

		try {
			int n;
			while ((n = reader.read(buf, length, buf.length - length)) != -1) {
				length += n;
				if (length == buf.length) {
					buf = Arrays.copyOf(buf, grow(buf.length));
				}
			}
		} catch (IOException ex) {
			throw new JsonException("JSON を読み元から読めませんでした: " + ex.getMessage(), ex);
		}

		return new JsonParser(buf, 0, length, options).parseDocument();

	}

	/**
	 * InputStream から JSON を読む
	 *
	 * <p><b>InputStream は閉じない</b>（開いた人が閉じる）。</p>
	 *
	 * @param stream	読み元
	 * @param charset	文字コード
	 * @param options	読み方
	 * @return	読んだ値
	 * @throws JsonException	読めなかったとき
	 */
	public static Object read (InputStream stream, Charset charset, JsonReadOptions options) {

		Objects.requireNonNull(stream, "stream");
		Objects.requireNonNull(charset, "charset");

		return read(new InputStreamReader(stream, charset), options);

	}

	/**
	 * 溜め置きを広げる大きさ
	 */
	private static int grow (int current) {

		if (current >= Integer.MAX_VALUE - 8) {
			throw new JsonException("JSON が大きすぎます", null);
		}
		return (int) Math.min((long) current * 2, Integer.MAX_VALUE - 8);

	}

}
