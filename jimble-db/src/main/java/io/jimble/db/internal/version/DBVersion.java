package io.jimble.db.internal.version;

import io.jimble.util.parse.Parse;
import io.jimble.util.data.Data;
import io.jimble.db.DB;
import io.jimble.db.internal.generator.info.TableInfo;
import io.jimble.db.dialect.DialectException;
import io.jimble.db.dialect.MySqlDialect;
import io.jimble.db.dialect.PostgreSqlDialect;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DBバージョン
 */
public class DBVersion {

	/*
	 * DBテーブル情報一覧
	 *
	 * マイグレーションは何本かのスレッドから同時に呼ばれうる（ロックを待つ側も読む）ので、
	 * 外も中も ConcurrentHashMap にしてある
	 */
	private final static Map<String, Map<String, TableInfo>> dbTableInfoMap = new ConcurrentHashMap<>();

	/**
	 * DBテーブル情報を読み込む
	 *
	 * @param db    DB
	 */
	public static void load (DB db) {

		if (dbTableInfoMap.containsKey(db.getDBName())) {
			return;
		}

		dbTableInfoMap.put(db.getDBName(), read(db));

	}

	/**
	 * DBテーブル情報を読み直す
	 *
	 * <p>
	 * {@link #load} は DB ごとに1回しか読まない。<b>ほかのプロセスが表を作ったあと</b>
	 * （何台かが同時に起動して、ロックを待っていたあいだに先の台がマイグレーションの表を作ったなど）や、
	 * テストで表を外から書き換えたあとに、これで読み直す。
	 * 読み終えてから入れ替えるので、読んでいるあいだもほかのスレッドは前の一覧を見る。
	 * </p>
	 *
	 * @param db	DB
	 */
	public static void reload (DB db) {

		dbTableInfoMap.put(db.getDBName(), read(db));

	}

	/**
	 * DB から表の一覧と版を読む
	 *
	 * @param db	DB
	 * @return	表の名前（小文字） → 表の情報
	 */
	private static Map<String, TableInfo> read (DB db) {

		Map<String, TableInfo> tableInfoMap = new ConcurrentHashMap<>();

		/*
		 * テーブル名とコメントの引き方は製品で違う（要件 F-D-30）。
		 * MySQL は information_schema.TABLES、PostgreSQL は pg_class + obj_description。
		 * <b>「コメントに版番号を書く」という仕組み自体は両方にある</b>ので、
		 * 引き方だけを方言に寄せて、仕組みは1つのままにしてある。
		 */
		// 引けなければ例外（1.x は空として進み、あるテーブルを作りにいって失敗していた）
		List<Data> tableList = db.selectList(db.dialect().tableCommentsSql());

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

		return tableInfoMap;

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
	 * @return  適用した場合 = true。この DB の定義を読み込んでいなければ false
	 * @throws io.jimble.db.SqlExecuteException	SQL が失敗したとき
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

		// 表のコメントに書いてある版。下げない
		long recordedVersion = nowVersion;

		for (Version version : versionList) {

			/*
			 * <b>列を足す版は、コメントの版にかかわらず、列があるかで決める</b>（D-302）。
			 * jooby_base から移した表はコメントが「:6」などで jimble の版より大きく、
			 * 版だけで見ると<b>列が足されないまま起動が通っていた</b>（2.5.8 の batch_master で cron が止まった）。
			 * 途中で止まって列だけ残った表でも、同じ ALTER をもう一度流さない（Duplicate column で起動しなくなっていた）
			 */
			if (version.column != null) {

				if (columnExists(db, version.column)) {
					recordedVersion = record(db, version.version, recordedVersion);
					continue;
				}

			} else if (version.version <= nowVersion) {
				continue;
			}

			String comment = tableComment + ":" + version.version;
			for (String sql : version.sqlList(product, tableName)) {
				// 失敗は例外（起動が止まる。2.0。要件 D-193）
				db.execute(sql.replace(PLACEHOLDER, comment));
			}

			recordedVersion = record(db, version.version, recordedVersion);

		}

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
		applied.comment = tableComment + ":" + recordedVersion;
		applied.version = recordedVersion;

		return true;

	}

	/**
	 * 版を1つ当てたら、すぐ表のコメントに書く（D-302）
	 *
	 * <p>
	 * <b>2.5.8 までは、全部の版を当てたあとで1回だけ書いていた。</b>途中で止まると（接続が切れる・プロセスが止められる）、
	 * 当てた版がコメントに残らず、次の起動で同じ DDL を流し直して失敗していた（MySQL の DDL は1本ずつ確定する）。
	 * <b>コメントの版は下げない</b>（jooby_base から移した「:6」の表に版 2 の列を足しても「:6」のまま）。
	 * </p>
	 *
	 * @param db				DB
	 * @param version			当てた版
	 * @param recordedVersion	コメントに書いてある版
	 * @return	コメントに書いてある版（書き換えたなら新しい版）
	 */
	private long record (DB db, long version, long recordedVersion) {

		if (version <= recordedVersion) {
			return recordedVersion;
		}

		db.execute(db.dialect().setTableCommentSql(tableName, tableComment + ":" + version));

		return version;

	}

	/**
	 * 列があるか
	 *
	 * @param db		DB
	 * @param column	列名
	 * @return	ある場合 = true
	 */
	private boolean columnExists (DB db, String column) {

		return db.select(db.dialect().columnExistsSql(), tableName, column)
			.map(row -> row.getLong("cnt") > 0)
			.orElse(false);

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

		/* この版が足す列（あれば、コメントの版ではなく列があるかで当てるかを決める） */
		private String column = null;

		/**
		 * この版は列を1つ足すだけ（D-302）
		 *
		 * <p>
		 * <b>列を足す版には必ず付ける。</b>付けると、表のコメントの版にかかわらず、起動のたびに列があるかを見て、
		 * 無いときだけ DDL を流す。移植元から引き継いだ表（コメントの版が jimble の版より大きい）や、
		 * 途中で止まって列だけ残った表でも正しく当たる。2列足すなら、同じ版番号で2つに分ける。
		 * </p>
		 *
		 * @param name	列名
		 * @return	自分
		 */
		public Version addsColumn (String name) {

			this.column = name;
			return this;

		}

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
