package io.jimble.util.url;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.IDN;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * 公開サフィックス（{@code co.jp} のような「登録できる単位」の境目）
 *
 * <p>
 * もとは guava の {@code InternetDomainName} を呼んでいた。
 * 使っていたのがこれ1つだけで、<b>guava は 2.97MB</b> あったので、
 * 表（Public Suffix List）だけを持って自前に置き換えた（要件 D-121）。
 * 答えは guava と変わらない。
 * </p>
 *
 * <h2>表は古くなる</h2>
 * <p>
 * {@code public-suffix-icann.txt} は<b>取ってきた日のまま</b>である。
 * 新しい TLD が増えても、更新しないかぎり<b>黙って古い答えを返す</b>。
 * これは guava も同じで（guava の版を上げるまで古いまま）、
 * 抱え方が変わっただけである。ファイルの先頭に取得日と取り直し方を書いてある。
 * </p>
 *
 * <h2>ICANN の部分だけ</h2>
 * <p>
 * 一覧には ICANN が決めたものと、事業者が自分で登録したもの（{@code blogspot.com} など）がある。
 * ここは<b>前者だけ</b>を読む（guava の {@code registrySuffix()} に合わせた）。
 * </p>
 */
final class PublicSuffix {

	/** 一覧の置き場所 */
	private static final String RESOURCE = "public-suffix-icann.txt";

	/** ドメイン全体の長さの上限 */
	private static final int MAX_LENGTH = 253;

	/** ラベル1つの長さの上限 */
	private static final int MAX_LABEL_LENGTH = 63;

	private PublicSuffix () {
	}

	/**
	 * 一覧
	 *
	 * <p>
	 * <b>初めて呼ばれたときに読む。</b>74KB のファイルを、使わないアプリにも読ませない
	 * （原則5。起動時に黙って何かを読みに行かない）。
	 * </p>
	 */
	private static final class Rules {

		static final Set<String> EXACT = new HashSet<>();
		static final Set<String> WILDCARD = new HashSet<>();
		static final Set<String> EXCEPTION = new HashSet<>();

		static {

			InputStream stream = PublicSuffix.class.getResourceAsStream(RESOURCE);

			// jar の作り方を間違えて resources が入らなかったとき、
			// <b>NullPointerException で出ると原因が分からない</b>
			if (stream == null) {
				throw new IllegalStateException("公開サフィックスの一覧が見つかりません: " + RESOURCE);
			}

			try (
					InputStream in = stream;
					BufferedReader reader = new BufferedReader(
						new InputStreamReader(in, StandardCharsets.UTF_8))
			) {

				String line;

				while ((line = reader.readLine()) != null) {

					line = line.trim();

					if (line.isEmpty() || line.startsWith("//")) {
						continue;
					}

					if (line.startsWith("!")) {
						add(EXCEPTION, line.substring(1));
					} else if (line.startsWith("*.")) {
						add(WILDCARD, line.substring(2));
					} else {
						add(EXACT, line);
					}

				}

			} catch (IOException ex) {
				throw new UncheckedIOException("公開サフィックスの一覧が読めません: " + RESOURCE, ex);
			}

		}

		/**
		 * 1つ入れる。日本語などの TLD は punycode でも引けるようにする
		 *
		 * @param set	入れ先
		 * @param rule	ルール
		 */
		private static void add (Set<String> set, String rule) {

			String lower = toAsciiLowerCase(rule);
			set.add(lower);

			if (isAscii(lower)) {
				return;
			}

			/*
			 * 日本語などの TLD は punycode でも引けるようにする。
			 * <b>JDK の変換表は一覧より古いことがある</b>ので、変換できないものは
			 * そのまま（元の綴りだけ）にする。<b>ここで例外を投げると一覧ごと読めなくなる</b>
			 */
			try {
				set.add(toAsciiLowerCase(IDN.toASCII(lower)));
			} catch (IllegalArgumentException ex) {
				// 変換できないものは元の綴りだけで引く
			}

		}

	}

	/**
	 * レジストリサフィックスを返す（{@code www.google.co.jp} → {@code co.jp}）
	 *
	 * @param domain ドメイン
	 * @return レジストリサフィックス。分からなければ null
	 */
	static String registrySuffix (String domain) {

		int at = suffixStart(domain);

		if (at < 0) {
			return null;
		}

		return normalize(domain).substring(at);

	}

	/**
	 * レジストリサフィックスの1つ内側までを返す（{@code www.google.co.jp} → {@code google.co.jp}）
	 *
	 * @param domain ドメイン
	 * @return 1つ内側までのドメイン。分からなければ null
	 */
	static String topDomainUnderRegistrySuffix (String domain) {

		int at = suffixStart(domain);

		if (at <= 0) {
			return null;
		}

		String value = normalize(domain);

		// サフィックスの1つ手前のラベルの先頭を探す
		int start = value.lastIndexOf('.', at - 2);

		return value.substring(start + 1);

	}

	/**
	 * サフィックスが始まる位置を返す
	 *
	 * @param domain ドメイン
	 * @return 位置。分からなければ -1
	 */
	private static int suffixStart (String domain) {

		String value = normalize(domain);

		if (!isValid(value)) {
			return -1;
		}

		/*
		 * 左から順に「ここから後ろ」を候補にして、当たるいちばん長いものを探す。
		 * <b>例外（!）が当たったら、そのラベルを1つ削ったところが境目になる</b>
		 */
		int at = 0;

		while (at < value.length()) {

			String candidate = value.substring(at);

			if (Rules.EXCEPTION.contains(candidate)) {
				int dot = candidate.indexOf('.');
				return dot < 0 ? -1 : at + dot + 1;
			}

			if (Rules.EXACT.contains(candidate)) {
				return at;
			}

			// *.foo は「なにか1つ + foo」に当たる
			int dot = candidate.indexOf('.');
			if (dot >= 0 && Rules.WILDCARD.contains(candidate.substring(dot + 1))) {
				return at;
			}

			int next = value.indexOf('.', at);

			if (next < 0) {
				break;
			}

			at = next + 1;

		}

		return -1;

	}

	/**
	 * 小文字にして、末尾の「.」を落とす
	 *
	 * @param domain ドメイン
	 * @return ならしたドメイン
	 */
	private static String normalize (String domain) {

		String value = toAsciiLowerCase(replaceDots(domain));

		if (value.endsWith(".")) {
			value = value.substring(0, value.length() - 1);
		}

		return value;

	}

	/**
	 * 全角などの「。」を「.」に揃える
	 *
	 * <p>
	 * ブラウザは {@code 。}（{@code U+3002}）{@code ．}（{@code U+FF0E}）{@code ｡}（{@code U+FF61}）を
	 * 区切りとして扱う。<b>揃えないと、日本語入力のまま貼られたドメインが1つのラベルに見える</b>。
	 * </p>
	 *
	 * @param value 文字列
	 * @return 揃えた文字列
	 */
	private static String replaceDots (String value) {

		StringBuilder sb = null;

		for (int i = 0; i < value.length(); i++) {

			char c = value.charAt(i);

			if (c == 0x3002 || c == 0xFF0E || c == 0xFF61) {
				if (sb == null) {
					sb = new StringBuilder(value.length());
					sb.append(value, 0, i);
				}
				sb.append('.');
			} else if (sb != null) {
				sb.append(c);
			}

		}

		return sb == null ? value : sb.toString();

	}

	/**
	 * ASCII の大文字だけを小文字にする
	 *
	 * <p>
	 * <b>{@code toLowerCase} は使わない。</b>ドメインの大文字小文字の同一視は ASCII の範囲だけで、
	 * 言語ごとの規則（トルコ語の {@code I} など）を持ち込むと<b>環境によって答えが変わる</b>。
	 * guava も ASCII だけを落としていた。
	 * </p>
	 *
	 * @param value 文字列
	 * @return 小文字にした文字列
	 */
	private static String toAsciiLowerCase (String value) {

		StringBuilder sb = null;

		for (int i = 0; i < value.length(); i++) {

			char c = value.charAt(i);

			if (c >= 'A' && c <= 'Z') {
				if (sb == null) {
					sb = new StringBuilder(value.length());
					sb.append(value, 0, i);
				}
				sb.append((char) (c + 32));
			} else if (sb != null) {
				sb.append(c);
			}

		}

		return sb == null ? value : sb.toString();

	}

	/**
	 * ドメインとして読める形か
	 *
	 * @param value ならしたドメイン
	 * @return 読めるなら true
	 */
	private static boolean isValid (String value) {

		if (value.isEmpty() || value.length() > MAX_LENGTH) {
			return false;
		}

		int at = 0;

		while (at <= value.length()) {

			int next = value.indexOf('.', at);
			int end = next < 0 ? value.length() : next;

			if (!isValidLabel(value, at, end)) {
				return false;
			}

			if (next < 0) {
				break;
			}

			at = next + 1;

		}

		return true;

	}

	/**
	 * ラベル1つが読める形か
	 *
	 * @param value	ドメイン
	 * @param from	ラベルの始まり
	 * @param to	ラベルの終わり
	 * @return 読めるなら true
	 */
	private static boolean isValidLabel (String value, int from, int to) {

		int length = to - from;

		if (length == 0 || length > MAX_LABEL_LENGTH) {
			return false;
		}

		/*
		 * <b>ASCII だけのラベルしか中身を見ない。</b>
		 * 日本語などのラベルは、どの文字を許すかが規格ごとに違ううえ、
		 * ここで弾くと<b>正しいドメインまで落ちる</b>（結合文字を使う文字体系がある）。
		 * guava も同じで、ASCII でないラベルは素通ししていた。
		 */
		for (int i = from; i < to; i++) {
			if (value.charAt(i) > 0x7F) {
				return true;
			}
		}

		if (isDash(value.charAt(from)) || isDash(value.charAt(to - 1))) {
			return false;
		}

		for (int i = from; i < to; i++) {

			char c = value.charAt(i);

			if (c >= 'a' && c <= 'z') continue;
			if (c >= '0' && c <= '9') continue;
			if (isDash(c)) continue;

			return false;

		}

		return true;

	}

	/**
	 * ラベルの端に置けない記号か
	 *
	 * @param c 文字
	 * @return 置けないなら true
	 */
	private static boolean isDash (char c) {

		return c == '-' || c == '_';

	}

	/**
	 * ASCII だけでできているか
	 *
	 * @param value 文字列
	 * @return ASCII だけなら true
	 */
	private static boolean isAscii (String value) {

		for (int i = 0; i < value.length(); i++) {
			if (value.charAt(i) > 0x7F) {
				return false;
			}
		}

		return true;

	}

}
