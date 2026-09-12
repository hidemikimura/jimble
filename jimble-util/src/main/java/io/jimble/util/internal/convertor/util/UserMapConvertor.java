package io.jimble.util.internal.convertor.util;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;
import io.jimble.util.convertor.PropertyUtil;

import java.util.Map;

/**
 * ユーザーマップ変換クラス.
 * 
 * @author DN
 */
public class UserMapConvertor implements IConvertor<Map<Object, Object>> {

	/** 変換クラス. */
	private Class< ? > destInstanceClass;

	/**
	 * コンストラクタ.
	 * 
	 * @param destClass 変換クラス
	 */
	public UserMapConvertor(Class< ? > destClass) {
		this.destInstanceClass = destClass;
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	@Override
	public Map<Object, Object> convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		Map<Object, Object> res = (Map<Object, Object>) PropertyUtil.newInstance(destInstanceClass);
		if (destClasses.length > 1) {
			Class< ? >[] dClasses = new Class< ? >[destClasses.length];
			dClasses[0] = Map.class;
			for (int i = 1; i < destClasses.length; i++) {
				dClasses[i] = destClasses[i];
			}
			MapUtil.convertMap(conf, obj, res, dClasses);
		} else {
			MapUtil.convertMap(conf, obj, res, destClasses);
		}

		return res;
	}

}
