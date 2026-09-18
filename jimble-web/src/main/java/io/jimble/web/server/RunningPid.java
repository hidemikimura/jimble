package io.jimble.web.server;

import io.jimble.util.log.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;

/**
 * 動いている印のファイル（{@code RUNNING_PID_{ポート番号}}）
 *
 * <p>
 * サーバーが待ち受けを始めたら、<b>アプリの jar があるディレクトリ</b>に
 * {@code RUNNING_PID_8080} のような名前でファイルを置く。中身はプロセス ID である。
 * 止めるときに消す。
 * </p>
 *
 * <p>
 * 止めるスクリプト（{@code kill $(cat RUNNING_PID_8080)}）や
 * 「いま動いているか」を見るためにある。
 * ポート番号が名前に入っているので、<b>同じディレクトリから複数立てても
 * 互いの印を消さない</b>。
 * </p>
 *
 * <p>
 * <b>jar から動いていないときは置かない</b>（開発中の {@code jimbleRun} やテストは
 * クラスのディレクトリから動く）。置く場所が「jar のディレクトリ」なので、
 * 無いものの隣には置けない。{@code user.dir} に逃がすと、
 * テストのたびにプロジェクトの根に印が残る。
 * </p>
 *
 * <p>
 * <b>置けなくてもサーバーは止めない。</b>印は補助であって、
 * 待ち受けそのものではない。書けなかったことは warn に出す。
 * </p>
 */
final class RunningPid {

	/** ファイル名の頭。あとにポート番号が付く */
	static final String PREFIX = "RUNNING_PID_";

	private RunningPid () {}

	/**
	 * ファイル名
	 *
	 * @param port	ポート番号
	 * @return	ファイル名
	 */
	static String fileName (int port) {

		return PREFIX + port;

	}

	/**
	 * クラスの入っている jar のディレクトリ
	 *
	 * @param cls	クラス（普通はアプリのクラス）
	 * @return	jar のディレクトリ。jar から動いていなければ null
	 */
	static Path jarDirectory (Class<?> cls) {

		try {

			CodeSource source = cls.getProtectionDomain().getCodeSource();

			if (source == null || source.getLocation() == null) {
				return null;
			}

			Path location = Path.of(source.getLocation().toURI());

			// ディレクトリなら jar ではない（build/classes から動いている）
			if (!Files.isRegularFile(location)) {
				return null;
			}

			return location.toAbsolutePath().getParent();

		} catch (Exception ex) {
			// 場所の取れないクラスローダもある。印を置けないだけで、動かない理由にはしない
			return null;
		}

	}

	/**
	 * 印を置く
	 *
	 * <p>
	 * jar から動いていなければ何もしない。
	 * 前回の印が残っていたら（正常に止まらなかったとき）warn を出して上書きする。
	 * </p>
	 *
	 * @param appClass	アプリのクラス（jar の場所を決める）
	 * @param port		ポート番号
	 * @return	置いたファイル。置かなかった・置けなかったときは null
	 */
	static Path create (Class<?> appClass, int port) {

		Path directory = jarDirectory(appClass);

		if (directory == null) {
			Log.debug("jar から動いていないので %s は置きません".formatted(fileName(port)));
			return null;
		}

		return create(directory, port);

	}

	/**
	 * 印を置く
	 *
	 * @param directory	置くディレクトリ
	 * @param port		ポート番号
	 * @return	置いたファイル。置けなかったときは null
	 */
	static Path create (Path directory, int port) {

		Path file = directory.resolve(fileName(port));

		if (Files.exists(file)) {
			Log.warn("%s が残っていました。前回は正常に止まらなかった可能性があります。上書きします".formatted(file));
		}

		try {

			Files.writeString(file, Long.toString(ProcessHandle.current().pid()), StandardCharsets.UTF_8);
			Log.info("%s を置きました（PID %d）".formatted(file, ProcessHandle.current().pid()));

			return file;

		} catch (IOException ex) {
			Log.warn("%s を置けませんでした: %s".formatted(file, ex));
			return null;
		}

	}

	/**
	 * 印を消す
	 *
	 * @param file	{@link #create} が返したファイル。null なら何もしない
	 */
	static void delete (Path file) {

		if (file == null) {
			return;
		}

		try {

			if (Files.deleteIfExists(file)) {
				Log.info("%s を消しました".formatted(file));
			}

		} catch (IOException ex) {
			Log.warn("%s を消せませんでした: %s".formatted(file, ex));
		}

	}

}
