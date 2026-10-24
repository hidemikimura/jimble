package io.jimble.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * JSON の読み方（D-307）
 *
 * <p>
 * <b>変えられない値である。</b>1つ作って使い回してよい（スレッドをまたいでも）。
 * 変えるときは {@link #toBuilder()} で写しを作る。
 * 作り直す前の読み手は、設定（Configration）に呼び出しごとの作業状態も持たせていたので、
 * 1つを共有すると結果が狂った。
 * </p>
 *
 * <h2>既定はゆるい</h2>
 * <p>
 * 実際に流れてくる JSON（手で書いた設定、古い API の応答）は厳密でないことが多い。
 * <b>意味がはっきりしているゆるさは受け付け、データを黙って壊すものは断る。</b>
 * </p>
 * <ul>
 *   <li>受け付ける：クォートの無いキーと値、シングルクォート、末尾のカンマ、コメント（<code>//</code> と <code>/&#42; &#42;/</code>）、
 *       {@code NaN} / {@code Infinity}、{@code +1} / {@code 01} / {@code .5} / {@code 1.}、文字列の中の生の改行やタブ</li>
 *   <li>断る：区切りの抜け（{@code {"a":1 "b":2}} / {@code [1,,2]}）、壊れたエスケープ（<code>&#92;q</code> / <code>&#92;u12</code>）、
 *       値の無いキー、閉じていない括弧、後ろの余分なもの</li>
 * </ul>
 * <p>{@link Builder#strict(boolean)} で、JSON の決まり（RFC 8259）どおりにだけ読む。</p>
 *
 * <h2>上限</h2>
 * <p>
 * <b>外から来た JSON を読むので、上限を必ず持つ。</b>どれも超えたら {@link JsonException}。
 * </p>
 * <ul>
 *   <li>入れ子の深さ：{@value #DEFAULT_MAX_DEPTH} 段</li>
 *   <li>1つの数値の長さ：{@value #DEFAULT_MAX_NUMBER_LENGTH} 文字</li>
 *   <li>1つの文字列の長さ：{@value #DEFAULT_MAX_STRING_LENGTH} 文字</li>
 * </ul>
 */
public final class JsonReadOptions {

	/** 入れ子の深さの既定の上限 */
	public static final int DEFAULT_MAX_DEPTH = 512;

	/** 1つの数値の長さの既定の上限（文字数） */
	public static final int DEFAULT_MAX_NUMBER_LENGTH = 1000;

	/** 1つの文字列の長さの既定の上限（文字数） */
	public static final int DEFAULT_MAX_STRING_LENGTH = 20_000_000;

	/** 既定の読み方 */
	public static final JsonReadOptions DEFAULT = builder().build();

	/* 厳密に読むか */
	private final boolean strict;

	/* 文字列の "true" / "false" を Boolean にするか */
	private final boolean stringBooleans;

	/* 入れ子の深さの上限 */
	private final int maxDepth;

	/* 1つの数値の長さの上限 */
	private final int maxNumberLength;

	/* 1つの文字列の長さの上限 */
	private final int maxStringLength;

	/* オブジェクトを入れる Map を作る */
	private final Supplier<? extends Map<String, Object>> mapFactory;

	/* 配列を入れる List を作る */
	private final Supplier<? extends List<Object>> listFactory;

	/* 文字列の値を読んだあとに通す（無ければ null） */
	private final Function<String, Object> stringValueHook;

	private JsonReadOptions (Builder builder) {

		this.strict = builder.strict;
		this.stringBooleans = builder.stringBooleans;
		this.maxDepth = builder.maxDepth;
		this.maxNumberLength = builder.maxNumberLength;
		this.maxStringLength = builder.maxStringLength;
		this.mapFactory = builder.mapFactory;
		this.listFactory = builder.listFactory;
		this.stringValueHook = builder.stringValueHook;

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
	 * @return	この読み方の値から始まる組み立て役
	 */
	public Builder toBuilder () {

		Builder builder = new Builder();
		builder.strict = strict;
		builder.stringBooleans = stringBooleans;
		builder.maxDepth = maxDepth;
		builder.maxNumberLength = maxNumberLength;
		builder.maxStringLength = maxStringLength;
		builder.mapFactory = mapFactory;
		builder.listFactory = listFactory;
		builder.stringValueHook = stringValueHook;
		return builder;

	}

	/**
	 * 厳密に読むか
	 *
	 * @return	JSON の決まりどおりにだけ読む場合 = true
	 */
	public boolean strict () {

		return strict;

	}

	/**
	 * 文字列の "true" / "false" を Boolean にするか
	 *
	 * @return	する場合 = true
	 */
	public boolean stringBooleans () {

		return stringBooleans;

	}

	/**
	 * 入れ子の深さの上限
	 *
	 * @return	段数
	 */
	public int maxDepth () {

		return maxDepth;

	}

	/**
	 * 1つの数値の長さの上限
	 *
	 * @return	文字数
	 */
	public int maxNumberLength () {

		return maxNumberLength;

	}

	/**
	 * 1つの文字列の長さの上限
	 *
	 * @return	文字数
	 */
	public int maxStringLength () {

		return maxStringLength;

	}

	/**
	 * オブジェクトを入れる Map を作る
	 *
	 * @return	作るもの
	 */
	public Supplier<? extends Map<String, Object>> mapFactory () {

		return mapFactory;

	}

	/**
	 * 配列を入れる List を作る
	 *
	 * @return	作るもの
	 */
	public Supplier<? extends List<Object>> listFactory () {

		return listFactory;

	}

	/**
	 * 文字列の値を読んだあとに通すもの
	 *
	 * @return	通すもの（無ければ null）
	 */
	public Function<String, Object> stringValueHook () {

		return stringValueHook;

	}

	/**
	 * 読み方の組み立て役
	 */
	public static final class Builder {

		private boolean strict = false;
		private boolean stringBooleans = true;
		private int maxDepth = DEFAULT_MAX_DEPTH;
		private int maxNumberLength = DEFAULT_MAX_NUMBER_LENGTH;
		private int maxStringLength = DEFAULT_MAX_STRING_LENGTH;
		private Supplier<? extends Map<String, Object>> mapFactory = LinkedHashMap::new;
		private Supplier<? extends List<Object>> listFactory = ArrayList::new;
		private Function<String, Object> stringValueHook = null;

		private Builder () {
		}

		/**
		 * 厳密に読むか（既定 false）
		 *
		 * <p>true にすると、クォートの無いキーと値・シングルクォート・末尾のカンマ・コメント・
		 * {@code NaN}・{@code +1} / {@code 01} / {@code .5}・文字列の中の生の制御文字を断る。</p>
		 *
		 * @param strict	厳密に読む場合 = true
		 * @return	自身
		 */
		public Builder strict (boolean strict) {

			this.strict = strict;
			return this;

		}

		/**
		 * 文字列の "true" / "false" を Boolean にするか（既定 true）
		 *
		 * <p>
		 * 文字列で真偽を返す API があるため、既定では Boolean にする。
		 * キーには効かない（キーはいつも文字列）。
		 * </p>
		 *
		 * @param stringBooleans	する場合 = true
		 * @return	自身
		 */
		public Builder stringBooleans (boolean stringBooleans) {

			this.stringBooleans = stringBooleans;
			return this;

		}

		/**
		 * 入れ子の深さの上限（既定 {@value JsonReadOptions#DEFAULT_MAX_DEPTH}）
		 *
		 * @param maxDepth	段数（1 以上）
		 * @return	自身
		 */
		public Builder maxDepth (int maxDepth) {

			this.maxDepth = positive(maxDepth, "maxDepth");
			return this;

		}

		/**
		 * 1つの数値の長さの上限（既定 {@value JsonReadOptions#DEFAULT_MAX_NUMBER_LENGTH}）
		 *
		 * <p>
		 * <b>大きくしすぎない。</b>長い数値を読む費用は、長さに比例するより速く増える。
		 * </p>
		 *
		 * @param maxNumberLength	文字数（1 以上）
		 * @return	自身
		 */
		public Builder maxNumberLength (int maxNumberLength) {

			this.maxNumberLength = positive(maxNumberLength, "maxNumberLength");
			return this;

		}

		/**
		 * 1つの文字列の長さの上限（既定 {@value JsonReadOptions#DEFAULT_MAX_STRING_LENGTH}）
		 *
		 * @param maxStringLength	文字数（1 以上）
		 * @return	自身
		 */
		public Builder maxStringLength (int maxStringLength) {

			this.maxStringLength = positive(maxStringLength, "maxStringLength");
			return this;

		}

		/**
		 * オブジェクトを入れる Map を作るもの（既定 {@code LinkedHashMap::new}）
		 *
		 * <p>キーの順番を保つ Map を渡すこと（読んだ順に並ぶのを約束にしている）。</p>
		 *
		 * @param mapFactory	作るもの
		 * @return	自身
		 */
		public Builder mapFactory (Supplier<? extends Map<String, Object>> mapFactory) {

			this.mapFactory = Objects.requireNonNull(mapFactory, "mapFactory");
			return this;

		}

		/**
		 * 配列を入れる List を作るもの（既定 {@code ArrayList::new}）
		 *
		 * @param listFactory	作るもの
		 * @return	自身
		 */
		public Builder listFactory (Supplier<? extends List<Object>> listFactory) {

			this.listFactory = Objects.requireNonNull(listFactory, "listFactory");
			return this;

		}

		/**
		 * 文字列の値を読んだあとに通すもの（既定なし）
		 *
		 * <p>
		 * 戻り値がそのまま値になる。キーには効かない。
		 * {@link #stringBooleans(boolean)} より先に通す（ここで文字列以外を返せば、それで決まる）。
		 * </p>
		 *
		 * @param stringValueHook	通すもの（null で外す）
		 * @return	自身
		 */
		public Builder stringValueHook (Function<String, Object> stringValueHook) {

			this.stringValueHook = stringValueHook;
			return this;

		}

		/**
		 * 作る
		 *
		 * @return	読み方
		 */
		public JsonReadOptions build () {

			return new JsonReadOptions(this);

		}

		private static int positive (int value, String name) {

			if (value < 1) {
				throw new IllegalArgumentException(name + " は 1 以上にしてください: " + value);
			}
			return value;

		}

	}

}
