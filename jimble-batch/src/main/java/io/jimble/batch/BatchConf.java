package io.jimble.batch;

import java.time.Duration;
import io.jimble.util.conf.Conf;
import io.jimble.util.log.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * バッチの設定
 *
 * <pre>
 * batch {
 *   scheduler_id            = ""    # このインスタンスの識別子（既定：ホスト名）
 *   heartbeat               = 3s    # 実行中であることを知らせる間隔
 *   alive                   = 10s   # これだけ更新が無ければ「実行中ではない」とみなす
 *   cancel_check            = 3s    # 中断指示を見にいく間隔
 *   progress                = 5s    # チャンクバッチが進み具合を履歴に書く間隔
 *   all_stop                = 1h    # 全停止フラグが効く時間
 * }
 * </pre>
 */
public final class BatchConf {

	/** 設定キー：インスタンス識別子 */
	public static final String KEY_SCHEDULER_ID = "batch.scheduler_id";

	/** 設定キー：ハートビート間隔 */
	public static final String KEY_HEARTBEAT = "batch.heartbeat";

	/** 設定キー：生存とみなす時間 */
	public static final String KEY_ALIVE = "batch.alive";

	/** 設定キー：中断指示を見にいく間隔 */
	public static final String KEY_CANCEL_CHECK = "batch.cancel_check";

	/** 設定キー：進み具合を履歴に書く間隔 */
	public static final String KEY_PROGRESS = "batch.progress";

	/** 設定キー：全停止フラグが効く時間 */
	public static final String KEY_ALL_STOP = "batch.all_stop";

	private BatchConf () {}

	/**
	 * このインスタンスの識別子
	 *
	 * <p>
	 * <b>移送元は MAC アドレスを使っていた。</b>
	 * コンテナでは MAC が起動のたびに変わり、しかも同じ値が別のホストで出ることもある。
	 * この値は「どのインスタンスが何本バッチを持っているか」を数えるキーなので、
	 * ぶれると同時実行数の割り振り（{@link AbstractBatch} 参照）が狂う。
	 * </p>
	 *
	 * <p>設定が無ければホスト名を使う。</p>
	 *
	 * @return	識別子
	 */
	public static String schedulerId () {

		String configured = Conf.conf().getString(KEY_SCHEDULER_ID, "");

		if (!configured.isEmpty()) {
			return configured;
		}

		return hostName();

	}

	/**
	 * ホスト名
	 *
	 * @return	ホスト名（取れなければ {@code "unknown"}）
	 */
	private static String hostName () {

		return HostName.VALUE;

	}

	/**
	 * ホスト名（プロセスで1回だけ引く）
	 *
	 * <p>
	 * <b>{@code InetAddress.getLocalHost()} を最初に使わない。</b>あれはホスト名を取ったあと、
	 * <b>その名前を名前解決する。</b>ホスト名が {@code /etc/hosts} に無い macOS では、
	 * mDNS の時間切れまで <b>5 秒ほど止まる</b>。最初の {@link BatchExecutor#parseArgs} がこれを踏み、
	 * 同時に起動したバッチは全員そこで待たされていた（{@code registerExecuteInfo} で待っているように見えた）。
	 * 欲しいのは名前だけなので、名前解決をしない取り方を先に試す。
	 * </p>
	 *
	 * <p>
	 * どれも OS の {@code gethostname()} と同じ値を返すので、<b>これまでの scheduler_id と変わらない</b>
	 * （{@code getLocalHost().getHostName()} も、名前解決に成功したときはこの値を返していた）。
	 * </p>
	 *
	 * <p>
	 * 持ち方をクラスの初期化にしたのは、<b>同時に呼ばれても引くのを1回にするため</b>である。
	 * 前は volatile の欄だったので、最初に同時に来た全員がそれぞれ引いていた。
	 * </p>
	 */
	private static final class HostName {

		static final String VALUE = HostNames.lookup(List.of(
			HostNames::fromProc
			, HostNames::fromCommand
			, HostNames::fromInetAddress));

		private HostName () {}

	}

	/**
	 * ホスト名の取り方（状態を持たない。テストから呼んでも {@link HostName} を初期化しない）
	 */
	static final class HostNames {

		private HostNames () {}

		/**
		 * 名前解決をしない取り方だけで引く（テストから）
		 *
		 * @return	ホスト名（取れなければ {@code "unknown"}）
		 */
		static String withoutResolve () {

			return lookup(List.of(HostNames::fromProc, HostNames::fromCommand));

		}

		/**
		 * 順に試して、最初に取れた名前を返す
		 *
		 * @param sources	取り方（null・空・例外は「取れなかった」）
		 * @return	ホスト名（どれでも取れなければ {@code "unknown"}）
		 */
		static String lookup (List<Supplier<String>> sources) {

			for (Supplier<String> source : sources) {

				try {
					String name = source.get();
					if (name != null && !name.isBlank()) {
						return name.strip();
					}
				} catch (Exception ex) {
					// 次の取り方へ
				}

			}

			Log.warn("ホスト名を取得できませんでした。batch.scheduler_id を設定してください");
			return "unknown";

		}

		/**
		 * Linux：カーネルが持っている名前（名前解決しない）
		 */
		static String fromProc () {

			Path path = Path.of("/proc/sys/kernel/hostname");

			if (!Files.isReadable(path)) {
				return null;
			}

			try {
				return Files.readString(path);
			} catch (IOException ex) {
				return null;
			}

		}

		/**
		 * macOS・Windows ほか：{@code hostname} コマンド（名前解決しない）
		 */
		static String fromCommand () {

			try {

				Process process = new ProcessBuilder("hostname")
					.redirectError(ProcessBuilder.Redirect.DISCARD)
					.start();

				if (!process.waitFor(3, TimeUnit.SECONDS)) {
					process.destroyForcibly();
					return null;
				}

				if (process.exitValue() != 0) {
					return null;
				}

				try (var in = process.getInputStream()) {
					String out = new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
					// 1行目だけ（余計な出力が混ざっても名前にしない）
					return out.lines().findFirst().orElse(null);
				}

			} catch (IOException ex) {
				return null;
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return null;
			}

		}

		/**
		 * 最後の手段：{@code InetAddress.getLocalHost()}（名前解決するので遅いことがある）
		 */
		static String fromInetAddress () {

			try {
				return InetAddress.getLocalHost().getHostName();
			} catch (IOException ex) {
				return null;
			}

		}

	}

	/**
	 * ハートビート間隔
	 *
	 * @return	間隔
	 */
	public static Duration heartbeat () {

		return Conf.conf().getDuration(KEY_HEARTBEAT, Duration.ofSeconds(3));

	}

	/**
	 * 生存とみなす時間
	 *
	 * @return	時間
	 */
	public static Duration alive () {

		return Conf.conf().getDuration(KEY_ALIVE, Duration.ofSeconds(10));

	}

	/**
	 * 中断指示を見にいく間隔
	 *
	 * @return	間隔
	 */
	public static Duration cancelCheck () {

		return Conf.conf().getDuration(KEY_CANCEL_CHECK, Duration.ofSeconds(3));

	}

	/**
	 * 進み具合を履歴に書く間隔（秒）
	 *
	 * <p>
	 * {@link AbstractChunkBatch} が {@code batch_history.execute_info} を
	 * 上書きする間隔である。0 以下にすると<b>チャンクごとに毎回書く。</b>
	 * </p>
	 *
	 * @return	間隔
	 */
	public static Duration progress () {

		return Conf.conf().getDuration(KEY_PROGRESS, Duration.ofSeconds(5));

	}

	/**
	 * 全停止フラグが効く時間
	 *
	 * @return	時間
	 */
	public static Duration allStop () {

		return Conf.conf().getDuration(KEY_ALL_STOP, Duration.ofHours(1));

	}

}
