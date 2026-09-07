package io.jimble.web.context;

import io.jimble.db.DBSticky;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * リクエストのスコープに sticky が乗っているか（要件 F-D-19）
 *
 * <p>
 * 移送元はリクエストのスコープに {@code DBSticky} を束ねていたが、
 * jimble には移していなかった。{@code DBSticky.sticky()} が常に false になり、
 * <b>読み取り用のレプリカがある構成では、登録した直後に一覧を引くと
 * いま入れたものが無い</b>ことがあった。黙って古い答えが返る類なので、
 * ここで固定しておく。
 * </p>
 */
class WebContextStickyTest {

	@Test
	@DisplayName("リクエストの外では sticky にならない")
	void outsideRequest () {

		assertFalse(DBSticky.sticky(), "リクエストの外なのに固定されている");

	}

	@Test
	@DisplayName("リクエストの中で書き込むと、以降の参照が固定される")
	void insideRequest () {

		try (WebContext context = Fakes.context("POST", "/posts")) {

			context.run(() -> {

				assertFalse(DBSticky.sticky(), "まだ書き込んでいない");

				// DB への書き込みが呼ぶもの
				DBSticky.updated();

				assertTrue(DBSticky.sticky(), "リクエストのスコープに sticky が乗っていない");

			});

		}

		// リクエストを抜ければ元に戻る
		assertFalse(DBSticky.sticky(), "リクエストを抜けたのに固定されたまま");

	}

	@Test
	@DisplayName("リクエストごとに別々で、持ち越さない")
	void perRequest () {

		try (WebContext first = Fakes.context("POST", "/posts")) {
			first.run(DBSticky::updated);
		}

		try (WebContext second = Fakes.context("GET", "/posts")) {

			second.run(() -> assertFalse(DBSticky.sticky(), "別のリクエストへ持ち越されている"));

		}

	}

}
