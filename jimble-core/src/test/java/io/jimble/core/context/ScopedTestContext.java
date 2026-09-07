package io.jimble.core.context;

/**
 * ScopedValue の拡張点を確認するためのテスト用コンテキスト
 *
 * <p>
 * 本番では リクエストスコープキャッシュ / ログ / 操作情報 / stickyコネクション が
 * 同じやり方で乗る（要件 F-C-11）。
 * </p>
 */
class ScopedTestContext extends Context<ScopedTestContext> {

	/* 追加のScopedValue */
	static final ScopedValue<String> EXTRA = ScopedValue.newInstance();

	/* 追加値 */
	private final String extra;

	/**
	 * コンストラクタ
	 *
	 * @param extra	追加値
	 */
	ScopedTestContext (String extra) {

		this.extra = extra;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected ScopedTestContext self () {

		return this;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected ScopedValue.Carrier scopedValues () {

		return super.scopedValues().where(EXTRA, extra);

	}

}
