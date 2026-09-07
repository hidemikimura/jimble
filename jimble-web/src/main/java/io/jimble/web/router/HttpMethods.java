package io.jimble.web.router;

import java.util.List;

/**
 * HTTPメソッド
 */
public final class HttpMethods {

	/** GET */
	public static final String GET = "GET";
	/** POST */
	public static final String POST = "POST";
	/** PUT */
	public static final String PUT = "PUT";
	/** PATCH */
	public static final String PATCH = "PATCH";
	/** DELETE */
	public static final String DELETE = "DELETE";
	/** HEAD */
	public static final String HEAD = "HEAD";
	/** OPTIONS */
	public static final String OPTIONS = "OPTIONS";
	/** TRACE */
	public static final String TRACE = "TRACE";

	/** 全メソッド */
	public static final List<String> ALL = List.of(GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS, TRACE);

	/**
	 * コンストラクタ
	 */
	private HttpMethods () {

	}

}
