package io.jimble.db.sqlcache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.InvalidClassException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.jimble.util.data.Data;

/**
 * 置き場から読み戻すときの守り
 */
class SqlCacheDeserializeTest {

	@Test
	@DisplayName("書いたものはそのまま読み戻せる（BLOB の列も）")
	void roundTrip () throws Exception {

		List<Data> rows = new ArrayList<>();
		rows.add(new Data().putData("id", 1).putData("blob", new byte[100_000]));

		List<Data> back = SqlCache.deserialize(SqlCache.serialize(rows));

		assertEquals(1, back.size());
		assertEquals(100_000, ((byte[]) back.get(0).get("blob")).length);

	}

	@Test
	@DisplayName("D-251 本文より長い配列は、確保する前に断る（数十バイトで大きな配列を作らせない）")
	void rejectsArrayLongerThanStream () throws Exception {

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
			out.writeObject(new byte[0]);
		}

		// 末尾の4バイトが配列の長さ（0）。900 万に書き換える
		byte[] stream = bytes.toByteArray();
		int length = 9_000_000;
		stream[stream.length - 4] = (byte) (length >>> 24);
		stream[stream.length - 3] = (byte) (length >>> 16);
		stream[stream.length - 2] = (byte) (length >>> 8);
		stream[stream.length - 1] = (byte) length;

		String value = Base64.getEncoder().encodeToString(stream);

		// フィルタで断られる（EOF まで読みにいかない）
		assertThrows(InvalidClassException.class, () -> SqlCache.deserialize(value));

	}

}
