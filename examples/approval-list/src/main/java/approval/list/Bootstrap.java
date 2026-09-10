package approval.list;

import io.jimble.db.DBUtil;
import io.jimble.db.migration.Migration;
import io.jimble.util.conf.Conf;

/**
 * 起動のときに必ず通すところ
 */
public final class Bootstrap {

	private Bootstrap () {
	}

	/**
	 * DB を用意する
	 */
	public static void load () {

		Migration.install();

		if (!DBUtil.load(Conf.conf().config(), Bootstrap.class)) {
			throw new IllegalStateException(
				"DB を読み込めませんでした（このすぐ上のログに原因が出ています）");
		}

	}

}
