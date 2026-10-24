package io.jimble.util.bench;

import io.jimble.util.bench.Budget.Runs;
import io.jimble.util.bench.JsonSamples.SampleBean;
import io.jimble.util.bench.JsonSamples.SampleRecord;
import io.jimble.util.convertor.Convertor;
import io.jimble.util.data.Data;
import io.jimble.util.json.Dson;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 型変換（{@link Convertor}）と {@link Data} の getter の費用
 *
 * <pre>
 * ./gradlew :jimble-util:bench
 * </pre>
 *
 * <p>
 * <b>Convertor を作り直す前と後を、同じもので比べるため。</b>
 * フォームの値・DB の値・JSON の値は、どれもここを通って Bean / record / 数値になる。
 * 測った値は {@code jimble-util/build/bench/bench.txt} に出る（時間は記録だけ）。
 * </p>
 *
 * <p>
 * 上限（byte）は、作り直す前の実測（2026-10-24、Apple M5・Java 25）に 2 割ほど足した値である。
 * 減ったら、そのときの実測に合わせて下げること（{@link DsonBench} と同じ）。
 * </p>
 */
@Tag("bench")
class ConvertorBench {

	/** 数値の文字列（整数） */
	private static final String INTEGER_TEXT = "12345";

	/** 数値の文字列（long の幅） */
	private static final String LONG_TEXT = "1234567890123";

	/** 数値の文字列（小数） */
	private static final String DECIMAL_TEXT = "1234.5678";

	/** 日時の文字列（Date へ） */
	private static final String DATE_TEXT = "2026-10-24 10:00:00";

	/** 日時の文字列（Instant へ） */
	private static final String INSTANT_TEXT = "2026-10-24T10:00:00Z";

	/** JSON から読んだ Data（値は Integer / Double / String / List） */
	private static Data json;

	/** JSON から読んだ 100 件 */
	private static List<?> jsonList;

	/** フォームから来たような Data（値はすべて文字列） */
	private static Data form;

	@BeforeAll
	static void prepare () {

		json = Dson.decodes(JsonSamples.small(), Data.class);
		jsonList = Dson.decodes(JsonSamples.medium(), List.class);

		form = new Data()
			.putData("i", "123")
			.putData("l", "1234567890123")
			.putData("d", "12.5")
			.putData("s", "abc")
			.putData("b", "1234.56");

		// 変換できていることを先に確かめる（できずに null で測ると、意味の無い数字が出る）
		SampleBean bean = Convertor.convert(null, json, SampleBean.class);
		SampleRecord record = Convertor.convert(null, json, SampleRecord.class);
		List<?> beans = Convertor.convert(null, jsonList, List.class, SampleBean.class);

		assertEquals("user1", bean.getName());
		assertEquals("user1", record.name());
		assertEquals(JsonSamples.MEDIUM_COUNT, beans.size());
		assertEquals(SampleBean.class, beans.getFirst().getClass());
		assertEquals(Integer.valueOf(12345), Convertor.convert(null, INTEGER_TEXT, Integer.class));
		assertEquals(new BigDecimal(DECIMAL_TEXT), Convertor.convert(null, DECIMAL_TEXT, BigDecimal.class));
		/*
		 * <b>値までは見ない。</b>2026-10-24 時点の Convertor は末尾の Z（UTC）を読まずに
		 * 既定のタイムゾーンの時刻として読む（JST だと 9 時間ずれる）。
		 * 正しさはテストの側で見るもので、ここでは「変換できた」ことだけを見る
		 */
		assertNotNull(Convertor.convert(null, INSTANT_TEXT, Instant.class));

	}

	@AfterAll
	static void report () {

		Bench.report();

	}

	@Test
	@DisplayName("Data → Bean / record の割り当てが上限を超えない")
	void toBean () {

		Budget budget = new Budget();

		budget.measure("Convertor 小 Data → Bean", Runs.SMALL, 1_408
			, () -> Convertor.convert(null, json, SampleBean.class));
		budget.measure("Convertor 小 Data → record", Runs.SMALL, 3_328
			, () -> Convertor.convert(null, json, SampleRecord.class));
		budget.measure("Convertor 中（100件）List<Data> → List<Bean>", Runs.MEDIUM, 102_400
			, () -> Convertor.convert(null, jsonList, List.class, SampleBean.class));

		budget.assertWithin();

	}

	@Test
	@DisplayName("文字列 → 数値・日時の割り当てが上限を超えない")
	void fromString () {

		Budget budget = new Budget();

		budget.measure("Convertor \"12345\" → Integer", Runs.SMALL, 1_024
			, () -> Convertor.convert(null, INTEGER_TEXT, Integer.class));
		budget.measure("Convertor \"1234567890123\" → Long", Runs.SMALL, 1_024
			, () -> Convertor.convert(null, LONG_TEXT, Long.class));
		budget.measure("Convertor \"1234.5678\" → Double", Runs.SMALL, 384
			, () -> Convertor.convert(null, DECIMAL_TEXT, Double.class));
		budget.measure("Convertor \"1234.5678\" → BigDecimal", Runs.SMALL, 384
			, () -> Convertor.convert(null, DECIMAL_TEXT, BigDecimal.class));
		budget.measure("Convertor \"2026-10-24 10:00:00\" → Date", Runs.SMALL, 76_800
			, () -> Convertor.convert(null, DATE_TEXT, Date.class));
		budget.measure("Convertor \"2026-10-24T10:00:00Z\" → Instant", Runs.SMALL, 10_240
			, () -> Convertor.convert(null, INSTANT_TEXT, Instant.class));

		// 比べるための基準（JDK だけで変換したとき。上限は見ない）
		Bench.record("（基準）Integer.valueOf(\"12345\")", Runs.SMALL.warmup, Runs.SMALL.measure
			, () -> Integer.valueOf(INTEGER_TEXT));
		Bench.record("（基準）new BigDecimal(\"1234.5678\")", Runs.SMALL.warmup, Runs.SMALL.measure
			, () -> new BigDecimal(DECIMAL_TEXT));
		Bench.record("（基準）Instant.parse(\"2026-10-24T10:00:00Z\")", Runs.SMALL.warmup, Runs.SMALL.measure
			, () -> Instant.parse(INSTANT_TEXT));

		budget.assertWithin();

	}

	@Test
	@DisplayName("Data の getter の割り当てが上限を超えない")
	void getter () {

		Budget budget = new Budget();

		budget.measure("Data getter ×10（JSON から読んだ値）", Runs.SMALL, 384
			, ConvertorBench::readJson);
		budget.measure("Data getter ×5（文字列の値。フォームから来た形）", Runs.SMALL, 448
			, ConvertorBench::readForm);
		budget.measure("Data getInt ×1（Integer の値）", Runs.SMALL, 64
			, () -> json.getInt("age"));
		budget.measure("Data getString ×1（String の値）", Runs.SMALL, 64
			, () -> json.getString("name"));
		budget.measure("Data getString ×1（Integer の値）", Runs.SMALL, 320
			, () -> json.getString("id"));

		budget.assertWithin();

	}

	/**
	 * JSON から読んだ Data の値を 10 回取り出す
	 *
	 * @return 取り出した値から作った数（JIT に消されないため）
	 */
	private static long readJson () {

		return json.getInt("age") + json.getLong("id") + (long) json.getDouble("score")
			+ json.getString("name").length() + json.getString("email").length()
			+ json.getBigDecimal("price").scale()
			+ json.getInt("age") + json.getLong("id")
			+ json.getString("note").length() + (long) json.getDouble("score");

	}

	/**
	 * 文字列の値を持つ Data から 5 回取り出す
	 *
	 * @return 取り出した値から作った数（JIT に消されないため）
	 */
	private static long readForm () {

		return form.getInt("i") + form.getLong("l") + (long) form.getDouble("d")
			+ form.getString("s").length() + form.getBigDecimal("b").scale();

	}

}
