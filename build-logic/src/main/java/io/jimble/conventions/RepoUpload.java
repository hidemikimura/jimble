package io.jimble.conventions;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * build/central にある版を、repo.jimble.io（Cloudflare R2）へ上げる
 *
 * <p>
 * Maven Central には月ごとの上限（ファイル数・大きさ・リリース数）があり、
 * jimble の1版（16 成果物・約 400 ファイル・約 60MB）でほぼ届くので、2.7.11 から自前の置き場へ移した。
 * </p>
 *
 * <p>
 * <b>一度上げた版は上書きしない。</b>すべての成果物の maven-metadata.xml に載っていれば止める（Central と同じ約束）。
 * 途中で落ちたときは、流し直せば続きから上げる。上げる順は jar → pom / module → maven-metadata.xml。手順の全体は docs/publishing-repo.md にある。
 * </p>
 */
public abstract class RepoUpload extends DefaultTask {

	/** 公開の URL */
	public static final String PUBLIC_URL = "https://repo.jimble.io";

	/** バケットの既定（JIMBLE_R2_BUCKET で変えられる） */
	static final String DEFAULT_BUCKET = "jimble-repo";

	/* maven-metadata.xml と一緒に置くチェックサム（拡張子 → アルゴリズム） */
	private static final Map<String, String> CHECKSUMS = Map.of(
		"md5", "MD5", "sha1", "SHA-1", "sha256", "SHA-256", "sha512", "SHA-512");

	/* lastUpdated の形 */
	private static final DateTimeFormatter LAST_UPDATED = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	/* 何ファイルごとに進み具合を出すか */
	private static final int PROGRESS_EVERY = 50;

	/**
	 * 上げる版
	 *
	 * @return 版
	 */
	@Input
	public abstract Property<String> getVersion ();

	/**
	 * Maven のレイアウトで出した置き場（build/central）
	 *
	 * <p>前の版のファイルも残っているので、入力として追わない（版のディレクトリだけを選んで上げる）。</p>
	 *
	 * @return 置き場
	 */
	@Internal
	public abstract DirectoryProperty getRepoDir ();

	/**
	 * 上げずに、上げるものを並べるだけにするか
	 *
	 * @return 並べるだけの場合 = true
	 */
	@Input
	public abstract Property<Boolean> getDryRun ();

	/**
	 * 環境変数を読む
	 *
	 * @return ProviderFactory
	 */
	@Inject
	protected abstract ProviderFactory getProviders ();

	/**
	 * 上げる
	 */
	@TaskAction
	public void upload () {

		String version = getVersion().get();

		if (version.endsWith("-SNAPSHOT")) {
			throw new GradleException("-SNAPSHOT は上げません。-Pjimble.version=2.7.11 のように渡してください");
		}

		Path root = getRepoDir().get().getAsFile().toPath();
		RepoLayout.Plan plan = RepoLayout.plan(root, version);

		if (plan.files().isEmpty()) {
			throw new GradleException(root + " に " + version + " がありません");
		}

		if (!plan.stray().isEmpty()) {
			throw new GradleException("jar / pom / module とその署名・チェックサムのほかのファイルがあります。消してから流してください:\n  "
				+ String.join("\n  ", plan.stray()));
		}

		if (!plan.unsigned().isEmpty()) {
			throw new GradleException("署名（.asc）の無いファイルがあります。JIMBLE_SIGNING_KEY / JIMBLE_SIGNING_PASSWORD を渡してください:\n  "
				+ String.join("\n  ", plan.unsigned()));
		}

		getLogger().lifecycle("{}: {} ファイル / {} 成果物", version, plan.files().size(), plan.coordinates().size());

		if (getDryRun().get()) {
			plan.coordinates().forEach(c -> getLogger().lifecycle("  {}:{}", c.groupId(), c.artifactId()));
			getLogger().lifecycle("（repo.dryRun なので上げていません）");
			return;
		}

		R2Client client = new R2Client(env("JIMBLE_R2_ACCOUNT_ID"), bucket(), env("JIMBLE_R2_ACCESS_KEY_ID"), env("JIMBLE_R2_SECRET_ACCESS_KEY"));

		Map<RepoLayout.Coordinate, Optional<String>> metadata = new LinkedHashMap<>();
		plan.coordinates().forEach(c -> metadata.put(c, client.getText(c.dir() + "/maven-metadata.xml")));

		List<RepoLayout.Coordinate> missing = plan.coordinates().stream()
			.filter(c -> metadata.get(c).map(xml -> !MavenMetadata.versions(xml).contains(version)).orElse(true))
			.toList();

		if (missing.isEmpty()) {
			throw new GradleException(version + " はもう上がっています（どの成果物の maven-metadata.xml にも載っています）。"
				+ "上げた版は変えません。版を上げてください");
		}

		if (missing.size() < plan.coordinates().size()) {
			getLogger().lifecycle("{} は途中まで上がっています（maven-metadata.xml に載っていない成果物が {} / {}）。続きから上げます",
				version, missing.size(), plan.coordinates().size());
		}

		uploadFiles(client, root, plan.files());
		writeMetadata(client, missing, metadata, version);
		verifyPublic(plan.coordinates().get(0), version);

	}

	/*
	 * 置き場に同じ中身（ETag = MD5）があれば飛ばし、無い・違うなら置く。
	 *
	 * <b>「上がっている」は maven-metadata.xml に載っているかで決める</b>（pom の有無ではない）。
	 * pom の有無で止めると、途中で落ちたときに流し直せず、手で消すしかなくなる。
	 * 載せる前の版は、まだ誰にも知らせていないので、手元の中身で置き直してよい。
	 */
	private void uploadFiles (R2Client client, Path root, List<String> files) {

		int done = 0;
		int skipped = 0;
		int replaced = 0;

		for (String key : files) {

			byte[] body = read(root.resolve(key));
			Optional<String> etag = client.etag(key);

			if (etag.isPresent() && etag.get().equals(S3Signer.digestHex("MD5", body))) {
				skipped++;
			} else {
				replaced += etag.isPresent() ? 1 : 0;
				client.put(key, body, RepoLayout.contentType(key), RepoLayout.cacheControl(key));
			}

			done++;

			if (done % PROGRESS_EVERY == 0 || done == files.size()) {
				getLogger().lifecycle("  {} / {}", done, files.size());
			}

		}

		if (skipped > 0 || replaced > 0) {
			getLogger().lifecycle("同じ中身で置いてあったので飛ばした: {} / 違う中身だったので置き直した: {}", skipped, replaced);
		}

	}

	/* 載っていない成果物の maven-metadata.xml に版を足して書き戻す（チェックサムを先に、本体を最後に） */
	private void writeMetadata (R2Client client, List<RepoLayout.Coordinate> coordinates,
			Map<RepoLayout.Coordinate, Optional<String>> existing, String version) {

		String now = ZonedDateTime.now(ZoneOffset.UTC).format(LAST_UPDATED);

		for (RepoLayout.Coordinate c : coordinates) {

			String key = c.dir() + "/maven-metadata.xml";
			String merged = MavenMetadata.merge(existing.get(c).orElse(null), c.groupId(), c.artifactId(), version, now);
			byte[] body = merged.getBytes(StandardCharsets.UTF_8);

			for (Map.Entry<String, String> checksum : CHECKSUMS.entrySet()) {
				client.put(key + "." + checksum.getKey(), S3Signer.digestHex(checksum.getValue(), body).getBytes(StandardCharsets.UTF_8),
					"text/plain", RepoLayout.CACHE_METADATA);
			}

			client.put(key, body, RepoLayout.contentType(key), RepoLayout.CACHE_METADATA);

		}

		getLogger().lifecycle("maven-metadata.xml を {} 個書き直しました（並べて流さないこと。読んで書き戻すので、片方の版が消える）", coordinates.size());

	}

	/* 公開の URL から取れるかを見る（取れなくても、上げたものは取り消さない） */
	private void verifyPublic (RepoLayout.Coordinate c, String version) {

		String url = PUBLIC_URL + "/" + c.dir() + "/" + version + "/" + c.artifactId() + "-" + version + ".pom";

		try {

			HttpResponse<Void> response = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create(url)).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
				HttpResponse.BodyHandlers.discarding());

			if (response.statusCode() == 200) {
				getLogger().lifecycle("上げました: {}", url);
			} else {
				getLogger().warn("上げましたが、公開の URL が {} を返しました: {}（R2 のバケットに repo.jimble.io をつないであるか確かめてください）",
					response.statusCode(), url);
			}

		} catch (IOException ex) {
			getLogger().warn("上げましたが、公開の URL を確かめられませんでした: {}（{}）", url, ex.getMessage());
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}

	}

	/* 前後の空白と改行は落とす（コピーの取りこぼしで署名が合わなくなる。値はログに出さない） */
	private String env (String name) {

		String value = getProviders().environmentVariable(name).getOrNull();

		if (value == null || value.isBlank()) {
			throw new GradleException(name + " がありません。docs/publishing-repo.md の「毎回やること」を見てください");
		}

		return value.trim();

	}

	private String bucket () {

		return getProviders().environmentVariable("JIMBLE_R2_BUCKET").map(String::trim).getOrElse(DEFAULT_BUCKET);

	}

	private static byte[] read (Path path) {

		try {
			return Files.readAllBytes(path);
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}

	}

}
