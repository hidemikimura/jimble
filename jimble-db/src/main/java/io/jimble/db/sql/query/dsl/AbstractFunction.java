package io.jimble.db.sql.query.dsl;

import io.jimble.db.dialect.SqlWriter;
import io.jimble.db.sql.query.select.ISelect;
import io.jimble.util.data.definition.IColumn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 引数を取る関数の土台（要件 F-D-31）
 *
 * <p>
 * 移送元の DSL は<b>1関数につき同じ 100 行</b>を書いていた
 * （引数の書き出し・パラメータの拾い上げ・列と DSL と値の場合分け）。
 * 関数が増えるほど、<b>どれか1つだけ直し忘れる</b>形になる。
 * ここに1つだけ置いて、関数ごとのクラスは
 * <b>「どう並べて書くか」だけ</b>を持つようにした。
 * </p>
 *
 * <p>
 * 引数には次のものを渡せる。
 * </p>
 *
 * <table>
 *   <caption>引数に渡せるもの</caption>
 *   <tr><th>渡すもの</th><th>SQL に出るもの</th></tr>
 *   <tr><td>{@link IColumn}</td><td>{@code `テーブル`.`列`}</td></tr>
 *   <tr><td>{@link IDsl}（関数）</td><td>その関数</td></tr>
 *   <tr><td>{@link ISelect}</td><td>その式</td></tr>
 *   <tr><td>そのほかの値</td><td>{@code ?}（バインドする）</td></tr>
 * </table>
 */
public abstract class AbstractFunction implements IDsl {

	/* 引数 */
	private final List<Object> args;

	/**
	 * コンストラクタ
	 *
	 * @param args	引数
	 */
	protected AbstractFunction (Object...args) {

		this.args = args == null ? List.of() : Arrays.asList(args);

	}

	// region 引数

	/**
	 * 引数の数
	 *
	 * @return	数
	 */
	protected int argCount () {

		return args.size();

	}

	/**
	 * 引数
	 *
	 * @param index	何番目か（0から）
	 * @return	引数
	 */
	protected Object arg (int index) {

		return args.get(index);

	}

	/**
	 * 引数を1つ書き出す
	 *
	 * @param sb	書き出し先
	 * @param index	何番目か（0から）
	 */
	protected void writeArg (SqlWriter sb, int index) {

		write(sb, args.get(index));

	}

	/**
	 * 引数を1つ書き出す {@link Runnable}
	 *
	 * <p>{@code Dialect} は「値をここに書く」を Runnable で受け取る。</p>
	 *
	 * @param sb	書き出し先
	 * @param index	何番目か（0から）
	 * @return	書き出す処理
	 */
	protected Runnable writer (SqlWriter sb, int index) {

		return () -> writeArg(sb, index);

	}

	/**
	 * 引数を全部、カンマ区切りで書き出す
	 *
	 * @param sb	書き出し先
	 */
	protected void writeArgs (SqlWriter sb) {

		for (int i = 0; i < args.size(); i++) {
			if (i > 0) {
				sb.append(", ");
			}
			writeArg(sb, i);
		}

	}

	/**
	 * 値を1つ書き出す
	 *
	 * @param sb	書き出し先
	 * @param value	値
	 */
	protected static void write (SqlWriter sb, Object value) {

		/*
		 * まとまった値は<b>? 1つに対して複数バインドされる</b>
		 * （Parameter#flatten が広げる）。以降のバインドが全部ずれるので、
		 * 組み立てるところで落とす。
		 */
		if (value instanceof java.util.Collection || (value != null && value.getClass().isArray())) {
			throw new IllegalArgumentException(
				"関数の引数に一覧は渡せません: " + value.getClass().getName());
		}

		if (value instanceof IColumn column) {
			sb.qualified(column);
		} else if (value instanceof IDsl dsl) {
			dsl.dslSql(sb);
		} else if (value instanceof ISelect select) {
			select.selectSql(sb);
		} else {
			sb.append("?");
		}

	}

	// endregion

	// region パラメータ

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean hasParameter () {

		return !parameters().isEmpty();

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object getParameter () {

		return parameters();

	}

	/**
	 * バインドするパラメータ
	 *
	 * <p>
	 * <b>書き出す順と同じ順</b>で並べる。
	 * ここがずれると、値が別の {@code ?} に入る。
	 * </p>
	 *
	 * @return	パラメータ
	 */
	protected List<Object> parameters () {

		List<Object> params = new ArrayList<>();

		for (Object value : args) {
			addParameter(params, value);
		}

		return params;

	}

	/**
	 * パラメータを1つ足す
	 *
	 * @param params	パラメータ一覧
	 * @param value		値
	 */
	protected static void addParameter (List<Object> params, Object value) {

		if (value instanceof IColumn) {
			// 列は SQL に出るのでバインドしない
			return;
		}

		if (value instanceof IDsl dsl) {
			if (dsl.hasParameter()) {
				params.add(dsl.getParameter());
			}
			return;
		}

		if (value instanceof ISelect select) {
			if (select.hasParameter()) {
				params.add(select.getParameter());
			}
			return;
		}

		params.add(value);

	}

	// endregion

}
