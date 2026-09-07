package io.jimble.util.json.decoder;

import io.jimble.util.convertor.Configration;

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;

/**
 * JSONデコーダインターフェイス.
 * 
 * @author DN
 */
public interface IDecoder {

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
	 * JSON文字列をオブジェクトにデコードする.
	 * 
	 * @param conf 設定情報
	 * @param value JSON文字列
	 * @return オブジェクト
	 */
	public Object decode(Configration conf, String value);

	/**
	 * JSON文字列をオブジェクトにデコードする.
	 * 
	 * @param conf 設定情報
	 * @param reader 入力リーダー
	 * @return オブジェクト
	 */
	public Object decode(Configration conf, Reader reader);

	/**
	 * JSON文字列をオブジェクトにデコードする.
	 * 
	 * @param conf 設定情報
	 * @param stream 入力ストリーム
	 * @param charset 文字コード
	 * @return オブジェクト
	 * @throws Exception 例外
	 */
	public Object decode(Configration conf, InputStream stream, Charset charset) throws Exception;

	/**
	 * JSON文字列をオブジェクトにデコードする.
	 * 
	 * @param conf 設定情報
	 * @param file JSONファイル
	 * @param charset 文字コード
	 * @return オブジェクト
	 * @throws Exception 例外
	 */
	public Object decode(Configration conf, File file, Charset charset) throws Exception;

}
