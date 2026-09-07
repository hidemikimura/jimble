package io.jimble.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * コマンドライン引数の解析（要件 F-B-03）
 */
class BatchArgsTest {

	@Test
	@DisplayName("env と class を取り出し、残りは Data に入る")
	void parse () {

		BatchArgs args = BatchExecutor.parseArgs(new String[]{
			"env=local", "class=app.batch.Rss", "site_id=42", "dry_run=true"
		});

		assertEquals("local", args.env);
		assertEquals("app.batch.Rss", args.className);
		assertEquals("42", args.cliArgs.getString("site_id"));
		assertEquals(42L, args.cliArgs.getLong("site_id"), "型付きで取れる");
		assertTrue(args.cliArgs.getBoolean("dry_run"));
		assertEquals(4, args.cliArgsList.size());

	}

	@Test
	@DisplayName("= が無い引数はそのまま並びに残る")
	void parseWithoutEquals () {

		BatchArgs args = BatchExecutor.parseArgs(new String[]{ "--verbose", "class=x" });

		assertEquals(List.of("--verbose", "class=x"), args.cliArgsList);
		assertTrue(args.cliArgs.isEmpty());

	}

	@Test
	@DisplayName("値に = が入っていても壊れない")
	void parseValueWithEquals () {

		BatchArgs args = BatchExecutor.parseArgs(new String[]{ "query=a=1&b=2" });

		assertEquals("a=1&b=2", args.cliArgs.getString("query"));

	}

	@Test
	@DisplayName("引数が無くても落ちない")
	void parseEmpty () {

		assertNull(BatchExecutor.parseArgs(null).className);
		assertNull(BatchExecutor.parseArgs(new String[0]).className);

	}

	@Test
	@DisplayName("D-79 env= がいまの環境と同じなら通る")
	void envMatching () {

		// 既定は local
		assertNotNull(BatchExecutor.parseArgs(new String[]{ "env=local", "class=x" }));

	}

	@Test
	@DisplayName("D-79 env= が食い違っていたら、その場で止める")
	void envMismatchThrows () {

		/*
		 * env= では環境は変わらない（設定はここに来る前に読み終わっている）。
		 * 黙って受けると「本番のつもりで local の設定で流していた」が起きる。
		 */
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class
			, () -> BatchExecutor.parseArgs(new String[]{ "env=prod", "class=x" }));

		assertTrue(ex.getMessage().contains("-Djimble.env=prod"), ex.getMessage());

	}

	@Test
	@DisplayName("uid は実行ごとに変わる")
	void uniqueUid () {

		assertFalse(new BatchArgs().uid.equals(new BatchArgs().uid));

	}

	@Test
	@DisplayName("スケジューラ ID が入る")
	void schedulerId () {

		/*
		 * 移送元は MAC アドレスを使っていた。コンテナでは起動のたびに変わり、
		 * 同じ値が別のホストで出ることもある。
		 */
		BatchArgs args = BatchExecutor.parseArgs(new String[]{ "class=x" });

		assertNotNull(args.schedulerId);
		assertFalse(args.schedulerId.isEmpty());
		assertFalse(args.schedulerId.matches("([0-9A-F]{2}:){5}[0-9A-F]{2}")
			, "MAC アドレスが入っている: " + args.schedulerId);

	}

}
