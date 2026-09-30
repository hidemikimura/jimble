package io.jimble.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * インスタンスの識別子（ホスト名）の取り方（要件 F-B-05）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * ホスト名を {@code InetAddress.getLocalHost()} で取っていた。あれは名前を名前解決するので、
 * ホスト名が {@code /etc/hosts} に無い macOS では <b>5 秒ほど止まる</b>。最初の
 * {@link BatchExecutor#parseArgs} がこれを踏み、<b>同時に起動したバッチが全員 5 秒待たされていた</b>。
 * </p>
 */
class BatchConfTest {

	@Test
	@DisplayName("F-B-05 ホスト名は名前解決を待たずに取れ、これまでの getLocalHost().getHostName() と同じ名前になる")
	void hostNameIsFastAndUnchanged () {

		/*
		 * ホスト名を引くのはこのテストだけ（プロセスで1回しか引かないので、ほかで先に引くと測れない）。
		 * 先に時間を測る。getLocalHost() は結果を 5 秒持つので、比べる方を先に呼ぶと
		 * 名前解決を待つ取り方でも速く見えてしまう
		 */
		long started = System.nanoTime();
		String id = BatchConf.schedulerId();
		long millis = (System.nanoTime() - started) / 1_000_000;

		assertFalse(id.isBlank(), "識別子が空です");
		assertTrue(millis < 2000, "ホスト名を取るのに %d ms かかりました（名前解決を待っている）".formatted(millis));

		// scheduler_id が上げる前と変わらない（変わると、同じホストが別のインスタンスに数えられる）
		String before;
		try {
			before = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException ex) {
			// 名前解決できない環境では、これまでは "unknown" だった（比べられない）
			return;
		}

		assertEquals(before, id);
		assertEquals(before, BatchConf.HostNames.withoutResolve());

	}

	@Test
	@DisplayName("F-B-05 名前解決をしない取り方だけでホスト名が取れる（getLocalHost まで下りない）")
	void resolvedWithoutNameService () {

		// schedulerId() はここで呼ばない（ホスト名はプロセスで1回だけ引くので、時間を測るテストの前に引いてしまう）
		assertNotEquals("unknown", BatchConf.HostNames.withoutResolve());

	}

	@Test
	@DisplayName("F-B-05 取れた最初の名前を使い、あとの取り方（遅いかもしれない）は呼ばない")
	void firstNameWins () {

		AtomicInteger laterCalls = new AtomicInteger();

		List<Supplier<String>> sources = List.of(
			() -> null
			, () -> "  "
			, () -> { throw new IllegalStateException("取れない"); }
			, () -> " host-a\n"
			, () -> { laterCalls.incrementAndGet(); return "host-b"; });

		assertEquals("host-a", BatchConf.HostNames.lookup(sources));
		assertEquals(0, laterCalls.get(), "名前が取れたのに、あとの取り方も呼んでいます");

	}

	@Test
	@DisplayName("F-B-05 どれでも取れなければ unknown")
	void unknownWhenNothingWorks () {

		assertEquals("unknown", BatchConf.HostNames.lookup(List.of(() -> null, () -> "")));

	}

}
