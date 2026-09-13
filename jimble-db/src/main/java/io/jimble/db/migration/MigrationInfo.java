package io.jimble.db.migration;

import java.io.File;

/**
 * マイグレーション SQL ファイルの所在
 *
 * <p>
 * <b>2つの形しかない。</b>jar の外で動いているときは {@link #ofFile}、
 * jar の中で動いているときは {@link #ofJar} である。
 * </p>
 *
 * <p>
 * <b>public フィールドではない（D-173）。</b>フィールドのままだと
 * <b>どちらの形なのかを名乗る場所が無く</b>、4本を1本ずつ埋める形になる——
 * 3本だけ埋めた中途半端なものが作れてしまう。
 * 入口を2つに分けたので、<b>形の組み合わせを間違える道が消えた</b>。
 * フィールドは 1.0 のあとアクセサに置き換えられないので、いま直しておく。
 * </p>
 */
public final class MigrationInfo {

	/** SQL ファイル名（{@code migration/<schema>/} からの相対。DB の {@code migration.name} と一致する） */
	private final String sqlFileName;

	/** jar 内のパス（jar 実行のときだけ入る） */
	private final String sqlFilePathInJar;

	/** SQL ファイル（jar 外実行のときだけ入る） */
	private final File sqlFile;

	/** jar を開くためのクラスローダ（jar 実行のときだけ入る） */
	private final ClassLoader classLoader;

	/**
	 * @param sqlFileName		SQL ファイル名
	 * @param sqlFilePathInJar	jar 内のパス
	 * @param sqlFile			SQL ファイル
	 * @param classLoader		クラスローダ
	 */
	private MigrationInfo (String sqlFileName, String sqlFilePathInJar, File sqlFile, ClassLoader classLoader) {

		this.sqlFileName = sqlFileName;
		this.sqlFilePathInJar = sqlFilePathInJar;
		this.sqlFile = sqlFile;
		this.classLoader = classLoader;

	}

	/**
	 * jar の外にある SQL ファイル
	 *
	 * @param sqlFileName	SQL ファイル名
	 * @param sqlFile		SQL ファイル
	 * @return	所在
	 */
	public static MigrationInfo ofFile (String sqlFileName, File sqlFile) {

		return new MigrationInfo(sqlFileName, null, sqlFile, null);

	}

	/**
	 * jar の中にある SQL ファイル
	 *
	 * @param sqlFileName		SQL ファイル名
	 * @param sqlFilePathInJar	jar 内のパス
	 * @param classLoader		jar を開くためのクラスローダ
	 * @return	所在
	 */
	public static MigrationInfo ofJar (String sqlFileName, String sqlFilePathInJar, ClassLoader classLoader) {

		return new MigrationInfo(sqlFileName, sqlFilePathInJar, null, classLoader);

	}

	/**
	 * SQL ファイル名
	 *
	 * @return	ファイル名
	 */
	public String sqlFileName () {

		return sqlFileName;

	}

	/**
	 * jar 内のパス
	 *
	 * @return	パス（jar 外実行なら null）
	 */
	public String sqlFilePathInJar () {

		return sqlFilePathInJar;

	}

	/**
	 * SQL ファイル
	 *
	 * @return	ファイル（jar 実行なら null）
	 */
	public File sqlFile () {

		return sqlFile;

	}

	/**
	 * jar を開くためのクラスローダ
	 *
	 * @return	クラスローダ（jar 外実行なら null）
	 */
	public ClassLoader classLoader () {

		return classLoader;

	}

	/**
	 * jar の中にあるか
	 *
	 * @return	jar 内なら true
	 */
	public boolean inJar () {

		return sqlFilePathInJar != null;

	}

	@Override
	public String toString () {

		return "MigrationInfo(" + sqlFileName + (inJar() ? " in jar" : "") + ")";

	}

}
