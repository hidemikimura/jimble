package io.jimble.db.internal.sql.query.dsl.where;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.query.dsl.IDsl;
import io.jimble.db.sql.query.select.ISelect;
import io.jimble.db.sql.query.where.IWhere;
import io.jimble.db.sql.query.where.WhereQuery;

import java.util.ArrayList;
import java.util.List;

/**
 * match
 */
public class Match implements IDsl {

	/* select */
	private ISelect[] selects = null;

	/* value */
	private String value = null;

	/* search modifier */
	private SearchModifier searchModifier = null;

	/**
	 * コンストラクタ
	 *
	 * @param selects	ISelect
	 */
	public Match (ISelect...selects) {

		this.selects = selects;

	}

	/**
	 * Against
	 *
	 * @param value 値
	 * @return  Match
	 */
	public Match against (String value) {

		this.value = value;
		return this;

	}

	/**
	 * デフォルトモード
	 *
	 * @return  IWhere
	 */
	public IWhere defaultMode () {

		this.searchModifier = null;
		return where();

	}

	/**
	 * IN NATURAL LANGUAGE MODE
	 *
	 * @return  IWhere
	 */
	public IWhere inNaturalLanguageMode () {

		this.searchModifier = SearchModifier.IN_NATURAL_LANGUAGE_MODE;
		return where();

	}

	/**
	 * IN NATURAL LANGUAGE MODE WITH QUERY EXPANSION
	 *
	 * @return  IWhere
	 */
	public IWhere inNaturalLanguageModeWithQueryExpansion () {

		this.searchModifier = SearchModifier.IN_NATURAL_LANGUAGE_MODE_WITH_QUERY_EXPANSION;
		return where();

	}

	/**
	 * IN BOOLEAN MODE
	 *
	 * @return  IWhere
	 */
	public IWhere inBooleanMode () {

		this.searchModifier = SearchModifier.IN_BOOLEAN_MODE;
		return where();

	}

	/**
	 * WITH QUERY EXPANSION
	 *
	 * @return  IWhere
	 */
	public IWhere withQueryExpansion () {

		this.searchModifier = SearchModifier.WITH_QUERY_EXPANSION;
		return where();

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dslSql (SqlWriter sb) {

		/*
		 * 全文検索は製品ごとに別物である（要件 F-D-30）。
		 *
		 * PostgreSQL の to_tsvector に寄せると、語彙の分割もスコアも変わる。
		 * <b>「動くけれど検索結果が違う」</b>がいちばん気づけないので、
		 * 対応する語彙が無い製品では組み立てた時点で例外にする。
		 */
		sb.dialect().fullTextMatch(sb.builder()
			, () -> {
				for (int i = 0; i < selects.length; i++) {
					if (i > 0) {
						sb.append(", ");
					}
					selects[i].selectSql(sb);
				}
			}
			, searchModifier == null ? null : searchModifier.searchModifier());

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasParameter() {

		if (value != null) {
			return true;
		}

		for (ISelect select : selects) {
			if (select.hasParameter()) {
				return true;
			}
		}

		return false;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object getParameter() {

		List<Object> params = new ArrayList<>();
		if (value != null) {
			params.add(value);
		}

		for (ISelect select : selects) {
			if (select.hasParameter()) {
				params.add(select.getParameter());
			}
		}

		return params;

	}

	/**
	 * IWhere
	 *
	 * @return  IWhere
	 */
	private IWhere where () {

		return new WhereQuery((IDsl) this);

	}

	/**
	 * 検索オプション
	 */
	public enum SearchModifier {

		/* IN NATURAL LANGUAGE MODE */
		IN_NATURAL_LANGUAGE_MODE("IN NATURAL LANGUAGE MODE")

		/* IN NATURAL LANGUAGE MODE WITH QUERY EXPANSION */
		, IN_NATURAL_LANGUAGE_MODE_WITH_QUERY_EXPANSION("IN NATURAL LANGUAGE MODE WITH QUERY EXPANSION")

		/* IN BOOLEAN MODE */
		, IN_BOOLEAN_MODE("IN BOOLEAN MODE")

		/* WITH QUERY EXPANSION */
		, WITH_QUERY_EXPANSION("WITH QUERY EXPANSION")

		;

		/* search modifier */
		private final String searchModifier;

		/**
		 * search modifierを取得する
		 *
		 * @return  search modifier
		 */
		public String searchModifier() {

			return searchModifier;

		}

		/**
		 * コンストラクタ
		 *
		 * @param searchModifier    search modifier
		 */
		SearchModifier (String searchModifier) {

			this.searchModifier = searchModifier;

		}

	}

}
