package io.jimble.util.internal.http.httpclient.method;

import io.jimble.util.internal.http.httpclient.AbstractHttpExecutor;

import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;

/**
 * OPTIONS
 */
public class HttpOptionsExecutor extends AbstractHttpExecutor<HttpOptionsExecutor> {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getMethod() {

		return "OPTIONS";

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Builder createRequestBuilder () {

		return HttpRequest.newBuilder().method("OPTIONS", HttpRequest.BodyPublishers.noBody());

	}

}
