package io.jimble.db.internal.generator.info;

import java.util.ArrayList;
import java.util.List;

public class IndexInfo {

	public String name;

	public boolean isPrimary;

	public boolean isUnique;

	public List<String> columnNameList = new ArrayList<>();

	/**
	 * 条件（部分インデックスの {@code WHERE}）。無ければ null
	 *
	 * <p>
	 * <b>落とすと、生成される DDL が「全行にかかるインデックス」になる。</b>
	 * PostgreSQL でだけ入る（要件 F-D-30）。
	 * </p>
	 */
	public String predicate;

}
