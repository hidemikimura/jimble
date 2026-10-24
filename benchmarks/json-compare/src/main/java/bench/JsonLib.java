package bench;

import bench.Samples.SampleBean;
import bench.Samples.SampleRecord;

import io.jimble.util.data.Data;
import io.jimble.util.json.Dson;

import org.apache.fory.json.ForyJson;
import org.apache.fory.reflect.TypeRef;

import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 比べるライブラリの口（どれも同じ形で呼べるようにそろえる）
 *
 * <p>
 * <b>どのライブラリも「いちばん普通の呼び方」で呼ぶ。</b>
 * 特別な設定（速くなるオプションや、型を先に登録する手間）は入れない。
 * 利用者が最初に書く形で比べるためである。
 * </p>
 */
interface JsonLib {

	/** Jackson（参考。書く側の共通の入力を作るのと、結果が正しいかを見るのにも使う） */
	JsonMapper JACKSON = JsonMapper.builder().build();

	/** Apache Fory JSON */
	ForyJson FORY = ForyJson.builder().build();

	/**
	 * 文字列 → 木（Map / List）
	 *
	 * @param json	JSON
	 * @return 木
	 */
	Object readTree (String json);

	/**
	 * byte[]（UTF-8）→ 木
	 *
	 * @param json	JSON
	 * @return 木
	 */
	Object readTreeBytes (byte[] json);

	/**
	 * 文字列 → Bean / record（1件）
	 *
	 * @param json	JSON
	 * @param type	型
	 * @return 読んだもの
	 */
	Object readObject (String json, Class<?> type);

	/**
	 * 文字列 → Bean / record の List
	 *
	 * @param json	JSON
	 * @param type	要素の型
	 * @return 読んだもの
	 */
	Object readList (String json, Class<?> type);

	/**
	 * → 文字列
	 *
	 * @param value	書くもの
	 * @return JSON
	 */
	String write (Object value);

	/**
	 * → byte[]（UTF-8）
	 *
	 * @param value	書くもの
	 * @return JSON
	 */
	byte[] writeBytes (Object value);

	/**
	 * 名前からライブラリを選ぶ
	 *
	 * @param name	jimble / fastjson2 / wast / fory / jackson
	 * @return ライブラリの口
	 */
	static JsonLib of (String name) {

		return switch (name) {
			case "jimble" -> new Jimble();
			case "fastjson2" -> new Fastjson2();
			case "wast" -> new Wast();
			case "fory" -> new Fory();
			case "jackson" -> new Jackson();
			default -> throw new IllegalArgumentException("知らないライブラリです: " + name);
		};

	}

	/**
	 * jimble（{@link Dson}）
	 *
	 * <p>
	 * 木は jimble の {@link Data}（オブジェクト）と List（配列）で読む。
	 * byte[] からは InputStream で読み、byte[] へは OutputStream に書く（Dson に byte[] の口は無いので）。
	 * </p>
	 */
	final class Jimble implements JsonLib {

		@Override
		public Object readTree (String json) {
			return json.charAt(0) == '[' ? Dson.decodes(json, List.class) : Dson.decodes(json, Data.class);
		}

		@Override
		public Object readTreeBytes (byte[] json) {
			Class<?> type = json[0] == '[' ? List.class : Data.class;
			return new Dson().decode(new ByteArrayInputStream(json), StandardCharsets.UTF_8.name(), type);
		}

		@Override
		public Object readObject (String json, Class<?> type) {
			return Dson.decodes(json, type);
		}

		@Override
		public Object readList (String json, Class<?> type) {
			return Dson.decodes(json, List.class, type);
		}

		@Override
		public String write (Object value) {
			return Dson.encodes(value);
		}

		@Override
		public byte[] writeBytes (Object value) {
			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			Dson.encodes(value, stream, StandardCharsets.UTF_8.name());
			return stream.toByteArray();
		}

	}

	/** fastjson2 */
	final class Fastjson2 implements JsonLib {

		@Override
		public Object readTree (String json) {
			return com.alibaba.fastjson2.JSON.parse(json);
		}

		@Override
		public Object readTreeBytes (byte[] json) {
			return com.alibaba.fastjson2.JSON.parse(json);
		}

		@Override
		public Object readObject (String json, Class<?> type) {
			return com.alibaba.fastjson2.JSON.parseObject(json, type);
		}

		@Override
		public Object readList (String json, Class<?> type) {
			return com.alibaba.fastjson2.JSON.parseArray(json, type);
		}

		@Override
		public String write (Object value) {
			return com.alibaba.fastjson2.JSON.toJSONString(value);
		}

		@Override
		public byte[] writeBytes (Object value) {
			return com.alibaba.fastjson2.JSON.toJSONBytes(value);
		}

	}

	/** wast */
	final class Wast implements JsonLib {

		@Override
		public Object readTree (String json) {
			return io.github.wycst.wast.json.JSON.parse(json);
		}

		@Override
		public Object readTreeBytes (byte[] json) {
			return io.github.wycst.wast.json.JSON.parse(json);
		}

		@Override
		public Object readObject (String json, Class<?> type) {
			return io.github.wycst.wast.json.JSON.parseObject(json, type);
		}

		@Override
		public Object readList (String json, Class<?> type) {
			return io.github.wycst.wast.json.JSON.parseArray(json, type);
		}

		@Override
		public String write (Object value) {
			return io.github.wycst.wast.json.JSON.toJsonString(value);
		}

		@Override
		public byte[] writeBytes (Object value) {
			return io.github.wycst.wast.json.JSON.toJsonBytes(value);
		}

	}

	/** Apache Fory JSON */
	final class Fory implements JsonLib {

		/** List&lt;Bean&gt; の型 */
		private static final TypeRef<List<SampleBean>> BEANS = new TypeRef<>() { };

		/** List&lt;record&gt; の型 */
		private static final TypeRef<List<SampleRecord>> RECORDS = new TypeRef<>() { };

		@Override
		public Object readTree (String json) {
			return FORY.fromJson(json, Object.class);
		}

		@Override
		public Object readTreeBytes (byte[] json) {
			return FORY.fromJson(json, Object.class);
		}

		@Override
		public Object readObject (String json, Class<?> type) {
			return FORY.fromJson(json, type);
		}

		@Override
		public Object readList (String json, Class<?> type) {
			return type == SampleBean.class ? FORY.fromJson(json, BEANS) : FORY.fromJson(json, RECORDS);
		}

		@Override
		public String write (Object value) {
			return FORY.toJson(value);
		}

		@Override
		public byte[] writeBytes (Object value) {
			return FORY.toJsonBytes(value);
		}

	}

	/** Jackson 3 */
	final class Jackson implements JsonLib {

		@Override
		public Object readTree (String json) {
			return JACKSON.readValue(json, Object.class);
		}

		@Override
		public Object readTreeBytes (byte[] json) {
			return JACKSON.readValue(json, Object.class);
		}

		@Override
		public Object readObject (String json, Class<?> type) {
			return JACKSON.readValue(json, type);
		}

		@Override
		public Object readList (String json, Class<?> type) {
			return JACKSON.readValue(json, JACKSON.getTypeFactory().constructCollectionType(List.class, type));
		}

		@Override
		public String write (Object value) {
			return JACKSON.writeValueAsString(value);
		}

		@Override
		public byte[] writeBytes (Object value) {
			return JACKSON.writeValueAsBytes(value);
		}

	}

}
