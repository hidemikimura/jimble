package io.jimble.util.json.encoder;

import io.jimble.util.convertor.Configration;

import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.Charset;

/**
 * JSONエンコーダインターフェイス.
 * 
 * @author DN
 */
public interface IEncoder {

	/**
	 * エラー判定.
	 * 
	 * @return エラー
	 */
	public boolean isError();

	/**
	 * エラー詳細取得.
	 * 
	 * @return エラー詳細
	 */
	public Exception getErrorException();

	/**
	 * エラー初期化.
	 */
	public void clearError();

	/**
	 * オブジェクトをJSON文字列にエンコードする.
	 * 
	 * @param conf 設定情報
	 * @param value オブジェクト
	 * @return JSON文字列
	 */
	public String encode(Configration conf, Object value);

	/**
	 * オブジェクトをJSON文字列にエンコードし出力する.
	 * 
	 * @param conf 設定情報
	 * @param value オブジェクト
	 * @param stream 出力先
	 * @param charset 文字コード
	 * @return JSON文字列
	 */
	public void encode(Configration conf, Object value, OutputStream stream, Charset charset);

	/**
	 * オブジェクトをJSON文字列にエンコードし出力する.
	 * 
	 * @param conf 設定情報
	 * @param value オブジェクト
	 * @param writer 出力先
	 * @return JSON文字列
	 */
	public void encode(Configration conf, Object value, Writer writer);

}
