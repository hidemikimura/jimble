package io.jimble.gradle.ai;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;

/**
 * アプリの {@code .claude/skills/} を、使っている jimble の版の skill に揃える（要件 D-187）
 *
 * <h2>なぜ要るのか</h2>
 * <p>
 * {@code jimble new} は skill を<b>写して</b>置く。jimble の版を上げても写しはそのままなので、
 * <b>アプリの AI は古い jimble の説明を読み続ける</b>。実際、アプリ側の AI が
 * 「jimble の skill に書いていない補足」を自分で書き足していた（新しい skill には入っていたもの）。
 * </p>
 *
 * <h2>手で直したものは上書きしない</h2>
 * <p>
 * 置いたときの中身のハッシュを {@code .claude/jimble-skills.properties} に控える。
 * いまのファイルが控えと同じなら「jimble が置いたまま」なので入れ替えてよい。違えば<b>誰かが直した</b>ので触らない。
 * 控えの無いファイル（控えを書くようになる前の {@code jimble new} で置いたもの）も、
 * 直したかどうか分からないので触らない——{@code overwrite} を付けたときだけ入れ替える。
 * </p>
 *
 * <p>
 * <b>控えの書式</b>（{@code jimble-cli} の {@code jimble new} も同じものを書く）：
 * </p>
 * <pre>
 * version=1.5.0
 * skills/jimble/SKILL.md=&lt;中身の SHA-256（16 進の小文字）&gt;
 * </pre>
 */
public final class SkillsInstaller {

	/** 控えのファイル（{@code .claude/} の下） */
	public static final String RECORD = "jimble-skills.properties";

	/** 控えのキー：版 */
	public static final String KEY_VERSION = "version";

	/** 控えのキーの頭：skill */
	public static final String KEY_SKILL_PREFIX = "skills/";

	/**
	 * 1つの skill をどうしたか
	 */
	public enum Result {

		/** 無かったので置いた */
		ADDED,

		/** jimble が置いたままだったので入れ替えた */
		UPDATED,

		/** 同じだった */
		UNCHANGED,

		/** 頼まれたので、直してあったものを入れ替えた */
		OVERWRITTEN,

		/** 手で直してあるので触らなかった */
		KEPT_EDITED,

		/** 控えが無く、直したかどうか分からないので触らなかった */
		KEPT_UNKNOWN

	}

	/**
	 * 1つの skill の結果
	 *
	 * @param path		相対パス（{@code jimble/SKILL.md}）
	 * @param result	どうしたか
	 */
	public record Entry(String path, Result result) {}

	/**
	 * 結果
	 *
	 * @param version			揃えた版
	 * @param previousVersion	前に揃えた版。控えが無ければ空文字
	 * @param entries			skill ごとの結果
	 * @param created			無かったので置いた案内（{@code AGENTS.md} など）
	 * @param gone				控えにあるが、この版には無い skill（消さない）
	 */
	public record Report(String version, String previousVersion, List<Entry> entries, List<String> created, List<String> gone) {

		/**
		 * 触らなかったものがあるか
		 *
		 * @return	ある場合 = true
		 */
		public boolean hasKept () {

			return entries.stream().anyMatch(entry ->
				entry.result() == Result.KEPT_EDITED || entry.result() == Result.KEPT_UNKNOWN);

		}

	}

	private SkillsInstaller () {
	}

	/**
	 * 揃える
	 *
	 * @param rootDir	プロジェクトの根（{@code .claude/} と {@code AGENTS.md} を置く所）
	 * @param skills	この版の skill（相対パス → 中身）
	 * @param version	この版
	 * @param agents	{@code AGENTS.md} の中身（無ければ置く。あれば触らない）
	 * @param claude	{@code CLAUDE.md} の中身（同上）
	 * @param overwrite	控えの無いもの・直してあるものも入れ替えるか
	 * @return	結果
	 */
	public static Report install (Path rootDir, Map<String, byte[]> skills, String version
		, byte[] agents, byte[] claude, boolean overwrite) {

		try {

			Path claudeDir = rootDir.resolve(".claude");
			Path skillsDir = claudeDir.resolve("skills");
			Path recordFile = claudeDir.resolve(RECORD);

			Properties before = read(recordFile);
			Properties after = new Properties();
			after.setProperty(KEY_VERSION, version);

			List<Entry> entries = new ArrayList<>();

			for (Map.Entry<String, byte[]> skill : skills.entrySet()) {

				String path = skill.getKey();
				String key = KEY_SKILL_PREFIX + path;
				Path target = skillsDir.resolve(path);
				String bundledHash = sha256(skill.getValue());
				String recorded = before.getProperty(key);

				Result result;

				if (!Files.exists(target)) {
					write(target, skill.getValue());
					result = Result.ADDED;
				} else {

					String currentHash = sha256(Files.readAllBytes(target));

					if (currentHash.equals(bundledHash)) {
						result = Result.UNCHANGED;
					} else if (currentHash.equals(recorded)) {
						write(target, skill.getValue());
						result = Result.UPDATED;
					} else if (overwrite) {
						write(target, skill.getValue());
						result = Result.OVERWRITTEN;
					} else if (recorded != null) {
						result = Result.KEPT_EDITED;
					} else {
						result = Result.KEPT_UNKNOWN;
					}

				}

				/*
				 * 触らなかったものは<b>前の控えのまま</b>にする。
				 * この版のハッシュを書くと、次に「jimble が置いたまま」と見て上書きしてしまう
				 */
				if (result == Result.KEPT_EDITED) {
					after.setProperty(key, recorded);
				} else if (result != Result.KEPT_UNKNOWN) {
					after.setProperty(key, bundledHash);
				}

				entries.add(new Entry(path, result));

			}

			List<String> gone = new ArrayList<>();

			for (String key : new TreeSet<>(before.stringPropertyNames())) {
				if (key.startsWith(KEY_SKILL_PREFIX) && !skills.containsKey(key.substring(KEY_SKILL_PREFIX.length()))) {
					gone.add(key.substring(KEY_SKILL_PREFIX.length()));
				}
			}

			List<String> created = new ArrayList<>();

			if (createIfAbsent(rootDir.resolve("AGENTS.md"), agents)) {
				created.add("AGENTS.md");
			}

			if (createIfAbsent(rootDir.resolve("CLAUDE.md"), claude)) {
				created.add("CLAUDE.md");
			}

			writeRecord(recordFile, after);

			return new Report(version, before.getProperty(KEY_VERSION, ""), List.copyOf(entries), List.copyOf(created), List.copyOf(gone));

		} catch (IOException ex) {

			throw new UncheckedIOException("skill を揃えられませんでした: " + rootDir, ex);

		}

	}

	/**
	 * 控えの版（控えが無ければ空文字）
	 *
	 * @param rootDir	プロジェクトの根
	 * @return	版
	 */
	public static String recordedVersion (Path rootDir) {

		return read(rootDir.resolve(".claude").resolve(RECORD)).getProperty(KEY_VERSION, "");

	}

	/**
	 * SHA-256（16 進の小文字）
	 *
	 * @param bytes	中身
	 * @return	ハッシュ
	 */
	public static String sha256 (byte[] bytes) {

		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}

	}

	/**
	 * 控えを読む（無ければ空）
	 *
	 * @param file	ファイル
	 * @return	控え
	 */
	private static Properties read (Path file) {

		Properties properties = new Properties();

		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				properties.load(reader);
			} catch (IOException ex) {
				throw new UncheckedIOException("控えを読めませんでした: " + file, ex);
			}
		}

		return properties;

	}

	/**
	 * 控えを書く（キーの順に並べる。差分を読みやすくするため、日時の行は書かない）
	 *
	 * @param file			ファイル
	 * @param properties	控え
	 * @throws IOException	書けなかった場合
	 */
	private static void writeRecord (Path file, Properties properties) throws IOException {

		Files.createDirectories(file.getParent());

		StringBuilder text = new StringBuilder();
		text.append("# jimble が置いた skill の控え（./gradlew jimbleSkills が読み書きする。手で直さない）\n");

		for (String key : new TreeSet<>(properties.stringPropertyNames())) {
			text.append(key).append('=').append(properties.getProperty(key)).append('\n');
		}

		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			writer.write(text.toString());
		}

	}

	/**
	 * 書く
	 *
	 * @param file		ファイル
	 * @param bytes		中身
	 * @throws IOException	書けなかった場合
	 */
	private static void write (Path file, byte[] bytes) throws IOException {

		Files.createDirectories(file.getParent());
		Files.write(file, bytes);

	}

	/**
	 * 無ければ置く
	 *
	 * @param file		ファイル
	 * @param bytes		中身
	 * @return	置いた場合 = true
	 * @throws IOException	書けなかった場合
	 */
	private static boolean createIfAbsent (Path file, byte[] bytes) throws IOException {

		if (Files.exists(file)) {
			return false;
		}

		write(file, bytes);

		return true;

	}

}
