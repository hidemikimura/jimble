package io.jimble.conventions;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * 成果物ごとの maven-metadata.xml（版の一覧）を作る
 *
 * <p>
 * Maven Central は自分で作ってくれたが、repo.jimble.io は<b>ただのファイル置き場</b>なので、こちらで作る。
 * 決まった版を指す依存（{@code io.jimble:jimble-web:2.7.11}）には要らないが、
 * {@code latest.release} や {@code 2.7.+}、IDE の版の補完はこれを読む。
 * </p>
 *
 * <p>
 * <b>置き場にあるものを読んで、版を1つ足して書き戻す。</b>
 * 手元の build/central にあるものは使わない（掃除すれば消え、別の手元から出せば食い違う）。
 * </p>
 */
final class MavenMetadata {

	/* groupId / artifactId / 版に使ってよい文字（XML に書くので、それ以外は止める） */
	private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]+");

	private MavenMetadata () {
	}

	/**
	 * 版を1つ足した maven-metadata.xml を作る
	 *
	 * @param existing		置き場にあるもの（無ければ null）
	 * @param groupId		groupId
	 * @param artifactId	artifactId
	 * @param version		足す版
	 * @param lastUpdated	更新時刻（{@code yyyyMMddHHmmss}。UTC）
	 * @return maven-metadata.xml の中身
	 */
	static String merge (String existing, String groupId, String artifactId, String version, String lastUpdated) {

		for (String value : List.of(groupId, artifactId, version, lastUpdated)) {
			if (!SAFE.matcher(value).matches()) {
				throw new IllegalArgumentException("maven-metadata.xml に書けない値です: " + value);
			}
		}

		TreeSet<String> versions = new TreeSet<>(MavenMetadata::compareVersions);

		if (existing != null) {

			Document document = parse(existing);

			String existingGroup = text(document, "groupId");
			String existingArtifact = text(document, "artifactId");

			if (!groupId.equals(existingGroup) || !artifactId.equals(existingArtifact)) {
				throw new IllegalStateException("置き場の maven-metadata.xml が別の成果物のものです: "
					+ existingGroup + ":" + existingArtifact + "（足そうとしたのは " + groupId + ":" + artifactId + "）");
			}

			versions.addAll(versions(document));

		}

		versions.add(version);

		String newest = versions.last();

		// release はプレリリース（-rc1 など）を除いた最大。無ければ書かない
		String release = versions.descendingSet().stream().filter(v -> !isPreRelease(v)).findFirst().orElse(null);

		StringBuilder xml = new StringBuilder();
		xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		xml.append("<metadata>\n");
		xml.append("  <groupId>").append(groupId).append("</groupId>\n");
		xml.append("  <artifactId>").append(artifactId).append("</artifactId>\n");
		xml.append("  <versioning>\n");
		xml.append("    <latest>").append(newest).append("</latest>\n");
		if (release != null) {
			xml.append("    <release>").append(release).append("</release>\n");
		}
		xml.append("    <versions>\n");
		for (String v : versions) {
			xml.append("      <version>").append(v).append("</version>\n");
		}
		xml.append("    </versions>\n");
		xml.append("    <lastUpdated>").append(lastUpdated).append("</lastUpdated>\n");
		xml.append("  </versioning>\n");
		xml.append("</metadata>\n");

		return xml.toString();

	}

	/**
	 * 載っている版（古い順）
	 *
	 * @param xml	maven-metadata.xml の中身
	 * @return 版
	 */
	static List<String> versions (String xml) {

		TreeSet<String> sorted = new TreeSet<>(MavenMetadata::compareVersions);
		sorted.addAll(versions(parse(xml)));

		return List.copyOf(sorted);

	}

	/**
	 * 版を比べる
	 *
	 * <p>
	 * {@code -} より前を {@code .} で区切り、数の部分は数として比べる（2.7.10 は 2.7.9 より大きい）。
	 * 前が同じなら、<b>{@code -} のあるもの（プレリリース）は無いものより小さい</b>（2.7.12-rc1 は 2.7.12 より小さい）。
	 * </p>
	 *
	 * @param a	版
	 * @param b	版
	 * @return a が小さければ負、同じなら 0、大きければ正
	 */
	static int compareVersions (String a, String b) {

		int base = compareParts(baseOf(a).split("\\."), baseOf(b).split("\\."));

		if (base != 0) {
			return base;
		}

		if (isPreRelease(a) != isPreRelease(b)) {
			return isPreRelease(a) ? -1 : 1;
		}

		return isPreRelease(a) ? compareParts(qualifierOf(a).split("[.-]"), qualifierOf(b).split("[.-]")) : 0;

	}

	/**
	 * プレリリースか（{@code -} がある）
	 *
	 * @param version	版
	 * @return プレリリースの場合 = true
	 */
	static boolean isPreRelease (String version) {

		return version.indexOf('-') >= 0;

	}

	private static String baseOf (String version) {

		return isPreRelease(version) ? version.substring(0, version.indexOf('-')) : version;

	}

	private static String qualifierOf (String version) {

		return version.substring(version.indexOf('-') + 1);

	}

	private static int compareParts (String[] left, String[] right) {

		for (int i = 0; i < Math.min(left.length, right.length); i++) {

			int compared = isNumber(left[i]) && isNumber(right[i])
				? Long.compare(Long.parseLong(left[i]), Long.parseLong(right[i]))
				: left[i].compareTo(right[i]);

			if (compared != 0) {
				return compared;
			}

		}

		return Integer.compare(left.length, right.length);

	}

	private static boolean isNumber (String part) {

		return !part.isEmpty() && part.length() <= 18 && part.chars().allMatch(Character::isDigit);

	}

	private static List<String> versions (Document document) {

		NodeList nodes = document.getElementsByTagName("version");
		List<String> versions = new ArrayList<>();

		for (int i = 0; i < nodes.getLength(); i++) {
			versions.add(nodes.item(i).getTextContent().trim());
		}

		return versions;

	}

	private static String text (Document document, String tag) {

		NodeList nodes = document.getDocumentElement().getElementsByTagName(tag);

		return nodes.getLength() == 0 ? null : ((Element) nodes.item(0)).getTextContent().trim();

	}

	/**
	 * 読む（DOCTYPE があれば止める。外の実体を読みに行かせない）
	 */
	private static Document parse (String xml) {

		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setExpandEntityReferences(false);
			return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
		} catch (ParserConfigurationException | SAXException | IOException ex) {
			throw new IllegalStateException("maven-metadata.xml を読めません: " + ex.getMessage(), ex);
		}

	}

}
