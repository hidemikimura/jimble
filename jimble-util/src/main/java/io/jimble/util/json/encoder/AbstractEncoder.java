package io.jimble.util.json.encoder;

/**
 * 基底JSONエンコーダクラス.
 * 
 * @author DN
 */
public abstract class AbstractEncoder implements IEncoder {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isError() {
		return error;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Exception getErrorException() {
		return exception;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void clearError() {
		this.error = false;
		this.exception = null;
	}

	/** エラー判定. */
	private boolean error = false;

	/** エラー. */
	private Exception exception;

	/**
	 * エラーを設定する.
	 * 
	 * @param e エラー
	 */
	protected void setError(Exception e) {
		exception = e;
		error = true;
	}

}
