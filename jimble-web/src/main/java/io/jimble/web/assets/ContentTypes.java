package io.jimble.web.assets;

import java.util.Map;

/**
 * 拡張子から Content-Type を決める
 *
 * <p>
 * 移送元は {@code URLConnection.getContentType()} と
 * {@code Files.probeContentType()} を使っていた。
 * </p>
 *
 * <ul>
 *   <li>{@code URLConnection} は jar の中だと {@code content/unknown} を返しがち</li>
 *   <li>{@code Files.probeContentType()} は<b>OS の設定に依存する。</b>
 *       同じ jar でも動く環境によって結果が変わる</li>
 * </ul>
 *
 * <p>
 * <b>「jar でもディレクトリでも同じように動く」</b>（要件 F-W-18）ためには、
 * 環境を見ない対応表のほうがよい。
 * </p>
 */
public final class ContentTypes {

	/** 分からないときの型 */
	public static final String DEFAULT = "application/octet-stream";

	/* 拡張子 → Content-Type */
	private static final Map<String, String> TYPES = Map.ofEntries(
		Map.entry("html", "text/html; charset=UTF-8"),
		Map.entry("htm", "text/html; charset=UTF-8"),
		Map.entry("css", "text/css; charset=UTF-8"),
		Map.entry("js", "text/javascript; charset=UTF-8"),
		Map.entry("mjs", "text/javascript; charset=UTF-8"),
		Map.entry("json", "application/json; charset=UTF-8"),
		Map.entry("map", "application/json; charset=UTF-8"),
		Map.entry("xml", "application/xml; charset=UTF-8"),
		Map.entry("txt", "text/plain; charset=UTF-8"),
		Map.entry("csv", "text/csv; charset=UTF-8"),
		Map.entry("svg", "image/svg+xml"),
		Map.entry("png", "image/png"),
		Map.entry("jpg", "image/jpeg"),
		Map.entry("jpeg", "image/jpeg"),
		Map.entry("gif", "image/gif"),
		Map.entry("webp", "image/webp"),
		Map.entry("avif", "image/avif"),
		Map.entry("ico", "image/x-icon"),
		Map.entry("woff", "font/woff"),
		Map.entry("woff2", "font/woff2"),
		Map.entry("ttf", "font/ttf"),
		Map.entry("otf", "font/otf"),
		Map.entry("eot", "application/vnd.ms-fontobject"),
		Map.entry("pdf", "application/pdf"),
		Map.entry("zip", "application/zip"),
		Map.entry("mp4", "video/mp4"),
		Map.entry("webm", "video/webm"),
		Map.entry("mp3", "audio/mpeg"),
		Map.entry("wasm", "application/wasm")
	);

	private ContentTypes () {}

	/**
	 * パスから Content-Type を決める
	 *
	 * @param path	パス
	 * @return	Content-Type（分からなければ {@value #DEFAULT}）
	 */
	public static String of (String path) {

		return TYPES.getOrDefault(extension(path), DEFAULT);

	}

	/**
	 * 拡張子があるか
	 *
	 * @param path	パス
	 * @return	ある場合 = true
	 */
	public static boolean hasKnownExtension (String path) {

		return TYPES.containsKey(extension(path));

	}

	/**
	 * 拡張子
	 *
	 * @param path	パス
	 * @return	拡張子（小文字。無ければ空文字）
	 */
	private static String extension (String path) {

		if (path == null) {
			return "";
		}

		int slash = path.lastIndexOf('/');
		int dot = path.lastIndexOf('.');

		if (dot < 0 || dot < slash || dot == path.length() - 1) {
			return "";
		}

		return path.substring(dot + 1).toLowerCase();

	}

}
