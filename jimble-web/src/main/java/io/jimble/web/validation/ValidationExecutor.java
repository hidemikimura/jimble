package io.jimble.web.validation;

import io.jimble.core.executor.AbstractExecutor;
import io.jimble.util.data.Data;
import io.jimble.web.context.WebContext;

import java.util.ArrayList;
import java.util.List;

/**
 * ルート単位のバリデーション（要件 F-V-04）
 *
 * <p>
 * Executor のキューに積む。<b>失敗したら後続の UseCase をキャンセルする。</b>
 * </p>
 *
 * <pre>
 * post("/items", context -&gt; {
 *     context.addExecutor(new SaveItemValidation());
 *     context.addExecutor(new SaveItemUseCase());
 * });
 * </pre>
 *
 * <pre>
 * public class SaveItemValidation extends ValidationExecutor {
 *
 *     &#64;Override
 *     protected void validate (WebContext context) {
 *         Data request = context.request().bodyAll();
 *         if (request.getStringOptional("title").isEmpty()) {
 *             addError("title", "入力してください");
 *         }
 *     }
 * }
 * </pre>
 *
 * <h2>移送元から変えたところ</h2>
 * <ol>
 *   <li><b>{@code appContext} をフィールドに持たない。</b>移送元は {@code execute()} で
 *       自分のフィールドに代入してから {@code addError()} がそれを参照していた。
 *       <b>Executor を使い回すと前のリクエストのコンテキストに書き込む。</b>
 *       検証中のコンテキストを引数で持ち回る形にした</li>
 *   <li>エラーをいったん手元に集め、<b>キャンセル処理の中でレスポンスに載せる。</b>
 *       途中で例外が出たときに中途半端に書き込まれた状態を残さない</li>
 * </ol>
 */
public abstract class ValidationExecutor extends AbstractExecutor<WebContext> {

	/** 失敗時のステータスコード */
	public static final int STATUS_CODE = 422;

	/** レスポンスに載せるキー */
	public static final String RESPONSE_KEY = "validation";

	/* このリクエストで集めたエラー */
	private final Data errors = new Data();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final void execute (WebContext context) throws Exception {

		errors.clear();

		validate(context);

		if (!errors.isEmpty()) {
			cancel();
		}

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * 入力値をレスポンスに戻し（画面の再表示用）、エラーを載せて 422 にする。
	 * </p>
	 */
	@Override
	public void onCancel (WebContext context) throws Exception {

		// 返す入力値から秘密を落とす（D-241）
		context.response().putForm(echoable(context.request().bodyAll()));
		context.response().json(RESPONSE_KEY, errors);
		context.response().code(STATUS_CODE);

	}

	/**
	 * 検証する
	 *
	 * @param context	コンテキスト
	 */
	protected abstract void validate (WebContext context);

	// region エラーの登録

	/**
	 * エラーを足す
	 *
	 * @param name		項目名
	 * @param message	メッセージ
	 */
	protected void addError (String name, String message) {

		Object current = errors.get(name);

		if (current instanceof List<?> list) {
			@SuppressWarnings("unchecked")
			List<String> messages = (List<String>) list;
			messages.add(message);
			return;
		}

		List<String> messages = new ArrayList<>();
		messages.add(message);
		errors.put(name, messages);

	}

	/**
	 * {@link ValidationRules} の結果をまとめて足す
	 *
	 * @param validationErrors	{@link ValidationRules#errors} の戻り
	 */
	protected void addErrors (Data validationErrors) {

		Data messages = ValidationMessages.toMessages(validationErrors);

		for (String name : messages.keySet()) {
			if (messages.get(name) instanceof List<?> list) {
				for (Object message : list) {
					addError(name, String.valueOf(message));
				}
			}
		}

	}

	/**
	 * 集めたエラー
	 *
	 * @return	項目名 → メッセージの一覧
	 */
	public Data errors () {

		return errors;

	}

	/**
	 * エラーがあるか
	 *
	 * @return	ある場合 = true
	 */
	public boolean hasError () {

		return !errors.isEmpty();

	}

	// endregion

	/**
	 * 422 で返す入力値から、秘密を落とす（D-241）
	 *
	 * <p>
	 * <b>かつては送られた本文をそのまま返していた。</b>ログインや登録の検証に落ちると、
	 * 入れたパスワードが応答に載り、ブラウザの開発ツール・HAR・画面側のエラー送信・APM に残った。
	 * 名前に password / secret / token などを含む項目を落とす（入れ子も見る）。
	 * </p>
	 *
	 * <p>
	 * <b>リストの中も見る</b>（D-272）。2.5.1 までは {@code Data} の中だけを見ていたので、
	 * {@code users[0][password]=...} や JSON の {@code {"users":[{"password":"..."}]}} はそのまま返っていた。
	 * {@value #ECHO_MAX_DEPTH} 段より深いところは写さない（深い入れ子で {@code StackOverflowError} にしない）。
	 * </p>
	 *
	 * @param input	入力値
	 * @return	秘密を落とした写し
	 */
	public static io.jimble.util.data.Data echoable (io.jimble.util.data.Data input) {

		return echoable(input, 0);

	}

	/** 422 で写す入れ子の深さの上限 */
	static final int ECHO_MAX_DEPTH = 64;

	private static io.jimble.util.data.Data echoable (io.jimble.util.data.Data input, int depth) {

		io.jimble.util.data.Data copy = new io.jimble.util.data.Data();

		if (input == null || depth >= ECHO_MAX_DEPTH) {
			return copy;
		}

		for (java.util.Map.Entry<String, Object> entry : input.entrySet()) {

			if (isSecret(entry.getKey())) {
				continue;
			}

			copy.put(entry.getKey(), echoableValue(entry.getValue(), depth + 1));

		}

		return copy;

	}

	private static Object echoableValue (Object value, int depth) {

		if (value instanceof io.jimble.util.data.Data nested) {
			return echoable(nested, depth);
		}

		if (value instanceof java.util.List<?> list) {

			if (depth >= ECHO_MAX_DEPTH) {
				return java.util.List.of();
			}

			java.util.List<Object> copy = new java.util.ArrayList<>(list.size());

			for (Object item : list) {
				copy.add(echoableValue(item, depth + 1));
			}

			return copy;

		}

		if (value instanceof java.util.Map<?, ?> map) {
			// Data でない Map（アプリが入れたもの）も同じに扱う
			io.jimble.util.data.Data data = new io.jimble.util.data.Data();
			map.forEach((key, item) -> data.put(String.valueOf(key), item));
			return echoable(data, depth);
		}

		return value;

	}

	/**
	 * 返してはいけない項目の名前か
	 */
	static boolean isSecret (String name) {

		if (name == null) {
			return false;
		}

		String lower = name.toLowerCase(java.util.Locale.ROOT);

		return lower.contains("password") || lower.contains("passwd") || lower.contains("secret")
			|| lower.contains("token") || lower.contains("card_number") || lower.contains("cardnumber")
			|| lower.equals("cvc") || lower.equals("cvv") || lower.equals("pin") || lower.equals("otp") || lower.equals("code_verifier");

	}

}
