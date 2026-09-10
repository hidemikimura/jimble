/**
 * 社員サービス
 *
 * <p>
 * 画面から切り離した業務ロジック。ここを jimble-web の REST に差し替えれば本番になる。
 * （デモなのでメモリ上のデータを非同期で返している）
 * </p>
 */

/** 社員 */
export interface Staff {
	id: number;
	name: string;
	age: number;
	mail: string;
	department: string;
	employment: string;
	active: boolean;
	notify: boolean;
	note: string;
}

/** 入力中の社員 */
export interface StaffForm {
	name?: string;
	age?: number | null;
	mail?: string;
	department?: string;
	employment?: string;
	active?: boolean;
	notify?: boolean;
	note?: string;
}

/** 部署 */
export const DEPARTMENTS = [
	{ value: 'sales', label: '営業部' },
	{ value: 'dev', label: '開発部' },
	{ value: 'admin', label: '総務部' }
];

/** 雇用形態 */
export const EMPLOYMENTS = [
	{ value: 'full', label: '正社員' },
	{ value: 'contract', label: '契約社員' },
	{ value: 'part', label: 'パート' }
];

/**
 * 部署名を引く
 *
 * @param value 値
 * @return 表示名
 */
export function departmentLabel (value: string): string {

	return DEPARTMENTS.find((entry) => entry.value === value)?.label ?? '-';

}

/**
 * 雇用形態名を引く
 *
 * @param value 値
 * @return 表示名
 */
export function employmentLabel (value: string): string {

	return EMPLOYMENTS.find((entry) => entry.value === value)?.label ?? '-';

}

/* デモ用データ */
let SEQUENCE = 0;
let STAFF_LIST: Staff[] = [
	['山田 太郎', 34, 'yamada@example.com', 'sales', 'full', true],
	['佐藤 花子', 28, 'sato@example.com', 'dev', 'full', true],
	['鈴木 一郎', 45, 'suzuki@example.com', 'admin', 'contract', true],
	['田中 美咲', 31, 'tanaka@example.com', 'dev', 'full', true],
	['高橋 健', 52, 'takahashi@example.com', 'sales', 'part', false],
	['伊藤 结衣', 26, 'ito@example.com', 'dev', 'contract', true],
	['渡辺 大輔', 39, 'watanabe@example.com', 'admin', 'full', true],
	['中村 彩', 41, 'nakamura@example.com', 'sales', 'full', false]
].map(([name, age, mail, department, employment, active]) => ({
	id: ++SEQUENCE,
	name: name as string,
	age: age as number,
	mail: mail as string,
	department: department as string,
	employment: employment as string,
	active: active as boolean,
	notify: true,
	note: ''
}));

/**
 * 遅延する
 *
 * @param ms ミリ秒
 * @return 待ち
 */
const wait = (ms: number): Promise<void> => new Promise((done) => setTimeout(done, ms));

/**
 * 社員を検索する
 *
 * @param keyword 検索語
 * @return 社員一覧
 */
export async function findStaff (keyword?: string): Promise<Staff[]> {

	await wait(200);

	if (!keyword) {
		return [...STAFF_LIST];
	}
	return STAFF_LIST.filter((staff) => staff.name.includes(keyword) || staff.mail.includes(keyword));

}

/**
 * 社員を 1 件取得する
 *
 * @param id 社員ID
 * @return 社員
 */
export async function findStaffById (id: string | number): Promise<Staff | null> {

	await wait(120);
	return STAFF_LIST.find((staff) => staff.id === Number(id)) ?? null;

}

/**
 * 社員を登録する
 *
 * @param form 入力値
 * @return 登録した社員
 */
export async function saveStaff (form: StaffForm): Promise<Staff> {

	await wait(400);

	const staff: Staff = {
		id: ++SEQUENCE,
		name: form.name ?? '',
		age: Number(form.age ?? 0),
		mail: form.mail ?? '',
		department: form.department ?? '',
		employment: form.employment ?? 'full',
		active: form.active !== false,
		notify: form.notify === true,
		note: form.note ?? ''
	};
	STAFF_LIST = [...STAFF_LIST, staff];

	return staff;

}

/**
 * 社員を削除する
 *
 * @param id 社員ID
 * @return 削除できたか
 */
export async function deleteStaff (id: number): Promise<boolean> {

	await wait(300);

	const before = STAFF_LIST.length;
	STAFF_LIST = STAFF_LIST.filter((staff) => staff.id !== id);
	return STAFF_LIST.length < before;

}

/**
 * 入力値を検証する
 *
 * @param form 入力値
 * @return 項目名 → エラー文言
 */
export function validateStaff (form: StaffForm = {}): Record<string, string> {

	const errors: Record<string, string> = {};

	if (!form.name) {
		errors.name = '氏名は必須です';
	}
	if (form.age == null) {
		errors.age = '年齢は必須です';
	} else if (Number(form.age) < 18 || Number(form.age) > 100) {
		errors.age = '年齢は 18 〜 100 で入力してください';
	}
	if (form.mail && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(form.mail)) {
		errors.mail = 'メールアドレスの形式が正しくありません';
	}
	if (!form.department) {
		errors.department = '部署を選んでください';
	}

	return errors;

}
