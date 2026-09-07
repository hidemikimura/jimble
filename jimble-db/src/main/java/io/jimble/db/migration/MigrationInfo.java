package io.jimble.db.migration;

import java.io.File;

/**
 * マイグレーション SQL ファイルの所在
 *
 * <p>
 * jar の外で動いているときは {@link #sqlFile}、jar の中で動いているときは
 * {@link #sqlFilePathInJar} のどちらか一方だけが入る。
 * </p>
 */
public class MigrationInfo {

	/** SQL ファイル名（`migration/&lt;schema&gt;/` からの相対。DB の {@code migration.name} と一致する） */
	public String sqlFileName;

	/** jar 内のパス（jar 実行のときだけ設定される） */
	public String sqlFilePathInJar;

	/** SQL ファイル（jar 外実行のときだけ設定される） */
	public File sqlFile;

	/** jar を開くためのクラスローダ（jar 実行のときだけ設定される） */
	public ClassLoader classLoader;

}
