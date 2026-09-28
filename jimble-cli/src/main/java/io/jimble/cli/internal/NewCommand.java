package io.jimble.cli.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code jimble new}（要件 F-X-01）
 *
 * <pre>
 * jimble new my-blog
 * </pre>
 *
 * <p>
 * <b>そのままビルドして起動できるものを出す。</b>
 * 雛形が「あとで手を入れないと動かない」状態だと、
 * 最初につまずくのが自分のコードなのか雛形なのか分からなくなる。
 * DB も Redis も要らない形で出しているのはそのためである
 * （使うようになったら外すコメントを置いてある）。
 * </p>
 */
final class NewCommand {

	/** 置き換えるもの */
	private static final String KEY_NAME = "__NAME__";

	/** 置き換えるもの */
	private static final String KEY_PACKAGE = "__PACKAGE__";

	/** 置き換えるもの */
	private static final String KEY_PACKAGE_PATH = "__PACKAGE_PATH__";

	/** 置き換えるもの */
	private static final String KEY_DB = "__DB__";

	/** 置き換えるもの */
	private static final String KEY_VERSION = "__JIMBLE_VERSION__";

	/* skill を置く場所 */
	private static final String SKILLS_DIR = ".claude/skills/";

	/* 置いた skill の控え（.claude/ の下） */
	static final String SKILLS_RECORD = "jimble-skills.properties";

	private NewCommand () {}

	/**
	 * SHA-256（16 進の小文字。Gradle プラグインの控えと同じ形）
	 *
	 * @param bytes	中身
	 * @return	ハッシュ
	 */
	static String sha256 (byte[] bytes) {

		try {
			return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (java.security.NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}

	}

	/**
	 * 作る
	 *
	 * @param name		プロジェクト名
	 * @param parent	作る場所
	 * @return	作ったディレクトリ
	 * @throws IOException	作れなかった場合
	 */
	static Path run (String name, Path parent) throws IOException {

		ProjectName project = ProjectName.of(name);

		Path root = parent.resolve(project.name());

		/*
		 * すでにあるものを上書きしない。
		 * 「作り直そうとしたら、書きかけのコードが消えた」を起こさない。
		 */
		if (Files.exists(root)) {
			throw new IOException("すでにあります: %s".formatted(root.toAbsolutePath()));
		}

		Map<String, String> values = values(project);

		/*
		 * 置いた skill の控え（要件 D-187）。./gradlew jimbleSkills がこれを見て、
		 * 「jimble が置いたまま」のものだけを新しい版に入れ替える。
		 * 書式は Gradle プラグインの SkillsInstaller と同じ
		 */
		StringBuilder record = new StringBuilder();
		record.append("# jimble が置いた skill の控え（./gradlew jimbleSkills が読み書きする。手で直さない）\n");

		java.util.TreeMap<String, String> hashes = new java.util.TreeMap<>();

		for (Skeleton.Entry entry : Skeleton.ENTRIES) {

			Path target = root.resolve(replace(entry.target(), values));
			String text = replace(read(entry), values);

			Files.createDirectories(target.getParent());
			Files.writeString(target, text, StandardCharsets.UTF_8);

			if (entry.target().startsWith(SKILLS_DIR)) {
				hashes.put("skills/" + entry.target().substring(SKILLS_DIR.length()), sha256(text.getBytes(StandardCharsets.UTF_8)));
			}

		}

		hashes.forEach((key, hash) -> record.append(key).append('=').append(hash).append('\n'));
		record.append("version=").append(Version.current()).append('\n');

		Files.writeString(root.resolve(".claude/" + SKILLS_RECORD), record.toString(), StandardCharsets.UTF_8);

		return root;

	}

	/**
	 * 置き換えるものの表
	 *
	 * @param project	プロジェクト
	 * @return	表
	 */
	private static Map<String, String> values (ProjectName project) {

		Map<String, String> values = new LinkedHashMap<>();

		values.put(KEY_NAME, project.name());
		values.put(KEY_PACKAGE, project.packageName());
		values.put(KEY_PACKAGE_PATH, project.packageName());
		values.put(KEY_DB, project.databaseName());
		values.put(KEY_VERSION, Version.current());

		return values;

	}

	/**
	 * 雛形を読む
	 *
	 * @param entry	ファイル
	 * @return	中身
	 */
	private static String read (Skeleton.Entry entry) {

		String path = Skeleton.resourcePath(entry);

		try (InputStream stream = NewCommand.class.getResourceAsStream(path)) {

			if (stream == null) {
				/*
				 * jar の作り方を間違えると起きる。
				 * 黙って空のファイルを置くと、原因が分からないものが手元に残る。
				 */
				throw new IllegalStateException("雛形が jar に入っていません: " + path);
			}

			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);

		} catch (IOException ex) {

			throw new UncheckedIOException("雛形を読めませんでした: " + path, ex);

		}

	}

	/**
	 * 置き換える
	 *
	 * @param text		文字列
	 * @param values	置き換えるものの表
	 * @return	置き換えたもの
	 */
	private static String replace (String text, Map<String, String> values) {

		String replaced = text;

		for (Map.Entry<String, String> value : values.entrySet()) {
			replaced = replaced.replace(value.getKey(), value.getValue());
		}

		return replaced;

	}

}
