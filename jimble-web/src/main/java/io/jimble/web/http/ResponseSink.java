package io.jimble.web.http;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;

/**
 * HTTP サーバー実装への出力口
 *
 * <p>
 * <b>helidon との接点をこの1つに閉じる。</b>
 * </p>
 *
 * <p>
 * 送信は1回だけ。送信済みの状態で再度送信すると実装側が例外を投げる。
 * </p>
 */
public interface ResponseSink {

	/**
	 * 送信済みか
	 *
	 * @return	送信済みなら true
	 */
	boolean isSent ();

	/**
	 * ステータスコードを設定する
	 *
	 * @param statusCode	ステータスコード
	 */
	void status (int statusCode);

	/**
	 * ヘッダを設定する（同名があれば置き換える）
	 *
	 * @param name	ヘッダ名
	 * @param value	値
	 */
	void header (String name, String value);

	/**
	 * ヘッダを足す（同名を置き換えない）
	 *
	 * <p>
	 * {@code Set-Cookie} のように<b>同じ名前で複数返す必要があるヘッダ</b>のためにある。
	 * </p>
	 *
	 * @param name	ヘッダ名
	 * @param value	値
	 */
	void addHeader (String name, String value);

	/**
	 * ボディなしで送信する
	 */
	void send ();

	/**
	 * テキストを送信する
	 *
	 * @param text	テキスト
	 */
	void send (String text);

	/**
	 * バイト列を送信する
	 *
	 * @param body	バイト列
	 */
	void send (byte[] body);

	/**
	 * ストリームを送信する（長さ不明）
	 *
	 * @param stream	ストリーム
	 */
	default void send (InputStream stream) {

		send(stream, -1L);

	}

	/**
	 * テキストを文字コードを指定して送信する
	 *
	 * @param text		テキスト
	 * @param charset	文字コード
	 */
	default void send (String text, java.nio.charset.Charset charset) {

		send(text.getBytes(charset));

	}

	/**
	 * ヘッダを設定する（数値）
	 *
	 * @param name	ヘッダ名
	 * @param value	値
	 */
	default void header (String name, long value) {

		header(name, String.valueOf(value));

	}

	/**
	 * ストリームを送信する
	 *
	 * @param stream		ストリーム
	 * @param contentLength	長さ。不明なら -1
	 */
	void send (InputStream stream, long contentLength);

	/**
	 * ファイルをダウンロードさせる
	 *
	 * @param path		ファイル
	 * @param fileName	ダウンロード時のファイル名。null なら実ファイル名
	 */
	void sendFile (Path path, String fileName);

	/**
	 * リダイレクトする
	 *
	 * @param url	遷移先
	 */
	void redirect (String url);

	/**
	 * 出力ストリーム
	 *
	 * @return	出力ストリーム
	 */
	OutputStream outputStream ();

}
