package io.jimble.util.internal.convertor.math;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;
import io.jimble.util.convertor.PropertyUtil;

import java.math.BigDecimal;

/**
 * BigDecimal変換クラス.
 * 
 * @author DN
 */
public class BigDecimalConvertor implements IConvertor<BigDecimal> {

	/** インスタンス. */
	public static final BigDecimalConvertor INSTANCE = new BigDecimalConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public BigDecimal convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		if (obj instanceof BigDecimal) {
			return (BigDecimal) obj;
		}

		try {
			return new BigDecimal(PropertyUtil.toString(obj));
		} catch (Exception e) {
			return new BigDecimal("0");
		}

	}

}
