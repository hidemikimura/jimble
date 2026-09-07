package io.jimble.gradle;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.resolve.DirectoryCodeResolver;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * jte テンプレートを Java のソースに変換する
 *
 * <p>
 * <b>コンパイルはしない。</b>生成した {@code .java} を普通のソースとして
 * {@code compileJava} に食わせる。こうすると：
 * </p>
 *
 * <ul>
 *   <li>テンプレートの型の間違いが<b>アプリのコンパイルエラーとして出る</b>
 *       （jte を使う理由がこれ）</li>
 *   <li>生成物がアプリの {@code jar} に普通に入る。実行時コンパイルが要らない（要件 F-W-10）</li>
 *   <li>IDE が生成コードを追える。スタックトレースも読める</li>
 * </ul>
 *
 * <p>
 * 実行するのは Gradle デーモンの中である。jte のコンパイラ本体
 * （{@code gg.jte:jte}）はこのプラグインの依存であって、
 * <b>アプリの実行時クラスパスには入らない。</b>
 * </p>
 */
public abstract class GenerateJteTask extends DefaultTask {

	/**
	 * テンプレートの置き場所
	 *
	 * @return	置き場所
	 */
	@Internal
	public abstract DirectoryProperty getSourceDirectory();

	/**
	 * テンプレートの一覧
	 *
	 * <p>
	 * 変更を見るのはこちら。{@link #getSourceDirectory()} を
	 * {@code @InputDirectory} にするとディレクトリが無いビルドで失敗する。
	 * </p>
	 *
	 * @return	一覧
	 */
	@InputFiles
	@SkipWhenEmpty
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract ConfigurableFileCollection getTemplates();

	/**
	 * 生成先
	 *
	 * @return	生成先
	 */
	@OutputDirectory
	public abstract DirectoryProperty getTargetDirectory();

	/**
	 * 生成するクラスのパッケージ
	 *
	 * @return	パッケージ
	 */
	@Input
	public abstract Property<String> getPackageName();

	/**
	 * {@code Html} または {@code Plain}
	 *
	 * @return	種別
	 */
	@Input
	public abstract Property<String> getContentType();

	/**
	 * 制御構造の行を出力から落とすか
	 *
	 * @return	落とす場合 = true
	 */
	@Input
	public abstract Property<Boolean> getTrimControlStructures();

	/**
	 * HTML コメントを出力に残すか
	 *
	 * @return	残す場合 = true
	 */
	@Input
	public abstract Property<Boolean> getHtmlCommentsPreserved();

	/**
	 * 変換する
	 */
	@TaskAction
	public void generate () {

		File sourceDirectory = getSourceDirectory().get().getAsFile();
		File targetDirectory = getTargetDirectory().get().getAsFile();

		/*
		 * 消したテンプレートの生成物が残ると、
		 * 消したはずのものがコンパイルを通り jar にも入る。
		 * 毎回まっさらにする。
		 */
		getProject().delete(targetDirectory);
		targetDirectory.mkdirs();

		if (!sourceDirectory.isDirectory()) {
			getLogger().info("jte: テンプレートがありません: {}", sourceDirectory);
			return;
		}

		Path source = sourceDirectory.toPath();
		Path target = targetDirectory.toPath();

		TemplateEngine engine = TemplateEngine.create(
			new DirectoryCodeResolver(source)
			, target
			, ContentType.valueOf(getContentType().get())
			// クラスローダは要らない。ここではコンパイルまでしないため
			, null
			, getPackageName().get()
		);

		engine.setTrimControlStructures(getTrimControlStructures().get());
		engine.setHtmlCommentsPreserved(getHtmlCommentsPreserved().get());

		List<String> generated = engine.generateAll();

		getLogger().lifecycle("jte: {} 件のテンプレートを変換しました", generated.size());

		for (String name : generated) {
			getLogger().info("jte: {}", name);
		}

	}

}
