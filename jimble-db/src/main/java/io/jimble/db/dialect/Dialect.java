package io.jimble.db.dialect;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

/**
 * DB 製品ごとの書き方（要件 F-D-30）
 *
 * <p>
 * <b>SQL ビルダーは製品を知らない。</b>
 * 製品ごとに違うところだけをここに集め、
 * {@link io.jimble.db.DB} が<b>接続先の方言を渡して</b>組み立てさせる。
 * </p>
 *
 * <pre>
 * db {
 *   blog {
 *     product = "postgresql"   # mysql（既定） | postgresql
 *   }
 * }
 * </pre>
 *
 * <p>
 * <b>アプリのコードは変わらない。</b>
 * {@code db.select(SQL.select().from(...))} と書いたものが、
 * 接続先に合わせた SQL になる。
 * </p>
 *
 * <h2>実装は枠組みの中の2つだけである（要件 D-157）</h2>
 * <p>
 * <b>{@code sealed} にしてある。</b>{@link Dialects#of(String)} は
 * <b>べた書きの表で製品を選ぶ</b>ので、そもそも自前の方言を設定から選ぶ道は無い——
 * <b>開いている顔をして閉じていた</b>のを、見たとおりにしただけである。
 * </p>
 *
 * <p>
 * <b>閉じておく理由は、こちらが動けるようにしておくためでもある。</b>
 * このインタフェースには抽象メソッドが 40 以上あり、
 * <b>SQL 関数を1つ足すたびに、外の実装は壊れる</b>。
 * 閉じていれば、足すのは枠組みの中の話で済む。
 * </p>
 *
 * <p>
 * <b>あとから開くことはできる</b>（{@code sealed} を外すのは互換を壊さない）。
 * <b>あとから閉じることはできない。</b>——だから先に閉じてある。
 * 対応してほしい製品があれば、<b>枠組みに足す</b>形で受け付ける。
 * </p>
 *
 * <h2>書けないものは投げる</h2>
 * <p>
 * その製品に対応する語彙が無いもの（MySQL の {@code MATCH ... AGAINST} など）は
 * <b>組み立てた時点で {@link DialectException}</b> にする。
 * 壊れた SQL を DB に投げると、返ってくるのは製品の構文エラーで、
 * <b>どのビルダーが原因かが分からない</b>。
 * </p>
 */
public sealed interface Dialect permits MySqlDialect, PostgreSqlDialect {

	/**
	 * 製品名（設定に書く値）
	 *
	 * @return	製品名
	 */
	String name();

	// region 識別子

	/**
	 * 識別子を囲む
	 *
	 * @param sb	出力先
	 * @param name	名前
	 */
	void identifier (StringBuilder sb, String name);

	// endregion

	// region 関数

	/**
	 * 関数名
	 *
	 * @param function	関数
	 * @return	その製品での名前
	 * @throws DialectException	その製品に無い場合
	 */
	String function (SqlFunction function);

	/**
	 * 文字列の連結（要件 F-D-30）
	 *
	 * <p>
	 * <b>同じ {@code CONCAT} でも NULL の扱いが逆。</b>
	 * MySQL の {@code CONCAT} は引数に1つでも NULL があれば NULL を返すが、
	 * PostgreSQL の {@code concat} は NULL を空文字として無視する。
	 * 名前を置き換えるだけだと<b>「動くけれど結果が違う」</b>になるので、
	 * PostgreSQL では {@code ||} に置き換える。
	 * </p>
	 *
	 * @param sb		出力先
	 * @param values	引数の出力（1つずつ）
	 */
	void concat (StringBuilder sb, List<Runnable> values);

	/**
	 * 日付の書式（要件 F-D-30）
	 *
	 * <p>
	 * <b>MySQL の {@code DATE_FORMAT} と PostgreSQL の {@code to_char} は
	 * 書式の言語が別物。</b>{@code %Y-%m-%d} をそのまま {@code to_char} に渡すと、
	 * 例外にならずに<b>まったく違う文字列</b>が返る。
	 * 書き換えられない製品では投げる。
	 * </p>
	 *
	 * @param sb		出力先
	 * @param value		値の出力
	 * @param format	MySQL の書式
	 * @throws DialectException	その製品で書けない場合
	 */
	void dateFormat (StringBuilder sb, Runnable value, String format);

	/**
	 * 日時から一部を取り出す（要件 F-D-31）
	 *
	 * <p>
	 * MySQL は {@code YEAR(x)}、PostgreSQL は {@code EXTRACT(YEAR FROM x)}。
	 * <b>返る値まで揃える</b>（曜日の起点など）。
	 * </p>
	 *
	 * @param sb	出力先
	 * @param part	取り出す部分
	 * @param value	値の出力
	 */
	void datePart (StringBuilder sb, DatePart part, Runnable value);

	/**
	 * 日時を足す・引く（要件 F-D-31）
	 *
	 * <p>
	 * {@code ?} が1つ出る（足す数）。
	 * MySQL は {@code DATE_ADD(x, INTERVAL ? DAY)}、
	 * PostgreSQL は {@code (x + (? * INTERVAL '1 DAY'))}。
	 * </p>
	 *
	 * @param sb		出力先
	 * @param value		値の出力
	 * @param unit		単位
	 * @param subtract	引くなら true
	 */
	void dateAdd (StringBuilder sb, Runnable value, DateUnit unit, boolean subtract);

	/**
	 * 日付の差（日数。要件 F-D-31）
	 *
	 * <p>
	 * {@code a - b} の日数。MySQL は {@code DATEDIFF(a, b)}、
	 * PostgreSQL は {@code (a::date - b::date)}。
	 * </p>
	 *
	 * @param sb	出力先
	 * @param from	引かれるほうの出力
	 * @param to	引くほうの出力
	 */
	void dateDiffDays (StringBuilder sb, Runnable from, Runnable to);

	/**
	 * 日時の差（秒。要件 F-D-31）
	 *
	 * <p>{@code a - b} の秒数。</p>
	 *
	 * @param sb	出力先
	 * @param from	引かれるほうの出力
	 * @param to	引くほうの出力
	 */
	void dateDiffSeconds (StringBuilder sb, Runnable from, Runnable to);

	/**
	 * 日時から日付だけを取り出す（要件 F-D-31）
	 *
	 * @param sb	出力先
	 * @param value	値の出力
	 */
	void toDate (StringBuilder sb, Runnable value);

	/**
	 * 今日の日付
	 *
	 * @return	SQL の式
	 */
	String currentDate ();

	/**
	 * いまの時刻
	 *
	 * @return	SQL の式
	 */
	String currentTime ();

	/**
	 * 日時を epoch 秒にする（要件 F-D-31）
	 *
	 * @param sb	出力先
	 * @param value	値の出力
	 */
	void unixTimestamp (StringBuilder sb, Runnable value);

	/**
	 * epoch 秒を日時にする（要件 F-D-31）
	 *
	 * @param sb	出力先
	 * @param value	値の出力
	 */
	void fromUnixTime (StringBuilder sb, Runnable value);

	/**
	 * 部分文字列の位置（1から。無ければ 0。要件 F-D-31）
	 *
	 * <p>
	 * <b>引数の並びが逆。</b>MySQL は {@code LOCATE(探すもの, 対象)}、
	 * PostgreSQL は {@code STRPOS(対象, 探すもの)}。
	 * </p>
	 *
	 * @param sb		出力先
	 * @param needle	探すものの出力
	 * @param haystack	対象の出力
	 */
	void locate (StringBuilder sb, Runnable needle, Runnable haystack);

	/**
	 * 条件で分ける（要件 F-D-31）
	 *
	 * <p>
	 * MySQL は {@code IF(c, a, b)}、PostgreSQL に {@code IF} は無いので
	 * {@code CASE WHEN c THEN a ELSE b END}。
	 * </p>
	 *
	 * @param sb		出力先
	 * @param condition	条件の出力
	 * @param whenTrue	真のときの出力
	 * @param whenFalse	偽のときの出力
	 */
	void ifThenElse (StringBuilder sb, Runnable condition, Runnable whenTrue, Runnable whenFalse);

	/**
	 * 型変換（要件 F-D-31）
	 *
	 * @param sb		出力先
	 * @param value		値の出力
	 * @param type		行き先の型
	 * @param precision	{@link CastType#DECIMAL} の全体桁。ほかでは無視する
	 * @param scale		{@link CastType#DECIMAL} の小数桁。ほかでは無視する
	 */
	void cast (StringBuilder sb, Runnable value, CastType type, int precision, int scale);

	/**
	 * 集めて1つの文字列にする（要件 F-D-31）
	 *
	 * <p>
	 * <b>区切り文字の書き方がまるで違う。</b>
	 * MySQL は {@code GROUP_CONCAT(x SEPARATOR ',')}、
	 * PostgreSQL は {@code STRING_AGG(x::text, ',')}。
	 * PostgreSQL は文字列でないと受け取らないので、こちらで変換する。
	 * </p>
	 *
	 * @param sb		出力先
	 * @param value		値の出力
	 * @param separator	区切り文字
	 * @param distinct	重複を除くなら true
	 */
	void groupConcat (StringBuilder sb, Runnable value, String separator, boolean distinct);

	/**
	 * 正規表現に当たるか（要件 F-D-31）
	 *
	 * <p>
	 * {@code ?} が1つ出る（パターン）。
	 * MySQL は {@code x REGEXP ?}、PostgreSQL は {@code x ~ ?}。
	 * </p>
	 *
	 * <p>
	 * <b>正規表現の方言までは揃わない。</b>MySQL 8 は ICU、PostgreSQL は POSIX で、
	 * {@code ^} {@code $} {@code []} {@code +} のような素直な書き方は同じだが、
	 * {@code \d} のような略記は<b>片方でしか効かない</b>ことがある。
	 * </p>
	 *
	 * @param sb		出力先
	 * @param value		値の出力
	 * @param ignoreCase	大文字小文字を無視するなら true
	 */
	void regexp (StringBuilder sb, Runnable value, boolean ignoreCase);

	/**
	 * 桁を指定した四捨五入
	 *
	 * <p>
	 * PostgreSQL の {@code round(x, n)} は {@code numeric} にしか無い。
	 * {@code double precision} を渡すと関数が見つからない。
	 * </p>
	 *
	 * @param sb		出力先
	 * @param value		値の出力
	 * @param digits	桁
	 */
	void round (StringBuilder sb, Runnable value, int digits);

	/**
	 * 桁を落とす
	 *
	 * @param sb		出力先
	 * @param value		値の出力
	 * @param digits	桁
	 */
	void truncate (StringBuilder sb, Runnable value, int digits);

	/**
	 * JSON の値を取り出す
	 *
	 * @param sb		出力先
	 * @param value		値の出力
	 * @param path		パス
	 * @param unquote	引用符を外すか
	 */
	void jsonExtract (StringBuilder sb, Runnable value, String path, boolean unquote);

	/**
	 * いまから前後にずらした日時
	 *
	 * @param sb	出力先
	 * @param unit	単位（{@code SECOND} / {@code MINUTE} / {@code HOUR} / {@code DAY} / {@code WEEK} / {@code MONTH} / {@code YEAR}）
	 * @param ago	前にずらすなら true
	 */
	void intervalFromNow (StringBuilder sb, String unit, boolean ago);

	/**
	 * 全文検索
	 *
	 * @param sb		出力先
	 * @param columns	対象の列の出力
	 * @param modifier	検索の種類（MySQL の {@code IN BOOLEAN MODE} など）
	 * @throws DialectException	その製品に無い場合
	 */
	void fullTextMatch (StringBuilder sb, Runnable columns, String modifier);

	// endregion

	// region INSERT

	/**
	 * 重複を無視する INSERT の前置き
	 *
	 * @return	前置き（MySQL は {@code IGNORE }、PostgreSQL は空）
	 */
	String insertIgnorePrefix ();

	/**
	 * 重複を無視する INSERT の後置き
	 *
	 * @return	後置き（PostgreSQL は {@code ON CONFLICT DO NOTHING}、MySQL は空）
	 */
	String insertIgnoreSuffix ();

	/**
	 * 重複したら更新する句の始まり
	 *
	 * <p>
	 * PostgreSQL は<b>どのキーで重複を見るかを書く必要がある</b>
	 * （{@code ON CONFLICT (id) DO UPDATE SET}）。
	 * </p>
	 *
	 * @param keyColumns	重複を見る列。空なら主キーが分からないということ
	 * @return	句
	 * @throws DialectException	キーが要るのに分からない場合
	 */
	String onDuplicateKeyUpdate (List<String> keyColumns);

	/**
	 * 入れようとした値を指す書き方
	 *
	 * <p>MySQL は {@code VALUES(col)}、PostgreSQL は {@code EXCLUDED.col}。</p>
	 *
	 * @param sb		出力先
	 * @param table		テーブル名
	 * @param column	列名
	 */
	void insertedValue (StringBuilder sb, String table, String column);

	// endregion

	// region 実行

	/**
	 * 真偽値をバインドする
	 *
	 * <p>
	 * MySQL は {@code tinyint(1)} なので 1 / 0 を渡す。
	 * PostgreSQL は本物の {@code boolean} なので、1 を渡すと型が合わない。
	 * </p>
	 *
	 * @param statement	ステートメント
	 * @param index		位置
	 * @param value		値
	 * @throws Exception	バインドできなかった場合
	 */
	void bindBoolean (PreparedStatement statement, int index, boolean value) throws Exception;

	/**
	 * JSON をバインドする
	 *
	 * @param statement	ステートメント
	 * @param index		位置
	 * @param json		JSON 文字列
	 * @throws Exception	バインドできなかった場合
	 */
	void bindJson (PreparedStatement statement, int index, String json) throws Exception;

	/**
	 * 自動採番された値を取り出す
	 *
	 * <p>
	 * MySQL は採番した列だけを返すが、<b>PostgreSQL は行を丸ごと返す</b>ので、
	 * 1列目が採番列とは限らない。
	 * </p>
	 *
	 * @param resultSet	結果
	 * @return	値
	 * @throws Exception	取り出せなかった場合
	 */
	long generatedKey (ResultSet resultSet) throws Exception;

	/**
	 * 版を確かめる SQL
	 *
	 * @return	SQL
	 */
	String versionSql ();

	/**
	 * データベースが無いというエラーか
	 *
	 * <p>{@code create_database_sql} を実行するかどうかの判定に使う。</p>
	 *
	 * @param message	エラーメッセージ
	 * @return	そうなら true
	 */
	boolean isUnknownDatabase (String message);

	/**
	 * SQL の字面の決まり（要件 F-G-05）
	 *
	 * <p>
	 * どこからどこまでが文字列やコメントなのかは製品で違う。
	 * マイグレーションの SQL を「;」で切り分けるときに要る。
	 * </p>
	 *
	 * @return	決まり
	 */
	SqlSyntax sqlSyntax ();

	/**
	 * この型名は JSON か
	 *
	 * @param typeName	{@code ResultSetMetaData#getColumnTypeName}
	 * @return	JSON なら true
	 */
	boolean isJsonType (String typeName);

	/**
	 * この型名は地理空間か
	 *
	 * <p>
	 * MySQL は geometry を VARBINARY として返すので型名で見分ける。
	 * <b>これを見ずに VARBINARY を全部 geometry 扱いすると、
	 * PostgreSQL の {@code bytea} が壊れる。</b>
	 * </p>
	 *
	 * @param typeName	型名
	 * @return	地理空間なら true
	 */
	boolean isGeometryType (String typeName);

	/**
	 * 地理空間の列をテキスト表現で読む（要件 F-D-30）
	 *
	 * <p>
	 * <b>返ってくる形が製品でまったく違う。</b>
	 * MySQL は「先頭4バイトが SRID」の生バイト列、
	 * PostGIS は16進の EWKB 文字列。
	 * 片方のつもりで読むと例外になり、<b>黙って null になる</b>。
	 * </p>
	 *
	 * @param resultSet	結果
	 * @param index		列番号（1から）
	 * @return	WKT。読めなければ null
	 * @throws Exception	取り出せなかった場合
	 */
	String geometryText (ResultSet resultSet, int index) throws Exception;

	// endregion

	// region テーブルの版（要件 F-D-19b）

	/**
	 * テーブル名とコメントを引く SQL
	 *
	 * <p>返す列は {@code table_name} と {@code table_comment}。</p>
	 *
	 * @return	SQL
	 */
	String tableCommentsSql ();

	/**
	 * テーブルにコメントを付ける SQL
	 *
	 * @param table		テーブル名
	 * @param comment	コメント
	 * @return	SQL
	 */
	String setTableCommentSql (String table, String comment);

	/**
	 * ロック待ちの上限をこのセッションにだけ設定する SQL（要件 F-G-19）
	 *
	 * <p>
	 * MySQL は {@code SET SESSION innodb_lock_wait_timeout}、
	 * PostgreSQL は {@code SET lock_timeout}（ミリ秒）。
	 * </p>
	 *
	 * @param seconds	秒
	 * @return	SQL
	 */
	String setLockTimeoutSql (int seconds);

	/**
	 * データベースを作るときに繋ぎ先にする URL（要件 F-D-30）
	 *
	 * <p>
	 * <b>まだ無いデータベースには繋げない</b>ので、別のところに繋いで
	 * {@code create_database_sql} を実行する。
	 * MySQL はスキーマ名を落とすだけでよいが、
	 * <b>PostgreSQL は必ずどれかのデータベースに繋ぐ必要がある</b>ので
	 * {@code postgres} に差し替える。
	 * </p>
	 *
	 * @param url	元の URL
	 * @return	繋ぎ先の URL
	 */
	String maintenanceUrl (String url);

	/**
	 * 「いまから ? 単位ぶん前／後」の式（生 SQL 用。要件 F-D-30）
	 *
	 * <p>
	 * {@code ?} は1つ。秒数・分数をパラメータで渡す。
	 * MySQL は {@code CURRENT_TIMESTAMP + INTERVAL - ? SECOND}、
	 * PostgreSQL は {@code CURRENT_TIMESTAMP - (? * INTERVAL '1 second')}。
	 * </p>
	 *
	 * @param unit	単位（{@code SECOND} / {@code MINUTE} など）
	 * @param ago	前なら true
	 * @return	SQL の式
	 */
	default String intervalFromNow (String unit, boolean ago) {

		StringBuilder sb = new StringBuilder();
		intervalFromNow(sb, unit, ago);

		return sb.toString();

	}

	/**
	 * 関数の呼び出し（引数なし。生 SQL 用）
	 *
	 * @param function	関数
	 * @return	{@code RAND()} など
	 */
	default String call (SqlFunction function) {

		return function(function) + "()";

	}

	/**
	 * 識別子を囲む（生 SQL 用）
	 *
	 * @param name	名前
	 * @return	囲んだ名前
	 */
	default String identifier (String name) {

		StringBuilder sb = new StringBuilder();
		identifier(sb, name);

		return sb.toString();

	}

	// endregion

	/**
	 * その製品では書けないと伝える
	 *
	 * @param feature	書けないもの
	 * @return	例外
	 */
	default DialectException unsupported (String feature) {

		return new DialectException("%s は %s では使えません".formatted(feature, name()));

	}

}
