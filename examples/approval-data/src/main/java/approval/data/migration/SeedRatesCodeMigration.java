package approval.data.migration;

import db.approval_data_example.ApprovalDataExample;
import db.approval_data_example.table.rate.Rate;

import io.jimble.db.DB;
import io.jimble.db.sql.SQL;
import io.jimble.db.migration.code.AbstractCodeMigration;
import io.jimble.util.data.Data;

import java.util.List;

/**
 * レートの端数を丸める（コードマイグレーション。要件 F-G-18）
 *
 * <h2>SQL のマイグレーションと何が違うか</h2>
 * <p>
 * <b>Java で書けることが違う。</b>
 * 「既にある値を読んで、計算して、書き戻す」ような移行は SQL では書きにくい。
 * 逆に、テーブルを作る・列を足すは SQL のほうがよい。
 * </p>
 *
 * <p>
 * <b>1回しか走らない。</b>{@code migration_code} に版が残るので、
 * 起動のたびに実行されることはない。落ちたら {@code error} で残り、
 * <b>起動そのものが止まる</b>（黙って先へ進まない）。
 * </p>
 *
 * <p>
 * <b>状態はメイン DB にしか残らない。</b>
 * サブ DB のデータを詰め替えるコードマイグレーションを書いても、
 * 「やった」という記録はメインの {@code migration_code} に入る。
 * </p>
 */
public class SeedRatesCodeMigration extends AbstractCodeMigration {

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * <b>登録した順ではなく、この値の順に走る。</b>
	 * 日付にしておくと、あとから間に挟むときに困らない。
	 * </p>
	 */
	@Override
	protected int versionYyyyMmDd () {

		return 20260910;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void execute () {

		DB db = ApprovalDataExample.db();

		List<Data> rates = db.selectList(SQL.select().from(Rate.instance()).orderBy(Rate.id));

		int rounded = 0;

		for (Data rate : rates) {

			long value = rate.getLong(Rate.value);
			long next = Math.round(value / 10.0) * 10;

			if (value == next) {
				continue;
			}

			db.update(SQL.update(Rate.instance())
				.set(Rate.value, next)
				.where(Rate.id.eq(rate.getLong(Rate.id))));

			rounded++;

		}

		/*
		 * 何をしたかを残す。migration_code.execute_info に JSON で入る。
		 *
		 * <b>addErrorInfo を1件でも入れると、完走しても error 扱いになる。</b>
		 * 「動いたが結果が変」を成功にしないためである。
		 */
		addExecuteInfo("rounded", rounded);
		addExecuteInfo("total", rates.size());

	}

}
