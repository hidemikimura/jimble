package io.jimble.gradle.run;

import java.io.File;
import java.util.List;

/**
 * アプリの起動の指定（要件 F-X-02）
 *
 * @param javaExecutable	使う {@code java}。<b>Gradle デーモンの JVM ではなく</b>
 *						プロジェクトのツールチェーンのもの
 * @param mainClass		起動クラス
 * @param classpath		クラスパス（区切りは {@link File#pathSeparator}）
 * @param env			環境名
 * @param port			待ち受けポート
 * @param jvmArgs		JVM 引数
 * @param args			コマンドライン引数
 * @param workingDir	作業ディレクトリ
 */
record AppSpec(
	String javaExecutable
	, String mainClass
	, String classpath
	, String env
	, int port
	, List<String> jvmArgs
	, List<String> args
	, File workingDir
) {}
