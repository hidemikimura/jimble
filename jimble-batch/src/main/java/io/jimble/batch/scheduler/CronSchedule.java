package io.jimble.batch.scheduler;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;

import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * cron の解釈（要件 F-B-04）
 *
 * <p>
 * UNIX の 5 項目（{@code 分 時 日 月 曜日}）。
 * 解釈は {@code com.cronutils:cron-utils}（Apache-2.0）に任せる。
 * </p>
 *
 * <p>
 * <b>次回時刻を出すところだけを切り出してある。</b>
 * 移送元はスケジューラ本体の中で直接パースしており、
 * 「この cron は次にいつ動くのか」を単体で確かめられなかった。
 * </p>
 */
public final class CronSchedule {

	/* パーサ（スレッドセーフ） */
	private static final CronParser PARSER =
		new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));

	/* cron */
	private final Cron cron;

	/* 元の文字列 */
	private final String expression;

	/**
	 * コンストラクタ
	 *
	 * @param cron			cron
	 * @param expression	元の文字列
	 */
	private CronSchedule (Cron cron, String expression) {

		this.cron = cron;
		this.expression = expression;

	}

	/**
	 * 解釈する
	 *
	 * @param expression	cron（{@code 分 時 日 月 曜日}）
	 * @return	解釈したもの（読めなければ null）
	 */
	public static CronSchedule parse (String expression) {

		if (expression == null || expression.isBlank()) {
			return null;
		}

		try {

			Cron cron = PARSER.parse(expression.trim());
			cron.validate();

			return new CronSchedule(cron, expression.trim());

		} catch (Exception ex) {

			return null;

		}

	}

	/**
	 * 次に動く時刻
	 *
	 * @param from	この時刻より後
	 * @return	時刻（無ければ null）
	 */
	public ZonedDateTime nextExecution (ZonedDateTime from) {

		Optional<ZonedDateTime> next = ExecutionTime.forCron(cron).nextExecution(from);

		return next.orElse(null);

	}

	/**
	 * 元の文字列
	 *
	 * @return	cron
	 */
	public String expression () {

		return expression;

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString () {

		return "CronSchedule(%s)".formatted(expression);

	}

}
