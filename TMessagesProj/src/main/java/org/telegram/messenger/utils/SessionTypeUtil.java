package org.telegram.messenger.utils;

import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

public class SessionTypeUtil {

    public enum SessionType {
        UNKNOWN,
        XBOX,
        BRAVE,
        VIVALDI,
        OPERA,
        EDGE,
        CHROME,
        FIREFOX,
        SAFARI,
        ANDROID,
        WINDOWS,
        UBUNTU,
        LINUX,
        IPHONE,
        IPAD,
        MAC,
        APPLE,
        PREMIUMBOT,
        FRAGMENT,
        QUESTION
    }

    public static SessionType getSessionTypeObject(TLRPC.TL_authorization authorization) {
        String appName = authorization.app_name;
        String deviceModel = authorization.device_model.toLowerCase();
        String platform = authorization.platform.toLowerCase();
        String systemVersion = authorization.system_version.toLowerCase();

        if (deviceModel.contains("xbox")) {
            return SessionType.XBOX;
        }

        boolean isWeb = appName.contains("web") &&
                        (appName.indexOf("web") + 3 == appName.length() ||
                        !Character.isLowerCase(appName.charAt(appName.indexOf("web") + 3)));

        if (isWeb) {
            if (deviceModel.contains("brave")) {
                return SessionType.BRAVE;
            } else if (deviceModel.contains("vivaldi")) {
                return SessionType.VIVALDI;
            } else if (deviceModel.contains("opera") || deviceModel.contains("opr")) {
                return SessionType.OPERA;
            } else if (deviceModel.contains("edg")) {
                return SessionType.EDGE;
            } else if (deviceModel.contains("chrome")) {
                return SessionType.CHROME;
            } else if (deviceModel.contains("firefox") || deviceModel.contains("fxios")) {
                return SessionType.FIREFOX;
            } else if (deviceModel.contains("safari")) {
                return SessionType.SAFARI;
            }
        }

        if (platform.startsWith("android") || systemVersion.contains("android")) {
            return SessionType.ANDROID;
        } else if (platform.startsWith("windows") || systemVersion.contains("windows")) {
            return SessionType.WINDOWS;
        } else if (platform.startsWith("ubuntu") || systemVersion.contains("ubuntu")) {
            return SessionType.UBUNTU;
        } else if (platform.startsWith("linux") || systemVersion.contains("linux")) {
            return SessionType.LINUX;
        }

        boolean isIos = platform.startsWith("ios") || systemVersion.contains("ios");
        boolean isMacos = platform.startsWith("macos") || systemVersion.contains("macos");
        if (isIos && deviceModel.contains("iphone")) {
            return SessionType.IPHONE;
        } else if (isIos && deviceModel.contains("ipad")) {
            return SessionType.IPAD;
        } else if (isMacos && deviceModel.contains("mac")) {
            return SessionType.MAC;
        } else if (isIos || isMacos) {
            return SessionType.APPLE;
        }

        if (platform.contains("fragment")) {
            return SessionType.FRAGMENT;
        } else if (platform.contains("premiumbot")) {
            return SessionType.PREMIUMBOT;
        } else if (platform.equals("?")) {
            return SessionType.QUESTION;
        }

        return SessionType.UNKNOWN;
    }

    public static DrawableInfo getDrawableInfoForSessionType(SessionType sessionType) {
        int iconId;
        int colorKey, colorKey2;

        switch (sessionType) {
            case SAFARI:
                iconId = R.drawable.device_web_safari;
                colorKey = Theme.key_avatar_backgroundPink;
                colorKey2 = Theme.key_avatar_background2Pink;
                break;
            case EDGE:
                iconId = R.drawable.device_web_edge;
                colorKey = Theme.key_avatar_backgroundPink;
                colorKey2 = Theme.key_avatar_background2Pink;
                break;
            case CHROME:
                iconId = R.drawable.device_web_chrome;
                colorKey = Theme.key_avatar_backgroundPink;
                colorKey2 = Theme.key_avatar_background2Pink;
                break;
            case OPERA:
                iconId = R.drawable.device_web_opera;
                colorKey = Theme.key_avatar_backgroundPink;
                colorKey2 = Theme.key_avatar_background2Pink;
                break;
            case FIREFOX:
                iconId = R.drawable.device_web_firefox;
                colorKey = Theme.key_avatar_backgroundPink;
                colorKey2 = Theme.key_avatar_background2Pink;
                break;
            case VIVALDI:
                iconId = R.drawable.device_web_other;
                colorKey = Theme.key_avatar_backgroundPink;
                colorKey2 = Theme.key_avatar_background2Pink;
                break;
            case IPHONE:
                iconId = R.drawable.device_phone_ios;
                colorKey = Theme.key_avatar_backgroundBlue;
                colorKey2 = Theme.key_avatar_background2Blue;
                break;
            case IPAD:
                iconId = R.drawable.device_tablet_ios;
                colorKey = Theme.key_avatar_backgroundBlue;
                colorKey2 = Theme.key_avatar_background2Blue;
                break;
            case WINDOWS:
                iconId = R.drawable.device_desktop_win;
                colorKey = Theme.key_avatar_backgroundCyan;
                colorKey2 = Theme.key_avatar_background2Cyan;
                break;
            case MAC:
                iconId = R.drawable.device_desktop_osx;
                colorKey = Theme.key_avatar_backgroundCyan;
                colorKey2 = Theme.key_avatar_background2Cyan;
                break;
            case ANDROID:
                // You may need to adjust this based on the actual icon logic
                iconId = R.drawable.device_phone_android;
                colorKey = Theme.key_avatar_backgroundGreen;
                colorKey2 = Theme.key_avatar_background2Green;
                break;
            case FRAGMENT:
                iconId = R.drawable.fragment;
                colorKey = -1;
                colorKey2 = -1;
                break;
            case PREMIUMBOT:
                iconId = R.drawable.filled_star_plus;
                colorKey = Theme.key_color_yellow;
                colorKey2 = Theme.key_color_orange;
                break;
            case QUESTION:
                iconId = R.drawable.msg_emoji_question;
                colorKey = -1;
                colorKey2 = -1;
                break;
            default:
                iconId = R.drawable.device_web_other;
                colorKey = Theme.key_avatar_backgroundPink;
                colorKey2 = Theme.key_avatar_background2Pink;
                break;
        }

        return new DrawableInfo(iconId, colorKey, colorKey2);
    }

    public static class DrawableInfo {
        private final int iconId;
        private final int colorKey;
        private final int colorKey2;

        public DrawableInfo(int iconId, int colorKey, int colorKey2) {
            this.iconId = iconId;
            this.colorKey = colorKey;
            this.colorKey2 = colorKey2;
        }

        public int getIconId() {
            return iconId;
        }

        public int getColorKey() {
            return colorKey;
        }

        public int getColorKey2() {
            return colorKey2;
        }
    }
}

