package io.jimble.util.stopwatch;

import io.jimble.util.log.Log;

public class StopWatch {

	private long before = System.currentTimeMillis();

	public void s () {

		before = System.currentTimeMillis();

	}

	public void e (String message) {

		long now = System.currentTimeMillis();
		Log.info(message + " :" + (now - before) + "ms");
		before = System.currentTimeMillis();

	}

}
