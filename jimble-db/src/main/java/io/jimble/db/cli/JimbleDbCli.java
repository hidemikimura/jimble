package io.jimble.db.cli;

import io.jimble.db.DBSource;
import io.jimble.db.DBUtil;
import io.jimble.db.internal.generator.Generator;
import io.jimble.db.internal.generator.GeneratorConf;
import io.jimble.db.migration.Migration;
import io.jimble.util.conf.Conf;
import io.jimble.util.log.Log;

import java.io.File;

/**
 * マイグレーションとコード生成の CLI
 *
 * <p>
 * CI や本番デプロイから、アプリを起動せずに実行するための入口（要件 F-G-08）。
 * ステップ7の Gradle タスクもこれを呼ぶだけにする。
 * </p>
 *
 * <pre>
 * java -cp ... io.jimble.db.cli.JimbleDbCli migrate
 * java -cp ... io.jimble.db.cli.JimbleDbCli codegen src/main/java
 * </pre>
 *
 * <p>
 * SQL ファイルとテーブル定義はクラスパスから探すので、
 * <b>アプリの {@code resources} をクラスパスに載せて起動すること。</b>
 * </p>
 */
public final class JimbleDbCli {

	/** 終了コード：失敗 */
	private static final int EXIT_FAILURE = 1;

	private JimbleDbCli () {}

	/**
	 * エントリポイント
	 *
	 * @param args	{@code migrate} または {@code codegen <出力ディレクトリ>}
	 */
	public static void main (String[] args) {

		if (args.length == 0) {
			usage();
			System.exit(EXIT_FAILURE);
			return;
		}

		try {

			switch (args[0]) {
				case "migrate" -> migrate();
				case "codegen" -> codegen(args);
				default -> {
					usage();
					System.exit(EXIT_FAILURE);
				}
			}

		} catch (Exception ex) {

			Log.error(ex);
			System.err.println(ex.getMessage());
			System.exit(EXIT_FAILURE);

		} finally {

			DBUtil.stop();

		}

	}

	/**
	 * マイグレーションを適用する
	 *
	 * <p>
	 * <b>SQL マイグレーションだけを行う。</b>コードマイグレーションはアプリのクラスを
	 * 登録して初めて動くので（D-19）、アプリの起動時に実行される。
	 * </p>
	 */
	private static void migrate () {

		load();

		for (DBSource dbSource : DBUtil.getDataSourceList()) {
			Log.info("migrate: " + dbSource.name);
			Migration.migrate(dbSource, JimbleDbCli.class);
		}

		Log.info("migrate: 完了");

	}

	/**
	 * テーブル定義のコードを生成する
	 *
	 * @param args	コマンドライン引数
	 */
	private static void codegen (String[] args) {

		if (args.length < 2) {
			usage();
			System.exit(EXIT_FAILURE);
			return;
		}

		load();

		File outputDir = outputDir(new File(args[1]), GeneratorConf.packageName());
		Log.info("codegen: " + outputDir);

		Generator.generate(outputDir, GeneratorConf.packageName());

		Log.info("codegen: 完了");

	}

	/**
	 * 設定と DB を読み込む
	 *
	 * <p>
	 * ここでは起動時マイグレーションを組み込まない（{@link Migration#install()} を呼ばない）。
	 * <b>CLI は指示されたことだけをする。</b>
	 * </p>
	 */
	private static void load () {

		Conf.reload();

		if (!DBUtil.load(Conf.conf().config(), JimbleDbCli.class)) {
			throw new IllegalStateException("DB に接続できませんでした（env=%s）".formatted(Conf.env()));
		}

	}

	/**
	 * パッケージ名を掘った出力先
	 *
	 * @param sourceRoot	ソースルート（例 {@code src/main/java}）
	 * @param packageName	パッケージ名（例 {@code com.example.db}）
	 * @return	出力先
	 */
	private static File outputDir (File sourceRoot, String packageName) {

		File dir = sourceRoot;
		for (String part : packageName.split("\\.")) {
			dir = new File(dir, part);
		}

		return dir;

	}

	/**
	 * 使い方を出す
	 */
	private static void usage () {

		System.err.println("""
			使い方:
			  migrate                    未適用のマイグレーションを適用する
			  codegen <ソースルート>      テーブル定義のコードを生成する（例: src/main/java）
			""");

	}

}
