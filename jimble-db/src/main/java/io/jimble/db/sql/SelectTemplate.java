package io.jimble.db.sql;

import io.jimble.db.dialect.Dialect;
import io.jimble.db.dialect.DialectException;
import io.jimble.db.dialect.Dialects;
import io.jimble.db.internal.sql.query.parameter.Parameter;
import io.jimble.db.internal.sql.template.TemplateScope;
import io.jimble.util.internal.array.ArrayUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * 形を決めて、値だけを差し替える SELECT（2.8.0）
 *
 * <pre>
 * // 一度だけ作る（static final に置く）
 * static final SelectTemplate PRODUCT_BY_ID = SQL.template(SQL.select()
 *     .from(Product.instance())
 *     .where(Product.id.eq(Dsl.param("id")))
 *     .where(Product.deleted_at.is_null()));
 *
 * // 要求のたびに値を入れて投げる
 * Optional&lt;Data&gt; product = db.select(PRODUCT_BY_ID.bind("id", productId));
 * </pre>
 *
 * <p>
 * <b>SQL の字面は方言ごとに 1 回だけ作る。</b>要求のたびにビルダーを組み立てて SQL を書き出す手間
 * （{@code SQL.select}・{@code Column.eq}・{@code SelectBuilder.sql}・{@code params}）が無くなる。
 * 出る SQL とパラメータは、同じ形を今のビルダーに値を入れて組み立てたときと 1 文字も変わらない。
 * 結果も同じ形（テーブル名でネストした {@code Data}）で返り、{@code selectCached} も使える。
 * </p>
 *
 * <ul>
 *   <li>値の場所には {@link io.jimble.db.sql.query.dsl.Dsl#param(String)} を、
 *       {@code IN} / {@code NOT IN} の値の並びには {@link io.jimble.db.sql.query.dsl.Dsl#params(String)} を置く。
 *       値の印でない値（{@code status.eq(1)} の {@code 1} など）は、決まった値のまま毎回送る</li>
 *   <li>{@code IN} は値の数で文が変わる。値の数だけ {@code ?} を並べた字面にする。
 *       値の並びの印が 1 つなら、値の数 32 個までの字面を方言ごとに覚える（数が違えば別の字面なので、取り違えない）</li>
 *   <li>作ったあとは変わらないので、スレッドをまたいで同時に使ってよい。覚えるのは方言ごとの字面だけで、値はためない
 *       （量は template の数 × 方言の数 × 33 で止まる）</li>
 *   <li>条件によって WHERE を足したり足さなかったりする文には向かない（形が毎回変わる）。今のビルダーで書く</li>
 * </ul>
 *
 * <p>
 * <b>{@code SQL.template(...)} に渡したビルダーは、そのあと使わないこと。</b>
 * 句の並び（WHERE・ORDER BY など）は写して持つが、FROM や条件などの部品そのものは元のビルダーと分け合うので、
 * 元のビルダーに {@code inner(...)} を足したり部品を書き換えたりすると template も変わる。
 * {@code IN} などに置いたサブクエリのビルダーも写さずに分け合う（template が書き出すときにも使う）ので、
 * template に渡したサブクエリも、そのあと書き換えたり、ほかの文に使い回したりしないこと。
 * {@code SQL.template(SQL.select()...)} のように、その場で組み立てて渡すのがよい。
 * </p>
 */
public final class SelectTemplate {

	static {
		// SQL 結果キャッシュの依存を求めるところにだけ、形を渡す
		io.jimble.db.internal.sql.template.TemplateShapes.install(bound -> bound.template().builder);
	}

	/* 組み立てる形（写し。外には出さない） */
	private final SelectBuilder builder;

	/* パラメータの並び（決まった値か、値の印） */
	private final Object[] slots;

	/* 値の印の名前と、値の並びか（現れた順） */
	private final Map<String, Boolean> names;

	/* 字面に出る値の並びの印の名前（現れた順） */
	private final String[] listOrder;

	/* 最後に使った方言の字面（方言はふつう 1 つなので、たいていここで当たる） */
	private volatile Rendered last;

	/* 方言ごとの字面 */
	private final Map<Dialect, Rendered> byDialect = new ConcurrentHashMap<>();

	/*
	 * 値の並びの印が 1 つの template で、値の数ごとの字面を覚える上限。
	 * 買い物かごの中身のように、IN の値の数はたいてい小さい。これより多いときは毎回つなぐ
	 */
	private static final int SIZED_SQL_MAX = 32;

	/**
	 * 方言で書き出した字面
	 *
	 * @param dialect	方言
	 * @param fragments	値の並びの印で切った字面（印が n 個なら n + 1 個）
	 * @param length	切れ端の長さの合計
	 * @param sized		値の並びの印が 1 つのとき、値の数ごとの字面（添字が値の数。作ったら入れる。ほかは null）
	 */
	private record Rendered (Dialect dialect, String[] fragments, int length, AtomicReferenceArray<String> sized) {}

	SelectTemplate (SelectBuilder source) {

		if (source == null) {
			throw new SqlBuildException("SQL.template(...) に null が渡されました");
		}

		this.builder = source.copy();

		Prepared prepared = TemplateScope.building(() -> prepare(this.builder));

		this.slots = prepared.slots();
		this.names = prepared.names();
		this.listOrder = prepared.listOrder();

		// 既定の方言の字面は作って覚えておく（印の置き場所の誤りもここで分かる）
		Rendered rendered = prepared.rendered();
		this.byDialect.put(rendered.dialect(), rendered);
		this.last = rendered;

	}

	/**
	 * 作るときに求めたもの
	 */
	private record Prepared (Object[] slots, Map<String, Boolean> names, String[] listOrder, Rendered rendered) {}

	/**
	 * 字面とパラメータの並びを求め、印の置き方を確かめる
	 *
	 * @param builder	形
	 * @return	求めたもの
	 */
	private static Prepared prepare (SelectBuilder builder) {

		/*
		 * 先に書き出す（全列の SELECT は、書き出したあとの params() にその列の値が入る。DB に投げるときと同じ順にする）。
		 * 既定の方言で書けない形（主 DB が PostgreSQL で、MySQL のサブ DB にだけ投げるインデックスのヒントなど）もあるので、
		 * 書ける方言を順に試す。どれでも書けなければ、既定の方言の例外を投げる
		 */
		Dialect dialect = null;
		String sql = null;
		DialectException first = null;
		for (Dialect candidate : candidates()) {
			try {
				sql = builder.sql(candidate);
				dialect = candidate;
				break;
			} catch (DialectException ex) {
				if (first == null) {
					first = ex;
				}
			}
		}
		if (sql == null) {
			throw first;
		}

		List<Object> params = builder.params();
		Object[] slots = params.toArray();

		Map<String, Boolean> names = new LinkedHashMap<>();
		List<String> listParams = new ArrayList<>();

		for (Object slot : slots) {
			if (slot instanceof TemplateParam param) {
				Boolean known = names.putIfAbsent(param.name(), param.isList());
				if (known != null && known != param.isList()) {
					throw new SqlBuildException(
						"値の印の名前 %s を、1 つの値（Dsl.param）と値の並び（Dsl.params）の両方に使っています。名前を分けてください"
							.formatted(param.name()));
				}
				if (param.isList()) {
					listParams.add(param.name());
				}
			}
		}

		String[] listOrder = listParams.toArray(new String[0]);

		return new Prepared(slots, names, listOrder, split(dialect, sql, listOrder));

	}

	/**
	 * 作るときに書き出してみる方言（既定の方言から）
	 *
	 * @return	方言
	 */
	private static List<Dialect> candidates () {

		List<Dialect> dialects = new ArrayList<>();
		dialects.add(Dialects.defaultDialect());
		for (String product : new String[] { "mysql", "postgresql" }) {
			Dialect dialect = Dialects.of(product);
			if (!dialects.contains(dialect)) {
				dialects.add(dialect);
			}
		}
		return dialects;

	}

	/**
	 * 字面を値の並びの印で切る
	 *
	 * @param dialect	方言
	 * @param sql		字面
	 * @param listOrder	パラメータに現れた値の並びの印の名前（この順に字面にも出ていなければならない）
	 * @return	切ったもの
	 */
	private static Rendered split (Dialect dialect, String sql, String[] listOrder) {

		String[] parts = sql.split(String.valueOf(TemplateScope.TOKEN), -1);

		// 切れ端と印の名前が交互に並ぶ（a, 名前, b, 名前, c）
		int found = (parts.length - 1) / 2;
		boolean sameOrder = parts.length % 2 == 1 && found == listOrder.length;
		for (int i = 0; sameOrder && i < found; i++) {
			sameOrder = parts[i * 2 + 1].equals(listOrder[i]);
		}

		if (!sameOrder) {
			throw new SqlBuildException(
				"値の並びの印（Dsl.params）は、IN / NOT IN の値の場所にだけ置けます。1 つの値の場所には Dsl.param を置いてください");
		}

		String[] fragments = new String[found + 1];
		int length = 0;
		for (int i = 0; i <= found; i++) {
			fragments[i] = parts[i * 2];
			length += fragments[i].length();
		}

		AtomicReferenceArray<String> sized = found == 1 ? new AtomicReferenceArray<>(SIZED_SQL_MAX + 1) : null;

		return new Rendered(dialect, fragments, length, sized);

	}

	/**
	 * 方言の字面
	 *
	 * @param dialect	方言
	 * @return	字面
	 */
	Rendered rendered (Dialect dialect) {

		Rendered current = last;
		if (current != null && current.dialect() == dialect) {
			return current;
		}

		current = byDialect.get(dialect);
		if (current == null) {
			current = render(dialect);
		}

		last = current;

		return current;

	}

	/**
	 * 方言の字面を作る（方言ごとに 1 回。組み立ては写しの中身を書き換えるので、1 つずつ）
	 *
	 * @param dialect	方言
	 * @return	字面
	 */
	private synchronized Rendered render (Dialect dialect) {

		Rendered existing = byDialect.get(dialect);
		if (existing != null) {
			return existing;
		}

		Rendered created = TemplateScope.building(() -> split(dialect, builder.sql(dialect), listOrder));
		byDialect.put(dialect, created);

		return created;

	}

	/**
	 * 値を入れる
	 *
	 * <pre>
	 * db.selectList(PRODUCTS.bind("shop", shopId, "ids", List.of(1L, 2L, 3L)));
	 * </pre>
	 *
	 * @param namesAndValues	名前と値を交互に並べたもの
	 * @return	値を入れた SELECT
	 * @throws SqlBuildException	名前の過不足・名前と値の組になっていない・値の形が合わないとき
	 */
	public BoundSelect bind (Object...namesAndValues) {

		Object[] pairs = namesAndValues == null ? new Object[0] : namesAndValues;

		if (pairs.length % 2 != 0) {
			throw new SqlBuildException("bind(...) には名前と値を交互に並べてください（引数が %d 個です）".formatted(pairs.length));
		}

		Map<String, Object> values = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			if (!(pairs[i] instanceof String name)) {
				throw new SqlBuildException("bind(...) の %d 番目には値の印の名前（文字列）を置いてください: %s".formatted(i + 1, pairs[i]));
			}
			if (values.containsKey(name)) {
				throw new SqlBuildException("bind(...) に同じ名前 %s が 2 回あります".formatted(name));
			}
			values.put(name, pairs[i + 1]);
		}

		return bind(values);

	}

	/**
	 * 値を入れる
	 *
	 * @param values	名前と値
	 * @return	値を入れた SELECT
	 * @throws SqlBuildException	名前の過不足・値の形が合わないとき
	 */
	public BoundSelect bind (Map<String, ?> values) {

		Map<String, ?> given = values == null ? Map.of() : values;

		for (Object key : given.keySet()) {
			if (!(key instanceof String name) || !names.containsKey(name)) {
				throw new SqlBuildException("値の印に無い名前 %s が bind(...) に渡されました（印: %s）".formatted(key, names.keySet()));
			}
		}

		Map<String, List<?>> lists = new LinkedHashMap<>();
		for (Map.Entry<String, Boolean> entry : names.entrySet()) {
			String name = entry.getKey();
			if (!given.containsKey(name)) {
				throw new SqlBuildException("値の印 %s の値が bind(...) にありません".formatted(name));
			}
			Object value = given.get(name);
			if (entry.getValue()) {
				lists.put(name, listValue(name, value));
			} else {
				singleValue(name, value);
			}
		}

		List<Object> params = new ArrayList<>(slots.length + 8);
		for (Object slot : slots) {
			if (slot instanceof TemplateParam param) {
				if (param.isList()) {
					params.addAll(lists.get(param.name()));
				} else {
					params.add(given.get(param.name()));
				}
			} else {
				params.add(slot);
			}
		}

		int[] listSizes = new int[listOrder.length];
		for (int i = 0; i < listOrder.length; i++) {
			listSizes[i] = lists.get(listOrder[i]).size();
		}

		return new BoundSelect(this, listSizes, params);

	}

	/**
	 * 1 つの値を確かめる
	 */
	private static void singleValue (String name, Object value) {

		if (value == null) {
			// 今のビルダーの eq(null) と同じく断る（= NULL はどの行にも当たらない）
			throw new SqlBuildException(
				"値の印 %s に null が渡されました。NULL と比べるなら is_null() / is_not_null() を使う形を別に作ってください".formatted(name));
		}

		// リストや配列は断る（? が 1 つの場所に値が増えて、後ろとずれる）
		Parameter.single(value);

		rejectSqlPart(name, value);

	}

	/**
	 * SQL の部品（列・関数・条件・ビルダーなど）を値として受けない
	 *
	 * <p>
	 * ビルダーなら {@code id.eq(Product.shop_id)} は {@code = `product`.`shop_id`} と書かれるが、template の印の場所は
	 * もう {@code ?} に決まっているので、<b>部品のオブジェクトそのものがバインドされる</b>（DB は文字列として受け、黙って違う行を返す）。
	 * 部品を置くなら、印ではなく部品を置いた形の template を作る。
	 * </p>
	 */
	private static void rejectSqlPart (String name, Object value) {

		if (value instanceof io.jimble.util.data.definition.IColumn
			|| value instanceof io.jimble.db.sql.query.dsl.IDsl
			|| value instanceof io.jimble.db.sql.query.select.ISelect
			|| value instanceof io.jimble.db.sql.query.where.IWhere
			|| value instanceof io.jimble.db.internal.sql.query.order_by.IOrderBy
			|| value instanceof IBuilder
			|| value instanceof io.jimble.util.data.definition.ITable
			|| value instanceof TemplateParam) {
			throw new SqlBuildException(
				("値の印 %s に SQL の部品（%s）が渡されました。bind に渡せるのは値だけです。"
					+ "列や式と比べるなら、印ではなくその部品を置いた形の template を作ってください")
					.formatted(name, value.getClass().getName()));
		}

	}

	/**
	 * 値の並びを確かめて、リストにする
	 */
	private static List<?> listValue (String name, Object value) {

		List<?> list;
		if (value instanceof List<?> l) {
			list = l;
		} else if (value instanceof Collection<?> c) {
			list = new ArrayList<>(c);
		} else if (value != null && value.getClass().isArray() && !(value instanceof byte[])) {
			list = ArrayUtil.toList(value);
		} else if (value == null) {
			list = List.of();
		} else {
			throw new SqlBuildException(
				"値の並びの印 %s には、リストか配列を渡してください（%s が来ました）".formatted(name, value.getClass().getSimpleName()));
		}

		if (list.isEmpty()) {
			// 今のビルダーの in(空の一覧) と同じく断る
			throw new SqlBuildException(
				("値の並びの印 %s に空の一覧（または null）が渡されました。そのままでは壊れた SQL になります。"
					+ "「どれにも当たらない」なら呼び出し側で引かずに済ませ、「条件を付けない」なら別の形を使ってください").formatted(name));
		}

		// 入れ子は断る（平らにされて ? の数とずれる）
		Parameter.singles(list);

		for (Object element : list) {
			rejectSqlPart(name, element);
		}

		return list;

	}

	/**
	 * 値を入れた字面
	 *
	 * @param dialect		方言
	 * @param listSizes		値の並びの印ごとの値の数
	 * @return	字面
	 */
	String sql (Dialect dialect, int[] listSizes) {

		Rendered rendered = rendered(dialect);
		String[] fragments = rendered.fragments();

		if (fragments.length == 1) {
			return fragments[0];
		}

		// 値の並びの印が 1 つなら、値の数ごとの字面を覚えて使う（値の数が違えば別の字面なので、取り違えない）
		AtomicReferenceArray<String> sized = rendered.sized();
		if (sized != null && listSizes[0] <= SIZED_SQL_MAX) {
			String cached = sized.get(listSizes[0]);
			if (cached == null) {
				cached = join(rendered, listSizes);
				sized.set(listSizes[0], cached);
			}
			return cached;
		}

		return join(rendered, listSizes);

	}

	/**
	 * 切れ端のあいだに、値の数だけ ? を並べる
	 *
	 * @param rendered		字面
	 * @param listSizes		値の並びの印ごとの値の数
	 * @return	字面
	 */
	private static String join (Rendered rendered, int[] listSizes) {

		String[] fragments = rendered.fragments();

		int questions = 0;
		for (int size : listSizes) {
			questions += size * 3;
		}

		StringBuilder sb = new StringBuilder(rendered.length() + questions);
		sb.append(fragments[0]);
		for (int i = 0; i < listSizes.length; i++) {
			for (int k = 0; k < listSizes[i]; k++) {
				if (k > 0) {
					sb.append(", ");
				}
				sb.append('?');
			}
			sb.append(fragments[i + 1]);
		}

		return sb.toString();

	}

	/**
	 * 値の印の名前（現れた順）
	 *
	 * @return	名前
	 */
	public List<String> names () {

		return List.copyOf(names.keySet());

	}

}
