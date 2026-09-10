package approval.forms;

import io.jimble.util.data.Data;
import io.jimble.web.context.WebContext;
import io.jimble.web.validation.ValidationExecutor;
import io.jimble.web.validation.ValidationMessages;

import java.util.List;

/**
 * 申請の入力を確かめる（要件 F-V-04）
 *
 * <p>
 * <b>これに落ちると、後ろの {@link SaveUseCase} は走らない。</b>
 * 保存の処理の中に「まず入力を確かめて…」と書かなくてよくなるのが、この形の値打ちである。
 * </p>
 *
 * <h2>落ちたときに返るもの</h2>
 * <pre>
 * 422
 * {
 *   "validation": { "amount": ["1 以上 1000000 以下の整数で入力してください"] },
 *   "kind": "travel",
 *   "amount": "0"
 * }
 * </pre>
 * <p>
 * <b>送られてきた値も一緒に返る</b>ので、画面を組み直せる（要件 F-W-09）。
 * </p>
 *
 * <h2>ルートへの登録は {@code ::new} で</h2>
 * <p>
 * <b>エラーはこのインスタンスに溜まる。</b>使い回すと前のリクエストのエラーが混ざるので、
 * {@code post("/requests", SaveValidation::new, SaveUseCase::new)} と書いて
 * <b>リクエストごとに作らせる</b>。
 * </p>
 */
public class SaveValidation extends ValidationExecutor {

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void validate (WebContext context) {

		Data request = context.request().bodyAll();

		// 申請そのもの
		addErrors(RequestRules.request().validate(null, request));

		validateItems(request);

	}

	/**
	 * 明細を確かめる
	 *
	 * <p>
	 * <b>行ごとに見て、何行目かを添えて返す。</b>
	 * 「品目を入れてください」だけ返しても、<b>5行あったらどれか分からない</b>。
	 * </p>
	 *
	 * @param request	リクエスト
	 */
	private void validateItems (Data request) {

		List<Data> items = request.getDataList("items");

		if (items == null || items.isEmpty()) {

			// 提出するなら明細が1行は要る。下書きなら空でよい
			if (request.getBoolean(RequestRules.KEY_SUBMIT)) {
				addError("items", "明細を1行以上入れてください");
			}

			return;

		}

		/*
		 * <b>エラーのある行だけ返る。</b>{@code index} は 1 始まりである
		 * （0 始まりだと、画面に「0行目」と出て読み手が戸惑う）。
		 *
		 * 返ってくるのは<b>文言ではなく、どの決まりに落ちたかの記録</b>なので、
		 * {@link ValidationMessages#toMessages} で文言にしてから積む。
		 * ついでにテーブル名のネストもここで剥がれる。
		 */
		for (Data error : RequestRules.item().validate(null, items)) {

			int index = error.getInt("index");

			Data messages = ValidationMessages.toMessages(error);

			for (String name : messages.keySet()) {

				/*
				 * <b>項目名を、送られてきたときの形に戻して返す。</b>
				 * {@code name} とだけ返しても、画面はどの入力欄を赤くすればいいか分からない。
				 * {@code items[0][name]} なら、そのまま name 属性と突き合わせられる
				 */
				String field = "items[%d][%s]".formatted(index - 1, name);

				for (Object message : messages.getObjectListOptional(name, Object.class)) {
					addError(field, String.valueOf(message));
				}

			}

		}

	}


}
