package io.jimble.db.dialect;

/**
 * SQL の字面の決まり（要件 F-D-30 / F-G-05）
 *
 * <p>
 * <b>SQL を「読む」ときだけに要るもの</b>を集めてある。
 * マイグレーションの SQL を「;」で1文ずつに切り分けるとき
 * （{@code MigrationSql#split}）、どこからどこまでが文字列やコメントなのかが
 * <b>製品で違う</b>ので、それを外から見えるようにしたものである。
 * </p>
 *
 * <p>
 * 決まりを間違えると、切り分けの結果が黙って変わる。たとえば
 * {@code insert into t values ('c:\');} は、バックスラッシュをエスケープとして読むと
 * <b>そこで文字列が終わらないことになり、後ろの SQL が全部1文につながる</b>。
 * </p>
 *
 * <p>
 * <b>record ではない（D-173）。</b>record だったときは
 * <b>7 個の位置引数が全部 boolean</b> だったので、順番を間違えても型で気づけなかった——
 * 実際、<b>Javadoc の {@code @param} の並びが実物とズレていた</b>
 * （{@code dashCommentNeedsSpace} と {@code backtickQuote} が入れ替わっていた）。
 * いまは名前で1つずつ立てる（{@link Builder}）ので、並べ替えても意味が変わらない。
 * 8 個目を足すのも、正準コンストラクタが無いので<b>あとからできる</b>。
 * </p>
 *
 * <p>
 * <b>作れるのはこのパッケージの中だけである。</b>{@link Dialect} は {@code sealed} なので
 * （D-157）、アプリが自前の製品を足すことはない。
 * </p>
 */
public final class SqlSyntax {

	/** 文字列リテラルの中でバックスラッシュがエスケープになるか */
	private final boolean backslashEscape;

	/** {@code #} から行末までがコメントになるか */
	private final boolean hashComment;

	/** {@code --} のあとに空白が要るか */
	private final boolean dashCommentNeedsSpace;

	/** {@code `} で識別子を囲めるか */
	private final boolean backtickQuote;

	/** <!-- -->{@code /*!...} が実行されるコメントになるか */
	private final boolean executableComment;

	/** {@code $tag$ ... $tag$} が文字列になるか */
	private final boolean dollarQuote;

	/** ブロックコメントを入れ子にできるか */
	private final boolean nestedBlockComment;

	/**
	 * @param builder	組み立て中のもの
	 */
	private SqlSyntax (Builder builder) {

		this.backslashEscape = builder.backslashEscape;
		this.hashComment = builder.hashComment;
		this.dashCommentNeedsSpace = builder.dashCommentNeedsSpace;
		this.backtickQuote = builder.backtickQuote;
		this.executableComment = builder.executableComment;
		this.dollarQuote = builder.dollarQuote;
		this.nestedBlockComment = builder.nestedBlockComment;

	}

	/**
	 * 文字列リテラルの中でバックスラッシュがエスケープになるか
	 *
	 * <p>
	 * MySQL は なる。<b>PostgreSQL は既定で ならない</b>
	 * （{@code standard_conforming_strings = on}。ただし {@code E'...'} と書いたときだけ なる）。
	 * </p>
	 *
	 * @return	なるなら true
	 */
	public boolean backslashEscape () {

		return backslashEscape;

	}

	/**
	 * {@code #} から行末までがコメントになるか（MySQL だけ）
	 *
	 * @return	なるなら true
	 */
	public boolean hashComment () {

		return hashComment;

	}

	/**
	 * {@code --} のあとに空白が要るか
	 *
	 * <p>
	 * <b>MySQL は要る</b>（{@code 1--2} は「1 引く マイナス2」であってコメントではない）。
	 * PostgreSQL は要らない。
	 * </p>
	 *
	 * @return	要るなら true
	 */
	public boolean dashCommentNeedsSpace () {

		return dashCommentNeedsSpace;

	}

	/**
	 * {@code `} で識別子を囲めるか（MySQL だけ）
	 *
	 * <p>
	 * <b>PostgreSQL でこれを囲みとして読むと、書き間違えたバックティック1つで
	 * 後ろの SQL が全部くっつく。</b>
	 * </p>
	 *
	 * @return	囲めるなら true
	 */
	public boolean backtickQuote () {

		return backtickQuote;

	}

	/**
	 * <!-- -->{@code /*!...} が<b>実行されるコメント</b>になるか
	 *
	 * <p>
	 * MySQL / MariaDB だけ（{@code /*!40101 ...} / {@code /*M!100301 ...}）。
	 * コメントに見えて中身が動くので、捨ててはいけない。
	 * </p>
	 *
	 * @return	なるなら true
	 */
	public boolean executableComment () {

		return executableComment;

	}

	/**
	 * {@code $tag$ ... $tag$} が文字列になるか（PostgreSQL だけ）
	 *
	 * <p>関数の本体や {@code DO} ブロックがこの形で書かれる。</p>
	 *
	 * @return	なるなら true
	 */
	public boolean dollarQuote () {

		return dollarQuote;

	}

	/**
	 * ブロックコメントを入れ子にできるか（PostgreSQL だけ）
	 *
	 * @return	できるなら true
	 */
	public boolean nestedBlockComment () {

		return nestedBlockComment;

	}

	@Override
	public String toString () {

		StringBuilder text = new StringBuilder("SqlSyntax(");
		if (backslashEscape) { text.append("backslashEscape "); }
		if (hashComment) { text.append("hashComment "); }
		if (dashCommentNeedsSpace) { text.append("dashCommentNeedsSpace "); }
		if (backtickQuote) { text.append("backtickQuote "); }
		if (executableComment) { text.append("executableComment "); }
		if (dollarQuote) { text.append("dollarQuote "); }
		if (nestedBlockComment) { text.append("nestedBlockComment "); }

		return text.toString().trim() + ")";

	}

	/**
	 * 立てるものだけを名前で並べる
	 *
	 * <p>
	 * <b>立てなかったものは false である。</b>7 個を位置で並べる形に戻さないこと——
	 * 全部 boolean なので、順番を間違えても型では気づけない。
	 * </p>
	 */
	static final class Builder {

		private boolean backslashEscape;
		private boolean hashComment;
		private boolean dashCommentNeedsSpace;
		private boolean backtickQuote;
		private boolean executableComment;
		private boolean dollarQuote;
		private boolean nestedBlockComment;

		/** @return 自分自身 */
		Builder backslashEscape () { this.backslashEscape = true; return this; }

		/** @return 自分自身 */
		Builder hashComment () { this.hashComment = true; return this; }

		/** @return 自分自身 */
		Builder dashCommentNeedsSpace () { this.dashCommentNeedsSpace = true; return this; }

		/** @return 自分自身 */
		Builder backtickQuote () { this.backtickQuote = true; return this; }

		/** @return 自分自身 */
		Builder executableComment () { this.executableComment = true; return this; }

		/** @return 自分自身 */
		Builder dollarQuote () { this.dollarQuote = true; return this; }

		/** @return 自分自身 */
		Builder nestedBlockComment () { this.nestedBlockComment = true; return this; }

		/** @return 出来上がったもの */
		SqlSyntax build () { return new SqlSyntax(this); }

	}

	/** MySQL / MariaDB の決まり */
	public static final SqlSyntax MYSQL = new Builder()
		.backslashEscape()
		.hashComment()
		.dashCommentNeedsSpace()
		.backtickQuote()
		.executableComment()
		.build();

	/** PostgreSQL の決まり */
	public static final SqlSyntax POSTGRESQL = new Builder()
		.dollarQuote()
		.nestedBlockComment()
		.build();

}
