package io.jimble.gradle.ai;

import io.jimble.gradle.ai.JimbleChecker.Finding;
import io.jimble.gradle.ai.JimbleChecker.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 既知の落とし穴を見つける（要件 D-188）
 *
 * <p>
 * 規則ごとに<b>見つけること</b>と<b>見つけないこと</b>（正しい書き方・コメントや文字列の中・抑止の印）を固める。
 * 誤検知が多いと誰も流さなくなるので、見つけないほうも同じだけ大事である。
 * </p>
 */
class JimbleCheckerTest {

	private static void write (Path root, String path, String text) throws IOException {

		Path file = root.resolve(path);
		Files.createDirectories(file.getParent());
		Files.writeString(file, text, StandardCharsets.UTF_8);

	}

	private static List<Finding> check (Path root) {

		return JimbleChecker.check(root, root, "1.5.0");

	}

	private static List<String> rules (List<Finding> findings) {

		return findings.stream().map(finding -> finding.rule() + ":" + finding.line()).toList();

	}

	@Test
	@DisplayName("J101 request().get〜 を見つける。bodyAll()・request().request()・コメント・文字列の中は見つけない")
	void requestGet (@TempDir Path root) throws IOException {

		write(root, "src/main/java/app/A.java", """
			class A {
				void a (WebContext context) {
					String x = context.request().getString("x");
					String y = context.request().bodyAll().getString("y");
					String m = context.request().request().getString("method");
					// context.request().getString("コメント")
					String s = "context.request().getString(\\"文字列\\")";
					long n = context.request()
						.getLong("n");
				}
			}
			""");

		List<Finding> findings = check(root);

		assertEquals(List.of("J101:3", "J101:8"), rules(findings), findings.toString());
		assertEquals(Level.ERROR, findings.get(0).level());
		assertTrue(findings.get(0).fix().contains("bodyAll()"));
		assertTrue(findings.get(0).url().endsWith("request-response.md"));

	}

	@Test
	@DisplayName("抑止の印（その行か前の行）があれば出さない。規則が違えば出す")
	void ignore (@TempDir Path root) throws IOException {

		write(root, "src/main/java/app/A.java", """
			class A {
				void a (WebContext context) {
					String x = context.request().getString("x"); // jimble-check:ignore J101
					// jimble-check:ignore J101
					String y = context.request().getString("y");
					// jimble-check:ignore J999
					String z = context.request().getString("z");
				}
			}
			""");

		assertEquals(List.of("J101:7"), rules(check(root)));

	}

	@Test
	@DisplayName("J301 Migration.install() が DBUtil.load のあとにある")
	void migrationAfterLoad (@TempDir Path root) throws IOException {

		write(root, "src/main/java/app/Bad.java", """
			class Bad {
				static void load () {
					DBUtil.load(Conf.conf().config(), Bad.class);
					Migration.install();
				}
			}
			""");
		write(root, "src/main/java/app/Good.java", """
			class Good {
				static void load () {
					Migration.install();
					DBUtil.load(Conf.conf().config(), Good.class);
				}
			}
			""");

		assertEquals(List.of("J301:4"), rules(check(root)));

	}

	@Test
	@DisplayName("J302 BatchTables.install が無い / J303 sync が add より前")
	void batch (@TempDir Path root) throws IOException {

		write(root, "src/main/java/app/Batch.java", """
			class Batch {
				public static void main (String[] args) {
					Bootstrap.load();
					BatchRegistry.sync(DBUtil.getMainDB());
					BatchRegistry.add(CleanupBatch::new);
				}
			}
			""");

		assertEquals(List.of("J303:4", "J302:4"), rules(check(root)));

		write(root, "src/main/java/app/Bootstrap.java", """
			class Bootstrap {
				static void load () {
					BatchTables.install(DBUtil.getMainDB());
				}
			}
			""");

		assertEquals(List.of("J303:4"), rules(check(root)), "BatchTables.install がどこかにあれば J302 は出ない");

	}

	@Test
	@DisplayName("J401 外側の guard と、種別のブロックの中の restore")
	void guardOrder (@TempDir Path root) throws IOException {

		write(root, "src/main/java/app/Bad.java", """
			class Bad extends JimbleApp {{
				before(Auth::guard);
				path("/ops", () -> {
					attribute(Auth.REALM, "operator");
					before(Remember.restore("operator", Ops::find));
				});
			}}
			""");
		write(root, "src/main/java/app/Good.java", """
			class Good extends JimbleApp {{
				path("/ops", () -> {
					attribute(Auth.REALM, "operator");
					before(Remember.restore("operator", Ops::find));
					before(Auth::guard);
				});
				path("", () -> {
					before(Remember.restore(App::find));
					before(Auth::guard);
				});
			}}
			""");

		assertEquals(List.of("J401:5"), rules(check(root)));

	}

	@Test
	@DisplayName("J701 DBTransaction を使うファイルの空の catch（WARN）")
	void emptyCatch (@TempDir Path root) throws IOException {

		write(root, "src/main/java/app/Save.java", """
			class Save {
				void save (DB db) {
					try (DBTransaction transaction = new DBTransaction(db)) {
						transaction.beginTransaction();
						transaction.commitEndTransaction();
					} catch (Exception e) {}
				}
			}
			""");
		write(root, "src/main/java/app/Other.java", """
			class Other {
				void other () {
					try { run(); } catch (Exception e) {}
				}
			}
			""");

		List<Finding> findings = check(root);

		assertEquals(List.of("J701:6", "J801:3"), rules(findings));
		assertEquals(Level.WARN, findings.get(0).level());

	}

	@Test
	@DisplayName("J201 ? の無い環境変数（WARN）/ J202 ${?X} のあとに書き直す（ERROR）。2行組とパスの参照は見つけない")
	void conf (@TempDir Path root) throws IOException {

		write(root, "conf/application.conf", """
			db {
				main_db {
					url      = "jdbc:mariadb://127.0.0.1:3306/app"
					url      = ${?DB_URL}
					password = ${DB_PASSWORD}
					username = ${?DB_USER}
					username = "app"   # 書き直し
				}
			}
			cookie.secret = ""
			cookie.secret = ${?COOKIE_SECRET}
			other.url = ${db.main_db.url}
			# password = ${IN_COMMENT}
			""");

		List<Finding> findings = check(root);

		assertEquals(List.of("J201:5", "J202:7"), rules(findings), findings.toString());
		assertTrue(findings.get(1).message().contains("db.main_db.username"), findings.get(1).message());

	}

	@Test
	@DisplayName("J304 MQ の表を codegen から外していない（codegen を使うときだけ）")
	void mqTables (@TempDir Path root) throws IOException {

		write(root, "src/main/java/app/Notice.java", """
			class Notice extends AbstractMqExecutor {
				public String queueName () { return "mq_notice"; }
			}
			""");
		write(root, "src/main/java/app/Scheduler.java", """
			class Scheduler { void run () { new DbScheduler().start(); } }
			""");

		assertTrue(check(root).isEmpty(), "codegen を使っていなければ出さない");

		write(root, "conf/application.conf", """
			codegen {
				exclude_tables = ["mq_notice"]
			}
			scheduler.queue_name = "mq_scheduler"
			""");

		List<Finding> findings = check(root);

		assertEquals(1, findings.size(), findings.toString());
		assertEquals("J304", findings.get(0).rule());
		assertTrue(findings.get(0).message().contains("mq_scheduler"), "exclude_tables の外に書いた名前では外れない");

	}

	@Test
	@DisplayName("J501 skill の版が使っている jimble と違う / 控えが無い（WARN）")
	void skills (@TempDir Path root) throws IOException {

		assertTrue(check(root).isEmpty(), "skill を置いていなければ出さない");

		write(root, ".claude/skills/jimble/SKILL.md", "古い skill");

		assertEquals(List.of("J501:0"), rules(check(root)));

		write(root, ".claude/" + SkillsInstaller.RECORD, "version=1.4.2\n");
		assertTrue(check(root).get(0).message().contains("1.4.2"));

		write(root, ".claude/" + SkillsInstaller.RECORD, "version=1.5.0\n");
		assertTrue(check(root).isEmpty());

	}

	@Test
	@DisplayName("コメントと文字列を潰しても、行と桁はずれない")
	void blankKeepsPositions () {

		String source = "a /* x\ny */ b \"c\\\"d\" 'e' // f\n\"\"\"\ng\n\"\"\" h";
		String blanked = JimbleChecker.blank(source);

		assertEquals(source.length(), blanked.length());
		assertEquals(source.chars().filter(c -> c == '\n').count(), blanked.chars().filter(c -> c == '\n').count());
		assertTrue(blanked.contains(" b ") && blanked.endsWith(" h"), blanked);
		assertTrue(!blanked.contains("x") && !blanked.contains("f") && !blanked.contains("g"), blanked);

	}

}
