package io.jimble.db.data;

import io.jimble.util.data.Data;
import io.jimble.db.sql.definition.column.Column;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * ResultSetイテレータクラス.
 */
class ResultSetIterator implements Iterator<Data> {

	// region SQL結果セット

	/* SQL結果セット. */
	private ResultSet resultSet;

	/**
	 * SQL結果セットを取得する.
	 *
	 * @return	SQL結果セット
	 */
	public ResultSet getResultSet() {
		return resultSet;
	}

	// endregion

	private boolean isEndNext = false;
	private boolean isHasNext = false;
	private Data currentData;

	// region ResultSetを読み込む

	/**
	 * ResultSetを読み込む.
	 *
	 * @param resultSet	SQL結果セット
	 */
	public void load (ResultSet resultSet) {

		this.resultSet = resultSet;

	}

	// endregion


	// region hasNext

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasNext() {

		// 次のデータを取得する
		getNextData();

		// データの存在判定を返却する
		return isHasNext;

	}

	// endregion

	// region next

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Data next() {

		// 次のデータを取得する
		getNextData();

		// 次のデータへ移動していない状態にする
		isEndNext = false;

		// データを返却する
		return currentData;

	}

	// endregion

	// region remove

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void remove() {

		// TODO サポートしていません

	}

	// endregion

	// region 次のデータを取得する

	private boolean isKeyGot = false;

	/* キー情報一覧 */
	private final List<KeyInfo> keyInfoList = new ArrayList<>();

	/* 列型種別一覧(0=その他、1=JSON) */
	private final List<Integer> columnTypeList = new ArrayList<>();

	/**
	 * 次のデータを取得する.
	 */
	private void getNextData () {

		if (!isEndNext) {
			// 次のデータに移動していない場合、次のデータの存在を判定しデータを取得する

			// 移動済に設定する
			isEndNext = true;

			// 次のデータに移動する
			if (resultSet == null) {
				isHasNext = false;
			} else {
				try {
					isHasNext = resultSet.next();
				} catch (Exception ex) {
					isHasNext = false;
				}
			}


			if (isHasNext) {

				// 移動出来た場合、データを変換して保持しておく
				if (!isKeyGot) {
					try {
						ResultSetMetaData metaData = resultSet.getMetaData();
						int colCount = metaData.getColumnCount();
						for (int i = 0; i < colCount; i++) {
							String key = metaData.getColumnLabel(i + 1);

							String typeName = metaData.getColumnTypeName(i + 1);
							if ("JSON".equalsIgnoreCase(typeName)) {
								columnTypeList.add(-999);
							} else {
								columnTypeList.add(metaData.getColumnType(i + 1));
							}

							{
								KeyInfo keyInfo = new KeyInfo();
								keyInfo.key = key;
								int delimiterIndex = key.indexOf(Column.SPLITTER);
								if (delimiterIndex != -1) {
									keyInfo.isNest = true;
									keyInfo.parentKey = key.substring(0, delimiterIndex);
									keyInfo.childKey = key.substring(delimiterIndex + 2);
								}
								keyInfoList.add(keyInfo);
							}
						}
						isKeyGot = true;
					} catch (Exception ex) {}
				}

				// データを変換して保持する
				currentData = ResultSetConverter.convert(resultSet, keyInfoList, columnTypeList);

			}

		}

	}

	// endregion

	public static class KeyInfo {

		String key;

		boolean isNest;

		String parentKey;

		String childKey;

	}

}
