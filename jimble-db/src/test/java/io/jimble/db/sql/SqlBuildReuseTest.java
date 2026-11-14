package io.jimble.db.sql;

import io.jimble.db.dialect.Dialect;
import io.jimble.db.dialect.Dialects;
import io.jimble.db.sql.SqlGoldenCases.GenProduct;
import io.jimble.db.sql.SqlGoldenCases.GenShop;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 組み立ての使い回し（2.7.19）が、同時に呼ばれても・長い文のあとでも、同じ字面と値を出すか
 *
 * <p>
 * 字面と値の並びが 2.7.4 と同じことは {@link SqlGoldenTest} が見る。ここでは、使い回すもの
 * （表クラスごとの全列の字面・最後に使った方言・主表ごとの長さの見込み）を
 * <b>仮想スレッドから同時に</b>・<b>方言を入れ替えながら</b>・<b>長さの違う文を混ぜて</b>使ったときに、
 * 1 本のスレッドで作ったものと 1 文字も違わないことを見る。
 * </p>
 */
class SqlBuildReuseTest {

	private static final Dialect MYSQL = Dialects.of("mysql");

	private static final Dialect POSTGRESQL = Dialects.of("postgresql");

	/** 組み立てる形（IN の値の数で文が変わるもの・長い文を含む） */
	private static List<Supplier<IBuilder>> shapes () {

		List<Supplier<IBuilder>> shapes = new ArrayList<>();

		shapes.add(() -> SQL.select().from(GenProduct.instance()).where(GenProduct.id.eq(1L)));
		shapes.add(() -> SQL.select().from(GenProduct.instance())
			.inner(GenShop.instance()).on(GenShop.id.eq(GenProduct.shop_id))
			.where(GenProduct.deleted_at.is_null())
			.limit(20).offset(40));
		shapes.add(() -> SQL.select(GenProduct.id, GenProduct.name, GenShop.odd)
			.from(GenProduct.instance())
			.left(GenShop.instance()).on(GenShop.id.eq(GenProduct.shop_id))
			.where(GenProduct.price.ge(BigDecimal.ONE)));
		for (int n = 1; n <= 4; n++) {
			List<Long> ids = LongStream.rangeClosed(1, n).boxed().toList();
			shapes.add(() -> SQL.select().from(GenProduct.instance()).where(GenProduct.id.in(ids)));
		}
		// 長さの見込みの上限（16 KB）を超える文。このあとの短い文の字面が変わらないこと
		List<Long> many = LongStream.rangeClosed(1, 6000).boxed().toList();
		shapes.add(() -> SQL.select().from(GenProduct.instance()).where(GenProduct.id.in(many)));
		shapes.add(() -> SQL.update(GenProduct.instance())
			.set(GenProduct.name, "n")
			.set(GenProduct.price, GenProduct.price.plus(1L))
			.where(GenProduct.id.in(List.of(1L, 2L)))
			.where(GenProduct.deleted_at.is_null()));
		shapes.add(() -> SQL.insert(GenProduct.instance())
			.value(GenProduct.id, 1L)
			.value(GenProduct.shop_id, 2L)
			.value(GenProduct.name, "名前")
			.value(GenProduct.deleted_at, null));
		shapes.add(() -> SQL.insert(GenShop.instance())
			.value(GenShop.id, GenProduct.id)
			.value(SQL.select(GenProduct.id).from(GenProduct.instance()).where(GenProduct.price.in(List.of(1, 2, 3)))));

		return shapes;

	}

	private static String render (IBuilder builder, Dialect dialect) {

		return builder.sql(dialect) + " " + builder.params();

	}

	@Test
	@DisplayName("仮想スレッドから同時に・方言を入れ替えながら組み立てても、1 本で作ったものと同じ字面と値になる")
	void concurrentSameAsSequential () throws Exception {

		List<Supplier<IBuilder>> shapes = shapes();
		Dialect[] dialects = { MYSQL, POSTGRESQL };

		List<String> expected = new ArrayList<>();
		for (Supplier<IBuilder> shape : shapes) {
			for (Dialect dialect : dialects) {
				expected.add(render(shape.get(), dialect));
			}
		}

		int tasks = 64;
		int rounds = 50;

		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

			List<Future<List<String>>> futures = new ArrayList<>();
			for (int t = 0; t < tasks; t++) {
				int offset = t;
				futures.add(executor.submit(() -> {
					List<String> mismatches = new ArrayList<>();
					for (int r = 0; r < rounds; r++) {
						// スレッドごとに形と方言の順をずらし、最後に使った方言と長さの見込みを奪い合わせる
						for (int i = 0; i < expected.size(); i++) {
							int k = (i + offset + r) % expected.size();
							String actual = render(shapes.get(k / dialects.length).get(), dialects[k % dialects.length]);
							if (!expected.get(k).equals(actual)) {
								mismatches.add(actual);
							}
						}
					}
					return mismatches;
				}));
			}

			for (Future<List<String>> future : futures) {
				assertEquals(List.of(), future.get());
			}

		}

	}

	@Test
	@DisplayName("長い文のあとの短い文も、同じ主表の文は同じ字面になる")
	void shortAfterLong () {

		String before = SQL.select().from(GenProduct.instance()).where(GenProduct.id.eq(1L)).sql(MYSQL);

		String longSql = SQL.select().from(GenProduct.instance())
			.where(GenProduct.id.in(LongStream.rangeClosed(1, 3000).boxed().toList())).sql(MYSQL);
		assertTrue(longSql.length() > 3000 * 3);

		String after = SQL.select().from(GenProduct.instance()).where(GenProduct.id.eq(1L)).sql(MYSQL);

		assertEquals(before, after);

	}

}
