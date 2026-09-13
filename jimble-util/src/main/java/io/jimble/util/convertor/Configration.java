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
	 * 循環参照を見つけるための記録（書き出している最中に動く）
	 *
	 * <p><b>設定ではなく作業用の状態である。</b></p>
	 */
	private final HashSet<Integer> hashSet = new HashSet<>();

	/** いま何段目か（書き出している最中に動く） */
	private int hierarchy = 0;

	/** これ以上は降りない段数 */
	private int maxHierarchy = 50;

	/** 日付を日付として出すか */
	private boolean isOutputDateType = false;

	/** 日付の書式 */
	private DateTimeFormatter outputDateTypeFormat = null;

	/** 字下げして出すか */
	private boolean isOutputIndent = false;

	/** null の項目も出すか */
	private boolean isOutputNullValue = true;

	/** 厳格に出すか */
	private boolean isOutputStrict = false;

	/** 書き終わったら閉じるか */
	private boolean isAutoClose = true;

	/** テーブルネストの扱い（要件 F-A-11）。既定は「そのまま」 */
	private TableNest tableNest = TableNest.AS_IS;

	/** 1件ごとに循環参照の記録を消すか */
	private boolean isClearHashSet = true;

	/** 4バイト文字を落とすか */
	private boolean isRemove4ByteCharacter = false;

	/** 知らないクラスも出すか */
	private boolean isOutputUnknown = true;

	/**
	 * いま何段目か（書き出している最中に動く）
	 *
	 * @return	いま何段目か（書き出している最中に動く）
	 */
	public int hierarchy () {

		return hierarchy;

	}

	/**
	 * いま何段目か（書き出している最中に動く） を決める
	 *
	 * @param hierarchy	いま何段目か（書き出している最中に動く）
	 */
	public void hierarchy (int hierarchy) {

		this.hierarchy = hierarchy;

	}

	/**
	 * これ以上は降りない段数
	 *
	 * @return	これ以上は降りない段数
	 */
	public int maxHierarchy () {

		return maxHierarchy;

	}

	/**
	 * これ以上は降りない段数 を決める
	 *
	 * @param maxHierarchy	これ以上は降りない段数
	 */
	public void maxHierarchy (int maxHierarchy) {

		this.maxHierarchy = maxHierarchy;

	}

	/**
	 * 日付を日付として出すか
	 *
	 * @return	日付を日付として出すか
	 */
	public boolean isOutputDateType () {

		return isOutputDateType;

	}

	/**
	 * 日付を日付として出すか を決める
	 *
	 * @param isOutputDateType	日付を日付として出すか
	 */
	public void isOutputDateType (boolean isOutputDateType) {

		this.isOutputDateType = isOutputDateType;

	}

	/**
	 * 日付の書式
	 *
	 * @return	日付の書式
	 */
	public DateTimeFormatter outputDateTypeFormat () {

		return outputDateTypeFormat;

	}

	/**
	 * 日付の書式 を決める
	 *
	 * @param outputDateTypeFormat	日付の書式
	 */
	public void outputDateTypeFormat (DateTimeFormatter outputDateTypeFormat) {

		this.outputDateTypeFormat = outputDateTypeFormat;

	}

	/**
	 * 字下げして出すか
	 *
	 * @return	字下げして出すか
	 */
	public boolean isOutputIndent () {

		return isOutputIndent;

	}

	/**
	 * 字下げして出すか を決める
	 *
	 * @param isOutputIndent	字下げして出すか
	 */
	public void isOutputIndent (boolean isOutputIndent) {

		this.isOutputIndent = isOutputIndent;

	}

	/**
	 * null の項目も出すか
	 *
	 * @return	null の項目も出すか
	 */
	public boolean isOutputNullValue () {

		return isOutputNullValue;

	}

	/**
	 * null の項目も出すか を決める
	 *
	 * @param isOutputNullValue	null の項目も出すか
	 */
	public void isOutputNullValue (boolean isOutputNullValue) {

		this.isOutputNullValue = isOutputNullValue;

	}

	/**
	 * 厳格に出すか
	 *
	 * @return	厳格に出すか
	 */
	public boolean isOutputStrict () {

		return isOutputStrict;

	}

	/**
	 * 厳格に出すか を決める
	 *
	 * @param isOutputStrict	厳格に出すか
	 */
	public void isOutputStrict (boolean isOutputStrict) {

		this.isOutputStrict = isOutputStrict;

	}

	/**
	 * 書き終わったら閉じるか
	 *
	 * @return	書き終わったら閉じるか
	 */
	public boolean isAutoClose () {

		return isAutoClose;

	}

	/**
	 * 書き終わったら閉じるか を決める
	 *
	 * @param isAutoClose	書き終わったら閉じるか
	 */
	public void isAutoClose (boolean isAutoClose) {

		this.isAutoClose = isAutoClose;

	}

	/**
	 * テーブルネストの扱い（要件 F-A-11）。既定は「そのまま」
	 *
	 * @return	テーブルネストの扱い（要件 F-A-11）。既定は「そのまま」
	 */
	public TableNest tableNest () {

		return tableNest;

	}

	/**
	 * テーブルネストの扱い（要件 F-A-11）。既定は「そのまま」 を決める
	 *
	 * @param tableNest	テーブルネストの扱い（要件 F-A-11）。既定は「そのまま」
	 */
	public void tableNest (TableNest tableNest) {

		this.tableNest = tableNest;

	}

	/**
	 * 1件ごとに循環参照の記録を消すか
	 *
	 * @return	1件ごとに循環参照の記録を消すか
	 */
	public boolean isClearHashSet () {

		return isClearHashSet;

	}

	/**
	 * 1件ごとに循環参照の記録を消すか を決める
	 *
	 * @param isClearHashSet	1件ごとに循環参照の記録を消すか
	 */
	public void isClearHashSet (boolean isClearHashSet) {

		this.isClearHashSet = isClearHashSet;

	}

	/**
	 * 4バイト文字を落とすか
	 *
	 * @return	4バイト文字を落とすか
	 */
	public boolean isRemove4ByteCharacter () {

		return isRemove4ByteCharacter;

	}

	/**
	 * 4バイト文字を落とすか を決める
	 *
	 * @param isRemove4ByteCharacter	4バイト文字を落とすか
	 */
	public void isRemove4ByteCharacter (boolean isRemove4ByteCharacter) {

		this.isRemove4ByteCharacter = isRemove4ByteCharacter;

	}

	/**
	 * 知らないクラスも出すか
	 *
	 * @return	知らないクラスも出すか
	 */
	public boolean isOutputUnknown () {

		return isOutputUnknown;

	}

	/**
	 * 知らないクラスも出すか を決める
	 *
	 * @param isOutputUnknown	知らないクラスも出すか
	 */
	public void isOutputUnknown (boolean isOutputUnknown) {

		this.isOutputUnknown = isOutputUnknown;

	}

	/**
	 * 循環参照を見つけるための記録
	 *
	 * <p>
	 * <b>作業用の状態なので、そのまま返す。</b>書き出している最中に
	 * 出入りするものであって、設定ではない。
	 * </p>
	 *
	 * @return	記録
	 */
	public HashSet<Integer> hashSet () {

		return hashSet;

	}

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

		return maxHierarchy > 0 && hierarchy > maxHierarchy;
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
