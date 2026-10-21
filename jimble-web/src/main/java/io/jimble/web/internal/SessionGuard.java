package io.jimble.web.internal;

import java.util.function.Supplier;

/**
 * セッションを使ってはいけない区間（D-305）
 *
 * <p>
 * {@code ApiToken.authenticate} の {@code lookup} を呼んでいるあいだに {@code context.session()} を呼ばれると、
 * トークンのリクエストに既定の保存先のセッションができ、<b>セッションの Cookie が出る</b>。その区間だけ、
 * {@code session()} を分かりやすい例外にする。
 * </p>
 *
 * <p>
 * <b>内部の都合でしかない。</b>{@code ApiToken}（{@code io.jimble.web.auth}）と
 * {@code WebContext}（{@code io.jimble.web.context}）がパッケージをまたいで使うので、両方から見える所に置いている。
 * </p>
 */
public final class SessionGuard {

	/* 禁じている理由（区間の中だけ値がある） */
	private static final ScopedValue<String> REASON = ScopedValue.newInstance();

	private SessionGuard () {
	}

	/**
	 * セッションを使わせずに呼ぶ
	 *
	 * @param reason	session() を呼ばれたときの例外の文
	 * @param body		呼ぶもの
	 * @param <T>		戻り値の型
	 * @return	body の戻り値
	 */
	public static <T> T forbid (String reason, Supplier<T> body) {

		return ScopedValue.where(REASON, reason).call(body::get);

	}

	/**
	 * セッションを使ってよいか確かめる
	 *
	 * @throws IllegalStateException	使ってはいけない区間の場合
	 */
	public static void check () {

		if (REASON.isBound()) {
			throw new IllegalStateException(REASON.get());
		}

	}

}
