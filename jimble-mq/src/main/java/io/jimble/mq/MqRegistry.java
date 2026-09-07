package io.jimble.mq;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * MQ Executor の登録（要件 F-M-07）
 *
 * <pre>
 * MqRegistry.add(SendMailExecutor::new);
 * MqRegistry.add(ThumbnailExecutor::new);
 * </pre>
 *
 * <h2>移送元から直したところ</h2>
 * <ol>
 *   <li><b>Guava の {@code ClassPath} でパッケージを走査していた。</b>
 *       原則2 / 要件 NF-P-03 / 要件 F-M-07 のいずれにも反する
 *       （バッチと同じ判断。{@code BatchRegistry} 参照）</li>
 *   <li><b>登録の入れ物が素の {@code HashMap} だった。</b>
 *       書くのは起動時だけとはいえ、<b>読むのは全ワーカースレッド</b>である。
 *       安全に見える保証がない</li>
 *   <li><b>Executor のインスタンスを1つ作って使い回していた。</b>
 *       {@link MqExecutor} 参照。ここでは<b>作り方（{@code Supplier}）を持つ</b></li>
 * </ol>
 */
public final class MqRegistry {

	/* キューのテーブル名 → キー → 作り方 */
	private static final Map<String, Map<String, Supplier<MqExecutor>>> EXECUTORS = new ConcurrentHashMap<>();

	private MqRegistry () {}

	/**
	 * Executor を登録する
	 *
	 * @param supplier	Executor を作るもの（{@code MyExecutor::new}）
	 * @return	登録したキー
	 */
	public static String add (Supplier<MqExecutor> supplier) {

		MqExecutor probe = supplier.get();

		if (probe == null) {
			throw new IllegalArgumentException("MQ Executor を作れませんでした");
		}

		String queueName = probe.queueName();
		String key = probe.key();

		if (queueName == null || queueName.isEmpty()) {
			throw new IllegalArgumentException("queueName() が空です: " + probe.getClass().getName());
		}

		if (key == null || key.isEmpty()) {
			throw new IllegalArgumentException("key() が空です: " + probe.getClass().getName());
		}

		Map<String, Supplier<MqExecutor>> queue =
			EXECUTORS.computeIfAbsent(queueName, name -> new ConcurrentHashMap<>());

		if (queue.putIfAbsent(key, supplier) != null) {
			throw new IllegalStateException("MQ のキーが二重に登録されています: %s / %s".formatted(queueName, key));
		}

		return key;

	}

	/**
	 * Executor を作る
	 *
	 * @param queueName	キューのテーブル名
	 * @param key		キー
	 * @return	Executor（登録されていなければ null）
	 */
	public static MqExecutor create (String queueName, String key) {

		Map<String, Supplier<MqExecutor>> queue = EXECUTORS.get(queueName);

		if (queue == null) {
			return null;
		}

		Supplier<MqExecutor> supplier = queue.get(key);

		return supplier == null ? null : supplier.get();

	}

	/**
	 * キューで使われている実行種別
	 *
	 * <p>種別ごとにワーカーを立てるので、要るものだけを返す。</p>
	 *
	 * @param queueName	キューのテーブル名
	 * @return	実行種別（登録順）
	 */
	public static List<io.jimble.mq.status.MqExecuteType> executeTypes (String queueName) {

		Map<String, Supplier<MqExecutor>> queue = EXECUTORS.get(queueName);

		if (queue == null) {
			return List.of();
		}

		Map<io.jimble.mq.status.MqExecuteType, Boolean> found = new LinkedHashMap<>();

		for (Supplier<MqExecutor> supplier : queue.values()) {
			found.put(supplier.get().executeType(), Boolean.TRUE);
		}

		return new ArrayList<>(found.keySet());

	}

	/**
	 * 登録されているキー
	 *
	 * @param queueName	キューのテーブル名
	 * @return	キー
	 */
	public static List<String> keys (String queueName) {

		Map<String, Supplier<MqExecutor>> queue = EXECUTORS.get(queueName);

		return queue == null ? List.of() : List.copyOf(queue.keySet());

	}

	/**
	 * 登録を全部消す（テスト用）
	 */
	public static void clear () {

		EXECUTORS.clear();

	}

}
