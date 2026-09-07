package io.jimble.web.validation;

import io.jimble.util.data.Data;
import io.jimble.web.validation.error.ValidationErrorType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * エラーメッセージ（要件 F-V-03）
 *
 * <p>
 * {@link ValidationRules#validate} が返すのは<b>種別と設定値だけ</b>で、
 * 人が読む文言は入っていない。移送元もそうだった（画面側でそれぞれ組み立てていた）。
 * 要件は「項目名・メッセージの一覧」なので、ここで文言に変える。
 * </p>
 *
 * <pre>
 * Data errors = rules.validate(db, request);
 * Data messages = ValidationMessages.toMessages(errors);
 * // { "name": ["入力してください"], "age": ["1 以上 120 以下の整数で入力してください"] }
 * </pre>
 *
 * <p>
 * 文言を変えたいときは {@link #put(ValidationErrorType, BiFunction)} で差し替える。
 * </p>
 */
public final class ValidationMessages {

	/** エラー情報のキー：種別 */
	public static final String KEY_TYPE = "validation_type";

	/** エラー情報のキー：設定値 */
	public static final String KEY_SETTING = "validation_setting";

	/** エラー情報のキー：入力値 */
	public static final String KEY_INPUT = "input";

	/* 種別 → 文言を作る関数（種別, 設定値） */
	private static final Map<ValidationErrorType, BiFunction<ValidationErrorType, Data, String>> MESSAGES =
		new LinkedHashMap<>();

	static {
		reset();
	}

	private ValidationMessages () {}

	/**
	 * 文言を差し替える
	 *
	 * @param type		種別
	 * @param message	文言を作る関数
	 */
	public static void put (ValidationErrorType type, BiFunction<ValidationErrorType, Data, String> message) {

		MESSAGES.put(type, message);

	}

	/**
	 * 既定に戻す
	 */
	public static void reset () {

		MESSAGES.clear();

		MESSAGES.put(ValidationErrorType.Empty, (type, s) -> "入力してください");
		MESSAGES.put(ValidationErrorType.Boolean, (type, s) -> "true か false で入力してください");
		MESSAGES.put(ValidationErrorType.Email, (type, s) -> "メールアドレスの形式で入力してください");
		MESSAGES.put(ValidationErrorType.Url, (type, s) -> "URL の形式で入力してください");
		MESSAGES.put(ValidationErrorType.Domain, (type, s) -> "ドメインの形式で入力してください");
		MESSAGES.put(ValidationErrorType.Date, (type, s) -> date(s));
		MESSAGES.put(ValidationErrorType.Regex, (type, s) -> "形式が正しくありません");
		MESSAGES.put(ValidationErrorType.Enum, (type, s) -> "指定できない値です");
		MESSAGES.put(ValidationErrorType.CharacterType, (type, s) -> "使用できない文字が含まれています");
		MESSAGES.put(ValidationErrorType.Integer, (type, s) -> range(s, "整数"));
		MESSAGES.put(ValidationErrorType.Number, (type, s) -> range(s, "数値"));
		MESSAGES.put(ValidationErrorType.TextLength, (type, s) -> length(s, "文字"));
		MESSAGES.put(ValidationErrorType.TextByteLength, (type, s) -> length(s, "バイト"));
		MESSAGES.put(ValidationErrorType.Custom, (type, s) -> "入力内容を確認してください");
		MESSAGES.put(ValidationErrorType.Error, (type, s) -> "検証できませんでした");
		MESSAGES.put(ValidationErrorType.Unknown, (type, s) -> "入力内容を確認してください");

	}

	/**
	 * エラー情報を文言に変える
	 *
	 * @param errors	{@link ValidationRules#validate} の戻り
	 * @return	項目名 → 文言の一覧
	 */
	public static Data toMessages (Data errors) {

		Data messages = new Data();

		if (errors == null) {
			return messages;
		}

		for (String key : errors.keySet()) {

			Object value = errors.get(key);
			if (!(value instanceof Data error)) {
				continue;
			}

			if (!error.containsKey(KEY_TYPE)) {
				// テーブル名でネストしている（Column 版で入れた場合）
				for (String innerKey : error.keySet()) {
					if (error.get(innerKey) instanceof Data inner && inner.containsKey(KEY_TYPE)) {
						add(messages, innerKey, message(inner));
					}
				}
				continue;
			}

			add(messages, key, message(error));

		}

		return messages;

	}

	/**
	 * 1件分の文言
	 *
	 * @param error	エラー情報
	 * @return	文言
	 */
	public static String message (Data error) {

		ValidationErrorType type = type(error);
		Data settings = error.getDataOptional(KEY_SETTING);

		BiFunction<ValidationErrorType, Data, String> message = MESSAGES.get(type);

		return message == null ? "入力内容を確認してください" : message.apply(type, settings);

	}

	// region 文言の組み立て

	/**
	 * 範囲（整数・数値）
	 *
	 * @param settings	設定値
	 * @param unit		単位の言葉
	 * @return	文言
	 */
	private static String range (Data settings, String unit) {

		boolean hasMin = settings.containsKey("min");
		boolean hasMax = settings.containsKey("max");

		if (hasMin && hasMax) {
			return "%s 以上 %s 以下の%sで入力してください"
				.formatted(settings.getString("min"), settings.getString("max"), unit);
		}
		if (hasMin) {
			return "%s 以上の%sで入力してください".formatted(settings.getString("min"), unit);
		}
		if (hasMax) {
			return "%s 以下の%sで入力してください".formatted(settings.getString("max"), unit);
		}

		return "%sで入力してください".formatted(unit);

	}

	/**
	 * 長さ（文字数・バイト数）
	 *
	 * @param settings	設定値
	 * @param unit		単位の言葉
	 * @return	文言
	 */
	private static String length (Data settings, String unit) {

		boolean hasMin = settings.containsKey("min");
		boolean hasMax = settings.containsKey("max");

		if (hasMin && hasMax) {
			return "%s%s以上 %s%s以下で入力してください"
				.formatted(settings.getString("min"), unit, settings.getString("max"), unit);
		}
		if (hasMin) {
			return "%s%s以上で入力してください".formatted(settings.getString("min"), unit);
		}
		if (hasMax) {
			return "%s%s以下で入力してください".formatted(settings.getString("max"), unit);
		}

		return "長さが正しくありません";

	}

	/**
	 * 日時
	 *
	 * @param settings	設定値
	 * @return	文言
	 */
	private static String date (Data settings) {

		String format = settings.getStringOptional("format");

		return format.isEmpty()
			? "日時の形式で入力してください"
			: "%s の形式で入力してください".formatted(format);

	}

	// endregion

	/**
	 * 種別を取り出す
	 *
	 * @param error	エラー情報
	 * @return	種別
	 */
	private static ValidationErrorType type (Data error) {

		Object value = error.get(KEY_TYPE);

		if (value instanceof ValidationErrorType type) {
			return type;
		}

		try {
			return ValidationErrorType.valueOf(String.valueOf(value));
		} catch (Exception ex) {
			return ValidationErrorType.Unknown;
		}

	}

	/**
	 * 文言を足す
	 *
	 * @param messages	文言の一覧
	 * @param key		項目名
	 * @param message	文言
	 */
	private static void add (Data messages, String key, String message) {

		Object current = messages.get(key);

		if (current instanceof List<?> list) {
			@SuppressWarnings("unchecked")
			List<String> messageList = (List<String>) list;
			messageList.add(message);
			return;
		}

		List<String> messageList = new ArrayList<>();
		messageList.add(message);
		messages.put(key, messageList);

	}

}
