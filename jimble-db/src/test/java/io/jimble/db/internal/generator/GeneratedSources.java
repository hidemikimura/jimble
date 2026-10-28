package io.jimble.db.internal.generator;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 生成した Java を、テストの中でコンパイルする
 *
 * <p>コンパイルしたクラスは {@code <生成先>/__classes} に置く。</p>
 */
final class GeneratedSources {

	private GeneratedSources () {}

	/**
	 * 生成された Java をコンパイルする
	 *
	 * <p>クラスパスはこのテストが動いているものをそのまま使う。</p>
	 *
	 * @param sourceRoot	生成先
	 * @return	エラーメッセージ（無ければ空）
	 */
	static List<String> compile (Path sourceRoot) {

		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		if (compiler == null) {
			// JRE で動かしている場合。ここは JDK 前提なので起きない
			throw new IllegalStateException("javac が使えません（JDK で実行してください）");
		}

		List<File> sources = new ArrayList<>();
		try (Stream<Path> paths = Files.walk(sourceRoot)) {
			paths.filter(p -> p.toString().endsWith(".java")).forEach(p -> sources.add(p.toFile()));
		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}

		assertFalse(sources.isEmpty(), "生成された Java がない");

		File classesDir = sourceRoot.resolve("__classes").toFile();
		classesDir.mkdirs();

		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

		try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {

			List<String> options = List.of(
				"-classpath", System.getProperty("java.class.path")
				, "-d", classesDir.getAbsolutePath()
				, "-encoding", "UTF-8"
				, "-nowarn"
			);

			compiler.getTask(
				null
				, fileManager
				, diagnostics
				, options
				, null
				, fileManager.getJavaFileObjectsFromFiles(sources)
			).call();

		} catch (IOException ex) {
			throw new IllegalStateException(ex);
		}

		List<String> errors = new ArrayList<>();
		diagnostics.getDiagnostics().stream()
			.filter(d -> d.getKind() == javax.tools.Diagnostic.Kind.ERROR)
			.forEach(d -> errors.add(d.toString()));

		return errors;

	}

}
