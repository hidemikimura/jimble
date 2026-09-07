package io.jimble.util.geometry;

import io.jimble.util.log.Log;
import org.locationtech.jts.algorithm.Centroid;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKTReader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * geometry
 */
public class GeometryUtil {

	/**
	 * 緯度経度の中心地
	 *
	 * @param locations	座標一覧[緯度, 経度]
	 * @return	中心座標
	 */
	public static double[] center (double[]...locations) {

		try {

			List<double[]> list = new ArrayList<>(Arrays.asList(locations));

			double[] first = list.getFirst();
			double[] last = list.getLast();
			if (first[0] != last[0]
				|| first[1] != last[1]) {
				list.add(first);
			}

			StringBuilder sb = new StringBuilder();
			sb.append("POLYGON((");
			for (int i = 0; i < list.size(); i++) {
				if (i > 0) {
					sb.append(", ");
				}
				sb.append(list.get(i)[1]);
				sb.append(" ");
				sb.append(list.get(i)[0]);
			}
			sb.append("))");

			WKTReader reader = new WKTReader();
			Geometry geometry = reader.read(sb.toString());
			Centroid centroid = new Centroid(geometry);
			return new double[]{ centroid.getCentroid().y, centroid.getCentroid().x };

		} catch (Exception ex) {

			Log.error(ex);
			return null;

		}

	}

	/**
	 * MySQLのGeometryのbyte配列をテキスト表現に変換する
	 *
	 * @param geometryAsBytes	MySQLのGeometryのbyte配列
	 * @return	テキスト表現
	 */
	public static String parseMySQLGeometry (byte[] geometryAsBytes) {

		try {

			byte[] sridBytes = new byte[4];
			System.arraycopy(geometryAsBytes, 0, sridBytes, 0, 4);
			boolean bigEndian = (geometryAsBytes[4] == 0x00);
			int srid = 0;
			if (bigEndian) {
				for (int k = 0; k < sridBytes.length; k++) {
					srid = (srid << 8) + (sridBytes[k] & 0xff);
				}
			} else {
				for (int k = 0; k < sridBytes.length; k++) {
					srid += (sridBytes[k] & 0xff) << (8 * k);
				}
			}

			WKBReader wkbReader = new WKBReader();
			byte[] wkb = new byte[geometryAsBytes.length - 4];
			System.arraycopy(geometryAsBytes, 4, wkb, 0, wkb.length);
			Geometry dbGeometry = wkbReader.read(wkb);
			dbGeometry.setSRID(srid);

			return dbGeometry.toText();

		} catch (Exception ex) {

			Log.error(ex);
			return null;

		}

	}

}
