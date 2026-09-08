package io.jimble.db.sql.query.dsl.property;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.query.dsl.IDsl;

/**
 * point function
 */
public class Point implements IDsl {

	/* 緯度 */
	private final double lat;

	/**
	 * 緯度
	 *
	 * @return	緯度
	 */
	public double getLat () {

		return lat;

	}

	/* 経度 */
	private final double lon;

	/**
	 * 経度
	 *
	 * @return	経度
	 */
	public double getLon () {

		return lon;

	}

	/**
	 * コンストラクタ
	 *
	 * @param lat	緯度
	 * @param lon	経度
	 */
	public Point(double lat, double lon) {

		this.lat = lat;
		this.lon = lon;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql (SqlWriter sb) {

		sb.append("'POINT(");
		sb.append(lat);
		sb.append(" ");
		sb.append(lon);
		sb.append(")'");

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasParameter() {

		return false;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object getParameter() {

		return null;

	}

}
