package io.jimble.util.internal.http.httpclient.method;

import io.jimble.util.internal.http.httpclient.AbstractHttpPostExecutor;

import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;

/**
 * PUT
 */
public class HttpPutExecutor extends AbstractHttpPostExecutor<HttpPutExecutor> {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getMethod() {

		return "PUT";

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Builder createRequestBuilder () {

		return HttpRequest.newBuilder().PUT(createBodyPublisher());

	}

}
