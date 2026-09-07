package io.jimble.mcp;

import io.jimble.util.conf.Conf;

import java.util.List;

/**
 * MCP の設定
 *
 * <pre>
 * mcp {
 *   path            = "/mcp"
 *   allowed_origins = ["https://example.com"]   # 空なら Origin つきの要求を全部断る
 *   name            = "my-mcp"
 *   version         = "1.0.0"
 * }
 * </pre>
 */
public final class McpConf {

	/** 設定キー：エンドポイントのパス */
	public static final String KEY_PATH = "mcp.path";

	/** 設定キー：許すオリジン */
	public static final String KEY_ALLOWED_ORIGINS = "mcp.allowed_origins";

	/** 設定キー：サーバーの名前 */
	public static final String KEY_NAME = "mcp.name";

	/** 設定キー：サーバーの版 */
	public static final String KEY_VERSION = "mcp.version";

	/** 既定のパス */
	public static final String DEFAULT_PATH = "/mcp";

	private McpConf () {}

	/**
	 * エンドポイントのパス
	 *
	 * @return	パス
	 */
	public static String path () {

		return Conf.conf().getString(KEY_PATH, DEFAULT_PATH);

	}

	/**
	 * 許すオリジン
	 *
	 * <p>
	 * <b>空でも「全部許す」ではない。</b>
	 * 仕様は「{@code Origin} が付いていて、それが不正なら 403」と定めている
	 * （DNS リバインディング対策。MUST）。
	 * リストが空なら、<b>{@code Origin} が付いた要求はすべて断る。</b>
	 * ブラウザ以外（CLI・エージェント）は {@code Origin} を付けないので、そのまま通る。
	 * </p>
	 *
	 * @return	オリジン
	 */
	public static List<String> allowedOrigins () {

		return Conf.conf().getStringListOptional(KEY_ALLOWED_ORIGINS);

	}

	/**
	 * サーバーの名前
	 *
	 * @return	名前
	 */
	public static String name () {

		return Conf.conf().getString(KEY_NAME, "jimble");

	}

	/**
	 * サーバーの版
	 *
	 * @return	版
	 */
	public static String version () {

		return Conf.conf().getString(KEY_VERSION, "0.1.0");

	}

}
