package io.jimble.batch;

import io.jimble.util.data.Data;
import io.jimble.util.string.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * バッチ引数（要件 F-B-03）
 *
 * <pre>
 * java -cp app.jar app.Batch env=local class=app.batch.RssFetchBatch site_id=42
 * </pre>
 *
 * <p>
 * {@code env} と {@code class} は jimble が使う。それ以外の {@code key=value} は
 * {@link #cliArgs()} に {@link Data} として入る。
 * </p>
 *
 * <p>
 * <b>public フィールドではない（D-173）。</b>フィールドは 1.0 のあと
 * アクセサに置き換えられない。ここは<b>「未設定」の表し方が3通りに割れていた</b>——
 * {@code settings} は {@code null}、{@code schedulerId} は空文字、
 * {@code cliArgs} は空の {@code Data} だった。読む側は<b>どれで書かれているかを
 * 覚えて分岐する</b>ことになり、片方を忘れても例外にはならない。
 * いまは<b>入れ物は必ず空で存在し、無いものは {@code isEmpty()} で分かる</b>。
 * </p>
 */
public final class BatchArgs {

	/** 実行ごとの一意な値 */
	private String uid = StringUtil.uniqueString();

	/** 環境名 */
	private String env;

	/** バッチクラス名（パッケージ名を含む） */
	private String className;

	/** {@code key=value} 形式の引数 */
	private Data cliArgs = new Data();

	/** 引数をそのまま並べたもの */
	private final List<String> cliArgsList = new ArrayList<>();

	/** スケジューラからの実行か */
	private boolean fromScheduler = false;

	/** どのインスタンスから実行されたか（{@link BatchConf#schedulerId()}） */
	private String schedulerId = "";

	/** cron（スケジューラからの実行でなければ null） */
	private String cron;

	/** バッチ設定（マスタの設定を上書きする。空なら上書きしない） */
	private Data settings = new Data();

	/** マスタの状態や同時実行数を無視して実行するか */
	private boolean forceExecute = false;

	/**
	 * 実行ごとの一意な値
	 *
	 * @return	一意な値
	 */
	public String uid () {

		return uid;

	}

	/**
	 * 環境名
	 *
	 * @return	環境名（{@code env=} が無ければ null）
	 */
	public String env () {

		return env;

	}

	/**
	 * 環境名を決める
	 *
	 * @param env	環境名
	 */
	public void env (String env) {

		this.env = env;

	}

	/**
	 * バッチクラス名
	 *
	 * @return	クラス名（{@code class=} が無ければ null）
	 */
	public String className () {

		return className;

	}

	/**
	 * バッチクラス名を決める
	 *
	 * @param className	クラス名
	 */
	public void className (String className) {

		this.className = className;

	}

	/**
	 * {@code key=value} 形式の引数
	 *
	 * @return	引数（無ければ空。null にはならない）
	 */
	public Data cliArgs () {

		return cliArgs;

	}

	/**
	 * {@code key=value} 形式の引数を差し替える
	 *
	 * @param cliArgs	引数（null なら空）
	 */
	public void cliArgs (Data cliArgs) {

		this.cliArgs = cliArgs == null ? new Data() : cliArgs;

	}

	/**
	 * 引数をそのまま並べたもの
	 *
	 * <p><b>中の {@code List} は返さない。</b>返すと外から書き換えられる。</p>
	 *
	 * @return	引数（読み取り専用）
	 */
	public List<String> cliArgsList () {

		return Collections.unmodifiableList(cliArgsList);

	}

	/**
	 * 引数を1つ足す
	 *
	 * @param arg	引数
	 */
	public void addCliArg (String arg) {

		cliArgsList.add(arg);

	}

	/**
	 * スケジューラからの実行か
	 *
	 * @return	スケジューラからなら true
	 */
	public boolean fromScheduler () {

		return fromScheduler;

	}

	/**
	 * スケジューラからの実行かを決める
	 *
	 * @param fromScheduler	スケジューラからなら true
	 */
	public void fromScheduler (boolean fromScheduler) {

		this.fromScheduler = fromScheduler;

	}

	/**
	 * どのインスタンスから実行されたか
	 *
	 * @return	インスタンス名（決めていなければ空文字。null にはならない）
	 */
	public String schedulerId () {

		return schedulerId;

	}

	/**
	 * どのインスタンスから実行されたかを決める
	 *
	 * @param schedulerId	インスタンス名（null なら空文字）
	 */
	public void schedulerId (String schedulerId) {

		this.schedulerId = schedulerId == null ? "" : schedulerId;

	}

	/**
	 * cron
	 *
	 * @return	cron（スケジューラからの実行でなければ null）
	 */
	public String cron () {

		return cron;

	}

	/**
	 * cron を決める
	 *
	 * @param cron	cron
	 */
	public void cron (String cron) {

		this.cron = cron;

	}

	/**
	 * バッチ設定（マスタの設定を上書きする）
	 *
	 * <p>
	 * <b>空なら上書きしない。</b>{@code null} を返していたころは、
	 * 読む側が {@code != null} を書き忘れると落ちた。
	 * </p>
	 *
	 * @return	設定（無ければ空。null にはならない）
	 */
	public Data settings () {

		return settings;

	}

	/**
	 * バッチ設定を差し替える
	 *
	 * @param settings	設定（null なら空）
	 */
	public void settings (Data settings) {

		this.settings = settings == null ? new Data() : settings;

	}

	/**
	 * マスタの状態や同時実行数を無視して実行するか
	 *
	 * @return	無視するなら true
	 */
	public boolean forceExecute () {

		return forceExecute;

	}

	/**
	 * マスタの状態や同時実行数を無視するかを決める
	 *
	 * @param forceExecute	無視するなら true
	 */
	public void forceExecute (boolean forceExecute) {

		this.forceExecute = forceExecute;

	}

	@Override
	public String toString () {

		return "BatchArgs(" + className + " env=" + env + " uid=" + uid + ")";

	}

}
