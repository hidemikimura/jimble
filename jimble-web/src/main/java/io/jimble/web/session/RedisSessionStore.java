package io.jimble.web.session;

import io.jimble.db.redis.RedisClient;
import io.jimble.util.data.Data;
import io.jimble.web.context.WebContext;
import org.redisson.api.RMap;

import java.time.Duration;
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
 */
public final class RedisSessionStore implements SessionStore {

	/** キーの接頭辞 */
	public static final String KEY_PREFIX = "session:";

	/* タイムアウト */
	private final Duration timeout;

	/**
	 * コンストラクタ（設定から作る）
	 */
	public RedisSessionStore () {

		this(SessionConf.timeout().toMinutes());

	}

	/**
	 * コンストラクタ
	 *
	 * @param timeoutMinutes	タイムアウト（分）
	 */
	public RedisSessionStore (long timeoutMinutes) {

		this.timeout = Duration.ofMinutes(timeoutMinutes);

	}

	// region SessionStore

	@Override
	public SessionEntry load (WebContext context) {

		String sessionId = SessionId.get(context);
		if (sessionId == null || sessionId.isEmpty()) {
			return SessionEntry.empty();
		}

		RMap<String, String> map = map(sessionId);

		Data data = new Data();
		for (Map.Entry<String, String> entry : map.readAllMap().entrySet()) {
			data.put(entry.getKey(), entry.getValue());
		}

		if (data.isEmpty()) {
			return SessionEntry.empty();
		}

		// 触られたので期限を延ばす
		map.expire(timeout);

		return new SessionEntry(data, true);

	}

	@Override
	public void save (WebContext context, SessionEntry entry) {

		String sessionId = SessionId.getOrCreate(context);

		RMap<String, String> map = map(sessionId);
		map.clear();

		if (!entry.data().isEmpty()) {
			Data data = entry.data();
			for (String key : data.keySet()) {
				map.put(key, data.getString(key));
			}
		}

		map.expire(timeout);

	}

	@Override
	public void touch (WebContext context, SessionEntry entry) {

		String sessionId = SessionId.get(context);
		if (sessionId == null || sessionId.isEmpty()) {
			return;
		}

		map(sessionId).expire(timeout);

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
