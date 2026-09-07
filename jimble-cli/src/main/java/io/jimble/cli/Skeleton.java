package io.jimble.cli;

import java.util.List;

/**
 * 雛形に置くファイルの一覧（要件 F-X-01）
 *
 * <p>
 * <b>クラスパスを走査して集めたりしない</b>（原則2 / NF-P-03）。
 * ここに並んでいるものが、生成されるものの全部である。
 * </p>
 *
 * <p>
 * 置き場所の {@code __PACKAGE_PATH__} も中身と同じように置き換わる。
 * </p>
 */
final class Skeleton {

	/**
	 * 1つのファイル
	 *
	 * @param resource	雛形のリソース名
	 * @param target	置き場所（プロジェクトのルートから）
	 */
	record Entry(String resource, String target) {}

	/** リソースの置き場所 */
	private static final String BASE = "/io/jimble/cli/skeleton/";

	/** 生成するもの */
	static final List<Entry> ENTRIES = List.of(
		new Entry("settings.gradle.kts.txt", "settings.gradle.kts")
		, new Entry("build.gradle.kts.txt", "build.gradle.kts")
		, new Entry("gradle.properties.txt", "gradle.properties")
		, new Entry("gitignore.txt", ".gitignore")
		, new Entry("README.md.txt", "README.md")
		, new Entry("App.java.txt", "src/main/java/__PACKAGE_PATH__/App.java")
		, new Entry("index.jte.txt", "src/main/jte/__PACKAGE_PATH__/index.jte")
		, new Entry("application.conf.txt", "conf/application.conf")
		/*
		 * ログの設定。無いと logback の既定（全部まとめてコンソール）になり、
		 * アクセスログもアプリのログも同じところに混ざる。
		 */
		, new Entry("logback.xml.txt", "conf/logback.xml")
		/*
		 * マイグレーションは「DB を使うようになったら」なので、
		 * 拡張子を .sql にしないでおく。
		 * 置いただけで流れると、DB を使わない人が驚く。
		 */
		, new Entry("migration.sql.txt", "conf/migration/__DB__/001_create_note.sql.example")
	);

	private Skeleton () {}

	/**
	 * リソースのパス
	 *
	 * @param entry	ファイル
	 * @return	パス
	 */
	static String resourcePath (Entry entry) {

		return BASE + entry.resource();

	}

}
