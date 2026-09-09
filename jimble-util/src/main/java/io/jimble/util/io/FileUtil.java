package io.jimble.util.io;

import io.jimble.util.string.StringUtil;
import io.jimble.util.log.Log;
import org.apache.tika.Tika;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.csv.TextAndCSVConfig;
import org.apache.tika.parser.csv.TextAndCSVParser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.helpers.DefaultHandler;

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

	/** 種別を見分けるもの（作り直すとクラスパスを走査するので使い回す） */
	private static final Tika TIKA = new Tika();

	/** 区切り文字の名前（{@code comma}）と文字（{@code ,}）の対応 */
	private static final TextAndCSVConfig CSV_CONFIG = new TextAndCSVConfig();

	/**
	 * ファイルのコンテンツタイプを取得する
	 *
	 * <p>
	 * 中身（マジックナンバー）とファイル名の両方を見る。中身のほうが強い。
	 * </p>
	 *
	 * <p>
	 * {@code Tika} は<b>使い回す</b>。作るたびに {@code META-INF/services} を
	 * 読み直すので、ファイルを返すたびに新しく作るとクラスパスの走査が毎回走る。
	 * スレッド安全である。
	 * </p>
	 *
	 * @param file	ファイル
	 * @return	コンテンツタイプ
	 */
	public static String getFileContentType (File file) {

		try {

			String contentType = TIKA.detect(file);
			if (contentType != null && !contentType.isEmpty()) {
				return contentType;
			}

			return Files.probeContentType(file.toPath());

		} catch (Exception ex) {

			return null;

		}

	}

	/**
	 * ファイルのメタデータ情報を取得する
	 *
	 * <p>
	 * 種別で振り分ける。画像・動画なら幅と高さ、CSV なら文字コードと区切り文字が付く。
	 * </p>
	 *
	 * <p>
	 * <b>種別はパラメータを落としてから見る。</b>ブラウザから来るのは
	 * {@code text/csv; charset=UTF-8} の形なので、そのまま突き合わせると
	 * <b>CSV なのに CSV として扱われない</b>。
	 * </p>
	 *
	 * @param file			ファイル
	 * @param contentType	種別（空なら中身から見分ける）
	 * @return	メタデータ情報（種別が分からなければ null）
	 */
	public static FileMetaData getFileMetaData (File file, String contentType) {

		if (contentType == null || contentType.isEmpty()) {
			contentType = getFileContentType(file);
		}

		String baseType = baseType(contentType);

		if (baseType == null) {
			return null;
		}

		if (baseType.startsWith("image/") || baseType.startsWith("video/")) {
			return getImageFileMetaData(file);
		}

		if (isDelimitedText(baseType)) {
			return csvMetaData(file, file.getName(), baseType);
		}

		return new FileMetaData(baseType, 0, 0, "", "");

	}

	/**
	 * 画像ファイルのメタデータ情報を取得する
	 *
	 * <p>
	 * 種別は中身から見分け（tika）、幅と高さは<b>ヘッダだけを読む</b>（{@link ImageSize}）。
	 * 画素は展開しないので、大きな画像でもヒープを食わない。
	 * </p>
	 *
	 * <p>
	 * <b>幅と高さが取れるのは PNG / JPEG / GIF / BMP / TIFF / WebP だけ</b>である。
	 * HEIC・AVIF・SVG と動画は 0 になる（要件 D-116）。
	 * </p>
	 *
	 * @param file	画像ファイル
	 * @return	メタデータ情報（種別が分からなければ null）
	 */
	public static FileMetaData getImageFileMetaData (File file) {

		String contentType = baseType(getFileContentType(file));

		if (contentType == null) {
			return null;
		}

		ImageSize size = ImageSize.read(file);

		return new FileMetaData(contentType, size.width(), size.height(), "", "");

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
	 * <p>
	 * 種別・文字コード・区切り文字を返す。
	 * </p>
	 *
	 * <h4>種別</h4>
	 * <p>
	 * tika が中身を読んで {@code text/csv} か {@code text/tsv} だと言えばそれを使い、
	 * 言えなければ<b>ファイル名から決めた種別</b>を使う。中身が短いと
	 * tika は「区切りが規則的だ」と言い切れず {@code text/plain} を返すので、
	 * <b>2行の CSV が text/plain になってしまう</b>のを防ぐためである。
	 * </p>
	 *
	 * <h4>文字コード</h4>
	 * <p>
	 * <b>tika が読んだものをそのまま返す。</b>種別を名前から決め直したときも捨てない
	 * （以前は種別ごと差し替えていたので、<b>文字コードが常に空になっていた</b>）。
	 * </p>
	 *
	 * <h4>区切り文字</h4>
	 * <p>
	 * tika が見分けた区切りを<b>その文字そのもの</b>で返す（{@code ","} / {@code "\t"} /
	 * {@code ";"} / {@code "|"}）。tika は {@code comma} のような名前で持っているので、
	 * ここで文字に戻す。
	 * </p>
	 *
	 * <p>
	 * <b>中身が短いと区切りは空になる。</b>tika は自信が無ければ言わないので、
	 * ここで当てずっぽうを返さない（間違った区切りを返すほうが困る）。
	 * </p>
	 *
	 * @param file	CSVファイル
	 * @param fileName	ファイル名
	 * @return	メタデータ情報（種別が分からなければ null）
	 */
	public static FileMetaData getCsvFileMetaData (File file, String fileName) {

		return csvMetaData(file, fileName, null);

	}

	/**
	 * CSV のメタデータを読む
	 *
	 * @param file			CSVファイル
	 * @param fileName		ファイル名
	 * @param declaredType	呼び出し側が分かっている種別（無ければ null）
	 * @return	メタデータ情報（種別が分からなければ null）
	 */
	private static FileMetaData csvMetaData (File file, String fileName, String declaredType) {

		TextAndCSVParser parser = new TextAndCSVParser();

		// 中身は読み捨てる。溜め込むと、大きな CSV でヒープを使い切る
		BodyContentHandler handler = new BodyContentHandler(new DefaultHandler());
		Metadata metaData = new Metadata();

		try (
			TikaInputStream is = TikaInputStream.get(file.toPath())
		) {
			parser.parse(is, handler, metaData, new ParseContext());
		} catch (Exception ex) {
			/*
			 * 途中で落ちても<b>そこまでに読めたものは使う</b>。
			 * 種別・文字コード・区切りは行を読み始める前に決まっているので、
			 * 途中の1行のために全部を捨てる理由がない。
			 * 何も読めていなければ、このあと種別が決まらず null になる
			 */
			Log.debug("CSV を最後まで読めませんでした: " + fileName);
		}

		String contentType = csvContentType(metaData, file, fileName, declaredType);

		if (contentType == null) {
			return null;
		}

		String charset = metaData.get(HttpHeaders.CONTENT_ENCODING);

		return new FileMetaData(
			contentType
			, 0
			, 0
			, charset == null ? "" : charset
			, delimiterOf(metaData)
		);

	}

	/**
	 * CSV の種別を決める
	 *
	 * <p>順に見る。</p>
	 * <ol>
	 *   <li>tika が中身を読んで {@code text/csv} / {@code text/tsv} と言った → それ</li>
	 *   <li>ファイル名からの判定が {@code text/csv} / {@code text/tsv} → それ</li>
	 *   <li>どちらもただのテキスト → <b>呼び出し側が言っていた種別</b>（無ければ {@code text/csv}）。
	 *       中身が短いと tika は区切りを言い切れず {@code text/plain} を返すので、
	 *       <b>2行の CSV が text/plain になってしまう</b>のを防ぐ</li>
	 *   <li>テキストですらない（名前だけ csv の画像など） → <b>その種別</b>。
	 *       CSV だと言い張らない</li>
	 * </ol>
	 *
	 * @param metaData		tika が読んだもの
	 * @param file			ファイル
	 * @param fileName		ファイル名
	 * @param declaredType	呼び出し側が分かっている種別（無ければ null）
	 * @return	種別（分からなければ null）
	 */
	private static String csvContentType (Metadata metaData, File file, String fileName, String declaredType) {

		String parsed = baseType(metaData.get(HttpHeaders.CONTENT_TYPE));
		if (isDelimitedText(parsed)) {
			return parsed;
		}

		String detected = detectByName(file, fileName);
		if (isDelimitedText(detected)) {
			return detected;
		}

		if (detected != null && !detected.startsWith("text/")) {
			return detected;
		}

		if (isDelimitedText(declaredType)) {
			return declaredType;
		}

		return parsed == null && detected == null ? null : "text/csv";

	}

	/**
	 * ファイル名も手がかりにして種別を見分ける
	 *
	 * @param file		ファイル
	 * @param fileName	ファイル名
	 * @return	種別（分からなければ null）
	 */
	private static String detectByName (File file, String fileName) {

		try (
			TikaInputStream is = TikaInputStream.get(file.toPath())
		) {

			Metadata metaData = new Metadata();
			metaData.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);

			return baseType(MimeTypes.getDefaultMimeTypes().detect(is, metaData, new ParseContext()).toString());

		} catch (Exception ex) {
			return null;
		}

	}

	/**
	 * 種別からパラメータ（{@code ; charset=...}）を落とす
	 *
	 * @param contentType	種別
	 * @return	種別（小文字）。無ければ null
	 */
	private static String baseType (String contentType) {

		if (contentType == null || contentType.isEmpty()) {
			return null;
		}

		int semicolon = contentType.indexOf(';');

		return (semicolon < 0 ? contentType : contentType.substring(0, semicolon)).trim().toLowerCase();

	}

	/**
	 * 区切りのあるテキストか
	 *
	 * @param contentType	種別
	 * @return	そうなら true
	 */
	private static boolean isDelimitedText (String contentType) {

		return "text/csv".equals(contentType) || "text/tsv".equals(contentType);

	}

	/**
	 * 区切り文字を取り出す
	 *
	 * <p>tika は {@code comma} のような名前で持っているので、文字に戻す。</p>
	 *
	 * @param metaData	tika が読んだもの
	 * @return	区切り文字（分からなければ空）
	 */
	private static String delimiterOf (Metadata metaData) {

		String name = metaData.get(TextAndCSVParser.DELIMITER_PROPERTY);

		if (name == null || name.isEmpty()) {
			return "";
		}

		Character delimiter = CSV_CONFIG.getNameToDelimiterMap().get(name);

		// 表に無い名前は文字に戻せない。名前をそのまま返すと「1文字」の約束が崩れる
		return delimiter == null ? "" : String.valueOf(delimiter);

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
