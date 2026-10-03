package io.jimble.web.auth.passkey;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CBOR（RFC 8949）を読む（パスキーに要るぶんだけ。D-261）
 *
 * <p>
 * パスキーの登録の応答（attestationObject）と、公開鍵（COSE_Key）が CBOR で来る。
 * ライブラリを足さず（D-152）、<b>WebAuthn が使う形だけ</b>を読む。
 * </p>
 *
 * <ul>
 *   <li>整数（正・負）→ {@link Long}（収まらなければ {@link BigInteger}）</li>
 *   <li>バイト列 → {@code byte[]}、文字列 → {@link String}</li>
 *   <li>配列 → {@link List}、マップ → {@link Map}（キーの順を保つ）</li>
 *   <li>真偽 → {@link Boolean}、null / undefined → {@code null}</li>
 * </ul>
 *
 * <p>
 * <b>長さを決めない形（indefinite length）と浮動小数は断る</b>（WebAuthn は使わない。CTAP2 の正規の形）。
 * タグは中身だけを返す。入れ子の深さと、宣言された長さは本文を超えないかを確かめる
 * （数バイトの入力で大きな配列を確保させない）。
 * </p>
 */
final class Cbor {

	/** 入れ子の上限 */
	static final int MAX_DEPTH = 16;

	/* 本文 */
	private final byte[] bytes;

	/* 読んでいる位置 */
	private int position;

	private Cbor (byte[] bytes, int offset) {

		this.bytes = bytes;
		this.position = offset;

	}

	/**
	 * 1つの値を読む（後ろに余りがあれば断る）
	 *
	 * @param bytes	CBOR
	 * @return	値
	 * @throws IllegalArgumentException	読めない場合
	 */
	static Object decode (byte[] bytes) {

		Cbor cbor = new Cbor(bytes, 0);
		Object value = cbor.read(0);

		if (cbor.position != bytes.length) {
			throw new IllegalArgumentException("CBOR の後ろに余りがあります");
		}

		return value;

	}

	/**
	 * 途中から1つの値を読む（読み終えた位置も返す）
	 *
	 * <p>authenticatorData の中の公開鍵のように、後ろに続きがあるものに使う。</p>
	 *
	 * @param bytes		本文
	 * @param offset	読み始める位置
	 * @return	値と、読み終えた位置
	 * @throws IllegalArgumentException	読めない場合
	 */
	static Decoded decodePrefix (byte[] bytes, int offset) {

		Cbor cbor = new Cbor(bytes, offset);
		Object value = cbor.read(0);

		return new Decoded(value, cbor.position);

	}

	/**
	 * 途中から読んだもの
	 *
	 * @param value	値
	 * @param end	読み終えた位置
	 */
	record Decoded(Object value, int end) {}

	/**
	 * 1つ読む
	 */
	private Object read (int depth) {

		if (depth > MAX_DEPTH) {
			throw new IllegalArgumentException("CBOR の入れ子が深すぎます");
		}

		int initial = next();
		int major = initial >>> 5;
		int info = initial & 0x1f;

		if (info == 31) {
			throw new IllegalArgumentException("長さを決めない CBOR は読みません");
		}

		return switch (major) {
			case 0 -> integer(argument(info), false);
			case 1 -> integer(argument(info), true);
			case 2 -> take(length(argument(info)));
			case 3 -> new String(take(length(argument(info))), java.nio.charset.StandardCharsets.UTF_8);
			case 4 -> array(length(argument(info)), depth);
			case 5 -> map(length(argument(info)), depth);
			case 6 -> {
				argument(info);
				yield read(depth + 1);
			}
			default -> simple(info);
		};

	}

	private List<Object> array (int length, int depth) {

		List<Object> list = new ArrayList<>(length);

		for (int i = 0; i < length; i++) {
			list.add(read(depth + 1));
		}

		return list;

	}

	private Map<Object, Object> map (int length, int depth) {

		Map<Object, Object> map = new LinkedHashMap<>();

		for (int i = 0; i < length; i++) {

			Object key = read(depth + 1);

			if (key instanceof byte[]) {
				throw new IllegalArgumentException("CBOR のマップのキーにバイト列は使えません");
			}

			if (map.containsKey(key)) {
				throw new IllegalArgumentException("CBOR のマップのキーが重なっています: " + key);
			}

			map.put(key, read(depth + 1));

		}

		return map;

	}

	private Object simple (int info) {

		return switch (info) {
			case 20 -> Boolean.FALSE;
			case 21 -> Boolean.TRUE;
			case 22, 23 -> null;
			default -> throw new IllegalArgumentException("この CBOR の値は読みません（浮動小数など）: " + info);
		};

	}

	/**
	 * 引数（長さや整数の値）を読む。符号なし 64 ビットまで
	 */
	private BigInteger argument (int info) {

		if (info < 24) {
			return BigInteger.valueOf(info);
		}

		int size = switch (info) {
			case 24 -> 1;
			case 25 -> 2;
			case 26 -> 4;
			case 27 -> 8;
			default -> throw new IllegalArgumentException("CBOR の長さが読めません: " + info);
		};

		return new BigInteger(1, take(size));

	}

	private static Object integer (BigInteger value, boolean negative) {

		BigInteger result = negative ? value.negate().subtract(BigInteger.ONE) : value;

		return result.bitLength() < 64 ? (Object) result.longValue() : result;

	}

	/**
	 * 長さ（本文の残りを超えるものは断る。1要素に少なくとも1バイト要るので、配列とマップも同じ上限で見る）
	 */
	private int length (BigInteger value) {

		if (value.compareTo(BigInteger.valueOf(bytes.length - position)) > 0) {
			throw new IllegalArgumentException("CBOR の長さが本文より長いです");
		}

		return value.intValue();

	}

	private int next () {

		if (position >= bytes.length) {
			throw new IllegalArgumentException("CBOR が途中で終わっています");
		}

		return bytes[position++] & 0xff;

	}

	private byte[] take (int length) {

		if (length < 0 || position + length > bytes.length) {
			throw new IllegalArgumentException("CBOR が途中で終わっています");
		}

		byte[] out = java.util.Arrays.copyOfRange(bytes, position, position + length);
		position += length;

		return out;

	}

}
