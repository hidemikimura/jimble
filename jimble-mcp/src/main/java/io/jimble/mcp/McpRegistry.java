package io.jimble.mcp;

import io.jimble.mcp.prompt.McpPrompt;
import io.jimble.mcp.resource.McpResource;
import io.jimble.mcp.tool.McpTool;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * このサーバーが公開しているもの
 *
 * <p>
 * <b>明示登録だけ。</b>アノテーションもクラスパスの走査も使わない
 * （原則2 / NF-P-03。バッチの F-B-09、MQ の F-M-07 と同じ）。
 * <b>{@link McpController} を上から読めば、何を公開しているかが全部分かる。</b>
 * </p>
 *
 * <p>
 * 順序は<b>登録した順</b>を保つ（{@link LinkedHashMap}）。
 * 仕様は「同じ並びで返すこと」を勧めている。
 * 並びが揺れるとクライアントのキャッシュが効かず、
 * モデルに渡すプロンプトのキャッシュも当たらなくなる。
 * </p>
 */
public final class McpRegistry {

	/* ツール */
	private final Map<String, Supplier<McpTool>> tools = new LinkedHashMap<>();

	/* リソース */
	private final Map<String, Supplier<McpResource>> resources = new LinkedHashMap<>();

	/* プロンプト */
	private final Map<String, Supplier<McpPrompt>> prompts = new LinkedHashMap<>();

	/**
	 * ツールを足す
	 *
	 * @param name		名前
	 * @param supplier	作るもの
	 */
	public void tool (String name, Supplier<McpTool> supplier) {

		check(name);

		if (tools.putIfAbsent(name, supplier) != null) {
			throw new IllegalStateException("同じ名前のツールが登録されています: " + name);
		}

	}

	/**
	 * リソースを足す
	 *
	 * @param uri		URI
	 * @param supplier	作るもの
	 */
	public void resource (String uri, Supplier<McpResource> supplier) {

		if (resources.putIfAbsent(uri, supplier) != null) {
			throw new IllegalStateException("同じ URI のリソースが登録されています: " + uri);
		}

	}

	/**
	 * プロンプトを足す
	 *
	 * @param name		名前
	 * @param supplier	作るもの
	 */
	public void prompt (String name, Supplier<McpPrompt> supplier) {

		if (prompts.putIfAbsent(name, supplier) != null) {
			throw new IllegalStateException("同じ名前のプロンプトが登録されています: " + name);
		}

	}

	/**
	 * ツール
	 *
	 * @return	ツール
	 */
	public Map<String, Supplier<McpTool>> tools () {

		return Collections.unmodifiableMap(tools);

	}

	/**
	 * リソース
	 *
	 * @return	リソース
	 */
	public Map<String, Supplier<McpResource>> resources () {

		return Collections.unmodifiableMap(resources);

	}

	/**
	 * プロンプト
	 *
	 * @return	プロンプト
	 */
	public Map<String, Supplier<McpPrompt>> prompts () {

		return Collections.unmodifiableMap(prompts);

	}

	/**
	 * 名前を確かめる
	 *
	 * <p>
	 * 仕様が勧める形（英数字と {@code _ - .}、1〜128 文字）から外れていたら止める。
	 * <b>登録した時点で止める</b>のが要点である。
	 * 実行時に弾くと、クライアントによって使えたり使えなかったりする。
	 * </p>
	 *
	 * @param name	名前
	 */
	private static void check (String name) {

		if (name == null || name.isEmpty() || name.length() > 128) {
			throw new IllegalArgumentException("ツール名は1〜128文字にしてください: " + name);
		}

		if (!name.matches("[A-Za-z0-9_.-]+")) {
			throw new IllegalArgumentException(
				"ツール名に使えるのは英数字と _ - . だけです: " + name);
		}

	}

}
