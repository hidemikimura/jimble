package io.jimble.db.data;

import io.jimble.db.SqlExecuteException;
import io.jimble.util.exception.CodeException;

import io.jimble.util.data.Data;
import io.jimble.db.dialect.Dialect;
import io.jimble.db.dialect.Dialects;
import io.jimble.db.sql.definition.column.Column;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Types;
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

	/* 列の型を見分ける方言（要件 F-D-30） */
	private Dialect dialect = null;

	/**
	 * 方言を渡す
	 *
	 * <p>
	 * <b>接続先の製品を使う。</b>渡されなければ既定の方言を見るが、
	 * 製品の違うデータソースを混ぜていると
	 * <b>jsonb の列が Data に戻らない</b>といったことが黙って起きる。
	 * </p>
	 *
	 * @param dialect	方言
	 */
	public void dialect (Dialect dialect) {

		this.dialect = dialect;

	}

	/**
	 * 方言
	 *
	 * @return	方言
	 */
	private Dialect dialect () {

		return dialect != null ? dialect : Dialects.defaultDialect();

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

	/* 全部 null のテーブルを落とすか（結果を読み始めたときに1回だけ設定から読む） */
	private boolean removeAllNullTableData;

	/* 列の読み方（最初の行で決める。覚えたものがあれば使い回す。3.0.0） */
	private ResultColumns columns;

	/* 覚える単位のデータソース（null なら覚えない。3.0.0） */
	private String source;

	/* SQL（覚えるキー。3.0.0） */
	private String sql;

	/* ビルダーが書いた位置ごとの結果のキー（null なら列名で入れ子にする。3.0.0） */
	private List<String> labels;

	/**
	 * SQL・位置ごとの結果のキー・データソースを受け取る（読み始める前に。3.0.0）
	 *
	 * @param source	データソース（null なら覚えない）
	 * @param sql		SQL（null なら覚えない）
	 * @param labels	位置ごとの結果のキー（null なら列名で入れ子にする）
	 */
	void hint (String source, String sql, List<String> labels) {

		this.source = source;
		this.sql = sql;
		this.labels = labels;

	}

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
					/*
					 * 1.4 までは false にして「最後まで読んだ」扱いにしていた（要件 D-190）。
					 * 途中で接続が切れると、<b>結果が黙って途中までになっていた</b>。
					 */
					isHasNext = false;
					throw new SqlExecuteException("結果を読んでいる途中で失敗しました: " + ex.getMessage(),
						new CodeException("DB_999", ex.getMessage(), ex));
				}
			}


			if (isHasNext) {

				// 移動出来た場合、データを変換して保持しておく
				if (!isKeyGot) {
					try {
						columns = columns(resultSet.getMetaData());
						removeAllNullTableData = ResultSetConverter.removesAllNullTableData();
						isKeyGot = true;
					} catch (Exception ex) {
						// 1.4 までは握りつぶして、<b>キーの無い空の行</b>を返していた（要件 D-190）
						throw new SqlExecuteException("結果の列を読めませんでした: " + ex.getMessage(),
							new CodeException("DB_999", ex.getMessage(), ex));
					}
				}

				// データを変換して保持する
				currentData = columns.plan() != null
					? columns.plan().read(resultSet, dialect(), removeAllNullTableData)
					: ResultSetConverter.convert(resultSet, columns.keys(), columns.types(), dialect(), removeAllNullTableData);

			}

		}

	}

	// endregion

	/**
	 * 列の読み方を決める（覚えたものがあれば使い回す。3.0.0）
	 *
	 * @param metaData	結果の列
	 * @return	読み方
	 * @throws java.sql.SQLException	列の情報を読めなかったとき
	 */
	private ResultColumns columns (ResultSetMetaData metaData) throws java.sql.SQLException {

		int count = metaData.getColumnCount();

		// 位置ごとのキーの数が列の数と合わなければ使わない（列名で入れ子にする）
		List<String> useLabels = labels != null && labels.size() == count ? labels : null;

		boolean cacheable = source != null && sql != null;
		int[] jdbcTypes = new int[count];
		for (int i = 0; i < count; i++) {
			jdbcTypes[i] = metaData.getColumnType(i + 1);
		}

		if (cacheable) {
			ResultColumns cached = ResultColumnCache.get(source, sql, useLabels, count);
			// 列ごとの JDBC の型が覚えたときと同じときだけ使う（実行中の DDL で INT を BIGINT にした、などを拾う）
			if (cached != null && java.util.Arrays.equals(cached.jdbcTypes(), jdbcTypes)) {
				return cached;
			}
		}

		List<KeyInfo> keyInfoList = new ArrayList<>(count);
		List<Integer> columnTypeList = new ArrayList<>(count);
		readColumns(metaData, dialect(), useLabels, keyInfoList, columnTypeList);
		RowPlan plan = RowPlan.of(metaData, dialect(), keyInfoList, columnTypeList);

		ResultColumns created = new ResultColumns(List.copyOf(keyInfoList), List.copyOf(columnTypeList), plan
			, useLabels == null ? null : java.util.Collections.unmodifiableList(new ArrayList<>(useLabels)), jdbcTypes);

		if (cacheable) {
			ResultColumnCache.put(source, sql, created);
		}

		return created;

	}

	/**
	 * 結果の列のキーと型の分けを読む（キーはドライバの列名）
	 *
	 * @param metaData			結果の列
	 * @param dialect			方言
	 * @param keyInfoList		キーを足す先
	 * @param columnTypeList	型の分けを足す先
	 * @throws java.sql.SQLException	列の情報を読めなかったとき
	 */
	static void readColumns (ResultSetMetaData metaData, Dialect dialect, List<KeyInfo> keyInfoList, List<Integer> columnTypeList) throws java.sql.SQLException {

		readColumns(metaData, dialect, null, keyInfoList, columnTypeList);

	}

	/**
	 * 結果の列のキーと型の分けを読む
	 *
	 * @param metaData			結果の列
	 * @param dialect			方言
	 * @param labels			位置ごとの結果のキー（ビルダーが書いたもの。null の位置と、全体が null ならドライバの列名）
	 * @param keyInfoList		キーを足す先
	 * @param columnTypeList	型の分けを足す先
	 * @throws java.sql.SQLException	列の情報を読めなかったとき
	 */
	static void readColumns (ResultSetMetaData metaData, Dialect dialect, List<String> labels, List<KeyInfo> keyInfoList, List<Integer> columnTypeList) throws java.sql.SQLException {

		int colCount = metaData.getColumnCount();
		for (int i = 0; i < colCount; i++) {

			// ビルダーが書いたキーがあれば、列名を聞かない（3.0.0。SQL に AS `表__列` を付けないので、列名は列そのものの名前）
			String label = labels == null ? null : labels.get(i);
			String key = label != null ? label : metaData.getColumnLabel(i + 1);

			/*
			 * 型名で見分ける（要件 F-D-30）。
			 *
			 * MySQL の geometry は VARBINARY として返るので、
			 * <b>型名を見ずに VARBINARY を全部 geometry 扱いすると、
			 * PostgreSQL の bytea が壊れる。</b>
			 */
			String typeName = metaData.getColumnTypeName(i + 1);

			if (dialect.isJsonType(typeName)) {
				columnTypeList.add(ResultSetConverter.TYPE_JSON);
			} else if (dialect.isGeometryType(typeName)) {
				columnTypeList.add(ResultSetConverter.TYPE_GEOMETRY);
			} else if (metaData.getColumnType(i + 1) == Types.TIMESTAMP && dialect.isLocalDateTimeType(typeName)) {
				columnTypeList.add(ResultSetConverter.TYPE_LOCAL_DATETIME);
			} else {
				columnTypeList.add(metaData.getColumnType(i + 1));
			}

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

	public static class KeyInfo {

		String key;

		boolean isNest;

		String parentKey;

		String childKey;

	}

}
