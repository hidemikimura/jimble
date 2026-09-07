package io.jimble.util.io.output;

import io.jimble.util.io.IOUtil;
import io.jimble.util.log.Log;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class TextOutput implements Closeable, AutoCloseable {

	private BufferedWriter bw;

	public TextOutput(File file) {

		try {
			this.bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
		} catch (Exception ex) {
			Log.error(ex);
		}

	}

	public boolean write (String line) {

		try {
			bw.write(line);
			return true;
		} catch (Exception ex) {
			Log.error(ex);
			return false;
		}

	}

	public boolean writeLine (String line) {

		try {
			bw.write(line);
			bw.newLine();
			return true;
		} catch (Exception ex) {
			Log.error(ex);
			return false;
		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close() throws IOException {

		IOUtil.close(bw);

	}

}
