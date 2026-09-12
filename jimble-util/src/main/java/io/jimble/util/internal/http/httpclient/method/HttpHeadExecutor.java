package io.jimble.util.internal.http.httpclient.method;

import io.jimble.util.internal.http.httpclient.AbstractHttpExecutor;

import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;

/**
 * HEAD
 */
public class HttpHeadExecutor extends AbstractHttpExecutor<HttpHeadExecutor> {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getMethod() {

		return "HEAD";

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Builder createRequestBuilder () {

		return HttpRequest.newBuilder().method("HEAD", HttpRequest.BodyPublishers.noBody());

	}

}
