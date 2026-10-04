package io.jimble.web.session;

import io.jimble.db.redis.RedisClient;
import io.jimble.util.data.Data;
import io.jimble.web.context.WebContext;
import org.redisson.api.RBatch;
import org.redisson.api.RFuture;
import org.redisson.api.RMap;
import org.redisson.api.RScript;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis セッション（要件 F-S-01）
 *
 * <p>
 * セッション ID をキーにしたハッシュに持つ。<b>期限は Redis の TTL に任せる。</b>
 * </p>
 *
 * <h2>移送元から変えたところ</h2>
 * <ol>
 *   <li><b>最終アクセス日時を自前で持っていた。</b>移送元は {@code __accessed_at} という
 *       キーを値に混ぜ、読むたびに {@code Instant.parse} して期限を判定していた。
 *       <b>そのキーが無いと {@code Instant.parse(null)} で落ちる</b>し、
 *       期限切れのデータが Redis に残り続ける。TTL を使えばどちらも起きない</li>
 *   <li>{@code __accessed_at} がアプリから見えるセッションデータに混ざっていた</li>
 * </ol>
 *
 * <h2>発行からの上限（D-260）</h2>
 * <p>
 * 発行した時刻をハッシュの {@value #CREATED_AT} に持つ（アプリから見えるデータには混ぜない）。
 * {@code session.absolute_timeout} を書いたときは、TTL を「最後に使ってから」と「発行からの残り」の短いほうにし、
 * 過ぎていたら捨てる。
 * </p>
 */
public final class RedisSessionStore implements SessionStore {

	/** キーの接頭辞 */
	public static final String KEY_PREFIX = "session:";

	/** 発行した時刻（エポック秒）を持つフィールド。アプリのデータには出さない */
	static final String CREATED_AT = "\u0000jimble.created_at";

	/* タイムアウト */
	private final Duration timeout;

	/* 発行からの上限（0 なら上限なし） */
	private final Duration absoluteTimeout;

	/**
	 * コンストラクタ（設定から作る）
	 */
	public RedisSessionStore () {

		this(SessionConf.timeout().toMinutes(), SessionConf.serverAbsoluteTimeout());

	}

	/**
	 * コンストラクタ
	 *
	 * @param timeoutMinutes	タイムアウト（分）
	 */
	public RedisSessionStore (long timeoutMinutes) {

		this(timeoutMinutes, Duration.ZERO);

	}

	/**
	 * コンストラクタ
	 *
	 * @param timeoutMinutes	タイムアウト（分）
	 * @param absoluteTimeout	発行からの上限（0 なら上限なし）
	 */
	public RedisSessionStore (long timeoutMinutes, Duration absoluteTimeout) {

		this.timeout = Duration.ofMinutes(timeoutMinutes);
		this.absoluteTimeout = absoluteTimeout == null || absoluteTimeout.isNegative() ? Duration.ZERO : absoluteTimeout;

	}

	/**
	 * 延ばす長さ
	 *
	 * @param createdAt	発行した時刻（エポック秒）
	 * @return	TTL（0 以下なら、もう期限切れ）
	 */
	private Duration ttl (long createdAt) {

		if (absoluteTimeout.isZero()) {
			return timeout;
		}

		Duration left = absoluteTimeout.minusSeconds(nowSeconds() - createdAt);

		return left.compareTo(timeout) < 0 ? left : timeout;

	}

	private static long nowSeconds () {

		return System.currentTimeMillis() / 1000;

	}

	/**
	 * 発行した時刻を読む（2.2.3 までに作ったものには無いので、いまにする）
	 */
	private static long createdAt (String value) {

		try {
			return value == null ? nowSeconds() : Long.parseLong(value);
		} catch (NumberFormatException ex) {
			return nowSeconds();
		}

	}

	// region SessionStore

	@Override
	public SessionEntry load (WebContext context) {

		String sessionId = SessionId.get(context);
		if (sessionId == null || sessionId.isEmpty()) {
			return SessionEntry.empty();
		}

		RMap<String, String> map = map(sessionId);

		Map<String, String> all;
		boolean extended = false;

		if (absoluteTimeout.isZero()) {

			/*
			 * <b>読むのと延ばすのを1回の往復で</b>（D-288）。発行からの上限が無ければ、延ばす長さは発行時刻によらない。
			 * 2.5.1 までは HGETALL と EXPIRE を別々に送っていた（セッションのあるリクエストごとに往復1回ぶん余計）
			 */
			RBatch batch = RedisClient.client().createBatch();
			RFuture<Map<String, String>> read = batch.<String, String>getMap(KEY_PREFIX + sessionId).readAllMapAsync();
			batch.getMap(KEY_PREFIX + sessionId).expireAsync(timeout);
			batch.execute();

			all = read.toCompletableFuture().join();
			extended = true;

		} else {
			all = map.readAllMap();
		}

		String created = all.get(CREATED_AT);

		Data data = new Data();
		for (Map.Entry<String, String> entry : all.entrySet()) {
			if (!CREATED_AT.equals(entry.getKey())) {
				data.put(entry.getKey(), entry.getValue());
			}
		}

		if (data.isEmpty()) {
			return SessionEntry.empty();
		}

		Duration ttl = ttl(createdAt(created));

		if (ttl.isZero() || ttl.isNegative()) {

			// 発行からの上限を過ぎた。中身も ID も捨てる（D-260）
			map.delete();
			SessionId.remove(context);

			return SessionEntry.empty();

		}

		long createdSeconds = createdAt(created);

		if (created == null) {
			map.fastPut(CREATED_AT, String.valueOf(createdSeconds));
		}

		// 触られたので期限を延ばす（上でまとめて延ばしていなければ）
		if (!extended) {
			map.expire(ttl);
		}

		// 発行時刻を持たせておく（保存するときに読み直さない。D-288）
		return new SessionEntry(data, true, createdSeconds * 1000);

	}

	@Override
	public void save (WebContext context, SessionEntry entry) {

		String sessionId = SessionId.getOrCreate(context);

		RMap<String, String> map = map(sessionId);

		// 発行した時刻は持ち越す（読み込んだときに持たせたもの。新しいセッションなら、いま）
		long created = entry.issuedAt() > 0 ? entry.issuedAt() / 1000
			: entry.isExisting() ? createdAt(map.get(CREATED_AT)) : nowSeconds();
		Duration ttl = ttl(created);

		// 中身が無い・発行からの上限を過ぎたなら、何も残さない
		if (entry.data().isEmpty() || ttl.isZero() || ttl.isNegative()) {
			map.delete();
			return;
		}

		Map<String, String> fields = new LinkedHashMap<>();
		Data data = entry.data();
		for (String key : data.keySet()) {
			String value = data.getString(key);
			if (value != null) {
				fields.put(key, value);
			}
		}
		fields.put(CREATED_AT, String.valueOf(created));

		/*
		 * <b>消す・入れる・期限を、1つの Lua で送る</b>（D-268）。
		 *
		 * 2.5.1 までは clear・キーの数だけ put・fastPut・expire を1つずつ送っていた。
		 * <b>clear と put のあいだに同じ人の別のリクエストが読むと、空のセッション（ログアウトした状態）が見えた</b>。
		 * 途中で落ちると、期限の無いハッシュも残った。
		 *
		 * <b>読み込んだセッションは、まだあるときだけ書く</b>（D-275）。読んでから保存するまでのあいだに
		 * ログアウト（destroy）されたら、書き戻さない——書き戻すと、盗まれた ID でログアウトしても
		 * 同時に流れていたリクエストがログインを生き返らせた。
		 */
		List<Object> args = new ArrayList<>(2 + fields.size() * 2);
		args.add(entry.isExisting() ? "1" : "0");
		args.add(String.valueOf(Math.max(ttl.toMillis(), 1)));
		fields.forEach((key, value) -> {
			args.add(key);
			args.add(value);
		});

		RedisClient.client().getScript(StringCodec.INSTANCE).eval(
			RScript.Mode.READ_WRITE
			, SAVE_SCRIPT
			, RScript.ReturnType.LONG
			, List.of(KEY_PREFIX + sessionId)
			, args.toArray());

	}

	/*
	 * 保存する（ARGV[1] が "1" なら、まだあるときだけ。ARGV[2] は期限のミリ秒、ARGV[3..] は項目と値）
	 */
	private static final String SAVE_SCRIPT = """
		if ARGV[1] == '1' and redis.call('EXISTS', KEYS[1]) == 0 then
			return 0
		end
		redis.call('DEL', KEYS[1])
		for i = 3, #ARGV, 2 do
			redis.call('HSET', KEYS[1], ARGV[i], ARGV[i + 1])
		end
		redis.call('PEXPIRE', KEYS[1], ARGV[2])
		return 1
		""";

	@Override
	public void touch (WebContext context, SessionEntry entry) {

		String sessionId = SessionId.get(context);
		if (sessionId == null || sessionId.isEmpty()) {
			return;
		}

		RMap<String, String> map = map(sessionId);

		// 発行からの上限が無ければ、発行時刻を読まずに延ばす（D-288）
		if (absoluteTimeout.isZero()) {
			map.expire(timeout);
			return;
		}

		Duration ttl = ttl(createdAt(map.get(CREATED_AT)));

		if (ttl.isZero() || ttl.isNegative()) {
			map.delete();
			return;
		}

		map.expire(ttl);

	}

	@Override
	public void destroy (WebContext context) {

		String sessionId = SessionId.get(context);
		if (sessionId != null && !sessionId.isEmpty()) {
			map(sessionId).delete();
		}

		SessionId.remove(context);

	}

	// endregion

	/**
	 * セッションのハッシュ
	 *
	 * @param sessionId	セッション ID
	 * @return	ハッシュ
	 */
	private RMap<String, String> map (String sessionId) {

		return RedisClient.client().getMap(KEY_PREFIX + sessionId);

	}

}
