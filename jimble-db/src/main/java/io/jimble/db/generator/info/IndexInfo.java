package io.jimble.db.generator.info;

import java.util.ArrayList;
import java.util.List;

public class IndexInfo {

	public String name;

	public boolean isPrimary;

	public boolean isUnique;

	public List<String> columnNameList = new ArrayList<>();

}
