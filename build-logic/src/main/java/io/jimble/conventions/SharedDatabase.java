package io.jimble.conventions;

import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

/**
 * 実 DB に繋ぐテストを直列にするための印
 *
 * <p>
 * <b>開発用 DB は1つしかない</b>（要件 D-16）。{@code org.gradle.parallel=true} なので、
 * モジュールが増えると複数の {@code dbTest} が同じスキーマを同時に触る。
 * 実際、あるモジュールのテストが TRUNCATE した裏で
 * 別のモジュールのバッチが走っていて、拾えるはずの行が消えていた。
 * </p>
 */
public abstract class SharedDatabase implements BuildService<BuildServiceParameters.None> {
}
