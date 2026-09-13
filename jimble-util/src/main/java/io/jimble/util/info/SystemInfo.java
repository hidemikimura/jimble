package io.jimble.util.info;

/**
 * いまの台の様子（{@link SystemInfoUtil#get()} が作る）
 *
 * <p>
 * <b>その瞬間を写したものである。</b>持ち回っても値は追いつかないので、
 * 見たいときに取り直すこと。
 * </p>
 *
 * <p>
 * <b>record ではない（D-173）。</b>record にすると正準コンストラクタが
 * 項目の一覧そのものになるので、<b>GC 回数・スレッド数・稼働時間を
 * あとから足せない</b>。作るのはこのパッケージの中だけなので、
 * <b>コンストラクタは公開していない</b>——足すときに壊れる相手が居ない。
 * </p>
 *
 * <p>
 * <b>{@code equals} は書いていない。</b>中身は測った瞬間の値なので、
 * 2つが「等しい」ことに意味が無い（同じ台で2回測れば、ふつう違う値になる）。
 * </p>
 */
public final class SystemInfo {

	/** CPU 数 */
	private final int availableProcessors;

	/** この VM の CPU 使用率（%） */
	private final double processCpuLoad;

	/** 台全体の CPU 使用率（%） */
	private final double systemCpuLoad;

	/** ヒープ使用量（byte） */
	private final long heapMemoryUsage;

	/** ノンヒープ使用量（byte） */
	private final long nonHeapMemoryUsage;

	/**
	 * @param availableProcessors	CPU 数
	 * @param processCpuLoad		この VM の CPU 使用率（%）
	 * @param systemCpuLoad			台全体の CPU 使用率（%）
	 * @param heapMemoryUsage		ヒープ使用量（byte）
	 * @param nonHeapMemoryUsage	ノンヒープ使用量（byte）
	 */
	SystemInfo (
		int availableProcessors
		, double processCpuLoad
		, double systemCpuLoad
		, long heapMemoryUsage
		, long nonHeapMemoryUsage
	) {

		this.availableProcessors = availableProcessors;
		this.processCpuLoad = processCpuLoad;
		this.systemCpuLoad = systemCpuLoad;
		this.heapMemoryUsage = heapMemoryUsage;
		this.nonHeapMemoryUsage = nonHeapMemoryUsage;

	}

	/**
	 * CPU 数
	 *
	 * @return	CPU 数
	 */
	public int availableProcessors () {

		return availableProcessors;

	}

	/**
	 * この VM の CPU 使用率
	 *
	 * @return	%（取れない環境では負の値）
	 */
	public double processCpuLoad () {

		return processCpuLoad;

	}

	/**
	 * 台全体の CPU 使用率
	 *
	 * @return	%（取れない環境では負の値）
	 */
	public double systemCpuLoad () {

		return systemCpuLoad;

	}

	/**
	 * ヒープ使用量
	 *
	 * @return	byte
	 */
	public long heapMemoryUsage () {

		return heapMemoryUsage;

	}

	/**
	 * ノンヒープ使用量
	 *
	 * @return	byte
	 */
	public long nonHeapMemoryUsage () {

		return nonHeapMemoryUsage;

	}

	@Override
	public String toString () {

		return "SystemInfo(cpu=" + availableProcessors
			+ ", process=" + processCpuLoad + "%"
			+ ", system=" + systemCpuLoad + "%"
			+ ", heap=" + heapMemoryUsage
			+ ", nonHeap=" + nonHeapMemoryUsage + ")";

	}

}
