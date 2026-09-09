package io.jimble.web.request;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Accept が JSON を名指ししているかの判定（{@link Request#isJsonAccepted}）
 *
 * <p>
 * <b>ここは「付いているのに効かない」が起きやすい。</b>
 * 前は Accept を "," で割った断片を丸ごと {@code application/json} と比べていたので、
 * <b>{@code application/json;q=0.9} も {@code application/json, text/plain} の
 * 2つ目以降に空白があるものも false</b> だった。
 * </p>
 */
class AcceptJsonTest {

	// region 名指ししている

	@Test
	@DisplayName("そのまま書いてあれば true")
	void plain () {

		assertTrue(Request.isJsonAccepted("application/json"));
		assertTrue(Request.isJsonAccepted("text/javascript"));

	}

	@Test
	@DisplayName("q 値が付いていても true")
	void withQuality () {

		/*
		 * <b>これが false だったのがいちばん困る。</b>
		 * 「JSON がいいが、無ければ何でも」と丁寧に書いたクライアントほど
		 * この形になり、<b>丁寧に書いたほうが損をする</b>形になっていた
		 */
		assertTrue(Request.isJsonAccepted("application/json;q=0.9"));
		assertTrue(Request.isJsonAccepted("application/json; q=0.9, */*;q=0.1"));

	}

	@Test
	@DisplayName("並びの途中にあっても、前後に空白があっても true")
	void inList () {

		assertTrue(Request.isJsonAccepted("text/html, application/json"));
		assertTrue(Request.isJsonAccepted("text/html,  application/json  ,text/plain"));

	}

	@Test
	@DisplayName("大文字小文字は問わない")
	void ignoreCase () {

		assertTrue(Request.isJsonAccepted("Application/JSON"));

	}

	// endregion

	// region 名指ししていない

	@Test
	@DisplayName("q=0 は「要らない」なので false")
	void refused () {

		assertFalse(Request.isJsonAccepted("application/json;q=0"));
		assertFalse(Request.isJsonAccepted("text/html, application/json;q=0.0"));

	}

	@Test
	@DisplayName("*/* は true にしない")
	void anything () {

		/*
		 * <b>「何でもいい」であって「JSON がいい」ではない。</b>
		 * ここを true にすると、ビュー（画面）を返すルートが JSON を返すようになる——
		 * {@code curl} の既定も、ブラウザが画像を取りにくるときも「何でもいい」である
		 */
		assertFalse(Request.isJsonAccepted("*/*"));
		assertFalse(Request.isJsonAccepted("application/*"));

	}

	@Test
	@DisplayName("ブラウザの Accept は false")
	void browser () {

		assertFalse(Request.isJsonAccepted(
			"text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"));

	}

	@Test
	@DisplayName("似ているだけの型は false")
	void lookalike () {

		// JSON:API や problem+json は「JSON なら何でも」ではなく、その形を求めている
		assertFalse(Request.isJsonAccepted("application/vnd.api+json"));
		assertFalse(Request.isJsonAccepted("application/problem+json"));
		assertFalse(Request.isJsonAccepted("application/jsonl"));

	}

	@Test
	@DisplayName("無い・空・壊れていても落ちない")
	void broken () {

		assertFalse(Request.isJsonAccepted(null));
		assertFalse(Request.isJsonAccepted(""));
		assertFalse(Request.isJsonAccepted(",,,"));
		assertFalse(Request.isJsonAccepted(";q="));

		// 読めない q は「付いていない」と同じに扱う（それだけで断らない）
		assertTrue(Request.isJsonAccepted("application/json;q=たかい"));

	}

	// endregion

}
