package io.jimble.util.convertor.math;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;
import io.jimble.util.convertor.PropertyUtil;

import java.math.BigInteger;

/**
 * BigInteger変換クラス.
 * 
 * @author DN
 */
public class BigIntegerConvertor implements IConvertor<BigInteger> {

	/** インスタンス. */
	public static final BigIntegerConvertor INSTANCE = new BigIntegerConvertor();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public BigInteger convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		if (obj instanceof BigInteger) {
			return (BigInteger) obj;
		}

		try {
			return new BigInteger(PropertyUtil.toString(obj));
		} catch (Exception e) {
			return new BigInteger("0");
		}

	}

}
