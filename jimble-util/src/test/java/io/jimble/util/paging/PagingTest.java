package io.jimble.util.paging;

import com.typesafe.config.ConfigFactory;

import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Paging} のテスト（要件 F-V-05 / F-V-06）
 */
class PagingTest {

	@AfterEach
	void resetConf () {

		Conf.reload();

	}

	/**
	 * リクエストデータを作る
	 *
	 * @param page	ページ番号
	 * @param per	取得件数
	 * @return	リクエストデータ
	 */
	private Data request (String page, String per) {

		Data data = new Data();
		if (page != null) {
			data.put(Paging.namePage(), page);
		}
		if (per != null) {
			data.put(Paging.namePer(), per);
		}

		return data;

	}

	// region 読み込み

	@Test
	@DisplayName("page と per を読む")
	void load () {

		Paging paging = new Paging();
		paging.load(request("3", "20"), 0);

		assertEquals(3, paging.page());
		assertEquals(20, paging.per());
		assertEquals(41, paging.start(), "3ページ目は 41 件目から");

	}

	@Test
	@DisplayName("指定が無ければ既定値になる")
	void defaults () {

		Paging paging = new Paging();
		paging.load(new Data(), 0);

		assertEquals(1, paging.page());
		assertEquals(Paging.DEFAULT_PER, paging.per());
		assertEquals(1, paging.start());

	}

	@Test
	@DisplayName("D-159 per=all も上限までしか返さない")
	void perAllIsCapped () {

		/*
		 * <b>上限が無いと、誰でも {@code ?per=all} と打つだけで全件を引ける。</b>
		 * 100 万行のテーブルなら、1回で止まる。
		 */
		Paging paging = new Paging();
		paging.load(request("2", "all"), 0);

		assertFalse(paging.perAll(), "上限があるのに全件取得のままです");
		assertEquals(Paging.DEFAULT_MAX_PER, paging.per());
		assertEquals(2, paging.page(), "ページ番号を捨てています");

	}

	@Test
	@DisplayName("上限を外せば per=all は全件取得")
	void perAllWithoutCap () {

		Conf.replace(ConfigFactory
			.parseString(Paging.KEY_MAX_PER + " = 0").withFallback(Conf.conf().config()));

		Paging paging = new Paging();
		paging.load(request("2", "all"), 0);

		assertTrue(paging.perAll());
		assertEquals(1, paging.page(), "全件取得は常に1ページ");

	}

	@Test
	@DisplayName("D-159 上限を超える件数は上限に丸める")
	void perIsCapped () {

		Paging paging = new Paging();
		paging.load(request("1", String.valueOf(Paging.DEFAULT_MAX_PER + 500)), 0);

		assertEquals(Paging.DEFAULT_MAX_PER, paging.per(), "上限が効いていません");

	}

	@Test
	@DisplayName("ページング指定の有無を判定する")
	void hasPaging () {

		assertTrue(Paging.hasPaging(request("2", null)));
		assertTrue(Paging.hasPaging(request(null, "20")));
		assertTrue(Paging.hasPaging(request(null, "all")));

		assertFalse(Paging.hasPaging(new Data()));
		assertFalse(Paging.hasPaging(request("", "")));
		assertFalse(Paging.hasPaging(request("abc", "xyz")), "数値でない指定は無視する");

	}

	// endregion

	// region 総件数と最大ページ（F-V-05）

	@Test
	@DisplayName("総件数から最大ページを出す")
	void maxPage () {

		Paging paging = new Paging();
		paging.load(request("1", "10"), 0);

		paging.set(10, 95);

		assertEquals(95, paging.totalCount());
		assertEquals(10, paging.maxPage(), "95 件を 10 件ずつなら 10 ページ");
		assertEquals(10, paging.count());

	}

	@Test
	@DisplayName("割り切れる件数でも最大ページが1つ増えない")
	void maxPageExact () {

		Paging paging = new Paging();
		paging.load(request("1", "10"), 0);

		paging.set(10, 100);

		assertEquals(10, paging.maxPage());

	}

	@Test
	@DisplayName("0件でも最大ページは1")
	void maxPageEmpty () {

		Paging paging = new Paging();
		paging.load(request("1", "10"), 0);

		paging.set(0, 0);

		assertEquals(1, paging.maxPage());

	}

	@Test
	@DisplayName("per=all で0件でもゼロ除算にならない")
	void maxPagePerAllEmpty () {

		Paging paging = new Paging();
		paging.load(request("1", "all"), 0);

		paging.set(0, 0);

		assertEquals(1, paging.maxPage());

	}

	// endregion

	// region キー名の設定（F-V-06）

	@Test
	@DisplayName("リクエストキー名を設定で変えられる")
	void configurableNames () {

		Conf.replace(ConfigFactory
			.parseString("paging.name_page = \"p\"\npaging.name_per = \"limit\"")
			.withFallback(Conf.conf().config()));

		assertEquals("p", Paging.namePage());
		assertEquals("limit", Paging.namePer());

		Data data = new Data();
		data.put("p", "2");
		data.put("limit", "30");

		Paging paging = new Paging();
		paging.load(data, 0);

		assertEquals(2, paging.page());
		assertEquals(30, paging.per());

	}

	@Test
	@DisplayName("設定を読み込み直せば反映される")
	void namesFollowReload () {

		// 移送元は static final でクラス初期化時に1回読むだけだった
		Conf.replace(ConfigFactory.parseString("paging.name_page = \"p\"").withFallback(Conf.conf().config()));
		assertEquals("p", Paging.namePage());

		Conf.reload();
		assertEquals(Paging.DEFAULT_NAME_PAGE, Paging.namePage());

	}

	// endregion

}
