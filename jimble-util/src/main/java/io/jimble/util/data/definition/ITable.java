package io.jimble.util.data.definition;


/**
 * Table
 */
public interface ITable {

	/**
	 * スキーマ定義を取得する
	 *
	 * @return	スキーマ定義
	 */
	public ISchema schema();

	/**
	 * テーブル名を取得する
	 *
	 * @return	テーブル名
	 */
	public String name();

}
