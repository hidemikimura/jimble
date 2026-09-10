import { html } from '../../vendor/lit.js';
import type { TemplateResult } from '../../vendor/lit.js';
import { Builder, renderNode, resolve } from '../core/builder.js';
import type { Node } from '../core/builder.js';
import type { Context } from '../core/context.js';
import type { AccordionSection, AppState, Resolvable } from '../core/types.js';

/**
 * アコーディオンビルダー
 *
 * <pre>
 * UI.accordion()
 *     .section('basic', '基本情報', UI.label('...'))
 *     .section('note', '備考', UI.label('...'))
 *     .open(['basic'])
 *     .single();
 * </pre>
 */
export class AccordionBuilder<S extends object = AppState> extends Builder<S> {

	private sections: { section: AccordionSection; children: Node<S>[] }[] = [];
	private openValues: Resolvable<string[], Context<S>> = [];
	private singleValue: Resolvable<boolean, Context<S>> | undefined;
	private toggleHandler: ((value: string, open: boolean, ctx: Context<S>) => unknown) | null = null;

	/**
	 * 節を足す
	 *
	 * @param value 値（重ならない文字列）
	 * @param label 見出し
	 * @param children 中身
	 * @return ビルダー
	 */
	section (value: string, label: string, ...children: Node<S>[]): this {

		this.sections.push({ section: { value, label }, children });
		return this;

	}

	/**
	 * 最初から開いている節
	 *
	 * @param values 値
	 * @return ビルダー
	 */
	open (values: Resolvable<string[], Context<S>>): this {

		this.openValues = values;
		return this;

	}

	/**
	 * 一度に 1 つだけ開く
	 *
	 * @param value そうするか
	 * @return ビルダー
	 */
	single (value: Resolvable<boolean, Context<S>> = true): this {

		this.singleValue = value;
		return this;

	}

	/**
	 * 開閉したときの処理
	 *
	 * @param handler 処理
	 * @return ビルダー
	 */
	onToggle (handler: (value: string, open: boolean, ctx: Context<S>) => unknown): this {

		this.toggleHandler = handler;
		return this;

	}

	protected override template (ctx: Context<S>): TemplateResult {

		return html`<jb-accordion
			.sections=${this.sections.map((entry) => entry.section)}
			.open=${resolve(this.openValues, ctx) ?? []}
			.single=${resolve(this.singleValue, ctx) === true}
			@jb-toggle=${(event: CustomEvent<{ value: string; open: boolean }>) =>
				this.toggleHandler?.(event.detail.value, event.detail.open, ctx)}
		>${this.sections.map((entry) => html`
			<div slot=${'section-' + entry.section.value}>
				${entry.children.map((child) => renderNode(child, ctx))}
			</div>
		`)}</jb-accordion>`;

	}

}
