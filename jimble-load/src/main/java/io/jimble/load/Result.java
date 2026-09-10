package io.jimble.load;

import java.net.URI;
import java.util.Arrays;

/**
 * 測った結果（要件 NF-P-08）
 *
 * @param url			叩き先
 * @param connections	同時に張った接続の数
 * @param seconds		測った時間（秒）
 * @param requests		返ってきた本数（2xx のみ）
 * @param errors		2xx でなかった、または例外になった本数
 * @param rps			秒あたりの本数
 * @param p50			レイテンシの中央値（ミリ秒）
 * @param p90			レイテンシの 90 パーセンタイル（ミリ秒）
 * @param p99			レイテンシの 99 パーセンタイル（ミリ秒）
 * @param max			レイテンシの最大（ミリ秒）
 */
public record Result (
	URI url
	, int connections
	, double seconds
	, long requests
	, long errors
	, double rps
	, double p50
	, double p90
	, double p99
	, double max
) {

	/**
	 * 記録から作る
	 *
	 * @param url			叩き先
	 * @param connections	同時に張った接続の数
	 * @param elapsedNanos	かかった時間
	 * @param latencies		1本ごとのレイテンシ（ナノ秒）
	 * @param errors		失敗した本数
	 * @return	結果
	 */
	static Result of (URI url, int connections, long elapsedNanos, long[] latencies, long errors) {

		Arrays.sort(latencies);

		double seconds = elapsedNanos / 1_000_000_000.0;

		return new Result(
			url
			, connections
			, seconds
			, latencies.length
			, errors
			, seconds == 0 ? 0 : latencies.length / seconds
			, percentile(latencies, 0.50)
			, percentile(latencies, 0.90)
			, percentile(latencies, 0.99)
			, percentile(latencies, 1.00)
		);

	}

	/**
	 * パーセンタイル（ミリ秒）
	 *
	 * @param sorted	並べ替え済みのレイテンシ
	 * @param ratio		割合
	 * @return	ミリ秒
	 */
	private static double percentile (long[] sorted, double ratio) {

		if (sorted.length == 0) {
			return 0;
		}

		int index = (int) Math.ceil(ratio * sorted.length) - 1;

		return sorted[Math.clamp(index, 0, sorted.length - 1)] / 1_000_000.0;

	}

	/** 接続数が台のコア数を超えている行に付ける印 */
	public static final String OVER_CORES = "▲";

	/**
	 * 表の1行にする
	 *
	 * @param label	名前
	 * @param cores	台のコア数。0 以下なら印を付けない
	 * @return	1行
	 */
	public String toRow (String label, int cores) {

		return "%-24s %5d本  %10.0f rps   p50 %6.2f   p90 %6.2f   p99 %6.2f   max %7.2f   %s%s"
			.formatted(
				label
				, connections
				, rps
				, p50
				, p90
				, p99
				, max
				/*
				 * <b>失敗は必ず出す。</b>0 でも「0」と書く——
				 * 書かないと、<b>出ていないのか無いのか</b>が読み手に分からない
				 */
				, errors == 0 ? "失敗なし" : "失敗 " + errors + " ★"
				, overCoresNote(connections, cores)
			);

	}

	/**
	 * 接続数が台のコア数を超えていることを言う（要件 NF-P-08）
	 *
	 * <p>
	 * <b>この行は相手ではなく自分（負荷生成）の限界を測っている疑いがある。</b>
	 * 負荷をかける側は<b>接続1本につきスレッドを1本</b>使い、相手と同じ台で動く。
	 * コア数を超えたところでは、相手が何であっても同じ数字に寄っていく——
	 * <b>10 コアの台で 64 接続を測ったら、素の helidon より jimble のほうが速いことになった</b>
	 * （走るたびに符号が変わる）。
	 * </p>
	 *
	 * <p>
	 * <b>行そのものは消さない。</b>接続数を固定にしてあるのは、
	 * <b>台をまたいで同じ表を並べられる</b>ようにするためである
	 * （台ごとに行が変わると、Mac と CI の結果を比べられなくなる）。
	 * 消すかわりに印を付けて、<b>読み手が読み飛ばせる</b>ようにする。
	 * </p>
	 *
	 * @param connections	同時に張った接続の数
	 * @param cores			台のコア数。0 以下なら何も言わない
	 * @return	印。付けないときは空文字
	 */
	public static String overCoresNote (int connections, int cores) {

		if (cores <= 0 || connections <= cores) {
			return "";
		}

		return "   %s接続>コア%d".formatted(OVER_CORES, cores);

	}

}
