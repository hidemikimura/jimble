package io.jimble.docs;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 言語ごとの文言（要件 NF-D-06）
 *
 * <p>
 * <b>ページの中身とは別に、枠にも文言がある。</b>
 * 見出しの並び・注記の見出し・検索欄・前後のリンク・フッタ。
 * ここを日本語のまま英語版に出すと<b>半分だけ英語のサイト</b>になる。
 * </p>
 *
 * <p>
 * <b>知らない言語が来たら既定（日本語）に落とす。</b>
 * 文言が1つ足りないだけでビルドが落ちるより、
 * 出てから直せるほうがよい。
 * </p>
 */
final class Texts {

	/** 既定の言語 */
	static final String DEFAULT_LANGUAGE = "ja";

	/**
	 * コンストラクタ
	 */
	private Texts () {

	}

	// region まとまりの並び

	/* まとまりの並び（サイドバーに出る順） */
	private static final Map<String, List<String>> SECTIONS = Map.of(
		"ja", List.of("はじめに", "基本", "データベース", "実行基盤", "プロトコル", "開発", "そのほか")
		, "en", List.of("Getting started", "Basics", "Database", "Runtime", "Protocols", "Development", "More")
	);

	/**
	 * まとまりの並び
	 *
	 * @param language	言語
	 * @return	並び
	 */
	static List<String> sections (String language) {

		return SECTIONS.getOrDefault(language, SECTIONS.get(DEFAULT_LANGUAGE));

	}

	/**
	 * まとまりが分からないページの行き先
	 *
	 * @param language	言語
	 * @return	まとまりの名前
	 */
	static String otherSection (String language) {

		List<String> sections = sections(language);

		return sections.get(sections.size() - 1);

	}

	// endregion

	// region 注記の見出し

	/* 注記の印 → 見出し */
	private static final Map<String, Map<String, String>> CALLOUTS = Map.of(
		"ja", Map.of("note", "補足", "tip", "こつ", "warn", "注意", "trap", "落とし穴")
		, "en", Map.of("note", "Note", "tip", "Tip", "warn", "Careful", "trap", "Trap")
	);

	/**
	 * 注記の見出し
	 *
	 * @param language	言語
	 * @return	印 → 見出し
	 */
	static Map<String, String> callouts (String language) {

		return CALLOUTS.getOrDefault(language, CALLOUTS.get(DEFAULT_LANGUAGE));

	}

	// endregion

	// region 枠の文言

	/**
	 * 枠の文言（テンプレートに渡す）
	 *
	 * @param language	言語
	 * @return	キー → 文言
	 */
	static Map<String, String> ui (String language) {

		Map<String, String> ja = new LinkedHashMap<>();
		ja.put("skip", "本文へ");
		ja.put("search", "検索");
		ja.put("nav", "目次");
		ja.put("pager", "前後のページ");
		ja.put("previous", "前");
		ja.put("next", "次");
		ja.put("toc", "このページの中身");
		ja.put("onThisPage", "このページ");
		ja.put("tagline", "jimble — Java 製の Web アプリケーションフレームワーク / Apache-2.0");
		ja.put("colophon", "このサイトは jimble のテンプレート（jte）で作った静的 HTML です。外部の CDN には繋いでいません。");

		if (!"en".equals(language)) {
			return ja;
		}

		Map<String, String> en = new LinkedHashMap<>();
		en.put("skip", "Skip to content");
		en.put("search", "Search");
		en.put("nav", "Pages");
		en.put("pager", "Previous and next page");
		en.put("previous", "Previous");
		en.put("next", "Next");
		en.put("toc", "On this page");
		en.put("onThisPage", "On this page");
		en.put("tagline", "jimble — a Java web framework / Apache-2.0");
		en.put("colophon", "This site is static HTML built with jimble's own template engine (jte). It talks to no external CDN.");

		return en;

	}

	// endregion

}
