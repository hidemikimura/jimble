package io.jimble.db.sql.query.dsl;

import io.jimble.db.dialect.CastType;
import io.jimble.db.dialect.DatePart;
import io.jimble.db.dialect.DateUnit;
import io.jimble.db.dialect.SqlFunction;
import io.jimble.db.sql.query.dsl.where.Regexp;
import io.jimble.util.data.definition.IColumn;
import io.jimble.db.sql.query.dsl.select.IntervalFromNow;
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

		return new IntervalFromNow("SECOND", true, seconds);

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

		return new IntervalFromNow("SECOND", false, seconds);

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

		return new IntervalFromNow("MINUTE", true, minutes);

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

		return new IntervalFromNow("MINUTE", false, minutes);

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

		return new IntervalFromNow("HOUR", true, hours);

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

		return new IntervalFromNow("HOUR", false, hours);

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

		return new IntervalFromNow("DAY", true, days);

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

		return new IntervalFromNow("DAY", false, days);

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

		return new IntervalFromNow("WEEK", true, weeks);

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

		return new IntervalFromNow("WEEK", false, weeks);

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

		return new IntervalFromNow("MONTH", true, months);

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

		return new IntervalFromNow("MONTH", false, months);

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

		return new IntervalFromNow("YEAR", true, years);

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

		return new IntervalFromNow("YEAR", false, years);

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

	// region 文字列（要件 F-D-31）

	/**
	 * 小文字にする
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	public static ISelect lower (Object value) {

		return new SelectQuery().dsl(new NamedFunction(SqlFunction.LOWER, value));

	}

	/**
	 * 大文字にする
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	public static ISelect upper (Object value) {

		return new SelectQuery().dsl(new NamedFunction(SqlFunction.UPPER, value));

	}

	/**
	 * 前後の空白を落とす
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	public static ISelect trim (Object value) {

		return new SelectQuery().dsl(new NamedFunction(SqlFunction.TRIM, value));

	}

	/**
	 * 前の空白を落とす
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	public static ISelect ltrim (Object value) {

		return new SelectQuery().dsl(new NamedFunction(SqlFunction.LTRIM, value));

	}

	/**
	 * 後ろの空白を落とす
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	public static ISelect rtrim (Object value) {

		return new SelectQuery().dsl(new NamedFunction(SqlFunction.RTRIM, value));

	}

	/**
	 * 文字数
	 *
	 * <p>
	 * <b>バイト数ではない。</b>MySQL の {@code LENGTH} はバイト数なので、
	 * ここでは {@code CHAR_LENGTH} を使う。バイト数は {@link #byteLength}。
	 * </p>
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	public static ISelect length (Object value) {

		return new SelectQuery().dsl(new NamedFunction(SqlFunction.CHAR_LENGTH, value));

	}

	/**
	 * バイト数
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	public static ISelect byteLength (Object value) {

		return new SelectQuery().dsl(new NamedFunction(SqlFunction.BYTE_LENGTH, value));

	}

	/**
	 * 部分文字列（1から）
	 *
	 * @param value	値
	 * @param from	何文字目から（1から）
	 * @return	ISelect
	 */
	public static ISelect substring (Object value, int from) {

		return new SelectQuery().dsl(new NamedFunction(SqlFunction.SUBSTRING, value, from));

	}

	/**
	 * 部分文字列（1から）
	 *
	 * @param value		値
	 * @param from		何文字目から（1から）
	 * @param length	何文字
	 * @return	ISelect
	 */
	public static ISelect substring (Object value, int from, int length) {

		return new SelectQuery().dsl(new NamedFunction(SqlFunction.SUBSTRING, value, from, length));

	}

	/**
	 * 置き換える
	 *
	 * @param value	値
	 * @param from	探すもの
	 * @param to	置き換えるもの
	 * @return	ISelect
	 */
	public static ISelect replace (Object value, Object from, Object to) {

		return new SelectQuery().dsl(new NamedFunction(SqlFunction.REPLACE, value, from, to));

	}

	/**
	 * 左から n 文字
	 *
	 * @param value		値
	 * @param length	文字数
	 * @return	ISelect
	 */
	public static ISelect left (Object value, int length) {

		return new SelectQuery().dsl(new NamedFunction(SqlFunction.LEFT, value, length));

	}

	/**
	 * 右から n 文字
	 *
	 * @param value		値
	 * @param length	文字数
	 * @return	ISelect
	 */
	public static ISelect right (Object value, int length) {

		return new SelectQuery().dsl(new NamedFunction(SqlFunction.RIGHT, value, length));

	}

	/**
	 * 左を埋める
	 *
	 * @param value		値
	 * @param length	埋めたあとの文字数
	 * @param pad		埋める文字
	 * @return	ISelect
	 */
	public static ISelect lpad (Object value, int length, String pad) {

		return new SelectQuery().dsl(new NamedFunction(SqlFunction.LPAD, value, length, pad));

	}

	/**
	 * 右を埋める
	 *
	 * @param value		値
	 * @param length	埋めたあとの文字数
	 * @param pad		埋める文字
	 * @return	ISelect
	 */
	public static ISelect rpad (Object value, int length, String pad) {

		return new SelectQuery().dsl(new NamedFunction(SqlFunction.RPAD, value, length, pad));

	}

	/**
	 * 逆順にする
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	public static ISelect reverse (Object value) {

		return new SelectQuery().dsl(new NamedFunction(SqlFunction.REVERSE, value));

	}

	/**
	 * 繰り返す
	 *
	 * @param value	値
	 * @param count	回数
	 * @return	ISelect
	 */
	public static ISelect repeat (Object value, int count) {

		return new SelectQuery().dsl(new NamedFunction(SqlFunction.REPEAT, value, count));

	}

	/**
	 * 区切り文字を挟んで連結する
	 *
	 * <p>
	 * <b>{@link #concat} と違って NULL を飛ばす</b>（両製品ともそう動く）。
	 * </p>
	 *
	 * @param separator	区切り文字
	 * @param values	値
	 * @return	ISelect
	 */
	public static ISelect concatWs (String separator, Object...values) {

		Object[] args = new Object[values.length + 1];
		args[0] = separator;
		System.arraycopy(values, 0, args, 1, values.length);

		return new SelectQuery().dsl(new NamedFunction(SqlFunction.CONCAT_WS, args));

	}

	/**
	 * MD5
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	public static ISelect md5 (Object value) {

		return new SelectQuery().dsl(new NamedFunction(SqlFunction.MD5, value));

	}

	/**
	 * 部分文字列の位置（1から。無ければ 0）
	 *
	 * @param needle	探すもの
	 * @param haystack	対象
	 * @return	ISelect
	 */
	public static ISelect locate (Object needle, Object haystack) {

		return new SelectQuery().dsl(new Locate(needle, haystack));

	}

	// endregion

	// region 数値（要件 F-D-31）

	/**
	 * 絶対値
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	public static ISelect abs (Object value) {

		return new SelectQuery().dsl(new NamedFunction(SqlFunction.ABS, value));

	}

	/**
	 * 剰余
	 *
	 * @param value		値
	 * @param divisor	割る数
	 * @return	ISelect
	 */
	public static ISelect mod (Object value, Object divisor) {

		return new SelectQuery().dsl(new NamedFunction(SqlFunction.MOD, value, divisor));

	}

	/**
	 * べき乗
	 *
	 * @param value		値
	 * @param exponent	指数
	 * @return	ISelect
	 */
	public static ISelect power (Object value, Object exponent) {

		return new SelectQuery().dsl(new NamedFunction(SqlFunction.POWER, value, exponent));

	}

	/**
	 * 平方根
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	public static ISelect sqrt (Object value) {

		return new SelectQuery().dsl(new NamedFunction(SqlFunction.SQRT, value));

	}

	/**
	 * 符号（-1 / 0 / 1）
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	public static ISelect sign (Object value) {

		return new SelectQuery().dsl(new NamedFunction(SqlFunction.SIGN, value));

	}

	/**
	 * 指数
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	public static ISelect exp (Object value) {

		return new SelectQuery().dsl(new NamedFunction(SqlFunction.EXP, value));

	}

	/**
	 * 自然対数
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	public static ISelect ln (Object value) {

		return new SelectQuery().dsl(new NamedFunction(SqlFunction.LN, value));

	}

	/**
	 * 常用対数
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	public static ISelect log10 (Object value) {

		return new SelectQuery().dsl(new NamedFunction(SqlFunction.LOG10, value));

	}

	/**
	 * いちばん大きい値
	 *
	 * <p>
	 * <b>NULL の扱いが製品で違う。</b>MySQL は1つでも NULL なら NULL、
	 * PostgreSQL は NULL を無視する。NULL が入りうるなら
	 * {@link #ifnull} で埋めてから渡すこと。
	 * </p>
	 *
	 * @param values	値
	 * @return	ISelect
	 */
	public static ISelect greatest (Object...values) {

		return new SelectQuery().dsl(new NamedFunction(SqlFunction.GREATEST, values));

	}

	/**
	 * いちばん小さい値（NULL の扱いは {@link #greatest} と同じ）
	 *
	 * @param values	値
	 * @return	ISelect
	 */
	public static ISelect least (Object...values) {

		return new SelectQuery().dsl(new NamedFunction(SqlFunction.LEAST, values));

	}

	// endregion

	// region 日付（要件 F-D-31）

	/**
	 * 今日の日付
	 *
	 * @return	ISelect
	 */
	public static ISelect curDate () {

		return new SelectQuery().dsl(new CurrentDate());

	}

	/**
	 * いまの時刻
	 *
	 * @return	ISelect
	 */
	public static ISelect curTime () {

		return new SelectQuery().dsl(new CurrentTime());

	}

	/**
	 * 日時から日付だけを取り出す
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	public static ISelect date (Object value) {

		return new SelectQuery().dsl(new ToDate(value));

	}

	/**
	 * 年
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	public static ISelect year (Object value) {

		return new SelectQuery().dsl(new DatePartOf(DatePart.YEAR, value));

	}

	/**
	 * 月（1-12）
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	public static ISelect month (Object value) {

		return new SelectQuery().dsl(new DatePartOf(DatePart.MONTH, value));

	}

	/**
	 * 日（1-31）
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	public static ISelect day (Object value) {

		return new SelectQuery().dsl(new DatePartOf(DatePart.DAY, value));

	}

	/**
	 * 時（0-23）
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	public static ISelect hour (Object value) {

		return new SelectQuery().dsl(new DatePartOf(DatePart.HOUR, value));

	}

	/**
	 * 分（0-59）
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	public static ISelect minute (Object value) {

		return new SelectQuery().dsl(new DatePartOf(DatePart.MINUTE, value));

	}

	/**
	 * 秒（0-59）
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	public static ISelect second (Object value) {

		return new SelectQuery().dsl(new DatePartOf(DatePart.SECOND, value));

	}

	/**
	 * 四半期（1-4）
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	public static ISelect quarter (Object value) {

		return new SelectQuery().dsl(new DatePartOf(DatePart.QUARTER, value));

	}

	/**
	 * 曜日（1-7。日曜が 1）
	 *
	 * <p>PostgreSQL の {@code DOW} は日曜が 0 だが、ここで揃えてある。</p>
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	public static ISelect dayOfWeek (Object value) {

		return new SelectQuery().dsl(new DatePartOf(DatePart.DAY_OF_WEEK, value));

	}

	/**
	 * 年の通算日（1-366）
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	public static ISelect dayOfYear (Object value) {

		return new SelectQuery().dsl(new DatePartOf(DatePart.DAY_OF_YEAR, value));

	}

	/**
	 * 週（ISO-8601。1-53）
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	public static ISelect weekOfYear (Object value) {

		return new SelectQuery().dsl(new DatePartOf(DatePart.WEEK, value));

	}

	/**
	 * 日時を足す
	 *
	 * @param value		値
	 * @param amount	足す数
	 * @param unit		単位
	 * @return	ISelect
	 */
	public static ISelect dateAdd (Object value, long amount, DateUnit unit) {

		return new SelectQuery().dsl(new DateAdd(value, amount, unit, false));

	}

	/**
	 * 日時を引く
	 *
	 * @param value		値
	 * @param amount	引く数
	 * @param unit		単位
	 * @return	ISelect
	 */
	public static ISelect dateSub (Object value, long amount, DateUnit unit) {

		return new SelectQuery().dsl(new DateAdd(value, amount, unit, true));

	}

	/**
	 * 日付の差（日数）
	 *
	 * @param from	引かれるほう
	 * @param to	引くほう
	 * @return	ISelect
	 */
	public static ISelect dateDiff (Object from, Object to) {

		return new SelectQuery().dsl(new DateDiff(from, to));

	}

	/**
	 * 日時の差（秒）
	 *
	 * @param from	引かれるほう
	 * @param to	引くほう
	 * @return	ISelect
	 */
	public static ISelect secondsBetween (Object from, Object to) {

		return new SelectQuery().dsl(new SecondsBetween(from, to));

	}

	/**
	 * 日時を epoch 秒にする
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	public static ISelect unixTimestamp (Object value) {

		return new SelectQuery().dsl(new UnixTimestamp(value));

	}

	/**
	 * epoch 秒を日時にする
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	public static ISelect fromUnixTime (Object value) {

		return new SelectQuery().dsl(new FromUnixTime(value));

	}

	// endregion

	// region 条件・型変換（要件 F-D-31）

	/**
	 * 最初の NULL でない値
	 *
	 * @param values	値
	 * @return	ISelect
	 */
	public static ISelect coalesce (Object...values) {

		return new SelectQuery().dsl(new Coalesce(values));

	}

	/**
	 * 2つが同じなら NULL
	 *
	 * @param value1	値1
	 * @param value2	値2
	 * @return	ISelect
	 */
	public static ISelect nullif (Object value1, Object value2) {

		return new SelectQuery().dsl(new NullIf(value1, value2));

	}

	/**
	 * 条件で分ける
	 *
	 * <p>MySQL は {@code IF}、PostgreSQL は {@code CASE WHEN} になる。</p>
	 *
	 * @param condition	条件
	 * @param whenTrue	真のときの値
	 * @param whenFalse	偽のときの値
	 * @return	ISelect
	 */
	public static ISelect ifThenElse (IWhere condition, Object whenTrue, Object whenFalse) {

		return new SelectQuery().dsl(new IfThenElse(condition, whenTrue, whenFalse));

	}

	/**
	 * 型変換
	 *
	 * @param value	値
	 * @param type	行き先の型
	 * @return	ISelect
	 */
	public static ISelect cast (Object value, CastType type) {

		return new SelectQuery().dsl(new Cast(value, type));

	}

	/**
	 * 型変換（桁を指定する）
	 *
	 * @param value		値
	 * @param precision	全体桁
	 * @param scale		小数桁
	 * @return	ISelect
	 */
	public static ISelect castDecimal (Object value, int precision, int scale) {

		return new SelectQuery().dsl(new Cast(value, CastType.DECIMAL, precision, scale));

	}

	/**
	 * 正規表現に当たるか
	 *
	 * <p>
	 * <b>正規表現の方言までは揃わない。</b>{@code \d} のような略記ではなく
	 * {@code [0-9]} のように文字クラスで書くこと。
	 * </p>
	 *
	 * @param value		値
	 * @param pattern	正規表現
	 * @return	IWhere
	 */
	public static IWhere regexp (Object value, String pattern) {

		return new WhereQuery(new Regexp(value, pattern, false));

	}

	/**
	 * 正規表現に当たるか（大文字小文字を無視する）
	 *
	 * @param value		値
	 * @param pattern	正規表現
	 * @return	IWhere
	 */
	public static IWhere regexpIgnoreCase (Object value, String pattern) {

		return new WhereQuery(new Regexp(value, pattern, true));

	}

	// endregion

	// region 集約の追加（要件 F-D-31）

	/**
	 * 重複を除いた件数
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	public static ISelect countDistinct (Object value) {

		return new SelectQuery().dsl(new NamedFunction(SqlFunction.COUNT, "DISTINCT", value));

	}

	/**
	 * 重複を除いた合計
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	public static ISelect sumDistinct (Object value) {

		return new SelectQuery().dsl(new NamedFunction(SqlFunction.SUM, "DISTINCT", value));

	}

	/**
	 * 標本標準偏差
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	public static ISelect stddev (Object value) {

		return new SelectQuery().dsl(new NamedFunction(SqlFunction.STDDEV, value));

	}

	/**
	 * 標本分散
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	public static ISelect variance (Object value) {

		return new SelectQuery().dsl(new NamedFunction(SqlFunction.VARIANCE, value));

	}

	/**
	 * 集めて1つの文字列にする（区切りはカンマ）
	 *
	 * @param value	値
	 * @return	ISelect
	 */
	public static ISelect groupConcat (Object value) {

		return groupConcat(value, ",");

	}

	/**
	 * 集めて1つの文字列にする
	 *
	 * <p>MySQL は {@code GROUP_CONCAT}、PostgreSQL は {@code STRING_AGG} になる。</p>
	 *
	 * @param value		値
	 * @param separator	区切り文字
	 * @return	ISelect
	 */
	public static ISelect groupConcat (Object value, String separator) {

		return new SelectQuery().dsl(new GroupConcat(value, separator, false));

	}

	/**
	 * 集めて1つの文字列にする（重複を除く）
	 *
	 * @param value		値
	 * @param separator	区切り文字
	 * @return	ISelect
	 */
	public static ISelect groupConcatDistinct (Object value, String separator) {

		return new SelectQuery().dsl(new GroupConcat(value, separator, true));

	}

	// endregion

	// region ウィンドウ関数（要件 F-D-31）

	/**
	 * 集約をウィンドウ関数にする
	 *
	 * <pre>
	 * Dsl.over(Dsl.sum(Sale.amount)).partitionBy(Sale.shop_id).as("total")
	 * </pre>
	 *
	 * @param function	中身（{@code Dsl.sum(...)} など）
	 * @return	Over
	 */
	public static Over over (Object function) {

		return new Over(toDsl(function));

	}

	/**
	 * 通し番号
	 *
	 * @return	Over
	 */
	public static Over rowNumber () {

		return new Over(new NamedFunction(SqlFunction.ROW_NUMBER));

	}

	/**
	 * 順位（同順位のあとは飛ぶ）
	 *
	 * @return	Over
	 */
	public static Over rank () {

		return new Over(new NamedFunction(SqlFunction.RANK));

	}

	/**
	 * 順位（同順位のあとも飛ばない）
	 *
	 * @return	Over
	 */
	public static Over denseRank () {

		return new Over(new NamedFunction(SqlFunction.DENSE_RANK));

	}

	/**
	 * n 等分したときの何番目か
	 *
	 * @param buckets	いくつに分けるか
	 * @return	Over
	 */
	public static Over nTile (int buckets) {

		// MySQL は n をリテラルでしか受け取らない（? を置くと実行時に弾かれる）
		return new Over(new NamedFunction(SqlFunction.NTILE, new SqlLiteral(buckets)));

	}

	/**
	 * 前の行の値
	 *
	 * @param value	値
	 * @return	Over
	 */
	public static Over lag (Object value) {

		return new Over(new NamedFunction(SqlFunction.LAG, value));

	}

	/**
	 * n 行前の値
	 *
	 * @param value		値
	 * @param offset	何行前か
	 * @return	Over
	 */
	public static Over lag (Object value, int offset) {

		// MySQL は行数をリテラルでしか受け取らない
		return new Over(new NamedFunction(SqlFunction.LAG, value, new SqlLiteral(offset)));

	}

	/**
	 * 次の行の値
	 *
	 * @param value	値
	 * @return	Over
	 */
	public static Over lead (Object value) {

		return new Over(new NamedFunction(SqlFunction.LEAD, value));

	}

	/**
	 * n 行後の値
	 *
	 * @param value		値
	 * @param offset	何行後か
	 * @return	Over
	 */
	public static Over lead (Object value, int offset) {

		// MySQL は行数をリテラルでしか受け取らない
		return new Over(new NamedFunction(SqlFunction.LEAD, value, new SqlLiteral(offset)));

	}

	/**
	 * 範囲の最初の値
	 *
	 * @param value	値
	 * @return	Over
	 */
	public static Over firstValue (Object value) {

		return new Over(new NamedFunction(SqlFunction.FIRST_VALUE, value));

	}

	/**
	 * 範囲の最後の値
	 *
	 * @param value	値
	 * @return	Over
	 */
	public static Over lastValue (Object value) {

		return new Over(new NamedFunction(SqlFunction.LAST_VALUE, value));

	}

	/**
	 * {@link IDsl} にする
	 *
	 * <p>
	 * {@code Dsl.sum(...)} は {@link ISelect} を返すので、
	 * ウィンドウ関数の中身にするには中の {@link IDsl} が要る。
	 * </p>
	 *
	 * @param value	値
	 * @return	IDsl
	 */
	private static IDsl toDsl (Object value) {

		if (value instanceof IDsl dsl) {
			return dsl;
		}

		if (value instanceof SelectQuery query && query.dsl() != null) {

			/*
			 * SelectQuery は別名や四則演算も持てるが、ここでは中の関数しか使わない。
			 * <b>黙って落とすと `SUM(x) * 2` が `SUM(x)` になる</b>ので、はっきり断る。
			 */
			if (query.hasDecoration()) {
				throw new IllegalArgumentException(
					"ウィンドウ関数の中身に別名や計算は付けられません。OVER のあとに付けてください");
			}

			return query.dsl();

		}

		throw new IllegalArgumentException(
			"ウィンドウ関数の中身にできるのは関数だけです: " + (value == null ? "null" : value.getClass().getName()));

	}

	// endregion

}
