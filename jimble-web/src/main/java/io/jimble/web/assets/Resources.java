package io.jimble.web.assets;

import java.io.File;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;

/**
 * クラスパス上のリソースの存在確認
 *
 * <p>
 * <b>jar でもディレクトリでも同じ答えを返す</b>のが役目（要件 F-W-18）。
 * ディレクトリを「ファイルがある」と答えないことが要点で、
 * SPA / MPA の「ファイルが無ければ index.html」の判定がこれに乗る。
 * </p>
 *
 * <h2>移送元から変えたところ</h2>
 * <ol>
 *   <li><b>{@code JarFile} を開きっぱなしにしていた。</b>移送元は
 *       {@code new JarFile(...)} をフィールドに持ち、閉じていなかった。
 *       ここでは JDK がキャッシュする {@code JarURLConnection} を使う</li>
 *   <li><b>結果を無制限にキャッシュしていた。</b>移送元は Guava キャッシュに
 *       リクエストパスをそのまま入れていたので、<b>存在しないパスを大量に叩かれると
 *       キャッシュが際限なく増える。</b>上限を設けた</li>
 * </ol>
 */
public final class Resources {

	/** 存在確認のキャッシュ上限 */
	public static final int MAX_CACHE_SIZE = 4096;

	/* 存在確認のキャッシュ */
	private static final Map<String, Boolean> CACHE = new ConcurrentHashMap<>();

	private Resources () {}

	/**
	 * ファイルとして存在するか
	 *
	 * <p>ディレクトリは false。</p>
	 *
	 * @param resource	クラスパス上のパス（先頭 {@code /}）
	 * @return	存在する場合 = true
	 */
	public static boolean isFile (String resource) {

		if (resource == null || resource.isEmpty()) {
			return false;
		}

		Boolean cached = CACHE.get(resource);
		if (cached != null) {
			return cached;
		}

		boolean exists = lookup(resource);

		// 上限を超えたら作り直す。存在しないパスを大量に叩かれても増え続けない
		if (CACHE.size() >= MAX_CACHE_SIZE) {
			CACHE.clear();
		}
		CACHE.put(resource, exists);

		return exists;

	}

	/**
	 * 実際に見に行く
	 *
	 * @param resource	クラスパス上のパス
	 * @return	存在する場合 = true
	 */
	private static boolean lookup (String resource) {

		URL url = Resources.class.getResource(resource);

		if (url == null) {
			return false;
		}

		try {

			if ("file".equals(url.getProtocol())) {
				return new File(url.toURI()).isFile();
			}

			if ("jar".equals(url.getProtocol())) {
				// useCaches は既定（true）のまま。JDK が JarFile を持つので閉じない
				JarURLConnection connection = (JarURLConnection) url.openConnection();
				JarEntry entry = connection.getJarEntry();
				return entry != null && !entry.isDirectory();
			}

			// 上記以外のプロトコルは開けたら「ある」とみなす
			return url.openConnection().getContentLengthLong() >= 0;

		} catch (Exception ex) {

			return false;

		}

	}

	/**
	 * キャッシュを消す（テスト用）
	 */
	public static void clearCache () {

		CACHE.clear();

	}

	/**
	 * キャッシュの件数
	 *
	 * @return	件数
	 */
	public static int cacheSize () {

		return CACHE.size();

	}

}
