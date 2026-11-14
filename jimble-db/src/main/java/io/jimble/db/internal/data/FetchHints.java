package io.jimble.db.internal.data;

import io.jimble.db.data.ResultSetFetcher;

import java.util.List;

/**
 * {@link ResultSetFetcher} に、SQL・位置ごとの結果のキー・データソースを jimble の中（DB）からだけ渡す（3.0.0）
 *
 * <p>
 * 結果の列の読み方を覚えて使い回すのと、ビルダーが書いた位置ごとのキーで入れ子にするのに使う。
 * 公開メソッドにするとアプリが頼れる形になるので、ここを通す。渡し方は {@link ResultSetFetcher} が読み込まれたときに決まる。
 * </p>
 */
public final class FetchHints {

	/**
	 * 渡し方
	 */
	public interface Access {

		/**
		 * 渡す
		 *
		 * @param fetcher	フェッチャー
		 * @param source	データソース（覚える単位。null なら覚えない）
		 * @param sql		SQL（null なら覚えない）
		 * @param labels	位置ごとの結果のキー（null なら列名で入れ子にする）
		 */
		void hint (ResultSetFetcher fetcher, String source, String sql, List<String> labels);

		/**
		 * 覚えた列の読み方を全部忘れる
		 */
		void clear ();

	}

	/* 渡し方 */
	private static volatile Access access;

	private FetchHints () {}

	/**
	 * 渡し方を決める（{@link ResultSetFetcher} だけが呼ぶ。2 回目からは何もしない）
	 *
	 * @param value	渡し方
	 */
	public static synchronized void install (Access value) {

		Class<?> caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
		if (caller != ResultSetFetcher.class) {
			throw new IllegalStateException("FetchHints.install は ResultSetFetcher だけが呼べます: " + caller.getName());
		}

		if (access == null) {
			access = value;
		}

	}

	/**
	 * 渡す
	 *
	 * @param fetcher	フェッチャー
	 * @param source	データソース
	 * @param sql		SQL
	 * @param labels	位置ごとの結果のキー
	 */
	public static void hint (ResultSetFetcher fetcher, String source, String sql, List<String> labels) {

		access().hint(fetcher, source, sql, labels);

	}

	/**
	 * 覚えた列の読み方を全部忘れる（{@code DBUtil.load} のたび）
	 */
	public static void clear () {

		access().clear();

	}

	private static Access access () {

		Access current = access;
		if (current == null) {
			// ResultSetFetcher を読み込ませる（まだ一度も使っていないとき）
			try {
				Class.forName(ResultSetFetcher.class.getName(), true, ResultSetFetcher.class.getClassLoader());
			} catch (ClassNotFoundException ex) {
				throw new IllegalStateException(ex);
			}
			current = access;
		}
		return current;

	}

}
