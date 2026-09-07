package io.jimble.util.http.httpclient.method;

import io.jimble.util.http.httpclient.AbstractHttpPostExecutor;

import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;

/**
 * PATCH
 */
public class HttpPatchExecutor extends AbstractHttpPostExecutor<HttpPatchExecutor> {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getMethod() {

		return "PATCH";

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Builder createRequestBuilder () {

		return HttpRequest.newBuilder().method("PATCH", createBodyPublisher());

	}

}
