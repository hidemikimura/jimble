package io.jimble.db;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * どのスレッドが接続を借りに来たかを記録する（テスト用）
 *
 * <p>
 * メインの DB のデータソースを包んで差し替える。{@code DBSource.dataSource(DataSource)} がパッケージの中だけなので、
 * このクラスは {@code io.jimble.db} に置いている。
 * </p>
 */
public final class ConnectionWatch implements AutoCloseable {

	/* 差し替える前 */
	private final DataSource original;

	/* 借りに来たスレッド（名前@ID。名前の無い仮想スレッドも1本ずつ数える） */
	private final Set<String> threads = ConcurrentHashMap.newKeySet();

	/* 記録するか */
	private volatile boolean recording = false;

	private ConnectionWatch () {

		this.original = DBUtil.getMainDataSource().dataSource();

		DBUtil.getMainDataSource().dataSource(new Recording());

	}

	/**
	 * 差し替える
	 *
	 * @return	記録
	 */
	public static ConnectionWatch install () {

		return new ConnectionWatch();

	}

	/**
	 * 記録し直す
	 */
	public void startRecording () {

		threads.clear();
		recording = true;

	}

	/**
	 * 記録をやめて、借りに来たスレッドを返す
	 *
	 * @return	スレッド（名前@ID）
	 */
	public Set<String> stopRecording () {

		recording = false;

		return Set.copyOf(threads);

	}

	@Override
	public void close () {

		DBUtil.getMainDataSource().dataSource(original);

	}

	/**
	 * 記録するデータソース
	 */
	private final class Recording implements DataSource {

		@Override
		public Connection getConnection () throws SQLException {

			if (recording) {
				threads.add(Thread.currentThread().getName() + "@" + Thread.currentThread().threadId());
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
