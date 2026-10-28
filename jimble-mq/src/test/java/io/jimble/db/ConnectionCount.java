package io.jimble.db;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * データソース1つに、接続を借りに来た回数を数える（テスト用）
 *
 * <p>
 * 書き込みのデータソースを包んで差し替える。{@code DBSource.dataSource(DataSource)} がパッケージの中だけなので、
 * このクラスは {@code io.jimble.db} に置いている。
 * </p>
 */
public final class ConnectionCount implements AutoCloseable {

	/* 数えるデータソース */
	private final DBSource source;

	/* 差し替える前 */
	private final DataSource original;

	/* 借りに来た回数 */
	private final AtomicInteger count = new AtomicInteger();

	private ConnectionCount (String name) {

		this.source = DBUtil.getDataSource(name);
		this.original = source.dataSource();

		source.dataSource(new Counting());

	}

	/**
	 * 差し替える
	 *
	 * @param name	データソース名
	 * @return	数え
	 */
	public static ConnectionCount install (String name) {

		return new ConnectionCount(name);

	}

	/**
	 * 数え直す
	 */
	public void reset () {

		count.set(0);

	}

	/**
	 * 借りに来た回数
	 *
	 * @return	回数
	 */
	public int count () {

		return count.get();

	}

	@Override
	public void close () {

		source.dataSource(original);

	}

	/**
	 * 数えるデータソース
	 */
	private final class Counting implements DataSource {

		@Override
		public Connection getConnection () throws SQLException {

			count.incrementAndGet();
			return original.getConnection();

		}

		@Override public Connection getConnection (String user, String password) throws SQLException { return getConnection(); }
		@Override public PrintWriter getLogWriter () throws SQLException { return original.getLogWriter(); }
		@Override public void setLogWriter (PrintWriter out) throws SQLException { original.setLogWriter(out); }
		@Override public void setLoginTimeout (int seconds) throws SQLException { original.setLoginTimeout(seconds); }
		@Override public int getLoginTimeout () throws SQLException { return original.getLoginTimeout(); }
		@Override public Logger getParentLogger () { return Logger.getGlobal(); }
		@Override public <T> T unwrap (Class<T> iface) throws SQLException { return original.unwrap(iface); }
		@Override public boolean isWrapperFor (Class<?> iface) throws SQLException { return original.isWrapperFor(iface); }

	}

}
