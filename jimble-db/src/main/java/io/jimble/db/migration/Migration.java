package io.jimble.db.migration;

import io.jimble.db.FrameworkTables;
import io.jimble.db.DB;
import io.jimble.db.DBSource;
import io.jimble.db.DBUtil;
import io.jimble.db.dialect.Dialects;
import io.jimble.db.lock.DBLock;
import io.jimble.db.migration.code.CodeMigration;
import io.jimble.db.version.DBVersion;
import io.jimble.util.comparator.FileNameComparator;
import io.jimble.util.data.Data;
import io.jimble.util.hash.Hash;
import io.jimble.util.io.FileUtil;
import io.jimble.util.io.IOUtil;
import io.jimble.util.log.Log;

import java.io.File;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * SQL マイグレーション
 *
 * <p>
 * {@code migration/<schema>/*.sql} を名前順に適用する。1ファイルは
 * {@code # --- !Ups} と {@code # --- !Downs} で up / down に分かれる（要件 F-G-05）。
 * </p>
 *
 * <h2>状態</h2>
 * <table>
 *   <caption>{@code migration.state}</caption>
 *   <tr><th>値</th><th>意味</th></tr>
 *   <tr><td>{@code complete}</td><td>up 適用済み</td></tr>
 *   <tr><td>{@code up_error}</td><td>up が失敗した。次回は down → up をやり直す</td></tr>
 *   <tr><td>{@code down_error}</td><td>down が失敗した。手で直すまで先に進まない</td></tr>
 * </table>
 *
 * <h2>移送元から変えたところ</h2>
 * <ol>
 *   <li><b>失敗を握りつぶさない。</b>移送元は例外を {@code Log.error} して黙って戻っていたため、
 *       <b>マイグレーションが失敗してもアプリが起動していた。</b>
 *       {@link MigrationException} を投げて起動を止める（要件 F-G-16）</li>
 *   <li><b>down を設定で切り替える</b>（D-2 / 要件 F-G-11）。既定は実行しない。
 *       down なしで適用済み SQL が書き換わっていた場合は、黙って進まず失敗させる（要件 F-G-10）</li>
 *   <li><b>ロック待ちに上限を置く</b>（要件 F-G-19）。
 *       {@code innodb_lock_wait_timeout} をこのセッションにだけ設定する</li>
 *   <li><b>コード生成を切り離した。</b>移送元はこの中でクラスの物理パスから4階層上を辿って
 *       {@code src/main/java} を探し当てて生成していた。<b>ビルド構成に強く依存して壊れやすい。</b>
 *       生成は Gradle の {@code codegen} タスクから行う（M3 ステップ6・7）</li>
 *   <li><b>down 未記述を警告する</b>（要件 F-G-12）</li>
 * </ol>
 */
public final class Migration {

	/** ロックキー（SQL・コードの両マイグレーションで共有する） */
	public static final String LOCK_KEY = "migration";

	private Migration () {}

	// region 起動時への組み込み

	/**
	 * 起動時マイグレーションを組み込む
	 *
	 * <p>
	 * {@link DBUtil} の起動タスクに登録する。<b>アプリの {@code main} から明示的に呼ぶ。</b>
	 * クラスパスを走査して勝手に有効化することはしない（原則2・NF-P-03）。
	 * </p>
	 *
	 * <pre>
	 * public static void main (String[] args) {
	 *     Migration.install();
	 *     ...
	 * }
	 * </pre>
	 *
	 * <p>
	 * 設定 {@code migration.on_startup} が false のときは何も登録しない（要件 F-G-17）。
	 * </p>
	 *
	 * @return	登録した場合 = true
	 */
	public static boolean install () {

		if (!MigrationConf.isOnStartup()) {
			Log.info("起動時マイグレーションは無効です（" + MigrationConf.KEY_ON_STARTUP + "）");
			return false;
		}

		// データソースごとに SQL マイグレーションを適用する（要件 F-G-09）
		DBUtil.addDataSourceTask((dbSource, db, appClass) -> migrate(dbSource, appClass));

		// 全データソースの初期化後にコードマイグレーションを実行する（要件 F-G-18）
		DBUtil.addStartupTask(CodeMigration::execute);

		return true;

	}

	// endregion

	// region 適用

	/**
	 * マイグレーションを適用する
	 *
	 * @param dbSource	データソース
	 * @param appClass	アプリケーションクラス（SQL ファイルの探索に使う）
	 * @throws MigrationException	適用に失敗した場合
	 */
	public static void migrate (DBSource dbSource, Class<?> appClass) {

		migrate(dbSource, list(dbSource, appClass));

	}

	/**
	 * マイグレーションを適用する
	 *
	 * <p>
	 * SQL ファイルの探索結果を直接渡す形。クラスパスの外に置いた SQL を流したいとき
	 * （テスト・CLI）に使う。
	 * </p>
	 *
	 * @param dbSource	データソース
	 * @param fileList	SQL ファイル一覧（名前順に並べ替えてから使う）
	 * @throws MigrationException	適用に失敗した場合
	 */
	public static void migrate (DBSource dbSource, List<MigrationInfo> fileList) {

		createTables(dbSource);

		/*
		 * 「いまの製品で流すもの」と「置いてあるもの全部」の両方を持つ。
		 * 消えた判定（down）は<b>全部</b>を見なければならない。
		 * 製品で絞ったほうを見ると、他の製品向けのファイルが「消えた」ことになり、
		 * <b>相手の製品で流したテーブルを down する</b>（要件 F-G-20）。
		 */
		List<MigrationInfo> all = sortByName(fileList);
		List<MigrationInfo> sorted = forProduct(dbSource, all);

		// 複数インスタンスが同時に起動しても一度しか適用しない（要件 F-G-15。D-1 ロックテーブル方式）
		DB lockDB = DBUtil.getDB(dbSource.name);
		DBLock.create(lockDB, LOCK_KEY);

		try (lockDB) {

			lockDB.beginTransaction();

			setLockTimeout(lockDB);

			if (!DBLock.lock(lockDB, LOCK_KEY)) {
				throw new MigrationException(
					"マイグレーションのロックを取得できませんでした（%d 秒待機）。他のインスタンスが適用中の可能性があります。"
						.formatted(MigrationConf.lockTimeout().toSeconds())
				);
			}

			apply(dbSource, sorted, all);

			lockDB.commitEndTransaction();

		} catch (MigrationException ex) {

			rollbackQuietly(lockDB);
			throw ex;

		} catch (Exception ex) {

			rollbackQuietly(lockDB);
			throw new MigrationException("マイグレーションに失敗しました: " + dbSource.name, ex);

		}

	}

	/**
	 * ロック待ちの上限を設定する
	 *
	 * <p>
	 * このセッションにだけ効く。待ちきれなかったインスタンスは
	 * {@link DBLock#lock} が false を返し、起動に失敗する（要件 F-G-19）。
	 * </p>
	 *
	 * @param db	DB
	 */
	private static void setLockTimeout (DB db) {

		db.execute(db.dialect().setLockTimeoutSql((int) MigrationConf.lockTimeout().toSeconds()));

	}

	/**
	 * ロールバックする（失敗しても握りつぶす）
	 *
	 * @param db	DB
	 */
	private static void rollbackQuietly (DB db) {

		try {
			db.rollbackEndTransaction();
		} catch (Exception ignore) {
			// ロールバック自体の失敗は元の例外を隠さないよう黙る
		}

	}

	/**
	 * 適用本体
	 *
	 * @param dbSource	データソース
	 * @param fileList	SQL ファイル一覧（いまの製品で流すもの）
	 * @param allFiles	SQL ファイル一覧（製品で絞る前の全部）
	 */
	private static void apply (DBSource dbSource, List<MigrationInfo> fileList, List<MigrationInfo> allFiles) {

		DB db = DBUtil.getDB(dbSource.name);

		List<Data> appliedList = sortedApplied(db);

		// 0. 名前だけ変わったものを見つけたら、そこで止める
		checkRenamed(dbSource, allFiles, appliedList);

		/*
		 * 1. SQL ファイルが消えたものを down する（新しいものから）
		 *
		 * 見るのは<b>製品で絞る前</b>の一覧である。他の製品向けのファイルは
		 * 「置いてあるが流さない」だけで、消えたわけではない。
		 */
		for (Data applied : appliedList.reversed()) {
			if (!containsFile(allFiles, applied.getString("name"))) {
				downRemoved(db, applied);
			}
		}

		// 2. SQL ファイルを名前順に適用する
		for (MigrationInfo info : fileList) {
			applyOne(db, info, findApplied(appliedList, info.sqlFileName));
		}

	}

	/**
	 * 名前が変わっただけのファイルを見つける
	 *
	 * <p>
	 * 適用済みは<b>ファイル名で覚えている</b>ので、名前を変えると別のファイルになる。
	 * {@code 001_create_post.sql} を製品ごとに分けて {@code 001_create_post.mysql.sql} に
	 * 変えると（要件 F-G-20）、<b>同じ SQL がもう一度流れて
	 * 「Table 'post' already exists」になる</b>。
	 * </p>
	 *
	 * <p>
	 * 見分けるのは<b>製品の接尾辞を落とした名前</b>である。中身のハッシュで見ると、
	 * 分けた先の SQL は製品ごとに中身が違うので<b>片方の製品でしか気づけない</b>し、
	 * たまたま中身が同じだけの別のファイルを取り違える。
	 * </p>
	 *
	 * <p>
	 * 履歴の付け替えは<b>やらない</b>。名前が同じでも別の意図かもしれないし、
	 * 履歴を黙って書き換えるのは後から追えない。
	 * <b>何をすれば直るかだけを出して止める。</b>
	 * </p>
	 *
	 * <p>
	 * {@code up_error}（up が失敗したまま）は見送る。<b>そちらは元から
	 * 履歴を消してやり直す道がある</b>（{@link #downRemoved}）ので、
	 * ここで止めると自力で直らなくなる。
	 * </p>
	 *
	 * @param dbSource		データソース
	 * @param allFiles		SQL ファイル一覧（製品で絞る前の全部）
	 * @param appliedList	適用済みレコード
	 */
	private static void checkRenamed (DBSource dbSource, List<MigrationInfo> allFiles, List<Data> appliedList) {

		String product = dbSource.dialect().name();

		// まとめて出す。1件ずつ止めると、名前を変えた数だけ流し直すことになる
		List<String> renamed = new ArrayList<>();
		List<String> sqls = new ArrayList<>();
		List<String> others = new ArrayList<>();

		for (Data applied : appliedList) {

			String oldName = applied.getString("name");

			if (containsFile(allFiles, oldName)) {
				// ファイルはある。名前は変わっていない
				continue;
			}

			if ("up_error".equals(applied.getString("state"))) {
				continue;
			}

			String base = baseName(oldName);
			MigrationInfo hit = null;
			boolean forOtherProduct = false;

			for (MigrationInfo info : allFiles) {

				if (!base.equals(baseName(info.sqlFileName))
					|| findApplied(appliedList, info.sqlFileName) != null) {
					continue;
				}

				String suffix = productSuffix(info.sqlFileName);

				if (suffix == null || suffix.equals(product)) {
					hit = info;
					break;
				}

				forOtherProduct = true;

			}

			if (hit != null) {

				renamed.add("%s → %s".formatted(oldName, hit.sqlFileName));

				/*
				 * 中身まで変わっているなら hash も一緒に入れ替える。
				 * 名前だけ直すと、次は「書き換えられています」（要件 F-G-10）で止まる。
				 * その版は<b>すでに当たっている</b>ので、当て直すのではなく記録を合わせる。
				 */
				String hash = Hash.md5(hashText(readSqlText(hit)));

				sqls.add(hash.equals(applied.getString("hash"))
					? "  UPDATE migration SET name = '%s' WHERE name = '%s';"
						.formatted(hit.sqlFileName, oldName)
					: "  UPDATE migration SET name = '%s', hash = '%s' WHERE name = '%s';"
						.formatted(hit.sqlFileName, hash, oldName));

				sqls.add("  UPDATE migration_history SET name = '%s' WHERE name = '%s';"
					.formatted(hit.sqlFileName, oldName));

			} else if (forOtherProduct) {
				others.add(oldName);
			}

		}

		if (!others.isEmpty()) {
			throw new MigrationException("""
				適用済みのマイグレーション SQL が、他の製品向けの名前に変わっています（いまは %s）:
				  %s
				いまの製品で流すファイルが無いので、この DB では二度と読まれません。
				%s 向けのファイルを置くか、履歴を消してください。
				  DELETE FROM migration_history WHERE name = '%s';
				  DELETE FROM migration WHERE name = '%s';"""
				.formatted(product, String.join("\n  ", others), product
					, others.getFirst(), others.getFirst()));
		}

		if (renamed.isEmpty()) {
			return;
		}

		throw new MigrationException("""
			マイグレーション SQL の名前が変わったようです（いまは %s）:
			  %s
			適用済みはファイル名で覚えているので、このままだと同じ SQL がもう一度流れます。
			履歴の名前も変えてください。
			%s
			この SQL はこの DB（%s）のものです。他の製品の DB では、その製品向けのファイル名に読み替えてください。
			履歴に残る up / down は古いままになります（migration.down = true のときだけ効きます）。
			名前を変えたのではなく、同じ SQL を別のものとして流したいのなら、番号を変えてください。"""
			.formatted(product, String.join("\n  ", renamed)
				, String.join("\n", sqls), product));

	}

	/**
	 * SQL ファイルが消えた分を down する
	 *
	 * @param db		DB
	 * @param applied	適用済みレコード
	 */
	private static void downRemoved (DB db, Data applied) {

		String name = applied.getString("name");

		if ("up_error".equals(applied.getString("state"))) {
			// up が失敗したまま SQL ファイルが消された。down すべきものが無いので履歴だけ消す
			db.delete("DELETE FROM migration WHERE name = ?", name);
			return;
		}

		if (!MigrationConf.isDownEnabled()) {
			Log.warn("SQL ファイルが見つかりませんが down は無効です。適用済みのまま残します: " + name);
			return;
		}

		String down = applied.getString("down");
		if (down == null || down.isEmpty()) {
			Log.warn("down が記述されていないため巻き戻せません: " + name);
			db.delete("DELETE FROM migration WHERE name = ?", name);
			return;
		}

		String errorMessage = execute(db, name, "down", down);
		if (errorMessage != null) {
			markError(db, name, "down_error", errorMessage);
			throw new MigrationException("down に失敗しました: %s / %s".formatted(name, errorMessage));
		}

		db.delete("DELETE FROM migration WHERE name = ?", name);

	}

	/**
	 * SQL ファイル1件を適用する
	 *
	 * @param db		DB
	 * @param info		SQL ファイル
	 * @param applied	適用済みレコード（未適用なら null）
	 */
	private static void applyOne (DB db, MigrationInfo info, Data applied) {

		String name = info.sqlFileName;
		String sqlText = readSqlText(info);
		String hash = Hash.md5(hashText(sqlText));
		String[] upDown = MigrationSql.toUpDown(sqlText);
		String up = upDown[0];
		String down = upDown[1];

		if (down.isEmpty()) {
			// 適用は妨げない（要件 F-G-12）
			Log.warn("down が記述されていません: " + name);
		}

		if (applied == null) {
			applyNew(db, name, hash, up, down);
			return;
		}

		String state = applied.getString("state");

		if ("down_error".equals(state)) {
			throw new MigrationException(
				"down が失敗したまま残っています。手で直してください: %s / %s".formatted(name, applied.getString("error_info"))
			);
		}

		if ("up_error".equals(state)) {
			reapply(db, applied, name, hash, up, down);
			return;
		}

		// complete
		if (!hash.equals(applied.getString("hash"))) {
			// 適用済みの SQL が書き換わっている（要件 F-G-10）
			if (!MigrationConf.isDownEnabled()) {
				throw new MigrationException(
					("適用済みのマイグレーションが書き換えられています: %s"
						+ "（down が無効なため巻き戻せません。新しい SQL ファイルを追加してください）").formatted(name)
				);
			}
			reapply(db, applied, name, hash, up, down);
		}

	}

	/**
	 * 未適用の SQL を適用する
	 *
	 * @param db	DB
	 * @param name	SQL ファイル名
	 * @param hash	ハッシュ
	 * @param up	up
	 * @param down	down
	 */
	private static void applyNew (DB db, String name, String hash, String up, String down) {

		String errorMessage = up.isEmpty() ? null : execute(db, name, "up", up);

		db.insert(
			"INSERT INTO migration (name, hash, up, down, state, error_info) VALUES (?, ?, ?, ?, ?, ?)"
			, name
			, hash
			, up
			, down
			, errorMessage == null ? "complete" : "up_error"
			, errorMessage
		);

		if (errorMessage != null) {
			throw new MigrationException("up に失敗しました: %s / %s".formatted(name, errorMessage));
		}

		Log.info("マイグレーションを適用しました: " + name);

	}

	/**
	 * 適用し直す（旧 down → 新 up）
	 *
	 * @param db		DB
	 * @param applied	適用済みレコード
	 * @param name		SQL ファイル名
	 * @param hash		新しいハッシュ
	 * @param up		新しい up
	 * @param down		新しい down
	 */
	private static void reapply (DB db, Data applied, String name, String hash, String up, String down) {

		String oldDown = applied.getString("down");
		if (oldDown != null && !oldDown.isEmpty()) {
			String downError = execute(db, name, "down", oldDown);
			if (downError != null) {
				markError(db, name, "down_error", downError);
				throw new MigrationException("down に失敗しました: %s / %s".formatted(name, downError));
			}
		}

		String errorMessage = up.isEmpty() ? null : execute(db, name, "up", up);

		db.update(
			"UPDATE migration SET hash = ?, up = ?, down = ?, state = ?, error_info = ? WHERE name = ?"
			, hash
			, up
			, down
			, errorMessage == null ? "complete" : "up_error"
			, errorMessage
			, name
		);

		if (errorMessage != null) {
			throw new MigrationException("up に失敗しました: %s / %s".formatted(name, errorMessage));
		}

		Log.info("マイグレーションを適用し直しました: " + name);

	}

	/**
	 * エラー状態にする
	 *
	 * @param db			DB
	 * @param name			SQL ファイル名
	 * @param state			状態
	 * @param errorMessage	エラーメッセージ
	 */
	private static void markError (DB db, String name, String state, String errorMessage) {

		db.update(
			"UPDATE migration SET state = ?, error_info = ? WHERE name = ?"
			, state
			, errorMessage
			, name
		);

	}

	// endregion

	// region SQL の実行

	/**
	 * SQL を実行する
	 *
	 * <p>
	 * <b>書いてあるのに1文も取り出せなかったら失敗にする。</b>
	 * 全部コメントだったということなので、成功として通すと
	 * <b>「down したことにして履歴だけ消す」</b>（{@link #downRemoved}）が起きる。
	 * テーブルは残ったまま履歴だけ消えるので、次の適用が
	 * 「already exists」で落ちるまで気づけない。
	 * </p>
	 *
	 * @param db	DB
	 * @param name	SQL ファイル名
	 * @param kind	種別（up / down）
	 * @param sqls	SQL（複数文）
	 * @return	エラーメッセージ（成功時は null）
	 */
	private static String execute (DB db, String name, String kind, String sqls) {

		List<String> list = MigrationSql.split(sqls, db.dialect());

		if (list.isEmpty()) {
			return "実行できる SQL がありません（%s が全部コメントです）".formatted(kind);
		}

		for (String sql : list) {

			db.execute(sql);

			if (db.isError()) {
				String errorMessage = db.getError().getMessage();
				addHistory(db, name, kind, sql, errorMessage);
				return errorMessage;
			}

			addHistory(db, name, kind, sql, null);

		}

		return null;

	}

	/**
	 * 実行履歴を残す
	 *
	 * @param db			DB
	 * @param name			SQL ファイル名
	 * @param kind			種別
	 * @param sql			SQL
	 * @param errorMessage	エラーメッセージ
	 */
	private static void addHistory (DB db, String name, String kind, String sql, String errorMessage) {

		boolean isError = errorMessage != null && !errorMessage.isEmpty();

		db.insert("""
				INSERT INTO migration_history (
					name
					, kind
					, sql_text
					, state
					, error_info
					, executed_at
				) VALUES (
					?
					, ?
					, ?
					, ?
					, ?
					, NOW()
				)
			"""
			, name
			, kind
			, sql
			, isError ? "error" : "complete"
			, isError ? errorMessage : null
		);

	}

	// endregion

	// region SQL ファイルの探索

	/**
	 * SQL ファイル一覧を名前順で取得する
	 *
	 * @param dbSource	データソース
	 * @param appClass	アプリケーションクラス
	 * @return	SQL ファイル一覧
	 */
	private static List<MigrationInfo> list (DBSource dbSource, Class<?> appClass) {

		String dir = MigrationConf.resourceDir() + "/" + dbSource.conf.schema;

		URL url = appClass.getClassLoader().getResource(dir);
		if (url == null) {
			Log.warn("マイグレーション SQL のディレクトリがありません: " + dir);
			return new ArrayList<>();
		}

		/*
		 * jar の中かどうかは「アプリが jar で動いているか」ではなく
		 * 「この URL が jar の中を指しているか」で決める。
		 *
		 * 移送元は appClass が jar かどうかを見ていた。フレームワークが jar で、
		 * SQL がディレクトリにある構成（Gradle からの実行）では判定を誤る。
		 */
		if ("jar".equals(url.getProtocol())) {
			return listInJar(appClass, url, dir);
		}

		return listInDirectory(url);

	}

	/**
	 * SQL ファイル一覧を名前順に並べ替える
	 *
	 * <p>{@code 2.sql} が {@code 10.sql} より先に来る自然順（{@link FileNameComparator}）。</p>
	 *
	 * @param fileList	SQL ファイル一覧
	 * @return	並べ替えた一覧（引数は変更しない）
	 */
	private static List<MigrationInfo> sortByName (List<MigrationInfo> fileList) {

		List<MigrationInfo> list = new ArrayList<>(fileList);

		list.sort(new Comparator<MigrationInfo>() {
			@Override
			public int compare (MigrationInfo o1, MigrationInfo o2) {
				try {
					return FileNameComparator.strCmpLogical(o1.sqlFileName, o2.sqlFileName);
				} catch (Exception ex) {
					return o1.sqlFileName.compareTo(o2.sqlFileName);
				}
			}
		});

		return list;

	}

	/**
	 * いまの製品で流すものだけに絞る（要件 F-G-20）
	 *
	 * <p>
	 * ファイル名の接尾辞で分ける。{@code 001_create_post.mysql.sql} は MySQL のときだけ、
	 * {@code 001_create_post.postgresql.sql} は PostgreSQL のときだけ流す。
	 * <b>接尾辞の無いファイルはどの製品でも流す</b>（両方で同じ SQL が通るなら分けなくていい）。
	 * </p>
	 *
	 * <p>
	 * 別名も同じものとして扱う（{@code .mariadb.sql} は MySQL）。
	 * 判定は {@link Dialects#productNameOrNull} に任せる。
	 * <b>設定に書ける名前とファイル名の判定がずれると、置いたのに流れない SQL ができる</b>ため。
	 * </p>
	 *
	 * <p>
	 * 適用済みの記録はファイル名で持つので、製品ごとに別の名前になる。
	 * <b>MySQL で流したものを PostgreSQL の記録が「消えた」と見なして down することはない。</b>
	 * 逆に、片方の製品でしか使わない SQL を後から消したときは、その製品でだけ down が走る。
	 * </p>
	 *
	 * @param dbSource	データソース
	 * @param fileList	SQL ファイル一覧
	 * @return	いまの製品で流すもの
	 */
	private static List<MigrationInfo> forProduct (DBSource dbSource, List<MigrationInfo> fileList) {

		String product = dbSource.dialect().name();

		List<MigrationInfo> list = new ArrayList<>();
		List<String> skipped = new ArrayList<>();

		for (MigrationInfo info : fileList) {

			String suffix = productSuffix(info.sqlFileName);

			if (suffix == null || suffix.equals(product)) {
				list.add(info);
			} else {
				skipped.add(info.sqlFileName);
			}

		}

		if (!skipped.isEmpty()) {
			// 黙って飛ばすと「置いたのに流れない」に気づけない
			Log.info("マイグレーション: 他の製品向けを飛ばしました（いまは %s）: %s"
				.formatted(product, String.join(", ", skipped)));
		}

		checkSameVersion(product, list);

		return list;

	}

	/**
	 * 同じ版が二重に流れないか見る
	 *
	 * <p>
	 * 製品名には別名があるので（{@code .mariadb.sql} も {@code .mysql.sql} も MySQL）、
	 * 両方置くと<b>同じ SQL が2回流れる</b>。接尾辞なしと接尾辞つきを同じ版に置いた場合も同じ。
	 * ファイル名が違うので適用済みの記録も別々になり、<b>2つ目が
	 * 「already exists」で落ちるまで気づけない。</b>
	 * </p>
	 *
	 * @param product	いまの製品
	 * @param list		いまの製品で流すファイル一覧
	 */
	private static void checkSameVersion (String product, List<MigrationInfo> list) {

		Map<String, String> seen = new HashMap<>();

		for (MigrationInfo info : list) {

			String before = seen.put(baseName(info.sqlFileName), info.sqlFileName);

			if (before != null) {
				throw new MigrationException(
					"同じ版のマイグレーション SQL が、いまの製品（%s）で2つとも流れます: %s / %s（どちらかにしてください）"
						.formatted(product, before, info.sqlFileName));
			}

		}

	}

	/**
	 * ファイル名から製品名を取り出す
	 *
	 * <p>
	 * {@code 001_create_post.postgresql.sql} なら {@code postgresql}。
	 * 拡張子の手前が製品名として読めないとき（{@code 001_create_post.sql}、
	 * {@code v1.2_create.sql}）は null を返す。
	 * </p>
	 *
	 * <p>
	 * {@code .sql} で終わらない名前も null を返す（{@link #migrate(DBSource, List)} は
	 * 一覧を外から渡せるので、<b>拡張子を当てにして切り落としてはいけない</b>）。
	 * </p>
	 *
	 * @param fileName	SQL ファイル名
	 * @return	正規の製品名。製品の指定が無ければ null
	 */
	private static String productSuffix (String fileName) {

		String base = withoutSqlExtension(fileName);

		int dot = base.lastIndexOf('.');
		if (dot < 0) {
			return null;
		}

		return Dialects.productNameOrNull(base.substring(dot + 1));

	}

	/**
	 * 製品名の接尾辞も拡張子も落とした名前
	 *
	 * <p>
	 * {@code 001_create_post.sql} も {@code 001_create_post.mysql.sql} も
	 * {@code 001_create_post} になる。<b>製品ごとに分けたときに
	 * 「同じ版」だと分かるのはこの名前である</b>（要件 F-G-20）。
	 * </p>
	 *
	 * @param fileName	SQL ファイル名
	 * @return	名前
	 */
	private static String baseName (String fileName) {

		String base = withoutSqlExtension(fileName);

		int dot = base.lastIndexOf('.');
		if (dot < 0 || Dialects.productNameOrNull(base.substring(dot + 1)) == null) {
			return base;
		}

		return base.substring(0, dot);

	}

	/**
	 * {@code .sql} を落とす（付いていなければそのまま）
	 *
	 * @param fileName	SQL ファイル名
	 * @return	名前
	 */
	private static String withoutSqlExtension (String fileName) {

		String name = fileName == null ? "" : fileName;

		return name.toLowerCase().endsWith(".sql")
			? name.substring(0, name.length() - ".sql".length())
			: name;

	}

	/**
	 * ディレクトリから SQL ファイル一覧を取得する
	 *
	 * @param url	リソースディレクトリの URL
	 * @return	SQL ファイル一覧
	 */
	private static List<MigrationInfo> listInDirectory (URL url) {

		List<MigrationInfo> list = new ArrayList<>();

		File[] files = new File(url.getFile()).listFiles();
		if (files == null) {
			return list;
		}

		for (File file : files) {
			if (!file.isFile() || !file.getName().endsWith(".sql")) {
				continue;
			}
			MigrationInfo info = new MigrationInfo();
			info.sqlFile = file;
			info.sqlFileName = file.getName();
			list.add(info);
		}

		return list;

	}

	/**
	 * jar の中から SQL ファイル一覧を取得する
	 *
	 * @param appClass	アプリケーションクラス
	 * @param url		リソースディレクトリの URL（{@code jar:} プロトコル）
	 * @param dir		リソースディレクトリ
	 * @return	SQL ファイル一覧
	 */
	private static List<MigrationInfo> listInJar (Class<?> appClass, URL url, String dir) {

		List<MigrationInfo> list = new ArrayList<>();

		String prefix = dir + "/";

		try {
			JarURLConnection connection = (JarURLConnection) url.openConnection();
			try (JarFile jar = connection.getJarFile()) {
				Enumeration<JarEntry> entries = jar.entries();
				while (entries.hasMoreElements()) {
					JarEntry entry = entries.nextElement();
					if (!entry.getName().startsWith(prefix) || !entry.getName().endsWith(".sql")) {
						continue;
					}
					MigrationInfo info = new MigrationInfo();
					info.sqlFilePathInJar = entry.getName();
					info.sqlFileName = entry.getName().substring(prefix.length());
					info.classLoader = appClass.getClassLoader();
					list.add(info);
				}
			}
		} catch (Exception ex) {
			throw new MigrationException("jar 内のマイグレーション SQL を読めませんでした: " + dir, ex);
		}

		return list;

	}

	/**
	 * SQL 全文を読む
	 *
	 * <p>
	 * <b>読んだままを返す（改行を潰さない）。</b>潰すと {@code --} や {@code #} の
	 * 行コメントが行末で終わらなくなり、<b>そこから先の SQL が全部コメントになる</b>。
	 * ハッシュを取るときだけ {@link #hashText} で潰す。
	 * </p>
	 *
	 * @param info		SQL ファイル
	 * @return	SQL 全文
	 */
	private static String readSqlText (MigrationInfo info) {

		String text = info.sqlFilePathInJar == null
			? FileUtil.readAll(info.sqlFile)
			: IOUtil.read(info.classLoader.getResourceAsStream(info.sqlFilePathInJar));

		if (text == null) {
			throw new MigrationException("マイグレーション SQL を読めませんでした: " + info.sqlFileName);
		}

		return text;

	}

	/**
	 * ハッシュを取るための形にする
	 *
	 * <p>
	 * タブと改行は空白に潰す（改行コードでハッシュが変わらないように）。
	 * <b>この潰し方を変えると、適用済みのマイグレーションが
	 * すべて「書き換えられた」ことになる</b>（要件 F-G-10）ので変えないこと。
	 * </p>
	 *
	 * @param sqlText	SQL 全文
	 * @return	潰した SQL
	 */
	private static String hashText (String sqlText) {

		return sqlText.replace("\t", " ").replaceAll("\\R", " ").trim();

	}

	// endregion

	// region 適用済みレコード

	/**
	 * 適用済みレコードを名前順で取得する
	 *
	 * @param db	DB
	 * @return	適用済みレコード
	 */
	private static List<Data> sortedApplied (DB db) {

		List<Data> list = db.selectList("SELECT * FROM migration");

		list.sort(new Comparator<Data>() {
			@Override
			public int compare (Data o1, Data o2) {
				try {
					return FileNameComparator.strCmpLogical(o1.getString("name"), o2.getString("name"));
				} catch (Exception ex) {
					return o1.getString("name").compareTo(o2.getString("name"));
				}
			}
		});

		return list;

	}

	/**
	 * SQL ファイル一覧に含まれるか
	 *
	 * @param list	SQL ファイル一覧
	 * @param name	SQL ファイル名
	 * @return	含まれる場合 = true
	 */
	private static boolean containsFile (List<MigrationInfo> list, String name) {

		for (MigrationInfo info : list) {
			if (info.sqlFileName.equals(name)) {
				return true;
			}
		}

		return false;

	}

	/**
	 * 適用済みレコードを探す
	 *
	 * @param list	適用済みレコード
	 * @param name	SQL ファイル名
	 * @return	適用済みレコード（無ければ null）
	 */
	private static Data findApplied (List<Data> list, String name) {

		for (Data data : list) {
			if (name.equals(data.getString("name"))) {
				return data;
			}
		}

		return null;

	}

	// endregion

	// region テーブル作成

	/**
	 * 管理テーブルを作る
	 *
	 * @param dbSource	データソース
	 */
	private static void createTables (DBSource dbSource) {

		DB db = DBUtil.getDB(dbSource.name);

		DBVersion migration = new DBVersion(FrameworkTables.MIGRATION, "マイグレーション情報");
		migration.add(1)
			.mysql("""
				create table migration (
					name varchar(255) not null primary key
					, hash varchar(255) not null
					, up mediumtext null
					, down mediumtext null
					, state varchar(255) null
					, error_info text null
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='%s'
			""".formatted(migration.placeholder()))
			.postgresql("""
				create table migration (
					name varchar(255) not null primary key
					, hash varchar(255) not null
					, up text null
					, down text null
					, state varchar(255) null
					, error_info text null
				)
			""");
		if (!migration.apply(db)) {
			throw new MigrationException("migration テーブルを作成できませんでした: " + dbSource.name);
		}

		DBVersion history = new DBVersion(FrameworkTables.MIGRATION_HISTORY, "マイグレーション履歴");
		history.add(1)
			.mysql("""
				create table migration_history (
					id bigint unsigned auto_increment primary key
					, name varchar(255) not null
					, kind varchar(100) not null
					, sql_text longtext null
					, state varchar(255) null
					, error_info text null
					, executed_at datetime not null
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='%s'
			""".formatted(history.placeholder()))
			.postgresql("""
				create table migration_history (
					id bigserial primary key
					, name varchar(255) not null
					, kind varchar(100) not null
					, sql_text text null
					, state varchar(255) null
					, error_info text null
					, executed_at timestamp not null
				)
			""");
		if (!history.apply(db)) {
			throw new MigrationException("migration_history テーブルを作成できませんでした: " + dbSource.name);
		}

	}

	// endregion

}
