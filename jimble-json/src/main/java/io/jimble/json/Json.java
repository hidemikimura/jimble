package io.jimble.json;

import io.jimble.json.internal.JsonGenerator;
import io.jimble.json.internal.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Objects;

/**
 * JSON を読む・書く（D-307）
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
	 * JSON の文字列にする（既定の書き方）
	 *
	 * @param value	値（Map / List / 配列 / Bean / record / 文字列 / 数値 / 日時など。null は {@code null}）
	 * @return	JSON
	 * @throws JsonException	書けなかったとき（循環参照・深すぎる入れ子など）
	 */
	public static String write (Object value) {

		return write(value, JsonWriteOptions.DEFAULT);

	}

	/**
	 * JSON の文字列にする
	 *
	 * @param value		値
	 * @param options	書き方
	 * @return	JSON
	 * @throws JsonException	書けなかったとき
	 */
	public static String write (Object value, JsonWriteOptions options) {

		Objects.requireNonNull(options, "options");

		JsonGenerator generator = new JsonGenerator(options, null);
		generator.writeDocument(value);
		return generator.toString();

	}

	/**
	 * Writer に JSON を書く
	 *
	 * <p><b>Writer は閉じない</b>（開いた人が閉じる）。書き終わったら flush する。</p>
	 *
	 * @param value		値
	 * @param writer	書き先
	 * @param options	書き方
	 * @throws JsonException	書けなかったとき（書き先の失敗も、原因を付けてこれにする）
	 */
	public static void write (Object value, Writer writer, JsonWriteOptions options) {

		Objects.requireNonNull(writer, "writer");
		Objects.requireNonNull(options, "options");

		try {
			new JsonGenerator(options, writer).writeDocument(value);
			writer.flush();
		} catch (UncheckedIOException ex) {
			throw new JsonException("JSON を書き先に書けませんでした: " + ex.getCause().getMessage(), ex.getCause());
		} catch (IOException ex) {
			throw new JsonException("JSON を書き先に書けませんでした: " + ex.getMessage(), ex);
		}

	}

	/**
	 * OutputStream に JSON を書く
	 *
	 * <p><b>OutputStream は閉じない</b>（開いた人が閉じる）。書き終わったら flush する。</p>
	 *
	 * @param value		値
	 * @param stream	書き先
	 * @param charset	文字コード
	 * @param options	書き方
	 * @throws JsonException	書けなかったとき
	 */
	public static void write (Object value, OutputStream stream, Charset charset, JsonWriteOptions options) {

		Objects.requireNonNull(stream, "stream");
		Objects.requireNonNull(charset, "charset");
		Objects.requireNonNull(options, "options");

		try {
			new JsonGenerator(options, stream, charset).writeDocument(value);
			stream.flush();
		} catch (UncheckedIOException ex) {
			throw new JsonException("JSON を書き先に書けませんでした: " + ex.getCause().getMessage(), ex.getCause());
		} catch (IOException ex) {
			throw new JsonException("JSON を書き先に書けませんでした: " + ex.getMessage(), ex);
		}

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
