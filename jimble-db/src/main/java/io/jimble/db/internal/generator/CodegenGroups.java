package io.jimble.db.internal.generator;

import io.jimble.db.DBSource;
import io.jimble.db.DBUtil;
import io.jimble.db.internal.generator.info.ColumnInfo;
import io.jimble.db.internal.generator.info.TableInfo;
import io.jimble.util.log.Log;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * 生成コードの組（{@code db.<名前>.codegen_name}・{@code codegen}）
 *
 * <p>
 * 同じ表の作りの DB を何本もデータソースにしても、生成コードは<b>1 組だけ</b>持つ。
 * テーブルクラスは SQL に表の名前しか入れないので、どのデータソースの DB に渡しても、その DB で動く。
 * </p>
 */
final class CodegenGroups {

	private CodegenGroups () {}

	/** codegen_name に書ける形（フォルダとパッケージが同じ綴りになるよう、小文字で始まる小文字・数字・_） */
	private static final Pattern CODEGEN_NAME = Pattern.compile("[a-z][a-z0-9_]*");

	/**
	 * パッケージの末尾とクラス名の元
	 *
	 * <p>
	 * {@code codegen_name} を書いていればそれ、無ければデータソース名（これまでどおり）。
	 * データソース名に大文字があると、<b>フォルダは小文字、パッケージは元の綴り</b>になる（これまでの作り。
	 * 変えると生成物の置き場所が変わるので変えない）。そのときは警告して、{@code codegen_name} で揃えられることを言う。
	 * </p>
	 *
	 * @param dbSource	データソース
	 * @return	名前
	 * @throws GeneratorException	codegen_name の形が正しくない場合
	 */
	static String codegenName (DBSource dbSource) {

		String own = dbSource.conf().codegenName();

		if (own == null || own.isEmpty()) {
			if (!dbSource.name().equals(dbSource.name().toLowerCase())) {
				Log.warn(("codegen: データソース名 %s に大文字があるので、フォルダ（%s）とパッケージ（%s）の綴りが揃いません。"
					+ "db.%s.codegen_name に小文字の名前を書くと揃います").formatted(
						dbSource.name(), dbSource.name().toLowerCase(), dbSource.name(), dbSource.name()));
			}
			return dbSource.name();
		}

		if (!CODEGEN_NAME.matcher(own).matches()) {
			throw new GeneratorException(
				"db.%s.codegen_name は小文字で始まる小文字・数字・_ で書いてください（パッケージ名とフォルダ名になります）: %s"
					.formatted(dbSource.name(), own));
		}

		return own;

	}

	/**
	 * codegen_name ごとにまとめる（codegen = false は外す）
	 *
	 * @param sources	データソース
	 * @return	codegen_name → データソース（名前の順）。名前の順
	 */
	static SortedMap<String, List<DBSource>> of (List<DBSource> sources) {

		SortedMap<String, List<DBSource>> groups = new TreeMap<>();

		sources.stream()
			.filter(source -> source.conf().codegen())
			.sorted(Comparator.comparing(DBSource::name))
			.forEach(source -> groups.computeIfAbsent(codegenName(source), key -> new ArrayList<>()).add(source));

		return groups;

	}

	/**
	 * 代表（主があれば主、無ければ名前の順で最初）
	 *
	 * @param sources	同じ codegen_name のデータソース（名前の順）
	 * @return	代表
	 */
	static DBSource representative (List<DBSource> sources) {

		DBSource main = DBUtil.getMainDataSource();

		return sources.stream().filter(source -> source == main).findFirst().orElse(sources.getFirst());

	}

	/**
	 * 表の作りが代表と違えば警告する
	 *
	 * <p>
	 * 生成は代表の DB から読むので、ほかの DB の表が違っても生成物には出ない。
	 * <b>違う作りの DB に同じ生成コードを使うと、実行してから SQL のエラーになる</b>ので、ここで言う。
	 * 見るのは表の名前と、列の名前・型。止めはしない（移行の途中で一時的にずれることがある）。
	 * </p>
	 *
	 * @param representative	代表
	 * @param sources			同じ codegen_name のデータソース
	 */
	static void warnIfDifferent (DBSource representative, List<DBSource> sources) {

		Map<String, String> expected = shape(Generator.getTableInfoList(representative));

		for (DBSource source : sources) {

			if (source == representative) {
				continue;
			}

			Map<String, String> actual = shape(Generator.getTableInfoList(source));

			Set<String> tables = new TreeSet<>(expected.keySet());
			tables.addAll(actual.keySet());

			for (String table : tables) {
				String want = expected.get(table);
				String got = actual.get(table);
				if (want == null) {
					Log.warn("codegen: %s に、代表の %s に無い表 %s があります".formatted(source.name(), representative.name(), table));
				} else if (got == null) {
					Log.warn("codegen: %s に、代表の %s にある表 %s がありません".formatted(source.name(), representative.name(), table));
				} else if (!want.equals(got)) {
					Log.warn("codegen: %s の表 %s の列が、代表の %s と違います（%s / 代表 %s）"
						.formatted(source.name(), table, representative.name(), got, want));
				}
			}

		}

	}

	/**
	 * 表の作り（表の名前 → 列の名前と型）
	 *
	 * @param tables	表
	 * @return	表の名前 → 「列 型, 列 型, ...」
	 */
	private static Map<String, String> shape (List<TableInfo> tables) {

		Map<String, String> shape = new TreeMap<>();

		for (TableInfo table : tables) {
			StringBuilder columns = new StringBuilder();
			for (ColumnInfo column : table.columnList) {
				if (!columns.isEmpty()) {
					columns.append(", ");
				}
				columns.append(column.name).append(' ').append(column.type);
			}
			shape.put(table.name, columns.toString());
		}

		return shape;

	}

}
