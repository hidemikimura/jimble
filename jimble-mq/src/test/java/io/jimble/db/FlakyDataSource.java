package io.jimble.db;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.logging.Logger;

/**
 * 落としたり戻したりできるデータソース（テスト用）
 *
 * <p>
 * 書き込みのデータソースを包んで差し替える。{@link #down()} のあいだは、接続を借りようとすると
 * つながらないときと同じ例外を投げる。{@code DBSource.dataSource(DataSource)} がパッケージの中だけなので、
 * このクラスは {@code io.jimble.db} に置いている。
 * </p>
 */
public final class FlakyDataSource implements AutoCloseable {

	/* 差し替えるデータソース */
	private final DBSource source;

	/* 差し替える前 */
	private final DataSource original;

	/* 落ちているか */
	private volatile boolean down = false;

	/* 落ちているとき、例外を投げるまで待つ時間（つながらない DB で connection_timeout まで待たされるのと同じ） */
	private volatile long delayMillis = 0;

	private FlakyDataSource (String name) {

		this.source = DBUtil.getDataSource(name);
		this.original = source.dataSource();

		source.dataSource(new Flaky());

	}

	/**
	 * 差し替える
	 *
	 * @param name	データソース名
	 * @return	差し替えたもの
	 */
	public static FlakyDataSource install (String name) {

		return new FlakyDataSource(name);

	}

	/**
	 * 落とす
	 */
	public void down () {

		delayMillis = 0;
		down = true;

	}

	/**
	 * 落とす（接続を借りようとすると、しばらく待たされてから失敗する）
	 *
	 * @param delayMillis	待たせる時間（ミリ秒）
	 */
	public void downSlowly (long delayMillis) {

		this.delayMillis = delayMillis;
		down = true;

	}

	/**
	 * 戻す
	 */
	public void up () {

		down = false;

	}

	@Override
	public void close () {

		source.dataSource(original);

	}

	/**
	 * 落ちているあいだは接続を貸さないデータソース
	 */
	private final class Flaky implements DataSource {

		@Override
		public Connection getConnection () throws SQLException {

			if (down) {
				if (delayMillis > 0) {
					try {
						Thread.sleep(delayMillis);
					} catch (InterruptedException ex) {
						Thread.currentThread().interrupt();
					}
				}
				throw new SQLTransientConnectionException("テストで落としています: " + source.name());
			}

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
