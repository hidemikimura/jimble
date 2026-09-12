package io.jimble.util.bot;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.jimble.util.json.Dson;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * BOT 判定
 *
 * <p>
 * 名乗り（User-Agent）を <a href="https://github.com/monperrus/crawler-user-agents">
 * crawler-user-agents</a> の一覧と突き合わせる。
 * 一覧は {@code crawler-user-agents.json} として<b>同梱している</b>。
 * </p>
 *
 * <h2>これは2代目である（要件 D-164）</h2>
 * <p>
 * 初代（{@code BotUtil}）は<b>定義ファイルを同梱しておらず</b>、
 * 読めなかったときの逃げ道が<b>移送元のディレクトリ構成のまま</b>だったので、
 * <b>一覧が常に0件</b>で動いていた——
 * true を返すのは「名乗りが空のとき」だけで、
 * <b>巡回は全部「人」として数えられていた</b>。
 * 誰も呼んでいなかったので<b>消して、こちらに名前を譲った</b>。
 * </p>
 *
 * <p>
 * <b>いちばん静かな壊れ方は、いまも「定義が0件のまま動くこと」である。</b>
 * リソースが見つからなければ<b>その場で落とす</b>ようにしてあり、
 * {@code BotUtilTest} が代表的な巡回を当てて見張っている。
 * </p>
 */
public class BotUtil {

	/**
	 * コンストラクタ
	 *
	 * <p>持ち物は無い。</p>
	 */
	public BotUtil () {
	}

	/* 読み込み済み判定 */
	private static volatile boolean loaded = false;

	/* ロック */
	private static final ReentrantLock loadLock = new ReentrantLock();

	/* パターンリスト */
	private static final List<Pattern> patternList = new ArrayList<>();

	/* UA結果マップ */
	private static final Cache<String, Boolean> uaResultMapCache = Caffeine.newBuilder()
		.maximumSize(10000)
		.build();
	private static final Map<String, Boolean> uaResultMap = uaResultMapCache.asMap();

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

		return isBot(ua);

	}

	/**
	 * bot判定
	 *
	 * @param ua    UA
	 * @return  bot判定
	 */
	private static boolean isBot (String ua) {

		Boolean cached = uaResultMap.get(ua);
		if (cached != null) {
			return cached;
		}

		for (Pattern pattern : patternList) {
			if (pattern.matcher(ua).find()) {
				uaResultMap.put(ua, Boolean.TRUE);
				return true;
			}
		}

		uaResultMap.put(ua, Boolean.FALSE);
		return false;

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

		List<Data> jsonList = null;
		try (
			Reader reader = loadResource("crawler-user-agents.json")
		) {
			jsonList = Dson.decodes(reader, List.class, Data.class);
		} catch (Exception ex) {
			Log.error(ex);
		}

		if (jsonList != null) {
			for (Data data : jsonList) {
				patternList.add(Pattern.compile(data.getString("pattern")));
			}
		}

		Log.info("loaded bot definition ua pattern: " + patternList.size());
		Log.info("loaded bot definition time: " + (System.currentTimeMillis() - start) + "ms");

	}

	/**
	 * リソースを読み込む
	 *
	 * @param name  リソース名
	 * @return  入力ストリーム
	 * @throws IOException 例外
	 */
	private static Reader loadResource (String name) throws IOException {

		/*
		 * クラスパスから読む。
		 *
		 * 移送元はここに「見つからなければ jar の場所から
		 * ../../../resources/main/lib/base/util/common/bot/ を辿る」という
		 * 逃げ道があった。移送先ではそのパスが存在しないので、
		 * <b>起動のたびにエラーを1行出して、ボットのパターンが0件のまま動いていた。</b>
		 * 「ボット判定が効いていない」ことに気づけない形になっていたので、
		 * 逃げ道を消してリソースを同梱した。
		 */
		InputStream inputStream = BotUtil.class.getResourceAsStream(name);

		if (inputStream == null) {
			throw new FileNotFoundException(
				"ボット定義が見つかりません: %s（jimble-util の resources に同梱されているはず）".formatted(name));
		}

		InputStreamReader isr = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
		return new BufferedReader(isr);

	}

}
