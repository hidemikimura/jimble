package io.jimble.batch;

import io.jimble.db.DB;
import io.jimble.db.DBTransaction;
import io.jimble.db.DBUtil;
import io.jimble.util.log.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 一定件数ずつ読んで、まとめて書くバッチ（要件 F-B-12）
 *
 * <p>
 * 「全部読んでから全部書く」を避けるための形である。
 * 1件ずつ読み、{@link #chunkSize()} 件たまったら
 * <b>そのかたまりだけを1つのトランザクションで書いて確定する。</b>
 * 100万件を1トランザクションで抱えないので、
 * 途中で落ちても<b>そこまでは確定している</b>し、ロールバックも軽い。
 * </p>
 *
 * <pre>
 * public class RequestArchiveBatch extends AbstractChunkBatch&lt;Data&gt; {
 *
 *     &#64;Override public String batchName () { return "古い申請の書庫入れ"; }
 *     &#64;Override public String cron ()      { return "0 4 * * *"; }
 *     &#64;Override public int chunkSize ()    { return 500; }
 *
 *     &#64;Override
 *     protected Iterator&lt;Data&gt; reader (BatchArgs args, DB db) {
 *
 *         return KeyPagingReader.of("id", 0L, chunkSize(), (lastKey, limit) -&gt; db.selectList("""
 *                 SELECT id, amount FROM request
 *                 WHERE status = ? AND id &gt; ?
 *                 ORDER BY id ASC
 *                 LIMIT ?
 *             """, "approved", lastKey, limit));
 *
 *     }
 *
 *     &#64;Override
 *     protected void write (List&lt;Data&gt; items, DB db) {
 *         for (Data item : items) {
 *             db.update("UPDATE request SET status = 'archived' WHERE id = ?", item.getLong("id"));
 *         }
 *     }
 * }
 * </pre>
 *
 * <h2>読む DB と書く DB は別である</h2>
 * <p>
 * {@link #reader(BatchArgs, DB)} に渡る {@code db} と
 * {@link #write(List, DB)} に渡る {@code db} は<b>別のインスタンス</b>である。
 * 同じにはできない。{@code DBTransaction.close()} は {@code db.close()} を呼び、
 * それはコネクションをプールへ返すので、
 * <b>同じ {@code DB} で読んでいるとチャンクを1つ確定した時点で読みかけが死ぬ。</b>
 * 渡された {@code db} をそのまま使っていれば、これは起きない。
 * </p>
 *
 * <h2>読み方は「キー順のページング」を勧める</h2>
 * <p>
 * カーソル（{@code selectListWithFetcher}）でも書けるが、
 * <b>カーソルは開いている間ずっとコネクションを1本押さえる。</b>
 * 数時間かかるバッチだと、その間ずっとである。
 * {@link KeyPagingReader} はページごとに引き直すので、
 * ページとページの間はコネクションを持たない。
 * </p>
 *
 * <h2>途中で落ちたとき</h2>
 * <p>
 * <b>そのチャンクはロールバックし、バッチ全体を失敗にする。</b>
 * 黙って次へ進むことはしない。
 * どこまで確定したかは {@code batch_history.execute_info} に残る
 * （{@link #KEY_WRITTEN} など）。
 * </p>
 *
 * <h2>進み具合</h2>
 * <p>
 * {@link BatchConf#progressSeconds()} ごとに
 * {@code batch_history.execute_info} を書き換える。
 * 長いバッチでも「いま何件目か」が外から見える。
 * </p>
 *
 * @param <T>	1件の型
 */
public abstract class AbstractChunkBatch<T> extends AbstractBatch {

	// region 実行情報のキー

	/** 実行情報のキー：読んだ件数 */
	public static final String KEY_READ = "chunk_read";

	/** 実行情報のキー：{@link #process(Object)} が null を返してよけた件数 */
	public static final String KEY_FILTERED = "chunk_filtered";

	/** 実行情報のキー：確定した件数 */
	public static final String KEY_WRITTEN = "chunk_written";

	/** 実行情報のキー：確定したチャンク数 */
	public static final String KEY_CHUNKS = "chunk_committed";

	/** 実行情報のキー：中断で抜けたか */
	public static final String KEY_CANCELED = "chunk_canceled";

	/** 実行情報のキー：何チャンク目で落ちたか */
	public static final String KEY_FAILED_AT = "chunk_failed_at";

	// endregion

	// region 実装するもの

	/**
	 * 1件ずつ読む
	 *
	 * <p>
	 * 渡された {@code db} を使うこと。
	 * <b>{@link #write(List, DB)} の {@code db} とは別のインスタンス</b>である。
	 * </p>
	 *
	 * <p>キー順のページングなら {@link KeyPagingReader} が使える。</p>
	 *
	 * @param args	引数
	 * @param db	読み取り用の DB
	 * @return	読み取り
	 * @throws Exception	エラー
	 */
	protected abstract Iterator<T> reader (BatchArgs args, DB db) throws Exception;

	/**
	 * 1件を加工する
	 *
	 * <p>
	 * 既定は素通しである。
	 * <b>null を返すと、その1件は書かずによける</b>（{@link #KEY_FILTERED} に数える）。
	 * </p>
	 *
	 * @param item	読んだもの
	 * @return	書くもの（よけるなら null）
	 * @throws Exception	エラー
	 */
	protected T process (T item) throws Exception {

		return item;

	}

	/**
	 * まとめて書く
	 *
	 * <p>
	 * このメソッドの呼び出し1回が1トランザクションである。
	 * 抜けた時点でコミットされる。
	 * </p>
	 *
	 * <p>
	 * <b>{@code db.insert()} などは失敗しても例外を投げない。</b>
	 * {@code db.isError()} にその<b>直前の1文</b>の結果が入るだけである。
	 * 複数文を流すなら、1文ごとに見るか、自分で例外を投げること。
	 * 枠のほうでも最後に一度だけ見ているが、それは最後の1文しか拾えない。
	 * </p>
	 *
	 * @param items	書くもの（空にはならない）
	 * @param db	書き込み用の DB
	 * @throws Exception	エラー
	 */
	protected abstract void write (List<T> items, DB db) throws Exception;

	/**
	 * 1チャンクの件数
	 *
	 * @return	件数
	 */
	public int chunkSize () {

		return 100;

	}

	// endregion

	// region 状態

	/* 読んだ件数 */
	private long read = 0;

	/* よけた件数 */
	private long filtered = 0;

	/* 確定した件数 */
	private long written = 0;

	/* 確定したチャンク数 */
	private long chunks = 0;

	/* 最後に履歴へ書いた時刻 */
	private long lastProgress = 0;

	// endregion

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * 読む → 加工する → まとめて書く、を繰り返すだけである。
	 * ここは差し替えさせない。手を入れるのは
	 * {@link #reader(BatchArgs, DB)} / {@link #process(Object)} / {@link #write(List, DB)}。
	 * </p>
	 */
	@Override
	public final void execute (BatchArgs args) {

		try {
			runChunks(args);
		} catch (RuntimeException | Error ex) {
			throw ex;
		} catch (Exception ex) {
			/*
			 * execute() は検査例外を投げられない。
			 * 包むが、原因はそのまま持たせる。
			 * batch_history には「Caused by」までスタックトレースが残る。
			 */
			throw new RuntimeException(ex);
		}

	}

	/**
	 * チャンクを回す
	 *
	 * @param args	引数
	 * @throws Exception	エラー
	 */
	private void runChunks (BatchArgs args) throws Exception {

		int size = chunkSize();

		if (size <= 0) {
			throw new IllegalStateException(
				"chunkSize() は 1 以上にしてください: %d（%s）".formatted(size, className()));
		}

		/*
		 * 読む DB と書く DB を分ける。
		 * DBUtil.getMainDB() は呼ぶたびに新しい DB を返すので、これで別々になる。
		 */
		DB readDb = DBUtil.getMainDB();
		DB writeDb = DBUtil.getMainDB();

		Iterator<T> reader = reader(args, readDb);

		if (reader == null) {
			throw new IllegalStateException("reader() が null を返しました: %s".formatted(className()));
		}

		List<T> chunk = new ArrayList<>(size);
		boolean canceled = false;

		lastProgress = System.currentTimeMillis();
		putCounts();

		while (reader.hasNext()) {

			// 中断はチャンクの切れ目で見る（要件 F-B-06）
			if (isCancelOrder()) {
				canceled = true;
				break;
			}

			chunk.clear();

			while (chunk.size() < size && reader.hasNext()) {

				read++;

				T processed = process(reader.next());

				if (processed == null) {
					filtered++;
					continue;
				}

				chunk.add(processed);

			}

			/*
			 * 残りが全部よけられた。
			 *
			 * 内側のループは<b>かたまりが満たない限り読み続ける</b>ので、
			 * 途中のよけられた分でここへ来ることはない。
			 * ここへ来るのは<b>読み切ったとき</b>だけである。
			 * それでも空の {@code write()} は呼ばない。
			 */
			if (chunk.isEmpty()) {
				break;
			}

			try {

				writeChunk(writeDb, chunk);

			} catch (Exception ex) {

				// どこまで確定したかを残してから投げる
				putCounts();
				executeInfo().putData(KEY_FAILED_AT, chunks + 1);

				Log.error(ex, "チャンクの書き込みで失敗しました: %s / %d チャンク目 / 確定 %d 件"
					.formatted(className(), chunks + 1, written));

				throw ex;

			}

			written += chunk.size();
			chunks++;

			putCounts();
			flushProgress();

		}

		executeInfo().putData(KEY_CANCELED, canceled);

		/*
		 * ここで履歴に書き足さない。
		 * この後 AbstractBatch の finishHistory() が execute_info を丸ごと書く。
		 */
		putCounts();

		Log.info("チャンクを終えました: %s / 読 %d 件 / よけ %d 件 / 確定 %d 件 / %d チャンク%s"
			.formatted(className(), read, filtered, written, chunks, canceled ? " / 中断" : ""));

	}

	/**
	 * 1チャンクを1トランザクションで書く
	 *
	 * @param db	書き込み用の DB
	 * @param items	書くもの
	 * @throws Exception	エラー
	 */
	private void writeChunk (DB db, List<T> items) throws Exception {

		/*
		 * 抜けるときに close() が走る。
		 * コミット済みならコネクションはもう返っているので何もしない。
		 * 例外で抜けたときはここでロールバックされる。
		 */
		try (DBTransaction transaction = new DBTransaction(db)) {

			transaction.beginTransaction();

			write(List.copyOf(items), db);

			/*
			 * 最後の1文しか見られない（DB は文を流すたびに error を上書きする）。
			 * 何も見ないよりはよい、という程度のもので、
			 * <b>write() の側で見るのが本筋</b>である。
			 */
			if (db.isError()) {
				throw new IllegalStateException(
					"チャンクの書き込みが失敗しています: %s".formatted(String.valueOf(db.getError())));
			}

			transaction.commitEndTransaction();

		}

	}

	/**
	 * 件数を実行情報に入れる（メモリ上だけ。DB は書かない）
	 */
	private void putCounts () {

		executeInfo().putData(KEY_READ, read);
		executeInfo().putData(KEY_FILTERED, filtered);
		executeInfo().putData(KEY_WRITTEN, written);
		executeInfo().putData(KEY_CHUNKS, chunks);

	}

	/**
	 * 進み具合を履歴に書く
	 *
	 * <p>
	 * <b>毎チャンク書かない。</b>チャンクが小さいと更新のほうが重くなる。
	 * {@link BatchConf#progressSeconds()} に1回だけ書く。
	 * </p>
	 *
	 * <p>
	 * 書き込みは<b>チャンクのトランザクションの外</b>で、別の {@link DB} で行う。
	 * 中でやると、そのチャンクが落ちたときに進み具合まで一緒に消える。
	 * </p>
	 *
	 */
	private void flushProgress () {

		if (batchId() <= 0) {
			return;
		}

		long intervalMillis = BatchConf.progressSeconds() * 1000;

		if (System.currentTimeMillis() - lastProgress <= intervalMillis) {
			return;
		}

		lastProgress = System.currentTimeMillis();

		DBUtil.getMainDB().update(
			"UPDATE batch_history SET execute_info = ? WHERE id = ?", executeInfo(), batchId());

	}

}
