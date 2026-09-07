package io.jimble.util.io;

import io.jimble.util.parse.Parse;
import io.jimble.util.string.StringUtil;
import io.jimble.util.log.Log;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.csv.TextAndCSVParser;
import org.apache.tika.sax.BodyContentHandler;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * ファイルユーティリティ.
 *
 * @author DN
 */
public final class FileUtil {

	private static final Map<Class<?>, Boolean> JAR_MAP = new HashMap<>();

	public static boolean isJar (Class<?> cls) {

		Boolean res = JAR_MAP.get(cls);
		if (res == null) {
			try {
				final File jarFile = new File(cls.getProtectionDomain().getCodeSource().getLocation().getPath());
				res = jarFile.isFile();
				JAR_MAP.put(cls, res);
			} catch (Exception ignore) {
				res = false;
				JAR_MAP.put(cls, false);
			}
		}

		return res;

	}

	/**
	 * ディレクトリを作成する
	 * 親ディレクトリを遡って作成する
	 *
	 * @param dir	ディレクトリ
	 */
	public static void mkdirs (File dir) {

		File parent = dir.getParentFile();
		if (parent == null) {
			return;
		}

		if (!parent.exists()) {
			mkdirs(parent);
		}

		if (!dir.exists()) {
			dir.mkdirs();
		}

	}

	/* 使用不可能な記号 */
	private static final String[] PROHIBITED_SYMBOLS = {
		"\\", "/", ":", "*", "?", "'", "\"", "<", ">"
	};

	/* WindowsとMS-DOSの予約デバイス名 */
	private static final String[] PROHIBITED_CHARACTERS = {
		"CON", "PRN", "AUX", "CLOCK$", "NUL",
		"COM0", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
		"LPT0", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
	};

	public static String safeFileName (String name, boolean includeExtension, String replace) {

		String result = name;

		for (String c : PROHIBITED_SYMBOLS) {
			result = result.replaceAll(Pattern.quote(c), replace);
		}

		for (String c : PROHIBITED_CHARACTERS) {
			if (c.equals(result)) {
				result = replace;
			}
		}

		if (!includeExtension) {
			result = result.replaceAll(Pattern.quote("."), replace);
		}

		return result;

	}

	public static void delete (File file) {

		if (file == null) {
			return;
		}

		if (!file.exists()) {
			return;
		}

		if (file.isDirectory()) {

			File[] files = file.listFiles();
			if (files != null) {

				for (File f : files) {

					delete(f);

				}

			}

		}

		file.delete();

	}

	/**
	 * ファイル/ディレクトリをコピーする
	 *
	 * @param src	コピー元
	 * @param dest	コピー先
	 */
	public static boolean copy (Path src, Path dest) {

		try {

			Files.walkFileTree(src, new SimpleFileVisitor<Path>() {

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

					Path targetFile = dest.resolve(src.relativize(file));
					Path parentDir = targetFile.getParent();
					Files.createDirectories(parentDir);
					Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
					return FileVisitResult.CONTINUE;

				}

			});

			return true;

		} catch (Exception ex) {

			Log.error(ex);
			return false;

		}

	}

	public static File getTempFile () {

		try {

			File file = File.createTempFile("temp", "dat");
			File dir = file.getParentFile();
			if (!dir.exists()) {
				dir.mkdirs();
			}

			return file;

		} catch (Exception ex) {

			File dir = new File("/tmp");
			if (!dir.exists()) {
				dir.mkdirs();
			}
			File file = new File(dir.getAbsolutePath(), String.format("%s_%s_%s", "temp", String.valueOf(System.currentTimeMillis()), StringUtil.createPassword(32)));

			return file;

		}

	}

	/**
	 * 作業ディレクトリを取得する.
	 *
	 * @return	作業ディレクトリ
	 */
	public static File getTempDir () {

		try {

			File file = getTempFile();
			File dir = file.getParentFile();
			if (!dir.exists()) {
				dir.mkdirs();
			}

			file.delete();
			return dir;

		} catch (Exception ex) {

			File dir = new File("/tmp");
			if (!dir.exists()) {
				dir.mkdirs();
			}

			return dir;

		}

	}

	/**
	 * ファイルコピー.
	 *
	 * @param src	元
	 * @param dest	先
	 * @return	正常に終了した場合 = true
	 */
	public static boolean copy (File src, File dest) {

		try {

			FileChannel srcChannel = new FileInputStream(src).getChannel();
			FileChannel destChannel = new FileOutputStream(dest).getChannel();

			try {

				srcChannel.transferTo(0, srcChannel.size(), destChannel);

				return true;

			} catch (Exception ex) {

				Log.error(ex);
				return false;

			} finally {

				srcChannel.close();
				destChannel.close();

			}

		} catch (Exception ex) {

			Log.error(ex);
			return false;

		}

	}

	/** ファイルサイズ単位. */
	private static final String[] FILESIZE_UNIT = new String[] { "B", "KB", "MB", "GB", "TB" };

	/**
	 * ファイルサイズ文字列を取得する.
	 *
	 * @param size ファイルサイズ
	 * @return ファイルサイズ文字列
	 */
	public static String getFileSize(long size) {

		if (size <= 0) {
			return "0B";
		}

		int digitGroups = (int) (Math.log10(size) / Math.log10(1024));

		double calc = Math.floor(size / Math.pow(1024, digitGroups) * 10d) / 10d;

		return new DecimalFormat("#,##0.#").format(calc) + FILESIZE_UNIT[digitGroups];

	}

	/**
	 * 数値をフォーマットする.
	 *
	 * @param size 数値
	 * @return フォーマット文字列
	 */
	public static String formatDecimal(long size) {

		return new DecimalFormat("#,##0.#").format(size);

	}

	public static String readAll (File file) {

		return readAll(file, "UTF-8");

	}

	public static String readAll (File file, String charset) {

		try {

			return Files.readString(file.toPath(), Charset.forName(charset));

		} catch (Exception ex) {}


		return null;

	}

	/**
	 * ディレクトリを検索する
	 *
	 * @param file	起点のディレクトリ
	 * @param name	検索対象ディレクトリ名
	 * @return	ディレクトリ
	 */
	public static File searchDir (File file, String name) {

		if (file == null) {
			return null;
		}

		if (file.isDirectory()) {

			if (file.getName().equals(name)) {

				return file;

			}

			File[] files = file.listFiles();
			if (files != null) {

				for (File f : files) {

					File res = searchDir(f, name);
					if (res != null) {
						return res;
					}

				}

			}

		}

		return null;

	}

	public static List<File> listFiles (File dir, boolean underLayer) {

		List<File> files = new ArrayList<>();

		if (dir == null || !dir.exists() || !dir.isDirectory()) {
			return files;
		}

		File[] fileArray = dir.listFiles();
		if (fileArray == null) {
			return files;
		}

		for (File f : fileArray) {

			if (f.isDirectory()) {

				if (underLayer) {
					files.addAll(listFiles(f, true));
				}

			} else {

				files.add(f);

			}

		}

		return files;

	}

	/**
	 * ファイルのコンテンツタイプを取得する
	 *
	 * @param file	ファイル
	 * @return	コンテンツタイプ
	 */
	public static String getFileContentType (File file) {

		try {

			Tika tika = new Tika();
			String contentType = tika.detect(file);
			if (contentType != null && !contentType.isEmpty()) {
				return contentType;
			}

			return Files.probeContentType(file.toPath());

		} catch (Exception ex) {

			return null;

		}

	}

	public static FileMetaData getFileMetaData (File file, String contentType) {

		if (contentType == null || contentType.isEmpty()) {
			contentType = getFileContentType(file);
		}

		if (contentType == null || contentType.isEmpty()) {
			return null;
		}

		String _contentType = contentType.toLowerCase();
		if (_contentType.startsWith("image/") || _contentType.startsWith("video/")) {
			return getImageFileMetaData(file);
		} else if (_contentType.equals("text/csv")) {
			return getCsvFileMetaData(file);
		} else {
			return new FileMetaData(
				contentType
				, 0
				, 0
				, ""
				, ""
			);
		}

	}

	/**
	 * 画像ファイルのメタデータ情報を取得する
	 *
	 * @param file	画像ファイル
	 * @return	メタデータ情報
	 */
	public static FileMetaData getImageFileMetaData (File file) {

		AutoDetectParser parser = new AutoDetectParser();
		BodyContentHandler handler = new BodyContentHandler(-1);
		Metadata metaData = new Metadata();
		ParseContext context = new ParseContext();
		try (
			InputStream is = new FileInputStream(file)
		) {
			parser.parse(is, handler, metaData, context);
		} catch (Exception ex) {
			return null;
		}

		String contentType = metaData.get(Metadata.CONTENT_TYPE);
		if (contentType != null) {
			contentType = contentType.toLowerCase();
		}

		int width = Parse.parseInt(metaData.get(Metadata.IMAGE_WIDTH));
		int height = Parse.parseInt(metaData.get(Metadata.IMAGE_LENGTH));

		return new FileMetaData(
			contentType
			, width
			, height
			, null
			, null
		);

	}

	/**
	 * CSVファイルのメタデータ情報を取得する
	 *
	 * @param file	CSVファイル
	 * @return	メタデータ情報
	 */
	public static FileMetaData getCsvFileMetaData (File file) {

		return getCsvFileMetaData(file, file.getName());

	}

	/**
	 * CSVファイルのメタデータ情報を取得する
	 *
	 * @param file	CSVファイル
	 * @param fileName	ファイル名
	 * @return	メタデータ情報
	 */
	public static FileMetaData getCsvFileMetaData (File file, String fileName) {

		TextAndCSVParser parser = new TextAndCSVParser();
		BodyContentHandler handler = new BodyContentHandler(-1);
		Metadata metaData = new Metadata();
		ParseContext context = new ParseContext();
		try (
			InputStream is = new FileInputStream(file)
		) {
			parser.parse(is, handler, metaData, context);
		} catch (Exception ex) {
			return null;
		}

		String mimeType = null;
		try (
			InputStream is = new FileInputStream(file);
			BufferedInputStream bufferedInputStream = new BufferedInputStream(is);
		) {
			Metadata metaData2 = new Metadata();
			metaData2.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
			mimeType = MimeTypes.getDefaultMimeTypes().detect(bufferedInputStream, metaData2).toString();
		} catch (Exception ignore) {}

		String contentType = metaData.get(Metadata.CONTENT_TYPE);
		if (mimeType != null && !mimeType.isEmpty()) {
			if (contentType == null || !contentType.startsWith("text/csv")) {
				contentType = mimeType;
			}
		}
		if (contentType == null || contentType.isEmpty()) {
			return null;
		}
		contentType = contentType.toLowerCase();

		String[] types = contentType.split(";");
		contentType = types[0];
		String charset = "";
		String delimiter = "";
		for (int i = 1; i < types.length; i++) {
			String value = types[i].trim();
			if (value.startsWith("charset=")) {
				charset = value.substring("charset=".length());
			} else if (value.startsWith("delimiter=")) {
				delimiter = value.substring("delimiter=".length());
			}
		}

		return new FileMetaData(
			contentType
			, 0
			, 0
			, charset
			, delimiter
		);

	}

	/**
	 * ファイルメタデータ情報
	 *
	 * @param contentType	ContentType
	 * @param width			横幅 ※ 画像ファイルの場合のみ
	 * @param height		縦幅 ※ 画像ファイルの場合のみ
	 * @param charset		文字コード ※ テキストファイルの場合のみ
	 * @param delimiter		区切り文字 ※ 区切り文字テキストファイルの場合のみ
	 */
	public record FileMetaData (
		String contentType
		, int width
		, int height
		, String charset
		, String delimiter
	){}

	/**
	 * コンストラクタ.
	 */
	private FileUtil() {
		// 隠蔽
	}

}
