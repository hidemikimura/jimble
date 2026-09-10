import type { ToastVariant } from './types.js';
import { JbToast } from '../components/jb-toast.js';

/** 知らせのオプション */
export interface NotifyOptions {
	/** 種類 */
	variant?: ToastVariant;
	/** 消えるまでの時間（ミリ秒。0 で自動では消さない） */
	duration?: number;
}

/* 置き場所 */
let container: HTMLElement | null = null;

/**
 * 置き場所を用意する
 *
 * @return 置き場所
 */
function ensureContainer (): HTMLElement {

	if (container != null && container.isConnected) {
		return container;
	}

	container = document.createElement('div');
	container.id = 'jb-toast-container';
	container.setAttribute('style', [
		'position:fixed', 'top:16px', 'right:16px', 'z-index:2147483000',
		'display:flex', 'flex-direction:column', 'gap:8px', 'pointer-events:none'
	].join(';'));
	document.body.appendChild(container);

	return container;

}

/**
 * 知らせ（トースト）
 *
 * <p>
 * 画面のどこからでも呼べる。見た目はテーマの {@code jb-toast} が決める。
 * </p>
 *
 * <pre>
 * notify.success('保存しました');
 * notify.error('保存に失敗しました', { duration: 0 });
 * </pre>
 */
export const notify = {

	/**
	 * 知らせを出す
	 *
	 * @param message 文言
	 * @param options オプション
	 * @return 出した要素
	 */
	show (message: string, options: NotifyOptions = {}): JbToast {

		const toast = document.createElement('jb-toast') as JbToast;
		toast.message = message;
		toast.variant = options.variant ?? 'info';
		toast.style.pointerEvents = 'auto';

		ensureContainer().appendChild(toast);

		const duration = options.duration ?? 4000;
		if (duration > 0) {
			setTimeout(() => toast.remove(), duration);
		}

		return toast;

	},

	/**
	 * 知らせを出す（情報）
	 *
	 * @param message 文言
	 * @param options オプション
	 * @return 出した要素
	 */
	info (message: string, options: NotifyOptions = {}): JbToast {

		return notify.show(message, { ...options, variant: 'info' });

	},

	/**
	 * 知らせを出す（成功）
	 *
	 * @param message 文言
	 * @param options オプション
	 * @return 出した要素
	 */
	success (message: string, options: NotifyOptions = {}): JbToast {

		return notify.show(message, { ...options, variant: 'success' });

	},

	/**
	 * 知らせを出す（注意）
	 *
	 * @param message 文言
	 * @param options オプション
	 * @return 出した要素
	 */
	warning (message: string, options: NotifyOptions = {}): JbToast {

		return notify.show(message, { ...options, variant: 'warning' });

	},

	/**
	 * 知らせを出す（失敗）
	 *
	 * @param message 文言
	 * @param options オプション
	 * @return 出した要素
	 */
	error (message: string, options: NotifyOptions = {}): JbToast {

		return notify.show(message, { ...options, variant: 'danger' });

	},

	/**
	 * 出ている知らせを全部消す
	 */
	clear (): void {

		container?.replaceChildren();

	}

};
