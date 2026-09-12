plugins {
	id("jimble.java-conventions")
	id("jimble.test-conventions")
	id("jimble.publish-conventions")

	application
}

description = "プロジェクト雛形の生成と、マイグレーション・コード生成の実行（要件 F-X-01 / F-G-08）"

dependencies {
	/*
	 * migrate / codegen は jimble-db の CLI に委ねる。
	 * ここが持つのは入口と雛形だけである。
	 */
	api(project(":jimble-db"))
}

/*
 * skill を雛形へ写す（要件 NF-D-09）。
 *
 * <b>正は .claude/skills/ の1か所だけ。</b>ここへ手で写した写しを置くと、
 * 直したときに<b>片方だけ古くなる</b>——しかも古いほうが利用者の手元へ配られる。
 * ビルドで写して、写し忘れは JimbleNewSkillsTest が落とす。
 */
val skeletonSkills = layout.buildDirectory.dir("generated/skeleton")

val copySkills by tasks.registering(Copy::class) {
	from(rootProject.layout.projectDirectory.dir(".claude/skills"))
	// リソースの根から見た置き場所に合わせる（Skeleton.BASE + "skills/"）
	into(skeletonSkills.map { it.dir("io/jimble/cli/skeleton/skills") })
}

sourceSets.main {
	resources.srcDir(copySkills.map { skeletonSkills })
}

/*
 * 版は雛形が参照する依存の版になる（Version クラスが読む）。
 * 手で書かず、ビルドの版をそのまま入れる。
 */
tasks.jar {
	manifest {
		attributes(
			"Implementation-Title" to "jimble",
			"Implementation-Version" to project.version,
		)
	}
}

application {
	mainClass = "io.jimble.cli.internal.JimbleCli"
	applicationName = "jimble"
	applicationDefaultJvmArgs = listOf("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}
