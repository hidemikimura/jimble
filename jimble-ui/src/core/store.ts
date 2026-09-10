import { fail, isDev, suggest } from './dev.js';
import type { AppState, Path, PathValue } from './types.js';

/**
 * 状態ストア
 *
 * <p>
 * 画面の状態はすべてここに入れる。値が変わると購読者へ通知し、App が再描画する。
 * DOM は状態の写像であり、アプリのコードが DOM を直接触ることはない。
 * </p>
 *
 * <p>
 * 開発モードでは、<b>初期状態に無いキー</b>を読み書きすると落とす。
 * {@code ctx.get('froms.name')} のような綴り違いを、黙って undefined にしない。
 * </p>
 */
export class Store<S extends object = AppState> {

	/**
	 * ストアを作成する
	 *
	 * @param initial 初期値
	 * @return ストア
	 */
	static of<T extends object> (initial: T): Store<T> {

		return new Store<T>(initial);

	}

	/* 保持データ */
	#data: S;

	/* 宣言済みのキー（初期状態のキー） */
	#declared: Set<string>;

	/* 購読者 */
	#subscribers = new Set<() => void>();

	/* 通知予約中か */
	#scheduled = false;

	/**
	 * コンストラクタ
	 *
	 * @param initial 初期値
	 */
	constructor (initial: S) {

		this.#data = initial;
		this.#declared = new Set(Object.keys(initial as object));

	}

	/** 保持データ全体 */
	get data (): S {

		return this.#data;

	}

	/**
	 * 値を取得する
	 *
	 * @param path ドット区切りのパス
	 * @return 値
	 */
	get<P extends Path<S> & string> (path: P): PathValue<S, P>;
	get (): S;
	get (path?: string): unknown {

		if (path == null || path === '') {
			return this.#data;
		}

		this.#check(path, '読み');

		return path.split('.').reduce<unknown>(
			(owner, key) => (owner == null ? undefined : (owner as Record<string, unknown>)[key]),
			this.#data
		);

	}

	/**
	 * 値を設定する
	 *
	 * <p>途中のオブジェクトが無い場合は作る。</p>
	 *
	 * @param path ドット区切りのパス
	 * @param value 値
	 * @return ストア
	 */
	set<P extends Path<S> & string> (path: P, value: PathValue<S, P>): this {

		this.#check(path, '書き');

		const keys = path.split('.');
		const last = keys.pop() as string;

		let cursor = this.#data as Record<string, unknown>;
		for (const key of keys) {
			if (cursor[key] == null || typeof cursor[key] !== 'object') {
				cursor[key] = {};
			}
			cursor = cursor[key] as Record<string, unknown>;
		}
		cursor[last] = value;

		return this.notify();

	}

	/**
	 * 値をまとめて設定する
	 *
	 * @param values 値
	 * @return ストア
	 */
	patch (values: Partial<S>): this {

		for (const key of Object.keys(values)) {
			this.#check(key, '書き');
		}

		Object.assign(this.#data as object, values);
		return this.notify();

	}

	/**
	 * 状態のキーを宣言する
	 *
	 * <p>初期状態に無いキーを後から足すときに使う。</p>
	 *
	 * @param key キー
	 * @param value 初期値
	 * @return ストア
	 */
	declare (key: string, value?: unknown): this {

		this.#declared.add(key);
		(this.#data as Record<string, unknown>)[key] = value;
		return this.notify();

	}

	/**
	 * 任意の更新を行う
	 *
	 * @param updater 更新処理
	 * @return ストア
	 */
	update (updater: (data: S) => void): this {

		updater(this.#data);
		return this.notify();

	}

	/**
	 * 変更を購読する
	 *
	 * @param listener リスナ
	 * @return 解除関数
	 */
	subscribe (listener: () => void): () => void {

		this.#subscribers.add(listener);
		return () => this.#subscribers.delete(listener);

	}

	/**
	 * 変更を通知する
	 *
	 * <p>1 マイクロタスク内の複数更新はまとめて 1 回にする。</p>
	 *
	 * @return ストア
	 */
	notify (): this {

		if (this.#scheduled) {
			return this;
		}
		this.#scheduled = true;

		queueMicrotask(() => {
			this.#scheduled = false;
			for (const listener of this.#subscribers) {
				listener();
			}
		});

		return this;

	}

	/**
	 * 宣言済みのキーかどうか確かめる
	 *
	 * @param path パス
	 * @param operation 操作の名前
	 */
	#check (path: string, operation: string): void {

		if (!isDev()) {
			return;
		}

		const root = path.split('.')[0] as string;
		if (this.#declared.has(root)) {
			return;
		}

		fail(
			'状態 "' + path + '" を' + operation + '出そうとしましたが、"' + root + '" は初期状態に宣言されていません',
			suggest(root, [...this.#declared])
				?? 'App.of().state({ ' + root + ': ... }) に足すか、store.declare("' + root + '") を呼んでください'
		);

	}

}
