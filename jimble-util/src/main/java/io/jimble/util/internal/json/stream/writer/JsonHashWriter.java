package io.jimble.util.internal.json.stream.writer;

import io.jimble.util.convertor.Configration;
import io.jimble.util.json.Dson;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON Hash出力
 */
public class JsonHashWriter implements Closeable, AutoCloseable {

	/* 子要素 */
	private final List<Closeable> children = new ArrayList<>();

	/* Root判定 */
	private boolean isRoot = false;

	/* writer */
	private final BufferedWriter writer;

	/* 初回要素判定 */
	private boolean isFirstKey = true;

	/**
	 * コンストラクタ
	 *
	 * @param file  ファイル
	 * @throws IOException  エラー
	 */
	public JsonHashWriter(File file) throws IOException {
		this.isRoot = true;
		this.writer = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8));
		this.writer.write("{");
	}

	/**
	 * コンストラクタ
	 *
	 * @param writer    writer
	 * @throws IOException  エラー
	 */
	public JsonHashWriter(BufferedWriter writer) throws IOException {
		this.writer = writer;
		this.writer.write("{");
	}

	/**
	 * write
	 *
	 * @param key   キー
	 * @param obj   Object
	 * @throws IOException  エラー
	 */
	public void write (String key, Object obj) throws IOException {
		if (obj == null) {
			// null
			writeChildrenEnd();
			writeSeparator();
			writeKey(key);
			this.writer.write("null");
		} else if (obj instanceof File file) {
			writeChildrenEnd();
			writeSeparator();
			writeKey(key);
			try (
				FileReader fr = new FileReader(file, StandardCharsets.UTF_8);
				BufferedReader br = new BufferedReader(fr);
			) {
				int len;
				char[] buf = new char[1024];
				while ((len = br.read(buf)) != -1) {
					this.writer.write(buf, 0, len);
				}
			}
		} else {
			writeChildrenEnd();
			writeSeparator();
			writeKey(key);
			Dson.encodes(configration(), obj, writer);
		}
	}

	/**
	 * ハッシュ追加
	 *
	 * @return  ハッシュライター
	 * @throws IOException  エラー
	 */
	public JsonHashWriter hash (String key) throws IOException {
		writeChildrenEnd();
		writeSeparator();
		writeKey(key);
		JsonHashWriter jsonHashWriter = new JsonHashWriter(writer);
		children.add(jsonHashWriter);
		return jsonHashWriter;
	}

	/**
	 * 配列追加
	 *
	 * @return  配列ライター
	 * @throws IOException  エラー
	 */
	public JsonArrayWriter array (String key) throws IOException {
		writeChildrenEnd();
		writeSeparator();
		writeKey(key);
		JsonArrayWriter jsonArrayWriter = new JsonArrayWriter(writer);
		children.add(jsonArrayWriter);
		return jsonArrayWriter;
	}

	/**
	 * キーwrite
	 *
	 * @param key   キー
	 * @throws IOException  エラー
	 */
	private void writeKey (String key) throws IOException {
		this.writer.write("\"");
		this.writer.write(Dson.escapeString(key));
		this.writer.write("\":");
	}

	/**
	 * JSON出力設定
	 *
	 * @return  JSON出力設定
	 */
	private Configration configration () {
		Configration configration = new Configration();
		configration.isAutoClose = false;
		return configration;
	}

	/**
	 * 区切り文字出力
	 *
	 * @throws IOException  エラー
	 */
	private void writeSeparator () throws IOException {
		if (!isFirstKey) {
			this.writer.write(",");
		}
		isFirstKey = false;
	}

	/**
	 * 子要素を閉じる
	 *
	 * @throws IOException  エラー
	 */
	private void writeChildrenEnd () throws IOException {
		for (Closeable child : children) {
			child.close();
		}
		children.clear();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close() throws IOException {
		writeChildrenEnd();
		this.writer.write("}");
		if (this.isRoot) {
			this.writer.flush();
			this.writer.close();
		}
	}

}
