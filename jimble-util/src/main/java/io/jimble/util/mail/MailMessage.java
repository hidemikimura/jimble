package io.jimble.util.mail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 送るメール（D-263）
 *
 * <pre>{@code
 * Mailer.send(new MailMessage()
 *     .to("hanako@example.co.jp", "山田 花子")
 *     .subject("申請が承認されました")
 *     .text("山田 様\n\n経費の申請（No.123）が承認されました。"));
 * }</pre>
 *
 * <p>
 * From を書かなければ設定（{@code mail.from} / {@code mail.from_name}）を使う。
 * {@code text} と {@code html} の両方を入れると、メールソフトが選べる形（multipart/alternative）で送る。
 * <b>HTML だけのメールは迷惑メールに入りやすい</b>ので、HTML を送るときも {@code text} を入れること。
 * </p>
 */
public final class MailMessage {

	/* 自分で決めさせないヘッダ（jimble が書く） */
	private static final Set<String> RESERVED = Set.of(
		"from", "to", "cc", "bcc", "reply-to", "subject", "date", "message-id"
		, "mime-version", "content-type", "content-transfer-encoding", "content-disposition");

	private MailAddress from;

	private final List<MailAddress> to = new ArrayList<>();

	private final List<MailAddress> cc = new ArrayList<>();

	private final List<MailAddress> bcc = new ArrayList<>();

	private final List<MailAddress> replyTo = new ArrayList<>();

	private String subject = "";

	private String text;

	private String html;

	private final List<MailAttachment> attachments = new ArrayList<>();

	private final Map<String, String> headers = new LinkedHashMap<>();

	// region 宛先

	/**
	 * 差出人
	 *
	 * @param address	メールアドレス
	 * @param name		表示する名前
	 * @return	このメール
	 */
	public MailMessage from (String address, String name) {

		this.from = MailAddress.of(address, name);
		return this;

	}

	/**
	 * 差出人
	 *
	 * @param address	メールアドレス
	 * @return	このメール
	 */
	public MailMessage from (String address) {

		return from(address, "");

	}

	/**
	 * 宛先を足す
	 *
	 * @param address	メールアドレス
	 * @param name		表示する名前
	 * @return	このメール
	 */
	public MailMessage to (String address, String name) {

		to.add(MailAddress.of(address, name));
		return this;

	}

	/**
	 * 宛先を足す
	 *
	 * @param address	メールアドレス
	 * @return	このメール
	 */
	public MailMessage to (String address) {

		return to(address, "");

	}

	/**
	 * Cc を足す
	 *
	 * @param address	メールアドレス
	 * @return	このメール
	 */
	public MailMessage cc (String address) {

		cc.add(MailAddress.of(address));
		return this;

	}

	/**
	 * Bcc を足す（ヘッダには書かず、宛先にだけ入れる）
	 *
	 * @param address	メールアドレス
	 * @return	このメール
	 */
	public MailMessage bcc (String address) {

		bcc.add(MailAddress.of(address));
		return this;

	}

	/**
	 * 返信先を足す
	 *
	 * @param address	メールアドレス
	 * @return	このメール
	 */
	public MailMessage replyTo (String address) {

		replyTo.add(MailAddress.of(address));
		return this;

	}

	// endregion

	// region 中身

	/**
	 * 件名
	 *
	 * @param subject	件名（日本語でもよい。改行は入れられない）
	 * @return	このメール
	 */
	public MailMessage subject (String subject) {

		if (subject != null && (subject.indexOf('\r') >= 0 || subject.indexOf('\n') >= 0)) {
			throw new MailException("件名に改行は入れられません");
		}

		this.subject = subject == null ? "" : subject;
		return this;

	}

	/**
	 * 本文（テキスト）
	 *
	 * @param text	本文
	 * @return	このメール
	 */
	public MailMessage text (String text) {

		this.text = text;
		return this;

	}

	/**
	 * 本文（HTML）
	 *
	 * @param html	本文（利用者が入れた値は、テンプレートでエスケープしてから入れる）
	 * @return	このメール
	 */
	public MailMessage html (String html) {

		this.html = html;
		return this;

	}

	/**
	 * 添付する
	 *
	 * @param fileName		ファイル名
	 * @param content		中身
	 * @param contentType	種類（{@code application/pdf} など）
	 * @return	このメール
	 */
	public MailMessage attach (String fileName, byte[] content, String contentType) {

		attachments.add(new MailAttachment(fileName, content, contentType));
		return this;

	}

	/**
	 * ヘッダを足す（{@code List-Unsubscribe} など）
	 *
	 * @param name	名前（From や Subject など、jimble が書くものは決められない）
	 * @param value	値（ASCII。改行は入れられない）
	 * @return	このメール
	 */
	public MailMessage header (String name, String value) {

		if (name == null || !name.matches("[!-9;-~]+")) {
			throw new MailException("ヘッダの名前の形が違います: " + name);
		}

		if (RESERVED.contains(name.toLowerCase(Locale.ROOT))) {
			throw new MailException("このヘッダは jimble が書きます（メソッドで決めてください）: " + name);
		}

		if (value == null || value.length() > 900 || !value.matches("[\\t\\x20-\\x7e]*")) {
			throw new MailException("ヘッダの値は、改行の無い 900 文字までの ASCII にしてください: " + name);
		}

		headers.put(name, value);
		return this;

	}

	// endregion

	// region 読む

	/**
	 * 差出人
	 *
	 * @return	差出人（決めていなければ null）
	 */
	public MailAddress from () {

		return from;

	}

	/**
	 * 宛先
	 *
	 * @return	宛先
	 */
	public List<MailAddress> to () {

		return Collections.unmodifiableList(to);

	}

	/**
	 * Cc
	 *
	 * @return	Cc
	 */
	public List<MailAddress> cc () {

		return Collections.unmodifiableList(cc);

	}

	/**
	 * Bcc
	 *
	 * @return	Bcc
	 */
	public List<MailAddress> bcc () {

		return Collections.unmodifiableList(bcc);

	}

	/**
	 * 返信先
	 *
	 * @return	返信先
	 */
	public List<MailAddress> replyTo () {

		return Collections.unmodifiableList(replyTo);

	}

	/**
	 * 件名
	 *
	 * @return	件名
	 */
	public String subject () {

		return subject;

	}

	/**
	 * 本文（テキスト）
	 *
	 * @return	本文（無ければ null）
	 */
	public String text () {

		return text;

	}

	/**
	 * 本文（HTML）
	 *
	 * @return	本文（無ければ null）
	 */
	public String html () {

		return html;

	}

	/**
	 * 添付ファイル
	 *
	 * @return	添付ファイル
	 */
	public List<MailAttachment> attachments () {

		return Collections.unmodifiableList(attachments);

	}

	/**
	 * 足したヘッダ
	 *
	 * @return	ヘッダ
	 */
	public Map<String, String> headers () {

		return Collections.unmodifiableMap(headers);

	}

	/**
	 * 送る相手（To・Cc・Bcc。重なりは除く）
	 *
	 * @return	送る相手
	 */
	public List<MailAddress> recipients () {

		Map<String, MailAddress> all = new LinkedHashMap<>();

		for (List<MailAddress> list : List.of(to, cc, bcc)) {
			for (MailAddress address : list) {
				all.putIfAbsent(address.address().toLowerCase(Locale.ROOT), address);
			}
		}

		return List.copyOf(all.values());

	}

	// endregion

	/**
	 * MIME の形にする（生のメールを受け付ける送り先や、テストに使う）
	 *
	 * @return	MIME の形のメール（CRLF）
	 * @throws MailException	差出人・宛先・本文が無い場合
	 */
	public byte[] toMime () {

		return MimeWriter.write(this, check(null));

	}

	/**
	 * From を決めていなければ、設定の差出人を入れる
	 */
	void fillFrom (MailAddress sender) {

		if (from == null) {
			from = sender;
		}

	}

	/**
	 * 送れる形か確かめる
	 *
	 * @param defaultFrom	From を決めていないときの差出人（無ければ null）
	 * @return	差出人
	 * @throws MailException	宛先・差出人・本文が無い場合
	 */
	MailAddress check (MailAddress defaultFrom) {

		MailAddress sender = from != null ? from : defaultFrom;

		if (sender == null) {
			throw new MailException("差出人がありません（MailMessage.from(...) か、設定の mail.from を書いてください）");
		}

		if (to.isEmpty() && cc.isEmpty() && bcc.isEmpty()) {
			throw new MailException("宛先がありません");
		}

		if (text == null && html == null) {
			throw new MailException("本文がありません（text(...) か html(...)）");
		}

		return sender;

	}

}
