package io.jimble.json;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * JSON の書き方（D-307）
 *
 * <p>
 * <b>変えられない値である。</b>1つ作って使い回してよい（スレッドをまたいでも）。
 * 変えるときは {@link #toBuilder()} で写しを作る。
 * </p>
 *
 * <h2>日時の書き方</h2>
 * <table>
 *   <caption>型ごとの既定の形</caption>
 *   <tr><th>型</th><th>形</th></tr>
 *   <tr><td>{@code Date}・{@code Timestamp}・{@code Calendar}・{@code LocalDateTime}・{@code Instant}・{@code OffsetDateTime}・{@code ZonedDateTime}</td>
 *       <td>{@code yyyy/MM/dd HH:mm:ss}（時差を持つものはサーバーの時間帯にそろえる）</td></tr>
 *   <tr><td>{@code LocalDate}・{@code java.sql.Date}</td><td>{@code yyyy/MM/dd}</td></tr>
 *   <tr><td>{@code LocalTime}・{@code java.sql.Time}</td><td>{@code HH:mm:ss}</td></tr>
 *   <tr><td>{@code Duration}・{@code Period}</td><td>ISO 8601（{@code PT1H30M}）</td></tr>
 * </table>
 * <p>
 * {@link Builder#keepOffset(boolean)} を true にすると、時差を持つもの（{@code Date}・{@code Calendar}・{@code Instant}・
 * {@code OffsetDateTime}・{@code ZonedDateTime}）を ISO 8601 の時差つき（{@code 2026-10-24T19:00:00+09:00}）で書く。
 * </p>
 */
public final class JsonWriteOptions {

	/** 入れ子の深さの既定の上限（読むときと同じ） */
	public static final int DEFAULT_MAX_DEPTH = JsonReadOptions.DEFAULT_MAX_DEPTH;

	/** 日時の既定の形 */
	public static final DateTimeFormatter DEFAULT_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

	/** 日付の既定の形 */
	public static final DateTimeFormatter DEFAULT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

	/** 時刻の既定の形 */
	public static final DateTimeFormatter DEFAULT_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

	/** 既定の書き方 */
	public static final JsonWriteOptions DEFAULT = builder().build();

	private final boolean indent;
	private final boolean writeNulls;
	private final boolean escapeSlash;
	private final boolean escapeHtml;
	private final boolean dropSupplementary;
	private final DateTimeFormatter dateTimeFormat;
	private final DateTimeFormatter dateFormat;
	private final DateTimeFormatter timeFormat;
	private final ZoneId zone;
	private final boolean keepOffset;
	private final String datePrefix;
	private final JsonCycle cycle;
	private final int maxDepth;
	private final List<TypeWriterEntry> typeWriters;

	private JsonWriteOptions (Builder builder) {

		this.indent = builder.indent;
		this.writeNulls = builder.writeNulls;
		this.escapeSlash = builder.escapeSlash;
		this.escapeHtml = builder.escapeHtml;
		this.dropSupplementary = builder.dropSupplementary;
		this.dateTimeFormat = builder.dateTimeFormat;
		this.dateFormat = builder.dateFormat;
		this.timeFormat = builder.timeFormat;
		this.zone = builder.zone;
		this.keepOffset = builder.keepOffset;
		this.datePrefix = builder.datePrefix;
		this.cycle = builder.cycle;
		this.maxDepth = builder.maxDepth;
		this.typeWriters = List.copyOf(builder.typeWriters);

	}

	/**
	 * 作り始める
	 *
	 * @return	既定の値から始まる組み立て役
	 */
	public static Builder builder () {

		return new Builder();

	}

	/**
	 * 写しを作り始める
	 *
	 * @return	この書き方の値から始まる組み立て役
	 */
	public Builder toBuilder () {

		Builder builder = new Builder();
		builder.indent = indent;
		builder.writeNulls = writeNulls;
		builder.escapeSlash = escapeSlash;
		builder.escapeHtml = escapeHtml;
		builder.dropSupplementary = dropSupplementary;
		builder.dateTimeFormat = dateTimeFormat;
		builder.dateFormat = dateFormat;
		builder.timeFormat = timeFormat;
		builder.zone = zone;
		builder.keepOffset = keepOffset;
		builder.datePrefix = datePrefix;
		builder.cycle = cycle;
		builder.maxDepth = maxDepth;
		builder.typeWriters.addAll(typeWriters);
		return builder;

	}

	/**
	 * 字下げするか
	 *
	 * @return	字下げするか
	 */
	public boolean indent () {
		return indent;
	}

	/**
	 * null の値の項目も書くか
	 *
	 * @return	null の値の項目も書くか
	 */
	public boolean writeNulls () {
		return writeNulls;
	}

	/**
	 * {@code /} を {@code \/} にするか
	 *
	 * @return	{@code /} を {@code \/} にするか
	 */
	public boolean escapeSlash () {
		return escapeSlash;
	}

	/**
	 * {@code <} {@code >} を {@code \u003C} {@code \u003E} にするか
	 *
	 * @return	{@code <} {@code >} を {@code \u003C} {@code \u003E} にするか
	 */
	public boolean escapeHtml () {
		return escapeHtml;
	}

	/**
	 * BMP の外の文字（絵文字など）を落とすか
	 *
	 * @return	BMP の外の文字（絵文字など）を落とすか
	 */
	public boolean dropSupplementary () {
		return dropSupplementary;
	}

	/**
	 * 日時の形
	 *
	 * @return	日時の形
	 */
	public DateTimeFormatter dateTimeFormat () {
		return dateTimeFormat;
	}

	/**
	 * 日付の形
	 *
	 * @return	日付の形
	 */
	public DateTimeFormatter dateFormat () {
		return dateFormat;
	}

	/**
	 * 時刻の形
	 *
	 * @return	時刻の形
	 */
	public DateTimeFormatter timeFormat () {
		return timeFormat;
	}

	/**
	 * 時間帯（null なら、書くときのサーバーの時間帯）
	 *
	 * @return	時間帯（null なら、書くときのサーバーの時間帯）
	 */
	public ZoneId zone () {
		return zone;
	}

	/**
	 * 時差を持つ日時を、時差つきの ISO 8601 で書くか
	 *
	 * @return	時差を持つ日時を、時差つきの ISO 8601 で書くか
	 */
	public boolean keepOffset () {
		return keepOffset;
	}

	/**
	 * 日時の前に付ける文字（null なら付けない）
	 *
	 * @return	日時の前に付ける文字（null なら付けない）
	 */
	public String datePrefix () {
		return datePrefix;
	}

	/**
	 * 循環参照を見つけたときの動き
	 *
	 * @return	循環参照を見つけたときの動き
	 */
	public JsonCycle cycle () {
		return cycle;
	}

	/**
	 * 入れ子の深さの上限
	 *
	 * @return	入れ子の深さの上限
	 */
	public int maxDepth () {
		return maxDepth;
	}

	/**
	 * 足した型ごとの書き方（足した順）
	 *
	 * @return	足した型ごとの書き方（足した順）
	 */
	public List<TypeWriterEntry> typeWriters () {
		return typeWriters;
	}

	/* 型ごとの書き方を、クラスごとに1回だけ引いて覚える（足した書き方が無ければ使わない） */
	private final ClassValue<JsonTypeWriter> typeWriterLookup = new ClassValue<>() {
		@Override
		protected JsonTypeWriter computeValue (Class<?> type) {
			for (TypeWriterEntry entry : typeWriters) {
				if (entry.type().isAssignableFrom(type)) {
					return entry.writer();
				}
			}
			return null;
		}
	};

	/**
	 * クラスに効く、足した書き方
	 *
	 * @param type	クラス
	 * @return	書き方（無ければ null）
	 */
	public JsonTypeWriter typeWriterFor (Class<?> type) {
		return typeWriters.isEmpty() ? null : typeWriterLookup.get(type);
	}

	/**
	 * 足した型ごとの書き方
	 *
	 * @param type		型（この型とその子に効く）
	 * @param writer	書き方
	 */
	public record TypeWriterEntry (Class<?> type, JsonTypeWriter writer) {
	}

	/**
	 * 書き方の組み立て役
	 */
	public static final class Builder {

		private boolean indent = false;
		private boolean writeNulls = true;
		private boolean escapeSlash = true;
		private boolean escapeHtml = false;
		private boolean dropSupplementary = false;
		private DateTimeFormatter dateTimeFormat = DEFAULT_DATE_TIME_FORMAT;
		private DateTimeFormatter dateFormat = DEFAULT_DATE_FORMAT;
		private DateTimeFormatter timeFormat = DEFAULT_TIME_FORMAT;
		private ZoneId zone = null;
		private boolean keepOffset = false;
		private String datePrefix = null;
		private JsonCycle cycle = JsonCycle.THROW;
		private int maxDepth = DEFAULT_MAX_DEPTH;
		private final List<TypeWriterEntry> typeWriters = new ArrayList<>();

		private Builder () {
		}

		/**
		 * 字下げするか（既定 false）。字下げはタブで、{@code :} の後ろに空白は入れない
		 *
		 * @param indent	する場合 = true
		 * @return	自身
		 */
		public Builder indent (boolean indent) {
			this.indent = indent;
			return this;
		}

		/**
		 * null の値の項目も書くか（既定 true）。配列の中の null はいつも書く
		 *
		 * @param writeNulls	書く場合 = true
		 * @return	自身
		 */
		public Builder writeNulls (boolean writeNulls) {
			this.writeNulls = writeNulls;
			return this;
		}

		/**
		 * {@code /} を {@code \/} にするか（既定 true。HTML の中に埋め込んでも {@code </script>} にならない）
		 *
		 * @param escapeSlash	する場合 = true
		 * @return	自身
		 */
		public Builder escapeSlash (boolean escapeSlash) {
			this.escapeSlash = escapeSlash;
			return this;
		}

		/**
		 * {@code <} {@code >} もエスケープするか（既定 false）
		 *
		 * @param escapeHtml	する場合 = true
		 * @return	自身
		 */
		public Builder escapeHtml (boolean escapeHtml) {
			this.escapeHtml = escapeHtml;
			return this;
		}

		/**
		 * BMP の外の文字（絵文字など）を落とすか（既定 false。4 バイトの文字を入れられない DB に渡すとき）
		 *
		 * @param dropSupplementary	落とす場合 = true
		 * @return	自身
		 */
		public Builder dropSupplementary (boolean dropSupplementary) {
			this.dropSupplementary = dropSupplementary;
			return this;
		}

		/**
		 * 日時の形（既定 {@code yyyy/MM/dd HH:mm:ss}）
		 *
		 * @param dateTimeFormat	形
		 * @return	自身
		 */
		public Builder dateTimeFormat (DateTimeFormatter dateTimeFormat) {
			this.dateTimeFormat = Objects.requireNonNull(dateTimeFormat, "dateTimeFormat");
			return this;
		}

		/**
		 * 日付の形（既定 {@code yyyy/MM/dd}）
		 *
		 * @param dateFormat	形
		 * @return	自身
		 */
		public Builder dateFormat (DateTimeFormatter dateFormat) {
			this.dateFormat = Objects.requireNonNull(dateFormat, "dateFormat");
			return this;
		}

		/**
		 * 時刻の形（既定 {@code HH:mm:ss}）
		 *
		 * @param timeFormat	形
		 * @return	自身
		 */
		public Builder timeFormat (DateTimeFormatter timeFormat) {
			this.timeFormat = Objects.requireNonNull(timeFormat, "timeFormat");
			return this;
		}

		/**
		 * 時間帯（既定 null = 書くときのサーバーの時間帯）
		 *
		 * @param zone	時間帯
		 * @return	自身
		 */
		public Builder zone (ZoneId zone) {
			this.zone = zone;
			return this;
		}

		/**
		 * 時差を持つ日時を、時差つきの ISO 8601 で書くか（既定 false）
		 *
		 * <p>
		 * true にすると {@code Date}・{@code Calendar}・{@code Instant}・{@code OffsetDateTime}・{@code ZonedDateTime} を
		 * {@code 2026-10-24T19:00:00+09:00} の形で書く（{@code Instant} は {@link #zone(ZoneId)} の時差で）。
		 * 時差を持たないもの（{@code LocalDateTime} など）は変わらない。
		 * </p>
		 *
		 * @param keepOffset	時差つきで書く場合 = true
		 * @return	自身
		 */
		public Builder keepOffset (boolean keepOffset) {
			this.keepOffset = keepOffset;
			return this;
		}

		/**
		 * 日時の前に付ける文字（既定 null = 付けない）
		 *
		 * @param datePrefix	付ける文字
		 * @return	自身
		 */
		public Builder datePrefix (String datePrefix) {
			this.datePrefix = datePrefix;
			return this;
		}

		/**
		 * 循環参照を見つけたときの動き（既定 {@link JsonCycle#THROW}）
		 *
		 * @param cycle	動き
		 * @return	自身
		 */
		public Builder cycle (JsonCycle cycle) {
			this.cycle = Objects.requireNonNull(cycle, "cycle");
			return this;
		}

		/**
		 * 入れ子の深さの上限（既定 {@value JsonWriteOptions#DEFAULT_MAX_DEPTH}）。超えたら、循環の設定によらず例外
		 *
		 * @param maxDepth	段数（1 以上）
		 * @return	自身
		 */
		public Builder maxDepth (int maxDepth) {
			if (maxDepth < 1) {
				throw new IllegalArgumentException("maxDepth は 1 以上にしてください: " + maxDepth);
			}
			this.maxDepth = maxDepth;
			return this;
		}

		/**
		 * 型ごとの書き方を足す（この型とその子に効く。決まった型より先に見る）
		 *
		 * <p>先に足したものが優先する。</p>
		 *
		 * @param type		型
		 * @param writer	書き方
		 * @return	自身
		 */
		public Builder typeWriter (Class<?> type, JsonTypeWriter writer) {
			typeWriters.add(new TypeWriterEntry(Objects.requireNonNull(type, "type"), Objects.requireNonNull(writer, "writer")));
			return this;
		}

		/**
		 * 作る
		 *
		 * @return	書き方
		 */
		public JsonWriteOptions build () {

			return new JsonWriteOptions(this);

		}

	}

}
