package io.jimble.db;

import com.zaxxer.hikari.HikariDataSource;
import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * トランザクションを始められなかったとき、取った接続を返す
 *
 * <p>
 * {@code txBegin()} は接続を取ってから {@code setAutoCommit(false)} を呼ぶ。
 * <b>そこで失敗すると、接続を握ったまま抜けていた</b>——closeTask の登録より前なので、
 * {@code DBUtil.getMainDB().transaction(...)} のように DB を使い捨てにする書き方では<b>プールへ戻らなかった</b>。
 * </p>
 *
 * <p><b>開発用 DB が必要</b>（要件 D-16）。</p>
 */
@Tag("db")
class TxBeginLeakTest {

	/* 差し替える前のデータソース */
	private DataSource original;

	@BeforeEach
	void setUp () {

		Conf.reload();
		DBUtil.load(Conf.conf().config(), TxBeginLeakTest.class);

		original = DBUtil.getMainDataSource().dataSource();

	}

	@AfterEach
	void tearDown () {

		if (original != null) {
			DBUtil.getMainDataSource().dataSource(original);
		}

		DBUtil.stop();

	}

	@Test
	@DisplayName("setAutoCommit(false) が失敗したら、接続をプールへ返して TransactionException")
	void failedBeginReturnsTheConnection () {

		int before = activeConnections();

		DBUtil.getMainDataSource().dataSource(new RefusingAutoCommit(original));

		DB db = DBUtil.getMainDB();

		// 使い捨ての DB（閉じない）。いちばん漏れやすい書き方
		assertThrows(TransactionException.class, () -> db.transaction(tx -> { }));

		assertEquals(before, activeConnections(), "トランザクションを始められなかった接続が、プールへ戻っていません");

	}

	@Test
	@DisplayName("始められたトランザクションは、これまでどおり")
	void normalBeginStillWorks () {

		int before = activeConnections();

		DBUtil.getMainDB().transaction(tx -> DBUtil.getMainDB().execute("SELECT 1"));

		assertEquals(before, activeConnections());

	}

	/**
	 * 使っている接続の数
	 */
	private int activeConnections () {

		if (original instanceof HikariDataSource hikari) {
			return hikari.getHikariPoolMXBean().getActiveConnections();
		}

		throw new AssertionError("Hikari ではないので本数が見られません");

	}

	/**
	 * setAutoCommit(false) だけ断る接続を配るデータソース
	 */
	private static final class RefusingAutoCommit implements DataSource {

		/* 本物 */
		private final DataSource delegate;

		RefusingAutoCommit (DataSource delegate) {
			this.delegate = delegate;
		}

		@Override
		public Connection getConnection () throws SQLException {

			Connection real = delegate.getConnection();

			return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{ Connection.class }
				, (proxy, method, args) -> {
					if ("setAutoCommit".equals(method.getName()) && Boolean.FALSE.equals(args[0])) {
						throw new SQLException("自動コミットを切れませんでした（テスト）");
					}
					try {
						return method.invoke(real, args);
					} catch (java.lang.reflect.InvocationTargetException ex) {
						throw ex.getCause();
					}
				});

		}

		@Override public Connection getConnection (String user, String password) throws SQLException { return getConnection(); }
		@Override public PrintWriter getLogWriter () throws SQLException { return delegate.getLogWriter(); }
		@Override public void setLogWriter (PrintWriter out) throws SQLException { delegate.setLogWriter(out); }
		@Override public void setLoginTimeout (int seconds) throws SQLException { delegate.setLoginTimeout(seconds); }
		@Override public int getLoginTimeout () throws SQLException { return delegate.getLoginTimeout(); }
		@Override public Logger getParentLogger () { return Logger.getGlobal(); }
		@Override public <T> T unwrap (Class<T> iface) throws SQLException { return delegate.unwrap(iface); }
		@Override public boolean isWrapperFor (Class<?> iface) throws SQLException { return delegate.isWrapperFor(iface); }

	}

}
