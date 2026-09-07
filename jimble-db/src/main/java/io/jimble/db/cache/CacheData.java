package io.jimble.db.cache;

import io.jimble.util.io.FileUtil;

import java.io.File;
import java.util.Date;

/**
 * キャッシュデータ
 */
public class CacheData {

	/**
	 * コンストラクタ（エラー）
	 *
	 * @param cacheKey      キャッシュキー
	 * @param groupKey      グループキー
	 */
	public CacheData (String cacheKey, String groupKey) {

		this.error = true;
		this.cacheKey = cacheKey;
		this.groupKey = groupKey;

	}

	/**
	 * コンストラクタ
	 *
	 * @param cacheKey			キャッシュキー
	 * @param contentString		文字列コンテンツ
	 * @param contentType		コンテンツタイプ
	 * @param objectCreatedAt	オブジェクト作成日時
	 */
	public CacheData(String cacheKey, String contentString, String contentType, Date objectCreatedAt) {

		this(cacheKey, null, contentString, contentType, objectCreatedAt);

	}

	/**
	 * コンストラクタ
	 *
	 * @param cacheKey			キャッシュキー
	 * @param groupKey			グループキー
	 * @param contentString		文字列コンテンツ
	 * @param contentType		コンテンツタイプ
	 * @param objectCreatedAt	オブジェクト作成日時
	 */
	public CacheData(String cacheKey, String groupKey, String contentString, String contentType, Date objectCreatedAt) {

		this.cacheKey = cacheKey;
		this.groupKey = groupKey;
		this.hasContentString = contentString != null;
		this.contentString = contentString;
		this.contentType = contentType;
		if (objectCreatedAt != null) {
			this.objectCreatedAt.setTime(objectCreatedAt.getTime());
		}

	}

	/**
	 * コンストラクタ
	 *
	 * @param cacheKey			キャッシュキー
	 * @param contentFile		ファイルコンテンツ
	 * @param contentType		コンテンツタイプ
	 * @param objectCreatedAt	オブジェクト作成日時
	 */
	public CacheData(String cacheKey, File contentFile, String contentType, Date objectCreatedAt) {

		this(cacheKey, null, contentFile, contentType, objectCreatedAt);

	}

	/**
	 * コンストラクタ
	 *
	 * @param cacheKey			キャッシュキー
	 * @param groupKey			グループキー
	 * @param contentFile		ファイルコンテンツ
	 * @param contentType   	コンテンツタイプ
	 * @param objectCreatedAt	オブジェクト作成日時
	 */
	public CacheData(String cacheKey, String groupKey, File contentFile, String contentType, Date objectCreatedAt) {

		this.cacheKey = cacheKey;
		this.groupKey = groupKey;
		this.hasContentFile = contentFile != null && contentFile.isFile() && contentFile.exists();
		this.contentFile = contentFile;
		this.contentType = contentType;
		if (objectCreatedAt != null) {
			this.objectCreatedAt.setTime(objectCreatedAt.getTime());
		}

	}

	// region エラー

	/* エラー */
	private boolean error = false;

	/**
	 * エラー判定
	 *
	 * @return  エラー判定
	 */
	public boolean isError() {

		return error;

	}

	// endregion

	// region キャッシュキー

	/* キャッシュキー */
	private String cacheKey = null;

	/**
	 * キャッシュキーを取得する
	 *
	 * @return  キャッシュキー
	 */
	public String cacheKey () {

		return cacheKey;

	}

	// endregion

	// region キャッシュグループキー

	/* グループキー */
	private String groupKey = null;

	/**
	 * グループキーを取得する
	 *
	 * @return  グループキー
	 */
	public String groupKey () {

		return groupKey;

	}

	// endregion

	// region 文字列コンテンツ

	/* 文字列保持判定 */
	private boolean hasContentString = false;

	/* 文字列コンテンツ */
	private String contentString = null;

	/**
	 * 文字列保持判定
	 *
	 * @return  文字列保持判定
	 */
	public boolean hasContentString () {

		return hasContentString;

	}

	/**
	 * 文字列コンテンツを取得する
	 *
	 * @return  文字列コンテンツ
	 */
	public String contentString() {

		if (contentString == null && contentFile != null && contentFile.exists() && contentFile.isFile()) {
			contentString = FileUtil.readAll(contentFile);
		}

		return contentString;

	}

	// endregion

	// region ファイルコンテンツ

	/* ファイルコンテンツ保持判定 */
	private boolean hasContentFile = false;

	/* ファイルコンテンツ */
	private File contentFile = null;

	/**
	 * ファイルコンテンツ保持判定
	 *
	 * @return  ファイルコンテンツ保持判定
	 */
	public boolean hasContentFile () {

		return hasContentFile;

	}

	/**
	 * ファイルコンテンツを取得する
	 *
	 * @return  ファイルコンテンツ
	 */
	public File contentFile() {

		return contentFile;

	}

	// endregion

	// region コンテンツタイプ

	/* コンテンツタイプ */
	private String contentType = null;

	/**
	 * コンテンツタイプを取得する
	 *
	 * @return  コンテンツタイプ
	 */
	public String contentType() {

		return contentType;

	}

	// endregion

	// region オブジェクト作成日時

	/* オブジェクト作成日時 */
	private final Date objectCreatedAt = new Date();

	/**
	 * オブジェクト作成日時を取得する
	 *
	 * @return  オブジェクト作成日時
	 */
	public Date objectCreatedAt () {

		return objectCreatedAt;

	}

	// endregion

}
