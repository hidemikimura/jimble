package io.jimble.util.internal;

import io.jimble.util.log.Log;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 同じ警告をプロセスで1度だけ出す（内部。要件 D-192）
 *
 * <p>
 * 2.0 で例外になる書き方・黙って効かない書き方を、1.5 のうちに<b>ログで知らせる</b>ために使う。
 * 毎回出すと、同じ行がリクエストの数だけ並んで、ほかのログが読めなくなる。
 * </p>
 *
 * <p>
 * 鍵ごとに1度。<b>どこで呼ばれたか</b>（jimble の外の最初の呼び出し元）を添える——
 * 警告を見た人（と AI）が、直す行へそのまま行けるようにする。
 * </p>
 */
public final class WarnOnce {

	private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

	private WarnOnce () {
	}

	/**
	 * 警告する（その鍵で初めてのときだけ）
	 *
	 * @param key		鍵（警告の種類）
	 * @param message	内容（直し方まで書く）
	 * @return	今回出したら true
	 */
	public static boolean warn (String key, String message) {

		if (!WARNED.add(key)) {
			return false;
		}

		String caller = caller();
		Log.warn(caller == null ? message : message + "（呼び出し元: " + caller + "）");
		return true;

	}

	/**
	 * もう出したか
	 *
	 * @param key	鍵
	 * @return	出していれば true
	 */
	public static boolean warned (String key) {

		return WARNED.contains(key);

	}

	/**
	 * 忘れる（テスト用）
	 */
	public static void reset () {

		WARNED.clear();

	}

	/*
	 * jimble の外の最初の呼び出し元。テストから呼ばれたときはテストのクラスになる。
	 */
	private static String caller () {

		return StackWalker.getInstance().walk(frames -> frames
			.filter(f -> !f.getClassName().startsWith("io.jimble.")
				|| f.getClassName().endsWith("Test")
				|| f.getClassName().contains("Test$"))
			.filter(f -> !f.getClassName().startsWith("java.") && !f.getClassName().startsWith("jdk.")
				&& !f.getClassName().startsWith("sun."))
			.findFirst()
			.map(f -> f.getClassName() + "." + f.getMethodName() + ":" + f.getLineNumber())
			.orElse(null));

	}

}
