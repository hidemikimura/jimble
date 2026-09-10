package approval.list;

import io.jimble.db.DBUtil;
import io.jimble.util.conf.Conf;
import io.jimble.web.server.JimbleServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * サンプル（approval-list）を実際に起動して叩く（要件 NF-T-06）
 *
 * <p>
 * 初期データは 002_seed.sql で入る（部署3・社員6・申請60件）。
 * <b>件数を当てにしているテストがある</b>ので、初期データを変えるならここも直すこと。
 * </p>
 *
 * <pre>
 * ./gradlew :examples:approval-list:pgTest
 * </pre>
 */
@Tag("db")
class ApprovalListIntegrationTest {

	/** 初期データの申請の件数 */
	private static final int SEEDED = 60;

	/* サーバー */
	private static JimbleServer server;

	/* クライアント */
	private static HttpClient client;

	@BeforeAll
	static void startServer () {

		Conf.reload();

		Bootstrap.load();

		server = JimbleServer.start(new ListApp(), 0);

		client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();

	}

	@AfterAll
	static void stopServer () {

		if (server != null) {
			server.stop();
		}

		DBUtil.stop();

	}

	// region ページング（要件 F-V-05 / F-V-06）

	@Test
	@DisplayName("F-V-05 総件数と最終ページが返る")
	void pagingHasTotal () throws Exception {

		String body = get("/requests").body();

		assertEquals(SEEDED, number(body, "total"), body);
		assertEquals(1, number(body, "page"), body);
		assertEquals(10, number(body, "per"), body);

		/*
		 * <b>ここが 0 になるのがいちばんありそうな壊れ方である。</b>
		 * selectList で引くと総件数が入らないので、画面には「1/0 ページ」と出る
		 */
		assertEquals(6, number(body, "max_page"), body);

	}

	@Test
	@DisplayName("F-V-05 ページをまたいでも同じものが2回出ない")
	void pagesDoNotOverlap () throws Exception {

		String first = get("/requests?page=1").body();
		String second = get("/requests?page=2").body();

		assertEquals(10, count(first, "\"id\""), first);
		assertEquals(10, count(second, "\"id\""), second);

		/*
		 * <b>並び順が決まっていないとページがずれる。</b>
		 * created_at だけで並べると同着があり、<b>同じ行が2ページに出る</b>ことがある。
		 * id を第2の並び順に足してあるのはそのため
		 */
		for (long id : ids(first)) {
			assertFalse(ids(second).contains(id), "%d が1ページ目にも2ページ目にも出ています".formatted(id));
		}

	}

	@Test
	@DisplayName("F-V-06 per で1ページの件数を変えられる")
	void perChangesPageSize () throws Exception {

		String body = get("/requests?per=25").body();

		assertEquals(25, count(body, "\"id\""), body);
		assertEquals(3, number(body, "max_page"), body);

	}

	@Test
	@DisplayName("最後のページは端数になる")
	void lastPageIsPartial () throws Exception {

		String body = get("/requests?per=25&page=3").body();

		assertEquals(10, count(body, "\"id\""), body);

	}

	// endregion

	// region 絞り込み

	@Test
	@DisplayName("F-D-05 部署で絞ると、その部署のものだけ返る（JOIN）")
	void filterByDepartment () throws Exception {

		String all = get("/requests?per=all").body();
		assertTrue(all.contains("営業部"), all);

		/*
		 * <b>id を決め打ちしない。</b>連番はマイグレーションを流し直すと進むので、
		 * 手元と CI で違う値になる（実際に 1,2,3 のつもりで書いて 4,5,6 になっていた）
		 */
		long sales = departmentId("営業部");

		String body = get("/requests?per=all&department_id=" + sales).body();

		assertTrue(body.contains("営業部"), body);
		assertFalse(body.contains("開発部"), body);
		assertFalse(body.contains("総務部"), body);

	}

	@Test
	@DisplayName("F-D-07 絞り込みが空でも落ちない（空の in を作らない）")
	void emptyFilterIsNotAnEmptyIn () throws Exception {

		/*
		 * <b>{@code in(空)} は SQL を組み立てた時点で例外になる</b>（要件 F-D-07）。
		 * 「空なら条件ごと外す」を枠組みがやってしまうと<b>全件が返る</b>ので、
		 * 呼ぶ側が「空なら積まない」と書く。ここはその形になっているか
		 */
		HttpResponse<String> response = get("/requests");

		assertEquals(200, response.statusCode(), response.body());
		assertEquals(SEEDED, number(response.body(), "total"));

	}

	@Test
	@DisplayName("状態を複数指定できる（in）")
	void filterBySeveralStatuses () throws Exception {

		String one = get("/requests?per=all&status=pending").body();
		String two = get("/requests?per=all&status=pending&status=approved").body();

		assertTrue(count(two, "\"id\"") > count(one, "\"id\"")
			, "複数指定が効いていません: %d / %d".formatted(count(one, "\"id\""), count(two, "\"id\"")));

		assertFalse(one.contains("approved"), one);

	}

	// endregion

	// region 集計（要件 F-D-05 / F-D-09）

	@Test
	@DisplayName("F-D-05 部署ごとにまとめて、合計の多い順で返る")
	void summary () throws Exception {

		String body = get("/requests/summary").body();

		// 部署は3つ
		assertEquals(3, count(body, "\"department\""), body);

		// 3部署 × 20件
		assertTrue(body.contains("\"count\":20"), body);

		/*
		 * <b>別名を付けた列はネストされない。</b>
		 * テーブルの列だけが親子に割られるので、count はトップレベルに出る
		 */
		assertFalse(body.contains("\"request\":{"), "集計の結果がネストされています: " + body);

	}

	@Test
	@DisplayName("F-D-09 条件付きの集計（承認されたぶんだけ）が別に出る")
	void conditionalAggregate () throws Exception {

		String body = get("/requests/summary").body();

		assertTrue(body.contains("approved_total"), body);

		/*
		 * <b>承認ぶんは全体より少ない。</b>同じ値なら CASE が効いていない
		 * （全部足してしまっている）
		 */
		long total = number(body, "total");
		long approved = number(body, "approved_total");

		assertTrue(approved < total
			, "条件付きの集計が効いていません: 承認 %d / 全体 %d".formatted(approved, total));

		assertTrue(approved > 0, body);

	}

	@Test
	@DisplayName("D-135 集計した値を CASE の条件にできる（大／中／小が SQL で付く）")
	void sizeIsDecidedInSql () throws Exception {

		String body = get("/requests/summary").body();

		assertTrue(body.contains("\"size\""), body);

		/*
		 * <b>前はここが書けなかった。</b>{@code Dsl.sum(...)} に比較が無く、
		 * 区分けを Java 側で付けていた（D-135）。
		 * 3部署とも同じ区分けなら、条件が効いていない疑いがある
		 */
		assertTrue(body.contains("大") || body.contains("中") || body.contains("小"), body);

	}

	// endregion

	// region キャッシュ（要件 F-D-28）

	@Test
	@DisplayName("F-D-28 更新すると、キャッシュが自分で消える")
	void cacheIsInvalidatedByUpdate () throws Exception {

		// 1回目でキャッシュに乗る
		assertTrue(get("/departments").body().contains("総務部"));

		long general = departmentId("総務部");

		String renamed = "総務部" + System.currentTimeMillis();

		HttpResponse<String> update = post(
			"/departments/" + general + "/rename", "name=" + enc(renamed));

		assertEquals(200, update.statusCode(), update.body());

		/*
		 * <b>消す処理はアプリ側に1行も無い。</b>更新した側が勝手に消す。
		 * ここが古いままなら、キャッシュの失効が効いていない
		 */
		String after = get("/departments").body();

		assertTrue(after.contains(renamed), "キャッシュが古いままです: " + after);

		// 戻す（他のテストの順番に影響させない）
		post("/departments/" + general + "/rename", "name=" + enc("総務部"));

	}

	// endregion

	// region CSV（要件 F-D-12 / F-Y-08）

	@Test
	@DisplayName("F-D-12 CSV が JOIN 込みで、1行ずつ書き出される")
	void csv () throws Exception {

		HttpResponse<String> response = get("/requests.csv");

		assertEquals(200, response.statusCode());
		assertTrue(response.headers().firstValue("content-type").orElse("").contains("text/csv")
			, response.headers().map().toString());

		String[] lines = response.body().split("\n");

		// 見出し + 60 行
		assertEquals(SEEDED + 1, lines.length, response.body());
		assertTrue(lines[0].contains("部署"), lines[0]);

		// JOIN した先の名前が入っていること
		assertTrue(response.body().contains("営業部"), lines[1]);

	}

	// endregion

	// region ここで固定していないこと

	/*
	 * - <b>ページングと集計を重ねていない。</b>総件数を数える SQL が集計を包んだ形になり、
	 *   その組み合わせは jimble にテストが無い。踏まないことにした（RequestListController の説明）
	 * - <b>ScopeCache（F-W-15）が効いていることは見ていない。</b>
	 *   1リクエストで2回引く経路がこのサンプルに無いので、外から見て差が出ない
	 * - <b>キャッシュの TTL</b> は待たないと確かめられないので見ていない
	 * - <b>「キャッシュに実際に乗っていること」は見ていない。</b>
	 *   selectListCached を selectList に変えても、このテストは全部通る——
	 *   外から見て変わるのは速さだけだからである。
	 *   ここが見張っているのは<b>「更新したのに古いものが出続ける」</b>ほうで、
	 *   そちらが利用者に見える壊れ方である
	 */

	// endregion

	/**
	 * 部署の名前から id を引く
	 *
	 * @param name	名前
	 * @return	id
	 * @throws Exception	失敗した場合
	 */
	private static long departmentId (String name) throws Exception {

		String body = get("/departments").body();

		Matcher matcher = Pattern
			.compile("\\{\"id\":(\\d+),\"name\":\"" + Pattern.quote(name) + "\"")
			.matcher(body);

		if (!matcher.find()) {
			throw new AssertionError("%s が見つかりません: %s".formatted(name, body));
		}

		return Long.parseLong(matcher.group(1));

	}

	/**
	 * GET する
	 *
	 * @param path	パス
	 * @return	応答
	 * @throws Exception	失敗した場合
	 */
	private static HttpResponse<String> get (String path) throws Exception {

		return client.send(
			HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path)).GET().build()
			, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

	}

	/**
	 * POST する
	 *
	 * @param path	パス
	 * @param body	本文
	 * @return	応答
	 * @throws Exception	失敗した場合
	 */
	private static HttpResponse<String> post (String path, String body) throws Exception {

		return client.send(
			HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
				.build()
			, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

	}

	/**
	 * URL エンコードする
	 *
	 * @param value	値
	 * @return	エンコードしたもの
	 */
	private static String enc (String value) {

		return URLEncoder.encode(value, StandardCharsets.UTF_8);

	}

	/**
	 * JSON から数を1つ取る
	 *
	 * @param body	本文
	 * @param name	名前
	 * @return	数
	 */
	private static long number (String body, String name) {

		Matcher matcher = Pattern.compile("\"" + name + "\"\\s*:\\s*(-?\\d+)").matcher(body);

		if (!matcher.find()) {
			throw new AssertionError("%s が返っていません: %s".formatted(name, body));
		}

		return Long.parseLong(matcher.group(1));

	}

	/**
	 * 出てくる回数を数える
	 *
	 * @param body		本文
	 * @param needle	探すもの
	 * @return	回数
	 */
	private static int count (String body, String needle) {

		int found = 0;
		int at = 0;

		while ((at = body.indexOf(needle, at)) >= 0) {
			found++;
			at += needle.length();
		}

		return found;

	}

	/**
	 * 応答に入っている id を集める
	 *
	 * @param body	本文
	 * @return	id
	 */
	private static java.util.List<Long> ids (String body) {

		java.util.List<Long> ids = new java.util.ArrayList<>();

		Matcher matcher = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(body);

		while (matcher.find()) {
			ids.add(Long.parseLong(matcher.group(1)));
		}

		return ids;

	}

}
