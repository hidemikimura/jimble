package io.jimble.util.internal.json.stream.writer;

import io.jimble.util.convertor.Configration;
import io.jimble.util.json.Dson;

import java.io.*;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON Array出力
 */
public class JsonArrayWriter implements Closeable, AutoCloseable {

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
	public JsonArrayWriter(File file) throws IOException {
		this.isRoot = true;
		this.writer = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8));
		this.writer.write("[");
	}

	/**
	 * コンストラクタ
	 *
	 * @param writer    writer
	 * @throws IOException  エラー
	 */
	public JsonArrayWriter (BufferedWriter writer) throws IOException {
		this.writer = writer;
		this.writer.write("[");
	}

	/**
	 * write
	 *
	 * @param obj   Object
	 * @throws IOException  エラー
	 */
	public void write (Object obj) throws IOException {
		if (obj == null) {
			// null
			writeChildrenEnd();
			writeSeparator();
			this.writer.write("null");
		} else if (obj.getClass().isArray()) {
			// 配列
			writeChildrenEnd();
			int length = Array.getLength(obj);
			for (int i = 0; i < length; i++) {
				writeSeparator();
				Object o = Array.get(obj, i);
				Dson.encodes(configration(), o, writer);
			}
		} else if (obj instanceof Iterable<?> iterable) {
			writeChildrenEnd();
			for (Object o : iterable) {
				writeSeparator();
				Dson.encodes(configration(), o, writer);
			}
		} else {
			writeChildrenEnd();
			writeSeparator();
			Dson.encodes(configration(), obj, writer);
		}
	}

	/**
	 * ハッシュ追加
	 *
	 * @return  ハッシュライター
	 * @throws IOException  エラー
	 */
	public JsonHashWriter hash () throws IOException {
		writeChildrenEnd();
		writeSeparator();
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
	public JsonArrayWriter array () throws IOException {
		writeChildrenEnd();
		writeSeparator();
		JsonArrayWriter jsonArrayWriter = new JsonArrayWriter(writer);
		children.add(jsonArrayWriter);
		return jsonArrayWriter;
	}

	/**
	 * JSON出力設定
	 *
	 * @return  JSON出力設定
	 */
	private Configration configration () {
		Configration configration = new Configration();
		configration.isAutoClose(false);
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
		this.writer.write("]");
		if (this.isRoot) {
			this.writer.flush();
			this.writer.close();
		}
	}

}
