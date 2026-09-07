package io.jimble.util.data.definition;


/**
 * Column
 */
public interface IColumn {

	/**
	 * テーブル定義を取得する
	 *
	 * @return	テーブル定義
	 */
	public ITable table();

	/**
	 * 列名を取得する
	 *
	 * @return	列名
	 */
	public String name();

	/**
	 * クラスを取得する
	 *
	 * @return  クラス
	 */
	public Class<?> clazz();

	/**
	 * null許容判定
	 *
	 * @return  null許容判定
	 */
	public boolean isNullable();

	/**
	 * デフォルト値
	 *
	 * @return  デフォルト値
	 */
	public Object defaultValue();

	/**
	 * PK判定
	 *
	 * @return PK判定
	 */
	public boolean isPrimaryKey();

}
