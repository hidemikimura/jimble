package io.jimble.conventions;

import org.gradle.api.Project;
import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.maven.MavenPom;

/**
 * ビルドの規約が共有するもの（設計書 D-13）
 *
 * <p>
 * 本体・gradle-plugin（別ビルド）の<b>両方から呼ぶ</b>。
 * 以前は同じものを2か所に書き写していて、直すときは両方直すよう
 * 注意書きを付けていた。build-logic を別ビルドにしたので共有できる。
 * </p>
 */
public final class JimbleBuild {

	private JimbleBuild () {
	}

	/**
	 * 版
	 *
	 * <p>
	 * {@code -Pjimble.version} で上書きできる。既定は
	 * {@code gradle/libs.versions.toml} の {@code jimble} にある
	 * （<b>ほかの版と同じ表に置いてある。</b>ここに書き写すと、
	 * 本体・gradle-plugin・バンドルの3か所で食い違いうる）。
	 * </p>
	 *
	 * <p>
	 * Maven Central は {@code -SNAPSHOT} を受け付けない（スナップショットは別のリポジトリ）ので、
	 * リリースのときだけ {@code ./gradlew centralBundle -Pjimble.version=0.2.0} のように渡す。
	 * 既定を素の {@code 0.2.0} にしないのは、うっかり publish したものが
	 * 「リリース版」として残るのを避けるためである。
	 * </p>
	 *
	 * @param project	プロジェクト
	 * @return 版
	 */
	public static String version (Project project) {

		Provider<String> override = project.getProviders().gradleProperty("jimble.version");

		if (override.isPresent()) {
			return override.get();
		}

		VersionCatalog libs = project.getExtensions()
			.getByType(VersionCatalogsExtension.class)
			.named("libs");

		return libs.findVersion("jimble")
			.orElseThrow(() -> new IllegalStateException(
				"gradle/libs.versions.toml に [versions] jimble がありません"))
			.getRequiredVersion();

	}

	/**
	 * Maven Central が要求する POM の項目（要件 NF-L-04）
	 *
	 * <p>
	 * name / description / url / licenses / developers / scm。
	 * <b>どれか1つでも欠けると、アップロードは通ってから検証で落ちる。</b>
	 * </p>
	 *
	 * @param pom			書き込む先
	 * @param moduleName	名前
	 * @param description	説明（各モジュールの description。遅らせて読む）
	 */
	public static void pom (MavenPom pom, String moduleName, Provider<String> description) {

		pom.getName().set(moduleName);
		pom.getDescription().set(description);
		pom.getUrl().set("https://jimble.io");

		pom.licenses(licenses -> licenses.license(license -> {
			license.getName().set("The Apache License, Version 2.0");
			license.getUrl().set("https://www.apache.org/licenses/LICENSE-2.0.txt");
		}));

		/*
		 * <b>organization は入れない。</b>Maven Central が求めているのは
		 * 「連絡が付くこと」で、所属ではない（Central にある caffeine / jspecify /
		 * fastcsv も id・name・email だけである）。
		 */
		pom.developers(developers -> developers.developer(developer -> {
			developer.getId().set("hidemikimura");
			developer.getName().set("Hidemi Kimura");
			developer.getEmail().set("hidemikimura@gmail.com");
		}));

		pom.scm(scm -> {
			scm.getUrl().set("https://github.com/hidemikimura/jimble");
			scm.getConnection().set("scm:git:https://github.com/hidemikimura/jimble.git");
			// 書き込む側は SSH
			scm.getDeveloperConnection().set("scm:git:ssh://git@github.com/hidemikimura/jimble.git");
		});

	}

}
