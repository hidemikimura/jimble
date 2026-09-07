package io.jimble.util.url;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * URL Builder
 */
public class UrlBuilder {

	private String url;

	private final List<Parameter> parameterList = new ArrayList<>();

	public UrlBuilder url (String url) {
		this.url = url;
		return this;
	}

	public UrlBuilder addParameter (String name, String value) {
		parameterList.add(new Parameter(name, value));
		return this;
	}

	public String build () {
		return build(StandardCharsets.UTF_8);
	}

	public String build (Charset charset) {

		StringBuilder sb = new StringBuilder();
		sb.append(url);

		for (int i = 0; i < parameterList.size(); i++) {
			Parameter parameter = parameterList.get(i);
			if (i == 0) {
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

	private record Parameter (
		String name
		, String value
	) {}

}
