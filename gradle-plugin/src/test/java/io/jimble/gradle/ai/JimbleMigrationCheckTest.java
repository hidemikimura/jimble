package io.jimble.gradle.ai;

import io.jimble.gradle.ai.JimbleChecker.Finding;
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
 * 2.0 への移行で書き換える呼び出しを見つける（要件 D-192 / D-198）
 *
 * <p>
 * 規則ごとに、見つけるものと<b>見つけないもの</b>（新しい書き方・似た名前の別物・コメントの中）を固める。
 * </p>
 */
class JimbleMigrationCheckTest {

	private static List<String> run (Path root, String body, boolean target2) throws IOException {

		Path file = root.resolve("src/main/java/app/A.java");
		Files.createDirectories(file.getParent());
		Files.writeString(file, "class A {\n\tvoid a () throws Exception {\n" + body + "\n\t}\n}\n", StandardCharsets.UTF_8);

		List<Finding> findings = JimbleChecker.check(root, root, "1.5.0", target2);
		return findings.stream()
			.filter(f -> f.rule().startsWith("J8") || f.rule().startsWith("J9"))
			.map(f -> f.rule() + ":" + (f.line() - 2))
			.toList();

	}

	@Test
	@DisplayName("J801 / J802 トランザクションの古い書き方。db.begin() と db.transaction() は見つけない")
	void transactions (@TempDir Path root) throws IOException {

		assertEquals(List.of("J801:1", "J801:2"), run(root, """
			try (DBTransaction t = new DBTransaction(db)) { t.beginTransaction(); t.commitEndTransaction(); }
			DBTransaction.transaction(db, t -> {});
			try (Tx tx = db.begin()) { tx.commit(); }
			db.transaction(tx -> {});""", false));

		assertEquals(List.of("J802:1", "J802:2"), run(root, """
			db.beginTransaction();
			db.commitEndTransaction();
			// db.rollbackEndTransaction();""", false));

	}

	@Test
	@DisplayName("J803 Router x = router.path(\"/x\") を見つける。ブロックの形と Cookie.path(...) は見つけない")
	void routerPath (@TempDir Path root) throws IOException {

		assertEquals(List.of("J803:1"), run(root, """
			Router admin = router.path("/admin");
			router.path("/admin", a -> a.get("/x", h));
			Cookie c = new Cookie("a", "b").path("/app");
			String p = context.request().path();""", false));

	}

	@Test
	@DisplayName("J804 列の subtract を見つける。BigDecimal / BigInteger の subtract は見つけない")
	void subtract (@TempDir Path root) throws IOException {

		assertEquals(List.of("J804:1"), run(root, """
			SQL.select(Post.view_count.subtract(2));
			BigDecimal x = BigDecimal.ONE.subtract(price);
			BigInteger y = a.subtract(b);""", false));

	}

	@Test
	@DisplayName("J805 / J806 / J807 Dsl.or・Cookie を1つ渡す put・eq(null)")
	void misc (@TempDir Path root) throws IOException {

		assertEquals(List.of("J805:1", "J806:3", "J806:4", "J807:6", "J807:7"), run(root, """
			q.where(a, Dsl.or(b));
			q.where(a, Dsl.anyOf(b, c));
			context.cookies().put(new Cookie("theme", "dark").httpOnly(false));
			context.cookies().put(cookie);
			context.cookies().put("name", value);
			q.where(Post.deleted_at.eq(null));
			q.where(Post.deleted_at.not(null));
			q.where(Post.deleted_at.is_null());""", false));

	}

	@Test
	@DisplayName("J808 文字列 \"now()\" を見つける。コメントの中と Dsl.now() は見つけない")
	void nowString (@TempDir Path root) throws IOException {

		assertEquals(List.of("J808:1"), run(root, """
			row.put("created_at", "now()");
			// "now()" はコメント
			b.value(Post.created_at, Dsl.now());""", false));

	}

	@Test
	@DisplayName("D-198 J809 は 2.0 で外した（文として書く validate(...) が 2.0 の正しい書き方）")
	void validateIsNotReported (@TempDir Path root) throws IOException {

		assertEquals(List.of(), run(root, """
			rules.validate(db, input);
			addErrors(rules.errors(db, input));""", true));

	}

	@Test
	@DisplayName("J810 long x = db.insert(...) を見つける。insertKey は見つけない")
	void insertKeyOrCount (@TempDir Path root) throws IOException {

		assertEquals(List.of("J810:1"), run(root, """
			long id = db.insert(b);
			long key = db.insertKey(b);
			db.insert(b);""", false));

	}

	@Test
	@DisplayName("D-198 2.0 の jimbleCheck は J9xx もいつも出す。Optional で受け取っている 2.0 の書き方は出さない")
	void target2Always (@TempDir Path root) throws IOException {

		String body = """
			Data row = db.select(b);
			if (!db.execute("x")) { }
			if (db.isError()) { }
			assertTrue(DBUtil.load(conf, A.class));
			var r = RedisLock.tryLock("k", 1, 2);
			Data one = db.selectOrThrow(b);
			context.session().data().put("a", "1");""";

		List<String> expected = List.of("J901:1", "J902:2", "J903:3", "J904:4", "J905:5", "J906:6", "J907:7");
		assertEquals(expected, run(root, body, true));

		// 既定（--target を付けない 3 引数版）も同じ
		Path file = root.resolve("src/main/java/app/A.java");
		assertEquals(expected.size(), JimbleChecker.check(root, root, "2.0.0").stream()
			.filter(f -> f.rule().startsWith("J9")).count());
		assertTrue(Files.exists(file));

		// 2.0 の書き方は出さない
		assertEquals(List.of(), run(root, """
			Data row = db.select(b).orElse(null);
			Data one = db.select(SQL.select().from(T.instance()).where(T.id.eq(1))).orElseThrow(() -> new HttpException(404, "x"));
			boolean held = RedisLock.tryLock("k", 1, 2).isPresent();
			Optional<RedisLockResult> lock = RedisLock.tryLock("k", 1, 2);
			Data s = context.session().data();""", true));

	}

	@Test
	@DisplayName("移行の規則はどれも WARN で、引き先は migrate-2.md")
	void levelAndUrl (@TempDir Path root) throws IOException {

		Path file = root.resolve("src/main/java/app/A.java");
		Files.createDirectories(file.getParent());
		Files.writeString(file, "class A { void a () { Router r = router.path(\"/x\"); } }\n", StandardCharsets.UTF_8);

		Finding f = JimbleChecker.check(root, root, "1.5.0", false).stream()
			.filter(x -> x.rule().equals("J803")).findFirst().orElseThrow();
		assertEquals(JimbleChecker.Level.WARN, f.level());
		assertTrue(f.url().endsWith("migrate-2.md"), f.url());
		assertTrue(f.fix().contains("path(\"/admin\", admin ->"), f.fix());

	}

}
