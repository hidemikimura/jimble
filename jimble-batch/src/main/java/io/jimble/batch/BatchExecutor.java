package io.jimble.batch;

import io.jimble.db.DB;
import io.jimble.db.DBUtil;
import io.jimble.db.cache.Cache;
import io.jimble.db.cache.ICache;
import io.jimble.util.conf.Conf;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;

import java.util.Date;

/**
 * バッチの起動（要件 F-B-01〜09）
 *
 * <pre>
 * public class Batch {
 *
 *     public static void main (String[] args) {
 *
 *         // 1. 設定と DB
 *         DBUtil.load(Conf.conf().config(), Batch.class);
 *
 *         // 2. テーブルとバッチの登録（要件 F-B-09）
 *         BatchTables.install(DBUtil.getMainDB());
 *         BatchRegistry.add(RssFetchBatch::new);
 *         BatchRegistry.sync(DBUtil.getMainDB());
 *
 *         // 3. 実行
 *         System.exit(BatchExecutor.start(args).isExecuted() ? 0 : 1);
 *     }
 * }
 * </pre>
 *
 * <pre>
 * java -cp app.jar app.Batch env=local class=app.batch.RssFetchBatch site_id=42
 * </pre>
 *
 * <h2>移送元から直したところ</h2>
 * <ol>
 *   <li><b>アプリケーションの起動をここでやっていた。</b>
 *       移送元は jooby / Jetty をここから立ち上げており、<b>そのために
 *       テンポラリディレクトリのファイルロックで
 *       プロセス間の起動を直列化する処理が 120 行あった</b>
 *       （複数のバッチが同時に起動すると jooby の初期化がぶつかるため）。
 *       jimble のバッチは <b>Web サーバーを起動しない</b>ので、まるごと要らない。
 *       起動の順番はアプリの {@code main} に書く（原則1）</li>
 *   <li><b>CLI の {@code class=} を {@code Class.forName} に渡していた。</b>
 *       コマンドラインの文字列から任意のクラスを組み立てていたことになる。
 *       {@link BatchRegistry} に登録されたものだけを引く</li>
 *   <li><b>スケジューラ ID を MAC アドレスから取っていた</b>（{@link BatchConf#schedulerId()}）</li>
 *   <li><b>結果を返していなかった。</b>実行されなくても正常終了と区別が付かない。
 *       {@link BatchResult} を返す</li>
 * </ol>
 */
public final class BatchExecutor {

	/** 全バッチ停止フラグのキャッシュキー */
	public static final String KEY_ALL_STOP = "jimble_batch_all_not_start";

	/* バッチとして起動されたか */
	private static volatile boolean batchInstance = false;

	private BatchExecutor () {}

	/**
	 * バッチとして起動されたか
	 *
	 * @return	バッチの場合 = true
	 */
	public static boolean isBatchInstance () {

		return batchInstance;

	}

	/**
	 * コマンドライン引数から実行する
	 *
	 * @param args	コマンドライン引数
	 * @return	結果
	 */
	public static BatchResult start (String[] args) {

		batchInstance = true;

		BatchArgs batchArgs = parseArgs(args);

		if (batchArgs.className == null || batchArgs.className.isEmpty()) {
			Log.error("バッチのクラス名が指定されていません: class=<クラス名> を渡してください");
			return BatchResult.invalid_args;
		}

		return execute(batchArgs);

	}

	/**
	 * 実行する
	 *
	 * @param batchArgs	引数
	 * @return	結果
	 */
	public static BatchResult execute (BatchArgs batchArgs) {

		AbstractBatch batch = BatchRegistry.create(batchArgs.className);

		if (batch == null) {
			Log.error("バッチが登録されていません: %s / BatchRegistry.add(%s::new) を書いてください"
				.formatted(batchArgs.className, simpleName(batchArgs.className)));
			return BatchResult.skipped_not_registered;
		}

		if (isAllNotStart()) {
			Log.warn("全バッチ停止フラグが立っています: %s".formatted(batchArgs.className));
			return BatchResult.skipped_all_stopped;
		}

		cleanupExecuteInfo();

		return batch.run(batchArgs, null);

	}

	/**
	 * コマンドライン引数を解析する（要件 F-B-03）
	 *
	 * @param args	コマンドライン引数
	 * @return	引数
	 */
	public static BatchArgs parseArgs (String[] args) {

		BatchArgs batchArgs = new BatchArgs();

		if (args != null) {

			for (String arg : args) {

				batchArgs.cliArgsList.add(arg);

				int index = arg.indexOf('=');

				if (index <= 0) {
					continue;
				}

				String key = arg.substring(0, index);
				String value = arg.substring(index + 1);

				if ("env".equalsIgnoreCase(key)) {
					batchArgs.env = value;
				} else if ("class".equalsIgnoreCase(key)) {
					batchArgs.className = value;
				} else {
					batchArgs.cliArgs.putData(key, value);
				}

			}

		}

		batchArgs.schedulerId = BatchConf.schedulerId();

		checkEnv(batchArgs);

		return batchArgs;

	}

	/**
	 * {@code env=} が効いているか確かめる（要件 D-79）
	 *
	 * <p>
	 * <b>{@code env=} では環境は変わらない。</b>
	 * 設定はここに来る前（{@code DBUtil.load} など）に読み終わっているので、
	 * <b>受け取っても手遅れ</b>である。
	 * </p>
	 *
	 * <p>
	 * それでも移送元からの書き方として README や手順書に残っており、
	 * <b>「本番のつもりで local の設定で流していた」</b>が起こりえた。
	 * 食い違っていたら、その場で止める。
	 * </p>
	 *
	 * @param batchArgs	引数
	 */
	private static void checkEnv (BatchArgs batchArgs) {

		if (batchArgs.env == null || batchArgs.env.isEmpty()) {
			return;
		}

		if (batchArgs.env.equals(Conf.env())) {
			return;
		}

		throw new IllegalArgumentException("""
			env=%s と指定されていますが、いま動いているのは env=%s です。
			  env= では環境は変わりません（設定はここに来る前に読み終わっています）。
			  JVM の引数で渡してください。
			    java -Djimble.env=%s -cp app.jar %s ..."""
			.formatted(batchArgs.env, Conf.env(), batchArgs.env, "<起動クラス>"));

	}

	// region 全バッチ停止（要件 F-B-07）

	/**
	 * 全バッチ停止フラグを立てる
	 *
	 * @return	立てられた場合 = true
	 */
	public static boolean allNotStart () {

		if (!DBUtil.isUseDB()) {
			return true;
		}

		try (DB db = DBUtil.getMainDB()) {

			Data data = new Data().putData("created_at", new Date());

			return Cache.instance(db).set(KEY_ALL_STOP, data.getJsonString(), "application/json");

		} catch (Exception ex) {

			Log.error(ex, "全バッチ停止フラグを立てられませんでした");
			return false;

		}

	}

	/**
	 * 全バッチ停止フラグを外す
	 *
	 * @return	外せた場合 = true
	 */
	public static boolean allReleaseNotStart () {

		if (!DBUtil.isUseDB()) {
			return true;
		}

		try (DB db = DBUtil.getMainDB()) {

			Cache.instance(db).remove(KEY_ALL_STOP);

			return true;

		} catch (Exception ex) {

			Log.error(ex, "全バッチ停止フラグを外せませんでした");
			return false;

		}

	}

	/**
	 * 全バッチ停止フラグが立っているか
	 *
	 * <p>
	 * 立ててから {@link BatchConf#allStop()} だけ効く。
	 * <b>外し忘れても、いつかは動き出す。</b>
	 * </p>
	 *
	 * @return	立っている場合 = true
	 */
	public static boolean isAllNotStart () {

		if (!DBUtil.isUseDB()) {
			return false;
		}

		try (DB db = DBUtil.getMainDB()) {

			ICache cache = Cache.instance(db);
			String json = cache.getString(KEY_ALL_STOP);

			if (json == null || json.isEmpty()) {
				return false;
			}

			Data data = Data.fromJsonString(json);
			long createdAt = data.getDateTime("created_at");

			return System.currentTimeMillis() - createdAt <= BatchConf.allStop().toMillis();

		} catch (Exception ex) {

			Log.error(ex, "全バッチ停止フラグを読めませんでした");
			return false;

		}

	}

	// endregion

	/**
	 * いまバッチが走っているか
	 *
	 * @return	走っている場合 = true
	 */
	public static boolean isExecutingBatch () {

		try (DB db = DBUtil.getMainDB()) {

			Data row = db.select("""
					SELECT
						uid
					FROM
						batch_execute_info
					WHERE
						updated_at >= %s
					LIMIT 1
				""".formatted(db.dialect().intervalFromNow("SECOND", true))
				, BatchConf.alive().toSeconds());

			return row != null;

		} catch (Exception ex) {

			Log.error(ex, "バッチの実行状況を読めませんでした");
			return false;

		}

	}

	/**
	 * 落ちたプロセスが残した実行情報を消す
	 *
	 * <p>
	 * 移送元はこれを仮想スレッドに投げっぱなしにしていた。
	 * <b>すぐ後で同時実行数を数えるので、消え終わる前に数えることがある。</b>
	 * 1本のクエリなので待ってよい。
	 * </p>
	 */
	public static void cleanupExecuteInfo () {

		try (DB db = DBUtil.getMainDB()) {

			db.delete("""
					DELETE FROM batch_execute_info
					WHERE
						updated_at < %s
				""".formatted(db.dialect().intervalFromNow("SECOND", true))
				, BatchConf.alive().toSeconds() * 3);

		} catch (Exception ex) {

			Log.error(ex, "実行情報の掃除に失敗しました");

		}

	}

	/**
	 * クラス名の末尾
	 *
	 * @param className	クラス名
	 * @return	末尾
	 */
	private static String simpleName (String className) {

		int index = className.lastIndexOf('.');

		return index < 0 ? className : className.substring(index + 1);

	}

}
