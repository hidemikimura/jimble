package io.jimble.db.redis;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.util.Pool;
import io.netty.buffer.ByteBuf;
import org.redisson.client.codec.BaseCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

import java.nio.charset.StandardCharsets;

/**
 * Redis に文字列として読み書きする（D-208）
 *
 * <h2>なぜ Redisson の既定を使わないのか</h2>
 * <p>
 * <b>Redisson の既定は {@code Kryo5Codec} で、読めるクラスを絞っていない</b>
 * （{@code setRegistrationRequired(false)}。Throwable などは Java のシリアライズで読む）。
 * jimble は文字列しか入れないのに、<b>読むときは何のクラスでも作っていた</b>。
 * Redis に書き込める相手（同じネットワーク、SSRF、Redis を共有する別のアプリ）は、
 * セッションを偽造したり（{@code __auth_id} を入れる）、ガジェットを仕込んだりできた。
 * </p>
 *
 * <h2>書く・読む</h2>
 * <ul>
 *   <li><b>書くのはただの UTF-8</b>（{@link StringCodec} と同じ）</li>
 *   <li><b>読むときは、2.2.2 までに Kryo で書いた文字列だけ</b>を読み替える。上げた日に、
 *       Redis のセッションが全部切れたり、キャッシュが壊れた値を返したりしないため。
 *       読み替えに使う Kryo は<b>登録したクラスしか読まない</b>（既定で登録されるのは文字列と基本型だけ）。
 *       文字列にならなければ、ただの UTF-8 として読む</li>
 * </ul>
 *
 * <p>
 * Kryo の文字列は必ず {@code 0x03} で始まる。UTF-8 の文字列がその字（制御文字 ETX）で始まることは、まず無い。
 * </p>
 */
public final class StringValueCodec extends BaseCodec {

	/** 使い回す */
	public static final StringValueCodec INSTANCE = new StringValueCodec();

	/** Kryo で書いた文字列の先頭の1バイト（文字列のクラスの番号） */
	static final byte KRYO_STRING = 0x03;

	/* 古い値を読み替える Kryo（スレッドごとに持たず、使い回す） */
	private static final Pool<Kryo> LEGACY = new Pool<>(true, false, 16) {
		@Override
		protected Kryo create () {
			Kryo kryo = new Kryo();
			// 登録したクラスしか読まない。既定で登録されているのは基本型と String だけ
			kryo.setRegistrationRequired(true);
			kryo.setReferences(false);
			return kryo;
		}
	};

	/* 書く */
	private final Encoder encoder = StringCodec.INSTANCE.getValueEncoder();

	/* 読む */
	private final Decoder<Object> decoder = (buf, state) -> decode(buf);

	private StringValueCodec () {}

	@Override
	public Decoder<Object> getValueDecoder () {

		return decoder;

	}

	@Override
	public Encoder getValueEncoder () {

		return encoder;

	}

	/**
	 * 読む
	 *
	 * @param buf	バイト列
	 * @return	文字列
	 */
	static String decode (ByteBuf buf) {

		byte[] bytes = new byte[buf.readableBytes()];
		buf.readBytes(bytes);

		return decode(bytes);

	}

	/**
	 * 読む
	 *
	 * @param bytes	バイト列
	 * @return	文字列
	 */
	static String decode (byte[] bytes) {

		if (bytes.length >= 2 && bytes[0] == KRYO_STRING) {
			String legacy = legacyString(bytes);
			if (legacy != null) {
				return legacy;
			}
		}

		return new String(bytes, StandardCharsets.UTF_8);

	}

	/**
	 * 2.2.2 までに Kryo で書いた文字列として読む
	 *
	 * @param bytes	バイト列
	 * @return	文字列。そう読めなければ null
	 */
	private static String legacyString (byte[] bytes) {

		Kryo kryo = LEGACY.obtain();

		try (Input input = new Input(bytes)) {

			Object value = kryo.readClassAndObject(input);

			// 文字列で、ちょうど最後まで読めたときだけ
			return value instanceof String text && input.position() == bytes.length ? text : null;

		} catch (RuntimeException ex) {
			return null;
		} finally {
			LEGACY.free(kryo);
		}

	}

}
