package io.jimble.util.convertor.lang;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.Convertor;
import io.jimble.util.convertor.IConvertor;
import io.jimble.util.convertor.PropertyUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;

/**
 * Record変換クラス
 */
public class RecordConvertor<E> implements IConvertor<E> {

	/* インスタンス */
	public static final RecordConvertor<Object> INSTANCE = new RecordConvertor<>();

	/**
	 * {@inheritDoc}
	 */
	@Override
	/*
	 * 無検査キャスト：組み立てた record を {@code E} として返す。
	 * <b>どの record を作るかは destClasses[0] で決まる</b>ので、
	 * 型からは {@code E} と一致することを証明できない。
	 * 呼び出し側が違う型を書いていればそこで ClassCastException になる。
	 */
	@SuppressWarnings("unchecked")
	public E convert(Configration conf, Object obj, Class<?>... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		/** 循環参照前処理. */

		if (conf.isMaxHierarchy()) {
			return null;
		}

		int hash = System.identityHashCode(obj);

		if (!conf.hashSet.add(hash)) {
			return null;
		}
		conf.Hierarchy++;


		RecordComponent[] recordComponents = destClasses[0].getRecordComponents();
		Class<?>[] parameterTypes = new Class<?>[recordComponents.length];
		for (int i = 0; i < recordComponents.length; i++) {
			parameterTypes[i] = recordComponents[i].getType();
		}
		Constructor<?> constructor = destClasses[0].getDeclaredConstructor(parameterTypes);
		constructor.setAccessible(true);

		Object[] params = new Object[recordComponents.length];
		if (obj instanceof Map<?, ?>) {

			Map<?, ?> map = (Map<?, ?>) obj;
			for (int i = 0; i < recordComponents.length; i++) {
				RecordComponent recordComponent = recordComponents[i];
				if (map.containsKey(recordComponent.getName())) {
					params[i] = Convertor.convert(conf, map.get(recordComponent.getName()), recordComponent.getType());
				} else {
					params[i] = PropertyUtil.newInstance(recordComponent.getType());
				}
			}

		} else {

			List<PropertyUtil.MethodFieldInfo> names = PropertyUtil.getFieldNames(obj.getClass());
			for (int i = 0; i < recordComponents.length; i++) {
				RecordComponent recordComponent = recordComponents[i];
				PropertyUtil.MethodFieldInfo methodFieldInfo = null;
				for (PropertyUtil.MethodFieldInfo info : names) {
					if (info.getFieldName().equals(recordComponent.getName())) {
						methodFieldInfo = info;
						break;
					}
				}
				if (methodFieldInfo != null) {
					Object v = methodFieldInfo.getProperty(obj);
					params[i] = Convertor.convert(conf, v, recordComponent.getType());
				} else {
					params[i] = PropertyUtil.newInstance(recordComponent.getType());
				}
			}

		}

		Object res = constructor.newInstance(params);

		/* 循環参照後処理. */

		conf.Hierarchy--;
		conf.hashSet.remove(hash);

		return (E) res;

	}

}
