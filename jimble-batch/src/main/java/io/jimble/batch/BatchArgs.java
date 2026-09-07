package io.jimble.batch;

import io.jimble.util.data.Data;
import io.jimble.util.string.StringUtil;

import java.util.ArrayList;
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
 * {@link #cliArgs} に {@link Data} として入る。
 * </p>
 */
public class BatchArgs {

	/** 実行ごとの一意な値 */
	public String uid = StringUtil.uniqueString();

	/** 環境名 */
	public String env;

	/** バッチクラス名（パッケージ名を含む） */
	public String className;

	/** {@code key=value} 形式の引数 */
	public Data cliArgs = new Data();

	/** 引数をそのまま並べたもの */
	public List<String> cliArgsList = new ArrayList<>();

	/** スケジューラからの実行か */
	public boolean fromScheduler = false;

	/** どのインスタンスから実行されたか（{@link BatchConf#schedulerId()}） */
	public String schedulerId = "";

	/** cron */
	public String cron;

	/** バッチ設定（マスタの設定を上書きする） */
	public Data settings = null;

	/** マスタの状態や同時実行数を無視して実行するか */
	public boolean forceExecute = false;

}
