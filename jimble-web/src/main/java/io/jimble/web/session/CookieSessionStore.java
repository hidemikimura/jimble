package io.jimble.web.session;

import io.jimble.util.crypto.Aead;
import io.jimble.util.crypto.KeyMatch;
import io.jimble.util.metrics.Metrics;
import io.jimble.util.data.Data;
import io.jimble.util.json.Dson;
import io.jimble.web.context.WebContext;
import io.jimble.web.cookie.Cookie;
import io.jimble.web.cookie.CookieConf;
import io.jimble.web.cookie.Cookies;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Cookie セッション（要件 F-S-01 / F-S-08）
 *
 * <p>
 * サーバー側に何も置かない。中身を <b>AES-256-GCM で暗号化</b>して Cookie 1つに載せる。
 * 暗号化と改ざん検知が同時に効く（{@link Aead}）。
 * </p>
 *
 * <h2>移送元から変えたところ</h2>
 * <ol>
 *   <li><b>暗号化していなかった。</b>移送元は {@code session__<キー>} という Cookie に
 *       値をそのまま入れていた。署名はされても<b>中身は誰でも読める。</b>
 *       要件 F-S-08 は「署名と暗号化の両方」なので、まとめて1つの暗号文にした</li>
 *   <li><b>キーごとに Cookie を作っていた。</b>項目が増えるほど Cookie が増え、
 *       消すときも1つずつだった。1つの Cookie にまとめた</li>
 *   <li><b>サイズ上限が無かった。</b>Cookie は 4KB を超えるとブラウザに黙って捨てられる。
 *       超えたら {@link IllegalStateException} で落とす（要件 F-S-08「超過時は明確に失敗させる」）</li>
 * </ol>
 *
 * <h2>有効期限をサーバー側でも見る（D-209）</h2>
 * <p>
 * <b>2.2.2 までは、暗号文の中に時刻が無かった。</b>{@code session.timeout} は Cookie の {@code Max-Age} に
 * なるだけで、それを守るのはブラウザである。<b>1度盗まれた Cookie は、ログアウトしても、
 * タイムアウトを過ぎても、いつまでも使えた</b>（ログアウトは削除の Set-Cookie を送るだけ）。
 * </p>
 * <p>
 * いまは暗号文の中に<b>発行した時刻（{@code iat}）と最後に使った時刻（{@code seen}）</b>を入れ、読むときに
 * {@code seen} から {@code session.timeout}、{@code iat} から {@code session.absolute_timeout}（既定 1 日）を
 * 過ぎたものを捨てる。<b>時刻の無い（2.2.2 までの）Cookie も捨てる</b>——いつ発行されたものか分からないので、
 * 盗まれたものと見分けられない。
 * </p>
 * <p>
 * それでも、<b>ログアウトした瞬間に、盗まれた写しまで無効にはできない</b>（サーバーに何も置かないため）。
 * 最後に使ってから {@code session.timeout} までは使える。すぐに無効にしたいなら
 * {@code store = "db"} か {@code "redis"} にするか、{@code Auth.revoke} を使う。
 * </p>
 */
public final class CookieSessionStore implements SessionStore {

	/** Cookie 名 */
	public static final String COOKIE_NAME = "session";

	/**
	 * 値の上限（バイト）
	 *
	 * <p>Cookie 1つの上限は 4096 バイト。名前と属性の分を引いて余裕を見る。</p>
	 */
	public static final int MAX_VALUE_BYTES = 3800;

	/** メトリクス名：古い鍵で読めたセッションの数（要件 NF-S-09） */
	public static final String METRIC_STALE = "session.stale_secret";

	/* タイムアウト（分） */
	private final long timeoutMinutes;

	/* 発行からの上限（ミリ秒） */
	private final long absoluteTimeoutMillis;

	/** 暗号文の中身の形の版（D-209） */
	static final int FORMAT = 2;

	/**
	 * 最後に使った時刻を書き直す間隔（ミリ秒）
	 *
	 * <p>読むだけのリクエストのたびに暗号化し直さないため。これより短い間は {@code Max-Age} だけ延ばす。</p>
	 */
	static final long SEEN_RESOLUTION_MILLIS = 60_000;

	/* 時計（テストで差し替える） */
	java.util.function.LongSupplier clock = System::currentTimeMillis;

	/* 暗号鍵（先頭が「いま書くのに使う鍵」。要件 NF-S-09） */
	private final List<String> secrets;

	/**
	 * コンストラクタ（設定から作る）
	 */
	public CookieSessionStore () {

		this(SessionConf.secrets(), SessionConf.timeout().toMinutes(), SessionConf.absoluteTimeout());

	}

	/**
	 * コンストラクタ
	 *
	 * @param secret			暗号鍵
	 * @param timeoutMinutes	タイムアウト（分）
	 */
	public CookieSessionStore (String secret, long timeoutMinutes) {

		this(secret == null || secret.isEmpty() ? List.of() : List.of(secret), timeoutMinutes);

	}

	/**
	 * コンストラクタ
	 *
	 * @param secrets			暗号鍵（<b>先頭が「いま書くのに使う鍵」</b>。要件 NF-S-09）
	 * @param timeoutMinutes	タイムアウト（分）
	 */
	public CookieSessionStore (List<String> secrets, long timeoutMinutes) {

		this(secrets, timeoutMinutes, SessionConf.DEFAULT_ABSOLUTE_TIMEOUT);

	}

	/**
	 * コンストラクタ
	 *
	 * @param secrets			暗号鍵（<b>先頭が「いま書くのに使う鍵」</b>。要件 NF-S-09）
	 * @param timeoutMinutes	タイムアウト（分。最後に使ってから）
	 * @param absoluteTimeout	発行からの上限（使い続けても延びない。D-209）
	 */
	public CookieSessionStore (List<String> secrets, long timeoutMinutes, java.time.Duration absoluteTimeout) {

		if (secrets == null || secrets.isEmpty()) {
			throw new IllegalStateException(
				"Cookie セッションには暗号鍵が要ります（%s）".formatted(SessionConf.KEY_SECRET));
		}

		this.secrets = List.copyOf(secrets);
		this.timeoutMinutes = timeoutMinutes;
		this.absoluteTimeoutMillis = absoluteTimeout.toMillis();

	}

	// region SessionStore

	@Override
	public SessionEntry load (WebContext context) {

		String encrypted = context.cookies().get(COOKIE_NAME);
		if (encrypted == null || encrypted.isEmpty()) {
			return SessionEntry.empty();
		}

		/*
		 * 鍵を順に試す（要件 NF-S-09）。
		 * 改ざんされていたら null。空の新規セッションとして扱う
		 */
		KeyMatch match = Aead.decryptAny(encrypted, secrets);

		if (match == null) {
			return SessionEntry.empty();
		}

		Envelope envelope = open(match.value());

		if (envelope == null) {
			// 期限切れか、時刻の無い 2.2.2 までの Cookie。空の新規セッションとして扱う
			return SessionEntry.empty();
		}

		if (match.isStale()) {

			// 数えておく。0 になるまで古い鍵を捨てられない
			Metrics.count(METRIC_STALE);

			/*
			 * <b>読んだその場で、今の鍵で包み直す。</b>
			 * {@code save()} を待たない——セッションを<b>読むだけ</b>のページでは
			 * 保存が呼ばれないので、待っていると<b>その人はずっと古い鍵のまま</b>になる。
			 * 中身は変えないので、アプリから見た振る舞いは変わらない
			 */
			write(context, envelope.data(), envelope.issuedAt(), envelope.seen());

		}

		return new SessionEntry(envelope.data(), true, envelope.issuedAt());

	}

	@Override
	public void save (WebContext context, SessionEntry entry) {

		if (entry.data().isEmpty()) {
			destroy(context);
			return;
		}

		long now = clock.getAsLong();

		// 発行した時刻は引き継ぐ。ID を作り直したとき（ログイン）は既存ではないので、いまから数え直す
		long issuedAt = entry.isExisting() && entry.issuedAt() > 0 ? entry.issuedAt() : now;

		write(context, entry.data(), issuedAt, now);

	}

	/**
	 * 今の鍵で暗号化して書く
	 *
	 * @param context	コンテキスト
	 * @param data		中身
	 * @param issuedAt	発行した時刻
	 * @param seen		最後に使った時刻
	 */
	private void write (WebContext context, Data data, long issuedAt, long seen) {

		Data envelope = new Data();
		envelope.put("v", FORMAT);
		envelope.put("iat", issuedAt);
		envelope.put("seen", seen);
		envelope.put("data", data);

		// 書くときは必ず先頭の鍵。古い鍵で書いたら入れ替えが終わらない
		put(context, Aead.encrypt(Dson.encodes(envelope), secrets.get(0)));

	}

	/**
	 * 暗号文の中身
	 *
	 * @param data		中身
	 * @param issuedAt	発行した時刻
	 * @param seen		最後に使った時刻
	 */
	record Envelope (Data data, long issuedAt, long seen) {}

	/**
	 * 中身を読み、期限を確かめる（D-209）
	 *
	 * @param json	復号した中身
	 * @return	中身。期限切れか、時刻の無い（2.2.2 までの）形なら null
	 */
	Envelope open (String json) {

		Data envelope;

		try {
			envelope = Dson.decodes(json, Data.class);
		} catch (RuntimeException ex) {
			return null;
		}

		if (envelope == null || !envelope.containsKey("v") || envelope.getLong("v") != FORMAT) {
			return null;
		}

		long issuedAt = envelope.getLong("iat");
		long seen = envelope.getLong("seen");
		long now = clock.getAsLong();

		if (now - seen > timeoutMinutes * 60_000L || now - issuedAt > absoluteTimeoutMillis) {
			return null;
		}

		Data data = envelope.getDataOptional("data");

		return new Envelope(data == null ? new Data() : data, issuedAt, seen);

	}

	/**
	 * 暗号文を Cookie に置く
	 *
	 * <p>
	 * <b>署名も通す。</b>{@code cookie.secret} を設定していると、
	 * 受け取り側（{@code Cookies}）は<b>すべての Cookie の署名を検証し、
	 * 落ちたものは捨てる</b>。ここで署名していないと、
	 * <b>次のリクエストでセッションの Cookie が黙って捨てられる</b>——
	 * 例外も出ず、ログも出ず、「保存したのに消えている」だけが残る。
	 * </p>
	 *
	 * <p>
	 * 暗号（GCM）だけでも改ざんは検知できるので二重ではあるが、
	 * <b>要件 F-S-08 は「署名と暗号化の両方」</b>と定めている。
	 * </p>
	 *
	 * @param context	コンテキスト
	 * @param encrypted	暗号文
	 */
	private void put (WebContext context, String encrypted) {

		String value = Cookies.sign(encrypted);

		/*
		 * <b>大きさは署名まで含めて見る。</b>ブラウザが持つのはこちらである。
		 * 暗号文だけで測ると、上限すれすれのときに<b>署名の分だけはみ出して、
		 * 黙って捨てられる</b>
		 */
		int bytes = value.getBytes(StandardCharsets.UTF_8).length;

		if (bytes > MAX_VALUE_BYTES) {
			// 黙って捨てられるより、書いた側に気づかせる
			throw new IllegalStateException(
				"Cookie セッションが大きすぎます（%d > %d バイト）。保存先を db か redis にしてください"
					.formatted(bytes, MAX_VALUE_BYTES));
		}

		/*
		 * 読み返しに使う値は<b>署名を外した暗号文</b>にする。
		 * load / touch がそれを Aead に渡すため
		 */
		context.cookies().put(CookieConf.create(COOKIE_NAME, value, timeoutMinutes * 60), encrypted);

	}

	@Override
	public void touch (WebContext context, SessionEntry entry) {

		String encrypted = context.cookies().get(COOKIE_NAME);
		if (encrypted == null || encrypted.isEmpty()) {
			return;
		}

		/*
		 * <b>読めるかどうかを先に見る。</b>
		 * 前はここで中身を見ずに有効期限だけ延ばしていたので、
		 * <b>読めなくなった Cookie が延々と延命され</b>、
		 * かつ<b>古い鍵の暗号文が新しい鍵に入れ替わらなかった</b>（要件 NF-S-09）。
		 */
		KeyMatch match = Aead.decryptAny(encrypted, secrets);

		if (match == null) {
			// 読めないものを延ばしても意味がない。放っておけば期限で消える
			return;
		}

		Envelope envelope = open(match.value());

		if (envelope == null) {
			// 期限切れのものは延ばさない
			return;
		}

		long now = clock.getAsLong();

		if (match.current() && now - envelope.seen() < SEEN_RESOLUTION_MILLIS) {
			// 今の鍵で、少し前に使ったばかり。中身はそのまま、Cookie の有効期限だけ延ばす
			put(context, encrypted);
			return;
		}

		if (!match.current()) {
			// 古い鍵。中身は変えずに、今の鍵で包み直す
			Metrics.count(METRIC_STALE);
		}

		// 最後に使った時刻を書き直す
		write(context, envelope.data(), envelope.issuedAt(), now);

	}

	@Override
	public void destroy (WebContext context) {

		context.cookies().remove(COOKIE_NAME);

	}

	// endregion

}
