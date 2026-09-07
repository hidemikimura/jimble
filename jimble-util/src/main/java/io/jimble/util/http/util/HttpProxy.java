package io.jimble.util.http.util;

/**
 * プロキシ情報
 */
public class HttpProxy {

	// region ホスト

	/* ホスト */
	public String host;

	/**
	 * ホストを設定する
	 *
	 * @param host	ホスト
	 */
	public HttpProxy setHost (String host) {

		this.host = host;
		return this;

	}

	// endregion

	// region ポート

	/* ポート */
	public int port;

	/**
	 * ポートを設定する
	 *
	 * @param port	ポート
	 */
	public HttpProxy setPort (int port) {

		this.port = port;
		return this;

	}

	// endregion

	// region 認証ID

	/* 認証ID */
	public String id;

	/**
	 * 認証IDを設定する
	 *
	 * @param id	認証ID
	 */
	public HttpProxy setId (String id) {

		this.id = id;
		return this;

	}

	// endregion

	// region 認証パスワード

	/* 認証パスワード */
	public String pass;

	/**
	 * 認証パスワードを設定する
	 *
	 * @param pass	認証パスワード
	 */
	public HttpProxy setPass (String pass) {

		this.pass = pass;
		return this;

	}

	// endregion


	// region コンストラクタ

	/**
	 * コンストラクタ
	 */
	public HttpProxy() {


	}

	/**
	 * コンストラクタ
	 *
	 * @param host	ホスト
	 * @param port	ポート
	 */
	public HttpProxy(String host, int port) {

		this.host = host;
		this.port = port;

	}

	/**
	 * コンストラクタ
	 *
	 * @param host	ホスト
	 * @param port	ポート
	 * @param id	認証ID
	 * @param pass	認証パスワード
	 */
	public HttpProxy(String host, int port, String id, String pass) {

		this.host = host;
		this.port = port;
		this.id = id;
		this.pass = pass;

	}

	// endregion

}
