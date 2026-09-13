package approval.jobs.batch;

import io.jimble.batch.AbstractChunkBatch;
import io.jimble.batch.BatchArgs;
import io.jimble.batch.KeyPagingReader;
import io.jimble.db.DB;
import io.jimble.util.data.Data;

import java.util.Iterator;
import java.util.List;

/**
 * 古い申請を書庫へ移す（<b>一定件数ずつ</b>。要件 F-B-12）
 *
 * <h2>ここで見せたいこと：全部を1トランザクションで抱えないこと</h2>
 * <p>
 * 「古いものを全部移す」を素直に書くと、
 * <b>100万件を1つのトランザクションで抱える</b>ことになる。
 * 途中で落ちれば全部戻り、走っている間じゅう他を待たせる。
 * </p>
 * <p>
 * {@link AbstractChunkBatch} は {@link #chunkSize()} 件ごとに確定する。
 * 途中で落ちても<b>そこまでは入っている</b>し、どこまで入ったかは履歴に残る。
 * </p>
 *
 * <h2>読む先と書く先を別のテーブルにしてある</h2>
 * <p>
 * <b>{@link #reader(BatchArgs, DB)} の {@code db} と
 * {@link #write(List, DB)} の {@code db} は別のインスタンス</b>である。
 * 同じにはできない——チャンクを1つ確定した時点でコネクションがプールへ返るので、
 * <b>同じ {@code DB} で読んでいると読みかけが死ぬ。</b>
 * </p>
 * <p>
 * 渡されたものをそのまま使っていれば、これは起きない。
 * ここで書庫を別テーブルにしてあるのは、<b>その形が目に見えるようにする</b>ためでもある。
 * </p>
 *
 * <h2>読み方はキー順のページング</h2>
 * <p>
 * カーソルでも書けるが、<b>カーソルは開いている間ずっとコネクションを1本押さえる。</b>
 * {@link KeyPagingReader} はページごとに引き直すので、間はコネクションを持たない。
 * </p>
 * <p>
 * <b>読んでいる列を書き換えないこと。</b>ここでは {@code id} で進み、
 * 書き換えるのは別テーブルへの INSERT と、元テーブルからの DELETE である。
 * {@code id} そのものは触らないので、ページングは狂わない。
 * </p>
 *
 * <pre>
 * java -cp app.jar approval.jobs.JobsBatch env=local class=approval.jobs.batch.ArchiveChunkBatch days=365
 * </pre>
 */
public class ArchiveChunkBatch extends AbstractChunkBatch<Data> {

	/** 設定キー：何日より古いものを移すか */
	public static final String KEY_DAYS = "days";

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String batchName () {

		return "古い申請の書庫入れ";

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * <b>{@code isScheduler()} は書かない。</b>
	 * あれは「これはスケジューラ自身のバッチか」であって、
	 * 「スケジューラに載せるか」ではない。true にすると
	 * <b>マスタにも履歴にも載らなくなる</b>。
	 * cron で回すのに要るのは、cron を書くことだけである。
	 * </p>
	 */
	@Override
	public String cron () {

		// 毎朝4時
		return "0 4 * * *";

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Data defaultBatchSettings () {

		return new Data().putData(KEY_DAYS, 365);

	}

	/**
	 * 1かたまりの件数
	 *
	 * <p>
	 * サンプルなので小さくしてある（動かすと何回かに分かれるのが見える）。
	 * 実際には数百〜数千にする。
	 * </p>
	 *
	 * @return	件数
	 */
	@Override
	public int chunkSize () {

		return 3;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Iterator<Data> reader (BatchArgs args, DB db) {

		long days = days(args);

		/*
		 * SQL は丸ごとここに書く。
		 *
		 * KeyPagingReader は文字列を組み立てない。
		 * 組み立てさせると<b>何番目の ? に何が入るのか読めなくなる</b>（原則1）。
		 * lastKey と limit をどこへ置くかも、ここで決めている。
		 */
		return KeyPagingReader.of("id", 0L, chunkSize(), (lastKey, limit) -> db.selectList("""
				SELECT
					id, staff_id, amount, needed_on, status, created_at
				FROM
					request
				WHERE
					status IN ('approved', 'rejected')
					AND created_at < NOW() - %s
					AND id > ?
				ORDER BY
					id ASC
				LIMIT ?
			""".formatted("interval '" + days + " days'")
			, lastKey, limit));

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * 既定は素通しなので、書かなくてもよい。
	 * ここでは<b>「よける」ことができる</b>のを見せるために置いてある——
	 * {@code null} を返した1件は書かれず、履歴の {@code chunk_filtered} に数えられる。
	 * </p>
	 */
	@Override
	protected Data process (Data item) {

		// 0 円の申請は書庫にも入れない（説明のための例）
		if (item.getLong("amount") <= 0) {
			return null;
		}

		return item;

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * この呼び出し1回が1トランザクションである。抜けた時点で確定する。
	 * </p>
	 */
	@Override
	protected void write (List<Data> items, DB db) {

		for (Data item : items) {

			db.insert("""
					INSERT INTO request_archive (
						id, staff_id, amount, needed_on, status, created_at, archived_at
					) VALUES (
						?, ?, ?, ?, ?, ?, NOW()
					)
				"""
				, item.getLong("id")
				, item.getLong("staff_id")
				, item.getLong("amount")
				/*
				 * 日付と日時は get() で生のまま渡す。
				 *
				 * getString() で取ると varchar として渡り、PostgreSQL が
				 * 「timestamp の列に character varying」と言って落ちる。
				 * Data は LinkedHashMap なので、get() が
				 * ドライバから返った型（Timestamp / Date）をそのまま返す。
				 */
				, item.get("needed_on")
				, item.getString("status")
				, item.get("created_at"));

			/*
			 * 1文ごとに見る。
			 *
			 * db.insert() は失敗しても例外を投げず、
			 * db.isError() は<b>直前の1文しか覚えていない</b>。
			 * まとめて最後に1回だけ見ると、途中の失敗を取りこぼす。
			 */
			if (db.isError()) {
				throw new IllegalStateException("書庫に入れられませんでした: id=%d / %s"
					.formatted(item.getLong("id"), String.valueOf(db.getError())));
			}

			db.delete("DELETE FROM request WHERE id = ?", item.getLong("id"));

			if (db.isError()) {
				throw new IllegalStateException("元の申請を消せませんでした: id=%d / %s"
					.formatted(item.getLong("id"), String.valueOf(db.getError())));
			}

		}

	}

	/**
	 * 何日より古いものを移すか
	 *
	 * @param args	引数
	 * @return	日数
	 */
	private long days (BatchArgs args) {

		String fromArgs = args.cliArgs().getStringOptional(KEY_DAYS);

		if (!fromArgs.isEmpty()) {
			return Long.parseLong(fromArgs);
		}

		return settings() == null ? 365 : settings().getLong(KEY_DAYS);

	}

}
