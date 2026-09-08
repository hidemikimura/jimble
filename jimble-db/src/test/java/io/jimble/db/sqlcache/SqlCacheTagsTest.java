package io.jimble.db.sqlcache;

import io.jimble.db.sql.SQL;
import io.jimble.db.sql.query.dsl.Dsl;
import io.jimble.db.sqlcache.SqlCacheSchema.Customer;
import io.jimble.db.sqlcache.SqlCacheSchema.OrderItem;
import io.jimble.db.sqlcache.SqlCacheSchema.Shop;
import io.jimble.util.data.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * キャッシュの依存タグ（要件 F-D-28）
 *
 * <p>
 * <b>DB は要らない。</b>ビルダーと結果の形だけで決まる。
 * </p>
 */
class SqlCacheTagsTest {

	/** データベース名（タグの前に付く） */
	private static final String DB_NAME = "jimble_test";

	// region 小物

	/**
	 * 結合した1行を作る（SELECT の結果はテーブル名でネストする。要件 F-D-02）
	 *
	 * @param customerId	顧客ID
	 * @param shopId		店舗ID
	 * @return	行
	 */
	private static Data row (long customerId, long shopId) {

		Data customer = new Data();
		customer.put("id", customerId);
		customer.put("shop_id", shopId);
		customer.put("code", "C" + customerId);
		customer.put("name", "顧客" + customerId);

		Data shop = new Data();
		shop.put("id", shopId);
		shop.put("name", "店" + shopId);

		Data row = new Data();
		row.put("customer", customer);
		row.put("shop", shop);

		return row;

	}

	// endregion

	@Test
	@DisplayName("PK で1件引いた結合の依存は、両テーブルの行タグだけ")
	void joinedByPrimaryKey () {

		Set<String> tags = SqlCacheTags.of(DB_NAME,
			SQL.select()
				.from(Customer.instance())
				.inner(Shop.instance()).on(Customer.shop_id.eq(Shop.id))
				.where(Customer.id.eq(1))
			, List.of(row(1, 1)));

		assertTrue(tags.contains("jimble_test/customer#id#1"), tags.toString());
		assertTrue(tags.contains("jimble_test/customer#code#c1"), tags.toString());
		assertTrue(tags.contains("jimble_test/shop#id#1"), tags.toString());

		/*
		 * テーブルタグが付かないことが要点。
		 * 付くと customer.id = 2 の更新でもこのキャッシュが消えてしまう。
		 */
		assertFalse(tags.contains("jimble_test/customer#*"), tags.toString());
		assertFalse(tags.contains("jimble_test/shop#*"), tags.toString());

		// 「このテーブルを読んでいる」印は付く（絞れない更新に備えて）
		assertTrue(tags.contains("jimble_test/customer#@"), tags.toString());
		assertTrue(tags.contains("jimble_test/shop#@"), tags.toString());

	}

	@Test
	@DisplayName("キーで絞っていない SELECT にはテーブルタグが付く")
	void notPinned () {

		Set<String> tags = SqlCacheTags.of(DB_NAME,
			SQL.select()
				.from(Customer.instance())
				.where(Customer.shop_id.eq(1))
			, List.of(row(1, 1)));

		// INSERT で行が増えると結果が変わる
		assertTrue(tags.contains("jimble_test/customer#*"), tags.toString());

	}

	@Test
	@DisplayName("1対Nの結合先にはテーブルタグが付く")
	void oneToManyJoin () {

		Data row = row(1, 1);
		Data item = new Data();
		item.put("id", 10L);
		item.put("customer_id", 1L);
		item.put("amount", 100L);
		row.put("order_item", item);

		Set<String> tags = SqlCacheTags.of(DB_NAME,
			SQL.select()
				.from(Customer.instance())
				.left(OrderItem.instance()).on(OrderItem.customer_id.eq(Customer.id))
				.where(Customer.id.eq(1))
			, List.of(row));

		// customer は1行に決まる
		assertTrue(tags.contains("jimble_test/customer#id#1"), tags.toString());
		assertFalse(tags.contains("jimble_test/customer#*"), tags.toString());

		/*
		 * order_item は customer_id で結合しているだけ（キーではない）。
		 * <b>注文が1件増えると結果が変わる</b>ので、テーブルごと見る。
		 */
		assertTrue(tags.contains("jimble_test/order_item#*"), tags.toString());

	}

	@Test
	@DisplayName("キーの列を SELECT していなければテーブルタグに倒す")
	void missingKeyColumn () {

		Data customer = new Data();
		customer.put("id", 1L);
		customer.put("name", "顧客1");   // code（一意）を引いていない

		Data row = new Data();
		row.put("customer", customer);

		Set<String> tags = SqlCacheTags.of(DB_NAME,
			SQL.select(Customer.id, Customer.name)
				.from(Customer.instance())
				.where(Customer.id.eq(1))
			, List.of(row));

		/*
		 * code で絞った更新（WHERE code = 'C1'）を取りこぼすので、
		 * テーブルごと見るしかない。
		 */
		assertTrue(tags.contains("jimble_test/customer#*"), tags.toString());

	}

	@Test
	@DisplayName("0件の結果はテーブルタグだけ")
	void emptyResult () {

		Set<String> tags = SqlCacheTags.of(DB_NAME,
			SQL.select().from(Customer.instance()).where(Customer.id.eq(999))
			, List.of());

		assertTrue(tags.contains("jimble_test/customer#*"), tags.toString());

	}

	@Test
	@DisplayName("OR が混ざっていたら絞り込めていない扱いにする")
	void orIsNotNarrowing () {

		Set<String> tags = SqlCacheTags.of(DB_NAME,
			SQL.select()
				.from(Customer.instance())
				.where(Customer.id.eq(1).or(Customer.name.eq("顧客2")))
			, List.of(row(1, 1)));

		assertTrue(tags.contains("jimble_test/customer#*"), tags.toString());

	}

	// region 更新側

	@Test
	@DisplayName("PK で絞った UPDATE はその行タグを消す")
	void updateByPrimaryKey () {

		Set<String> tags = SqlCacheTags.of(DB_NAME,
			SQL.update(Customer.instance()).set(Customer.name, "name1").where(Customer.id.eq(1)));

		assertTrue(tags.contains("jimble_test/customer#id#1"), tags.toString());
		assertFalse(tags.contains("jimble_test/customer#id#2"), tags.toString());

	}

	@Test
	@DisplayName("一意列で絞った UPDATE もその行タグを消す")
	void updateByUniqueKey () {

		Set<String> tags = SqlCacheTags.of(DB_NAME,
			SQL.update(Customer.instance()).set(Customer.name, "name1").where(Customer.code.eq("C1")));

		assertTrue(tags.contains("jimble_test/customer#code#c1"), tags.toString());

	}

	@Test
	@DisplayName("IN で絞った UPDATE は並べたぶんだけ消す")
	void updateByIn () {

		Set<String> tags = SqlCacheTags.of(DB_NAME,
			SQL.update(Customer.instance()).set(Customer.name, "x").where(Customer.id.in(List.of(1, 3))));

		assertTrue(tags.contains("jimble_test/customer#id#1"), tags.toString());
		assertTrue(tags.contains("jimble_test/customer#id#3"), tags.toString());
		assertFalse(tags.contains("jimble_test/customer#id#2"), tags.toString());

	}

	@Test
	@DisplayName("NOT IN で絞った UPDATE はテーブルごと消す（IN と同じ扱いにしない）")
	void updateByNotIn () {

		/*
		 * IN (1, 3) は「1 か 3 の行」だが、NOT IN (1, 3) は<b>それ以外の全部</b>。
		 * 1 と 3 の行タグだけを消すと、<b>本当に書き換わった行のキャッシュが残る</b>
		 * （古い値が返り続ける）。読めるようにはしたが、絞り込みには使わない。
		 */
		Set<String> tags = SqlCacheTags.of(DB_NAME,
			SQL.update(Customer.instance()).set(Customer.name, "x").where(Customer.id.not_in(List.of(1, 3))));

		assertTrue(tags.contains("jimble_test/customer#*"), tags.toString());
		assertFalse(tags.contains("jimble_test/customer#id#1"), tags.toString());
		assertFalse(tags.contains("jimble_test/customer#id#3"), tags.toString());

	}

	@Test
	@DisplayName("キーでない列で絞った UPDATE はテーブルごと消す")
	void updateByNonKey () {

		Set<String> tags = SqlCacheTags.of(DB_NAME,
			SQL.update(Customer.instance()).set(Customer.name, "x").where(Customer.shop_id.eq(1)));

		assertTrue(tags.contains("jimble_test/customer#*"), tags.toString());
		assertTrue(tags.contains("jimble_test/customer#@"), tags.toString());
		assertFalse(tags.contains("jimble_test/customer#id#1"), tags.toString());

	}

	@Test
	@DisplayName("INSERT は行が増えるのでテーブルごと消す")
	void insertClearsTable () {

		Set<String> tags = SqlCacheTags.of(DB_NAME,
			SQL.insert(Customer.instance()).value(Customer.name, "新しい人"));

		assertTrue(tags.contains("jimble_test/customer#*"), tags.toString());

		// 1行に絞ってあるキャッシュは、増えた行が入りようがないので消さない
		assertFalse(tags.contains("jimble_test/customer#@"), tags.toString());

	}

	@Test
	@DisplayName("DELETE も PK で絞れる")
	void deleteByPrimaryKey () {

		Set<String> tags = SqlCacheTags.of(DB_NAME,
			SQL.delete(Customer.instance()).where(Customer.id.eq(7)));

		assertTrue(tags.contains("jimble_test/customer#id#7"), tags.toString());

	}

	// endregion

	// region レビューで出た取りこぼし

	@Test
	@DisplayName("外側の OR も絞り込めていない扱いにする")
	void topLevelOrIsNotNarrowing () {

		// WHERE (customer.id = 1) OR (customer.shop_id = 5)
		Set<String> tags = SqlCacheTags.of(DB_NAME,
			SQL.update(Customer.instance()).set(Customer.name, "x")
				.where(Customer.id.eq(1), Dsl.or(Customer.shop_id.eq(5))));

		assertTrue(tags.contains("jimble_test/customer#@"), tags.toString());
		assertFalse(tags.contains("jimble_test/customer#id#1"), tags.toString());

	}

	@Test
	@DisplayName("ON DUPLICATE KEY UPDATE は既存の行も書き換えるので全部消す")
	void upsertClearsEverything () {

		Set<String> tags = SqlCacheTags.of(DB_NAME,
			SQL.insert(Customer.instance())
				.value(Customer.id, 1)
				.value(Customer.name, "新")
				.onDuplicateKeyUpdate(Customer.name, "新"));

		assertTrue(tags.contains("jimble_test/customer#@"), tags.toString());

	}

	@Test
	@DisplayName("IN にサブクエリを渡したものは「1行に決まっている」と見なさない")
	void inSubQueryIsNotPinned () {

		Set<String> tags = SqlCacheTags.of(DB_NAME,
			SQL.select()
				.from(Customer.instance())
				.where(Customer.id.in(
					SQL.select(OrderItem.customer_id).from(OrderItem.instance())))
			, List.of(row(1, 1)));

		assertTrue(tags.contains("jimble_test/customer#*"), tags.toString());

	}

	@Test
	@DisplayName("主キー同士の結合だけでは、どちらも決まっていない")
	void keyToKeyJoinWithoutWhere () {

		Data row = row(1, 1);

		Set<String> tags = SqlCacheTags.of(DB_NAME,
			SQL.select()
				.from(Customer.instance())
				.inner(Shop.instance()).on(Customer.id.eq(Shop.id))
			, List.of(row));

		/*
		 * WHERE が無いので両方とも全件である。
		 * 「相手も決まっているか」を見ないと、両方とも決まっている扱いになり、
		 * <b>INSERT で増えた行が出てこない</b>。
		 */
		assertTrue(tags.contains("jimble_test/customer#*"), tags.toString());
		assertTrue(tags.contains("jimble_test/shop#*"), tags.toString());

	}

	@Test
	@DisplayName("LIMIT を付けたら、行が決まっていても一覧タグを付ける")
	void limitIsNotPinned () {

		Set<String> tags = SqlCacheTags.of(DB_NAME,
			SQL.select()
				.from(Customer.instance())
				.where(Customer.id.in(List.of(1, 2, 3)))
				.orderByAsc(Customer.name)
				.limit(1)
			, List.of(row(1, 1)));

		/*
		 * 別の行の名前が変わるだけで、返る行が入れ替わる。
		 * 「id = 1 の行にしか依存していない」とは言えない。
		 */
		assertTrue(tags.contains("jimble_test/customer#*"), tags.toString());

	}

	@Test
	@DisplayName("日時と byte[] のキーもそろう")
	void normalizeDateAndBytes () {

		java.util.Date date = new java.util.Date(1_700_000_000_123L);
		java.sql.Timestamp timestamp = new java.sql.Timestamp(1_700_000_000_123L);

		assertEquals(SqlCacheTags.normalize(date), SqlCacheTags.normalize(timestamp));

		assertEquals(SqlCacheTags.normalize(new byte[]{1, 2, 3})
			, SqlCacheTags.normalize(new byte[]{1, 2, 3}));

	}

	// endregion

	@Test
	@DisplayName("1 と 1L と \"1\" は同じタグになる")
	void valuesAreNormalized () {

		assertTrue(SqlCacheTags.of(DB_NAME,
			SQL.update(Customer.instance()).set(Customer.name, "x").where(Customer.id.eq(1)))
			.contains("jimble_test/customer#id#1"));

		assertTrue(SqlCacheTags.of(DB_NAME,
			SQL.update(Customer.instance()).set(Customer.name, "x").where(Customer.id.eq(1L)))
			.contains("jimble_test/customer#id#1"));

		assertTrue(SqlCacheTags.of(DB_NAME,
			SQL.update(Customer.instance()).set(Customer.name, "x").where(Customer.id.eq("1")))
			.contains("jimble_test/customer#id#1"));

	}

}
