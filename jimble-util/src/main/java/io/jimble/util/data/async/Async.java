package io.jimble.util.data.async;

/**
 * 遅延読み込みするもの
 *
 * <p>
 * {@link AsyncData} と {@link AsyncList} の共通の目印。
 * <b>読み込みを起こさずに状態を見る</b>ためだけの型である。
 * </p>
 *
 * <p>
 * 先読み（要件 F-A-06。Phase 2）はツリーを walk して未読み込みの枝を集めるので、
 * <b>「遅延読み込みするもの」を1つの型で拾えることが要る</b>（要件 F-A-10）。
 * {@code Data} の要約表示（要件 F-D-27）もここを見る。
 * </p>
 */
public interface Async {

	/**
	 * 読み込み済みか
	 *
	 * @return	読み込み済みの場合 = true
	 */
	boolean isLoaded ();

	/**
	 * 読み込みに失敗したか（要件 F-A-09）
	 *
	 * @return	失敗した場合 = true
	 */
	boolean isLoadFailed ();

	/**
	 * まとめて読む単位の識別子（要件 F-A-10）
	 *
	 * @return	識別子（先読みの対象外なら null）
	 */
	String batchKey ();

	/**
	 * {@link #batchKey()} の中で自分を特定する値（要件 F-A-10）
	 *
	 * @return	値
	 */
	Object batchId ();

}
