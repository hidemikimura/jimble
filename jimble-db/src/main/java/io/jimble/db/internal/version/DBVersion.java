package io.jimble.db.internal.version;

import io.jimble.util.parse.Parse;
import io.jimble.util.data.Data;
import io.jimble.db.DB;
import io.jimble.db.internal.generator.info.TableInfo;
import io.jimble.db.dialect.DialectException;
import io.jimble.db.dialect.MySqlDialect;
import io.jimble.db.dialect.PostgreSqlDialect;
import io.jimble.util.log.Log;

import java.util.*;

/**
 * DBバージョン
 */
public class DBVersion {

	/* DBテーブル情報一覧 */
	private final static Map<String, Map<String, TableInfo>> dbTableInfoMap = new HashMap<>();

	/**
	 * DBテーブル情報を読み込む
	 *
	 * @param db    DB
	 */
	public static void load (DB db) {

		if (dbTableInfoMap.containsKey(db.getDBName())) {
			return;
		}

		Map<String, TableInfo> tableInfoMap = new HashMap<>();

		/*
		 * テーブル名とコメントの引き方は製品で違う（要件 F-D-30）。
		 * MySQL は information_schema.TABLES、PostgreSQL は pg_class + obj_description。
		 * <b>「コメントに版番号を書く」という仕組み自体は両方にある</b>ので、
		 * 引き方だけを方言に寄せて、仕組みは1つのままにしてある。
		 */
		List<Data> tableList = db.selectList(db.dialect().tableCommentsSql());

		if (tableList == null) {
			Log.error("テーブル一覧を引けませんでした: " + db.getDBName());
			tableList = List.of();
		}

		for (Data table : tableList) {
			TableInfo tableInfo = new TableInfo();
			tableInfo.name = table.getString("table_name");
			tableInfo.comment = table.getStringOptional("table_comment");
			{
				int index = tableInfo.comment.lastIndexOf(":");
				if (index > 0) {
					tableInfo.version = Parse.parseLong(tableInfo.comment.substring(index + 1));
					if (tableInfo.version < 1) {
						tableInfo.version = 1;
					}
				}
			}
			tableInfoMap.put(tableInfo.name.toLowerCase(), tableInfo);
		}
		dbTableInfoMap.put(db.getDBName(), tableInfoMap);

	}

	/* コメントプレースホルダー */
	private static final String PLACEHOLDER = "{COMMENT}";

	/* テーブル名 */
	private final String tableName;

	/* コメント */
	private final String tableComment;

	/* バージョン一覧 */
	private final List<Version> versionList = new ArrayList<>();

	/**
	 * コンストラクタ
	 *
	 * @param tableName     テーブル名
	 * @param tableComment  コメント
	 */
	public DBVersion (String tableName, String tableComment) {

		this.tableName = tableName;
		this.tableComment = tableComment;

	}

	/**
	 * プレースホルダー
	 *
	 * @return  プレースホルダー
	 */
	public String placeholder () {

		return PLACEHOLDER;

	}

	/**
	 * SQLを追加する（MySQL 用。要件 F-D-30）
	 *
	 * <p>
	 * <b>製品を書かないものは MySQL 扱いになる。</b>
	 * ほかの製品で動かすと、その版の SQL が無いと言って止まる
	 * （黙って MySQL の DDL を投げて読めない構文エラーにしない）。
	 * </p>
	 *
	 * @param version   バージョン
	 * @param sqls      SQL
	 * @return	この版
	 */
	public Version add (long version, String...sqls) {

		return add(version).mysql(sqls);

	}

	/**
	 * 版を足す（要件 F-D-30）
	 *
	 * <pre>
	 * version.add(1)
	 *     .mysql("""
	 *         create table x (...) ENGINE=InnoDB ... COMMENT='%s'
	 *         """.formatted(version.placeholder()))
	 *     .postgresql("""
	 *         create table x (...)
	 *         """);
	 * </pre>
	 *
	 * <p>
	 * <b>製品ごとの DDL を並べて置く。</b>片方だけ直したときに目に入る
	 * （AsyncData の {@code load} / {@code loadBatch} と同じ理由。D-64）。
	 * </p>
	 *
	 * @param version	バージョン
	 * @return	この版
	 */
	public Version add (long version) {

		Version versionObj = new Version();
		versionObj.version = version;
		versionList.add(versionObj);

		return versionObj;

	}

	/**
	 * 適用する
	 *
	 * @param db    DB
	 * @return  正常に終了した場合 = true
	 */
	public boolean apply (DB db) {

		if (!dbTableInfoMap.containsKey(db.getDBName())) {
			return false;
		}

		long nowVersion = 0;
		Map<String, TableInfo> tableInfoMap = dbTableInfoMap.get(db.getDBName());
		if (tableInfoMap.containsKey(tableName.toLowerCase())) {
			TableInfo tableInfo = tableInfoMap.get(tableName.toLowerCase());
			nowVersion = tableInfo.version;
		}

		String product = db.dialect().name();

		long applyVersion = nowVersion;
		for (Version version : versionList) {
			if (version.version <= nowVersion) {
				continue;
			}
			String comment = tableComment + ":" + version.version;
			for (String sql : version.sqlList(product, tableName)) {
				db.execute(sql.replace(PLACEHOLDER, comment));
				if (db.isError()) {
					Log.error(db.getError());
					return false;
				}
			}
			applyVersion = version.version;
		}

		db.execute(db.dialect().setTableCommentSql(tableName, tableComment + ":" + applyVersion));

		// 適用結果をキャッシュに書き戻す
		//
		// 移送元はここを書き戻していなかったため、同じ JVM の中で apply() を2回呼ぶと
		// 1回目に作ったテーブルを2回目も作ろうとして失敗していた。
		// 起動時に1回しか呼ばれない使い方では表面化しなかったが、
		// マイグレーション（何度でも呼べる入口）では毎回失敗する。
		TableInfo applied = tableInfoMap.get(tableName.toLowerCase());
		if (applied == null) {
			applied = new TableInfo();
			applied.name = tableName;
			tableInfoMap.put(tableName.toLowerCase(), applied);
		}
		applied.comment = tableComment + ":" + applyVersion;
		applied.version = applyVersion;

		return true;

	}

	/**
	 * バージョン情報
	 */
	public static class Version {

		/* バージョン */
		long version;

		/* 製品 → SQL */
		private final Map<String, List<String>> byProduct = new LinkedHashMap<>();

		/* 製品を問わない SQL */
		private List<String> anySql = null;

		/**
		 * MySQL / MariaDB 用
		 *
		 * @param sqls	SQL
		 * @return	自分
		 */
		public Version mysql (String...sqls) {

			byProduct.put(MySqlDialect.NAME, Arrays.asList(sqls));
			return this;

		}

		/**
		 * PostgreSQL 用
		 *
		 * @param sqls	SQL
		 * @return	自分
		 */
		public Version postgresql (String...sqls) {

			byProduct.put(PostgreSqlDialect.NAME, Arrays.asList(sqls));
			return this;

		}

		/**
		 * どの製品でも同じ
		 *
		 * @param sqls	SQL
		 * @return	自分
		 */
		public Version any (String...sqls) {

			anySql = Arrays.asList(sqls);
			return this;

		}

		/**
		 * この製品の SQL
		 *
		 * @param product	製品
		 * @param tableName	テーブル名（エラーに出す）
		 * @return	SQL
		 * @throws DialectException	その製品の SQL が無い場合
		 */
		List<String> sqlList (String product, String tableName) {

			List<String> sqls = byProduct.get(product);

			if (sqls != null) {
				return sqls;
			}

			if (anySql != null) {
				return anySql;
			}

			/*
			 * <b>黙って MySQL の DDL を投げない。</b>
			 * 投げると製品の構文エラーが返るだけで、
			 * 「この版の DDL を書いていない」ことに辿り着けない。
			 */
			throw new DialectException(
				"%s の版 %d に %s 用の DDL がありません".formatted(tableName, version, product));

		}

	}

}
