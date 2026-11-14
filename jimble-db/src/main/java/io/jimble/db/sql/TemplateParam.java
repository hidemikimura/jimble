package io.jimble.db.sql;

import java.util.regex.Pattern;

/**
 * 値の印（{@link SelectTemplate} の値を差し替える場所。3.0.0）
 *
 * <p>
 * {@link io.jimble.db.sql.query.dsl.Dsl#param(String)}（1 つの値）と
 * {@link io.jimble.db.sql.query.dsl.Dsl#params(String)}（{@code IN} / {@code NOT IN} の値の並び）で作り、
 * 値を書く場所に置く。<b>{@link SQL#template(SelectBuilder)} の中でだけ使える</b>。
 * ビルダーのまま DB に投げると、組み立てのところで {@link SqlBuildException} になる
 * （印のオブジェクトそのものを値として送らない）。
 * </p>
 */
public final class TemplateParam {

	/* 名前に使える文字（SQL の字面に印として埋めるので、記号や空白を入れない） */
	private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");

	/* 名前 */
	private final String name;

	/* 値の並びか */
	private final boolean list;

	private TemplateParam (String name, boolean list) {

		if (name == null || !NAME.matcher(name).matches()) {
			throw new SqlBuildException(
				"値の印の名前に使えない文字があります（%s）。英字か _ で始め、英数字と _ だけで 64 文字までにしてください"
					.formatted(name));
		}

		this.name = name;
		this.list = list;

	}

	/**
	 * 1 つの値の印
	 *
	 * @param name	名前
	 * @return	印
	 * @throws SqlBuildException	名前に使えない文字があるとき
	 */
	public static TemplateParam single (String name) {

		return new TemplateParam(name, false);

	}

	/**
	 * 値の並びの印（{@code IN} / {@code NOT IN} に置く）
	 *
	 * @param name	名前
	 * @return	印
	 * @throws SqlBuildException	名前に使えない文字があるとき
	 */
	public static TemplateParam list (String name) {

		return new TemplateParam(name, true);

	}

	/**
	 * 名前
	 *
	 * @return	名前
	 */
	public String name () {

		return name;

	}

	/**
	 * 値の並びか
	 *
	 * @return	値の並びなら true
	 */
	public boolean isList () {

		return list;

	}

	@Override
	public String toString () {

		return list ? ":" + name + "[]" : ":" + name;

	}

}
