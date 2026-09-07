package io.jimble.docs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 目次
 *
 * <p>
 * <b>並びはページ側の front matter が決める</b>（{@code section} と {@code order}）。
 * 生成側にページ名を並べた表を持つと、ページを足すたびに2か所を直すことになる。
 * </p>
 */
public final class SiteNav {

	/* まとまり → ページ */
	private final Map<String, List<Page>> sections = new LinkedHashMap<>();

	/**
	 * コンストラクタ
	 *
	 * @param pages	ページ
	 * @param order	まとまりの並び
	 */
	public SiteNav (List<Page> pages, List<String> order) {

		for (String section : order) {
			sections.put(section, new ArrayList<>());
		}

		for (Page page : pages) {
			sections.computeIfAbsent(page.section(), key -> new ArrayList<>()).add(page);
		}

		sections.values().forEach(list -> list.sort((a, b) -> {
			int compared = Integer.compare(a.order(), b.order());
			return compared != 0 ? compared : a.title().compareTo(b.title());
		}));

		sections.values().removeIf(List::isEmpty);

	}

	/**
	 * まとまり
	 *
	 * @return	まとまり
	 */
	public Map<String, List<Page>> sections () {

		return sections;

	}

}
