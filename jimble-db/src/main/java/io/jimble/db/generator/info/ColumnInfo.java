package io.jimble.db.generator.info;

import java.util.regex.Pattern;

public class ColumnInfo {

	public String name;

	private String nameUpperCamel;

	public String nameUpperCamel () {

		if (nameUpperCamel != null) {
			return nameUpperCamel;
		}

		String[] names = name.replaceAll(Pattern.quote("-"), "_").split(Pattern.quote("_"));
		StringBuilder sb = new StringBuilder();
		for (String n : names) {
			sb.append(n.substring(0, 1).toUpperCase());
			if (n.length() > 1) {
				sb.append(n.substring(1));
			}
		}

		nameUpperCamel = sb.toString();
		return nameUpperCamel;

	}

	private String nameLowerCamel;

	public String nameLowerCamel () {

		if (nameLowerCamel != null) {
			return nameLowerCamel;
		}

		String[] names = name.replaceAll(Pattern.quote("-"), "_").split(Pattern.quote("_"));
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < names.length; i++) {
			String n = names[i];
			if (i == 0) {
				sb.append(n.substring(0, 1).toLowerCase());
			} else {
				sb.append(n.substring(0, 1).toUpperCase());
			}
			if (n.length() > 1) {
				sb.append(n.substring(1));
			}
		}

		nameLowerCamel = sb.toString();
		return nameLowerCamel;

	}

	public String type;

	public String extra;

	public Class<?> typeClass;

	public String typeClassNameUpperCamel () {

		String name = typeClass.getTypeName();
		{
			int index = name.lastIndexOf('.');
			if (index > -1) {
				name = name.substring(index + 1);
			}
		}

		return name.substring(0, 1).toUpperCase() + name.substring(1);

	}

	public boolean nullable;

	public Object defaultValue;

	public String defaultValueString;

	public boolean primaryKey;

	public String comment;

}
