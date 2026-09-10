import { nothing } from '../../vendor/lit.js';
import type { TemplateResult } from '../../vendor/lit.js';
import type { Context } from './context.js';
import type { AppState, Resolvable } from './types.js';

/** 描画できるもの */
export type Renderable = TemplateResult | typeof nothing | string | number | null | undefined | false;

/** 画面に置けるもの */
export type Node<S extends object = AppState> =
	| Builder<S>
	| Renderable
	| ((ctx: Context<S>) => Node<S>)
	| readonly Node<S>[];

/**
 * 値を解決する
 *
 * <p>値そのものでも、コンテキストを受け取る関数でも書ける。</p>
 *
 * @param value 値または関数
 * @param ctx コンテキスト
 * @return 解決した値
 */
export function resolve<V, S extends object> (value: Resolvable<V, Context<S>> | undefined, ctx: Context<S>): V | undefined {

	return typeof value === 'function' ? (value as (c: Context<S>) => V)(ctx) : value;

}

/**
 * ビルダー基底
 *
 * <p>
 * SQL の {@code AbstractBuilder} に相当する。
 * すべてのメソッドは自身を返し、最後に {@link Builder#render} で描画結果を作る。
 * </p>
 */
export abstract class Builder<S extends object = AppState> {

	/** 子要素 */
	protected children: Node<S>[] = [];

	/** 表示条件 */
	protected condition: Resolvable<boolean, Context<S>> | undefined = undefined;

	/**
	 * 子要素を追加する
	 *
	 * @param children 子要素
	 * @return ビルダー
	 */
	add (...children: Node<S>[]): this {

		for (const child of (children as unknown[]).flat(Infinity) as Node<S>[]) {
			if (child != null && child !== false) {
				this.children.push(child);
			}
		}
		return this;

	}

	/**
	 * 表示条件
	 *
	 * @param condition 条件
	 * @return ビルダー
	 */
	when (condition: Resolvable<boolean, Context<S>>): this {

		this.condition = condition;
		return this;

	}

	/**
	 * 非表示条件
	 *
	 * @param condition 条件
	 * @return ビルダー
	 */
	unless (condition: Resolvable<boolean, Context<S>>): this {

		this.condition = (ctx: Context<S>) => !resolve(condition, ctx);
		return this;

	}

	/**
	 * 描画する
	 *
	 * @param ctx コンテキスト
	 * @return テンプレート
	 */
	render (ctx: Context<S>): Renderable | Renderable[] {

		if (this.condition != null && !resolve(this.condition, ctx)) {
			return nothing;
		}
		return this.template(ctx);

	}

	/**
	 * テンプレートを作る（サブクラスで実装する）
	 *
	 * @param ctx コンテキスト
	 * @return テンプレート
	 */
	protected abstract template (ctx: Context<S>): Renderable | Renderable[];

	/**
	 * 子要素を描画する
	 *
	 * @param ctx コンテキスト
	 * @return テンプレート一覧
	 */
	protected renderChildren (ctx: Context<S>): (Renderable | Renderable[])[] {

		return this.children.map((child) => renderNode(child, ctx));

	}

}

/**
 * 任意のノードを描画する
 *
 * <p>ビルダー・配列・関数・文字列を受け付ける。</p>
 *
 * @param node ノード
 * @param ctx コンテキスト
 * @return テンプレート
 */
export function renderNode<S extends object> (node: Node<S>, ctx: Context<S>): Renderable | Renderable[] {

	if (node == null || node === false) {
		return nothing;
	}
	if (Array.isArray(node)) {
		return node.flatMap((child) => renderNode(child as Node<S>, ctx));
	}
	if (node instanceof Builder) {
		return node.render(ctx);
	}
	if (typeof node === 'function') {
		return renderNode((node as (c: Context<S>) => Node<S>)(ctx), ctx);
	}
	return node as Renderable;

}
