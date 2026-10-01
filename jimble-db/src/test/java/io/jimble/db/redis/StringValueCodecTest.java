package io.jimble.db.redis;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.codec.Kryo5Codec;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Redis に文字列として読み書きする（D-208）
 *
 * <p>
 * Redisson の既定（Kryo5Codec）は、読むときに<b>何のクラスでも作っていた</b>。
 * 書くのはただの UTF-8 にし、読むときは 2.2.2 までに Kryo で書いた<b>文字列だけ</b>を読み替える。
 * </p>
 */
class StringValueCodecTest {

	/** 作られたら数える（Kryo が作ってしまわないかを見る） */
	public static final class Gadget {

		static final AtomicInteger CREATED = new AtomicInteger();

		public String command = "rm -rf /";

		public Gadget () {
			CREATED.incrementAndGet();
		}

	}

	private static byte[] kryo (Object value) throws Exception {

		ByteBuf buf = new Kryo5Codec().getValueEncoder().encode(value);
		byte[] bytes = new byte[buf.readableBytes()];
		buf.readBytes(bytes);
		buf.release();
		return bytes;

	}

	private static Object read (byte[] bytes) throws Exception {

		return StringValueCodec.INSTANCE.getValueDecoder().decode(Unpooled.wrappedBuffer(bytes), null);

	}

	@Test
	@DisplayName("書くのはただの UTF-8")
	void writesPlainUtf8 () throws Exception {

		ByteBuf buf = StringValueCodec.INSTANCE.getValueEncoder().encode("日本語 {\"a\":1}");

		assertEquals("日本語 {\"a\":1}", buf.toString(StandardCharsets.UTF_8));
		buf.release();

	}

	@Test
	@DisplayName("ただの UTF-8 は、そのまま読む")
	void readsPlainUtf8 () throws Exception {

		for (String value : List.of("", "a", "hello", "{\"x\":1}", "日本語のセッション")) {
			assertEquals(value, read(value.getBytes(StandardCharsets.UTF_8)));
		}

	}

	@Test
	@DisplayName("2.2.2 までに Kryo で書いた文字列も読める（上げた日にセッションもキャッシュも切れない）")
	void readsLegacyKryoStrings () throws Exception {

		for (String value : List.of("", "a", "__auth_id", "{\"x\":1}", "日本語のセッション", "x".repeat(10_000))) {
			assertEquals(value, read(kryo(value)), "読み替えられません: " + value);
		}

	}

	@Test
	@DisplayName("Kryo で書いた文字列以外のものは、クラスを作らずにただの UTF-8 として読む")
	void neverInstantiatesOtherClasses () throws Exception {

		int before = Gadget.CREATED.get();
		byte[] payload = kryo(new Gadget());
		Gadget.CREATED.set(before);

		// 対照：Redisson の既定（Kryo5Codec）で読むと、作ってしまう（2.2.2 まで）
		Object made = new Kryo5Codec().getValueDecoder().decode(Unpooled.wrappedBuffer(payload), null);
		assertInstanceOf(Gadget.class, made);
		assertEquals(before + 1, Gadget.CREATED.get());
		Gadget.CREATED.set(before);

		Object value = read(payload);

		assertInstanceOf(String.class, value);
		assertEquals(before, Gadget.CREATED.get(), "Redis に置かれた値から、クラスを作っています");

		ArrayList<String> list = new ArrayList<>(List.of("a"));
		assertInstanceOf(String.class, read(kryo(list)));

	}

}
