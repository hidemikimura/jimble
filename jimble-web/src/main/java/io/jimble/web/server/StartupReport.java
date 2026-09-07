package io.jimble.web.server;

import io.jimble.db.DBSource;
import io.jimble.db.DBUtil;
import io.jimble.db.cache.Cache;
import io.jimble.db.redis.RedisClient;
import io.jimble.util.conf.Conf;
import io.jimble.util.hash.PasswordUtil;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;
import io.jimble.web.session.SessionConf;
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
 * jimble 構成: env=local / session=none / cache=db / redis=なし / db=[jimble_test] / パスワード暗号化=なし
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
		 * jar の外の conf/ を読んだかどうか（要件 D-71）。
		 * 「直したはずの設定が効いていない」の原因がここに出る。
		 */
		fields.put("conf_dir", Conf.confDir().getPath());
		fields.put("conf_files", Conf.sources());

		Log.info("jimble 構成: env=%s / session=%s / cache=%s / redis=%s / db=%s / パスワード暗号化=%s".formatted(
			Conf.env()
			, SessionConf.store()
			, Cache.type()
			, RedisClient.isConfigured() ? "あり" : "なし"
			, dataSourceNames()
			, PasswordUtil.isEncrypt() ? "あり" : "なし"
		), fields);

		logConfSources();

		warnUnusableCombination();

	}

	/**
	 * どの設定ファイルを読んだかを出す（要件 D-71）
	 *
	 * <p>
	 * jar に同梱した設定と、jar の外の {@code conf/} の両方があるとき、
	 * <b>どちらが効いているのかが分からない</b>のがいちばん困る。
	 * 外のものを読んだならそれを、読んでいないならその旨を出す。
	 * </p>
	 */
	private static void logConfSources () {

		List<String> sources = Conf.sources();

		if (sources.isEmpty()) {
			Log.info("設定: クラスパスのみ（%s/ に application.conf はありません）"
				.formatted(Conf.confDir().getPath()));
			return;
		}

		Log.info("設定: %s（クラスパスより優先）".formatted(String.join(", ", sources)));

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
				names.add(dbSource.name);
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

		if (RedisClient.isConfigured()) {
			return;
		}

		if (Cache.TYPE_REDIS.equalsIgnoreCase(Cache.type())) {
			Log.warn("cache.type = redis ですが Redis が設定されていません。キャッシュを使うと失敗します");
		}

		if ("redis".equalsIgnoreCase(SessionConf.store())) {
			Log.warn("session.store = redis ですが Redis が設定されていません。セッションを使うと失敗します");
		}

	}

}
