package approval.auth;

import io.jimble.db.DBUtil;
import io.jimble.db.migration.Migration;
import io.jimble.util.conf.Conf;

/**
 * 起動のときに必ず通すところ
 *
 * <p>
 * <b>入口は {@link AuthApp} 1つしかないが、テストからも同じものを呼ぶ。</b>
 * テストだけ別に用意すると、<b>入口が変わったときにテストだけ通る</b>状態になる。
 * </p>
 *
 * <p>
 * セッションのテーブルはここでは作らない。
 * <b>DB セッションは最初に使われたときに自分で作る</b>ので、
 * ここに書くと「2か所で同じテーブルを作る」ことになる。
 * </p>
 */
public final class Bootstrap {

	private Bootstrap () {
	}

	/**
	 * DB を用意する
	 */
	public static void load () {

		// 起動時マイグレーション（要件 F-G-07 / F-G-15）。DBUtil.load より前に呼ぶ
		Migration.install();

		// 繋がらなければ例外で止まる（2.0。1.x は false を返したので、見ないと起動してしまった）
		DBUtil.load(Conf.conf().config(), Bootstrap.class);

	}

}
