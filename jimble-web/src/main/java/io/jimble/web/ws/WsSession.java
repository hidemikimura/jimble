package io.jimble.web.ws;

import io.jimble.util.data.Data;
import io.jimble.util.log.Log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1本の接続（要件 F-W-22）
 *
 * <p>
 * helidon の型はここから外に出さない（要件 F-C-03 と同じ方針）。
 * </p>
 *
 * <h2>接続に紐づく情報はここに置く</h2>
 *
 * <p>
 * 「誰が繋いでいるか」「どの部屋にいるか」は<b>接続の持ち物</b>であって、
 * メッセージ1件の持ち物ではない。{@link #attributes()} に入れる。
 * </p>
 *
 * <p>
 * <b>{@code Context} に置いてはいけない。</b>
 * jimble はメッセージ1件ごとに Context を作って捨てる（D-56）ので、
 * Context に置いたものは次のメッセージには残らない。
 * </p>
 */
public final class WsSession {

	/* 出口（helidon はこの向こう側） */
	private final WsSink sink;

	/* 接続に紐づく情報 */
	private final Map<String, Object> attributes = new ConcurrentHashMap<>();

	/* アップグレード時のヘッダ */
	private final Data headers;

	/* 繋いできたパス */
	private final String path;

	/**
	 * コンストラクタ
	 *
	 * <p>
	 * <b>フレームワーク内部から呼ぶ。</b>
	 * 受け取るのは {@link WsSink} であって helidon の型ではない（D-173）——
	 * ここが helidon だと、<b>公開パッケージの署名に helidon が出る</b>。
	 * </p>
	 *
	 * @param sink		出口
	 * @param path		パス
	 * @param headers	アップグレード時のヘッダ
	 */
	public WsSession (WsSink sink, String path, Data headers) {

		this.sink = sink;
		this.path = path;
		this.headers = headers;

	}

	/**
	 * 文字を送る
	 *
	 * @param text	文字
	 * @return	送れた場合 = true
	 */
	public boolean send (String text) {

		try {

			return sink.send(text);

		} catch (Exception ex) {

			// 相手がもう居ない。よくあることなので騒がない
			Log.debug("WebSocket に送れませんでした: " + ex.getMessage());
			return false;

		}

	}

	/**
	 * JSON を送る
	 *
	 * @param data	中身
	 * @return	送れた場合 = true
	 */
	public boolean send (Data data) {

		return send(data == null ? "{}" : data.getJsonString());

	}

	/**
	 * 閉じる
	 *
	 * @param reason	理由
	 */
	public void close (String reason) {

		try {
			sink.close(reason);
		} catch (Exception ex) {
			Log.debug("WebSocket を閉じるときに切れていました: " + ex.getMessage());
		}

	}

	/**
	 * 接続に紐づく情報
	 *
	 * <p>
	 * <b>ここに置いたものは接続が切れるまで残る。</b>
	 * 認証したユーザー、参加している部屋など。
	 * </p>
	 *
	 * @return	情報
	 */
	public Map<String, Object> attributes () {

		return attributes;

	}

	/**
	 * 繋いできたパス
	 *
	 * @return	パス
	 */
	public String path () {

		return path;

	}

	/**
	 * アップグレード時のヘッダ
	 *
	 * <p>Cookie もここから読める。</p>
	 *
	 * @return	ヘッダ
	 */
	public Data headers () {

		return headers;

	}

	/**
	 * アップグレード時の Cookie
	 *
	 * @param name	名前
	 * @return	値。無ければ空文字
	 */
	public String cookie (String name) {

		String header = headers.getStringOptional("cookie");

		for (String part : header.split(";")) {

			String trimmed = part.trim();
			String prefix = name + "=";

			if (trimmed.startsWith(prefix)) {
				return trimmed.substring(prefix.length());
			}

		}

		return "";

	}

}
