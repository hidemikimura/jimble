package io.jimble.db.sql;

import io.jimble.util.data.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * リクエストを渡してよい列（D-224）
 *
 * <p>
 * {@code apply(Data)} / {@code setRow(Data)} はどの列名でも受け付けるので、リクエストをそのまま渡すと
 * 画面に出していない列を当てられたり（{@code password_hash|starts_with}）、
 * 書き換えてはいけない列を書き換えられたりした。
 * </p>
 */
class AllowedColumnsTest {

	@Test
	@DisplayName("apply：許していない列で絞ったり並べたりできない")
	void applyRejectsOtherColumns () {

		Data probe = Data.fromJsonString("{\"where\": {\"site\": {\"password_hash|starts_with\": \"$2a$10$a\"}}}");

		SqlBuildException ex = assertThrows(SqlBuildException.class, () -> SQL.select(TestSchema.Site.id)
			.from(TestSchema.Site.instance()).apply(probe, TestSchema.Site.name));
		assertTrue(ex.getMessage().contains("site.password_hash"), ex.getMessage());

		Data order = Data.fromJsonString("{\"order\": {\"site\": {\"secret\": \"asc\"}}}");
		assertThrows(SqlBuildException.class, () -> SQL.select(TestSchema.Site.id)
			.from(TestSchema.Site.instance()).apply(order, TestSchema.Site.name));

	}

	@Test
	@DisplayName("apply：許した列なら今までどおり")
	void applyAllowsListedColumns () {

		Data request = Data.fromJsonString(
			"{\"where\": {\"site\": {\"name|contains\": \"jim\"}}, \"order\": {\"site\": {\"id\": \"desc\"}}}");

		String sql = assertDoesNotThrow(() -> SQL.select(TestSchema.Site.id)
			.from(TestSchema.Site.instance()).apply(request, TestSchema.Site.name, TestSchema.Site.id)).sql();

		assertTrue(sql.contains("LIKE") && sql.contains("ORDER BY"), sql);

	}

	@Test
	@DisplayName("setRow / valueRow：許していない列は入れさせない")
	void rowRejectsOtherColumns () {

		Data form = new Data();
		form.put("name", "x");
		form.put("feed_count", 9999);

		assertThrows(SqlBuildException.class
			, () -> SQL.update(TestSchema.Site.instance()).setRow(form, TestSchema.Site.name));
		assertThrows(SqlBuildException.class
			, () -> SQL.insert(TestSchema.Site.instance()).valueRow(form, TestSchema.Site.name));

		assertDoesNotThrow(() -> SQL.update(TestSchema.Site.instance())
			.setRow(form, TestSchema.Site.name, TestSchema.Site.feed_count).where(TestSchema.Site.id.eq(1)).params());

	}

}
