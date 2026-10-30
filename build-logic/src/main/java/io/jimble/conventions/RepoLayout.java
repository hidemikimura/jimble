package io.jimble.conventions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * build/central（Maven のレイアウト）から、repo.jimble.io へ上げるものを選ぶ
 */
final class RepoLayout {

	/** 版のディレクトリの中身（一度上げたら変えない） */
	static final String CACHE_IMMUTABLE = "public, max-age=31536000, immutable";

	/** maven-metadata.xml（版を足すたびに変わる） */
	static final String CACHE_METADATA = "public, max-age=300";

	/* 上げてよいファイル（jar / pom / module と、その署名・チェックサム） */
	private static final Pattern ALLOWED = Pattern.compile(".+\\.(jar|pom|module)(\\.asc)?(\\.(md5|sha1|sha256|sha512))?");

	private RepoLayout () {
	}

	/**
	 * 成果物の座標
	 *
	 * @param groupId		groupId
	 * @param artifactId	artifactId
	 * @param dir			置き場の中のディレクトリ（{@code io/jimble/jimble-web}）
	 */
	record Coordinate (String groupId, String artifactId, String dir) {}

	/**
	 * 上げるもの
	 *
	 * @param files			上げるファイル（置き場の中のパス。上げる順）
	 * @param coordinates	maven-metadata.xml を書き直す成果物
	 */
	record Plan (List<String> files, List<Coordinate> coordinates) {

		/**
		 * 上げてはいけないもの（{@code .DS_Store} や、前のビルドの残り）
		 *
		 * @return 置き場の中のパス
		 */
		List<String> stray () {

			return files.stream()
				.filter(file -> !ALLOWED.matcher(file.substring(file.lastIndexOf('/') + 1)).matches())
				.toList();

		}

		/**
		 * 上げる段（1段目は jar など、2段目は pom / module とその署名・チェックサム）
		 *
		 * <p>
		 * <b>段の中は並べて上げてよいが、段をまたいではいけない。</b>
		 * 2段目は、1段目が全部置けてから上げる（pom が見えた時点で jar も取れるようにする）。
		 * </p>
		 *
		 * @return 段ごとのファイル（空の段は入れない）
		 */
		List<List<String>> stages () {

			List<String> artifacts = files.stream().filter(file -> !isDescriptor(file)).toList();
			List<String> descriptors = files.stream().filter(RepoLayout::isDescriptor).toList();

			return Stream.of(artifacts, descriptors).filter(stage -> !stage.isEmpty()).toList();

		}

		/**
		 * 署名（.asc）が無い jar / pom / module
		 *
		 * @return 置き場の中のパス
		 */
		List<String> unsigned () {

			Set<String> all = Set.copyOf(files);

			return files.stream()
				.filter(file -> file.endsWith(".jar") || file.endsWith(".pom") || file.endsWith(".module"))
				.filter(file -> !all.contains(file + ".asc"))
				.toList();

		}

	}

	/**
	 * その版のファイルを選ぶ
	 *
	 * <p>
	 * <b>jar を先に、pom と module をあとに上げる。</b>Gradle は pom / module を見つけてから jar を取りに来るので、
	 * 途中で取りに来た人に「pom はあるのに jar が無い」を見せない。maven-metadata.xml は最後に別に書く。
	 * </p>
	 *
	 * @param root		build/central
	 * @param version	版
	 * @return 上げるもの
	 */
	static Plan plan (Path root, String version) {

		List<String> files;

		try (Stream<Path> walk = Files.walk(root)) {
			files = walk
				.filter(Files::isRegularFile)
				.filter(path -> path.getParent().getFileName().toString().equals(version))
				.filter(path -> !path.getFileName().toString().startsWith("maven-metadata"))
				.map(path -> root.relativize(path).toString().replace('\\', '/'))
				.sorted(Comparator.comparing(RepoLayout::isDescriptor).thenComparing(Comparator.naturalOrder()))
				.collect(Collectors.toList());
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}

		Set<Coordinate> coordinates = new LinkedHashSet<>();

		for (String file : files.stream().sorted().toList()) {
			coordinates.add(coordinate(file));
		}

		return new Plan(List.copyOf(files), new ArrayList<>(coordinates));

	}

	/**
	 * Content-Type
	 *
	 * @param key	置き場の中のパス
	 * @return Content-Type
	 */
	static String contentType (String key) {

		if (key.endsWith(".jar")) {
			return "application/java-archive";
		}
		if (key.endsWith(".pom") || key.endsWith(".xml")) {
			return "application/xml";
		}
		if (key.endsWith(".module")) {
			return "application/json";
		}

		// .asc と .md5 / .sha1 / .sha256 / .sha512
		return "text/plain";

	}

	/**
	 * Cache-Control
	 *
	 * @param key	置き場の中のパス
	 * @return Cache-Control
	 */
	static String cacheControl (String key) {

		return key.substring(key.lastIndexOf('/') + 1).startsWith("maven-metadata") ? CACHE_METADATA : CACHE_IMMUTABLE;

	}

	/* pom / module（とその署名・チェックサム）か */
	private static boolean isDescriptor (String key) {

		String name = key.substring(key.lastIndexOf('/') + 1);

		return name.contains(".pom") || name.contains(".module");

	}

	/* io/jimble/jte/io.jimble.jte.gradle.plugin/2.7.11/x.pom → io.jimble.jte : io.jimble.jte.gradle.plugin */
	private static Coordinate coordinate (String file) {

		String[] parts = file.split("/");

		if (parts.length < 4) {
			throw new IllegalStateException("Maven のレイアウトになっていません: " + file);
		}

		String artifactId = parts[parts.length - 3];
		String groupId = String.join(".", List.of(parts).subList(0, parts.length - 3));
		String dir = String.join("/", List.of(parts).subList(0, parts.length - 2));

		return new Coordinate(groupId, artifactId, dir);

	}

}
