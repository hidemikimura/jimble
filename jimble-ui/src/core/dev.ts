/**
 * 開発時の診断
 *
 * <p>
 * このフレームワークは「間違いに気づけること」を最優先にする。
 * 開発モードでは <b>すぐ例外を投げ、画面にも赤く出す</b>。
 * 本番モードでは console にだけ出し、画面は落とさない。
 * </p>
 */

/** 動作モード */
export type Mode = 'development' | 'production';

/* 現在のモード */
let MODE: Mode = 'development';

/**
 * 動作モードを設定する
 *
 * @param mode モード
 */
export function setMode (mode: Mode): void {

	MODE = mode;

}

/**
 * 現在の動作モードを取得する
 *
 * @return モード
 */
export function mode (): Mode {

	return MODE;

}

/**
 * 開発モードかどうか
 *
 * @return 開発モードなら true
 */
export function isDev (): boolean {

	return MODE === 'development';

}

/**
 * このフレームワークが投げる例外
 */
export class JimbleError extends Error {

	/** 直し方のヒント */
	readonly hint: string | undefined;

	constructor (message: string, hint?: string) {

		super(hint == null ? message : message + '\n  → ' + hint);
		this.name = 'JimbleError';
		this.hint = hint;

	}

}

/**
 * 使い方の誤りとして落とす
 *
 * <p>開発モードでは例外を投げ、本番モードでは警告に留める。</p>
 *
 * @param message 何が起きたか
 * @param hint 直し方
 */
export function fail (message: string, hint?: string): void {

	const error = new JimbleError(message, hint);

	if (isDev()) {
		report(error);
		throw error;
	}

	console.error('[jimble-ui] ' + error.message);

}

/**
 * 警告する（落とさない）
 *
 * @param message 何が起きたか
 * @param hint 直し方
 */
export function warn (message: string, hint?: string): void {

	console.warn('[jimble-ui] ' + message + (hint == null ? '' : '\n  → ' + hint));

}

/**
 * 例外を記録する
 *
 * <p>開発モードでは画面にも赤いパネルで出す。</p>
 *
 * @param error 例外
 * @param context どこで起きたか
 */
export function report (error: unknown, context?: string): void {

	const message = error instanceof Error ? error.message : String(error);
	console.error('[jimble-ui] ' + (context == null ? '' : context + ': ') + message, error);

	if (isDev()) {
		showOverlay(message, context);
	}

}

/* 画面に出した文言（重複を出さないため） */
const SHOWN = new Set<string>();

/**
 * 画面にエラーパネルを出す
 *
 * <p>同じ内容は 1 回だけ出す（同じ誤りが再描画のたびに増えないように）。</p>
 *
 * @param message 文言
 * @param context どこで起きたか
 */
function showOverlay (message: string, context?: string): void {

	if (typeof document === 'undefined' || SHOWN.has(message)) {
		return;
	}
	SHOWN.add(message);

	let panel = document.getElementById('jb-error-overlay');
	if (panel == null) {
		panel = document.createElement('div');
		panel.id = 'jb-error-overlay';
		panel.setAttribute('style', [
			'position:fixed', 'left:12px', 'right:12px', 'bottom:12px', 'z-index:2147483647',
			'max-height:45vh', 'overflow:auto', 'padding:12px 14px',
			'background:#4c0519', 'color:#ffe4e6', 'border:1px solid #f43f5e', 'border-radius:8px',
			'font:12px/1.6 ui-monospace, SFMono-Regular, Menlo, monospace', 'white-space:pre-wrap'
		].join(';'));

		const close = document.createElement('button');
		close.textContent = '×';
		close.setAttribute('style', 'position:absolute;top:6px;right:10px;background:none;border:0;color:inherit;font-size:16px;cursor:pointer');
		close.addEventListener('click', () => {
			panel?.remove();
			SHOWN.clear();
		});
		panel.appendChild(close);

		document.body.appendChild(panel);
	}

	const line = document.createElement('div');
	line.setAttribute('style', 'margin:0 18px 8px 0');
	line.textContent = '[jimble-ui] ' + (context == null ? '' : context + ': ') + message;
	panel.appendChild(line);

}

/**
 * 似ている名前を探す
 *
 * <p>「onSubmit はありません。もしかして onChange？」を出すため。</p>
 *
 * @param input 入力された名前
 * @param candidates 候補
 * @return ヒント文言
 */
export function suggest (input: string, candidates: readonly string[]): string | undefined {

	const near = candidates
		.map((candidate) => ({ candidate, distance: distance(input.toLowerCase(), candidate.toLowerCase()) }))
		.filter((entry) => entry.distance <= Math.max(2, Math.floor(input.length / 3)))
		.sort((a, b) => a.distance - b.distance)
		.slice(0, 3)
		.map((entry) => entry.candidate);

	if (near.length === 0) {
		return candidates.length === 0 ? undefined : '使えるのは: ' + candidates.slice(0, 12).join(' / ');
	}
	return 'もしかして: ' + near.join(' / ');

}

/**
 * 編集距離を求める
 *
 * @param a 文字列
 * @param b 文字列
 * @return 距離
 */
function distance (a: string, b: string): number {

	const rows: number[][] = [];

	for (let i = 0; i <= a.length; i++) {
		rows[i] = [i];
	}
	for (let j = 0; j <= b.length; j++) {
		rows[0]![j] = j;
	}

	for (let i = 1; i <= a.length; i++) {
		for (let j = 1; j <= b.length; j++) {
			rows[i]![j] = Math.min(
				rows[i - 1]![j]! + 1,
				rows[i]![j - 1]! + 1,
				rows[i - 1]![j - 1]! + (a[i - 1] === b[j - 1] ? 0 : 1)
			);
		}
	}

	return rows[a.length]![b.length]!;

}

/**
 * 存在しないメソッドの呼び出しを検出する見張りを付ける
 *
 * <p>
 * 開発モードのみ。AI や補完が生み出した「それらしいが存在しないメソッド」を、
 * 黙って無視せずその場で落とす。
 * </p>
 *
 * @param target 対象
 * @return 見張り付きの対象
 */
export function guard<T extends object> (target: T): T {

	if (!isDev()) {
		return target;
	}

	const proxy: T = new Proxy(target, {

		get (owner, property, receiver) {

			if (typeof property === 'string'
				&& !(property in owner)
				&& property !== 'then'
				&& property !== 'toJSON'
				&& !property.startsWith('_')) {

				fail(
					owner.constructor.name + ' に ' + property + ' はありません',
					suggest(property, membersOf(owner))
				);

			}

			const value = Reflect.get(owner, property, receiver);

			/* メソッドが this を返したら、見張りを外さないように包み直す */
			if (typeof value === 'function') {
				return function (this: unknown, ...args: unknown[]): unknown {
					const result = (value as (...a: unknown[]) => unknown).apply(owner, args);
					return result === owner ? proxy : result;
				};
			}

			return value;

		}

	});

	return proxy;

}

/**
 * 呼べるメソッド名を集める
 *
 * <p>内部で持っているだけの値は候補に出さない（プロトタイプ側だけを見る）。</p>
 *
 * @param target 対象
 * @return メソッド名
 */
function membersOf (target: object): string[] {

	/* 実装の都合で公開されているだけのものは候補に出さない */
	const internal = new Set(['template', 'renderChildren', 'accept', 'click', 'emit']);

	const names = new Set<string>();

	for (
		let owner: object | null = Object.getPrototypeOf(target);
		owner != null && owner !== Object.prototype;
		owner = Object.getPrototypeOf(owner)
	) {
		for (const name of Object.getOwnPropertyNames(owner)) {
			if (name !== 'constructor' && !name.startsWith('_') && !internal.has(name)) {
				names.add(name);
			}
		}
	}

	return [...names].sort();

}
