package io.jimble.core.lifecycle;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * アプリケーションのライフサイクル
 *
 * <p>
 * 「Web として動いているのかバッチとして動いているのか」と
 * 「停止が始まっているか」を、下位モジュールから参照するための場所。
 * </p>
 *
 * <p>
 * バックグラウンドスレッドは {@link #isStopped()} を見てループを抜ける。
 * </p>
 */
public final class AppLifecycle {

	/**
	 * 実行形態
	 */
	public enum Mode {

		/** Webサーバー */
		WEB,

		/** バッチ */
		BATCH,

	}

	/* 実行形態 */
	private static volatile Mode mode = Mode.WEB;

	/* 停止判定 */
	private static final AtomicBoolean STOPPED = new AtomicBoolean(false);

	/**
	 * コンストラクタ
	 */
	private AppLifecycle () {

	}

	/**
	 * 実行形態
	 *
	 * @return	実行形態
	 */
	public static Mode mode () {

		return mode;

	}

	/**
	 * 実行形態を設定する
	 *
	 * <p>起動時に1回だけ呼ぶ。</p>
	 *
	 * @param value	実行形態
	 */
	public static void mode (Mode value) {

		mode = Objects.requireNonNull(value, "mode");

	}

	/**
	 * バッチとして動いているか
	 *
	 * @return	バッチなら true
	 */
	public static boolean isBatch () {

		return mode == Mode.BATCH;

	}

	/**
	 * 停止が始まっているか
	 *
	 * @return	停止していれば true
	 */
	public static boolean isStopped () {

		return STOPPED.get();

	}

	/**
	 * 停止を通知する
	 */
	public static void markStopped () {

		STOPPED.set(true);

	}

	/**
	 * 状態を戻す
	 *
	 * <p>テスト用。</p>
	 */
	public static void reset () {

		mode = Mode.WEB;
		STOPPED.set(false);

	}

}
