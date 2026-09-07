package io.jimble.core.context;

/**
 * テスト用コンテキスト
 */
class TestContext extends Context<TestContext> {

	/* クローズ回数 */
	private int closeCount = 0;

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected TestContext self () {

		return this;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void doClose () {

		closeCount++;

	}

	/**
	 * クローズ回数
	 *
	 * @return	クローズ回数
	 */
	int closeCount () {

		return closeCount;

	}

}
