package io.jimble.db.sql;

import io.jimble.db.dialect.Dialect;
import io.jimble.db.dialect.Dialects;

import java.util.List;

/**
 * builder
 */
public interface IBuilder {

	/**
	 * SQLを作成する
	 *
	 * <p>
	 * <b>既定の方言</b>（主データソースの {@code product}）で作る。
	 * {@link io.jimble.db.DB} は接続先の方言を渡す {@link #sql(Dialect)} を呼ぶので、
	 * <b>アプリのコードは書き換えなくてよい</b>（要件 F-D-30）。
	 * </p>
	 *
	 * @return	SQL
	 */
	default String sql () {

		return sql(Dialects.defaultDialect());

	}

	/**
	 * SQLを作成する（要件 F-D-30）
	 *
	 * <p>
	 * <b>DB 接続なしで呼べる</b>（要件 F-D-06）。
	 * その製品では書けないものが混ざっていたら
	 * {@link io.jimble.db.dialect.DialectException} を投げる。
	 * </p>
	 *
	 * @param dialect	方言
	 * @return	SQL
	 */
	String sql (Dialect dialect);

	/**
	 * パラメータを取得する
	 *
	 * @return	パラメータ
	 */
	List<Object> params ();

}
