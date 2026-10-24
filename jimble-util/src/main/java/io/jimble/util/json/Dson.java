package io.jimble.util.json;

import io.jimble.util.annotation.CheckReturnValue;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.Convertor;
import io.jimble.util.convertor.PropertyUtil;
import io.jimble.json.Json;
import io.jimble.json.JsonException;
import io.jimble.json.JsonReadOptions;
import io.jimble.util.internal.json.decoder.DecodeConfigKeys;
import io.jimble.util.internal.json.decoder.IDecoder;
import io.jimble.util.internal.json.decoder.StreamDecoder;
import io.jimble.util.internal.json.encoder.DefaultEncoder;
import io.jimble.util.internal.json.encoder.IEncoder;
import io.jimble.util.internal.json.formatter.lang.StringFormatter;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;

import java.io.*;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * JSONユーティリティクラス.<br>
 * ※このクラスはJava5.0以降のバーションで使用してください.
 *
 * @author DN
 */
@CheckReturnValue
public final class Dson {

	/* ***************************** コンストラクタ. ******************************/

	/**
	 * コンストラクタ.
	 */
	public Dson () {
		// JSONエンコーダーを保持する
		this.encoder = new DefaultEncoder();
		// 読むのは jimble-json（D-307）。古い読み手は、古い設定（DECODE_STRING_UNESCAPE）のときだけ作る
		this.decoder = null;
		this.legacyDecoder = false;
	}

	/**
	 * コンストラクタ.
	 *
	 * @param encoder JSONエンコーダ
	 * @param decoder JSONデコーダ
	 */
	public Dson (IEncoder encoder, IDecoder decoder) {
		// JSONエンコーダーを保持する
		this.encoder = encoder == null ? new DefaultEncoder() : encoder;
		// JSONデコーダーを保持する（渡されたら、それで読む）
		this.decoder = decoder == null ? new StreamDecoder() : decoder;
		this.legacyDecoder = decoder != null;
	}

	/* ***************************** エラー. ******************************/

	/**
	 * エラー判定.
	 */
	private boolean error = false;

	/**
	 * エラー.
	 */
	private Exception exception;

	/**
	 * エラー判定.
	 *
	 * @return エラーの場合 true / エラーではない場合 false
	 */
	public boolean isError () {

		return error || this.encoder.isError() || (this.decoder != null && this.decoder.isError());
	}

	/**
	 * エラーを取得する.
	 *
	 * @return エラー
	 */
	public Exception getErrorException () {

		return error ? exception : this.encoder.isError() ? this.encoder.getErrorException() : this.decoder != null && this.decoder.isError() ? this.decoder.getErrorException() : null;
	}

	/**
	 * エラーを設定する.
	 *
	 * @param e エラー
	 */
	private void setError (Exception e) {

		exception = e;
		error = true;
	}

	/**
	 * エラーを初期化する.
	 */
	private void clearError () {

		this.error = false;
		this.exception = null;
		this.encoder.clearError();
		if (this.decoder != null) {
			this.decoder.clearError();
		}
	}

	/**
	 * デコード結果を {@code Data} として返す
	 *
	 * <p>
	 * <b>4つの decode（ファイル / Reader / ストリーム / 文字列）で同じことをしていた。</b>
	 * 同じ無検査キャストが4か所に並ぶと、
	 * <b>本当に危ないキャストが警告の山に埋もれる</b>ので、ここ1つに閉じる。
	 * </p>
	 * <p>
	 * {@code T} が {@code Data} を受け取れることは、呼ぶ前に
	 * {@code PropertyUtil.isAssignableFrom(Data.class, destClasses[0])} で確かめている。
	 * </p>
	 *
	 * @param dest	デコード結果
	 * @param <T>	返す型
	 * @return	{@code Data} にできなければ null（呼び出し側が {@code Convertor} に回す）
	 */
	@SuppressWarnings("unchecked")
	private static <T> T asData (Object dest) {

		if (dest instanceof Data) {
			return (T) dest;
		}

		if (dest instanceof Map<?, ?> map) {

			try {
				Data res = (Data) PropertyUtil.newInstance(dest.getClass());
				res.putAll((Map<String, ?>) map);
				return (T) res;
			} catch (Exception ignore) {}

		}

		return null;

	}

	/**
	 * 読めなければ投げる（static の {@code decodes} はここを通す。要件 D-195）
	 *
	 * <p>
	 * 1.x の {@code decodes} は、壊れた JSON で<b>黙って {@code null}</b> を返していた——
	 * エラーは中で作った {@code Dson} に入ったまま捨てられるので、呼んだ側からは見る手段が無かった。
	 * インスタンスの {@code decode} は、これまでどおり {@link #isError()} で見る。
	 * </p>
	 *
	 * @param body	読む処理
	 * @param <T>	返す型
	 * @return	読めたもの
	 * @throws JsonParseException	読めなかったとき
	 */
	private static <T> T decodeOrThrow (java.util.function.Function<Dson, T> body) {

		Dson dson = new Dson();
		T result = body.apply(dson);
		if (dson.isError()) {
			Exception cause = dson.getErrorException();
			throw new JsonParseException("JSON を読めませんでした: " + (cause == null ? "" : cause.getMessage()), cause);
		}
		return result;

	}

	/**
	 * 文字の JSON を読む。括弧が閉じていなければ、読む前に投げる（要件 D-195）
	 *
	 * <p>読み手は寛容で、閉じ括弧の無い JSON も途中までの値として読んでしまう。</p>
	 *
	 * @param text	JSON
	 * @param body	読む処理
	 * @param <T>	返す型
	 * @return	読めたもの
	 * @throws JsonParseException	読めなかったとき
	 */
	private static <T> T decodeTextOrThrow (String text, java.util.function.Function<Dson, T> body) {

		T result = decodeOrThrow(body);
		// JSON ですらないもの（読み手は null を返すだけでエラーにしない）
		if (result == null && text != null && !text.isBlank() && !"null".equals(text.strip())) {
			String shown = text.strip();
			throw new JsonParseException("JSON を読めませんでした: " + (shown.length() > 80 ? shown.substring(0, 80) + "…" : shown), null);
		}
		return result;

	}

	/* ***************************** デコード. ******************************/

	/**
	 * JSONデコーダ.
	 */
	private IDecoder decoder;

	/**
	 * 渡された読み手で読むか（渡されなければ jimble-json で読む。D-307）
	 */
	private boolean legacyDecoder;

	/**
	 * JSONファイルをオブジェクトにデコードする.
	 *
	 * @param file        JSONファイル
	 * @param charset     文字コード
	 * @param destClasses 変換希望クラス([0]=変換希望クラス、[1...]=ジェネリクス指定)
	 * @return デコード結果
	 */
	public static <T> T decodes (File file, String charset, Class<?>... destClasses) {

		return decodeOrThrow(dson -> dson.decode(file, charset, destClasses));
	}

	/**
	 * JSONファイルをオブジェクトにデコードする.
	 *
	 * @param conf        設定情報
	 * @param file        JSONファイル
	 * @param charset     文字コード
	 * @param destClasses 変換希望クラス([0]=変換希望クラス、[1...]=ジェネリクス指定)
	 * @return デコード結果
	 */
	public static <T> T decodes (Configration conf, File file, String charset, Class<?>... destClasses) {

		return decodeOrThrow(dson -> dson.decode(conf, file, charset, destClasses));
	}

	/**
	 * JSON文字ストリームをオブジェクトにデコードする.<br>
	 * <b>読み終わると、渡した Reader を閉じます</b>（続けて使えません）。
	 *
	 * @param reader      JSON文字ストリーム
	 * @param destClasses 変換希望クラス([0]=変換希望クラス、[1...]=ジェネリクス指定)
	 * @return デコード結果
	 */
	public static <T> T decodes (Reader reader, Class<?>... destClasses) {

		return decodeOrThrow(dson -> dson.decode(reader, destClasses));
	}

	/**
	 * JSON文字ストリームをオブジェクトにデコードする.<br>
	 * <b>読み終わると、渡した Reader を閉じます</b>（続けて使えません）。
	 *
	 * @param conf        設定情報
	 * @param reader      JSON文字ストリーム
	 * @param destClasses 変換希望クラス([0]=変換希望クラス、[1...]=ジェネリクス指定)
	 * @return デコード結果
	 */
	public static <T> T decodes (Configration conf, Reader reader, Class<?>... destClasses) {

		return decodeOrThrow(dson -> dson.decode(conf, reader, destClasses));
	}

	/**
	 * JSONストリームをオブジェクトにデコードする.<br>
	 * <b>読み終わると、渡した InputStream を閉じます</b>（続けて使えません）。
	 *
	 * @param stream      JSONストリーム
	 * @param charset     文字コード
	 * @param destClasses 変換希望クラス([0]=変換希望クラス、[1...]=ジェネリクス指定)
	 * @return デコード結果
	 */
	public <T> T decodes (InputStream stream, String charset, Class<?>... destClasses) {

		return decodeOrThrow(dson -> dson.decode(stream, charset, destClasses));
	}

	/**
	 * JSONストリームをオブジェクトにデコードする.<br>
	 * <b>読み終わると、渡した InputStream を閉じます</b>（続けて使えません）。
	 *
	 * @param conf        設定情報
	 * @param stream      JSONフストリーム
	 * @param charset     文字コード
	 * @param destClasses 変換希望クラス([0]=変換希望クラス、[1...]=ジェネリクス指定)
	 * @return デコード結果
	 */
	public <T> T decodes (Configration conf, InputStream stream, String charset, Class<?>... destClasses) {

		/*
		 * <b>{@code decodes} ではなく {@code decode} を呼ぶ。</b>
		 * 自分自身を呼ぶと、<b>1回目の呼び出しで {@code StackOverflowError} になる</b>——
		 * 例外の中身が「再帰しました」としか言わないので、
		 * <b>渡した JSON が悪いのだと思って延々と探すことになる</b>。
		 * 隣の3引数版は最初から {@code decode} を呼んでいた（こちらだけ取り違えていた）。
		 */
		return decodeOrThrow(dson -> dson.decode(conf, stream, charset, destClasses));
	}

	/**
	 * JSON 文字列をオブジェクトにデコードする（型を1つだけ指定する）
	 *
	 * <p>
	 * <b>戻り値の型が、渡したクラスに縛られる（D-173）。</b>
	 * 可変長のほう（{@code Class<?>...}）は {@code <T>} をどこにも縛っていないので、
	 * <b>受け取る変数の型が何であってもコンパイルが通る</b>——
	 * </p>
	 *
	 * <pre>
	 * Foo f = Dson.decodes(json, Data.class);   // 通る。落ちるのは代入のとき
	 * </pre>
	 *
	 * <p>
	 * <b>{@code ClassCastException} は呼んだ側の行で出る</b>ので、
	 * どこが間違っているのかは分かるが、<b>気づくのは動かしたときである</b>。
	 * 型を1つだけ渡すならこちらが選ばれ、<b>その場でコンパイルエラーになる</b>。
	 * </p>
	 *
	 * @param <T>       変換先の型
	 * @param value     JSON 文字列
	 * @param destClass 変換希望クラス
	 * @return デコード結果
	 */
	public static <T> T decodes (String value, Class<T> destClass) {

		return decodeTextOrThrow(value, dson -> dson.decode(value, destClass));

	}

	/**
	 * JSON 文字列をオブジェクトにデコードする（型を1つだけ指定する）
	 *
	 * <p>戻り値の型が、渡したクラスに縛られる（D-173）。</p>
	 *
	 * @param <T>       変換先の型
	 * @param conf      設定情報
	 * @param value     JSON 文字列
	 * @param destClass 変換希望クラス
	 * @return デコード結果
	 */
	public static <T> T decodes (Configration conf, String value, Class<T> destClass) {

		return decodeTextOrThrow(value, dson -> dson.decode(conf, value, destClass));

	}

	/**
	 * JSON文字列をオブジェクトにデコードする.
	 *
	 * @param value       JSON文字列
	 * @param destClasses 変換希望クラス([0]=変換希望クラス、[1...]=ジェネリクス指定)
	 * @return デコード結果
	 */
	public static <T> T decodes (String value, Class<?>... destClasses) {

		return decodeTextOrThrow(value, dson -> dson.decode(value, destClasses));
	}

	/**
	 * JSON文字列をオブジェクトにデコードする.
	 *
	 * @param conf        設定情報
	 * @param value       JSON文字列
	 * @param destClasses 変換希望クラス([0]=変換希望クラス、[1...]=ジェネリクス指定)
	 * @return デコード結果
	 */
	public static <T> T decodes (Configration conf, String value, Class<?>... destClasses) {

		return decodeTextOrThrow(value, dson -> dson.decode(conf, value, destClasses));
	}

	/**
	 * JSONファイルをオブジェクトにデコードする.
	 *
	 * @param file        JSONファイル
	 * @param charset     文字コード
	 * @param destClasses 変換希望クラス([0]=変換希望クラス、[1...]=ジェネリクス指定)
	 * @return デコード結果
	 */
	public <T> T decode (File file, String charset, Class<?>... destClasses) {

		return decode(new Configration(), file, charset, destClasses);
	}

	/**
	 * JSONファイルをオブジェクトにデコードする.
	 *
	 * @param conf        設定情報
	 * @param file        JSONファイル
	 * @param charset     文字コード
	 * @param destClasses 変換希望クラス([0]=変換希望クラス、[1...]=ジェネリクス指定)
	 * @return デコード結果
	 */
	public <T> T decode (Configration conf, File file, String charset, Class<?>... destClasses) {

		return decodeWith(conf, new TreeSource() {
			@Override public Object read (JsonReadOptions options) throws IOException {
				try (Reader reader = new InputStreamReader(new FileInputStream(file), Charset.forName(charset))) {
					return Json.read(reader, options);
				}
			}
			@Override public Object readLegacy (IDecoder decoder, Configration c) throws Exception {
				return decoder.decode(c, file, Charset.forName(charset));
			}
		}, destClasses);
	}

	/**
	 * JSON文字ストリームをオブジェクトにデコードする.<br>
	 * <b>読み終わると、渡した Reader を閉じます</b>（続けて使えません）。
	 *
	 * @param reader      JSON文字ストリーム
	 * @param destClasses 変換希望クラス([0]=変換希望クラス、[1...]=ジェネリクス指定)
	 * @return デコード結果
	 */
	public <T> T decode (Reader reader, Class<?>... destClasses) {

		return decode(null, reader, destClasses);
	}

	/**
	 * JSON文字ストリームをオブジェクトにデコードする.<br>
	 * <b>読み終わると、渡した Reader を閉じます</b>（続けて使えません）。
	 *
	 * @param conf        設定情報
	 * @param reader      JSON文字ストリーム
	 * @param destClasses 変換希望クラス([0]=変換希望クラス、[1...]=ジェネリクス指定)
	 * @return デコード結果
	 */
	public <T> T decode (Configration conf, Reader reader, Class<?>... destClasses) {

		return decodeWith(conf, new TreeSource() {
			@Override public Object read (JsonReadOptions options) throws IOException {
				// 読み終わったら閉じる（作り直す前と同じ）
				try (Reader closing = reader) {
					return Json.read(closing, options);
				}
			}
			@Override public Object readLegacy (IDecoder decoder, Configration c) throws Exception {
				return decoder.decode(c, reader);
			}
		}, destClasses);
	}

	/**
	 * JSONストリームをオブジェクトにデコードする.<br>
	 * <b>読み終わると、渡した InputStream を閉じます</b>（続けて使えません）。
	 *
	 * @param stream      JSONストリーム
	 * @param charset     文字コード
	 * @param destClasses 変換希望クラス([0]=変換希望クラス、[1...]=ジェネリクス指定)
	 * @return デコード結果
	 */
	public <T> T decode (InputStream stream, String charset, Class<?>... destClasses) {

		return decode(new Configration(), stream, charset, destClasses);
	}

	/**
	 * JSONストリームをオブジェクトにデコードする.<br>
	 * <b>読み終わると、渡した InputStream を閉じます</b>（続けて使えません）。
	 *
	 * @param conf        設定情報
	 * @param stream      JSONフストリーム
	 * @param charset     文字コード
	 * @param destClasses 変換希望クラス([0]=変換希望クラス、[1...]=ジェネリクス指定)
	 * @return デコード結果
	 */
	public <T> T decode (Configration conf, InputStream stream, String charset, Class<?>... destClasses) {

		return decodeWith(conf, new TreeSource() {
			@Override public Object read (JsonReadOptions options) throws IOException {
				// 読み終わったら閉じる（作り直す前と同じ）
				try (InputStream closing = stream) {
					return Json.read(closing, Charset.forName(charset), options);
				}
			}
			@Override public Object readLegacy (IDecoder decoder, Configration c) throws Exception {
				return decoder.decode(c, stream, Charset.forName(charset));
			}
		}, destClasses);
	}

	/**
	 * JSON文字列をオブジェクトにデコードする.
	 *
	 * @param json        JSON文字列
	 * @param destClasses 変換希望クラス([0]=変換希望クラス、[1...]=ジェネリクス指定)
	 * @return デコード結果
	 */
	public <T> T decode (String json, Class<?>... destClasses) {

		return decode(new Configration(), json, destClasses);
	}

	/**
	 * JSON文字列をオブジェクトにデコードする.
	 *
	 * @param conf        設定情報
	 * @param json        JSON文字列
	 * @param destClasses 変換希望クラス([0]=変換希望クラス、[1...]=ジェネリクス指定)
	 * @return デコード結果
	 */
	public <T> T decode (Configration conf, String json, Class<?>... destClasses) {

		return decodeWith(conf, new TreeSource() {
			@Override public Object read (JsonReadOptions options) {
				return Json.read(json, options);
			}
			@Override public Object readLegacy (IDecoder decoder, Configration c) throws Exception {
				return decoder.decode(c, json);
			}
		}, destClasses);
	}

	/**
	 * JSON の読み元（D-307）
	 */
	private interface TreeSource {

		/**
		 * jimble-json で読む
		 *
		 * @param options	読み方
		 * @return	木（Data / List / スカラー / null）
		 * @throws Exception	読めなかったとき
		 */
		Object read (JsonReadOptions options) throws Exception;

		/**
		 * 古い読み手で読む（渡された IDecoder か、古い設定のとき）
		 *
		 * @param decoder	読み手
		 * @param conf		設定
		 * @return	木
		 * @throws Exception	読めなかったとき
		 */
		Object readLegacy (IDecoder decoder, Configration conf) throws Exception;

	}

	/** 既定の読み方：オブジェクトは Data、配列は ArrayList（D-307） */
	private static final JsonReadOptions READ_OPTIONS = JsonReadOptions.builder()
		.mapFactory(Data::new)
		.build();

	/**
	 * 読んで、指定の型にする（decode の4つの入口の中身）
	 *
	 * @param conf			設定
	 * @param source		読み元
	 * @param destClasses	変換希望クラス
	 * @return	読めたもの（読めなければ null で、{@link #isError()} が true）
	 */
	@SuppressWarnings("unchecked")
	private <T> T decodeWith (Configration conf, TreeSource source, Class<?>... destClasses) {

		clearError();
		try {

			if (conf == null) {
				conf = new Configration();
			} else {
				conf.clearHashSet();
			}

			conf.isClearHashSet(false);

			if (destClasses.length == 0) {
				destClasses = new Class<?>[]{Data.class};
			}

			Object dest = legacyDecoder || conf.get(DecodeConfigKeys.DECODE_STRING_UNESCAPE) != null
				? source.readLegacy(legacyDecoder(), conf)
				: source.read(readOptions(conf));

			/*
			 * 一番外はオブジェクトか配列だけを受ける。
			 * 作り直す前は、文字から読むと例外、Reader から読むと黙って null で、入口で食い違っていた
			 */
			if (dest != null && !(dest instanceof Map) && !(dest instanceof List)) {
				throw new JsonParseException("JSON を読めませんでした（一番外がオブジェクトでも配列でもありません）: " + dest, null);
			}

			if (PropertyUtil.isAssignableFrom(Data.class, destClasses[0])) {
				T res = asData(dest);
				if (res != null) {
					return res;
				}
			}

			/*
			 * <b>読んだ木がそのまま指定の型なら、写さずに返す。</b>
			 * 作り直す前は List や Object を指定しても変換を通し、木をもう一度まるごと作っていた
			 */
			if (isAlreadyShaped(dest, destClasses)) {
				return (T) dest;
			}

			return Convertor.convert(conf, dest, destClasses);

		} catch (Exception e) {
			setError(e);
			return null;
		}

	}

	/**
	 * 読んだ木が、そのまま指定の型になっているか
	 *
	 * <p>木のオブジェクトは Data、配列は ArrayList。要素の型の指定があれば、要素も見る。</p>
	 */
	private static boolean isAlreadyShaped (Object dest, Class<?>[] destClasses) {

		if (dest == null || !destClasses[0].isInstance(dest)) {
			return false;
		}

		if (destClasses.length == 1) {
			return dest instanceof Data || dest instanceof java.util.ArrayList;
		}

		if (destClasses.length == 2 && dest instanceof java.util.ArrayList<?> list) {
			Class<?> element = destClasses[1];
			for (Object item : list) {
				if (item != null && !element.isInstance(item)) {
					return false;
				}
			}
			return element == Data.class || element == Object.class;
		}

		return false;

	}

	/**
	 * 古い読み手（必要になってから作る）
	 */
	private IDecoder legacyDecoder () {

		if (decoder == null) {
			decoder = new StreamDecoder();
		}
		return decoder;

	}

	/**
	 * 設定から、読み方を決める
	 */
	private static JsonReadOptions readOptions (Configration conf) {

		if (!conf.isOutputDateType()) {
			return READ_OPTIONS;
		}

		return READ_OPTIONS.toBuilder()
			.stringValueHook(s -> dateTypeValue(conf, s))
			.build();

	}

	/**
	 * 型つきの日付（{@code "date::..."}）を Date に戻す（{@link Configration#isOutputDateType()} のとき）
	 */
	private static Object dateTypeValue (Configration conf, String s) {

		if (!s.startsWith("date::")) {
			return s;
		}

		String text = s.substring(6);

		if (conf.outputDateTypeFormat() != null) {
			try {
				LocalDateTime d = LocalDateTime.parse(text, conf.outputDateTypeFormat());
				Calendar calendar = Calendar.getInstance();
				calendar.set(Calendar.YEAR, d.getYear());
				calendar.set(Calendar.MONTH, d.getMonth().ordinal());
				calendar.set(Calendar.DAY_OF_MONTH, d.getDayOfMonth());
				calendar.set(Calendar.HOUR_OF_DAY, d.getHour());
				calendar.set(Calendar.MINUTE, d.getMinute());
				calendar.set(Calendar.SECOND, d.getSecond());
				calendar.set(Calendar.MILLISECOND, 0);
				return calendar.getTime();
			} catch (Exception ignore) {
				// 書式が合わなければ、下の変換に任せる
			}
		}

		try {
			return Convertor.convert(null, text, Date.class);
		} catch (Exception ex) {
			return text;
		}

	}

	/* ***************************** エンコード. ******************************/

	/**
	 * JSONエンコーダ.
	 */
	private IEncoder encoder;

	/**
	 * オブジェクトからJSON文字列にエンコードする.
	 *
	 * @param json オブジェクト
	 * @return JSON文字列
	 */
	public static String encodes (Object json) {

		return encodeAndReport(new Dson(), dson -> dson.encode(json));
	}

	/**
	 * オブジェクトからJSON文字列にエンコードする.
	 *
	 * @param conf 設定情報
	 * @param json オブジェクト
	 * @return JSON文字列
	 */
	public static String encodes (Configration conf, Object json) {

		return encodeAndReport(new Dson(), dson -> dson.encode(conf, json));
	}

	/**
	 * 使い捨ての Dson で書き出し、失敗していたらログに残す（D-173）
	 *
	 * <p>
	 * <b>ここが黙って倒れる道だった。</b>{@code static} の {@code encodes} は
	 * その場で {@code Dson} を作って捨てるので、
	 * <b>中で記録された失敗を誰も読まない</b>——
	 * 呼んだ側に返るのは空文字だけで、{@code Data.getJsonString()} は
	 * それを <b>{@code {}}</b> に変える。
	 * </p>
	 *
	 * <p>
	 * つまり<b>API が 200 で空の JSON を返し、ログにも何も出ない</b>状態になっていた。
	 * 戻り値は変えられない（{@code String} で、呼ぶ側が大量にある）ので、
	 * <b>せめて理由をログに残す</b>。
	 * </p>
	 *
	 * @param dson		使い捨ての Dson
	 * @param encoder	書き出し
	 * @return	JSON 文字列（失敗したら空文字）
	 */
	private static void reportStream (java.util.function.Consumer<Dson> encoder) {

		/*
		 * 流す版も失敗を読む（要件 D-190）。1.4 までは String 版だけを D-173 で直していて、
		 * <b>Response.send(Data) が使う流す版は、失敗を誰も読んでいなかった</b>——
		 * 200 のまま本文が途中で切れ、ログにも何も出なかった。
		 * 書き先の失敗（相手が切った）は珍しくないので警告に留める。
		 */
		Dson dson = new Dson();
		encoder.accept(dson);

		if (dson.isError()) {
			Exception e = dson.getErrorException();
			if (e instanceof java.io.IOException || (e != null && e.getCause() instanceof java.io.IOException)) {
				Log.warn("JSON を書き出している途中で書き先が失敗しました（相手が切った可能性があります）: " + e.getMessage());
			} else {
				Log.error(e, "JSON に書き出せませんでした（本文が途中で切れています）");
			}
		}

	}

	private static String encodeAndReport (Dson dson, java.util.function.Function<Dson, String> encoder) {

		String result = encoder.apply(dson);

		if (dson.isError()) {
			Log.error(dson.getErrorException(), "JSON に書き出せませんでした（空の JSON が返ります）");
		}

		return result;

	}

	/**
	 * オブジェクトからJSON文字列にエンコードし出力する.<br>
	 * <b>書き終わると、渡した OutputStream を閉じます。</b>閉じたくなければ、{@code isAutoClose(false)} にした {@link Configration} を渡してください。
	 *
	 * @param json    オブジェクト
	 * @param stream  出力先
	 * @param charset 文字コード
	 */
	public static void encodes (Object json, OutputStream stream, String charset) {

		reportStream(dson -> dson.encode(json, stream, charset));
	}

	/**
	 * オブジェクトからJSON文字列にエンコードし出力する.<br>
	 * <b>書き終わると、渡した OutputStream を閉じます。</b>閉じたくなければ、{@code isAutoClose(false)} にした {@link Configration} を渡してください。
	 *
	 * @param conf    設定情報
	 * @param json    オブジェクト
	 * @param stream  出力先
	 * @param charset 文字コード
	 */
	public static void encodes (Configration conf, Object json, OutputStream stream, String charset) {

		reportStream(dson -> dson.encode(conf, json, stream, charset));
	}

	/**
	 * オブジェクトからJSON文字列にエンコードし出力する.<br>
	 * <b>書き終わると、渡した Writer を閉じます。</b>閉じたくなければ、{@code isAutoClose(false)} にした {@link Configration} を渡してください。
	 *
	 * @param json   オブジェクト
	 * @param writer 出力先
	 */
	public static void encodes (Object json, Writer writer) {

		reportStream(dson -> dson.encode(new Configration(), json, writer));
	}

	/**
	 * オブジェクトからJSON文字列にエンコードし出力する.<br>
	 * <b>書き終わると、渡した Writer を閉じます。</b>閉じたくなければ、{@code isAutoClose(false)} にした {@link Configration} を渡してください。
	 *
	 * @param conf   設定情報
	 * @param json   オブジェクト
	 * @param writer 出力先
	 */
	public static void encodes (Configration conf, Object json, Writer writer) {

		reportStream(dson -> dson.encode(conf, json, writer));
	}

	/**
	 * オブジェクトからJSON文字列にエンコードする.
	 *
	 * @param json オブジェクト
	 * @return JSON文字列
	 */
	public String encode (Object json) {

		clearError();
		return this.encoder.encode(new Configration(), json);
	}

	/**
	 * オブジェクトからJSON文字列にエンコードする.
	 *
	 * @param conf 設定情報
	 * @param json オブジェクト
	 * @return JSON文字列
	 */
	public String encode (Configration conf, Object json) {

		clearError();
		return this.encoder.encode(conf, json);
	}

	/**
	 * オブジェクトからJSON文字列にエンコードし出力する.<br>
	 * <b>書き終わると、渡した OutputStream を閉じます。</b>閉じたくなければ、{@code isAutoClose(false)} にした {@link Configration} を渡してください。
	 *
	 * @param json    オブジェクト
	 * @param stream  出力先
	 * @param charset 文字コード
	 */
	public void encode (Object json, OutputStream stream, String charset) {

		clearError();
		this.encoder.encode(new Configration(), json, stream, Charset.forName(charset));
	}

	/**
	 * オブジェクトからJSON文字列にエンコードし出力する.<br>
	 * <b>書き終わると、渡した OutputStream を閉じます。</b>閉じたくなければ、{@code isAutoClose(false)} にした {@link Configration} を渡してください。
	 *
	 * @param conf    設定情報
	 * @param json    オブジェクト
	 * @param stream  出力先
	 * @param charset 文字コード
	 */
	public void encode (Configration conf, Object json, OutputStream stream, String charset) {

		clearError();
		this.encoder.encode(conf, json, stream, Charset.forName(charset));
	}

	/**
	 * オブジェクトからJSON文字列にエンコードし出力する.<br>
	 * <b>書き終わると、渡した Writer を閉じます。</b>閉じたくなければ、{@code isAutoClose(false)} にした {@link Configration} を渡してください。
	 *
	 * @param json   オブジェクト
	 * @param writer 出力先
	 */
	public void encode (Object json, Writer writer) {

		clearError();
		this.encoder.encode(new Configration(), json, writer);
	}

	/**
	 * オブジェクトからJSON文字列にエンコードし出力する.<br>
	 * <b>書き終わると、渡した Writer を閉じます。</b>閉じたくなければ、{@code isAutoClose(false)} にした {@link Configration} を渡してください。
	 *
	 * @param conf   設定情報
	 * @param json   オブジェクト
	 * @param writer 出力先
	 */
	public void encode (Configration conf, Object json, Writer writer) {

		clearError();
		this.encoder.encode(conf, json, writer);
	}

	/**
	 * 文字列をJSONエスケープする
	 *
	 * @param src 文字列
	 * @return JSONエスケープされた文字列
	 */
	public static String escapeString (String src) {

		return StringFormatter.escape(src);

	}

}
