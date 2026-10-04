package io.jimble.web.validation;

import io.jimble.util.data.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 422 で返す入力値から秘密を落とす（D-241）
 */
class EchoableInputTest {

	@Test
	@DisplayName("password / token などを落とす（入れ子も）。ほかはそのまま")
	void dropsSecrets () {

		Data input = Data.fromJsonString("""
			{"login_id": "taro", "password": "hunter2", "new_password_confirm": "x",
			 "user": {"name": "taro", "api_token": "t"}, "otp": "123456"}
			""");

		Data echo = ValidationExecutor.echoable(input);

		assertEquals("taro", echo.getString("login_id"));
		assertFalse(echo.containsKey("password"));
		assertFalse(echo.containsKey("new_password_confirm"));
		assertFalse(echo.containsKey("otp"));
		assertEquals("taro", echo.getDataOptional("user").getString("name"));
		assertFalse(echo.getDataOptional("user").containsKey("api_token"));

	}

	@Test
	@DisplayName("D-272 リストの中の秘密も落とす（かつては users[0][password] がそのまま返っていた）")
	void dropsSecretsInLists () {

		Data input = Data.fromJsonString("""
			{"users": [{"name": "taro", "password": "hunter2"}, [{"token": "t", "id": 1}]], "tags": ["a", "b"]}
			""");

		Data echo = ValidationExecutor.echoable(input);

		String json = echo.getJsonString();
		assertFalse(json.contains("hunter2"), json);
		assertFalse(json.contains("\"t\""), json);
		assertTrue(json.contains("taro"), json);
		assertEquals(java.util.List.of("a", "b"), echo.get("tags"));

	}

	@Test
	@DisplayName("D-272 深い入れ子でも落ちない（深すぎるところは写さない）")
	void deepNestingDoesNotOverflow () {

		Data root = new Data();
		Data cursor = root;
		for (int i = 0; i < 100_000; i++) {
			Data next = new Data();
			cursor.put("a", i % 2 == 0 ? next : java.util.List.of(next));
			cursor = next;
		}

		Data echo = assertDoesNotThrow(() -> ValidationExecutor.echoable(root));
		assertTrue(echo.containsKey("a"));

		// 写したものの深さは上限まで（JSON にして返すときにも深く潜らない）
		int depth = 0;
		Object cursorValue = echo;
		while (cursorValue instanceof Data data && data.containsKey("a")) {
			Object next = data.get("a");
			cursorValue = next instanceof java.util.List<?> list && !list.isEmpty() ? list.get(0) : next;
			depth++;
		}
		assertTrue(depth <= ValidationExecutor.ECHO_MAX_DEPTH, "深さ " + depth + " まで写している");

	}

}
