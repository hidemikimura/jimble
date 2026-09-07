package io.jimble.web.router;

import java.util.List;

/**
 * ルートのマッチ結果
 *
 * <p>
 * <b>フックはマッチが確定したノード列からのみ組み立てる。</b>
 * 探索の途中で通っただけの枝のフックは含まない。
 * </p>
 *
 * <ul>
 *     <li>{@code beforeHooks} — 外側 → 内側</li>
 *     <li>{@code afterHooks} — 内側 → 外側</li>
 *     <li>{@code errorHooks} — 内側 → 外側</li>
 * </ul>
 */
public final class RouteMatch {

	/* ルート */
	private final Route route;

	/* パス変数 */
	private final PathVariables variables;

	/* before */
	private final List<Handler> beforeHooks;

	/* after */
	private final List<Handler> afterHooks;

	/* error */
	private final List<ErrorHandler> errorHooks;

	/**
	 * コンストラクタ
	 *
	 * @param route			ルート。未マッチなら null
	 * @param variables		パス変数
	 * @param beforeHooks	before
	 * @param afterHooks	after
	 * @param errorHooks	error
	 */
	RouteMatch (
		Route route
		, PathVariables variables
		, List<Handler> beforeHooks
		, List<Handler> afterHooks
		, List<ErrorHandler> errorHooks
	) {

		this.route = route;
		this.variables = variables;
		this.beforeHooks = List.copyOf(beforeHooks);
		this.afterHooks = List.copyOf(afterHooks);
		this.errorHooks = List.copyOf(errorHooks);

	}

	/**
	 * マッチしたか
	 *
	 * @return	マッチしていれば true
	 */
	public boolean matched () {

		return route != null;

	}

	/**
	 * ルート
	 *
	 * @return	ルート。未マッチなら null
	 */
	public Route route () {

		return route;

	}

	/**
	 * パス変数
	 *
	 * @return	パス変数
	 */
	public PathVariables variables () {

		return variables;

	}

	/**
	 * before（外側 → 内側）
	 *
	 * @return	before
	 */
	public List<Handler> beforeHooks () {

		return beforeHooks;

	}

	/**
	 * after（内側 → 外側）
	 *
	 * @return	after
	 */
	public List<Handler> afterHooks () {

		return afterHooks;

	}

	/**
	 * error（内側 → 外側）
	 *
	 * @return	error
	 */
	public List<ErrorHandler> errorHooks () {

		return errorHooks;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString () {

		if (!matched()) {
			return "RouteMatch(未マッチ)";
		}

		return "RouteMatch(%s %s)".formatted(route, variables);

	}

}
