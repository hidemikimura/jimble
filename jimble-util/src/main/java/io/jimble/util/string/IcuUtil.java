package io.jimble.util.string;

import java.util.HashMap;
import java.util.Map;

/**
 * 文字列変換ユーティリティ（かな・全角半角）
 *
 * <p>
 * もとは ICU4J の {@code Transliterator} を呼んでいたが、<b>この4つのためだけに 14.5MB を抱える</b>
 * のはやめて、表を持つことにした（要件 D-120）。
 * ICU が返していたものと突き合わせてあり、<b>長音符の扱い以外は1文字も変わらない</b>。
 * </p>
 *
 * <h2>ICU と変えたところ</h2>
 * <p>
 * <b>{@link #convertToHiragana(String)} は「ー」をそのまま残す。</b>
 * ICU は「ひらがなに長音符は使わない」という決まりで直前のかなの母音に開いていた
 * （{@code コーヒー} → {@code こおひい}、{@code ヶ月} → {@code け月}）。
 * ふりがなの正規化や検索キー作りで<b>往復して元に戻らない</b>ほうが困るので、残すことにした。
 * {@code ヵ} → {@code か}、{@code ヶ} → {@code け} は ICU のままである（ひらがなの {@code ゕ ゖ} は使わない）。
 * </p>
 * <p>
 * ICU がついでにやっていた<b>無関係な文字の Unicode 正規化</b>（チベット文字の合成、結合文字の並べ替えなど）
 * はやらない。かなの変換を呼んで別の文字が書き換わるのは、原則5（隠れた副作用を作らない）に反する。
 * </p>
 *
 * <h2>やること</h2>
 * <ul>
 *   <li>ひらがな ⇄ カタカナ（{@code ゕ ゖ} は ICU と同じく動かさない）</li>
 *   <li><b>半角カナも取り込む</b>（{@code ｱ} → {@code ア} / {@code あ}、{@code ｡｢｣､･} も全角に）</li>
 *   <li><b>濁点・半濁点を合成する</b>（{@code か} + {@code ゙} → {@code ガ}）。
 *       合成するのは結合文字 {@code U+3099 U+309A} と半角の {@code ﾞ ﾟ} だけで、
 *       <b>離れた {@code ゛ ゜}（{@code U+309B U+309C}）は合成しない</b>（ICU と同じ）</li>
 *   <li><b>囲みカタカナと単位の合成文字を開く</b>（{@code ㋐} → {@code ア}、{@code ㌀} → {@code アパート}）</li>
 *   <li>全角 ⇄ 半角（ASCII・記号・カタカナ・ハングル字母・矢印・罫線）</li>
 * </ul>
 */
public class IcuUtil {

	// region かな

	/**
	 * カタカナに変換
	 *
	 * @param src 文字
	 * @return カタカナ
	 */
	public static String convertToKatakana (String src) {

		if (src == null || src.isEmpty()) {
			return "";
		}

		return compose(replace(src, KATAKANA));

	}

	/**
	 * ひらがなに変換
	 *
	 * @param src 文字
	 * @return ひらがな
	 */
	public static String convertToHiragana (String src) {

		// いったんカタカナに寄せてから落とす。
		// こうしないと、半角カナ・囲みカタカナ・合成文字の表を2つ持つことになる
		if (src == null || src.isEmpty()) {
			return "";
		}

		return compose(replace(replace(src, KATAKANA), HIRAGANA));

	}

	// endregion

	// region 全角・半角

	/**
	 * 半角に変換
	 *
	 * @param src 文字
	 * @return 半角文字
	 */
	public static String convertHankaku (String src) {

		if (src == null || src.isEmpty()) {
			return "";
		}

		StringBuilder sb = new StringBuilder(src.length());

		for (int i = 0; i < src.length(); i++) {

			char c = src.charAt(i);

			// 全角 ASCII（！ 〜 ～）はまとめてずらせる
			if (c >= 0xFF01 && c <= 0xFF5E) {
				sb.append((char) (c - 0xFEE0));
				continue;
			}

			String to = HANKAKU.get(c);
			sb.append(to == null ? String.valueOf(c) : to);

		}

		return sb.toString();

	}

	/**
	 * 全角に変換
	 *
	 * @param src 文字
	 * @return 全角文字
	 */
	public static String convertZenkaku (String src) {

		if (src == null || src.isEmpty()) {
			return "";
		}

		StringBuilder sb = new StringBuilder(src.length());

		for (int i = 0; i < src.length(); i++) {

			char c = src.charAt(i);

			/*
			 * <b>半角カナ＋半角の濁点は、先に2文字で見る</b>（ｶ + ﾞ → ガ）。
			 * 1文字ずつ直すと カ + ゙ になり、ICU の答えと変わる。
			 * なお ICU は<b>全角のカナに付いた濁点は合成しない</b>ので、ここも2文字目が半角のときだけ見る
			 */
			if (i + 1 < src.length()) {
				String composed = ZENKAKU_PAIR.get(src.substring(i, i + 2));
				if (composed != null) {
					sb.append(composed);
					i++;
					continue;
				}
			}

			// 半角 ASCII（! 〜 ~）はまとめてずらせる
			if (c >= 0x0021 && c <= 0x007E) {
				sb.append((char) (c + 0xFEE0));
				continue;
			}

			String to = ZENKAKU.get(c);
			sb.append(to == null ? String.valueOf(c) : to);

		}

		return sb.toString();

	}

	// endregion

	// region 中身

	/**
	 * 表を1文字ずつ引いて置き換える
	 *
	 * @param src	文字列
	 * @param table	表
	 * @return 置き換えた文字列
	 */
	private static String replace (String src, Map<Character, String> table) {

		StringBuilder sb = new StringBuilder(src.length());

		for (int i = 0; i < src.length(); i++) {

			char c = src.charAt(i);
			String to = table.get(c);

			sb.append(to == null ? String.valueOf(c) : to);

		}

		return sb.toString();

	}

	/**
	 * 濁点・半濁点を1文字にまとめる
	 *
	 * <p>
	 * まとめるのは結合文字（{@code U+3099} / {@code U+309A}）だけである。
	 * 半角の {@code ﾞ ﾟ} はここへ来るまでに結合文字へ寄せてあり、
	 * <b>離れた {@code ゛ ゜} は合成しない</b>（ICU と同じ。{@code ン゛} は {@code ン゛} のまま）。
	 * </p>
	 *
	 * @param src 文字列
	 * @return まとめた文字列
	 */
	private static String compose (String src) {

		StringBuilder sb = new StringBuilder(src.length());

		for (int i = 0; i < src.length(); i++) {

			char c = src.charAt(i);

			if (i + 1 < src.length()) {

				char mark = src.charAt(i + 1);

				// 表には結合文字の組しか入っていないので、
				// ここで絞らなくても答えは変わらない。1文字ごとの引き当てを減らすためである
				if (mark == 0x3099 || mark == 0x309A) {

					Character made = COMPOSED.get("" + c + mark);

					if (made != null) {
						sb.append(made.charValue());
						i++;
						continue;
					}

				}

			}

			sb.append(c);

		}

		return sb.toString();

	}

	// endregion

	// region 表

	/** ひらがな → カタカナ、半角カナ → カタカナ、囲みカタカナ・合成文字を開く */
	private static final Map<Character, String> KATAKANA = new HashMap<>();

	/** カタカナ → ひらがな */
	private static final Map<Character, String> HIRAGANA = new HashMap<>();

	/** 全角 → 半角 */
	private static final Map<Character, String> HANKAKU = new HashMap<>();

	/** 半角 → 全角 */
	private static final Map<Character, String> ZENKAKU = new HashMap<>();

	/** 半角カナ＋半角濁点 → 全角カナ1文字 */
	private static final Map<String, String> ZENKAKU_PAIR = new HashMap<>();

	/** かな＋結合濁点 → 濁点付き1文字 */
	private static final Map<String, Character> COMPOSED = new HashMap<>();

	/** 半角カナ（｡ 〜 ﾟ） */
	private static final String HALFWIDTH_KANA = "｡｢｣､･ｦｧｨｩｪｫｬｭｮｯｰｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝﾞﾟ";

	/** 半角カナに対応する全角（上と1文字ずつ対応する） */
	private static final String HALFWIDTH_KANA_FULL = "。「」、・ヲァィゥェォャュョッーアイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワン゙゚";

	/** 囲みカタカナ（㋐ 〜 ㋾） */
	private static final String CIRCLED_KATAKANA = "㋐㋑㋒㋓㋔㋕㋖㋗㋘㋙㋚㋛㋜㋝㋞㋟㋠㋡㋢㋣㋤㋥㋦㋧㋨㋩㋪㋫㋬㋭㋮㋯㋰㋱㋲㋳㋴㋵㋶㋷㋸㋹㋺㋻㋼㋽㋾";

	/** 囲みカタカナに対応するカタカナ */
	private static final String CIRCLED_KATAKANA_PLAIN = "アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヰヱヲ";

	/** 単位などの合成文字（㌀ 〜 ㍗）と、ゟ・ヿ。開くと2文字以上になる */
	private static final String[] SQUARED_KATAKANA = {
		"㌀", "アパート", "㌁", "アルファ",
		"㌂", "アンペア", "㌃", "アール",
		"㌄", "イニング", "㌅", "インチ",
		"㌆", "ウォン", "㌇", "エスクード",
		"㌈", "エーカー", "㌉", "オンス",
		"㌊", "オーム", "㌋", "カイリ",
		"㌌", "カラット", "㌍", "カロリー",
		"㌎", "ガロン", "㌏", "ガンマ",
		"㌐", "ギガ", "㌑", "ギニー",
		"㌒", "キュリー", "㌓", "ギルダー",
		"㌔", "キロ", "㌕", "キログラム",
		"㌖", "キロメートル", "㌗", "キロワット",
		"㌘", "グラム", "㌙", "グラムトン",
		"㌚", "クルゼイロ", "㌛", "クローネ",
		"㌜", "ケース", "㌝", "コルナ",
		"㌞", "コーポ", "㌟", "サイクル",
		"㌠", "サンチーム", "㌡", "シリング",
		"㌢", "センチ", "㌣", "セント",
		"㌤", "ダース", "㌥", "デシ",
		"㌦", "ドル", "㌧", "トン",
		"㌨", "ナノ", "㌩", "ノット",
		"㌪", "ハイツ", "㌫", "パーセント",
		"㌬", "パーツ", "㌭", "バーレル",
		"㌮", "ピアストル", "㌯", "ピクル",
		"㌰", "ピコ", "㌱", "ビル",
		"㌲", "ファラッド", "㌳", "フィート",
		"㌴", "ブッシェル", "㌵", "フラン",
		"㌶", "ヘクタール", "㌷", "ペソ",
		"㌸", "ペニヒ", "㌹", "ヘルツ",
		"㌺", "ペンス", "㌻", "ページ",
		"㌼", "ベータ", "㌽", "ポイント",
		"㌾", "ボルト", "㌿", "ホン",
		"㍀", "ポンド", "㍁", "ホール",
		"㍂", "ホーン", "㍃", "マイクロ",
		"㍄", "マイル", "㍅", "マッハ",
		"㍆", "マルク", "㍇", "マンション",
		"㍈", "ミクロン", "㍉", "ミリ",
		"㍊", "ミリバール", "㍋", "メガ",
		"㍌", "メガトン", "㍍", "メートル",
		"㍎", "ヤード", "㍏", "ヤール",
		"㍐", "ユアン", "㍑", "リットル",
		"㍒", "リラ", "㍓", "ルピー",
		"㍔", "ルーブル", "㍕", "レム",
		"㍖", "レントゲン", "㍗", "ワット",
		"ゟ", "ヨリ", "ヿ", "コト",
	};

	/** カタカナ → ひらがなで 0x60 ずらすだけでは足りないもの */
	private static final String KATAKANA_ODD = "ヵヶ";

	/** 上に対応するひらがな */
	private static final String KATAKANA_ODD_HIRAGANA = "かけ";

	/** カタカナ → ひらがなで2文字になるもの（ひらがなに濁点付きの ワ 行が無い） */
	private static final String[] KATAKANA_ODD_EXPAND = {
		"ヷ", "わ゙", "ヸ", "ゐ゙",
		"ヹ", "ゑ゙", "ヺ", "を゙",
	};

	/** 濁点付き1文字の、濁点を外した側 */
	private static final String COMPOSED_BASE = "かきくけこさしすせそたちつてとははひひふふへへほほうゝカキクケコサシスセソタチツテトハハヒヒフフヘヘホホウワヰヱヲヽ";

	/** 濁点付き1文字の、濁点の側（U+3099 か U+309A） */
	private static final String COMPOSED_MARK = "゙゙゙゙゙゙゙゙゙゙゙゙゙゙゙゙゚゙゚゙゚゙゚゙゚゙゙゙゙゙゙゙゙゙゙゙゙゙゙゙゙゙゙゚゙゚゙゚゙゚゙゚゙゙゙゙゙゙";

	/** 上の2つを合わせた1文字 */
	private static final String COMPOSED_CHAR = "がぎぐげござじずぜぞだぢづでどばぱびぴぶぷべぺぼぽゔゞガギグゲゴザジズゼゾダヂヅデドバパビピブプベペボポヴヷヸヹヺヾ";

	/** 全角 → 半角（かなと記号。1文字で収まるもの） */
	private static final String ZEN_KANA = "　、。「」゙゚ァアィイゥウェエォオカキクケコサシスセソタチッツテトナニヌネノハヒフヘホマミムメモャヤュユョヨラリルレロワヲン・ー";

	/** 上に対応する半角 */
	private static final String ZEN_KANA_HALF = " ､｡｢｣ﾞﾟｧｱｨｲｩｳｪｴｫｵｶｷｸｹｺｻｼｽｾｿﾀﾁｯﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓｬﾔｭﾕｮﾖﾗﾘﾙﾚﾛﾜｦﾝ･ｰ";

	/** 全角 → 半角で2文字になるもの（濁点を分ける） */
	private static final String[] ZEN_KANA_SPLIT = {
		"ガ", "ｶﾞ", "ギ", "ｷﾞ", "グ", "ｸﾞ",
		"ゲ", "ｹﾞ", "ゴ", "ｺﾞ", "ザ", "ｻﾞ",
		"ジ", "ｼﾞ", "ズ", "ｽﾞ", "ゼ", "ｾﾞ",
		"ゾ", "ｿﾞ", "ダ", "ﾀﾞ", "ヂ", "ﾁﾞ",
		"ヅ", "ﾂﾞ", "デ", "ﾃﾞ", "ド", "ﾄﾞ",
		"バ", "ﾊﾞ", "パ", "ﾊﾟ", "ビ", "ﾋﾞ",
		"ピ", "ﾋﾟ", "ブ", "ﾌﾞ", "プ", "ﾌﾟ",
		"ベ", "ﾍﾞ", "ペ", "ﾍﾟ", "ボ", "ﾎﾞ",
		"ポ", "ﾎﾟ", "ヴ", "ｳﾞ", "ヷ", "ﾜﾞ",
		"ヺ", "ｦﾞ",
	};

	/** 全角 → 半角の記号（￠ など、矢印・罫線） */
	private static final String ZEN_SYMBOL = "￠￡￢￣￤￥￦←↑→↓│■○";

	/** 上に対応する半角 */
	private static final String ZEN_SYMBOL_HALF = "¢£¬¯¦¥₩￩￪￫￬￨￭￮";

	/** 全角 → 半角のハングル字母 */
	private static final String ZEN_HANGUL = "ᄀᄁᄂᄃᄄᄅᄆᄇᄈᄉᄊᄋᄌᄍᄎᄏᄐᄑᄒᄚᄡᅠᅡᅢᅣᅤᅥᅦᅧᅨᅩᅪᅫᅬᅭᅮᅯᅰᅱᅲᅳᅴᅵᆪᆬᆭᆰᆱᆲᆳᆴᆵ";

	/** 上に対応する半角 */
	private static final String ZEN_HANGUL_HALF = "ﾡﾢﾤﾧﾨﾩﾱﾲﾳﾵﾶﾷﾸﾹﾺﾻﾼﾽﾾﾰﾴﾠￂￃￄￅￆￇￊￋￌￍￎￏￒￓￔￕￖￗￚￛￜﾣﾥﾦﾪﾫﾬﾭﾮﾯ";

	/** 半角 → 全角（1文字で収まるもの） */
	private static final String HAN_ALL = "｡｢｣､･ｦｧｨｩｪｫｬｭｮｯｰｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝﾞﾟﾠﾡﾢﾣﾤﾥﾦﾧﾨﾩﾪﾫﾬﾭﾮﾯﾰﾱﾲﾳﾴﾵﾶﾷﾸﾹﾺﾻﾼﾽﾾￂￃￄￅￆￇￊￋￌￍￎￏￒￓￔￕￖￗￚￛￜ￨￩￪￫￬￭￮ ¢£¥¦¬¯₩";

	/** 上に対応する全角 */
	private static final String HAN_ALL_ZEN = "。「」、・ヲァィゥェォャュョッーアイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワン゙゚ᅠᄀᄁᆪᄂᆬᆭᄃᄄᄅᆰᆱᆲᆳᆴᆵᄚᄆᄇᄈᄡᄉᄊᄋᄌᄍᄎᄏᄐᄑ하ᅢᅣᅤᅥᅦᅧᅨᅩᅪᅫᅬᅭᅮᅯᅰᅱᅲᅳᅴᅵ│←↑→↓■○　￠￡￥￤￢￣￦";

	/** 半角カナ2文字 → 全角カナ1文字 */
	private static final String[] HAN_PAIR = {
		"ｶﾞ", "ガ", "ｷﾞ", "ギ", "ｸﾞ", "グ",
		"ｹﾞ", "ゲ", "ｺﾞ", "ゴ", "ｻﾞ", "ザ",
		"ｼﾞ", "ジ", "ｽﾞ", "ズ", "ｾﾞ", "ゼ",
		"ｿﾞ", "ゾ", "ﾀﾞ", "ダ", "ﾁﾞ", "ヂ",
		"ﾂﾞ", "ヅ", "ﾃﾞ", "デ", "ﾄﾞ", "ド",
		"ﾊﾞ", "バ", "ﾊﾟ", "パ", "ﾋﾞ", "ビ",
		"ﾋﾟ", "ピ", "ﾌﾞ", "ブ", "ﾌﾟ", "プ",
		"ﾍﾞ", "ベ", "ﾍﾟ", "ペ", "ﾎﾞ", "ボ",
		"ﾎﾟ", "ポ", "ｳﾞ", "ヴ", "ﾜﾞ", "ヷ",
		"ｦﾞ", "ヺ",
	};

	static {

		// ひらがな → カタカナ。3041〜3094 と 309D〜309E は 0x60 ずれているだけ。
		// ゕ（3095）ゖ（3096）は動かさない（ICU も動かさない）
		for (char c = 0x3041; c <= 0x3094; c++) {
			KATAKANA.put(c, String.valueOf((char) (c + 0x60)));
		}
		KATAKANA.put((char) 0x309D, String.valueOf((char) 0x30FD));
		KATAKANA.put((char) 0x309E, String.valueOf((char) 0x30FE));

		put(KATAKANA, HALFWIDTH_KANA, HALFWIDTH_KANA_FULL);
		put(KATAKANA, CIRCLED_KATAKANA, CIRCLED_KATAKANA_PLAIN);
		put(KATAKANA, SQUARED_KATAKANA);

		// カタカナ → ひらがな。30A1〜30F4 と 30FD〜30FE は 0x60 ずれているだけ
		for (char c = 0x30A1; c <= 0x30F4; c++) {
			HIRAGANA.put(c, String.valueOf((char) (c - 0x60)));
		}
		HIRAGANA.put((char) 0x30FD, String.valueOf((char) 0x309D));
		HIRAGANA.put((char) 0x30FE, String.valueOf((char) 0x309E));

		put(HIRAGANA, KATAKANA_ODD, KATAKANA_ODD_HIRAGANA);
		put(HIRAGANA, KATAKANA_ODD_EXPAND);

		// 全角 → 半角
		put(HANKAKU, ZEN_KANA, ZEN_KANA_HALF);
		put(HANKAKU, ZEN_SYMBOL, ZEN_SYMBOL_HALF);
		put(HANKAKU, ZEN_HANGUL, ZEN_HANGUL_HALF);
		put(HANKAKU, ZEN_KANA_SPLIT);

		// 半角 → 全角
		put(ZENKAKU, HAN_ALL, HAN_ALL_ZEN);
		for (int i = 0; i < HAN_PAIR.length; i += 2) {
			ZENKAKU_PAIR.put(HAN_PAIR[i], HAN_PAIR[i + 1]);
		}

		// 濁点の合成
		for (int i = 0; i < COMPOSED_CHAR.length(); i++) {
			COMPOSED.put("" + COMPOSED_BASE.charAt(i) + COMPOSED_MARK.charAt(i), COMPOSED_CHAR.charAt(i));
		}

	}

	/**
	 * 1文字ずつ対応する2つの文字列を表に入れる
	 *
	 * @param table	表
	 * @param from	変換元
	 * @param to	変換先（from と同じ長さ）
	 */
	private static void put (Map<Character, String> table, String from, String to) {

		if (from.length() != to.length()) {
			throw new IllegalStateException("表の長さが違う: " + from.length() + " / " + to.length());
		}

		for (int i = 0; i < from.length(); i++) {
			table.put(from.charAt(i), String.valueOf(to.charAt(i)));
		}

	}

	/**
	 * 「変換元, 変換先」の並びを表に入れる
	 *
	 * @param table	表
	 * @param pairs	変換元と変換先が交互に並んだもの
	 */
	private static void put (Map<Character, String> table, String[] pairs) {

		for (int i = 0; i < pairs.length; i += 2) {
			table.put(pairs[i].charAt(0), pairs[i + 1]);
		}

	}

	// endregion

}
