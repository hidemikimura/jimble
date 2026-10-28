package io.jimble.gradle.ai;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;
import org.gradle.api.tasks.options.Option;

/**
 * {@code .claude/skills/} を、使っている jimble の版の skill に揃える（要件 D-187）
 *
 * <pre>
 * ./gradlew jimbleSkills
 * ./gradlew jimbleSkills --overwrite    # 手で直したもの・控えの無いものも入れ替える（この版に無いものは消す）
 * </pre>
 *
 * <p>
 * <b>プロジェクトの外（{@code .claude/}）に書くので、最新かどうかを Gradle に判断させない。</b>
 * 毎回走り、中で「同じなら書かない」を判断する。
 * </p>
 */
@UntrackedTask(because = "プロジェクトの根の .claude/ を読み書きする。同じなら書かないのは中で判断する")
public abstract class JimbleSkillsTask extends DefaultTask {

	/**
	 * コンストラクタ
	 */
	public JimbleSkillsTask () {

	}

	/**
	 * プロジェクトの根（{@code .claude/} と {@code AGENTS.md} を置く所）
	 *
	 * @return	根
	 */
	@Internal
	public abstract DirectoryProperty getRootDir ();

	/**
	 * 手で直したもの・控えの無いものも入れ替えるか
	 *
	 * @return	入れ替える場合 = true
	 */
	@Input
	public abstract Property<Boolean> getOverwrite ();

	/**
	 * コマンドラインの {@code --overwrite}
	 *
	 * @param overwrite	入れ替えるか
	 */
	@Option(option = "overwrite", description = "手で直した skill・控えの無い skill も、この版のものに入れ替える（この版に無い skill は消す）")
	public void setOverwriteOption (boolean overwrite) {

		getOverwrite().set(overwrite);

	}

	/**
	 * 揃える
	 */
	@TaskAction
	public void install () {

		SkillsInstaller.Report report = SkillsInstaller.install(
			getRootDir().get().getAsFile().toPath()
			, AiResources.skills()
			, AiResources.version()
			, AiResources.agents()
			, AiResources.claude()
			, getOverwrite().getOrElse(false));

		getLogger().lifecycle(report.previousVersion().isEmpty() || report.previousVersion().equals(report.version())
			? "jimble %s の skill に揃えます".formatted(report.version())
			: "jimble %s → %s の skill に揃えます".formatted(report.previousVersion(), report.version()));

		for (SkillsInstaller.Entry entry : report.entries()) {
			getLogger().lifecycle("  %-10s .claude/skills/%s".formatted(label(entry.result()), entry.path()));
		}

		for (String created : report.created()) {
			getLogger().lifecycle("  %-10s %s".formatted("置いた", created));
		}

		for (String removed : report.removed()) {
			getLogger().lifecycle("  %-10s .claude/skills/%s（この版には無い）".formatted("消した", removed));
		}

		for (String gone : report.gone()) {
			getLogger().lifecycle("  %-10s .claude/skills/%s（この版には無いが、手で直してあるので消さない）".formatted("古い", gone));
		}

		if (report.hasKept()) {
			getLogger().warn("""
				触らなかった skill があります。
				  手で直してある / 直したかどうか分からない（控えを書くようになる前に置いたもの）ためです。
				  この版の skill と古い skill の両方を AI が読むことになります。
				  アプリ固有のことは skill ではなく AGENTS.md に書き、この版の skill に入れ替えてください
				  （この版に無い skill は消えます）：
				    ./gradlew jimbleSkills --overwrite""");
		}

	}

	/**
	 * 結果の見出し
	 *
	 * @param result	結果
	 * @return	見出し
	 */
	static String label (SkillsInstaller.Result result) {

		return switch (result) {
			case ADDED -> "足した";
			case UPDATED -> "入れ替えた";
			case UNCHANGED -> "同じ";
			case OVERWRITTEN -> "上書きした";
			case KEPT_EDITED -> "触らない（手で直してある）";
			case KEPT_UNKNOWN -> "触らない（控えが無い）";
		};

	}

}
