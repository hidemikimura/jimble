package bench;

import bench.Samples.SampleBean;
import bench.Samples.SampleRecord;

import com.sun.management.ThreadMXBean;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * JSON ライブラリの比較（1 回の起動で 1 つのライブラリだけ測る）
 *
 * <pre>
 * java ... bench.JsonCompare &lt;ライブラリ&gt; &lt;結果の TSV&gt;
 * </pre>
 *
 * <p>
 * <b>1 つの JVM で 1 つのライブラリしか動かさない。</b>同じ JVM で続けて測ると、
 * 前に測ったライブラリの JIT の状態や GC の状態が、あとのライブラリに乗る。
 * くり返しと順番の入れ替えは run.sh がやる。
 * </p>
 *
 * <h2>測り方</h2>
 * <ul>
 *   <li>項目ごとに {@code bench.warmupMs}（既定 1,000ms）空回しして、その回数から 1 ラウンドの回数を決める</li>
 *   <li>1 ラウンド（約 {@code warmupMs / 5}）を 5 回測り、<b>その中央値</b>を 1 回あたりの時間にする</li>
 *   <li>割り当ては {@code ThreadMXBean#getThreadAllocatedBytes} の実測（呼んだスレッドのぶんだけ）</li>
 * </ul>
 *
 * <p>
 * 結果は TSV（項目・ナノ秒・byte・正しさ）で書く。正しさは、結果を Jackson の木にして
 * 入力と同じかどうかで見る（{@code ok} / {@code NG} / {@code error}）。
 * </p>
 */
public final class JsonCompare {

	/** 割り当て量を返してくれる MXBean */
	private static final ThreadMXBean THREAD_MX = (ThreadMXBean) ManagementFactory.getThreadMXBean();

	/** 空回しの時間（ミリ秒） */
	private static final long WARMUP_MS = Long.getLong("bench.warmupMs", 1_000);

	/** 時間を測るラウンドの数（中央値を取る） */
	private static final int ROUNDS = 5;

	/** 割り当てを測る回数の上限 */
	private static final long MAX_ALLOCATION_RUNS = 20_000;

	/** 空回しで一度に回す回数（時計を見る回数を減らす） */
	private static final int BATCH = 8;

	/** ナノ秒とミリ秒の比 */
	private static final long NANOS_PER_MILLI = 1_000_000L;

	/** 数値は値で比べる（1000.56 を Double で持つか BigDecimal で持つかは問わない） */
	private static final Comparator<JsonNode> NUMBERS_AS_DECIMALS = (a, b) -> {

		if (a.equals(b)) {
			return 0;
		}

		if (a.isNumber() && b.isNumber()) {
			return a.decimalValue().compareTo(b.decimalValue());
		}

		return 1;

	};

	/*
	 * 測ったものの置き場。<b>読まない。</b>
	 * 使われていないと判定されると、JIT が処理そのものを消してしまう
	 */
	private static volatile Object keep;

	/** 結果の行 */
	private final List<String> rows = new ArrayList<>();

	/** 測るライブラリ */
	private final JsonLib lib;

	private JsonCompare (JsonLib lib) {
		this.lib = lib;
	}

	/**
	 * 入口
	 *
	 * @param args	ライブラリの名前、結果の TSV
	 * @throws IOException 結果を書けなかったとき
	 */
	public static void main (String[] args) throws IOException {

		if (args.length != 2) {
			System.err.println("使い方: JsonCompare <jimble|fastjson2|wast|fory|jackson> <結果の TSV>");
			System.exit(2);
		}

		JsonCompare compare = new JsonCompare(JsonLib.of(args[0]));
		compare.run();

		Path out = Path.of(args[1]);
		Files.createDirectories(out.toAbsolutePath().getParent());
		Files.write(out, compare.rows, StandardCharsets.UTF_8);

	}

	/**
	 * 全項目を測る
	 */
	private void run () {

		Map<String, String> inputs = new LinkedHashMap<>();
		inputs.put("small", Samples.small());
		inputs.put("medium", Samples.medium());
		inputs.put("large", Samples.large());
		inputs.put("numbers", Samples.numbers());
		inputs.put("strings", Samples.strings());

		// 書く側の入力は全ライブラリ共通：Jackson で読んだ LinkedHashMap / ArrayList / Bean / record
		Map<String, Object> trees = new LinkedHashMap<>();
		inputs.forEach((key, json) -> trees.put(key, JsonLib.JACKSON.readValue(json, Object.class)));

		Map<String, List<SampleBean>> beans = new LinkedHashMap<>();
		Map<String, List<SampleRecord>> records = new LinkedHashMap<>();

		for (String key : List.of("medium", "large")) {
			beans.put(key, JsonLib.JACKSON.readValue(inputs.get(key), new TypeReference<List<SampleBean>>() { }));
			records.put(key, JsonLib.JACKSON.readValue(inputs.get(key), new TypeReference<List<SampleRecord>>() { }));
		}

		SampleBean oneBean = JsonLib.JACKSON.readValue(inputs.get("small"), SampleBean.class);
		SampleRecord oneRecord = JsonLib.JACKSON.readValue(inputs.get("small"), SampleRecord.class);

		// 木（Map / List）
		inputs.forEach((key, json) -> measure("read tree " + key, json, () -> lib.readTree(json)));

		for (String key : List.of("medium", "large", "strings")) {
			byte[] bytes = inputs.get(key).getBytes(StandardCharsets.UTF_8);
			measure("read tree bytes " + key, inputs.get(key), () -> lib.readTreeBytes(bytes));
		}

		trees.forEach((key, tree) -> measure("write tree " + key, inputs.get(key), () -> lib.write(tree)));

		for (String key : List.of("medium", "large", "strings")) {
			Object tree = trees.get(key);
			measure("write tree bytes " + key, inputs.get(key), () -> lib.writeBytes(tree));
		}

		// Bean / record
		String small = inputs.get("small");
		measure("read bean small", small, () -> lib.readObject(small, SampleBean.class));
		measure("read record small", small, () -> lib.readObject(small, SampleRecord.class));

		for (String key : List.of("medium", "large")) {
			String json = inputs.get(key);
			measure("read bean list " + key, json, () -> lib.readList(json, SampleBean.class));
			measure("read record list " + key, json, () -> lib.readList(json, SampleRecord.class));
		}

		measure("write bean small", small, () -> lib.write(oneBean));
		measure("write record small", small, () -> lib.write(oneRecord));

		for (String key : List.of("medium", "large")) {
			List<SampleBean> beanList = beans.get(key);
			List<SampleRecord> recordList = records.get(key);
			measure("write bean list " + key, inputs.get(key), () -> lib.write(beanList));
			measure("write record list " + key, inputs.get(key), () -> lib.write(recordList));
		}

		List<SampleBean> largeBeans = beans.get("large");
		measure("write bean list bytes large", inputs.get("large"), () -> lib.writeBytes(largeBeans));

	}

	/**
	 * 1 項目を測って行を足す
	 *
	 * @param name		項目の名前
	 * @param expected	正しい結果（JSON）
	 * @param op		測るもの
	 */
	private void measure (String name, String expected, Supplier<Object> op) {

		Object first;

		try {
			first = op.get();
		} catch (RuntimeException ex) {
			// 読めない・書けないライブラリもある（表には NA と出す）
			System.out.printf("%-30s 失敗: %s%n", name, ex);
			rows.add(String.join("\t", name, "NA", "NA", "error"));
			return;
		}

		String correctness = sameAs(first, expected) ? "ok" : "NG";

		long perRound = warmup(op);
		long bytes = allocated(op, Math.min(perRound, MAX_ALLOCATION_RUNS));
		double nanos = medianNanos(op, perRound);

		System.out.printf("%-30s %14.1f ns %12d byte  %s%n", name, nanos, bytes, correctness);
		rows.add(String.join("\t", name, "%.1f".formatted(nanos), Long.toString(bytes), correctness));

	}

	/**
	 * 空回しして、1 ラウンドに回す回数を返す
	 *
	 * @param op	回すもの
	 * @return 1 ラウンドの回数
	 */
	private static long warmup (Supplier<Object> op) {

		long end = System.nanoTime() + WARMUP_MS * NANOS_PER_MILLI;
		long count = 0;

		while (System.nanoTime() < end) {
			for (int i = 0; i < BATCH; i++) {
				keep = op.get();
			}
			count += BATCH;
		}

		return Math.max(BATCH, count / ROUNDS);

	}

	/**
	 * 1 回あたりの割り当て byte 数
	 *
	 * @param op	回すもの
	 * @param count	回す回数
	 * @return byte 数
	 */
	private static long allocated (Supplier<Object> op, long count) {

		long id = Thread.currentThread().threadId();
		long start = THREAD_MX.getThreadAllocatedBytes(id);

		for (long i = 0; i < count; i++) {
			keep = op.get();
		}

		return (THREAD_MX.getThreadAllocatedBytes(id) - start) / count;

	}

	/**
	 * {@link #ROUNDS} ラウンド測った、1 回あたりのナノ秒の中央値
	 *
	 * @param op		回すもの
	 * @param perRound	1 ラウンドの回数
	 * @return ナノ秒
	 */
	private static double medianNanos (Supplier<Object> op, long perRound) {

		double[] rounds = new double[ROUNDS];

		for (int r = 0; r < ROUNDS; r++) {

			long start = System.nanoTime();

			for (long i = 0; i < perRound; i++) {
				keep = op.get();
			}

			rounds[r] = (System.nanoTime() - start) / (double) perRound;

		}

		Arrays.sort(rounds);

		return rounds[ROUNDS / 2];

	}

	/**
	 * 結果が入力と同じ中身か（Jackson の木にして比べる）
	 *
	 * <p>
	 * 読んだ結果は Jackson で書き直してから、書いた結果（文字列・byte[]）はそのまま、木にする。
	 * 数値の型の違い（Integer と Long、Double と BigDecimal）は同じとみなす。
	 * </p>
	 *
	 * @param result	ライブラリが返したもの
	 * @param expected	正しい結果（JSON）
	 * @return 同じなら true
	 */
	private static boolean sameAs (Object result, String expected) {

		try {

			String json = switch (result) {
				case String text -> text;
				case byte[] bytes -> new String(bytes, StandardCharsets.UTF_8);
				default -> JsonLib.JACKSON.writeValueAsString(result);
			};

			JsonNode actual = JsonLib.JACKSON.readTree(json);
			JsonNode wanted = JsonLib.JACKSON.readTree(expected);

			return actual.equals(NUMBERS_AS_DECIMALS, wanted);

		} catch (RuntimeException ex) {
			System.out.println("  （正しさを確かめられませんでした: " + ex + "）");
			return false;
		}

	}

}
