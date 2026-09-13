package io.jimble.web.http;

import io.jimble.util.convertor.UploadFile;

import java.io.InputStream;

import java.nio.charset.Charset;
import java.util.LinkedHashMap;
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
	 * <b>繋ぐ前の値がほしいときは {@link #headerValues()}</b>（1.1 で足した）。
	 * この2つを揃えるのは 2.0 である。
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
	 * <b>捨てたほうも見たいときは {@link #cookieValues()}</b>（1.1 で足した）。
	 * </p>
	 *
	 * @return	Cookie（生の値）
	 */
	Map<String, String> cookies ();

	/**
	 * ヘッダ（繋ぐ前）
	 *
	 * <p>
	 * <b>{@link #headers()} と同じ中身を、繋がずに並べたもの。</b>
	 * キーは小文字。{@code Accept: a, b} と {@code Accept: c} が2行で来たら
	 * {@code ["a, b", "c"]} である——<b>2行だったことが分かる</b>。
	 * </p>
	 *
	 * <p>
	 * <b>既定は {@link #headers()} を1要素ずつに包んだもの。</b>
	 * 繋いだあとの値しか持たない実装（{@code CallRequestSource} など）は
	 * これで正しい——嘘の分割をするより、<b>1行だったことにしておくほうが安全</b>である。
	 * 本当に2行来たことを知っているのは HTTP サーバー実装だけなので、
	 * そちらで上書きする。
	 * </p>
	 *
	 * @return	ヘッダ（値は来た行の順）
	 */
	default Map<String, List<String>> headerValues () {

		Map<String, List<String>> result = new LinkedHashMap<>();

		for (Map.Entry<String, String> entry : headers().entrySet()) {
			result.put(entry.getKey(), List.of(entry.getValue()));
		}

		return result;

	}

	/**
	 * Cookie（捨てる前）
	 *
	 * <p>
	 * <b>{@link #cookies()} が先頭1本だけ採るのに対して、こちらは全部並べる。</b>
	 * 同じ名前が2つ来るのは、<b>別のパスやドメインに同じ名前で置かれた</b>ときである。
	 * <b>セッション ID でこれが起きると、先頭がどちらかは Cookie の仕様では決まらない</b>——
	 * 気づけるように、{@code Cookies} が2本以上あればログに出す。
	 * </p>
	 *
	 * @return	Cookie（値は来た順。生の値）
	 */
	default Map<String, List<String>> cookieValues () {

		Map<String, List<String>> result = new LinkedHashMap<>();

		for (Map.Entry<String, String> entry : cookies().entrySet()) {
			result.put(entry.getKey(), List.of(entry.getValue()));
		}

		return result;

	}

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
