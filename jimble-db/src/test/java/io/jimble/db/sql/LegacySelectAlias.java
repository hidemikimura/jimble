package io.jimble.db.sql;

import com.typesafe.config.ConfigValueFactory;
import io.jimble.db.internal.sql.select.SelectAlias;
import io.jimble.util.conf.Conf;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * テストを 2.x と同じ SQL（列に {@code AS `表__列`} を付ける）で流す（{@code @ExtendWith(LegacySelectAlias.class)}。3.0.0）
 *
 * <p>
 * 3.0.0 から既定は別名を付けない。SQL の字面を 2.x の形で比べているテストは、これを付けて
 * {@value SelectAlias#KEY} = true で流す（別名を付けたときの字面が 2.x から変わっていないことの確かめにもなる）。
 * 別名を付けないときの字面は {@link SelectAliasTest} が見る。
 * </p>
 */
public final class LegacySelectAlias implements BeforeEachCallback, AfterEachCallback {

	@Override
	public void beforeEach (ExtensionContext context) {

		Conf.replace(Conf.conf().config().withValue(SelectAlias.KEY, ConfigValueFactory.fromAnyRef(true)));

	}

	@Override
	public void afterEach (ExtensionContext context) {

		Conf.reload();

	}

}
