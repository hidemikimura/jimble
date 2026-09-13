package io.jimble.util.useragent;

import java.util.List;

/**
 * User-Agent から読み取ったもの（{@link UserAgentUtil#parse(String)} が作る）
 *
 * <p>
 * <b>相手の自己申告である。</b>User-Agent は誰でも好きに名乗れるので、
 * <b>これで許可・不許可を決めないこと</b>。画面の出し分けや集計に使うものである。
 * </p>
 *
 * <p>
 * <b>public フィールドではない（D-173）。</b>フィールドは 1.0 のあと
 * アクセサに置き換えられない。とくに {@code DEVICE_LIST} と {@code OS_LIST} は
 * <b>{@code public static final ArrayList}</b> だったので、
 * アプリから {@code clear()} できた——<b>消えるのはプロセス全体で1つの一覧</b>である。
 * いまは {@link List#of} なので、書き換えようとするとその場で落ちる。
 * </p>
 */
public final class UserAgentInfo {

	/** 端末: スマートフォン */
	public static final String DEVICE_Mobile = "Mobile";

	/** 端末: タブレット */
	public static final String DEVICE_Tablet = "Tablet";

	/** 端末: PC */
	public static final String DEVICE_Desktop = "Desktop";

	/** 端末の一覧（読み取り専用） */
	public static final List<String> DEVICE_LIST = List.of(DEVICE_Mobile, DEVICE_Desktop, DEVICE_Tablet);

	/** OS: iOS */
	public static final String OS_iOS = "iOS";

	/** OS: Android */
	public static final String OS_Android = "Android";

	/** OS: Windows Phone */
	public static final String OS_WindowsPhone = "Windows Phone";

	/** OS: Windows */
	public static final String OS_Windows = "Windows";

	/** OS: Mac */
	public static final String OS_Mac = "Mac";

	/** OS: Linux */
	public static final String OS_Linux = "Linux";

	/** OS: Chrome OS */
	public static final String OS_ChromeOS = "Chrome OS";

	/** OS: FreeBSD */
	public static final String OS_FreeBSD = "FreeBSD";

	/** OS: それ以外 */
	public static final String OS_Other = "Other";

	/** OS の一覧（読み取り専用） */
	public static final List<String> OS_LIST = List.of(
		OS_iOS, OS_Android, OS_WindowsPhone, OS_Windows, OS_Mac, OS_Linux, OS_ChromeOS, OS_FreeBSD, OS_Other);

	/** そのままの User-Agent 文字列 */
	private String ua;

	/** Android か */
	private boolean android;

	/** iOS か */
	private boolean iOS;

	/** Windows Phone か */
	private boolean windowsPhone;

	/** スマートフォンでもタブレットでもないか（PC） */
	private boolean other = true;

	/** Windows か */
	private boolean isWindowsOS;

	/** Mac か */
	private boolean isMacOS;

	/** Linux か */
	private boolean isLinuxOS;

	/** Chrome OS か */
	private boolean isChromeOS;

	/** FreeBSD か */
	private boolean isBSDOS;

	/** タブレットか */
	private boolean tablet;

	/** Facebook のアプリ内ブラウザか */
	private boolean inFacebook;

	/** X（Twitter）のアプリ内ブラウザか */
	private boolean inTwitter;

	/** Chrome か */
	private boolean isChrome;

	/** Edge か */
	private boolean isEdge;

	/**
	 * そのままの User-Agent 文字列
	 *
	 * @return	そのままの User-Agent 文字列
	 */
	public String ua () {

		return ua;

	}

	/**
	 * Android か
	 *
	 * @return	Android か
	 */
	public boolean android () {

		return android;

	}

	/**
	 * iOS か
	 *
	 * @return	iOS か
	 */
	public boolean iOS () {

		return iOS;

	}

	/**
	 * Windows Phone か
	 *
	 * @return	Windows Phone か
	 */
	public boolean windowsPhone () {

		return windowsPhone;

	}

	/**
	 * スマートフォンでもタブレットでもないか（PC）
	 *
	 * @return	スマートフォンでもタブレットでもないか（PC）
	 */
	public boolean other () {

		return other;

	}

	/**
	 * Windows か
	 *
	 * @return	Windows か
	 */
	public boolean isWindowsOS () {

		return isWindowsOS;

	}

	/**
	 * Mac か
	 *
	 * @return	Mac か
	 */
	public boolean isMacOS () {

		return isMacOS;

	}

	/**
	 * Linux か
	 *
	 * @return	Linux か
	 */
	public boolean isLinuxOS () {

		return isLinuxOS;

	}

	/**
	 * Chrome OS か
	 *
	 * @return	Chrome OS か
	 */
	public boolean isChromeOS () {

		return isChromeOS;

	}

	/**
	 * FreeBSD か
	 *
	 * @return	FreeBSD か
	 */
	public boolean isBSDOS () {

		return isBSDOS;

	}

	/**
	 * タブレットか
	 *
	 * @return	タブレットか
	 */
	public boolean tablet () {

		return tablet;

	}

	/**
	 * Facebook のアプリ内ブラウザか
	 *
	 * @return	Facebook のアプリ内ブラウザか
	 */
	public boolean inFacebook () {

		return inFacebook;

	}

	/**
	 * X（Twitter）のアプリ内ブラウザか
	 *
	 * @return	X（Twitter）のアプリ内ブラウザか
	 */
	public boolean inTwitter () {

		return inTwitter;

	}

	/**
	 * Chrome か
	 *
	 * @return	Chrome か
	 */
	public boolean isChrome () {

		return isChrome;

	}

	/**
	 * Edge か
	 *
	 * @return	Edge か
	 */
	public boolean isEdge () {

		return isEdge;

	}

	/**
	 * そのままの User-Agent 文字列 を決める
	 *
	 * @param ua	そのままの User-Agent 文字列
	 */
	void ua (String ua) {

		this.ua = ua;

	}

	/**
	 * Android か を決める
	 *
	 * @param android	Android か
	 */
	void android (boolean android) {

		this.android = android;

	}

	/**
	 * iOS か を決める
	 *
	 * @param iOS	iOS か
	 */
	void iOS (boolean iOS) {

		this.iOS = iOS;

	}

	/**
	 * Windows Phone か を決める
	 *
	 * @param windowsPhone	Windows Phone か
	 */
	void windowsPhone (boolean windowsPhone) {

		this.windowsPhone = windowsPhone;

	}

	/**
	 * スマートフォンでもタブレットでもないか（PC） を決める
	 *
	 * @param other	スマートフォンでもタブレットでもないか（PC）
	 */
	void other (boolean other) {

		this.other = other;

	}

	/**
	 * Windows か を決める
	 *
	 * @param isWindowsOS	Windows か
	 */
	void isWindowsOS (boolean isWindowsOS) {

		this.isWindowsOS = isWindowsOS;

	}

	/**
	 * Mac か を決める
	 *
	 * @param isMacOS	Mac か
	 */
	void isMacOS (boolean isMacOS) {

		this.isMacOS = isMacOS;

	}

	/**
	 * Linux か を決める
	 *
	 * @param isLinuxOS	Linux か
	 */
	void isLinuxOS (boolean isLinuxOS) {

		this.isLinuxOS = isLinuxOS;

	}

	/**
	 * Chrome OS か を決める
	 *
	 * @param isChromeOS	Chrome OS か
	 */
	void isChromeOS (boolean isChromeOS) {

		this.isChromeOS = isChromeOS;

	}

	/**
	 * FreeBSD か を決める
	 *
	 * @param isBSDOS	FreeBSD か
	 */
	void isBSDOS (boolean isBSDOS) {

		this.isBSDOS = isBSDOS;

	}

	/**
	 * タブレットか を決める
	 *
	 * @param tablet	タブレットか
	 */
	void tablet (boolean tablet) {

		this.tablet = tablet;

	}

	/**
	 * Facebook のアプリ内ブラウザか を決める
	 *
	 * @param inFacebook	Facebook のアプリ内ブラウザか
	 */
	void inFacebook (boolean inFacebook) {

		this.inFacebook = inFacebook;

	}

	/**
	 * X（Twitter）のアプリ内ブラウザか を決める
	 *
	 * @param inTwitter	X（Twitter）のアプリ内ブラウザか
	 */
	void inTwitter (boolean inTwitter) {

		this.inTwitter = inTwitter;

	}

	/**
	 * Chrome か を決める
	 *
	 * @param isChrome	Chrome か
	 */
	void isChrome (boolean isChrome) {

		this.isChrome = isChrome;

	}

	/**
	 * Edge か を決める
	 *
	 * @param isEdge	Edge か
	 */
	void isEdge (boolean isEdge) {

		this.isEdge = isEdge;

	}

	/**
	 * スマートフォンか
	 *
	 * @return	スマートフォンなら true
	 */
	public boolean isSmartPhone () {

		return !other && !tablet;

	}

	/**
	 * タブレットか
	 *
	 * @return	タブレットなら true
	 */
	public boolean isTablet () {

		return tablet;

	}

	/**
	 * PC か
	 *
	 * @return	PC なら true
	 */
	public boolean isPC () {

		return other;

	}

	/**
	 * 端末の名前
	 *
	 * @return	{@link #DEVICE_LIST} のどれか
	 */
	public String getDevice () {

		if (isSmartPhone()) {
			return DEVICE_Mobile;
		}

		if (isTablet()) {
			return DEVICE_Tablet;
		}

		return DEVICE_Desktop;

	}

	/**
	 * OS の名前
	 *
	 * @return	{@link #OS_LIST} のどれか
	 */
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

	@Override
	public String toString () {

		return "UserAgentInfo(" + getDevice() + " / " + getOS() + ")";

	}

}
