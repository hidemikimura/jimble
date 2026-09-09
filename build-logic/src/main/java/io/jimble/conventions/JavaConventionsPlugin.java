package io.jimble.conventions;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

import java.util.List;

/**
 * Java モジュールの共通設定（設計書 D-13）
 *
 * <p>
 * 以前は root の {@code build.gradle.kts} の {@code subprojects {}} にあった。
 * モジュールが 12 まで増えて root が 700 行を超えたので、規約プラグインへ移した。
 * </p>
 *
 * <pre>{@code
 * plugins {
 *     id("jimble.java-conventions")
 * }
 * }</pre>
 */
public class JavaConventionsPlugin implements Plugin<Project> {

	@Override
	public void apply (Project project) {

		project.getPluginManager().apply("java-library");

		project.setGroup("io.jimble");
		project.setVersion(JimbleBuild.version(project));

		// Java 25（要件定義 5. 技術前提）
		project.getExtensions().getByType(JavaPluginExtension.class)
			.toolchain(toolchain -> toolchain.getLanguageVersion().set(JavaLanguageVersion.of(25)));

		project.getTasks().withType(JavaCompile.class).configureEach(compile -> {

			compile.getOptions().setEncoding("UTF-8");

			compile.getOptions().getCompilerArgs().addAll(List.of(
				"-Xlint:all"
				, "-Xlint:-serial"
				/*
				 * this-escape を切る（要件 D-15）。
				 *
				 * jimble は<b>ルートをコンストラクタで登録する</b>
				 * （class PostController extends Controller { { get("/posts", ...); } }）。
				 * javac から見ると「派生クラスの初期化が終わる前に this を触っている」ので、
				 * <b>正しく書いたコントローラが必ず1件警告を出す。</b>
				 *
				 * 登録の口（Controller の get / post / before / error / install …）は
				 * <b>全部 protected final</b> で、上書きされたメソッドを呼ぶことはない。
				 * つまりこの形は安全である。
				 *
				 * 1つずつ @SuppressWarnings を付ける手もあるが、
				 * <b>jimble を使う人が書くコントローラすべてに要ることになる</b>。
				 * フレームワークの設計に由来する警告なので、ここで落とす。
				 */
				, "-Xlint:-this-escape"
				/*
				 * <b>警告をエラーにする</b>（要件 D-15）。
				 *
				 * 「あとで潰す」で溜めた警告は潰されない。
				 * 移送時点の警告を jimble-util だけ種別ごと落としていたせいで、
				 * <b>本当に危ないものが山に埋もれて見えなくなっていた。</b>
				 * 消せないものは、消せない理由を書いた @SuppressWarnings を
				 * <b>その場所に</b>付ける（読めば理由が分かる）。
				 */
				, "-Werror"
				// リフレクションに頼らずパラメータ名を残す
				, "-parameters"
			));

		});

		/*
		 * doclint は<b>全モジュールで有効</b>である（要件 D-15）。
		 * 移送してきた jimble-util だけ切っていたが、
		 * <b>切っている場所で壊れても気づけない</b>（publishToMavenLocal が
		 * javadoc で落ちて初めて分かる、という形でしか出てこない）。
		 * 2026-09-08 に 45 件を潰して、切るのをやめた。
		 */
		project.getTasks().withType(Javadoc.class).configureEach(
			javadoc -> javadoc.getOptions().setEncoding("UTF-8"));

	}

}
