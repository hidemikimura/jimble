package io.jimble.db.generator;

import io.jimble.util.conf.Conf;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * コード生成の設定
 *
 * <pre>
 * codegen {
 *   # 生成先のパッケージ（既定 "db"）
 *   package = "db"
 *   # 生成対象から外すテーブル（jimble の管理テーブルは指定しなくても外れる）
 *   exclude_tables = ["legacy_import"]
 * }
 * </pre>
 */
public final class GeneratorConf {

	/** 設定キー：生成先パッケージ */
	public static final String KEY_PACKAGE = "codegen.package";

	/** 設定キー：生成対象から外すテーブル */
	public static final String KEY_EXCLUDE_TABLES = "codegen.exclude_tables";

	/** 既定の生成先パッケージ */
	public static final String DEFAULT_PACKAGE = "db";

	/**
	 * jimble 自身が作る管理テーブル
	 *
	 * <p>
	 * <b>アプリのテーブル定義には出さない。</b>
	 * 移送元は {@code SHOW TABLE STATUS} の結果をそのまま生成対象にしていたため、
	 * フレームワークの内部テーブルまでアプリのコードに現れていた。
	 * </p>
	 */
	public static final Set<String> FRAMEWORK_TABLES = Set.of(
		"migration"
		, "migration_history"
		, "migration_code"
		, "db_lock"
		, "db_log"
		, "db_value"
		, "db_cache"
		, "db_sticky"
		, "redis_lock"
		/*
		 * バッチの3つも jimble が作るものである（jimble-batch）。
		 * 名前が固定なのでここに書ける。
		 * MQ のテーブルは名前をアプリが決めるので（mq_blog など）ここには書けない。
		 * 要らなければ codegen.exclude_tables に足す。
		 */
		, "batch_master"
		, "batch_history"
		, "batch_execute_info"
	);

	private GeneratorConf () {}

	/**
	 * 生成先パッケージ
	 *
	 * @return	パッケージ名
	 */
	public static String packageName () {

		String value = Conf.conf().getString(KEY_PACKAGE, DEFAULT_PACKAGE);

		return value == null || value.isEmpty() ? DEFAULT_PACKAGE : value;

	}

	/**
	 * 生成対象から外すテーブル
	 *
	 * <p>jimble の管理テーブルに、設定で足したものを合わせて返す。</p>
	 *
	 * @return	テーブル名（小文字）
	 */
	public static Set<String> excludeTables () {

		Set<String> excludes = new LinkedHashSet<>(FRAMEWORK_TABLES);

		List<String> configured = Conf.conf().getStringListOptional(KEY_EXCLUDE_TABLES);
		for (String table : configured) {
			if (table != null && !table.isEmpty()) {
				excludes.add(table.toLowerCase());
			}
		}

		return excludes;

	}

}
