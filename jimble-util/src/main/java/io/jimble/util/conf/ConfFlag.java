package io.jimble.util.conf;

/**
 * リクエストごとに読み直さない設定値（要件 D-167）
 *
 * <h2>なぜ要るのか</h2>
 * <p>
 * <b>{@code Conf.conf().getBoolean(...)} は1回 143 byte / 150ns かかる。</b>
 * {@code hasPath} と {@code getBoolean} がそれぞれキーの文字列を解析して
 * <b>パスの表現を作り直す</b>ためである。
 * </p>
 *
 * <p>
 * <b>1回なら何でもない。</b>問題は<b>リクエストのたびに呼んでいるところ</b>で、
 * 「アクセスログを出すか」「プロキシを信じるか」を<b>確かめるだけ</b>で
 * 1リクエストの割り当ての数%が消えていた——
 * <b>出す・出さないに関わらず、確かめる費用は毎回かかる</b>。
 * </p>
 *
 * <h2>いつ読み直すのか</h2>
 * <p>
 * <b>設定そのものが入れ替わったときだけ。</b>
 * {@link Conf#replace(com.typesafe.config.Config)} と {@link Conf#reload()} は
 * <b>{@code Conf} のインスタンスを作り直す</b>ので、
 * <b>持っているインスタンスと違えば読み直す</b>——
 * 覚えっぱなしにはならないし、無効化を呼び忘れる余地も無い。
 * </p>
 *
 * <pre>
 * private static final ConfFlag ACCESS_LOG = ConfFlag.of("server.access_log", true);
 *
 * if (ACCESS_LOG.get()) {
 *     …
 * }
 * </pre>
 */
public final class ConfFlag {

	/* キー */
	private final String key;

	/* 書いていないときの値 */
	private final boolean defaultValue;

	/*
	 * 覚えている値と、それを読んだときの Conf。
	 *
	 * <b>2つを1つの record にまとめて volatile で持つ。</b>
	 * 別々の変数にすると、<b>片方だけ新しい組み合わせ</b>が見えうる——
	 * 「新しい Conf から読んだ印」と「古い値」が対になると、
	 * <b>設定を変えたのに古い値のまま固まる</b>。
	 */
	private volatile Cached cached;

	/**
	 * 覚えているもの
	 *
	 * @param from	読んだときの設定
	 * @param value	値
	 */
	private record Cached (Conf from, boolean value) {}

	/**
	 * コンストラクタ
	 *
	 * @param key			キー
	 * @param defaultValue	書いていないときの値
	 */
	private ConfFlag (String key, boolean defaultValue) {

		this.key = key;
		this.defaultValue = defaultValue;

	}

	/**
	 * 作る
	 *
	 * <p><b>{@code static final} で1つだけ持つこと。</b>呼ぶたびに作ると意味が無い。</p>
	 *
	 * @param key			キー
	 * @param defaultValue	書いていないときの値
	 * @return	設定値
	 */
	public static ConfFlag of (String key, boolean defaultValue) {

		return new ConfFlag(key, defaultValue);

	}

	/**
	 * 値
	 *
	 * @return	値
	 */
	public boolean get () {

		Conf current = Conf.conf();

		Cached snapshot = cached;

		if (snapshot != null && snapshot.from() == current) {
			return snapshot.value();
		}

		boolean value = current.getBoolean(key, defaultValue);

		cached = new Cached(current, value);

		return value;

	}

	/**
	 * キー
	 *
	 * @return	キー
	 */
	public String key () {

		return key;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString () {

		return "ConfFlag[" + key + "=" + get() + "]";

	}

}
