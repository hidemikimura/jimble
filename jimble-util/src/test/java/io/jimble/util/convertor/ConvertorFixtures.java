package io.jimble.util.convertor;

import io.jimble.util.data.Data;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * 型変換の characterization test で共通に使う型と入口
 *
 * <p>
 * 公開の入口（{@link Convertor#convert(Configration, Object, Class...)}）だけを通す。
 * 日付を読むテストは、時間帯を {@link #ZONE} に固定してから動かす（{@link #fixZone()} / {@link #restoreZone()}）。
 * </p>
 */
final class ConvertorFixtures {

	/** 日付のテストで固定する時間帯 */
	static final String ZONE = "Asia/Tokyo";

	/** 固定する前の時間帯 */
	private static TimeZone original;

	private ConvertorFixtures () {
	}

	/** 設定なし（null）で変換する。2つの形のうち、設定を渡す形も null を渡したのと同じに動く */
	static <T> T conv (Object src, Class<?>... destClasses) {

		return Convertor.convert(null, src, destClasses);

	}

	/** 時間帯を固定する */
	static void fixZone () {

		original = TimeZone.getDefault();
		TimeZone.setDefault(TimeZone.getTimeZone(ZONE));

	}

	/** 時間帯を戻す */
	static void restoreZone () {

		if (original != null) {
			TimeZone.setDefault(original);
		}

	}

	/** Data を組み立てる（キーと値を交互に） */
	static Data data (Object... keyValues) {

		Data res = new Data();
		for (int i = 0; i < keyValues.length; i += 2) {
			res.put((String) keyValues[i], keyValues[i + 1]);
		}
		return res;

	}

	/** 状態 */
	enum Status { draft, published }

	/** getter だけを持つ Bean（値は private の項目に書かれる） */
	public static class Member {

		private String name;
		private int age;
		private Status status;
		private List<Item> items;
		private Date joinedAt;

		public String getName () { return name; }
		public int getAge () { return age; }
		public Status getStatus () { return status; }
		public List<Item> getItems () { return items; }
		public Date getJoinedAt () { return joinedAt; }

	}

	/** public の項目だけの Bean */
	public static class Item {

		public String code;
		public int qty;

	}

	/** いろいろな型の項目を持つ Bean */
	public static class Holder {

		public List<LineItem> lines;
		public Map<String, Integer> counts;
		public Set<String> tags;
		public int[] nums;
		public Data extra;
		public List<Data> rows;

	}

	/** setter しか無い項目（値を受ける private の項目は名前が違う） */
	public static class SetterOnly {

		private String hidden;

		public void setLabel (String label) { this.hidden = label; }
		public String hidden () { return hidden; }

	}

	/** 親クラス */
	public static class Parent {

		private String parentName;
		public String parentPublic;

		public String parentName () { return parentName; }

	}

	/** 子クラス */
	public static class Child extends Parent {

		private String childName;

		public String childName () { return childName; }

	}

	/** 自分を指せる Bean */
	public static class Node {

		public String name;
		public Node next;

	}

	/** getter と setter の組を持つ Bean */
	public static class Pair {

		private String left;
		public int right;
		public static String SHARED = "shared";

		public String getLeft () { return left; }
		public void setLeft (String left) { this.left = left; }

	}

	/** 明細の record */
	public record LineItem(String code, int qty) {}

	/** 一覧を持つ record */
	public record Order(String name, int count, Integer boxed, List<LineItem> lines) {}

	/** 値を検査する record（"bad" を渡すと例外） */
	public record Checked(String name) {

		public Checked {
			if ("bad".equals(name)) {
				throw new IllegalArgumentException("bad");
			}
		}

	}

}
