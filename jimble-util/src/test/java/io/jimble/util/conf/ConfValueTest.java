package io.jimble.util.conf;

import com.typesafe.config.ConfigFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfValueTest {

	@AfterEach
	void reset () {

		Conf.reload();

	}

	@Test
	@DisplayName("同じ設定のあいだは 1 回しか読まない")
	void readsOncePerConf () {

		Conf.replace(ConfigFactory.parseString("a.b = \"x\""));
		AtomicInteger reads = new AtomicInteger();
		ConfValue<String> value = ConfValue.of(c -> {
			reads.incrementAndGet();
			return c.getString("a.b", "");
		});

		assertEquals("x", value.get());
		assertEquals("x", value.get());
		assertEquals(1, reads.get());

	}

	@Test
	@DisplayName("replace / reload で差し替えたら読み直す（古い値が残らない）")
	void replacingTheConfigIsPickedUp () {

		ConfValue<Duration> value = ConfValue.of(c -> c.getDuration("a.timeout", Duration.ofMinutes(30)));

		Conf.replace(ConfigFactory.parseString("a.timeout = 5s"));
		assertEquals(Duration.ofSeconds(5), value.get());

		Conf.replace(ConfigFactory.parseString("a.timeout = 2m"));
		assertEquals(Duration.ofMinutes(2), value.get());

		Conf.replace(ConfigFactory.parseString(""));
		assertEquals(Duration.ofMinutes(30), value.get());

	}

	@Test
	@DisplayName("null も覚える（毎回読み直さない）")
	void nullIsAValue () {

		Conf.replace(ConfigFactory.parseString(""));
		AtomicInteger reads = new AtomicInteger();
		ConfValue<String> value = ConfValue.of(c -> {
			reads.incrementAndGet();
			return null;
		});

		assertNull(value.get());
		assertNull(value.get());
		assertEquals(1, reads.get());

	}

	@Test
	@DisplayName("読めなかったときは覚えず、毎回同じ例外を出す")
	void failuresAreNotRemembered () {

		Conf.replace(ConfigFactory.parseString("a.timeout = 30"));
		ConfValue<Duration> value = ConfValue.of(c -> c.getDuration("a.timeout", Duration.ZERO));

		assertThrows(IllegalStateException.class, value::get);
		assertThrows(IllegalStateException.class, value::get);

		Conf.replace(ConfigFactory.parseString("a.timeout = 30s"));
		assertEquals(Duration.ofSeconds(30), value.get());

	}

}
