package io.jimble.web.cors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CORS の既定と、あとから足せない口（D-173。要件 NF-S-05）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>CORS が緩いことは、動かしても分からない。</b>
 * 自分のサイトからは同じように動くし、ブラウザも何も言わない。
 * 違うのは<b>他人のサイトから叩けるかどうか</b>だけである。
 * </p>
 */
class CorsSafetyTest {

	/**
	 * Cookie 付きは既定で許さないこと
	 *
	 * <p>
	 * <b>既定が {@code true} だった。</b>{@code allAllowOrigin()} と並ぶと
	 * <b>任意のサイトが利用者のログイン状態のまま API を叩ける</b>——
	 * 受け取った origin をそのまま返す作りなので、
	 * ブラウザが {@code *} に掛けている制約も効かない。
	 * </p>
	 */
	@Test
	@DisplayName("Allow-Credentials の既定は false")
	void credentialsAreOffByDefault () {

		assertFalse(new Cors().allowCredentials(), "既定で Cookie 付きを許している");

	}

	/** 「どこからでも」と「Cookie 付き」が同時に立たないこと */
	@Test
	@DisplayName("allAllowOrigin() と allowCredentials(true) は同時に指定できない")
	void allOriginsWithCredentialsIsRefused () {

		assertThrows(IllegalStateException.class
			, () -> new Cors().allowCredentials(true).allAllowOrigin());

		assertThrows(IllegalStateException.class
			, () -> new Cors().allAllowOrigin().allowCredentials(true));

	}

	/** 片方ずつなら通ること */
	@Test
	@DisplayName("片方だけなら指定できる")
	void eachAloneIsFine () {

		assertTrue(new Cors().allAllowOrigin().matchOrigin("https://どこか.example"));
		assertTrue(new Cors().addAllowOrigin("https://example.com").allowCredentials(true).allowCredentials());

	}

	/**
	 * 一覧を外から足せないこと
	 *
	 * <p>
	 * <b>足せてしまうと、足したぶんが黙って無視される。</b>
	 * マッチャは {@code addAllowXxx} のときにしか作り直さないので、
	 * {@code allowedOrigins().add(...)} で入れた origin は<b>以後ずっと通らない</b>——
	 * 「許可したのに CORS で落ちる」の原因になる。
	 * </p>
	 */
	@Test
	@DisplayName("許可一覧は外から書き換えられない")
	void listsAreReadOnly () {

		Cors cors = new Cors().addAllowOrigin("https://example.com");

		assertThrows(UnsupportedOperationException.class
			, () -> cors.allowedOrigins().add("https://わるいところ.example"));
		assertThrows(UnsupportedOperationException.class
			, () -> cors.allowedHeaders().add("X-Anything"));
		assertThrows(UnsupportedOperationException.class
			, () -> cors.allowedMethods().add("DELETE"));
		assertThrows(UnsupportedOperationException.class
			, () -> cors.exposeHeaders().add("X-Anything"));

	}

	/** 正しい足し方なら効くこと */
	@Test
	@DisplayName("addAllowOrigin() で足したものは通る")
	void addedOriginMatches () {

		Cors cors = new Cors().addAllowOrigin("https://example.com");

		assertTrue(cors.matchOrigin("https://example.com"));
		assertFalse(cors.matchOrigin("https://わるいところ.example"));

		cors.addAllowOrigin("https://ふたつめ.example");

		assertTrue(cors.matchOrigin("https://ふたつめ.example"), "足したのに通らない（マッチャが作り直されていない）");

	}

	/** 既定の許可メソッド */
	@Test
	@DisplayName("既定の許可メソッドは GET / POST / OPTIONS の3つ")
	void defaultMethods () {

		assertEquals(3, new Cors().allowedMethods().size(), new Cors().allowedMethods().toString());

	}

}
