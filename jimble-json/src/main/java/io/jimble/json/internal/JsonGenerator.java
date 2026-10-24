package io.jimble.json.internal;

import io.jimble.json.JsonCycle;
import io.jimble.json.JsonException;
import io.jimble.json.JsonTypeWriter;
import io.jimble.json.JsonWriteOptions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.RandomAccess;

/**
 * JSON を書く（D-307）
 *
 * <p>
 * <b>使い捨て。</b>1回の書き出しに1つ作る（スレッドをまたいで使わない）。
 * 書いた文字は溜め置き（char の配列）にためて、{@link Writer} があれば溜まるたびに流し、無ければ最後に文字列にする。
 * </p>
 *
 * <ul>
 *   <li>型ごとの書き方は、クラスごとに1回だけ決めて覚える（{@link ClassValue}）</li>
 *   <li>文字列は、エスケープの要らない区間をまとめて写す</li>
 *   <li>int / long は、文字列を作らずに桁を直接書く</li>
 *   <li>循環は、書いている途中の祖先と参照そのもの（{@code ==}）で比べる</li>
 * </ul>
 */
public final class JsonGenerator implements JsonTypeWriter.Output {

	/* 溜め置きの大きさ */
	private static final int BUFFER_SIZE = 2048;

	/* 16進の文字（大文字） */
	private static final char[] HEX = "0123456789ABCDEF".toCharArray();

	/* ASCII のエスケープ（0 ならそのまま、正なら \ と短い形の文字、-1 なら \\u00XX、-2 は / 、-3 は < >） */
	private static final int[] ESCAPES = new int[128];

	static {
		for (int i = 0; i < 0x20; i++) {
			ESCAPES[i] = -1;
		}
		ESCAPES['\b'] = 'b';
		ESCAPES['\t'] = 't';
		ESCAPES['\n'] = 'n';
		ESCAPES['\f'] = 'f';
		ESCAPES['\r'] = 'r';
		ESCAPES['"'] = '"';
		ESCAPES['\\'] = '\\';
		ESCAPES[0x7F] = -1;
		ESCAPES['/'] = -2;
		ESCAPES['<'] = -3;
		ESCAPES['>'] = -3;
	}

	/* 書き方 */
	private final JsonWriteOptions options;

	/* 書き先（無ければ最後に文字列にする。OutputStream のときは、大きくなってから作る） */
	private Writer writer;

	/* 書き先の OutputStream（Writer を渡されたときは null） */
	private final java.io.OutputStream stream;

	/* OutputStream の文字コード */
	private final java.nio.charset.Charset charset;

	/* 溜め置き（ASCII だけなら 1 文字 1 バイトで持てる） */
	private final TextBuffer sb;

	/* 書き先へ流すときの写し場所（必要になってから作る） */
	private char[] chunk;

	/* UTF-8 で書くときのバイトの写し場所（必要になってから作る） */
	private byte[] bytes;

	/* OutputStream の文字コードが UTF-8 か（自分でバイトにする） */
	private final boolean utf8;

	/* 書き先へ流しながら書くか（文字列にするときは false） */
	private final boolean streaming;

	/* この書き方での ASCII のエスケープ（0 ならそのまま） */
	private final int[] escapes;

	/* 書いている途中の祖先（循環を見つけるため） */
	private Object[] stack = new Object[16];

	/* 祖先に入った位置のキー（例外に場所を載せるため。String / Integer） */
	private Object[] path = new Object[16];

	/* いまの深さ */
	private int depth;

	/* 足した書き方を通さずに書く値（plain で渡されたもの。1回だけ） */
	private Object plainOnce;

	/* 書くときの時間帯 */
	private final ZoneId zone;

	/**
	 * コンストラクタ
	 *
	 * @param options	書き方
	 * @param writer	書き先（null なら {@link #toString()} で文字列にする）
	 */
	public JsonGenerator (JsonWriteOptions options, Writer writer) {

		this(options, writer, null, null);

	}

	/**
	 * コンストラクタ（OutputStream に書く）
	 *
	 * <p>
	 * 小さい JSON は最後にまとめてバイトにして書く（Writer を作らない）。
	 * 溜め置きが大きくなったら、そこで初めて Writer を作って流しながら書く。
	 * </p>
	 *
	 * @param options	書き方
	 * @param stream	書き先
	 * @param charset	文字コード
	 */
	public JsonGenerator (JsonWriteOptions options, java.io.OutputStream stream, java.nio.charset.Charset charset) {

		this(options, null, stream, charset);

	}

	private JsonGenerator (JsonWriteOptions options, Writer writer, java.io.OutputStream stream, java.nio.charset.Charset charset) {

		this.options = options;
		this.writer = writer;
		this.stream = stream;
		this.charset = charset;
		this.utf8 = java.nio.charset.StandardCharsets.UTF_8.equals(charset);
		// 書き先があるときは、流すところまで広げずに済む大きさで作る（広げるたびに写し直さない）
		this.streaming = writer != null || stream != null;
		this.sb = new TextBuffer(streaming ? BUFFER_SIZE + 256 : 512);
		this.escapes = escapeTable(options);
		this.zone = options.zone() != null ? options.zone() : ZoneId.systemDefault();

	}

	/**
	 * 一番外の値を書く
	 *
	 * @param value	値
	 */
	public void writeDocument (Object value) {

		value(value);

		if (stream != null && writer == null) {
			if (utf8) {
				drainUtf8();
				return;
			}
			// 小さい JSON：Writer を作らずに、まとめてバイトにする
			try {
				stream.write(sb.toString().getBytes(charset));
			} catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
			sb.clear();
			return;
		}

		drain();

		if (writer != null) {
			try {
				writer.flush();
			} catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}

	}

	/**
	 * 書いた JSON（書き先が無いとき）
	 *
	 * @return	JSON
	 */
	@Override
	public String toString () {

		return sb.toString();

	}

	// region 値

	@Override
	public void value (Object value) {

		if (value == null) {
			raw4('n', 'u', 'l', 'l');
			return;
		}

		Class<?> type = value.getClass();

		if (value != plainOnce) {
			JsonTypeWriter custom = options.typeWriterFor(type);
			if (custom != null) {
				custom.write(value, this);
				return;
			}
		}

		plainOnce = null;

		switch (Kind.of(type)) {
			case STRING -> writeString((String) value);
			case TO_STRING -> writeString(value.toString());
			case INTEGER -> writeLong(((Number) value).longValue());
			case DOUBLE -> sb.appendAscii(Double.toString((Double) value));
			case FLOAT -> sb.appendAscii(Float.toString((Float) value));
			case NUMBER -> rawString(value.toString());
			case BOOLEAN -> sb.appendAscii((Boolean) value ? "true" : "false");
			case ENUM -> writeString(((Enum<?>) value).name());
			case MAP -> writeMap((Map<?, ?>) value);
			case LIST -> writeList((List<?>) value);
			case ITERABLE -> writeIterator(value, ((Iterable<?>) value).iterator());
			case ENUMERATION -> writeEnumeration(value, (Enumeration<?>) value);
			case ARRAY -> writeArray(value);
			case CHAR_ARRAY -> writeCharArray((char[]) value);
			case OPTIONAL -> value(((Optional<?>) value).orElse(null));
			case DATE -> writeDate((Date) value);
			case SQL_DATE -> writeTemporal(((java.sql.Date) value).toLocalDate(), options.dateFormat());
			case SQL_TIME -> writeTemporal(((java.sql.Time) value).toLocalTime(), options.timeFormat());
			case CALENDAR -> writeInstant(Instant.ofEpochMilli(((Calendar) value).getTimeInMillis()));
			case LOCAL_DATE_TIME -> writeTemporal((LocalDateTime) value, options.dateTimeFormat());
			case LOCAL_DATE -> writeTemporal((LocalDate) value, options.dateFormat());
			case LOCAL_TIME -> writeTemporal((LocalTime) value, options.timeFormat());
			case INSTANT -> writeInstant((Instant) value);
			case OFFSET_DATE_TIME -> writeOffset((OffsetDateTime) value);
			case ZONED_DATE_TIME -> writeOffset(((ZonedDateTime) value).toOffsetDateTime());
			case RECORD, BEAN -> writeBean(value);
		}

	}

	@Override
	public void plain (Object value) {

		plainOnce = value;
		value(value);

	}

	@Override
	public void string (CharSequence value) {

		if (value == null) {
			raw4('n', 'u', 'l', 'l');
			return;
		}
		writeString(value.toString());

	}

	@Override
	public void raw (CharSequence json) {

		rawString(json.toString());

	}



	// endregion

	// region 入れ物

	private void writeMap (Map<?, ?> map) {

		if (!enter(map, '{')) {
			return;
		}

		boolean first = true;
		boolean writeNulls = options.writeNulls();

		for (Map.Entry<?, ?> entry : map.entrySet()) {

			Object key = entry.getKey();
			if (key == null) {
				continue;
			}

			Object v = entry.getValue();
			if (v == null && !writeNulls) {
				continue;
			}

			first = separator(first);
			String name = key instanceof String s ? s : String.valueOf(key);
			writeKey(name);

			path[depth - 1] = name;
			value(v);

		}

		exit('}', first);

	}

	/* キーの書き出し（"name":）を覚える表の大きさ（2 の累乗） */
	private static final int KEY_CACHE_SIZE = 64;

	/* 覚えるキーの長さの上限 */
	private static final int KEY_CACHE_MAX_LENGTH = 64;

	/* 覚えたキー */
	private String[] cachedKeys;

	/* 覚えたキーの書き出し（"name":） */
	private String[] cachedKeyPrefixes;

	/**
	 * キーと「:」を書く
	 *
	 * <p>
	 * 配列の中のオブジェクトは同じキーが続くので、エスケープした形（{@code "name":}）を小さな表で覚えて使い回す。
	 * </p>
	 */
	private void writeKey (String name) {

		if (name.length() > KEY_CACHE_MAX_LENGTH) {
			writeString(name);
			put(':');
			return;
		}

		String[] keys = cachedKeys;
		if (keys == null) {
			keys = new String[KEY_CACHE_SIZE];
			cachedKeys = keys;
			cachedKeyPrefixes = new String[KEY_CACHE_SIZE];
		}

		int slot = name.hashCode() & (KEY_CACHE_SIZE - 1);
		String cached = keys[slot];

		if (cached == name || name.equals(cached)) {
			sb.append(cachedKeyPrefixes[slot]);
			return;
		}

		int start = sb.length();
		writeString(name);
		put(':');

		keys[slot] = name;
		cachedKeyPrefixes[slot] = sb.substring(start);

	}

	private void writeList (List<?> list) {

		if (!(list instanceof RandomAccess)) {
			writeIterator(list, list.iterator());
			return;
		}

		if (!enter(list, '[')) {
			return;
		}

		boolean first = true;
		for (int i = 0, n = list.size(); i < n; i++) {
			first = separator(first);
			path[depth - 1] = i;
			value(list.get(i));
		}

		exit(']', first);

	}

	private void writeIterator (Object owner, Iterator<?> it) {

		if (!enter(owner, '[')) {
			return;
		}

		boolean first = true;
		int i = 0;
		while (it.hasNext()) {
			first = separator(first);
			path[depth - 1] = i++;
			value(it.next());
		}

		exit(']', first);

	}

	private void writeEnumeration (Object owner, Enumeration<?> e) {

		if (!enter(owner, '[')) {
			return;
		}

		boolean first = true;
		int i = 0;
		while (e.hasMoreElements()) {
			first = separator(first);
			path[depth - 1] = i++;
			value(e.nextElement());
		}

		exit(']', first);

	}

	private void writeArray (Object array) {

		if (!enter(array, '[')) {
			return;
		}

		boolean first = true;
		int n = Array.getLength(array);

		if (array instanceof int[] ints) {
			for (int i = 0; i < n; i++) {
				first = separator(first);
				writeLong(ints[i]);
			}
		} else if (array instanceof long[] longs) {
			for (int i = 0; i < n; i++) {
				first = separator(first);
				writeLong(longs[i]);
			}
		} else if (array instanceof byte[] bytes) {
			for (int i = 0; i < n; i++) {
				first = separator(first);
				writeLong(bytes[i]);
			}
		} else if (array instanceof Object[] objects) {
			for (int i = 0; i < n; i++) {
				first = separator(first);
				path[depth - 1] = i;
				value(objects[i]);
			}
		} else {
			for (int i = 0; i < n; i++) {
				first = separator(first);
				value(Array.get(array, i));
			}
		}

		exit(']', first);

	}

	private void writeCharArray (char[] chars) {

		if (!enter(chars, '[')) {
			return;
		}

		boolean first = true;
		for (char c : chars) {
			first = separator(first);
			writeString(String.valueOf(c));
		}

		exit(']', first);

	}

	private void writeBean (Object bean) {

		if (!enter(bean, '{')) {
			return;
		}

		BeanProperties properties = BeanProperties.of(bean.getClass());
		String[] names = properties.names;
		MethodHandle[] getters = properties.getters;
		boolean writeNulls = options.writeNulls();
		boolean first = true;

		for (int i = 0; i < names.length; i++) {

			Object v;
			try {
				v = (Object) getters[i].invokeExact(bean);
			} catch (RuntimeException | Error ex) {
				throw ex;
			} catch (Throwable ex) {
				throw new JsonException("項目「" + names[i] + "」を読めませんでした（" + bean.getClass().getName() + "）: " + ex, ex);
			}

			if (v == null && !writeNulls) {
				continue;
			}

			first = separator(first);
			writeKey(names[i]);

			path[depth - 1] = names[i];
			value(v);

		}

		exit('}', first);

	}

	/**
	 * 入れ物に入る（循環と深さを見る）
	 *
	 * @return	中身を書く場合 = true（循環で空や null を書いたら false）
	 */
	private boolean enter (Object container, char open) {

		for (int i = 0; i < depth; i++) {
			if (stack[i] == container) {
				return cycle(open);
			}
		}

		if (depth >= options.maxDepth()) {
			throw new JsonException("入れ子が深すぎます（上限 " + options.maxDepth() + " 段）: " + path(depth), 0, 0);
		}

		if (depth == stack.length) {
			stack = java.util.Arrays.copyOf(stack, depth * 2);
			path = java.util.Arrays.copyOf(path, depth * 2);
		}

		stack[depth] = container;
		path[depth] = null;
		depth++;

		put(open);
		return true;

	}

	/**
	 * 循環を見つけた
	 */
	private boolean cycle (char open) {

		JsonCycle mode = options.cycle();

		if (mode == JsonCycle.THROW) {
			throw new JsonException("循環参照があります: " + path(depth), 0, 0);
		}

		if (mode == JsonCycle.NULL) {
			raw4('n', 'u', 'l', 'l');
		} else {
			put(open);
			put(open == '{' ? '}' : ']');
		}

		return false;

	}

	/**
	 * 入れ物から出る
	 */
	private void exit (char close, boolean empty) {

		depth--;
		stack[depth] = null;

		if (!empty) {
			newline(depth);
		}

		put(close);

	}

	/**
	 * 要素の前（2つめからは「,」。字下げするなら改行とタブ）
	 *
	 * @return	false（次からは2つめ以降）
	 */
	private boolean separator (boolean first) {

		if (!first) {
			put(',');
			if (streaming) {
				flushIfLarge();
			}
		}
		newline(depth);
		return false;

	}

	private void newline (int tabs) {

		if (!options.indent()) {
			return;
		}

		put('\n');
		for (int i = 0; i < tabs; i++) {
			put('\t');
		}

	}

	/**
	 * いまの場所（例外に載せる。{@code $.a.b[0]}）
	 */
	private String path (int upTo) {

		StringBuilder sb = new StringBuilder("$");
		for (int i = 0; i < upTo; i++) {
			Object key = path[i];
			if (key instanceof Integer index) {
				sb.append('[').append(index).append(']');
			} else if (key != null) {
				sb.append('.').append(key);
			}
		}
		return sb.toString();

	}

	// endregion

	// region 日時

	private void writeDate (Date date) {

		writeInstant(Instant.ofEpochMilli(date.getTime()));

	}

	private void writeInstant (Instant instant) {

		if (options.keepOffset()) {
			writeTemporal(instant.atZone(zone).toOffsetDateTime(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
			return;
		}

		writeTemporal(instant.atZone(zone).toLocalDateTime(), options.dateTimeFormat());

	}

	private void writeOffset (OffsetDateTime dateTime) {

		if (options.keepOffset()) {
			writeTemporal(dateTime, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
			return;
		}

		writeTemporal(dateTime.atZoneSameInstant(zone).toLocalDateTime(), options.dateTimeFormat());

	}

	/**
	 * 日時を書く
	 *
	 * <p>
	 * <b>日時の文字はエスケープしない</b>（{@code /} も {@code \/} にしない。作り直す前と同じ）。
	 * 日時の形に、エスケープの要る文字は出てこない。
	 * </p>
	 */
	private void writeTemporal (TemporalAccessor temporal, DateTimeFormatter format) {

		String prefix = options.datePrefix();

		put('"');
		if (prefix != null) {
			rawString(prefix);
		}
		rawString(format.format(temporal));
		put('"');

	}

	// endregion

	// region 文字と数

	/**
	 * 文字列を書く（クォートで囲み、エスケープする）
	 */
	private void writeString (String s) {

		put('"');

		int[] table = escapes;
		boolean dropSupplementary = options.dropSupplementary();

		int length = s.length();
		int start = 0;

		// いまの区間が Latin-1 の文字だけか（溜め置きに 1 バイトのまま写せるか）
		boolean latin1 = true;

		for (int i = 0; i < length; i++) {

			char c = s.charAt(i);

			if (c < 128) {
				int e = table[c];
				if (e == 0) {
					continue;
				}
				putRange(s, start, i, latin1);
				start = i + 1;
				latin1 = true;
				if (e > 0) {
					put('\\');
					put((char) e);
				} else if (e == -2) {
					put('\\');
					put('/');
				} else {
					unicodeEscape(c);
				}
				continue;
			}

			if (c >= 0x100) {
				latin1 = false;
			}

			if (c == '\u2028' || c == '\u2029') {
				putRange(s, start, i, latin1);
				start = i + 1;
				latin1 = true;
				unicodeEscape(c);
				continue;
			}

			if (dropSupplementary && Character.isHighSurrogate(c) && i + 1 < length && Character.isLowSurrogate(s.charAt(i + 1))) {
				putRange(s, start, i, latin1);
				i++;
				start = i + 1;
				latin1 = true;
			}

		}

		putRange(s, start, length, latin1);
		put('"');

	}


	/**
	 * この書き方での ASCII のエスケープの表を作る（書かない設定のものは 0 にする）
	 */
	private static int[] escapeTable (JsonWriteOptions options) {

		int[] table = ESCAPES.clone();
		if (!options.escapeSlash()) {
			table['/'] = 0;
		}
		if (!options.escapeHtml()) {
			table['<'] = 0;
			table['>'] = 0;
		}
		return table;

	}

	private void unicodeEscape (char c) {

		sb.append('\\');
		sb.append('u');
		sb.append(HEX[(c >> 12) & 0xF]);
		sb.append(HEX[(c >> 8) & 0xF]);
		sb.append(HEX[(c >> 4) & 0xF]);
		sb.append(HEX[c & 0xF]);

	}

	/**
	 * 整数を書く（文字列を作らない）
	 */
	private void writeLong (long value) {

		// StringBuilder.append(long) は文字列を作らずに桁を書く
		sb.append(value);

	}


	// endregion

	// region 溜め置き

	private void put (char c) {

		sb.append(c);

	}

	private void raw4 (char a, char b, char c, char d) {

		sb.append(a);
		sb.append(b);
		sb.append(c);
		sb.append(d);

	}

	private void rawString (String s) {

		sb.append(s);

	}

	private void putRange (String s, int start, int end, boolean latin1) {

		sb.append(s, start, end, latin1);

	}

	/**
	 * 溜め置きが大きくなっていれば、書き先へ流す（書き先があるときだけ）
	 */
	private void flushIfLarge () {

		if (sb.length() < BUFFER_SIZE || (writer == null && stream == null)) {
			return;
		}

		if (utf8 && writer == null) {
			drainUtf8();
			return;
		}

		if (writer == null) {
			writer = new java.io.OutputStreamWriter(stream, charset);
		}

		drain();

	}

	/**
	 * 溜め置きを UTF-8 のバイトにして OutputStream へ流す（Writer を通さない）
	 *
	 * <p>対になっていないサロゲートは {@code ?} にする（OutputStreamWriter と同じ）。</p>
	 */
	private void drainUtf8 () {

		int length = sb.length();
		if (length == 0) {
			return;
		}

		// ASCII だけなら、写さずにそのまま書く（多くの JSON はこれで済む）
		try {
			if (sb.writeAsciiTo(stream)) {
				sb.clear();
				return;
			}
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}

		char[] chars = chunk;
		if (chars == null) {
			chars = new char[Math.min(BUFFER_SIZE, length)];
			chunk = chars;
		} else if (chars.length < Math.min(BUFFER_SIZE, length)) {
			chars = new char[BUFFER_SIZE];
			chunk = chars;
		}

		byte[] out = bytes;
		if (out == null || out.length < chars.length * 3 + 4) {
			out = new byte[chars.length * 3 + 4];
			bytes = out;
		}

		try {

			for (int at = 0; at < length; ) {

				int count = Math.min(chars.length, length - at);
				// サロゲートの組を途中で切らない
				if (count < length - at && Character.isHighSurrogate(sb.charAt(at + count - 1))) {
					count--;
				}
				sb.getChars(at, at + count, chars, 0);
				at += count;

				int n = 0;
				for (int i = 0; i < count; i++) {
					char c = chars[i];
					if (c < 0x80) {
						out[n++] = (byte) c;
					} else if (c < 0x800) {
						out[n++] = (byte) (0xC0 | (c >> 6));
						out[n++] = (byte) (0x80 | (c & 0x3F));
					} else if (Character.isHighSurrogate(c) && i + 1 < count && Character.isLowSurrogate(chars[i + 1])) {
						int cp = Character.toCodePoint(c, chars[++i]);
						out[n++] = (byte) (0xF0 | (cp >> 18));
						out[n++] = (byte) (0x80 | ((cp >> 12) & 0x3F));
						out[n++] = (byte) (0x80 | ((cp >> 6) & 0x3F));
						out[n++] = (byte) (0x80 | (cp & 0x3F));
					} else if (Character.isSurrogate(c)) {
						out[n++] = '?';
					} else {
						out[n++] = (byte) (0xE0 | (c >> 12));
						out[n++] = (byte) (0x80 | ((c >> 6) & 0x3F));
						out[n++] = (byte) (0x80 | (c & 0x3F));
					}
				}

				stream.write(out, 0, n);

			}

		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}

		sb.clear();

	}


	/**
	 * 溜め置きを書き先へ流す
	 */
	private void drain () {

		if (writer == null || sb.length() == 0) {
			return;
		}

		if (chunk == null) {
			chunk = new char[BUFFER_SIZE];
		}

		try {
			int length = sb.length();
			for (int at = 0; at < length; at += chunk.length) {
				int n = Math.min(chunk.length, length - at);
				sb.getChars(at, at + n, chunk, 0);
				writer.write(chunk, 0, n);
			}
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}

		sb.clear();

	}

	// endregion

	/**
	 * 型の分け方（クラスごとに1回だけ決める）
	 */
	private enum Kind {

		STRING, TO_STRING, INTEGER, DOUBLE, FLOAT, NUMBER, BOOLEAN, ENUM,
		MAP, LIST, ITERABLE, ENUMERATION, ARRAY, CHAR_ARRAY, OPTIONAL,
		DATE, SQL_DATE, SQL_TIME, CALENDAR, LOCAL_DATE_TIME, LOCAL_DATE, LOCAL_TIME,
		INSTANT, OFFSET_DATE_TIME, ZONED_DATE_TIME, RECORD, BEAN;

		private static final ClassValue<Kind> CACHE = new ClassValue<>() {
			@Override
			protected Kind computeValue (Class<?> type) {
				return classify(type);
			}
		};

		static Kind of (Class<?> type) {

			return CACHE.get(type);

		}

		private static Kind classify (Class<?> type) {

			if (type == String.class) {
				return STRING;
			}
			if (type == Integer.class || type == Long.class || type == Short.class || type == Byte.class) {
				return INTEGER;
			}
			if (type == Double.class) {
				return DOUBLE;
			}
			if (type == Float.class) {
				return FLOAT;
			}
			if (type == Boolean.class) {
				return BOOLEAN;
			}
			if (Number.class.isAssignableFrom(type)) {
				// BigDecimal / BigInteger / AtomicInteger など（toString の形で書く）
				return NUMBER;
			}
			if (type.isEnum() || (type.getSuperclass() != null && type.getSuperclass().isEnum())) {
				return ENUM;
			}
			if (Map.class.isAssignableFrom(type)) {
				return MAP;
			}
			if (List.class.isAssignableFrom(type)) {
				return LIST;
			}
			if (Iterable.class.isAssignableFrom(type)) {
				return ITERABLE;
			}
			if (Enumeration.class.isAssignableFrom(type)) {
				return ENUMERATION;
			}
			if (type == char[].class) {
				return CHAR_ARRAY;
			}
			if (type.isArray()) {
				return ARRAY;
			}
			if (type == Optional.class) {
				return OPTIONAL;
			}
			if (java.sql.Date.class.isAssignableFrom(type)) {
				return SQL_DATE;
			}
			if (java.sql.Time.class.isAssignableFrom(type)) {
				return SQL_TIME;
			}
			if (Date.class.isAssignableFrom(type)) {
				return DATE;
			}
			if (Calendar.class.isAssignableFrom(type)) {
				return CALENDAR;
			}
			if (type == LocalDateTime.class) {
				return LOCAL_DATE_TIME;
			}
			if (type == LocalDate.class) {
				return LOCAL_DATE;
			}
			if (type == LocalTime.class) {
				return LOCAL_TIME;
			}
			if (type == Instant.class) {
				return INSTANT;
			}
			if (type == OffsetDateTime.class) {
				return OFFSET_DATE_TIME;
			}
			if (type == ZonedDateTime.class) {
				return ZONED_DATE_TIME;
			}
			if (CharSequence.class.isAssignableFrom(type) || type == Character.class
				|| type == Duration.class || type == Period.class) {
				return TO_STRING;
			}
			if (Throwable.class.isAssignableFrom(type)) {
				// 例外は中に潜らない（スタックや原因の中身を JSON に出さない）。toString の文字列にする
				return TO_STRING;
			}
			if (type.isRecord()) {
				return RECORD;
			}
			if (type == Object.class) {
				// 中身の無いオブジェクト（{}）
				return BEAN;
			}
			if (isPlatformClass(type)) {
				// JDK のクラス（UUID / URI / File / Path / Class / Throwable など）は中に潜らず、文字列にする
				return TO_STRING;
			}
			return BEAN;

		}

		/**
		 * JDK のクラスか（中のフィールドを読みに行かない）
		 */
		private static boolean isPlatformClass (Class<?> type) {

			Module module = type.getModule();
			if (module.isNamed()) {
				String name = module.getName();
				return name.startsWith("java.") || name.startsWith("jdk.");
			}

			String name = type.getName();
			return name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jdk.") || name.startsWith("sun.");

		}

	}

}
