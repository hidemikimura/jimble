package io.jimble.db.sql.query.dsl;

import io.jimble.util.data.definition.IColumn;
import io.jimble.db.sql.query.dsl.select.*;
import io.jimble.db.sql.query.dsl.where.Match;
import io.jimble.db.sql.query.dsl.where.STWithIn;
import io.jimble.db.sql.query.select.ISelect;
import io.jimble.db.sql.query.select.SelectQuery;
import io.jimble.db.sql.query.select.SelectValue;
import io.jimble.db.sql.query.where.IWhere;
import io.jimble.db.sql.query.where.WhereQuery;

/**
 * DSL
 */
public class Dsl {

	// region value

	/**
	 * value
	 *
	 * @param value value
	 * @return  ISelect
	 */
	public static ISelect value (Object value) {

		return new SelectValue().value(value);

	}

	// endregion

	// region VALUES

	/**
	 * values
	 *
	 * @param column	column
	 * @return	values
	 */
	public static ISelect values (IColumn column) {

		return new SelectQuery().dsl(new Values(column));

	}

	// endregion

	// region DATE_FORMAT

	/**
	 * date_format
	 *
	 * @param select	select
	 * @param format	format
	 * @return	date_format
	 */
	public static ISelect dateFormat (ISelect select, String format) {

		return new SelectQuery().dsl(new DateFormat(select, format));

	}

	// endregion

	// region count

	/**
	 * count
	 *
	 * @return	count
	 */
	public static ISelect count() {

		return new SelectQuery().dsl(new Count(null));

	}

	/**
	 * count
	 *
	 * @param select	select
	 * @return	count
	 */
	public static ISelect count(ISelect select) {

		return new SelectQuery().dsl(new Count(select));

	}

	// endregion

	// region sum

	/**
	 * sum
	 *
	 * @param select	select
	 * @return	sum
	 */
	public static ISelect sum(ISelect select) {

		return new SelectQuery().dsl(new Sum(select));

	}

	// endregion

	// region min

	/**
	 * min
	 *
	 * @param select	select
	 * @return	min
	 */
	public static ISelect min(ISelect select) {

		return new SelectQuery().dsl(new Min(select));

	}

	// endregion

	// region max

	/**
	 * max
	 *
	 * @param select	select
	 * @return	max
	 */
	public static ISelect max(ISelect select) {

		return new SelectQuery().dsl(new Max(select));

	}

	// endregion

	// region avg

	/**
	 * avg
	 *
	 * @param select	select
	 * @return	avg
	 */
	public static ISelect avg(ISelect select) {

		return new SelectQuery().dsl(new Avg(select));

	}

	// endregion

	// region ceiling

	/**
	 * ceiling
	 *
	 * @param select	select
	 * @return	ceiling
	 */
	public static ISelect ceiling(ISelect select) {

		return new SelectQuery().dsl(new Ceiling(select));

	}

	// endregion

	// region floor

	/**
	 * floor
	 *
	 * @param select	select
	 * @return	floor
	 */
	public static ISelect floor(ISelect select) {

		return new SelectQuery().dsl(new Floor(select));

	}

	// endregion

	// region round

	/**
	 * round
	 *
	 * @param select	select
	 * @return	round
	 */
	public static ISelect round(ISelect select) {

		return new SelectQuery().dsl(new Round(select));

	}

	// endregion

	// region round

	/**
	 * round
	 *
	 * @param select	select
	 * @param digit		桁数
	 * @return	round
	 */
	public static ISelect round(ISelect select, int digit) {

		return new SelectQuery().dsl(new Round(select, digit));

	}

	// endregion

	// region truncate

	/**
	 * truncate
	 *
	 * @param select	select
	 * @return	truncate
	 */
	public static ISelect truncate(ISelect select) {

		return new SelectQuery().dsl(new Truncate(select));

	}

	// endregion

	// region truncate

	/**
	 * truncate
	 *
	 * @param select	select
	 * @param digit		桁数
	 * @return	truncate
	 */
	public static ISelect truncate(ISelect select, int digit) {

		return new SelectQuery().dsl(new Truncate(select, digit));

	}

	// endregion

	// region now

	/**
	 * now
	 *
	 * @return	now
	 */
	public static ISelect now() {

		return new SelectQuery().dsl(new Now());

	}

	// endregion

	// region now

	/**
	 * rand
	 *
	 * @return	rand
	 */
	public static ISelect rand() {

		return new SelectQuery().dsl(new Rand());

	}

	// endregion

	// region case

	/**
	 * case
	 *
	 * @return  Case
	 */
	public static Case caseWhen() {

		return new Case();

	}

	// endregion

	// region json_extract

	/**
	 * json_extract
	 *
	 * @param field     field
	 * @param jsonPath  json path
	 * @return  ISelect
	 */
	public static ISelect jsonExtract (ISelect field, String jsonPath) {

		return new SelectQuery().dsl(new JsonExtract(field, jsonPath));

	}

	// endregion

	// region json_unquote

	/**
	 * json_unquote
	 *
	 * @param field     field
	 * @param jsonPath  json path
	 * @return  ISelect
	 */
	public static ISelect jsonUnquote (ISelect field, String jsonPath) {

		return new SelectQuery().dsl(new JsonUnquote(field, jsonPath));

	}

	// endregion

	// region ifnull

	/**
	 * ifnull
	 *
	 * @param value1    値1
	 * @param value2    値2
	 * @return  ISelect
	 */
	public static ISelect ifnull (Object value1, Object value2) {

		return new SelectQuery().dsl(new IfNull(value1, value2));

	}

	// endregion


	// region match

	/**
	 * match
	 *
	 * @param select    ISelect
	 * @return  Match
	 */
	public static Match match (ISelect select) {

		return new Match(select);

	}

	// endregion


	// region and

	/**
	 * AND
	 *
	 * @param where	where
	 * @return	where
	 */
	public static IWhere and(IWhere where) {

		WhereQuery whereQuery = (WhereQuery) where;
		whereQuery.logicalOperator("AND");

		return whereQuery;

	}

	// endregion

	// region or

	/**
	 * OR
	 *
	 * @param where	where
	 * @return	where
	 */
	public static IWhere or(IWhere where) {

		WhereQuery whereQuery = (WhereQuery) where;
		whereQuery.logicalOperator("OR");

		return whereQuery;

	}

	// endregion


	// region 何秒前

	/**
	 * 何秒前
	 *
	 * @param seconds   秒
	 * @return  Dsl
	 */
	public static IDsl secondsAgo (long seconds) {

		return new FreeSQL(
			"CURRENT_TIMESTAMP + INTERVAL - ? SECOND"
			, seconds
		);

	}

	// endregion

	// region 何秒後

	/**
	 * 何秒後
	 *
	 * @param seconds   秒
	 * @return  Dsl
	 */
	public static IDsl secondsAfter (long seconds) {

		return new FreeSQL(
			"CURRENT_TIMESTAMP + INTERVAL + ? SECOND"
			, seconds
		);

	}

	// endregion

	// region 何分前

	/**
	 * 何分前
	 *
	 * @param minutes   分
	 * @return  Dsl
	 */
	public static IDsl minutesAgo (long minutes) {

		return new FreeSQL(
			"CURRENT_TIMESTAMP + INTERVAL - ? MINUTE"
			, minutes
		);

	}

	// endregion

	// region 何分後

	/**
	 * 何分後
	 *
	 * @param minutes   分
	 * @return  Dsl
	 */
	public static IDsl minutesAfter (long minutes) {

		return new FreeSQL(
			"CURRENT_TIMESTAMP + INTERVAL + ? MINUTE"
			, minutes
		);

	}

	// endregion

	// region 何時間前

	/**
	 * 何時間前
	 *
	 * @param hours   時間
	 * @return  Dsl
	 */
	public static IDsl hoursAgo (long hours) {

		return new FreeSQL(
			"CURRENT_TIMESTAMP + INTERVAL - ? HOUR"
			, hours
		);

	}

	// endregion

	// region 何時間後

	/**
	 * 何時間後
	 *
	 * @param hours   時間
	 * @return  Dsl
	 */
	public static IDsl hoursAfter (long hours) {

		return new FreeSQL(
			"CURRENT_TIMESTAMP + INTERVAL + ? HOUR"
			, hours
		);

	}

	// endregion

	// region 何日前

	/**
	 * 何日前
	 *
	 * @param days   日
	 * @return  Dsl
	 */
	public static IDsl daysAgo (long days) {

		return new FreeSQL(
			"CURRENT_TIMESTAMP + INTERVAL - ? DAY"
			, days
		);

	}

	// endregion

	// region 何日後

	/**
	 * 何日後
	 *
	 * @param days   日
	 * @return  Dsl
	 */
	public static IDsl daysAfter (long days) {

		return new FreeSQL(
			"CURRENT_TIMESTAMP + INTERVAL + ? DAY"
			, days
		);

	}

	// endregion

	// region 何週間前

	/**
	 * 何週間前
	 *
	 * @param weeks   週間
	 * @return  Dsl
	 */
	public static IDsl weeksAgo (long weeks) {

		return new FreeSQL(
			"CURRENT_TIMESTAMP + INTERVAL - ? WEEK"
			, weeks
		);

	}

	// endregion

	// region 何週間後

	/**
	 * 何週間後
	 *
	 * @param weeks   週間
	 * @return  Dsl
	 */
	public static IDsl weeksAfter (long weeks) {

		return new FreeSQL(
			"CURRENT_TIMESTAMP + INTERVAL + ? WEEK"
			, weeks
		);

	}

	// endregion

	// region 何ヶ月前

	/**
	 * 何ヶ月前
	 *
	 * @param months   月
	 * @return  Dsl
	 */
	public static IDsl monthsAgo (long months) {

		return new FreeSQL(
			"CURRENT_TIMESTAMP + INTERVAL - ? MONTH"
			, months
		);

	}

	// endregion

	// region 何ヶ月後

	/**
	 * 何ヶ月後
	 *
	 * @param months   月
	 * @return  Dsl
	 */
	public static IDsl monthsAfter (long months) {

		return new FreeSQL(
			"CURRENT_TIMESTAMP + INTERVAL + ? MONTH"
			, months
		);

	}

	// endregion

	// region 何年前

	/**
	 * 何年前
	 *
	 * @param years   年
	 * @return  Dsl
	 */
	public static IDsl yearsAgo (long years) {

		return new FreeSQL(
			"CURRENT_TIMESTAMP + INTERVAL - ? YEAR"
			, years
		);

	}

	// endregion

	// region 何年後

	/**
	 * 何年後
	 *
	 * @param years   年
	 * @return  Dsl
	 */
	public static IDsl yearsAfter (long years) {

		return new FreeSQL(
			"CURRENT_TIMESTAMP + INTERVAL + ? YEAR"
			, years
		);

	}

	// endregion

	// region Concat

	/**
	 * concat
	 *
	 * @param values	値一覧
	 * @return	Concat
	 */
	public static Concat concat (Object...values) {

		return new Concat(values);

	}

	// endregion

	// region ST_GeomFromText

	/**
	 * ST_GeomFromText
	 *
	 * @param value	値
	 * @return	STGeomFromText
	 */
	public static ISelect stGeomFromText (Object value) {

		return new SelectQuery().dsl(new STGeomFromText(value));

	}

	/**
	 * ST_GeomFromText
	 *
	 * @param value	値
	 * @param srId	SRID
	 * @return	STGeomFromText
	 */
	public static ISelect stGeomFromText (Object value, int srId) {

		return new SelectQuery().dsl(new STGeomFromText(value, srId));

	}

	// endregion

	// region ST_Distance_Sphere

	/**
	 * ST_Distance_Sphere
	 *
	 * @param value1	値1
	 * @param value2	値2
	 * @return	STDistanceSphere
	 */
	public static ISelect stDistanceSphere (Object value1, Object value2) {

		return new SelectQuery().dsl(new STDistanceSphere(value1, value2));

	}

	// endregion

	// region ST_Within

	/**
	 * ST_Within
	 *
	 * @param value1	値1
	 * @param value2	値2
	 * @return	STWithIn
	 */
	public static IWhere stWithin (Object value1, Object value2) {

		return new WhereQuery(new STWithIn(value1, value2));

	}

	// endregion

}
