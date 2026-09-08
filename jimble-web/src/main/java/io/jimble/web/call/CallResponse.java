package io.jimble.web.call;

import io.jimble.util.data.Data;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 内部呼び出しの結果（要件 F-W-27）
 *
 * <p>
 * <b>ネットワークに出ていないレスポンス。</b>
 * ハンドラが組み立てたものをそのまま持っている。
 * </p>
 */
public final class CallResponse {

	/* ステータスコード */
	private final int code;

	/* ヘッダ（同じ名前で複数あり得る） */
	private final Map<String, List<String>> headers;

	/* 本文 */
	private final byte[] body;

	/**
	 * コンストラクタ
	 *
	 * @param code		ステータスコード
	 * @param headers	ヘッダ
	 * @param body		本文
	 */
	CallResponse (int code, Map<String, List<String>> headers, byte[] body) {

		this.code = code;
		this.headers = Collections.unmodifiableMap(headers);
		this.body = body;

	}

	/**
	 * ステータスコード
	 *
	 * @return	ステータスコード
	 */
	public int code () {

		return code;

	}

	/**
	 * 成功したか（2xx）
	 *
	 * @return	2xx なら true
	 */
	public boolean isSuccess () {

		return code >= 200 && code < 300;

	}

	/**
	 * Content-Type
	 *
	 * @return	Content-Type。無ければ null
	 */
	public String contentType () {

		return header("Content-Type");

	}

	/**
	 * 本文が圧縮されているか
	 *
	 * <p>
	 * キャッシュ済みのファイルを返すルート（{@code response().cache(...)}）は
	 * <b>gzip のまま</b>返す。中身を読みたい側は、これを見て諦める必要がある。
	 * </p>
	 *
	 * @return	圧縮されていれば true
	 */
	public boolean isCompressed () {

		String encoding = header("Content-Encoding");

		return encoding != null && !encoding.isBlank() && !"identity".equalsIgnoreCase(encoding.trim());

	}

	/**
	 * ヘッダ
	 *
	 * @return	ヘッダ（同じ名前で複数あり得る）
	 */
	public Map<String, List<String>> headers () {

		return headers;

	}

	/**
	 * ヘッダ1つ
	 *
	 * @param name	名前（大小を区別しない）
	 * @return	値。無ければ null。複数あれば最初のもの
	 */
	public String header (String name) {

		List<String> values = headerValues(name);

		return values.isEmpty() ? null : values.get(0);

	}

	/**
	 * ヘッダの値をすべて
	 *
	 * <p>{@code Set-Cookie} のように<b>同じ名前で複数返る</b>ものはこちらで取る。</p>
	 *
	 * @param name	名前（大小を区別しない）
	 * @return	値。無ければ空
	 */
	public List<String> headerValues (String name) {

		for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
			if (entry.getKey().equalsIgnoreCase(name)) {
				return entry.getValue();
			}
		}

		return List.of();

	}

	/**
	 * 本文（バイト列）
	 *
	 * @return	本文
	 */
	public byte[] bytes () {

		return body.clone();

	}

	/**
	 * 本文（文字列）
	 *
	 * @return	本文
	 */
	public String text () {

		return new String(body, StandardCharsets.UTF_8);

	}

	/**
	 * 本文が JSON のオブジェクトか
	 *
	 * <p>
	 * Content-Type と本文の中身の<b>両方</b>を見る。
	 * ステータスだけ 200 でエラーページを返す API があるためである。
	 * 配列（{@code [...]}）は false を返す。
	 * </p>
	 *
	 * @return	JSON のオブジェクトなら true
	 */
	public boolean isJsonObject () {

		String trimmed = text().trim();

		if (isCompressed()) {
			return false;
		}

		if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
			return false;
		}

		String type = contentType();

		return type == null || type.toLowerCase(Locale.ROOT).contains("json");

	}

	/**
	 * 本文を JSON として読む
	 *
	 * @return	JSON。読めなければ空の {@link Data}
	 */
	public Data json () {

		try {
			return Data.fromJsonString(text());
		} catch (Exception cause) {
			return new Data();
		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString () {

		return "CallResponse(%d, %d bytes)".formatted(code, body.length);

	}

}
