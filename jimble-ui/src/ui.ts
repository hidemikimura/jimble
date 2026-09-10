import { StackBuilder } from './builders/stack.js';
import { LabelBuilder } from './builders/label.js';
import { InputBuilder } from './builders/input.js';
import { ButtonBuilder } from './builders/button.js';
import { EachBuilder } from './builders/each.js';
import { SelectBuilder } from './builders/select.js';
import { CheckboxBuilder } from './builders/checkbox.js';
import { RadioBuilder } from './builders/radio.js';
import { TabsBuilder } from './builders/tabs.js';
import { TableBuilder } from './builders/table.js';
import { PaginationBuilder } from './builders/pagination.js';
import { DialogBuilder } from './builders/dialog.js';
import { FormBuilder } from './builders/form.js';
import { GridBuilder } from './builders/grid.js';
import { StatBuilder } from './builders/stat.js';
import { ChartBuilder } from './builders/chart.js';
import { FileBuilder } from './builders/file.js';
import { DateRangeBuilder } from './builders/daterange.js';
import { BreadcrumbBuilder } from './builders/breadcrumb.js';
import { AccordionBuilder } from './builders/accordion.js';
import { EmptyBuilder } from './builders/empty.js';
import { IconBuilder } from './builders/icon.js';
import { MenuBuilder } from './builders/menu.js';
import { SidebarBuilder } from './builders/sidebar.js';
import { PageHeaderBuilder } from './builders/page-header.js';
import { guard } from './core/dev.js';
import type { Node } from './core/builder.js';
import type { Context } from './core/context.js';
import type { AppState, BreadcrumbItem, ChartPoint, Resolvable, TabItem } from './core/types.js';

/**
 * UI ビルダーの入口
 *
 * <p>
 * SQL における {@code SQL} クラスと同じ位置づけ。
 * ここから始めれば HTML も CSS も書かずに画面が組める。
 * </p>
 *
 * <p>
 * 開発モードでは、返ってくるビルダーに見張りが付く。
 * 存在しないメソッドを呼ぶと、その場で例外＋画面表示になる。
 * </p>
 */
export const UI = {

	/* ---------------- レイアウト ---------------- */

	/**
	 * 縦に積む
	 *
	 * @param children 子要素
	 * @return ビルダー
	 */
	column<S extends object = AppState> (...children: Node<S>[]): StackBuilder<S> {

		return guard(new StackBuilder<S>('column', children));

	},

	/**
	 * 横に並べる
	 *
	 * @param children 子要素
	 * @return ビルダー
	 */
	row<S extends object = AppState> (...children: Node<S>[]): StackBuilder<S> {

		return guard(new StackBuilder<S>('row', children));

	},

	/**
	 * カードを並べる（グリッド）
	 *
	 * @param children 子要素
	 * @return ビルダー
	 */
	grid<S extends object = AppState> (...children: Node<S>[]): GridBuilder<S> {

		return guard(new GridBuilder<S>(children));

	},

	/**
	 * カード（面）にまとめる
	 *
	 * @param children 子要素
	 * @return ビルダー
	 */
	card<S extends object = AppState> (...children: Node<S>[]): StackBuilder<S> {

		return guard(new StackBuilder<S>('column', children).surface());

	},

	/* ---------------- 表示 ---------------- */

	/**
	 * 本文
	 *
	 * @param text 文言
	 * @return ビルダー
	 */
	label<S extends object = AppState> (text: Resolvable<string | number | null | undefined, Context<S>>): LabelBuilder<S> {

		return guard(new LabelBuilder<S>(text));

	},

	/**
	 * 大見出し
	 *
	 * @param text 文言
	 * @return ビルダー
	 */
	title<S extends object = AppState> (text: Resolvable<string | number | null | undefined, Context<S>>): LabelBuilder<S> {

		return guard(new LabelBuilder<S>(text, 'title'));

	},

	/**
	 * 見出し
	 *
	 * @param text 文言
	 * @return ビルダー
	 */
	heading<S extends object = AppState> (text: Resolvable<string | number | null | undefined, Context<S>>): LabelBuilder<S> {

		return guard(new LabelBuilder<S>(text, 'heading'));

	},

	/**
	 * 注釈
	 *
	 * @param text 文言
	 * @return ビルダー
	 */
	caption<S extends object = AppState> (text: Resolvable<string | number | null | undefined, Context<S>>): LabelBuilder<S> {

		return guard(new LabelBuilder<S>(text, 'caption'));

	},

	/* ---------------- 入力 ---------------- */

	/**
	 * 文字入力
	 *
	 * @param name 項目名
	 * @return ビルダー
	 */
	text<S extends object = AppState> (name: string): InputBuilder<S> {

		return guard(new InputBuilder<S>(name, 'text'));

	},

	/**
	 * 数値入力
	 *
	 * @param name 項目名
	 * @return ビルダー
	 */
	number<S extends object = AppState> (name: string): InputBuilder<S> {

		return guard(new InputBuilder<S>(name, 'number'));

	},

	/**
	 * パスワード入力
	 *
	 * @param name 項目名
	 * @return ビルダー
	 */
	password<S extends object = AppState> (name: string): InputBuilder<S> {

		return guard(new InputBuilder<S>(name, 'password'));

	},

	/**
	 * 日付入力
	 *
	 * @param name 項目名
	 * @return ビルダー
	 */
	date<S extends object = AppState> (name: string): InputBuilder<S> {

		return guard(new InputBuilder<S>(name, 'date'));

	},

	/**
	 * 複数行入力
	 *
	 * @param name 項目名
	 * @return ビルダー
	 */
	textarea<S extends object = AppState> (name: string): InputBuilder<S> {

		return guard(new InputBuilder<S>(name, 'text').multiline());

	},

	/**
	 * 選択
	 *
	 * @param name 項目名
	 * @return ビルダー
	 */
	select<S extends object = AppState> (name: string): SelectBuilder<S> {

		return guard(new SelectBuilder<S>(name));

	},

	/**
	 * チェックボックス
	 *
	 * @param name 項目名
	 * @return ビルダー
	 */
	checkbox<S extends object = AppState> (name: string): CheckboxBuilder<S> {

		return guard(new CheckboxBuilder<S>(name, 'jb-checkbox'));

	},

	/**
	 * スイッチ
	 *
	 * @param name 項目名
	 * @return ビルダー
	 */
	toggle<S extends object = AppState> (name: string): CheckboxBuilder<S> {

		return guard(new CheckboxBuilder<S>(name, 'jb-switch'));

	},

	/**
	 * ラジオ
	 *
	 * @param name 項目名
	 * @return ビルダー
	 */
	radio<S extends object = AppState> (name: string): RadioBuilder<S> {

		return guard(new RadioBuilder<S>(name));

	},

	/**
	 * ボタン
	 *
	 * @param label 文言
	 * @return ビルダー
	 */
	button<S extends object = AppState> (label: Resolvable<string, Context<S>>): ButtonBuilder<S> {

		return guard(new ButtonBuilder<S>(label));

	},

	/**
	 * フォーム（中で Enter が押されたら送信）
	 *
	 * @param children 子要素
	 * @return ビルダー
	 */
	form<S extends object = AppState> (...children: Node<S>[]): FormBuilder<S> {

		return guard(new FormBuilder<S>(children));

	},

	/* ---------------- 一覧・画面部品 ---------------- */

	/**
	 * タブ
	 *
	 * @param tabs タブ一覧
	 * @return ビルダー
	 */
	tabs<S extends object = AppState> (tabs: Resolvable<TabItem[], Context<S>>): TabsBuilder<S> {

		return guard(new TabsBuilder<S>(tabs));

	},

	/**
	 * 表
	 *
	 * @param items 行にするデータ
	 * @return ビルダー
	 */
	table<T, S extends object = AppState> (items: Resolvable<readonly T[], Context<S>>): TableBuilder<T, S> {

		return guard(new TableBuilder<T, S>(items));

	},

	/**
	 * ページ送り
	 *
	 * @return ビルダー
	 */
	pagination<S extends object = AppState> (): PaginationBuilder<S> {

		return guard(new PaginationBuilder<S>());

	},

	/**
	 * ダイアログ
	 *
	 * @param children 子要素
	 * @return ビルダー
	 */
	dialog<S extends object = AppState> (...children: Node<S>[]): DialogBuilder<S> {

		return guard(new DialogBuilder<S>(children));

	},

	/**
	 * ファイル添付
	 *
	 * @param name 項目名
	 * @return ビルダー
	 */
	file<S extends object = AppState> (name: string): FileBuilder<S> {

		return guard(new FileBuilder<S>(name));

	},

	/**
	 * 日付範囲
	 *
	 * @param name 項目名
	 * @return ビルダー
	 */
	dateRange<S extends object = AppState> (name: string): DateRangeBuilder<S> {

		return guard(new DateRangeBuilder<S>(name));

	},

	/**
	 * 統計タイル
	 *
	 * @param label 見出し
	 * @param value 値
	 * @return ビルダー
	 */
	stat<S extends object = AppState> (
		label: Resolvable<string, Context<S>>,
		value: Resolvable<string | number, Context<S>>
	): StatBuilder<S> {

		return guard(new StatBuilder<S>(label, value));

	},

	/**
	 * グラフ（棒 / 折れ線）
	 *
	 * @param points 点の並び
	 * @return ビルダー
	 */
	chart<S extends object = AppState> (points: Resolvable<ChartPoint[], Context<S>>): ChartBuilder<S> {

		return guard(new ChartBuilder<S>(points));

	},

	/**
	 * パンくず
	 *
	 * @param items 並び
	 * @return ビルダー
	 */
	breadcrumb<S extends object = AppState> (items: Resolvable<BreadcrumbItem[], Context<S>>): BreadcrumbBuilder<S> {

		return guard(new BreadcrumbBuilder<S>(items));

	},

	/**
	 * アコーディオン
	 *
	 * @return ビルダー
	 */
	accordion<S extends object = AppState> (): AccordionBuilder<S> {

		return guard(new AccordionBuilder<S>());

	},

	/**
	 * 空状態
	 *
	 * @param heading 見出し
	 * @return ビルダー
	 */
	empty<S extends object = AppState> (heading: Resolvable<string, Context<S>>): EmptyBuilder<S> {

		return guard(new EmptyBuilder<S>(heading));

	},

	/**
	 * 飾り（アイコン）
	 *
	 * @param name 名前（plus / edit / trash / user など）
	 * @return ビルダー
	 */
	icon<S extends object = AppState> (name: Resolvable<string, Context<S>>): IconBuilder<S> {

		return guard(new IconBuilder<S>(name));

	},

	/**
	 * ドロップダウン（行の「…」メニュー）
	 *
	 * @return ビルダー
	 */
	menu<S extends object = AppState> (): MenuBuilder<S> {

		return guard(new MenuBuilder<S>());

	},

	/* ---------------- 画面の骨組み ---------------- */

	/**
	 * サイドバー
	 *
	 * @return ビルダー
	 */
	sidebar<S extends object = AppState> (): SidebarBuilder<S> {

		return guard(new SidebarBuilder<S>());

	},

	/**
	 * 画面の見出し
	 *
	 * @param heading 題名
	 * @return ビルダー
	 */
	pageHeader<S extends object = AppState> (heading: Resolvable<string, Context<S>>): PageHeaderBuilder<S> {

		return guard(new PageHeaderBuilder<S>(heading));

	},

	/* ---------------- 繰り返し ---------------- */

	/**
	 * 繰り返し
	 *
	 * @param items 一覧
	 * @param itemView 1 件分の画面
	 * @return ビルダー
	 */
	each<T, S extends object = AppState> (
		items: Resolvable<readonly T[], Context<S>>,
		itemView: (item: T, index: number, ctx: Context<S>) => Node<S>
	): EachBuilder<T, S> {

		return guard(new EachBuilder<T, S>(items, itemView));

	}

};
