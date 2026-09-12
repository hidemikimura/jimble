package io.jimble.util.xml;

import io.jimble.util.convertor.Convertor;
import io.jimble.util.internal.xml.converter.IXmlDataTextConverter;

import java.util.*;

/**
 * XML 汎用Dataクラス
 */
public class XmlData {

	/* タグ名 */
	private String tagName;

	/* 属性 */
	private final Map<String, String> attributes = new LinkedHashMap<>();

	/* 子要素 */
	private final Map<String, List<XmlData>> children = new LinkedHashMap<>();

	/* テキスト */
	private String textContent;

	/* テキストコンバータ */
	private IXmlDataTextConverter converter;

	/**
	 * コンストラクタ
	 */
	public XmlData() {

		this("");

	}

	/**
	 * コンストラクタ
	 *
	 * @param tagName タグ名
	 */
	public XmlData(String tagName) {

		this(tagName, "");

	}

	/**
	 * コンストラクタ
	 *
	 * @param tagName タグ名
	 */
	public XmlData(String tagName, String textContent) {

		this.tagName = tagName;
		this.textContent = textContent;

	}

	// region タグ名

	/**
	 * タグ名を設定する
	 *
	 * @param  tagName タグ名
	 * @return         XmlCommonBean
	 */
	public XmlData setTagName(String tagName) {

		this.tagName = tagName;
		return this;

	}

	/**
	 * タグ名を取得する
	 *
	 * @return タグ名
	 */
	public String getTagName() {

		return this.tagName;

	}

	// endregion

	// region 属性

	/**
	 * 属性を追加する
	 *
	 * @param  name  属性名
	 * @param  value 属性値
	 * @return       XmlCommonBean
	 */
	public XmlData addAttribute(String name, String value) {

		attributes.put(name, value);
		return this;

	}

	/**
	 * 属性値を取得する
	 *
	 * @param  name 属性名
	 * @return      属性値
	 */
	public String getAttribute(String name) {

		if (attributes.containsKey(name)) {
			return attributes.get(name);
		}

		return "";

	}

	/**
	 * 属性名一覧を取得する
	 *
	 * @return 属性名一覧
	 */
	public Set<String> getAttributeKeys() {

		return attributes.keySet();

	}

	/**
	 * 属性一覧を取得する
	 *
	 * @return 属性一覧
	 */
	public Set<Map.Entry<String, String>> getAttributes() {

		return attributes.entrySet();

	}

	// endregion

	// region 子要素

	/**
	 * 子要素を追加する
	 *
	 * @param  child 子要素
	 * @return       XmlCommonBean
	 */
	public XmlData addChild(XmlData child) {

		if (children.containsKey(child.getTagName())) {
			children.get(child.getTagName()).add(child);
		} else {
			List<XmlData> _childs = new ArrayList<>();
			_childs.add(child);
			children.put(child.getTagName(), _childs);
		}

		return this;

	}

	/**
	 * 子要素を取得する
	 *
	 * @param  tagName タグ名
	 * @return         子要素
	 */
	public XmlData getChild(String tagName) {

		if (!children.containsKey(tagName)) {
			return null;
		}

		List<XmlData> childList = children.get(tagName);

		if (childList.isEmpty()) {
			return null;
		}

		return childList.get(0);

	}

	/**
	 * 子要素を取得する
	 *
	 * @param  tagName タグ名
	 * @return         子要素
	 */
	public XmlData getChildOptional(String tagName) {

		XmlData result = getChild(tagName);

		if (result == null) {
			result = new XmlData().setTagName(tagName);
			addChild(result);
		}

		return result;

	}

	/**
	 * 子要素一覧を取得する
	 *
	 * <p>
	 * <b>無ければ空の一覧を返す（要件 D-161）。</b>
	 * 以前は {@code null} を返していたので、
	 * {@code for (XmlData x : xml.getListChild("item"))} と書くと
	 * <b>要素が0本のときだけ NullPointerException</b> になった——
	 * 試したデータに1本でも入っていれば通るので、<b>気づかないまま本番へ出る</b>。
	 * </p>
	 *
	 * <p>
	 * <b>返ってくる空の一覧には足せない。</b>足したいときは
	 * {@link #addChild(XmlData)} か {@link #getListChildOptional(String)} を使うこと——
	 * <b>木に繋がっていない一覧に足しても、書き出しには出てこない</b>。
	 * </p>
	 *
	 * @param  tagName タグ名
	 * @return         子要素一覧（無ければ空）
	 */
	public List<XmlData> getListChild(String tagName) {

		List<XmlData> result = children.get(tagName);

		return result != null ? result : List.of();

	}

	/**
	 * 子要素一覧を取得する（無ければ作って足す）
	 *
	 * <p>
	 * <b>読むだけの口ではない。</b>無ければ<b>その場で木に足す</b>ので、
	 * そのあと書き出すと<b>元には無かった枠が出てくる</b>。
	 * </p>
	 *
	 * @param  tagName タグ名
	 * @return         子要素一覧（木に繋がっている）
	 */
	public List<XmlData> getListChildOptional(String tagName) {

		return children.computeIfAbsent(tagName, key -> new ArrayList<>());

	}

	/**
	 * 子要素名一覧を取得する
	 *
	 * @return 子要素名一覧
	 */
	public Set<String> getChildKeys() {

		return children.keySet();

	}

	// endregion

	// region テキスト

	/**
	 * テキストを設定する
	 *
	 * @param  textContent テキスト
	 * @return             XmlCommonBean
	 */
	public XmlData setTextContent(String textContent) {

		this.textContent = textContent;
		return this;

	}

	/**
	 * テキストを追記する
	 *
	 * @param  textContent テキスト
	 * @return             XmlCommonBean
	 */
	public XmlData addTextContent(String textContent) {

		if (this.textContent == null) {
			this.textContent = textContent;
		} else {
			this.textContent += textContent;
		}

		return this;

	}

	/**
	 * テキストを取得する
	 *
	 * @return テキスト
	 */
	public String getTextContent() {

		return this.textContent;

	}

	/**
	 * 値を取得する
	 *
	 * @return 値
	 */
	public String getString() {

		return this.textContent;

	}

	/**
	 * 値を取得する
	 *
	 * @return 値
	 */
	public byte getByte() {

		try {
			return Convertor.convert(null, this.textContent, byte.class);
		} catch (Exception ex) {
			return 0;
		}

	}

	/**
	 * 値を取得する
	 *
	 * @return 値
	 */
	public short getShort() {

		try {
			return Convertor.convert(null, this.textContent, short.class);
		} catch (Exception ex) {
			return 0;
		}

	}

	/**
	 * 値を取得する
	 *
	 * @return 値
	 */
	public int getInt() {

		try {
			return Convertor.convert(null, this.textContent, int.class);
		} catch (Exception ex) {
			return 0;
		}

	}

	/**
	 * 値を取得する
	 *
	 * @return 値
	 */
	public long getLong() {

		try {
			return Convertor.convert(null, this.textContent, long.class);
		} catch (Exception ex) {
			return 0;
		}

	}

	/**
	 * 値を取得する
	 *
	 * @return 値
	 */
	public float getFloat() {

		try {
			return Convertor.convert(null, this.textContent, float.class);
		} catch (Exception ex) {
			return 0;
		}

	}

	/**
	 * 値を取得する
	 *
	 * @return 値
	 */
	public double getDouble() {

		try {
			return Convertor.convert(null, this.textContent, double.class);
		} catch (Exception ex) {
			return 0;
		}

	}

	/**
	 * 値を取得する
	 *
	 * @return 値
	 */
	public Date getDate() {

		try {
			return Convertor.convert(null, this.textContent, Date.class);
		} catch (Exception ex) {
			return null;
		}

	}

	// endregion

	// region テキストコンバータを設定する

	/**
	 * テキストコンバータを設定する
	 *
	 * @param  converter テキストコンバータ
	 * @return           XmlCommonBean
	 */
	public XmlData setTextConverter(IXmlDataTextConverter converter) {

		this.converter = converter;
		return this;

	}

	// endregion

	// region 値を変換して取得する

	/**
	 * 値を変換して取得する
	 *
	 * @return 値
	 */
	public String getConvertedText() {

		if (this.converter == null) {
			return getTextContent();
		}

		return this.converter.convert(getTextContent());

	}

	// endregion

}
