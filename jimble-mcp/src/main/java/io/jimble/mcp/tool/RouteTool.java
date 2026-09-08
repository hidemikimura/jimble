package io.jimble.mcp.tool;

import io.jimble.mcp.schema.JsonSchema;
import io.jimble.util.data.Data;
import io.jimble.util.log.Log;
import io.jimble.web.call.CallRequest;
import io.jimble.web.call.CallResponse;
import io.jimble.web.context.WebContext;
import io.jimble.web.server.Dispatcher;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 実装済みの API をツールにする（要件 F-MCP-15）
 *
 * <pre>
 * public class BlogMcp extends McpController {
 *
 *     {
 *         tool("list_posts", RouteTool.of("GET", "/api/posts")
 *             .description("記事の一覧を返す。page で頁を指定する")
 *             .input(JsonSchema.object()
 *                 .integer("page", "ページ番号（1から）").min(1)));
 *
 *         tool("get_post", RouteTool.of("GET", "/api/posts/{id}")
 *             .description("記事を1件返す")
 *             .input(JsonSchema.object()
 *                 .integer("id", "記事ID").required()));
 *
 *         tool("create_post", RouteTool.of("POST", "/api/posts")
 *             .description("記事を1件登録する")
 *             .input(JsonSchema.object()
 *                 .string("title", "題名").required()
 *                 .string("body", "本文").required()));
 *     }
 *
 * }
 * </pre>
 *
 * <p>
 * <b>API を先に作り、あとから MCP でも出す</b>ときのための道具である。
 * ハンドラを2度書かない。API を直せば MCP 側も一緒に直る。
 * </p>
 *
 * <p>
 * ドメイン層を共有できるならそちらが先である（design-m9 4章）。
 * これは<b>「API として組み上がったもの（検証・整形・権限まで含めて）を
 * そのまま出したい」</b>ときに使う。
 * </p>
 *
 * <h2>引数の割り当て</h2>
 * <ul>
 *     <li>パスの {@code {name}} と同じ名前の引数 → パスに埋める</li>
 *     <li>残りの引数 → {@code GET} / {@code DELETE} / {@code HEAD} ならクエリ、
 *         それ以外なら JSON の本文</li>
 * </ul>
 *
 * <h2>結果</h2>
 * <ul>
 *     <li>2xx で本文が JSON のオブジェクト → 構造化した中身と文字の両方で返す</li>
 *     <li>2xx でそれ以外 → 文字で返す</li>
 *     <li>4xx → {@link ToolResult#error(String)}（本文をそのまま渡す。モデルが直せる）</li>
 *     <li>5xx → {@link ToolResult#error(String)}（<b>本文は渡さない</b>。要件 F-MCP-11）</li>
 * </ul>
 *
 * <p>
 * ルートの {@code before} / {@code after} は<b>効く</b>。
 * 認証を {@code before} に置いている API は、MCP から呼んでも認証を通る
 * （外側のリクエストのヘッダと Cookie を引き継ぐ）。
 * </p>
 */
public final class RouteTool implements Supplier<McpTool> {

	/* HTTPメソッド */
	private final String method;

	/* パスのかたち */
	private final String pattern;

	/* パスの中の変数名 */
	private final Set<String> pathVariables;

	/* 説明 */
	private String description;

	/* 人が読む名前 */
	private String title;

	/* 入力の形 */
	private JsonSchema input = JsonSchema.empty();

	/* 出力の形 */
	private JsonSchema output;

	/**
	 * コンストラクタ
	 *
	 * @param method	HTTPメソッド
	 * @param pattern	パスのかたち
	 */
	private RouteTool (String method, String pattern) {

		this.method = Objects.requireNonNull(method, "method").toUpperCase(Locale.ROOT);
		this.pattern = Objects.requireNonNull(pattern, "pattern");
		this.pathVariables = variablesOf(this.pattern);

		/*
		 * ワイルドカード（/files/*）は埋める手立てが無い。
		 * 黙って通すと「*」という名前のものを取りに行き、
		 * <b>エラーも出ないまま毎回 404 になる。</b>
		 * 登録した時点で止める（要件 F-X-05）。
		 */
		if (this.pattern.contains("*")) {
			throw new IllegalArgumentException(
				"ワイルドカードを含むルートはツールにできません: %s。埋める引数を {name} で書いてください"
					.formatted(this.pattern));
		}

	}

	/**
	 * 組み立てを始める
	 *
	 * @param method	HTTPメソッド（{@code GET} など）
	 * @param pattern	ルートのパス（{@code /api/posts/{id}}）
	 * @return	自分
	 */
	public static RouteTool of (String method, String pattern) {

		return new RouteTool(method, pattern);

	}

	// region 組み立てる

	/**
	 * 何をするツールか
	 *
	 * <p><b>ここはモデルが読む。</b>いつ使うべきかが分かるように書くこと。</p>
	 *
	 * @param description	説明
	 * @return	自分
	 */
	public RouteTool description (String description) {

		this.description = description;

		return this;

	}

	/**
	 * 人が読む名前
	 *
	 * @param title	名前
	 * @return	自分
	 */
	public RouteTool title (String title) {

		this.title = title;

		return this;

	}

	/**
	 * 入力の形
	 *
	 * <p>
	 * パスの {@code {name}} は<b>ここに書いて {@code required()} を付ける</b>。
	 * 書き忘れると、モデルは何を渡せばいいか分からない。
	 * </p>
	 *
	 * @param input	形
	 * @return	自分
	 */
	public RouteTool input (JsonSchema input) {

		this.input = input == null ? JsonSchema.empty() : input;

		return this;

	}

	/**
	 * 出力の形
	 *
	 * @param output	形
	 * @return	自分
	 */
	public RouteTool output (JsonSchema output) {

		this.output = output;

		return this;

	}

	// endregion

	/**
	 * {@inheritDoc}
	 *
	 * <p>呼ばれるたびに1つ作る（要件 F-MCP-10）。</p>
	 */
	@Override
	public McpTool get () {

		return new Invocation(this);

	}

	/**
	 * パスの中の変数名を拾う
	 *
	 * @param pattern	パスのかたち
	 * @return	変数名
	 */
	private static Set<String> variablesOf (String pattern) {

		Set<String> names = new LinkedHashSet<>();

		for (String segment : pattern.split("/")) {
			if (segment.startsWith("{") && segment.endsWith("}") && segment.length() > 2) {
				names.add(segment.substring(1, segment.length() - 1));
			}
		}

		return names;

	}

	/**
	 * 1回ぶんの呼び出し
	 */
	private static final class Invocation implements McpTool {

		/* もとの組み立て */
		private final RouteTool source;

		/**
		 * コンストラクタ
		 *
		 * @param source	もとの組み立て
		 */
		private Invocation (RouteTool source) {

			this.source = source;

		}

		@Override
		public String description () {

			return source.description == null
				? "%s %s".formatted(source.method, source.pattern)
				: source.description;

		}

		@Override
		public String title () {

			return source.title;

		}

		@Override
		public JsonSchema inputSchema () {

			return source.input;

		}

		@Override
		public JsonSchema outputSchema () {

			return source.output;

		}

		@Override
		public ToolResult call (WebContext context, Data arguments) throws Exception {

			Dispatcher dispatcher = context.dispatcher();

			if (dispatcher == null) {
				throw new IllegalStateException(
					"ディスパッチャがありません。RouteTool は jimble のサーバー上でしか使えません");
			}

			Data given = arguments == null ? new Data() : arguments;

			String path;

			try {
				path = buildPath(given);
			} catch (IllegalArgumentException cause) {
				// パス変数が足りないのはモデルが直せる
				return ToolResult.error(cause.getMessage());
			}

			CallRequest request = CallRequest.of(source.method, path);

			applyArguments(request, given);

			CallResponse response = dispatcher.call(context, request);

			return toResult(response);

		}

		/**
		 * パスを組み立てる
		 *
		 * @param arguments	引数
		 * @return	パス
		 */
		private String buildPath (Data arguments) {

			if (source.pathVariables.isEmpty()) {
				return source.pattern;
			}

			List<String> segments = new ArrayList<>();

			for (String segment : source.pattern.split("/", -1)) {

				if (!(segment.startsWith("{") && segment.endsWith("}") && segment.length() > 2)) {
					segments.add(segment);
					continue;
				}

				String name = segment.substring(1, segment.length() - 1);
				Object value = arguments.get(name);

				if (value == null || text(value).isEmpty()) {
					throw new IllegalArgumentException("引数 %s が要ります（%s の一部です）".formatted(name, source.pattern));
				}

				if (value instanceof Map || value instanceof List) {
					throw new IllegalArgumentException(
						"引数 %s には値を1つ渡してください（%s の一部です）".formatted(name, source.pattern));
				}

				/*
				 * セグメント単位でエンコードする（要件 F-R-23）。
				 * URLEncoder は空白を + にするので、パスでは %20 に直す。
				 */
				segments.add(URLEncoder.encode(text(value), StandardCharsets.UTF_8).replace("+", "%20"));

			}

			return String.join("/", segments);

		}

		/**
		 * 引数をリクエストに割り当てる
		 *
		 * @param request	リクエスト
		 * @param arguments	引数
		 */
		private void applyArguments (CallRequest request, Data arguments) {

			boolean useQuery = "GET".equals(source.method)
				|| "DELETE".equals(source.method)
				|| "HEAD".equals(source.method);

			Data body = new Data();

			for (Map.Entry<String, Object> entry : arguments.entrySet()) {

				if (source.pathVariables.contains(entry.getKey())) {
					continue;
				}

				if (!useQuery) {
					body.put(entry.getKey(), entry.getValue());
					continue;
				}

				for (String value : asStrings(entry.getValue())) {
					request.query(entry.getKey(), value);
				}

			}

			if (!useQuery) {
				request.json(body);
			}

		}

		/**
		 * クエリに載せる形にする
		 *
		 * @param value	値
		 * @return	値
		 */
		private static List<String> asStrings (Object value) {

			if (value == null) {
				return List.of("");
			}

			if (!(value instanceof List<?> list)) {
				return List.of(text(value));
			}

			List<String> values = new ArrayList<>();

			for (Object item : list) {
				values.add(item == null ? "" : text(item));
			}

			return values;

		}

		/**
		 * 値を文字にする
		 *
		 * <p>
		 * <b>入れ子のオブジェクトは JSON にする。</b>
		 * {@code Data.toString()} は<b>キーと型名だけを出して値を出さない</b>ので、
		 * そのまま使うと {@code filter=Data(1件) {status=String}} のような
		 * <b>値の消えたクエリ</b>ができあがる。しかも例外は出ない。
		 * </p>
		 *
		 * @param value	値
		 * @return	文字
		 */
		private static String text (Object value) {

			if (value == null) {
				return "";
			}

			if (value instanceof Map<?, ?> || value instanceof List<?>) {

				Data wrapped = new Data();
				wrapped.put("value", value);

				String json = wrapped.getJsonString();
				int start = json.indexOf(':');

				return start < 0 ? json : json.substring(start + 1, json.length() - 1);

			}

			return String.valueOf(value);

		}

		/**
		 * 結果にする
		 *
		 * @param response	レスポンス
		 * @return	結果
		 */
		private ToolResult toResult (CallResponse response) {

			if (response.code() >= 500) {

				/*
				 * 中身は外に出さない（要件 F-MCP-11）。
				 * 500 の本文にはスタックトレースが入っていることがある。
				 */
				Log.error("内部呼び出しが失敗しました: %s %s -> %d"
					.formatted(source.method, source.pattern, response.code()));

				return ToolResult.error("処理に失敗しました（%d）".formatted(response.code()));

			}

			if (response.code() >= 400) {

				/*
				 * 4xx はモデルが直せる。API がそのために書いた本文をそのまま渡す。
				 * 「日付の形式が違う」「その ID は無い」はモデルが読んで直せる。
				 */
				String text = response.text();

				return ToolResult.error(text.isEmpty()
					? "要求が受け付けられませんでした（%d）".formatted(response.code())
					: text);

			}

			if (response.isSuccess() && response.isCompressed()) {

				/*
				 * 圧縮したまま返るルート（response().cache(...) など）は
				 * 中身を読めない。<b>文字化けした本文をモデルに渡すより、
				 * 使えないと言うほうがよい。</b>
				 */
				Log.warn("圧縮された本文はツールにできません: %s %s".formatted(source.method, source.pattern));

				return ToolResult.error("この API は圧縮した本文を返すため、ツールとしては使えません");

			}

			if (response.code() >= 300) {

				String location = response.header("Location");

				return ToolResult.error("転送されました（%d）%s"
					.formatted(response.code(), location == null ? "" : ": " + location));

			}

			String text = response.text();

			if (response.isJsonObject()) {

				/*
				 * 構造化した中身と文字の両方で返す（仕様の勧め）。
				 * 古いクライアントは構造化を読まない。
				 */
				return ToolResult.of()
					.addText(text)
					.structured(response.json());

			}

			return ToolResult.text(text);

		}

	}

}
