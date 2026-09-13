package io.jimble.util.json;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.Convertor;
import io.jimble.util.convertor.PropertyUtil;
import io.jimble.util.internal.json.decoder.IDecoder;
import io.jimble.util.internal.json.decoder.StreamDecoder;
import io.jimble.util.internal.json.encoder.DefaultEncoder;
import io.jimble.util.internal.json.encoder.IEncoder;
import io.jimble.util.internal.json.formatter.lang.StringFormatter;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Map;

/**
 * JSONユーティリティクラス.<br>
 * ※このクラスはJava5.0以降のバーションで使用してください.
 *
 * @author DN
 */
public final class Dson {

	/* ***************************** コンストラクタ. ******************************/

	/**
	 * コンストラクタ.
	 */
	public Dson () {
		// JSONエンコーダーを保持する
		this.encoder = new DefaultEncoder();
		// JSONデコーダーを保持する
		this.decoder = new StreamDecoder();
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
		// JSONデコーダーを保持する
		this.decoder = decoder == null ? new StreamDecoder() : decoder;
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

		return error || this.encoder.isError() || this.decoder.isError();
	}

	/**
	 * エラーを取得する.
	 *
	 * @return エラー
	 */
	public Exception getErrorException () {

		return error ? exception : this.encoder.isError() ? this.encoder.getErrorException() : this.decoder.isError() ? this.decoder.getErrorException() : null;
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
		this.decoder.clearError();
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

	/* ***************************** デコード. ******************************/

	/**
	 * JSONデコーダ.
	 */
	private IDecoder decoder;

	/**
	 * JSONファイルをオブジェクトにデコードする.
	 *
	 * @param file        JSONファイル
	 * @param charset     文字コード
	 * @param destClasses 変換希望クラス([0]=変換希望クラス、[1...]=ジェネリクス指定)
	 * @return デコード結果
	 */
	public static <T> T decodes (File file, String charset, Class<?>... destClasses) {

		return new Dson().decode(file, charset, destClasses);
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

		return new Dson().decode(conf, file, charset, destClasses);
	}

	/**
	 * JSON文字ストリームをオブジェクトにデコードする.<br>
	 * JSON文字ストリームは自動でcloseされません。呼び出し元でcloseを行なってください.
	 *
	 * @param reader      JSON文字ストリーム
	 * @param destClasses 変換希望クラス([0]=変換希望クラス、[1...]=ジェネリクス指定)
	 * @return デコード結果
	 */
	public static <T> T decodes (Reader reader, Class<?>... destClasses) {

		return new Dson().decode(reader, destClasses);
	}

	/**
	 * JSON文字ストリームをオブジェクトにデコードする.<br>
	 * JSON文字ストリームは自動でcloseされません。呼び出し元でcloseを行なってください.
	 *
	 * @param conf        設定情報
	 * @param reader      JSON文字ストリーム
	 * @param destClasses 変換希望クラス([0]=変換希望クラス、[1...]=ジェネリクス指定)
	 * @return デコード結果
	 */
	public static <T> T decodes (Configration conf, Reader reader, Class<?>... destClasses) {

		return new Dson().decode(conf, reader, destClasses);
	}

	/**
	 * JSONストリームをオブジェクトにデコードする.<br>
	 * JSONストリームは自動でcloseされません。呼び出し元でcloseを行なってください.
	 *
	 * @param stream      JSONストリーム
	 * @param charset     文字コード
	 * @param destClasses 変換希望クラス([0]=変換希望クラス、[1...]=ジェネリクス指定)
	 * @return デコード結果
	 */
	public <T> T decodes (InputStream stream, String charset, Class<?>... destClasses) {

		return new Dson().decode(stream, charset, destClasses);
	}

	/**
	 * JSONストリームをオブジェクトにデコードする.<br>
	 * JSONストリームは自動でcloseされません。呼び出し元でcloseを行なってください.
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
		return new Dson().decode(conf, stream, charset, destClasses);
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

		return new Dson().decode(value, destClass);

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

		return new Dson().decode(conf, value, destClass);

	}

	/**
	 * JSON文字列をオブジェクトにデコードする.
	 *
	 * @param value       JSON文字列
	 * @param destClasses 変換希望クラス([0]=変換希望クラス、[1...]=ジェネリクス指定)
	 * @return デコード結果
	 */
	public static <T> T decodes (String value, Class<?>... destClasses) {

		return new Dson().decode(value, destClasses);
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

		return new Dson().decode(conf, value, destClasses);
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

		clearError();
		try {

			if (conf == null) {
				// 設定情報が指定されていない場合、デフォルト状態の設定情報を作成する
				conf = new Configration();
			} else {
				conf.clearHashSet();
			}

			conf.isClearHashSet(false);

			if (destClasses.length == 0) {
				// デコード指定がない場合、デコード指定を「Map」にする
				destClasses = new Class<?>[]{Data.class};
			}

			Object dest = this.decoder.decode(conf, file, Charset.forName(charset));
			if (PropertyUtil.isAssignableFrom(Data.class, destClasses[0])) {
				T res = asData(dest);
				if (res != null) {
					return res;
				}
			}

			return Convertor.convert(conf, dest, destClasses);

		} catch (Exception e) {
			setError(e);
			return null;
		}
	}

	/**
	 * JSON文字ストリームをオブジェクトにデコードする.<br>
	 * JSON文字ストリームは自動でcloseされません。呼び出し元でcloseを行なってください.
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
	 * JSON文字ストリームは自動でcloseされません。呼び出し元でcloseを行なってください.
	 *
	 * @param conf        設定情報
	 * @param reader      JSON文字ストリーム
	 * @param destClasses 変換希望クラス([0]=変換希望クラス、[1...]=ジェネリクス指定)
	 * @return デコード結果
	 */
	public <T> T decode (Configration conf, Reader reader, Class<?>... destClasses) {

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

			Object dest = this.decoder.decode(conf, reader);
			if (PropertyUtil.isAssignableFrom(Data.class, destClasses[0])) {
				T res = asData(dest);
				if (res != null) {
					return res;
				}
			}

			return Convertor.convert(conf, dest, destClasses);

		} catch (Exception e) {
			setError(e);
			return null;
		}
	}

	/**
	 * JSONストリームをオブジェクトにデコードする.<br>
	 * JSONストリームは自動でcloseされません。呼び出し元でcloseを行なってください.
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
	 * JSONストリームは自動でcloseされません。呼び出し元でcloseを行なってください.
	 *
	 * @param conf        設定情報
	 * @param stream      JSONフストリーム
	 * @param charset     文字コード
	 * @param destClasses 変換希望クラス([0]=変換希望クラス、[1...]=ジェネリクス指定)
	 * @return デコード結果
	 */
	public <T> T decode (Configration conf, InputStream stream, String charset, Class<?>... destClasses) {

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

			Object dest = this.decoder.decode(conf, stream, Charset.forName(charset));
			if (PropertyUtil.isAssignableFrom(Data.class, destClasses[0])) {
				T res = asData(dest);
				if (res != null) {
					return res;
				}
			}

			return Convertor.convert(conf, dest, destClasses);

		} catch (Exception e) {
			setError(e);
			return null;
		}
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

			Object dest = this.decoder.decode(conf, json);
			if (PropertyUtil.isAssignableFrom(Data.class, destClasses[0])) {
				T res = asData(dest);
				if (res != null) {
					return res;
				}
			}

			return Convertor.convert(conf, dest, destClasses);

		} catch (Exception e) {
			setError(e);
			return null;
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
	private static String encodeAndReport (Dson dson, java.util.function.Function<Dson, String> encoder) {

		String result = encoder.apply(dson);

		if (dson.isError()) {
			Log.error(dson.getErrorException(), "JSON に書き出せませんでした（空の JSON が返ります）");
		}

		return result;

	}

	/**
	 * オブジェクトからJSON文字列にエンコードし出力する.<br>
	 * ストリームは自動でcloseされません。呼び出し元でcloseを行なってください.
	 *
	 * @param json    オブジェクト
	 * @param stream  出力先
	 * @param charset 文字コード
	 */
	public static void encodes (Object json, OutputStream stream, String charset) {

		new Dson().encode(json, stream, charset);
	}

	/**
	 * オブジェクトからJSON文字列にエンコードし出力する.<br>
	 * ストリームは自動でcloseされません。呼び出し元でcloseを行なってください.
	 *
	 * @param conf    設定情報
	 * @param json    オブジェクト
	 * @param stream  出力先
	 * @param charset 文字コード
	 */
	public static void encodes (Configration conf, Object json, OutputStream stream, String charset) {

		new Dson().encode(conf, json, stream, charset);
	}

	/**
	 * オブジェクトからJSON文字列にエンコードし出力する.<br>
	 * 文字ストリームは自動でcloseされません。呼び出し元でcloseを行なってください.
	 *
	 * @param json   オブジェクト
	 * @param writer 出力先
	 */
	public static void encodes (Object json, Writer writer) {

		new Dson().encode(new Configration(), json, writer);
	}

	/**
	 * オブジェクトからJSON文字列にエンコードし出力する.<br>
	 * 文字ストリームは自動でcloseされません。呼び出し元でcloseを行なってください.
	 *
	 * @param conf   設定情報
	 * @param json   オブジェクト
	 * @param writer 出力先
	 */
	public static void encodes (Configration conf, Object json, Writer writer) {

		new Dson().encode(conf, json, writer);
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
	 * ストリームは自動でcloseされません。呼び出し元でcloseを行なってください.
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
	 * ストリームは自動でcloseされません。呼び出し元でcloseを行なってください.
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
	 * 文字ストリームは自動でcloseされません。呼び出し元でcloseを行なってください.
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
	 * 文字ストリームは自動でcloseされません。呼び出し元でcloseを行なってください.
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
