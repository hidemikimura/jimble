package io.jimble.json.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 小数の速い道（Eisel–Lemire）が Double.parseDouble と1ビットも違わないこと（D-307）
 *
 * <p>速い道が決めきれずに NaN を返すのはかまわない（呼んだ側が遅い道で読む）。違う値を返すのはだめ。</p>
 */
class FastDoubleParserTest {

	/* 速い道で決めた数（少なすぎれば、速い道が働いていない） */
	private int decided;

	private void check (String text) {

		char[] chars = text.toCharArray();
		double fast = FastDoubleParser.parse(chars, 0, chars.length);

		if (Double.isNaN(fast)) {
			return;
		}

		decided++;
		assertEquals(Double.doubleToRawLongBits(Double.parseDouble(text)), Double.doubleToRawLongBits(fast), text);

	}

	@Test
	@DisplayName("Double.toString の形（最短の17桁まで）を乱数で20万個")
	void shortestRepresentations () {

		Random random = new Random(1);

		for (int i = 0; i < 200_000; i++) {
			double value = switch (i % 4) {
				case 0 -> random.nextDouble();
				case 1 -> random.nextDouble() * 1e6;
				case 2 -> Double.longBitsToDouble(random.nextLong() & 0x7FEFFFFFFFFFFFFFL);
				default -> random.nextGaussian() * Math.pow(10, random.nextInt(40) - 20);
			};
			check(Double.toString(value));
			check(String.valueOf((float) value));
		}

		assertTrue(decided > 300_000, "速い道で決めた数が少なすぎます: " + decided);

	}

	@Test
	@DisplayName("桁と指数を乱数で作った数を20万個（19桁まで・指数つき・先頭と末尾の0）")
	void randomDigits () {

		Random random = new Random(2);

		for (int i = 0; i < 200_000; i++) {
			StringBuilder text = new StringBuilder();
			if (random.nextBoolean()) {
				text.append('-');
			}
			int intDigits = random.nextInt(10);
			for (int d = 0; d < intDigits; d++) {
				text.append((char) ('0' + random.nextInt(10)));
			}
			text.append('.');
			int fracDigits = 1 + random.nextInt(19 - Math.min(intDigits, 18));
			for (int d = 0; d < fracDigits; d++) {
				text.append((char) ('0' + random.nextInt(10)));
			}
			if (random.nextBoolean()) {
				text.append(random.nextBoolean() ? 'e' : 'E').append(random.nextInt(640) - 330);
			}
			check(text.toString());
		}

	}

	@ParameterizedTest
	@ValueSource(strings = {
		"0.1", "0.2", "0.3", "1.7976931348623157e308", "2.2250738585072014e-308", "2.2250738585072011e-308",
		"4.9e-324", "1e-400", "1e400", "9007199254740993.0", "9007199254740992.5", "1.00000000000000011102230246251565404236316680908203125",
		"123456789012345678.9", "0.000000000000000000000000000001", "5e-1", "2.5e-1", "1.0e23", "8.988465674311580536566680e307",
		"0.5", "1.5", "2.5", "3.5", "4503599627370496.5", "4503599627370497.5", "-0.0", "0.0e10"
	})
	@DisplayName("丸めの境目・最大と最小の近く・とても小さい数とあふれる数")
	void edges (String text) {

		check(text);

	}

}
