package bench;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

/**
 * ベンチマークの入力（JSON の文字列と、読み書きする Bean / record）
 *
 * <p>
 * <b>jimble-util のベンチマーク（{@code io.jimble.util.bench.JsonSamples}）の写しで、同じ入力である。</b>
 * 片方だけ変えると、jimble-util の数字とこちらの表が噛み合わなくなるので、変えるときは両方を直す。
 * こちらにだけ {@link #large()}（4,800 件）がある。
 * </p>
 *
 * <ul>
 *   <li>{@link #small()} — 10 キーのオブジェクト1つ（約 230 文字）</li>
 *   <li>{@link #medium()} — それを 100 件並べた配列（約 23KB）</li>
 *   <li>{@link #large()} — それを 4,800 件並べた配列（約 1.1MB）</li>
 *   <li>{@link #numbers()} — 数値だけの 10 キー × 1,000 件（整数・負数・小数・指数表記が混ざる）</li>
 *   <li>{@link #strings()} — 日本語とエスケープ（{@code \n} {@code \"} {@code \\} {@code \t}・バックスラッシュ u の文字参照）の多い 10 キー × 100 件</li>
 * </ul>
 */
public final class Samples {

	/** 中くらいの配列の件数 */
	static final int MEDIUM_COUNT = 100;

	/** 大きい配列の件数 */
	static final int LARGE_COUNT = 4_800;

	/** 数値の多い配列の件数 */
	static final int NUMBERS_COUNT = 1_000;

	/** 文字列の多い配列の件数 */
	static final int STRINGS_COUNT = 100;

	/** 1件あたりのキーの数（数値・文字列の配列） */
	private static final int KEYS = 10;

	/** 乱数の種（毎回同じ入力にする） */
	private static final long SEED = 1;

	private Samples () {
	}

	/**
	 * 読み書きする record（{@link SampleBean} と同じ項目）
	 *
	 * @param id		ID
	 * @param name		名前
	 * @param email		メール
	 * @param age		年齢
	 * @param score		点数
	 * @param active	有効か
	 * @param created	作成日時（文字列のまま）
	 * @param tags		タグ
	 * @param price		価格
	 * @param note		メモ
	 */
	public record SampleRecord (long id, String name, String email, int age, double score, boolean active
		, String created, List<String> tags, BigDecimal price, String note) {
	}

	/**
	 * 読み書きする Bean（getter / setter を持つ普通のクラス）
	 */
	public static final class SampleBean {

		private long id;
		private String name;
		private String email;
		private int age;
		private double score;
		private boolean active;
		private String created;
		private List<String> tags;
		private BigDecimal price;
		private String note;

		public long getId () { return id; }
		public void setId (long id) { this.id = id; }
		public String getName () { return name; }
		public void setName (String name) { this.name = name; }
		public String getEmail () { return email; }
		public void setEmail (String email) { this.email = email; }
		public int getAge () { return age; }
		public void setAge (int age) { this.age = age; }
		public double getScore () { return score; }
		public void setScore (double score) { this.score = score; }
		public boolean isActive () { return active; }
		public void setActive (boolean active) { this.active = active; }
		public String getCreated () { return created; }
		public void setCreated (String created) { this.created = created; }
		public List<String> getTags () { return tags; }
		public void setTags (List<String> tags) { this.tags = tags; }
		public BigDecimal getPrice () { return price; }
		public void setPrice (BigDecimal price) { this.price = price; }
		public String getNote () { return note; }
		public void setNote (String note) { this.note = note; }

	}

	/**
	 * 10 キーのオブジェクト1つ
	 *
	 * @return JSON
	 */
	static String small () {

		return object(1);

	}

	/**
	 * {@link #small()} と同じ形を {@link #MEDIUM_COUNT} 件並べた配列
	 *
	 * @return JSON
	 */
	static String medium () {

		return array(MEDIUM_COUNT);

	}

	/**
	 * {@link #small()} と同じ形を {@link #LARGE_COUNT} 件並べた配列
	 *
	 * @return JSON
	 */
	static String large () {

		return array(LARGE_COUNT);

	}

	/**
	 * {@link #small()} と同じ形を {@code count} 件並べた配列
	 *
	 * @param count	件数
	 * @return JSON
	 */
	static String array (int count) {

		StringBuilder json = new StringBuilder("[");

		for (int i = 0; i < count; i++) {
			if (i > 0) {
				json.append(',');
			}
			json.append(object(i));
		}

		return json.append(']').toString();

	}

	/**
	 * 数値の多い配列
	 *
	 * @return JSON
	 */
	static String numbers () {

		Random random = new Random(SEED);
		StringBuilder json = new StringBuilder("[");

		for (int i = 0; i < NUMBERS_COUNT; i++) {

			if (i > 0) {
				json.append(',');
			}

			json.append("{\"a\":").append(random.nextInt(100_000))
				.append(",\"b\":").append(random.nextLong())
				.append(",\"c\":").append(random.nextDouble() * 1000)
				.append(",\"d\":").append(random.nextInt(10))
				.append(",\"e\":").append(-random.nextInt(1_000_000))
				.append(",\"f\":").append(random.nextDouble())
				.append(",\"g\":").append(random.nextInt())
				.append(",\"h\":").append(random.nextInt(100)).append(".25")
				.append(",\"i\":").append(random.nextLong() / 1000)
				.append(",\"j\":").append(random.nextFloat())
				.append('}');

		}

		return json.append(']').toString();

	}

	/**
	 * 日本語とエスケープの多い配列
	 *
	 * @return JSON
	 */
	static String strings () {

		StringBuilder json = new StringBuilder("[");

		for (int i = 0; i < STRINGS_COUNT; i++) {

			if (i > 0) {
				json.append(',');
			}

			json.append('{');

			for (int k = 0; k < KEYS; k++) {
				if (k > 0) {
					json.append(',');
				}
				json.append("\"k").append(k).append("\":\"日本語の文字列です「テスト」").append(i)
					.append("\\n改行と\\\"引用\\\"と\\\\バックスラッシュ\\t タブ \\u00e9\\u3042 混在 text ").append(k)
					.append('"');
			}

			json.append('}');

		}

		return json.append(']').toString();

	}

	/**
	 * 10 キーのオブジェクト（数値・文字列・真偽・配列・小数が混ざる）
	 *
	 * @param i	何件目か
	 * @return JSON
	 */
	private static String object (int i) {

		return "{\"id\":" + (100_000L + i)
			+ ",\"name\":\"user" + i + "\""
			+ ",\"email\":\"user" + i + "@example.com\""
			+ ",\"age\":" + (20 + i % 50)
			+ ",\"score\":" + (i * 1.25 + 0.5)
			+ ",\"active\":" + (i % 2 == 0)
			+ ",\"created\":\"2026-10-24T10:00:00\""
			+ ",\"tags\":[\"a\",\"b\",\"c\"]"
			+ ",\"price\":" + (1000 + i) + ".56"
			+ ",\"note\":\"hello world " + i + "\"}";

	}

}
