package io.jimble.converter;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 変換のしかた（D-307）
 *
 * <p>
 * <b>変えられない値である。</b>1つ作って使い回してよい（スレッドをまたいでも）。
 * 作り直す前の Configration は、呼び出しごとの作業状態（循環参照の記録・深さ）も持っていたので、共有すると結果が狂った。
 * </p>
 *
 * <h2>既定の決まり</h2>
 * <ul>
 *   <li>意味がはっきりしているゆるさは受け付ける（全角の数字・{@code "1,234"}・{@code 1.0} → int・{@code yes} → true など）</li>
 *   <li>値が変わってしまうものは {@link ConversionException}（{@code 1.5} → int・桁あふれ・ありえない日付など）</li>
 *   <li>Map のキーで、変換先に無いものは飛ばす</li>
 *   <li>危ない型（{@code Class}・{@code ClassLoader}・{@code InetAddress}・{@code Pattern}・{@code File}・{@code Path}）は作らない</li>
 *   <li>循環参照は例外</li>
 * </ul>
 */
public final class ConverterOptions {

	/** 既定の変換のしかた */
	public static final ConverterOptions DEFAULT = builder().build();

	/**
	 * ゆるい変換（読めない値を null / 0 にする。CSV のように、壊れた値を飛ばして読みたいとき）
	 */
	public static final ConverterOptions LENIENT = builder().lenient(true).build();

	/* 危ない型（外から来た文字から作ると、クラスの初期化・名前解決・ReDoS・ファイルへの到達が起きる） */
	private static final Set<String> UNSAFE_TYPES = Set.of(
		"java.lang.Class", "java.lang.ClassLoader", "java.net.InetAddress", "java.net.Inet4Address", "java.net.Inet6Address",
		"java.util.regex.Pattern", "java.io.File", "java.nio.file.Path"
	);

	private final boolean lenient;
	private final boolean rejectUnknownKeys;
	private final boolean allowUnsafeTypes;
	private final boolean snakeCaseKeys;
	private final boolean cycleToNull;
	private final ZoneId zone;
	private final Supplier<? extends Map<String, Object>> mapFactory;

	private ConverterOptions (Builder builder) {

		this.lenient = builder.lenient;
		this.rejectUnknownKeys = builder.rejectUnknownKeys;
		this.allowUnsafeTypes = builder.allowUnsafeTypes;
		this.snakeCaseKeys = builder.snakeCaseKeys;
		this.cycleToNull = builder.cycleToNull;
		this.zone = builder.zone;
		this.mapFactory = builder.mapFactory;

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
	 * @return	この変換のしかたの値から始まる組み立て役
	 */
	public Builder toBuilder () {

		Builder builder = new Builder();
		builder.lenient = lenient;
		builder.rejectUnknownKeys = rejectUnknownKeys;
		builder.allowUnsafeTypes = allowUnsafeTypes;
		builder.snakeCaseKeys = snakeCaseKeys;
		builder.cycleToNull = cycleToNull;
		builder.zone = zone;
		builder.mapFactory = mapFactory;
		return builder;

	}

	/**
	 * 読めない値を null / 0 にするか
	 *
	 * @return	する場合 = true
	 */
	public boolean lenient () {

		return lenient;

	}

	/**
	 * Map のキーで、変換先に無いものを断るか
	 *
	 * @return	断る場合 = true
	 */
	public boolean rejectUnknownKeys () {

		return rejectUnknownKeys;

	}

	/**
	 * 危ない型を作ってよいか
	 *
	 * @return	作ってよい場合 = true
	 */
	public boolean allowUnsafeTypes () {

		return allowUnsafeTypes;

	}

	/**
	 * キーの区切りを camelCase にしても探すか
	 *
	 * @return	探す場合 = true
	 */
	public boolean snakeCaseKeys () {

		return snakeCaseKeys;

	}

	/**
	 * 循環参照を null にするか（しなければ例外）
	 *
	 * @return	null にする場合 = true
	 */
	public boolean cycleToNull () {

		return cycleToNull;

	}

	/**
	 * 時差の無い日時を補う時間帯
	 *
	 * @return	時間帯（null なら、変換するときのサーバーの時間帯）
	 */
	public ZoneId zone () {

		return zone;

	}

	/**
	 * 値の型が決まっていない Map を作るもの
	 *
	 * @return	作るもの
	 */
	public Supplier<? extends Map<String, Object>> mapFactory () {

		return mapFactory;

	}

	/**
	 * 危ない型か
	 *
	 * @param type	型
	 * @return	危ない型なら true
	 */
	public boolean isUnsafe (Class<?> type) {

		return !allowUnsafeTypes && UNSAFE.get(type);

	}

	/* 危ない型か（クラスごとに1回だけ調べる） */
	private static final ClassValue<Boolean> UNSAFE = new ClassValue<>() {
		@Override
		protected Boolean computeValue (Class<?> type) {
			for (Class<?> c = type; c != null; c = c.getSuperclass()) {
				if (UNSAFE_TYPES.contains(c.getName())) {
					return true;
				}
			}
			for (Class<?> i : type.getInterfaces()) {
				if (UNSAFE_TYPES.contains(i.getName())) {
					return true;
				}
			}
			return false;
		}
	};

	/**
	 * 変換のしかたの組み立て役
	 */
	public static final class Builder {

		private boolean lenient = false;
		private boolean rejectUnknownKeys = false;
		private boolean allowUnsafeTypes = false;
		private boolean snakeCaseKeys = false;
		private boolean cycleToNull = false;
		private ZoneId zone = null;
		private Supplier<? extends Map<String, Object>> mapFactory = LinkedHashMap::new;

		private Builder () {
		}

		/**
		 * 読めない値を null / 0 にするか（既定 false = 例外）
		 *
		 * @param lenient	する場合 = true
		 * @return	自身
		 */
		public Builder lenient (boolean lenient) {

			this.lenient = lenient;
			return this;

		}

		/**
		 * Map のキーで、変換先に無いものを断るか（既定 false = 飛ばす）
		 *
		 * @param rejectUnknownKeys	断る場合 = true
		 * @return	自身
		 */
		public Builder rejectUnknownKeys (boolean rejectUnknownKeys) {

			this.rejectUnknownKeys = rejectUnknownKeys;
			return this;

		}

		/**
		 * 危ない型（Class・InetAddress・Pattern・File など）を作ってよいか（既定 false）
		 *
		 * <p>外から来た値を変換するなら、true にしない。</p>
		 *
		 * @param allowUnsafeTypes	作ってよい場合 = true
		 * @return	自身
		 */
		public Builder allowUnsafeTypes (boolean allowUnsafeTypes) {

			this.allowUnsafeTypes = allowUnsafeTypes;
			return this;

		}

		/**
		 * キーの区切り（{@code _} / {@code -} / 空白）を camelCase にしても探すか（既定 false）
		 *
		 * @param snakeCaseKeys	探す場合 = true
		 * @return	自身
		 */
		public Builder snakeCaseKeys (boolean snakeCaseKeys) {

			this.snakeCaseKeys = snakeCaseKeys;
			return this;

		}

		/**
		 * 循環参照を null にするか（既定 false = 例外）
		 *
		 * @param cycleToNull	null にする場合 = true
		 * @return	自身
		 */
		public Builder cycleToNull (boolean cycleToNull) {

			this.cycleToNull = cycleToNull;
			return this;

		}

		/**
		 * 時差の無い日時を補う時間帯（既定 null = サーバーの時間帯）
		 *
		 * @param zone	時間帯
		 * @return	自身
		 */
		public Builder zone (ZoneId zone) {

			this.zone = zone;
			return this;

		}

		/**
		 * 値の型が決まっていない Map を作るもの（既定 {@code LinkedHashMap::new}）
		 *
		 * @param mapFactory	作るもの
		 * @return	自身
		 */
		public Builder mapFactory (Supplier<? extends Map<String, Object>> mapFactory) {

			this.mapFactory = Objects.requireNonNull(mapFactory, "mapFactory");
			return this;

		}

		/**
		 * 作る
		 *
		 * @return	変換のしかた
		 */
		public ConverterOptions build () {

			return new ConverterOptions(this);

		}

	}

}
