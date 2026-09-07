package io.jimble.mcp.prompt;

import io.jimble.mcp.McpProtocol;
import io.jimble.util.data.Data;
import io.jimble.web.context.WebContext;

import java.util.ArrayList;
import java.util.List;

/**
 * プロンプト1つ
 *
 * <p>
 * ユーザーが選んで使う定型文。<b>モデルが勝手に呼ぶものではない。</b>
 * </p>
 */
public interface McpPrompt {

	/**
	 * 何のプロンプトか
	 *
	 * @return	説明
	 */
	String description ();

	/**
	 * 受け取る引数
	 *
	 * @return	引数の名前と説明
	 */
	default List<Argument> arguments () {

		return List.of();

	}

	/**
	 * 組み立てる
	 *
	 * @param context	コンテキスト
	 * @param arguments	引数
	 * @return	やりとり
	 * @throws Exception	組み立てられなかった場合
	 */
	List<Message> get (WebContext context, Data arguments) throws Exception;

	/**
	 * 引数
	 *
	 * @param name			名前
	 * @param description	説明
	 * @param required		必須か
	 */
	record Argument(String name, String description, boolean required) {

		/**
		 * JSON にする
		 *
		 * @return	JSON
		 */
		public Data toData () {

			Data data = new Data();
			data.put("name", name);
			data.put("description", description);
			data.put("required", required);

			return data;

		}

	}

	/**
	 * やりとり1つ
	 *
	 * @param role	だれの発言か（{@code user} / {@code assistant}）
	 * @param text	中身
	 */
	record Message(String role, String text) {

		/**
		 * ユーザーの発言
		 *
		 * @param text	中身
		 * @return	発言
		 */
		public static Message user (String text) {

			return new Message("user", text);

		}

		/**
		 * JSON にする
		 *
		 * @return	JSON
		 */
		public Data toData () {

			Data content = new Data();
			content.put("type", "text");
			content.put("text", text);

			Data data = new Data();
			data.put("role", role);
			data.put("content", content);

			return data;

		}

	}

	/**
	 * 取得した結果を仕様の形にする
	 *
	 * @param description	説明
	 * @param messages		やりとり
	 * @return	JSON
	 */
	static Data toResult (String description, List<Message> messages) {

		List<Data> list = new ArrayList<>();
		for (Message message : messages) {
			list.add(message.toData());
		}

		Data data = new Data();
		data.put("resultType", McpProtocol.RESULT_TYPE_COMPLETE);
		data.put("description", description);
		data.put("messages", list);

		return data;

	}

}
