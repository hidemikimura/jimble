/**
 * 共有する型
 */

declare global {

	/**
	 * アプリの状態の型
	 *
	 * <p>
	 * アプリ側でこのインターフェースを拡張すると、
	 * 状態のパスとその値が<b>型で検査される</b>ようになる。
	 * </p>
	 *
	 * <pre>
	 * declare global {
	 *     interface JimbleAppState {
	 *         form: { name: string; age: number | null };
	 *         list: Staff[];
	 *     }
	 * }
	 *
	 * ctx.get('form.name');   // string
	 * ctx.get('form.nmae');   // コンパイルエラー
	 * </pre>
	 *
	 * <p>拡張しなければ何でも通る（型の恩恵は無い）。</p>
	 */
	interface JimbleAppState {
		[key: string]: any;
	}

}

/** アプリの状態の型 */
export type AppState = JimbleAppState;

/* 深さの上限（型の再帰を止めるため） */
type Prev = [never, 0, 1, 2, 3, 4, 5];

/**
 * 索引シグネチャを取り除いた「実際に宣言されたキー」だけを残す
 *
 * <p>
 * 既定の {@link JimbleAppState} は何でも通すために索引シグネチャを持つ。
 * アプリが拡張したときだけ、そこに書かれたキーで検査したいので、
 * 索引シグネチャ側は落とす。
 * </p>
 */
type KnownKeys<T> = {
	[K in keyof T as string extends K ? never : number extends K ? never : K]: T[K]
};

/* ドット区切りのパスを組み立てる */
type PathOf<T, D extends number = 5> =
	[D] extends [never] ? never :
	T extends readonly unknown[] ? never :
	T extends object ? {
		[K in keyof T & string]: NonNullable<T[K]> extends object
			? (NonNullable<T[K]> extends readonly unknown[] ? K : K | `${K}.${PathOf<NonNullable<T[K]>, Prev[D]>}`)
			: K
	}[keyof T & string] : never;

/**
 * ドット区切りで辿れるパスの型
 *
 * <p>
 * {@code 'form.name'} のような文字列を、存在する組み合わせだけに絞る。
 * 状態の型を宣言していない場合は、何でも通る（{@code string}）。
 * </p>
 */
export type Path<T, D extends number = 5> =
	keyof KnownKeys<T> extends never ? string : PathOf<KnownKeys<T>, D>;

/**
 * パスが指す値の型
 */
export type PathValue<T, P extends string> =
	P extends `${infer Head}.${infer Rest}`
		? Head extends keyof T ? PathValue<NonNullable<T[Head]>, Rest> : unknown
		: P extends keyof T ? T[P] : unknown;

/** 状態のパス */
export type StatePath = Path<AppState> & string;

/** 値そのもの、またはコンテキストから計算する関数 */
export type Resolvable<V, C> = V | ((ctx: C) => V);

/** 間隔・余白の大きさ */
export type Size = 'none' | 'xs' | 'sm' | 'md' | 'lg' | 'xl';

/** 交差軸の揃え */
export type Align = 'start' | 'center' | 'end' | 'stretch';

/** 主軸の揃え */
export type Justify = 'start' | 'center' | 'end' | 'between';

/**
 * 幅
 *
 * <p>
 * トークン（sm / md / lg / full）のほか、'220px' のような CSS の長さも書ける。
 * 長さを渡した場合は max-width として当たる。
 * </p>
 */
export type Width = 'sm' | 'md' | 'lg' | 'full' | (string & {});

/** 文字の種類 */
export type TextVariant = 'title' | 'heading' | 'body' | 'caption' | 'danger';

/**
 * ボタンの種類
 *
 * <p>{@code quiet-danger} は「枠なしの危険操作」。一覧の行から削除するときなどに使う。</p>
 */
export type ButtonVariant = 'default' | 'primary' | 'danger' | 'quiet' | 'quiet-danger';

/** 入力の種類 */
export type InputType = 'text' | 'number' | 'password' | 'email' | 'tel' | 'date' | 'time' | 'url';

/** 選択肢 */
export interface SelectOption {
	value: string;
	label: string;
	disabled?: boolean;
}

/** タブ */
export interface TabItem {
	value: string;
	label: string;
	badge?: string | number;
}

/** 表の列 */
export interface TableColumn {
	/** 列のキー */
	key: string;
	/** 見出し */
	label: string;
	/** 揃え */
	align?: 'start' | 'center' | 'end';
	/** 幅（CSS の値。テーマが style に流す） */
	width?: string;
	/** 並べ替えできるか */
	sortable?: boolean;
}

/** 表の行（セルはテーマがそのまま置く） */
export interface TableRow {
	/** 行の識別子 */
	key: string;
	/** セルの中身 */
	cells: unknown[];
}

/** 並び順 */
export type SortOrder = 'asc' | 'desc';

/** 知らせの種類 */
export type ToastVariant = 'info' | 'success' | 'warning' | 'danger';

/** ダイアログの大きさ */
export type DialogSize = 'sm' | 'md' | 'lg';

/** グラフの点 */
export interface ChartPoint {
	label: string;
	value: number;
}

/** グラフの種類 */
export type ChartType = 'bar' | 'line';

/** 増減の向き */
export type Trend = 'up' | 'down' | 'flat';

/** パンくずの 1 つ */
export interface BreadcrumbItem {
	label: string;
	/** 押したときの遷移先。省略すると押せない（現在地） */
	path?: string;
}

/** アコーディオンの 1 節 */
export interface AccordionSection {
	value: string;
	label: string;
	hint?: string;
}

/** 選ばれたファイルの情報 */
export interface FileInfo {
	name: string;
	size: number;
}

/** 飾りの大きさ */
export type IconSize = 'sm' | 'md' | 'lg';

/** ドロップダウンの 1 項目 */
export interface MenuItem {
	/** 選ばれたときに返る値 */
	value: string;
	/** 文言 */
	label: string;
	/** 飾り（jb-icon の名前） */
	icon?: string;
	/** 危険操作として目立たせるか */
	danger?: boolean;
	/** 選べないか */
	disabled?: boolean;
	/** 区切り線として出すか（label は無視される） */
	divider?: boolean;
}

/** 落とす位置 */
export type ReorderPosition = 'before' | 'after';

/** サイドバーの 1 項目 */
export interface NavItem {
	/** 選ばれたときに返る値。既定ではそのまま遷移先のパスになる */
	value: string;
	/** 文言 */
	label: string;
	/** 飾り（jb-icon の名前） */
	icon?: string;
	/** 右端の数字など */
	badge?: string | number;
	/** まとまりの見出し。同じ見出しが続く間は 1 度だけ出る */
	group?: string;
	/** 選べないか */
	disabled?: boolean;
}
