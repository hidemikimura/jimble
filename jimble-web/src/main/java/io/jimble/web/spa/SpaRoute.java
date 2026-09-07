package io.jimble.web.spa;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SPA のパスと書き換え処理
 *
 * <p>
 * {@code /items/{id}} のような書き方でパスパラメータを取る。
 * </p>
 *
 * <h2>移送元から直したところ</h2>
 * <p>
 * <b>パスの文字をそのまま正規表現に埋めていた。</b>
 * {@code .} や {@code +} が正規表現のメタ文字として効いてしまうので、
 * {@code /a.b/{id}} が {@code /axb/1} にマッチする。
 * 変数以外の部分は {@link Pattern#quote} で リテラル として扱う。
 * </p>
 */
public final class SpaRoute {

	/* もとのパス */
	private final String path;

	/* 正規表現 */
	private final Pattern pattern;

	/* 変数名（出現順） */
	private final List<String> variableNames;

	/* 書き換え処理 */
	private final SpaRewriter rewriter;

	/**
	 * コンストラクタ
	 *
	 * @param path		パス（{@code /items/{id}} 形式）
	 * @param rewriter	書き換え処理
	 */
	public SpaRoute (String path, SpaRewriter rewriter) {

		this.path = path;
		this.rewriter = rewriter;
		this.variableNames = new ArrayList<>();
		this.pattern = compile(path, variableNames);

	}

	/**
	 * もとのパス
	 *
	 * @return	パス
	 */
	public String path () {

		return path;

	}

	/**
	 * 書き換え処理
	 *
	 * @return	書き換え処理
	 */
	public SpaRewriter rewriter () {

		return rewriter;

	}

	/**
	 * マッチするか
	 *
	 * @param requestPath	リクエストのパス
	 * @return	パスパラメータ（マッチしなければ null）
	 */
	public Map<String, String> match (String requestPath) {

		Matcher matcher = pattern.matcher(requestPath == null ? "" : requestPath);

		if (!matcher.matches()) {
			return null;
		}

		Map<String, String> variables = new LinkedHashMap<>();

		for (int i = 0; i < variableNames.size(); i++) {
			variables.put(variableNames.get(i), matcher.group(i + 1));
		}

		return variables;

	}

	/**
	 * パスを正規表現にする
	 *
	 * @param path			パス
	 * @param variableNames	変数名の受け取り先
	 * @return	正規表現
	 */
	private static Pattern compile (String path, List<String> variableNames) {

		StringBuilder regex = new StringBuilder("^");
		StringBuilder literal = new StringBuilder();
		StringBuilder name = new StringBuilder();

		boolean inVariable = false;

		for (char c : path.toCharArray()) {

			if (c == '{') {
				// 変数以外はメタ文字として効かせない
				appendLiteral(regex, literal);
				inVariable = true;
			} else if (c == '}') {
				inVariable = false;
				variableNames.add(name.toString());
				name.setLength(0);
				regex.append("([^/]+)");
			} else if (inVariable) {
				name.append(c);
			} else {
				literal.append(c);
			}

		}

		appendLiteral(regex, literal);
		regex.append("$");

		return Pattern.compile(regex.toString());

	}

	/**
	 * 文字どおりの部分を足す
	 *
	 * @param regex		正規表現
	 * @param literal	文字どおりの部分
	 */
	private static void appendLiteral (StringBuilder regex, StringBuilder literal) {

		if (literal.isEmpty()) {
			return;
		}

		regex.append(Pattern.quote(literal.toString()));
		literal.setLength(0);

	}

}
