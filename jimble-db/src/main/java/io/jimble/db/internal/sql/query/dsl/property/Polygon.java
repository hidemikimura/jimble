package io.jimble.db.internal.sql.query.dsl.property;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.query.dsl.IDsl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * polygon function
 */
public class Polygon implements IDsl {

	/* 座標一覧 */
	private final List<Point> pointList = new ArrayList<>();

	/**
	 * 座標追加
	 *
	 * @param points	Point一覧
	 * @return	Polygon
	 */
	public Polygon addPoint (Point...points) {

		this.pointList.addAll(Arrays.asList(points));
		return this;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql (SqlWriter sb) {

		if (pointList.isEmpty()) {
			return;
		}

		Point first = pointList.getFirst();
		Point last = pointList.getLast();
		if (first.getLat() != last.getLat()
			|| first.getLon() != last.getLon()) {
			pointList.add(first);
		}

		sb.append("'POLYGON((");
		boolean isFirst = true;
		for (Point point : pointList) {
			if (isFirst) {
				isFirst = false;
			} else {
				sb.append(", ");
			}
			sb.append(point.getLat());
			sb.append(" ");
			sb.append(point.getLon());
		}
		sb.append("))'");

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
