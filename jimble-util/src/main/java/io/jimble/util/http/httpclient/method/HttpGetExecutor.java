package io.jimble.util.http.httpclient.method;

import io.jimble.util.http.httpclient.AbstractHttpExecutor;

import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;

/**
 * GET
 */
public class HttpGetExecutor extends AbstractHttpExecutor<HttpGetExecutor> {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getMethod() {

		return "GET";

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Builder createRequestBuilder () {

		return HttpRequest.newBuilder().GET();

	}

}
