package io.jimble.web.response;

import io.jimble.web.context.WebContext;
import io.jimble.web.http.ResponseSink;
import io.jimble.web.template.ModelAndView;
import io.jimble.web.template.Templates;
import io.jimble.util.conf.Conf;
import io.jimble.db.cache.CacheData;
import io.jimble.util.convertor.Configration;
import io.jimble.util.data.TableNest;
import io.jimble.util.io.FileUtil;
import io.jimble.util.data.Data;
import io.jimble.util.data.async.AsyncPrefetch;
import io.jimble.util.data.definition.IColumn;
import io.jimble.util.log.Log;
import io.jimble.web.request.Request;
import io.jimble.web.response.stream.ResponseOutputStream;
import io.jimble.web.sse.SseConf;
import io.jimble.web.sse.SseEvent;
import io.jimble.web.sse.SseStream;

import java.io.*;
import java.nio.charset.Charset;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * レスポンス情報
 */
public final class Response extends Data {

	/* Context */
	private final WebContext context;

	/* リクエスト */
	private final Request request;

	/* 出力口（HTTP サーバー実装を包む） */
	private final ResponseSink sink;

	/**
	 * コンストラクタ
	 *
	 * @param context	Context
	 * @param request	リクエスト
	 * @param sink		出力口
	 */
	public Response (WebContext context, Request request, ResponseSink sink) {

		this.context = context;
		this.request = request;
		this.sink = sink;

	}

	// region レスポンス開始済み判定

	/* レスポンス開始済み判定 */
	private boolean isResponseStarted = false;

	/**
	 * レスポンス内容が組み立てられているか
	 *
	 * <p>
	 * {@code json()} / {@code text()} / {@code modelAndView()} などで<b>内容を積んだ</b>状態。
	 * <b>まだ送信していない。</b>送信済みかどうかは {@link #isSent()} を見ること。
	 * </p>
	 *
	 * @return  内容がある場合 = true
	 */
	public boolean isResponseStarted () {

		return isResponseStarted || sink.isSent();

	}

	/**
	 * 送信済みか
	 *
	 * <p>
	 * <b>実際にバイトが書き出されたかどうか</b>だけを見る。
	 * {@code Dispatcher} と {@code Stage} の「送信済みなら打ち切る」判定はこれを使う。
	 * </p>
	 *
	 * <p>
	 * ここを {@link #isResponseStarted()} と同じにしていたため、
	 * <b>ハンドラが {@code json()} だけを呼んで {@code send()} を呼ばない場合に
	 * 「もう送った」と誤判定して、何も返さないまま終わっていた。</b>
	 * </p>
	 *
	 * @return	送信済みなら true
	 */
	public boolean isSent () {

		return sink.isSent();

	}

	// endregion

	// region IOバッファサイズ

	/* IOバッファサイズ */
	private int ioBufferSize = -1;

	/**
	 * IOバッファサイズを設定する
	 *
	 * @param ioBufferSize  IOバッファサイズ(byte)
	 * @return  Response
	 */
	public Response ioBufferSize (int ioBufferSize) {

		this.ioBufferSize = ioBufferSize;
		return this;

	}

	/**
	 * IOバッファサイズを取得する
	 *
	 * @return  IOバッファサイズ(byte)
	 */
	public int IoBufferSize () {

		return this.ioBufferSize;

	}

	/**
	 * IOバッファサイズを取得する
	 *
	 * @return  IOバッファサイズ(byte)
	 */
	private int getIoBufferSize () {

		if (this.ioBufferSize > 0) {
			return this.ioBufferSize;
		}

		return Conf.conf().getInt("jimble.io.buffer_size", 256 * 1024);

	}

	// endregion

	// region フォームデータ

	/*
	 * フォームデータ（要件 F-W-09）。
	 *
	 * <b>ここに入れたものは、応答そのものにも入る。</b>
	 * 前はこのフィールドに溜めるだけで、<b>誰も読んでいなかった</b>——
	 * {@code ValidationExecutor} が検証に落ちたときに入力値をここへ入れているのに、
	 * <b>本文にもテンプレートにも出てこない</b>ので、画面を組み直せなかった。
	 * ドキュメント（errors.md）は最初から「入力値も一緒に返る」と書いてある（D-133）。
	 */
	private Data formData = new Data();

	/**
	 * フォームデータを取得する
	 *
	 * @return  フォームデータ
	 */
	public Data getForm () {

		return this.formData;

	}

	/**
	 * フォームデータを追加する
	 *
	 * @param key   キー
	 * @param value 値
	 * @return  Response
	 */
	public Response putForm (String key, Object value) {

		this.formData.put(key, value);

		// 応答にも載せる。ここを忘れると getForm() でしか読めない値になる
		putData(key, value);

		return this;

	}

	/**
	 * フォームデータを追加する
	 *
	 * @param data  フォームデータ
	 * @return  Response
	 */
	public Response putForm (Data data) {

		if (data == null) {
			return this;
		}

		this.formData.putAll(data);

		for (Map.Entry<String, Object> entry : data.entrySet()) {
			putData(entry.getKey(), entry.getValue());
		}

		return this;

	}

	/**
	 * フォームデータを設定する
	 *
	 * @param formData  フォームデータ
	 * @return  Response
	 */
	public Response setForm (Data formData) {

		this.formData = formData == null ? new Data() : formData;

		for (Map.Entry<String, Object> entry : this.formData.entrySet()) {
			putData(entry.getKey(), entry.getValue());
		}

		return this;

	}

	/**
	 * フォームデータ存在判定
	 *
	 * @return  存在する場合 = true
	 */
	public boolean hasForm () {

		return this.formData != null && !this.formData.isEmpty();

	}

	// endregion


	// region レスポンスコード

	/** Cache-Control ヘッダ名 */
	public static final String HEADER_CACHE_CONTROL = "Cache-Control";

	/** Content-Type */
	public static final String HEADER_CONTENT_TYPE = "Content-Type";

	/** JSON の Content-Type */
	public static final String CONTENT_TYPE_JSON = "application/json; charset=UTF-8";

	/* Cache-Control を自分で設定したか */
	private boolean cacheControlSet = false;

	/* Content-Type を自分で設定したか */
	private boolean contentTypeSet = false;

	/* レスポンスコード */
	private int responseCode = 200;
	/* レスポンスコード設定済み判定 */
	private boolean isSettedResponseCode = false;

	/**
	 * レスポンスコード
	 *
	 * @return  レスポンスコード
	 */
	public int code () {

		return this.responseCode;

	}

	/**
	 * レスポンスコードを設定する
	 *
	 * @param code  レスポンスコード
	 * @return  Response
	 */
	public Response code (int code) {

		this.responseCode = code;
		this.isSettedResponseCode = true;
		return this;

	}

	// endregion

	// region テーブルネスト（要件 F-A-11）

	/* テーブルネストの扱い */
	private TableNest tableNest = TableNest.AS_IS;

	/**
	 * このレスポンスのテーブルネストの扱いを決める（要件 F-A-11）
	 *
	 * <p>
	 * {@code AsyncData} / {@code AsyncList} を<b>テーブル名でネストして返すかどうか</b>。
	 * 既定は {@link TableNest#AS_IS}（{@code setData} が作った形のまま）。
	 * </p>
	 *
	 * <pre>
	 * // 管理画面 API はテーブルネストで返す
	 * before(context -&gt; context.response().tableNest(TableNest.ON));
	 * </pre>
	 *
	 * <p>
	 * <b>同じ {@code AsyncData} を、面ごとに違う形で返すためにある。</b>
	 * 形ごとにクラスを2つ書くと、片方だけ直したときに気づけない。
	 * </p>
	 *
	 * @param tableNest	テーブルネストの扱い
	 * @return	Response
	 */
	public Response tableNest (TableNest tableNest) {

		this.tableNest = tableNest == null ? TableNest.AS_IS : tableNest;
		return this;

	}

	/**
	 * テーブルネストの扱い
	 *
	 * @return	テーブルネストの扱い
	 */
	public TableNest tableNest () {

		return this.tableNest;

	}

	// endregion

	// region JSONレスポンス

	/* JSONレスポンス判定 */
	private boolean isResponseJson = false;

	/**
	 * JSONレスポンス判定
	 *
	 * @return  JSONレスポンスの場合 = true
	 */
	public boolean isResponseJson() {

		return this.isResponseJson;

	}

	/**
	 * JSONレスポンスを設定する
	 *
	 * @param data  データ
	 * @return  Response
	 */
	public Response json (Data data) {

		this.isResponseStarted = true;
		this.isResponseJson = true;
		putAllData(data);
		return this;


	}

	/**
	 * JSONレスポンスを追加する
	 *
	 * @param key   キー
	 * @param value 値
	 * @return  Response
	 */
	public Response json (String key, Object value) {

		this.isResponseStarted = true;
		this.isResponseJson = true;
		putData(key, value);
		return this;

	}

	/**
	 * JSONレスポンスを追加する
	 *
	 * @param column	列
	 * @param value		値
	 * @return  Response
	 */
	public Response json (IColumn column, Object value) {

		this.isResponseStarted = true;
		this.isResponseJson = true;
		putData(column, value);
		return this;

	}

	// endregion

	// region JSONLレスポンス

	/* JSONLレスポンス */
	private List<Data> responseJsonL = new ArrayList<>();

	/* JSONLレスポンス */
	private boolean isResponseJsonL = false;

	/**
	 * JSONLレスポンス判定
	 *
	 * @return  JSONLレスポンスの場合 = true
	 */
	public boolean isResponseJsonL() {

		return this.isResponseJsonL;

	}

	/**
	 * JSONLを追加する
	 *
	 * @param json  JSON
	 * @return  Response
	 */
	public Response addJsonL (Data json) {

		this.isResponseJsonL = true;
		this.responseJsonL.add(json);
		return this;

	}

	/**
	 * JSONLを設定する
	 *
	 * @param jsonL JSONL
	 * @return  Response
	 */
	public Response jsonL (List<Data> jsonL) {

		this.isResponseJsonL = true;
		this.responseJsonL = jsonL;
		return this;

	}

	// endregion

	// region 文字列レスポンス

	/* 文字列レスポンス */
	private String responseText = null;

	/**
	 * 文字列レスポンスを設定する
	 *
	 * @param text  文字列
	 * @return  Response
	 */
	public Response text (String text) {

		this.isResponseStarted = true;
		this.responseText = text;
		return this;

	}

	// endregion

	// region ModelAndViewレスポンス

	/* ModelAndViewレスポンス */
	private ModelAndView modelAndViewResponse = null;

	/**
	 * ModelAndViewレスポンス
	 *
	 * @param modelAndView  ModelAndView
	 * @return  Response
	 */
	public Response modelAndView (ModelAndView modelAndView) {

		this.isResponseStarted = true;
		this.modelAndViewResponse = modelAndView;
		return this;

	}

	/**
	 * ModelAndViewレスポンス（モデルはこのレスポンス自身）
	 *
	 * <p>
	 * {@code Response} は {@code Data} なので、そのままモデルになる。
	 * </p>
	 *
	 * <pre>
	 * context.response()
	 *     .put("title", "一覧")
	 *     .view("shop/item/index.jte");
	 * </pre>
	 *
	 * @param view	テンプレート名
	 * @return	Response
	 */
	public Response view (String view) {

		return modelAndView(new ModelAndView(view, this));

	}

	/**
	 * ModelAndViewレスポンス
	 *
	 * @param view	テンプレート名
	 * @param model	モデル
	 * @return	Response
	 */
	public Response view (String view, Data model) {

		return modelAndView(new ModelAndView(view, model));

	}

	// endregion

	// region リダイレクトレスポンス

	/* リダイレクトレスポンス */
	private String redirectResponse = null;

	/**
	 * リダイレクトレスポンスを設定する
	 *
	 * @param redirectResponse  リダイレクトレスポンス
	 * @return  Response
	 */
	public Response redirect (String redirectResponse) {

		code(302);
		this.isResponseStarted = true;
		this.redirectResponse = redirectResponse;
		return this;

	}

	// endregion

	// region ダウンロードFileレスポンス

	/* ダウンロードFileレスポンス */
	private File downloadFileResponse = null;
	/* ダウンロードFile名 */
	private String downloadFileName = null;

	/**
	 * ダウンロードFileレスポンスを設定する
	 *
	 * @param fileResponse  ダウンロードFile
	 * @return  Response
	 */
	public Response download (File fileResponse) {

		this.isResponseStarted = true;
		this.downloadFileResponse = fileResponse;
		return this;

	}

	/**
	 * ダウンロードFileレスポンスを設定する
	 *
	 * @param fileResponse  ダウンロードFile
	 * @param fileName      ダウンロードFile名
	 * @return  Response
	 */
	public Response download (File fileResponse, String fileName) {

		this.isResponseStarted = true;
		this.downloadFileResponse = fileResponse;
		this.downloadFileName = fileName;
		return this;

	}

	// endregion

	// region Fileレスポンス

	/* Fileレスポンス */
	private File fileResponse = null;
	/* Fileコンテンツタイプ */
	private String fileContentType = null;

	/**
	 * Fileレスポンスを設定する
	 *
	 * @param file  File
	 * @return  Response
	 */
	public Response file (File file) {

		this.isResponseStarted = true;
		this.fileResponse = file;
		return this;

	}

	/**
	 * ViewFileレスポンスを設定する
	 *
	 * @param file          File
	 * @param contentType   コンテンツタイプ
	 * @return  Response
	 */
	public Response file (File file, String contentType) {

		this.isResponseStarted = true;
		this.fileResponse = file;
		this.fileContentType = contentType;
		return this;

	}

	// endregion

	// region ストリームレスポンス

	/* ストリームレスポンス */
	private InputStream streamResponse = null;
	/* ストリームコンテンツタイプ */
	private String streamContentType = null;
	/* ストリームコンテンツ長 */
	private long streamContentLength = -1;

	/**
	 * ストリームレスポンスを設定する
	 *
	 * @param streamResponse    ストリーム
	 * @param contentType       コンテンツタイプ
	 * @return  Response
	 */
	public Response stream (InputStream streamResponse, String contentType) {

		this.isResponseStarted = true;
		this.streamResponse = streamResponse;
		if (!(this.streamResponse instanceof BufferedInputStream)) {
			this.streamResponse = new BufferedInputStream(streamResponse);
		}
		this.streamContentType = contentType;
		return this;

	}

	/**
	 * ストリームレスポンスを設定する
	 *
	 * @param streamResponse    ストリーム
	 * @param contentType       コンテンツタイプ
	 * @param contentLength     コンテンツ長
	 * @return  Response
	 */
	public Response stream (InputStream streamResponse, String contentType, long contentLength) {

		this.isResponseStarted = true;
		this.streamResponse = streamResponse;
		if (!(this.streamResponse instanceof BufferedInputStream)) {
			this.streamResponse = new BufferedInputStream(streamResponse);
		}
		this.streamContentType = contentType;
		this.streamContentLength = contentLength;
		return this;

	}

	// endregion

	// region キャッシュレスポンス

	/* キャッシュレスポンス */
	private CacheData cacheResponse = null;

	/**
	 * キャッシュレスポンスを設定する
	 *
	 * @param cacheResponse キャッシュレスポンス
	 * @return  Response
	 */
	public Response cache (CacheData cacheResponse) {

		this.isResponseStarted = true;
		this.cacheResponse = cacheResponse;
		return this;

	}

	// endregion


	// region レスポンスヘッダ

	/**
	 * レスポンスヘッダを設定する
	 *
	 * @param name  名前
	 * @param value 値
	 * @return  Response
	 */
	public Response setResponseHeader (String name, String value) {

		if (sink.isSent()) {
			return this;
		}

		if (HEADER_CACHE_CONTROL.equalsIgnoreCase(name)) {
			// 自分で決めた値を送信時に上書きしない
			cacheControlSet = true;
		}

		if (HEADER_CONTENT_TYPE.equalsIgnoreCase(name)) {
			// 同上
			contentTypeSet = true;
		}

		sink.header(name, value);
		return this;

	}

	/**
	 * 既定の Cache-Control を設定する
	 *
	 * <p>
	 * <b>動的レスポンスは {@code no-store}</b>（要件 F-X-07）。
	 * ただし静的配信のように<b>自分で Cache-Control を設定した場合は上書きしない。</b>
	 * </p>
	 *
	 * <p>
	 * 移送元（と移送直後の jimble）は送信のたびに無条件で {@code no-store} を書いていた。
	 * <b>静的ファイルにも no-store が付き、ブラウザキャッシュが効かなかった。</b>
	 * </p>
	 */
	private void applyDefaultCacheControl () {

		if (cacheControlSet) {
			return;
		}

		sink.header(HEADER_CACHE_CONTROL, "no-store");

	}

	// endregion


	// region 出力ストリームを取得する

	/**
	 * 出力ストリームを取得する
	 *
	 * @return  出力ストリーム
	 */
	public OutputStream outputStream () {

		isResponseStarted = true;
		return new ResponseOutputStream(this, sink.outputStream());

	}

	// endregion


	// region SSE（要件 F-W-21）

	/**
	 * サーバーから送り続ける口を開く（要件 F-W-21）
	 *
	 * <pre>
	 * try (SseStream sse = context.response().sse()) {
	 *     sse.send("tick", new Data().putData("n", 1));
	 * }
	 * </pre>
	 *
	 * <p>
	 * <b>ここを呼んだ時点でヘッダが確定する。</b>あとから変えられない。
	 * 中身の型・キャッシュ・バッファ抑止をここで入れる。
	 * </p>
	 *
	 * <p>
	 * <b>DB 接続を握ったまま張らないこと。</b>
	 * 1本のストリームは1つの実行単位（要件 F-C-01）なので、
	 * 繋ぎっぱなしのぶんだけ接続プールを食う（{@link SseStream} 参照）。
	 * </p>
	 *
	 * @return	送る口
	 */
	public SseStream sse () {

		return sse(SseConf.maxDuration(), SseConf.maxEvents());

	}

	/**
	 * サーバーから送り続ける口を開く（要件 F-W-21）
	 *
	 * @param maxDuration	張っていられる上限。null か 0 以下なら無制限
	 * @param maxEvents		送れる件数の上限。0 以下なら無制限
	 * @return	送る口
	 */
	public SseStream sse (Duration maxDuration, long maxEvents) {

		code(200);

		setResponseHeader("Content-Type", SseStream.CONTENT_TYPE);

		// 途中で溜め込まれると SSE の意味が無くなる
		setResponseHeader(HEADER_CACHE_CONTROL, "no-store");
		setResponseHeader("X-Accel-Buffering", "no");

		SseStream stream = new SseStream(outputStream(), maxDuration, maxEvents);

		/*
		 * 繋ぎ直すまでの時間を最初に伝える。
		 * 上限で切ったあと、クライアントはこの時間のあとに勝手に戻ってくる。
		 */
		long retry = SseConf.retry().toMillis();
		if (retry > 0) {
			// 件数には数えない。中身が無いので
			stream.sendWithoutCounting(new SseEvent(null, null, null, retry, null));
		}

		return stream;

	}

	// endregion

	/**
	 * 溜めた Cookie を書き出す
	 *
	 * <p>
	 * <b>送信のどの経路を通っても、ヘッダを書く直前にここを通る。</b>
	 * 2回目以降は何もしない（{@link io.jimble.web.cookie.Cookies#flush}）。
	 * </p>
	 */
	private void flushCookies () {

		context.flushCookies(sink);

	}

	/**
	 * レスポンス後処理
	 */
	public void afterResponse () {

		// レスポンス後処理（M4 でセッション保存などが入る）

	}

	// region 既定のエラー応答（要件 F-C-17 / D-11）

	/** 既定のエラー応答の入れ物の名前 */
	public static final String ERROR_KEY = "error";

	/**
	 * 本文が1つでも組み立てられているか
	 *
	 * <p>ハンドラが本文を1つでも用意していれば true。</p>
	 *
	 * <p>
	 * <b>{@code isEmpty()} という名前にしないこと（D-173）。</b>
	 * {@code Response} は {@link Data}（→ {@code LinkedHashMap}）を継いでいるので、
	 * その名前は {@code Map#isEmpty()} を<b>意図せず上書きする</b>。
	 * 上書きすると {@link Data#summary()} が早期 return して、
	 * <b>{@code put(...)} で積んだ中身がログから丸ごと消える</b>——
	 * 「本文がまだ無い」と「マップが空」は別のことである。
	 * </p>
	 *
	 * @return	本文があれば true
	 */
	public boolean hasBody () {

		return isResponseJson
			|| isResponseJsonL
			|| responseText != null
			|| modelAndViewResponse != null
			|| redirectResponse != null
			|| downloadFileResponse != null
			|| fileResponse != null
			|| streamResponse != null
			|| cacheResponse != null;

	}

	/**
	 * 既定のエラー応答を用意する（要件 F-C-17 / D-11）
	 *
	 * <p>
	 * <b>アプリが何も返さなかったときだけ入る。</b>
	 * {@code error(...)} で本文を組み立てていれば、そちらがそのまま出る。
	 * </p>
	 *
	 * <p>
	 * <b>中身は決め打ちである。</b>例外のメッセージも、スタックトレースも、
	 * SQL も入れない（要件 NF-S-06）。ここに原因を書くと、
	 * <b>本番かどうかの判断を1か所忘れただけで外に漏れる</b>。
	 * 原因はログに残っている（{@code Dispatcher} が 500 番台を出している）。
	 * </p>
	 *
	 * <p>
	 * JSON を名指しされていれば JSON、そうでなければ短いテキストを返す。
	 * <b>{@code *&#47;*}（何でもいい）はテキストにする</b>——
	 * 「何でもいい」相手に構造を返しても読み手が居ない。
	 * </p>
	 *
	 * @param statusCode	ステータスコード
	 * @param reason		短い理由（{@code "Not Found"} のような、内部情報を含まない語）
	 */
	public void errorBody (int statusCode, String reason) {

		if (hasBody()) {
			return;
		}

		if (request.acceptJson()) {

			Data error = new Data();
			error.put("status", statusCode);
			error.put("message", reason);

			json(ERROR_KEY, error);

			return;

		}

		/*
		 * ここでは送らずに<b>組み立てるだけ</b>にする。
		 * 送ってしまうと、この経路だけ {@code Stage} を通らない形になり、
		 * 「送信済みなら打ち切る」の判定が2か所に増える（要件 F-C-13）。
		 */
		setResponseHeader(HEADER_CONTENT_TYPE, "text/plain; charset=UTF-8");

		text("%d %s".formatted(statusCode, reason));

	}

	// endregion


	// region レスポンス送信

	/**
	 * レスポンス送信
	 *
	 * <p>
	 * <b>戻り値は自分自身である（D-173）。</b>引数ありの {@code send(...)} 13 個が
	 * すべて {@code Response} を返すのに、ここだけ {@code Object} の {@code null} を返していた——
	 * <b>引数無しのときだけ連鎖が切れる</b>形だった。戻り値の型はメソッド記述子の一部なので、
	 * <b>1.0 のあとは 2.0 まで直せない</b>。
	 * </p>
	 *
	 * @return  自分自身
	 */
	public Response send () {

		if (sink.isSent()) {
			return this;
		}

		flushCookies();
		applyDefaultCacheControl();
		sink.status(responseCode);

		prefetch();

		// JSONレスポンス
		if (isResponseJson) {
			send(this);
			return this;
		}

		// JSONLレスポンス
		if (isResponseJsonL) {
			send(responseJsonL);
			return this;
		}

		// 文字列レスポンス
		if (responseText != null) {
			send(responseText);
			return this;
		}

		// キャッシュレスポンス
		if (cacheResponse != null) {
			String contentType = cacheResponse.contentType();
			if (cacheResponse.hasContentFile()) {
				if (contentType == null) {
					contentType = FileUtil.getFileContentType(cacheResponse.contentFile());
				}
				send(cacheResponse.contentFile(), contentType, "gzip");
			} else if (cacheResponse.hasContentString()) {
				if (contentType == null) {
					send(cacheResponse.contentString());
				} else {
					send(cacheResponse.contentString(), contentType);
				}
			} else {
				send(500);
			}
			return this;
		}

		// ModelAndViewレスポンス
		if (modelAndViewResponse != null) {
			// JSONレスポンスを要求されている場合はデータだけ返す（移送元と同じ）
			if (request.acceptJson()) {
				send(this);
				return this;
			}
			sendView(modelAndViewResponse);
			return this;
		}

		// リダイレクトレスポンス
		if (redirectResponse != null) {
			flushCookies();
			sink.redirect(redirectResponse);
			afterResponse();
			return this;
		}

		// ダウンロードFileレスポンス
		if (downloadFileResponse != null) {
			try {
				flushCookies();
				sink.sendFile(downloadFileResponse.toPath(), downloadFileName);
				afterResponse();
				return this;
			} catch (Exception ex) {
				Log.error(ex, request, this);
				this.responseCode = 500;
				sink.status(500); sink.send();
				afterResponse();
				return this;
			}
		}

		// ファイルレスポンス
		if (fileResponse != null) {
			if (fileContentType == null) {
				fileContentType = FileUtil.getFileContentType(fileResponse);
			}
			send(fileResponse, fileContentType);
			return this;
		}

		// ストリームレスポンス
		if (streamResponse != null) {
			send(streamResponse, streamContentType, streamContentLength);
			return this;
		}

		// レスポンスなし
		if (request.acceptJson()) {
			// JSONレスポンスを要求されている場合はデータを返す
			send(this);
		} else if (isSettedResponseCode) {
			// レスポンスコードありの場合はレスポンスコードを返す
			sink.status(responseCode); sink.send();
			afterResponse();
		} else {
			// 何もない場合はNO_CONTENTを返す
			this.responseCode = 204;
			sink.status(204); sink.send();
			afterResponse();
		}
		return this;

	}

	/**
	 * テンプレートを描画して返す
	 *
	 * <p>
	 * <b>いったん文字列に組み立ててから送る。</b>
	 * ストリームで書きながら描画すると速いが、
	 * 途中でテンプレートが落ちたときには<b>もうヘッダも本文も出てしまっていて、
	 * 500 を返せない。</b>HTML1ページ分の大きさなら組み立ててからで困らない。
	 * 大きなものを流したい場合は {@code Templates.render(name, model, writer)} を使う。
	 * </p>
	 *
	 * @param modelAndView	テンプレートとモデル
	 */
	private void sendView (ModelAndView modelAndView) {

		String html;

		try {

			html = Templates.render(modelAndView.view(), modelAndView.model());

		} catch (Exception ex) {

			Log.error(ex, request, this);
			this.responseCode = 500;
			sink.status(500); sink.send();
			afterResponse();
			return;

		}

		send(html, Templates.responseContentType(), StandardCharsets.UTF_8);

	}

	/**
	 * 遅延読み込みの枝をまとめて埋める（要件 F-A-06）
	 *
	 * <p>
	 * <b>既定では何もしない。</b>{@code async.prefetch.on_response = true} に
	 * したときだけ走る。原則5（隠れた I/O を作らない）に照らすと、
	 * 黙って走らせるものではない。入れたとたんに挙動が変わることもない。
	 * </p>
	 *
	 * <p>
	 * 走らせても<b>出力は変わらない</b>（要件 F-A-08）。変わるのは
	 * 「1件ずつ引いていたものが IN 句1本になる」ところだけである。
	 * 明示的に走らせたいときは {@code AsyncPrefetch.run(data)} を直接呼ぶ。
	 * </p>
	 */
	private void prefetch () {

		if (!AsyncPrefetch.isOnResponse()) {
			return;
		}

		/* putData で入れたものは自分自身にぶら下がっている */
		AsyncPrefetch.run(this);

		if (isResponseJsonL) {
			AsyncPrefetch.run(responseJsonL);
		}

	}

	/**
	 * レスポンス送信
	 *
	 * @param code    ステータスコード
	 * @return  Response
	 */
	public Response send (int code) {

		if (sink.isSent()) {
			return this;
		}

		flushCookies();
		applyDefaultCacheControl();
		this.responseCode = code;
		this.isSettedResponseCode = true;
		sink.status(code);
		sink.send();

		if (code >= 400) {
//			Log.error("Response code: " + code);
		}

		afterResponse();

		return this;

	}

	/**
	 * レスポンス送信
	 *
	 * @param is    入力ストリーム
	 * @return  Response
	 */
	public Response send (InputStream is) {

		if (sink.isSent()) {
			return this;
		}

		flushCookies();
		applyDefaultCacheControl();
		sink.status(responseCode);
		sink.send(is);

		afterResponse();

		return this;

	}

	/**
	 * レスポンス送信
	 *
	 * @param   is          入力ストリーム
	 * @param   contentType コンテンツタイプ
	 * @return  Response
	 */
	public Response send (InputStream is, String contentType) {

		return send(is, contentType, -1);

	}

	/**
	 * レスポンス送信
	 *
	 * @param   is              入力ストリーム
	 * @param   contentType     コンテンツタイプ
	 * @param   contentLength   コンテンツ長
	 * @return  Response
	 */
	public Response send (InputStream is, String contentType, long contentLength) {

		if (sink.isSent()) {
			return this;
		}

		flushCookies();
		applyDefaultCacheControl();
		sink.status(responseCode);
		sink.header(HEADER_CONTENT_TYPE, contentType);
		if (contentLength > 0) {
			sink.header("Content-Length", contentLength);
		}
		try (
			BufferedInputStream bis = new BufferedInputStream(is, getIoBufferSize())
		) {
			sink.send(bis);
		} catch (Exception ex) {
			Log.error(ex, request, this);
			this.responseCode = 500;
			sink.status(500); sink.send();
		}

		afterResponse();

		return this;

	}

	/**
	 * Content-Type に文字コードを足す
	 *
	 * <p>
	 * <b>文字コードを指定して本文を書いたのに、Content-Type に載せていなかった。</b>
	 * 受け取る側は文字コードを推測することになり、
	 * {@code text/plain} や CSV では文字化けする（HTML は {@code <meta>} で助かることが多い）。
	 * </p>
	 *
	 * @param contentType	コンテンツタイプ
	 * @param charset		文字コード
	 * @return	{@code charset} つきのコンテンツタイプ
	 */
	static String withCharset (String contentType, Charset charset) {

		if (contentType == null || contentType.isEmpty() || charset == null) {
			return contentType;
		}

		if (contentType.toLowerCase().contains("charset=")) {
			return contentType;
		}

		return contentType + "; charset=" + charset.name();

	}

	/**
	 * レスポンス送信
	 *
	 * @param text  文字列
	 * @return  Response
	 */
	public Response send (String text) {

		if (sink.isSent()) {
			return this;
		}

		flushCookies();
		applyDefaultCacheControl();
		sink.status(responseCode);
		sink.send(text);

		afterResponse();

		return this;

	}

	/**
	 * レスポンス送信
	 *
	 * @param text          文字列
	 * @param contentType   コンテンツタイプ
	 * @return  Response
	 */
	public Response send (String text, String contentType) {

		if (sink.isSent()) {
			return this;
		}

		flushCookies();
		applyDefaultCacheControl();
		sink.status(responseCode);
		sink.header(HEADER_CONTENT_TYPE, contentType);
		sink.send(text);

		afterResponse();

		return this;

	}

	/**
	 * レスポンス送信
	 *
	 * @param text      文字列
	 * @param charset   文字コード
	 * @return  Response
	 */
	public Response send (String text, Charset charset) {

		if (sink.isSent()) {
			return this;
		}

		flushCookies();
		applyDefaultCacheControl();
		sink.status(responseCode);
		sink.send(text, charset);

		afterResponse();

		return this;

	}

	/**
	 * レスポンス送信
	 *
	 * @param text          文字列
	 * @param contentType   コンテンツタイプ
	 * @param charset       文字コード
	 * @return  Response
	 */
	public Response send (String text, String contentType, Charset charset) {

		if (sink.isSent()) {
			return this;
		}

		flushCookies();
		applyDefaultCacheControl();
		sink.status(responseCode);
		sink.header("Content-Type", withCharset(contentType, charset));
		sink.send(text, charset);

		afterResponse();

		return this;

	}

	/**
	 * レスポンス送信
	 *
	 * @param json  JSONデータ
	 * @return  Response
	 */
	public Response send (Data json) {

		if (sink.isSent()) {
			return this;
		}

		flushCookies();
		applyDefaultCacheControl();
		sink.status(responseCode);

		/*
		 * JSON を返すなら Content-Type も JSON にする。
		 *
		 * ブラウザの fetch().json() は Content-Type を見ないので気づきにくいが、
		 * <b>付いていないと困る相手がいる</b>（curl | jq、プロキシ、
		 * Accept で振り分ける中継、他言語のクライアント）。
		 * JSONL 側は最初から付けていた。付け忘れである。
		 */
		if (!contentTypeSet) {
			sink.header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON);
		}

		try (
			BufferedOutputStream bos = new BufferedOutputStream(sink.outputStream(), getIoBufferSize())
		) {
			Configration configration = new Configration();
			configration.isAutoClose(true);
			configration.tableNest(tableNest);
			json.outputJsonString(bos, configration);
		} catch (Exception ex) {
			Log.error(ex, request, this);
			this.responseCode = 500;
			sink.status(500); sink.send();
		}

		afterResponse();

		return this;

	}

	/**
	 * レスポンス送信
	 *
	 * @param jsonL JSONL
	 * @return  Response
	 */
	public Response send (List<Data> jsonL) {

		if (sink.isSent()) {
			return this;
		}

		flushCookies();
		applyDefaultCacheControl();
		sink.status(responseCode);

		if (!contentTypeSet) {
			sink.header(HEADER_CONTENT_TYPE, "application/jsonl");
		}

		byte[] ln = "\n".getBytes(StandardCharsets.UTF_8);
		try (
			BufferedOutputStream bos = new BufferedOutputStream(sink.outputStream(), getIoBufferSize())
		) {
			boolean isFirst = true;
			for (Data json : jsonL) {
				if (isFirst) {
					isFirst = false;
				} else {
					bos.write(ln);
				}

				/*
				 * 1行ごとに作る。
				 * Configration は循環参照の記録を持っていて、
				 * <b>使い回すと2行目以降で組み立てが変わりうる</b>。
				 */
				Configration configration = new Configration();
				configration.isAutoClose(false);
				configration.tableNest(tableNest);

				json.outputJsonString(bos, configration);
			}
		} catch (Exception ex) {
			Log.error(ex, request, this);
			this.responseCode = 500;
			sink.status(500); sink.send();
		}

		afterResponse();

		return this;

	}

	/**
	 * レスポンス送信
	 *
	 * @param file          ファイル
	 * @param contentType   コンテンツタイプ
	 * @return  Response
	 */
	public Response send (File file, String contentType) {

		return send(file, contentType, null);

	}

	/**
	 * レスポンス送信
	 *
	 * @param file              ファイル
	 * @param contentType       コンテンツタイプ
	 * @param contentEncoding   コンテンツエンコーディング
	 * @return  Response
	 */
	public Response send (File file, String contentType, String contentEncoding) {

		if (sink.isSent()) {
			return this;
		}

		flushCookies();
		applyDefaultCacheControl();
		sink.status(responseCode);
		sink.header(HEADER_CONTENT_TYPE, contentType);
		if (contentEncoding != null) {
			sink.header("Content-Encoding", contentEncoding);
		}
		sink.header("Content-Length", file.length());
		try (
			FileInputStream fis = new FileInputStream(file);
			BufferedInputStream bis = new BufferedInputStream(fis, getIoBufferSize())
		) {
			sink.send(bis);
		} catch (Exception ex) {}

		afterResponse();

		return this;

	}

	/**
	 * レスポンス送信
	 *
	 * @param buff  バイト配列
	 * @return  Response
	 */
	public Response send (byte[] buff) {

		if (sink.isSent()) {
			return this;
		}

		flushCookies();
		applyDefaultCacheControl();
		sink.status(responseCode);
		sink.header("Content-Length", buff.length);
		sink.send(buff);

		afterResponse();

		return this;

	}

	// endregion

}
