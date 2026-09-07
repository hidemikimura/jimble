package io.jimble.web.validation.error;

/**
 * validation error type
 */
public enum ValidationErrorType {

	Unknown

	/* カスタム */
	, Custom

	/* エラー */
	, Error

	/* 空文字 */
	, Empty


	/* 文字列長 */
	, TextLength

	/* 文字列Byte長 */
	, TextByteLength


	/* 真偽値 */
	, Boolean


	/* 整数 */
	, Integer


	/* 数値 */
	, Number


	/* メールアドレス */
	, Email

	/* URL */
	, Url


	/* ドメイン */
	, Domain


	/* 文字種別 */
	, CharacterType


	/* 日時 */
	, Date


	/* 正規表現 */
	, Regex


	/* enum */
	, Enum


	;

}
