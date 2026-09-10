package io.jimble.batch;

import io.jimble.util.data.Data;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * キー順に少しずつ読む（{@link AbstractChunkBatch} 用）
 *
 * <p>
 * <b>「前回の最後のキーより大きいものを N 件」を繰り返す。</b>
 * ページとページの間はコネクションを持たないので、
 * 何時間走っても DB のコネクションを1本占有し続けることがない。
 * </p>
 *
 * <pre>
 * KeyPagingReader.of("id", 0L, 500, (lastKey, limit) -&gt; db.selectList("""
 *         SELECT id, amount FROM request
 *         WHERE status = ? AND id &gt; ?
 *         ORDER BY id ASC
 *         LIMIT ?
 *     """, "approved", lastKey, limit));
 * </pre>
 *
 * <p>
 * SQL は<b>丸ごと呼ぶ側に書かせる。</b>
 * ここで文字列を組み立てると、どこに何番目の {@code ?} が入るのかが読めなくなる
 * （原則1）。{@code lastKey} と {@code limit} をどこに置くかも呼ぶ側が決める。
 * </p>
 *
 * <h2>キーの条件</h2>
 * <ul>
 *   <li><b>一意であること。</b>同じ値が2行あると、境目でこぼれるか、二重に読む</li>
 *   <li><b>SQL の {@code ORDER BY} と揃っていること。</b>
 *       昇順で読むなら {@code キー &gt; ?} と {@code ORDER BY キー ASC}</li>
 *   <li><b>走っている間に書き換わらないこと。</b>
 *       読んだ後に更新するなら、キーではない列を更新する</li>
 * </ul>
 *
 * <p>
 * キーが進まないまま満杯のページが返ったときは例外にする。
 * <b>黙って無限ループするより落ちたほうがよい</b>（原則5）。
 * </p>
 */
public final class KeyPagingReader implements Iterator<Data> {

	/**
	 * 1ページ引く
	 */
	@FunctionalInterface
	public interface Page {

		/**
		 * 引く
		 *
		 * @param lastKey	前のページの最後のキー（1ページ目は開始キー）
		 * @param limit		件数
		 * @return	行（無ければ空。null でもよい）
		 */
		List<Data> fetch (Object lastKey, int limit);

	}

	/* キーの列名 */
	private final String keyColumn;

	/* 1ページの件数 */
	private final int pageSize;

	/* ページを引くもの */
	private final Page page;

	/* 前のページの最後のキー */
	private Object lastKey;

	/* いま読んでいるページ */
	private Iterator<Data> current = Collections.emptyIterator();

	/* もうページは無い */
	private boolean drained = false;

	/**
	 * コンストラクタ
	 *
	 * @param keyColumn	キーの列名
	 * @param startKey	開始キー（これより大きいものから読む）
	 * @param pageSize	1ページの件数
	 * @param page		ページを引くもの
	 */
	private KeyPagingReader (String keyColumn, Object startKey, int pageSize, Page page) {

		this.keyColumn = keyColumn;
		this.lastKey = startKey;
		this.pageSize = pageSize;
		this.page = page;

	}

	/**
	 * 作る
	 *
	 * @param keyColumn	キーの列名（{@code SELECT} に含めること）
	 * @param startKey	開始キー（これより大きいものから読む。連番なら 0）
	 * @param pageSize	1ページの件数
	 * @param page		ページを引くもの
	 * @return	読み取り
	 */
	public static KeyPagingReader of (String keyColumn, Object startKey, int pageSize, Page page) {

		if (keyColumn == null || keyColumn.isBlank()) {
			throw new IllegalArgumentException("キーの列名を指定してください");
		}

		if (pageSize <= 0) {
			throw new IllegalArgumentException("1ページの件数は 1 以上にしてください: %d".formatted(pageSize));
		}

		if (page == null) {
			throw new IllegalArgumentException("ページを引くものを指定してください");
		}

		return new KeyPagingReader(keyColumn, startKey, pageSize, page);

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasNext () {

		if (current.hasNext()) {
			return true;
		}

		if (drained) {
			return false;
		}

		fetchNextPage();

		return current.hasNext();

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Data next () {

		if (!hasNext()) {
			throw new NoSuchElementException("もう読むものがありません");
		}

		Data row = current.next();

		if (!row.containsKey(keyColumn)) {
			throw new IllegalStateException(
				"キーの列が結果にありません: %s（SELECT に入れてください）".formatted(keyColumn));
		}

		lastKey = row.get(keyColumn);

		return row;

	}

	/**
	 * 次のページを引く
	 */
	private void fetchNextPage () {

		Object previousKey = lastKey;

		List<Data> rows = page.fetch(lastKey, pageSize);

		if (rows == null || rows.isEmpty()) {
			drained = true;
			current = Collections.emptyIterator();
			return;
		}

		/*
		 * 満杯で返ってきたのにキーが1つも進んでいない。
		 * WHERE の向きと ORDER BY が食い違っているか、キーが一意でない。
		 * このまま回すと同じページを永久に引き続ける。
		 */
		Data last = rows.getLast();

		if (rows.size() >= pageSize && Objects.equals(previousKey, last.get(keyColumn))) {
			throw new IllegalStateException(
				"キーが進みません: %s = %s（WHERE の向きと ORDER BY、キーの一意性を確かめてください）"
					.formatted(keyColumn, String.valueOf(previousKey)));
		}

		/*
		 * 満たなかった＝これが最後のページ。
		 * もう1回引きにいかない（空を確かめるためだけの1クエリを省く）。
		 */
		if (rows.size() < pageSize) {
			drained = true;
		}

		current = rows.iterator();

	}

}
