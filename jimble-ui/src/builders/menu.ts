import { html } from '../../vendor/lit.js';
import type { TemplateResult } from '../../vendor/lit.js';
import { Builder, resolve } from '../core/builder.js';
import type { Context } from '../core/context.js';
import { report } from '../core/dev.js';
import type { AppState, MenuItem, Resolvable } from '../core/types.js';

/** 項目の見た目の指定 */
export interface MenuItemOptions {
	/** 飾り（jb-icon の名前） */
	icon?: string;
	/** 選べないか */
	disabled?: boolean;
}

/* 1 項目分（文言と処理） */
interface Entry<S extends object> {
	item: MenuItem;
	handler: ((ctx: Context<S>) => unknown) | null;
}

/**
 * ドロップダウンビルダー（行の「…」メニュー）
 *
 * <pre>
 * UI.menu()
 *     .item('編集', (ctx) =&gt; ctx.go('/makers/edit?id=' + maker.id), { icon: 'edit' })
 *     .divider()
 *     .danger('削除', async (ctx) =&gt; { await remove(maker.id); await load(ctx); }, { icon: 'trash' });
 * </pre>
 */
export class MenuBuilder<S extends object = AppState> extends Builder<S> {

	private entries: Entry<S>[] = [];
	private triggerLabel: Resolvable<string, Context<S>> | undefined;
	private triggerIcon = 'more';
	private alignValue: 'start' | 'end' = 'end';
	private disabledValue: Resolvable<boolean, Context<S>> | undefined;

	/**
	 * 項目を足す
	 *
	 * @param label 文言
	 * @param handler 選ばれたときの処理
	 * @param options 見た目
	 * @return ビルダー
	 */
	item (label: string, handler: (ctx: Context<S>) => unknown, options: MenuItemOptions = {}): this {

		return this.push(label, handler, options, false);

	}

	/**
	 * 危険操作の項目を足す（赤くなる）
	 *
	 * @param label 文言
	 * @param handler 選ばれたときの処理
	 * @param options 見た目
	 * @return ビルダー
	 */
	danger (label: string, handler: (ctx: Context<S>) => unknown, options: MenuItemOptions = {}): this {

		return this.push(label, handler, options, true);

	}

	/**
	 * 選ばれたら画面遷移する項目を足す
	 *
	 * @param label 文言
	 * @param path 遷移先
	 * @param options 見た目
	 * @return ビルダー
	 */
	go (label: string, path: Resolvable<string, Context<S>>, options: MenuItemOptions = {}): this {

		return this.push(label, (ctx: Context<S>) => {
			ctx.go(resolve(path, ctx) as string);
		}, options, false);

	}

	/**
	 * 区切り線を足す
	 *
	 * @return ビルダー
	 */
	divider (): this {

		this.entries.push({
			item: { value: 'divider-' + String(this.entries.length), label: '', divider: true },
			handler: null
		});
		return this;

	}

	/**
	 * ボタンの文言（既定は文言なしの「…」だけ）
	 *
	 * @param label 文言
	 * @return ビルダー
	 */
	trigger (label: Resolvable<string, Context<S>>): this {

		this.triggerLabel = label;
		return this;

	}

	/**
	 * ボタンの飾り（jb-icon の名前。空文字にすると出さない）
	 *
	 * @param name 名前
	 * @return ビルダー
	 */
	icon (name: string): this {

		this.triggerIcon = name;
		return this;

	}

	/**
	 * 左に開く
	 *
	 * @return ビルダー
	 */
	alignStart (): this {

		this.alignValue = 'start';
		return this;

	}

	/**
	 * 使用不可
	 *
	 * @param value 使用不可か
	 * @return ビルダー
	 */
	disabled (value: Resolvable<boolean, Context<S>> = true): this {

		this.disabledValue = value;
		return this;

	}

	protected override template (ctx: Context<S>): TemplateResult {

		return html`<jb-menu
			.items=${this.entries.map((entry) => entry.item)}
			.label=${resolve(this.triggerLabel, ctx) ?? ''}
			.icon=${this.triggerIcon}
			.align=${this.alignValue}
			.disabled=${resolve(this.disabledValue, ctx) === true}
			@jb-select=${(event: CustomEvent<{ value: string }>) => this.select(event.detail.value, ctx)}
		></jb-menu>`;

	}

	/* 項目を組み立てて足す */
	private push (
		label: string,
		handler: (ctx: Context<S>) => unknown,
		options: MenuItemOptions,
		danger: boolean
	): this {

		this.entries.push({
			item: {
				value: 'item-' + String(this.entries.length),
				label,
				...(options.icon == null ? {} : { icon: options.icon }),
				...(options.disabled == null ? {} : { disabled: options.disabled }),
				...(danger ? { danger: true } : {})
			},
			handler
		});
		return this;

	}

	/* 選ばれた項目の処理を実行する */
	private select (value: string, ctx: Context<S>): void {

		const entry = this.entries.find((candidate) => candidate.item.value === value);
		if (entry?.handler == null) {
			return;
		}

		try {
			const result = entry.handler(ctx);
			if (result instanceof Promise) {
				result.catch((error: unknown) => report(error, 'menu の ' + entry.item.label));
			}
		} catch (error) {
			report(error, 'menu の ' + entry.item.label);
		}

	}

}
