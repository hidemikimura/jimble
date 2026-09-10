import type { Store } from './store.js';
import type { Router } from './router.js';
import type { App } from './app.js';
import type { Api } from './api.js';
import type { AppState, Path, PathValue } from './types.js';

/**
 * 描画コンテキスト
 *
 * <p>
 * SQLBuilder における {@code Dialect} に相当する。
 * Builder は自分では状態を持たず、描画時にこのコンテキストから値を引く。
 * </p>
 */
export class Context<S extends object = AppState> {

	/** アプリ */
	readonly app: App<S>;

	/** 状態ストア */
	readonly store: Store<S>;

	/** ルータ */
	readonly router: Router<S>;

	/** API クライアント */
	readonly api: Api | null;

	/** パスパラメータ */
	readonly params: Readonly<Record<string, string>>;

	/** クエリパラメータ */
	readonly query: Readonly<Record<string, string>>;

	constructor (args: {
		app: App<S>;
		store: Store<S>;
		router: Router<S>;
		api?: Api | null;
		params?: Record<string, string>;
		query?: Record<string, string>;
	}) {

		this.app = args.app;
		this.store = args.store;
		this.router = args.router;
		this.api = args.api ?? null;
		this.params = args.params ?? {};
		this.query = args.query ?? {};

	}

	/** 状態ストア（別名） */
	get state (): Store<S> {

		return this.store;

	}

	/**
	 * 状態を取得する
	 *
	 * @param path パス
	 * @return 値
	 */
	get<P extends Path<S> & string> (path: P): PathValue<S, P> {

		return this.store.get(path);

	}

	/**
	 * 状態を設定する
	 *
	 * @param path パス
	 * @param value 値
	 * @return コンテキスト
	 */
	set<P extends Path<S> & string> (path: P, value: PathValue<S, P>): this {

		this.store.set(path, value);
		return this;

	}

	/**
	 * 画面遷移する
	 *
	 * @param path パス
	 * @return コンテキスト
	 */
	go (path: string): this {

		this.router.go(path);
		return this;

	}

}
