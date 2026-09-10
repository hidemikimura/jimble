/**
 * ダッシュボードのデータ
 *
 * <p>デモなのでメモリ上で作る。本番では jimble-web の REST に置き換える。</p>
 */
import type { ChartPoint } from '../../dist/index.js';

/** 売上の要約 */
export interface Summary {
	sales: number;
	orders: number;
	customers: number;
	returnRate: number;
}

/** 資料 */
export interface Document {
	name: string;
	size: number;
	at: string;
}

/**
 * 遅延する
 *
 * @param ms ミリ秒
 * @return 待ち
 */
const wait = (ms: number): Promise<void> => new Promise((done) => setTimeout(done, ms));

/**
 * 要約を取得する
 *
 * @return 要約
 */
export async function findSummary (): Promise<Summary> {

	await wait(200);
	return { sales: 12_480_000, orders: 1_284, customers: 862, returnRate: 2.4 };

}

/**
 * 日別の売上を取得する
 *
 * @param days 日数
 * @return 点の並び
 */
export async function findDailySales (days: number): Promise<ChartPoint[]> {

	await wait(200);

	const base = [420, 380, 510, 470, 620, 700, 540, 590, 660, 480, 520, 610, 720, 680];
	return Array.from({ length: days }, (_unused, index) => ({
		label: String(index + 1) + '日',
		value: base[index % base.length] ?? 400
	}));

}

/**
 * 部門別の売上を取得する
 *
 * @return 点の並び
 */
export async function findByCategory (): Promise<ChartPoint[]> {

	await wait(200);
	return [
		{ label: '食品', value: 4200 },
		{ label: '飲料', value: 3100 },
		{ label: '日用品', value: 2600 },
		{ label: 'その他', value: 1400 }
	];

}

/**
 * 金額を整形する
 *
 * @param value 値
 * @return 文字列
 */
export function yen (value: number): string {

	return value.toLocaleString('ja-JP');

}
