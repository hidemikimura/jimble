package io.jimble.web.assets;

import io.jimble.web.context.WebContext;
import io.jimble.web.server.Dispatcher;
import io.jimble.web.server.JimbleApp;
import io.jimble.web.support.Fakes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 静的ファイルでディレクトリの中身の一覧を返さない（D-238）
 *
 * <p>クラスパスが file: のとき（IDE・jimbleRun・テスト）、かつてはディレクトリの中身の一覧を返していた。</p>
 */
class AssetDirectoryTest {

	private static Fakes.FakeResponseSink get (String path) {

		JimbleApp app = new JimbleApp() {
			{
				install(() -> AssetHandler.mount("/assets", "test-assets"));
			}
		};

		Fakes.FakeResponseSink sink = new Fakes.FakeResponseSink();

		try (WebContext context = new WebContext(new Fakes.FakeRequestSource("GET", path), sink)) {
			new Dispatcher(app).dispatch(context);
		}

		return sink;

	}

	@Test
	@DisplayName("ディレクトリは 404、ファイルは 200")
	void directoryIsNotListed () {

		assertEquals(404, get("/assets/sub").status());
		assertEquals(200, get("/assets/sub/app.js").status());

	}

}
