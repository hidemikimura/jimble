package io.jimble.web.http;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * パスはあるが、そのメソッドでは受けていない（要件 F-R-25）
 *
 * <p>
 * <b>404 と分けるのは、原因がまったく違うからである。</b>
 * 404 は「そんなものは無い」、405 は「あるが、その呼び方ではない」。
 * 一緒くたにすると、<b>{@code post} と書くべきところを {@code get} と書いた</b>だけの間違いが、
 * 「パスが間違っている」に見えてしまう。
 * </p>
 *
 * <p>
 * 仕様（RFC 9110）は 405 に {@code Allow} を<b>付けなければならない</b>と定めている。
 * ここで持っているメソッドの一覧がそれになる。
 * </p>
 */
public class MethodNotAllowedException extends HttpException {

	/** 受けているメソッド */
	private final Set<String> allowed;

	/**
	 * コンストラクタ
	 *
	 * @param path		パス
	 * @param allowed	そのパスで受けているメソッド
	 */
	public MethodNotAllowedException (String path, Set<String> allowed) {

		/*
		 * <b>並びを決めておく。</b>{@code Set.copyOf} の並びは決まっていないので、
		 * そのまま Allow に出すと<b>起動のたびに順番が変わる</b>——
		 * 応答を突き合わせているテストや、差分を見ている監視が意味もなく揺れる。
		 */
		super(405, "そのメソッドでは受けていません: %s（受けているのは %s）"
			.formatted(path, String.join(", ", new TreeSet<>(allowed))));

		this.allowed = Collections.unmodifiableSet(new LinkedHashSet<>(new TreeSet<>(allowed)));

	}

	/**
	 * 受けているメソッド
	 *
	 * @return	メソッド
	 */
	public Set<String> allowed () {

		return allowed;

	}

	/**
	 * {@code Allow} ヘッダの値
	 *
	 * @return	値（{@code "GET, POST"} のような形）
	 */
	public String allowHeader () {

		return String.join(", ", allowed);

	}

}
