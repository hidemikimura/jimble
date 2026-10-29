package io.jimble.conventions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * maven-metadata.xml の合成
 */
class MavenMetadataTest {

	private static final String NOW = "20261029120000";

	@Test
	@DisplayName("まだ無ければ、その版だけの metadata を作る")
	void createsNew () {

		String xml = MavenMetadata.merge(null, "io.jimble", "jimble-web", "2.7.11", NOW);

		assertEquals("""
			<?xml version="1.0" encoding="UTF-8"?>
			<metadata>
			  <groupId>io.jimble</groupId>
			  <artifactId>jimble-web</artifactId>
			  <versioning>
			    <latest>2.7.11</latest>
			    <release>2.7.11</release>
			    <versions>
			      <version>2.7.11</version>
			    </versions>
			    <lastUpdated>20261029120000</lastUpdated>
			  </versioning>
			</metadata>
			""", xml);

	}

	@Test
	@DisplayName("あれば版を足し、latest / release は大きいほうの版にする（2.7.10 は 2.7.9 より大きい）")
	void mergesAndPicksNewest () {

		String before = MavenMetadata.merge(null, "io.jimble", "jimble-web", "2.7.9", NOW);
		String after = MavenMetadata.merge(before, "io.jimble", "jimble-web", "2.7.10", NOW);

		assertEquals(List.of("2.7.9", "2.7.10"), MavenMetadata.versions(after));
		assertTrue(after.contains("<release>2.7.10</release>"), after);

	}

	@Test
	@DisplayName("古い版をあとから足しても、release は動かさない")
	void olderVersionKeepsRelease () {

		String before = MavenMetadata.merge(null, "io.jimble", "jimble-web", "2.8.0", NOW);
		String after = MavenMetadata.merge(before, "io.jimble", "jimble-web", "2.7.12", NOW);

		assertEquals(List.of("2.7.12", "2.8.0"), MavenMetadata.versions(after));
		assertTrue(after.contains("<latest>2.8.0</latest>"), after);
		assertTrue(after.contains("<release>2.8.0</release>"), after);

	}

	@Test
	@DisplayName("同じ版を2回足しても1つにまとめる")
	void noDuplicates () {

		String once = MavenMetadata.merge(null, "io.jimble", "jimble-web", "2.7.11", NOW);
		String twice = MavenMetadata.merge(once, "io.jimble", "jimble-web", "2.7.11", NOW);

		assertEquals(List.of("2.7.11"), MavenMetadata.versions(twice));

	}

	@Test
	@DisplayName("別の成果物の metadata には足さない")
	void rejectsOtherArtifact () {

		String other = MavenMetadata.merge(null, "io.jimble", "jimble-db", "2.7.11", NOW);

		assertThrows(IllegalStateException.class, () -> MavenMetadata.merge(other, "io.jimble", "jimble-web", "2.7.11", NOW));

	}

	@Test
	@DisplayName("DOCTYPE のある XML は読まない")
	void rejectsDoctype () {

		String xml = """
			<?xml version="1.0"?>
			<!DOCTYPE metadata [<!ENTITY x SYSTEM "file:///etc/passwd">]>
			<metadata><groupId>io.jimble</groupId><artifactId>jimble-web</artifactId></metadata>
			""";

		assertThrows(IllegalStateException.class, () -> MavenMetadata.versions(xml));

	}

	@Test
	@DisplayName("プレリリース（-rc1 など）は latest にはなるが release にはならず、正式版より小さい")
	void preReleaseIsNotRelease () {

		String xml = MavenMetadata.merge(null, "io.jimble", "jimble-web", "2.7.11", NOW);
		xml = MavenMetadata.merge(xml, "io.jimble", "jimble-web", "2.7.12-rc1", NOW);

		assertTrue(xml.contains("<latest>2.7.12-rc1</latest>"), xml);
		assertTrue(xml.contains("<release>2.7.11</release>"), xml);

		xml = MavenMetadata.merge(xml, "io.jimble", "jimble-web", "2.7.12", NOW);

		assertEquals(List.of("2.7.11", "2.7.12-rc1", "2.7.12"), MavenMetadata.versions(xml));
		assertTrue(xml.contains("<latest>2.7.12</latest>"), xml);
		assertTrue(xml.contains("<release>2.7.12</release>"), xml);

	}

	@Test
	@DisplayName("その版が載っているかを返す")
	void containsVersion () {

		String xml = MavenMetadata.merge(null, "io.jimble", "jimble-web", "2.7.11", NOW);

		assertTrue(MavenMetadata.versions(xml).contains("2.7.11"));
		assertTrue(!MavenMetadata.versions(xml).contains("2.7.12"));

	}

	@Test
	@DisplayName("版は数として比べる")
	void comparesNumerically () {

		assertTrue(MavenMetadata.compareVersions("2.7.10", "2.7.9") > 0);
		assertTrue(MavenMetadata.compareVersions("2.10.0", "2.9.9") > 0);
		assertTrue(MavenMetadata.compareVersions("3.0.0", "3.0.0") == 0);
		assertTrue(MavenMetadata.compareVersions("3.0", "3.0.1") < 0);
		assertTrue(MavenMetadata.compareVersions("2.7.12-rc1", "2.7.12") < 0);
		assertTrue(MavenMetadata.compareVersions("2.7.12-rc1", "2.7.11") > 0);
		assertTrue(MavenMetadata.compareVersions("2.7.12-rc2", "2.7.12-rc1") > 0);

	}

}
