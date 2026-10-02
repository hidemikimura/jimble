package io.jimble.web.session;

import io.jimble.util.data.Data;
import io.jimble.web.context.WebContext;
import io.jimble.web.csrf.Csrf;

/**
 * セッション
 *
 * <p>
 * <b>保存は明示的な {@link #save()} で行う。自動保存はしない</b>（要件 F-S-02）。
 * </p>
 *
 * <pre>
 * context.session().put("user_id", 42);
 * context.session().save();
 * </pre>
 *
 * <p>
 * 読み書きの時点で初めて保存先から読み込む。<b>触らなければ何も起きない</b>（要件 F-S-12）。
 * </p>
 *
 * <h2>移送元から変えたところ</h2>
 * <ol>
 *   <li><b>保存し忘れを検知する</b>（要件 F-S-03）。中身を変えたのに {@link #save()} を
 *       呼ばずにリクエストが終わったら、コンテキストのクローズ時に警告を出す。
 *       移送元にはこれが無く、<b>「保存したつもり」が黙って消えていた</b></li>
 *   <li>{@code __update} をアプリから見えるデータに混ぜない（{@link SessionEntry} 参照）</li>
 * </ol>
 */
public final class Session {

	/* コンテキスト */
	private final WebContext context;

	/* 保存先 */
	private final SessionStore store;

	/* 中身（未読み込みは null） */
	private SessionEntry entry;

	/* 変更されたか */
	private boolean dirty = false;

	/* 保存したか */
	private boolean saved = false;

	/* 破棄したか（要件 D-192） */
	private boolean destroyed = false;

	/**
	 * コンストラクタ
	 *
	 * @param context	コンテキスト
	 * @param store		保存先
	 */
	public Session (WebContext context, SessionStore store) {

		this.context = context;
		this.store = store;

	}

	// region 保存

	/**
	 * 保存する
	 *
	 * <p>
	 * 1リクエストで何度呼んでも保存は1回。
	 * 一度も読み書きしていなければ、最終アクセス日時だけ更新する。
	 * </p>
	 */
	public void save () {

		if (saved) {
			return;
		}

		saved = true;

		if (entry == null) {
			// 触っていない。生存期間だけ延ばす
			store.touch(context, SessionEntry.empty());
			return;
		}

		store.save(context, entry);

	}

	/**
	 * セッション ID を振り直す（中身は持ち越す）
	 *
	 * <p>
	 * <b>ログインが通った直後に呼ぶ。</b>ログインの前後で ID が変わらないと、
	 * <b>ログイン前に攻撃者が仕込んだ ID のまま、そのセッションが権限を持つ</b>
	 * （セッション固定化）。仕込む道はいくらでもある——
	 * 公開ページのリンク、別のサブドメイン、ブラウザの拡張。
	 * </p>
	 *
	 * <p>
	 * <b>中身は持ち越す。</b>ログインの直前に入れたもの（戻り先の URL、
	 * 入力途中のフォーム、CSRF トークン）が消えると、
	 * <b>ログインした瞬間に元いた場所を見失う。</b>
	 * ただし {@code csrf.bind_session = true} のときは、CSRF トークンだけは作り直す（{@link Csrf#rotate}）。
	 * </p>
	 *
	 * <p>
	 * <b>ここでは保存しない</b>（要件 F-S-02）。新しい ID が発行されるのは
	 * {@link #save()} のときである。振り直したあと保存しないまま終わると、
	 * <b>古い側は消えていて新しい側は無い</b>——つまりログインしていない状態になる。
	 * <b>閉じるほうに倒れる</b>ので、これでよい。
	 * </p>
	 *
	 * <p>
	 * <b>保存先によって効き方が違う。</b>
	 * </p>
	 * <ul>
	 *   <li>DB / Redis … 古い行を消して、新しい ID で書き直す</li>
	 *   <li>Cookie … <b>ID で引いていないので、中身の Cookie を書き直すだけ</b>。
	 *       <b>古い値はすぐには無効にならない</b>。最後に使ってから {@code session.timeout}、
	 *       発行から {@code session.absolute_timeout} を過ぎると捨てる（D-209。かつてはいつまでも使えた）</li>
	 *   <li>なし … 何も起きない</li>
	 * </ul>
	 */
	public void regenerateId () {

		// 先に読む。消したあとでは読めない
		load();

		Data carried = entry.data();

		/*
		 * 古い側を消す。
		 * DB / Redis はここで ID の Cookie も消える。
		 * Cookie ストアは自分の Cookie しか消さないので、下でこちらからも消す
		 */
		store.destroy(context);

		SessionId.remove(context);

		// 中身は持ち越す。既存ではなくなるので新規として扱う
		entry = new SessionEntry(carried, false);

		dirty = true;

		/*
		 * <b>保存済みの印を戻す。</b>戻さないと save() が何もせずに帰り、
		 * <b>新しい ID が発行されないまま</b>になる（古い側は消えているので、
		 * ログインしたのにログインしていない、が起きる）
		 */
		saved = false;

		/*
		 * <b>CSRF のトークンも回す</b>（csrf.bind_session = true のとき。D-255）。
		 * ログインの前に植え付けたトークンを、ログインのあとも使わせない
		 */
		if (isAvailable() && Csrf.bindSession()) {
			Csrf.rotate(context);
		}

	}

	/**
	 * 保存先があるか
	 *
	 * @return	{@code session.store = "none"} なら false
	 */
	public boolean isAvailable () {

		return !(store instanceof EmptySessionStore);

	}

	/**
	 * 破棄する
	 *
	 * <p>ログアウトで使う。</p>
	 */
	public void destroy () {

		load();
		entry.data().clear();

		store.destroy(context);

		dirty = false;
		saved = true;
		destroyed = true;

	}

	/**
	 * 変更されたか
	 *
	 * @return	変更された場合 = true
	 */
	public boolean isDirty () {

		return dirty;

	}

	/**
	 * 保存されたか
	 *
	 * @return	保存された場合 = true
	 */
	public boolean isSaved () {

		return saved;

	}

	/**
	 * 保存し忘れていないか
	 *
	 * <p>コンテキストのクローズ時に呼ばれる（要件 F-S-03）。</p>
	 *
	 * @return	変更したのに保存していない場合 = true
	 */
	public boolean isUnsavedChange () {

		return dirty && !saved;

	}

	// endregion

	// region 取得

	/**
	 * 中身（読み取り専用の写し）
	 *
	 * <p>
	 * <b>書き換えると {@code UnsupportedOperationException}</b>（2.0。要件 D-196）。
	 * 1.x は中身そのものを返していたので、{@code session().data().put(...)} と書くと
	 * <b>「変えた」印が付かず、保存されなかった</b>。変えるなら {@link #put(String, String)} ほか。
	 * </p>
	 *
	 * @return	中身の写し
	 */
	public Data data () {

		load();

		return entry.data().readOnlyCopy();

	}

	/**
	 * 取得
	 *
	 * @param key	キー
	 * @return	値（無ければ空文字）
	 */
	public String get (String key) {

		load();
		return entry.data().getStringOptional(key);

	}

	/**
	 * 取得
	 *
	 * @param key	キー
	 * @return	値
	 */
	public int getInt (String key) {

		load();
		return entry.data().getInt(key);

	}

	/**
	 * 取得
	 *
	 * @param key	キー
	 * @return	値
	 */
	public long getLong (String key) {

		load();
		return entry.data().getLong(key);

	}

	/**
	 * 取得
	 *
	 * @param key	キー
	 * @return	値
	 */
	public boolean getBoolean (String key) {

		load();
		return entry.data().getBoolean(key);

	}

	/**
	 * あるか
	 *
	 * @param key	キー
	 * @return	ある場合 = true
	 */
	public boolean has (String key) {

		load();
		return entry.data().containsKey(key);

	}

	// endregion

	/*
	 * 変えた印を付ける（要件 D-192）。
	 *
	 * 1.4 までは、save() のあとに変えると<b>黙って捨てていた</b>——save() は1リクエストに1回で、
	 * 2度目は何もしなかったからである。Auth.login(...) も中で save() するので、
	 * ログインの直後に入れた値が消えていた。
	 * いまは「保存済み」を戻すので、もう一度 save() すれば保存され、
	 * 呼ばなければ終わりに「save() が呼ばれていません」と警告が出る。
	 */
	private void markDirty () {

		dirty = true;
		saved = false;

	}

	/*
	 * destroy() のあとの変更は例外（2.0。要件 D-196）。
	 * 保存先が無いので、入れても次のリクエストには残らない——1.x は黙って捨て、1.5 は警告だった。
	 */
	private void requireNotDestroyed () {

		if (destroyed) {
			throw new IllegalStateException(
				"destroy() のあとにセッションは変えられません（保存先が無いので残りません）。"
					+ "ログアウトのあとに値を入れたいなら、destroy() の前に入れるか、次のリクエストで入れてください"
					+ io.jimble.util.internal.Docs.see("session-security"));
		}

	}

	// region 設定

	/**
	 * 設定
	 *
	 * @param key	キー
	 * @param value	値
	 */
	public void put (String key, String value) {

		requireNotDestroyed();
		load();
		entry.data().put(key, value);
		markDirty();

	}

	/**
	 * 設定
	 *
	 * @param key	キー
	 * @param value	値
	 */
	public void put (String key, int value) {

		put(key, String.valueOf(value));

	}

	/**
	 * 設定
	 *
	 * @param key	キー
	 * @param value	値
	 */
	public void put (String key, long value) {

		put(key, String.valueOf(value));

	}

	/**
	 * 設定
	 *
	 * @param key	キー
	 * @param value	値
	 */
	public void put (String key, boolean value) {

		put(key, String.valueOf(value));

	}

	/**
	 * 消す
	 *
	 * @param key	キー
	 */
	public void remove (String key) {

		requireNotDestroyed();
		load();
		entry.data().remove(key);
		markDirty();

	}

	/**
	 * 全部消す
	 *
	 * <p>破棄したいなら {@link #destroy()}。</p>
	 */
	public void clear () {

		requireNotDestroyed();
		load();
		entry.data().clear();
		markDirty();

	}

	// endregion

	/**
	 * 保存先から読み込む（1回だけ）
	 */
	private void load () {

		if (entry != null) {
			return;
		}

		entry = store.load(context);

	}

}
