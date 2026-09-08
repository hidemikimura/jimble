package io.jimble.db.sql.query.dsl.select;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.query.dsl.AbstractDsl;
import io.jimble.db.sql.query.dsl.IDsl;
import io.jimble.db.sql.query.order_by.IOrderBy;
import io.jimble.db.sql.query.select.ISelect;
import io.jimble.util.data.definition.IColumn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * ウィンドウ関数（要件 F-D-31）
 *
 * <p>
 * <b>行をまとめずに、まとめた結果を各行に付ける。</b>
 * {@code GROUP BY} と違って行が減らない。
 * MySQL 8 と PostgreSQL で書き方は同じなので、方言の分岐は要らない。
 * </p>
 *
 * <pre>
 * // 店ごとの売上順位
 * SQL.select(
 *         Sale.shop_id
 *         , Sale.amount
 *         , Dsl.rank().partitionBy(Sale.shop_id).orderBy(Sale.amount.desc()).as("rank"))
 *     .from(Sale.instance());
 *
 * // 累計
 * Dsl.over(Dsl.sum(Sale.amount))
 *     .orderBy(Sale.sold_at.asc())
 *     .rowsBetween(WindowFrame.unboundedPreceding(), WindowFrame.currentRow())
 *     .as("total");
 * </pre>
 */
public class Over extends AbstractDsl implements IDsl {

	/* 中身の関数 */
	private final IDsl function;

	/* PARTITION BY */
	private final List<Object> partitionList = new ArrayList<>();

	/* ORDER BY */
	private final List<IOrderBy> orderByList = new ArrayList<>();

	/* ROWS BETWEEN の始まり */
	private WindowFrame frameStart = null;

	/* ROWS BETWEEN の終わり */
	private WindowFrame frameEnd = null;

	/**
	 * コンストラクタ
	 *
	 * @param function	中身の関数
	 */
	public Over (IDsl function) {

		this.function = function;

	}

	// region 組み立て

	/**
	 * PARTITION BY
	 *
	 * <p>ここで区切った中だけで数える。</p>
	 *
	 * @param values	列や式
	 * @return	自分
	 */
	public Over partitionBy (Object...values) {

		partitionList.addAll(Arrays.asList(values));
		return this;

	}

	/**
	 * ORDER BY
	 *
	 * <p>
	 * <b>順位や累計は、この順で決まる。</b>
	 * {@code Column#asc()} / {@code desc()} を渡す。
	 * </p>
	 *
	 * @param orderBys	並び
	 * @return	自分
	 */
	public Over orderBy (IOrderBy...orderBys) {

		orderByList.addAll(Arrays.asList(orderBys));
		return this;

	}

	/**
	 * ROWS BETWEEN
	 *
	 * @param start	始まり
	 * @param end	終わり
	 * @return	自分
	 */
	public Over rowsBetween (WindowFrame start, WindowFrame end) {

		/*
		 * 片方だけだと、下の出力で<b>ROWS BETWEEN ごと消える</b>。
		 * 累計のつもりがパーティション全体の合計になり、例外も出ない。
		 */
		if (start == null || end == null) {
			throw new IllegalArgumentException("ROWS BETWEEN は始まりと終わりの両方が要ります");
		}

		this.frameStart = start;
		this.frameEnd = end;
		return this;

	}

	// endregion

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql (SqlWriter sb) {

		function.dslSql(sb);

		sb.append(" OVER (");

		boolean written = false;

		if (!partitionList.isEmpty()) {

			sb.append("PARTITION BY ");
			written = true;

			for (int i = 0; i < partitionList.size(); i++) {
				if (i > 0) {
					sb.append(", ");
				}
				write(sb, partitionList.get(i));
			}

		}

		if (!orderByList.isEmpty()) {

			if (written) {
				sb.append(' ');
			}

			sb.append("ORDER BY ");
			written = true;

			for (int i = 0; i < orderByList.size(); i++) {
				if (i > 0) {
					sb.append(", ");
				}
				orderByList.get(i).orderBySql(sb);
			}

		}

		if (frameStart != null && frameEnd != null) {

			if (written) {
				sb.append(' ');
			}

			sb.append("ROWS BETWEEN ").append(frameStart.sql()).append(" AND ").append(frameEnd.sql());

		}

		sb.append(')');

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasParameter () {

		return !parameters().isEmpty();

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object getParameter () {

		return parameters();

	}

	/**
	 * バインドするパラメータ
	 *
	 * <p><b>書き出す順と同じ順</b>で並べる。</p>
	 *
	 * @return	パラメータ
	 */
	private List<Object> parameters () {

		List<Object> params = new ArrayList<>();

		if (function.hasParameter()) {
			params.add(function.getParameter());
		}

		for (Object value : partitionList) {
			if (value instanceof IColumn) {
				continue;
			}
			if (value instanceof IDsl dsl) {
				if (dsl.hasParameter()) {
					params.add(dsl.getParameter());
				}
			} else if (value instanceof ISelect select) {
				if (select.hasParameter()) {
					params.add(select.getParameter());
				}
			} else {
				params.add(value);
			}
		}

		/*
		 * ORDER BY のぶんも忘れずに拾う。
		 * <b>? を1つ出して1つも数えないと、以降のバインドが全部ずれる</b>
		 * （SELECT の値が WHERE に入る）。
		 */
		for (IOrderBy orderBy : orderByList) {
			if (orderBy.hasParameter()) {
				params.add(orderBy.getParameter());
			}
		}

		return params;

	}

	/**
	 * 値を1つ書き出す
	 *
	 * @param sb	書き出し先
	 * @param value	値
	 */
	private static void write (SqlWriter sb, Object value) {

		if (value instanceof IColumn column) {
			sb.qualified(column);
		} else if (value instanceof IDsl dsl) {
			dsl.dslSql(sb);
		} else if (value instanceof ISelect select) {
			select.selectSql(sb);
		} else {
			sb.append("?");
		}

	}

}
