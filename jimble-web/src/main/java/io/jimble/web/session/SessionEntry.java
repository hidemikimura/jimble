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

	/* 発行した時刻（ミリ秒。分からなければ 0。Cookie セッションが使う。D-209） */
	private final long issuedAt;

	/**
	 * コンストラクタ
	 *
	 * @param data		中身
	 * @param existing	保存先に既にある場合 = true
	 */
	public SessionEntry (Data data, boolean existing) {

		this(data, existing, 0L);

	}

	/**
	 * コンストラクタ（発行した時刻つき）
	 *
	 * @param data		中身
	 * @param existing	保存先に既にあるか
	 * @param issuedAt	発行した時刻（ミリ秒。分からなければ 0）
	 */
	public SessionEntry (Data data, boolean existing, long issuedAt) {

		this.data = data == null ? new Data() : data;
		this.existing = existing;
		this.issuedAt = issuedAt;

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
	 * 発行した時刻（Cookie セッションが、発行からの上限を数えるのに使う。D-209）
	 *
	 * @return	ミリ秒。分からなければ 0
	 */
	public long issuedAt () {

		return issuedAt;

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
