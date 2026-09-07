package io.jimble.web.ratelimit;

import io.jimble.db.redis.RedisClient;
import org.redisson.api.RScript;
import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.util.List;

/**
 * Redis に置く（要件 F-R-15）
 *
 * <p>
 * <b>台をまたいで数える。</b>複数台に並べるならこれを使う。
 * </p>
 *
 * <h2>なぜ Lua なのか</h2>
 * <p>
 * トークンバケットは<b>「残り」と「最後に足した時刻」を一緒に</b>読み書きしないと成り立たない。
 * 別々のコマンドでやると、同時に来たぶんが数え落ちる。
 * Redis はスクリプトを<b>不可分に</b>実行するので、そこに全部入れる。
 * </p>
 */
public final class RedisRateLimitStore implements RateLimitStore {

	/** キーの接頭辞 */
	public static final String PREFIX = "jimble:rate_limit:";

	/*
	 * トークンバケット。
	 *
	 * KEYS[1] = キー
	 * ARGV[1] = 貯められる回数
	 * ARGV[2] = 空から満タンに戻るまでのミリ秒
	 * ARGV[3] = いまの時刻（ミリ秒）
	 *
	 * 返り = { 通してよいか(1/0), 残り, 待つミリ秒 }
	 */
	private static final String SCRIPT = """
		local limit = tonumber(ARGV[1])
		local duration = tonumber(ARGV[2])
		local now = tonumber(ARGV[3])

		local state = redis.call('hmget', KEYS[1], 'tokens', 'at')
		local tokens = tonumber(state[1])
		local at = tonumber(state[2])

		if tokens == nil then
			tokens = limit
			at = now
		end

		tokens = math.min(limit, tokens + (now - at) * limit / duration)

		local allowed = 0
		local wait = 0

		if tokens >= 1 then
			tokens = tokens - 1
			allowed = 1
		else
			wait = math.ceil((1 - tokens) * duration / limit)
		end

		redis.call('hset', KEYS[1], 'tokens', tokens, 'at', now)

		-- 満タンに戻るまで置いておけば足りる。放っておくと増え続ける
		redis.call('pexpire', KEYS[1], duration * 2)

		return { allowed, math.floor(tokens), wait }
		""";

	/**
	 * {@inheritDoc}
	 */
	@Override
	public RateLimitResult consume (String key, long limit, Duration duration) {

		RScript script = RedisClient.client().getScript(StringCodec.INSTANCE);

		List<Object> result = script.eval(
			RScript.Mode.READ_WRITE
			, SCRIPT
			, RScript.ReturnType.LIST
			, List.of(PREFIX + key)
			, String.valueOf(limit)
			, String.valueOf(duration.toMillis())
			, String.valueOf(System.currentTimeMillis())
		);

		long allowed = toLong(result.get(0));
		long remaining = toLong(result.get(1));
		long wait = toLong(result.get(2));

		return allowed == 1 ? RateLimitResult.allow(remaining) : RateLimitResult.deny(wait);

	}

	/**
	 * Lua からの戻りを数にする
	 *
	 * @param value	値
	 * @return	数
	 */
	private static long toLong (Object value) {

		return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));

	}

}
