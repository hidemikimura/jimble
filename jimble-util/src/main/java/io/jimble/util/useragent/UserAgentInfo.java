package io.jimble.util.useragent;

import java.util.ArrayList;
import java.util.List;

/**
 * ユーザーエージェント情報.
 */
public class UserAgentInfo {

	public static final String DEVICE_Mobile = "Mobile";
	public static final String DEVICE_Tablet = "Tablet";
	public static final String DEVICE_Desktop = "Desktop";

	public static final List<String> DEVICE_LIST = new ArrayList<>();
	static {
		DEVICE_LIST.add(DEVICE_Mobile);
		DEVICE_LIST.add(DEVICE_Desktop);
		DEVICE_LIST.add(DEVICE_Tablet);

	}


	public static final String OS_iOS = "iOS";
	public static final String OS_Android = "Android";
	public static final String OS_WindowsPhone = "Windows Phone";
	public static final String OS_Windows = "Windows";
	public static final String OS_Mac = "Mac";
	public static final String OS_Linux = "Linux";
	public static final String OS_ChromeOS = "Chrome OS";
	public static final String OS_FreeBSD = "FreeBSD";
	public static final String OS_Other = "Other";

	public static final List<String> OS_LIST = new ArrayList<>();
	static {
		OS_LIST.add(OS_iOS);
		OS_LIST.add(OS_Android);
		OS_LIST.add(OS_WindowsPhone);
		OS_LIST.add(OS_Windows);
		OS_LIST.add(OS_Mac);
		OS_LIST.add(OS_Linux);
		OS_LIST.add(OS_ChromeOS);
		OS_LIST.add(OS_FreeBSD);
		OS_LIST.add(OS_Other);
	}



	public String ua;

	public boolean android;

	public boolean iOS;

	public boolean windowsPhone;


	public boolean other = true;

	public boolean isWindowsOS;

	public boolean isMacOS;

	public boolean isLinuxOS;

	public boolean isChromeOS;

	public boolean isBSDOS;


	public boolean tablet;


	public boolean inFacebook;

	public boolean inTwitter;


	public boolean isChrome;

	public boolean isEdge;



	public boolean isSmartPhone () {

		return !other && !tablet;

	}

	public boolean isTablet () {

		return tablet;

	}

	public boolean isPC () {

		return other;

	}

	public String getDevice () {

		if (isSmartPhone()) {
			return DEVICE_Mobile;
		}

		if (isTablet()) {
			return DEVICE_Tablet;
		}

		return DEVICE_Desktop;

	}

	public String getOS () {

		if (iOS) {
			return OS_iOS;
		}

		if (android) {
			return OS_Android;
		}

		if (windowsPhone) {
			return OS_WindowsPhone;
		}

		if (isWindowsOS) {
			return OS_Windows;
		}

		if (isMacOS) {
			return OS_Mac;
		}

		if (isLinuxOS) {
			return OS_Linux;
		}

		if (isChromeOS) {
			return OS_ChromeOS;
		}

		if (isBSDOS) {
			return OS_FreeBSD;
		}

		return OS_Other;

	}

}
