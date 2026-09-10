import { render as litRender } from '../../vendor/lit.js';
import { Store } from './store.js';
import { Router } from './router.js';
import { Context } from './context.js';
import { renderNode } from './builder.js';
import { Theme, useTheme, currentTheme } from './theme.js';
import { report, setMode } from './dev.js';
import type { Mode } from './dev.js';
import type { Node } from './builder.js';
import type { View, RouteOptions, RouterMode, Route } from './router.js';
import type { ThemeDefinition } from './theme.js';
import type { Api } from './api.js';
import type { AppState } from './types.js';
import '../components/index.js';

/** 共通レイアウト */
export type Layout<S extends object> = (page: Node<S>, ctx: Context<S>) => Node<S>;

/**
 * アプリケーション
 *
 * <p>
 * SQL における {@code SQL} クラスと同じく、ここが入口になる。
 * 画面は「コンテキストを受け取ってビルダーを返す関数」で書く。
 * </p>
 *
 * <pre>
 * App.of()
 *     .state({ count: 0 })
 *     .route('/', HomePage)
 *     .route('/staff/:id', StaffPage)
 *     .mount();
 * </pre>
 */
export class App<S extends object = AppState> {

	/**
	 * アプリを作成する
	 *
	 * @return アプリ
	 */
	static of (): App {

		return new App();

	}

	/* 状態ストア */
	#store: Store<S> = Store.of({} as S);

	/* ルータ */
	#router = new Router<S>();

	/* テーマ */
	#theme: string | ThemeDefinition | Theme = 'original';

	/* テーマトークンの上書き */
	#tokens: Record<string, string> | undefined = undefined;

	/* API クライアント */
	#api: Api | null = null;

	/* 描画先 */
	#root: HTMLElement | null = null;

	/* 共通レイアウト */
	#layout: Layout<S> | null = null;

	/* 現在の描画対象 */
	#current: { route: Route<S> | null; ctx: Context<S> | null } = { route: null, ctx: null };

	/* 描画予約中か */
	#scheduled = false;

	/**
	 * 初期状態を設定する
	 *
	 * <p>ここに書いたキーだけが読み書きできる（開発モード）。</p>
	 *
	 * @param initial 初期状態
	 * @return アプリ
	 */
	state (initial: S): this {

		this.#store = Store.of(initial);
		return this;

	}

	/**
	 * 動作モードを設定する
	 *
	 * <p>
	 * development … 誤りを見つけたら即例外＋画面に赤く表示（既定）<br>
	 * production … console に警告を出すだけで画面は落とさない
	 * </p>
	 *
	 * @param mode モード
	 * @return アプリ
	 */
	mode (mode: Mode): this {

		setMode(mode);
		return this;

	}

	/**
	 * テーマを設定する
	 *
	 * @param theme テーマ名 / 定義 / テーマ
	 * @param tokens 上書きするトークン
	 * @return アプリ
	 */
	theme (theme: string | ThemeDefinition | Theme, tokens?: Record<string, string>): this {

		this.#theme = theme;
		this.#tokens = tokens;
		return this;

	}

	/**
	 * テーマを切り替える（起動後）
	 *
	 * @param theme テーマ
	 * @param tokens 上書きするトークン
	 * @return テーマ
	 */
	async useTheme (theme: string | ThemeDefinition | Theme, tokens?: Record<string, string>): Promise<Theme> {

		this.#theme = theme;
		this.#tokens = tokens;

		const applied = await useTheme(theme, tokens);
		this.#store.notify();
		return applied;

	}

	/** 現在のテーマ名 */
	get themeName (): string {

		return currentTheme().name;

	}

	/**
	 * API クライアントを設定する
	 *
	 * @param api クライアント
	 * @return アプリ
	 */
	api (api: Api): this {

		this.#api = api;
		return this;

	}

	/**
	 * ルーティング方式を設定する
	 *
	 * @param mode hash | history
	 * @return アプリ
	 */
	routing (mode: RouterMode): this {

		this.#router.mode(mode);
		return this;

	}

	/**
	 * 画面を追加する
	 *
	 * @param path パス
	 * @param view 画面を作る関数
	 * @param options オプション（enter: 表示時に実行する非同期処理）
	 * @return アプリ
	 */
	route (path: string, view: View<S>, options?: RouteOptions<S>): this {

		this.#router.route(path, view, options);
		return this;

	}

	/**
	 * 見つからない場合の画面を設定する
	 *
	 * @param view 画面を作る関数
	 * @return アプリ
	 */
	notFound (view: View<S>): this {

		this.#router.notFound(view);
		return this;

	}

	/**
	 * 共通レイアウトを設定する
	 *
	 * @param layout 画面を包む関数
	 * @return アプリ
	 */
	layout (layout: Layout<S>): this {

		this.#layout = layout;
		return this;

	}

	/**
	 * 起動する
	 *
	 * @param target 描画先。省略時は body 直下に作る
	 * @return アプリ
	 */
	mount (target?: string | HTMLElement): this {

		if (target == null) {
			this.#root = document.createElement('div');
			document.body.appendChild(this.#root);
		} else if (typeof target === 'string') {
			this.#root = document.querySelector<HTMLElement>(target);
		} else {
			this.#root = target;
		}

		if (this.#root == null) {
			throw new Error('[jimble-ui] 描画先が見つかりません: ' + String(target));
		}

		this.#store.subscribe(() => this.#schedule());

		/* テーマ（CSS）の読み込みを待ってから最初の描画を行う */
		useTheme(this.#theme, this.#tokens)
			.then(() => this.#router.start(() => void this.#enter()))
			.catch((error: unknown) => report(error, 'テーマの適用'));

		return this;

	}

	/** 状態ストア */
	get store (): Store<S> {

		return this.#store;

	}

	/** ルータ */
	get router (): Router<S> {

		return this.#router;

	}

	/**
	 * 再描画を予約する
	 */
	#schedule (): void {

		if (this.#scheduled) {
			return;
		}
		this.#scheduled = true;

		requestAnimationFrame(() => {
			this.#scheduled = false;
			this.#draw();
		});

	}

	/**
	 * ルート遷移時の処理
	 */
	async #enter (): Promise<void> {

		const { route, params, query } = this.#router.resolve();
		const ctx = new Context<S>({
			app: this,
			store: this.#store,
			router: this.#router,
			api: this.#api,
			params,
			query
		});

		this.#current = { route, ctx };
		this.#draw();

		const enter = route?.options?.enter;
		if (typeof enter === 'function') {
			try {
				await enter(ctx);
			} catch (error) {
				report(error, 'enter (' + (route?.pattern ?? '') + ')');
			}
			this.#draw();
		}

	}

	/**
	 * 描画する
	 */
	#draw (): void {

		const { route, ctx } = this.#current;
		if (ctx == null || this.#root == null) {
			return;
		}

		try {
			let node: Node<S> = route == null ? 'ページが見つかりません' : route.view(ctx);
			if (this.#layout != null) {
				node = this.#layout(node, ctx);
			}
			litRender(renderNode(node, ctx), this.#root);
		} catch (error) {
			report(error, '描画 (' + (route?.pattern ?? '?') + ')');
		}

	}

}
