package io.jimble.db;

import io.jimble.db.redis.lock.RedisLock;
import io.jimble.db.sql.SelectBuilder;
import io.jimble.db.sql.definition.column.Column;
import io.jimble.db.sql.definition.table.Table;
import io.jimble.db.sql.query.dsl.Dsl;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.util.json.Dson;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.0 の形のトランザクション（{@link Tx}）と一意制約の見分け（要件 D-191）
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。{@code ./gradlew :jimble-db:pgTest}</p>
 */
@Tag("db")
class TxIntegrationTest {

	@BeforeAll
	static void load () {

		Conf.reload();
		DBUtil.load(Conf.conf().config(), TxIntegrationTest.class);

		DB db = DBUtil.getMainDB();
		db.execute("DROP TABLE IF EXISTS tx_rows");
		TestDdl.execute(db, """
			CREATE TABLE tx_rows (
				id    bigint unsigned auto_increment primary key,
				code  varchar(50) not null unique
			) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
			""");

	}

	@AfterAll
	static void stop () {

		DBUtil.getMainDB().execute("DROP TABLE IF EXISTS tx_rows");
		DBUtil.stop();

	}

	@BeforeEach
	void clean () {

		DBUtil.getMainDB().execute("DELETE FROM tx_rows");

	}

	private static long count () {

		return DBUtil.getMainDB().select("SELECT COUNT(*) AS c FROM tx_rows").orElseThrow().getLong("c");

	}

	private static void insert (DB db, String code) {

		db.insert("INSERT INTO tx_rows (code) VALUES (?)", code);

	}

	// region Tx

	@Test
	@DisplayName("D-191 tx.commit() は確定して終わる")
	void commitEnds () {

		DB db = DBUtil.getMainDB();
		try (Tx tx = db.begin()) {
			insert(db, "a");
			tx.commit();
			assertTrue(tx.isFinished());
			assertFalse(db.isTransaction(), "commit() で終わっていない");
		}
		assertEquals(1, count());

	}

	@Test
	@DisplayName("D-191 commit せずに閉じたら巻き戻す")
	void closeRollsBack () {

		DB db = DBUtil.getMainDB();
		try (Tx tx = db.begin()) {
			insert(db, "a");
			assertFalse(tx.isFinished());
		}
		assertEquals(0, count());
		assertFalse(db.isTransaction());

	}

	@Test
	@DisplayName("D-191 2度目の commit は例外（終わったものを確定したつもりにならない）")
	void secondCommitThrows () {

		DB db = DBUtil.getMainDB();
		try (Tx tx = db.begin()) {
			tx.commit();
			IllegalStateException e = assertThrows(IllegalStateException.class, tx::commit);
			assertTrue(e.getMessage().contains("checkpoint()"), e.getMessage());
			assertThrows(IllegalStateException.class, tx::rollback);
		}

	}

	@Test
	@DisplayName("D-191 checkpoint() は確定して続ける（そのあとの分は、commit しなければ巻き戻る）")
	void checkpointContinues () {

		DB db = DBUtil.getMainDB();
		try (Tx tx = db.begin()) {
			insert(db, "a");
			tx.checkpoint();
			assertTrue(db.isTransaction(), "checkpoint() で終わっている");
			insert(db, "b");
		}
		assertEquals(1, count(), "checkpoint より前だけが残るはず");

	}

	@Test
	@DisplayName("D-191 中でエラーが出ていたら commit は DB_004 の TransactionException（非検査）")
	void commitAfterErrorThrowsDb004 () {

		DB db = DBUtil.getMainDB();
		try (Tx tx = db.begin()) {
			insert(db, "a");
			// 受け止めて続けても（2.0 は失敗が例外）
			assertThrows(SqlExecuteException.class, () -> db.select("SELECT * FROM tx_no_such_table"));
			TransactionException e = assertThrows(TransactionException.class, tx::commit);
			assertEquals("DB_004", e.getCode());
		}
		assertEquals(0, count());

	}

	// endregion

	// region transaction / transactionResult

	@Test
	@DisplayName("D-191 transaction は例外なく終われば確定し、値を返す版もある")
	void transactionCommits () {

		DB db = DBUtil.getMainDB();
		db.transaction(tx -> insert(db, "a"));
		long id = db.transactionResult(tx -> db.insertKey("INSERT INTO tx_rows (code) VALUES (?)", "b"));

		assertEquals(2, count());
		assertTrue(id > 0);
		assertFalse(db.isTransaction());

	}

	@Test
	@DisplayName("D-191 非検査例外は巻き戻して、同じ例外をそのまま投げ直す")
	void runtimeExceptionRollsBack () {

		DB db = DBUtil.getMainDB();
		IllegalStateException boom = new IllegalStateException("boom");

		IllegalStateException e = assertThrows(IllegalStateException.class, () -> db.transaction(tx -> {
			insert(db, "a");
			throw boom;
		}));
		assertSame(boom, e);
		assertEquals(0, count());
		assertFalse(db.isTransaction());

	}

	@Test
	@DisplayName("D-191 検査例外は巻き戻して TransactionException（DB_006）に包む")
	void checkedExceptionIsWrapped () {

		DB db = DBUtil.getMainDB();
		IOException io = new IOException("disk");

		TransactionException e = assertThrows(TransactionException.class, () -> db.transaction(tx -> {
			insert(db, "a");
			throw io;
		}));
		assertEquals("DB_006", e.getCode());
		assertSame(io, e.getCause().getCause());
		assertEquals(0, count());

	}

	@Test
	@DisplayName("D-191 中で tx.rollback() したら確定しない（例外にもならない）")
	void rollbackInsideBody () {

		DB db = DBUtil.getMainDB();
		db.transaction(tx -> {
			insert(db, "a");
			tx.rollback();
		});
		assertEquals(0, count());

	}

	// endregion

	// region 入れ子

	@Test
	@DisplayName("D-191 入れ子は合流する：中の commit は何もせず、外の commit で両方入る")
	void nestedJoins () {

		DB db = DBUtil.getMainDB();
		db.transaction(outer -> {
			insert(db, "a");
			db.transaction(inner -> {
				assertTrue(inner.isJoined());
				insert(db, "b");
			});
			assertTrue(db.isTransaction(), "中が外を終わらせている");
		});
		assertEquals(2, count());

	}

	@Test
	@DisplayName("D-191 中で例外が出たら、外の commit は DB_005 で断られ、全部巻き戻る")
	void nestedFailureMakesOuterRollbackOnly () {

		DB db = DBUtil.getMainDB();

		TransactionException e = assertThrows(TransactionException.class, () -> db.transaction(outer -> {
			insert(db, "a");
			assertThrows(IllegalStateException.class, () -> db.transaction(inner -> {
				insert(db, "b");
				throw new IllegalStateException("中で失敗");
			}));
		}));
		assertEquals("DB_005", e.getCode(), e.getMessage());
		assertEquals(0, count());

	}

	@Test
	@DisplayName("D-191 中の tx.rollback() も外を巻き戻し専用にする")
	void nestedRollbackMakesOuterRollbackOnly () {

		DB db = DBUtil.getMainDB();
		try (Tx outer = db.begin()) {
			insert(db, "a");
			try (Tx inner = db.begin()) {
				inner.rollback();
			}
			TransactionException e = assertThrows(TransactionException.class, outer::commit);
			assertEquals("DB_005", e.getCode());
		}
		assertEquals(0, count());

	}

	// endregion

	// region 一意制約

	@Test
	@DisplayName("D-191 insertKey が一意制約に当たったら DuplicateKeyException")
	void insertKeyDuplicate () {

		DB db = DBUtil.getMainDB();
		db.insertKey("INSERT INTO tx_rows (code) VALUES (?)", "dup");

		DuplicateKeyException e = assertThrows(DuplicateKeyException.class,
			() -> db.insertKey("INSERT INTO tx_rows (code) VALUES (?)", "dup"));
		assertEquals("DB_999", e.getCode());

	}

	@Test
	@DisplayName("D-193 一意制約でない失敗は DuplicateKeyException にならない。元の例外は cause に残る")
	void duplicateKeyOnlyForUniqueViolations () {

		DB db = DBUtil.getMainDB();
		insert(db, "dup");

		DuplicateKeyException dup = assertThrows(DuplicateKeyException.class, () -> db.insert("INSERT INTO tx_rows (code) VALUES (?)", "dup"));
		assertInstanceOf(java.sql.SQLException.class, dup.getCause().getCause());

		SqlExecuteException other = assertThrows(SqlExecuteException.class, () -> db.insert("INSERT INTO tx_no_such_table (code) VALUES (?)", "x"));
		assertFalse(other instanceof DuplicateKeyException, "一意制約ではないのに DuplicateKeyException");

	}

	// endregion

	// region @CheckReturnValue

	@Test
	@DisplayName("D-191 戻り値を捨てると効かないメソッドに @CheckReturnValue が付いている")
	void checkReturnValue () throws Exception {

		for (String m : new String[] {"select", "selectList", "selectOrThrow", "selectListOrThrow", "selectCached",
			"selectListCached", "selectListPerformance", "selectListWithRowCount", "selectListWithRowCountPerformance",
			"insertNoReturnKey", "begin"}) {
			assertAnnotated(DB.class, m);
		}
		/*
		 * 2.0 で失敗が例外になったので、戻り値（件数・採番値）を捨ててよいものからは外した（要件 D-193）。
		 * 付けたままだと、文として db.update(...); と書くたびに警告が出る。
		 */
		for (String m : new String[] {"insert", "update", "delete", "execute", "executeBatch", "insertBatch", "insertKey"}) {
			assertNotAnnotated(DB.class, m);
		}
		assertAnnotated(DBUtil.class, "healthCheck");
		assertAnnotated(RedisLock.class, "lock");
		assertAnnotated(RedisLock.class, "tryLock");
		assertAnnotated(Table.class, "inner");
		assertAnnotated(Table.class, "left");
		assertAnnotated(Table.class, "on");
		assertAnnotated(SelectBuilder.class, "copy");
		assertAnnotated(Data.class, "fromJsonString");
		assertTypeAnnotated(Column.class);
		assertTypeAnnotated(Dsl.class);
		assertTypeAnnotated(Dson.class);

	}

	private static ClassModel model (Class<?> type) throws Exception {

		try (var in = type.getResourceAsStream(type.getSimpleName() + ".class")) {
			return ClassFile.of().parse(in.readAllBytes());
		}

	}

	private static boolean hasAnnotation (java.util.Optional<java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute> attr) {

		return attr.map(a -> a.annotations().stream()
				.anyMatch(an -> an.className().equalsString("Lio/jimble/util/annotation/CheckReturnValue;")))
			.orElse(false);

	}

	private static void assertNotAnnotated (Class<?> type, String method) throws Exception {

		for (MethodModel m : model(type).methods()) {
			if (m.methodName().equalsString(method) && (m.flags().flagsMask() & ClassFile.ACC_PUBLIC) != 0) {
				assertFalse(hasAnnotation(m.findAttribute(Attributes.runtimeInvisibleAnnotations())),
					type.getSimpleName() + "." + method + m.methodTypeSymbol().displayDescriptor() + " に @CheckReturnValue が付いています");
			}
		}

	}

	private static void assertAnnotated (Class<?> type, String method) throws Exception {

		List<MethodModel> methods = model(type).methods().stream()
			.filter(m -> m.methodName().equalsString(method))
			.filter(m -> (m.flags().flagsMask() & ClassFile.ACC_PUBLIC) != 0)
			.toList();
		assertFalse(methods.isEmpty(), type.getSimpleName() + "." + method + " がありません");
		for (MethodModel m : methods) {
			assertTrue(hasAnnotation(m.findAttribute(Attributes.runtimeInvisibleAnnotations())),
				type.getSimpleName() + "." + method + m.methodTypeSymbol().displayDescriptor() + " に @CheckReturnValue が付いていません");
		}

	}

	private static void assertTypeAnnotated (Class<?> type) throws Exception {

		assertTrue(hasAnnotation(model(type).findAttribute(Attributes.runtimeInvisibleAnnotations())),
			type.getSimpleName() + " に @CheckReturnValue が付いていません");

	}

	// endregion

}
