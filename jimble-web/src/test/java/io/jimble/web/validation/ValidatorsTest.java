package io.jimble.web.validation;

import io.jimble.util.data.Data;
import io.jimble.util.exception.CodeException;
import io.jimble.web.validation.error.ValidationErrorType;
import io.jimble.web.validation.validator.CharacterTypeValidator.CharacterType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 検証器の1つずつ（要件 D-161）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>13 個ある検証器のうち、テストが触っていたのは4つだけだった。</b>
 * {@code bool} {@code characterType} {@code date} {@code domain} {@code enum}
 * {@code number} {@code regex} {@code textByteLength} {@code url} の<b>9つは
 * 一度も動かしていなかった</b>。
 * </p>
 *
 * <p>
 * <b>検証が壊れたときに出るのは「エラーが出ないこと」である。</b>
 * 通してはいけない入力が黙って通り、<b>その先で保存されてから</b>おかしくなる。
 * 動かしてみたら実際に壊れていて、直したのが下の4件である。
 * </p>
 *
 * <ul>
 *   <li><b>{@code bool()} は何も検証していなかった。</b>
 *       {@code yes} も {@code はい} も {@code -1} も通り、
 *       <b>読み出し側は同じ値を {@code false} と読む</b></li>
 *   <li><b>5つの検証器が部分一致だった。</b>
 *       {@code a@example.com'; DROP--} が {@code email()} を、
 *       {@code https://example.com} が {@code domain()} を通っていた</li>
 *   <li><b>{@code number()} が {@code NaN} を通していた。</b>
 *       {@code NaN} との比較は {@code <} も {@code >} も false なので、
 *       <b>{@code min}/{@code max} ごとすり抜ける</b></li>
 *   <li><b>{@code characterType()} は空の指定で {@code PatternSyntaxException} を投げていた</b></li>
 * </ul>
 *
 * <p>
 * <b>ここは「通る/通らない」を1件ずつ並べる場所である。</b>
 * 組み合わせ（順番・複数行・メッセージ）は {@code ValidationTest} が見ている。
 * </p>
 */
class ValidatorsTest {

	/** enum の見本 */
	enum Color {

		/** 赤 */
		RED

		/** 青（小文字の定数名も試す） */
		, blue

	}

	/**
	 * 通るか
	 *
	 * @param rule	規則
	 * @param value	値
	 * @return	通る場合 = true
	 */
	private static boolean ok (ValidationRule rule, Object value) throws CodeException {

		return !rule.validate(null, new Data(), false, value).error();

	}

	/**
	 * どの種別で落ちたか
	 *
	 * @param rule	規則
	 * @param value	値
	 * @return	種別
	 */
	private static ValidationErrorType errorType (ValidationRule rule, Object value) throws CodeException {

		return rule.validate(null, new Data(), false, value).validationError().errorType;

	}

	// region 全部に共通すること

	@Test
	@DisplayName("null と空文字は、どの検証器も通す（弾くのは required() の仕事）")
	void nullAndEmptyAreAlwaysValid () throws CodeException {

		/*
		 * <b>ここを崩すと「任意項目」が書けなくなる。</b>
		 * 「入っていれば形を見る、入っていなければ何も言わない」が全体の約束で、
		 * <b>入っているかどうかは required() だけが決める</b>。
		 */
		List<ValidationRule> rules = List.of(
			new ValidationRule().bool()
			, new ValidationRule().number()
			, new ValidationRule().integer()
			, new ValidationRule().email()
			, new ValidationRule().url()
			, new ValidationRule().domain()
			, new ValidationRule().date()
			, new ValidationRule().date("yyyy-MM-dd")
			, new ValidationRule().regex("^[0-9]+$")
			, new ValidationRule().enumType(Color.class)
			, new ValidationRule().textLength(3, 5)
			, new ValidationRule().textByteLength(3, 5)
			, new ValidationRule().characterType(new CharacterType[] { CharacterType.NumberHan }));

		for (ValidationRule rule : rules) {
			assertTrue(ok(rule, null), "null を落としています");
			assertTrue(ok(rule, ""), "空文字を落としています（required() の仕事です）");
		}

		assertFalse(ok(new ValidationRule().required(), ""), "required() まで空文字を通しています");
		assertFalse(ok(new ValidationRule().required(), null));

	}

	// endregion

	// region 真偽値

	@Test
	@DisplayName("D-161 bool() は true/false/1/0 だけを通す")
	void boolTakesOnlyFourSpellings () throws CodeException {

		ValidationRule rule = new ValidationRule().bool();

		for (String value : List.of("true", "false", "1", "0", "TRUE", "False")) {
			assertTrue(ok(rule, value), "落としています: " + value);
		}

	}

	@Test
	@DisplayName("D-161 bool() は yes / はい / 2 を落とす")
	void boolRefusesEverythingElse () throws CodeException {

		ValidationRule rule = new ValidationRule().bool();

		/*
		 * <b>直す前は、この5つが全部通っていた。</b>
		 * しかも読み出し側（{@code Data.getBooleanObject}）は
		 * {@code "true"} と {@code "1"} 以外を {@code false} と読むので、
		 * <b>{@code yes} と答えた人が {@code いいえ} として保存されていた</b>——
		 * 例外もログも出ないまま。
		 */
		for (String value : List.of("yes", "no", "はい", "2", "-1", "on")) {
			assertFalse(ok(rule, value), "通しています: " + value);
		}

		assertEquals(ValidationErrorType.Boolean, errorType(rule, "yes"));

	}

	// endregion

	// region 数値

	@Test
	@DisplayName("number() が通す綴り")
	void numberTakesNumbers () throws CodeException {

		ValidationRule rule = new ValidationRule().number();

		for (String value : List.of("1", "0", "1.5", "-1.5", "+1", ".5", "1e3", "-2E-3")) {
			assertTrue(ok(rule, value), "落としています: " + value);
		}

	}

	@Test
	@DisplayName("D-161 number() は NaN / Infinity / 1d / 1f を落とす")
	void numberRefusesJavaSpellings () throws CodeException {

		ValidationRule rule = new ValidationRule().number();

		/*
		 * <b>{@code Double.parseDouble} は Java のソースに書ける綴りを全部受け付ける。</b>
		 * 数値の検証としては<b>広すぎる</b>。
		 */
		for (String value : List.of("NaN", "Infinity", "-Infinity", "1d", "1f", "0x10", "1_000")) {
			assertFalse(ok(rule, value), "通しています: " + value);
		}

	}

	@Test
	@DisplayName("D-161 NaN は範囲の検査ごとすり抜けない")
	void nanDoesNotSlipPastTheRange () throws CodeException {

		/*
		 * <b>これがいちばん危ない形だった。</b>
		 * {@code NaN < 0} も {@code 10 < NaN} も false なので、
		 * <b>min も max も「範囲内」と答える</b>——
		 * {@code number(0, 10)} と書いてあるのに、<b>検査そのものが無効になる</b>。
		 */
		assertFalse(ok(new ValidationRule().number(0, 10), "NaN"), "範囲の検査をすり抜けています");
		assertFalse(ok(new ValidationRule().numberMax(10), "Infinity"), "上限をすり抜けています");

		/*
		 * <b>綴りだけ見ても足りない。</b>{@code 1e400} は書き方としては数値だが、
		 * double に入れると {@code Infinity} になる——<b>そこでまた上限をすり抜ける</b>。
		 */
		assertFalse(ok(new ValidationRule().numberMax(10), "1e400"), "桁あふれで上限をすり抜けています");
		assertTrue(ok(new ValidationRule().numberMax(10), "1e-400"), "小さすぎる値まで落としています");

		/*
		 * <b>上限を書いていないときは、比較が助けてくれない。</b>
		 * {@code number()} だけなら {@code 1e400} は<b>綴りとしては正しい</b>ので、
		 * {@code Double.isFinite} で落とさないと通ってしまう——
		 * <b>この検証器が double で見ている以上、double に入らない値は通せない</b>。
		 */
		assertFalse(ok(new ValidationRule().number(), "1e400"), "double に入らない値を通しています");
		assertFalse(ok(new ValidationRule().number(), "-1e400"));

	}

	@Test
	@DisplayName("D-161 number() は前後の空白を落とさない（integer() と揃える）")
	void numberDoesNotTrim () throws CodeException {

		/*
		 * {@code Double.parseDouble} は前後の空白を黙って落とすが、
		 * {@code Long.parseLong} は落とさない。<b>同じ {@code " 1 "} が
		 * {@code number()} だけ通る</b>のは説明が付かない。
		 */
		assertFalse(ok(new ValidationRule().number(), " 1 "));
		assertFalse(ok(new ValidationRule().integer(), " 1 "));

	}

	@Test
	@DisplayName("number(min, max) は両端を含む")
	void numberRangeIsInclusive () throws CodeException {

		ValidationRule rule = new ValidationRule().number(0, 10);

		assertTrue(ok(rule, "0"));
		assertTrue(ok(rule, "10"));
		assertFalse(ok(rule, "-0.1"));
		assertFalse(ok(rule, "10.1"));

		assertEquals(ValidationErrorType.Number, errorType(rule, "10.1"));

	}

	// endregion

	// region メールアドレス / URL / ドメイン

	@Test
	@DisplayName("D-161 email() は全体が1つのメールアドレスであることを見る")
	void emailIsWholeString () throws CodeException {

		ValidationRule rule = new ValidationRule().email();

		assertTrue(ok(rule, "a@example.com"));
		assertTrue(ok(rule, "user.name+tag@example.co.jp"));

		/*
		 * <b>直す前は部分一致だった。</b>中にメールアドレスらしい文字列が
		 * 入ってさえいれば通ったので、<b>後ろに何を付けても検証を抜けた</b>。
		 */
		assertFalse(ok(rule, "こんにちは a@example.com です"), "部分一致で通しています");
		assertFalse(ok(rule, "a@example.com'; DROP--"), "後ろのゴミごと通しています");
		assertFalse(ok(rule, "a@example.com\nBcc: x@example.com"), "改行から先を見ていません");

		assertFalse(ok(rule, "@example.com"));
		assertFalse(ok(rule, "not an email"));

	}

	@Test
	@DisplayName("D-161 url() は全体が1つの URL であることを見る")
	void urlIsWholeString () throws CodeException {

		ValidationRule rule = new ValidationRule().url();

		assertTrue(ok(rule, "https://example.com"));
		assertTrue(ok(rule, "http://example.com/a?b=c"));
		assertTrue(ok(rule, "example.com"), "スキーム無しは通す約束です");

		assertFalse(ok(rule, "見て https://example.com ここ"), "部分一致で通しています");
		assertFalse(ok(rule, "https://example.com そのあとに何か"));
		assertFalse(ok(rule, "https://"));

		/*
		 * <b>これは直す前から落ちていた。</b>
		 * 落ち続けることを確かめておく——<b>リンクにして出す先である</b>。
		 */
		assertFalse(ok(rule, "javascript:alert(1)"));

		assertEquals(ValidationErrorType.Url, errorType(rule, "not a url"));

	}

	@Test
	@DisplayName("D-161 domain() は全体が1つのドメインであることを見る")
	void domainIsWholeString () throws CodeException {

		ValidationRule rule = new ValidationRule().domain();

		assertTrue(ok(rule, "example.com"));
		assertTrue(ok(rule, "www.example.co.jp"));
		assertTrue(ok(rule, "192.168.0.1"), "IP アドレスも通す約束です");

		assertFalse(ok(rule, "https://example.com"), "スキーム付きを通しています");
		assertFalse(ok(rule, "example.com/../etc"), "パスごと通しています");
		assertFalse(ok(rule, "ここに example.com が入る"));
		assertFalse(ok(rule, "-example.com"));
		assertFalse(ok(rule, "example"), "点が無いものは通さない約束です");

	}

	// endregion

	// region 正規表現 / 文字種別

	@Test
	@DisplayName("D-161 regex() は全体が当てはまることを見る")
	void regexIsWholeString () throws CodeException {

		/*
		 * <b>直す前は部分一致だった。</b>
		 * {@code regex("[0-9]{4}")} は「4桁の数字」のつもりで書くものだが、
		 * <b>{@code abc1234xyz} が通っていた</b>。
		 */
		assertFalse(ok(new ValidationRule().regex("[0-9]{4}"), "abc1234xyz"), "部分一致で通しています");
		assertTrue(ok(new ValidationRule().regex("[0-9]{4}"), "1234"));

		// 頭と尻を書いてある正規表現は、そのまま動く
		assertTrue(ok(new ValidationRule().regex("^[0-9]+$"), "123"));
		assertFalse(ok(new ValidationRule().regex("^[0-9]+$"), "12a"));

		// 部分一致させたいときは自分で挟む
		assertTrue(ok(new ValidationRule().regex(".*[0-9]+.*"), "abc1def"));

	}

	@Test
	@DisplayName("D-161 末尾の改行は通さない")
	void trailingNewlineIsRefused () throws CodeException {

		/*
		 * <b>Java の {@code $} は「末尾」ではない。</b>
		 * <b>末尾の改行1つの手前にも当たる</b>ので、{@code ^[0-9]+$} を
		 * {@code find()} で当てると <b>{@code "12\n"} が通る</b>。
		 * {@code matches()} なら文字列全部と突き合わせるので当たらない。
		 */
		assertFalse(ok(new ValidationRule().regex("^[0-9]+$"), "12\n"));
		assertFalse(ok(new ValidationRule()
			.characterType(new CharacterType[] { CharacterType.NumberHan }), "12\n"));

	}

	@Test
	@DisplayName("characterType() は指定した文字種だけを通す")
	void characterTypeTakesOnlyTheListedTypes () throws CodeException {

		ValidationRule number = new ValidationRule()
			.characterType(new CharacterType[] { CharacterType.NumberHan });

		assertTrue(ok(number, "123"));
		assertFalse(ok(number, "12a"));
		assertFalse(ok(number, "１２３"), "全角を通しています");

		ValidationRule kana = new ValidationRule()
			.characterType(new CharacterType[] { CharacterType.Hiragana });

		assertTrue(ok(kana, "ひらがな"));
		assertFalse(ok(kana, "カタカナ"));

		assertEquals(ValidationErrorType.CharacterType, errorType(number, "12a"));

	}

	@Test
	@DisplayName("characterType() は文字種と文字を混ぜられる")
	void characterTypeMixesTypesAndCharacters () throws CodeException {

		ValidationRule rule = new ValidationRule()
			.characterType(new CharacterType[] { CharacterType.AlphabetHan }, new Character[] { '-' });

		assertTrue(ok(rule, "ab-c"));
		assertTrue(ok(rule, "abc"));
		assertFalse(ok(rule, "ab_c"), "指定していない記号を通しています");

		// 文字だけの指定もできる
		ValidationRule only = new ValidationRule().characterType(new Character[] { 'a', 'b' });

		assertTrue(ok(only, "abba"));
		assertFalse(ok(only, "abc"));

	}

	@Test
	@DisplayName("D-161 characterType() に何も指定しないと、その場で落ちる")
	void characterTypeWithoutAnythingFailsLoudly () throws CodeException {

		/*
		 * <b>直す前は {@code ^[]+$} を組み立てて {@code PatternSyntaxException} が出ていた。</b>
		 * 「正規表現が壊れています」としか読めないので、
		 * <b>指定を書き忘れたことに気づけない</b>。
		 */
		IllegalStateException ex = assertThrows(IllegalStateException.class
			, () -> ok(new ValidationRule().characterType(new CharacterType[] {}), "a"));

		assertTrue(ex.getMessage().contains("characterType"), ex.getMessage());

	}

	// endregion

	// region enum

	@Test
	@DisplayName("enumType() は定数名と1文字ずつ同じものだけを通す")
	void enumTakesTheConstantName () throws CodeException {

		ValidationRule rule = new ValidationRule().enumType(Color.class);

		assertTrue(ok(rule, "RED"));
		assertTrue(ok(rule, "blue"));

		/*
		 * <b>大小は無視しない。</b>{@code Enum.valueOf} と同じ扱いで、
		 * <b>読み替えを始めると「どちらで保存されるのか」が分からなくなる</b>。
		 */
		assertFalse(ok(rule, "red"), "大文字小文字を読み替えています");
		assertFalse(ok(rule, "BLUE"));
		assertFalse(ok(rule, "GREEN"));
		assertFalse(ok(rule, " RED"), "前後の空白を落としています");

		assertEquals(ValidationErrorType.Enum, errorType(rule, "GREEN"));

	}

	// endregion

	// region 文字列のバイト長

	@Test
	@DisplayName("textByteLength() は文字数ではなくバイト数で見る")
	void textByteLengthCountsBytes () throws CodeException {

		ValidationRule rule = new ValidationRule().textByteLength(1, 3);

		assertTrue(ok(rule, "abc"), "半角3文字 = 3バイト");
		assertFalse(ok(rule, "abcd"));

		/*
		 * <b>ここが文字数と分かれるところである。</b>
		 * UTF-8 のひらがなは1文字3バイトなので、<b>「あ」で上限に届く</b>。
		 */
		assertTrue(ok(rule, "あ"), "UTF-8 のひらがなは3バイトです");
		assertFalse(ok(rule, "あい"), "6バイトを通しています");

		// 文字数で見るほうは通る
		assertTrue(ok(new ValidationRule().textLength(1, 3), "あい"));

		assertEquals(ValidationErrorType.TextByteLength, errorType(rule, "あい"));

	}

	@Test
	@DisplayName("textByteLength() は文字コードを変えられる")
	void textByteLengthTakesACharset () throws CodeException {

		// Shift_JIS のひらがなは1文字2バイト
		assertTrue(ok(new ValidationRule()
			.textByteLength(1, 4, java.nio.charset.Charset.forName("Shift_JIS")), "あい"));

		assertFalse(ok(new ValidationRule()
			.textByteLength(1, 4, StandardCharsets.UTF_8), "あい"));

	}

	@Test
	@DisplayName("textByteLengthMin / Max は片側だけを見る")
	void textByteLengthOneSided () throws CodeException {

		assertTrue(ok(new ValidationRule().textByteLengthMin(3), "abcdefghij"));
		assertFalse(ok(new ValidationRule().textByteLengthMin(3), "ab"));

		assertTrue(ok(new ValidationRule().textByteLengthMax(3), "a"));
		assertFalse(ok(new ValidationRule().textByteLengthMax(3), "abcd"));

	}

	// endregion

	// region 日時

	@Test
	@DisplayName("date(format) は書式に当てはまるものを通す")
	void dateWithAFormat () throws CodeException {

		ValidationRule rule = new ValidationRule().date("yyyy-MM-dd");

		assertTrue(ok(rule, "2026-09-12"));
		assertFalse(ok(rule, "2026/09/12"), "区切りが違うものを通しています");
		assertFalse(ok(rule, "abc"));

		assertEquals(ValidationErrorType.Date, errorType(rule, "abc"));

	}

	@Test
	@DisplayName("date() は書式を決めずに読めるものを通す")
	void dateWithoutAFormat () throws CodeException {

		ValidationRule rule = new ValidationRule().date();

		for (String value : List.of("2026-09-12", "2026/09/12", "2026-09-12 10:00:00"
			, "20260912", "2026年09月12日")) {
			assertTrue(ok(rule, value), "落としています: " + value);
		}

		assertFalse(ok(rule, "abc"));
		assertFalse(ok(rule, "12"));

	}

	// endregion

	// region ここで固定していないこと

	/*
	 * - <b>{@code date()} が「正しい日付か」を見ていないことは、直していない。</b>
	 *   {@code 2026-13-01}（13月）も {@code 2026-02-31}（2月31日）も、
	 *   {@code date()} でも {@code date("yyyy-MM-dd")} でも<b>通る</b>。
	 *   {@code SimpleDateFormat} が既定で寛容（13月を翌年1月として読む）で、
	 *   かつ<b>途中まで読めれば成功を返す</b>（{@code 2026-09-12x} が通る）ためである。
	 *   {@code date()} 側は {@code Convertor} の共通変換を通っていて、
	 *   <b>DB の読み出しやリクエストの変換と同じ道</b>なので、
	 *   ここだけ締めると影響が枠組み全体に及ぶ——<b>意図して手を付けていない</b>（要件 D-161）。
	 *   厳密に見たい項目は {@code regex()} を重ねること
	 * - <b>{@code email()} が RFC のどこまでを通すか</b>は見ていない。
	 *   {@code Patterns.EMAIL_ADDRESS} が決めていて、<b>ここで固定すると
	 *   パターンを直すたびにテストが落ちる</b>。見ているのは「全体で当てるか」だけである
	 * - <b>{@code custom()} に渡した検証器</b>は見ていない（{@code ValidationTest} が見ている）
	 * - <b>複数の規則を重ねたときの順番</b>も見ていない（同上）
	 */

	// endregion

}
