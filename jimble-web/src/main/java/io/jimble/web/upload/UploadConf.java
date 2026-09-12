package io.jimble.web.upload;

import io.jimble.util.conf.Conf;

import java.nio.file.Path;

/**
 * ファイルアップロードの設定（要件 F-W-06 / NF-S-05）
 *
 * <pre>
 * upload {
 *   max_file_size  = 10MiB       # 1ファイルの上限
 *   max_total_size = 10MiB       # 1リクエストの合計上限（server.max_request_size と揃える）
 *   max_files      = 20         # 1リクエストのファイル数上限
 *   temp_dir       = ""         # 一時保存先（空なら OS の一時ディレクトリ）
 * }
 * </pre>
 *
 * <p>
 * <b>上限は既定で必ず掛かる</b>（NF-S-05）。設定を書かなくても無制限にはならない。
 * </p>
 */
public final class UploadConf {

	/** 設定キー：1ファイルの上限 */
	public static final String KEY_MAX_FILE_SIZE = "upload.max_file_size";

	/** 設定キー：1リクエストの合計上限 */
	public static final String KEY_MAX_TOTAL_SIZE = "upload.max_total_size";

	/** 設定キー：ファイル数の上限 */
	public static final String KEY_MAX_FILES = "upload.max_files";

	/** 設定キー：一時保存先 */
	public static final String KEY_TEMP_DIR = "upload.temp_dir";

	/** 既定の1ファイル上限（10MiB） */
	public static final long DEFAULT_MAX_FILE_SIZE = 10L * 1024 * 1024;

	/**
	 * 既定の合計上限（10MiB）
	 *
	 * <p>
	 * <b>{@code server.max_request_size} の既定と同じにしてある</b>（要件 D-159）。
	 * 以前は 50MiB だったが、<b>本文は先に helidon が 10MiB で切る</b>ので、
	 * <b>既定のままでは 50MiB に決して届かなかった</b>——
	 * しかも出るのはアップロードのエラーではなく別の失敗である。
	 * </p>
	 *
	 * <p>
	 * <b>上げるときは両方上げる。</b>片方だけ上げると起動時に落ちる。
	 * </p>
	 */
	public static final long DEFAULT_MAX_TOTAL_SIZE = 10L * 1024 * 1024;

	/** 既定のファイル数上限 */
	public static final int DEFAULT_MAX_FILES = 20;

	/** 一時ファイルの接頭辞 */
	public static final String TEMP_PREFIX = "jimble-upload-";

	private UploadConf () {}

	/**
	 * 1ファイルの上限（バイト）
	 *
	 * @return	バイト数
	 */
	public static long maxFileSize () {

		return Conf.conf().getBytes(KEY_MAX_FILE_SIZE, DEFAULT_MAX_FILE_SIZE);

	}

	/**
	 * 1リクエストの合計上限（バイト）
	 *
	 * @return	バイト数
	 */
	public static long maxTotalSize () {

		return Conf.conf().getBytes(KEY_MAX_TOTAL_SIZE, DEFAULT_MAX_TOTAL_SIZE);

	}

	/**
	 * ファイル数の上限
	 *
	 * @return	件数
	 */
	public static int maxFiles () {

		return Conf.conf().getInt(KEY_MAX_FILES, DEFAULT_MAX_FILES);

	}

	/**
	 * 一時保存先
	 *
	 * @return	ディレクトリ（未設定なら OS の一時ディレクトリ）
	 */
	public static Path tempDir () {

		String dir = Conf.conf().getString(KEY_TEMP_DIR, "");

		return dir.isEmpty() ? Path.of(System.getProperty("java.io.tmpdir")) : Path.of(dir);

	}

}
