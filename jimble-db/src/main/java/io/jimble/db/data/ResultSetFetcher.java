package io.jimble.db.data;

import io.jimble.util.data.Data;
import io.jimble.util.log.Log;

import java.io.Closeable;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Iterator;

/**
 * ResultSet操作クラス
 */
public class ResultSetFetcher implements Iterable<Data>, Closeable, AutoCloseable {

	/* SQL結果セットイテレータ */
	private ResultSetIterator iterator;

	/* SQLステートメント */
	private Statement statement;

	/* エラー判定 */
	public boolean isError = false;


	// region コンストラクタ

	/**
	 * コンストラクタ
	 */
	public ResultSetFetcher() {

		this.iterator = new ResultSetIterator();

	}

	// endregion


	// region ResultSetを読み込む

	/**
	 * ResultSetを読み込む
	 *
	 * @param statement	SQLステートメント
	 * @param resultSet	SQL結果セット
	 */
	public void load (Statement statement, ResultSet resultSet) {

		this.statement = statement;
		this.iterator.load(resultSet);

	}

	/**
	 * 方言を渡す（要件 F-D-30）
	 *
	 * <p>列の型を見分けるのに、接続先の製品が要る。</p>
	 *
	 * @param dialect	方言
	 */
	public void dialect (io.jimble.db.dialect.Dialect dialect) {

		this.iterator.dialect(dialect);

	}

	// endregion

	// region 行を移動する

	/**
	 * 行を移動する
	 *
	 * @param number	行番号(1から)
	 * @return	移動できた場合 = true
	 */
	public boolean moveRow (int number) throws Exception {

		return this.iterator.getResultSet().absolute(number);

	}

	// endregion

	// region 行番号を取得する

	/**
	 * 行番号を取得する
	 *
	 * @return	行番号
	 */
	public int getRow () throws Exception {

		return this.iterator.getResultSet().getRow();

	}

	// endregion

	// region 最後の行に移動する

	/**
	 * 最後の行に移動する
	 *
	 * @return	移動できた場合 = true
	 */
	public boolean moveLast () throws Exception {

		return this.iterator.getResultSet().last();

	}

	// endregion

	// region close

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close() throws IOException {

		Exception exception1 = null;
		Exception exception2 = null;

		if (iterator != null && iterator.getResultSet() != null) {

			try {
				iterator.getResultSet().close();
				iterator = null;
			} catch (Exception ex) {
				Log.info(ex.getMessage(), ex);
				exception1 = ex;
			}

		}

		if (statement != null) {

			try {
				statement.close();
			} catch (Exception ex) {
				Log.info(ex.getMessage(), ex);
				exception2 = ex;
			}

		}

		if (exception1 != null || exception2 != null) {

			IOException exception = new IOException();
			if (exception1 != null) {
				exception.addSuppressed(exception1);
			}
			if (exception2 != null) {
				exception.addSuppressed(exception2);
			}

			throw exception;

		}

	}

	// endregion

	// region iterator

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Iterator<Data> iterator() {

		return iterator;

	}

	// endregion

}
