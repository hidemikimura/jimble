package io.jimble.gradle.ai;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * jimble の既知の落とし穴を、ソースと設定から機械的に見つける（要件 D-188）
 *
 * <h2>何を見るか</h2>
 * <p>
 * <b>動かしても黙って間違える</b>ものだけを見る。起動時に落ちて直し方を言うもの
 * （単位の無い時間など。要件 D-159）は、動かせば分かるので見ない。
 * どれも実際にアプリ側の AI が踏んだもの、または skill に「落とし穴」として書いてあるものである。
 * </p>
 *
 * <h2>読み方は荒い</h2>
 * <p>
 * Java は構文木にせず、<b>コメントと文字列を空白に潰してから</b>正規表現で見る
 * （プラグインに構文解析器を足さないため）。取りこぼしはあるが、<b>誤検知は抑止できる</b>：
 * 行末か前の行に {@code jimble-check:ignore J101} と書けば、その行のその規則は出さない。
 * </p>
 */
public final class JimbleChecker {

	/** 抑止の印 */
	public static final String IGNORE = "jimble-check:ignore";

	/** 引き先の頭 */
	static final String DOCS = "https://jimble.io/ja/";

	/**
	 * 重さ
	 */
	public enum Level {

		/** 直す（タスクを落とす） */
		ERROR,

		/** 見る（落とさない） */
		WARN

	}

	/**
	 * 見つけたもの
	 *
	 * @param rule		規則（{@code J101} の形）
	 * @param level		重さ
	 * @param file		ファイル（根からの相対パス）。ファイルに依らないものは空文字
	 * @param line		行（1 始まり）。行に依らないものは 0
	 * @param message	何が起きるか
	 * @param fix		直し方
	 * @param url		引き先（jimble.io の .md）
	 */
	public record Finding(String rule, Level level, String file, int line, String message, String fix, String url) {

		/**
		 * 1件ぶんの文
		 *
		 * @return	文
		 */
		public String format () {

			String where = file.isEmpty() ? "" : line > 0 ? " %s:%d".formatted(file, line) : " " + file;

			return "[%s %s]%s  %s%n    直し方: %s%n    詳しく: %s".formatted(rule, level, where, message, fix, url);

		}

	}

	/* J101: Request は Data を継いでいるが中身は空。get〜 はコンパイルが通って null / 空を返す */
	private static final Pattern REQUEST_GET = Pattern.compile("(?<!request\\(\\))\\.request\\(\\)\\s*\\.\\s*get[A-Z]\\w*\\s*\\(");

	/* J201: 環境変数らしい名前を ? なしで参照している */
	private static final Pattern ENV_WITHOUT_Q = Pattern.compile("\\$\\{([A-Z][A-Z0-9_]*)\\}");

	/* HOCON の代入（key = value / key : value / key { ） */
	private static final Pattern CONF_ASSIGN = Pattern.compile("^\\s*([A-Za-z0-9_.\\-\"]+)\\s*(=|:|\\+=)\\s*(.*)$");

	/* HOCON のブロックの開始（key {） */
	private static final Pattern CONF_BLOCK = Pattern.compile("^\\s*([A-Za-z0-9_.\\-\"]+)\\s*\\{\\s*$");

	/* 空の catch */
	private static final Pattern EMPTY_CATCH = Pattern.compile("catch\\s*\\([^)]*\\)\\s*\\{\\s*\\}");

	private JimbleChecker () {
	}

	/**
	 * 見る
	 *
	 * @param rootDir		プロジェクトの根（{@code .claude/} がある所）
	 * @param projectDir	プロジェクト（{@code src/} と {@code conf/} がある所）
	 * @param version		使っている jimble の版（プラグインの版）
	 * @return	見つけたもの
	 */
	public static List<Finding> check (Path rootDir, Path projectDir, String version) {

		// 2.0 では J9xx もいつも出す（要件 D-198）
		return check(rootDir, projectDir, version, true);

	}

	/**
	 * 見る（2.0 への移行も見るか選べる）
	 *
	 * @param rootDir		ルートプロジェクトのディレクトリ（skill の置き場）
	 * @param projectDir	見るプロジェクトのディレクトリ
	 * @param version		いまの jimble の版
	 * @param target2		{@code --target=2.0}：2.0 で型や意味が変わる呼び出し（J9xx）も出す
	 * @return	見つけたもの
	 */
	public static List<Finding> check (Path rootDir, Path projectDir, String version, boolean target2) {

		List<Finding> findings = new ArrayList<>();

		Map<Path, String> java = read(projectDir.resolve("src"), ".java");
		Map<Path, String> conf = new LinkedHashMap<>();
		conf.putAll(read(projectDir.resolve("conf"), ".conf"));
		conf.putAll(read(projectDir.resolve("src/main/resources"), ".conf"));

		for (Map.Entry<Path, String> file : java.entrySet()) {
			checkJava(projectDir, file.getKey(), file.getValue(), findings);
			checkMigration(projectDir, file.getKey(), file.getValue(), findings, target2);
		}

		checkProject(projectDir, java, conf, findings);

		for (Map.Entry<Path, String> file : conf.entrySet()) {
			checkConf(projectDir, file.getKey(), file.getValue(), findings);
		}

		checkSkills(rootDir, version, findings);

		return findings;

	}

	// region 2.0 への移行（要件 D-192）

	/*
	 * 規則：番号・形・読み方・直し方。J8xx（1.5 で置き換え先があったもの）と J9xx（2.0 で型や意味が変わったもの）。
	 * 1.5 の jimbleCheck は J9xx を --target=2.0 のときだけ出した（1.5 では正しい書き方でもあったので）。
	 * 2.0 の jimbleCheck はどちらもいつも出す（要件 D-198）。target2 は 1.5 との互換のために残してある。
	 * J809（validate の戻り値を捨てている）は 2.0 で外した——文として書く validate(...) が 2.0 の正しい書き方になった。
	 */
	private record MigrationRule (String id, Pattern pattern, boolean target2, String message, String fix) {}

	private static final String MIGRATE = DOCS + "migrate-2.md";

	/*
	 * 呼び出し元（db. / ctx.db(). / this.db. など）。識別子か空の () を . でつないだもので、末尾の . まで。
	 * 引数の中へは入らない——[\w.()]* だと db.insertKey(SQL.insert( の SQL.insert( に当たっていた。
	 */
	private static final String RECEIVER = "(?:\\w+(?:\\s*\\(\\s*\\))?\\s*\\.\\s*)+";

	private static final List<MigrationRule> MIGRATION_RULES = List.of(
		new MigrationRule("J801", Pattern.compile("\\bnew\\s+DBTransaction\\s*\\(|\\bDBTransaction\\s*\\.\\s*transaction\\s*\\("), false
			, "DBTransaction は 2.0 で消えた（1.5.0 で非推奨。検査例外を投げ、commit() が終わらなかった）"
			, "db.transaction(tx -> { ... }) か try (Tx tx = db.begin()) { ...; tx.commit(); } に書き換える（commitEndTransaction() は tx.commit()、commit() は tx.checkpoint()）"),
		new MigrationRule("J802", Pattern.compile("\\.\\s*(?:beginTransaction|commitEndTransaction|rollbackEndTransaction|endTransaction)\\s*\\(\\s*\\)"), false
			, "DB のトランザクション操作（beginTransaction など）は 2.0 で消えた（1.5.0 で非推奨）"
			, "try (Tx tx = db.begin()) { ...; tx.commit(); }（抜けたら巻き戻る）か db.transaction(tx -> { ... })"),
		new MigrationRule("J803", Pattern.compile("\\bRouter\\s+\\w+\\s*=\\s*" + RECEIVER + "path\\s*\\(\\s*\"[^\"]*\"\\s*\\)"), false
			, "Router.path(パス) は 2.0 で消えた（配下のルーターを返すだけで、受け取り忘れると親に登録していた）"
			, "router.path(\"/admin\", admin -> { admin.get(...); }) のブロックで書く"),
		new MigrationRule("J804", Pattern.compile("\\b[A-Z]\\w*\\.[a-z_]\\w*\\.\\s*subtract\\s*\\("), false
			, "列の subtract(...) は 2.0 で消えた（名前と違って割り算（/）を出していた）"
			, "割り算なら divide(...)、引き算なら minus(...)"),
		new MigrationRule("J805", Pattern.compile("\\bDsl\\s*\\.\\s*(?:or|and)\\s*\\("), false
			, "Dsl.or(...) / Dsl.and(...) は 2.0 で消えた（「直前とつなぐ印」で、引数を書き換えていた）"
			, "括弧でまとめる Dsl.anyOf(a, b, ...) / Dsl.allOf(a, b, ...)"),
		new MigrationRule("J806", Pattern.compile("\\bcookies\\s*\\(\\s*\\)\\s*\\.\\s*put\\s*\\(\\s*(?:new\\s+Cookie\\s*\\([^;]*\\)|[\\w.]+)\\s*\\)\\s*;"), false
			, "cookies().put(Cookie) は 2.0 で消えた（署名しなかったので、cookie.secret があると次のリクエストの get が \"\" になっていた）"
			, "署名するなら cookies().putSigned(cookie)、しないなら cookies().putUnsigned(cookie)"),
		new MigrationRule("J807", Pattern.compile("\\.\\s*(?:eq|not)\\s*\\(\\s*null\\s*\\)"), false
			, "eq(null) / not(null) は 2.0 で例外になった（「= NULL」はどの行にも当たらない）"
			, "is_null() / is_not_null() を使う"),
		new MigrationRule("J810", Pattern.compile("\\blong\\s+\\w+\\s*=\\s*" + RECEIVER + "insert\\s*\\("), false
			, "insert(...) は 2.0 で値を返さなくなった（1.x は「採番値か件数のどちらか」で、採番列の有無で決まっていた）"
			, "採番値が欲しいなら insertKey(...)（無ければ例外）、件数が欲しいなら execute(...)"),
		new MigrationRule("J901", Pattern.compile("\\bData\\s+\\w+\\s*=\\s*" + RECEIVER + "select(?:Cached)?\\s*\\("), true
			, "select(...) は 2.0 で Optional<Data> を返し、失敗は例外になった（この行はコンパイルが通らない）"
			, "db.select(...).orElseThrow(() -> new HttpException(404, ...)) か .orElse(null)（null は「0件」だけ）"),
		new MigrationRule("J902", Pattern.compile("(?:\\bif\\s*\\(\\s*!\\s*|\\bboolean\\s+\\w+\\s*=\\s*)" + RECEIVER + "execute\\s*\\("), true
			, "execute(...) は 2.0 で件数（int）を返し、失敗は例外になった（この行はコンパイルが通らない）"
			, "失敗は例外で受ける。db.transaction(...) の中なら何も書かなくてよい"),
		new MigrationRule("J903", Pattern.compile("\\.\\s*isError\\s*\\(\\s*\\)"), true
			, "isError() は 2.0 で無くなった（DB の失敗は SqlExecuteException）"
			, "db.transaction(...) の中なら確定しないことで守られる。分岐したいのは一意制約くらいなので DuplicateKeyException で受ける"),
		new MigrationRule("J904", Pattern.compile("(?:\\bif\\s*\\(\\s*!\\s*|\\bboolean\\s+\\w+\\s*=\\s*|assert\\w*\\s*\\(\\s*)(?:DBUtil\\s*\\.\\s*load|DBLock\\s*\\.\\s*(?:lock|create))\\s*\\("), true
			, "DBUtil.load / DBLock.lock / DBLock.create は 2.0 で値を返さず、失敗は例外になった"
			, "戻り値の分岐を消し、失敗は例外で受ける（DBLock.lock はトランザクションの中で呼ぶ）"),
		new MigrationRule("J905", Pattern.compile("\\bRedisLock\\s*\\.\\s*tryLock\\s*\\("), true
			, "RedisLock.tryLock(...) は 2.0 で Optional<RedisLockResult> を返す"
			, "取れたかは Optional で見る。lock(...) は取れなければ例外になる"),
		new MigrationRule("J906", Pattern.compile("\\.\\s*(?:selectOrThrow|selectListOrThrow|insertNoReturnKey)\\s*\\("), true
			, "selectOrThrow / selectListOrThrow / insertNoReturnKey は 2.0 で非推奨（select / selectList / insert が同じ意味になった）"
			, "select(...).orElse(null) / selectList(...) / insert(...)（件数が要るなら execute(...)）に置き換える"),
		new MigrationRule("J907", Pattern.compile("\\bsession\\s*\\(\\s*\\)\\s*\\.\\s*data\\s*\\(\\s*\\)\\s*\\.\\s*(?:put|putData|remove|clear|putAll)\\s*\\("), true
			, "session().data() は 2.0 で読み取り専用の写しになった（書き換えると例外。1.x は「変えた」印が付かず保存されなかった）"
			, "context.session().put(キー, 値) / remove(キー) で変える")
	);

	/*
	 * 戻り値が Optional になった呼び出し。2.0 の書き方（.orElse(...) などで受け取る）なら出さない
	 */
	private static final java.util.Set<String> OPTIONAL_RESULT = java.util.Set.of("J901", "J905");

	private static final Pattern OPTIONAL_USE = Pattern.compile(
		"\\s*\\.\\s*(?:orElse|orElseGet|orElseThrow|isPresent|isEmpty|ifPresent|ifPresentOrElse|map|flatMap|filter|get|stream|or)\\s*\\(");

	/**
	 * 呼び出しの閉じ括弧のあとで Optional として受け取っているか
	 *
	 * @param code	文字列の中身を空白にしたソース
	 * @param open	呼び出しの開き括弧の位置
	 * @return	受け取っていれば true
	 */
	static boolean takesOptional (String code, int open) {

		int depth = 0;
		for (int i = open; i < code.length(); i++) {
			char c = code.charAt(i);
			if (c == '(') {
				depth++;
			} else if (c == ')') {
				depth--;
				if (depth == 0) {
					return OPTIONAL_USE.matcher(code).region(i + 1, code.length()).lookingAt();
				}
			}
		}
		return false;

	}

	private static final Pattern OPTIONAL_ASSIGN = Pattern.compile("Optional\\s*<[^;=(){}]*>\\s*\\w+\\s*=\\s*$");

	/**
	 * {@code Optional<...> x = } に入れているか
	 *
	 * @param code	文字列の中身を空白にしたソース
	 * @param start	呼び出しの始まり
	 * @return	入れていれば true
	 */
	static boolean assignedToOptional (String code, int start) {

		int from = Math.max(0, code.lastIndexOf(';', start - 1) + 1);
		from = Math.max(from, code.lastIndexOf('{', start - 1) + 1);
		return OPTIONAL_ASSIGN.matcher(code.substring(from, start)).find();

	}

	/**
	 * 2.0 への移行で書き換える呼び出しを見る
	 *
	 * @param projectDir	プロジェクト
	 * @param path			ファイル
	 * @param raw			中身
	 * @param findings		見つけたもの
	 * @param target2		J9xx も出すか
	 */
	static void checkMigration (Path projectDir, Path path, String raw, List<Finding> findings, boolean target2) {

		String code = blank(raw);
		String[] rawLines = raw.split("\n", -1);
		String file = relative(projectDir, path);
		boolean usesDbTransaction = code.contains("DBTransaction");

		for (MigrationRule rule : MIGRATION_RULES) {
			if (rule.target2() && !target2) {
				continue;
			}
			// DBTransaction のファイルは J801 で出す（同じメソッド名が J802 にも当たる）
			if ("J802".equals(rule.id()) && usesDbTransaction) {
				continue;
			}
			// blank() は文字列の中身を空白にするが、引用符は残す——"/admin" は "      " として当たる
			Matcher m = rule.pattern().matcher(code);
			while (m.find()) {
				// 2.0 の正しい書き方（Optional を受け取っている）は出さない
				if (OPTIONAL_RESULT.contains(rule.id())
					&& (takesOptional(code, m.end() - 1) || assignedToOptional(code, m.start()))) {
					continue;
				}
				add(findings, rawLines, rule.id(), Level.WARN, file, lineOf(code, m.start()), rule.message(), rule.fix(), MIGRATE);
			}
		}

		// J808: 文字列 "now()"（コメントの中は除く）。2.0 はただの文字列として入る
		int from = 0;
		while (true) {
			int at = raw.indexOf("\"now()\"", from);
			if (at < 0) {
				break;
			}
			if (at < code.length() && code.charAt(at) == '"') {
				add(findings, rawLines, "J808", Level.WARN, file, lineOf(code, at)
					, "文字列 \"now()\" は 2.0 で set(Data) / value(Data) に入れてもただの文字列になった（1.x は SQL の NOW() に変えていた）"
					, "現在時刻は Dsl.now() を値に入れる。平らな行なら setRow(Data) / valueRow(Data)（変換しない）"
					, MIGRATE);
			}
			from = at + 1;
		}

	}

	// endregion

	// region Java（1ファイルで分かるもの）

	/**
	 * 1ファイルを見る
	 *
	 * @param projectDir	プロジェクト
	 * @param path			ファイル
	 * @param raw			中身
	 * @param findings		見つけたもの
	 */
	static void checkJava (Path projectDir, Path path, String raw, List<Finding> findings) {

		String code = blank(raw);
		String[] rawLines = raw.split("\n", -1);
		String file = relative(projectDir, path);

		// J101
		Matcher request = REQUEST_GET.matcher(code);
		while (request.find()) {
			add(findings, rawLines, "J101", Level.ERROR, file, lineOf(code, request.start())
				, "context.request().get〜(...) で読んでいる。2.0 の Request は Data を継承しないのでコンパイルが通らない（1.x はコンパイルが通って、いつも null / 空を返していた）"
				, "送られてきた値は context.request().bodyAll().getString(\"x\")（クエリだけなら bodyQuery()、JSON だけなら bodyJson()）"
				, DOCS + "request-response.md");
		}

		// J301: Migration.install() は DBUtil.load(...) より前
		int load = code.indexOf("DBUtil.load(");
		int migration = code.indexOf("Migration.install(");
		if (load >= 0 && migration > load) {
			add(findings, rawLines, "J301", Level.ERROR, file, lineOf(code, migration)
				, "Migration.install() が DBUtil.load(...) のあとにある。登録した処理はもう走り終わっているので、マイグレーションは黙って流れない"
				, "Migration.install() を DBUtil.load(...) の前に移す（流れるのは load の中）"
				, DOCS + "codegen.md");
		}

		// J303: BatchRegistry.sync(...) は登録が全部済んでから
		int sync = code.indexOf("BatchRegistry.sync(");
		int lastAdd = code.lastIndexOf("BatchRegistry.add(");
		if (sync >= 0 && lastAdd > sync) {
			add(findings, rawLines, "J303", Level.ERROR, file, lineOf(code, sync)
				, "BatchRegistry.sync(...) が BatchRegistry.add(...) より前にある。登録の無い状態で同期すると、全部のバッチが nothing になり、スケジューラが何も回さない"
				, "BatchRegistry.sync(...) を、add(...) を全部済ませたあとに移す"
				, DOCS + "batch.md");
		}

		// J401: 種別つきのブロックの restore より先に、アプリ全体の guard が走る
		if (code.contains("Auth.REALM")) {
			checkGuardOrder(code, rawLines, file, findings);
		}

		// J402: forgetAll だけでは、ほかの端末のセッションが残る（F-W-33）
		if (!code.contains("Auth.revoke")) {
			for (int at = code.indexOf("Remember.forgetAll("); at >= 0; at = code.indexOf("Remember.forgetAll(", at + 1)) {
				add(findings, rawLines, "J402", Level.WARN, file, lineOf(code, at)
					, "Remember.forgetAll(...) は remember-me の記憶を消すだけで、ほかの端末でいまログインしているセッションはそのまま入れる"
					, "パスワードを変えたあとなら Auth.revokeOthers(context)、管理画面から締め出すなら Auth.revoke(id)（どちらも中で forgetAll を呼ぶ）"
					, DOCS + "auth.md");
			}
		}

		// J701: トランザクションを使うファイルの空の catch
		if (code.contains("DBTransaction") || code.contains(".begin()") || code.contains(".transaction(")
			|| code.contains("TransactionException")) {
			Matcher empty = EMPTY_CATCH.matcher(code);
			while (empty.find()) {
				add(findings, rawLines, "J701", Level.WARN, file, lineOf(code, empty.start())
					, "空の catch がある。トランザクションの中で SQL の失敗を捨てても tx.commit() は DB_004 で断る。外で TransactionException を捨てると、保存できていないのに成功を返す"
					, "捨てずに投げ直す（非検査なので throws は要らない）。受け止めて続けたいなら、トランザクションの外で受けて新しい Tx でやり直す"
					, DOCS + "transaction.md");
			}
		}

	}

	/**
	 * guard と restore の深さを比べる
	 *
	 * <p>
	 * アプリ全体の {@code before} は、ブロックの {@code before} より先に走る。
	 * <b>浅いところに guard があり、深いところ（種別のブロック）に restore がある</b>と、
	 * 覚えていても restore が思い出す前に 401 になる。
	 * </p>
	 *
	 * @param code		コメントと文字列を潰したソース
	 * @param rawLines	元の行
	 * @param file		ファイル
	 * @param findings	見つけたもの
	 */
	private static void checkGuardOrder (String code, String[] rawLines, String file, List<Finding> findings) {

		int guardDepth = Integer.MAX_VALUE;
		int depth = 0;

		for (int i = 0; i < code.length(); i++) {

			char c = code.charAt(i);

			if (c == '{') {
				depth++;
			} else if (c == '}') {
				depth--;
			} else if (code.startsWith("before(Auth::guard)", i)) {
				guardDepth = Math.min(guardDepth, depth);
			} else if (code.startsWith("before(Remember.restore(", i) && depth > guardDepth) {
				add(findings, rawLines, "J401", Level.ERROR, file, lineOf(code, i)
					, "ブロックの中の Remember.restore より先に、外側の before(Auth::guard) が走る。覚えていても毎回 401 になる"
					, "種別（Auth.REALM）を付けたブロックでは、restore と guard の両方をブロックの中に置く（外側の guard は種別なしのブロックへ移す）"
					, DOCS + "auth.md");
			}

		}

	}

	// endregion

	// region プロジェクト（ファイルをまたぐもの）

	/**
	 * プロジェクトをまたいで見る
	 *
	 * @param projectDir	プロジェクト
	 * @param java			Java
	 * @param conf			設定
	 * @param findings		見つけたもの
	 */
	static void checkProject (Path projectDir, Map<Path, String> java, Map<Path, String> conf, List<Finding> findings) {

		Map<Path, String> code = new LinkedHashMap<>();
		java.forEach((path, raw) -> code.put(path, blank(raw)));

		// J302: バッチのテーブルを作っていない
		Path syncFile = firstContaining(code, "BatchRegistry.sync(");
		if (syncFile != null && firstContaining(code, "BatchTables.install(") == null) {
			String text = code.get(syncFile);
			add(findings, java.get(syncFile).split("\n", -1), "J302", Level.ERROR, relative(projectDir, syncFile)
				, lineOf(text, text.indexOf("BatchRegistry.sync("))
				, "BatchTables.install(db) をどこでも呼んでいない。バッチのテーブルはマイグレーションでは作らないので無いままになり、sync() はエラーログを出すだけで落ちない"
				, "DBUtil.load(...) のあと、BatchRegistry.sync(...) の前に BatchTables.install(DBUtil.getMainDB()) を呼ぶ"
				, DOCS + "batch.md");
		}

		// J304: MQ の表を codegen から外していない
		String allConf = String.join("\n", conf.values());
		boolean usesCodegen = allConf.contains("codegen") || Files.isDirectory(projectDir.resolve("src/main/java/db"));
		if (usesCodegen) {

			List<String> tables = new ArrayList<>();

			if (firstContaining(code, "DbScheduler") != null) {
				tables.add("mq_scheduler");
			}

			Pattern queue = Pattern.compile("queueName\\s*\\(\\s*\\)\\s*\\{\\s*return\\s*\"(mq_[A-Za-z0-9_]+)\"");
			for (String raw : java.values()) {
				Matcher matcher = queue.matcher(raw);
				while (matcher.find()) {
					tables.add(matcher.group(1));
				}
			}

			List<String> excluded = new ArrayList<>();
			Matcher list = Pattern.compile("exclude_tables\\s*[=:]\\s*\\[([^\\]]*)\\]").matcher(allConf);
			while (list.find()) {
				Matcher name = Pattern.compile("\"([^\"]+)\"").matcher(list.group(1));
				while (name.find()) {
					excluded.add(name.group(1).toLowerCase());
				}
			}

			for (String table : tables.stream().distinct().toList()) {
				if (!excluded.contains(table.toLowerCase())) {
					findings.add(new Finding("J304", Level.WARN, "", 0
						, "MQ の表 %s を codegen が外さない（名前をアプリが決めるので、jimble の管理テーブルに入っていない）".formatted(table)
						, "conf/application.conf の codegen.exclude_tables に \"%s\" を足す（ワイルドカードは使えない）".formatted(table)
						, DOCS + "codegen.md"));
				}
			}

		}

	}

	// endregion

	// region 設定

	/**
	 * 設定ファイルを見る
	 *
	 * @param projectDir	プロジェクト
	 * @param path			ファイル
	 * @param raw			中身
	 * @param findings		見つけたもの
	 */
	static void checkConf (Path projectDir, Path path, String raw, List<Finding> findings) {

		String file = relative(projectDir, path);
		String[] lines = raw.split("\n", -1);

		Deque<String> blocks = new ArrayDeque<>();
		Map<String, Integer> envOnly = new HashMap<>();

		for (int i = 0; i < lines.length; i++) {

			String line = stripConfComment(lines[i]);
			int lineNo = i + 1;

			// J201
			Matcher env = ENV_WITHOUT_Q.matcher(line);
			while (env.find()) {
				add(findings, lines, "J201", Level.WARN, file, lineNo
					, "${%s} に ? が無い。環境変数が無い環境（手元・テスト）では起動時に落ちる".formatted(env.group(1))
					, "既定の行を先に書き、あとに ${?%s} の行を置く（2行組）".formatted(env.group(1))
					, DOCS + "config.md");
			}

			Matcher block = CONF_BLOCK.matcher(line);
			if (block.matches()) {
				blocks.push(unquote(block.group(1)));
				continue;
			}

			if (line.trim().equals("}")) {
				if (!blocks.isEmpty()) {
					blocks.pop();
				}
				continue;
			}

			Matcher assign = CONF_ASSIGN.matcher(line);
			if (!assign.matches() || assign.group(3).trim().equals("{")) {
				if (assign.matches()) {
					blocks.push(unquote(assign.group(1)));
				}
				continue;
			}

			String key = fullKey(blocks, unquote(assign.group(1)));
			String value = assign.group(3).trim();

			if (value.startsWith("${?")) {
				envOnly.put(key, lineNo);
			} else if (envOnly.containsKey(key)) {
				// J202: ${?X} のあとに値を書き直している
				add(findings, lines, "J202", Level.ERROR, file, lineNo
					, "%s が ${?...} の行（%d 行目）のあとで書き直されている。あとの行が必ず勝つので、環境変数が効かない".formatted(key, envOnly.get(key))
					, "既定の行を先に、${?...} の行をあとに置く"
					, DOCS + "config.md");
				envOnly.remove(key);
			}

		}

	}

	/**
	 * ブロックを含めたキー
	 *
	 * @param blocks	開いているブロック（内側が先頭）
	 * @param key		キー
	 * @return	キー
	 */
	private static String fullKey (Deque<String> blocks, String key) {

		List<String> parts = new ArrayList<>(blocks);
		java.util.Collections.reverse(parts);
		parts.add(key);

		return String.join(".", parts);

	}

	/**
	 * 設定のコメントを落とす（# と //。文字列の中は見ない）
	 *
	 * @param line	行
	 * @return	行
	 */
	private static String stripConfComment (String line) {

		boolean quoted = false;

		for (int i = 0; i < line.length(); i++) {

			char c = line.charAt(i);

			if (c == '"') {
				quoted = !quoted;
			} else if (!quoted && (c == '#' || (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/'))) {
				return line.substring(0, i);
			}

		}

		return line;

	}

	/**
	 * 引用符を外す
	 *
	 * @param key	キー
	 * @return	キー
	 */
	private static String unquote (String key) {

		return key.replace("\"", "");

	}

	// endregion

	// region skill

	/**
	 * skill が使っている版のものか
	 *
	 * @param rootDir	プロジェクトの根
	 * @param version	使っている jimble の版
	 * @param findings	見つけたもの
	 */
	static void checkSkills (Path rootDir, String version, List<Finding> findings) {

		if (!Files.isDirectory(rootDir.resolve(".claude/skills/jimble"))) {
			return;
		}

		String recorded = SkillsInstaller.recordedVersion(rootDir);

		if (!recorded.equals(version)) {
			findings.add(new Finding("J501", Level.WARN, ".claude/skills/", 0
				, recorded.isEmpty()
					? "jimble の skill がどの版のものか分からない（控えを書くようになる前に置いたもの）。使っている jimble は %s".formatted(version)
					: "jimble の skill が %s のもの。使っている jimble は %s".formatted(recorded, version)
				, "./gradlew jimbleSkills で揃える（手で直した skill は上書きしない）"
				, DOCS + "gradle.md"));
		}

	}

	// endregion

	// region 下回り

	/**
	 * 見つけたものを足す（抑止の印があれば足さない）
	 *
	 * @param findings	見つけたもの
	 * @param rawLines	元の行
	 * @param rule		規則
	 * @param level		重さ
	 * @param file		ファイル
	 * @param line		行
	 * @param message	何が起きるか
	 * @param fix		直し方
	 * @param url		引き先
	 */
	private static void add (List<Finding> findings, String[] rawLines, String rule, Level level, String file, int line
		, String message, String fix, String url) {

		if (ignored(rawLines, line, rule)) {
			return;
		}

		findings.add(new Finding(rule, level, file, line, message, fix, url));

	}

	/**
	 * 抑止の印があるか（その行か、前の行）
	 *
	 * @param rawLines	元の行
	 * @param line		行（1 始まり）
	 * @param rule		規則
	 * @return	ある場合 = true
	 */
	static boolean ignored (String[] rawLines, int line, String rule) {

		for (int i = line - 2; i <= line - 1; i++) {
			if (i >= 0 && i < rawLines.length) {
				int at = rawLines[i].indexOf(IGNORE);
				if (at >= 0 && rawLines[i].substring(at).contains(rule)) {
					return true;
				}
			}
		}

		return false;

	}

	/**
	 * コメントと文字列を空白に潰す（改行は残す）
	 *
	 * @param source	ソース
	 * @return	潰したもの
	 */
	static String blank (String source) {

		StringBuilder out = new StringBuilder(source.length());
		int i = 0;

		while (i < source.length()) {

			char c = source.charAt(i);

			if (source.startsWith("//", i)) {
				while (i < source.length() && source.charAt(i) != '\n') {
					out.append(' ');
					i++;
				}
			} else if (source.startsWith("/*", i)) {
				int end = source.indexOf("*/", i + 2);
				end = end < 0 ? source.length() : end + 2;
				blankRange(source, i, end, out);
				i = end;
			} else if (source.startsWith("\"\"\"", i)) {
				int end = source.indexOf("\"\"\"", i + 3);
				end = end < 0 ? source.length() : end + 3;
				out.append('"');
				blankRange(source, i + 1, end - 1, out);
				out.append('"');
				i = end;
			} else if (c == '"' || c == '\'') {
				int end = i + 1;
				while (end < source.length() && source.charAt(end) != c && source.charAt(end) != '\n') {
					end += source.charAt(end) == '\\' ? 2 : 1;
				}
				end = Math.min(end + 1, source.length());
				out.append(c);
				blankRange(source, i + 1, end - 1, out);
				out.append(c);
				i = end;
			} else {
				out.append(c);
				i++;
			}

		}

		return out.toString();

	}

	/**
	 * 範囲を空白にする（改行は残す）
	 */
	private static void blankRange (String source, int from, int to, StringBuilder out) {

		for (int j = from; j < to && j < source.length(); j++) {
			out.append(source.charAt(j) == '\n' ? '\n' : ' ');
		}

	}

	/**
	 * 位置の行（1 始まり）
	 */
	private static int lineOf (String text, int index) {

		int line = 1;

		for (int i = 0; i < index && i < text.length(); i++) {
			if (text.charAt(i) == '\n') {
				line++;
			}
		}

		return line;

	}

	/**
	 * 含んでいる最初のファイル
	 */
	private static Path firstContaining (Map<Path, String> files, String text) {

		for (Map.Entry<Path, String> file : files.entrySet()) {
			if (file.getValue().contains(text)) {
				return file.getKey();
			}
		}

		return null;

	}

	/**
	 * 相対パス（区切りは /）
	 */
	private static String relative (Path base, Path path) {

		return base.relativize(path).toString().replace('\\', '/');

	}

	/**
	 * ディレクトリの下のファイルを読む（無ければ空）
	 */
	private static Map<Path, String> read (Path dir, String suffix) {

		Map<Path, String> files = new LinkedHashMap<>();

		if (!Files.isDirectory(dir)) {
			return files;
		}

		try (Stream<Path> paths = Files.walk(dir)) {

			for (Path path : paths.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(suffix)).sorted().toList()) {
				files.put(path, Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n"));
			}

		} catch (IOException ex) {
			throw new UncheckedIOException("読めませんでした: " + dir, ex);
		}

		return files;

	}

	// endregion

}
