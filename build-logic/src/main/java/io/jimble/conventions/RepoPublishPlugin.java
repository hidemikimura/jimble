package io.jimble.conventions;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * repo.jimble.io へ上げるタスク（{@code repoPublish}）を足す（ルートだけに適用する）
 *
 * <p>
 * 材料を build/central に出すこと・木がきれいかを見ること・タグを打つことは、
 * Central のときと同じものをルートの build.gradle.kts が {@code repoPublish} に足す。
 * ここが持つのは「上げる」だけである。
 * </p>
 */
public class RepoPublishPlugin implements Plugin<Project> {

	@Override
	public void apply (Project project) {

		project.getTasks().register("repoPublish", RepoUpload.class, task -> {

			task.setGroup("publishing");
			task.setDescription("build/central の版を repo.jimble.io（Cloudflare R2）へ上げ、送った木にタグを打って push する");

			task.getVersion().convention(JimbleBuild.version(project));
			task.getRepoDir().convention(project.getLayout().getBuildDirectory().dir("central"));
			task.getDryRun().convention(project.getProviders().gradleProperty("repo.dryRun").map(Boolean::parseBoolean).orElse(false));

			// 置き場の状態は手元では分からないので、毎回動かす
			task.doNotTrackState("上げたかどうかは R2 にしか無い");

		});

	}

}
