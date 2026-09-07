package io.jimble.web.upload;

import io.jimble.util.conf.Conf;

import java.nio.file.Path;

/**
 * ファイルアップロードの設定（要件 F-W-06 / NF-S-05）
 *
 * <pre>
 * upload {
 *   max_file_size  = 10485760   # 1ファイルの上限（バイト。既定 10MB）
 *   max_total_size = 52428800   # 1リクエストの合計上限（バイト。既定 50MB）
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

	/** 既定の1ファイル上限（10MB） */
	public static final long DEFAULT_MAX_FILE_SIZE = 10L * 1024 * 1024;

	/** 既定の合計上限（50MB） */
	public static final long DEFAULT_MAX_TOTAL_SIZE = 50L * 1024 * 1024;

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

		return Conf.conf().getLong(KEY_MAX_FILE_SIZE, DEFAULT_MAX_FILE_SIZE);

	}

	/**
	 * 1リクエストの合計上限（バイト）
	 *
	 * @return	バイト数
	 */
	public static long maxTotalSize () {

		return Conf.conf().getLong(KEY_MAX_TOTAL_SIZE, DEFAULT_MAX_TOTAL_SIZE);

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
