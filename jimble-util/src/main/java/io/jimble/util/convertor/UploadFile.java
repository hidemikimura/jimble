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

		this.name = name;
		this.fileName = fileName;
		this.contentType = contentType;
		this.fileSize = fileSize;
		this.file = file;

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
