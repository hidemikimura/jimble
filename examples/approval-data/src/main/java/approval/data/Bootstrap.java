package approval.data;

import approval.data.migration.SeedRatesCodeMigration;

import io.jimble.db.DBUtil;
import io.jimble.db.migration.Migration;
import io.jimble.db.migration.code.CodeMigration;
import io.jimble.util.conf.Conf;

/**
 * 起動のときに必ず通すところ
 *
 * <p>
 * <b>入口が何であれ、ここを通す。</b>Web も結合テストも同じものを呼ぶ。
 * 入口ごとに用意するものが違うと、
 * 「Web だけ立てたら最初のリクエストが 500」のような形で出る。
 * </p>
 */
public final class Bootstrap {

	private Bootstrap () {
	}

	/**
	 * DB を用意する
	 */
	public static void load () {

		/*
		 * コードマイグレーション（要件 F-G-18）。
		 *
		 * <b>クラスパス走査はしない</b>（原則2 / NF-P-03）。ここに並べたものだけが走る。
		 * 走るのは Migration.install() の時点ではなく、
		 * <b>DBUtil.load() の最後</b>（全データソースの用意が済んだあと）である。
		 */
		CodeMigration.add(new SeedRatesCodeMigration());

		Migration.install();

		if (!DBUtil.load(Conf.conf().config(), Bootstrap.class)) {
			throw new IllegalStateException(
				"DB を読み込めませんでした（このすぐ上のログに原因が出ています）");
		}

	}

}
