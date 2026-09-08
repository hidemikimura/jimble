package io.jimble.util.json;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.Convertor;
import io.jimble.util.convertor.PropertyUtil;
import io.jimble.util.json.decoder.IDecoder;
import io.jimble.util.json.decoder.StreamDecoder;
import io.jimble.util.json.encoder.DefaultEncoder;
import io.jimble.util.json.encoder.IEncoder;
import io.jimble.util.json.formatter.lang.StringFormatter;
import io.jimble.util.data.Data;

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

		return new Dson().decodes(conf, stream, charset, destClasses);
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

			conf.isClearHashSet = false;

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

			conf.isClearHashSet = false;

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

			conf.isClearHashSet = false;

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

			conf.isClearHashSet = false;

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

		return new Dson().encode(json);
	}

	/**
	 * オブジェクトからJSON文字列にエンコードする.
	 *
	 * @param conf 設定情報
	 * @param json オブジェクト
	 * @return JSON文字列
	 */
	public static String encodes (Configration conf, Object json) {

		return new Dson().encode(conf, json);
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
