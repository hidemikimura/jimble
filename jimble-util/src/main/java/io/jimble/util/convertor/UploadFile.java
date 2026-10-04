package io.jimble.util.convertor;

import java.io.File;

/**
 * 送られてきたファイル1つ（multipart/form-data）
 *
 * <p>
 * <b>中身は一時ファイルに落としてある。</b>{@link #file()} はリクエストが終わると消えるので、
 * 残したいものは<b>そのリクエストの中で</b>別の場所へ写すこと。
 * </p>
 *
 * <p>
 * <b>public フィールドではない（D-173）。</b>フィールドは 1.0 のあと
 * アクセサに置き換えられない——検証も、遅延計算も、不変化も入れられなくなる。
 * 5本を1本ずつ埋める形だったので、<b>埋め忘れたものが外へ出る道</b>もあった。
 * </p>
 */
public final class UploadFile {

	/** 項目名（フォームの name） */
	private final String name;

	/** 送られてきたファイル名 */
	private final String fileName;

	/** Content-Type */
	private final String contentType;

	/** 大きさ（byte） */
	private final long fileSize;

	/** 中身を落とした一時ファイル */
	private final File file;

	/** 送られてきたファイル名（フォルダを含む。D-266） */
	private final String relativePath;

	/**
	 * 作る
	 *
	 * @param name			項目名（フォームの name）
	 * @param fileName		送られてきたファイル名
	 * @param contentType	Content-Type
	 * @param fileSize		大きさ（byte）
	 * @param file			中身を落とした一時ファイル
	 */
	public UploadFile (String name, String fileName, String contentType, long fileSize, File file) {

		this(name, fileName, contentType, fileSize, file, fileName);

	}

	/**
	 * 作る（フォルダを含むファイル名つき。D-266）
	 *
	 * @param name			項目名（フォームの name）
	 * @param fileName		送られてきたファイル名（最後の名前だけ）
	 * @param contentType	Content-Type
	 * @param fileSize		大きさ（byte）
	 * @param file			中身を落とした一時ファイル
	 * @param relativePath	送られてきたファイル名（フォルダを含む。区切りは /）
	 */
	public UploadFile (String name, String fileName, String contentType, long fileSize, File file, String relativePath) {

		this.name = name;
		this.fileName = fileName;
		this.contentType = contentType;
		this.fileSize = fileSize;
		this.file = file;
		this.relativePath = relativePath == null ? fileName : relativePath;

	}

	/**
	 * 項目名（フォームの name）
	 *
	 * @return	項目名
	 */
	public String name () {

		return name;

	}

	/**
	 * 送られてきたファイル名
	 *
	 * <p>
	 * <b>そのまま保存先の名前に使わないこと。</b>相手が付けた文字列なので、
	 * {@code ../} も、長すぎる名前も、OS で使えない文字も入りうる。
	 * </p>
	 *
	 * @return	ファイル名
	 */
	public String fileName () {

		return fileName;

	}

	/**
	 * 送られてきたファイル名（フォルダを含む。D-266）
	 *
	 * <p>
	 * フォルダごとアップロードしたとき（{@code <input type="file" webkitdirectory>}）、ブラウザは
	 * {@code 写真/2026/a.png} のようにフォルダを含めて送る。{@link #fileName()} は最後の名前（{@code a.png}）だけ、
	 * こちらは送られてきたとおり（区切りは {@code /} にそろえる）。フォルダを含まなければ {@link #fileName()} と同じ。
	 * </p>
	 *
	 * <p>
	 * {@code ..} や {@code .}・空の段・頭の {@code /} は jimble が断っている（400）。それでも
	 * <b>そのまま保存先のパスに使わないこと</b>（長すぎる名前や、OS で使えない文字は入りうる）。
	 * </p>
	 *
	 * @return	フォルダを含むファイル名
	 */
	public String relativePath () {

		return relativePath;

	}

	/**
	 * Content-Type
	 *
	 * <p><b>相手の自己申告である。</b>中身がそうだという保証は無い。</p>
	 *
	 * @return	Content-Type
	 */
	public String contentType () {

		return contentType;

	}

	/**
	 * 大きさ
	 *
	 * @return	byte
	 */
	public long fileSize () {

		return fileSize;

	}

	/**
	 * 中身を落とした一時ファイル
	 *
	 * <p><b>リクエストが終わると消える。</b></p>
	 *
	 * @return	一時ファイル
	 */
	public File file () {

		return file;

	}

	@Override
	public String toString () {

		return "UploadFile(" + name + ": " + fileName + " " + fileSize + "byte)";

	}

}
