package io.jimble.web.cookie;

import io.jimble.util.crypto.KeyMatch;
import io.jimble.util.crypto.Signer;
import io.jimble.util.metrics.Metrics;
import io.jimble.util.data.Data;
import io.jimble.web.http.RequestSource;
import io.jimble.web.http.ResponseSink;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Cookie の読み書き（要件 F-S-05）
 *
 * <p>
 * 受信した Cookie は<b>署名を検証してから</b>渡す（{@code cookie.secret} がある場合）。
 * 検証に落ちた値は無かったことにする。生の値が要るときは {@link #raw()}。
 * </p>
 *
 * <h2>1つの名前につき Set-Cookie は1つ</h2>
 * <p>
 * 書き込みはいったん溜めて、<b>送信の直前に1回だけ</b>吐き出す（{@link #flush(ResponseSink)}）。
 * 同じ名前に何度書いても最後の1つだけが出る。
 * </p>
 * <p>
 * 移送元は {@code put()} のたびに {@code setResponseCookie} を呼んでいた。
 * Flash のように「消してから書く」処理で <b>同じ名前の Set-Cookie が2つ出て、
 * どちらが効くかブラウザ任せになっていた。</b>
 * </p>
 */
public final class Cookies {

	/* 受信した生の値 */
	private final Map<String, String> raw = new LinkedHashMap<>();

	/* 署名を検証した値 */
	private final Data verified = new Data();

	/* 送信予定（名前 → Cookie） */
	private final Map<String, Cookie> pending = new LinkedHashMap<>();

	/* 吐き出し済み */
	private boolean flushed = false;

	/* 古い鍵で読めた Cookie の名前（要件 NF-S-09） */
	private final Set<String> stale = new LinkedHashSet<>();

	/** メトリクス名：古い鍵で読めた Cookie の数 */
	public static final String METRIC_STALE = "cookie.stale_secret";

	/**
	 * 指標：署名の無い Cookie を通した回数（要件 D-159）
	 *
	 * <p>
	 * <b>これが 0 になるまで {@code cookie.accept_unsigned} を戻せない。</b>
	 * 数えないと、手順書に「しばらく待つ」としか書けない。
	 * </p>
	 */
	public static final String METRIC_UNSIGNED = "cookie.unsigned";

	/**
	 * コンストラクタ
	 *
	 * @param source	リクエスト
	 */
	public Cookies (RequestSource source) {

		if (source == null) {
			return;
		}

		boolean signed = CookieConf.isSigned();

		/*
		 * <b>鍵の並びは、要るときまで作らない。</b>
		 * Cookie が1つも来ないリクエストは多く、そこで毎回1つ作ると
		 * <b>1リクエストあたりの割り当てが黙って増える</b>（要件 NF-P-06）。
		 */
		List<String> secrets = null;

		for (Map.Entry<String, String> entry : source.cookies().entrySet()) {

			/*
			 * <b>いちばん先に元へ戻す</b>（{@link CookieValue}）。
			 * 署名は<b>符号化する前の値</b>に対して付いているので、
			 * ここで戻しておかないと、日本語を含む Cookie が<b>全部「改ざん」に見える</b>。
			 */
			String value = CookieValue.decode(entry.getValue());

			raw.put(entry.getKey(), value);

			if (!signed) {
				verified.put(entry.getKey(), value);
				continue;
			}

			if (secrets == null) {
				secrets = CookieConf.secrets();
			}

			/*
			 * 鍵を順に試す（要件 NF-S-09）。
			 * <b>先頭が「いま書くのに使う鍵」</b>で、残りは入れ替え前の古い鍵。
			 */
			KeyMatch match = Signer.unsignAny(value, secrets);

			if (match == null) {

				/*
				 * <b>署名を入れた直後は、まだ署名の無い Cookie が届く</b>（要件 D-159）。
				 * {@code cookie.accept_unsigned = true} の間だけ、そのまま通す——
				 * <b>書くほうは最初から署名する</b>ので、放っておけば入れ替わる。
				 */
				if (CookieConf.acceptUnsigned()) {
					verified.put(entry.getKey(), value);
					Metrics.count(METRIC_UNSIGNED);
				}

				// 検証に落ちた値は入れない。改ざんされた値をアプリに渡さないため
				continue;

			}

			verified.put(entry.getKey(), match.value());

			if (match.isStale()) {

				stale.add(entry.getKey());

				/*
				 * <b>数えておく。</b>これが 0 になるまで古い鍵を捨てられない。
				 * 数えないと、手順書に「しばらく待つ」としか書けなくなる
				 */
				Metrics.count(METRIC_STALE);

			}

		}

	}

	// region 取得

	/**
	 * 署名を検証した Cookie
	 *
	 * @return	Cookie
	 */
	public Data data () {

		return verified;

	}

	/**
	 * 署名を検証した Cookie
	 *
	 * @param name	名前
	 * @return	値（無ければ空文字）
	 */
	public String get (String name) {

		return verified.getStringOptional(name);

	}

	/**
	 * 受信した生の値
	 *
	 * <p>署名を検証していない。<b>そのままアプリのロジックに使わないこと。</b></p>
	 *
	 * @return	生の値
	 */
	public Map<String, String> raw () {

		return raw;

	}

	/**
	 * 受信した生の値
	 *
	 * @param name	名前
	 * @return	値（無ければ null）
	 */
	public String raw (String name) {

		return raw.get(name);

	}

	/**
	 * 古い鍵で読めたか（要件 NF-S-09）
	 *
	 * <p>
	 * <b>true なら、今の鍵で書き直さないと入れ替えが終わらない。</b>
	 * 枠組みが発行する {@code sid} と {@code csrf_token} は自分で書き直すが、
	 * <b>アプリが自分で書いた Cookie は自分で書き直すしかない</b>——
	 * 有効期限をいくつにすべきかは、書いた側にしか分からないためである
	 * （ブラウザは有効期限を送ってこない）。
	 * </p>
	 *
	 * <pre>
	 * if (context.cookies().isStale("mine")) {
	 *     context.cookies().put("mine", context.cookies().get("mine"), 30 * 24 * 60 * 60);
	 * }
	 * </pre>
	 *
	 * @param name	名前
	 * @return	古い鍵で読めた場合 = true
	 */
	public boolean isStale (String name) {

		return stale.contains(name);

	}

	/**
	 * 古い鍵で読めた Cookie の名前（要件 NF-S-09）
	 *
	 * @return	名前
	 */
	public Set<String> staleNames () {

		return Set.copyOf(stale);

	}

	// endregion

	// region 設定

	/**
	 * 書き込む
	 *
	 * @param name	名前
	 * @param value	値
	 */
	public void put (String name, String value) {

		put(CookieConf.create(name, sign(value)), value);

	}

	/**
	 * 書き込む
	 *
	 * @param name		名前
	 * @param value		値
	 * @param maxAge	有効秒数
	 */
	public void put (String name, String value, long maxAge) {

		put(CookieConf.create(name, sign(value), maxAge), value);

	}

	/**
	 * 書き込む
	 *
	 * <p>
	 * <b>値は署名しない。</b>属性を細かく決めたいときに使う。
	 * 署名が要るなら {@link #sign(String)} を通してから渡すこと。
	 * </p>
	 *
	 * @param cookie	Cookie
	 */
	public void put (Cookie cookie) {

		put(cookie, cookie.value());

	}

	/**
	 * 書き込む
	 *
	 * <p>
	 * <b>書いた値はこのリクエストの中で読み返せる</b>（{@link #get(String)}）。
	 * 受信した Cookie しか見えないと、同じリクエストで発行した CSRF トークンを
	 * 読み直すたびに新しいものが出てしまう。
	 * </p>
	 *
	 * @param cookie		Cookie（値は署名済みでよい）
	 * @param plainValue	署名を外した値。読み返しに使う
	 */
	public void put (Cookie cookie, String plainValue) {

		pending.put(cookie.name(), cookie);

		if (cookie.value() == null) {
			verified.remove(cookie.name());
		} else {
			verified.put(cookie.name(), plainValue);
		}

	}

	/**
	 * 消す
	 *
	 * @param name	名前
	 */
	public void remove (String name) {

		put(CookieConf.create(name, null, Cookie.MAX_AGE_DELETE));

	}

	/**
	 * 受信した Cookie を全部消す
	 */
	public void clear () {

		for (String name : raw.keySet()) {
			remove(name);
		}

	}

	/**
	 * 設定に従って署名する
	 *
	 * @param value	値
	 * @return	署名つきの値（鍵が無ければそのまま）
	 */
	public static String sign (String value) {

		if (value == null || !CookieConf.isSigned()) {
			return value;
		}

		// 書くときは必ず「いまの鍵」。古い鍵で書いたら入れ替えが終わらない
		return Signer.sign(value, CookieConf.secret());

	}

	// endregion

	// region 送信

	/**
	 * 溜めた Cookie を {@code Set-Cookie} として書き出す
	 *
	 * <p>2回目以降は何もしない。</p>
	 *
	 * @param sink	出力口
	 */
	public void flush (ResponseSink sink) {

		if (flushed || pending.isEmpty()) {
			flushed = true;
			return;
		}

		flushed = true;

		for (Cookie cookie : pending.values()) {
			sink.addHeader("Set-Cookie", cookie.toSetCookie());
		}

	}

	/**
	 * 送信予定の Cookie（テスト用）
	 *
	 * @return	名前 → Cookie
	 */
	public Map<String, Cookie> pending () {

		return pending;

	}

	// endregion

}
