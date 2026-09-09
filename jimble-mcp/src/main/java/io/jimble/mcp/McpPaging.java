package io.jimble.mcp;

import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * 一覧のページ分け（要件 F-MCP-14）
 *
 * <p>
 * {@code tools/list} / {@code resources/list} / {@code prompts/list} は、
 * 続きがあれば {@code nextCursor} を付けて返す。次の要求で {@code params.cursor} に
 * それを入れると続きが返る。
 * </p>
 *
 * <h2>大きさを決めるのはサーバーである</h2>
 * <p>
 * <b>クライアントは1ページの件数を指定できない</b>（仕様）。{@code mcp.page_size} で決める。
 * 既定は {@link #DEFAULT_PAGE_SIZE} 件で、それ以下しか登録していないアプリの応答は
 * <b>1 byte も変わらない</b>（{@code nextCursor} が付かない）。
 * </p>
 *
 * <h2>カーソルは中身を見せない</h2>
 * <p>
 * 仕様はカーソルを<b>不透明な文字列</b>と定めていて、クライアントは
 * 中身を読んだり組み立てたりしてはいけない。ここでは「何件目から」を
 * Base64 にしただけのものだが、<b>そう見えないようにしておく</b>のが約束である。
 * </p>
 *
 * <p>
 * <b>読めないカーソルは -32602 で断る</b>（仕様 SHOULD）。黙って先頭から返すと、
 * クライアントは<b>同じページを永遠に読み続ける</b>。
 * </p>
 */
public final class McpPaging {

	/** 設定キー：1ページの件数 */
	public static final String KEY_PAGE_SIZE = "mcp.page_size";

	/** 1ページの件数の既定 */
	public static final int DEFAULT_PAGE_SIZE = 100;

	/** カーソルの頭（これだけで jimble のものだと分かる） */
	private static final String PREFIX = "jimble:";

	private McpPaging () {
	}

	/**
	 * 1ページの件数
	 *
	 * @return 件数
	 */
	public static int pageSize () {

		int size = Conf.conf().getInt(KEY_PAGE_SIZE, DEFAULT_PAGE_SIZE);

		return size <= 0 ? DEFAULT_PAGE_SIZE : size;

	}

	/**
	 * 一覧を1ページぶんに切る
	 *
	 * @param all		全部
	 * @param cursor	続きの位置。先頭からなら null か空
	 * @return 切った結果
	 * @throws McpPagingException 読めないカーソルだったとき
	 */
	public static Page of (List<Data> all, String cursor) throws McpPagingException {

		int from = decode(cursor, all.size());
		int size = pageSize();
		int to = Math.min(from + size, all.size());

		List<Data> page = all.subList(from, to);

		return new Page(page, to < all.size() ? encode(to) : null);

	}

	/**
	 * 切った結果
	 *
	 * @param items			このページの中身
	 * @param nextCursor	続きのカーソル。続きが無ければ null
	 */
	public record Page(List<Data> items, String nextCursor) {
	}

	/**
	 * 読めないカーソル
	 */
	public static final class McpPagingException extends Exception {

		/**
		 * コンストラクタ
		 *
		 * @param message 内容
		 */
		McpPagingException (String message) {

			super(message);

		}

	}

	/**
	 * カーソルを組み立てる
	 *
	 * @param index 何件目から
	 * @return カーソル
	 */
	private static String encode (int index) {

		return Base64.getUrlEncoder().withoutPadding()
			.encodeToString((PREFIX + index).getBytes(StandardCharsets.UTF_8));

	}

	/**
	 * カーソルを読む
	 *
	 * @param cursor	カーソル
	 * @param total		全部の件数
	 * @return 何件目から
	 * @throws McpPagingException 読めなかったとき
	 */
	private static int decode (String cursor, int total) throws McpPagingException {

		if (cursor == null || cursor.isEmpty()) {
			return 0;
		}

		String decoded;

		try {
			decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException ex) {
			throw new McpPagingException("カーソルが読めません: " + cursor);
		}

		if (!decoded.startsWith(PREFIX)) {
			throw new McpPagingException("カーソルが読めません: " + cursor);
		}

		int index;

		try {
			index = Integer.parseInt(decoded.substring(PREFIX.length()));
		} catch (NumberFormatException ex) {
			throw new McpPagingException("カーソルが読めません: " + cursor);
		}

		/*
		 * <b>範囲の外は断る。</b>登録が減ったあとに古いカーソルで来ることがある。
		 * 黙って空を返すと、クライアントは<b>そこで終わったと思う</b>——
		 * 実際には先頭に残っているものが読まれていない。
		 */
		if (index < 0 || index > total) {
			throw new McpPagingException("カーソルの位置が範囲の外です: " + cursor);
		}

		return index;

	}

}
