package io.jimble.util.convertor;

import io.jimble.util.data.TableNest;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * 設定情報クラス.
 *
 * @author DN
 */
public class Configration {

	private static final DateTimeFormatter JSON_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

	/**
	 * 設定マップ.
	 */
	private Map<Object, Object> config = new HashMap<Object, Object>();

	/**
	 * 設定数.
	 */
	private boolean exists = false;

	/**
	 * ハッシュ値.
	 */
	private int hash = 0;

	/**
	 * 階層.
	 */
	public int Hierarchy = 0;

	/**
	 * 最大階層.
	 */
	public int MaxHierarchy = 50;

	/**
	 * 日付型出力
	 */
	public boolean isOutputDateType = false;

	/**
	 * 日付型出力フォーマッタ
	 */
	public DateTimeFormatter outputDateTypeFormat = null;

	/**
	 * インデント出力判定.
	 */
	public boolean isOutputIndent = false;

	/**
	 * Null値出力判定.
	 */
	public boolean isOutputNullValue = true;

	/**
	 * 厳格モード.
	 */
	public boolean isOutputStrict = false;

	/**
	 * 自動クローズ
	 */
	public boolean isAutoClose = true;

	/**
	 * テーブルネストの扱い（要件 F-A-11）.
	 *
	 * <p>
	 * {@code AsyncData} / {@code AsyncList} を書き出すときに、
	 * テーブル名でネストするかどうか。既定は「そのまま」で、
	 * <b>これまでと出力が変わらない</b>。
	 * </p>
	 */
	public TableNest tableNest = TableNest.AS_IS;

	/**
	 * 循環参照ハッシュセット.
	 */
	public HashSet<Integer> hashSet = new HashSet<>();

	/**
	 * 循環参照ハッシュセットリセット
	 */
	public boolean isClearHashSet = true;

	/**
	 * 4バイト文字除去
	 */
	public boolean isRemove4ByteCharacter = false;

	/**
	 * 不明クラスの出力
	 */
	public boolean isOutputUnknown = true;

	/**
	 * 循環参照ハッシュセットクリア
	 */
	public void clearHashSet () {

		if (!isClearHashSet) {
			return;
		}

		hashSet.clear();

	}

	/**
	 * 設定情報を設定する.
	 *
	 * @param key   設定情報キー
	 * @param value 値
	 * @return 設定情報オブジェクト
	 */
	public Configration put (Object key, Object value) {

		config.put(key, value);
		exists = config.size() > 0;
		hash = config.hashCode();
		return this;
	}

	/**
	 * 設定情報を取得する.
	 *
	 * @param key 設定情報キー
	 * @return 値
	 */
	public Object get (Object key) {

		return exists ? config.get(key) : null;
	}

	/**
	 * 設定情報を削除する.
	 *
	 * @param key 設定情報キー
	 * @return 設定情報オブジェクト
	 */
	public Configration remove (Object key) {

		if (exists) {
			config.remove(key);
			exists = config.size() > 0;
			hash = config.hashCode();
		}
		return this;
	}

	/**
	 * キーが存在するか判定する.
	 *
	 * @param key 設定情報キー
	 * @return 判定結果
	 */
	public boolean containsKey (Object key) {

		return exists && config.containsKey(key);
	}

	/**
	 * 設定情報のハッシュ値を取得する.
	 *
	 * @return ハッシュ値
	 */
	public int getHashCode () {

		return hash;
	}

	/**
	 * インデント出力判定を設定する.
	 *
	 * @param output インデント出力判定(出力する場合 true)
	 * @return 設定情報オブジェクト
	 */
	public Configration setOutputIndent (boolean output) {

		this.isOutputIndent = output;
		return this;
	}

	/**
	 * Null値出力判定を設定する.
	 *
	 * @param output Null値出力判定(出力する場合 true)
	 * @return 設定情報オブジェクト
	 */
	public Configration setOutputNullValue (boolean output) {

		this.isOutputNullValue = output;
		return this;
	}

	/**
	 * 日付型出力を設定する
	 *
	 * @param outputDateType 日付型出力
	 * @return 設定情報オブジェクト
	 */
	public Configration setOutputDateType (boolean outputDateType) {

		this.isOutputDateType = outputDateType;
		this.outputDateTypeFormat = JSON_DATE_FORMAT;
		return this;
	}

	/**
	 * 日付型出力を設定する
	 *
	 * @param outputDateType 日付型出力
	 * @return 設定情報オブジェクト
	 */
	public Configration setOutputDateType (boolean outputDateType, DateTimeFormatter outputDateTypeFormat) {

		this.isOutputDateType = outputDateType;
		this.outputDateTypeFormat = outputDateTypeFormat;
		return this;
	}

	/**
	 * 最大階層判定を取得する.
	 *
	 * @return 最大階層に達している場合 true
	 */
	public boolean isMaxHierarchy () {

		return MaxHierarchy > 0 && Hierarchy > MaxHierarchy;
	}

	/**
	 * デフォルトのフィールド名使用不可文字を設定する.
	 *
	 * @return 設定情報オブジェクト
	 */
	public Configration setDefaultFieldDelimiterChar () {

		put(ConvertorConfigKeys.CONVERT_FIELDNAME_DELIMITER, new char[]{'_', '-', ' '});
		return this;
	}

}
