package io.jimble.batch;

import io.jimble.batch.status.BatchMasterStatus;
import io.jimble.db.DB;
import io.jimble.db.data.SQLParameterList;
import io.jimble.db.dialect.Sqls;
import io.jimble.util.log.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * バッチの登録（要件 F-B-09）
 *
 * <p>
 * <b>明示的に登録する。</b>パッケージ名を渡してクラスパスを走査することはしない。
 * </p>
 *
 * <pre>
 * // アプリの起動コード
 * BatchRegistry.add(RssFetchBatch::new);
 * BatchRegistry.add(SitemapBatch::new);
 * BatchRegistry.sync(DBUtil.getMainDB());
 * </pre>
 *
 * <h2>なぜ走査をやめたか</h2>
 * <p>
 * 移送元は Guava の {@code ClassPath} でパッケージを走査し、
 * {@code AbstractBatch} を継承しているクラスを拾っていた。
 * </p>
 * <ul>
 *   <li>原則2（アノテーション・リフレクション走査を使わない）に反する</li>
 *   <li>要件 NF-P-03（起動時のリフレクションスキャンを行わない）に反する。
 *       <b>起動時間がクラスパスの大きさに比例する</b></li>
 *   <li><b>どのバッチが登録されるかがコードから読めない。</b>
 *       パッケージを移動しただけでバッチが消える</li>
 *   <li>jar の中と外で走査の結果が変わりうる</li>
 * </ul>
 * <p>コードマイグレーションで同じ判断をしている（D-19）。</p>
 *
 * <h2>登録されていないクラスは実行できない</h2>
 * <p>
 * 移送元は CLI の {@code class=} を {@code Class.forName} に渡していた。
 * <b>コマンドラインの文字列から任意のクラスを組み立てていた</b>ことになる。
 * jimble は登録済みのものだけを引く。
 * </p>
 */
public final class BatchRegistry {

	/* 登録されたバッチ（登録順を保つ） */
	private static final Map<String, Supplier<AbstractBatch>> BATCHES = new LinkedHashMap<>();

	/* 登録の排他 */
	private static final ReentrantLock LOCK = new ReentrantLock();

	private BatchRegistry () {}

	/**
	 * バッチを登録する
	 *
	 * <p>
	 * 登録のたびに1つ作って、クラス名とバッチ名を確かめる。
	 * <b>実行のたびに作り直す</b>ので、ここで作ったものは持ち回さない（原則3）。
	 * </p>
	 *
	 * @param supplier	バッチを作るもの（{@code MyBatch::new}）
	 * @return	登録したバッチのクラス名
	 */
	public static String add (Supplier<AbstractBatch> supplier) {

		AbstractBatch batch = supplier.get();

		if (batch == null) {
			throw new IllegalArgumentException("バッチを作れませんでした");
		}

		/*
		 * getCanonicalName() ではなく getName() を使う。
		 * 移送元は canonical name をキーにしていたが、これは
		 * 入れ子クラスで "a.B.C"（Class.forName では読めない形）になり、
		 * 無名クラスでは null になる。
		 */
		String className = batch.getClass().getName();

		LOCK.lock();

		try {

			if (BATCHES.containsKey(className)) {
				throw new IllegalStateException("バッチが二重に登録されています: " + className);
			}

			BATCHES.put(className, supplier);

		} finally {

			LOCK.unlock();

		}

		return className;

	}

	/**
	 * 登録に無いバッチを {@code nothing} にする
	 *
	 * <p>
	 * <b>移送元は {@code created_at} を世代マーカーにしていた</b>
	 * （「今回書いた時刻と違う行 = 消えたバッチ」）。
	 * MySQL の {@code datetime} は秒までしか持たないので、
	 * <b>同じ秒のうちに2回反映すると、消えたバッチを取りこぼす。</b>
	 * クラス名で直接判定する。
	 * </p>
	 *
	 * @param db		DB
	 * @param batches	登録されているバッチ
	 */
	private static void markDisappeared (DB db, List<AbstractBatch> batches) {

		StringBuilder placeholders = new StringBuilder();
		List<Object> params = new ArrayList<>();

		params.add(BatchMasterStatus.nothing.name());

		for (AbstractBatch batch : batches) {

			if (!placeholders.isEmpty()) {
				placeholders.append(", ");
			}

			placeholders.append('?');
			params.add(batch.getClass().getName());

		}

		params.add(BatchMasterStatus.nothing.name());

		db.update("""
				UPDATE batch_master SET
					status = ?
				WHERE
					class_name NOT IN (%s)
					AND status <> ?
			""".formatted(placeholders)
			, params.toArray());

	}

	/**
	 * バッチを作る
	 *
	 * @param className	クラス名
	 * @return	バッチ（登録されていなければ null）
	 */
	public static AbstractBatch create (String className) {

		Supplier<AbstractBatch> supplier;

		LOCK.lock();
		try {
			supplier = BATCHES.get(className);
		} finally {
			LOCK.unlock();
		}

		return supplier == null ? null : supplier.get();

	}

	/**
	 * 登録されているクラス名
	 *
	 * @return	クラス名（登録順）
	 */
	public static Collection<String> classNames () {

		LOCK.lock();
		try {
			return List.copyOf(BATCHES.keySet());
		} finally {
			LOCK.unlock();
		}

	}

	/**
	 * 登録を全部消す（テスト用）
	 */
	public static void clear () {

		LOCK.lock();
		try {
			BATCHES.clear();
		} finally {
			LOCK.unlock();
		}

	}

	/**
	 * 登録されたバッチをマスタに反映する
	 *
	 * <p>
	 * <b>コードから消えたバッチは {@code nothing} にする。</b>行そのものは残すので、
	 * 過去の履歴からたどれる。
	 * </p>
	 *
	 * @param db	DB
	 * @return	反映した件数
	 */
	public static int sync (DB db) {

		List<AbstractBatch> batches = new ArrayList<>();

		for (String className : classNames()) {

			AbstractBatch batch = create(className);

			if (batch == null || batch.isScheduler()) {
				continue;
			}

			batches.add(batch);

		}

		if (batches.isEmpty()) {
			return 0;
		}

		Date now = new Date();
		List<List<Object>> paramsList = new ArrayList<>();

		for (AbstractBatch batch : batches) {

			paramsList.add(new SQLParameterList(
				batch.getClass().getName()
				, batch.batchName()
				, BatchMasterStatus.enable.name()
				/*
				 * 列は int（MySQL / PostgreSQL とも）。boolean のまま渡すと
				 * PostgreSQL が「integer の列に boolean」と言って落ちる（要件 F-D-30）。
				 * DbScheduler の WHERE も = 1 で見ているので、int に揃える。
				 */
				, batch.isScheduler() ? 1 : 0
				, batch.isEnableScheduler() ? 1 : 0
				, batch.cron()
				, batch.cron()
				, batch.allowConcurrentExecutionCount()
				, batch.allowConcurrentExecutionCount()
				, batch.defaultBatchSettings()
				, batch.defaultBatchSettings()
				, now
			));

		}

		db.insertBatch("""
				INSERT INTO batch_master (
					class_name
					, name
					, status
					, is_scheduler
					, is_enable_scheduler
					, cron
					, default_cron
					, allow_concurrent_execution
					, default_allow_concurrent_execution
					, settings
					, default_settings
					, created_at
				) VALUES (
					?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
				)
			""" + Sqls.upsert(db.dialect(), List.of("class_name")
				, "name", "is_scheduler", "is_enable_scheduler", "default_cron"
				, "default_settings", "default_allow_concurrent_execution", "created_at")
			, paramsList);

		if (db.isError()) {
			Log.error("バッチマスタの更新に失敗しました: %s".formatted(db.getError()));
			return 0;
		}

		markDisappeared(db, batches);

		return batches.size();

	}

}
