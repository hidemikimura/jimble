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

		/*
		 * <b>戻り値を見る。</b>繋がらなかったときの原因はここのログに出るが、
		 * 見ずに先へ進むと、あとで「DB のことを何も言わない例外」で落ちる（D-130）。
		 */
		if (!DBUtil.load(Conf.conf().config(), Bootstrap.class)) {
			throw new IllegalStateException(
				"DB を読み込めませんでした（このすぐ上のログに原因が出ています）");
		}

	}

}
