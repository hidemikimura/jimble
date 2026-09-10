package io.jimble.load;

import java.net.URI;
import java.time.Duration;
import java.util.List;

/**
 * 負荷をかけて測る（要件 NF-P-08）
 *
 * <h2>サーバーと負荷は別のプロセスで動かす</h2>
 * <p>
 * <b>同じプロセスで測ると、負荷をかける側が相手の CPU を奪う。</b>
 * 台のコアが少ないほど効いて、<b>相手が遅いのか自分が邪魔しているのかが分からなくなる</b>。
 * そこで {@code server} と {@code client} を分けてある（{@code load.sh} が両方を起こす）。
 * </p>
 *
 * <pre>
 * # サーバーを起こす（そのまま前面で待つ）
 * jimble-load server helidon 9010
 * jimble-load server jimble  9011
 *
 * # 叩く
 * jimble-load client http://127.0.0.1:9011/ 64 3 10
 * #                  叩き先                  接続 温め 測る（秒）
 * </pre>
 *
 * <h2>数字の読み方</h2>
 * <p>
 * <b>rps は信じてよい。p99 は「詰まっていないときの速さ」である</b>
 * （理由は {@link Generator} の説明）。
 * </p>
 * <p>
 * <b>行の末尾に {@code ▲接続>コア} が付いていたら、その行は読まないこと。</b>
 * 負荷をかける側の限界を測っている（{@link Result#overCoresNote}）。
 * </p>
 */
public final class LoadMain {

	/** 使い方 */
	private static final String USAGE = """
		使い方:
		  server <helidon|jimble|jimble-nolog> <ポート>
		  client <URL> [接続数] [温める秒数] [測る秒数]
		""";

	private LoadMain () {
	}

	/**
	 * 入口
	 *
	 * @param args	引数
	 * @throws Exception	失敗した場合
	 */
	public static void main (String[] args) throws Exception {

		if (args.length < 2) {
			System.err.print(USAGE);
			System.exit(2);
			return;
		}

		switch (args[0]) {

			case "server" -> server(args[1], Integer.parseInt(args[2]));
			case "client" -> client(args);

			default -> {
				System.err.print(USAGE);
				System.exit(2);
			}

		}

	}

	/**
	 * サーバーを起こして待つ
	 *
	 * @param target	相手の名前
	 * @param port		ポート
	 * @throws Exception	失敗した場合
	 */
	private static void server (String target, int port) throws Exception {

		Runnable stop = switch (target) {
			case "helidon" -> Targets.helidon(port);
			case "jimble" -> Targets.jimble(port);
			case "jimble-nolog" -> Targets.jimble(port, false);
			default -> throw new IllegalArgumentException("知らない相手です: " + target);
		};

		Runtime.getRuntime().addShutdownHook(new Thread(stop));

		/*
		 * <b>用意ができたことを1行で伝える。</b>
		 * {@code load.sh} はこれを待ってから叩き始める——
		 * 決め打ちで待つと、<b>遅い台では温まる前に測り始める</b>。
		 */
		System.out.println("READY " + target + " " + port);
		System.out.flush();

		Thread.currentThread().join();

	}

	/**
	 * 叩く
	 *
	 * @param args	引数
	 * @throws Exception	失敗した場合
	 */
	private static void client (String[] args) throws Exception {

		URI url = URI.create(args[1]);

		int connections = args.length > 2 ? Integer.parseInt(args[2]) : 64;
		int warmup = args.length > 3 ? Integer.parseInt(args[3]) : 3;
		int measure = args.length > 4 ? Integer.parseInt(args[4]) : 10;

		Result result = Generator.run(
			url, connections, Duration.ofSeconds(warmup), Duration.ofSeconds(measure));

		/*
		 * <b>コア数はここで数える。</b>負荷をかける側と相手は同じ台にいるので、
		 * このプロセスから見えるコア数がそのまま「取り合っている数」である
		 * （印の意味は {@link Result#overCoresNote}）。
		 */
		System.out.println(result.toRow(url.toString(), Runtime.getRuntime().availableProcessors()));

		if (result.errors() > 0) {
			/*
			 * <b>失敗があったら終了コードで言う。</b>表の中の小さな数字は見落とされる。
			 * 落ちているアプリはたいてい速いので、<b>いちばん良い数字と一緒に出てくる</b>
			 */
			System.exit(1);
		}

	}

	/**
	 * 何本の接続で試すかの既定
	 *
	 * @return	接続数
	 */
	public static List<Integer> defaultConnections () {

		return List.of(1, 8, 64, 256);

	}

}
