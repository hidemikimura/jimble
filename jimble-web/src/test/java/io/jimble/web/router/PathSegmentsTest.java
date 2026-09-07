package io.jimble.web.router;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * PathSegments のテスト
 */
class PathSegmentsTest {

	@Test
	@DisplayName("T-17 %2F はセグメント区切りにならない")
	void encodedSlashStaysInsideSegment () {

		PathSegments segments = PathSegments.ofRawPath("/search/a%2Fb");

		assertEquals(2, segments.size(), "3セグメントに割れてはいけない");
		assertEquals("search", segments.get(0));
		assertEquals("a/b", segments.get(1));

	}

	@Test
	@DisplayName("T-18 末尾スラッシュの有無で結果が変わらない")
	void trailingSlashIgnored () {

		assertEquals(
			PathSegments.ofRawPath("/a/b").toString()
			, PathSegments.ofRawPath("/a/b/").toString());

		assertEquals(2, PathSegments.ofRawPath("/a//b").size(), "連続スラッシュも空セグメントを落とす");

	}

	@Test
	@DisplayName("T-19 日本語のパーセントエンコードをデコードする")
	void decodesJapanese () {

		PathSegments segments = PathSegments.ofRawPath("/search/%E3%81%A6%E3%81%99%E3%81%A8");

		assertEquals(2, segments.size());
		assertEquals("てすと", segments.get(1));

	}

	@Test
	@DisplayName("\"+\" は空白に変換しない（フォームの規則を適用しない）")
	void plusIsNotSpace () {

		assertEquals("a+b", PathSegments.ofRawPath("/a+b").get(0));

	}

	@Test
	@DisplayName("壊れたエスケープは例外にせずそのまま通す")
	void brokenEscapeIsPassedThrough () {

		assertEquals("a%zz", PathSegments.ofRawPath("/a%zz").get(0));
		assertEquals("a%", PathSegments.ofRawPath("/a%").get(0));

	}

	@Test
	@DisplayName("ルートパスはセグメント0個")
	void rootPath () {

		assertEquals(0, PathSegments.ofRawPath("/").size());
		assertEquals(0, PathSegments.ofRawPath("").size());

	}

	@Test
	@DisplayName("指定位置以降を連結できる")
	void joinFrom () {

		PathSegments segments = PathSegments.ofRawPath("/a/b/c");

		assertEquals("a/b/c", segments.joinFrom(0));
		assertEquals("b/c", segments.joinFrom(1));
		assertEquals("", segments.joinFrom(3));

	}

	@Test
	@DisplayName("パターンはデコードしない")
	void patternIsNotDecoded () {

		PathSegments segments = PathSegments.ofPattern("/users/{id}/*");

		assertEquals(3, segments.size());
		assertEquals("{id}", segments.get(1));
		assertEquals("*", segments.get(2));

	}

}
