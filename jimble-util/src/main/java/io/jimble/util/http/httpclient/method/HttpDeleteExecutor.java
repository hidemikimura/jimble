package io.jimble.util.http.httpclient.method;

import io.jimble.util.http.httpclient.AbstractHttpPostExecutor;

import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;

/**
 * DELETE
 */
public class HttpDeleteExecutor extends AbstractHttpPostExecutor<HttpDeleteExecutor> {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getMethod() {

		return "DELETE";

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Builder createRequestBuilder () {

		return HttpRequest.newBuilder().method("DELETE", createBodyPublisher());

	}

}
