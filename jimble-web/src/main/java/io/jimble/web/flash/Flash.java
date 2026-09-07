package io.jimble.web.flash;

import io.jimble.util.data.Data;
import io.jimble.web.cookie.Cookie;
import io.jimble.web.cookie.CookieConf;
import io.jimble.web.cookie.Cookies;

/**
 * Flash（次のリクエストにだけ残る値。要件 F-S-07）
 *
 * <p>
 * リダイレクト先に一度だけメッセージを渡すためのもの。Cookie に載る。
 * </p>
 *
 * <pre>
 * context.flash().put("message", "保存しました");
 * context.response().redirect("/items");
 * </pre>
 *
 * <h2>移送元から直したところ</h2>
 * <ol>
 *   <li><b>読んでも消えていなかった。</b>移送元は Cookie を失効させる処理が無く、
 *       消すまで毎リクエスト付いて回っていた。<b>「次リクエストにだけ残る」になっていない。</b>
 *       受け取った flash は、そのリクエストの応答で失効させる</li>
 *   <li><b>String 以外の put が Cookie を書いていなかった。</b>
 *       {@code put(String, int)} などは {@code data} に入れるだけで
 *       {@code setResponseCookie} を呼んでおらず、<b>黙って消えていた</b></li>
 * </ol>
 */
public final class Flash {

	/** Cookie 名の接頭辞 */
	public static final String PREFIX = "flash__";

	/* Cookie */
	private final Cookies cookies;

	/* 受け取った値 */
	private final Data data = new Data();

	/**
	 * コンストラクタ
	 *
	 * <p>
	 * 受け取った flash Cookie を読み、<b>同時に失効させる</b>。
	 * このあと {@link #put} で同じキーに書けば、そちらが優先される
	 * （{@link Cookies} は名前ごとに1つしか {@code Set-Cookie} を出さない）。
	 * </p>
	 *
	 * @param cookies	Cookie
	 */
	public Flash (Cookies cookies) {

		this.cookies = cookies;

		for (String name : cookies.data().keySet().toArray(new String[0])) {

			if (!name.startsWith(PREFIX)) {
				continue;
			}

			data.put(name.substring(PREFIX.length()), cookies.get(name));

			// 読んだら消える。これをしないと「次リクエストだけ」にならない
			cookies.remove(name);

		}

	}

	// region 取得

	/**
	 * 受け取った値
	 *
	 * @return	値
	 */
	public Data data () {

		return data;

	}

	/**
	 * 取得
	 *
	 * @param key	キー
	 * @return	値（無ければ空文字）
	 */
	public String get (String key) {

		return data.getStringOptional(key);

	}

	/**
	 * 取得
	 *
	 * @param key	キー
	 * @return	値
	 */
	public int getInt (String key) {

		return data.getInt(key);

	}

	/**
	 * 取得
	 *
	 * @param key	キー
	 * @return	値
	 */
	public long getLong (String key) {

		return data.getLong(key);

	}

	/**
	 * 取得
	 *
	 * @param key	キー
	 * @return	値
	 */
	public boolean getBoolean (String key) {

		return data.getBoolean(key);

	}

	/**
	 * あるか
	 *
	 * @param key	キー
	 * @return	ある場合 = true
	 */
	public boolean has (String key) {

		return data.containsKey(key);

	}

	// endregion

	// region 設定

	/**
	 * 次のリクエストに渡す
	 *
	 * @param key	キー
	 * @param value	値
	 */
	public void put (String key, String value) {

		data.put(key, value);

		// セッション Cookie（MAX_AGE_SESSION）にする。次のリクエストで読まれて消える
		Cookie cookie = CookieConf.create(PREFIX + key, Cookies.sign(value), Cookie.MAX_AGE_SESSION);
		cookies.put(cookie, value);

	}

	/**
	 * 次のリクエストに渡す
	 *
	 * @param key	キー
	 * @param value	値
	 */
	public void put (String key, int value) {

		put(key, String.valueOf(value));

	}

	/**
	 * 次のリクエストに渡す
	 *
	 * @param key	キー
	 * @param value	値
	 */
	public void put (String key, long value) {

		put(key, String.valueOf(value));

	}

	/**
	 * 次のリクエストに渡す
	 *
	 * @param key	キー
	 * @param value	値
	 */
	public void put (String key, boolean value) {

		put(key, String.valueOf(value));

	}

	/**
	 * 消す
	 *
	 * @param key	キー
	 */
	public void remove (String key) {

		data.remove(key);
		cookies.remove(PREFIX + key);

	}

	/**
	 * 全部消す
	 */
	public void clear () {

		for (String key : data.keySet().toArray(new String[0])) {
			remove(key);
		}

	}

	// endregion

}
