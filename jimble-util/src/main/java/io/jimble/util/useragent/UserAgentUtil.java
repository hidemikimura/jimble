package io.jimble.util.useragent;

/**
 * ユーザーエージェントユーティリティ.
 */
public class UserAgentUtil {

	/**
	 * ユーザーエージェント情報を解析する.
	 *
	 * @param userAgent	ユーザーエージェント
	 * @return	ユーザーエージェント情報
	 */
	public static UserAgentInfo parse (String userAgent) {

		UserAgentInfo info = new UserAgentInfo();
		info.ua(userAgent);

		if (userAgent == null || userAgent.isEmpty()) {
			info.other(true);
			return info;
		}

		String ua = userAgent.toLowerCase();

		if (ua.contains("iphone")
				|| ua.contains("ipod")) {
			// iPhone、iPod

			info.other(false);
			info.iOS(true);
			info.tablet(false);

		} else if (ua.contains("ipad")) {
			// iPad

			info.other(false);
			info.iOS(true);
			info.tablet(true);

		} else if (ua.contains("android")) {
			// Android

			info.other(false);
			info.android(true);
			info.tablet((!ua.contains("mobile")));

		} else if (ua.contains("windows phone")) {
			// Windows Phone

			info.other(false);
			info.windowsPhone(true);

		} else {
			// PC、その他

			info.other(true);
			info.isMacOS(ua.contains("macintosh"));
			info.isWindowsOS(ua.contains("windows"));
			info.isLinuxOS(ua.contains("linux"));
			info.isChromeOS(ua.contains("cros"));
			info.isBSDOS(ua.contains("freebsd"));

		}

		// Chrome判定
		info.isChrome(ua.contains("chrome"));
		if (info.isChrome() && info.android()) {
			if (ua.contains("version/")) {
				info.isChrome(false);
			}
		}

		// Edge判定
		info.isEdge(ua.contains("edge/") || ua.contains("edg/"));

		return info;

	}

}
