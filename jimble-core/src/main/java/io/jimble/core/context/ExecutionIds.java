package io.jimble.core.context;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 実行IDの生成
 *
 * <p>
 * 先頭に時刻を置くことで、ログを実行ID順に並べると時系列になる。
 * </p>
 *
 * <h2>1つに書き込む（要件 D-171）</h2>
 * <p>
 * <b>{@code Long.toUnsignedString(x, 36)} を3回呼んで連結していた。</b>
 * 出来上がりは 22 文字ほど（64 byte 前後）なのに、
 * <b>中間の文字列が3つできて 339 byte</b> かかっていた——
 * <b>1リクエストにつき必ず1回</b>通るところである。
 * </p>
 *
 * <p>
 * 36 進の桁を<b>直に1つの入れ物へ書く</b>ようにした。
 * <b>出来上がる文字列は1文字も変わらない</b>——
 * 実行 ID はログを辿る鍵なので、形が変わると
 * <b>それで集計しているものが黙って壊れる</b>（要件 NF-O-01）。
 * </p>
 */
final class ExecutionIds {

	/* 連番 */
	private static final AtomicLong SEQUENCE = new AtomicLong();

	/**
	 * 36 進の数字
	 *
	 * <p>{@code Long.toUnsignedString(x, 36)} と同じ並びである。</p>
	 */
	private static final char[] DIGITS = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray();

	/**
	 * 入れ物の大きさ
	 *
	 * <p>
	 * 時刻が 9 文字ほど、乱数（32 ビット）が 7 文字、連番（16 ビット）が 4 文字、
	 * 区切りが 2 文字。<b>足りなければ伸びるだけ</b>だが、伸ばさないために取ってある。
	 * </p>
	 */
	private static final int CAPACITY = 24;

	/** 1つの数を書くのに要る桁数の上限（{@code long} を 36 進で） */
	private static final int MAX_DIGITS = 13;

	/**
	 * コンストラクタ
	 */
	private ExecutionIds () {

	}

	/**
	 * 実行IDを生成する
	 *
	 * @return	実行ID
	 */
	static String generate () {

		long time = System.currentTimeMillis();
		long random = ThreadLocalRandom.current().nextLong() & 0xFFFF_FFFFL;
		long sequence = SEQUENCE.incrementAndGet() & 0xFFFFL;

		StringBuilder sb = new StringBuilder(CAPACITY);

		append(sb, time);
		sb.append('-');
		append(sb, random);
		sb.append('-');
		append(sb, sequence);

		return sb.toString();

	}

	/**
	 * 36 進で書き足す
	 *
	 * <p>
	 * <b>3つとも負にならない</b>——時刻は正、乱数と連番は下位ビットだけ取っている。
	 * だから符号なしの扱いを分ける必要が無い。
	 * </p>
	 *
	 * @param sb	書き足す先
	 * @param value	値（0 以上）
	 */
	static void append (StringBuilder sb, long value) {

		if (value == 0) {
			sb.append('0');
			return;
		}

		/*
		 * <b>下の桁から出るので、いったん受けてから逆に読む。</b>
		 * 入れ物は呼び出しの中で閉じているので、逃げない
		 * （JIT が確保そのものを消せる）。
		 */
		char[] buffer = new char[MAX_DIGITS];

		int at = MAX_DIGITS;
		long rest = value;

		while (rest > 0) {
			buffer[--at] = DIGITS[(int) (rest % 36)];
			rest /= 36;
		}

		sb.append(buffer, at, MAX_DIGITS - at);

	}

}
