package io.jimble.db.cli;

import io.jimble.db.DBConf;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code migrate} / {@code migrateCheck} のオプション
 *
 * <pre>
 * --data-source &lt;名前&gt;     当てる先を絞る（くり返し可）
 * --migration-dir &lt;フォルダ&gt; そのフォルダを使うデータソースに絞る（くり返し可）
 * --parallel &lt;数&gt;          並べて当てる数（既定 1）
 * --keep-going              1 つ失敗してもほかを続ける
 * </pre>
 *
 * @param dataSources	--data-source（空なら絞らない）
 * @param migrationDirs	--migration-dir（空なら絞らない）
 * @param parallel		並べて当てる数
 * @param keepGoing		1 つ失敗してもほかを続ける場合 = true
 */
record MigrateOptions (List<String> dataSources, List<String> migrationDirs, int parallel, boolean keepGoing) {

	/** 並べる数の既定 */
	static final int DEFAULT_PARALLEL = 1;

	MigrateOptions {

		dataSources = List.copyOf(dataSources);
		migrationDirs = List.copyOf(migrationDirs);

	}

	/**
	 * 読む
	 *
	 * @param args	コマンドの後ろの引数
	 * @return	オプション
	 * @throws IllegalArgumentException	知らないオプション・値が無い・値が正しくない場合
	 */
	static MigrateOptions parse (List<String> args) {

		List<String> dataSources = new ArrayList<>();
		List<String> migrationDirs = new ArrayList<>();
		int parallel = DEFAULT_PARALLEL;
		boolean keepGoing = false;

		for (int i = 0; i < args.size(); i++) {

			String arg = args.get(i);

			switch (arg) {
				case "--data-source" -> dataSources.add(value(args, ++i, arg));
				case "--migration-dir" -> migrationDirs.add(value(args, ++i, arg));
				case "--parallel" -> parallel = parallel(value(args, ++i, arg));
				case "--keep-going" -> keepGoing = true;
				default -> throw new IllegalArgumentException("知らないオプションです: " + arg);
			}

		}

		return new MigrateOptions(dataSources, migrationDirs, parallel, keepGoing);

	}

	/**
	 * 当てる先のデータソースを選ぶ
	 *
	 * <p>
	 * {@code --data-source} と {@code --migration-dir} の両方があれば、両方を満たすものだけ。
	 * どちらも無ければ全部。順は名前の順。
	 * </p>
	 *
	 * @param confs	データソース名 → 設定（名前の順）
	 * @return	選んだデータソース名
	 * @throws IllegalArgumentException	知らないデータソース名・どれも使っていないフォルダを書いた場合、選んだものが無い場合
	 */
	List<String> select (Map<String, DBConf> confs) {

		for (String name : dataSources) {
			if (!confs.containsKey(name)) {
				throw new IllegalArgumentException("--data-source %s は設定にありません（db.<名前>）: %s".formatted(name, confs.keySet()));
			}
		}

		for (String dir : migrationDirs) {
			if (confs.values().stream().noneMatch(conf -> dir.equals(conf.migrationDir()))) {
				throw new IllegalArgumentException("--migration-dir %s を使うデータソースがありません".formatted(dir));
			}
		}

		List<String> selected = new ArrayList<>();

		for (Map.Entry<String, DBConf> entry : confs.entrySet()) {
			boolean byName = dataSources.isEmpty() || dataSources.contains(entry.getKey());
			boolean byDir = migrationDirs.isEmpty() || migrationDirs.contains(entry.getValue().migrationDir());
			if (byName && byDir) {
				selected.add(entry.getKey());
			}
		}

		if (selected.isEmpty()) {
			throw new IllegalArgumentException("当てる先のデータソースがありません（--data-source と --migration-dir の両方を満たすものが無い）");
		}

		return selected;

	}

	/**
	 * 絞っているか
	 *
	 * @return	--data-source か --migration-dir を書いた場合 = true
	 */
	boolean isFiltered () {

		return !dataSources.isEmpty() || !migrationDirs.isEmpty();

	}

	private static String value (List<String> args, int index, String option) {

		if (index >= args.size() || args.get(index).startsWith("--")) {
			throw new IllegalArgumentException(option + " に値がありません");
		}

		return args.get(index);

	}

	private static int parallel (String value) {

		int parallel;
		try {
			parallel = Integer.parseInt(value);
		} catch (NumberFormatException ex) {
			throw new IllegalArgumentException("--parallel は 1 以上の数で書いてください: " + value, ex);
		}

		if (parallel < 1) {
			throw new IllegalArgumentException("--parallel は 1 以上の数で書いてください: " + value);
		}

		return parallel;

	}

}
