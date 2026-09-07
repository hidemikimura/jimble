package io.jimble.gradle.run;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.StringJoiner;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * クラスパスだけを持つ JAR
 *
 * <p>
 * クラスパスが長くなるとコマンドラインの上限に当たる
 * （Windows で約 32,000 文字、環境によってはもっと短い）。
 * <b>モジュールと依存が増えたある日に、いきなり起動しなくなる</b>形の問題なので、
 * 長い場合は {@code Class-Path} だけを書いた JAR を経由する。
 * </p>
 */
final class ClasspathJar {

	private ClasspathJar () {}

	/**
	 * 作る
	 *
	 * @param classpath	クラスパス
	 * @param dir		置き場所
	 * @return	JAR
	 * @throws IOException	作れなかった場合
	 */
	static File create (String classpath, File dir) throws IOException {

		File jar = new File(dir, "build/tmp/jimble-run-classpath.jar");

		Files.createDirectories(jar.getParentFile().toPath());

		Manifest manifest = new Manifest();
		Attributes attributes = manifest.getMainAttributes();
		attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
		attributes.put(Attributes.Name.CLASS_PATH, toClassPathHeader(classpath));

		try (OutputStream out = Files.newOutputStream(jar.toPath());
			 JarOutputStream jarOut = new JarOutputStream(out, manifest)) {
			// 中身は要らない。マニフェストだけの JAR である
			jarOut.flush();
		}

		return jar;

	}

	/**
	 * {@code Class-Path} ヘッダの形にする
	 *
	 * @param classpath	クラスパス
	 * @return	ヘッダ
	 */
	private static String toClassPathHeader (String classpath) {

		StringJoiner joiner = new StringJoiner(" ");

		for (String entry : classpath.split(File.pathSeparator)) {

			if (entry.isEmpty()) {
				continue;
			}

			File file = new File(entry);
			String url = file.toURI().toString();

			// ディレクトリは末尾に "/" が要る
			joiner.add(file.isDirectory() && !url.endsWith("/") ? url + "/" : url);

		}

		return joiner.toString();

	}

}
