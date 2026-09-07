package io.jimble.db.sql;

import io.jimble.db.sql.definition.column.Column;
import io.jimble.db.sql.definition.schema.AbstractSchema;
import io.jimble.db.sql.definition.table.Table;

/**
 * テスト用のスキーマとテーブル定義
 *
 * <p>本番では {@code jimble-codegen} が同じ形のクラスを生成する。</p>
 */
public final class TestSchema extends AbstractSchema {

	/* インスタンス */
	public static final TestSchema INSTANCE = new TestSchema();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String name () {

		return "jimble_test";

	}

	/**
	 * site テーブル
	 */
	public static final class Site extends Table {

		/** id */
		public static final Column id = new Column(instance(), "id", long.class, false, null, true);

		/** group_id */
		public static final Column group_id = new Column(instance(), "group_id", long.class, false, null, false);

		/** name */
		public static final Column name = new Column(instance(), "name", String.class, true, null, false);

		/** feed_count */
		public static final Column feed_count = new Column(instance(), "feed_count", long.class, false, 0L, false);

		/** deleted_at */
		public static final Column deleted_at = new Column(instance(), "deleted_at", java.util.Date.class, true, null, false);

		/**
		 * コンストラクタ
		 */
		private Site () {

			super(INSTANCE, "site");

		}

		/**
		 * インスタンス
		 *
		 * @return	インスタンス
		 */
		public static Site instance () {

			return new Site();

		}

	}

	/**
	 * feed テーブル
	 */
	public static final class Feed extends Table {

		/** id */
		public static final Column id = new Column(instance(), "id", long.class, false, null, true);

		/** site_id */
		public static final Column site_id = new Column(instance(), "site_id", long.class, false, null, false);

		/** title */
		public static final Column title = new Column(instance(), "title", String.class, true, null, false);

		/**
		 * コンストラクタ
		 */
		private Feed () {

			super(INSTANCE, "feed");

		}

		/**
		 * インスタンス
		 *
		 * @return	インスタンス
		 */
		public static Feed instance () {

			return new Feed();

		}

	}

}
