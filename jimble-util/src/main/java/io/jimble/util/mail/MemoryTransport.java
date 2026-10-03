package io.jimble.util.mail;

import java.util.ArrayList;
import java.util.List;

/**
 * 送らずにメモリに貯める（テスト用。D-263）
 *
 * <pre>{@code
 * MemoryTransport memory = new MemoryTransport();
 * Mailer.use(memory);
 * ...
 * assertEquals("申請が承認されました", memory.sent().get(0).subject());
 * Mailer.reset();
 * }</pre>
 *
 * <p>設定の {@code mail.transport = "memory"} は {@link #shared()} を使う。</p>
 */
public final class MemoryTransport implements MailTransport {

	/* 設定で選んだときに使うもの */
	private static final MemoryTransport SHARED = new MemoryTransport();

	/* 送ったもの */
	private final List<MailMessage> sent = new ArrayList<>();

	/**
	 * 設定（{@code mail.transport = "memory"}）で使うもの
	 *
	 * @return	共有の入れ物
	 */
	public static MemoryTransport shared () {

		return SHARED;

	}

	@Override
	public synchronized void send (MailMessage message) {

		sent.add(message);

	}

	/**
	 * 送ったもの
	 *
	 * @return	送った順
	 */
	public synchronized List<MailMessage> sent () {

		return List.copyOf(sent);

	}

	/**
	 * 捨てる
	 */
	public synchronized void clear () {

		sent.clear();

	}

}
