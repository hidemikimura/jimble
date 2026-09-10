import { JbElement } from './base.js';
import type { PropertyDeclarations } from '../../vendor/lit.js';
import type { FileInfo } from '../core/types.js';

/**
 * ファイル添付
 *
 * <p>
 * 押して選ぶのと、放り込む（ドラッグ＆ドロップ）の両方。
 * 選ばれたら {@code jb-select} に File の配列を載せて投げる。
 * </p>
 */
export class JbFile extends JbElement {

	static override tag = 'jb-file';

	static override properties: PropertyDeclarations = {
		name: { type: String },
		label: { type: String },
		accept: { type: String },
		multiple: { type: Boolean, reflect: true, attribute: 'jb-multiple' },
		hint: { type: String },
		error: { type: String, reflect: true, attribute: 'jb-error' },
		disabled: { type: Boolean, reflect: true, attribute: 'jb-disabled' },
		dragging: { type: Boolean, reflect: true, attribute: 'jb-dragging' },
		files: { attribute: false }
	};

	/** 項目名 */
	name = '';

	/** ラベル */
	label = '';

	/** 受け付ける種類（例: '.pdf,image/*'） */
	accept = '';

	/** 複数選べるか */
	multiple = false;

	/** 補足 */
	hint = '';

	/** エラー文言 */
	error = '';

	/** 使用不可 */
	disabled = false;

	/** 放り込まれている最中か */
	dragging = false;

	/** 選ばれたファイル */
	files: FileInfo[] = [];

	/**
	 * 選ばれたときの処理（テーマのテンプレートから呼ぶ）
	 *
	 * @param event イベント
	 */
	handleSelect (event: Event): void {

		this.accept_((event.target as HTMLInputElement).files);

	}

	/**
	 * 放り込まれたときの処理（テーマのテンプレートから呼ぶ）
	 *
	 * @param event イベント
	 */
	handleDrop (event: DragEvent): void {

		event.preventDefault();
		this.dragging = false;
		if (!this.disabled) {
			this.accept_(event.dataTransfer?.files ?? null);
		}

	}

	/**
	 * 上に来たときの処理（テーマのテンプレートから呼ぶ）
	 *
	 * @param event イベント
	 * @param dragging 上に来ているか
	 */
	handleDragging (event: DragEvent, dragging: boolean): void {

		event.preventDefault();
		this.dragging = dragging && !this.disabled;

	}

	/**
	 * 選ばれたファイルを取り込む
	 *
	 * @param list ファイル
	 */
	protected accept_ (list: FileList | null): void {

		if (list == null || list.length === 0) {
			return;
		}

		const files = [...list].slice(0, this.multiple ? undefined : 1);
		this.files = files.map((file) => ({ name: file.name, size: file.size }));

		this.dispatchEvent(new CustomEvent('jb-select', {
			detail: { name: this.name, files },
			bubbles: true,
			composed: true
		}));

	}

	/**
	 * 選択を取り消す（テーマのテンプレートから呼ぶ）
	 */
	handleClear (): void {

		this.files = [];
		this.dispatchEvent(new CustomEvent('jb-select', {
			detail: { name: this.name, files: [] },
			bubbles: true,
			composed: true
		}));

	}

}

customElements.define('jb-file', JbFile);
