package io.jimble.batch.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * cron の解釈（要件 F-B-04）
 */
class CronScheduleTest {

	/* 判定の基準にする時刻 */
	private static final ZonedDateTime BASE =
		ZonedDateTime.of(2026, 9, 6, 12, 30, 15, 0, ZoneId.of("Asia/Tokyo"));

	@Test
	@DisplayName("毎分")
	void everyMinute () {

		CronSchedule cron = CronSchedule.parse("* * * * *");

		assertNotNull(cron);
		assertEquals(ZonedDateTime.of(2026, 9, 6, 12, 31, 0, 0, ZoneId.of("Asia/Tokyo"))
			, cron.nextExecution(BASE));

	}

	@Test
	@DisplayName("毎日 03:10")
	void daily () {

		CronSchedule cron = CronSchedule.parse("10 3 * * *");

		assertEquals(ZonedDateTime.of(2026, 9, 7, 3, 10, 0, 0, ZoneId.of("Asia/Tokyo"))
			, cron.nextExecution(BASE));

	}

	@Test
	@DisplayName("5分ごと")
	void everyFiveMinutes () {

		CronSchedule cron = CronSchedule.parse("*/5 * * * *");

		assertEquals(ZonedDateTime.of(2026, 9, 6, 12, 35, 0, 0, ZoneId.of("Asia/Tokyo"))
			, cron.nextExecution(BASE));

	}

	@Test
	@DisplayName("曜日を指定する")
	void dayOfWeek () {

		// 月曜の 09:00。2026-09-06 は日曜
		CronSchedule cron = CronSchedule.parse("0 9 * * 1");

		assertEquals(ZonedDateTime.of(2026, 9, 7, 9, 0, 0, 0, ZoneId.of("Asia/Tokyo"))
			, cron.nextExecution(BASE));

	}

	@Test
	@DisplayName("読めない cron は null")
	void invalid () {

		assertNull(CronSchedule.parse(null));
		assertNull(CronSchedule.parse(""));
		assertNull(CronSchedule.parse("   "));
		assertNull(CronSchedule.parse("毎分"));
		assertNull(CronSchedule.parse("* * *"), "項目が足りない");
		assertNull(CronSchedule.parse("99 * * * *"), "範囲外");

	}

	@Test
	@DisplayName("前後の空白は落とす")
	void trims () {

		assertNotNull(CronSchedule.parse("  0 3 * * *  "));
		assertEquals("0 3 * * *", CronSchedule.parse("  0 3 * * *  ").expression());

	}

}
