package io.jimble.web.cors;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * CORS
 */
public final class Cors {

	/* 全Origin許可判定 */
	private boolean isAllAllowOrigin = false;

	/* Access-Control-Allow-Origin */
	private final List<String> allowedOrigins = new ArrayList<>();

	/* Access-Control-Allow-Headers */
	private final List<String> allowedHeaders = new ArrayList<>();

	/* Access-Control-Allow-Methods */
	private final List<String> allowedMethods = new ArrayList<>();

	/* Access-Control-Expose-Headers */
	private final List<String> exposeHeaders = new ArrayList<>();

	/*
	 * Access-Control-Allow-Credentials
	 *
	 * <b>既定は false である（D-173）。</b>かつては true だった——
	 * allAllowOrigin() と並ぶと「どの origin にも Cookie 付きを許す」ことになり、
	 * しかも受け取った origin をそのまま返すので、
	 * ブラウザが * に掛けている「Cookie は付けない」という制約も効かない。
	 * Cookie を渡してよい相手は、名指しで決めるものである。
	 */
	private boolean allowCredentials = false;

	/* Access-Control-Max-Age */
	private int maxAge = 600;

	/* origin matcher */
	private Matcher<String> originMatcher;

	/* header matcher */
	private Matcher<List<String>> headerMatcher;

	/* method matcher */
	private Matcher<String> methodMatcher;

	{
		// デフォルト値

		// Allow-Headers
		allowedHeaders.add("Content-Type");
		allowedHeaders.add("Accept");
		allowedHeaders.add("Csrf-Token");
		allowedHeaders.add("Origin");

		// Allow-Methods
		allowedMethods.add("GET");
		allowedMethods.add("POST");
		allowedMethods.add("OPTIONS");
	}

	/**
	 * Allow-Originを取得する
	 *
	 * @return  Allow-Origin
	 */
	public List<String> allowedOrigins () {

		/*
		 * 中の List は返さない（D-173）。返すと add(...) で足せてしまうが、
		 * マッチャは addAllowXxx のときにしか作り直さないので
		 * 足した分は以後ずっと無視される——通ると思って足したものが、通らない。
		 */
		return java.util.Collections.unmodifiableList(allowedOrigins);

	}

	/**
	 * 全Origin許可判定
	 *
	 * @return  Cors
	 */
	public Cors allAllowOrigin () {

		/*
		 * Cookie 付きの「どこからでも」は作らせない（D-173）。
		 * この2つが揃うと、任意のサイトが利用者のログイン状態のまま API を叩ける。
		 * 受け取った origin をそのまま返す作りなので、ブラウザ側の制約も効かない。
		 */
		if (allowCredentials) {
			throw new IllegalStateException(
				"allAllowOrigin() と allowCredentials(true) は同時に指定できません"
					+ "（どの origin にも Cookie 付きを許すことになります）");
		}

		this.isAllAllowOrigin = true;
		return this;

	}

	/**
	 * Allow-Originを追加する
	 *
	 * @param origin    origin
	 * @return  Cors
	 */
	public Cors addAllowOrigin (String origin) {

		addOnce(allowedOrigins, origin);
		originMatcher = null;
		return this;

	}

	/**
	 * Allow-Headerを取得する
	 *
	 * @return  Allow-Header
	 */
	public List<String> allowedHeaders () {

		/*
		 * 中の List は返さない（D-173）。返すと add(...) で足せてしまうが、
		 * マッチャは addAllowXxx のときにしか作り直さないので
		 * 足した分は以後ずっと無視される——通ると思って足したものが、通らない。
		 */
		return java.util.Collections.unmodifiableList(allowedHeaders);

	}

	/**
	 * Allow-Headerを追加する
	 *
	 * @param header    header名
	 * @return  Cors
	 */
	public Cors addAllowHeader (String header) {

		addOnce(allowedHeaders, header);
		headerMatcher = null;
		return this;

	}

	/**
	 * Allow-Headerを取得する
	 *
	 * @return  Allow-Header
	 */
	public List<String> allowedMethods () {

		/*
		 * 中の List は返さない（D-173）。返すと add(...) で足せてしまうが、
		 * マッチャは addAllowXxx のときにしか作り直さないので
		 * 足した分は以後ずっと無視される——通ると思って足したものが、通らない。
		 */
		return java.util.Collections.unmodifiableList(allowedMethods);

	}

	/**
	 * any header判定
	 *
	 * @return  any headerの場合 = true
	 */
	public boolean anyHeader () {

		if (headerMatcher == null) {
			headerMatcher = allMatch(allowedHeaders);
		}

		return headerMatcher.wild;

	}

	/**
	 * Allow-Methodを追加する
	 *
	 * @param method    method
	 * @return  Cors
	 */
	public Cors addAllowedMethod (String method) {

		addOnce(allowedMethods, method);
		methodMatcher = null;
		return this;

	}

	/**
	 * Expose-Headerを取得する
	 *
	 * @return  Expose-Header
	 */
	public List<String> exposeHeaders () {

		/*
		 * 中の List は返さない（D-173）。返すと add(...) で足せてしまうが、
		 * マッチャは addAllowXxx のときにしか作り直さないので
		 * 足した分は以後ずっと無視される——通ると思って足したものが、通らない。
		 */
		return java.util.Collections.unmodifiableList(exposeHeaders);

	}

	/**
	 * Expose-Headerを追加する
	 *
	 * @param header    header
	 * @return  Cors
	 */
	public Cors addExposeHeader (String header) {

		addOnce(exposeHeaders, header);
		return this;

	}

	/**
	 * Allow-Credentialsを取得する
	 *
	 * @return  Allow-Credentials
	 */
	public boolean allowCredentials () {

		return allowCredentials;

	}

	/**
	 * Allow-Credentialsを設定する
	 *
	 * @param allowCredentials  Allow-Credentials
	 * @return  Cors
	 */
	public Cors allowCredentials (boolean allowCredentials) {

		if (allowCredentials && isAllAllowOrigin) {
			throw new IllegalStateException(
				"allAllowOrigin() と allowCredentials(true) は同時に指定できません"
					+ "（どの origin にも Cookie 付きを許すことになります）");
		}

		this.allowCredentials = allowCredentials;
		return this;

	}

	/**
	 * Access-Control-Max-Ageを取得する
	 *
	 * @return  Access-Control-Max-Age
	 */
	public int maxAge () {

		return maxAge;

	}

	/**
	 * Access-Control-Max-Ageを設定する
	 *
	 * @param maxAge    Access-Control-Max-Age
	 * @return  Cors
	 */
	public Cors maxAge (int maxAge) {

		this.maxAge = maxAge;
		return this;

	}

	/**
	 * 重複しないように足す
	 *
	 * <p>
	 * {@code allowedMethods} には既定で {@code GET} / {@code POST} / {@code OPTIONS} が
	 * 入っている。移送元は素の {@code add} だったので、
	 * <b>既定と同じものを足すとヘッダに二重に出ていた</b>（{@code GET, POST, OPTIONS, GET, POST}）。
	 * </p>
	 *
	 * @param list	追加先
	 * @param value	値
	 */
	private static void addOnce (List<String> list, String value) {

		if (value == null || value.isEmpty()) {
			return;
		}

		for (String current : list) {
			if (current.equalsIgnoreCase(value)) {
				return;
			}
		}

		list.add(value);

	}

	/**
	 * originが一致するか
	 *
	 * @param origin    リクエストオリジン
	 * @return  一致する場合 = true
	 */
	public boolean matchOrigin (String origin) {

		if (isAllAllowOrigin) {
			return true;
		}

		if (originMatcher == null) {
			originMatcher = firstMatch(allowedOrigins);
		}

		return originMatcher.test(origin);

	}

	/**
	 * ヘッダーが一致するか
	 *
	 * @param headers   ヘッダー一覧
	 * @return  一致する場合 = true
	 */
	public boolean matchHeader (List<String> headers) {

		if (headerMatcher == null) {
			headerMatcher = allMatch(allowedHeaders);
		}

		return headerMatcher.test(headers);

	}

	/**
	 * methodが一致するか
	 *
	 * @param method    method
	 * @return  一致する場合 = true
	 */
	public boolean matchMethod (String method) {

		if (methodMatcher == null) {
			methodMatcher = firstMatch(allowedMethods);
		}

		return methodMatcher.test(method);

	}

	/**
	 * いずれかにマッチするか判定するMatcherを返す
	 *
	 * @param values    List
	 * @return  Matcher
	 */
	private static Matcher<String> firstMatch (final List<String> values) {

		List<Pattern> patterns = values.stream().map(Cors::rewrite).toList();
		Predicate<String> predicate = it ->
			patterns.stream().anyMatch(pattern -> pattern.matcher(it).matches());

		return new Matcher<>(values, predicate);

	}

	/**
	 * すべてにマッチするか判定するMatcherを返す
	 *
	 * @param values    List
	 * @return  Matcher
	 */
	private static Matcher<List<String>> allMatch (final List<String> values) {

		Predicate<String> predicate = firstMatch(values);
		Predicate<List<String>> allmatch = it ->
			it.stream().allMatch(predicate);

		return new Matcher<>(values, allmatch);
	}

	/**
	 * originリライト
	 *
	 * @param origin    origin
	 * @return  Pattern
	 */
	private static Pattern rewrite (final String origin) {

		return Pattern.compile(origin.replace(".", "\\.").replace("*", ".*"), Pattern.CASE_INSENSITIVE);

	}

	/**
	 * matcher
	 */
	private static class Matcher<T> implements Predicate<T> {

		private List<String> values;

		private Predicate<T> predicate;

		private boolean wild;

		Matcher(final List<String> values, final Predicate<T> predicate) {
			this.values = values;
			this.predicate = predicate;
			this.wild = values.contains("*");
		}

		@Override
		public boolean test(final T value) {

			return predicate.test(value);

		}

		@Override
		public String toString() {

			return values.toString();

		}

	}

}
