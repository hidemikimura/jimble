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

	/* 一度に突き合わせるもの（D-287） */
	private static volatile BotMatcher matcher = null;

	/** 判定に使う UA の長さの上限（D-232） */
	static final int MAX_UA_LENGTH = 512;

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
	private static boolean isBot (String rawUa) {

		/*
		 * <b>先頭の 512 文字だけを見る</b>（D-232）。ふつうの UA は 300 文字に満たない。
		 * かつては丸ごと 1,500 ほどの正規表現にかけ、丸ごとキャッシュのキーにしていたので、
		 * 16KB の UA を送るたびに CPU を使わせ、キャッシュに 1 件 16KB ずつ積ませられた
		 */
		String ua = rawUa.length() > MAX_UA_LENGTH ? rawUa.substring(0, MAX_UA_LENGTH) : rawUa;

		Boolean cached = uaResultMap.get(ua);
		if (cached != null) {
			return cached;
		}

		BotMatcher current = matcher;

		boolean bot = current != null && current.matches(ua);

		uaResultMap.put(ua, bot);
		return bot;

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
			List<String> definitions = new ArrayList<>();
			for (Data data : jsonList) {
				definitions.add(data.getString("pattern"));
			}
			matcher = new BotMatcher(definitions);
		}

		Log.info("loaded bot definition ua pattern: " + (matcher == null ? 0 : matcher.size()));
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
