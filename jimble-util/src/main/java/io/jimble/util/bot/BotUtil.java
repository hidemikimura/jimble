package io.jimble.util.bot;

import io.jimble.util.log.Log;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.concurrent.locks.ReentrantLock;

/**
 * bot判定
 * https://user-agents.net/download
 */
public class BotUtil {

	/* 読み込み済み判定 */
	private static boolean loaded = false;

	/* BOT IP */
	private static HashSet<String> BOT_IP_HASH = new HashSet<>();

	/* BOT UA */
	private static HashSet<String> BOT_UA_HASH = new HashSet<>();

	/* ロック */
	private static final ReentrantLock loadLock = new ReentrantLock();

	/**
	 * BOT判定
	 *
	 * @param ip	IP
	 * @param ua	UA
	 * @return	BOTの場合 = true
	 */
	public static boolean isBot (String ip, String ua) {

		if (ip == null || ip.isEmpty()) {
			return false;
		}

		if (ua == null || ua.isEmpty()) {
			return true;
		}

		load();

		return BOT_IP_HASH.contains(ip) || BOT_UA_HASH.contains(ua.toLowerCase());

	}

	/**
	 * 定義を読み込む
	 */
	private static void load () {

		if (loaded) {
			return;
		}

		try {

			loadLock.lock();

			if (loaded) {
				return;
			}

			loadFile();

			loaded = true;

		} catch (Exception ex) {

			Log.error(ex);

		} finally {

			loadLock.unlock();

		}

	}

	/**
	 * 定義ファイル読み込み
	 */
	private static void loadFile () {

		long start = System.currentTimeMillis();

		BOT_IP_HASH = new HashSet<>();
		BOT_UA_HASH = new HashSet<>();
		try (
			InputStream is = loadResource("user-agents_bot-crawler.txt");
			InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
			BufferedReader br = new BufferedReader(isr)
		) {

			String line;
			while ((line = br.readLine()) != null) {
				if (line.isEmpty()) {
					continue;
				}
				int ipIndex = line.indexOf(" [ip:");
				if (ipIndex > 0) {
					int ipIndexEnd = line.indexOf("]", ipIndex + 1);
					if (ipIndexEnd > 0) {
						BOT_UA_HASH.add(line.substring(0, ipIndex).toLowerCase());
						BOT_IP_HASH.add(line.substring(ipIndex + 5, ipIndexEnd));
					} else {
						BOT_UA_HASH.add(line.toLowerCase());
					}
				} else {
					BOT_UA_HASH.add(line.toLowerCase());
				}
			}

		} catch (Exception ex) {

			Log.error(ex);

		}

		Log.info("loaded bot definition ua: " + BOT_UA_HASH.size());
		Log.info("loaded bot definition ip: " + BOT_IP_HASH.size());
		Log.info("loaded bot definition time: " + (System.currentTimeMillis() - start) + "ms");

	}

	/**
	 * リソースを読み込む
	 *
	 * @param name  リソース名
	 * @return  入力ストリーム
	 * @throws IOException 例外
	 */
	private static InputStream loadResource (String name) throws IOException {

		// new File(BotUtil.class.protectionDomain().getCodeSource().getLocation().getPath(), "../../../resources/main/lib/base/util/common/bot/user-agents_bot-crawler.txt").exists()
		try {
			InputStream is = BotUtil.class.getResourceAsStream(name);
			if (is == null) {
				throw new FileNotFoundException(name);
			}
			return is;
		} catch (Exception ex) {
			return new FileInputStream(new File(BotUtil.class.getProtectionDomain().getCodeSource().getLocation().getPath(), "../../../resources/main/lib/base/util/common/bot/" + name));
		}

	}

	public static void loadTest () {

		try (
			InputStream is = BotUtil.class.getResourceAsStream("user-agents_bot-crawler.txt");
			InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
			BufferedReader br = new BufferedReader(isr)
		) {

			String line;
			while ((line = br.readLine()) != null) {

			}

		} catch (Exception ex) {}

	}

}
