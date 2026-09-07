package io.jimble.web.session;

import io.jimble.util.data.Data;

/**
 * セッションの中身と、保存先が必要とする状態
 *
 * <p>
 * <b>「既存レコードかどうか」をアプリから見えるデータに混ぜないため</b>に分けてある。
 * 移送元は {@code __update} というキーをセッションデータに入れており、
 * {@code session().getData()} にそれが現れ、{@code clear()} が特別扱いを強いられていた。
 * </p>
 */
public final class SessionEntry {

	/* 中身 */
	private final Data data;

	/* 保存先に既にあるか */
	private final boolean existing;

	/**
	 * コンストラクタ
	 *
	 * @param data		中身
	 * @param existing	保存先に既にある場合 = true
	 */
	public SessionEntry (Data data, boolean existing) {

		this.data = data == null ? new Data() : data;
		this.existing = existing;

	}

	/**
	 * 空の新規セッション
	 *
	 * @return	セッション
	 */
	public static SessionEntry empty () {

		return new SessionEntry(new Data(), false);

	}

	/**
	 * 中身
	 *
	 * @return	中身
	 */
	public Data data () {

		return data;

	}

	/**
	 * 保存先に既にあるか
	 *
	 * @return	ある場合 = true
	 */
	public boolean isExisting () {

		return existing;

	}

}
