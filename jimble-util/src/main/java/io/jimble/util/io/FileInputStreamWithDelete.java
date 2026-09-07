package io.jimble.util.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Close時にファイルを削除するFileInputStream
 */
public class FileInputStreamWithDelete extends FileInputStream {

	private File file;

	public FileInputStreamWithDelete(File f) throws FileNotFoundException, IOException {

		super(f);
		this.file = f;

	}

	@Override
	public void close() throws IOException {

		super.close();
		FileUtil.delete(this.file);

	}

}
