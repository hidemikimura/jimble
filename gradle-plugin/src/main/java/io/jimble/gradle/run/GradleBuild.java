package io.jimble.gradle.run;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 作り直し（要件 F-X-02）
 *
 * <p>
 * ルートの {@code gradlew} を子プロセスで叩く。
 * </p>
 *
 * <p>
 * <b>いま動いているビルドの中から同じプロジェクトのタスクを呼ぶことはできない</b>
 * （プロジェクトのロックで固まる）。デーモンがもう1つ立つのは避けられないので、
 * 叩くタスクを {@code classes} までに絞って軽くしている
 * （移送元は {@code build} を叩いており、<b>保存のたびにテストまで走っていた</b>）。
 * </p>
 *
 * <h2>移送元から直したところ</h2>
 * <p>
 * <b>成否を「標準エラーに何か出たか」で見ていた。</b>
 * </p>
 *
 * <pre>
 * // 移送元
 * success = !processReaderRunnableError.isOutputted;
 * </pre>
 *
 * <p>
 * このため「無視するエラー文字」を 20 個ほど並べる必要があり
 * （{@code npm warn} / {@code Xlint} / {@code ノート: } / {@code │} …）、
 * <b>そこに載っていない警告が1行出ただけで、再起動しなくなる。</b>
 * 逆に <b>{@code Xlint} を含む本物のエラーは見逃す。</b>
 * ここでは<b>終了コードだけを見る。</b>
 * </p>
 */
final class GradleBuild {

	/* ルートディレクトリ */
	private final File rootDir;

	/* 流すタスク */
	private final List<String> tasks;

	/* ログ */
	private final RunLog log;

	/**
	 * コンストラクタ
	 *
	 * @param rootDir	ルートディレクトリ
	 * @param tasks		流すタスク
	 * @param log		ログ
	 */
	GradleBuild (File rootDir, List<String> tasks, RunLog log) {

		this.rootDir = rootDir;
		this.tasks = tasks;
		this.log = log;

	}

	/**
	 * 作り直す
	 *
	 * @return	結果
	 */
	BuildOutcome run () {

		if (tasks.isEmpty()) {
			return BuildOutcome.OK;
		}

		log.debug("作り直します: " + String.join(" ", tasks));

		List<String> command = new ArrayList<>();
		command.add(wrapper());
		command.addAll(tasks);
		// 進捗のアニメーションはログに混ざると読めない
		command.add("--console=plain");

		List<String> output = new ArrayList<>();

		try {

			Process process = new ProcessBuilder(command)
				.directory(rootDir)
				.redirectErrorStream(true)
				.start();

			try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

				String line;
				while ((line = reader.readLine()) != null) {
					output.add(line);
					System.out.println(line);
				}

			}

			int code = process.waitFor();

			if (code == 0) {
				log.debug("作り直しました");
				return BuildOutcome.OK;
			}

			log.error("作り直しに失敗しました（終了コード " + code + "）");
			return new BuildOutcome(false, output);

		} catch (InterruptedException ex) {

			Thread.currentThread().interrupt();
			return new BuildOutcome(false, List.of("作り直しを中断しました"));

		} catch (IOException ex) {

			log.error("作り直しを始められませんでした: " + ex.getMessage());
			output.add(String.valueOf(ex.getMessage()));
			return new BuildOutcome(false, output);

		}

	}

	/**
	 * ラッパーのパス
	 *
	 * @return	パス
	 */
	private String wrapper () {

		/*
		 * ラッパーは gradlew だけでは動かない。
		 * gradle/wrapper/gradle-wrapper.properties が要る。
		 * 揃っていないものを掴むと「Wrapper properties file does not exist」で
		 * 毎回ビルドに失敗し、原因がホットリロードの側にあるように見える。
		 */
		boolean hasWrapperProperties =
			new File(rootDir, "gradle/wrapper/gradle-wrapper.properties").isFile();

		if (hasWrapperProperties) {

			File sh = new File(rootDir, "gradlew");
			if (sh.canExecute()) {
				return sh.getAbsolutePath();
			}

			File bat = new File(rootDir, "gradlew.bat");
			if (bat.isFile()) {
				return bat.getAbsolutePath();
			}

		}

		// ラッパーが揃っていなければ PATH の gradle を使う
		log.debug("gradlew が見つからないので PATH の gradle を使います");
		return "gradle";

	}

}
