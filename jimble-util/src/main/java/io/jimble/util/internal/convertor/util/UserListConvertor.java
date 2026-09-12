package io.jimble.util.internal.convertor.util;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;
import io.jimble.util.convertor.PropertyUtil;

import java.util.List;

/**
 * ユーザーリスト変換クラス.
 * 
 * @author DN
 */
public class UserListConvertor implements IConvertor<List<Object>> {

	/** 変換クラス. */
	private Class< ? > destInstanceClass;

	/**
	 * コンストラクタ.
	 * 
	 * @param destClass 変換クラス
	 */
	public UserListConvertor(Class< ? > destClass) {
		this.destInstanceClass = destClass;
	}

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	@Override
	public List<Object> convert(Configration conf, Object obj, Class< ? >... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		List<Object> res = (List<Object>) PropertyUtil.newInstance(destInstanceClass);
		ListUtil.convertList(conf, obj, res, destClasses);

		return res;
	}

}
