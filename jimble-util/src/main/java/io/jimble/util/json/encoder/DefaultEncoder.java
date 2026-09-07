package io.jimble.util.json.encoder;

import io.jimble.util.convertor.Configration;
import io.jimble.util.json.formatter.Formatter;

import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.Charset;

/**
 * デフォルトJSONエンコーダクラス.
 *
 * @author DN
 */
public class DefaultEncoder extends AbstractEncoder {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String encode(Configration conf, Object obj) {

		try {
			return Formatter.format(obj, conf);
		} catch (Exception e) {
			setError(e);
			return "";
		}

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void encode(Configration conf, Object value, OutputStream stream, Charset charset) {
		try {
			Formatter.format(stream, charset, value, conf);
		} catch (Exception e) {
			setError(e);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void encode(Configration conf, Object value, Writer writer) {
		try {
			Formatter.format(writer, value, conf);
		} catch (Exception e) {
			setError(e);
		}
	}

}
