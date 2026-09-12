package io.jimble.docs;

import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 設定キーがドキュメントに載っているか（要件 D-159 / NF-C-03）
 *
 * <h2>なぜテストにするのか</h2>
 * <p>
 * <b>棚卸しの時点で、サイトに1行も出てこないキーが 30 本あった。</b>
 * {@code redis.settings.*} は 10 本まるごと、
 * {@code mq.poll_min} / {@code scheduler.tick_interval} /
 * {@code batch.alive} なども載っていなかった。
 * </p>
 *
 * <p>
 * <b>書いていないキーも公開 API である。</b>
 * ソースを読んだ誰かが見つけて使い、<b>1.0 で事故のように凍る</b>——
 * 「書いていないから変えてよい」とは言えない。
 * </p>
 *
 * <p>
 * <b>載せるか、消すか。</b>決めないまま増やせないようにするのがここの仕事で、
 * {@code ApiSurfaceTest} が公開パッケージに対してやっていることと同じ形である。
 * </p>
 */
class ConfigKeysTest {

	/** 一覧を載せるところ */
	private static final List<String> DOCS = List.of(
		"docs/site/ja/config.md"
		, "docs/site/en/config.md");

	/**
	 * 例の中の「名前を入れる場所」
	 *
	 * <p>
	 * <b>ここはキーではない。</b>{@code db.<名前>} や {@code mq.thread_count.<種別>} の
	 * ように、書く人が名前を決めるところである。例では見本の名前が入っているので、
	 * <b>その1段を飛ばして中を見る</b>。
	 * </p>
	 */
	private static final Set<String> PLACEHOLDERS = Set.of(
		"db", "auth.oidc", "mq.thread_count", "db.blog.subs");

	/** 見に行くモジュール */
	private static final List<String> MODULES = List.of(
		"jimble-core"
		, "jimble-util"
		, "jimble-db"
		, "jimble-web"
		, "jimble-batch"
		, "jimble-batch-manager"
		, "jimble-mq"
		, "jimble-mcp"
		, "jimble-otel"
		, "jimble-cli"
	);

	/**
	 * 一覧に出さないもの
	 *
	 * <p>
	 * <b>キーそのものではないもの</b>だけをここに入れる。
	 * 「説明を書くのが面倒だから」で足さないこと——
	 * <b>それをやると、この仕掛けは何も守らなくなる</b>。
	 * </p>
	 */
	private static final Set<String> NOT_A_KEY = Set.of(
		// 前置き。実際のキーは mq.thread_count.<種別>（一覧では別項で説明している）
		"mq.thread_count."
	);

	/** ドキュメントの中の ```conf のかたまり */
	private static final Pattern CONF_BLOCK = Pattern.compile(
		"```conf\\n(.*?)```", Pattern.DOTALL);

	/** {@code KEY_XXX = "a.b"} */
	private static final Pattern CONSTANT = Pattern.compile(
		"public static final String (KEY_[A-Z0-9_]+)\\s*\\n?\\s*=\\s*\"([a-z][a-z0-9_.]*)\"");

	/** {@code Conf.conf().getInt("a.b", …)} のように直に書いてあるもの */
	private static final Pattern INLINE = Pattern.compile(
		"\\.get(?:String|Int|Long|Boolean|Double|Duration|Bytes|Conf|StringListOptional)"
			+ "\\s*\\(\\s*\"([a-z][a-z0-9_.]*\\.[a-z0-9_.]+)\"");

	@Test
	@DisplayName("D-159 設定キーは全部ドキュメントに載っている")
	void everyKeyIsDocumented () throws IOException {

		Path root = projectRoot();

		Set<String> keys = keysInSource(root);

		assertTrue(keys.size() > 100, "キーが少なすぎる（探し方が壊れている）: " + keys.size());

		List<String> problems = new ArrayList<>();

		for (String doc : DOCS) {

			Path path = root.resolve(doc);

			assertTrue(Files.exists(path), doc + " がありません（.gitignore の /docs/* に飲まれていませんか）");

			Set<String> written = keysInExample(Files.readString(path, StandardCharsets.UTF_8));

			assertTrue(written.size() > 100, doc + " の例が読めていません: " + written.size());

			Set<String> missing = new TreeSet<>(keys);

			missing.removeAll(written);

			if (!missing.isEmpty()) {
				problems.add(doc + " の例に出ていないキー:\n  " + String.join("\n  ", missing));
			}

		}

		if (!problems.isEmpty()) {

			fail("""
				設定キーがドキュメントに載っていません。
				<b>載せるか、消すかを決めてください。</b>書いていないキーも公開 API です。

				%s""".formatted(String.join("\n\n", problems)));

		}

	}

	// region ここで固定していないこと

	/*
	 * - <b>例に書いてある値が既定値と合っているかは見ていない。</b>キーが出ていれば通る。
	 *   <b>既定値を変えて例を直し忘れると、ドキュメントが嘘をつく。</b>
	 *   突き合わせるには {@code jimble-docs} が全モジュールに依存する必要があり、
	 *   <b>ドキュメントを作るためだけの依存</b>を増やすことになるので、いまは入れていない
	 * - <b>ドキュメントにしか無いキー</b>（もう読まれていないのに載ったまま）も見ていない。
	 *   コード側の探し方が文字列の当てはめなので、<b>拾い漏れを「消し忘れ」と誤診する</b>ほうが害が大きい
	 * - <b>{@code db.<名前>} の中のキー</b>も見ていない。あちらは
	 *   {@code DBUtil} が知らないキーを起動時に落とすので、<b>コード側が表になっている</b>
	 */

	// endregion

	/**
	 * ドキュメントの例（```conf のかたまり）に書かれているキー
	 *
	 * <p>
	 * <b>文字列の当てはめではなく、HOCON として読む。</b>
	 * 例が壊れていたらそこで落ちるし、<b>入れ子の書き方が変わっても追随する</b>。
	 * </p>
	 *
	 * <p>
	 * {@code ${?ENV}} が入っているので<b>解決はできない</b>——
	 * {@code entrySet()} も {@code hasPath()} も解決を要求するので、
	 * <b>木を自分で降りる</b>。
	 * </p>
	 *
	 * @param markdown	ドキュメント
	 * @return	キー
	 */
	private static Set<String> keysInExample (String markdown) {

		Set<String> keys = new TreeSet<>();

		Matcher block = CONF_BLOCK.matcher(markdown);

		while (block.find()) {
			collect("", ConfigFactory.parseString(block.group(1)).root(), keys);
		}

		return keys;

	}

	/**
	 * HOCON の木を降りてキーを集める
	 *
	 * @param prefix	ここまでのパス
	 * @param object	いまの階層
	 * @param keys		集める先
	 */
	private static void collect (String prefix, ConfigObject object, Set<String> keys) {

		for (String name : object.keySet()) {

			String path = prefix.isEmpty() ? name : prefix + "." + name;

			ConfigValue value = object.get(name);

			keys.add(path);

			/*
			 * <b>{@code valueType()} は呼べない。</b>{@code ${?ENV}} は
			 * 解決していないと型を答えられずに落ちる（解決はできない——環境変数が要る）。
			 * <b>入れ子かどうかだけ見れば足りる。</b>
			 */
			if (!(value instanceof ConfigObject)) {
				continue;
			}

			/*
			 * <b>名前を入れる場所は1段飛ばす。</b>
			 * 例に書いてある名前（{@code db.blog}）はキーではないので、
			 * <b>その中身を1つ上のパスに載せ替える</b>。
			 */
			if (PLACEHOLDERS.contains(path)) {

				ConfigObject named = (ConfigObject) value;

				for (String child : named.keySet()) {

					ConfigValue inside = named.get(child);

					/*
					 * 名前のところに<b>値が直に書いてある</b>こともある
					 * （{@code db.remove_all_null_table_data} は名前ではなくキーである）。
					 */
					if (inside instanceof ConfigObject nested) {
						collect(path, nested, keys);
					} else {
						keys.add(path + "." + child);
					}

				}

				continue;

			}

			collect(path, (ConfigObject) value, keys);

		}

	}

	/**
	 * ソースに出てくる設定キー
	 *
	 * @param root	根
	 * @return	キー
	 * @throws IOException	読めなかった場合
	 */
	private static Set<String> keysInSource (Path root) throws IOException {

		Set<String> keys = new TreeSet<>();

		for (String module : MODULES) {

			Path main = root.resolve(module).resolve("src/main/java");

			if (!Files.exists(main)) {
				continue;
			}

			try (Stream<Path> files = Files.walk(main)) {

				for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {

					String source = Files.readString(file, StandardCharsets.UTF_8);

					Matcher constant = CONSTANT.matcher(source);

					while (constant.find()) {

						String key = constant.group(2);

						// db.<名前> の中のキーには「.」が無い（url / driver など）
						if (key.contains(".")) {
							keys.add(key);
						}

					}

					Matcher inline = INLINE.matcher(source);

					while (inline.find()) {
						keys.add(inline.group(1));
					}

				}

			}

		}

		keys.removeAll(NOT_A_KEY);

		return keys;

	}

	/**
	 * プロジェクトの根
	 *
	 * @return	根
	 */
	private static Path projectRoot () {

		Path at = Path.of("").toAbsolutePath();

		while (at != null) {

			if (Files.exists(at.resolve("settings.gradle.kts"))) {
				return at;
			}

			at = at.getParent();

		}

		throw new AssertionError("settings.gradle.kts が見つかりません");

	}

}
