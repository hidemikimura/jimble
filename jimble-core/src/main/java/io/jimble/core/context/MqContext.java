package io.jimble.core.context;

import java.util.Objects;

/**
 * MQメッセージ処理のコンテキスト
 *
 * <p>
 * メッセージ1件の処理ごとに生成する（要件 F-M-01）。
 * バッチと同じく、HTTP の偽物を作らずに成立する。
 * </p>
 */
public final class MqContext extends Context<MqContext> {

	/* キュー名 */
	private final String queueName;

	/* メッセージID */
	private final long messageId;

	/* 試行回数（1始まり） */
	private final int attempt;

	/**
	 * コンストラクタ
	 *
	 * @param queueName	キュー名
	 * @param messageId	メッセージID
	 * @param attempt	試行回数（1始まり）
	 */
	public MqContext (String queueName, long messageId, int attempt) {

		this.queueName = Objects.requireNonNull(queueName, "queueName");
		this.messageId = messageId;
		this.attempt = attempt;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected MqContext self () {

		return this;

	}

	/**
	 * キュー名
	 *
	 * @return	キュー名
	 */
	public String queueName () {

		return queueName;

	}

	/**
	 * メッセージID
	 *
	 * @return	メッセージID
	 */
	public long messageId () {

		return messageId;

	}

	/**
	 * 試行回数
	 *
	 * <p>
	 * 同じメッセージが二重に処理されうる前提で書くこと（要件 F-M-05）。
	 * </p>
	 *
	 * @return	試行回数（1始まり）
	 */
	public int attempt () {

		return attempt;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString () {

		return "MqContext(%s#%d, attempt=%d, %s)".formatted(queueName, messageId, attempt, executionId());

	}

}
