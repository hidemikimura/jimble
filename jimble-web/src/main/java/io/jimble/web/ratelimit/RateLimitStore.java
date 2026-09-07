package io.jimble.web.ratelimit;

import java.time.Duration;

/**
 * 数えた値の置き場（要件 F-R-15）
 *
 * <p>
 * 実装は3つ。{@code rate_limit.store} で選ぶ。
 * </p>
 *
 * <table border="1">
 *   <caption>置き場</caption>
 *   <tr><th>値</th><th>置き場</th><th>台をまたぐか</th></tr>
 *   <tr><td>{@code memory}（既定）</td><td>その JVM のメモリ</td><td>またがない</td></tr>
 *   <tr><td>{@code redis}</td><td>Redis</td><td><b>またぐ</b></td></tr>
 *   <tr><td>{@code db}</td><td>{@code rate_limit} テーブル</td><td><b>またぐ</b></td></tr>
 * </table>
 *
 * <p>
 * <b>1回ぶん数えるのは不可分でなければならない。</b>
 * 「読んで、足して、書く」を別々にやると、同時に来たぶんが数え落ちる。
 * </p>
 */
public interface RateLimitStore {

	/**
	 * 1回ぶん数える
	 *
	 * @param key		数える単位
	 * @param limit		貯められる回数
	 * @param duration	空から満タンに戻るまでの時間
	 * @return	結果
	 * @throws Exception	数えられなかった場合
	 */
	RateLimitResult consume (String key, long limit, Duration duration) throws Exception;

}
