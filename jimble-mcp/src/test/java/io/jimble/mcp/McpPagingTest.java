package io.jimble.mcp;

import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;

import com.typesafe.config.ConfigFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 一覧のページ分け（{@link McpPaging}／要件 F-MCP-14）
 *
 * <p>
 * <b>読めないカーソルを黙って先頭に倒すと、クライアントは同じページを永遠に読み続ける。</b>
 * ページングの壊れ方はたいていこれで、しかも<b>エラーが1つも出ない</b>。
 * ここで固定しておく。
 * </p>
 */
class McpPagingTest {

	@AfterEach
	void reset () {

		Conf.reload();

	}

	/**
	 * 1ページの件数を決める
	 *
	 * @param size 件数
	 */
	private static void pageSize (int size) {

		Conf.replace(ConfigFactory.parseString("mcp { page_size = %d }".formatted(size)));

	}

	/**
	 * 中身を作る
	 *
	 * @param count 件数
	 * @return 中身
	 */
	private static List<Data> items (int count) {

		List<Data> list = new ArrayList<>();

		for (int i = 0; i < count; i++) {
			list.add(new Data().putData("name", "item-" + i));
		}

		return list;

	}

	// region 切れる

	@Test
	@DisplayName("収まっていればカーソルを付けない")
	void fitsInOnePage () throws Exception {

		pageSize(10);

		McpPaging.Page page = McpPaging.of(items(10), null);

		assertEquals(10, page.items().size());

		/*
		 * <b>ここが null であることが、いまのアプリの応答が変わらないことの証拠である。</b>
		 * 既定は 100 件なので、それ以下しか登録していないアプリには nextCursor が付かない
		 */
		assertNull(page.nextCursor());

	}

	@Test
	@DisplayName("溢れたらカーソルを付けて、続きから返す")
	void paginates () throws Exception {

		pageSize(10);

		List<Data> all = items(25);

		McpPaging.Page first = McpPaging.of(all, null);

		assertEquals(10, first.items().size());
		assertEquals("item-0", first.items().get(0).getString("name"));
		assertNotNull(first.nextCursor());

		McpPaging.Page second = McpPaging.of(all, first.nextCursor());

		assertEquals(10, second.items().size());
		assertEquals("item-10", second.items().get(0).getString("name"));
		assertNotNull(second.nextCursor());

		McpPaging.Page third = McpPaging.of(all, second.nextCursor());

		assertEquals(5, third.items().size());
		assertEquals("item-20", third.items().get(0).getString("name"));

		// 最後のページにはカーソルを付けない（付けると1周多く読みにくる）
		assertNull(third.nextCursor());

	}

	@Test
	@DisplayName("全部たどると、1件も落とさず1件も重ならない")
	void coversEverything () throws Exception {

		pageSize(7);

		List<Data> all = items(50);
		List<String> seen = new ArrayList<>();

		String cursor = null;

		for (int guard = 0; guard < 100; guard++) {

			McpPaging.Page page = McpPaging.of(all, cursor);

			for (Data item : page.items()) {
				seen.add(item.getString("name"));
			}

			cursor = page.nextCursor();

			if (cursor == null) {
				break;
			}

		}

		assertEquals(50, seen.size());
		assertEquals(50, seen.stream().distinct().count(), "同じものを2度返している");
		assertEquals("item-0", seen.get(0));
		assertEquals("item-49", seen.get(49));

	}

	@Test
	@DisplayName("空でも落ちない")
	void empty () throws Exception {

		pageSize(10);

		McpPaging.Page page = McpPaging.of(List.of(), null);

		assertTrue(page.items().isEmpty());
		assertNull(page.nextCursor());

	}

	// endregion

	// region カーソル

	@Test
	@DisplayName("カーソルは中身が読めない形にする")
	void cursorIsOpaque () throws Exception {

		pageSize(10);

		String cursor = McpPaging.of(items(25), null).nextCursor();

		/*
		 * 仕様はカーソルを<b>不透明な文字列</b>と定めていて、
		 * クライアントは中身を読んだり組み立てたりしてはいけない。
		 * 「10」がそのまま見えていると、読める形だと思われる
		 */
		assertFalse(cursor.contains("10"), cursor);

	}

	@Test
	@DisplayName("読めないカーソルは断る（黙って先頭に倒さない）")
	void invalidCursor () {

		pageSize(10);

		List<Data> all = items(25);

		for (String broken : new String[]{"こわれている", "!!!!", "AAAA", "eyJwYWdlIjogM30="}) {
			assertThrows(McpPaging.McpPagingException.class
				, () -> McpPaging.of(all, broken), broken);
		}

	}

	@Test
	@DisplayName("範囲の外を指すカーソルも断る")
	void outOfRange () throws Exception {

		pageSize(10);

		String cursor = McpPaging.of(items(25), null).nextCursor();

		/*
		 * <b>登録が減ったあとに古いカーソルで来ることがある。</b>
		 * 黙って空を返すと、クライアントは「そこで終わった」と思う——
		 * 実際には先頭に残っているものが読まれていない
		 */
		assertThrows(McpPaging.McpPagingException.class, () -> McpPaging.of(items(5), cursor));

	}

	@Test
	@DisplayName("空文字は「先頭から」ではなく「カーソル無し」と同じに扱う")
	void emptyCursor () throws Exception {

		pageSize(10);

		assertEquals("item-0", McpPaging.of(items(25), "").items().get(0).getString("name"));

	}

	// endregion

	// region 件数

	@Test
	@DisplayName("件数は設定で決まる。既定は 100")
	void pageSizeFromConf () {

		Conf.replace(ConfigFactory.empty());
		assertEquals(McpPaging.DEFAULT_PAGE_SIZE, McpPaging.pageSize());

		pageSize(5);
		assertEquals(5, McpPaging.pageSize());

		// 0 以下は既定に倒す（0 にすると1件も返らないまま延々と回る）
		pageSize(0);
		assertEquals(McpPaging.DEFAULT_PAGE_SIZE, McpPaging.pageSize());

	}

	// endregion

}
