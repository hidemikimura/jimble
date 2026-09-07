package io.jimble.db.cache;

import io.jimble.util.conf.Conf;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.zip.GZIPOutputStream;

/**
 * cache
 */
public abstract class AbstractCache implements ICache {

	/**
	 * キャッシュ内容をファイルに出力する
	 *
	 * @param cacheKey			キャッシュキー
	 * @param groupKey			グループキー
	 * @param contentType		コンテンツタイプ
	 * @param contentLength		コンテンツ長
	 * @param content			コンテント
	 * @param outputFile		出力先ファイル
	 * @param objectCreatedAt	オブジェクト作成日時
	 * @return  キャッシュデータ
	 */
	protected CacheData writeFileCache (String cacheKey, String groupKey, String contentType, long contentLength, String content, File outputFile, Date objectCreatedAt) {

		if (!Cache.isFileResponse(contentLength)) {
			return new CacheData(cacheKey, groupKey, content, contentType, objectCreatedAt);
		}

		if (Conf.readOnlyContainer()) {
			return new CacheData(cacheKey, groupKey, content, contentType, objectCreatedAt);
		}

		File tempFile = new File(outputFile.getParentFile(), outputFile.getName() + "_" + System.currentTimeMillis());
		try (
			FileOutputStream fos = new FileOutputStream(tempFile);
			GZIPOutputStream gos = new GZIPOutputStream(fos);
			OutputStreamWriter osw = new OutputStreamWriter(gos);
			BufferedWriter bw = new BufferedWriter(osw)
		) {
			bw.write(content);
		} catch (Exception ex) {
			tempFile.delete();
			if (outputFile.exists()) {
				return new CacheData(cacheKey, groupKey, outputFile, contentType, objectCreatedAt);
			} else {
				return new CacheData(cacheKey, groupKey, content, contentType, objectCreatedAt);
			}
		}

		try {
			Files.move(tempFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			return new CacheData(cacheKey, groupKey, outputFile, contentType, objectCreatedAt);
		} catch (Exception ex) {
			tempFile.delete();
			if (outputFile.exists()) {
				return new CacheData(cacheKey, groupKey, outputFile, contentType, objectCreatedAt);
			} else {
				return new CacheData(cacheKey, groupKey, content, contentType, objectCreatedAt);
			}
		}

	}

}
