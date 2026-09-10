import { UI, App } from '../../dist/index.js';
import type { Context } from '../../dist/index.js';
import '../staff/ecx-theme.js';

/* 現在の語彙だけでログイン画面をどこまで書けるかの検証 */

declare global {
	interface JimbleAppState {
		form: { loginId?: string; password?: string };
		errors: Record<string, string>;
		message: string;
		busy: boolean;
	}
}

const LoginPage = (ctx: Context) => UI.column(
	UI.card(
		UI.title('管理画面ログイン'),
		UI.caption('社内アカウントでログインしてください'),
		UI.text('loginId').label('ログインID').required().bind('form.loginId')
			.error(() => ctx.get('errors.loginId')),
		UI.password('password').label('パスワード').required().bind('form.password')
			.error(() => ctx.get('errors.password')),
		UI.label(() => ctx.get('message')).danger().when(() => !!ctx.get('message')),
		UI.button('ログイン').primary().loading(() => ctx.get('busy')).onClick(login)
	).gap('lg').width('sm')
).align('center').pad('xl');

async function login (ctx: Context): Promise<void> {
	const form = ctx.get('form') ?? {};
	const errors: Record<string, string> = {};
	if (!form.loginId) errors.loginId = 'ログインIDを入力してください';
	if (!form.password) errors.password = 'パスワードを入力してください';
	ctx.store.patch({ errors, message: '' });
	if (Object.keys(errors).length > 0) return;

	ctx.set('busy', true);
	await new Promise((done) => setTimeout(done, 500));
	ctx.store.patch({ busy: false, message: 'ログインIDまたはパスワードが違います' });
}

App.of()
	.state({ form: {}, errors: {}, message: '', busy: false })
	.theme('ecx')
	.route('/', LoginPage)
	.mount();
