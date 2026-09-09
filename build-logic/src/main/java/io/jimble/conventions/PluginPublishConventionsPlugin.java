package io.jimble.conventions;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;

/**
 * gradle-plugin（別ビルド）を Maven Central へ出す設定（要件 NF-L-04 / D-22）
 *
 * <p>
 * <b>{@link PublishConventionsPlugin} とは分けてある。</b>
 * 向こうは {@code components["java"]} から publication を1つ作るが、こちらは
 * {@code java-gradle-plugin} が<b>プラグインマーカーの publication をあとから足す</b>ので、
 * 「これから増えるもの」にも掛かる形（{@code withType} ＋ {@code configureEach}）でないといけない。
 * </p>
 *
 * <p>
 * Gradle Plugin Portal には出さない。かわりに、{@code jimble new} が作る
 * {@code settings.gradle.kts} の {@code pluginManagement} で {@code mavenCentral()} を見に行かせる。
 * {@code java-gradle-plugin} が {@code io.jimble.jte:io.jimble.jte.gradle.plugin} という形の
 * プラグインマーカーも一緒に publish するので、
 * {@code plugins { id("io.jimble.jte") version "..." }} がそれで解決できる。
 * </p>
 */
public class PluginPublishConventionsPlugin implements Plugin<Project> {

	@Override
	public void apply (Project project) {

		project.getPluginManager().apply("maven-publish");

		PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);

		/*
		 * 置き場はリポジトリの build/central。本体と同じ場所に出して、
		 * 外側の centralBundle が丸ごと zip にする。
		 */
		publishing.getRepositories().maven(repository -> {
			repository.setName("central");
			// rootDir はこの別ビルド（<repo>/gradle-plugin）。その隣の build/central を指す
			repository.setUrl(project.getRootDir().toPath().resolveSibling("build").resolve("central").toFile());
		});

		publishing.getPublications().withType(MavenPublication.class).configureEach(publication ->
			// マーカーは name も description も空のまま出るので、ここで入れる
			publication.pom(pom -> JimbleBuild.pom(pom, publication.getArtifactId(), project.provider(
				() -> project.getDescription() == null ? "jimble Gradle plugin" : project.getDescription()))));

		PublishConventionsPlugin.signing(project, publishing);

	}

}
