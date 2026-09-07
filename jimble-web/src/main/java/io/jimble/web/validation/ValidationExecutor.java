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

		context.response().putForm(context.request().bodyAll());
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
	 * @param validationErrors	{@link ValidationRules#validate} の戻り
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

}
