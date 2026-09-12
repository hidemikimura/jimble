package io.jimble.cli.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ProjectName} のテスト（要件 F-X-01）
 */
class ProjectNameTest {

	@Test
	@DisplayName("名前1つからパッケージ名と DB 名が決まる")
	void derive () {

		ProjectName name = ProjectName.of("my-blog");

		assertEquals("my-blog", name.name());
		assertEquals("myblog", name.packageName());
		assertEquals("my_blog", name.databaseName());

	}

	@Test
	@DisplayName("区切りが違っても同じところへ落ちる")
	void normalizes () {

		for (String input : new String[]{ "my-blog", "my_blog", "My Blog", "MY-BLOG" }) {

			ProjectName name = ProjectName.of(input);

			assertEquals("myblog", name.packageName(), input);
			assertEquals("my_blog", name.databaseName(), input);

		}

	}

	@Test
	@DisplayName("大文字の切れ目では区切らない")
	void doesNotSplitCamelCase () {

		/*
		 * MyBlog を my_blog にすると読みやすいが、
		 * APIServer が a_p_i_server になる。
		 * 「見た目から結果が予想できる」ほうを採る。
		 */
		ProjectName name = ProjectName.of("MyBlog");

		assertEquals("myblog", name.packageName());
		assertEquals("myblog", name.databaseName());

	}

	@Test
	@DisplayName("数字で始まってもパッケージ名になる")
	void leadingDigit () {

		// 識別子は数字で始められない。先頭の数字は落とす
		assertEquals("app", ProjectName.of("2048app").packageName());

	}

	@Test
	@DisplayName("パスは書けない")
	void rejectsPath () {

		for (String input : new String[]{ "../etc", "a/b", "a\\b", ".." }) {

			IllegalArgumentException ex = assertThrows(
				IllegalArgumentException.class, () -> ProjectName.of(input), input);

			assertTrue(ex.getMessage().contains("パス"), ex.getMessage());

		}

	}

	@Test
	@DisplayName("英数字が無ければ、何が足りないか言って止まる")
	void rejectsSymbolsOnly () {

		IllegalArgumentException ex = assertThrows(
			IllegalArgumentException.class, () -> ProjectName.of("---"));

		// 「どう直すか」まで書く（要件 F-X-05）
		assertTrue(ex.getMessage().contains("英数字"), ex.getMessage());

	}

	@Test
	@DisplayName("Java の予約語になるものは止める")
	void rejectsReservedWord () {

		IllegalArgumentException ex = assertThrows(
			IllegalArgumentException.class, () -> ProjectName.of("package"));

		assertTrue(ex.getMessage().contains("予約語"), ex.getMessage());

	}

	@Test
	@DisplayName("空なら例を出して止める")
	void rejectsEmpty () {

		for (String input : new String[]{ "", "   " }) {
			assertThrows(IllegalArgumentException.class, () -> ProjectName.of(input), input);
		}

		assertThrows(IllegalArgumentException.class, () -> ProjectName.of(null));

	}

}
