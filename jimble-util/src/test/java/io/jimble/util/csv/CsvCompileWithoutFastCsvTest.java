package io.jimble.util.csv;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FastCSV がクラスパスに無くても、CSV の公開 API を使うコードをコンパイルできる
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * FastCSV は jimble-util の {@code implementation} の依存なので、<b>アプリのコンパイル時のクラスパスには無い</b>。
 * 2.1.4 の {@code CsvReader} は、非公開のコンストラクタの引数に FastCSV の型を出していた。javac は呼び出し先を決めるとき、
 * 引数の数が合うコンストラクタを {@code private} も含めて全部比べ、その引数の型のクラスを読みにいくので、
 * アプリの {@code new CsvReader(file, 1, 2)} が「de.siegmar.fastcsv.reader.CsvReader のクラス・ファイルが見つかりません」で落ちた。
 * </p>
 *
 * <p>
 * <b>jimble 自身のビルドやテストでは FastCSV がクラスパスにあるので、気づけない。</b>
 * ここでは jimble-util のクラスだけをクラスパスに置き、アプリと同じ条件でコンパイルする。
 * </p>
 */
class CsvCompileWithoutFastCsvTest {

	/** 公開のコンストラクタとメソッドを全部呼ぶ、アプリ側のコード */
	private static final String CONSUMER = """
		package app;

		import io.jimble.util.csv.CsvReader;
		import io.jimble.util.csv.CsvWriter;

		import java.io.ByteArrayInputStream;
		import java.io.ByteArrayOutputStream;
		import java.io.File;
		import java.io.OutputStreamWriter;
		import java.io.StringReader;
		import java.nio.charset.StandardCharsets;

		class Consumer {

			void read (File file) {

				new CsvReader(file);
				new CsvReader(file, "UTF-8");
				new CsvReader(file, 1, 2);
				new CsvReader(new ByteArrayInputStream(new byte[0]), "UTF-8");
				new CsvReader(new ByteArrayInputStream(new byte[0]), 1, 2, "UTF-8");
				new CsvReader(new StringReader(""));

				try (CsvReader csv = new CsvReader(new StringReader(""), 1, 2)) {
					while (csv.next()) {
						csv.getString("a");
						csv.getString(0);
						csv.getInt("a");
						csv.getLong(0);
						csv.getDate("a");
						csv.getHeader();
						csv.getRecord();
						csv.getKeyIndex("a");
					}
				}

			}

			void write (File file) {

				new CsvWriter(file);
				new CsvWriter(file, "UTF-8");
				new CsvWriter(new ByteArrayOutputStream());
				new CsvWriter(new ByteArrayOutputStream(), "UTF-8");

				try (CsvWriter csv = new CsvWriter(new OutputStreamWriter(new ByteArrayOutputStream(), StandardCharsets.UTF_8))) {
					csv.setWithBom(true).setLineSeparatorLF().setRecordSeparator(',').setQuoteCharacter('"');
					csv.writeLine("a", 1, null);
					csv.isBodyWritten();
				}

			}

		}
		""";

	@Test
	@DisplayName("FastCSV がクラスパスに無くても、CsvReader / CsvWriter を使うコードをコンパイルできる")
	void compilesWithoutFastCsv (@TempDir Path dir) throws Exception {

		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		assertNotNull(compiler, "javac が使えません（JDK で実行してください）");

		// jimble-util のクラスだけ（FastCSV の jar は置かない）
		String jimbleUtil = new File(CsvReader.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
		assertFalse(jimbleUtil.contains("fastcsv"), jimbleUtil);

		Path source = dir.resolve("app/Consumer.java");
		Files.createDirectories(source.getParent());
		Files.writeString(source, CONSUMER, StandardCharsets.UTF_8);

		Path classes = Files.createDirectories(dir.resolve("classes"));

		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

		try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {

			boolean ok = compiler.getTask(null, files, diagnostics
				, List.of("-classpath", jimbleUtil, "-d", classes.toString(), "-proc:none", "-encoding", "UTF-8")
				, null, files.getJavaFileObjects(source.toFile())).call();

			StringBuilder errors = new StringBuilder();
			diagnostics.getDiagnostics().stream()
				.filter(d -> d.getKind() == javax.tools.Diagnostic.Kind.ERROR)
				.forEach(d -> errors.append(d).append('\n'));

			assertTrue(ok, "FastCSV が無いとコンパイルできません（公開クラスのシグネチャに FastCSV の型が出ている）:\n" + errors);

		}

	}

}
