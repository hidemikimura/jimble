package io.jimble.web.response;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * 小さく始めて、足りなければ伸びるバッファつきの出力（D-200）
 *
 * <h2>なぜ BufferedOutputStream ではないのか</h2>
 * <p>
 * {@code BufferedOutputStream} は、<b>作った時点で指定の大きさの配列を取る</b>。
 * 2.1.5 まで、JSON を返すたびに {@code io.buffer_size}（当時の既定 256KiB）を取っていたので、
 * <b>100 バイトの JSON でも1回に 350KB ほどメモリを使っていた</b>。
 * </p>
 *
 * <p>
 * かといって小さく固定すると、大きな JSON が遅くなる（helidon の出力のバッファは 4KiB しかなく、
 * 細かく書くほど送り出しの回数が増える）。
 * <b>8KiB で始め、足りなければ倍にしていき、{@code io.buffer_size}（既定 64KiB）まで伸ばす</b>。
 * 小さな JSON は小さな配列で済み、大きな JSON は上限の大きさでまとめて書く。
 * </p>
 */
final class GrowingBufferedOutputStream extends OutputStream {

	/** 始めの大きさ */
	static final int INITIAL_SIZE = 8 * 1024;

	/* 書き出し先を開く（最初に溢れたときに1回だけ。D-279） */
	private final java.util.function.Supplier<OutputStream> opener;

	/* 溢れずに閉じたときの送り先（まとめて1回で） */
	private final IOConsumer whole;

	/* 書き出し先（開くまで null） */
	private OutputStream out;

	/* 閉じたか（書き手が閉じたあと、try-with-resources がもう一度閉じる） */
	private boolean closed = false;

	/* 伸ばす上限 */
	private final int maxSize;

	/* バッファ */
	private byte[] buf;

	/* バッファに入っているバイト数 */
	private int count = 0;

	/**
	 * コンストラクタ
	 *
	 * @param out		書き出し先
	 * @param maxSize	伸ばす上限（バイト）
	 */
	GrowingBufferedOutputStream (OutputStream out, int maxSize) {

		this(() -> out, null, maxSize);

		// 渡されたものは、もう開いている
		this.out = out;

	}

	/**
	 * コンストラクタ（書き出し先は、溢れたときに開く）
	 *
	 * <p>
	 * <b>溢れずに閉じたら、書き出し先を開かずに {@code whole} へまとめて渡す</b>（D-279）。
	 * 長さが分かるので、小さい JSON を圧縮せず、Content-Length も付けられる
	 * （開いてから書くと、長さが分からないまま圧縮するかを決めることになる）。
	 * </p>
	 *
	 * @param opener	書き出し先を開く
	 * @param whole		溢れずに閉じたときの送り先
	 * @param maxSize	伸ばす上限
	 */
	GrowingBufferedOutputStream (java.util.function.Supplier<OutputStream> opener, IOConsumer whole, int maxSize) {

		if (maxSize <= 0) {
			throw new IllegalArgumentException("maxSize は 1 以上にしてください: " + maxSize);
		}

		this.opener = opener;
		this.whole = whole;
		this.maxSize = maxSize;
		this.buf = new byte[Math.min(INITIAL_SIZE, maxSize)];

	}

	/** まとめて送る */
	@FunctionalInterface
	interface IOConsumer {

		void accept (byte[] bytes) throws IOException;

	}

	/**
	 * 書き出し先（無ければ開く）
	 */
	private OutputStream out () {

		if (out == null) {
			out = opener.get();
		}

		return out;

	}

	/**
	 * いまのバッファの大きさ（テスト用）
	 *
	 * @return	バイト
	 */
	int capacity () {

		return buf.length;

	}

	@Override
	public void write (int b) throws IOException {

		if (count >= buf.length) {
			makeRoom(1);
		}

		buf[count++] = (byte) b;

	}

	@Override
	public void write (byte[] b, int off, int len) throws IOException {

		java.util.Objects.checkFromIndexSize(off, len, b.length);

		if (len > buf.length - count) {
			makeRoom(len);
		}

		// 上限まで伸ばしても入らないほど大きいものは、溜めずにそのまま書く
		if (len > buf.length - count) {
			out().write(b, off, len);
			return;
		}

		System.arraycopy(b, off, buf, count, len);
		count += len;

	}

	/**
	 * {@code len} バイト入るようにする。<b>上限までは伸ばし、上限なら書き出して空ける</b>
	 *
	 * @param len	入れたいバイト数
	 * @throws IOException	書き出しの失敗
	 */
	private void makeRoom (int len) throws IOException {

		if (buf.length < maxSize) {

			long need = (long) count + len;
			long size = buf.length;
			while (size < need && size < maxSize) {
				size *= 2;
			}

			buf = Arrays.copyOf(buf, (int) Math.min(size, maxSize));

			if (len <= buf.length - count) {
				return;
			}

		}

		flushBuffer();

	}

	/**
	 * 溜めたものを書き出す（下の flush は呼ばない）
	 *
	 * @throws IOException	書き出しの失敗
	 */
	private void flushBuffer () throws IOException {

		if (count > 0) {
			out().write(buf, 0, count);
			count = 0;
		}

	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>まだ開いていなければ何もしない（閉じるときにまとめて送る）。</p>
	 */
	@Override
	public void flush () throws IOException {

		if (out == null) {
			return;
		}

		flushBuffer();
		out.flush();

	}

	@Override
	public void close () throws IOException {

		if (closed) {
			return;
		}

		closed = true;

		if (out == null) {
			// 溢れなかった。開かずにまとめて送る
			whole.accept(Arrays.copyOf(buf, count));
			count = 0;
			return;
		}

		try {
			flushBuffer();
		} finally {
			out.close();
		}

	}

}
