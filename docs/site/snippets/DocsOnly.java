package docs;

/**
 * ドキュメント専用のコード片
 *
 * <p>
 * <b>ここに置くのは、実際のアプリには無いが説明には要る形だけ</b>である。
 * 本物のコードから抜けるものは、必ずそちらから抜くこと（要件 NF-D-03）。
 * </p>
 *
 * <p>
 * <b>このファイルはコンパイルしない。</b>抜き出しの元にするだけである。
 * ここに置いたものは「コンパイルが通っている保証」が無いので、増やさないこと。
 * </p>
 */
final class DocsOnly {

	void selectNesting () {

		// docs:begin select-nesting
		Data row = db.select(
			SQL.select()
				.from(Post.instance())
				.where(Post.id.eq(1L))
		);

		// SELECT の結果はテーブル名でネストする（要件 F-D-02）
		String title = row.getData("post").getString("title");

		// Column で引けば、途中の文字列が出てこない
		String same = row.getString(Post.title);
		// docs:end

	}

	void dbErrorIsReturned () {

		// docs:begin db-error
		try (DB db = BlogExample.db()) {

			List<Data> rows = db.selectList(SQL.select().from(Post.instance()));

			/*
			 * DB のエラーは例外ではなく戻り値で返る（要件 F-D-11）。
			 * select 系は null、更新系は -1。
			 */
			if (rows == null) {
				Log.error("引けませんでした: " + db.getError());
				return;
			}

		}
		// docs:end

	}

	void sessionSave () {

		// docs:begin session-save
		context.session().put("user_id", 42);

		// 明示的に保存する（要件 F-S-02）。自動保存はしない
		context.session().save();
		// docs:end

	}

	void batch () {

		// docs:begin batch-class
		public class PostCleanupBatch extends AbstractBatch {

			@Override
			public String batchName () { return "記事の掃除"; }

			@Override
			public boolean isScheduler () { return true; }

			@Override
			public String cron () {

				// 毎日 3:15
				return "15 3 * * *";

			}

			@Override
			public void execute (BatchArgs args) {

				int days = settings().getInt("days");

				while (!isCancelOrder()) {
					// 長い処理は中断指示を見る（要件 F-B-06）
				}

			}

		}
		// docs:end

	}

	void mcpTool () {

		// docs:begin mcp-tool
		public class GetWeatherTool implements McpTool {

			@Override
			public String description () {

				// ここはモデルが読む。いつ使うか・何ができないかを書く
				return "都市名から現在の天気を返す。過去や予報は返せない";

			}

			@Override
			public JsonSchema inputSchema () {

				return JsonSchema.object()
					.string("city", "都市名").required();

			}

			@Override
			public ToolResult call (WebContext context, Data arguments) {

				String city = arguments.getString("city");

				if (!isKnown(city)) {
					// モデルが読んで直せる失敗は、例外ではなく isError で返す
					return ToolResult.error("その都市は扱えません: %s".formatted(city));
				}

				return ToolResult.text(weatherOf(city));

			}

		}
		// docs:end

	}

	void wsHandler () {

		// docs:begin ws-handler
		public class ChatHandler implements WsHandler {

			@Override
			public boolean onUpgrade (WsSession session) {

				// 認証はここで通す。Cookie が読めるのはこの時点だけ。断ると 403
				return "secret".equals(session.cookie("token"));

			}

			@Override
			public void onMessage (WsContext context, String message) {

				context.session().send("echo: " + message);

			}

		}
		// docs:end

	}

	void sseUsage () {

		// docs:begin sse-basic
		get("/events", context -> {

			try (SseStream sse = context.response().sse()) {

				sse.send("progress", new Data().putData("percent", 10));
				sse.send("done", new Data().putData("ok", true));

			}

		});
		// docs:end

	}

}
