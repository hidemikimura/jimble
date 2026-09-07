package io.jimble.util.number;

import java.util.Random;

public class NumberUtil {

	/**
	 * 範囲内の乱数を取得する
	 *
	 * @param min   最小値
	 * @param max   最大値
	 * @return  乱数
	 */
	public static int random (int min, int max) {

		return new Random().nextInt((max - min) + 1) + min;

	}

	/**
	 * 範囲内の乱数を取得する
	 *
	 * @param min   最小値
	 * @param max   最大値
	 * @return  乱数
	 */
	public static long random (long min, long max) {

		return new Random().nextLong((max - min) + 1) + min;

	}


}
