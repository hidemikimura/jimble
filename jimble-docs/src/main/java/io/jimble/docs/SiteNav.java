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
 *
 * <p>
 * 前後のページ（{@link #before(Page)} / {@link #after(Page)}）も同じ並びから出す。
 * <b>目次の順に読めば1周する</b>ようにするためで、別に順序を持たない。
 * </p>
 */
public final class SiteNav {

	/* まとまり → ページ */
	private final Map<String, List<Page>> sections = new LinkedHashMap<>();

	/* 目次の順に並べたもの（前後のページを出すため） */
	private final List<Page> flat = new ArrayList<>();

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

		sections.values().forEach(flat::addAll);

	}

	/**
	 * 目次の順に並べたページ
	 *
	 * @return	ページ（まとまりをまたいで、目次に出る順）
	 */
	public List<Page> flat () {

		return List.copyOf(flat);

	}

	/**
	 * 1つ前のページ
	 *
	 * @param page	いまのページ
	 * @return	前のページ（無ければ null）
	 */
	public Page before (Page page) {

		int index = indexOf(page);

		return index > 0 ? flat.get(index - 1) : null;

	}

	/**
	 * 1つ後のページ
	 *
	 * @param page	いまのページ
	 * @return	次のページ（無ければ null）
	 */
	public Page after (Page page) {

		int index = indexOf(page);

		return (index >= 0 && index < flat.size() - 1) ? flat.get(index + 1) : null;

	}

	/**
	 * 何番目か
	 *
	 * @param page	ページ
	 * @return	位置（無ければ -1）
	 */
	private int indexOf (Page page) {

		for (int index = 0; index < flat.size(); index++) {

			if (flat.get(index).slug().equals(page.slug())) {
				return index;
			}

		}

		return -1;

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
