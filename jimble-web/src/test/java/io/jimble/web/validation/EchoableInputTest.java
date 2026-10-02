package io.jimble.web.validation;

import io.jimble.util.data.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

}
