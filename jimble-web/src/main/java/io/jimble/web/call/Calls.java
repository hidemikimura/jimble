package io.jimble.web.call;

import io.jimble.web.context.WebContext;
import io.jimble.web.http.RequestSource;

import java.util.function.Consumer;

/**
 * 内部呼び出しの組み立て（要件 F-W-27）
 *
 * <p>
 * <b>入れ物の作りと後始末をここに閉じる。</b>
 * 実際にルートを回すのは {@code Dispatcher} で、
 * ここはコンテキストを作って畳むところだけを持つ。
 * </p>
 */
public final class Calls {

	/** 入れ子の深さの上限 */
	public static final int MAX_DEPTH = 8;

	/* いまの深さ */
	private static final ScopedValue<Integer> DEPTH = ScopedValue.newInstance();

	/**
	 * コンストラクタ
	 */
	private Calls () {

	}

	/**
	 * 内部呼び出しを1本走らせる
	 *
	 * @param outer		外側のコンテキスト
	 * @param request	呼び出すもの
	 * @param body		内側のコンテキストを回す処理
	 * @return	結果
	 */
	public static CallResponse call (WebContext outer, CallRequest request, Consumer<WebContext> body) {

		int depth = DEPTH.isBound() ? DEPTH.get() : 0;

		/*
		 * 内部呼び出しの中からさらに内部呼び出しができる。
		 * 自分を呼ぶルートを1つ作ると<b>そのまま無限に潜る</b>ので、
		 * 上限で止める。StackOverflowError より先にここで気づける。
		 */
		if (depth >= MAX_DEPTH) {
			throw new IllegalStateException(
				"内部呼び出しが深すぎます（上限 %d）。呼び出しが循環していないか確かめてください: %s %s"
					.formatted(MAX_DEPTH, request.method(), request.path()));
		}

		RequestSource source = request.toSource(outer);
		CallSink sink = new CallSink();

		try (WebContext inner = WebContext.internal(source, sink, outer)) {
			ScopedValue.where(DEPTH, depth + 1).run(() -> body.accept(inner));
		}

		return sink.toResponse();

	}

}
