package io.jimble.cli.internal;

import io.jimble.db.cli.JimbleDbCli;

import java.nio.file.Path;

/**
 * jimble の CLI（要件 F-X-01 / F-G-08）
 *
 * <pre>
 * jimble new &lt;name&gt;              プロジェクトの雛形を作る
 * jimble migrate                 未適用のマイグレーションを適用する
 * jimble codegen &lt;ソースルート&gt;   テーブル定義のコードを生成する
 * jimble version                 版を表示する
 * </pre>
 *
 * <p>
 * <b>{@code new} 以外はアプリの設定とリソースが要る。</b>
 * {@code application.conf} とマイグレーションの SQL はクラスパスから探すので、
 * プロジェクトの中から {@code ./gradlew migrate} を使うか、
 * アプリのクラスパスを足して起動すること。
 * </p>
 *
 * <p>
 * <b>ここに処理を書かない。</b>{@code migrate} / {@code codegen} の中身は
 * {@link JimbleDbCli} にある。入口を1つにするためだけのクラスである。
 * </p>
 */
public final class JimbleCli {

	/** 終了コード：失敗 */
	private static final int EXIT_FAILURE = 1;

	private JimbleCli () {}

	/**
	 * エントリポイント
	 *
	 * @param args	コマンドライン引数
	 */
	public static void main (String[] args) {

		if (args.length == 0) {
			usage();
			System.exit(EXIT_FAILURE);
			return;
		}

		switch (args[0]) {

			case "new" -> newProject(args);

			case "version", "--version", "-v" -> System.out.println("jimble " + Version.current());

			case "help", "--help", "-h" -> usage();

			/*
			 * 委譲する。JimbleDbCli は自分で System.exit する。
			 */
			case "migrate", "codegen" -> JimbleDbCli.main(args);

			default -> {
				System.err.println("知らないコマンドです: " + args[0]);
				usage();
				System.exit(EXIT_FAILURE);
			}

		}

	}

	/**
	 * 雛形を作る
	 *
	 * @param args	コマンドライン引数
	 */
	private static void newProject (String[] args) {

		if (args.length < 2) {
			System.err.println("プロジェクト名を指定してください（例: jimble new my-blog）");
			System.exit(EXIT_FAILURE);
			return;
		}

		try {

			Path root = NewCommand.run(args[1], Path.of("").toAbsolutePath());

			System.out.println("""
				%s を作りました。

				  cd %s
				  gradle wrapper          Gradle ラッパーを用意する（1回だけ）
				  ./gradlew run           起動する
				  ./gradlew jimbleRun     直したらリロードで反映される形で起動する

				  http://localhost:9000/
				"""
				.formatted(root.getFileName(), root.getFileName()));

		} catch (IllegalArgumentException ex) {

			// 名前が使えない。何が悪いかはメッセージに入っている（要件 F-X-05）
			System.err.println(ex.getMessage());
			System.exit(EXIT_FAILURE);

		} catch (Exception ex) {

			System.err.println("雛形を作れませんでした: " + ex.getMessage());
			System.exit(EXIT_FAILURE);

		}

	}

	/**
	 * 使い方を出す
	 */
	private static void usage () {

		System.err.println("""
			使い方: jimble <コマンド>

			  new <name>              プロジェクトの雛形を作る
			  migrate                 未適用のマイグレーションを適用する
			  codegen <ソースルート>   テーブル定義のコードを生成する（例: src/main/java）
			  version                 版を表示する

			migrate と codegen はアプリの設定（application.conf）とマイグレーションの
			SQL をクラスパスから探す。プロジェクトの中から ./gradlew migrate を使うか、
			アプリのクラスパスを足して起動すること。
			""");

	}

}
