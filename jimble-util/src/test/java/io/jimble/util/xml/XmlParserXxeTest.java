package io.jimble.util.xml;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * XML の解析で外部実体を読まない（D-206）
 *
 * <p>
 * JDK の既定のままの解析器を使っていたので、
 * {@code <!DOCTYPE r [<!ENTITY x SYSTEM "file:///etc/passwd">]><r>&x;</r>} で<b>サーバーのファイルが読めた</b>。
 * </p>
 */
class XmlParserXxeTest {

	@Test
	@DisplayName("外部実体でローカルのファイルを読めない（DOCTYPE を含む XML は解析しない）")
	void noExternalEntity (@TempDir Path dir) throws Exception {

		Path secret = dir.resolve("secret.txt");
		Files.writeString(secret, "TOP-SECRET");

		String xml = "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY x SYSTEM \"" + secret.toUri() + "\">]><r>&x;</r>";

		XmlData data = XmlParser.parse(xml);

		assertTrue(data == null || !String.valueOf(data.getTextContent()).contains("TOP-SECRET")
			, "外部実体でファイルを読んでいます");

	}

	@Test
	@DisplayName("外部の DTD も読まない（パラメータ実体）")
	void noExternalDtd (@TempDir Path dir) throws Exception {

		Path dtd = dir.resolve("evil.dtd");
		Files.writeString(dtd, "<!ENTITY y \"FROM-DTD\">");

		String xml = "<?xml version=\"1.0\"?><!DOCTYPE r SYSTEM \"" + dtd.toUri() + "\"><r>&y;</r>";

		XmlData data = XmlParser.parse(xml);

		assertTrue(data == null || !String.valueOf(data.getTextContent()).contains("FROM-DTD"));

	}

	@Test
	@DisplayName("DOCTYPE の無い、ふつうの XML は今までどおり読める")
	void ordinaryXml () {

		XmlData data = XmlParser.parse("<?xml version=\"1.0\"?><r a=\"1\"><c>犬 &amp; 猫</c></r>");

		assertNotNull(data);
		assertEquals("r", data.getTagName());

	}

}
