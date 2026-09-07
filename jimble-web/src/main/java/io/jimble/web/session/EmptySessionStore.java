package io.jimble.web.session;

import io.jimble.web.context.WebContext;

/**
 * セッションなし（要件 F-S-01）
 *
 * <p>
 * 何も保存しない。<b>Cookie も発行しない</b>ので、公開ページのキャッシュ効率が落ちない（要件 F-S-12）。
 * </p>
 *
 * <p>
 * アプリのコードは変えずに保存先だけ切り替えられる（要件 F-S-10）ので、
 * セッションを使わない画面ではこれを選べばよい。
 * </p>
 */
public final class EmptySessionStore implements SessionStore {

	/** 共有インスタンス（状態を持たない） */
	public static final EmptySessionStore INSTANCE = new EmptySessionStore();

	@Override
	public SessionEntry load (WebContext context) {

		return SessionEntry.empty();

	}

	@Override
	public void save (WebContext context, SessionEntry entry) {

		// 何もしない

	}

	@Override
	public void touch (WebContext context, SessionEntry entry) {

		// 何もしない

	}

	@Override
	public void destroy (WebContext context) {

		// 何もしない

	}

}
