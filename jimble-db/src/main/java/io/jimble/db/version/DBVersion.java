package io.jimble.db.version;

import io.jimble.util.parse.Parse;
import io.jimble.util.data.Data;
import io.jimble.db.DB;
import io.jimble.db.generator.info.TableInfo;
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
		List<Data> tableList = db.selectList("SHOW TABLE STATUS");
		for (Data table : tableList) {
			TableInfo tableInfo = new TableInfo();
			tableInfo.name = table.getString("Name");
			tableInfo.comment = table.getStringOptional("Comment");
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
	 * SQLを追加する
	 *
	 * @param version   バージョン
	 * @param sqls      SQL
	 */
	public void add (long version, String...sqls) {

		Version versionObj = new Version();
		versionObj.version = version;
		versionObj.sqlList.addAll(Arrays.asList(sqls));
		versionList.add(versionObj);

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

		long applyVersion = nowVersion;
		for (Version version : versionList) {
			if (version.version <= nowVersion) {
				continue;
			}
			String comment = tableComment + ":" + version.version;
			for (String sql : version.sqlList) {
				db.execute(sql.replace(PLACEHOLDER, comment));
				if (db.isError()) {
					Log.error(db.getError());
					return false;
				}
			}
			applyVersion = version.version;
		}

		db.execute("""
			alter table `%s` comment '%s';
			""".formatted(tableName, tableComment + ":" + applyVersion)
		);

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
	private static class Version {

		/* バージョン */
		long version;

		/* SQL */
		List<String> sqlList = new ArrayList<>();

	}

}
