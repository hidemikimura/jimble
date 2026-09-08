package io.jimble.util.convertor.data;

import io.jimble.util.convertor.Configration;
import io.jimble.util.convertor.IConvertor;
import io.jimble.util.convertor.PropertyUtil;
import io.jimble.util.convertor.util.MapUtil;
import io.jimble.util.json.Dson;
import io.jimble.util.data.Data;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Data変換クラス.
 *
 * @author DN
 */
public class DataConvertor implements IConvertor<Data> {

	/**
	 * インスタンス.
	 */
	public static final DataConvertor INSTANCE = new DataConvertor();

	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	@Override
	public Data convert (Configration conf, Object obj, Class<?>... destClasses) throws Exception {

		if (obj == null) {
			return null;
		}

		if (destClasses == null || destClasses.length == 0) {
			destClasses = new Class<?>[]{ Data.class };
		}

		Data res = (Data) PropertyUtil.newInstance(destClasses[0]);
		if (obj instanceof String) {
			try {
				res = Data.fromJsonString((String) obj);
				return res;
			} catch (Exception ex) {}
		} else if (obj instanceof byte[]) {
			try (
				ByteArrayInputStream bais = new ByteArrayInputStream((byte[]) obj);
				InputStreamReader isr = new InputStreamReader(bais);
				BufferedReader br = new BufferedReader(isr)
			) {
				res = Dson.decodes(br, Data.class);
			} catch (Exception ex) {}
		}

		Map<Object, Object> map = new LinkedHashMap<>();
		MapUtil.convertMap(conf, obj, map, destClasses);
		for (Object key : map.keySet()) {
			res.put(PropertyUtil.toString(key), map.get(key));
		}

		return res;

	}

}
