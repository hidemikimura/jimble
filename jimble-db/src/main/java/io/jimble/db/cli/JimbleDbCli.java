package io.jimble.db.cli;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigUtil;

import io.jimble.db.DBConf;
import io.jimble.db.DBUtil;
import io.jimble.db.internal.generator.Generator;
import io.jimble.db.internal.generator.GeneratorConf;
import io.jimble.db.migration.Migration;
import io.jimble.db.migration.MigrationCheck;
import io.jimble.util.conf.Conf;
import io.jimble.util.log.Log;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * マイグレーションとコード生成の CLI
 *
 * <p>
 * CI や本番デプロイから、アプリを起動せずに実行するための入口（要件 F-G-08）。
 * ステップ7の Gradle タスクもこれを呼ぶだけにする。
 * </p>
 *
 * <pre>
 * java -cp ... io.jimble.db.cli.JimbleDbCli migrate [--data-source 名前]... [--migration-dir フォルダ]... [--parallel 数] [--keep-going]
 * java -cp ... io.jimble.db.cli.JimbleDbCli migrateCheck [--data-source 名前]... [--migration-dir フォルダ]... [--parallel 数]
 * java -cp ... io.jimble.db.cli.JimbleDbCli codegen src/main/java
 * </pre>
 *
 * <p>
 * SQL ファイルとテーブル定義はクラスパスから探すので、
 * <b>アプリの {@code resources} をクラスパスに載せて起動すること。</b>
 * </p>
 */
public final class JimbleDbCli {

	/** 終了コード：成功 */
	static final int EXIT_SUCCESS = 0;

	/** 終了コード：失敗 */
	static final int EXIT_FAILURE = 1;

	private JimbleDbCli () {}

	/**
	 * エントリポイント
	 *
	 * @param args	{@code migrate}・{@code migrateCheck}（とオプション）、または {@code codegen <出力ディレクトリ>}
	 */
	public static void main (String[] args) {

		Conf.reload();

		int code = run(args, Conf.conf().config(), System.out);

		if (code != EXIT_SUCCESS) {
			System.exit(code);
		}

	}

	/**
	 * 実行する
	 *
	 * @param args		引数
	 * @param config	設定全体
	 * @param out		結果の一覧を書く先
	 * @return	終了コード
	 */
	static int run (String[] args, Config config, PrintStream out) {

		if (args.length == 0) {
			usage();
			return EXIT_FAILURE;
		}

		List<String> rest = List.of(args).subList(1, args.length);

		try {

			return switch (args[0]) {
				case "migrate" -> migrate(config, MigrateOptions.parse(rest), out);
				case "migrateCheck" -> migrateCheck(config, MigrateOptions.parse(rest), out);
				case "codegen" -> codegen(config, args);
				default -> {
					usage();
					yield EXIT_FAILURE;
				}
			};

		} catch (Exception ex) {

			Log.error(ex);
			System.err.println(ex.getMessage());
			return EXIT_FAILURE;

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
	 *
	 * <p>
	 * 既定は名前の順に1つずつ当て、<b>最初の失敗で止める</b>。{@code --keep-going} なら残りも当て、
	 * 最後にデータソースごとの結果を出す。失敗があれば 0 以外で終わる。
	 * </p>
	 *
	 * @param config	設定全体
	 * @param options	オプション
	 * @param out		結果の一覧を書く先
	 * @return	終了コード
	 */
	private static int migrate (Config config, MigrateOptions options, PrintStream out) {

		List<String> names = load(config, options);

		List<Outcome> outcomes = runEach(names, options, options.keepGoing(), name -> {
			int count = Migration.migrateAndCount(DBUtil.getDataSource(name), JimbleDbCli.class);
			return Outcome.ok(name, "完了（%d 件当てた）".formatted(count));
		});

		return report("migrate", outcomes, out);

	}

	/**
	 * マイグレーションが当たっているかを確かめる（DB には書かない）
	 *
	 * <p>
	 * データソースごとに、フォルダの SQL ファイルと {@code migration} の表を突き合わせ、
	 * 未適用・余分・ハッシュ違いを出す。ずれがあれば 0 以外で終わる。
	 * 同じ {@code migration_dir} のデータソースを並べて見れば、版がそろっているかが分かる。
	 * </p>
	 *
	 * @param config	設定全体
	 * @param options	オプション
	 * @param out		結果の一覧を書く先
	 * @return	終了コード
	 */
	private static int migrateCheck (Config config, MigrateOptions options, PrintStream out) {

		if (options.keepGoing()) {
			throw new IllegalArgumentException("migrateCheck に --keep-going はありません（いつも全部を見ます）");
		}

		List<String> names = load(config, options);

		List<Outcome> outcomes = runEach(names, options, true, name -> {
			MigrationCheck check = Migration.check(DBUtil.getDataSource(name), JimbleDbCli.class);
			return check.isClean() ? Outcome.ok(name, "そろっている") : Outcome.failed(name, describe(check));
		});

		return report("migrateCheck", outcomes, out);

	}

	/**
	 * データソースごとに流す
	 *
	 * @param names		データソース名（この順に始める）
	 * @param options	オプション（並べる数）
	 * @param keepGoing	1 つ失敗してもほかを続ける場合 = true
	 * @param task		1 つ分の処理
	 * @return	データソースごとの結果（names の順）
	 */
	private static List<Outcome> runEach (List<String> names, MigrateOptions options, boolean keepGoing, Function<String, Outcome> task) {

		AtomicBoolean stopped = new AtomicBoolean(false);
		ExecutorService executor = Executors.newFixedThreadPool(Math.min(options.parallel(), names.size()));

		try {

			List<Future<Outcome>> futures = new ArrayList<>();
			for (String name : names) {
				futures.add(executor.submit(() -> {
					if (stopped.get()) {
						return Outcome.skipped(name, "未実行（前の失敗で止めた）");
					}
					Log.info("%s: 始める".formatted(name));
					try {
						return task.apply(name);
					} catch (RuntimeException ex) {
						if (!keepGoing) {
							stopped.set(true);
						}
						Log.error("%s: 失敗しました".formatted(name), ex);
						return Outcome.failed(name, ex.getMessage());
					}
				}));
			}

			List<Outcome> outcomes = new ArrayList<>();
			for (Future<Outcome> future : futures) {
				outcomes.add(future.get());
			}

			return outcomes;

		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("中断されました", ex);
		} catch (ExecutionException ex) {
			throw new IllegalStateException(ex.getCause().getMessage(), ex.getCause());
		} finally {
			executor.shutdownNow();
		}

	}

	/**
	 * 結果の一覧を出す
	 *
	 * @param command	コマンド名
	 * @param outcomes	結果
	 * @param out		書く先
	 * @return	終了コード（成功でないものがあれば失敗）
	 */
	private static int report (String command, List<Outcome> outcomes, PrintStream out) {

		int width = outcomes.stream().mapToInt(outcome -> outcome.dataSource().length()).max().orElse(0);

		String line = "  %-" + Math.max(width, 1) + "s  %s";

		out.println(command + " の結果:");
		for (Outcome outcome : outcomes) {
			out.println(line.formatted(outcome.dataSource(), outcome.detail()));
		}

		boolean success = outcomes.stream().allMatch(Outcome::isOk);
		Log.info("%s: %s".formatted(command, success ? "完了" : "失敗があります"));

		return success ? EXIT_SUCCESS : EXIT_FAILURE;

	}

	/**
	 * ずれを1行にする
	 *
	 * @param check	確かめた結果
	 * @return	説明
	 */
	private static String describe (MigrationCheck check) {

		List<String> parts = new ArrayList<>();
		if (!check.notApplied().isEmpty()) {
			parts.add("未適用: " + String.join(", ", check.notApplied()));
		}
		if (!check.extra().isEmpty()) {
			parts.add("余分（ファイルが無い）: " + String.join(", ", check.extra()));
		}
		if (!check.hashMismatch().isEmpty()) {
			parts.add("ハッシュ違い: " + String.join(", ", check.hashMismatch()));
		}

		return String.join(" / ", parts);

	}

	/**
	 * データソースごとの結果
	 *
	 * @param dataSource	データソース名
	 * @param status		結果
	 * @param detail		説明
	 */
	private record Outcome (String dataSource, Status status, String detail) {

		enum Status { OK, FAILED, SKIPPED }

		static Outcome ok (String dataSource, String detail) {
			return new Outcome(dataSource, Status.OK, detail);
		}

		static Outcome failed (String dataSource, String message) {
			return new Outcome(dataSource, Status.FAILED, "失敗: " + message);
		}

		static Outcome skipped (String dataSource, String detail) {
			return new Outcome(dataSource, Status.SKIPPED, detail);
		}

		boolean isOk () {
			return status == Status.OK;
		}

	}

	/**
	 * テーブル定義のコードを生成する
	 *
	 * @param config	設定全体
	 * @param args		コマンドライン引数
	 * @return	終了コード
	 */
	private static int codegen (Config config, String[] args) {

		if (args.length < 2) {
			usage();
			return EXIT_FAILURE;
		}

		load(config);

		File outputDir = outputDir(new File(args[1]), GeneratorConf.packageName());
		Log.info("codegen: " + outputDir);

		Generator.generate(outputDir, GeneratorConf.packageName());

		Log.info("codegen: 完了");

		return EXIT_SUCCESS;

	}

	/**
	 * 設定と DB を読み込む
	 *
	 * <p>
	 * ここでは起動時マイグレーションを組み込まない（{@link Migration#install()} を呼ばない）。
	 * <b>CLI は指示されたことだけをする。</b>
	 * </p>
	 *
	 * @param config	設定全体
	 */
	private static void load (Config config) {

		try {
			DBUtil.load(config, JimbleDbCli.class);
		} catch (RuntimeException ex) {
			throw new IllegalStateException("DB に接続できませんでした（env=%s）: %s".formatted(Conf.env(), ex.getMessage()), ex);
		}

	}

	/**
	 * 当てる先のデータソースだけを読み込む
	 *
	 * <p>
	 * 絞ったときは、<b>選ばなかったデータソースにはつながない</b>（つながらない別の DB に引きずられて、
	 * どこにも当たらなくならないように）。
	 * </p>
	 *
	 * @param config	設定全体
	 * @param options	オプション
	 * @return	当てる先のデータソース名（名前の順）
	 */
	private static List<String> load (Config config, MigrateOptions options) {

		SortedMap<String, DBConf> confs = DBUtil.readDataSourceConfs(config);
		List<String> selected = options.select(confs);

		Config narrowed = config;
		for (String name : confs.keySet()) {
			if (!selected.contains(name)) {
				narrowed = narrowed.withoutPath(ConfigUtil.joinPath("db", name));
			}
		}

		if (options.isFiltered()) {
			Log.info("当てる先: " + selected);
		}

		load(narrowed);

		return selected;

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
			  migrate [オプション]        未適用のマイグレーションを適用する
			  migrateCheck [オプション]   当たっているかを確かめる（DB には書かない）。ずれがあれば 0 以外で終わる
			  codegen <ソースルート>      テーブル定義のコードを生成する（例: src/main/java）

			オプション（migrate・migrateCheck）:
			  --data-source <名前>        当てる先を絞る（くり返し可）
			  --migration-dir <フォルダ>  そのフォルダを使うデータソースに絞る（くり返し可）
			  --parallel <数>             並べて当てる数（既定 1）
			  --keep-going                1 つ失敗してもほかを続ける（migrate だけ。既定は最初の失敗で止める）
			""");

	}

}
