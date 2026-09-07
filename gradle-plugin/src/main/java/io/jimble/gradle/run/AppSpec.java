package io.jimble.gradle.run;

import java.io.File;
import java.util.List;

/**
 * アプリの起動の指定（要件 F-X-02 / D-77）
 *
 * <p>
 * アプリは<b>Gradle と同じ JVM の中で</b>動く（{@link AppRunner}）。
 * だから「どの {@code java} で動かすか」も「JVM 引数」もここには無い。
 * ヒープなどを変えたいときは {@code org.gradle.jvmargs} を使う。
 * </p>
 *
 * @param mainClass		起動クラス
 * @param classpath		クラスパス（区切りは {@link File#pathSeparator}）
 * @param env			環境名
 * @param port			待ち受けポート
 * @param args			コマンドライン引数
 * @param workingDir	作業ディレクトリ
 */
record AppSpec(
	String mainClass
	, String classpath
	, String env
	, int port
	, List<String> args
	, File workingDir
) {}
