package io.jimble.db.sql;

import io.jimble.db.dialect.Dialect;

import java.util.ArrayList;
import java.util.List;

/**
 * 値を入れた {@link SelectTemplate}（2.8.0）
 *
 * <p>
 * {@link SelectTemplate#bind(Object...)} で作り、{@code db.select(...)}・{@code db.selectList(...)}・
 * {@code db.selectCached(...)}・{@code db.selectListCached(...)} に渡す。作ったあとは変わらない。
 * </p>
 */
public final class BoundSelect implements IBuilder {

	/* template */
	private final SelectTemplate template;

	/* 値の並びの印ごとの値の数 */
	private final int[] listSizes;

	/* パラメータ */
	private final List<Object> params;

	BoundSelect (SelectTemplate template, int[] listSizes, List<Object> params) {

		this.template = template;
		this.listSizes = listSizes;
		this.params = params;

	}

	/**
	 * template
	 *
	 * @return	template
	 */
	public SelectTemplate template () {

		return template;

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>方言ごとの字面は template が 1 回だけ作る。{@code IN} の値の並びがあれば、値の数だけ {@code ?} を並べる。</p>
	 */
	@Override
	public String sql (Dialect dialect) {

		return template.sql(dialect, listSizes);

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>呼ぶたびに新しいリストを返す（書き換えても、この SELECT に響かない）。</p>
	 */
	@Override
	public List<Object> params () {

		return new ArrayList<>(params);

	}

}
