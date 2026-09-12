package io.jimble.util.url;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * URL を組み立てる
 *
 * <pre>
 * new UrlBuilder()
 *     .url("https://example.com/search")
 *     .addParameter("q", "犬 と 猫")
 *     .build();		// https://example.com/search?q=%E7%8A%AC+%E3%81%A8+%E7%8C%AB
 * </pre>
 *
 * <p>
 * <b>値は必ずエンコードする。</b>空白は {@code +} になる（クエリ文字列の決まり）。
 * </p>
 */
public class UrlBuilder {

	/* 土台の URL */
	private String url;

	/* 足すパラメータ */
	private final List<Parameter> parameterList = new ArrayList<>();

	/**
	 * 土台の URL
	 *
	 * <p>
	 * <b>すでに {@code ?} が付いていても構わない</b>——続きは {@code &} で足す。
	 * </p>
	 *
	 * @param url	URL
	 * @return	UrlBuilder
	 */
	public UrlBuilder url (String url) {
		this.url = url;
		return this;
	}

	/**
	 * パラメータを足す
	 *
	 * <p><b>同じ名前を何度でも足せる</b>（配列で受ける相手のため）。</p>
	 *
	 * @param name	名前
	 * @param value	値（{@code null} は空文字として扱う）
	 * @return	UrlBuilder
	 */
	public UrlBuilder addParameter (String name, String value) {
		parameterList.add(new Parameter(name, value == null ? "" : value));
		return this;
	}

	/**
	 * 組み立てる（UTF-8）
	 *
	 * @return	URL
	 */
	public String build () {
		return build(StandardCharsets.UTF_8);
	}

	/**
	 * 組み立てる
	 *
	 * @param charset	文字コード
	 * @return	URL
	 */
	public String build (Charset charset) {

		StringBuilder sb = new StringBuilder();
		sb.append(url);

		/*
		 * <b>土台にすでにクエリが付いていることがある（要件 D-161）。</b>
		 * 以前は無条件に {@code ?} を足していたので、
		 * {@code https://example.com/a?b=1} に1本足すと
		 * <b>{@code ...?b=1?c=2} という壊れた URL</b>が出来ていた——
		 * <b>例外は出ない。相手のサーバが「そんなパラメータは無い」と答えるだけ</b>である。
		 */
		boolean hasQuery = url != null && url.indexOf('?') >= 0;

		for (int i = 0; i < parameterList.size(); i++) {
			Parameter parameter = parameterList.get(i);
			if (i == 0 && !hasQuery) {
				sb.append("?");
			} else {
				sb.append("&");
			}
			sb.append(URLEncoder.encode(parameter.name, charset));
			sb.append('=');
			sb.append(URLEncoder.encode(parameter.value, charset));
		}

		return sb.toString();

	}

	/**
	 * パラメータ1本
	 *
	 * @param name	名前
	 * @param value	値
	 */
	private record Parameter (
		String name
		, String value
	) {}

}
