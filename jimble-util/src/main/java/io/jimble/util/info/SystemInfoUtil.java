package io.jimble.util.info;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

/**
 * システム情報
 */
public class SystemInfoUtil {

	/**
	 * システム情報を取得する
	 *
	 * @return	システム情報
	 */
	public static SystemInfo get () {

		com.sun.management.OperatingSystemMXBean osBean = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
		MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

		return new SystemInfo(
			osBean.getAvailableProcessors()
			, osBean.getProcessCpuLoad() * 100
			, osBean.getCpuLoad() * 100
			, memoryBean.getHeapMemoryUsage().getUsed()
			, memoryBean.getNonHeapMemoryUsage().getUsed()
		);

	}

}
