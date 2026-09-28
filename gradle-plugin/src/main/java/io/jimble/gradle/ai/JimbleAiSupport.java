package io.jimble.gradle.ai;

import org.gradle.api.Project;

/**
 * AI 向けのタスクを登録する（要件 D-187）
 *
 * <p>
 * <b>どの jimble のプラグインを当てても付く。</b>{@code io.jimble.jte} / {@code io.jimble.run} /
 * {@code io.jimble.db} のどれか1つでも当てていれば使える（{@code jimble new} は jte と run を当てる）。
 * 2つ以上当てたときに2度登録しないよう、名前で見る。
 * </p>
 */
public final class JimbleAiSupport {

	/** タスクの名前：skill を揃える */
	public static final String SKILLS_TASK = "jimbleSkills";

	/** タスクの名前：既知の落とし穴を見る */
	public static final String CHECK_TASK = "jimbleCheck";

	/** タスクのグループ */
	public static final String GROUP = "jimble";

	private JimbleAiSupport () {
	}

	/**
	 * 登録する
	 *
	 * @param project	プロジェクト
	 */
	public static void register (Project project) {

		if (project.getTasks().getNames().contains(SKILLS_TASK)) {
			return;
		}

		project.getTasks().register(SKILLS_TASK, JimbleSkillsTask.class, task -> {
			task.setGroup(GROUP);
			task.setDescription("AI 向けの skill（.claude/skills/）を、使っている jimble の版に揃える。手で直したものは上書きしない");
			task.getRootDir().set(project.getRootProject().getLayout().getProjectDirectory());
			task.getOverwrite().convention(false);
		});

		project.getTasks().register(CHECK_TASK, JimbleCheckTask.class, task -> {
			task.setGroup(GROUP);
			task.setDescription("jimble の既知の落とし穴をソースと設定から見つける。見つけたものには直し方と引き先を付ける");
			task.getRootDir().set(project.getRootProject().getLayout().getProjectDirectory());
			task.getProjectDir().set(project.getLayout().getProjectDirectory());
		});

	}

}
