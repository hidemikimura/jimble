import { fail } from './dev.js';
import type { html as litHtml, svg as litSvg, TemplateResult } from '../../vendor/lit.js';

/**
 * テンプレート関数（コンポーネントの状態から HTML を作る）
 *
 * <p>
 * 第 3 引数の {@code svg} は、SVG の<b>中身</b>を作るときに使う。
 * SVG の子要素を `html` で作ると HTML 要素として解釈され、描画されない。
 * </p>
 */
export type ComponentTemplate<E = any> = (el: E, html: typeof litHtml, svg: typeof litSvg) => TemplateResult;

/** テーマが持つコンポーネント 1 つ分の定義 */
export interface ComponentTheme<E = any> {
	/** どんな HTML を出すか。省略すると親テーマのまま */
	template?: ComponentTemplate<E>;
	/** 当てる CSS。継承時は親の後ろに足される */
	styles?: string;
	/** 親の CSS を捨てて置き換える */
	replaceStyles?: string;
}

/**
 * コンポーネントのテーマ定義を作る
 *
 * <p>要素の型を渡すと、テンプレートの中で {@code el.} が補完・検査される。</p>
 *
 * <pre>
 * 'jb-input': component&lt;JbInput&gt;({ template: (el, html) =&gt; html`...` })
 * </pre>
 *
 * @param entry コンポーネントのテーマ定義
 * @return コンポーネントのテーマ定義
 */
export function component<E> (entry: ComponentTheme<E>): ComponentTheme {

	return entry as ComponentTheme;

}

/** テーマ定義 */
export interface ThemeDefinition {
	/** テーマ名 */
	name: string;
	/** 継承元のテーマ名 */
	extends?: string;
	/** --jb-* として :root に流すトークン */
	tokens?: Record<string, string>;
	/** 文書側（Shadow DOM の外）の土台 CSS */
	base?: string | null;
	/** 全コンポーネント共通の CSS。関数を渡すと遅延読み込みする */
	shared?: (string | (() => Promise<string | { default: string }>))[];
	/** コンポーネントごとの定義 */
	components?: Record<string, ComponentTheme>;
}

/* 継承を解決した定義 */
interface ResolvedComponent {
	template: ComponentTemplate | null;
	styles: string[];
	replaced: boolean;
}

interface ResolvedDefinition {
	name: string;
	extends: string | null;
	tokens: Record<string, string>;
	base: string | null;
	shared: (string | (() => Promise<string | { default: string }>))[];
	components: Record<string, ResolvedComponent>;
}

/**
 * テーマ
 *
 * <p>
 * コンポーネントは「状態と振る舞い」だけを持ち、
 * <b>どんな HTML を出すか（テンプレート）と、どんな CSS を当てるか</b>はテーマが持つ。
 * テーマを差し替えれば、アプリのコードを 1 行も変えずに見た目が入れ替わる。
 * </p>
 */
export class Theme {

	/**
	 * テーマを作る
	 *
	 * @param source 登録済みテーマ名 / 定義 / テーマ
	 * @param tokens 上書きするトークン
	 * @return テーマ
	 */
	static of (source: string | ThemeDefinition | Theme, tokens?: Record<string, string>): Theme {

		if (source == null) {
			throw new Error('[jimble-ui] テーマを指定してください（例: original / bootstrap5 / tailwind-dark）');
		}

		let theme: Theme;
		if (source instanceof Theme) {
			theme = source;
		} else if (typeof source === 'string') {
			const definition = REGISTRY.get(source);
			if (definition == null) {
				fail(
					'テーマ "' + source + '" は登録されていません',
					'登録済み: ' + (themeNames().join(' / ') || '(なし)')
				);
				theme = new Theme({ name: source });
			} else {
				theme = new Theme(definition);
			}
		} else {
			theme = new Theme(source);
		}

		if (tokens != null) {
			theme = new Theme({ ...theme.source, tokens: { ...theme.source.tokens, ...tokens } });
		}
		return theme;

	}

	/**
	 * テーマを継承して新しいテーマ定義を作る
	 *
	 * <p>書いた差分だけが上書きされる。</p>
	 * <ul>
	 *   <li>{@code tokens} … 親のトークンに重ねる</li>
	 *   <li>{@code shared} … 親の共通 CSS の<b>後ろに</b>足す</li>
	 *   <li>{@code components[tag].template} … 指定すれば差し替え、省略すれば親のまま</li>
	 *   <li>{@code components[tag].styles} … 親の CSS の<b>後ろに</b>足す（後勝ちで上書きできる）</li>
	 *   <li>{@code components[tag].replaceStyles} … 親の CSS を捨てて置き換える</li>
	 * </ul>
	 *
	 * @param base 親テーマ名
	 * @param definition 差分のテーマ定義
	 * @return 登録したテーマ定義
	 */
	static extend (base: string, definition: Omit<ThemeDefinition, 'extends'>): ThemeDefinition {

		return registerTheme({ ...definition, extends: base });

	}

	/* 書かれたままのテーマ定義 */
	#source: ThemeDefinition;

	/* 継承を解決した定義 */
	#definition: ResolvedDefinition;

	/* 読み込んだ共通スタイルシート */
	#sharedSheets: CSSStyleSheet[] | null = null;

	/* コンポーネント別スタイルシート */
	#componentSheets = new Map<string, CSSStyleSheet[]>();

	constructor (definition: ThemeDefinition) {

		this.#source = definition;
		this.#definition = resolveDefinition(definition);

	}

	/** テーマ名 */
	get name (): string {

		return this.#definition.name;

	}

	/** 書かれたままのテーマ定義 */
	get source (): ThemeDefinition {

		return this.#source;

	}

	/** 継承を解決した定義 */
	get definition (): ResolvedDefinition {

		return this.#definition;

	}

	/**
	 * コンポーネントのテンプレートを取得する
	 *
	 * @param tag タグ名
	 * @return テンプレート関数
	 */
	template (tag: string): ComponentTemplate | null {

		return this.#definition.components[tag]?.template ?? null;

	}

	/**
	 * コンポーネントに適用するスタイルシートを取得する
	 *
	 * @param tag タグ名
	 * @return スタイルシート
	 */
	styleSheets (tag: string): CSSStyleSheet[] {

		if (!this.#componentSheets.has(tag)) {
			const styles = this.#definition.components[tag]?.styles ?? [];
			this.#componentSheets.set(tag, styles.map((text) => sheetOf(text)));
		}

		/* 共通 CSS → 親の CSS → 子の CSS の順。後ろほど強い */
		return [...(this.#sharedSheets ?? []), ...(this.#componentSheets.get(tag) ?? [])];

	}

	/**
	 * 共通 CSS を読み込む
	 *
	 * <p>Bootstrap のような大きい CSS は、そのテーマを選んだときだけ取りに行く。</p>
	 *
	 * @return テーマ
	 */
	async load (): Promise<this> {

		if (this.#sharedSheets != null) {
			return this;
		}

		const texts: string[] = [];
		for (const source of this.#definition.shared) {
			if (typeof source === 'function') {
				const loaded = await source();
				texts.push(typeof loaded === 'string' ? loaded : loaded.default);
			} else {
				texts.push(source);
			}
		}

		this.#sharedSheets = texts.map((text) => sheetOf(text));
		return this;

	}

	/**
	 * トークンと土台 CSS を文書に適用する
	 *
	 * @param root 適用先
	 * @return テーマ
	 */
	apply (root: HTMLElement = document.documentElement): this {

		for (const [name, value] of Object.entries(this.#definition.tokens)) {
			root.style.setProperty('--jb-' + name, value);
		}

		let base = document.getElementById('jb-base-style');
		if (base == null) {
			base = document.createElement('style');
			base.id = 'jb-base-style';
			document.head.appendChild(base);
		}
		base.textContent = this.#definition.base ?? DEFAULT_BASE;

		/*
		 * 共通 CSS は文書側にも当てる。
		 * Bootstrap のように :root へ変数を定義する CSS は、
		 * 文書側に無いと Shadow DOM 内へ継承されないため。
		 */
		document.adoptedStyleSheets = [...(this.#sharedSheets ?? [])];

		return this;

	}

}

/* 土台 CSS の既定 */
const DEFAULT_BASE = [
	'html, body { margin: 0; padding: 0; min-height: 100%; }',
	'body { background: var(--jb-color-bg); color: var(--jb-color-text);',
	' font-family: var(--jb-font); font-size: var(--jb-font-size-body); }',
	'*, *::before, *::after { box-sizing: border-box; }'
].join('\n');

/**
 * CSS 文字列からスタイルシートを作る
 *
 * @param text CSS
 * @return スタイルシート
 */
function sheetOf (text: string): CSSStyleSheet {

	const sheet = new CSSStyleSheet();
	sheet.replaceSync(text);
	return sheet;

}

/* ---------------------------------------------------------------
 * 継承の解決
 * --------------------------------------------------------------- */

/**
 * テーマ定義の継承を解決する
 *
 * @param definition テーマ定義
 * @param chain 継承の経路（循環検出用）
 * @return 解決した定義
 */
function resolveDefinition (definition: ThemeDefinition, chain: string[] = []): ResolvedDefinition {

	const own = {
		name: definition.name ?? 'anonymous',
		extends: definition.extends ?? null,
		tokens: definition.tokens ?? {},
		base: definition.base ?? null,
		shared: definition.shared ?? [],
		components: normalizeComponents(definition.components)
	};

	if (own.extends == null) {
		return own;
	}

	if (chain.includes(own.extends)) {
		throw new Error('[jimble-ui] テーマの継承が循環しています: ' + [...chain, own.extends].join(' -> '));
	}

	const parentDefinition = REGISTRY.get(own.extends);
	if (parentDefinition == null) {
		throw new Error('[jimble-ui] 継承元のテーマが登録されていません: ' + own.extends);
	}
	const parent = resolveDefinition(parentDefinition, [...chain, own.extends]);

	return {
		...own,
		tokens: { ...parent.tokens, ...own.tokens },
		base: own.base ?? parent.base,
		shared: [...parent.shared, ...own.shared],
		components: mergeComponents(parent.components, own.components)
	};

}

/**
 * コンポーネント定義を正規化する
 *
 * @param components コンポーネント定義
 * @return 正規化した定義
 */
function normalizeComponents (components: Record<string, ComponentTheme> | undefined): Record<string, ResolvedComponent> {

	const result: Record<string, ResolvedComponent> = {};

	for (const [tag, entry] of Object.entries(components ?? {})) {
		const styles = entry.replaceStyles ?? entry.styles;
		result[tag] = {
			template: entry.template ?? null,
			styles: styles == null ? [] : [styles],
			replaced: entry.replaceStyles != null
		};
	}

	return result;

}

/**
 * コンポーネント定義をマージする
 *
 * @param parent 親
 * @param child 子（正規化済み）
 * @return マージ結果
 */
function mergeComponents (
	parent: Record<string, ResolvedComponent>,
	child: Record<string, ResolvedComponent>
): Record<string, ResolvedComponent> {

	const result: Record<string, ResolvedComponent> = {};

	for (const tag of new Set([...Object.keys(parent), ...Object.keys(child)])) {

		const base = parent[tag] ?? { template: null, styles: [], replaced: false };
		const over = child[tag];

		if (over == null) {
			result[tag] = base;
			continue;
		}

		result[tag] = {
			template: over.template ?? base.template,
			styles: over.replaced ? over.styles : [...base.styles, ...over.styles],
			replaced: false
		};

	}

	return result;

}

/* ---------------------------------------------------------------
 * テーマの登録と現在テーマ
 * --------------------------------------------------------------- */

/* 登録済みテーマ定義 */
const REGISTRY = new Map<string, ThemeDefinition>();

/* 現在のテーマ */
let CURRENT = new Theme({ name: 'empty' });

/* テーマ変更の購読者 */
const LISTENERS = new Set<(theme: Theme) => void>();

/**
 * テーマ定義を登録する
 *
 * @param definition テーマ定義
 * @return テーマ定義
 */
export function registerTheme (definition: ThemeDefinition): ThemeDefinition {

	REGISTRY.set(definition.name, definition);
	return definition;

}

/**
 * テーマを継承して登録する（{@link Theme.extend} と同じ）
 *
 * @param base 親テーマ名
 * @param definition 差分のテーマ定義
 * @return 登録したテーマ定義
 */
export function extendTheme (base: string, definition: Omit<ThemeDefinition, 'extends'>): ThemeDefinition {

	return Theme.extend(base, definition);

}

/**
 * 登録済みテーマ名の一覧を取得する
 *
 * @return テーマ名
 */
export function themeNames (): string[] {

	return [...REGISTRY.keys()];

}

/**
 * 現在のテーマを取得する
 *
 * @return テーマ
 */
export function currentTheme (): Theme {

	return CURRENT;

}

/**
 * テーマを切り替える
 *
 * <p>共通 CSS の読み込みを待ってから、表示中のコンポーネントすべてに反映する。</p>
 *
 * @param source テーマ名 / 定義 / テーマ
 * @param tokens 上書きするトークン
 * @return テーマ
 */
export async function useTheme (source: string | ThemeDefinition | Theme, tokens?: Record<string, string>): Promise<Theme> {

	const theme = Theme.of(source, tokens);
	await theme.load();

	CURRENT = theme;
	theme.apply();

	for (const listener of LISTENERS) {
		listener(theme);
	}

	return theme;

}

/**
 * テーマ変更を購読する
 *
 * @param listener リスナ
 * @return 解除関数
 */
export function subscribeTheme (listener: (theme: Theme) => void): () => void {

	LISTENERS.add(listener);
	return () => LISTENERS.delete(listener);

}
