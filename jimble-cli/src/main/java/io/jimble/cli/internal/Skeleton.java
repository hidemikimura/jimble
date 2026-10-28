package io.jimble.cli.internal;

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
		 * ローカル用（cookie.secure = false）。共通の application.conf に書くと、本番も引き継ぐ（D-228）
		 */
		, new Entry("application.local.conf.txt", "conf/application.local.conf")
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
		/*
		 * AI 向けの skill（要件 NF-D-09）。
		 *
		 * <b>置いておかないと、AI は Spring のつもりで書く</b>——
		 * 注釈も DI も無いことを、書き始める前に知っている必要がある。
		 * 中身は jimble 自身の .claude/skills/ から<b>ビルドで写している</b>ので、
		 * ここに写しは無い（写し忘れは JimbleNewSkillsTest が落とす）。
		 * 分野ごとに分けてある（1つ 8 KB まで）。入口の jimble / jimble-web / jimble-db / jimble-batch が案内する。
		 */
		, new Entry("skills/jimble/SKILL.md", ".claude/skills/jimble/SKILL.md")
		, new Entry("skills/jimble-batch/SKILL.md", ".claude/skills/jimble-batch/SKILL.md")
		, new Entry("skills/jimble-batch-run/SKILL.md", ".claude/skills/jimble-batch-run/SKILL.md")
		, new Entry("skills/jimble-batch-write/SKILL.md", ".claude/skills/jimble-batch-write/SKILL.md")
		, new Entry("skills/jimble-config/SKILL.md", ".claude/skills/jimble-config/SKILL.md")
		, new Entry("skills/jimble-db/SKILL.md", ".claude/skills/jimble-db/SKILL.md")
		, new Entry("skills/jimble-db-config/SKILL.md", ".claude/skills/jimble-db-config/SKILL.md")
		, new Entry("skills/jimble-db-data/SKILL.md", ".claude/skills/jimble-db-data/SKILL.md")
		, new Entry("skills/jimble-db-migration/SKILL.md", ".claude/skills/jimble-db-migration/SKILL.md")
		, new Entry("skills/jimble-db-select/SKILL.md", ".claude/skills/jimble-db-select/SKILL.md")
		, new Entry("skills/jimble-db-tx/SKILL.md", ".claude/skills/jimble-db-tx/SKILL.md")
		, new Entry("skills/jimble-db-write/SKILL.md", ".claude/skills/jimble-db-write/SKILL.md")
		, new Entry("skills/jimble-json/SKILL.md", ".claude/skills/jimble-json/SKILL.md")
		, new Entry("skills/jimble-mq/SKILL.md", ".claude/skills/jimble-mq/SKILL.md")
		, new Entry("skills/jimble-mq-worker/SKILL.md", ".claude/skills/jimble-mq-worker/SKILL.md")
		, new Entry("skills/jimble-upgrade/SKILL.md", ".claude/skills/jimble-upgrade/SKILL.md")
		, new Entry("skills/jimble-web/SKILL.md", ".claude/skills/jimble-web/SKILL.md")
		, new Entry("skills/jimble-web-api-token/SKILL.md", ".claude/skills/jimble-web-api-token/SKILL.md")
		, new Entry("skills/jimble-web-auth/SKILL.md", ".claude/skills/jimble-web-auth/SKILL.md")
		, new Entry("skills/jimble-web-auth-methods/SKILL.md", ".claude/skills/jimble-web-auth-methods/SKILL.md")
		, new Entry("skills/jimble-web-input/SKILL.md", ".claude/skills/jimble-web-input/SKILL.md")
		, new Entry("skills/jimble-web-response/SKILL.md", ".claude/skills/jimble-web-response/SKILL.md")
		, new Entry("skills/jimble-web-server/SKILL.md", ".claude/skills/jimble-web-server/SKILL.md")
		, new Entry("skills/jimble-web-session/SKILL.md", ".claude/skills/jimble-web-session/SKILL.md")
		/*
		 * AI への案内（要件 D-187）。AGENTS.md はツールをまたいで読まれる決まりのファイルで、
		 * CLAUDE.md は Claude Code 向けにそれを読み込むだけ（@AGENTS.md）。
		 * アプリの決まりは AGENTS.md に書き足してもらう（skill を直させない）
		 */
		, new Entry("ai/AGENTS.md", "AGENTS.md")
		, new Entry("ai/CLAUDE.md", "CLAUDE.md")
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
