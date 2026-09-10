package blog;

import blog.mq.NoticeExecutor;
import io.jimble.batch.BatchTables;
import io.jimble.db.DBUtil;
import io.jimble.db.migration.Migration;
import io.jimble.mq.MqQueue;
import io.jimble.util.conf.Conf;

/**
 * 起動のときに必ず通すところ
 *
 * <p>
 * <b>入口が3つある</b>（{@link BlogApp} / {@link BlogBatch} / {@link BlogScheduler}）。
 * それぞれが自分の分だけ用意していると、<b>入口ごとに違う状態で動く。</b>
 * </p>
 *
 * <h2>実際に壊れた</h2>
 * <p>
 * MQ のテーブルを作っていたのは {@code BlogBatch} と {@code BlogScheduler} だけだった。
 * ところが<b>記事を登録するのは Web のほう</b>で、その中でキューに積んでいる。
 * </p>
 * <p>
 * <b>いちどでもバッチを動かしたマシンでは動いてしまう。</b>
 * まっさらな DB で Web だけ立てると、
 * 記事の登録が「トランザクションのコミットに失敗しました」で 500 になる。
 * 手元では気づけず、CI（まっさらな DB）で初めて出た。
 * </p>
 *
 * <h2>原則1 との折り合い</h2>
 * <p>
 * 「起動の順番が入口に全部書いてある」のが原則だが、
 * <b>同じことを3か所に書くと、そのうち1か所だけ古くなる。</b>
 * 順番そのものはここに上から書いてあるので、入口からは1行辿れば読める。
 * </p>
 */
public final class Bootstrap {

	private Bootstrap () {}

	/**
	 * DB とテーブルを用意する
	 *
	 * <p>
	 * <b>入口はどれも、いちばん最初にこれを呼ぶ。</b>
	 * </p>
	 */
	public static void load () {

		/*
		 * 起動時マイグレーション（要件 F-G-07 / F-G-15）。
		 * <b>DBUtil.load より前に呼ぶ</b>。ここで登録したものが load の中で走る。
		 * ローカルでは何もしない（Gradle の migrate タスクで当てる）。
		 */
		Migration.install();

		/*
		 * <b>戻り値を見る。</b>繋がらなかったときの原因はここのログに出るが、
		 * 見ずに先へ進むと、あとで「DB のことを何も言わない例外」で落ちる。
		 */
		if (!DBUtil.load(Conf.conf().config(), Bootstrap.class)) {
			throw new IllegalStateException(
				"DB を読み込めませんでした（このすぐ上のログに原因が出ています）");
		}

		/*
		 * バッチのテーブル（要件 F-B-09）。
		 * <b>Web でも要る。</b>BlogApp がバッチ管理画面を組み込んでいる。
		 */
		BatchTables.install(DBUtil.getMainDB());

		/*
		 * MQ のテーブル（要件 F-M-07）。
		 * <b>Web でも要る。</b>記事を登録したときにここへ積む。
		 */
		new MqQueue(NoticeExecutor.QUEUE_NAME).install();

	}

}
