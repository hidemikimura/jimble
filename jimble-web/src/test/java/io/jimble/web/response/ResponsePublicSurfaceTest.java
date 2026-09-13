package io.jimble.web.response;

import io.jimble.web.context.WebContext;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Response} の公開面（D-173。要件 NF-L-03）
 *
 * <p>
 * <b>1.0 のあとは直せない2つを、ここで固定する。</b>
 * </p>
 *
 * <ol>
 *   <li><b>{@code isEmpty()} を名乗らないこと。</b>{@code Response} は {@code Data}
 *       （→ {@code LinkedHashMap}）を継いでいるので、その名前は {@code Map#isEmpty()} を
 *       上書きし、<b>ログから中身が消える</b></li>
 *   <li><b>{@code send()} が自分自身を返すこと。</b>戻り値の型はメソッド記述子の一部なので、
 *       出したあとは 2.0 まで動かせない</li>
 * </ol>
 */
class ResponsePublicSurfaceTest {

	/**
	 * 本文を組み立てていない Response に put した中身が、ログに出ること
	 *
	 * <p>
	 * <b>ここが壊れると、静かに壊れる。</b>例外も出ないし、応答も変わらない。
	 * 変わるのは「あとから何が起きたか調べるとき」だけである。
	 * </p>
	 *
	 * <p>ここで固定していないこと：{@code summary()} の書式そのもの。</p>
	 */
	@Test
	@DisplayName("本文がまだ無い Response でも、put した中身がログに出る")
	void summaryKeepsEntries () {

		WebContext context = Fakes.context("GET", "/x");
		Response response = context.response();

		response.put("id", 10L);
		response.put("name", "田中");

		assertFalse(response.hasBody(), "本文はまだ組み立てていない");
		assertFalse(response.isEmpty(), "Map としては空ではない");

		String summary = response.summary();

		assertTrue(summary.contains("id"), "put した項目がログから消えている: " + summary);
		assertTrue(summary.contains("name"), "put した項目がログから消えている: " + summary);

	}

	/** 本文を組み立てたら {@code hasBody()} が true になること */
	@Test
	@DisplayName("本文を組み立てたら hasBody() が true になる")
	void hasBodyTurnsTrue () {

		WebContext context = Fakes.context("GET", "/x");
		Response response = context.response();

		assertFalse(response.hasBody());

		response.text("こんにちは");

		assertTrue(response.hasBody(), "text() で組み立てたのに false のまま");

	}

	/**
	 * 引数無しの {@code send()} が自分自身を返すこと
	 *
	 * <p>
	 * 引数ありの {@code send(...)} 13 個はすべて {@code Response} を返す。
	 * <b>ここだけ {@code Object} の {@code null} だった</b>ので、連鎖が切れていた。
	 * </p>
	 */
	@Test
	@DisplayName("send() は自分自身を返す（引数ありの send と揃える）")
	void sendReturnsSelf () {

		WebContext context = Fakes.context("GET", "/x");
		Response response = context.response();

		Response returned = response.text("ok").send();

		assertSame(response, returned, "send() が自分自身を返していない");

	}

	/** 送信済みのあとに呼んでも、自分自身が返ること（null を返さない） */
	@Test
	@DisplayName("送信済みのあとの send() も自分自身を返す")
	void sendAfterSentReturnsSelf () {

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();
		WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", "/x"), sink);
		Response response = context.response();

		response.text("ok").send();

		assertTrue(sink.isSent(), "1回目で送信済みになっていない（この経路を通っていない）");
		assertSame(response, response.send(), "2回目の send() が null を返している");

	}

}
