package io.jimble.util.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 戻り値を捨てると効かない（要件 D-191）
 *
 * <p>
 * これが付いたメソッドは<b>値を返すだけで、呼んだ相手を変えない</b>か、
 * <b>失敗を戻り値でしか知らせない</b>。文として書いて戻り値を捨てると、何も起きないか、失敗を見落とす。
 * </p>
 *
 * <pre>
 * Post.title.as("t");                  // 何も起きない（新しい式を返して捨てている）
 * db.update(builder);                  // 1.x では失敗が -1 で返るだけ
 * router.path("/admin");               // 子のルーターを返して捨てている
 * </pre>
 *
 * <p>
 * <b>Error Prone と IntelliJ IDEA は、パッケージを問わずこの名前の注釈を見る</b>ので、
 * 捨てた行に警告が出る。型に付けると、その型の値を返すメソッド全部に効く。
 * </p>
 *
 * <p>
 * 実行時には何もしない（{@link RetentionPolicy#CLASS}）。
 * </p>
 *
 * @since 1.5.0
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE, ElementType.PACKAGE})
public @interface CheckReturnValue {
}
