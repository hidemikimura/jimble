import { fail, isDev } from './dev.js';
import type { Context } from './context.js';
import type { Node } from './builder.js';
import type { AppState } from './types.js';

/** 画面を作る関数 */
export type View<S extends object = AppState> = (ctx: Context<S>) => Node<S>;

/** ルートのオプション */
export interface RouteOptions<S extends object = AppState> {
	/** 表示前に実行する処理（データ取得など） */
	enter?: (ctx: Context<S>) => unknown;
}

/** 登録したルート */
export interface Route<S extends object = AppState> {
	pattern: string;
	view: View<S>;
	options: RouteOptions<S>;
	keys: string[];
	regex: RegExp;
}

/** 解決結果 */
export interface Resolved<S extends object = AppState> {
	route: Route<S> | null;
	params: Record<string, string>;
	query: Record<string, string>;
}

/** ルーティング方式 */
export type RouterMode = 'hash' | 'history';

/**
 * ルータ
 *
 * <p>
 * 既定はハッシュ方式（{@code #/staff/1}）。サーバ設定なしで動く。
 * {@code mode('history')} で pushState 方式に切り替えられる。
 * </p>
 */
export class Router<S extends object = AppState> {

	/* ルート一覧 */
	#routes: Route<S>[] = [];

	/* 見つからない場合 */
	#notFound: View<S> | null = null;

	/* 方式 */
	#mode: RouterMode = 'hash';

	/* 変更ハンドラ */
	#handler: (() => void) | null = null;

	/**
	 * 方式を設定する
	 *
	 * @param mode hash | history
	 * @return ルータ
	 */
	mode (mode: RouterMode): this {

		this.#mode = mode;
		return this;

	}

	/**
	 * ルートを追加する
	 *
	 * @param pattern パターン（例: /staff/:id）
	 * @param view 画面を作る関数
	 * @param options オプション
	 * @return ルータ
	 */
	route (pattern: string, view: View<S>, options: RouteOptions<S> = {}): this {

		const keys: string[] = [];
		const source = String(pattern)
			.replace(/\/$/, '')
			.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
			.replace(/\/:([A-Za-z0-9_]+)/g, (_match, key: string) => {
				keys.push(key);
				return '/([^/]+)';
			});

		this.#routes.push({
			pattern,
			view,
			options,
			keys,
			regex: new RegExp('^' + (source === '' ? '/' : source) + '/?$')
		});

		return this;

	}

	/**
	 * 見つからない場合の画面を設定する
	 *
	 * @param view 画面を作る関数
	 * @return ルータ
	 */
	notFound (view: View<S>): this {

		this.#notFound = view;
		return this;

	}

	/** 登録済みのパターン一覧 */
	get patterns (): string[] {

		return this.#routes.map((route) => route.pattern);

	}

	/**
	 * 現在のパスを取得する
	 *
	 * @return パス
	 */
	path (): string {

		if (this.#mode === 'history') {
			return location.pathname + location.search;
		}
		return location.hash.replace(/^#/, '') || '/';

	}

	/**
	 * 現在のルートを解決する
	 *
	 * @return 解決結果
	 */
	resolve (): Resolved<S> {

		const [path, search] = this.path().split('?');

		const query: Record<string, string> = {};
		for (const [key, value] of new URLSearchParams(search ?? '')) {
			query[key] = value;
		}

		const matched = this.#match(path ?? '/');
		if (matched != null) {
			return { ...matched, query };
		}

		return {
			route: this.#notFound == null
				? null
				: { pattern: '(notFound)', view: this.#notFound, options: {}, keys: [], regex: /^$/ },
			params: {},
			query
		};

	}

	/**
	 * 画面遷移する
	 *
	 * @param path パス
	 * @param options replace: 履歴を置換する
	 * @return ルータ
	 */
	go (path: string, options: { replace?: boolean } = {}): this {

		if (isDev() && this.#match((path.split('?')[0]) ?? '/') == null) {
			fail(
				'"' + path + '" に遷移しようとしましたが、そのルートは登録されていません',
				'登録済み: ' + (this.patterns.length === 0 ? '(なし)' : this.patterns.join(' / '))
			);
		}

		if (this.#mode === 'history') {
			if (options.replace) {
				history.replaceState(null, '', path);
			} else {
				history.pushState(null, '', path);
			}
			this.#handler?.();
			return this;
		}

		const hash = '#' + path;
		if (options.replace) {
			history.replaceState(null, '', hash);
			this.#handler?.();
		} else {
			location.hash = hash;
		}
		return this;

	}

	/**
	 * 監視を開始する
	 *
	 * @param handler 変更ハンドラ
	 * @return ルータ
	 */
	start (handler: () => void): this {

		this.#handler = handler;
		window.addEventListener(this.#mode === 'history' ? 'popstate' : 'hashchange', handler);
		handler();
		return this;

	}

	/**
	 * 監視を終了する
	 *
	 * @return ルータ
	 */
	stop (): this {

		if (this.#handler != null) {
			window.removeEventListener(this.#mode === 'history' ? 'popstate' : 'hashchange', this.#handler);
			this.#handler = null;
		}
		return this;

	}

	/**
	 * パスに一致するルートを探す
	 *
	 * @param path パス
	 * @return 一致したルートとパラメータ
	 */
	#match (path: string): { route: Route<S>; params: Record<string, string> } | null {

		for (const route of this.#routes) {
			const matched = route.regex.exec(path || '/');
			if (matched == null) {
				continue;
			}

			const params: Record<string, string> = {};
			route.keys.forEach((key, index) => {
				params[key] = decodeURIComponent(matched[index + 1] as string);
			});

			return { route, params };
		}

		return null;

	}

}
