package io.jimble.gradle.ai;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.options.Option;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

import java.util.List;

/**
 * jimble の既知の落とし穴を機械的に見つける（要件 D-188）
 *
 * <pre>
 * ./gradlew jimbleCheck
 * </pre>
 *
 * <p>
 * 見つけたものには<b>直し方と引き先（jimble.io の .md）</b>を付ける。AI が出力だけを見て直せるように。
 * {@code ERROR} が1つでもあればタスクを落とし、{@code WARN} だけなら落とさない。
 * 誤検知は、その行か前の行に {@code // jimble-check:ignore J101} と書けば出なくなる。
 * </p>
 */
@UntrackedTask(because = "ソースと設定を読むだけで、出力するファイルが無い。毎回見る")
public abstract class JimbleCheckTask extends DefaultTask {

	/**
	 * コンストラクタ
	 */
	public JimbleCheckTask () {

	}

	/**
	 * プロジェクトの根（{@code .claude/} がある所）
	 *
	 * @return	根
	 */
	@Internal
	public abstract DirectoryProperty getRootDir ();

	/**
	 * プロジェクト（{@code src/} と {@code conf/} がある所）
	 *
	 * @return	プロジェクト
	 */
	@Internal
	public abstract DirectoryProperty getProjectDir ();

	/**
	 * どの版への移行を見るか（{@code --target=2.0}）
	 *
	 * <p>
	 * 2.0 の jimbleCheck は、2.0 で型や意味が変わった呼び出し（J9xx）を<b>いつも</b>出す（要件 D-198）。
	 * 1.5 で {@code --target=2.0} を付けて流していたスクリプトがそのまま動くよう、受け付けて何もしない。
	 * </p>
	 *
	 * @return	版
	 */
	@Internal
	@Option(option = "target", description = "2.0（既定。1.5 との互換のために受け付ける）")
	public abstract Property<String> getTarget ();

	/**
	 * 見る
	 */
	@TaskAction
	public void check () {

		String target = getTarget().getOrElse("");
		if (!target.isEmpty() && !"2.0".equals(target)) {
			throw new GradleException("jimbleCheck: --target に書けるのは 2.0 だけです: " + target);
		}

		List<JimbleChecker.Finding> findings = JimbleChecker.check(
			getRootDir().get().getAsFile().toPath()
			, getProjectDir().get().getAsFile().toPath()
			, AiResources.version()
			, true);

		if (findings.isEmpty()) {
			getLogger().lifecycle("jimbleCheck: 見つかりませんでした（jimble %s の既知の落とし穴）".formatted(AiResources.version()));
			return;
		}

		for (JimbleChecker.Finding finding : findings) {
			if (finding.level() == JimbleChecker.Level.ERROR) {
				getLogger().error(finding.format());
			} else {
				getLogger().warn(finding.format());
			}
		}

		long errors = findings.stream().filter(finding -> finding.level() == JimbleChecker.Level.ERROR).count();

		getLogger().lifecycle("jimbleCheck: ERROR %d 件 / WARN %d 件（誤検知はその行か前の行に // %s J101 のように書くと出なくなる）"
			.formatted(errors, findings.size() - errors, JimbleChecker.IGNORE));

		if (errors > 0) {
			throw new GradleException("jimbleCheck: 直すものが %d 件あります（上に直し方と引き先があります）".formatted(errors));
		}

	}

}
