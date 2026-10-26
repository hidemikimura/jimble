package io.jimble.util.conf;

import java.util.Objects;
import java.util.function.Function;

/**
 * リクエストごとに読み直さない設定値（真偽値以外も。2.7.5）
 *
 * <p>
 * {@link ConfFlag} の一般形。<b>文字列・時間・それらから作った値</b>を、
 * 読み込んだ設定 1 つにつき 1 回だけ解釈して持つ。
 * </p>
 *
 * <h2>なぜ要るのか</h2>
 * <p>
 * <b>{@code Conf.conf().getString(...)} は呼ぶたびにキーの文字列を解析する</b>
 * （{@code hasPath} と {@code getString} がそれぞれパスを作り直す）。
 * Cookie を 1 つ付けるだけで {@code cookie.*} を 8 つ読んでいて、
 * aqSell の購入導線では設定の読み出しだけで CPU の約 3% を使っていた。
 * </p>
 *
 * <h2>いつ読み直すのか</h2>
 * <p>
 * <b>設定そのものが入れ替わったときだけ。</b>
 * {@link Conf#replace(com.typesafe.config.Config)} と {@link Conf#reload()} は
 * {@code Conf} のインスタンスを作り直すので、持っているインスタンスと違えば読み直す——
 * テストで設定を差し替えても古い値は残らない。
 * </p>
 *
 * <p>
 * <b>読めなかったときは覚えない。</b>読む関数が投げた例外はそのまま出し、
 * 次の呼び出しでもう一度読む（書き間違いを毎回知らせるのは、覚える前と同じ）。
 * </p>
 *
 * <pre>
 * private static final ConfValue&lt;String&gt; DOMAIN = ConfValue.of(c -&gt; c.getString("cookie.domain", ""));
 *
 * String domain = DOMAIN.get();
 * </pre>
 *
 * @param <T>	値の型
 * @since 2.7.5
 */
public final class ConfValue<T> {

	/* 設定から値を作る関数 */
	private final Function<Conf, T> reader;

	/*
	 * 覚えている値と、それを読んだときの Conf。
	 * ConfFlag と同じく 1 つの record にまとめて volatile で持つ（片方だけ新しい組み合わせを見せない）
	 */
	private volatile Cached<T> cached;

	/**
	 * 覚えているもの
	 *
	 * @param from	読んだときの設定
	 * @param value	値
	 */
	private record Cached<T> (Conf from, T value) {}

	private ConfValue (Function<Conf, T> reader) {

		this.reader = Objects.requireNonNull(reader, "reader");

	}

	/**
	 * 作る
	 *
	 * @param reader	設定から値を作る関数（同じ設定には同じ値を返すこと）
	 * @param <T>		値の型
	 * @return	ConfValue
	 */
	public static <T> ConfValue<T> of (Function<Conf, T> reader) {

		return new ConfValue<>(reader);

	}

	/**
	 * 値
	 *
	 * @return	いまの設定での値
	 */
	public T get () {

		Conf current = Conf.conf();

		Cached<T> snapshot = cached;

		if (snapshot != null && snapshot.from() == current) {
			return snapshot.value();
		}

		T value = reader.apply(current);

		cached = new Cached<>(current, value);

		return value;

	}

}
