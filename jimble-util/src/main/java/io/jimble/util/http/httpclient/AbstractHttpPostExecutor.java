package io.jimble.util.http.httpclient;

import io.jimble.util.http.httpclient.publisher.MultipartFormDataBodyPublisher;
import io.jimble.util.data.Data;

import java.io.File;
import java.io.InputStream;
import java.net.URLEncoder;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * POST基底
 */
public abstract class AbstractHttpPostExecutor<E> extends AbstractHttpExecutor<E> {

	// region リクエストボディFORM

	/* リクエストボディFORM */
	private Map<String, List<String>> requestFormMap = new LinkedHashMap<>();

	/**
	 * リクエストボディFORMを追加する
	 *
	 * @param name  名前
	 * @param value 値
	 */
	public E addBodyForm (String name, String value) {

		if (!requestFormMap.containsKey(name)) {
			requestFormMap.put(name, new ArrayList<>());
		}
		requestFormMap.get(name).add(value);

		return (E) this;

	}

	// endregion

	// region リクエストボディFORMファイル

	/* リクエストボディFORMファイル */
	private Map<String, List<FilePart>> requestFormFileMap = new LinkedHashMap<>();

	/**
	 * リクエストボディFORMファイルを追加する
	 *
	 * @param name  名前
	 * @param value 値
	 * @param contentType コンテンツタイプ
	 */
	public E addBodyForm (String name, File value, String contentType) {

		if (!requestFormFileMap.containsKey(name)) {
			requestFormFileMap.put(name, new ArrayList<>());
		}

		FilePart filePart = new FilePart();
		filePart.value = value;
		filePart.contentType = contentType;

		requestFormFileMap.get(name).add(filePart);

		return (E) this;

	}

	/**
	 * リクエストボディFormを取得する
	 *
	 * @return  リクエストボディForm
	 */
	public Map<String, List<String>> getBodyForm () {

		return requestFormMap;

	}

	// endregion

	// region リクエストボディテキスト

	private String requestBodyPlainText = null;

	public String getBodyText () {

		return requestBodyPlainText;

	}

	private String requestBodyJson = null;

	public String getBodyJson () {

		if (requestBodyJson == null && requestBodyJsonData != null) {
			requestBodyJson = requestBodyJsonData.getJsonString();
		}

		return requestBodyJson;

	}

	private Data requestBodyJsonData = null;

	public Data getBodyJsonData () {

		if (requestBodyJsonData == null && requestBodyJson != null) {
			requestBodyJsonData = Data.fromJsonString(requestBodyJson);
		}

		return requestBodyJsonData;

	}

	private String requestBodyXml = null;

	public String getBodyXml () {

		return requestBodyXml;

	}

	/* リクエストボディテキスト */
	private TextPart requestBodyText = null;

	/**
	 * リクエストボディテキストを設定する
	 *
	 * @param value 値
	 * @param contentType コンテンツタイプ
	 */
	private void setBodyText (String value, String contentType) {

		requestBodyText = new TextPart();
		requestBodyText.value = value;
		requestBodyText.contentType = contentType;

	}

	/**
	 * リクエストボディテキストを設定する
	 *
	 * @param value 値
	 */
	public E setBodyText (String value) {

		requestBodyPlainText = value;
		setBodyText(value, "text/plain; charset=" + getCharset());
		return (E) this;

	}

	/**
	 * リクエストボディJSONを設定する
	 *
	 * @param value 値
	 */
	public E setBodyJson (String value) {

		requestBodyJson = value;
		setBodyText(requestBodyJson, "application/json; charset=" + getCharset());
		return (E) this;

	}

	/**
	 * リクエストボディJSONを設定する
	 *
	 * @param value 値
	 */
	public E setBodyJson (Data value) {

		requestBodyJsonData = value;
		requestBodyJson = requestBodyJsonData.getJsonString();
		setBodyText(requestBodyJson, "application/json; charset=" + getCharset());
		return (E) this;

	}

	/**
	 * リクエストボディテキストを設定する
	 *
	 * @param value 値
	 */
	public E setBodyXml (String value) {

		requestBodyXml = value;
		setBodyText(value, "application/xml; charset=" + getCharset());
		return (E) this;

	}

	// endregion

	// region リクエストボディファイル

	/* リクエストボディファイル */
	private FilePart requestBodyFile = null;

	/**
	 * リクエストボディファイルを設定する
	 *
	 * @param value 値
	 * @param contentType コンテンツタイプ
	 */
	public E setBodyFile (File value, String contentType) {

		requestBodyFile = new FilePart();
		requestBodyFile.value = value;
		requestBodyFile.contentType = contentType;

		return (E) this;

	}

	// endregion

	// region リクエストボディストリーム

	/* リクエストボディストリーム */
	private InputStream requestBodyStream = null;

	/**
	 * リクエストボディストリームを設定する
	 *
	 * @param is	リクエストボディストリーム
	 */
	public E setBodyStream (InputStream is) {

		requestBodyStream = is;

		return (E) this;

	}

	// endregion


	// region ボディを取得する

	private MultipartFormDataBodyPublisher multipartFormDataBodyPublisher = null;

	private void addBodyTypeHeader () {

		String contentType = getContentType();
		if (contentType != null && !contentType.isEmpty()) {
			addHeader("Content-Type", contentType);
		}

	}

	/**
	 * ボディを取得する
	 *
	 * @return  ボディ
	 */
	protected BodyPublisher createBodyPublisher () {

		try {

			// ストリーム
			if (requestBodyStream != null) {
				addBodyTypeHeader();
				return BodyPublishers.ofInputStream(() -> requestBodyStream);
			}

			// テキスト送信
			if (requestBodyText != null) {
				addBodyTypeHeader();
				return BodyPublishers.ofString(requestBodyText.value, Charset.forName(getCharset()));
			}

			// ファイル送信
			if (requestBodyFile != null) {
				addBodyTypeHeader();
				return BodyPublishers.ofFile(requestBodyFile.value.toPath());
			}

			// multipart/form-data
			if (!requestFormFileMap.isEmpty()) {
				multipartFormDataBodyPublisher = new MultipartFormDataBodyPublisher(Charset.forName(getCharset()));
				for (Entry<String, List<FilePart>> entry : requestFormFileMap.entrySet()) {
					for (FilePart value : entry.getValue()) {
						multipartFormDataBodyPublisher.addFile(entry.getKey(), value.value.toPath(), value.contentType);
					}
				}
			}

			// application/x-www-form-urlencoded
			if (!requestFormMap.isEmpty()) {
				if (multipartFormDataBodyPublisher != null) {
					for (Entry<String, List<String>> entry : requestFormMap.entrySet()) {
						for (String value : entry.getValue()) {
							multipartFormDataBodyPublisher.add(entry.getKey(), value);
						}
					}
				} else {
					Charset charset = Charset.forName(getCharset());
					StringBuilder sb = new StringBuilder();
					for (Entry<String, List<String>> entry : requestFormMap.entrySet()) {
						String key = URLEncoder.encode(entry.getKey(), charset);
						for (String value : entry.getValue()) {
							if (sb.length() > 0) {
								sb.append("&");
							}
							sb.append(key);
							sb.append("=");
							sb.append(URLEncoder.encode(value, charset));
						}
					}
					addBodyTypeHeader();
					return BodyPublishers.ofString(sb.toString(), charset);
				}
			}

			if (multipartFormDataBodyPublisher != null) {
				addBodyTypeHeader();
				return multipartFormDataBodyPublisher;
			}

		} catch (Exception ignore) {}

		return BodyPublishers.noBody();

	}

	// endregion

	// region コンテンツタイプを取得する

	/**
	 * コンテンツタイプを取得する
	 *
	 * @return  コンテンツタイプ
	 */
	protected String getContentType () {

		if (requestBodyText != null) {
			return requestBodyText.contentType;
		}

		if (requestBodyFile != null) {
			return requestBodyFile.contentType;
		}

		if (multipartFormDataBodyPublisher != null) {
			return multipartFormDataBodyPublisher.contentType();
		}

		if (!requestFormMap.isEmpty()) {
			return "application/x-www-form-urlencoded";
		}

		return "";

	}

	// endregion


	// region FilePart

	/**
	 * FilePart
	 */
	private static class FilePart {

		File value;

		String contentType;

	}

	// endregion

	// region TextPart

	/**
	 * TextPart
	 */
	private static class TextPart {

		String value;

		String contentType;

	}

	// endregion

}
