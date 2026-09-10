/**
 * API クライアント
 *
 * <p>
 * サーバ（jimble-web など）との通信をここに集める。
 * 画面のコードは fetch を直接呼ばない。
 * </p>
 *
 * <pre>
 * const api = Api.of('/api');
 * const list = await api.get&lt;Staff[]&gt;('/staff', { keyword: '山田' });
 * </pre>
 */
export class Api {

	/**
	 * クライアントを作成する
	 *
	 * @param base 基底パス
	 * @return クライアント
	 */
	static of (base?: string): Api {

		return new Api(base);

	}

	/** 基底パス */
	readonly base: string;

	/** 送信するヘッダ */
	readonly headers: Record<string, string> = { 'Content-Type': 'application/json' };

	constructor (base = '') {

		this.base = base;

	}

	/**
	 * ヘッダを追加する
	 *
	 * @param name 名前
	 * @param value 値
	 * @return クライアント
	 */
	header (name: string, value: string): this {

		this.headers[name] = value;
		return this;

	}

	/**
	 * GET
	 *
	 * @param path パス
	 * @param query クエリ
	 * @return 応答
	 */
	get<T = unknown> (path: string, query?: Record<string, string | number | boolean>): Promise<T> {

		const search = query == null
			? ''
			: '?' + new URLSearchParams(Object.entries(query).map(([k, v]) => [k, String(v)])).toString();

		return this.send<T>('GET', path + search, null);

	}

	/**
	 * POST
	 *
	 * @param path パス
	 * @param body 本文
	 * @return 応答
	 */
	post<T = unknown> (path: string, body?: unknown): Promise<T> {

		return this.send<T>('POST', path, body);

	}

	/**
	 * PUT
	 *
	 * @param path パス
	 * @param body 本文
	 * @return 応答
	 */
	put<T = unknown> (path: string, body?: unknown): Promise<T> {

		return this.send<T>('PUT', path, body);

	}

	/**
	 * DELETE
	 *
	 * @param path パス
	 * @return 応答
	 */
	delete<T = unknown> (path: string): Promise<T> {

		return this.send<T>('DELETE', path, null);

	}

	/**
	 * 送信する
	 *
	 * @param method メソッド
	 * @param path パス
	 * @param body 本文
	 * @return 応答
	 */
	protected async send<T> (method: string, path: string, body: unknown): Promise<T> {

		const response = await fetch(this.base + path, {
			method,
			headers: this.headers,
			body: body == null ? undefined : JSON.stringify(body)
		});

		if (!response.ok) {
			const text = await response.text().catch(() => '');
			const error = new Error('[jimble-ui] ' + method + ' ' + path + ' が失敗しました (' + response.status + ')') as Error & {
				status?: number;
				body?: string;
			};
			error.status = response.status;
			error.body = text;
			throw error;
		}

		if (response.status === 204) {
			return null as T;
		}

		const type = response.headers.get('Content-Type') ?? '';
		return (type.includes('json') ? await response.json() : await response.text()) as T;

	}

}
