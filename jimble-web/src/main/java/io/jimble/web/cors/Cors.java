package io.jimble.web.cors;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * CORS
 */
public class Cors {

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

	/* Access-Control-Allow-Credentials */
	private boolean allowCredentials = true;

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

		return allowedOrigins;

	}

	/**
	 * 全Origin許可判定
	 *
	 * @return  Cors
	 */
	public Cors allAllowOrigin () {

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

		return allowedHeaders;

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

		return allowedMethods;

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

		return exposeHeaders;

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
