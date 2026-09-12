package io.jimble.util.data.async;

import io.jimble.util.data.Data;
import io.jimble.util.data.TableNest;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Stream;
import java.util.function.IntFunction;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * 遅延読み込みリスト（要件 F-A-01〜05 / F-A-09 / F-A-10）
 *
 * <p>
 * <b>参照された時点ではじめて {@link #load()} が走る。</b>
 * </p>
 *
 * <pre>
 * public class SiteList extends AsyncList {
 *
 *     private final long groupId;
 *
 *     public SiteList (long groupId) { this.groupId = groupId; }
 *
 *     &#64;Override protected List&lt;Data&gt; load () { return db.selectList(...); }
 *     &#64;Override protected void setData (Data data) { add(data); }
 *     &#64;Override protected String hashKey () { return "SiteList:" + groupId; }
 * }
 * </pre>
 *
 * <p>
 * {@link #setRelationData(List)} は<b>全要素の {@link #setData(Data)} が終わってから
 * 1回だけ</b>呼ばれる（要件 F-A-03）。要素ごとに1本ずつクエリを投げる代わりに、
 * ここでまとめて引く。
 * </p>
 *
 * <h2>移送元から直したところ</h2>
 * <ol>
 *   <li>読み込み済みフラグの見え方（{@link AsyncState} 参照）</li>
 *   <li><b>{@code toArray()} が読み込みを起こしていなかった。</b>
 *       {@code ArrayList.toArray()} は内部配列を直接見るので、
 *       <b>未読み込みのリストは黙って空の配列を返す。</b>
 *       {@code new ArrayList&lt;&gt;(asyncList)} や {@code List.copyOf(asyncList)}、
 *       多くのライブラリがこれを通るため、<b>「なぜか空になる」形で表に出る。</b>
 *       いちばん質が悪い</li>
 *   <li><b>{@code indexOf} / {@code lastIndexOf} / {@code subList} も同じ。</b>
 *       いずれも内部配列を直接見るので、{@code indexOf} は -1 を返し、
 *       {@code subList(0, 2)} は範囲外で例外になる</li>
 *   <li><b>{@code getFirst()} / {@code getLast()} は例外を投げていた。</b>
 *       Java 21 で {@code ArrayList} が持つようになったもので、内部配列を見るため
 *       <b>未読み込みのリストは常に空扱いになり {@code NoSuchElementException}</b> になる</li>
 *   <li><b>{@code equals()} が無かった。</b>{@code hashCode()} だけ
 *       {@code hashKey()} 基準に変えてあり、<b>両者が食い違っていた。</b>
 *       しかも {@code ArrayList.equals()} は<b>相手を {@code iterator()} で走査する</b>ので、
 *       比較しただけで片方が読み込まれ、そのうえで答えが間違う
 *       （自分は内部配列を見るので 0 件のまま比べる）。要件 F-A-05</li>
 *   <li><b>{@code toString()} が全部読み込んでいた</b>（要件 F-D-27）</li>
 *   <li><b>読み込みに失敗したことを外から知る方法が無かった</b>（要件 F-A-09）</li>
 * </ol>
 */
public abstract class AsyncList extends ArrayList<Object> implements Async {

	private static final long serialVersionUID = 1L;

	/* 読み込みの状態 */
	private final AsyncState state = new AsyncState();

	/* 読み込んだデータ */
	private transient List<Data> dataList;

	// region 実装するもの（要件 F-A-02）

	/**
	 * データを読む
	 *
	 * @return	読んだデータ
	 * @throws Exception	読み込みに失敗した場合
	 */
	protected abstract List<Data> load () throws Exception;

	/**
	 * 読んだデータを1件ずつ反映する
	 *
	 * @param data	読んだデータ
	 * @throws Exception	反映に失敗した場合
	 */
	protected abstract void setData (Data data) throws Exception;

	/**
	 * 関連データを反映する（要件 F-A-03）
	 *
	 * <p>全要素の {@link #setData(Data)} が終わってから1回だけ呼ばれる。</p>
	 *
	 * @param dataList	読んだデータ一覧
	 * @throws Exception	反映に失敗した場合
	 */
	protected void setRelationData (List<Data> dataList) throws Exception {}

	/**
	 * まとめて読む（要件 F-A-06）
	 *
	 * <p>
	 * 先読み（{@link AsyncPrefetch}）が、同じ {@link #batchKey()} を持つ
	 * 未読み込みのノードを集めて<b>1回だけ</b>呼ぶ。
	 * 呼ばれるのは集まったうちの1つで、渡される {@code ids} には
	 * <b>ほかのノードの {@link #batchId()} も入っている。</b>
	 * </p>
	 *
	 * <p>
	 * <b>{@link #load()} と同じことを、IN 句で書く。</b>
	 * 片方だけ直すと結果がずれるので、必ず並べて置くこと
	 * （置き場を分けない理由がこれである）。
	 * </p>
	 *
	 * <p>
	 * 既定は {@code null}（先読みしない）。返さなかった id のノードは
	 * <b>「空」として読み込み済みになる</b>（そうしないと、そのぶんだけ
	 * 個別のクエリが飛んで先読みの意味が無くなる）。
	 * 例外を投げた場合は<b>どのノードも読み込み済みにしない</b>ので、
	 * それぞれが個別に {@link #load()} される。
	 * </p>
	 *
	 * @param ids	{@link #batchId()} の一覧（重複なし。1件以上）
	 * @return	id ごとのデータの一覧（先読みしないなら null）
	 * @throws Exception	読み込みに失敗した場合
	 */
	protected Map<Object, List<Data>> loadBatch (List<Object> ids) throws Exception {

		return null;

	}

	/**
	 * 同じものかどうかを決めるキー
	 *
	 * @return	キー
	 */
	protected abstract String hashKey ();

	// endregion

	// region 状態（読み込みを起こさない）

	/**
	 * 読み込み済みか
	 *
	 * @return	読み込み済みの場合 = true
	 */
	@Override
	public boolean isLoaded () {

		return state.isLoaded();

	}

	/**
	 * 読み込みに失敗したか（要件 F-A-09）
	 *
	 * @return	失敗した場合 = true
	 */
	@Override
	public boolean isLoadFailed () {

		return state.isFailed();

	}

	/**
	 * 読み込み済みにする（読み込みは起こさない）
	 */
	public void setLoaded () {

		state.markLoaded();

	}

	/**
	 * まとめて読む単位の識別子（要件 F-A-10）
	 *
	 * @return	識別子
	 */
	@Override
	public String batchKey () {

		return null;

	}

	/**
	 * {@link #batchKey()} の中で自分を特定する値（要件 F-A-10）
	 *
	 * @return	値
	 */
	@Override
	public Object batchId () {

		return null;

	}

	/**
	 * すでに持っている要素だけを返す（読み込みは起こさない）
	 *
	 * @return	要素の一覧
	 */
	public List<Object> loadedValues () {

		/*
		 * new ArrayList<>(this) は toArray() を通る。
		 * toArray() は読み込みを起こすようにしたので、ここでは使えない。
		 * 添字で取り出す。
		 */
		List<Object> values = new ArrayList<>(loadedSize());

		for (int i = 0; i < loadedSize(); i++) {
			values.add(super.get(i));
		}

		return values;

	}

	/**
	 * すでに持っている要素の数（読み込みは起こさない）
	 *
	 * @return	要素数
	 */
	public int loadedSize () {

		return super.size();

	}

	// endregion

	// region 読み込み

	/**
	 * 読んだデータ
	 *
	 * @return	データ（未読み込みなら null）
	 */
	protected List<Data> getDataList () {

		return this.dataList;

	}

	/**
	 * 読み込む（一度だけ）
	 */
	private void loadData () {

		state.loadOnce(this, () -> {

			this.dataList = load();

			if (this.dataList != null) {

				for (Data data : this.dataList) {
					setData(data);
				}

				// 全件の setData が終わってから1回だけ（要件 F-A-03）
				setRelationData(this.dataList);

			}

		});

	}

	/**
	 * 読み込まずにデータを入れる（要件 F-A-10）
	 *
	 * <p><b>すでに読み込み済みなら何もしない。</b></p>
	 *
	 * @param dataList	データ
	 */
	public void putData (List<Data> dataList) {

		state.loadOnce(this, () -> {

			this.dataList = dataList;

			if (dataList != null) {

				for (Data data : dataList) {
					setData(data);
				}

				setRelationData(dataList);

			}

		});

	}

	// endregion

	// region テーブルネストの組み直し（要件 F-A-11）

	/**
	 * テーブルネストの形を変えて取り出す（要件 F-A-11）
	 *
	 * <p>
	 * <b>自分は変わらない。</b>組み直した一覧を新しく作って返す。
	 * </p>
	 *
	 * <p>
	 * 要素が {@link AsyncData} なら<b>触らない</b>（その要素が自分で組み直す）。
	 * 要素が素の {@link Data} なら、このリストの生データから導いた
	 * 「テーブル名 → 列名」で組み直す。それ以外（文字列など）はそのまま。
	 * </p>
	 *
	 * <p><b>読み込みを起こす。</b></p>
	 *
	 * @param nest	どちらの形にするか
	 * @return	組み直した一覧（{@link TableNest#AS_IS} なら自分自身）
	 */
	public List<Object> reshape (TableNest nest) {

		if (nest == null || nest == TableNest.AS_IS) {
			return this;
		}

		// 中身が要るので読む
		loadData();

		Map<String, Set<String>> tables = TableShape.tablesOf(this.dataList);

		if (tables.isEmpty()) {
			return this;
		}

		List<Object> result = new ArrayList<>(super.size());

		for (int i = 0; i < super.size(); i++) {

			Object element = super.get(i);

			/*
			 * 要素が AsyncData なら、その要素が自分の生データで組み直す。
			 * ここで触ると、親の列名で子を組み直すことになる。
			 */
			if (element instanceof Data data && !(element instanceof Async)) {
				result.add(TableShape.reshape(data, tables, nest));
				continue;
			}

			result.add(element);

		}

		return result;

	}

	// endregion

	// region 同一性（読み込みを起こさない。要件 F-A-05）

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode () {

		return Objects.hashCode(hashKey());

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * 移送元は {@code hashCode()} だけを {@code hashKey()} 基準にしていて、
	 * <b>{@code equals()} は {@code ArrayList} のまま</b>だった。
	 * {@code ArrayList.equals()} は相手を {@code iterator()} で走査するので、
	 * <b>比較しただけで相手が読み込まれ、しかも自分は内部配列（0 件）のまま比べる。</b>
	 * 同じキーの2つが「等しくない」と言われる。
	 * </p>
	 */
	@Override
	public boolean equals (Object obj) {

		if (this == obj) {
			return true;
		}

		if (!(obj instanceof AsyncList asyncList)) {
			return false;
		}

		return Objects.equals(hashKey(), asyncList.hashKey());

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p><b>未読み込みなら読み込まない</b>（要件 F-D-27）。</p>
	 */
	@Override
	public String toString () {

		if (!state.isLoaded()) {
			return "%s(未読み込み)".formatted(getClass().getSimpleName());
		}

		if (state.isFailed()) {
			return "%s(読み込み失敗)".formatted(getClass().getSimpleName());
		}

		return "%s(%d件)".formatted(getClass().getSimpleName(), loadedSize());

	}

	// endregion

	// region 触られたら読む（要件 D-157）

	/*
	 * <b>ArrayList の公開メソッドを、1つ残らずここに並べてある。</b>
	 *
	 * 以前は「読む側」だけを選んで上書きしていたので、
	 * {@code add} / {@code set} / {@code clear} / {@code addAll} や、
	 * Java 21 で生えた {@code addFirst} / {@code removeLast} は
	 * <b>未読み込みの空リストをそのまま触っていた</b>——
	 * 落ちも警告も出さず、<b>「空のリスト」として振る舞っていた</b>。
	 *
	 * <b>漏れは AsyncListAccessFunnelTest が反射で見張る。</b>
	 * JDK が List にメソッドを足したら、そこで落ちる。
	 *
	 * <b>equals / hashCode / toString は通さない</b>（上の「同一性」を参照）。
	 * デバッガやログが覗いただけでクエリが飛ぶのは困る。
	 */

	@Override
	public int size () {

		loadData();
		return super.size();

	}

	@Override
	public boolean isEmpty () {

		loadData();
		return super.isEmpty();

	}

	@Override
	public boolean contains (Object o) {

		loadData();
		return super.contains(o);

	}

	@Override
	public int indexOf (Object o) {

		loadData();
		return super.indexOf(o);

	}

	@Override
	public int lastIndexOf (Object o) {

		loadData();
		return super.lastIndexOf(o);

	}

	@Override
	public Object clone () {

		loadData();
		return super.clone();

	}

	@Override
	public Object[] toArray () {

		loadData();
		return super.toArray();

	}

	@Override
	public <T> T[] toArray (T[] a) {

		loadData();
		return super.toArray(a);

	}

	@Override
	public <T> T[] toArray (IntFunction<T[]> generator) {

		loadData();
		return super.toArray(generator);

	}

	@Override
	public Object get (int index) {

		loadData();
		return super.get(index);

	}

	@Override
	public Object getFirst () {

		loadData();
		return super.getFirst();

	}

	@Override
	public Object getLast () {

		loadData();
		return super.getLast();

	}

	@Override
	public Object set (int index, Object element) {

		loadData();
		return super.set(index, element);

	}

	@Override
	public boolean add (Object element) {

		loadData();
		return super.add(element);

	}

	@Override
	public void add (int index, Object element) {

		loadData();
		super.add(index, element);

	}

	@Override
	public void addFirst (Object element) {

		loadData();
		super.addFirst(element);

	}

	@Override
	public void addLast (Object element) {

		loadData();
		super.addLast(element);

	}

	@Override
	public Object remove (int index) {

		loadData();
		return super.remove(index);

	}

	@Override
	public boolean remove (Object o) {

		loadData();
		return super.remove(o);

	}

	@Override
	public Object removeFirst () {

		loadData();
		return super.removeFirst();

	}

	@Override
	public Object removeLast () {

		loadData();
		return super.removeLast();

	}

	@Override
	public void clear () {

		loadData();
		super.clear();

	}

	@Override
	public boolean addAll (Collection<?> collection) {

		loadData();
		return super.addAll(collection);

	}

	@Override
	public boolean addAll (int index, Collection<?> collection) {

		loadData();
		return super.addAll(index, collection);

	}

	@Override
	public boolean removeAll (Collection<?> collection) {

		loadData();
		return super.removeAll(collection);

	}

	@Override
	public boolean retainAll (Collection<?> collection) {

		loadData();
		return super.retainAll(collection);

	}

	@Override
	public boolean containsAll (Collection<?> collection) {

		loadData();
		return super.containsAll(collection);

	}

	@Override
	public boolean removeIf (Predicate<? super Object> filter) {

		loadData();
		return super.removeIf(filter);

	}

	@Override
	public void forEach (Consumer<? super Object> action) {

		loadData();
		super.forEach(action);

	}

	@Override
	public void replaceAll (UnaryOperator<Object> operator) {

		loadData();
		super.replaceAll(operator);

	}

	@Override
	public void sort (Comparator<? super Object> comparator) {

		loadData();
		super.sort(comparator);

	}

	@Override
	public Iterator<Object> iterator () {

		loadData();
		return super.iterator();

	}

	@Override
	public ListIterator<Object> listIterator () {

		loadData();
		return super.listIterator();

	}

	@Override
	public ListIterator<Object> listIterator (int index) {

		loadData();
		return super.listIterator(index);

	}

	@Override
	public List<Object> subList (int fromIndex, int toIndex) {

		loadData();
		return super.subList(fromIndex, toIndex);

	}

	@Override
	public Spliterator<Object> spliterator () {

		loadData();
		return super.spliterator();

	}

	@Override
	public Stream<Object> stream () {

		loadData();
		return super.stream();

	}

	@Override
	public Stream<Object> parallelStream () {

		loadData();
		return super.parallelStream();

	}

	@Override
	public List<Object> reversed () {

		loadData();
		return super.reversed();

	}

	@Override
	public void ensureCapacity (int minCapacity) {

		loadData();
		super.ensureCapacity(minCapacity);

	}

	@Override
	public void trimToSize () {

		loadData();
		super.trimToSize();

	}

	// endregion

}
