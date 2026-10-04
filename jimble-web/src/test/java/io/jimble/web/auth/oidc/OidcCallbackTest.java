package io.jimble.web.auth.oidc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@code Oidc.callback} の形（D-269）
 *
 * <p>
 * 2.2.3 から 2.5.1 まで、2 引数と 3 引数の形は<b>ルートを登録したところで必ず例外になっていた</b>。
 * 種別を「ルートの Auth.REALM」にするため null を渡していたが、渡した先の 4 引数の形が null を断っていた。
 * 二要素認証のテストは中の {@code finishLogin} を直に呼んでいたので、作るところを誰も通っていなかった。
 * </p>
 */
class OidcCallbackTest {

	@Test
	@DisplayName("D-269 2 引数・3 引数・4 引数の形は、どれも作れる")
	void everyFormCanBeBuilt () {

		assertNotNull(Oidc.callback("google", user -> null));
		assertNotNull(Oidc.callback("google", user -> null, "/login/code"));
		assertNotNull(Oidc.callback("google", user -> null, null));
		assertNotNull(Oidc.callback("google", user -> null, "/ops/login/code", "operator"));
		assertNotNull(Oidc.callback("google", user -> null, "/login/code", ""));

	}

	@Test
	@DisplayName("4 引数の形に null の種別を渡したら断る。結び付ける関数が無ければ、どの形でも断る")
	void rejectsMissingArguments () {

		assertThrows(IllegalArgumentException.class, () -> Oidc.callback("google", user -> null, "/login/code", null));
		assertThrows(IllegalArgumentException.class, () -> Oidc.callback("google", null));
		assertThrows(IllegalArgumentException.class, () -> Oidc.callback("google", null, "/login/code"));
		assertThrows(IllegalArgumentException.class, () -> Oidc.callback("google", null, "/login/code", "operator"));

	}

}
