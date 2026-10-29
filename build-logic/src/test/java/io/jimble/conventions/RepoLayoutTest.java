package io.jimble.conventions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 上げるファイルの選び方
 */
class RepoLayoutTest {

	@TempDir
	Path root;

	@Test
	@DisplayName("その版のファイルだけを選び、jar を先に・pom と module をあとに並べる。maven-metadata は入れない")
	void plansOnlyThisVersion () throws IOException {

		touch("io/jimble/jimble-web/2.7.11/jimble-web-2.7.11.pom");
		touch("io/jimble/jimble-web/2.7.11/jimble-web-2.7.11.pom.asc");
		touch("io/jimble/jimble-web/2.7.11/jimble-web-2.7.11.jar");
		touch("io/jimble/jimble-web/2.7.11/jimble-web-2.7.11.jar.asc");
		touch("io/jimble/jimble-web/2.7.11/jimble-web-2.7.11.module");
		touch("io/jimble/jimble-web/2.7.10/jimble-web-2.7.10.jar");
		touch("io/jimble/jimble-web/maven-metadata.xml");
		touch("io/jimble/jte/io.jimble.jte.gradle.plugin/2.7.11/io.jimble.jte.gradle.plugin-2.7.11.pom");

		RepoLayout.Plan plan = RepoLayout.plan(root, "2.7.11");

		assertEquals(List.of(
			"io/jimble/jimble-web/2.7.11/jimble-web-2.7.11.jar",
			"io/jimble/jimble-web/2.7.11/jimble-web-2.7.11.jar.asc",
			"io/jimble/jimble-web/2.7.11/jimble-web-2.7.11.module",
			"io/jimble/jimble-web/2.7.11/jimble-web-2.7.11.pom",
			"io/jimble/jimble-web/2.7.11/jimble-web-2.7.11.pom.asc",
			"io/jimble/jte/io.jimble.jte.gradle.plugin/2.7.11/io.jimble.jte.gradle.plugin-2.7.11.pom"), plan.files());

		assertEquals(List.of(
			new RepoLayout.Coordinate("io.jimble", "jimble-web", "io/jimble/jimble-web"),
			new RepoLayout.Coordinate("io.jimble.jte", "io.jimble.jte.gradle.plugin", "io/jimble/jte/io.jimble.jte.gradle.plugin")), plan.coordinates());

	}

	@Test
	@DisplayName("署名（.asc）の無い jar / pom / module を挙げる")
	void listsUnsigned () throws IOException {

		touch("io/jimble/jimble-web/2.7.11/jimble-web-2.7.11.jar");
		touch("io/jimble/jimble-web/2.7.11/jimble-web-2.7.11.jar.asc");
		touch("io/jimble/jimble-web/2.7.11/jimble-web-2.7.11.pom");
		touch("io/jimble/jimble-web/2.7.11/jimble-web-2.7.11.pom.sha1");

		RepoLayout.Plan plan = RepoLayout.plan(root, "2.7.11");

		assertEquals(List.of("io/jimble/jimble-web/2.7.11/jimble-web-2.7.11.pom"), plan.unsigned());

	}

	@Test
	@DisplayName("jar / pom / module とその署名・チェックサムのほか（.DS_Store など）は挙げる")
	void listsStrayFiles () throws IOException {

		touch("io/jimble/jimble-web/2.7.11/jimble-web-2.7.11.jar");
		touch("io/jimble/jimble-web/2.7.11/jimble-web-2.7.11.jar.sha512");
		touch("io/jimble/jimble-web/2.7.11/.DS_Store");
		touch("io/jimble/jimble-web/2.7.11/notes.txt");

		RepoLayout.Plan plan = RepoLayout.plan(root, "2.7.11");

		assertEquals(List.of(
			"io/jimble/jimble-web/2.7.11/.DS_Store",
			"io/jimble/jimble-web/2.7.11/notes.txt"), plan.stray());

	}

	@Test
	@DisplayName("ファイルの種類から Content-Type と Cache-Control を決める")
	void contentTypeAndCache () {

		assertEquals("application/java-archive", RepoLayout.contentType("a/b/x-1.jar"));
		assertEquals("application/xml", RepoLayout.contentType("a/b/x-1.pom"));
		assertEquals("application/json", RepoLayout.contentType("a/b/x-1.module"));
		assertEquals("text/plain", RepoLayout.contentType("a/b/x-1.jar.sha1"));
		assertEquals(RepoLayout.CACHE_IMMUTABLE, RepoLayout.cacheControl("a/b/1/x-1.jar"));
		assertEquals(RepoLayout.CACHE_METADATA, RepoLayout.cacheControl("a/b/maven-metadata.xml.sha1"));

	}

	private void touch (String path) throws IOException {

		Path file = root.resolve(path);
		Files.createDirectories(file.getParent());
		Files.writeString(file, path);

	}

}
