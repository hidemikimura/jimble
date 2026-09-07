package io.jimble.gradle.run;

import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryWatcher;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * ソースの見張り（要件 F-X-02）
 *
 * <p>
 * JDK の {@code WatchService} は macOS では<b>2秒ごとのポーリング</b>で、
 * しかも再帰的に見張れない。保存してからブラウザに出るまでが体感で分かるほど遅れる。
 * {@code io.methvin:directory-watcher}（Apache-2.0）は macOS では FSEvents、
 * Linux では inotify を使うので、これを挟む。<b>Gradle デーモンの中でだけ使う依存</b>で、
 * アプリの実行時クラスパスには入らない。
 * </p>
 */
final class SourceWatcher implements Closeable {

	/* 見張るもの */
	private final DirectoryWatcher watcher;

	/**
	 * コンストラクタ
	 *
	 * @param roots			見張るディレクトリ
	 * @param excludes		見張らないディレクトリ
	 * @param extensions	見張る拡張子
	 * @param onChange		変わったときに呼ぶもの
	 * @throws IOException	見張りを始められなかった場合
	 */
	SourceWatcher (
		List<Path> roots
		, List<Path> excludes
		, Set<String> extensions
		, Consumer<Path> onChange
	) throws IOException {

		this.watcher = DirectoryWatcher.builder()
			.paths(roots)
			.listener(event -> {

				if (event.eventType() == DirectoryChangeEvent.EventType.OVERFLOW) {
					return;
				}

				Path path = event.path();

				if (Files.isDirectory(path)) {
					return;
				}

				if (isExcluded(path, excludes)) {
					return;
				}

				if (!extensions.contains(extensionOf(path))) {
					return;
				}

				onChange.accept(path);

			})
			.build();

	}

	/**
	 * 見張り始める（別のスレッドで動く）
	 */
	void start () {

		watcher.watchAsync();

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void close () {

		try {
			watcher.close();
		} catch (IOException ignore) {
			// 閉じられなくても止めるだけなので何もしない
		}

	}

	/**
	 * 見張らないところか
	 *
	 * @param path		パス
	 * @param excludes	見張らないディレクトリ
	 * @return	見張らない場合 = true
	 */
	private static boolean isExcluded (Path path, List<Path> excludes) {

		for (Path exclude : excludes) {
			if (path.startsWith(exclude)) {
				return true;
			}
		}

		return false;

	}

	/**
	 * 拡張子
	 *
	 * @param path	パス
	 * @return	拡張子（{@code .java} の形。無ければ空文字）
	 */
	private static String extensionOf (Path path) {

		String name = path.getFileName().toString();
		int index = name.lastIndexOf('.');

		return index <= 0 ? "" : name.substring(index).toLowerCase(Locale.ROOT);

	}

}
