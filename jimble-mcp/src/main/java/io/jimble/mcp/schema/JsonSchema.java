package io.jimble.mcp.schema;

import io.jimble.util.data.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ツールの入力の形（JSON Schema 2020-12 の必要なぶんだけ）
 *
 * <pre>
 * JsonSchema.object()
 *     .string("city", "都市名").required()
 *     .integer("days", "何日ぶん").min(1).max(7)
 *     .bool("detail", "詳しく返すか");
 * </pre>
 *
 * <p>
 * <b>外部の JSON Schema ライブラリを足さない。</b>
 * ここで要るのは「ツールの引数を宣言して JSON にする」ことだけで、
 * 検証（バリデーション）は jimble の {@code Validation} が持っている。
 * ライブラリを足すと、書き方が2つになる。
 * </p>
 *
 * <p>
 * 引数の無いツールは {@link #empty()} を使う。
 * <b>{@code null} は仕様違反</b>である（{@code inputSchema} は必ず妥当な JSON Schema）。
 * </p>
 */
public final class JsonSchema {

	/* 型 */
	private final String type;

	/* 説明 */
	private String description;

	/* プロパティ（宣言した順を保つ） */
	private final Map<String, JsonSchema> properties = new LinkedHashMap<>();

	/* 必須のもの */
	private final List<String> requiredNames = new ArrayList<>();

	/* 直前に足したプロパティ（required() などが効く先） */
	private String last;

	/* 追加のプロパティを許すか */
	private Boolean additionalProperties;

	/* 数値の下限・上限 */
	private Number minimum;
	private Number maximum;

	/* 文字列の候補 */
	private List<String> enumValues;

	/* 配列の中身 */
	private JsonSchema items;

	/**
	 * コンストラクタ
	 *
	 * @param type	型
	 */
	private JsonSchema (String type) {

		this.type = type;

	}

	// region 作る

	/**
	 * オブジェクト
	 *
	 * @return	形
	 */
	public static JsonSchema object () {

		return new JsonSchema("object");

	}

	/**
	 * 引数の無いツール向け
	 *
	 * <p>{@code {"type":"object","additionalProperties":false}}</p>
	 *
	 * @return	形
	 */
	public static JsonSchema empty () {

		JsonSchema schema = new JsonSchema("object");
		schema.additionalProperties = false;

		return schema;

	}

	// endregion

	// region プロパティを足す

	/**
	 * 文字列
	 *
	 * @param name			名前
	 * @param description	説明
	 * @return	自分
	 */
	public JsonSchema string (String name, String description) {

		return add(name, "string", description);

	}

	/**
	 * 整数
	 *
	 * @param name			名前
	 * @param description	説明
	 * @return	自分
	 */
	public JsonSchema integer (String name, String description) {

		return add(name, "integer", description);

	}

	/**
	 * 数値
	 *
	 * @param name			名前
	 * @param description	説明
	 * @return	自分
	 */
	public JsonSchema number (String name, String description) {

		return add(name, "number", description);

	}

	/**
	 * 真偽
	 *
	 * @param name			名前
	 * @param description	説明
	 * @return	自分
	 */
	public JsonSchema bool (String name, String description) {

		return add(name, "boolean", description);

	}

	/**
	 * 配列
	 *
	 * @param name			名前
	 * @param description	説明
	 * @param items			中身
	 * @return	自分
	 */
	public JsonSchema array (String name, String description, JsonSchema items) {

		add(name, "array", description);
		properties.get(name).items = items;

		return this;

	}

	/**
	 * 入れ子のオブジェクト
	 *
	 * @param name			名前
	 * @param description	説明
	 * @param nested		中身
	 * @return	自分
	 */
	public JsonSchema object (String name, String description, JsonSchema nested) {

		nested.description = description;
		properties.put(name, nested);
		last = name;

		return this;

	}

	/**
	 * 足す
	 *
	 * @param name			名前
	 * @param type			型
	 * @param description	説明
	 * @return	自分
	 */
	private JsonSchema add (String name, String type, String description) {

		JsonSchema property = new JsonSchema(type);
		property.description = description;

		properties.put(name, property);
		last = name;

		return this;

	}

	// endregion

	// region 直前のプロパティに付ける

	/**
	 * 直前のプロパティを必須にする
	 *
	 * @return	自分
	 */
	public JsonSchema required () {

		if (last != null && !requiredNames.contains(last)) {
			requiredNames.add(last);
		}

		return this;

	}

	/**
	 * 直前のプロパティに下限を付ける
	 *
	 * @param value	下限
	 * @return	自分
	 */
	public JsonSchema min (Number value) {

		if (last != null) {
			properties.get(last).minimum = value;
		}

		return this;

	}

	/**
	 * 直前のプロパティに上限を付ける
	 *
	 * @param value	上限
	 * @return	自分
	 */
	public JsonSchema max (Number value) {

		if (last != null) {
			properties.get(last).maximum = value;
		}

		return this;

	}

	/**
	 * 直前のプロパティに候補を付ける
	 *
	 * @param values	候補
	 * @return	自分
	 */
	public JsonSchema options (String... values) {

		if (last != null) {
			properties.get(last).enumValues = List.of(values);
		}

		return this;

	}

	// endregion

	/**
	 * 必須のもの
	 *
	 * @return	名前
	 */
	public List<String> requiredNames () {

		return List.copyOf(requiredNames);

	}

	/**
	 * JSON にする
	 *
	 * @return	JSON
	 */
	public Data toData () {

		Data data = new Data();
		data.put("type", type);

		if (description != null && !description.isEmpty()) {
			data.put("description", description);
		}

		if (enumValues != null) {
			data.put("enum", enumValues);
		}

		if (minimum != null) {
			data.put("minimum", minimum);
		}

		if (maximum != null) {
			data.put("maximum", maximum);
		}

		if (items != null) {
			data.put("items", items.toData());
		}

		if (!properties.isEmpty()) {

			Data props = new Data();
			for (Map.Entry<String, JsonSchema> entry : properties.entrySet()) {
				props.put(entry.getKey(), entry.getValue().toData());
			}

			data.put("properties", props);

		}

		if (!requiredNames.isEmpty()) {
			data.put("required", List.copyOf(requiredNames));
		}

		if (additionalProperties != null) {
			data.put("additionalProperties", additionalProperties);
		}

		return data;

	}

}
