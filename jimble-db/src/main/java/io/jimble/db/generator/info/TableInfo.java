package io.jimble.db.generator.info;

import java.util.ArrayList;
import java.util.List;

public class TableInfo {

	public String name;

	public String className;

	public String comment;

	public String getCommentNoVersion () {

		if (comment == null || comment.isEmpty()) {
			return name;
		}

		int index = comment.indexOf(":");
		if (index < 0) {
			if (comment.equals(name)) {
				return "";
			}
			return comment;
		}

		String _comment = comment.substring(0, index);
		if (_comment.equals(name)) {
			return "";
		}

		return _comment;

	}

	public long version = 1;

	public List<ColumnInfo> columnList = new ArrayList<>();

	public List<IndexInfo> indexList = new ArrayList<>();

}
