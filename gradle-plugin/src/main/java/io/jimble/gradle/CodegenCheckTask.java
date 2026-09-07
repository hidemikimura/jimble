package io.jimble.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * コミットされている生成物が最新かを確かめる
 *
 * <p>
 * 生成物はリポジトリにコミットする決まりなので（要件 F-G-13）、
 * <b>スキーマを変えたのに生成し直すのを忘れる</b>ことが起きる。
 * CI でこれを走らせて落とす（要件 F-G-14）。
 * </p>
 *
 * <p>
 * 一時ディレクトリに生成した結果と、コミットされている生成物を突き合わせる。
 * 足りない・余分・中身違いのどれでも失敗する。
 * </p>
 */
public abstract class CodegenCheckTask extends DefaultTask {

	/** 差分として並べる上限（多すぎても読めない） */
	private static final int MAX_REPORTED = 20;

	/**
	 * 生成し直した結果（一時ディレクトリ）
	 *
	 * @return	ディレクトリ
	 */
	@InputDirectory
	public abstract DirectoryProperty getGenerated ();

	/**
	 * コミットされている生成物（ソースルート）
	 *
	 * @return	ディレクトリ
	 */
	@InputDirectory
	public abstract DirectoryProperty getCommitted ();

	/**
	 * 突き合わせる
	 */
	@TaskAction
	public void check () {

		Path generatedRoot = getGenerated().get().getAsFile().toPath();
		Path committedRoot = getCommitted().get().getAsFile().toPath();

		Map<String, String> generated = readAll(generatedRoot);

		List<String> differences = new ArrayList<>();

		for (Map.Entry<String, String> entry : generated.entrySet()) {

			Path committed = committedRoot.resolve(entry.getKey());

			if (!Files.exists(committed)) {
				differences.add("生成物が足りません: " + entry.getKey());
				continue;
			}

			if (!entry.getValue().equals(read(committed))) {
				differences.add("生成物が古いです: " + entry.getKey());
			}

		}

		// 生成対象から消えたのにコミットされたまま残っているもの。
		// 生成した側にあるディレクトリだけを見る（手書きのコードは無視する）
		for (String stale : staleFiles(generated.keySet(), committedRoot)) {
			differences.add("生成対象ではないファイルが残っています: " + stale);
		}

		if (differences.isEmpty()) {
			getLogger().lifecycle("codegenCheck: 生成物はスキーマと一致しています（{} ファイル）", generated.size());
			return;
		}

		throw new GradleException(report(differences));

	}

	// region 突き合わせ

	/**
	 * 生成物ディレクトリの中で、生成されなかったファイルを探す
	 *
	 * @param generatedPaths	生成されたファイル（ルートからの相対パス）
	 * @param committedRoot		コミット側のソースルート
	 * @return	余分なファイル
	 */
	private List<String> staleFiles (Iterable<String> generatedPaths, Path committedRoot) {

		// 生成物が入っているディレクトリの集合
		List<String> directories = new ArrayList<>();
		for (String path : generatedPaths) {
			int index = path.lastIndexOf('/');
			String directory = index < 0 ? "" : path.substring(0, index);
			if (!directories.contains(directory)) {
				directories.add(directory);
			}
		}

		List<String> stale = new ArrayList<>();

		for (String directory : directories) {

			Path dir = committedRoot.resolve(directory);
			if (!Files.isDirectory(dir)) {
				continue;
			}

			try (Stream<Path> files = Files.list(dir)) {
				files.filter(Files::isRegularFile).forEach(file -> {
					String relative = committedRoot.relativize(file).toString().replace('\\', '/');
					boolean isGenerated = false;
					for (String path : generatedPaths) {
						if (path.equals(relative)) {
							isGenerated = true;
							break;
						}
					}
					if (!isGenerated) {
						stale.add(relative);
					}
				});
			} catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}

		}

		return stale;

	}

	/**
	 * 差分を読める形にする
	 *
	 * @param differences	差分
	 * @return	メッセージ
	 */
	private String report (List<String> differences) {

		StringBuilder sb = new StringBuilder();
		sb.append("コミットされている生成物がスキーマと一致しません（").append(differences.size()).append(" 件）。");
		sb.append("codegen を実行してコミットしてください。\n");

		int count = 0;
		for (String difference : differences) {
			if (count++ >= MAX_REPORTED) {
				sb.append("  ... 他 ").append(differences.size() - MAX_REPORTED).append(" 件\n");
				break;
			}
			sb.append("  ").append(difference).append("\n");
		}

		return sb.toString();

	}

	// endregion

	// region ファイル読み込み

	/**
	 * ディレクトリ以下の Java を全部読む
	 *
	 * @param root	ルート
	 * @return	相対パス → 中身
	 */
	private Map<String, String> readAll (Path root) {

		Map<String, String> files = new LinkedHashMap<>();

		try (Stream<Path> paths = Files.walk(root)) {
			paths.filter(Files::isRegularFile)
				.filter(path -> path.toString().endsWith(".java"))
				.sorted()
				.forEach(path -> files.put(root.relativize(path).toString().replace('\\', '/'), read(path)));
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}

		return files;

	}

	/**
	 * ファイルを読む
	 *
	 * @param path	パス
	 * @return	中身
	 */
	private String read (Path path) {

		try {
			return Files.readString(path, StandardCharsets.UTF_8);
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}

	}

	// endregion

}
