package io.jimble.web.validation.validator;

import io.jimble.util.data.Data;
import io.jimble.db.DB;
import io.jimble.util.exception.CodeException;
import io.jimble.web.validation.error.ValidationErrorType;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * 文字種別
 */
public class CharacterTypeValidator implements IValidator {

	/* 設定 */
	private final Data settings = new Data();

	/* lock */
	private final ReentrantLock lock = new ReentrantLock();

	/* Pattern */
	private Pattern pattern = null;

	/* Compiled */
	private boolean compiled = false;

	/**
	 * 文字種別
	 *
	 * @param types	文字種別
	 * @return	CharacterTypeValidator
	 */
	public CharacterTypeValidator characterType (CharacterType...types) {

		if (types != null && types.length > 0) {
			List<CharacterType> list = this.settings.getObjectListOptional("types", CharacterType.class);
			list.addAll(Arrays.asList(types));
		}

		return this;

	}

	/**
	 * 指定文字
	 *
	 * @param characters	指定文字
	 * @return	CharacterTypeValidator
	 */
	public CharacterTypeValidator character (Character...characters) {

		if (characters != null && characters.length > 0) {
			List<Character> list = this.settings.getObjectListOptional("symbols", Character.class);
			list.addAll(Arrays.asList(characters));
		}

		return this;

	}

	/**
	 * 正規表現をCompileする
	 */
	private void compile () {

		if (compiled) {
			return;
		}

		try {
			lock.lock();
			if (compiled) {
				return;
			}

			StringBuilder sb = new StringBuilder();
			sb.append("^[");
			if (this.settings.containsKey("types")) {
				List<CharacterType> list = this.settings.getObjectList("types", CharacterType.class);
				for (CharacterType t : list) {
					sb.append(t.regex());
				}
			}
			if (this.settings.containsKey("symbols")) {
				List<Character> list = this.settings.getObjectList("symbols", Character.class);
				for (Character c : list) {
					sb.append(Pattern.quote(c.toString()));
				}
			}
			sb.append("]+$");

			this.pattern = Pattern.compile(sb.toString());

			compiled = true;
		} finally {
			lock.unlock();
		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean validate(DB db, Data req, boolean isInsertRequest, Object value) throws CodeException {

		if (value == null) {
			return true;
		}

		String str = String.valueOf(value);
		if (str.isEmpty()) {
			return true;
		}

		compile();

		return pattern.matcher(str).find();

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ValidationErrorType errorType() {

		return ValidationErrorType.CharacterType;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Data settings() {

		return this.settings;

	}

	/**
	 * 文字種別
	 */
	public enum CharacterType {

		/* 半角数字 */
		NumberHan("0-9")

		/* 全角数字 */
		, NumberZen("０-９")

		/* 半角英字 */
		, AlphabetHan("a-zA-Z")

		/* 半角英字小文字 */
		, AlphabetHanLower("a-z")

		/* 半角英字大文字 */
		, AlphabetHanUpper("A-Z")

		/* 全角英字 */
		, AlphabetZen("ａ-ｚＡ-Ｚ")

		/* 全角英字小文字 */
		, AlphabetZenLower("ａ-ｚ")

		/* 全角英字大文字 */
		, AlphabetZenUpper("Ａ-Ｚ")

		/* ひらがな */
		, Hiragana("ぁ-んー")

		/* 半角カタカナ */
		, KatakanaHan("ｦ-ﾟ")

		/* 全角カタカナ */
		, KatakanaZen("ァ-ンヴー")

		/* 半角スペース */
		, SpaceHan(" ")

		/* 全角スペース */
		, SpaceZen("　")

		/* 記号（半角空白!”#$%&’()*+-.,/:;<=>?@[\]^_`{|}~） */
		, Symbol(" -/:-@\\[-\\`\\{-\\~")

		;

		/* 正規表現 */
		final String regex;

		/**
		 * コンストラクタ
		 *
		 * @param regex	正規表現
		 */
		CharacterType (String regex) {
			this.regex = regex;
		}

		/**
		 * 正規表現
		 *
		 * @return	正規表現
		 */
		String regex () {
			return this.regex;
		}

	}

}
