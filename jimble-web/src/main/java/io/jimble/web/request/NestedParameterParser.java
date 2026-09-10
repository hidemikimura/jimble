package io.jimble.web.request;

import io.jimble.util.data.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * リクエストパラメータのネスト解析（要件 F-W-01〜03）
 *
 * <p>
 * パスパラメータ・クエリ・フォーム・JSON をひとつの {@link Data} にまとめる。
 * </p>
 *
 * <pre>
 * user[name]=きむら                 → {"user":{"name":"きむら"}}
 * user[address][city]=京都          → {"user":{"address":{"city":"京都"}}}
 * items[0][name]=A&amp;items[1][name]=B → {"items":[{"name":"A"},{"name":"B"}]}
 * tags[]=a&amp;tags[]=b                 → {"tags":["a","b"]}
 * paging.page=2                     → {"paging":{"page":"2"}}
 * </pre>
 *
 * <h2>移送元から直したところ</h2>
 * <ol>
 *   <li><b>{@code a[b][c]} が解釈できていなかった。</b>
 *       移送元はキーを「{@code .} で分割」してから、各要素の {@code [...]} を
 *       <b>配列の添字としてだけ</b>読んでいた。
 *       つまり {@code user[name]} は {@code Parse.parseInt("name")} = 0 と解釈され、
 *       <b>{@code user[0]} 扱いで文字列リストに詰められていた。</b>
 *       {@code items[0][name]} も1つのキーとして扱われ、
 *       {@code items} の下に値が平らに並ぶだけだった。
 *       <b>要件 F-W-03 の例（{@code a[b][c]=1}）がそのまま動かない状態だった</b></li>
 *   <li><b>JSON を {@code MapUtil.mergeData()} で重ねていた。</b>
 *       あれは深いマージで、<b>同じキーに値があると1つのリストにまとめる。</b>
 *       クエリと JSON に同じキーがあると、値が配列に化けた
 *       （バッチ設定で同じ穴を踏んでいる。{@code docs/design-m7.md} 2.4）。
 *       <b>JSON を優先して上書きする</b>形にした</li>
 *   <li>使われていない {@code merge()} が 70 行ほど残っていた</li>
 * </ol>
 */
public final class NestedParameterParser {

	/** 添字なしの {@code []}（末尾に足す） */
	private static final int APPEND = -1;

	private NestedParameterParser () {}

	/**
	 * 解析する
	 *
	 * @param request	リクエスト
	 * @return	結果
	 */
	public static Data parse (Request request) {

		Data data = new Data();

		// パスパラメータ → クエリ → フォーム の順に重ねる
		putAll(data, request.bodyPath());
		putAll(data, request.bodyQuery());
		putAll(data, request.bodyForm());

		// JSON は最後に上書きで重ねる
		overlay(data, request.bodyJson());

		return data;

	}

	/**
	 * まとめて入れる
	 *
	 * @param data	入れ先
	 * @param src	入れるもの
	 */
	private static void putAll (Data data, Data src) {

		if (src == null) {
			return;
		}

		for (String key : src.keySet()) {
			set(data, key, src.getObject(key));
		}

	}

	// region 1件を入れる

	/**
	 * キーの位置に値を入れる
	 *
	 * @param root	根
	 * @param key	キー
	 * @param value	値
	 */
	private static void set (Data root, String key, Object value) {

		List<Segment> segments = segments(key);

		if (segments.isEmpty()) {
			return;
		}

		Object cursor = root;

		for (int i = 0; i < segments.size() - 1; i++) {
			cursor = descend(cursor, segments.get(i), segments.get(i + 1).isIndex());
			if (cursor == null) {
				return;
			}
		}

		assign(cursor, segments.getLast(), value);

	}

	/**
	 * 1段降りる（無ければ作る）
	 *
	 * @param container		いまの入れ物
	 * @param segment		いまの位置
	 * @param nextIsIndex	次が添字か（作る入れ物が List か Data かを決める）
	 * @return	降りた先（降りられなければ null）
	 */
	@SuppressWarnings("unchecked")
	private static Object descend (Object container, Segment segment, boolean nextIsIndex) {

		if (segment.isIndex()) {

			if (!(container instanceof List<?>)) {
				return null;
			}

			List<Object> list = (List<Object>) container;
			int index = segment.index() == APPEND ? list.size() : segment.index();

			grow(list, index);

			if (list.get(index) == null) {
				list.set(index, nextIsIndex ? new ArrayList<>() : new Data());
			}

			return list.get(index);

		}

		if (!(container instanceof Data data)) {
			return null;
		}

		Object child = data.getObject(segment.name());

		if (nextIsIndex && !(child instanceof List<?>)) {
			child = new ArrayList<>();
			data.put(segment.name(), child);
		} else if (!nextIsIndex && !(child instanceof Data)) {
			child = new Data();
			data.put(segment.name(), child);
		}

		return child;

	}

	/**
	 * 値を入れる
	 *
	 * @param container	入れ物
	 * @param segment	位置
	 * @param value		値
	 */
	@SuppressWarnings("unchecked")
	private static void assign (Object container, Segment segment, Object value) {

		if (segment.isIndex()) {

			if (!(container instanceof List<?>)) {
				return;
			}

			List<Object> list = (List<Object>) container;

			if (segment.index() == APPEND) {

				// tags[]=a&tags[]=b は1つのキーに2つの値として届く
				if (value instanceof List<?> values) {
					list.addAll(values);
				} else {
					list.add(value);
				}

				return;

			}

			grow(list, segment.index());
			list.set(segment.index(), single(value));

			return;

		}

		if (container instanceof Data data) {
			data.put(segment.name(), single(value));
		}

	}

	/**
	 * 1つだけなら中身を返す
	 *
	 * <p>
	 * クエリとフォームは {@code Map<String, List<String>>} で届くので、
	 * <b>1つしかない値も List に入っている。</b>そのままだと
	 * {@code getString()} が使えないので、1つなら取り出す。
	 * </p>
	 *
	 * <h2>空で送られたものは空文字にする</h2>
	 * <p>
	 * <b>{@code name=} のように値なしで送られると、空の List で届く。</b>
	 * そのまま通すと {@code required()} が素通りする——
	 * {@code EmptyValidator} が見るのは「null か、空の文字列か」なので、
	 * <b>空の List はどちらでもない</b>。
	 * つまり<b>必須の欄を空のまま送れば検証を抜けられた</b>（D-133）。
	 * </p>
	 * <p>
	 * <b>「送られていない」とは区別される。</b>そもそも送られなければ
	 * キー自体が届かないので、{@code containsKey} で見分けられる
	 * （PATCH で「送った項目だけ変える」がこれに乗っている）。
	 * </p>
	 *
	 * @param value	値
	 * @return	値
	 */
	private static Object single (Object value) {

		if (value instanceof List<?> list) {

			if (list.isEmpty()) {
				return "";
			}

			if (list.size() == 1) {
				return list.getFirst();
			}

		}

		return value;

	}

	/**
	 * リストを必要な長さまで伸ばす
	 *
	 * @param list	リスト
	 * @param index	必要な添字
	 */
	private static void grow (List<Object> list, int index) {

		while (list.size() <= index) {
			list.add(null);
		}

	}

	// endregion

	// region キーの分解

	/**
	 * キーを分解する
	 *
	 * <p>{@code .} と {@code [...]} の両方で区切る。</p>
	 *
	 * @param key	キー
	 * @return	分解した位置
	 */
	static List<Segment> segments (String key) {

		List<Segment> segments = new ArrayList<>();

		if (key == null || key.isEmpty()) {
			return segments;
		}

		StringBuilder name = new StringBuilder();

		for (int i = 0; i < key.length(); i++) {

			char c = key.charAt(i);

			if (c == '.') {

				flush(segments, name);

			} else if (c == '[') {

				flush(segments, name);

				int end = key.indexOf(']', i);

				if (end < 0) {
					// 閉じていない。以降はただの名前として扱う
					name.append(key, i, key.length());
					break;
				}

				String inner = key.substring(i + 1, end);

				segments.add(inner.isEmpty() || isDigits(inner)
					? Segment.index(inner.isEmpty() ? APPEND : Integer.parseInt(inner))
					: Segment.name(inner));

				i = end;

			} else {

				name.append(c);

			}

		}

		flush(segments, name);

		return segments;

	}

	/**
	 * 溜めた名前を1つの位置にする
	 *
	 * @param segments	位置の一覧
	 * @param name		溜めた名前
	 */
	private static void flush (List<Segment> segments, StringBuilder name) {

		if (name.isEmpty()) {
			return;
		}

		segments.add(Segment.name(name.toString()));
		name.setLength(0);

	}

	/**
	 * 数字だけか
	 *
	 * @param value	文字列
	 * @return	数字だけの場合 = true
	 */
	private static boolean isDigits (String value) {

		for (int i = 0; i < value.length(); i++) {
			if (!Character.isDigit(value.charAt(i))) {
				return false;
			}
		}

		return true;

	}

	/**
	 * キーの1区切り
	 *
	 * @param name	名前（添字のときは null）
	 * @param index	添字（名前のときは {@link Integer#MIN_VALUE}）
	 */
	record Segment (String name, int index) {

		/** 名前のときの添字 */
		private static final int NOT_INDEX = Integer.MIN_VALUE;

		/**
		 * 名前
		 *
		 * @param name	名前
		 * @return	位置
		 */
		static Segment name (String name) {

			return new Segment(name, NOT_INDEX);

		}

		/**
		 * 添字
		 *
		 * @param index	添字
		 * @return	位置
		 */
		static Segment index (int index) {

			return new Segment(null, index);

		}

		/**
		 * 添字か
		 *
		 * @return	添字の場合 = true
		 */
		boolean isIndex () {

			return index != NOT_INDEX;

		}

	}

	// endregion

	// region JSON を重ねる

	/**
	 * JSON を上書きで重ねる
	 *
	 * <p>
	 * 両方が {@link Data} なら中を見て重ね、そうでなければ JSON を優先する。
	 * <b>同じキーの値を1つのリストにまとめたりはしない</b>（移送元はしていた）。
	 * </p>
	 *
	 * @param base	重ねられる側
	 * @param json	重ねる側
	 */
	private static void overlay (Data base, Data json) {

		if (json == null || json.isEmpty()) {
			return;
		}

		for (String key : json.keySet()) {

			Object value = json.getObject(key);
			Object current = base.getObject(key);

			if (current instanceof Data currentData && value instanceof Data valueData) {
				overlay(currentData, valueData);
				continue;
			}

			base.put(key, value);

		}

	}

	// endregion

}
