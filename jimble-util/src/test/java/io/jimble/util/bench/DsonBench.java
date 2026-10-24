package io.jimble.util.bench;

import io.jimble.util.bench.Budget.Runs;
import io.jimble.util.bench.JsonSamples.SampleBean;
import io.jimble.util.bench.JsonSamples.SampleRecord;
import io.jimble.util.data.Data;
import io.jimble.util.json.Dson;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * JSON の読み書き（{@link Dson}）の費用
 *
 * <pre>
 * ./gradlew :jimble-util:bench
 * </pre>
 *
 * <h2>何のためにあるか</h2>
 * <p>
 * <b>Dson を作り直す前と後を、同じもので比べるため。</b>
 * 測った値は {@code jimble-util/build/bench/bench.txt} に出る（時間は記録だけ）。
 * 外部のライブラリ（fastjson2・wast・Apache Fory JSON・Jackson）との比べは
 * {@code benchmarks/json-compare/} にある。入力はそちらと同じ（{@link JsonSamples}）。
 * </p>
 *
 * <h2>上限（byte）の決め方</h2>
 * <p>
 * <b>作り直す前の実測（2026-10-24、Apple M5・Java 25）に 2 割ほど足した値である。</b>
 * 作り直しで大きく減る見込みなので、今は「今より増えていない」ことだけを見ている。
 * 減ったら、そのときの実測に合わせて下げること（下げないと、次に増えても気づけない）。
 * </p>
 */
@Tag("bench")
class DsonBench {

	/** 小さいオブジェクト1つ */
	private static final String SMALL = JsonSamples.small();

	/** 100 件の配列 */
	private static final String MEDIUM = JsonSamples.medium();

	/** 数値の多い配列 */
	private static final String NUMBERS = JsonSamples.numbers();

	/** 日本語とエスケープの多い配列 */
	private static final String STRINGS = JsonSamples.strings();

	/** OutputStream に書くときの出力先（使い回す。中身の割り当てを測りたくないので） */
	private static final int STREAM_CAPACITY = 1 << 20;

	/** 書く側の入力（Dson で読んだもの。アプリが持っているのはこの形なので） */
	private static Data smallData;
	private static List<?> mediumList;
	private static List<?> numbersList;
	private static List<?> stringsList;
	private static SampleBean smallBean;
	private static SampleRecord smallRecord;
	private static List<SampleBean> mediumBeans;
	private static List<SampleRecord> mediumRecords;

	@BeforeAll
	static void prepare () {

		smallData = Dson.decodes(SMALL, Data.class);
		mediumList = Dson.decodes(MEDIUM, List.class);
		numbersList = Dson.decodes(NUMBERS, List.class);
		stringsList = Dson.decodes(STRINGS, List.class);
		smallBean = Dson.decodes(SMALL, SampleBean.class);
		smallRecord = Dson.decodes(SMALL, SampleRecord.class);
		mediumBeans = Dson.decodes(MEDIUM, List.class, SampleBean.class);
		mediumRecords = Dson.decodes(MEDIUM, List.class, SampleRecord.class);

		/*
		 * <b>読めていることを先に確かめる。</b>読めずに空のまま測ると、
		 * 速くて安い（が意味の無い）数字が出る
		 */
		assertEquals(JsonSamples.MEDIUM_COUNT, mediumList.size());
		assertEquals(JsonSamples.NUMBERS_COUNT, numbersList.size());
		assertEquals(JsonSamples.STRINGS_COUNT, stringsList.size());
		assertEquals(JsonSamples.MEDIUM_COUNT, mediumBeans.size());
		assertEquals(JsonSamples.MEDIUM_COUNT, mediumRecords.size());
		assertEquals("user1", smallBean.getName());
		assertEquals("user1", smallRecord.name());
		assertEquals(SampleRecord.class, mediumRecords.getFirst().getClass());

	}

	@AfterAll
	static void report () {

		Bench.report();

	}

	@Test
	@DisplayName("文字列 → Data / List の割り当てが上限を超えない")
	void readTree () {

		Budget budget = new Budget();

		budget.measure("Dson 読む 小（10キー）→ Data", Runs.SMALL, 5_120
			, () -> Dson.decodes(SMALL, Data.class));
		budget.measure("Dson 読む 中（100件）→ List", Runs.MEDIUM, 640_000
			, () -> Dson.decodes(MEDIUM, List.class));
		budget.measure("Dson 読む 数値（1,000件）→ List", Runs.LARGE, 7_680_000
			, () -> Dson.decodes(NUMBERS, List.class));
		budget.measure("Dson 読む 日本語とエスケープ（100件）→ List", Runs.MEDIUM, 1_100_000
			, () -> Dson.decodes(STRINGS, List.class));

		budget.assertWithin();

	}

	@Test
	@DisplayName("Data / List → 文字列・OutputStream の割り当てが上限を超えない")
	void writeTree () {

		Budget budget = new Budget();
		ByteArrayOutputStream stream = new ByteArrayOutputStream(STREAM_CAPACITY);

		budget.measure("Dson 書く 小（10キー）Data → 文字列", Runs.SMALL, 4_096
			, () -> Dson.encodes(smallData));
		budget.measure("Dson 書く 中（100件）List → 文字列", Runs.MEDIUM, 155_648
			, () -> Dson.encodes(mediumList));
		budget.measure("Dson 書く 数値（1,000件）List → 文字列", Runs.LARGE, 1_769_472
			, () -> Dson.encodes(numbersList));
		budget.measure("Dson 書く 日本語とエスケープ（100件）List → 文字列", Runs.MEDIUM, 483_328
			, () -> Dson.encodes(stringsList));

		budget.measure("Dson 書く 小（10キー）Data → OutputStream", Runs.SMALL, 5_120
			, () -> write(smallData, stream));
		budget.measure("Dson 書く 中（100件）List → OutputStream", Runs.MEDIUM, 65_536
			, () -> write(mediumList, stream));
		budget.measure("Dson 書く 日本語とエスケープ（100件）List → OutputStream", Runs.MEDIUM, 24_576
			, () -> write(stringsList, stream));

		budget.assertWithin();

	}

	@Test
	@DisplayName("Bean / record の読み書きの割り当てが上限を超えない")
	void beanAndRecord () {

		Budget budget = new Budget();

		budget.measure("Dson 読む 小 → Bean", Runs.SMALL, 6_400
			, () -> Dson.decodes(SMALL, SampleBean.class));
		budget.measure("Dson 読む 小 → record", Runs.SMALL, 8_192
			, () -> Dson.decodes(SMALL, SampleRecord.class));

		/*
		 * <b>List で読むと、今は木を2回作っている</b>（Data の木を作ってから、それを Bean に移す）。
		 * 作り直しでいちばん減るはずの項目
		 */
		budget.measure("Dson 読む 中（100件）→ List<Bean>", Runs.MEDIUM, 600_000
			, () -> Dson.decodes(MEDIUM, List.class, SampleBean.class));
		budget.measure("Dson 読む 中（100件）→ List<record>", Runs.MEDIUM, 800_000
			, () -> Dson.decodes(MEDIUM, List.class, SampleRecord.class));

		budget.measure("Dson 書く 小 Bean → 文字列", Runs.SMALL, 4_096
			, () -> Dson.encodes(smallBean));
		budget.measure("Dson 書く 小 record → 文字列", Runs.SMALL, 4_096
			, () -> Dson.encodes(smallRecord));
		budget.measure("Dson 書く 中（100件）List<Bean> → 文字列", Runs.MEDIUM, 155_648
			, () -> Dson.encodes(mediumBeans));
		budget.measure("Dson 書く 中（100件）List<record> → 文字列", Runs.MEDIUM, 155_648
			, () -> Dson.encodes(mediumRecords));

		budget.assertWithin();

	}

	/**
	 * OutputStream に書く（出力先は使い回す）
	 *
	 * @param value		書くもの
	 * @param stream	出力先
	 * @return 出力先
	 */
	private static ByteArrayOutputStream write (Object value, ByteArrayOutputStream stream) {

		stream.reset();
		Dson.encodes(value, stream, StandardCharsets.UTF_8.name());

		return stream;

	}

}
