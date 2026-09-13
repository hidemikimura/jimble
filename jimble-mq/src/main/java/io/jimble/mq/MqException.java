package io.jimble.mq;

/**
 * キューの操作が失敗した（D-173）
 *
 * <p>
 * <b>積めなかったことは、戻り値では伝わらなかった。</b>
 * {@code put} は失敗を {@code -1} で返していたが、
 * <b>戻り値を見ている呼び出しは1つも無かった</b>——
 * 「注文は入ったが、メールのキューだけ無い」という、
 * このクラスが防ぐと言っている事故そのものが起きる。
 * </p>
 *
 * <p>
 * <b>{@code RuntimeException} である。</b>{@code put} の署名を変えると
 * 既存の呼び出しが全部コンパイルエラーになるし、
 * <b>捕まえて握り潰す場所を増やしたいわけでもない</b>——
 * トランザクションの中で投げれば、そのまま巻き戻る。
 * </p>
 */
public class MqException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/**
	 * コンストラクタ
	 *
	 * @param message	メッセージ
	 */
	public MqException (String message) {

		super(message);

	}

	/**
	 * コンストラクタ
	 *
	 * @param message	メッセージ
	 * @param cause		原因
	 */
	public MqException (String message, Throwable cause) {

		super(message, cause);

	}

}
