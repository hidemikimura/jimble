package io.jimble.conventions;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.plugins.signing.SigningExtension;

/**
 * Maven Central へ出すモジュールの設定（要件 NF-L-04 / 設計書 D-13）
 *
 * <p>
 * <b>これを適用したモジュールだけが publish される。</b>
 * 以前は {@code subprojects {}} の中で「名前が除外一覧に入っていないか」
 * 「パスが {@code :examples} で始まらないか」で振り分けていたが、
 * <b>publish するかどうかはモジュール自身の性質</b>なので、
 * そのモジュールの {@code build.gradle.kts} に書いてあるほうが辿りやすい（原則1）。
 * </p>
 *
 * <p>
 * 適用していないのは {@code jimble-docs}（jimble.io のサイトを作るためのもので、
 * アプリが依存するものではない）と {@code examples}（ライブラリではない）。
 * </p>
 */
public class PublishConventionsPlugin implements Plugin<Project> {

	@Override
	public void apply (Project project) {

		project.getPluginManager().apply("java-library");
		project.getPluginManager().apply("maven-publish");

		/*
		 * Maven Central は jar ごとに -sources.jar と -javadoc.jar を要求する。
		 * <b>publish しないモジュールには作らせない</b>（以前は examples にも付いていた）。
		 */
		JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
		java.withSourcesJar();
		java.withJavadocJar();

		PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);

		publishing.getPublications().create("maven", MavenPublication.class, publication -> {

			publication.from(project.getComponents().getByName("java"));

			publication.pom(pom -> JimbleBuild.pom(pom, project.getName(), project.provider(
				() -> project.getDescription() == null ? project.getName() : project.getDescription())));

		});

		/*
		 * central: Maven Central へ出すためのバンドルの材料。
		 *          ここへ出したものを centralBundle が zip にまとめる。
		 *
		 * gradle-plugin は別ビルドだが、<b>同じ場所</b>を指している。
		 */
		publishing.getRepositories().maven(repository -> {
			repository.setName("central");
			repository.setUrl(project.getRootProject().getLayout()
				.getBuildDirectory().dir("central").get().getAsFile());
		});

		signing(project, publishing);

	}

	/**
	 * 署名（要件 NF-L-04）
	 *
	 * <p>
	 * Maven Central は全ファイルに {@code .asc} を要求する。
	 * 鍵はファイルに置かず、環境変数から渡す。
	 * </p>
	 *
	 * <ul>
	 *   <li>{@code JIMBLE_SIGNING_KEY} — {@code gpg --armor --export-secret-keys} の中身</li>
	 *   <li>{@code JIMBLE_SIGNING_PASSWORD} — その鍵のパスフレーズ</li>
	 * </ul>
	 *
	 * <p>
	 * <b>鍵が無い環境では署名を飛ばす。</b>CI でも手元でも、
	 * 鍵を持っていない人のビルドが落ちないようにするためである。
	 * </p>
	 *
	 * @param project		プロジェクト
	 * @param publishing	publish の設定
	 */
	static void signing (Project project, PublishingExtension publishing) {

		Provider<String> signingKey = project.getProviders().environmentVariable("JIMBLE_SIGNING_KEY");

		if (!signingKey.isPresent()) {
			return;
		}

		project.getPluginManager().apply("signing");

		SigningExtension signing = project.getExtensions().getByType(SigningExtension.class);

		signing.useInMemoryPgpKeys(
			signingKey.get()
			, project.getProviders().environmentVariable("JIMBLE_SIGNING_PASSWORD").getOrElse(""));

		signing.sign(publishing.getPublications());

	}

}
