/*
 * jimble のパスキー（WebAuthn）のブラウザ側（D-261）
 *
 *   <script src="/passkey.js"></script>
 *
 *   // 登録（ログインしている人）
 *   await JimblePasskey.register("/passkey/register/options", "/passkey/register", { csrfToken: token });
 *
 *   // ログイン（ログイン ID を聞かない。ブラウザがパスキーの候補を出す）
 *   const result = await JimblePasskey.login("/passkey/login/options", "/passkey/login", { csrfToken: token });
 *
 *   // 入力欄の候補にパスキーを出す（<input autocomplete="username webauthn"> を置いておく）
 *   JimblePasskey.login("/passkey/login/options", "/passkey/login", { csrfToken: token, conditional: true });
 *
 * どれも成功すれば、サーバーの JSON を返す。断られたり、利用者が取り消したりすれば、Error を投げる
 * （error.status にサーバーの状態コード。取り消しは error.name === "NotAllowedError"）。
 */
(function (global) {

	"use strict";

	function toBuffer (value) {
		var base64 = value.replace(/-/g, "+").replace(/_/g, "/");
		var binary = atob(base64 + "===".slice((base64.length + 3) % 4));
		var bytes = new Uint8Array(binary.length);
		for (var i = 0; i < binary.length; i++) {
			bytes[i] = binary.charCodeAt(i);
		}
		return bytes.buffer;
	}

	function toBase64url (buffer) {
		if (!buffer) {
			return null;
		}
		var bytes = new Uint8Array(buffer);
		var binary = "";
		for (var i = 0; i < bytes.length; i++) {
			binary += String.fromCharCode(bytes[i]);
		}
		return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
	}

	async function post (url, body, options) {
		var headers = { "Content-Type": "application/json", "Accept": "application/json" };
		if (options.csrfToken) {
			headers["X-CSRF-Token"] = options.csrfToken;
		}
		var response = await fetch(url, {
			method: "POST",
			credentials: "same-origin",
			headers: headers,
			body: JSON.stringify(body || {})
		});
		// csrf.bind_session = true なら、ログインでトークンが変わる。次の POST に使えるよう差し替える
		var rotated = response.headers.get("X-CSRF-Token");
		if (rotated) {
			options.csrfToken = rotated;
			if (typeof options.onCsrfToken === "function") {
				options.onCsrfToken(rotated);
			}
		}
		var text = await response.text();
		var json = text ? JSON.parse(text) : {};
		if (!response.ok) {
			var error = new Error((json && json.error) || ("HTTP " + response.status));
			error.status = response.status;
			error.body = json;
			throw error;
		}
		return json;
	}

	function credentialToJSON (credential) {
		var response = credential.response;
		var json = {
			id: credential.id,
			rawId: toBase64url(credential.rawId),
			type: credential.type,
			authenticatorAttachment: credential.authenticatorAttachment || null,
			clientExtensionResults: credential.getClientExtensionResults ? credential.getClientExtensionResults() : {},
			response: { clientDataJSON: toBase64url(response.clientDataJSON) }
		};
		if (response.attestationObject) {
			json.response.attestationObject = toBase64url(response.attestationObject);
			json.response.transports = response.getTransports ? response.getTransports() : [];
		} else {
			json.response.authenticatorData = toBase64url(response.authenticatorData);
			json.response.signature = toBase64url(response.signature);
			json.response.userHandle = toBase64url(response.userHandle);
		}
		return json;
	}

	function supported () {
		return typeof global.PublicKeyCredential === "function" && !!(navigator.credentials && navigator.credentials.create);
	}

	async function register (optionsUrl, verifyUrl, options) {
		options = options || {};
		if (!supported()) {
			throw new Error("このブラウザはパスキーに対応していません");
		}
		var publicKey = await post(optionsUrl, options.body, options);
		publicKey.challenge = toBuffer(publicKey.challenge);
		publicKey.user.id = toBuffer(publicKey.user.id);
		(publicKey.excludeCredentials || []).forEach(function (c) { c.id = toBuffer(c.id); });
		var credential = await navigator.credentials.create({ publicKey: publicKey });
		return post(verifyUrl, credentialToJSON(credential), options);
	}

	async function login (optionsUrl, verifyUrl, options) {
		options = options || {};
		if (!supported()) {
			throw new Error("このブラウザはパスキーに対応していません");
		}
		if (options.conditional) {
			var available = global.PublicKeyCredential.isConditionalMediationAvailable
				&& await global.PublicKeyCredential.isConditionalMediationAvailable();
			if (!available) {
				return null;
			}
		}
		var publicKey = await post(optionsUrl, options.body, options);
		publicKey.challenge = toBuffer(publicKey.challenge);
		(publicKey.allowCredentials || []).forEach(function (c) { c.id = toBuffer(c.id); });
		var request = { publicKey: publicKey };
		if (options.conditional) {
			request.mediation = "conditional";
		}
		var credential = await navigator.credentials.get(request);
		return post(verifyUrl, credentialToJSON(credential), options);
	}

	global.JimblePasskey = { supported: supported, register: register, login: login };

})(window);
