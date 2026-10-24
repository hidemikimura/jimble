package io.jimble.util.convertor;

/**
 * 変換インターフェイス.
 * 
 * @author DN
 * @param <T>
 * @deprecated 型ごとの変換は {@link io.jimble.converter.TypeConverter} で書き、
 *             {@link io.jimble.converter.ConverterOptions.Builder#typeConverter(Class, io.jimble.converter.TypeConverter)} で足す（2.7.0。D-307）
 */
@Deprecated(since = "2.7.0")
public interface IConvertor<T> {

	/**
	 * オブジェクトを変換する.
	 * 
	 * @param conf 設定情報
	 * @param obj オブジェクト
	 * @param destClasses 変換希望クラス([0]=変換希望クラス、[1...]=ジェネリクス指定)
	 * @return 変換後オブジェクト
	 * @throws Exception 例外
	 */
	public T convert(Configration conf, Object obj, Class<?>... destClasses) throws Exception;

}
