package io.jimble.web.http;

import io.jimble.util.convertor.UploadFile;

import java.io.InputStream;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

/**
 * HTTP サーバー実装からの入力口
 *
 * <p>
 * <b>helidon との接点をこの1つに閉じる。</b>
 * {@code Request} はここから値を取り出して自分（{@code Data}）に詰めるだけで、
 * HTTP サーバー実装の型を一切知らない。
 * </p>
 */
public interface RequestSource {

	/**
	 * HTTPメソッド
	 *
	 * @return	大文字のメソッド名
	 */
	String method ();

	/**
	 * パス（生。パーセントエンコードされたまま）
	 *
	 * <p><b>ルーティングはこちらを使う</b>（要件 F-R-23）。</p>
	 *
	 * @return	生のパス
	 */
	String rawPath ();

	/**
	 * パス（デコード済み）
	 *
	 * @return	パス
	 */
	String path ();

	/**
	 * リクエストURL
	 *
	 * @return	URL
	 */
	String url ();

	/**
	 * プロトコル
	 *
	 * @return	プロトコル
	 */
	String protocol ();

	/**
	 * スキーム
	 *
	 * @return	スキーム
	 */
	String scheme ();

	/**
	 * ホスト
	 *
	 * @return	ホスト
	 */
	String host ();

	/**
	 * ポート番号
	 *
	 * @return	ポート番号
	 */
	int port ();

	/**
	 * 接続元アドレス
	 *
	 * @return	アドレス
	 */
	String remoteAddress ();

	/**
	 * クエリ文字列
	 *
	 * @return	クエリ文字列
	 */
	String query ();

	/**
	 * ヘッダ
	 *
	 * <p>
	 * <b>キーは小文字。同じ名前が2行で来たら {@code ", "} で繋ぐ</b>（RFC 9110。D-173）。
	 * {@code Cookie} だけは中身の区切りに合わせて {@code "; "} で繋ぐ。
	 * </p>
	 *
	 * <p>
	 * <b>{@code Map<String, String>} である。</b>{@link #queryParams()} が
	 * {@code Map<String, List<String>>} なのと揃っていないが、
	 * <b>1.0 では型を変えられない</b>（この interface を実装しているアプリがあれば全部壊れる）。
	 * 多値をそのまま扱いたい場合は 2.0 で揃える。
	 * </p>
	 *
	 * @return	ヘッダ
	 */
	Map<String, String> headers ();

	/**
	 * Cookie
	 *
	 * <p>
	 * <b>同じ名前が2つ来たら、先頭だけを採る。</b>Cookie の名前は1つであるべきなので
	 * それでよいが、<b>2つ来ること自体は起こりうる</b>——
	 * 別のパスやドメインに同じ名前で置かれた場合である。
	 * <b>セッション ID でこれが起きると、どちらが自分のものか分からない。</b>
	 * </p>
	 *
	 * @return	Cookie（生の値）
	 */
	Map<String, String> cookies ();

	/**
	 * クエリパラメータ
	 *
	 * @return	クエリパラメータ
	 */
	Map<String, List<String>> queryParams ();

	/**
	 * フォームパラメータ
	 *
	 * @return	フォームパラメータ
	 */
	Map<String, List<String>> formParams ();

	/**
	 * アップロードファイル
	 *
	 * @return	アップロードファイル
	 */
	List<UploadFile> files ();

	/**
	 * 後始末をする
	 *
	 * <p>
	 * アップロードで作った一時ファイルを消す。
	 * <b>コンテキストのクローズ時に必ず呼ばれる。</b>
	 * </p>
	 *
	 * <p>
	 * これが無いと、アップロードのたびに一時ファイルが残ってディスクが埋まる。
	 * </p>
	 */
	default void cleanup () {

	}

	/**
	 * ボディ本文
	 *
	 * @param charset	文字コード
	 * @return	本文。取得できなければ空文字
	 */
	String bodyText (Charset charset);

	/**
	 * ボディのストリーム
	 *
	 * <p>
	 * <b>リバースプロキシのように、中身を解釈せずそのまま転送したいとき</b>に使う。
	 * {@link #bodyText(Charset)} と同じ本体なので、どちらか一方しか読めない。
	 * </p>
	 *
	 * @return	ストリーム（本体が無ければ空のストリーム）
	 */
	default InputStream bodyStream () {

		return InputStream.nullInputStream();

	}

}
