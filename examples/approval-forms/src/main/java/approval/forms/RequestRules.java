package approval.forms;

import db.approval_forms_example.table.request.Request;
import db.approval_forms_example.table.request_item.RequestItem;

import io.jimble.web.validation.ValidationRule;
import io.jimble.web.validation.ValidationRules;
/**
 * 申請の検証ルール（要件 F-V-01 / F-V-02 / F-V-08）
 *
 * <p>
 * <b>ルールを1か所にまとめて、下書きと提出で使い回す。</b>
 * これが F-V-08（組み合わせの再利用）の実物である。
 * </p>
 *
 * <h2>なぜ if を2本書かないのか</h2>
 * <p>
 * 「下書きなら任意、提出なら必須」を書く素朴なやり方は、
 * <b>保存の処理の中に {@code if (提出) { 金額チェック } }</b> と書くことである。
 * ところが下書きと提出は<b>別の口</b>なので、<b>そのうち片方だけ直る</b>。
 * ここでは同じ {@link ValidationRules} を両方から使い、
 * <b>「提出かどうか」だけを外から渡す</b>。
 * </p>
 *
 * <h2>insertRequired という名前について</h2>
 * <p>
 * 移送元の名前がそのまま残っているが、意味は
 * <b>「送られてこなくても検証する」</b>である。
 * 送られてこない＝空なので、{@code empty()} と組むと「必須」になる。
 * ここでは「登録か更新か」ではなく<b>「提出かどうか」</b>に読み替えて使っている。
 * </p>
 */
public final class RequestRules {

	/** 種別 */
	public enum Kind {
		/** 出張 */
		travel,
		/** 備品 */
		supply,
		/** 書籍 */
		book
	}

	/** 状態：下書き */
	public static final String STATUS_DRAFT = "draft";

	/** 状態：提出済み */
	public static final String STATUS_PENDING = "pending";

	/** リクエストに入っている「提出かどうか」の印 */
	public static final String KEY_SUBMIT = "submit";

	/** 金額の上限（円） */
	public static final long MAX_AMOUNT = 1_000_000;

	private RequestRules () {
	}

	/**
	 * 申請そのもののルール
	 *
	 * <p>
	 * <b>毎回作る。</b>{@link ValidationRules} 自体は状態を持たないので使い回せるが、
	 * <b>使い回すと「どこで足されたルールなのか」が追えなくなる</b>（原則1）。
	 * 1リクエストに1回作る程度の費用は問題にならない。
	 * </p>
	 *
	 * @return	ルール
	 */
	public static ValidationRules request () {

		return new ValidationRules()

			/*
			 * <b>必須は empty() を積む。</b>ほかのバリデータは
			 * <b>空を通す</b>（値が来ていないものを型で落とさないため）ので、
			 * integer() だけ書くと「空でも通る」になる。
			 */
			.put(Request.kind, new ValidationRule()
				.insertRequired().empty().enumType(Kind.class))

			.put(Request.amount, new ValidationRule()
				.insertRequired().empty().integer(1, MAX_AMOUNT))

			// 希望日は提出でも任意。ただし書くなら日付であること
			.put(Request.needed_on, new ValidationRule().date("yyyy-MM-dd"))

			.put(Request.note, new ValidationRule().textLengthMax(500))

			/*
			 * <b>「提出かどうか」をここで決める。</b>
			 * 下書き（submit が無い）なら insertRequired は効かず、
			 * 送られてこなかった項目は検証されない
			 */
			.insertRequestChecker(req -> req.getBoolean(KEY_SUBMIT));

	}

	/**
	 * 明細1行のルール
	 *
	 * <p>
	 * 明細は行ごとに検証する（{@link ValidationRules#validate(io.jimble.db.DB, java.util.List)}）。
	 * <b>エラーのある行だけ、1始まりの {@code index} 付きで返る。</b>
	 * </p>
	 *
	 * @return	ルール
	 */
	public static ValidationRules item () {

		return new ValidationRules()
			.put(RequestItem.name, new ValidationRule().empty().textLengthMax(100))
			.put(RequestItem.amount, new ValidationRule().empty().integer(1, MAX_AMOUNT));

	}

	/**
	 * 種別として読める文字列か
	 *
	 * @param value	値
	 * @return	読める場合 = true
	 */
	public static boolean isKind (String value) {

		for (Kind kind : Kind.values()) {
			if (kind.name().equals(value)) {
				return true;
			}
		}

		return false;

	}

}
