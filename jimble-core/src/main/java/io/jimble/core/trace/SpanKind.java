package io.jimble.core.trace;

/**
 * スパンの種類
 *
 * <p>
 * トレースを見る道具が、待っている側と待たせている側を見分けるのに使う。
 * 名前は OpenTelemetry の決まりに合わせてある。
 * </p>
 */
public enum SpanKind {

	/** 頼まれた側（HTTP のリクエストを受けた） */
	server,

	/** 頼んだ側（DB や外部 API を呼んだ） */
	client,

	/** 積んだ側（MQ にメッセージを入れた） */
	producer,

	/** 取り出した側（MQ のメッセージを処理した） */
	consumer,

	/** そのプロセスの中だけの区切り */
	internal,

}
