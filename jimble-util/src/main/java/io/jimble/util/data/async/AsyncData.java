package io.jimble.util.data.async;

import io.jimble.util.data.Data;
import io.jimble.util.data.TableNest;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * 遅延読み込みデータ（要件 F-A-01〜05 / F-A-09 / F-A-10）
 *
 * <p>
 * <b>参照された時点ではじめて {@link #load()} が走る。</b>
 * {@link Data} を継承しているので、読み込んだあとは普通の {@code Data} として扱える。
 * </p>
 *
 * <pre>
 * public class SiteFeeds extends AsyncData {
 *
 *     private final long siteId;
 *
 *     public SiteFeeds (long siteId) { this.siteId = siteId; }
 *
 *     &#64;Override protected Data load () { return db.select(...); }
 *     &#64;Override protected void setData (Data data) { putAllData(data); }
 *     &#64;Override protected String hashKey () { return "SiteFeeds:" + siteId; }
 * }
 * </pre>
 *
 * <h2>読み込みを起こさずに見られるもの</h2>
 * <p>
 * 先読み（要件 F-A-06。Phase 2）はツリーを走査して未読み込みの枝を集める。
 * <b>走査そのものが読み込みを起こしては意味がない</b>ので、次は読み込みを起こさない
 * （要件 F-A-04 / F-A-10）。
 * </p>
 * <ul>
 *   <li>{@link #isLoaded()} / {@link #isLoadFailed()}</li>
 *   <li>{@link #loadedValues()}</li>
 *   <li>{@link #batchKey()} / {@link #batchId()}</li>
 *   <li>{@link #hashCode()} / {@link #equals(Object)}（要件 F-A-05）</li>
 *   <li><b>{@link #toString()}</b>（要件 F-D-27）</li>
 * </ul>
 *
 * <h2>移送元から直したところ</h2>
 * <ol>
 *   <li>読み込み済みフラグの見え方（{@link AsyncState} 参照）</li>
 *   <li><b>{@code toString()} がツリーを丸ごと読み込んでいた。</b>
 *       {@code Data.toString()} は JSON 化なので、
 *       <b>ログに1行出しただけで枝という枝に SQL が飛ぶ。</b>要件 F-D-27 に反する</li>
 *   <li><b>{@code containsValue()} が読み込みを起こしていなかった。</b>
 *       未読み込みのときに<b>黙って false を返す</b></li>
 *   <li><b>読み込みに失敗したことを外から知る方法が無かった</b>（要件 F-A-09）</li>
 *   <li>{@code setRelationData()} が抽象メソッドで、
 *       関連データを持たない実装にも空メソッドを書かせていた</li>
 * </ol>
 */
public abstract class AsyncData extends Data implements Async {

	private static final long serialVersionUID = 1L;

	/* 読み込みの状態 */
	private final AsyncState state = new AsyncState();

	/* 読み込んだデータ */
	private transient Data data;

	/* カスタム追加データ（使わないことも多いので、要るまで作らない） */
	private transient Data customAddData;

	// region 実装するもの（要件 F-A-02）

	/**
	 * データを読む
	 *
	 * @return	読んだデータ
	 * @throws Exception	読み込みに失敗した場合
	 */
	protected abstract Data load () throws Exception;

	/**
	 * 読んだデータを自分に反映する
	 *
	 * @param data	読んだデータ
	 * @throws Exception	反映に失敗した場合
	 */
	protected abstract void setData (Data data) throws Exception;

	/**
	 * 関連データを反映する
	 *
	 * <p>{@link #setData(Data)} のあとに呼ばれる。要らなければ書かなくてよい。</p>
	 *
	 * @param data	読んだデータ
	 * @throws Exception	反映に失敗した場合
	 */
	protected void setRelationData (Data data) throws Exception {}

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
	 * @return	id ごとのデータ（先読みしないなら null）
	 * @throws Exception	読み込みに失敗した場合
	 */
	protected Map<Object, Data> loadBatch (List<Object> ids) throws Exception {

		return null;

	}

	/**
	 * 同じものかどうかを決めるキー
	 *
	 * <p>
	 * {@link #hashCode()} と {@link #equals(Object)} はこれだけを見る。
	 * <b>比較しただけで読み込みが走らないため</b>（要件 F-A-05）。
	 * </p>
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
	 * <p>
	 * 失敗しても例外は投げず、ログを出して先に進む（移送元と同じ）。
	 * <b>失敗したことを知りたい側のために、状態だけは残す。</b>
	 * </p>
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
	 * <p>null を返すと先読みの対象外になり、個別に {@link #load()} される。</p>
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
	 * すでに持っている値だけを返す（読み込みは起こさない）
	 *
	 * @return	値の一覧
	 */
	public Collection<Object> loadedValues () {

		/* Data.values() は beforeAccess() を通すので、通らない口を使う */
		return rawValues();

	}

	// endregion

	// region 読み込み

	/**
	 * 読んだデータ
	 *
	 * @return	データ（未読み込みなら null）
	 */
	protected Data getData () {

		return this.data;

	}

	/**
	 * 読み込む（一度だけ）
	 */
	private void loadData () {

		state.loadOnce(this, () -> {

			this.data = load();

			if (this.data != null) {
				setData(this.data);
				setRelationData(this.data);
			}

		});

	}

	/**
	 * 読み込まずにデータを入れる
	 *
	 * <p>
	 * 先読み（要件 F-A-06。Phase 2）がまとめて引いた結果を流し込む口である
	 * （要件 F-A-10）。<b>すでに読み込み済みなら何もしない。</b>
	 * </p>
	 *
	 * @param data	データ
	 */
	public void putData (Data data) {

		state.loadOnce(this, () -> {

			this.data = data;

			if (data != null) {
				setData(data);
				setRelationData(data);
			}

		});

	}

	// endregion

	// region テーブルネストの組み直し（要件 F-A-11）

	/**
	 * テーブルネストの形を変えて取り出す（要件 F-A-11）
	 *
	 * <p>
	 * <b>自分は変わらない。</b>組み直した {@link Data} を新しく作って返す。
	 * 中の子（{@link Async}）は<b>同じインスタンスのまま</b>入るので、
	 * JSON の書き出しはそこから普通に降りていける。
	 * </p>
	 *
	 * <pre>
	 * // setData が flattenTable でも extractTableData でも、どちらの形にもできる
	 * post.reshape(TableNest.ON)    // {"post": {"id": 1, ...}, "comments": [...]}
	 * post.reshape(TableNest.OFF)   // {"id": 1, ..., "comments": [...]}
	 * </pre>
	 *
	 * <p>
	 * どのキーがどのテーブルの列かは<b>生の読み込みデータから導く</b>
	 * （{@link TableShape}）。導けないとき（{@code putData(null)} を受けた、
	 * 自由 SQL でテーブルネストしていない）は<b>そのまま返す</b>。
	 * </p>
	 *
	 * <p><b>読み込みを起こす。</b></p>
	 *
	 * @param nest	どちらの形にするか
	 * @return	組み直した {@link Data}（{@link TableNest#AS_IS} なら自分自身）
	 */
	public Data reshape (TableNest nest) {

		if (nest == null || nest == TableNest.AS_IS) {
			return this;
		}

		// 中身が要るので読む
		loadData();

		return TableShape.reshape(this, TableShape.tablesOf(this.data), nest);

	}

	// endregion

	// region カスタム追加データ

	/**
	 * カスタム追加データを足す
	 *
	 * @param key	キー
	 * @param value	値
	 */
	public void addCustomAddData (String key, Object value) {

		if (customAddData == null) {
			customAddData = new Data();
		}

		customAddData.put(key, value);

	}

	/**
	 * カスタム追加データ
	 *
	 * @return	カスタム追加データ
	 */
	protected Data getCustomAddData () {

		if (customAddData == null) {
			customAddData = new Data();
		}

		return customAddData;

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
	 * {@code Map#equals} は entrySet を見るので、
	 * <b>比較しただけで読み込みが走ってしまう。</b>
	 * </p>
	 */
	@Override
	public boolean equals (Object obj) {

		if (this == obj) {
			return true;
		}

		if (!(obj instanceof AsyncData asyncData)) {
			return false;
		}

		return Objects.equals(hashKey(), asyncData.hashKey());

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * <b>未読み込みなら読み込まない</b>（要件 F-D-27）。
	 * ログに1行出しただけで枝という枝に SQL が飛ぶ、ということが起きないようにする。
	 * </p>
	 */
	@Override
	public String toString () {

		if (!state.isLoaded()) {
			return "%s(未読み込み)".formatted(getClass().getSimpleName());
		}

		if (state.isFailed()) {
			return "%s(読み込み失敗)".formatted(getClass().getSimpleName());
		}

		return super.toString();

	}

	// endregion

	// region 触られたら読む（要件 D-157）

	/**
	 * 中身に触られたら読む
	 *
	 * <p>
	 * <b>ここ1つだけを上書きしている。</b>
	 * {@link Data} が {@code LinkedHashMap} の公開メソッドを全部ここへ通すので、
	 * <b>上書きし忘れたメソッドという概念が無くなる。</b>
	 * </p>
	 *
	 * <p>
	 * <b>以前は読み取りを1つずつ上書きしていた</b>ので、
	 * {@code remove} / {@code putIfAbsent} / {@code computeIfAbsent} や、
	 * Java 21 で生えた {@code putFirst} / {@code pollLastEntry} は
	 * <b>未読み込みの空マップをそのまま触っていた</b>——
	 * 落ちも警告も出さず、<b>「その項目は無い」と答えていた</b>。
	 * </p>
	 *
	 * <p>
	 * <b>読み込みの最中に呼ばれても回らない。</b>
	 * {@code load()} が返した中身を {@code setData} が {@code put} で入れるので、
	 * ここは<b>読み込みの中からも呼ばれる</b>——
	 * {@code AsyncState} が「読み込みの最中」を持っていて、そこで止まる。
	 * </p>
	 */
	@Override
	protected void beforeAccess () {

		loadData();

	}

	// endregion

}
