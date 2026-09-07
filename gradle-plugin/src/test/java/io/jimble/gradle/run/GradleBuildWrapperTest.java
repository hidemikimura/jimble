package io.jimble.gradle.run;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 壊れた Gradle ラッパーを掴まない（要件 F-X-02 / D-72）
 *
 * <p>
 * 移送した RSS アプリで踏んだもの。{@code gradlew} は
 * {@code java -jar gradle-wrapper.jar} で起動する新しい形なのに、
 * {@code gradle-wrapper.jar} が古い（{@code -classpath} 前提で
 * マニフェストに {@code Main-Class} が無い）ものだった。
 * </p>
 *
 * <p>
 * 出るのは<b>「メイン・マニフェスト属性がありません」だけ</b>で、
 * ホットリロードが壊れたようにしか見えない。
 * </p>
 */
class GradleBuildWrapperTest {

	/**
	 * ラッパーを作る
	 *
	 * @param root		ルート
	 * @param usesJar	gradlew が -jar で起動するか
	 * @param mainClass	JAR のマニフェストに Main-Class を入れるか
	 */
	private static void wrapper (Path root, boolean usesJar, boolean mainClass) throws IOException {

		Files.createDirectories(root.resolve("gradle/wrapper"));
		Files.writeString(root.resolve("gradle/wrapper/gradle-wrapper.properties")
			, "distributionUrl=https\\://example.invalid/gradle-9.7.1-bin.zip\n");

		Path script = root.resolve("gradlew");
		Files.writeString(script, usesJar
			? "#!/bin/sh\nexec java -jar \"$APP_HOME/gradle/wrapper/gradle-wrapper.jar\" \"$@\"\n"
			: "#!/bin/sh\nexec java -classpath \"$CLASSPATH\" org.gradle.wrapper.GradleWrapperMain \"$@\"\n");
		script.toFile().setExecutable(true);

		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		if (mainClass) {
			manifest.getMainAttributes()
				.put(Attributes.Name.MAIN_CLASS, "org.gradle.wrapper.GradleWrapperMain");
		}

		try (OutputStream out = Files.newOutputStream(root.resolve("gradle/wrapper/gradle-wrapper.jar"));
			 JarOutputStream jar = new JarOutputStream(out, manifest)) {
			jar.flush();
		}

	}

	/**
	 * ラッパーの解決結果
	 *
	 * @param root	ルート
	 * @return	使うコマンド
	 */
	private static String resolve (Path root) {

		return new GradleBuild(root.toFile(), List.of("classes"), new RunLog()).wrapperCommand();

	}

	@Test
	@DisplayName("揃っていれば gradlew を使う")
	void healthyWrapper (@TempDir Path root) throws IOException {

		wrapper(root, true, true);

		assertTrue(resolve(root).endsWith("gradlew"), resolve(root));

	}

	@Test
	@DisplayName("gradlew は -jar なのに JAR に Main-Class が無ければ gradle に逃がす")
	void mismatchedWrapper (@TempDir Path root) throws IOException {

		wrapper(root, true, false);

		assertEquals("gradle", resolve(root));

	}

	@Test
	@DisplayName("古い形（-classpath で起動する）はそのまま使う")
	void classpathStyleWrapper (@TempDir Path root) throws IOException {

		wrapper(root, false, false);

		assertTrue(resolve(root).endsWith("gradlew"), resolve(root));

	}

	@Test
	@DisplayName("JAR が無ければ gradle に逃がす")
	void missingJar (@TempDir Path root) throws IOException {

		wrapper(root, true, true);
		Files.delete(root.resolve("gradle/wrapper/gradle-wrapper.jar"));

		assertEquals("gradle", resolve(root));

	}

	@Test
	@DisplayName("properties が無ければ gradle を使う（これは普通のこと）")
	void noWrapperAtAll (@TempDir Path root) {

		assertEquals("gradle", resolve(root));

	}

}
