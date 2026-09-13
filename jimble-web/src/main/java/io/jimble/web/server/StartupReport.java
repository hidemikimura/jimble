package io.jimble.web.server;

import io.jimble.db.DBSource;
import io.jimble.db.DBUtil;
import io.jimble.db.cache.Cache;
import io.jimble.db.sqlcache.SqlCacheConf;
import io.jimble.db.redis.RedisClient;
import io.jimble.util.conf.Conf;
import io.jimble.util.hash.PasswordUtil;
import io.jimble.util.data.Data;
import io.jimble.web.auth.mfa.Mfa;
import io.jimble.web.auth.mfa.MfaConf;
import io.jimble.util.log.Log;
import io.jimble.web.cookie.CookieConf;
import io.jimble.web.session.SessionConf;
import io.jimble.web.session.SessionStores;
import io.jimble.web.upload.UploadConf;

import java.util.ArrayList;
import java.util.List;

/**
 * 起動時に「今どの実装が有効か」を出す（要件 F-U-11）
 *
 * <p>
 * 設定を切り替えられる箇所が増えるほど、<b>思っていた構成と違うまま動いている</b>
 * ことに気づけなくなる。起動時に1回まとめて出す。
 * </p>
 *
 * <pre>
 * jimble 構成: env=local / session=none / cache=db / sql_cache=off / redis=なし / db=[jimble_test] / パスワード暗号化=なし
 * </pre>
 */
public final class StartupReport {

	private StartupReport () {}

	/**
	 * 構成をログに出す
	 */
	public static void log () {

		Data fields = new Data();

		fields.put("env", Conf.env());
		fields.put("session", SessionConf.store());
		fields.put("cache", Cache.type());

		/*
		 * SQL結果キャッシュ（要件 F-D-28）は<b>既定で off</b> である。
		 * 書き忘れると「selectCached を呼んでいるのに効かない」になるので、
		 * 何になっているかを起動時に見せる（D-95）。
		 */
		fields.put("sql_cache", sqlCache());
		fields.put("redis", RedisClient.isConfigured());
		fields.put("db", dataSourceNames());
		fields.put("upload_max_file_size", UploadConf.maxFileSize());

		/*
		 * パスワードハッシュを暗号化するかは、明示していなければ
		 * 「cipher.key があるかどうか」で決まる（PasswordUtil.isEncrypt）。
		 * 状況で既定が変わるものは、何になったかを起動時に見せる。
		 */
		fields.put("password_encrypt", PasswordUtil.isEncrypt());

		/*
		 * 鍵が何本あるか（要件 NF-S-09）。
		 * <b>2本以上なら入れ替えの最中である。</b>
		 * 終わったのに previous_secrets を消し忘れている、が起動ログで分かる。
		 */
		fields.put("cookie_secrets", CookieConf.secrets().size());
		fields.put("session_secrets", SessionConf.secrets().size());

		/*
		 * どの設定ファイルを読んだか（要件 D-80）。
		 * 「直したはずの設定が効いていない」の原因がここに出る。
		 */
		fields.put("conf_files", Conf.sources());

		Log.info("jimble 構成: env=%s / session=%s / cache=%s / sql_cache=%s / redis=%s / db=%s / パスワード暗号化=%s / 鍵=%s"
			.formatted(
				Conf.env()
				, SessionConf.store()
				, Cache.type()
				, sqlCache()
				, RedisClient.isConfigured() ? "あり" : "なし"
				, dataSourceNames()
				, PasswordUtil.isEncrypt() ? "あり" : "なし"
				, secretCounts()
			), fields);

		logConfSources();

		warnUnusableCombination();

	}

	/**
	 * 鍵の本数（要件 NF-S-09）
	 *
	 * <p>2本以上なら入れ替えの最中である。</p>
	 *
	 * @return	{@code cookie=1 / session=2} のような形
	 */
	private static String secretCounts () {

		return "cookie=%d, session=%d".formatted(
			CookieConf.secrets().size(), SessionConf.secrets().size());

	}

	/**
	 * SQL結果キャッシュの状態
	 *
	 * @return	{@code off}、または置き場の名前
	 */
	private static String sqlCache () {

		return SqlCacheConf.enabled() ? SqlCacheConf.store() : "off";

	}

	/**
	 * どの設定ファイルを読んだかを出す（要件 D-80 / D-81）
	 *
	 * <p>
	 * 読むのは1つ（環境別があればそれ、無ければ共通）。
	 * <b>どこにあるものを読んだのか</b>まで出す。
	 * jar と {@code build/resources} の両方から起動できてしまうためである。
	 * </p>
	 *
	 * <p>
	 * 環境別ファイルに {@code include "application.conf"} を書き忘れると
	 * <b>共通の設定が丸ごと落ちる</b>。落ちていたら名指しで言う。
	 * </p>
	 */
	private static void logConfSources () {

		List<String> sources = Conf.sources();

		if (sources.isEmpty()) {
			Log.warn("設定: クラスパスに application.conf がありません（既定値だけで動いています）");
			return;
		}

		Log.info("設定: %s".formatted(String.join(", ", sources)));

		List<String> missing = Conf.missingFromEnvFile();

		if (!missing.isEmpty()) {
			Log.warn(("設定: application.conf にしかないキーが読まれていません: %s"
				+ " / application.%s.conf の先頭に include \"application.conf\" を書いてください")
				.formatted(String.join(", ", missing), Conf.env()));
		}

	}

	/**
	 * データソース名の一覧
	 *
	 * @return	名前
	 */
	private static List<String> dataSourceNames () {

		List<String> names = new ArrayList<>();

		try {
			for (DBSource dbSource : DBUtil.getDataSourceList()) {
				names.add(dbSource.name());
			}
		} catch (Exception ignore) {
			// DB 未設定。それ自体は異常ではない
		}

		return names;

	}

	/**
	 * 成立しない組み合わせを警告する
	 *
	 * <p>
	 * Redis 無しで Redis 前提の設定を選んでいると、<b>使った瞬間に落ちる。</b>
	 * 起動時に言っておく（要件 F-U-10 / F-U-11）。
	 * </p>
	 */
	private static void warnUnusableCombination () {

		warnMissingSecrets();

		warnMfaDisabledWithEnrollments();

		if (RedisClient.isConfigured()) {
			return;
		}

		if (Cache.TYPE_REDIS.equalsIgnoreCase(Cache.type())) {
			Log.warn("cache.type = redis ですが Redis が設定されていません。キャッシュを使うと失敗します");
		}

		if (SqlCacheConf.enabled() && SqlCacheConf.STORE_REDIS.equals(SqlCacheConf.store())) {
			Log.warn("sql_cache.store = redis ですが Redis が設定されていません。メモリに置きます（台をまたいで消えません）");
		}

		if ("redis".equalsIgnoreCase(SessionConf.store())) {
			Log.warn("session.store = redis ですが Redis が設定されていません。セッションを使うと失敗します");
		}

	}

	/**
	 * 二要素認証を切ったのに、登録済みの人が残っていることを言う（D-173）
	 *
	 * <p>
	 * <b>{@code auth.mfa.enabled = false} は、登録済みの人も含めて全員を素通しにする。</b>
	 * 推奨の書き方は {@code if (Mfa.isActive(id)) { ... } Auth.login(...);} なので、
	 * {@code isActive} が false を返すと<b>アプリはそのままログインさせる</b>——
	 * 「一時的に止める」つもりの1行で、<b>二要素を登録した全員がパスワードだけで入れる</b>。
	 * </p>
	 *
	 * <p>
	 * <b>起動を止めはしない。</b>本当に止めたい場面はあるし、
	 * 止めると「戻せない」ほうが困る。<b>人数を出して、知らずに落ちないようにする</b>。
	 * </p>
	 */
	private static void warnMfaDisabledWithEnrollments () {

		long enrolled = Mfa.disabledWithEnrollments();

		if (enrolled <= 0) {
			return;
		}

		Log.warn(("%s = false ですが、二要素認証を登録している利用者が %d 人います。"
			+ "この設定のあいだ、その %d 人はパスワードだけでログインできます")
			.formatted(MfaConf.KEY_ENABLED, enrolled, enrolled));

	}

	/**
	 * 鍵が無いことを言う（要件 F-S-08 / NF-S-09）
	 *
	 * <p>
	 * <b>鍵が無いと、署名の機能が丸ごと黙って無効になる。</b>
	 * 例外も出ないし、Cookie は普通に読み書きできるので、
	 * <b>動いているように見える</b>——効いていないことに気づく手がかりが1つも無い。
	 * </p>
	 *
	 * <p>
	 * <b>止めはしない。</b>手元で動かすだけのときに鍵を強制すると、
	 * 「とりあえず動かす」ができなくなる。
	 * </p>
	 */
	private static void warnMissingSecrets () {

		for (String message : missingSecretWarnings()) {
			Log.warn(message);
		}

	}

	/**
	 * 鍵が無いことの言い分（要件 F-S-08 / NF-S-09）
	 *
	 * <p><b>出す・出さないの判断だけを分けてある。</b>ログを覗かずに確かめられるように。</p>
	 *
	 * @return	言うこと。無ければ空
	 */
	static List<String> missingSecretWarnings () {

		List<String> warnings = new ArrayList<>();

		if (!CookieConf.isSigned()) {
			warnings.add(("%s が空です。Cookie に署名しません"
				+ "（セッション ID も CSRF トークンも改ざんを検知できません）。本番では必ず設定してください")
				.formatted(CookieConf.KEY_SECRET));
		}

		if (SessionStores.COOKIE.equalsIgnoreCase(SessionConf.store()) && SessionConf.secret().isEmpty()) {
			warnings.add("session.store = cookie ですが %s が空です。セッションを使うと起動できません"
				.formatted(SessionConf.KEY_SECRET));
		}

		return warnings;

	}

}
