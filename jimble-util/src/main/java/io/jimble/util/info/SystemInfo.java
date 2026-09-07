package io.jimble.util.info;

public record SystemInfo (
	// CPU数
	int availableProcessors
	// VMプロセスのCPU使用率
	, double processCpuLoad
	// システム全体のCPU使用率
	, double systemCpuLoad

	// ヒープ使用量
	, long heapMemoryUsage
	// ノンヒープ使用量
	, long nonHeapMemoryUsage
){}
