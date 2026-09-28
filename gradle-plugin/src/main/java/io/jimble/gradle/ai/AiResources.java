package io.jimble.gradle.ai;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * プラグインに入れてある skill と AI への案内（要件 D-187）
 *
 * <p>
 * <b>正はリポジトリの {@code .claude/skills/} の1か所だけ。</b>プラグインのビルドが写して jar に入れる
 * （{@code jimble-cli} と同じ考え方。手で写した写しを置くと、片方だけ古くなる）。
 * プラグインの版はアプリが使っている jimble の版なので、<b>ここにある skill はアプリの版のもの</b>になる。
 * </p>
 */
public final class AiResources {

	/* 置き場所 */
	private static final String BASE = "/io/jimble/gradle/ai/";

	private AiResources () {
	}

	/**
	 * 入れてある skill（{@code jimble/SKILL.md} の形の相対パス → 中身）
	 *
	 * @return	skill。並びは索引のとおり
	 */
	public static Map<String, byte[]> skills () {

		Map<String, byte[]> skills = new LinkedHashMap<>();

		for (String line : text("skills.txt").split("\n")) {

			String path = line.trim();

			if (!path.isEmpty()) {
				skills.put(path, bytes("skills/" + path));
			}

		}

		if (skills.isEmpty()) {
			throw new IllegalStateException("skill がプラグインに入っていません（ビルドの不具合）");
		}

		return skills;

	}

	/**
	 * この jimble の版
	 *
	 * @return	版
	 */
	public static String version () {

		return text("version.txt").trim();

	}

	/**
	 * AI への案内（{@code AGENTS.md}）
	 *
	 * @return	中身
	 */
	public static byte[] agents () {

		return bytes("AGENTS.md");

	}

	/**
	 * Claude Code への案内（{@code CLAUDE.md}。{@code AGENTS.md} を読み込むだけ）
	 *
	 * @return	中身
	 */
	public static byte[] claude () {

		return bytes("CLAUDE.md");

	}

	/**
	 * 文字で読む
	 *
	 * @param name	名前
	 * @return	中身
	 */
	static String text (String name) {

		return new String(bytes(name), StandardCharsets.UTF_8);

	}

	/**
	 * 読む
	 *
	 * @param name	名前
	 * @return	中身
	 */
	static byte[] bytes (String name) {

		try (InputStream stream = AiResources.class.getResourceAsStream(BASE + name)) {

			if (stream == null) {
				throw new IllegalStateException("プラグインに入っていません（ビルドの不具合）: " + BASE + name);
			}

			return stream.readAllBytes();

		} catch (IOException ex) {

			throw new UncheckedIOException("読めませんでした: " + BASE + name, ex);

		}

	}

}
