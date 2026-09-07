package io.jimble.util.data.async;

import io.jimble.util.log.Log;

import java.util.concurrent.locks.ReentrantLock;

/**
 * 遅延読み込みの状態
 *
 * <p>
 * {@link AsyncData} と {@link AsyncList} で同じ状態遷移を持つので、ここに1つ置く。
 * <b>「一度だけ読む」の判定を2箇所に書かないため</b>であって、抽象化のためではない。
 * </p>
 *
 * <h2>移送元から直したところ</h2>
 * <ol>
 *   <li><b>{@code isLoaded} が volatile でなかった。</b>
 *       ロックの外で読む速い経路があるので、<b>別のスレッドが
 *       「読み込み済み」だけを見て、中身が空のまま先に進める。</b>
 *       仮想スレッドでリクエストごとに走る前提だと、これは実際に起きる</li>
 *   <li><b>読み込みの本体より先に「読み込み済み」を立てていた。</b>
 *       自分自身からの再入を止めるためだが、上と組み合わさると
 *       <b>他のスレッドが「読み込み済みで中身が空」を見る。</b>
 *       再入は別のフラグで止め、<b>「読み込み済み」は最後に立てる</b></li>
 *   <li><b>公平ロックだった</b>（{@code new ReentrantLock(true)}）。
 *       公平ロックは待ち行列を厳密に守るぶん遅い。ここは中で1回クエリを投げるだけで、
 *       順番が入れ替わっても困らない</li>
 * </ol>
 */
final class AsyncState {

	/* ロック */
	private final ReentrantLock lock = new ReentrantLock();

	/* 読み込み済みか（ロックの外から読むので volatile） */
	private volatile boolean loaded = false;

	/* 読み込みに失敗したか（要件 F-A-09） */
	private volatile boolean failed = false;

	/* 読み込みの最中か（ロックの中でだけ触る。自分自身からの再入を止める） */
	private boolean loading = false;

	/**
	 * 読み込み済みか
	 *
	 * @return	読み込み済みの場合 = true
	 */
	boolean isLoaded () {

		return loaded;

	}

	/**
	 * 読み込みに失敗したか
	 *
	 * @return	失敗した場合 = true
	 */
	boolean isFailed () {

		return failed;

	}

	/**
	 * 読み込み済みにする（読み込みは起こさない）
	 */
	void markLoaded () {

		lock.lock();
		try {
			loaded = true;
		} finally {
			lock.unlock();
		}

	}

	/**
	 * 一度だけ読む
	 *
	 * <p>
	 * 失敗しても<b>読み込み済みにする。</b>
	 * そうしないと、参照されるたびに落ちるクエリを投げ続ける（要件 F-A-09）。
	 * </p>
	 *
	 * @param owner	失敗したときにログへ出す対象
	 * @param body	読み込みの本体
	 */
	void loadOnce (Object owner, Body body) {

		if (loaded) {
			return;
		}

		lock.lock();

		try {

			if (loaded) {
				return;
			}

			if (loading) {
				// 読み込みの最中に、その中から自分が参照された
				return;
			}

			loading = true;

			try {

				body.run();

			} catch (Exception ex) {

				failed = true;
				Log.error(ex, "遅延読み込みに失敗しました: %s".formatted(describe(owner)));

			} finally {

				loading = false;
				// 中身を入れ終えてから立てる
				loaded = true;

			}

		} finally {

			lock.unlock();

		}

	}

	/**
	 * ログ用の名前
	 *
	 * <p>
	 * <b>{@code toString()} を呼ばない。</b>読み込みに失敗した対象を
	 * 表示しようとして、また読み込みに行くことになる。
	 * </p>
	 *
	 * @param owner	対象
	 * @return	名前
	 */
	private static String describe (Object owner) {

		return owner == null ? "(不明)" : owner.getClass().getName();

	}

	/**
	 * 読み込みの本体
	 */
	@FunctionalInterface
	interface Body {

		/**
		 * 読む
		 *
		 * @throws Exception	読み込みに失敗した場合
		 */
		void run () throws Exception;

	}

}
