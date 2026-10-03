package io.jimble.util.mail;

/**
 * 添付ファイル（D-263）
 *
 * @param fileName		ファイル名（日本語でもよい）
 * @param content		中身
 * @param contentType	種類（{@code application/pdf} など）
 */
public record MailAttachment(String fileName, byte[] content, String contentType) {

	/**
	 * 作る（形を確かめる）
	 *
	 * @param fileName		ファイル名
	 * @param content		中身
	 * @param contentType	種類（空なら {@code application/octet-stream}）
	 * @throws MailException	ファイル名が無い・改行を含む、種類の形が違う場合
	 */
	public MailAttachment {

		if (fileName == null || fileName.isBlank()) {
			throw new MailException("添付ファイルの名前がありません");
		}

		if (fileName.indexOf('\r') >= 0 || fileName.indexOf('\n') >= 0) {
			throw new MailException("添付ファイルの名前に改行は入れられません");
		}

		if (content == null) {
			throw new MailException("添付ファイルの中身がありません: " + fileName);
		}

		contentType = contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType.strip();

		if (!contentType.matches("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+")) {
			throw new MailException("添付ファイルの種類の形が違います: " + contentType);
		}

		content = content.clone();

	}

	@Override
	public byte[] content () {

		return content.clone();

	}

}
