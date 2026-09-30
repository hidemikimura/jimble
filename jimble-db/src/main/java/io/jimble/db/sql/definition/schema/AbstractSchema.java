package io.jimble.db.sql.definition.schema;

import io.jimble.util.data.definition.ISchema;

import io.jimble.db.DBSource;
import io.jimble.db.DBUtil;
import io.jimble.db.sql.definition.table.Table;
import io.jimble.util.log.Log;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * schema
 */
public abstract class AbstractSchema implements ISchema {

	// region テーブル一覧

	/* テーブル一覧取得済み判定 */
	private boolean isGetTableList = false;

	/* テーブル一覧 */
	private final List<Table> tableList = new ArrayList<>();

	/* テーブル一覧取得ロック */
	private final ReentrantLock tableLock = new ReentrantLock();

	/**
	 * DBSource
	 *
	 * @return	DBSource
	 */
	public DBSource dbSource () {

		return DBUtil.getDataSource(name());

	}

	/**
	 * テーブル一覧を取得する
	 *
	 * @return	テーブル一覧
	 */
	public List<Table> tableList () {

		if (isGetTableList) {
			return tableList;
		}

		try {

			tableLock.lock();

			if (isGetTableList) {
				return tableList;
			}

			tableListInner();

		} catch (Exception ex) {

			Log.error(ex);

		} finally {

			tableLock.unlock();

		}

		return tableList;

	}

	/**
	 * テーブル一覧を取得する
	 */
	private void tableListInner () {

		// 静的フィールドなので field.get(this) の this は使われない（tableHolder が別のクラスでも読める）

		try {

			Field[] fields = tableHolder().getDeclaredFields();
			for (Field field : fields) {
				if (Table.class.isAssignableFrom(field.getType())) {
					tableList.add((Table) field.get(this));
				}
			}

		} catch (Exception ex) {

			Log.error(ex);

		}

		isGetTableList = true;

	}

	/**
	 * テーブルの定数（{@code static final Table}）を持つクラス
	 *
	 * <p>
	 * 既定は自分のクラス。生成されたスキーマの実体（{@code AqsellSchema}）は、定数を持つスキーマクラス
	 * （{@code Aqsell}）を返す——スキーマを2つに分けたのは、クラスの初期化を輪にしないためである。
	 * </p>
	 *
	 * @return	クラス
	 */
	protected Class<?> tableHolder () {

		return getClass();

	}

	// endregion

}
