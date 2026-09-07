package io.jimble.gradle.run;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

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
		command.add(wrapperCommand());
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
	 * <p>
	 * <b>壊れたラッパーを掴まない。</b>ここで掴むと、保存のたびにビルドが失敗し、
	 * <b>原因がホットリロードの側にあるように見える</b>。
	 * 使えないと分かったら、理由を言ってから PATH の {@code gradle} に逃がす。
	 * </p>
	 *
	 * @return	パス
	 */
	String wrapperCommand () {

		File script = wrapperScript();

		if (script != null) {

			String problem = wrapperProblem(script);

			if (problem == null) {
				return script.getAbsolutePath();
			}

			log.error("""
				%s が使えないので PATH の gradle を使います。
				  %s
				  直すには、このプロジェクトで次を流してください。
				    gradle wrapper --gradle-version <版>"""
				.formatted(script.getName(), problem));

			return "gradle";

		}

		// ラッパーが無ければ PATH の gradle を使う（これは普通のこと）
		log.debug("gradlew が見つからないので PATH の gradle を使います");
		return "gradle";

	}

	/**
	 * ラッパーのスクリプト
	 *
	 * <p>
	 * ラッパーは {@code gradlew} だけでは動かない。
	 * {@code gradle/wrapper/gradle-wrapper.properties} が要る。
	 * 揃っていないものを掴むと「Wrapper properties file does not exist」になる。
	 * </p>
	 *
	 * @return	スクリプト。無ければ null
	 */
	private File wrapperScript () {

		if (!new File(rootDir, "gradle/wrapper/gradle-wrapper.properties").isFile()) {
			return null;
		}

		File sh = new File(rootDir, "gradlew");
		if (sh.canExecute()) {
			return sh;
		}

		File bat = new File(rootDir, "gradlew.bat");
		if (bat.isFile()) {
			return bat;
		}

		return null;

	}

	/**
	 * ラッパーが使えない理由
	 *
	 * <p>
	 * よくあるのが<b>スクリプトと JAR の食い違い</b>である。
	 * いまの {@code gradlew} は
	 * {@code java -jar gradle/wrapper/gradle-wrapper.jar} で起動するので、
	 * JAR のマニフェストに {@code Main-Class} が要る。
	 * 古い（{@code -classpath} 前提の）JAR が混ざっていると
	 * <b>「メイン・マニフェスト属性がありません」</b>だけが出る。
	 * gradlew も JAR も見た目は揃っているので、これは分からない。
	 * </p>
	 *
	 * @param script	スクリプト
	 * @return	理由。使えるなら null
	 */
	private String wrapperProblem (File script) {

		File jar = new File(rootDir, "gradle/wrapper/gradle-wrapper.jar");

		if (!jar.isFile()) {
			return "gradle/wrapper/gradle-wrapper.jar がありません";
		}

		if (!usesJarOption(script)) {
			// 古い形（-classpath で起動する）。Main-Class は要らない
			return null;
		}

		try (JarFile jarFile = new JarFile(jar)) {

			Manifest manifest = jarFile.getManifest();

			String mainClass = manifest == null
				? null : manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);

			if (mainClass == null || mainClass.isEmpty()) {
				return "gradle-wrapper.jar のマニフェストに Main-Class がありません"
					+ "（%s は -jar で起動するので、古い gradle-wrapper.jar は使えません）"
						.formatted(script.getName());
			}

			return null;

		} catch (IOException ex) {

			return "gradle-wrapper.jar を読めません: " + ex.getMessage();

		}

	}

	/**
	 * スクリプトが {@code -jar} で起動するか
	 *
	 * @param script	スクリプト
	 * @return	{@code -jar} を使うなら true
	 */
	private boolean usesJarOption (File script) {

		try {

			return Files.readString(script.toPath(), StandardCharsets.UTF_8)
				.contains("-jar");

		} catch (IOException ex) {

			// 読めないなら判断しない（使えるものとして扱う）
			return false;

		}

	}

}
