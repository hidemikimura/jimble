package io.jimble.gradle.run;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * {@code jimbleRun} の設定（要件 F-X-02）
 *
 * <pre>
 * jimbleRun {
 *     mainClass = "blog.BlogApp"
 *     port      = 9000            // ブラウザで開くポート
 *     appPort   = 9100            // アプリが実際に待ち受けるポート
 *     debug     = true            // デバッガを繋げるようにする（既定 5005）
 * }
 * </pre>
 *
 * <p>
 * 既定値だけで動くようにしてある。書かなければならないのは
 * {@code mainClass} だけである。
 * </p>
 */
public abstract class JimbleRunExtension {

	/**
	 * コンストラクタ
	 */
	public JimbleRunExtension () {

	}

	/**
	 * アプリの起動クラス（{@code main} を持つクラス）
	 *
	 * <p><b>必須。</b></p>
	 *
	 * @return	起動クラス
	 */
	public abstract Property<String> getMainClass ();

	/**
	 * ブラウザで開くポート（既定 9000）
	 *
	 * @return	ポート
	 */
	public abstract Property<Integer> getPort ();

	/**
	 * アプリが待ち受けるポート（既定 {@code port + 100}）
	 *
	 * <p>
	 * 開発用プロキシがここへ転送する。アプリには
	 * {@code -Djimble.server.port=<appPort>} で渡る。
	 * </p>
	 *
	 * @return	ポート
	 */
	public abstract Property<Integer> getAppPort ();

	/**
	 * 環境名（既定 {@code local}）
	 *
	 * <p>アプリには {@code -Djimble.env=<env>} で渡る。</p>
	 *
	 * @return	環境名
	 */
	public abstract Property<String> getEnv ();

	/**
	 * 変更があったときに流す Gradle タスク（既定 {@code <このプロジェクト>:classes}）
	 *
	 * <p>
	 * {@code classes} には {@code codegen}（{@code io.jimble.db}）と
	 * {@code generateJte}（{@code io.jimble.jte}）が繋がっているので、
	 * テンプレートと生成コードもここで作り直される。
	 * </p>
	 *
	 * @return	タスク
	 */
	public abstract ListProperty<String> getBuildTasks ();

	/**
	 * 追加で見張るディレクトリ（ルートプロジェクトからの相対パス）
	 *
	 * <p>
	 * このプロジェクトの {@code src} と {@code conf} は既定で見張る。
	 * 依存している別モジュールを直したときも作り直したい場合にここへ足す。
	 * </p>
	 *
	 * @return	ディレクトリ
	 */
	public abstract ListProperty<String> getWatchDirs ();

	/**
	 * 見張らないディレクトリ（ルートプロジェクトからの相対パス）
	 *
	 * @return	ディレクトリ
	 */
	public abstract ListProperty<String> getExcludeDirs ();

	/**
	 * 見張る拡張子（既定 {@code .java .jte .html .js .css .conf .xml .properties .yml .sql}）
	 *
	 * @return	拡張子
	 */
	public abstract ListProperty<String> getWatchExtensions ();

	/**
	 * アプリに渡すコマンドライン引数
	 *
	 * @return	引数
	 */
	public abstract ListProperty<String> getArgs ();

	/**
	 * 再起動のきっかけ（既定 {@code on_request}）
	 *
	 * <ul>
	 *   <li>{@code on_request} — 変更しても印を付けるだけで、
	 *       <b>次のリクエストが来たときに</b>作り直して再起動する。
	 *       連続保存で何度も再起動しない</li>
	 *   <li>{@code immediate} — 変更が落ち着いたら（{@link #getQuietMillis()}）すぐ再起動する</li>
	 * </ul>
	 *
	 * @return	きっかけ
	 */
	public abstract Property<String> getRestartMode ();

	/**
	 * 変更が落ち着いたと見なすまでの時間（ミリ秒。既定 300）
	 *
	 * <p>
	 * エディタの保存は数十ミリ秒のあいだに複数のイベントを出す。
	 * まとめて1回にするための待ち時間である。
	 * </p>
	 *
	 * @return	ミリ秒
	 */
	public abstract Property<Long> getQuietMillis ();

	/**
	 * アプリが起きるのを待つ上限（秒。既定 60）
	 *
	 * @return	秒
	 */
	public abstract Property<Integer> getStartTimeoutSeconds ();

}
