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

		assertEquals("local", args.env());
		assertEquals("app.batch.Rss", args.className());
		assertEquals("42", args.cliArgs().getString("site_id"));
		assertEquals(42L, args.cliArgs().getLong("site_id"), "型付きで取れる");
		assertTrue(args.cliArgs().getBoolean("dry_run"));
		assertEquals(4, args.cliArgsList().size());

	}

	@Test
	@DisplayName("= が無い引数はそのまま並びに残る")
	void parseWithoutEquals () {

		BatchArgs args = BatchExecutor.parseArgs(new String[]{ "--verbose", "class=x" });

		assertEquals(List.of("--verbose", "class=x"), args.cliArgsList());
		assertTrue(args.cliArgs().isEmpty());

	}

	@Test
	@DisplayName("値に = が入っていても壊れない")
	void parseValueWithEquals () {

		BatchArgs args = BatchExecutor.parseArgs(new String[]{ "query=a=1&b=2" });

		assertEquals("a=1&b=2", args.cliArgs().getString("query"));

	}

	@Test
	@DisplayName("引数が無くても落ちない")
	void parseEmpty () {

		assertNull(BatchExecutor.parseArgs(null).className());
		assertNull(BatchExecutor.parseArgs(new String[0]).className());

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

		assertFalse(new BatchArgs().uid().equals(new BatchArgs().uid()));

	}

	@Test
	@DisplayName("スケジューラ ID が入る")
	void schedulerId () {

		/*
		 * 移送元は MAC アドレスを使っていた。コンテナでは起動のたびに変わり、
		 * 同じ値が別のホストで出ることもある。
		 */
		BatchArgs args = BatchExecutor.parseArgs(new String[]{ "class=x" });

		assertNotNull(args.schedulerId());
		assertFalse(args.schedulerId().isEmpty());
		assertFalse(args.schedulerId().matches("([0-9A-F]{2}:){5}[0-9A-F]{2}")
			, "MAC アドレスが入っている: " + args.schedulerId());

	}

	/**
	 * 「未設定」の表し方が1つであること（D-173）
	 *
	 * <p>
	 * <b>ここは3通りに割れていた。</b>{@code settings} は {@code null}、
	 * {@code schedulerId} は空文字、{@code cliArgs} は空の {@code Data}——
	 * 読む側はどれで書かれているかを覚えて分岐することになり、
	 * <b>片方を忘れても例外にはならない</b>（黙って別の道を通る）。
	 * </p>
	 *
	 * <p>ここで固定していないこと：{@code env} と {@code className} と {@code cron} は null のままである
	 * （「文字列が無い」ことに空文字とは別の意味があり、空文字にすると区別が消える）。</p>
	 */
	@Test
	@DisplayName("入れ物は必ず空で存在する（null を返さない）")
	void emptyContainersAreNeverNull () {

		BatchArgs args = new BatchArgs();

		assertNotNull(args.settings(), "settings が null");
		assertTrue(args.settings().isEmpty(), "settings が空でない");

		assertNotNull(args.cliArgs(), "cliArgs が null");
		assertTrue(args.cliArgs().isEmpty(), "cliArgs が空でない");

		assertNotNull(args.cliArgsList(), "cliArgsList が null");
		assertTrue(args.cliArgsList().isEmpty(), "cliArgsList が空でない");

		assertNotNull(args.schedulerId(), "schedulerId が null");

	}

	/** null を渡しても入れ物が消えないこと */
	@Test
	@DisplayName("null を渡しても空の入れ物になる")
	void nullBecomesEmpty () {

		BatchArgs args = new BatchArgs();

		args.settings(null);
		args.cliArgs(null);
		args.schedulerId(null);

		assertNotNull(args.settings());
		assertTrue(args.settings().isEmpty());
		assertNotNull(args.cliArgs());
		assertEquals("", args.schedulerId());

	}

	/**
	 * 引数の一覧が外から書き換えられないこと
	 *
	 * <p><b>返した {@code List} の中身は、走っている最中の記録である。</b></p>
	 */
	@Test
	@DisplayName("cliArgsList は外から書き換えられない")
	void cliArgsListIsReadOnly () {

		BatchArgs args = BatchExecutor.parseArgs(new String[]{ "class=x" });

		assertThrows(UnsupportedOperationException.class, () -> args.cliArgsList().clear());

	}

}
