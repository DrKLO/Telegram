/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.ActionBar;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.NinePatchDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.graphics.drawable.shapes.RoundRectShape;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.StateSet;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Bitmaps;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.time.SunDate;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.BackgroundGradientDrawable;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.ScamDrawable;
import org.telegram.ui.Components.ThemeEditorView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import androidx.annotation.UiThread;

public class Theme {

    public static class ThemeInfo implements NotificationCenter.NotificationCenterDelegate {
        public String name;
        public String pathToFile;
        public String pathToWallpaper;
        public String assetName;
        public String slug;
        public boolean badWallpaper;
        public boolean isBlured;
        public boolean isMotion;

        public int account;

        public TLRPC.TL_theme info;
        public boolean loaded = true;

        public String uploadingThumb;
        public String uploadingFile;
        public TLRPC.InputFile uploadedThumb;
        public TLRPC.InputFile uploadedFile;

        public int previewBackgroundColor;
        public int previewBackgroundGradientColor;
        public int previewWallpaperOffset;
        public int previewInColor;
        public int previewOutColor;
        public boolean previewParsed;
        public boolean themeLoaded = true;

        public int sortIndex;

        public int[] accentColorOptions;
        public int accentBaseColor;
        public int accentColor;
        final float[] accentBaseColorHsv = new float[3];
        final float[] accentColorHsv = new float[3];

        ThemeInfo() {

        }

        public ThemeInfo(ThemeInfo other) {
            name = other.name;
            pathToFile = other.pathToFile;
            assetName = other.assetName;
            sortIndex = other.sortIndex;
            accentColorOptions = other.accentColorOptions;
            accentBaseColor = other.accentBaseColor;
            accentColor = other.accentColor;
            info = other.info;
            loaded = other.loaded;
            uploadingThumb = other.uploadingThumb;
            uploadingFile = other.uploadingFile;
            uploadedThumb = other.uploadedThumb;
            uploadedFile = other.uploadedFile;
            account = other.account;
            pathToWallpaper = other.pathToWallpaper;
            slug = other.slug;
            badWallpaper = other.badWallpaper;
            isBlured = other.isBlured;
            isMotion = other.isMotion;

            previewBackgroundColor = other.previewBackgroundColor;
            previewBackgroundGradientColor = other.previewBackgroundGradientColor;
            previewWallpaperOffset = other.previewWallpaperOffset;
            previewInColor = other.previewInColor;
            previewOutColor = other.previewOutColor;
            previewParsed = other.previewParsed;
            themeLoaded = other.themeLoaded;

            Color.colorToHSV(accentBaseColor, accentBaseColorHsv);
            Color.colorToHSV(accentColor, accentColorHsv);
        }

        JSONObject getSaveJson() {
            try {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("name", name);
                jsonObject.put("path", pathToFile);
                jsonObject.put("account", account);
                if (info != null) {
                    SerializedData data = new SerializedData(info.getObjectSize());
                    info.serializeToStream(data);
                    jsonObject.put("info", Utilities.bytesToHex(data.toByteArray()));
                }
                jsonObject.put("loaded", loaded);
                return jsonObject;
            } catch (Exception e) {
                FileLog.e(e);
            }
            return null;
        }

        public String getName() {
            if ("Default".equals(name)) {
                return LocaleController.getString("Default", R.string.Default);
            } else if ("Blue".equals(name)) {
                return LocaleController.getString("ThemeBlue", R.string.ThemeBlue);
            } else if ("Dark Blue".equals(name)) {
                return LocaleController.getString("ThemeDark", R.string.ThemeDark);
            } else if ("Graphite".equals(name)) {
                return LocaleController.getString("ThemeGraphite", R.string.ThemeGraphite);
            } else if ("Arctic Blue".equals(name)) {
                return LocaleController.getString("ThemeArcticBlue", R.string.ThemeArcticBlue);
            }
            return info != null ? info.title : name;
        }

        public boolean isDark() {
            return "Dark Blue".equals(name) || "Graphite".equals(name);
        }

        public boolean isLight() {
            return pathToFile == null && !isDark();
        }

        public String getKey() {
            if (info != null) {
                return "remote" + info.id;
            }
            return name;
        }

        static ThemeInfo createWithJson(JSONObject object) {
            if (object == null) {
                return null;
            }
            try {
                ThemeInfo themeInfo = new ThemeInfo();
                themeInfo.name = object.getString("name");
                themeInfo.pathToFile = object.getString("path");
                if (object.has("account")) {
                    themeInfo.account = object.getInt("account");
                }
                if (object.has("info")) {
                    try {
                        SerializedData serializedData = new SerializedData(Utilities.hexToBytes(object.getString("info")));
                        themeInfo.info = (TLRPC.TL_theme) TLRPC.Theme.TLdeserialize(serializedData, serializedData.readInt32(true), true);
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }
                if (object.has("loaded")) {
                    themeInfo.loaded = object.getBoolean("loaded");
                }
                return themeInfo;
            } catch (Exception e) {
                FileLog.e(e);
            }
            return null;
        }

        static ThemeInfo createWithString(String string) {
            if (TextUtils.isEmpty(string)) {
                return null;
            }
            String[] args = string.split("\\|");
            if (args.length != 2) {
                return null;
            }
            ThemeInfo themeInfo = new ThemeInfo();
            themeInfo.name = args[0];
            themeInfo.pathToFile = args[1];
            return themeInfo;
        }

        void setAccentColorOptions(int[] options) {
            accentColorOptions = options;
            accentBaseColor = options[0];
            Color.colorToHSV(accentBaseColor, accentBaseColorHsv);
            setAccentColor(accentBaseColor);
        }

        void setAccentColor(int color) {
            accentColor = color;
            Color.colorToHSV(accentColor, accentColorHsv);
        }

        @UiThread
        private void loadThemeDocument() {
            loaded = false;
            NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.fileDidLoad);
            NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.fileDidFailToLoad);
            FileLoader.getInstance(account).loadFile(info.document, info, 1, 1);
        }

        @UiThread
        private void removeObservers() {
            NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.fileDidLoad);
            NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.fileDidFailToLoad);
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.fileDidLoad || id == NotificationCenter.fileDidFailToLoad) {
                String location = (String) args[0];
                if (info != null && info.document != null) {
                    String name = FileLoader.getAttachFileName(info.document);
                    if (location.equals(name)) {
                        removeObservers();
                        if (id == NotificationCenter.fileDidLoad) {
                            loaded = true;
                            previewParsed = false;
                            saveOtherThemes(true);
                            if (this == currentTheme && previousTheme == null) {
                                applyTheme(this, this == currentNightTheme);
                            }
                        }
                    }
                }
            }
        }
    }

    private static final Object sync = new Object();
    private static final Object wallpaperSync = new Object();

    public static final int ACTION_BAR_PHOTO_VIEWER_COLOR = 0x7f000000;
    public static final int ACTION_BAR_MEDIA_PICKER_COLOR = 0xff333333;
    public static final int ACTION_BAR_VIDEO_EDIT_COLOR = 0xff000000;
    public static final int ACTION_BAR_PLAYER_COLOR = 0xffffffff;
    public static final int ACTION_BAR_PICKER_SELECTOR_COLOR = 0xff3d3d3d;
    public static final int ACTION_BAR_WHITE_SELECTOR_COLOR = 0x40ffffff;
    public static final int ACTION_BAR_AUDIO_SELECTOR_COLOR = 0x2f000000;
    public static final int ARTICLE_VIEWER_MEDIA_PROGRESS_COLOR = 0xffffffff;
    //public static final int INPUT_FIELD_SELECTOR_COLOR = 0xffd6d6d6;

    public static final int AUTO_NIGHT_TYPE_NONE = 0;
    public static final int AUTO_NIGHT_TYPE_SCHEDULED = 1;
    public static final int AUTO_NIGHT_TYPE_AUTOMATIC = 2;

    private static final int LIGHT_SENSOR_THEME_SWITCH_DELAY = 1800;
    private static final int LIGHT_SENSOR_THEME_SWITCH_NEAR_DELAY = 12000;
    private static final int LIGHT_SENSOR_THEME_SWITCH_NEAR_THRESHOLD = 12000;
    private static SensorManager sensorManager;
    private static Sensor lightSensor;
    private static boolean lightSensorRegistered;
    private static float lastBrightnessValue = 1.0f;
    private static long lastThemeSwitchTime;
    private static boolean switchDayRunnableScheduled;
    private static boolean switchNightRunnableScheduled;
    private static Runnable switchDayBrightnessRunnable = new Runnable() {
        @Override
        public void run() {
            switchDayRunnableScheduled = false;
            applyDayNightThemeMaybe(false);
        }
    };
    private static Runnable switchNightBrightnessRunnable = new Runnable() {
        @Override
        public void run() {
            switchNightRunnableScheduled = false;
            applyDayNightThemeMaybe(true);
        }
    };
    public static int selectedAutoNightType = AUTO_NIGHT_TYPE_NONE;
    public static boolean autoNightScheduleByLocation;
    public static float autoNightBrighnessThreshold = 0.25f;
    public static int autoNightDayStartTime = 22 * 60;
    public static int autoNightDayEndTime = 8 * 60;
    public static int autoNightSunsetTime = 22 * 60;
    public static int autoNightLastSunCheckDay = -1;
    public static int autoNightSunriseTime = 8 * 60;
    public static String autoNightCityName = "";
    public static double autoNightLocationLatitude = 10000;
    public static double autoNightLocationLongitude = 10000;

    private static Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static int loadingCurrentTheme;
    private static int lastLoadingCurrentThemeTime;
    private static boolean loadingRemoteThemes;
    private static int lastLoadingThemesTime;
    private static int remoteThemesHash;

    public static ArrayList<ThemeInfo> themes;
    private static ArrayList<ThemeInfo> otherThemes;
    private static HashMap<String, ThemeInfo> themesDict;
    private static ThemeInfo currentTheme;
    private static ThemeInfo currentNightTheme;
    private static ThemeInfo currentDayTheme;
    private static ThemeInfo defaultTheme;
    private static ThemeInfo previousTheme;
    private static boolean switchingNightTheme;
    private static boolean isInNigthMode;

    public static PorterDuffColorFilter colorFilter;
    public static PorterDuffColorFilter colorPressedFilter;
    public static PorterDuffColorFilter colorFilter2;
    public static PorterDuffColorFilter colorPressedFilter2;
    private static int selectedColor;
    private static boolean isCustomTheme;
    private static int serviceMessageColor;
    private static int serviceSelectedMessageColor;
    public static int serviceMessageColorBackup;
    public static int serviceSelectedMessageColorBackup;
    private static int serviceMessage2Color;
    private static int serviceSelectedMessage2Color;
    private static int currentColor;
    private static int currentSelectedColor;
    private static Drawable wallpaper;
    private static Drawable themedWallpaper;
    private static int themedWallpaperFileOffset;
    private static String themedWallpaperLink;
    private static boolean isWallpaperMotion;
    private static Boolean isWallpaperMotionPrev;
    private static boolean isPatternWallpaper;

    public static Paint dividerPaint;
    public static Paint linkSelectionPaint;
    public static Paint checkboxSquare_eraserPaint;
    public static Paint checkboxSquare_checkPaint;
    public static Paint checkboxSquare_backgroundPaint;
    public static Paint avatar_backgroundPaint;

    public static Drawable listSelector;
    public static Drawable avatar_savedDrawable;
    public static Drawable avatar_ghostDrawable;

    public static Drawable moveUpDrawable;

    public static Paint dialogs_onlineCirclePaint;
    public static Paint dialogs_tabletSeletedPaint;
    public static Paint dialogs_pinnedPaint;
    public static Paint dialogs_countPaint;
    public static Paint dialogs_errorPaint;
    public static Paint dialogs_countGrayPaint;
    public static TextPaint dialogs_namePaint;
    public static TextPaint dialogs_nameEncryptedPaint;
    public static TextPaint dialogs_searchNamePaint;
    public static TextPaint dialogs_searchNameEncryptedPaint;
    public static TextPaint dialogs_messagePaint;
    public static TextPaint dialogs_messageNamePaint;
    public static TextPaint dialogs_messagePrintingPaint;
    public static TextPaint dialogs_timePaint;
    public static TextPaint dialogs_countTextPaint;
    public static TextPaint dialogs_archiveTextPaint;
    public static TextPaint dialogs_onlinePaint;
    public static TextPaint dialogs_offlinePaint;
    public static Drawable dialogs_checkDrawable;
    public static Drawable dialogs_checkReadDrawable;
    public static Drawable dialogs_halfCheckDrawable;
    public static Drawable dialogs_clockDrawable;
    public static Drawable dialogs_errorDrawable;
    public static Drawable dialogs_reorderDrawable;
    public static Drawable dialogs_lockDrawable;
    public static Drawable dialogs_groupDrawable;
    public static Drawable dialogs_broadcastDrawable;
    public static Drawable dialogs_botDrawable;
    public static Drawable dialogs_muteDrawable;
    public static Drawable dialogs_verifiedDrawable;
    public static ScamDrawable dialogs_scamDrawable;
    public static Drawable dialogs_verifiedCheckDrawable;
    public static Drawable dialogs_pinnedDrawable;
    public static Drawable dialogs_mentionDrawable;
    public static Drawable dialogs_holidayDrawable;
    public static RLottieDrawable dialogs_archiveAvatarDrawable;
    public static RLottieDrawable dialogs_archiveDrawable;
    public static RLottieDrawable dialogs_unarchiveDrawable;
    public static RLottieDrawable dialogs_pinArchiveDrawable;
    public static RLottieDrawable dialogs_unpinArchiveDrawable;
    public static boolean dialogs_archiveDrawableRecolored;
    public static boolean dialogs_archiveAvatarDrawableRecolored;
    private static int dialogs_holidayDrawableOffsetX;
    private static int dialogs_holidayDrawableOffsetY;
    private static long lastHolidayCheckTime;
    private static boolean canStartHolidayAnimation;

    public static TextPaint profile_aboutTextPaint;
    public static Drawable profile_verifiedDrawable;
    public static Drawable profile_verifiedCheckDrawable;

    public static Paint chat_docBackPaint;
    public static Paint chat_deleteProgressPaint;
    public static Paint chat_botProgressPaint;
    public static Paint chat_urlPaint;
    public static Paint chat_textSearchSelectionPaint;
    public static Paint chat_instantViewRectPaint;
    public static Paint chat_replyLinePaint;
    public static Paint chat_msgErrorPaint;
    public static Paint chat_statusPaint;
    public static Paint chat_statusRecordPaint;
    public static Paint chat_actionBackgroundPaint;
    public static Paint chat_timeBackgroundPaint;
    public static Paint chat_composeBackgroundPaint;
    public static Paint chat_radialProgressPaint;
    public static Paint chat_radialProgress2Paint;
    public static TextPaint chat_msgTextPaint;
    public static TextPaint chat_actionTextPaint;
    public static TextPaint chat_msgBotButtonPaint;
    public static TextPaint chat_msgGameTextPaint;
    public static TextPaint chat_msgTextPaintOneEmoji;
    public static TextPaint chat_msgTextPaintTwoEmoji;
    public static TextPaint chat_msgTextPaintThreeEmoji;
    public static TextPaint chat_infoPaint;
    public static TextPaint chat_livePaint;
    public static TextPaint chat_docNamePaint;
    public static TextPaint chat_locationTitlePaint;
    public static TextPaint chat_locationAddressPaint;
    public static TextPaint chat_durationPaint;
    public static TextPaint chat_gamePaint;
    public static TextPaint chat_shipmentPaint;
    public static TextPaint chat_instantViewPaint;
    public static TextPaint chat_audioTimePaint;
    public static TextPaint chat_audioTitlePaint;
    public static TextPaint chat_audioPerformerPaint;
    public static TextPaint chat_botButtonPaint;
    public static TextPaint chat_contactNamePaint;
    public static TextPaint chat_contactPhonePaint;
    public static TextPaint chat_timePaint;
    public static TextPaint chat_adminPaint;
    public static TextPaint chat_namePaint;
    public static TextPaint chat_forwardNamePaint;
    public static TextPaint chat_replyNamePaint;
    public static TextPaint chat_replyTextPaint;
    public static TextPaint chat_contextResult_titleTextPaint;
    public static TextPaint chat_contextResult_descriptionTextPaint;

    public static Drawable chat_msgNoSoundDrawable;
    public static Drawable chat_composeShadowDrawable;
    public static Drawable chat_roundVideoShadow;
    public static Drawable chat_msgInDrawable;
    public static Drawable chat_msgInSelectedDrawable;
    public static Drawable chat_msgInShadowDrawable;
    public static Drawable chat_msgOutDrawable;
    public static Drawable chat_msgOutSelectedDrawable;
    public static Drawable chat_msgOutShadowDrawable;
    public static Drawable chat_msgInMediaDrawable;
    public static Drawable chat_msgInMediaSelectedDrawable;
    public static Drawable chat_msgInMediaShadowDrawable;
    public static Drawable chat_msgOutMediaDrawable;
    public static Drawable chat_msgOutMediaSelectedDrawable;
    public static Drawable chat_msgOutMediaShadowDrawable;
    public static Drawable chat_msgOutCheckDrawable;
    public static Drawable chat_msgOutCheckSelectedDrawable;
    public static Drawable chat_msgOutCheckReadDrawable;
    public static Drawable chat_msgOutCheckReadSelectedDrawable;
    public static Drawable chat_msgOutHalfCheckDrawable;
    public static Drawable chat_msgOutHalfCheckSelectedDrawable;
    public static Drawable chat_msgOutClockDrawable;
    public static Drawable chat_msgOutSelectedClockDrawable;
    public static Drawable chat_msgInClockDrawable;
    public static Drawable chat_msgInSelectedClockDrawable;
    public static Drawable chat_msgMediaCheckDrawable;
    public static Drawable chat_msgMediaHalfCheckDrawable;
    public static Drawable chat_msgMediaClockDrawable;
    public static Drawable chat_msgStickerCheckDrawable;
    public static Drawable chat_msgStickerHalfCheckDrawable;
    public static Drawable chat_msgStickerClockDrawable;
    public static Drawable chat_msgStickerViewsDrawable;
    public static Drawable chat_msgInViewsDrawable;
    public static Drawable chat_msgInViewsSelectedDrawable;
    public static Drawable chat_msgOutViewsDrawable;
    public static Drawable chat_msgOutViewsSelectedDrawable;
    public static Drawable chat_msgMediaViewsDrawable;
    public static Drawable chat_msgInMenuDrawable;
    public static Drawable chat_msgInMenuSelectedDrawable;
    public static Drawable chat_msgOutMenuDrawable;
    public static Drawable chat_msgOutMenuSelectedDrawable;
    public static Drawable chat_msgMediaMenuDrawable;
    public static Drawable chat_msgInInstantDrawable;
    public static Drawable chat_msgOutInstantDrawable;
    public static Drawable chat_msgErrorDrawable;
    public static Drawable chat_muteIconDrawable;
    public static Drawable chat_lockIconDrawable;
    public static Drawable chat_inlineResultFile;
    public static Drawable chat_inlineResultAudio;
    public static Drawable chat_inlineResultLocation;
    public static Drawable chat_redLocationIcon;
    public static Drawable chat_msgOutBroadcastDrawable;
    public static Drawable chat_msgMediaBroadcastDrawable;
    public static Drawable chat_msgOutLocationDrawable;
    public static Drawable chat_msgBroadcastDrawable;
    public static Drawable chat_msgBroadcastMediaDrawable;
    public static Drawable chat_contextResult_shadowUnderSwitchDrawable;
    public static Drawable chat_shareDrawable;
    public static Drawable chat_shareIconDrawable;
    public static Drawable chat_replyIconDrawable;
    public static Drawable chat_goIconDrawable;
    public static Drawable chat_botLinkDrawalbe;
    public static Drawable chat_botInlineDrawable;
    public static Drawable chat_systemDrawable;
    public static Drawable chat_msgInCallDrawable;
    public static Drawable chat_msgInCallSelectedDrawable;
    public static Drawable chat_msgOutCallDrawable;
    public static Drawable chat_msgOutCallSelectedDrawable;

    public static Drawable chat_msgCallUpGreenDrawable;
    public static Drawable chat_msgCallDownRedDrawable;
    public static Drawable chat_msgCallDownGreenDrawable;

    public static Drawable chat_msgAvatarLiveLocationDrawable;
    public static Drawable chat_attachEmptyDrawable;
    public static Drawable[] chat_attachButtonDrawables = new Drawable[6];
    public static Drawable[] chat_locationDrawable = new Drawable[2];
    public static Drawable[] chat_contactDrawable = new Drawable[2];
    public static Drawable[] chat_cornerOuter = new Drawable[4];
    public static Drawable[] chat_cornerInner = new Drawable[4];
    public static Drawable[][] chat_fileStatesDrawable = new Drawable[10][2];
    public static CombinedDrawable[][] chat_fileMiniStatesDrawable = new CombinedDrawable[6][2];
    public static Drawable[][] chat_photoStatesDrawables = new Drawable[13][2];

    public static Drawable calllog_msgCallUpRedDrawable;
    public static Drawable calllog_msgCallUpGreenDrawable;
    public static Drawable calllog_msgCallDownRedDrawable;
    public static Drawable calllog_msgCallDownGreenDrawable;

    public static Drawable chat_fileIcon;
    public static Drawable chat_flameIcon;
    public static Drawable chat_gifIcon;

    public static final String key_dialogBackground = "dialogBackground";
    public static final String key_dialogBackgroundGray = "dialogBackgroundGray";
    public static final String key_dialogTextBlack = "dialogTextBlack";
    public static final String key_dialogTextLink = "dialogTextLink";
    public static final String key_dialogLinkSelection = "dialogLinkSelection";
    public static final String key_dialogTextRed = "dialogTextRed";
    public static final String key_dialogTextRed2 = "dialogTextRed2";
    public static final String key_dialogTextBlue = "dialogTextBlue";
    public static final String key_dialogTextBlue2 = "dialogTextBlue2";
    public static final String key_dialogTextBlue3 = "dialogTextBlue3";
    public static final String key_dialogTextBlue4 = "dialogTextBlue4";
    public static final String key_dialogTextGray = "dialogTextGray";
    public static final String key_dialogTextGray2 = "dialogTextGray2";
    public static final String key_dialogTextGray3 = "dialogTextGray3";
    public static final String key_dialogTextGray4 = "dialogTextGray4";
    public static final String key_dialogTextHint = "dialogTextHint";
    public static final String key_dialogInputField = "dialogInputField";
    public static final String key_dialogInputFieldActivated = "dialogInputFieldActivated";
    public static final String key_dialogCheckboxSquareBackground = "dialogCheckboxSquareBackground";
    public static final String key_dialogCheckboxSquareCheck = "dialogCheckboxSquareCheck";
    public static final String key_dialogCheckboxSquareUnchecked = "dialogCheckboxSquareUnchecked";
    public static final String key_dialogCheckboxSquareDisabled = "dialogCheckboxSquareDisabled";
    public static final String key_dialogScrollGlow = "dialogScrollGlow";
    public static final String key_dialogRoundCheckBox = "dialogRoundCheckBox";
    public static final String key_dialogRoundCheckBoxCheck = "dialogRoundCheckBoxCheck";
    public static final String key_dialogBadgeBackground = "dialogBadgeBackground";
    public static final String key_dialogBadgeText = "dialogBadgeText";
    public static final String key_dialogRadioBackground = "dialogRadioBackground";
    public static final String key_dialogRadioBackgroundChecked = "dialogRadioBackgroundChecked";
    public static final String key_dialogProgressCircle = "dialogProgressCircle";
    public static final String key_dialogLineProgress = "dialogLineProgress";
    public static final String key_dialogLineProgressBackground = "dialogLineProgressBackground";
    public static final String key_dialogButton = "dialogButton";
    public static final String key_dialogButtonSelector = "dialogButtonSelector";
    public static final String key_dialogIcon = "dialogIcon";
    public static final String key_dialogRedIcon = "dialogRedIcon";
    public static final String key_dialogGrayLine = "dialogGrayLine";
    public static final String key_dialogTopBackground = "dialogTopBackground";
    public static final String key_dialogCameraIcon = "dialogCameraIcon";
    public static final String key_dialog_inlineProgressBackground = "dialog_inlineProgressBackground";
    public static final String key_dialog_inlineProgress = "dialog_inlineProgress";
    public static final String key_dialogSearchBackground = "dialogSearchBackground";
    public static final String key_dialogSearchHint = "dialogSearchHint";
    public static final String key_dialogSearchIcon = "dialogSearchIcon";
    public static final String key_dialogSearchText = "dialogSearchText";
    public static final String key_dialogFloatingButton = "dialogFloatingButton";
    public static final String key_dialogFloatingButtonPressed = "dialogFloatingButtonPressed";
    public static final String key_dialogFloatingIcon = "dialogFloatingIcon";
    public static final String key_dialogShadowLine = "dialogShadowLine";

    public static final String key_windowBackgroundWhite = "windowBackgroundWhite";
    public static final String key_windowBackgroundUnchecked = "windowBackgroundUnchecked";
    public static final String key_windowBackgroundChecked = "windowBackgroundChecked";
    public static final String key_windowBackgroundCheckText = "windowBackgroundCheckText";
    public static final String key_progressCircle = "progressCircle";
    public static final String key_listSelector = "listSelectorSDK21";
    public static final String key_windowBackgroundWhiteInputField = "windowBackgroundWhiteInputField";
    public static final String key_windowBackgroundWhiteInputFieldActivated = "windowBackgroundWhiteInputFieldActivated";
    public static final String key_windowBackgroundWhiteGrayIcon = "windowBackgroundWhiteGrayIcon";
    public static final String key_windowBackgroundWhiteBlueText = "windowBackgroundWhiteBlueText";
    public static final String key_windowBackgroundWhiteBlueText2 = "windowBackgroundWhiteBlueText2";
    public static final String key_windowBackgroundWhiteBlueText3 = "windowBackgroundWhiteBlueText3";
    public static final String key_windowBackgroundWhiteBlueText4 = "windowBackgroundWhiteBlueText4";
    public static final String key_windowBackgroundWhiteBlueText5 = "windowBackgroundWhiteBlueText5";
    public static final String key_windowBackgroundWhiteBlueText6 = "windowBackgroundWhiteBlueText6";
    public static final String key_windowBackgroundWhiteBlueText7 = "windowBackgroundWhiteBlueText7";
    public static final String key_windowBackgroundWhiteBlueButton = "windowBackgroundWhiteBlueButton";
    public static final String key_windowBackgroundWhiteBlueIcon = "windowBackgroundWhiteBlueIcon";
    public static final String key_windowBackgroundWhiteGreenText = "windowBackgroundWhiteGreenText";
    public static final String key_windowBackgroundWhiteGreenText2 = "windowBackgroundWhiteGreenText2";
    public static final String key_windowBackgroundWhiteRedText = "windowBackgroundWhiteRedText";
    public static final String key_windowBackgroundWhiteRedText2 = "windowBackgroundWhiteRedText2";
    public static final String key_windowBackgroundWhiteRedText3 = "windowBackgroundWhiteRedText3";
    public static final String key_windowBackgroundWhiteRedText4 = "windowBackgroundWhiteRedText4";
    public static final String key_windowBackgroundWhiteRedText5 = "windowBackgroundWhiteRedText5";
    public static final String key_windowBackgroundWhiteRedText6 = "windowBackgroundWhiteRedText6";
    public static final String key_windowBackgroundWhiteGrayText = "windowBackgroundWhiteGrayText";
    public static final String key_windowBackgroundWhiteGrayText2 = "windowBackgroundWhiteGrayText2";
    public static final String key_windowBackgroundWhiteGrayText3 = "windowBackgroundWhiteGrayText3";
    public static final String key_windowBackgroundWhiteGrayText4 = "windowBackgroundWhiteGrayText4";
    public static final String key_windowBackgroundWhiteGrayText5 = "windowBackgroundWhiteGrayText5";
    public static final String key_windowBackgroundWhiteGrayText6 = "windowBackgroundWhiteGrayText6";
    public static final String key_windowBackgroundWhiteGrayText7 = "windowBackgroundWhiteGrayText7";
    public static final String key_windowBackgroundWhiteGrayText8 = "windowBackgroundWhiteGrayText8";
    public static final String key_windowBackgroundWhiteGrayLine = "windowBackgroundWhiteGrayLine";
    public static final String key_windowBackgroundWhiteBlackText = "windowBackgroundWhiteBlackText";
    public static final String key_windowBackgroundWhiteHintText = "windowBackgroundWhiteHintText";
    public static final String key_windowBackgroundWhiteValueText = "windowBackgroundWhiteValueText";
    public static final String key_windowBackgroundWhiteLinkText = "windowBackgroundWhiteLinkText";
    public static final String key_windowBackgroundWhiteLinkSelection = "windowBackgroundWhiteLinkSelection";
    public static final String key_windowBackgroundWhiteBlueHeader = "windowBackgroundWhiteBlueHeader";
    public static final String key_switchTrack = "switchTrack";
    public static final String key_switchTrackChecked = "switchTrackChecked";
    public static final String key_switchTrackBlue = "switchTrackBlue";
    public static final String key_switchTrackBlueChecked = "switchTrackBlueChecked";
    public static final String key_switchTrackBlueThumb = "switchTrackBlueThumb";
    public static final String key_switchTrackBlueThumbChecked = "switchTrackBlueThumbChecked";
    public static final String key_switchTrackBlueSelector = "switchTrackBlueSelector";
    public static final String key_switchTrackBlueSelectorChecked = "switchTrackBlueSelectorChecked";
    public static final String key_switch2Track = "switch2Track";
    public static final String key_switch2TrackChecked = "switch2TrackChecked";
    public static final String key_checkboxSquareBackground = "checkboxSquareBackground";
    public static final String key_checkboxSquareCheck = "checkboxSquareCheck";
    public static final String key_checkboxSquareUnchecked = "checkboxSquareUnchecked";
    public static final String key_checkboxSquareDisabled = "checkboxSquareDisabled";
    public static final String key_windowBackgroundGray = "windowBackgroundGray";
    public static final String key_windowBackgroundGrayShadow = "windowBackgroundGrayShadow";
    public static final String key_emptyListPlaceholder = "emptyListPlaceholder";
    public static final String key_divider = "divider";
    public static final String key_graySection = "graySection";
    public static final String key_graySectionText = "key_graySectionText";
    public static final String key_radioBackground = "radioBackground";
    public static final String key_radioBackgroundChecked = "radioBackgroundChecked";
    public static final String key_checkbox = "checkbox";
    public static final String key_checkboxDisabled = "checkboxDisabled";
    public static final String key_checkboxCheck = "checkboxCheck";
    public static final String key_fastScrollActive = "fastScrollActive";
    public static final String key_fastScrollInactive = "fastScrollInactive";
    public static final String key_fastScrollText = "fastScrollText";

    public static final String key_inappPlayerPerformer = "inappPlayerPerformer";
    public static final String key_inappPlayerTitle = "inappPlayerTitle";
    public static final String key_inappPlayerBackground = "inappPlayerBackground";
    public static final String key_inappPlayerPlayPause = "inappPlayerPlayPause";
    public static final String key_inappPlayerClose = "inappPlayerClose";

    public static final String key_returnToCallBackground = "returnToCallBackground";
    public static final String key_returnToCallText = "returnToCallText";

    public static final String key_contextProgressInner1 = "contextProgressInner1";
    public static final String key_contextProgressOuter1 = "contextProgressOuter1";
    public static final String key_contextProgressInner2 = "contextProgressInner2";
    public static final String key_contextProgressOuter2 = "contextProgressOuter2";
    public static final String key_contextProgressInner3 = "contextProgressInner3";
    public static final String key_contextProgressOuter3 = "contextProgressOuter3";
    public static final String key_contextProgressInner4 = "contextProgressInner4";
    public static final String key_contextProgressOuter4 = "contextProgressOuter4";

    public static final String key_avatar_text = "avatar_text";
    public static final String key_avatar_backgroundSaved = "avatar_backgroundSaved";
    public static final String key_avatar_backgroundArchived = "avatar_backgroundArchived";
    public static final String key_avatar_backgroundArchivedHidden = "avatar_backgroundArchivedHidden";
    public static final String key_avatar_backgroundRed = "avatar_backgroundRed";
    public static final String key_avatar_backgroundOrange = "avatar_backgroundOrange";
    public static final String key_avatar_backgroundViolet = "avatar_backgroundViolet";
    public static final String key_avatar_backgroundGreen = "avatar_backgroundGreen";
    public static final String key_avatar_backgroundCyan = "avatar_backgroundCyan";
    public static final String key_avatar_backgroundBlue = "avatar_backgroundBlue";
    public static final String key_avatar_backgroundPink = "avatar_backgroundPink";
    public static final String key_avatar_backgroundGroupCreateSpanBlue = "avatar_backgroundGroupCreateSpanBlue";

    public static final String key_avatar_backgroundInProfileBlue = "avatar_backgroundInProfileBlue";
    public static final String key_avatar_backgroundActionBarBlue = "avatar_backgroundActionBarBlue";
    public static final String key_avatar_actionBarSelectorBlue = "avatar_actionBarSelectorBlue";
    public static final String key_avatar_actionBarIconBlue = "avatar_actionBarIconBlue";
    public static final String key_avatar_subtitleInProfileBlue = "avatar_subtitleInProfileBlue";

    public static final String key_avatar_nameInMessageRed = "avatar_nameInMessageRed";
    public static final String key_avatar_nameInMessageOrange = "avatar_nameInMessageOrange";
    public static final String key_avatar_nameInMessageViolet = "avatar_nameInMessageViolet";
    public static final String key_avatar_nameInMessageGreen = "avatar_nameInMessageGreen";
    public static final String key_avatar_nameInMessageCyan = "avatar_nameInMessageCyan";
    public static final String key_avatar_nameInMessageBlue = "avatar_nameInMessageBlue";
    public static final String key_avatar_nameInMessagePink = "avatar_nameInMessagePink";

    public static String[] keys_avatar_background = {key_avatar_backgroundRed, key_avatar_backgroundOrange, key_avatar_backgroundViolet, key_avatar_backgroundGreen, key_avatar_backgroundCyan, key_avatar_backgroundBlue, key_avatar_backgroundPink};
    public static String[] keys_avatar_nameInMessage = {key_avatar_nameInMessageRed, key_avatar_nameInMessageOrange, key_avatar_nameInMessageViolet, key_avatar_nameInMessageGreen, key_avatar_nameInMessageCyan, key_avatar_nameInMessageBlue, key_avatar_nameInMessagePink};

    public static final String key_actionBarDefault = "actionBarDefault";
    public static final String key_actionBarDefaultSelector = "actionBarDefaultSelector";
    public static final String key_actionBarWhiteSelector = "actionBarWhiteSelector";
    public static final String key_actionBarDefaultIcon = "actionBarDefaultIcon";
    public static final String key_actionBarActionModeDefault = "actionBarActionModeDefault";
    public static final String key_actionBarActionModeDefaultTop = "actionBarActionModeDefaultTop";
    public static final String key_actionBarActionModeDefaultIcon = "actionBarActionModeDefaultIcon";
    public static final String key_actionBarActionModeDefaultSelector = "actionBarActionModeDefaultSelector";
    public static final String key_actionBarDefaultTitle = "actionBarDefaultTitle";
    public static final String key_actionBarDefaultSubtitle = "actionBarDefaultSubtitle";
    public static final String key_actionBarDefaultSearch = "actionBarDefaultSearch";
    public static final String key_actionBarDefaultSearchPlaceholder = "actionBarDefaultSearchPlaceholder";
    public static final String key_actionBarDefaultSubmenuItem = "actionBarDefaultSubmenuItem";
    public static final String key_actionBarDefaultSubmenuItemIcon = "actionBarDefaultSubmenuItemIcon";
    public static final String key_actionBarDefaultSubmenuBackground = "actionBarDefaultSubmenuBackground";
    public static final String key_actionBarTabActiveText = "actionBarTabActiveText";
    public static final String key_actionBarTabUnactiveText = "actionBarTabUnactiveText";
    public static final String key_actionBarTabLine = "actionBarTabLine";
    public static final String key_actionBarTabSelector = "actionBarTabSelector";
    public static final String key_actionBarDefaultArchived = "actionBarDefaultArchived";
    public static final String key_actionBarDefaultArchivedSelector = "actionBarDefaultArchivedSelector";
    public static final String key_actionBarDefaultArchivedIcon = "actionBarDefaultArchivedIcon";
    public static final String key_actionBarDefaultArchivedTitle = "actionBarDefaultArchivedTitle";
    public static final String key_actionBarDefaultArchivedSearch = "actionBarDefaultArchivedSearch";
    public static final String key_actionBarDefaultArchivedSearchPlaceholder = "actionBarDefaultSearchArchivedPlaceholder";

    public static final String key_actionBarBrowser = "actionBarBrowser";

    public static final String key_chats_onlineCircle = "chats_onlineCircle";
    public static final String key_chats_unreadCounter = "chats_unreadCounter";
    public static final String key_chats_unreadCounterMuted = "chats_unreadCounterMuted";
    public static final String key_chats_unreadCounterText = "chats_unreadCounterText";
    public static final String key_chats_name = "chats_name";
    public static final String key_chats_nameArchived = "chats_nameArchived";
    public static final String key_chats_secretName = "chats_secretName";
    public static final String key_chats_secretIcon = "chats_secretIcon";
    public static final String key_chats_nameIcon = "chats_nameIcon";
    public static final String key_chats_pinnedIcon = "chats_pinnedIcon";
    public static final String key_chats_archiveBackground = "chats_archiveBackground";
    public static final String key_chats_archivePinBackground = "chats_archivePinBackground";
    public static final String key_chats_archiveIcon = "chats_archiveIcon";
    public static final String key_chats_archiveText = "chats_archiveText";
    public static final String key_chats_message = "chats_message";
    public static final String key_chats_messageArchived = "chats_messageArchived";
    public static final String key_chats_message_threeLines = "chats_message_threeLines";
    public static final String key_chats_draft = "chats_draft";
    public static final String key_chats_nameMessage = "chats_nameMessage";
    public static final String key_chats_nameMessageArchived = "chats_nameMessageArchived";
    public static final String key_chats_nameMessage_threeLines = "chats_nameMessage_threeLines";
    public static final String key_chats_nameMessageArchived_threeLines = "chats_nameMessageArchived_threeLines";
    public static final String key_chats_attachMessage = "chats_attachMessage";
    public static final String key_chats_actionMessage = "chats_actionMessage";
    public static final String key_chats_date = "chats_date";
    public static final String key_chats_pinnedOverlay = "chats_pinnedOverlay";
    public static final String key_chats_tabletSelectedOverlay = "chats_tabletSelectedOverlay";
    public static final String key_chats_sentCheck = "chats_sentCheck";
    public static final String key_chats_sentReadCheck = "chats_sentReadCheck";
    public static final String key_chats_sentClock = "chats_sentClock";
    public static final String key_chats_sentError = "chats_sentError";
    public static final String key_chats_sentErrorIcon = "chats_sentErrorIcon";
    public static final String key_chats_verifiedBackground = "chats_verifiedBackground";
    public static final String key_chats_verifiedCheck = "chats_verifiedCheck";
    public static final String key_chats_muteIcon = "chats_muteIcon";
    public static final String key_chats_mentionIcon = "chats_mentionIcon";
    public static final String key_chats_menuTopShadow = "chats_menuTopShadow";
    public static final String key_chats_menuTopShadowCats = "chats_menuTopShadowCats";
    public static final String key_chats_menuBackground = "chats_menuBackground";
    public static final String key_chats_menuItemText = "chats_menuItemText";
    public static final String key_chats_menuItemCheck = "chats_menuItemCheck";
    public static final String key_chats_menuItemIcon = "chats_menuItemIcon";
    public static final String key_chats_menuName = "chats_menuName";
    public static final String key_chats_menuPhone = "chats_menuPhone";
    public static final String key_chats_menuPhoneCats = "chats_menuPhoneCats";
    public static final String key_chats_menuTopBackgroundCats = "chats_menuTopBackgroundCats";
    public static final String key_chats_menuTopBackground = "chats_menuTopBackground";
    public static final String key_chats_menuCloud = "chats_menuCloud";
    public static final String key_chats_menuCloudBackgroundCats = "chats_menuCloudBackgroundCats";
    public static final String key_chats_actionIcon = "chats_actionIcon";
    public static final String key_chats_actionBackground = "chats_actionBackground";
    public static final String key_chats_actionPressedBackground = "chats_actionPressedBackground";
    public static final String key_chats_actionUnreadIcon = "chats_actionUnreadIcon";
    public static final String key_chats_actionUnreadBackground = "chats_actionUnreadBackground";
    public static final String key_chats_actionUnreadPressedBackground = "chats_actionUnreadPressedBackground";

    public static final String key_chat_attachMediaBanBackground = "chat_attachMediaBanBackground";
    public static final String key_chat_attachMediaBanText = "chat_attachMediaBanText";
    public static final String key_chat_attachCheckBoxCheck = "chat_attachCheckBoxCheck";
    public static final String key_chat_attachCheckBoxBackground = "chat_attachCheckBoxBackground";
    public static final String key_chat_attachPhotoBackground = "chat_attachPhotoBackground";
    public static final String key_chat_attachActiveTab = "chat_attachActiveTab";
    public static final String key_chat_attachUnactiveTab = "chat_attachUnactiveTab";
    public static final String key_chat_attachPermissionImage = "chat_attachPermissionImage";
    public static final String key_chat_attachPermissionMark = "chat_attachPermissionMark";
    public static final String key_chat_attachPermissionText = "chat_attachPermissionText";
    public static final String key_chat_attachEmptyImage = "chat_attachEmptyImage";

    public static final String key_chat_attachGalleryBackground = "chat_attachGalleryBackground";
    public static final String key_chat_attachGalleryIcon = "chat_attachGalleryIcon";
    public static final String key_chat_attachAudioBackground = "chat_attachAudioBackground";
    public static final String key_chat_attachAudioIcon = "chat_attachAudioIcon";
    public static final String key_chat_attachFileBackground = "chat_attachFileBackground";
    public static final String key_chat_attachFileIcon = "chat_attachFileIcon";
    public static final String key_chat_attachContactBackground = "chat_attachContactBackground";
    public static final String key_chat_attachContactIcon = "chat_attachContactIcon";
    public static final String key_chat_attachLocationBackground = "chat_attachLocationBackground";
    public static final String key_chat_attachLocationIcon = "chat_attachLocationIcon";
    public static final String key_chat_attachPollBackground = "chat_attachPollBackground";
    public static final String key_chat_attachPollIcon = "chat_attachPollIcon";

    public static final String key_chat_status = "chat_status";
    public static final String key_chat_inRedCall = "chat_inUpCall";
    public static final String key_chat_inGreenCall = "chat_inDownCall";
    public static final String key_chat_outGreenCall = "chat_outUpCall";
    public static final String key_chat_inBubble = "chat_inBubble";
    public static final String key_chat_inBubbleSelected = "chat_inBubbleSelected";
    public static final String key_chat_inBubbleShadow = "chat_inBubbleShadow";
    public static final String key_chat_outBubble = "chat_outBubble";
    public static final String key_chat_outBubbleSelected = "chat_outBubbleSelected";
    public static final String key_chat_outBubbleShadow = "chat_outBubbleShadow";
    public static final String key_chat_messageTextIn = "chat_messageTextIn";
    public static final String key_chat_messageTextOut = "chat_messageTextOut";
    public static final String key_chat_messageLinkIn = "chat_messageLinkIn";
    public static final String key_chat_messageLinkOut = "chat_messageLinkOut";
    public static final String key_chat_serviceText = "chat_serviceText";
    public static final String key_chat_serviceLink = "chat_serviceLink";
    public static final String key_chat_serviceIcon = "chat_serviceIcon";
    public static final String key_chat_serviceBackground = "chat_serviceBackground";
    public static final String key_chat_serviceBackgroundSelected = "chat_serviceBackgroundSelected";
    public static final String key_chat_shareBackground = "chat_shareBackground";
    public static final String key_chat_shareBackgroundSelected = "chat_shareBackgroundSelected";
    public static final String key_chat_muteIcon = "chat_muteIcon";
    public static final String key_chat_lockIcon = "chat_lockIcon";
    public static final String key_chat_outSentCheck = "chat_outSentCheck";
    public static final String key_chat_outSentCheckSelected = "chat_outSentCheckSelected";
    public static final String key_chat_outSentCheckRead = "chat_outSentCheckRead";
    public static final String key_chat_outSentCheckReadSelected = "chat_outSentCheckReadSelected";
    public static final String key_chat_outSentClock = "chat_outSentClock";
    public static final String key_chat_outSentClockSelected = "chat_outSentClockSelected";
    public static final String key_chat_inSentClock = "chat_inSentClock";
    public static final String key_chat_inSentClockSelected = "chat_inSentClockSelected";
    public static final String key_chat_mediaSentCheck = "chat_mediaSentCheck";
    public static final String key_chat_mediaSentClock = "chat_mediaSentClock";
    public static final String key_chat_inMediaIcon = "chat_inMediaIcon";
    public static final String key_chat_outMediaIcon = "chat_outMediaIcon";
    public static final String key_chat_inMediaIconSelected = "chat_inMediaIconSelected";
    public static final String key_chat_outMediaIconSelected = "chat_outMediaIconSelected";
    public static final String key_chat_mediaTimeBackground = "chat_mediaTimeBackground";
    public static final String key_chat_outViews = "chat_outViews";
    public static final String key_chat_outViewsSelected = "chat_outViewsSelected";
    public static final String key_chat_inViews = "chat_inViews";
    public static final String key_chat_inViewsSelected = "chat_inViewsSelected";
    public static final String key_chat_mediaViews = "chat_mediaViews";
    public static final String key_chat_outMenu = "chat_outMenu";
    public static final String key_chat_outMenuSelected = "chat_outMenuSelected";
    public static final String key_chat_inMenu = "chat_inMenu";
    public static final String key_chat_inMenuSelected = "chat_inMenuSelected";
    public static final String key_chat_mediaMenu = "chat_mediaMenu";
    public static final String key_chat_outInstant = "chat_outInstant";
    public static final String key_chat_outInstantSelected = "chat_outInstantSelected";
    public static final String key_chat_inInstant = "chat_inInstant";
    public static final String key_chat_inInstantSelected = "chat_inInstantSelected";
    public static final String key_chat_sentError = "chat_sentError";
    public static final String key_chat_sentErrorIcon = "chat_sentErrorIcon";
    public static final String key_chat_selectedBackground = "chat_selectedBackground";
    public static final String key_chat_previewDurationText = "chat_previewDurationText";
    public static final String key_chat_previewGameText = "chat_previewGameText";
    public static final String key_chat_inPreviewInstantText = "chat_inPreviewInstantText";
    public static final String key_chat_outPreviewInstantText = "chat_outPreviewInstantText";
    public static final String key_chat_inPreviewInstantSelectedText = "chat_inPreviewInstantSelectedText";
    public static final String key_chat_outPreviewInstantSelectedText = "chat_outPreviewInstantSelectedText";
    public static final String key_chat_secretTimeText = "chat_secretTimeText";
    public static final String key_chat_stickerNameText = "chat_stickerNameText";
    public static final String key_chat_botButtonText = "chat_botButtonText";
    public static final String key_chat_botProgress = "chat_botProgress";
    public static final String key_chat_inForwardedNameText = "chat_inForwardedNameText";
    public static final String key_chat_outForwardedNameText = "chat_outForwardedNameText";
    public static final String key_chat_inViaBotNameText = "chat_inViaBotNameText";
    public static final String key_chat_outViaBotNameText = "chat_outViaBotNameText";
    public static final String key_chat_stickerViaBotNameText = "chat_stickerViaBotNameText";
    public static final String key_chat_inReplyLine = "chat_inReplyLine";
    public static final String key_chat_outReplyLine = "chat_outReplyLine";
    public static final String key_chat_stickerReplyLine = "chat_stickerReplyLine";
    public static final String key_chat_inReplyNameText = "chat_inReplyNameText";
    public static final String key_chat_outReplyNameText = "chat_outReplyNameText";
    public static final String key_chat_stickerReplyNameText = "chat_stickerReplyNameText";
    public static final String key_chat_inReplyMessageText = "chat_inReplyMessageText";
    public static final String key_chat_outReplyMessageText = "chat_outReplyMessageText";
    public static final String key_chat_inReplyMediaMessageText = "chat_inReplyMediaMessageText";
    public static final String key_chat_outReplyMediaMessageText = "chat_outReplyMediaMessageText";
    public static final String key_chat_inReplyMediaMessageSelectedText = "chat_inReplyMediaMessageSelectedText";
    public static final String key_chat_outReplyMediaMessageSelectedText = "chat_outReplyMediaMessageSelectedText";
    public static final String key_chat_stickerReplyMessageText = "chat_stickerReplyMessageText";
    public static final String key_chat_inPreviewLine = "chat_inPreviewLine";
    public static final String key_chat_outPreviewLine = "chat_outPreviewLine";
    public static final String key_chat_inSiteNameText = "chat_inSiteNameText";
    public static final String key_chat_outSiteNameText = "chat_outSiteNameText";
    public static final String key_chat_inContactNameText = "chat_inContactNameText";
    public static final String key_chat_outContactNameText = "chat_outContactNameText";
    public static final String key_chat_inContactPhoneText = "chat_inContactPhoneText";
    public static final String key_chat_inContactPhoneSelectedText = "chat_inContactPhoneSelectedText";
    public static final String key_chat_outContactPhoneText = "chat_outContactPhoneText";
    public static final String key_chat_outContactPhoneSelectedText = "chat_outContactPhoneSelectedText";
    public static final String key_chat_mediaProgress = "chat_mediaProgress";
    public static final String key_chat_inAudioProgress = "chat_inAudioProgress";
    public static final String key_chat_outAudioProgress = "chat_outAudioProgress";
    public static final String key_chat_inAudioSelectedProgress = "chat_inAudioSelectedProgress";
    public static final String key_chat_outAudioSelectedProgress = "chat_outAudioSelectedProgress";
    public static final String key_chat_mediaTimeText = "chat_mediaTimeText";
    public static final String key_chat_adminText = "chat_adminText";
    public static final String key_chat_adminSelectedText = "chat_adminSelectedText";
    public static final String key_chat_inTimeText = "chat_inTimeText";
    public static final String key_chat_outTimeText = "chat_outTimeText";
    public static final String key_chat_inTimeSelectedText = "chat_inTimeSelectedText";
    public static final String key_chat_outTimeSelectedText = "chat_outTimeSelectedText";
    public static final String key_chat_inAudioPerformerText = "chat_inAudioPerfomerText";
    public static final String key_chat_inAudioPerformerSelectedText = "chat_inAudioPerfomerSelectedText";
    public static final String key_chat_outAudioPerformerText = "chat_outAudioPerfomerText";
    public static final String key_chat_outAudioPerformerSelectedText = "chat_outAudioPerfomerSelectedText";
    public static final String key_chat_inAudioTitleText = "chat_inAudioTitleText";
    public static final String key_chat_outAudioTitleText = "chat_outAudioTitleText";
    public static final String key_chat_inAudioDurationText = "chat_inAudioDurationText";
    public static final String key_chat_outAudioDurationText = "chat_outAudioDurationText";
    public static final String key_chat_inAudioDurationSelectedText = "chat_inAudioDurationSelectedText";
    public static final String key_chat_outAudioDurationSelectedText = "chat_outAudioDurationSelectedText";
    public static final String key_chat_inAudioSeekbar = "chat_inAudioSeekbar";
    public static final String key_chat_inAudioCacheSeekbar = "chat_inAudioCacheSeekbar";
    public static final String key_chat_outAudioSeekbar = "chat_outAudioSeekbar";
    public static final String key_chat_outAudioCacheSeekbar = "chat_outAudioCacheSeekbar";
    public static final String key_chat_inAudioSeekbarSelected = "chat_inAudioSeekbarSelected";
    public static final String key_chat_outAudioSeekbarSelected = "chat_outAudioSeekbarSelected";
    public static final String key_chat_inAudioSeekbarFill = "chat_inAudioSeekbarFill";
    public static final String key_chat_outAudioSeekbarFill = "chat_outAudioSeekbarFill";
    public static final String key_chat_inVoiceSeekbar = "chat_inVoiceSeekbar";
    public static final String key_chat_outVoiceSeekbar = "chat_outVoiceSeekbar";
    public static final String key_chat_inVoiceSeekbarSelected = "chat_inVoiceSeekbarSelected";
    public static final String key_chat_outVoiceSeekbarSelected = "chat_outVoiceSeekbarSelected";
    public static final String key_chat_inVoiceSeekbarFill = "chat_inVoiceSeekbarFill";
    public static final String key_chat_outVoiceSeekbarFill = "chat_outVoiceSeekbarFill";
    public static final String key_chat_inFileProgress = "chat_inFileProgress";
    public static final String key_chat_outFileProgress = "chat_outFileProgress";
    public static final String key_chat_inFileProgressSelected = "chat_inFileProgressSelected";
    public static final String key_chat_outFileProgressSelected = "chat_outFileProgressSelected";
    public static final String key_chat_inFileNameText = "chat_inFileNameText";
    public static final String key_chat_outFileNameText = "chat_outFileNameText";
    public static final String key_chat_inFileInfoText = "chat_inFileInfoText";
    public static final String key_chat_outFileInfoText = "chat_outFileInfoText";
    public static final String key_chat_inFileInfoSelectedText = "chat_inFileInfoSelectedText";
    public static final String key_chat_outFileInfoSelectedText = "chat_outFileInfoSelectedText";
    public static final String key_chat_inFileBackground = "chat_inFileBackground";
    public static final String key_chat_outFileBackground = "chat_outFileBackground";
    public static final String key_chat_inFileBackgroundSelected = "chat_inFileBackgroundSelected";
    public static final String key_chat_outFileBackgroundSelected = "chat_outFileBackgroundSelected";
    public static final String key_chat_inVenueInfoText = "chat_inVenueInfoText";
    public static final String key_chat_outVenueInfoText = "chat_outVenueInfoText";
    public static final String key_chat_inVenueInfoSelectedText = "chat_inVenueInfoSelectedText";
    public static final String key_chat_outVenueInfoSelectedText = "chat_outVenueInfoSelectedText";
    public static final String key_chat_mediaInfoText = "chat_mediaInfoText";
    public static final String key_chat_linkSelectBackground = "chat_linkSelectBackground";
    public static final String key_chat_textSelectBackground = "chat_textSelectBackground";
    public static final String key_chat_wallpaper = "chat_wallpaper";
    public static final String key_chat_wallpaper_gradient_to = "chat_wallpaper_gradient_to";
    public static final String key_chat_messagePanelBackground = "chat_messagePanelBackground";
    public static final String key_chat_messagePanelShadow = "chat_messagePanelShadow";
    public static final String key_chat_messagePanelText = "chat_messagePanelText";
    public static final String key_chat_messagePanelHint = "chat_messagePanelHint";
    public static final String key_chat_messagePanelCursor = "chat_messagePanelCursor";
    public static final String key_chat_messagePanelIcons = "chat_messagePanelIcons";
    public static final String key_chat_messagePanelSend = "chat_messagePanelSend";
    public static final String key_chat_messagePanelSendPressed = "chat_messagePanelPressedSend";
    public static final String key_chat_messagePanelVoiceLock = "key_chat_messagePanelVoiceLock";
    public static final String key_chat_messagePanelVoiceLockBackground = "key_chat_messagePanelVoiceLockBackground";
    public static final String key_chat_messagePanelVoiceLockShadow = "key_chat_messagePanelVoiceLockShadow";
    public static final String key_chat_messagePanelVideoFrame = "chat_messagePanelVideoFrame";
    public static final String key_chat_topPanelBackground = "chat_topPanelBackground";
    public static final String key_chat_topPanelClose = "chat_topPanelClose";
    public static final String key_chat_topPanelLine = "chat_topPanelLine";
    public static final String key_chat_topPanelTitle = "chat_topPanelTitle";
    public static final String key_chat_topPanelMessage = "chat_topPanelMessage";
    public static final String key_chat_reportSpam = "chat_reportSpam";
    public static final String key_chat_addContact = "chat_addContact";
    public static final String key_chat_inLoader = "chat_inLoader";
    public static final String key_chat_inLoaderSelected = "chat_inLoaderSelected";
    public static final String key_chat_outLoader = "chat_outLoader";
    public static final String key_chat_outLoaderSelected = "chat_outLoaderSelected";
    public static final String key_chat_inLoaderPhoto = "chat_inLoaderPhoto";
    public static final String key_chat_inLoaderPhotoSelected = "chat_inLoaderPhotoSelected";
    public static final String key_chat_inLoaderPhotoIcon = "chat_inLoaderPhotoIcon";
    public static final String key_chat_inLoaderPhotoIconSelected = "chat_inLoaderPhotoIconSelected";
    public static final String key_chat_outLoaderPhoto = "chat_outLoaderPhoto";
    public static final String key_chat_outLoaderPhotoSelected = "chat_outLoaderPhotoSelected";
    public static final String key_chat_outLoaderPhotoIcon = "chat_outLoaderPhotoIcon";
    public static final String key_chat_outLoaderPhotoIconSelected = "chat_outLoaderPhotoIconSelected";
    public static final String key_chat_mediaLoaderPhoto = "chat_mediaLoaderPhoto";
    public static final String key_chat_mediaLoaderPhotoSelected = "chat_mediaLoaderPhotoSelected";
    public static final String key_chat_mediaLoaderPhotoIcon = "chat_mediaLoaderPhotoIcon";
    public static final String key_chat_mediaLoaderPhotoIconSelected = "chat_mediaLoaderPhotoIconSelected";
    public static final String key_chat_inLocationBackground = "chat_inLocationBackground";
    public static final String key_chat_inLocationIcon = "chat_inLocationIcon";
    public static final String key_chat_outLocationBackground = "chat_outLocationBackground";
    public static final String key_chat_outLocationIcon = "chat_outLocationIcon";
    public static final String key_chat_inContactBackground = "chat_inContactBackground";
    public static final String key_chat_inContactIcon = "chat_inContactIcon";
    public static final String key_chat_outContactBackground = "chat_outContactBackground";
    public static final String key_chat_outContactIcon = "chat_outContactIcon";
    public static final String key_chat_inFileIcon = "chat_inFileIcon";
    public static final String key_chat_inFileSelectedIcon = "chat_inFileSelectedIcon";
    public static final String key_chat_outFileIcon = "chat_outFileIcon";
    public static final String key_chat_outFileSelectedIcon = "chat_outFileSelectedIcon";
    public static final String key_chat_replyPanelIcons = "chat_replyPanelIcons";
    public static final String key_chat_replyPanelClose = "chat_replyPanelClose";
    public static final String key_chat_replyPanelName = "chat_replyPanelName";
    public static final String key_chat_replyPanelMessage = "chat_replyPanelMessage";
    public static final String key_chat_replyPanelLine = "chat_replyPanelLine";
    public static final String key_chat_searchPanelIcons = "chat_searchPanelIcons";
    public static final String key_chat_searchPanelText = "chat_searchPanelText";
    public static final String key_chat_secretChatStatusText = "chat_secretChatStatusText";
    public static final String key_chat_fieldOverlayText = "chat_fieldOverlayText";
    public static final String key_chat_stickersHintPanel = "chat_stickersHintPanel";
    public static final String key_chat_botSwitchToInlineText = "chat_botSwitchToInlineText";
    public static final String key_chat_unreadMessagesStartArrowIcon = "chat_unreadMessagesStartArrowIcon";
    public static final String key_chat_unreadMessagesStartText = "chat_unreadMessagesStartText";
    public static final String key_chat_unreadMessagesStartBackground = "chat_unreadMessagesStartBackground";
    public static final String key_chat_inlineResultIcon = "chat_inlineResultIcon";
    public static final String key_chat_emojiPanelBackground = "chat_emojiPanelBackground";
    public static final String key_chat_emojiPanelBadgeBackground = "chat_emojiPanelBadgeBackground";
    public static final String key_chat_emojiPanelBadgeText = "chat_emojiPanelBadgeText";
    public static final String key_chat_emojiSearchBackground = "chat_emojiSearchBackground";
    public static final String key_chat_emojiSearchIcon = "chat_emojiSearchIcon";
    public static final String key_chat_emojiPanelShadowLine = "chat_emojiPanelShadowLine";
    public static final String key_chat_emojiPanelEmptyText = "chat_emojiPanelEmptyText";
    public static final String key_chat_emojiPanelIcon = "chat_emojiPanelIcon";
    public static final String key_chat_emojiBottomPanelIcon = "chat_emojiBottomPanelIcon";
    public static final String key_chat_emojiPanelIconSelected = "chat_emojiPanelIconSelected";
    public static final String key_chat_emojiPanelStickerPackSelector = "chat_emojiPanelStickerPackSelector";
    public static final String key_chat_emojiPanelStickerPackSelectorLine = "chat_emojiPanelStickerPackSelectorLine";
    public static final String key_chat_emojiPanelBackspace = "chat_emojiPanelBackspace";
    public static final String key_chat_emojiPanelMasksIcon = "chat_emojiPanelMasksIcon";
    public static final String key_chat_emojiPanelMasksIconSelected = "chat_emojiPanelMasksIconSelected";
    public static final String key_chat_emojiPanelTrendingTitle = "chat_emojiPanelTrendingTitle";
    public static final String key_chat_emojiPanelStickerSetName = "chat_emojiPanelStickerSetName";
    public static final String key_chat_emojiPanelStickerSetNameHighlight = "chat_emojiPanelStickerSetNameHighlight";
    public static final String key_chat_emojiPanelStickerSetNameIcon = "chat_emojiPanelStickerSetNameIcon";
    public static final String key_chat_emojiPanelTrendingDescription = "chat_emojiPanelTrendingDescription";
    public static final String key_chat_botKeyboardButtonText = "chat_botKeyboardButtonText";
    public static final String key_chat_botKeyboardButtonBackground = "chat_botKeyboardButtonBackground";
    public static final String key_chat_botKeyboardButtonBackgroundPressed = "chat_botKeyboardButtonBackgroundPressed";
    public static final String key_chat_emojiPanelNewTrending = "chat_emojiPanelNewTrending";
    public static final String key_chat_messagePanelVoicePressed = "chat_messagePanelVoicePressed";
    public static final String key_chat_messagePanelVoiceBackground = "chat_messagePanelVoiceBackground";
    public static final String key_chat_messagePanelVoiceShadow = "chat_messagePanelVoiceShadow";
    public static final String key_chat_messagePanelVoiceDelete = "chat_messagePanelVoiceDelete";
    public static final String key_chat_messagePanelVoiceDuration = "chat_messagePanelVoiceDuration";
    public static final String key_chat_recordedVoicePlayPause = "chat_recordedVoicePlayPause";
    public static final String key_chat_recordedVoicePlayPausePressed = "chat_recordedVoicePlayPausePressed";
    public static final String key_chat_recordedVoiceProgress = "chat_recordedVoiceProgress";
    public static final String key_chat_recordedVoiceProgressInner = "chat_recordedVoiceProgressInner";
    public static final String key_chat_recordedVoiceDot = "chat_recordedVoiceDot";
    public static final String key_chat_recordedVoiceBackground = "chat_recordedVoiceBackground";
    public static final String key_chat_recordVoiceCancel = "chat_recordVoiceCancel";
    public static final String key_chat_recordTime = "chat_recordTime";
    public static final String key_chat_messagePanelCancelInlineBot = "chat_messagePanelCancelInlineBot";
    public static final String key_chat_gifSaveHintText = "chat_gifSaveHintText";
    public static final String key_chat_gifSaveHintBackground = "chat_gifSaveHintBackground";
    public static final String key_chat_goDownButton = "chat_goDownButton";
    public static final String key_chat_goDownButtonShadow = "chat_goDownButtonShadow";
    public static final String key_chat_goDownButtonIcon = "chat_goDownButtonIcon";
    public static final String key_chat_goDownButtonCounter = "chat_goDownButtonCounter";
    public static final String key_chat_goDownButtonCounterBackground = "chat_goDownButtonCounterBackground";
    public static final String key_chat_secretTimerBackground = "chat_secretTimerBackground";
    public static final String key_chat_secretTimerText = "chat_secretTimerText";

    public static final String key_passport_authorizeBackground = "passport_authorizeBackground";
    public static final String key_passport_authorizeBackgroundSelected = "passport_authorizeBackgroundSelected";
    public static final String key_passport_authorizeText = "passport_authorizeText";

    public static final String key_profile_creatorIcon = "profile_creatorIcon";
    public static final String key_profile_title = "profile_title";
    public static final String key_profile_actionIcon = "profile_actionIcon";
    public static final String key_profile_actionBackground = "profile_actionBackground";
    public static final String key_profile_actionPressedBackground = "profile_actionPressedBackground";
    public static final String key_profile_verifiedBackground = "profile_verifiedBackground";
    public static final String key_profile_verifiedCheck = "profile_verifiedCheck";
    public static final String key_profile_status = "profile_status";

    public static final String key_sharedMedia_startStopLoadIcon = "sharedMedia_startStopLoadIcon";
    public static final String key_sharedMedia_linkPlaceholder = "sharedMedia_linkPlaceholder";
    public static final String key_sharedMedia_linkPlaceholderText = "sharedMedia_linkPlaceholderText";
    public static final String key_sharedMedia_photoPlaceholder = "sharedMedia_photoPlaceholder";
    public static final String key_sharedMedia_actionMode = "sharedMedia_actionMode";

    public static final String key_featuredStickers_addedIcon = "featuredStickers_addedIcon";
    public static final String key_featuredStickers_buttonProgress = "featuredStickers_buttonProgress";
    public static final String key_featuredStickers_addButton = "featuredStickers_addButton";
    public static final String key_featuredStickers_addButtonPressed = "featuredStickers_addButtonPressed";
    public static final String key_featuredStickers_delButton = "featuredStickers_delButton";
    public static final String key_featuredStickers_delButtonPressed = "featuredStickers_delButtonPressed";
    public static final String key_featuredStickers_buttonText = "featuredStickers_buttonText";
    public static final String key_featuredStickers_unread = "featuredStickers_unread";

    public static final String key_stickers_menu = "stickers_menu";
    public static final String key_stickers_menuSelector = "stickers_menuSelector";

    public static final String key_changephoneinfo_image = "changephoneinfo_image";
    public static final String key_changephoneinfo_image2 = "changephoneinfo_image2";

    public static final String key_groupcreate_hintText = "groupcreate_hintText";
    public static final String key_groupcreate_cursor = "groupcreate_cursor";
    public static final String key_groupcreate_sectionShadow = "groupcreate_sectionShadow";
    public static final String key_groupcreate_sectionText = "groupcreate_sectionText";
    public static final String key_groupcreate_spanText = "groupcreate_spanText";
    public static final String key_groupcreate_spanBackground = "groupcreate_spanBackground";
    public static final String key_groupcreate_spanDelete = "groupcreate_spanDelete";

    public static final String key_contacts_inviteBackground = "contacts_inviteBackground";
    public static final String key_contacts_inviteText = "contacts_inviteText";

    public static final String key_login_progressInner = "login_progressInner";
    public static final String key_login_progressOuter = "login_progressOuter";

    public static final String key_musicPicker_checkbox = "musicPicker_checkbox";
    public static final String key_musicPicker_checkboxCheck = "musicPicker_checkboxCheck";
    public static final String key_musicPicker_buttonBackground = "musicPicker_buttonBackground";
    public static final String key_musicPicker_buttonIcon = "musicPicker_buttonIcon";

    public static final String key_picker_enabledButton = "picker_enabledButton";
    public static final String key_picker_disabledButton = "picker_disabledButton";
    public static final String key_picker_badge = "picker_badge";
    public static final String key_picker_badgeText = "picker_badgeText";

    public static final String key_location_sendLocationBackground = "location_sendLocationBackground";
    public static final String key_location_sendLiveLocationBackground = "location_sendLiveLocationBackground";
    public static final String key_location_sendLocationIcon = "location_sendLocationIcon";
    public static final String key_location_sendLiveLocationIcon = "location_sendLiveLocationIcon";
    public static final String key_location_liveLocationProgress = "location_liveLocationProgress";
    public static final String key_location_placeLocationBackground = "location_placeLocationBackground";
    public static final String key_dialog_liveLocationProgress = "dialog_liveLocationProgress";

    public static final String key_files_folderIcon = "files_folderIcon";
    public static final String key_files_folderIconBackground = "files_folderIconBackground";
    public static final String key_files_iconText = "files_iconText";

    public static final String key_sessions_devicesImage = "sessions_devicesImage";

    public static final String key_calls_callReceivedGreenIcon = "calls_callReceivedGreenIcon";
    public static final String key_calls_callReceivedRedIcon = "calls_callReceivedRedIcon";

    public static final String key_undo_background = "undo_background";
    public static final String key_undo_cancelColor = "undo_cancelColor";
    public static final String key_undo_infoColor = "undo_infoColor";

    public static final String key_sheet_scrollUp = "key_sheet_scrollUp";
    public static final String key_sheet_other = "key_sheet_other";

    //ununsed
    public static final String key_chat_outBroadcast = "chat_outBroadcast";
    public static final String key_chat_mediaBroadcast = "chat_mediaBroadcast";

    public static final String key_player_actionBar = "player_actionBar";
    public static final String key_player_actionBarSelector = "player_actionBarSelector";
    public static final String key_player_actionBarTitle = "player_actionBarTitle";
    public static final String key_player_actionBarTop = "player_actionBarTop";
    public static final String key_player_actionBarSubtitle = "player_actionBarSubtitle";
    public static final String key_player_actionBarItems = "player_actionBarItems";
    public static final String key_player_background = "player_background";
    public static final String key_player_time = "player_time";
    public static final String key_player_progressBackground = "player_progressBackground";
    public static final String key_player_progressCachedBackground = "key_player_progressCachedBackground";
    public static final String key_player_progress = "player_progress";
    public static final String key_player_placeholder = "player_placeholder";
    public static final String key_player_placeholderBackground = "player_placeholderBackground";
    public static final String key_player_button = "player_button";
    public static final String key_player_buttonActive = "player_buttonActive";

    private static HashMap<String, Integer> defaultColors = new HashMap<>();
    private static HashMap<String, String> fallbackKeys = new HashMap<>();
    private static HashSet<String> themeAccentExclusionKeys = new HashSet<>();
    private static HashMap<String, Integer> currentColorsNoAccent;
    private static HashMap<String, Integer> currentColors;
    private static HashMap<String, Integer> animatingColors;

    private static float[] hsv = new float[3];

    static {
        defaultColors.put(key_dialogBackground, 0xffffffff);
        defaultColors.put(key_dialogBackgroundGray, 0xfff0f0f0);
        defaultColors.put(key_dialogTextBlack, 0xff222222);
        defaultColors.put(key_dialogTextLink, 0xff2678b6);
        defaultColors.put(key_dialogLinkSelection, 0x3362a9e3);
        defaultColors.put(key_dialogTextRed, 0xffcd5a5a);
        defaultColors.put(key_dialogTextRed2, 0xffde3a3a);
        defaultColors.put(key_dialogTextBlue, 0xff2f8cc9);
        defaultColors.put(key_dialogTextBlue2, 0xff3a95d5);
        defaultColors.put(key_dialogTextBlue3, 0xff3ec1f9);
        defaultColors.put(key_dialogTextBlue4, 0xff19a7e8);
        defaultColors.put(key_dialogTextGray, 0xff348bc1);
        defaultColors.put(key_dialogTextGray2, 0xff757575);
        defaultColors.put(key_dialogTextGray3, 0xff999999);
        defaultColors.put(key_dialogTextGray4, 0xffb3b3b3);
        defaultColors.put(key_dialogTextHint, 0xff979797);
        defaultColors.put(key_dialogIcon, 0xff676b70);
        defaultColors.put(key_dialogRedIcon, 0xffe14d4d);
        defaultColors.put(key_dialogGrayLine, 0xffd2d2d2);
        defaultColors.put(key_dialogTopBackground, 0xff6fb2e5);
        defaultColors.put(key_dialogInputField, 0xffdbdbdb);
        defaultColors.put(key_dialogInputFieldActivated, 0xff37a9f0);
        defaultColors.put(key_dialogCheckboxSquareBackground, 0xff43a0df);
        defaultColors.put(key_dialogCheckboxSquareCheck, 0xffffffff);
        defaultColors.put(key_dialogCheckboxSquareUnchecked, 0xff737373);
        defaultColors.put(key_dialogCheckboxSquareDisabled, 0xffb0b0b0);
        defaultColors.put(key_dialogRadioBackground, 0xffb3b3b3);
        defaultColors.put(key_dialogRadioBackgroundChecked, 0xff37a9f0);
        defaultColors.put(key_dialogProgressCircle, 0xff527da3);
        defaultColors.put(key_dialogLineProgress, 0xff527da3);
        defaultColors.put(key_dialogLineProgressBackground, 0xffdbdbdb);
        defaultColors.put(key_dialogButton, 0xff4991cc);
        defaultColors.put(key_dialogButtonSelector, 0x0f000000);
        defaultColors.put(key_dialogScrollGlow, 0xfff5f6f7);
        defaultColors.put(key_dialogRoundCheckBox, 0xff4cb4f5);
        defaultColors.put(key_dialogRoundCheckBoxCheck, 0xffffffff);
        defaultColors.put(key_dialogBadgeBackground, 0xff3ec1f9);
        defaultColors.put(key_dialogBadgeText, 0xffffffff);
        defaultColors.put(key_dialogCameraIcon, 0xffffffff);
        defaultColors.put(key_dialog_inlineProgressBackground, 0xf6f0f2f5);
        defaultColors.put(key_dialog_inlineProgress, 0xff6b7378);
        defaultColors.put(key_dialogSearchBackground, 0xfff2f4f5);
        defaultColors.put(key_dialogSearchHint, 0xff98a0a7);
        defaultColors.put(key_dialogSearchIcon, 0xffa1a8af);
        defaultColors.put(key_dialogSearchText, 0xff222222);
        defaultColors.put(key_dialogFloatingButton, 0xff4cb4f5);
        defaultColors.put(key_dialogFloatingButtonPressed, 0xff4cb4f5);
        defaultColors.put(key_dialogFloatingIcon, 0xffffffff);
        defaultColors.put(key_dialogShadowLine, 0x12000000);

        defaultColors.put(key_windowBackgroundWhite, 0xffffffff);
        defaultColors.put(key_windowBackgroundUnchecked, 0xff9da7b1);
        defaultColors.put(key_windowBackgroundChecked, 0xff579ed9);
        defaultColors.put(key_windowBackgroundCheckText, 0xffffffff);
        defaultColors.put(key_progressCircle, 0xff527da3);
        defaultColors.put(key_windowBackgroundWhiteGrayIcon, 0xff81868b);
        defaultColors.put(key_windowBackgroundWhiteBlueText, 0xff4092cd);
        defaultColors.put(key_windowBackgroundWhiteBlueText2, 0xff3a95d5);
        defaultColors.put(key_windowBackgroundWhiteBlueText3, 0xff2678b6);
        defaultColors.put(key_windowBackgroundWhiteBlueText4, 0xff4d83b3);
        defaultColors.put(key_windowBackgroundWhiteBlueText5, 0xff4c8eca);
        defaultColors.put(key_windowBackgroundWhiteBlueText6, 0xff3a8ccf);
        defaultColors.put(key_windowBackgroundWhiteBlueText7, 0xff377aae);
        defaultColors.put(key_windowBackgroundWhiteBlueButton, 0xff1e88d3);
        defaultColors.put(key_windowBackgroundWhiteBlueIcon, 0xff379de5);
        defaultColors.put(key_windowBackgroundWhiteGreenText, 0xff26972c);
        defaultColors.put(key_windowBackgroundWhiteGreenText2, 0xff37a818);
        defaultColors.put(key_windowBackgroundWhiteRedText, 0xffcd5a5a);
        defaultColors.put(key_windowBackgroundWhiteRedText2, 0xffdb5151);
        defaultColors.put(key_windowBackgroundWhiteRedText3, 0xffd24949);
        defaultColors.put(key_windowBackgroundWhiteRedText4, 0xffcf3030);
        defaultColors.put(key_windowBackgroundWhiteRedText5, 0xffed3939);
        defaultColors.put(key_windowBackgroundWhiteRedText6, 0xffff6666);
        defaultColors.put(key_windowBackgroundWhiteGrayText, 0xff838c96);
        defaultColors.put(key_windowBackgroundWhiteGrayText2, 0xff8a8a8a);
        defaultColors.put(key_windowBackgroundWhiteGrayText3, 0xff999999);
        defaultColors.put(key_windowBackgroundWhiteGrayText4, 0xff808080);
        defaultColors.put(key_windowBackgroundWhiteGrayText5, 0xffa3a3a3);
        defaultColors.put(key_windowBackgroundWhiteGrayText6, 0xff757575);
        defaultColors.put(key_windowBackgroundWhiteGrayText7, 0xffc6c6c6);
        defaultColors.put(key_windowBackgroundWhiteGrayText8, 0xff6d6d72);
        defaultColors.put(key_windowBackgroundWhiteGrayLine, 0xffdbdbdb);
        defaultColors.put(key_windowBackgroundWhiteBlackText, 0xff222222);
        defaultColors.put(key_windowBackgroundWhiteHintText, 0xffa8a8a8);
        defaultColors.put(key_windowBackgroundWhiteValueText, 0xff3a95d5);
        defaultColors.put(key_windowBackgroundWhiteLinkText, 0xff2678b6);
        defaultColors.put(key_windowBackgroundWhiteLinkSelection, 0x3362a9e3);
        defaultColors.put(key_windowBackgroundWhiteBlueHeader, 0xff3a95d5);
        defaultColors.put(key_windowBackgroundWhiteInputField, 0xffdbdbdb);
        defaultColors.put(key_windowBackgroundWhiteInputFieldActivated, 0xff37a9f0);
        defaultColors.put(key_switchTrack, 0xffb0b5ba);
        defaultColors.put(key_switchTrackChecked, 0xff52ade9);
        defaultColors.put(key_switchTrackBlue, 0xff828e99);
        defaultColors.put(key_switchTrackBlueChecked, 0xff3c88c7);
        defaultColors.put(key_switchTrackBlueThumb, 0xffffffff);
        defaultColors.put(key_switchTrackBlueThumbChecked, 0xffffffff);
        defaultColors.put(key_switchTrackBlueSelector, 0x17404a53);
        defaultColors.put(key_switchTrackBlueSelectorChecked, 0x21024781);
        defaultColors.put(key_switch2Track, 0xfff57e7e);
        defaultColors.put(key_switch2TrackChecked, 0xff52ade9);
        defaultColors.put(key_checkboxSquareBackground, 0xff43a0df);
        defaultColors.put(key_checkboxSquareCheck, 0xffffffff);
        defaultColors.put(key_checkboxSquareUnchecked, 0xff737373);
        defaultColors.put(key_checkboxSquareDisabled, 0xffb0b0b0);
        defaultColors.put(key_listSelector, 0x0f000000);
        defaultColors.put(key_radioBackground, 0xffb3b3b3);
        defaultColors.put(key_radioBackgroundChecked, 0xff37a9f0);
        defaultColors.put(key_windowBackgroundGray, 0xfff0f0f0);
        defaultColors.put(key_windowBackgroundGrayShadow, 0xff000000);
        defaultColors.put(key_emptyListPlaceholder, 0xff959595);
        defaultColors.put(key_divider, 0xffd9d9d9);
        defaultColors.put(key_graySection, 0xffeef3f5);
        defaultColors.put(key_graySectionText, 0xff7f8991);
        defaultColors.put(key_contextProgressInner1, 0xffbfdff6);
        defaultColors.put(key_contextProgressOuter1, 0xff2b96e2);
        defaultColors.put(key_contextProgressInner2, 0xffbfdff6);
        defaultColors.put(key_contextProgressOuter2, 0xffffffff);
        defaultColors.put(key_contextProgressInner3, 0xffb3b3b3);
        defaultColors.put(key_contextProgressOuter3, 0xffffffff);
        defaultColors.put(key_contextProgressInner4, 0xffcacdd0);
        defaultColors.put(key_contextProgressOuter4, 0xff2f3438);
        defaultColors.put(key_fastScrollActive, 0xff52a3db);
        defaultColors.put(key_fastScrollInactive, 0xffc9cdd1);
        defaultColors.put(key_fastScrollText, 0xffffffff);

        defaultColors.put(key_avatar_text, 0xffffffff);

        defaultColors.put(key_avatar_backgroundSaved, 0xff66bffa);
        defaultColors.put(key_avatar_backgroundArchived, 0xffa9b6c1);
        defaultColors.put(key_avatar_backgroundArchivedHidden, 0xffc6c9cc);
        defaultColors.put(key_avatar_backgroundRed, 0xffe56555);
        defaultColors.put(key_avatar_backgroundOrange, 0xfff28c48);
        defaultColors.put(key_avatar_backgroundViolet, 0xff8e85ee);
        defaultColors.put(key_avatar_backgroundGreen, 0xff76c84d);
        defaultColors.put(key_avatar_backgroundCyan, 0xff5fbed5);
        defaultColors.put(key_avatar_backgroundBlue, 0xff549cdd);
        defaultColors.put(key_avatar_backgroundPink, 0xfff2749a);
        defaultColors.put(key_avatar_backgroundGroupCreateSpanBlue, 0xffe6eff7);

        defaultColors.put(key_avatar_backgroundInProfileBlue, 0xff5085b1);
        defaultColors.put(key_avatar_backgroundActionBarBlue, 0xff598fba);
        defaultColors.put(key_avatar_subtitleInProfileBlue, 0xffd7eafa);
        defaultColors.put(key_avatar_actionBarSelectorBlue, 0xff4981ad);
        defaultColors.put(key_avatar_actionBarIconBlue, 0xffffffff);

        defaultColors.put(key_avatar_nameInMessageRed, 0xffca5650);
        defaultColors.put(key_avatar_nameInMessageOrange, 0xffd87b29);
        defaultColors.put(key_avatar_nameInMessageViolet, 0xff4e92cc);
        defaultColors.put(key_avatar_nameInMessageGreen, 0xff50b232);
        defaultColors.put(key_avatar_nameInMessageCyan, 0xff379eb8);
        defaultColors.put(key_avatar_nameInMessageBlue, 0xff4e92cc);
        defaultColors.put(key_avatar_nameInMessagePink, 0xff4e92cc);

        defaultColors.put(key_actionBarDefault, 0xff527da3);
        defaultColors.put(key_actionBarDefaultIcon, 0xffffffff);
        defaultColors.put(key_actionBarActionModeDefault, 0xffffffff);
        defaultColors.put(key_actionBarActionModeDefaultTop, 0x10000000);
        defaultColors.put(key_actionBarActionModeDefaultIcon, 0xff676a6f);
        defaultColors.put(key_actionBarDefaultTitle, 0xffffffff);
        defaultColors.put(key_actionBarDefaultSubtitle, 0xffd5e8f7);
        defaultColors.put(key_actionBarDefaultSelector, 0xff406d94);
        defaultColors.put(key_actionBarWhiteSelector, 0x2f000000);
        defaultColors.put(key_actionBarDefaultSearch, 0xffffffff);
        defaultColors.put(key_actionBarDefaultSearchPlaceholder, 0x88ffffff);
        defaultColors.put(key_actionBarDefaultSubmenuItem, 0xff222222);
        defaultColors.put(key_actionBarDefaultSubmenuItemIcon, 0xff676b70);
        defaultColors.put(key_actionBarDefaultSubmenuBackground, 0xffffffff);
        defaultColors.put(key_actionBarActionModeDefaultSelector, 0xfff0f0f0);
        defaultColors.put(key_actionBarTabActiveText, 0xffffffff);
        defaultColors.put(key_actionBarTabUnactiveText, 0xffd5e8f7);
        defaultColors.put(key_actionBarTabLine, 0xffffffff);
        defaultColors.put(key_actionBarTabSelector, 0xff406d94);

        defaultColors.put(key_actionBarBrowser, 0xffffffff);

        defaultColors.put(key_actionBarDefaultArchived, 0xff6f7a87);
        defaultColors.put(key_actionBarDefaultArchivedSelector, 0xff5e6772);
        defaultColors.put(key_actionBarDefaultArchivedIcon, 0xffffffff);
        defaultColors.put(key_actionBarDefaultArchivedTitle, 0xffffffff);
        defaultColors.put(key_actionBarDefaultArchivedSearch, 0xffffffff);
        defaultColors.put(key_actionBarDefaultArchivedSearchPlaceholder, 0x88ffffff);

        defaultColors.put(key_chats_onlineCircle, 0xff4bcb1c);
        defaultColors.put(key_chats_unreadCounter, 0xff4ecc5e);
        defaultColors.put(key_chats_unreadCounterMuted, 0xffc6c9cc);
        defaultColors.put(key_chats_unreadCounterText, 0xffffffff);
        defaultColors.put(key_chats_archiveBackground, 0xff66a9e0);
        defaultColors.put(key_chats_archivePinBackground, 0xff9faab3);
        defaultColors.put(key_chats_archiveIcon, 0xffffffff);
        defaultColors.put(key_chats_archiveText, 0xffffffff);
        defaultColors.put(key_chats_name, 0xff222222);
        defaultColors.put(key_chats_nameArchived, 0xff525252);
        defaultColors.put(key_chats_secretName, 0xff00a60e);
        defaultColors.put(key_chats_secretIcon, 0xff19b126);
        defaultColors.put(key_chats_nameIcon, 0xff242424);
        defaultColors.put(key_chats_pinnedIcon, 0xffa8a8a8);
        defaultColors.put(key_chats_message, 0xff8b8d8f);
        defaultColors.put(key_chats_messageArchived, 0xff919191);
        defaultColors.put(key_chats_message_threeLines, 0xff8e9091);
        defaultColors.put(key_chats_draft, 0xffdd4b39);
        defaultColors.put(key_chats_nameMessage, 0xff3c7eb0);
        defaultColors.put(key_chats_nameMessageArchived, 0xff8b8d8f);
        defaultColors.put(key_chats_nameMessage_threeLines, 0xff424449);
        defaultColors.put(key_chats_nameMessageArchived_threeLines, 0xff5e5e5e);
        defaultColors.put(key_chats_attachMessage, 0xff3c7eb0);
        defaultColors.put(key_chats_actionMessage, 0xff3c7eb0);
        defaultColors.put(key_chats_date, 0xff95999C);
        defaultColors.put(key_chats_pinnedOverlay, 0x08000000);
        defaultColors.put(key_chats_tabletSelectedOverlay, 0x0f000000);
        defaultColors.put(key_chats_sentCheck, 0xff46aa36);
        defaultColors.put(key_chats_sentReadCheck, 0xff46aa36);
        defaultColors.put(key_chats_sentClock, 0xff75bd5e);
        defaultColors.put(key_chats_sentError, 0xffd55252);
        defaultColors.put(key_chats_sentErrorIcon, 0xffffffff);
        defaultColors.put(key_chats_verifiedBackground, 0xff33a8e6);
        defaultColors.put(key_chats_verifiedCheck, 0xffffffff);
        defaultColors.put(key_chats_muteIcon, 0xffbdc1c4);
        defaultColors.put(key_chats_mentionIcon, 0xffffffff);
        defaultColors.put(key_chats_menuBackground, 0xffffffff);
        defaultColors.put(key_chats_menuItemText, 0xff444444);
        defaultColors.put(key_chats_menuItemCheck, 0xff598fba);
        defaultColors.put(key_chats_menuItemIcon, 0xff889198);
        defaultColors.put(key_chats_menuName, 0xffffffff);
        defaultColors.put(key_chats_menuPhone, 0xffffffff);
        defaultColors.put(key_chats_menuPhoneCats, 0xffc2e5ff);
        defaultColors.put(key_chats_menuCloud, 0xffffffff);
        defaultColors.put(key_chats_menuCloudBackgroundCats, 0xff427ba9);
        defaultColors.put(key_chats_actionIcon, 0xffffffff);
        defaultColors.put(key_chats_actionBackground, 0xff65a9e0);
        defaultColors.put(key_chats_actionPressedBackground, 0xff569dd6);
        defaultColors.put(key_chats_actionUnreadIcon, 0xff737373);
        defaultColors.put(key_chats_actionUnreadBackground, 0xffffffff);
        defaultColors.put(key_chats_actionUnreadPressedBackground, 0xfff2f2f2);
        defaultColors.put(key_chats_menuTopBackgroundCats, 0xff598fba);

        defaultColors.put(key_chat_attachMediaBanBackground, 0xff464646);
        defaultColors.put(key_chat_attachMediaBanText, 0xffffffff);
        defaultColors.put(key_chat_attachCheckBoxCheck, 0xffffffff);
        defaultColors.put(key_chat_attachCheckBoxBackground, 0xff39b2f7);
        defaultColors.put(key_chat_attachPhotoBackground, 0x08000000);
        defaultColors.put(key_chat_attachActiveTab, 0xff33a7f5);
        defaultColors.put(key_chat_attachUnactiveTab, 0xff92999e);
        defaultColors.put(key_chat_attachPermissionImage, 0xff333333);
        defaultColors.put(key_chat_attachPermissionMark, 0xffe25050);
        defaultColors.put(key_chat_attachPermissionText, 0xff6f777a);
        defaultColors.put(key_chat_attachEmptyImage, 0xffcccccc);

        defaultColors.put(key_chat_attachGalleryBackground, 0xff459df5);
        defaultColors.put(key_chat_attachGalleryIcon, 0xffffffff);
        defaultColors.put(key_chat_attachAudioBackground, 0xffeb6060);
        defaultColors.put(key_chat_attachAudioIcon, 0xffffffff);
        defaultColors.put(key_chat_attachFileBackground, 0xff34b9f1);
        defaultColors.put(key_chat_attachFileIcon, 0xffffffff);
        defaultColors.put(key_chat_attachContactBackground, 0xfff2c04b);
        defaultColors.put(key_chat_attachContactIcon, 0xffffffff);
        defaultColors.put(key_chat_attachLocationBackground, 0xff36c766);
        defaultColors.put(key_chat_attachLocationIcon, 0xffffffff);
        defaultColors.put(key_chat_attachPollBackground, 0xfff2c04b);
        defaultColors.put(key_chat_attachPollIcon, 0xffffffff);


        defaultColors.put(key_chat_status, 0xffd5e8f7);
        defaultColors.put(key_chat_inGreenCall, 0xff00c853);
        defaultColors.put(key_chat_inRedCall, 0xffff4848);
        defaultColors.put(key_chat_outGreenCall, 0xff00c853);
        defaultColors.put(key_chat_shareBackground, 0x66728fa6);
        defaultColors.put(key_chat_shareBackgroundSelected, 0x99728fa6);
        defaultColors.put(key_chat_lockIcon, 0xffffffff);
        defaultColors.put(key_chat_muteIcon, 0xffb1cce3);
        defaultColors.put(key_chat_inBubble, 0xffffffff);
        defaultColors.put(key_chat_inBubbleSelected, 0xffecf7fd);
        defaultColors.put(key_chat_inBubbleShadow, 0xff1d3753);
        defaultColors.put(key_chat_outBubble, 0xffefffde);
        defaultColors.put(key_chat_outBubbleSelected, 0xffd9f7c5);
        defaultColors.put(key_chat_outBubbleShadow, 0xff1e750c);
        defaultColors.put(key_chat_inMediaIcon, 0xffffffff);
        defaultColors.put(key_chat_inMediaIconSelected, 0xffeff8fe);
        defaultColors.put(key_chat_outMediaIcon, 0xffefffde);
        defaultColors.put(key_chat_outMediaIconSelected, 0xffe1f8cf);
        defaultColors.put(key_chat_messageTextIn, 0xff000000);
        defaultColors.put(key_chat_messageTextOut, 0xff000000);
        defaultColors.put(key_chat_messageLinkIn, 0xff2678b6);
        defaultColors.put(key_chat_messageLinkOut, 0xff2678b6);
        defaultColors.put(key_chat_serviceText, 0xffffffff);
        defaultColors.put(key_chat_serviceLink, 0xffffffff);
        defaultColors.put(key_chat_serviceIcon, 0xffffffff);
        defaultColors.put(key_chat_mediaTimeBackground, 0x66000000);
        defaultColors.put(key_chat_outSentCheck, 0xff5db050);
        defaultColors.put(key_chat_outSentCheckSelected, 0xff5db050);
        defaultColors.put(key_chat_outSentCheckRead, 0xff5db050);
        defaultColors.put(key_chat_outSentCheckReadSelected, 0xff5db050);
        defaultColors.put(key_chat_outSentClock, 0xff75bd5e);
        defaultColors.put(key_chat_outSentClockSelected, 0xff75bd5e);
        defaultColors.put(key_chat_inSentClock, 0xffa1aab3);
        defaultColors.put(key_chat_inSentClockSelected, 0xff93bdca);
        defaultColors.put(key_chat_mediaSentCheck, 0xffffffff);
        defaultColors.put(key_chat_mediaSentClock, 0xffffffff);
        defaultColors.put(key_chat_inViews, 0xffa1aab3);
        defaultColors.put(key_chat_inViewsSelected, 0xff93bdca);
        defaultColors.put(key_chat_outViews, 0xff6eb257);
        defaultColors.put(key_chat_outViewsSelected, 0xff6eb257);
        defaultColors.put(key_chat_mediaViews, 0xffffffff);
        defaultColors.put(key_chat_inMenu, 0xffb6bdc5);
        defaultColors.put(key_chat_inMenuSelected, 0xff98c1ce);
        defaultColors.put(key_chat_outMenu, 0xff91ce7e);
        defaultColors.put(key_chat_outMenuSelected, 0xff91ce7e);
        defaultColors.put(key_chat_mediaMenu, 0xffffffff);
        defaultColors.put(key_chat_outInstant, 0xff55ab4f);
        defaultColors.put(key_chat_outInstantSelected, 0xff489943);
        defaultColors.put(key_chat_inInstant, 0xff3a8ccf);
        defaultColors.put(key_chat_inInstantSelected, 0xff3079b5);
        defaultColors.put(key_chat_sentError, 0xffdb3535);
        defaultColors.put(key_chat_sentErrorIcon, 0xffffffff);
        defaultColors.put(key_chat_selectedBackground, 0x280a90f0);
        defaultColors.put(key_chat_previewDurationText, 0xffffffff);
        defaultColors.put(key_chat_previewGameText, 0xffffffff);
        defaultColors.put(key_chat_inPreviewInstantText, 0xff3a8ccf);
        defaultColors.put(key_chat_outPreviewInstantText, 0xff55ab4f);
        defaultColors.put(key_chat_inPreviewInstantSelectedText, 0xff3079b5);
        defaultColors.put(key_chat_outPreviewInstantSelectedText, 0xff489943);
        defaultColors.put(key_chat_secretTimeText, 0xffe4e2e0);
        defaultColors.put(key_chat_stickerNameText, 0xffffffff);
        defaultColors.put(key_chat_botButtonText, 0xffffffff);
        defaultColors.put(key_chat_botProgress, 0xffffffff);
        defaultColors.put(key_chat_inForwardedNameText, 0xff3886c7);
        defaultColors.put(key_chat_outForwardedNameText, 0xff55ab4f);
        defaultColors.put(key_chat_inViaBotNameText, 0xff3a8ccf);
        defaultColors.put(key_chat_outViaBotNameText, 0xff55ab4f);
        defaultColors.put(key_chat_stickerViaBotNameText, 0xffffffff);
        defaultColors.put(key_chat_inReplyLine, 0xff599fd8);
        defaultColors.put(key_chat_outReplyLine, 0xff6eb969);
        defaultColors.put(key_chat_stickerReplyLine, 0xffffffff);
        defaultColors.put(key_chat_inReplyNameText, 0xff3a8ccf);
        defaultColors.put(key_chat_outReplyNameText, 0xff55ab4f);
        defaultColors.put(key_chat_stickerReplyNameText, 0xffffffff);
        defaultColors.put(key_chat_inReplyMessageText, 0xff000000);
        defaultColors.put(key_chat_outReplyMessageText, 0xff000000);
        defaultColors.put(key_chat_inReplyMediaMessageText, 0xffa1aab3);
        defaultColors.put(key_chat_outReplyMediaMessageText, 0xff65b05b);
        defaultColors.put(key_chat_inReplyMediaMessageSelectedText, 0xff89b4c1);
        defaultColors.put(key_chat_outReplyMediaMessageSelectedText, 0xff65b05b);
        defaultColors.put(key_chat_stickerReplyMessageText, 0xffffffff);
        defaultColors.put(key_chat_inPreviewLine, 0xff70b4e8);
        defaultColors.put(key_chat_outPreviewLine, 0xff88c97b);
        defaultColors.put(key_chat_inSiteNameText, 0xff3a8ccf);
        defaultColors.put(key_chat_outSiteNameText, 0xff55ab4f);
        defaultColors.put(key_chat_inContactNameText, 0xff4e9ad4);
        defaultColors.put(key_chat_outContactNameText, 0xff55ab4f);
        defaultColors.put(key_chat_inContactPhoneText, 0xff2f3438);
        defaultColors.put(key_chat_inContactPhoneSelectedText, 0xff2f3438);
        defaultColors.put(key_chat_outContactPhoneText, 0xff354234);
        defaultColors.put(key_chat_outContactPhoneSelectedText, 0xff354234);
        defaultColors.put(key_chat_mediaProgress, 0xffffffff);
        defaultColors.put(key_chat_inAudioProgress, 0xffffffff);
        defaultColors.put(key_chat_outAudioProgress, 0xffefffde);
        defaultColors.put(key_chat_inAudioSelectedProgress, 0xffeff8fe);
        defaultColors.put(key_chat_outAudioSelectedProgress, 0xffe1f8cf);
        defaultColors.put(key_chat_mediaTimeText, 0xffffffff);
        defaultColors.put(key_chat_inTimeText, 0xffa1aab3);
        defaultColors.put(key_chat_outTimeText, 0xff70b15c);
        defaultColors.put(key_chat_adminText, 0xffc0c6cb);
        defaultColors.put(key_chat_adminSelectedText, 0xff89b4c1);
        defaultColors.put(key_chat_inTimeSelectedText, 0xff89b4c1);
        defaultColors.put(key_chat_outTimeSelectedText, 0xff70b15c);
        defaultColors.put(key_chat_inAudioPerformerText, 0xff2f3438);
        defaultColors.put(key_chat_inAudioPerformerSelectedText, 0xff2f3438);
        defaultColors.put(key_chat_outAudioPerformerText, 0xff354234);
        defaultColors.put(key_chat_outAudioPerformerSelectedText, 0xff354234);
        defaultColors.put(key_chat_inAudioTitleText, 0xff4e9ad4);
        defaultColors.put(key_chat_outAudioTitleText, 0xff55ab4f);
        defaultColors.put(key_chat_inAudioDurationText, 0xffa1aab3);
        defaultColors.put(key_chat_outAudioDurationText, 0xff65b05b);
        defaultColors.put(key_chat_inAudioDurationSelectedText, 0xff89b4c1);
        defaultColors.put(key_chat_outAudioDurationSelectedText, 0xff65b05b);
        defaultColors.put(key_chat_inAudioSeekbar, 0xffe4eaf0);
        defaultColors.put(key_chat_inAudioCacheSeekbar, 0x3fe4eaf0);
        defaultColors.put(key_chat_outAudioSeekbar, 0xffbbe3ac);
        defaultColors.put(key_chat_outAudioCacheSeekbar, 0x3fbbe3ac);
        defaultColors.put(key_chat_inAudioSeekbarSelected, 0xffbcdee8);
        defaultColors.put(key_chat_outAudioSeekbarSelected, 0xffa9dd96);
        defaultColors.put(key_chat_inAudioSeekbarFill, 0xff72b5e8);
        defaultColors.put(key_chat_outAudioSeekbarFill, 0xff78c272);
        defaultColors.put(key_chat_inVoiceSeekbar, 0xffdee5eb);
        defaultColors.put(key_chat_outVoiceSeekbar, 0xffbbe3ac);
        defaultColors.put(key_chat_inVoiceSeekbarSelected, 0xffbcdee8);
        defaultColors.put(key_chat_outVoiceSeekbarSelected, 0xffa9dd96);
        defaultColors.put(key_chat_inVoiceSeekbarFill, 0xff72b5e8);
        defaultColors.put(key_chat_outVoiceSeekbarFill, 0xff78c272);
        defaultColors.put(key_chat_inFileProgress, 0xffebf0f5);
        defaultColors.put(key_chat_outFileProgress, 0xffdaf5c3);
        defaultColors.put(key_chat_inFileProgressSelected, 0xffcbeaf6);
        defaultColors.put(key_chat_outFileProgressSelected, 0xffc5eca7);
        defaultColors.put(key_chat_inFileNameText, 0xff4e9ad4);
        defaultColors.put(key_chat_outFileNameText, 0xff55ab4f);
        defaultColors.put(key_chat_inFileInfoText, 0xffa1aab3);
        defaultColors.put(key_chat_outFileInfoText, 0xff65b05b);
        defaultColors.put(key_chat_inFileInfoSelectedText, 0xff89b4c1);
        defaultColors.put(key_chat_outFileInfoSelectedText, 0xff65b05b);
        defaultColors.put(key_chat_inFileBackground, 0xffebf0f5);
        defaultColors.put(key_chat_outFileBackground, 0xffdaf5c3);
        defaultColors.put(key_chat_inFileBackgroundSelected, 0xffcbeaf6);
        defaultColors.put(key_chat_outFileBackgroundSelected, 0xffc5eca7);
        defaultColors.put(key_chat_inVenueInfoText, 0xffa1aab3);
        defaultColors.put(key_chat_outVenueInfoText, 0xff65b05b);
        defaultColors.put(key_chat_inVenueInfoSelectedText, 0xff89b4c1);
        defaultColors.put(key_chat_outVenueInfoSelectedText, 0xff65b05b);
        defaultColors.put(key_chat_mediaInfoText, 0xffffffff);
        defaultColors.put(key_chat_linkSelectBackground, 0x3362a9e3);
        defaultColors.put(key_chat_textSelectBackground, 0x6662a9e3);
        defaultColors.put(key_chat_emojiPanelBackground, 0xfff0f2f5);
        defaultColors.put(key_chat_emojiPanelBadgeBackground, 0xff4da6ea);
        defaultColors.put(key_chat_emojiPanelBadgeText, 0xffffffff);
        defaultColors.put(key_chat_emojiSearchBackground, 0xffe5e9ee);
        defaultColors.put(key_chat_emojiSearchIcon, 0xff94a1af);
        defaultColors.put(key_chat_emojiPanelShadowLine, 0x12000000);
        defaultColors.put(key_chat_emojiPanelEmptyText, 0xff949ba1);
        defaultColors.put(key_chat_emojiPanelIcon, 0xff9da4ab);
        defaultColors.put(key_chat_emojiBottomPanelIcon, 0xff8c9197);
        defaultColors.put(key_chat_emojiPanelIconSelected, 0xff2b97e2);
        defaultColors.put(key_chat_emojiPanelStickerPackSelector, 0xffe2e5e7);
        defaultColors.put(key_chat_emojiPanelStickerPackSelectorLine, 0xff56abf0);
        defaultColors.put(key_chat_emojiPanelBackspace, 0xff8c9197);
        defaultColors.put(key_chat_emojiPanelMasksIcon, 0xffffffff);
        defaultColors.put(key_chat_emojiPanelMasksIconSelected, 0xff62bfe8);
        defaultColors.put(key_chat_emojiPanelTrendingTitle, 0xff222222);
        defaultColors.put(key_chat_emojiPanelStickerSetName, 0xff828b94);
        defaultColors.put(key_chat_emojiPanelStickerSetNameHighlight, 0xff278ddb);
        defaultColors.put(key_chat_emojiPanelStickerSetNameIcon, 0xffb1b6bc);
        defaultColors.put(key_chat_emojiPanelTrendingDescription, 0xff8a8a8a);
        defaultColors.put(key_chat_botKeyboardButtonText, 0xff36474f);
        defaultColors.put(key_chat_botKeyboardButtonBackground, 0xffe4e7e9);
        defaultColors.put(key_chat_botKeyboardButtonBackgroundPressed, 0xffccd1d4);
        defaultColors.put(key_chat_unreadMessagesStartArrowIcon, 0xffa2b5c7);
        defaultColors.put(key_chat_unreadMessagesStartText, 0xff5695cc);
        defaultColors.put(key_chat_unreadMessagesStartBackground, 0xffffffff);
        defaultColors.put(key_chat_inFileIcon, 0xffa2b5c7);
        defaultColors.put(key_chat_inFileSelectedIcon, 0xff87b6c5);
        defaultColors.put(key_chat_outFileIcon, 0xff85bf78);
        defaultColors.put(key_chat_outFileSelectedIcon, 0xff85bf78);
        defaultColors.put(key_chat_inLocationBackground, 0xffebf0f5);
        defaultColors.put(key_chat_inLocationIcon, 0xffa2b5c7);
        defaultColors.put(key_chat_outLocationBackground, 0xffdaf5c3);
        defaultColors.put(key_chat_outLocationIcon, 0xff87bf78);
        defaultColors.put(key_chat_inContactBackground, 0xff72b5e8);
        defaultColors.put(key_chat_inContactIcon, 0xffffffff);
        defaultColors.put(key_chat_outContactBackground, 0xff78c272);
        defaultColors.put(key_chat_outContactIcon, 0xffefffde);
        defaultColors.put(key_chat_outBroadcast, 0xff46aa36);
        defaultColors.put(key_chat_mediaBroadcast, 0xffffffff);
        defaultColors.put(key_chat_searchPanelIcons, 0xff676a6f);
        defaultColors.put(key_chat_searchPanelText, 0xff676a6f);
        defaultColors.put(key_chat_secretChatStatusText, 0xff7f7f7f);
        defaultColors.put(key_chat_fieldOverlayText, 0xff3a8ccf);
        defaultColors.put(key_chat_stickersHintPanel, 0xffffffff);
        defaultColors.put(key_chat_replyPanelIcons, 0xff57a8e6);
        defaultColors.put(key_chat_replyPanelClose, 0xff8e959b);
        defaultColors.put(key_chat_replyPanelName, 0xff3a8ccf);
        defaultColors.put(key_chat_replyPanelMessage, 0xff222222);
        defaultColors.put(key_chat_replyPanelLine, 0xffe8e8e8);
        defaultColors.put(key_chat_messagePanelBackground, 0xffffffff);
        defaultColors.put(key_chat_messagePanelText, 0xff000000);
        defaultColors.put(key_chat_messagePanelHint, 0xffa4acb3);
        defaultColors.put(key_chat_messagePanelCursor, 0xff54a1db);
        defaultColors.put(key_chat_messagePanelShadow, 0xff000000);
        defaultColors.put(key_chat_messagePanelIcons, 0xff8e959b);
        defaultColors.put(key_chat_messagePanelVideoFrame, 0xff4badf7);
        defaultColors.put(key_chat_recordedVoicePlayPause, 0xffffffff);
        defaultColors.put(key_chat_recordedVoicePlayPausePressed, 0xffd9eafb);
        defaultColors.put(key_chat_recordedVoiceDot, 0xffda564d);
        defaultColors.put(key_chat_recordedVoiceBackground, 0xff559ee3);
        defaultColors.put(key_chat_recordedVoiceProgress, 0xffa2cef8);
        defaultColors.put(key_chat_recordedVoiceProgressInner, 0xffffffff);
        defaultColors.put(key_chat_recordVoiceCancel, 0xff999999);
        defaultColors.put(key_chat_messagePanelSend, 0xff62b0eb);
        defaultColors.put(key_chat_messagePanelSendPressed, 0xffffffff);
        defaultColors.put(key_chat_messagePanelVoiceLock, 0xffa4a4a4);
        defaultColors.put(key_chat_messagePanelVoiceLockBackground, 0xffffffff);
        defaultColors.put(key_chat_messagePanelVoiceLockShadow, 0xff000000);
        defaultColors.put(key_chat_recordTime, 0xff4d4c4b);
        defaultColors.put(key_chat_emojiPanelNewTrending, 0xff4da6ea);
        defaultColors.put(key_chat_gifSaveHintText, 0xffffffff);
        defaultColors.put(key_chat_gifSaveHintBackground, 0xcc111111);
        defaultColors.put(key_chat_goDownButton, 0xffffffff);
        defaultColors.put(key_chat_goDownButtonShadow, 0xff000000);
        defaultColors.put(key_chat_goDownButtonIcon, 0xff8e959b);
        defaultColors.put(key_chat_goDownButtonCounter, 0xffffffff);
        defaultColors.put(key_chat_goDownButtonCounterBackground, 0xff4da2e8);
        defaultColors.put(key_chat_messagePanelCancelInlineBot, 0xffadadad);
        defaultColors.put(key_chat_messagePanelVoicePressed, 0xffffffff);
        defaultColors.put(key_chat_messagePanelVoiceBackground, 0xff5795cc);
        defaultColors.put(key_chat_messagePanelVoiceShadow, 0x0d000000);
        defaultColors.put(key_chat_messagePanelVoiceDelete, 0xff737373);
        defaultColors.put(key_chat_messagePanelVoiceDuration, 0xffffffff);
        defaultColors.put(key_chat_inlineResultIcon, 0xff5795cc);
        defaultColors.put(key_chat_topPanelBackground, 0xffffffff);
        defaultColors.put(key_chat_topPanelClose, 0xff8c959a);
        defaultColors.put(key_chat_topPanelLine, 0xff6c9fd2);
        defaultColors.put(key_chat_topPanelTitle, 0xff3a8ccf);
        defaultColors.put(key_chat_topPanelMessage, 0xff999999);
        defaultColors.put(key_chat_reportSpam, 0xffcf5957);
        defaultColors.put(key_chat_addContact, 0xff4a82b5);
        defaultColors.put(key_chat_inLoader, 0xff72b5e8);
        defaultColors.put(key_chat_inLoaderSelected, 0xff65abe0);
        defaultColors.put(key_chat_outLoader, 0xff78c272);
        defaultColors.put(key_chat_outLoaderSelected, 0xff6ab564);
        defaultColors.put(key_chat_inLoaderPhoto, 0xffa2b8c8);
        defaultColors.put(key_chat_inLoaderPhotoSelected, 0xffa2b5c7);
        defaultColors.put(key_chat_inLoaderPhotoIcon, 0xfffcfcfc);
        defaultColors.put(key_chat_inLoaderPhotoIconSelected, 0xffebf0f5);
        defaultColors.put(key_chat_outLoaderPhoto, 0xff85bf78);
        defaultColors.put(key_chat_outLoaderPhotoSelected, 0xff7db870);
        defaultColors.put(key_chat_outLoaderPhotoIcon, 0xffdaf5c3);
        defaultColors.put(key_chat_outLoaderPhotoIconSelected, 0xffc0e8a4);
        defaultColors.put(key_chat_mediaLoaderPhoto, 0x66000000);
        defaultColors.put(key_chat_mediaLoaderPhotoSelected, 0x7f000000);
        defaultColors.put(key_chat_mediaLoaderPhotoIcon, 0xffffffff);
        defaultColors.put(key_chat_mediaLoaderPhotoIconSelected, 0xffd9d9d9);
        defaultColors.put(key_chat_secretTimerBackground, 0xcc3e648e);
        defaultColors.put(key_chat_secretTimerText, 0xffffffff);

        defaultColors.put(key_profile_creatorIcon, 0xff3a95d5);
        defaultColors.put(key_profile_actionIcon, 0xff81868a);
        defaultColors.put(key_profile_actionBackground, 0xffffffff);
        defaultColors.put(key_profile_actionPressedBackground, 0xfff2f2f2);
        defaultColors.put(key_profile_verifiedBackground, 0xffb2d6f8);
        defaultColors.put(key_profile_verifiedCheck, 0xff4983b8);
        defaultColors.put(key_profile_title, 0xffffffff);
        defaultColors.put(key_profile_status, 0xffd7eafa);

        defaultColors.put(key_player_actionBar, 0xffffffff);
        defaultColors.put(key_player_actionBarSelector, 0x0f000000);
        defaultColors.put(key_player_actionBarTitle, 0xff2f3438);
        defaultColors.put(key_player_actionBarTop, 0x99000000);
        defaultColors.put(key_player_actionBarSubtitle, 0xff8a8a8a);
        defaultColors.put(key_player_actionBarItems, 0xff8a8a8a);
        defaultColors.put(key_player_background, 0xffffffff);
        defaultColors.put(key_player_time, 0xff8c9296);
        defaultColors.put(key_player_progressBackground, 0xffe9eff5);
        defaultColors.put(key_player_progressCachedBackground, 0xffe9eff5);
        defaultColors.put(key_player_progress, 0xff4b9fe3);
        defaultColors.put(key_player_placeholder, 0xffa8a8a8);
        defaultColors.put(key_player_placeholderBackground, 0xfff0f0f0);
        defaultColors.put(key_player_button, 0xff333333);
        defaultColors.put(key_player_buttonActive, 0xff4ca8ea);

        defaultColors.put(key_sheet_scrollUp, 0xffe1e4e8);
        defaultColors.put(key_sheet_other, 0xffc9cdd3);

        defaultColors.put(key_files_folderIcon, 0xff999999);
        defaultColors.put(key_files_folderIconBackground, 0xfff0f0f0);
        defaultColors.put(key_files_iconText, 0xffffffff);

        defaultColors.put(key_sessions_devicesImage, 0xff969696);

        defaultColors.put(key_passport_authorizeBackground, 0xff45abef);
        defaultColors.put(key_passport_authorizeBackgroundSelected, 0xff409ddb);
        defaultColors.put(key_passport_authorizeText, 0xffffffff);

        defaultColors.put(key_location_sendLocationBackground, 0xff6da0d4);
        defaultColors.put(key_location_sendLiveLocationBackground, 0xffff6464);
        defaultColors.put(key_location_sendLocationIcon, 0xffffffff);
        defaultColors.put(key_location_sendLiveLocationIcon, 0xffffffff);
        defaultColors.put(key_location_liveLocationProgress, 0xff359fe5);
        defaultColors.put(key_location_placeLocationBackground, 0xff4ca8ea);
        defaultColors.put(key_dialog_liveLocationProgress, 0xff359fe5);

        defaultColors.put(key_calls_callReceivedGreenIcon, 0xff00c853);
        defaultColors.put(key_calls_callReceivedRedIcon, 0xffff4848);

        defaultColors.put(key_featuredStickers_addedIcon, 0xff50a8eb);
        defaultColors.put(key_featuredStickers_buttonProgress, 0xffffffff);
        defaultColors.put(key_featuredStickers_addButton, 0xff50a8eb);
        defaultColors.put(key_featuredStickers_addButtonPressed, 0xff439bde);
        defaultColors.put(key_featuredStickers_delButton, 0xffd95757);
        defaultColors.put(key_featuredStickers_delButtonPressed, 0xffc64949);
        defaultColors.put(key_featuredStickers_buttonText, 0xffffffff);
        defaultColors.put(key_featuredStickers_unread, 0xff4da6ea);

        defaultColors.put(key_inappPlayerPerformer, 0xff2f3438);
        defaultColors.put(key_inappPlayerTitle, 0xff2f3438);
        defaultColors.put(key_inappPlayerBackground, 0xffffffff);
        defaultColors.put(key_inappPlayerPlayPause, 0xff62b0eb);
        defaultColors.put(key_inappPlayerClose, 0xffa8a8a8);

        defaultColors.put(key_returnToCallBackground, 0xff44a1e3);
        defaultColors.put(key_returnToCallText, 0xffffffff);

        defaultColors.put(key_sharedMedia_startStopLoadIcon, 0xff36a2ee);
        defaultColors.put(key_sharedMedia_linkPlaceholder, 0xfff0f3f5);
        defaultColors.put(key_sharedMedia_linkPlaceholderText, 0xffb7bec3);
        defaultColors.put(key_sharedMedia_photoPlaceholder, 0xffedf3f7);
        defaultColors.put(key_sharedMedia_actionMode, 0xff4687b3);

        defaultColors.put(key_checkbox, 0xff5ec245);
        defaultColors.put(key_checkboxCheck, 0xffffffff);
        defaultColors.put(key_checkboxDisabled, 0xffb0b9c2);

        defaultColors.put(key_stickers_menu, 0xffb6bdc5);
        defaultColors.put(key_stickers_menuSelector, 0x0f000000);

        defaultColors.put(key_changephoneinfo_image, 0xffb8bfc5);
        defaultColors.put(key_changephoneinfo_image2, 0xff50a7ea);

        defaultColors.put(key_groupcreate_hintText, 0xffa1aab3);
        defaultColors.put(key_groupcreate_cursor, 0xff52a3db);
        defaultColors.put(key_groupcreate_sectionShadow, 0xff000000);
        defaultColors.put(key_groupcreate_sectionText, 0xff7c8288);
        defaultColors.put(key_groupcreate_spanText, 0xff222222);
        defaultColors.put(key_groupcreate_spanBackground, 0xfff2f2f2);
        defaultColors.put(key_groupcreate_spanDelete, 0xffffffff);

        defaultColors.put(key_contacts_inviteBackground, 0xff55be61);
        defaultColors.put(key_contacts_inviteText, 0xffffffff);

        defaultColors.put(key_login_progressInner, 0xffe1eaf2);
        defaultColors.put(key_login_progressOuter, 0xff62a0d0);

        defaultColors.put(key_musicPicker_checkbox, 0xff29b6f7);
        defaultColors.put(key_musicPicker_checkboxCheck, 0xffffffff);
        defaultColors.put(key_musicPicker_buttonBackground, 0xff5cafea);
        defaultColors.put(key_musicPicker_buttonIcon, 0xffffffff);
        defaultColors.put(key_picker_enabledButton, 0xff19a7e8);
        defaultColors.put(key_picker_disabledButton, 0xff999999);
        defaultColors.put(key_picker_badge, 0xff29b6f7);
        defaultColors.put(key_picker_badgeText, 0xffffffff);

        defaultColors.put(key_chat_botSwitchToInlineText, 0xff4391cc);

        defaultColors.put(key_undo_background, 0xea272f38);
        defaultColors.put(key_undo_cancelColor, 0xff85caff);
        defaultColors.put(key_undo_infoColor, 0xffffffff);

        fallbackKeys.put(key_chat_adminText, key_chat_inTimeText);
        fallbackKeys.put(key_chat_adminSelectedText, key_chat_inTimeSelectedText);
        fallbackKeys.put(key_player_progressCachedBackground, key_player_progressBackground);
        fallbackKeys.put(key_chat_inAudioCacheSeekbar, key_chat_inAudioSeekbar);
        fallbackKeys.put(key_chat_outAudioCacheSeekbar, key_chat_outAudioSeekbar);
        fallbackKeys.put(key_chat_emojiSearchBackground, key_chat_emojiPanelStickerPackSelector);
        fallbackKeys.put(key_location_sendLiveLocationIcon, key_location_sendLocationIcon);
        fallbackKeys.put(key_changephoneinfo_image2, key_featuredStickers_addButton);
        fallbackKeys.put(key_graySectionText, key_windowBackgroundWhiteGrayText2);
        fallbackKeys.put(key_chat_inMediaIcon, key_chat_inBubble);
        fallbackKeys.put(key_chat_outMediaIcon, key_chat_outBubble);
        fallbackKeys.put(key_chat_inMediaIconSelected, key_chat_inBubbleSelected);
        fallbackKeys.put(key_chat_outMediaIconSelected, key_chat_outBubbleSelected);
        fallbackKeys.put(key_chats_actionUnreadIcon, key_profile_actionIcon);
        fallbackKeys.put(key_chats_actionUnreadBackground, key_profile_actionBackground);
        fallbackKeys.put(key_chats_actionUnreadPressedBackground, key_profile_actionPressedBackground);
        fallbackKeys.put(key_dialog_inlineProgressBackground, key_windowBackgroundGray);
        fallbackKeys.put(key_dialog_inlineProgress, key_chats_menuItemIcon);
        fallbackKeys.put(key_groupcreate_spanDelete, key_chats_actionIcon);
        fallbackKeys.put(key_sharedMedia_photoPlaceholder, key_windowBackgroundGray);
        fallbackKeys.put(key_chat_attachPollBackground, key_chat_attachAudioBackground);
        fallbackKeys.put(key_chat_attachPollIcon, key_chat_attachAudioIcon);
        fallbackKeys.put(key_chats_onlineCircle, key_windowBackgroundWhiteBlueText);
        fallbackKeys.put(key_windowBackgroundWhiteBlueButton, key_windowBackgroundWhiteValueText);
        fallbackKeys.put(key_windowBackgroundWhiteBlueIcon, key_windowBackgroundWhiteValueText);
        fallbackKeys.put(key_undo_background, key_chat_gifSaveHintBackground);
        fallbackKeys.put(key_undo_cancelColor, key_chat_gifSaveHintText);
        fallbackKeys.put(key_undo_infoColor, key_chat_gifSaveHintText);
        fallbackKeys.put(key_windowBackgroundUnchecked, key_windowBackgroundWhite);
        fallbackKeys.put(key_windowBackgroundChecked, key_windowBackgroundWhite);
        fallbackKeys.put(key_switchTrackBlue, key_switchTrack);
        fallbackKeys.put(key_switchTrackBlueChecked, key_switchTrackChecked);
        fallbackKeys.put(key_switchTrackBlueThumb, key_windowBackgroundWhite);
        fallbackKeys.put(key_switchTrackBlueThumbChecked, key_windowBackgroundWhite);
        fallbackKeys.put(key_windowBackgroundCheckText, key_windowBackgroundWhiteBlackText);
        fallbackKeys.put(key_contextProgressInner4, key_contextProgressInner1);
        fallbackKeys.put(key_contextProgressOuter4, key_contextProgressOuter1);
        fallbackKeys.put(key_switchTrackBlueSelector, key_listSelector);
        fallbackKeys.put(key_switchTrackBlueSelectorChecked, key_listSelector);
        fallbackKeys.put(key_chat_emojiBottomPanelIcon, key_chat_emojiPanelIcon);
        fallbackKeys.put(key_chat_emojiSearchIcon, key_chat_emojiPanelIcon);
        fallbackKeys.put(key_chat_emojiPanelStickerSetNameHighlight, key_windowBackgroundWhiteBlueText4);
        fallbackKeys.put(key_chat_emojiPanelStickerPackSelectorLine, key_chat_emojiPanelIconSelected);
        fallbackKeys.put(key_sharedMedia_actionMode, key_actionBarDefault);
        fallbackKeys.put(key_sheet_scrollUp, key_chat_emojiPanelStickerPackSelector);
        fallbackKeys.put(key_sheet_other, key_player_actionBarItems);
        fallbackKeys.put(key_dialogSearchBackground, key_chat_emojiPanelStickerPackSelector);
        fallbackKeys.put(key_dialogSearchHint, key_chat_emojiPanelIcon);
        fallbackKeys.put(key_dialogSearchIcon, key_chat_emojiPanelIcon);
        fallbackKeys.put(key_dialogSearchText, key_windowBackgroundWhiteBlackText);
        fallbackKeys.put(key_dialogFloatingButton, key_dialogRoundCheckBox);
        fallbackKeys.put(key_dialogFloatingButtonPressed, key_dialogRoundCheckBox);
        fallbackKeys.put(key_dialogFloatingIcon, key_dialogRoundCheckBoxCheck);
        fallbackKeys.put(key_dialogShadowLine, key_chat_emojiPanelShadowLine);
        fallbackKeys.put(key_actionBarDefaultArchived, key_actionBarDefault);
        fallbackKeys.put(key_actionBarDefaultArchivedSelector, key_actionBarDefaultSelector);
        fallbackKeys.put(key_actionBarDefaultArchivedIcon, key_actionBarDefaultIcon);
        fallbackKeys.put(key_actionBarDefaultArchivedTitle, key_actionBarDefaultTitle);
        fallbackKeys.put(key_actionBarDefaultArchivedSearch, key_actionBarDefaultSearch);
        fallbackKeys.put(key_actionBarDefaultArchivedSearchPlaceholder, key_actionBarDefaultSearchPlaceholder);
        fallbackKeys.put(key_chats_message_threeLines, key_chats_message);
        fallbackKeys.put(key_chats_nameMessage_threeLines, key_chats_nameMessage);
        fallbackKeys.put(key_chats_nameArchived, key_chats_name);
        fallbackKeys.put(key_chats_nameMessageArchived, key_chats_nameMessage);
        fallbackKeys.put(key_chats_nameMessageArchived_threeLines, key_chats_nameMessage);
        fallbackKeys.put(key_chats_messageArchived, key_chats_message);
        fallbackKeys.put(key_avatar_backgroundArchived, key_chats_unreadCounterMuted);
        fallbackKeys.put(key_avatar_backgroundArchivedHidden, key_chats_unreadCounterMuted);
        fallbackKeys.put(key_chats_archiveBackground, key_chats_actionBackground);
        fallbackKeys.put(key_chats_archivePinBackground, key_chats_unreadCounterMuted);
        fallbackKeys.put(key_chats_archiveIcon, key_chats_actionIcon);
        fallbackKeys.put(key_chats_archiveText, key_chats_actionIcon);
        fallbackKeys.put(key_actionBarDefaultSubmenuItemIcon, key_dialogIcon);
        fallbackKeys.put(key_checkboxDisabled, key_chats_unreadCounterMuted);
        fallbackKeys.put(key_chat_status, key_actionBarDefaultSubtitle);
        fallbackKeys.put(key_chat_inGreenCall, key_calls_callReceivedGreenIcon);
        fallbackKeys.put(key_chat_inRedCall, key_calls_callReceivedRedIcon);
        fallbackKeys.put(key_chat_outGreenCall, key_calls_callReceivedGreenIcon);
        fallbackKeys.put(key_actionBarTabActiveText, key_actionBarDefaultTitle);
        fallbackKeys.put(key_actionBarTabUnactiveText, key_actionBarDefaultSubtitle);
        fallbackKeys.put(key_actionBarTabLine, key_actionBarDefaultTitle);
        fallbackKeys.put(key_actionBarTabSelector, key_actionBarDefaultSelector);
        fallbackKeys.put(key_profile_status, key_avatar_subtitleInProfileBlue);
        fallbackKeys.put(key_chats_menuTopBackgroundCats, key_avatar_backgroundActionBarBlue);
        fallbackKeys.put(key_chat_messagePanelSendPressed, key_chat_messagePanelVoicePressed);
        //fallbackKeys.put(key_chat_attachActiveTab, 0xff33a7f5); //TODO fallback
        //fallbackKeys.put(key_chat_attachUnactiveTab, 0xff92999e); //TODO fallback
        fallbackKeys.put(key_chat_attachPermissionImage, key_dialogTextBlack);
        fallbackKeys.put(key_chat_attachPermissionMark, key_chat_sentError);
        fallbackKeys.put(key_chat_attachPermissionText, key_dialogTextBlack);
        fallbackKeys.put(key_chat_attachEmptyImage, key_emptyListPlaceholder);
        fallbackKeys.put(key_actionBarBrowser, key_actionBarDefault);
        fallbackKeys.put(key_chats_sentReadCheck, key_chats_sentCheck);
        fallbackKeys.put(key_chat_outSentCheckRead, key_chat_outSentCheck);
        fallbackKeys.put(key_chat_outSentCheckReadSelected, key_chat_outSentCheckSelected);
        themeAccentExclusionKeys.addAll(Arrays.asList(keys_avatar_background));
        themeAccentExclusionKeys.addAll(Arrays.asList(keys_avatar_nameInMessage));
        themeAccentExclusionKeys.add(key_chat_attachFileBackground);
        themeAccentExclusionKeys.add(key_chat_attachGalleryBackground);

        themes = new ArrayList<>();
        otherThemes = new ArrayList<>();
        themesDict = new HashMap<>();
        currentColorsNoAccent = new HashMap<>();
        currentColors = new HashMap<>();

        ThemeInfo themeInfo = new ThemeInfo();
        themeInfo.name = "Default";
        themeInfo.previewBackgroundColor = 0xffcfd9e3;
        themeInfo.previewInColor = 0xffffffff;
        themeInfo.previewOutColor = 0xfff0fee0;
        themeInfo.sortIndex = 0;
        themes.add(currentDayTheme = currentTheme = defaultTheme = themeInfo);
        themesDict.put("Default", defaultTheme);

        themeInfo = new ThemeInfo();
        themeInfo.name = "Blue";
        themeInfo.assetName = "bluebubbles.attheme";
        themeInfo.previewBackgroundColor = 0xff95beec;
        themeInfo.previewInColor = 0xffffffff;
        themeInfo.previewOutColor = 0xffd0e6ff;
        themeInfo.sortIndex = 1;
        themeInfo.setAccentColorOptions(new int[] { 0xFF328ACF, 0xFF43ACC7, 0xFF52AC44, 0xFFCD5F93, 0xFFD28036, 0xFF8366CC, 0xFFCE4E57, 0xFFD3AE40, 0xFF7B88AB });
        themes.add(themeInfo);
        themesDict.put("Blue", themeInfo);

        themeInfo = new ThemeInfo();
        themeInfo.name = "Dark Blue";
        themeInfo.assetName = "darkblue.attheme";
        themeInfo.previewBackgroundColor = 0xff5f6e82;
        themeInfo.previewInColor = 0xff76869c;
        themeInfo.previewOutColor = 0xff82a8e3;
        themeInfo.sortIndex = 2;
        themeInfo.setAccentColorOptions(new int[] { 0xff3685fa, 0xff46c8ed, 0xff4ab841, 0xffeb7cb1, 0xffee902a, 0xffa281f0, 0xffd34324, 0xffeebd34, 0xff7f8fab, 0xff3581e3 });
        themes.add(themeInfo);
        themesDict.put("Dark Blue", currentNightTheme = themeInfo);

        if (BuildVars.DEBUG_VERSION) {
            themeInfo = new ThemeInfo();
            themeInfo.name = "Graphite";
            themeInfo.assetName = "graphite.attheme";
            themeInfo.previewBackgroundColor = 0xff7a7e89;
            themeInfo.previewInColor = 0xff989ba3;
            themeInfo.previewOutColor = 0xffa4bff9;
            themeInfo.sortIndex = 3;
            themes.add(themeInfo);
            themesDict.put("Graphite", themeInfo);
        }

        themeInfo = new ThemeInfo();
        themeInfo.name = "Arctic Blue";
        themeInfo.assetName = "arctic.attheme";
        themeInfo.previewBackgroundColor = 0xffffffff;
        themeInfo.previewInColor = 0xffebeef4;
        themeInfo.previewOutColor = 0xff7cb2fe;
        themeInfo.sortIndex = 4;
        themeInfo.setAccentColorOptions(new int[] { 0xFF3490EB, 0xFF43ACC7, 0xFF52AC44, 0xFFCD5F93, 0xFFD28036, 0xFF8366CC, 0xFFCE4E57, 0xFFD3AE40, 0xFF7B88AB });
        themes.add(themeInfo);
        themesDict.put("Arctic Blue", themeInfo);

        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE);
        String themesString = preferences.getString("themes2", null);
        remoteThemesHash = preferences.getInt("remoteThemesHash", 0);
        lastLoadingThemesTime = preferences.getInt("lastLoadingThemesTime", 0);
        if (!TextUtils.isEmpty(themesString)) {
            try {
                JSONArray jsonArray = new JSONArray(themesString);
                for (int a = 0; a < jsonArray.length(); a++) {
                    themeInfo = ThemeInfo.createWithJson(jsonArray.getJSONObject(a));
                    if (themeInfo != null) {
                        otherThemes.add(themeInfo);
                        themes.add(themeInfo);
                        themesDict.put(themeInfo.getKey(), themeInfo);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        } else {
            themesString = preferences.getString("themes", null);
            if (!TextUtils.isEmpty(themesString)) {
                String[] themesArr = themesString.split("&");
                for (int a = 0; a < themesArr.length; a++) {
                    themeInfo = ThemeInfo.createWithString(themesArr[a]);
                    if (themeInfo != null) {
                        otherThemes.add(themeInfo);
                        themes.add(themeInfo);
                        themesDict.put(themeInfo.getKey(), themeInfo);
                    }
                }
            }
            saveOtherThemes(true);
            preferences.edit().remove("themes").commit();
        }

        sortThemes();

        ThemeInfo applyingTheme = null;
        try {
            final ThemeInfo themeDarkBlue = themesDict.get("Dark Blue");

            preferences = MessagesController.getGlobalMainSettings();
            String theme = preferences.getString("theme", null);
            if ("Dark".equals(theme)) { // Old theme. Fallback to "Dark Blue" with specific accent.
                applyingTheme = themeDarkBlue;
                themeDarkBlue.setAccentColor(0xff3581e3);
            } else if (theme != null) {
                applyingTheme = themesDict.get(theme);
            }

            theme = preferences.getString("nighttheme", null);
            if ("Dark".equals(theme)) { // Old theme. Fallback to "Dark Blue" with specific accent.
                currentNightTheme = themeDarkBlue;
                themeDarkBlue.setAccentColor(0xff3581e3);
            } else if (theme != null) {
                ThemeInfo t = themesDict.get(theme);
                if (t !=  null) {
                    currentNightTheme = t;
                }
            }

            for (ThemeInfo info : themesDict.values()) {
                if (info.assetName != null && info.accentBaseColor != 0) {
                    info.setAccentColor(preferences.getInt("accent_for_" + info.assetName, info.accentColor));
                }
            }

            selectedAutoNightType = preferences.getInt("selectedAutoNightType", AUTO_NIGHT_TYPE_NONE);
            autoNightScheduleByLocation = preferences.getBoolean("autoNightScheduleByLocation", false);
            autoNightBrighnessThreshold = preferences.getFloat("autoNightBrighnessThreshold", 0.25f);
            autoNightDayStartTime = preferences.getInt("autoNightDayStartTime", 22 * 60);
            autoNightDayEndTime = preferences.getInt("autoNightDayEndTime", 8 * 60);
            autoNightSunsetTime = preferences.getInt("autoNightSunsetTime", 22 * 60);
            autoNightSunriseTime = preferences.getInt("autoNightSunriseTime", 8 * 60);
            autoNightCityName = preferences.getString("autoNightCityName", "");
            long val = preferences.getLong("autoNightLocationLatitude3", 10000);
            if (val != 10000) {
                autoNightLocationLatitude = Double.longBitsToDouble(val);
            } else {
                autoNightLocationLatitude = 10000;
            }
            val = preferences.getLong("autoNightLocationLongitude3", 10000);
            if (val != 10000) {
                autoNightLocationLongitude = Double.longBitsToDouble(val);
            } else {
                autoNightLocationLongitude = 10000;
            }
            autoNightLastSunCheckDay = preferences.getInt("autoNightLastSunCheckDay", -1);
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (applyingTheme == null) {
            applyingTheme = defaultTheme;
        } else {
            currentDayTheme = applyingTheme;
        }
        applyTheme(applyingTheme, false, false, false);
        AndroidUtilities.runOnUIThread(Theme::checkAutoNightThemeConditions);
    }

    private static Method StateListDrawable_getStateDrawableMethod;
    private static Field BitmapDrawable_mColorFilter;

    public static void saveAutoNightThemeConfig() {
        SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
        editor.putInt("selectedAutoNightType", selectedAutoNightType);
        editor.putBoolean("autoNightScheduleByLocation", autoNightScheduleByLocation);
        editor.putFloat("autoNightBrighnessThreshold", autoNightBrighnessThreshold);
        editor.putInt("autoNightDayStartTime", autoNightDayStartTime);
        editor.putInt("autoNightDayEndTime", autoNightDayEndTime);
        editor.putInt("autoNightSunriseTime", autoNightSunriseTime);
        editor.putString("autoNightCityName", autoNightCityName);
        editor.putInt("autoNightSunsetTime", autoNightSunsetTime);
        editor.putLong("autoNightLocationLatitude3", Double.doubleToRawLongBits(autoNightLocationLatitude));
        editor.putLong("autoNightLocationLongitude3", Double.doubleToRawLongBits(autoNightLocationLongitude));
        editor.putInt("autoNightLastSunCheckDay", autoNightLastSunCheckDay);
        if (currentNightTheme != null) {
            editor.putString("nighttheme", currentNightTheme.getKey());
        } else {
            editor.remove("nighttheme");
        }
        editor.commit();
    }

    @SuppressLint("PrivateApi")
    private static Drawable getStateDrawable(Drawable drawable, int index) {
        if (StateListDrawable_getStateDrawableMethod == null) {
            try {
                StateListDrawable_getStateDrawableMethod = StateListDrawable.class.getDeclaredMethod("getStateDrawable", int.class);
            } catch (Throwable ignore) {

            }
        }
        if (StateListDrawable_getStateDrawableMethod == null) {
            return null;
        }
        try {
            return (Drawable) StateListDrawable_getStateDrawableMethod.invoke(drawable, index);
        } catch (Exception ignore) {

        }
        return null;
    }

    public static Drawable createEmojiIconSelectorDrawable(Context context, int resource, int defaultColor, int pressedColor) {
        Resources resources = context.getResources();
        Drawable defaultDrawable = resources.getDrawable(resource).mutate();
        if (defaultColor != 0) {
            defaultDrawable.setColorFilter(new PorterDuffColorFilter(defaultColor, PorterDuff.Mode.MULTIPLY));
        }
        Drawable pressedDrawable = resources.getDrawable(resource).mutate();
        if (pressedColor != 0) {
            pressedDrawable.setColorFilter(new PorterDuffColorFilter(pressedColor, PorterDuff.Mode.MULTIPLY));
        }
        StateListDrawable stateListDrawable = new StateListDrawable() {
            @Override
            public boolean selectDrawable(int index) {
                if (Build.VERSION.SDK_INT < 21) {
                    Drawable drawable = getStateDrawable(this, index);
                    ColorFilter colorFilter = null;
                    if (drawable instanceof BitmapDrawable) {
                        colorFilter = ((BitmapDrawable) drawable).getPaint().getColorFilter();
                    } else if (drawable instanceof NinePatchDrawable) {
                        colorFilter = ((NinePatchDrawable) drawable).getPaint().getColorFilter();
                    }
                    boolean result = super.selectDrawable(index);
                    if (colorFilter != null) {
                        drawable.setColorFilter(colorFilter);
                    }
                    return result;
                }
                return super.selectDrawable(index);
            }
        };
        stateListDrawable.setEnterFadeDuration(1);
        stateListDrawable.setExitFadeDuration(200);
        stateListDrawable.addState(new int[]{android.R.attr.state_selected}, pressedDrawable);
        stateListDrawable.addState(new int[]{}, defaultDrawable);
        return stateListDrawable;
    }

    public static Drawable createEditTextDrawable(Context context, boolean alert) {
        Resources resources = context.getResources();
        Drawable defaultDrawable = resources.getDrawable(R.drawable.search_dark).mutate();
        defaultDrawable.setColorFilter(new PorterDuffColorFilter(getColor(alert ? Theme.key_dialogInputField : Theme.key_windowBackgroundWhiteInputField), PorterDuff.Mode.MULTIPLY));
        Drawable pressedDrawable = resources.getDrawable(R.drawable.search_dark_activated).mutate();
        pressedDrawable.setColorFilter(new PorterDuffColorFilter(getColor(alert ? Theme.key_dialogInputFieldActivated : Theme.key_windowBackgroundWhiteInputFieldActivated), PorterDuff.Mode.MULTIPLY));
        StateListDrawable stateListDrawable = new StateListDrawable() {
            @Override
            public boolean selectDrawable(int index) {
                if (Build.VERSION.SDK_INT < 21) {
                    Drawable drawable = getStateDrawable(this, index);
                    ColorFilter colorFilter = null;
                    if (drawable instanceof BitmapDrawable) {
                        colorFilter = ((BitmapDrawable) drawable).getPaint().getColorFilter();
                    } else if (drawable instanceof NinePatchDrawable) {
                        colorFilter = ((NinePatchDrawable) drawable).getPaint().getColorFilter();
                    }
                    boolean result = super.selectDrawable(index);
                    if (colorFilter != null) {
                        drawable.setColorFilter(colorFilter);
                    }
                    return result;
                }
                return super.selectDrawable(index);
            }
        };
        stateListDrawable.addState(new int[]{android.R.attr.state_enabled, android.R.attr.state_focused}, pressedDrawable);
        stateListDrawable.addState(new int[]{android.R.attr.state_focused}, pressedDrawable);
        stateListDrawable.addState(StateSet.WILD_CARD, defaultDrawable);
        return stateListDrawable;
    }

    public static boolean canStartHolidayAnimation() {
        return canStartHolidayAnimation;
    }

    public static int getEventType() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        int monthOfYear = calendar.get(Calendar.MONTH);
        int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
        int minutes = calendar.get(Calendar.MINUTE);
        int hour = calendar.get(Calendar.HOUR_OF_DAY);

        int eventType = -1;
        if (monthOfYear == 11 && dayOfMonth >= 24 && dayOfMonth <= 31 || monthOfYear == 0 && dayOfMonth == 1) {
            eventType = 0;
        }
        return eventType;
    }

    public static Drawable getCurrentHolidayDrawable() {
        if ((System.currentTimeMillis() - lastHolidayCheckTime) >= 60 * 1000) {
            lastHolidayCheckTime = System.currentTimeMillis();
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            int monthOfYear = calendar.get(Calendar.MONTH);
            int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
            int minutes = calendar.get(Calendar.MINUTE);
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            if (monthOfYear == 0 && dayOfMonth == 1 && minutes <= 10 && hour == 0) {
                canStartHolidayAnimation = true;
            } else {
                canStartHolidayAnimation = false;
            }
            if (dialogs_holidayDrawable == null) {
                if (monthOfYear == 11 && dayOfMonth >= (BuildVars.DEBUG_PRIVATE_VERSION ? 29 : 31) && dayOfMonth <= 31 || monthOfYear == 0 && dayOfMonth == 1) {
                    dialogs_holidayDrawable = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.newyear);
                    dialogs_holidayDrawableOffsetX = -AndroidUtilities.dp(3);
                    dialogs_holidayDrawableOffsetY = -AndroidUtilities.dp(1);
                }
            }
        }
        return dialogs_holidayDrawable;
    }

    public static int getCurrentHolidayDrawableXOffset() {
        return dialogs_holidayDrawableOffsetX;
    }

    public static int getCurrentHolidayDrawableYOffset() {
        return dialogs_holidayDrawableOffsetY;
    }

    public static Drawable createSimpleSelectorDrawable(Context context, int resource, int defaultColor, int pressedColor) {
        Resources resources = context.getResources();
        Drawable defaultDrawable = resources.getDrawable(resource).mutate();
        if (defaultColor != 0) {
            defaultDrawable.setColorFilter(new PorterDuffColorFilter(defaultColor, PorterDuff.Mode.MULTIPLY));
        }
        Drawable pressedDrawable = resources.getDrawable(resource).mutate();
        if (pressedColor != 0) {
            pressedDrawable.setColorFilter(new PorterDuffColorFilter(pressedColor, PorterDuff.Mode.MULTIPLY));
        }
        StateListDrawable stateListDrawable = new StateListDrawable() {
            @Override
            public boolean selectDrawable(int index) {
                if (Build.VERSION.SDK_INT < 21) {
                    Drawable drawable = getStateDrawable(this, index);
                    ColorFilter colorFilter = null;
                    if (drawable instanceof BitmapDrawable) {
                        colorFilter = ((BitmapDrawable) drawable).getPaint().getColorFilter();
                    } else if (drawable instanceof NinePatchDrawable) {
                        colorFilter = ((NinePatchDrawable) drawable).getPaint().getColorFilter();
                    }
                    boolean result = super.selectDrawable(index);
                    if (colorFilter != null) {
                        drawable.setColorFilter(colorFilter);
                    }
                    return result;
                }
                return super.selectDrawable(index);
            }
        };
        stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, pressedDrawable);
        stateListDrawable.addState(new int[]{android.R.attr.state_selected}, pressedDrawable);
        stateListDrawable.addState(StateSet.WILD_CARD, defaultDrawable);
        return stateListDrawable;
    }

    public static Drawable createCircleDrawable(int size, int color) {
        OvalShape ovalShape = new OvalShape();
        ovalShape.resize(size, size);
        ShapeDrawable defaultDrawable = new ShapeDrawable(ovalShape);
        defaultDrawable.getPaint().setColor(color);
        return defaultDrawable;
    }

    public static CombinedDrawable createCircleDrawableWithIcon(int size, int iconRes) {
        return createCircleDrawableWithIcon(size, iconRes, 0);
    }

    public static CombinedDrawable createCircleDrawableWithIcon(int size, int iconRes, int stroke) {
        Drawable drawable;
        if (iconRes != 0) {
            drawable = ApplicationLoader.applicationContext.getResources().getDrawable(iconRes).mutate();
        } else {
            drawable = null;
        }
        return createCircleDrawableWithIcon(size, drawable, stroke);
    }

    public static CombinedDrawable createCircleDrawableWithIcon(int size, Drawable drawable, int stroke) {
        OvalShape ovalShape = new OvalShape();
        ovalShape.resize(size, size);
        ShapeDrawable defaultDrawable = new ShapeDrawable(ovalShape);
        Paint paint = defaultDrawable.getPaint();
        paint.setColor(0xffffffff);
        if (stroke == 1) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(AndroidUtilities.dp(2));
        } else if (stroke == 2) {
            paint.setAlpha(0);
        }
        CombinedDrawable combinedDrawable = new CombinedDrawable(defaultDrawable, drawable);
        combinedDrawable.setCustomSize(size, size);
        return combinedDrawable;
    }

    public static Drawable createRoundRectDrawableWithIcon(int rad, int iconRes) {
        ShapeDrawable defaultDrawable = new ShapeDrawable(new RoundRectShape(new float[]{rad, rad, rad, rad, rad, rad, rad, rad}, null, null));
        defaultDrawable.getPaint().setColor(0xffffffff);
        Drawable drawable = ApplicationLoader.applicationContext.getResources().getDrawable(iconRes).mutate();
        return new CombinedDrawable(defaultDrawable, drawable);
    }

    public static void setCombinedDrawableColor(Drawable combinedDrawable, int color, boolean isIcon) {
        if (!(combinedDrawable instanceof CombinedDrawable)) {
            return;
        }
        Drawable drawable;
        if (isIcon) {
            drawable = ((CombinedDrawable) combinedDrawable).getIcon();
        } else {
            drawable = ((CombinedDrawable) combinedDrawable).getBackground();
        }
        if (drawable instanceof ColorDrawable) {
            ((ColorDrawable) drawable).setColor(color);
        } else {
            drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
        }
    }

    public static Drawable createSimpleSelectorCircleDrawable(int size, int defaultColor, int pressedColor) {
        OvalShape ovalShape = new OvalShape();
        ovalShape.resize(size, size);
        ShapeDrawable defaultDrawable = new ShapeDrawable(ovalShape);
        defaultDrawable.getPaint().setColor(defaultColor);
        ShapeDrawable pressedDrawable = new ShapeDrawable(ovalShape);
        if (Build.VERSION.SDK_INT >= 21) {
            pressedDrawable.getPaint().setColor(0xffffffff);
            ColorStateList colorStateList = new ColorStateList(
                    new int[][]{StateSet.WILD_CARD},
                    new int[]{pressedColor}
            );
            return new RippleDrawable(colorStateList, defaultDrawable, pressedDrawable);
        } else {
            pressedDrawable.getPaint().setColor(pressedColor);
            StateListDrawable stateListDrawable = new StateListDrawable();
            stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, pressedDrawable);
            stateListDrawable.addState(new int[]{android.R.attr.state_focused}, pressedDrawable);
            stateListDrawable.addState(StateSet.WILD_CARD, defaultDrawable);
            return stateListDrawable;
        }
    }

    public static Drawable createRoundRectDrawable(int rad, int defaultColor) {
        ShapeDrawable defaultDrawable = new ShapeDrawable(new RoundRectShape(new float[]{rad, rad, rad, rad, rad, rad, rad, rad}, null, null));
        defaultDrawable.getPaint().setColor(defaultColor);
        return defaultDrawable;
    }

    public static Drawable createSimpleSelectorRoundRectDrawable(int rad, int defaultColor, int pressedColor) {
        ShapeDrawable defaultDrawable = new ShapeDrawable(new RoundRectShape(new float[]{rad, rad, rad, rad, rad, rad, rad, rad}, null, null));
        defaultDrawable.getPaint().setColor(defaultColor);
        ShapeDrawable pressedDrawable = new ShapeDrawable(new RoundRectShape(new float[]{rad, rad, rad, rad, rad, rad, rad, rad}, null, null));
        pressedDrawable.getPaint().setColor(pressedColor);
        StateListDrawable stateListDrawable = new StateListDrawable();
        stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, pressedDrawable);
        stateListDrawable.addState(new int[]{android.R.attr.state_selected}, pressedDrawable);
        stateListDrawable.addState(StateSet.WILD_CARD, defaultDrawable);
        return stateListDrawable;
    }

    public static Drawable getRoundRectSelectorDrawable(int color) {
        if (Build.VERSION.SDK_INT >= 21) {
            Drawable maskDrawable = createRoundRectDrawable(AndroidUtilities.dp(3), 0xffffffff);
            ColorStateList colorStateList = new ColorStateList(
                    new int[][]{StateSet.WILD_CARD},
                    new int[]{(color & 0x00ffffff) | 0x19000000}
            );
            return new RippleDrawable(colorStateList, null, maskDrawable);
        } else {
            StateListDrawable stateListDrawable = new StateListDrawable();
            stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, createRoundRectDrawable(AndroidUtilities.dp(3), (color & 0x00ffffff) | 0x19000000));
            stateListDrawable.addState(new int[]{android.R.attr.state_selected}, createRoundRectDrawable(AndroidUtilities.dp(3), (color & 0x00ffffff) | 0x19000000));
            stateListDrawable.addState(StateSet.WILD_CARD, new ColorDrawable(0x00000000));
            return stateListDrawable;
        }
    }

    public static Drawable createSelectorWithBackgroundDrawable(int backgroundColor, int color) {
        if (Build.VERSION.SDK_INT >= 21) {
            Drawable maskDrawable = new ColorDrawable(backgroundColor);
            ColorStateList colorStateList = new ColorStateList(
                    new int[][]{StateSet.WILD_CARD},
                    new int[]{color}
            );
            return new RippleDrawable(colorStateList, new ColorDrawable(backgroundColor), maskDrawable);
        } else {
            StateListDrawable stateListDrawable = new StateListDrawable();
            stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(color));
            stateListDrawable.addState(new int[]{android.R.attr.state_selected}, new ColorDrawable(color));
            stateListDrawable.addState(StateSet.WILD_CARD, new ColorDrawable(backgroundColor));
            return stateListDrawable;
        }
    }

    public static Drawable getSelectorDrawable(boolean whiteBackground) {
        return getSelectorDrawable(getColor(key_listSelector), whiteBackground);
    }

    public static Drawable getSelectorDrawable(int color, boolean whiteBackground) {
        if (whiteBackground) {
            if (Build.VERSION.SDK_INT >= 21) {
                Drawable maskDrawable = new ColorDrawable(0xffffffff);
                ColorStateList colorStateList = new ColorStateList(
                        new int[][]{StateSet.WILD_CARD},
                        new int[]{color}
                );
                return new RippleDrawable(colorStateList, new ColorDrawable(getColor(key_windowBackgroundWhite)), maskDrawable);
            } else {
                StateListDrawable stateListDrawable = new StateListDrawable();
                stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(color));
                stateListDrawable.addState(new int[]{android.R.attr.state_selected}, new ColorDrawable(color));
                stateListDrawable.addState(StateSet.WILD_CARD, new ColorDrawable(getColor(key_windowBackgroundWhite)));
                return stateListDrawable;
            }
        } else {
            return createSelectorDrawable(color, 2);
        }
    }

    public static Drawable createSelectorDrawable(int color) {
        return createSelectorDrawable(color, 1, -1);
    }

    public static Drawable createSelectorDrawable(int color, int maskType) {
        return createSelectorDrawable(color, maskType, -1);
    }

    public static Drawable createSelectorDrawable(int color, int maskType, int radius) {
        Drawable drawable;
        if (Build.VERSION.SDK_INT >= 21) {
            Drawable maskDrawable = null;
            if ((maskType == 1 || maskType == 5) && Build.VERSION.SDK_INT >= 23) {
                maskDrawable = null;
            } else if (maskType == 1 || maskType == 3 || maskType == 4 || maskType == 5 || maskType == 6 || maskType == 7) {
                maskPaint.setColor(0xffffffff);
                maskDrawable = new Drawable() {

                    RectF rect;

                    @Override
                    public void draw(Canvas canvas) {
                        android.graphics.Rect bounds = getBounds();
                        if (maskType == 7) {
                            if (rect == null) {
                                rect = new RectF();
                            }
                            rect.set(bounds);
                            canvas.drawRoundRect(rect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), maskPaint);
                        } else {
                            int rad;
                            if (maskType == 1 || maskType == 6) {
                                rad = AndroidUtilities.dp(20);
                            } else if (maskType == 3) {
                                rad = (Math.max(bounds.width(), bounds.height()) / 2);
                            } else {
                                rad = (int) Math.ceil(Math.sqrt((bounds.left - bounds.centerX()) * (bounds.left - bounds.centerX()) + (bounds.top - bounds.centerY()) * (bounds.top - bounds.centerY())));
                            }
                            canvas.drawCircle(bounds.centerX(), bounds.centerY(), rad, maskPaint);
                        }
                    }

                    @Override
                    public void setAlpha(int alpha) {

                    }

                    @Override
                    public void setColorFilter(ColorFilter colorFilter) {

                    }

                    @Override
                    public int getOpacity() {
                        return PixelFormat.UNKNOWN;
                    }
                };
            } else if (maskType == 2) {
                maskDrawable = new ColorDrawable(0xffffffff);
            }
            ColorStateList colorStateList = new ColorStateList(
                    new int[][]{StateSet.WILD_CARD},
                    new int[]{color}
            );
            RippleDrawable rippleDrawable = new RippleDrawable(colorStateList, null, maskDrawable);
            if (Build.VERSION.SDK_INT >= 23) {
                if (maskType == 1) {
                    rippleDrawable.setRadius(radius <= 0 ? AndroidUtilities.dp(20) : radius);
                } else if (maskType == 5) {
                    rippleDrawable.setRadius(RippleDrawable.RADIUS_AUTO);
                }
            }
            return rippleDrawable;
        } else {
            StateListDrawable stateListDrawable = new StateListDrawable();
            stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(color));
            stateListDrawable.addState(new int[]{android.R.attr.state_selected}, new ColorDrawable(color));
            stateListDrawable.addState(StateSet.WILD_CARD, new ColorDrawable(0x00000000));
            return stateListDrawable;
        }
    }

    public static void applyPreviousTheme() {
        if (previousTheme == null) {
            return;
        }
        if (isWallpaperMotionPrev != null) {
            SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
            editor.putBoolean("selectedBackgroundMotion", isWallpaperMotion = isWallpaperMotionPrev);
            editor.commit();
        }
        if (isInNigthMode && currentNightTheme != null) {
            applyTheme(currentNightTheme, true, false, true);
        } else {
            applyTheme(previousTheme, true, false, false);
        }
        previousTheme = null;
        checkAutoNightThemeConditions();
    }

    private static void sortThemes() {
        Collections.sort(themes, (o1, o2) -> {
            if (o1.pathToFile == null && o1.assetName == null) {
                return -1;
            } else if (o2.pathToFile == null && o2.assetName == null) {
                return 1;
            }
            return o1.name.compareTo(o2.name);
        });
    }

    public static void applyThemeTemporary(ThemeInfo themeInfo) {
        previousTheme = getCurrentTheme();
        applyTheme(themeInfo, false, false, false);
    }

    public static ThemeInfo fillThemeValues(File file, String themeName, TLRPC.TL_theme theme) {
        try {
            ThemeInfo themeInfo = new ThemeInfo();
            themeInfo.name = themeName;
            themeInfo.info = theme;
            themeInfo.pathToFile = file.getAbsolutePath();
            themeInfo.account = UserConfig.selectedAccount;

            String[] wallpaperLink = new String[1];
            getThemeFileValues(new File(themeInfo.pathToFile), null, wallpaperLink);

            if (!TextUtils.isEmpty(wallpaperLink[0])) {
                String ling = wallpaperLink[0];
                themeInfo.pathToWallpaper = new File(ApplicationLoader.getFilesDirFixed(), Utilities.MD5(ling) + ".wp").getAbsolutePath();
                try {
                    Uri data = Uri.parse(ling);
                    themeInfo.slug = data.getQueryParameter("slug");
                    String mode = data.getQueryParameter("mode");
                    if (mode != null) {
                        mode = mode.toLowerCase();
                        String[] modes = mode.split(" ");
                        if (modes != null && modes.length > 0) {
                            for (int a = 0; a < modes.length; a++) {
                                if ("blur".equals(modes[a])) {
                                    themeInfo.isBlured = true;
                                } else if ("motion".equals(modes[a])) {
                                    themeInfo.isMotion = true;
                                }
                            }
                        }
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            } else {
                themedWallpaperLink = null;
            }

            return themeInfo;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    public static ThemeInfo applyThemeFile(File file, String themeName, TLRPC.TL_theme theme, boolean temporary) {
        try {
            if (!themeName.toLowerCase().endsWith(".attheme")) {
                themeName += ".attheme";
            }
            if (temporary) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.goingToPreviewTheme);
                ThemeInfo themeInfo = new ThemeInfo();
                themeInfo.name = themeName;
                themeInfo.info = theme;
                themeInfo.pathToFile = file.getAbsolutePath();
                themeInfo.account = UserConfig.selectedAccount;
                applyThemeTemporary(themeInfo);
                return themeInfo;
            } else {
                String key;
                File finalFile;
                if (theme != null) {
                    key = "remote" + theme.id;
                    finalFile = new File(ApplicationLoader.getFilesDirFixed(), key + ".attheme");
                } else {
                    key = themeName;
                    finalFile = new File(ApplicationLoader.getFilesDirFixed(), key);
                }
                if (!AndroidUtilities.copyFile(file, finalFile)) {
                    Theme.applyPreviousTheme();
                    return null;
                }

                previousTheme = null;

                ThemeInfo themeInfo = themesDict.get(key);
                if (themeInfo == null) {
                    themeInfo = new ThemeInfo();
                    themeInfo.name = themeName;
                    themeInfo.account = UserConfig.selectedAccount;
                    themes.add(themeInfo);
                    otherThemes.add(themeInfo);
                    sortThemes();
                } else {
                    themesDict.remove(key);
                }
                themeInfo.info = theme;
                themeInfo.pathToFile = finalFile.getAbsolutePath();
                themesDict.put(themeInfo.getKey(), themeInfo);
                saveOtherThemes(true);

                applyTheme(themeInfo, true, true, false);
                return themeInfo;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    public static void applyTheme(ThemeInfo themeInfo) {
        applyTheme(themeInfo, true, true, false);
    }

    public static void applyTheme(ThemeInfo themeInfo, boolean nightTheme) {
        applyTheme(themeInfo, true, true, nightTheme);
    }

    private static void applyTheme(ThemeInfo themeInfo, boolean save, boolean removeWallpaperOverride, final boolean nightTheme) {
        if (themeInfo == null) {
            return;
        }
        ThemeEditorView editorView = ThemeEditorView.getInstance();
        if (editorView != null) {
            editorView.destroy();
        }
        try {
            if (themeInfo.pathToFile != null || themeInfo.assetName != null) {
                if (!nightTheme && save) {
                    SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putString("theme", themeInfo.getKey());
                    if (removeWallpaperOverride) {
                        editor.remove("overrideThemeWallpaper");
                    }
                    editor.commit();
                }
                String[] wallpaperLink = new String[1];
                if (themeInfo.assetName != null) {
                    currentColorsNoAccent = getThemeFileValues(null, themeInfo.assetName, null);
                } else {
                    currentColorsNoAccent = getThemeFileValues(new File(themeInfo.pathToFile), null, wallpaperLink);
                }
                Integer offset = currentColorsNoAccent.get("wallpaperFileOffset");
                themedWallpaperFileOffset = offset != null ? offset : -1;
                if (!TextUtils.isEmpty(wallpaperLink[0])) {
                    themedWallpaperLink = wallpaperLink[0];
                    themeInfo.pathToWallpaper = new File(ApplicationLoader.getFilesDirFixed(), Utilities.MD5(themedWallpaperLink) + ".wp").getAbsolutePath();
                    try {
                        Uri data = Uri.parse(themedWallpaperLink);
                        themeInfo.slug = data.getQueryParameter("slug");
                        long id = Utilities.parseLong(data.getQueryParameter("id"));
                        long pattern = Utilities.parseLong(data.getQueryParameter("pattern"));

                        String mode = data.getQueryParameter("mode");
                        if (mode != null) {
                            mode = mode.toLowerCase();
                            String[] modes = mode.split(" ");
                            if (modes != null && modes.length > 0) {
                                for (int a = 0; a < modes.length; a++) {
                                    if ("blur".equals(modes[a])) {
                                        themeInfo.isBlured = true;
                                    } else if ("motion".equals(modes[a])) {
                                        themeInfo.isMotion = true;
                                    }
                                }
                            }
                        }
                        int intensity = Utilities.parseInt(data.getQueryParameter("intensity"));
                        int backgroundColor = 0;
                        try {
                            String bgColor = data.getQueryParameter("bg_color");
                            if (!TextUtils.isEmpty(bgColor)) {
                                backgroundColor = Integer.parseInt(bgColor, 16) | 0xff000000;
                            }
                        } catch (Exception ignore) {

                        }

                        if (!TextUtils.isEmpty(themeInfo.slug)) {
                            SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
                            if (save) {
                                editor.putString("selectedBackgroundSlug", themeInfo.slug);
                                if (pattern != 0) {
                                    editor.putLong("selectedBackground2", -1);
                                    editor.putLong("selectedPattern", pattern);
                                    isPatternWallpaper = true;
                                } else {
                                    editor.putLong("selectedBackground2", id);
                                    editor.putLong("selectedPattern", 0);
                                    isPatternWallpaper = false;
                                }
                                editor.putBoolean("selectedBackgroundBlurred", themeInfo.isBlured);
                                editor.putInt("selectedColor", backgroundColor);
                                editor.putFloat("selectedIntensity", intensity / 100.0f);
                                isWallpaperMotionPrev = null;
                            } else {
                                isWallpaperMotionPrev = isWallpaperMotion;
                            }
                            editor.putBoolean("selectedBackgroundMotion", isWallpaperMotion = themeInfo.isMotion);
                            editor.commit();
                        }
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                } else {
                    themedWallpaperLink = null;
                }
            } else {
                if (!nightTheme && save) {
                    SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.remove("theme");
                    if (removeWallpaperOverride) {
                        editor.remove("overrideThemeWallpaper");
                    }
                    editor.commit();
                }
                currentColorsNoAccent.clear();
                themedWallpaperFileOffset = 0;
                themedWallpaperLink = null;
                wallpaper = null;
                themedWallpaper = null;
            }
            currentTheme = themeInfo;
            if (!nightTheme && previousTheme == null) {
                currentDayTheme = currentTheme;
            }
            refreshThemeColors();
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (previousTheme == null && !switchingNightTheme) {
            MessagesController.getInstance(themeInfo.account).saveTheme(themeInfo, nightTheme, false);
        }
    }

    private static void refreshThemeColors() {
        currentColors.clear();
        currentColors.putAll(currentColorsNoAccent);
        ThemeInfo themeInfo = currentTheme;

        if (themeInfo.accentColor != 0 && themeInfo.accentBaseColor != 0 && themeInfo.accentColor != themeInfo.accentBaseColor) {
            HashSet<String> keys = new HashSet<>(currentColorsNoAccent.keySet());
            keys.addAll(defaultColors.keySet());
            keys.removeAll(themeAccentExclusionKeys);

            for (String key: keys) {
                Integer color = currentColorsNoAccent.get(key);
                if (color == null) {
                    String fallbackKey = fallbackKeys.get(key);
                    if (fallbackKey != null && currentColorsNoAccent.get(fallbackKey) != null) {
                        continue; // We'll fallback to correct color automatically
                    }
                }
                if (color == null) {
                    color = defaultColors.get(key);
                }

                int newColor = changeColorAccent(themeInfo.accentBaseColorHsv, themeInfo.accentColorHsv, color);
                if (newColor != color) currentColors.put(key, newColor);
            }
        }

        reloadWallpaper();
        applyCommonTheme();
        applyDialogsTheme();
        applyProfileTheme();
        applyChatTheme(false);
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewTheme, false));
    }

    public static int changeColorAccent(ThemeInfo themeInfo, int accent, int color) {
        if (accent == 0 || themeInfo.accentBaseColor == 0 || accent == themeInfo.accentBaseColor) {
            return color;
        }

        Color.colorToHSV(accent, hsv);
        return changeColorAccent(themeInfo.accentBaseColorHsv, hsv, color);
    }

    public static int changeColorAccent(int color) {
        return changeColorAccent(currentTheme, currentTheme.accentColor, color);
    }

    private static int changeColorAccent(float[] baseHsv, float[] accentHsv, int color) {
        final float baseH = baseHsv[0];
        final float baseS = baseHsv[1];
        final float baseV = baseHsv[2];

        final float accentH = accentHsv[0];
        final float accentS = accentHsv[1];
        final float accentV = accentHsv[2];

        Color.colorToHSV(color, hsv);
        final float colorH = hsv[0];
        final float colorS = hsv[1];
        final float colorV = hsv[2];

        // Only changing color's accent if its hue is close to base accent
        final float diffH = Math.min(Math.abs(colorH - baseH), Math.abs(colorH - baseH - 360f));
        if (diffH > 30f) return color;

        // Calculating saturation distance between the color and its base. To preserve better
        // contrast colors closer to base color will receive the most brightness change.
        float dist = Math.min(1.5f * colorS / baseS, 1f);

        hsv[0] = colorH + accentH - baseH;
        hsv[1] = colorS * accentS / baseS;
        hsv[2] = colorV * (1f - dist + dist * accentV / baseV);

        return Color.HSVToColor(Color.alpha(color), hsv);
    }

    public static void applyCurrentThemeAccent(int accent) {
        currentTheme.setAccentColor(accent);
        refreshThemeColors();
    }

    public static void saveThemeAccent(ThemeInfo themeInfo, int accent) {
        if (themeInfo.assetName != null) {
            MessagesController.getGlobalMainSettings().edit().putInt("accent_for_" + themeInfo.assetName, accent).commit();
            themeInfo.setAccentColor(accent);
        }
    }

    private static void saveOtherThemes(boolean full) {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        if (full) {
            JSONArray array = new JSONArray();
            for (int a = 0; a < otherThemes.size(); a++) {
                JSONObject jsonObject = otherThemes.get(a).getSaveJson();
                if (jsonObject != null) {
                    array.put(jsonObject);
                }
            }
            editor.putString("themes2", array.toString());
        }
        editor.putInt("remoteThemesHash", remoteThemesHash);
        editor.putInt("lastLoadingThemesTime", lastLoadingThemesTime);
        editor.putInt("lastLoadingCurrentThemeTime", lastLoadingCurrentThemeTime);
        editor.commit();
    }

    public static HashMap<String, Integer> getDefaultColors() {
        return defaultColors;
    }

    public static String getCurrentThemeName() {
        String text = currentDayTheme.getName();
        if (text.toLowerCase().endsWith(".attheme")) {
            text = text.substring(0, text.lastIndexOf('.'));
        }
        return text;
    }

    public static String getCurrentNightThemeName() {
        if (currentNightTheme == null) {
            return "";
        }
        String text = currentNightTheme.getName();
        if (text.toLowerCase().endsWith(".attheme")) {
            text = text.substring(0, text.lastIndexOf('.'));
        }
        return text;
    }

    public static ThemeInfo getCurrentTheme() {
        return currentDayTheme != null ? currentDayTheme : defaultTheme;
    }

    public static ThemeInfo getCurrentNightTheme() {
        return currentNightTheme;
    }

    public static boolean isCurrentThemeNight() {
        return currentTheme == currentNightTheme;
    }

    private static boolean isCurrentThemeDefault() {
        return currentTheme == defaultTheme;
    }

    public static boolean isThemeDefault(ThemeInfo themeInfo) {
        return themeInfo == defaultTheme;
    }

    private static long getAutoNightSwitchThemeDelay() {
        long newTime = SystemClock.elapsedRealtime();
        if (Math.abs(lastThemeSwitchTime - newTime) >= LIGHT_SENSOR_THEME_SWITCH_NEAR_THRESHOLD) {
            return LIGHT_SENSOR_THEME_SWITCH_DELAY;
        }
        return LIGHT_SENSOR_THEME_SWITCH_NEAR_DELAY;
    }

    private static final float MAXIMUM_LUX_BREAKPOINT = 500.0f;
    private static SensorEventListener ambientSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            float lux = event.values[0];
            if (lux <= 0) {
                lux = 0.1f;
            }
            if (ApplicationLoader.mainInterfacePaused || !ApplicationLoader.isScreenOn) {
                return;
            }
            if (lux > MAXIMUM_LUX_BREAKPOINT) {
                lastBrightnessValue = 1.0f;
            } else {
                lastBrightnessValue = (float) Math.ceil(9.9323f * Math.log(lux) + 27.059f) / 100.0f;
            }
            if (lastBrightnessValue <= autoNightBrighnessThreshold) {
                if (!MediaController.getInstance().isRecordingOrListeningByProximity()) {
                    if (switchDayRunnableScheduled) {
                        switchDayRunnableScheduled = false;
                        AndroidUtilities.cancelRunOnUIThread(switchDayBrightnessRunnable);
                    }
                    if (!switchNightRunnableScheduled) {
                        switchNightRunnableScheduled = true;
                        AndroidUtilities.runOnUIThread(switchNightBrightnessRunnable, getAutoNightSwitchThemeDelay());
                    }
                }
            } else {
                if (switchNightRunnableScheduled) {
                    switchNightRunnableScheduled = false;
                    AndroidUtilities.cancelRunOnUIThread(switchNightBrightnessRunnable);
                }
                if (!switchDayRunnableScheduled) {
                    switchDayRunnableScheduled = true;
                    AndroidUtilities.runOnUIThread(switchDayBrightnessRunnable, getAutoNightSwitchThemeDelay());
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    public static void setCurrentNightTheme(ThemeInfo theme) {
        boolean apply = currentTheme == currentNightTheme;
        currentNightTheme = theme;
        if (apply) {
            applyDayNightThemeMaybe(true);
        }
    }

    public static void checkAutoNightThemeConditions() {
        checkAutoNightThemeConditions(false);
    }

    public static void checkAutoNightThemeConditions(boolean force) {
        if (previousTheme != null) {
            return;
        }
        if (force) {
            if (switchNightRunnableScheduled) {
                switchNightRunnableScheduled = false;
                AndroidUtilities.cancelRunOnUIThread(switchNightBrightnessRunnable);
            }
            if (switchDayRunnableScheduled) {
                switchDayRunnableScheduled = false;
                AndroidUtilities.cancelRunOnUIThread(switchDayBrightnessRunnable);
            }
        }
        if (selectedAutoNightType != AUTO_NIGHT_TYPE_AUTOMATIC) {
            if (switchNightRunnableScheduled) {
                switchNightRunnableScheduled = false;
                AndroidUtilities.cancelRunOnUIThread(switchNightBrightnessRunnable);
            }
            if (switchDayRunnableScheduled) {
                switchDayRunnableScheduled = false;
                AndroidUtilities.cancelRunOnUIThread(switchDayBrightnessRunnable);
            }
            if (lightSensorRegistered) {
                lastBrightnessValue = 1.0f;
                sensorManager.unregisterListener(ambientSensorListener, lightSensor);
                lightSensorRegistered = false;
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("light sensor unregistered");
                }
            }
        }
        int switchToTheme = 0;
        if (selectedAutoNightType == AUTO_NIGHT_TYPE_SCHEDULED) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            int time = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
            int timeStart;
            int timeEnd;
            if (autoNightScheduleByLocation) {
                int day = calendar.get(Calendar.DAY_OF_MONTH);
                if (autoNightLastSunCheckDay != day && autoNightLocationLatitude != 10000 && autoNightLocationLongitude != 10000) {
                    int[] t = SunDate.calculateSunriseSunset(Theme.autoNightLocationLatitude, Theme.autoNightLocationLongitude);
                    autoNightSunriseTime = t[0];
                    autoNightSunsetTime = t[1];
                    autoNightLastSunCheckDay = day;
                    saveAutoNightThemeConfig();
                }
                timeStart = autoNightSunsetTime;
                timeEnd = autoNightSunriseTime;
            } else {
                timeStart = autoNightDayStartTime;
                timeEnd = autoNightDayEndTime;
            }
            if (timeStart < timeEnd) {
                if (timeStart <= time && time <= timeEnd) {
                    switchToTheme = 2;
                } else {
                    switchToTheme = 1;
                }
            } else {
                if (timeStart <= time && time <= 24 * 60 || 0 <= time && time <= timeEnd) {
                    switchToTheme = 2;
                } else {
                    switchToTheme = 1;
                }
            }
        } else if (selectedAutoNightType == AUTO_NIGHT_TYPE_AUTOMATIC) {
            if (lightSensor == null) {
                sensorManager = (SensorManager) ApplicationLoader.applicationContext.getSystemService(Context.SENSOR_SERVICE);
                lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            }
            if (!lightSensorRegistered && lightSensor != null) {
                sensorManager.registerListener(ambientSensorListener, lightSensor, 500000);
                lightSensorRegistered = true;
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("light sensor registered");
                }
            }
            if (lastBrightnessValue <= autoNightBrighnessThreshold) {
                if (!switchNightRunnableScheduled) {
                    switchToTheme = 2;
                }
            } else {
                if (!switchDayRunnableScheduled) {
                    switchToTheme = 1;
                }
            }
        } else if (selectedAutoNightType == AUTO_NIGHT_TYPE_NONE) {
            switchToTheme = 1;
        }
        if (switchToTheme != 0) {
            applyDayNightThemeMaybe(switchToTheme == 2);
        }
        if (force) {
            lastThemeSwitchTime = 0;
        }
    }

    private static void applyDayNightThemeMaybe(boolean night) {
        if (previousTheme != null) {
            return;
        }

        if (night) {
            if (currentTheme != currentNightTheme) {
                isInNigthMode = true;
                lastThemeSwitchTime = SystemClock.elapsedRealtime();
                switchingNightTheme = true;
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, currentNightTheme, true);
                switchingNightTheme = false;
            }
        } else {
            if (currentTheme != currentDayTheme) {
                isInNigthMode = false;
                lastThemeSwitchTime = SystemClock.elapsedRealtime();
                switchingNightTheme = true;
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme, currentDayTheme, true);
                switchingNightTheme = false;
            }
        }
    }

    public static boolean deleteTheme(ThemeInfo themeInfo) {
        if (themeInfo.pathToFile == null) {
            return false;
        }
        boolean currentThemeDeleted = false;
        if (currentTheme == themeInfo) {
            applyTheme(defaultTheme, true, false, false);
            currentThemeDeleted = true;
        }
        if (themeInfo == currentNightTheme) {
            currentNightTheme = themesDict.get("Dark Blue");
        }

        themeInfo.removeObservers();
        otherThemes.remove(themeInfo);
        themesDict.remove(themeInfo.name);
        themes.remove(themeInfo);
        File file = new File(themeInfo.pathToFile);
        file.delete();
        saveOtherThemes(true);
        return currentThemeDeleted;
    }

    public static ThemeInfo createNewTheme(String name) {
        ThemeInfo newTheme = new ThemeInfo();
        newTheme.pathToFile = new File(ApplicationLoader.getFilesDirFixed(), "theme" + Utilities.random.nextLong() + ".attheme").getAbsolutePath();
        newTheme.name = name;
        newTheme.account = UserConfig.selectedAccount;
        saveCurrentTheme(newTheme, true, true, false);
        return newTheme;
    }

    public static void saveCurrentTheme(ThemeInfo themeInfo, boolean finalSave, boolean newTheme, boolean upload) {
        String wallpaperLink = null;

        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        boolean overrideThemeWallpaper = preferences.getBoolean("overrideThemeWallpaper", false);
        String slug = preferences.getString("selectedBackgroundSlug", null);
        long id = preferences.getLong("selectedBackground2", DEFAULT_BACKGROUND_ID);
        long pattern = preferences.getLong("selectedPattern", 0);
        if (upload) {
            if (!TextUtils.isEmpty(slug) && (id != DEFAULT_BACKGROUND_ID || pattern != 0) && (overrideThemeWallpaper || !hasWallpaperFromTheme())) {
                boolean isBlurred = preferences.getBoolean("selectedBackgroundBlurred", false);
                boolean isMotion = preferences.getBoolean("selectedBackgroundMotion", false);
                int selectedColor = preferences.getInt("selectedColor", 0);
                float intensity = preferences.getFloat("selectedIntensity", 1.0f);
                StringBuilder modes = new StringBuilder();
                if (isBlurred) {
                    modes.append("blur");
                }
                if (isMotion) {
                    if (modes.length() > 0) {
                        modes.append("+");
                    }
                    modes.append("motion");
                }
                if (id != -1) {
                    wallpaperLink = "https://attheme.org?slug=" + slug + "&id=" + id;
                } else {
                    String color = String.format("%02x%02x%02x", (byte) (selectedColor >> 16) & 0xff, (byte) (selectedColor >> 8) & 0xff, (byte) (selectedColor & 0xff)).toLowerCase();
                    wallpaperLink = "https://attheme.org?slug=" + slug + "&intensity=" + (int) (intensity * 100) + "&bg_color=" + color + "&pattern=" + pattern;
                }
                if (modes.length() > 0) {
                    wallpaperLink += "&mode=" + modes.toString();
                }
            }
        } else {
            wallpaperLink = themedWallpaperLink;
        }

        Drawable wallpaperToSave = newTheme ? wallpaper : themedWallpaper;
        if (newTheme && wallpaperToSave != null) {
            themedWallpaper = wallpaper;
        }
        StringBuilder result = new StringBuilder();
        for (HashMap.Entry<String, Integer> entry : currentColors.entrySet()) {
            String key = entry.getKey();
            if (wallpaperToSave instanceof BitmapDrawable || wallpaperLink != null) {
                if (Theme.key_chat_wallpaper.equals(key) || Theme.key_chat_wallpaper_gradient_to.equals(key)) {
                    continue;
                }
            }
            result.append(key).append("=").append(entry.getValue()).append("\n");
        }
        FileOutputStream stream = null;
        try {
            stream = new FileOutputStream(themeInfo.pathToFile);
            if (result.length() == 0 && !(wallpaperToSave instanceof BitmapDrawable) && TextUtils.isEmpty(wallpaperLink)) {
                result.append(' ');
            }
            stream.write(AndroidUtilities.getStringBytes(result.toString()));
            if (!TextUtils.isEmpty(wallpaperLink)) {
                stream.write(AndroidUtilities.getStringBytes("WLS=" + wallpaperLink + "\n"));
                if (newTheme) {
                    try {
                        Bitmap bitmap = ((BitmapDrawable) wallpaperToSave).getBitmap();
                        FileOutputStream wallpaperStream = new FileOutputStream(new File(ApplicationLoader.getFilesDirFixed(), Utilities.MD5(wallpaperLink) + ".wp"));
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 87, wallpaperStream);
                        wallpaperStream.close();
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }
            } else if (wallpaperToSave instanceof BitmapDrawable) {
                Bitmap bitmap = ((BitmapDrawable) wallpaperToSave).getBitmap();
                if (bitmap != null) {
                    stream.write(new byte[]{'W', 'P', 'S', '\n'});
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 87, stream);
                    stream.write(new byte[]{'\n', 'W', 'P', 'E', '\n'});
                }
                if (finalSave && !upload) {
                    wallpaper = wallpaperToSave;
                    calcBackgroundColor(wallpaperToSave, 2);
                }
            }
            if (!upload) {
                if (themesDict.get(themeInfo.getKey()) == null) {
                    themes.add(themeInfo);
                    themesDict.put(themeInfo.getKey(), themeInfo);
                    otherThemes.add(themeInfo);
                    saveOtherThemes(true);
                    sortThemes();
                }
                currentTheme = themeInfo;
                if (currentTheme != currentNightTheme) {
                    currentDayTheme = currentTheme;
                }
                preferences = MessagesController.getGlobalMainSettings();
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString("theme", currentDayTheme.getKey());
                editor.commit();
            }
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        if (finalSave) {
            MessagesController.getInstance(themeInfo.account).saveThemeToServer(themeInfo);
        }
    }

    public static void checkCurrentRemoteTheme(boolean force) {
        if (loadingCurrentTheme != 0 || !force && Math.abs(System.currentTimeMillis() / 1000 - lastLoadingCurrentThemeTime) < 60 * 60) {
            return;
        }
        for (int a = 0; a < 2; a++) {
            ThemeInfo themeInfo = a == 0 ? currentDayTheme : currentNightTheme;
            if (themeInfo == null || themeInfo.info == null || themeInfo.info.document == null || !UserConfig.getInstance(themeInfo.account).isClientActivated()) {
                continue;
            }
            loadingCurrentTheme++;
            TLRPC.TL_account_getTheme req = new TLRPC.TL_account_getTheme();
            req.document_id = themeInfo.info.document.id;
            req.format = "android";
            TLRPC.TL_inputTheme inputTheme = new TLRPC.TL_inputTheme();
            inputTheme.access_hash = themeInfo.info.access_hash;
            inputTheme.id = themeInfo.info.id;
            req.theme = inputTheme;
            ConnectionsManager.getInstance(themeInfo.account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                loadingCurrentTheme--;
                boolean changed = false;
                if (response instanceof TLRPC.TL_theme) {
                    TLRPC.TL_theme theme = (TLRPC.TL_theme) response;
                    if (theme.document != null) {
                        themeInfo.info = theme;
                        themeInfo.loadThemeDocument();
                        changed = true;
                    }
                }
                if (loadingCurrentTheme == 0) {
                    lastLoadingCurrentThemeTime = (int) (System.currentTimeMillis() / 1000);
                    saveOtherThemes(changed);
                }
            }));
        }
    }

    public static void loadRemoteThemes(final int currentAccount, boolean force) {
        if (loadingRemoteThemes || !force && Math.abs(System.currentTimeMillis() / 1000 - lastLoadingThemesTime) < 60 * 60 || !UserConfig.getInstance(currentAccount).isClientActivated()) {
            return;
        }
        loadingRemoteThemes = true;
        TLRPC.TL_account_getThemes req = new TLRPC.TL_account_getThemes();
        req.format = "android";
        req.hash = remoteThemesHash;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            loadingRemoteThemes = false;
            if (response instanceof TLRPC.TL_account_themes) {
                TLRPC.TL_account_themes res = (TLRPC.TL_account_themes) response;
                remoteThemesHash = res.hash;
                lastLoadingThemesTime = (int) (System.currentTimeMillis() / 1000);
                ArrayList<ThemeInfo> oldServerThemes = new ArrayList<>();
                for (int a = 0, N = otherThemes.size(); a < N; a++) {
                    ThemeInfo info = otherThemes.get(a);
                    if (info.info != null && info.account == currentAccount) {
                        oldServerThemes.add(info);
                    }
                }
                boolean added = false;
                for (int a = 0, N = res.themes.size(); a < N; a++) {
                    TLRPC.Theme t = res.themes.get(a);
                    if (!(t instanceof TLRPC.TL_theme)) {
                        continue;
                    }
                    TLRPC.TL_theme theme = (TLRPC.TL_theme) t;
                    String key = "remote" + theme.id;
                    ThemeInfo info = themesDict.get(key);
                    if (info == null) {
                        info = new ThemeInfo();
                        info.account = currentAccount;
                        info.pathToFile = new File(ApplicationLoader.getFilesDirFixed(), key + ".attheme").getAbsolutePath();
                        themes.add(info);
                        otherThemes.add(info);
                        added = true;
                    } else {
                        oldServerThemes.remove(info);
                    }
                    info.name = theme.title;
                    info.info = theme;
                    themesDict.put(info.getKey(), info);
                }
                for (int a = 0, N = oldServerThemes.size(); a < N; a++) {
                    ThemeInfo info = oldServerThemes.get(a);
                    info.removeObservers();
                    otherThemes.remove(info);
                    themesDict.remove(info.name);
                    themes.remove(info);
                    File file = new File(info.pathToFile);
                    file.delete();
                    boolean isNightTheme = false;
                    if (currentDayTheme == info) {
                        currentDayTheme = defaultTheme;
                    } else if (currentNightTheme == info) {
                        currentNightTheme = themesDict.get("Dark Blue");
                        isNightTheme = true;
                    }
                    if (currentTheme == info) {
                        applyTheme(isNightTheme ? currentNightTheme : currentDayTheme, true, false, isNightTheme);
                    }
                }
                saveOtherThemes(true);
                sortThemes();
                if (added) {
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.themeListUpdated);
                }
            }
        }));
    }

    public static void setThemeFileReference(TLRPC.TL_theme info) {
        for (int a = 0, N = Theme.themes.size(); a < N; a++) {
            Theme.ThemeInfo themeInfo = Theme.themes.get(a);
            if (themeInfo.info != null && themeInfo.info.id == info.id) {
                if (themeInfo.info.document != null && info.document != null) {
                    themeInfo.info.document.file_reference = info.document.file_reference;
                    saveOtherThemes(true);
                }
                break;
            }
        }
    }

    public static boolean isThemeInstalled(ThemeInfo themeInfo) {
        return themeInfo != null && themesDict.get(themeInfo.getKey()) != null;
    }

    public static void setThemeUploadInfo(ThemeInfo theme, TLRPC.TL_theme info, boolean update) {
        if (info == null) {
            return;
        }
        String key = "remote" + info.id;
        if (theme != null) {
            themesDict.remove(theme.getKey());
        } else {
            theme = themesDict.get(key);
        }
        if (theme == null) {
            return;
        }
        theme.info = info;
        theme.name = info.title;
        File oldPath = new File(theme.pathToFile);
        File newPath = new File(ApplicationLoader.getFilesDirFixed(), key + ".attheme");
        if (!oldPath.equals(newPath)) {
            try {
                AndroidUtilities.copyFile(oldPath, newPath);
                theme.pathToFile = newPath.getAbsolutePath();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        if (update) {
            theme.loadThemeDocument();
        } else {
            theme.previewParsed = false;
        }
        themesDict.put(theme.getKey(), theme);
        saveOtherThemes(true);
    }

    public static File getAssetFile(String assetName) {
        File file = new File(ApplicationLoader.getFilesDirFixed(), assetName);
        long size;
        try {
            InputStream stream = ApplicationLoader.applicationContext.getAssets().open(assetName);
            size = stream.available();
            stream.close();
        } catch (Exception e) {
            size = 0;
            FileLog.e(e);
        }
        if (!file.exists() || size != 0 && file.length() != size) {
            try (InputStream in = ApplicationLoader.applicationContext.getAssets().open(assetName)) {
                AndroidUtilities.copyFile(in, file);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return file;
    }

    private static int getPreviewColor(HashMap<String, Integer> colors, String key) {
        Integer color = colors.get(key);
        if (color == null) {
            color = defaultColors.get(key);
        }
        return color;
    }

    public static String createThemePreviewImage(ThemeInfo themeInfo) {
        try {
            String[] wallpaperLink = new String[1];
            HashMap<String, Integer> colors = getThemeFileValues(new File(themeInfo.pathToFile), null, wallpaperLink);
            Integer wallpaperFileOffset = colors.get("wallpaperFileOffset");
            Bitmap bitmap = Bitmaps.createBitmap(560, 678, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            Paint paint = new Paint();

            int actionBarColor = getPreviewColor(colors, key_actionBarDefault);
            int actionBarIconColor = getPreviewColor(colors, key_actionBarDefaultIcon);
            int messageFieldColor = getPreviewColor(colors, key_chat_messagePanelBackground);
            int messageFieldIconColor = getPreviewColor(colors, key_chat_messagePanelIcons);
            int messageInColor = getPreviewColor(colors, key_chat_inBubble);
            int messageOutColor = getPreviewColor(colors, key_chat_outBubble);
            Integer backgroundColor = colors.get(key_chat_wallpaper);
            Integer serviceColor = colors.get(key_chat_serviceBackground);
            Integer gradientToColor = colors.get(key_chat_wallpaper_gradient_to);

            Drawable backDrawable = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.preview_back).mutate();
            setDrawableColor(backDrawable, actionBarIconColor);
            Drawable otherDrawable = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.preview_dots).mutate();
            setDrawableColor(otherDrawable, actionBarIconColor);
            Drawable emojiDrawable = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.preview_smile).mutate();
            setDrawableColor(emojiDrawable, messageFieldIconColor);
            Drawable micDrawable = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.preview_mic).mutate();
            setDrawableColor(micDrawable, messageFieldIconColor);
            Drawable msgInDrawable = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.preview_msg_in).mutate();
            setDrawableColor(msgInDrawable, messageInColor);
            Drawable msgOutDrawable = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.preview_msg_out).mutate();
            setDrawableColor(msgOutDrawable, messageOutColor);
            RectF rect = new RectF();

            boolean hasBackground = false;
            if (backgroundColor != null) {
                Drawable wallpaperDrawable;
                if (gradientToColor == null) {
                    wallpaperDrawable = new ColorDrawable(backgroundColor);
                } else {
                    wallpaperDrawable = new BackgroundGradientDrawable(GradientDrawable.Orientation.BL_TR, new int[] { backgroundColor, gradientToColor });
                }
                wallpaperDrawable.setBounds(0, 120, bitmap.getWidth(), bitmap.getHeight() - 120);
                wallpaperDrawable.draw(canvas);
                if (serviceColor == null) {
                    serviceColor = AndroidUtilities.calcDrawableColor(new ColorDrawable(backgroundColor))[0];
                }
                hasBackground = true;
            } else if (wallpaperFileOffset != null && wallpaperFileOffset >= 0 || !TextUtils.isEmpty(wallpaperLink[0])) {
                FileInputStream stream = null;
                File pathToWallpaper = null;
                try {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    if (!TextUtils.isEmpty(wallpaperLink[0])) {
                        pathToWallpaper = new File(ApplicationLoader.getFilesDirFixed(), Utilities.MD5(wallpaperLink[0]) + ".wp");
                        BitmapFactory.decodeFile(pathToWallpaper.getAbsolutePath(), options);
                    } else {
                        stream = new FileInputStream(themeInfo.pathToFile);
                        stream.getChannel().position(wallpaperFileOffset);
                        BitmapFactory.decodeStream(stream, null, options);
                    }
                    if (options.outWidth > 0 && options.outHeight > 0) {
                        float scale = Math.min(options.outWidth / 560.0f, options.outHeight / 560.0f);
                        options.inSampleSize = 1;
                        if (scale > 1.0f) {
                            do {
                                options.inSampleSize *= 2;
                            } while (options.inSampleSize < scale);
                        }
                        options.inJustDecodeBounds = false;
                        Bitmap wallpaper;
                        if (pathToWallpaper != null) {
                            wallpaper = BitmapFactory.decodeFile(pathToWallpaper.getAbsolutePath(), options);
                        } else {
                            stream.getChannel().position(wallpaperFileOffset);
                            wallpaper = BitmapFactory.decodeStream(stream, null, options);
                        }
                        if (wallpaper != null) {
                            Paint bitmapPaint = new Paint();
                            bitmapPaint.setFilterBitmap(true);
                            scale = Math.min(wallpaper.getWidth() / 560.0f, wallpaper.getHeight() / 560.0f);
                            rect.set(0, 0, wallpaper.getWidth() / scale, wallpaper.getHeight() / scale);
                            rect.offset((bitmap.getWidth() - rect.width()) / 2, (bitmap.getHeight() - rect.height()) / 2);
                            canvas.drawBitmap(wallpaper, null, rect, bitmapPaint);
                            hasBackground = true;
                            if (serviceColor == null) {
                                serviceColor = AndroidUtilities.calcDrawableColor(new BitmapDrawable(wallpaper))[0];
                            }
                        }
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                } finally {
                    try {
                        if (stream != null) {
                            stream.close();
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            }
            if (!hasBackground) {
                BitmapDrawable catsDrawable = (BitmapDrawable) ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.catstile).mutate();
                if (serviceColor == null) {
                    serviceColor = AndroidUtilities.calcDrawableColor(catsDrawable)[0];
                }
                catsDrawable.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
                catsDrawable.setBounds(0, 120, bitmap.getWidth(), bitmap.getHeight() - 120);
                catsDrawable.draw(canvas);
            }

            paint.setColor(actionBarColor);
            canvas.drawRect(0, 0, bitmap.getWidth(), 120, paint);

            if (backDrawable != null) {
                int x = 13;
                int y = (120 - backDrawable.getIntrinsicHeight()) / 2;
                backDrawable.setBounds(x, y, x + backDrawable.getIntrinsicWidth(), y + backDrawable.getIntrinsicHeight());
                backDrawable.draw(canvas);
            }
            if (otherDrawable != null) {
                int x = bitmap.getWidth() - otherDrawable.getIntrinsicWidth() - 10;
                int y = (120 - otherDrawable.getIntrinsicHeight()) / 2;
                otherDrawable.setBounds(x, y, x + otherDrawable.getIntrinsicWidth(), y + otherDrawable.getIntrinsicHeight());
                otherDrawable.draw(canvas);
            }
            if (msgOutDrawable != null) {
                msgOutDrawable.setBounds(161, 216, bitmap.getWidth() - 20, 216 + 92);
                msgOutDrawable.draw(canvas);

                msgOutDrawable.setBounds(161, 430, bitmap.getWidth() - 20, 430 + 92);
                msgOutDrawable.draw(canvas);
            }
            if (msgInDrawable != null) {
                msgInDrawable.setBounds(20, 323, 399, 323 + 92);
                msgInDrawable.draw(canvas);
            }
            if (serviceColor != null) {
                int x = (bitmap.getWidth() - 126) / 2;
                int y = 150;
                rect.set(x, y, x + 126, y + 42);
                paint.setColor(serviceColor);
                canvas.drawRoundRect(rect, 21, 21, paint);
            }

            paint.setColor(messageFieldColor);
            canvas.drawRect(0, bitmap.getHeight() - 120, bitmap.getWidth(), bitmap.getHeight(), paint);
            if (emojiDrawable != null) {
                int x = 22;
                int y = bitmap.getHeight() - 120 + (120 - emojiDrawable.getIntrinsicHeight()) / 2;
                emojiDrawable.setBounds(x, y, x + emojiDrawable.getIntrinsicWidth(), y + emojiDrawable.getIntrinsicHeight());
                emojiDrawable.draw(canvas);
            }
            if (micDrawable != null) {
                int x = bitmap.getWidth() - micDrawable.getIntrinsicWidth() - 22;
                int y = bitmap.getHeight() - 120 + (120 - micDrawable.getIntrinsicHeight()) / 2;
                micDrawable.setBounds(x, y, x + micDrawable.getIntrinsicWidth(), y + micDrawable.getIntrinsicHeight());
                micDrawable.draw(canvas);
            }
            canvas.setBitmap(null);

            String fileName = Integer.MIN_VALUE + "_" + SharedConfig.getLastLocalId() + ".jpg";
            final File cacheFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
            try {
                FileOutputStream stream = new FileOutputStream(cacheFile);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream);
                SharedConfig.saveConfig();
                return cacheFile.getAbsolutePath();
            } catch (Throwable e) {
                FileLog.e(e);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return null;
    }

    private static HashMap<String, Integer> getThemeFileValues(File file, String assetName, String[] wallpaperLink) {
        FileInputStream stream = null;
        HashMap<String, Integer> stringMap = new HashMap<>();
        try {
            byte[] bytes = new byte[1024];
            int currentPosition = 0;
            if (assetName != null) {
                file = getAssetFile(assetName);
            }
            stream = new FileInputStream(file);
            int idx;
            int read;
            boolean finished = false;
            int wallpaperFileOffset = -1;
            while ((read = stream.read(bytes)) != -1) {
                int previousPosition = currentPosition;
                int start = 0;
                for (int a = 0; a < read; a++) {
                    if (bytes[a] == '\n') {
                        int len = a - start + 1;
                        String line = new String(bytes, start, len - 1);
                        if (line.startsWith("WLS=")) {
                            if (wallpaperLink != null && wallpaperLink.length > 0) {
                                wallpaperLink[0] = line.substring(4);
                            }
                        } else if (line.startsWith("WPS")) {
                            wallpaperFileOffset = currentPosition + len;
                            finished = true;
                            break;
                        } else {
                            if ((idx = line.indexOf('=')) != -1) {
                                String key = line.substring(0, idx);
                                String param = line.substring(idx + 1);
                                int value;
                                if (param.length() > 0 && param.charAt(0) == '#') {
                                    try {
                                        value = Color.parseColor(param);
                                    } catch (Exception ignore) {
                                        value = Utilities.parseInt(param);
                                    }
                                } else {
                                    value = Utilities.parseInt(param);
                                }
                                stringMap.put(key, value);
                            }
                        }
                        start += len;
                        currentPosition += len;
                    }
                }
                if (previousPosition == currentPosition) {
                    break;
                }
                stream.getChannel().position(currentPosition);
                if (finished) {
                    break;
                }
            }
            stringMap.put("wallpaperFileOffset", wallpaperFileOffset);
        } catch (Throwable e) {
            FileLog.e(e);
        } finally {
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return stringMap;
    }

    public static void createCommonResources(Context context) {
        if (dividerPaint == null) {
            dividerPaint = new Paint();
            dividerPaint.setStrokeWidth(1);

            avatar_backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            checkboxSquare_checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            checkboxSquare_checkPaint.setStyle(Paint.Style.STROKE);
            checkboxSquare_checkPaint.setStrokeWidth(AndroidUtilities.dp(2));
            checkboxSquare_eraserPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            checkboxSquare_eraserPaint.setColor(0);
            checkboxSquare_eraserPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            checkboxSquare_backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            linkSelectionPaint = new Paint();

            Resources resources = context.getResources();

            avatar_savedDrawable = resources.getDrawable(R.drawable.chats_saved);
            avatar_ghostDrawable = resources.getDrawable(R.drawable.ghost);

            if (dialogs_archiveAvatarDrawable != null) {
                dialogs_archiveAvatarDrawable.setCallback(null);
                dialogs_archiveAvatarDrawable.recycle();
            }
            if (dialogs_archiveDrawable != null) {
                dialogs_archiveDrawable.recycle();
            }
            if (dialogs_unarchiveDrawable != null) {
                dialogs_unarchiveDrawable.recycle();
            }
            if (dialogs_pinArchiveDrawable != null) {
                dialogs_pinArchiveDrawable.recycle();
            }
            if (dialogs_unpinArchiveDrawable != null) {
                dialogs_unpinArchiveDrawable.recycle();
            }
            dialogs_archiveAvatarDrawable = new RLottieDrawable(R.raw.chats_archiveavatar, "chats_archiveavatar", AndroidUtilities.dp(36), AndroidUtilities.dp(36), false);
            dialogs_archiveDrawable = new RLottieDrawable(R.raw.chats_archive, "chats_archive", AndroidUtilities.dp(36), AndroidUtilities.dp(36));
            dialogs_unarchiveDrawable = new RLottieDrawable(R.raw.chats_unarchive, "chats_unarchive", AndroidUtilities.dp(AndroidUtilities.dp(36)), AndroidUtilities.dp(36));
            dialogs_pinArchiveDrawable = new RLottieDrawable(R.raw.chats_hide, "chats_hide", AndroidUtilities.dp(36), AndroidUtilities.dp(36));
            dialogs_unpinArchiveDrawable = new RLottieDrawable(R.raw.chats_unhide, "chats_unhide", AndroidUtilities.dp(36), AndroidUtilities.dp(36));
            
            applyCommonTheme();
        }
    }

    public static void applyCommonTheme() {
        if (dividerPaint == null) {
            return;
        }
        dividerPaint.setColor(getColor(key_divider));
        linkSelectionPaint.setColor(getColor(key_windowBackgroundWhiteLinkSelection));

        setDrawableColorByKey(avatar_savedDrawable, key_avatar_text);

        dialogs_archiveAvatarDrawable.setLayerColor("Arrow1.**", getColor(key_avatar_backgroundArchived));
        dialogs_archiveAvatarDrawable.setLayerColor("Arrow2.**", getColor(key_avatar_backgroundArchived));
        dialogs_archiveAvatarDrawable.setLayerColor("Box2.**", getColor(key_avatar_text));
        dialogs_archiveAvatarDrawable.setLayerColor("Box1.**", getColor(key_avatar_text));
        dialogs_archiveAvatarDrawableRecolored = false;
        dialogs_archiveAvatarDrawable.setAllowDecodeSingleFrame(true);
        
        dialogs_pinArchiveDrawable.setLayerColor("Arrow.**", getColor(key_chats_archiveIcon));
        dialogs_pinArchiveDrawable.setLayerColor("Line.**", getColor(key_chats_archiveIcon));
        
        dialogs_unpinArchiveDrawable.setLayerColor("Arrow.**", getColor(key_chats_archiveIcon));
        dialogs_unpinArchiveDrawable.setLayerColor("Line.**", getColor(key_chats_archiveIcon));

        dialogs_archiveDrawable.setLayerColor("Arrow.**", getColor(key_chats_archiveBackground));
        dialogs_archiveDrawable.setLayerColor("Box2.**", getColor(key_chats_archiveIcon));
        dialogs_archiveDrawable.setLayerColor("Box1.**", getColor(key_chats_archiveIcon));
        dialogs_archiveDrawableRecolored = false;

        dialogs_unarchiveDrawable.setLayerColor("Arrow1.**", getColor(key_chats_archiveIcon));
        dialogs_unarchiveDrawable.setLayerColor("Arrow2.**", getColor(key_chats_archivePinBackground));
        dialogs_unarchiveDrawable.setLayerColor("Box2.**", getColor(key_chats_archiveIcon));
        dialogs_unarchiveDrawable.setLayerColor("Box1.**", getColor(key_chats_archiveIcon));
    }

    public static void createDialogsResources(Context context) {
        createCommonResources(context);
        if (dialogs_namePaint == null) {
            Resources resources = context.getResources();

            dialogs_namePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            dialogs_namePaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            dialogs_nameEncryptedPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            dialogs_nameEncryptedPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            dialogs_searchNamePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            dialogs_searchNamePaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            dialogs_searchNameEncryptedPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            dialogs_searchNameEncryptedPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            dialogs_messagePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            dialogs_messageNamePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            dialogs_messageNamePaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            dialogs_messagePrintingPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            dialogs_timePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            dialogs_countTextPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            dialogs_countTextPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            dialogs_archiveTextPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            dialogs_archiveTextPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            dialogs_onlinePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            dialogs_offlinePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);

            dialogs_tabletSeletedPaint = new Paint();
            dialogs_pinnedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            dialogs_onlineCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            dialogs_countPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            dialogs_countGrayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            dialogs_errorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            dialogs_lockDrawable = resources.getDrawable(R.drawable.list_secret);
            dialogs_checkDrawable = resources.getDrawable(R.drawable.list_check).mutate();
            dialogs_checkReadDrawable = resources.getDrawable(R.drawable.list_check).mutate();
            dialogs_halfCheckDrawable = resources.getDrawable(R.drawable.list_halfcheck);
            dialogs_clockDrawable = resources.getDrawable(R.drawable.msg_clock).mutate();
            dialogs_errorDrawable = resources.getDrawable(R.drawable.list_warning_sign);
            dialogs_reorderDrawable = resources.getDrawable(R.drawable.list_reorder);
            dialogs_groupDrawable = resources.getDrawable(R.drawable.list_group);
            dialogs_broadcastDrawable = resources.getDrawable(R.drawable.list_broadcast);
            dialogs_muteDrawable = resources.getDrawable(R.drawable.list_mute).mutate();
            dialogs_verifiedDrawable = resources.getDrawable(R.drawable.verified_area);
            dialogs_scamDrawable = new ScamDrawable(11);
            dialogs_verifiedCheckDrawable = resources.getDrawable(R.drawable.verified_check);
            dialogs_mentionDrawable = resources.getDrawable(R.drawable.mentionchatslist);
            dialogs_botDrawable = resources.getDrawable(R.drawable.list_bot);
            dialogs_pinnedDrawable = resources.getDrawable(R.drawable.list_pin);
            moveUpDrawable = resources.getDrawable(R.drawable.preview_open);

            applyDialogsTheme();
        }

        dialogs_messageNamePaint.setTextSize(AndroidUtilities.dp(14));
        dialogs_timePaint.setTextSize(AndroidUtilities.dp(13));
        dialogs_countTextPaint.setTextSize(AndroidUtilities.dp(13));
        dialogs_archiveTextPaint.setTextSize(AndroidUtilities.dp(13));
        dialogs_onlinePaint.setTextSize(AndroidUtilities.dp(15));
        dialogs_offlinePaint.setTextSize(AndroidUtilities.dp(15));
        dialogs_searchNamePaint.setTextSize(AndroidUtilities.dp(16));
        dialogs_searchNameEncryptedPaint.setTextSize(AndroidUtilities.dp(16));
    }

    public static void applyDialogsTheme() {
        if (dialogs_namePaint == null) {
            return;
        }
        dialogs_namePaint.setColor(getColor(key_chats_name));
        dialogs_nameEncryptedPaint.setColor(getColor(key_chats_secretName));
        dialogs_searchNamePaint.setColor(getColor(key_chats_name));
        dialogs_searchNameEncryptedPaint.setColor(getColor(key_chats_secretName));
        dialogs_messagePaint.setColor(dialogs_messagePaint.linkColor = getColor(key_chats_message));
        dialogs_messageNamePaint.setColor(dialogs_messageNamePaint.linkColor = getColor(key_chats_nameMessage_threeLines));
        dialogs_tabletSeletedPaint.setColor(getColor(key_chats_tabletSelectedOverlay));
        dialogs_pinnedPaint.setColor(getColor(key_chats_pinnedOverlay));
        dialogs_timePaint.setColor(getColor(key_chats_date));
        dialogs_countTextPaint.setColor(getColor(key_chats_unreadCounterText));
        dialogs_archiveTextPaint.setColor(getColor(key_chats_archiveText));
        dialogs_messagePrintingPaint.setColor(getColor(key_chats_actionMessage));
        dialogs_countPaint.setColor(getColor(key_chats_unreadCounter));
        dialogs_countGrayPaint.setColor(getColor(key_chats_unreadCounterMuted));
        dialogs_errorPaint.setColor(getColor(key_chats_sentError));
        dialogs_onlinePaint.setColor(getColor(key_windowBackgroundWhiteBlueText3));
        dialogs_offlinePaint.setColor(getColor(key_windowBackgroundWhiteGrayText3));

        setDrawableColorByKey(dialogs_lockDrawable, key_chats_secretIcon);
        setDrawableColorByKey(dialogs_checkDrawable, key_chats_sentCheck);
        setDrawableColorByKey(dialogs_checkReadDrawable, key_chats_sentReadCheck);
        setDrawableColorByKey(dialogs_halfCheckDrawable, key_chats_sentReadCheck);
        setDrawableColorByKey(dialogs_clockDrawable, key_chats_sentClock);
        setDrawableColorByKey(dialogs_errorDrawable, key_chats_sentErrorIcon);
        setDrawableColorByKey(dialogs_groupDrawable, key_chats_nameIcon);
        setDrawableColorByKey(dialogs_broadcastDrawable, key_chats_nameIcon);
        setDrawableColorByKey(dialogs_botDrawable, key_chats_nameIcon);
        setDrawableColorByKey(dialogs_pinnedDrawable, key_chats_pinnedIcon);
        setDrawableColorByKey(dialogs_reorderDrawable, key_chats_pinnedIcon);
        setDrawableColorByKey(dialogs_muteDrawable, key_chats_muteIcon);
        setDrawableColorByKey(dialogs_mentionDrawable, key_chats_mentionIcon);
        setDrawableColorByKey(dialogs_verifiedDrawable, key_chats_verifiedBackground);
        setDrawableColorByKey(dialogs_verifiedCheckDrawable, key_chats_verifiedCheck);
        setDrawableColorByKey(dialogs_holidayDrawable, key_actionBarDefaultTitle);
        setDrawableColorByKey(dialogs_scamDrawable, key_chats_draft);
    }

    public static void destroyResources() {
        for (int a = 0; a < chat_attachButtonDrawables.length; a++) {
            if (chat_attachButtonDrawables[a] != null) {
                chat_attachButtonDrawables[a].setCallback(null);
            }
        }
    }

    public static void reloadAllResources(Context context) {
        destroyResources();
        if (chat_msgInDrawable != null) {
            chat_msgInDrawable = null;
            currentColor = 0;
            currentSelectedColor = 0;
            createChatResources(context, false);
        }
        if (dialogs_namePaint != null) {
            dialogs_namePaint = null;
            createDialogsResources(context);
        }
        if (profile_verifiedDrawable != null) {
            profile_verifiedDrawable = null;
            createProfileResources(context);
        }
    }

    public static void createChatResources(Context context, boolean fontsOnly) {
        synchronized (sync) {
            if (chat_msgTextPaint == null) {
                chat_msgTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                chat_msgGameTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                chat_msgTextPaintOneEmoji = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                chat_msgTextPaintTwoEmoji = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                chat_msgTextPaintThreeEmoji = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                chat_msgBotButtonPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                chat_msgBotButtonPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            }
        }

        if (!fontsOnly && chat_msgInDrawable == null) {
            chat_infoPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_docNamePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_docNamePaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            chat_docBackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            chat_deleteProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            chat_botProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            chat_botProgressPaint.setStrokeCap(Paint.Cap.ROUND);
            chat_botProgressPaint.setStyle(Paint.Style.STROKE);
            chat_locationTitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_locationTitlePaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            chat_locationAddressPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_urlPaint = new Paint();
            chat_textSearchSelectionPaint = new Paint();
            chat_radialProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            chat_radialProgressPaint.setStrokeCap(Paint.Cap.ROUND);
            chat_radialProgressPaint.setStyle(Paint.Style.STROKE);
            chat_radialProgressPaint.setColor(0x9fffffff);
            chat_radialProgress2Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            chat_radialProgress2Paint.setStrokeCap(Paint.Cap.ROUND);
            chat_radialProgress2Paint.setStyle(Paint.Style.STROKE);
            chat_audioTimePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            chat_livePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_livePaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            chat_audioTitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_audioTitlePaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            chat_audioPerformerPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_botButtonPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_botButtonPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            chat_contactNamePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_contactNamePaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            chat_contactPhonePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_durationPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_gamePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_gamePaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            chat_shipmentPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_timePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            chat_adminPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            chat_namePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            chat_namePaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            chat_forwardNamePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            chat_replyNamePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            chat_replyNamePaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            chat_replyTextPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            chat_instantViewPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_instantViewPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            chat_instantViewRectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            chat_instantViewRectPaint.setStyle(Paint.Style.STROKE);
            chat_replyLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            chat_msgErrorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            chat_statusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            chat_statusRecordPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            chat_statusRecordPaint.setStyle(Paint.Style.STROKE);
            chat_statusRecordPaint.setStrokeCap(Paint.Cap.ROUND);
            chat_actionTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_actionTextPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            chat_actionBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            chat_timeBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            chat_contextResult_titleTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_contextResult_titleTextPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            chat_contextResult_descriptionTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            chat_composeBackgroundPaint = new Paint();

            Resources resources = context.getResources();

            chat_msgInDrawable = resources.getDrawable(R.drawable.msg_in).mutate();
            chat_msgInSelectedDrawable = resources.getDrawable(R.drawable.msg_in).mutate();

            chat_msgNoSoundDrawable = resources.getDrawable(R.drawable.video_muted);

            chat_msgOutDrawable = resources.getDrawable(R.drawable.msg_out).mutate();
            chat_msgOutSelectedDrawable = resources.getDrawable(R.drawable.msg_out).mutate();

            chat_msgInMediaDrawable = resources.getDrawable(R.drawable.msg_photo).mutate();
            chat_msgInMediaSelectedDrawable = resources.getDrawable(R.drawable.msg_photo).mutate();
            chat_msgOutMediaDrawable = resources.getDrawable(R.drawable.msg_photo).mutate();
            chat_msgOutMediaSelectedDrawable = resources.getDrawable(R.drawable.msg_photo).mutate();

            chat_msgOutCheckDrawable = resources.getDrawable(R.drawable.msg_check).mutate();
            chat_msgOutCheckSelectedDrawable = resources.getDrawable(R.drawable.msg_check).mutate();
            chat_msgOutCheckReadDrawable = resources.getDrawable(R.drawable.msg_check).mutate();
            chat_msgOutCheckReadSelectedDrawable = resources.getDrawable(R.drawable.msg_check).mutate();
            chat_msgMediaCheckDrawable = resources.getDrawable(R.drawable.msg_check).mutate();
            chat_msgStickerCheckDrawable = resources.getDrawable(R.drawable.msg_check).mutate();
            chat_msgOutHalfCheckDrawable = resources.getDrawable(R.drawable.msg_halfcheck).mutate();
            chat_msgOutHalfCheckSelectedDrawable = resources.getDrawable(R.drawable.msg_halfcheck).mutate();
            chat_msgMediaHalfCheckDrawable = resources.getDrawable(R.drawable.msg_halfcheck).mutate();
            chat_msgStickerHalfCheckDrawable = resources.getDrawable(R.drawable.msg_halfcheck).mutate();
            chat_msgOutClockDrawable = resources.getDrawable(R.drawable.msg_clock).mutate();
            chat_msgOutSelectedClockDrawable = resources.getDrawable(R.drawable.msg_clock).mutate();
            chat_msgInClockDrawable = resources.getDrawable(R.drawable.msg_clock).mutate();
            chat_msgInSelectedClockDrawable = resources.getDrawable(R.drawable.msg_clock).mutate();
            chat_msgMediaClockDrawable = resources.getDrawable(R.drawable.msg_clock).mutate();
            chat_msgStickerClockDrawable = resources.getDrawable(R.drawable.msg_clock).mutate();
            chat_msgInViewsDrawable = resources.getDrawable(R.drawable.msg_views).mutate();
            chat_msgInViewsSelectedDrawable = resources.getDrawable(R.drawable.msg_views).mutate();
            chat_msgOutViewsDrawable = resources.getDrawable(R.drawable.msg_views).mutate();
            chat_msgOutViewsSelectedDrawable = resources.getDrawable(R.drawable.msg_views).mutate();
            chat_msgMediaViewsDrawable = resources.getDrawable(R.drawable.msg_views).mutate();
            chat_msgStickerViewsDrawable = resources.getDrawable(R.drawable.msg_views).mutate();
            chat_msgInMenuDrawable = resources.getDrawable(R.drawable.msg_actions).mutate();
            chat_msgInMenuSelectedDrawable = resources.getDrawable(R.drawable.msg_actions).mutate();
            chat_msgOutMenuDrawable = resources.getDrawable(R.drawable.msg_actions).mutate();
            chat_msgOutMenuSelectedDrawable = resources.getDrawable(R.drawable.msg_actions).mutate();
            chat_msgMediaMenuDrawable = resources.getDrawable(R.drawable.video_actions);
            chat_msgInInstantDrawable = resources.getDrawable(R.drawable.msg_instant).mutate();
            chat_msgOutInstantDrawable = resources.getDrawable(R.drawable.msg_instant).mutate();
            chat_msgErrorDrawable = resources.getDrawable(R.drawable.msg_warning);
            chat_muteIconDrawable = resources.getDrawable(R.drawable.list_mute).mutate();
            chat_lockIconDrawable = resources.getDrawable(R.drawable.ic_lock_header);
            chat_msgBroadcastDrawable = resources.getDrawable(R.drawable.broadcast3).mutate();
            chat_msgBroadcastMediaDrawable = resources.getDrawable(R.drawable.broadcast3).mutate();
            chat_msgInCallDrawable = resources.getDrawable(R.drawable.ic_call).mutate();
            chat_msgInCallSelectedDrawable = resources.getDrawable(R.drawable.ic_call).mutate();
            chat_msgOutCallDrawable = resources.getDrawable(R.drawable.ic_call).mutate();
            chat_msgOutCallSelectedDrawable = resources.getDrawable(R.drawable.ic_call).mutate();
            chat_msgCallUpGreenDrawable = resources.getDrawable(R.drawable.ic_call_made_green_18dp).mutate();
            chat_msgCallDownRedDrawable = resources.getDrawable(R.drawable.ic_call_received_green_18dp).mutate();
            chat_msgCallDownGreenDrawable = resources.getDrawable(R.drawable.ic_call_received_green_18dp).mutate();

            calllog_msgCallUpRedDrawable = resources.getDrawable(R.drawable.ic_call_made_green_18dp).mutate();
            calllog_msgCallUpGreenDrawable = resources.getDrawable(R.drawable.ic_call_made_green_18dp).mutate();
            calllog_msgCallDownRedDrawable = resources.getDrawable(R.drawable.ic_call_received_green_18dp).mutate();
            calllog_msgCallDownGreenDrawable = resources.getDrawable(R.drawable.ic_call_received_green_18dp).mutate();
            chat_msgAvatarLiveLocationDrawable = resources.getDrawable(R.drawable.livepin).mutate();

            chat_inlineResultFile = resources.getDrawable(R.drawable.bot_file);
            chat_inlineResultAudio = resources.getDrawable(R.drawable.bot_music);
            chat_inlineResultLocation = resources.getDrawable(R.drawable.bot_location);
            chat_redLocationIcon = resources.getDrawable(R.drawable.map_pin).mutate();

            chat_msgInShadowDrawable = resources.getDrawable(R.drawable.msg_in_shadow);
            chat_msgOutShadowDrawable = resources.getDrawable(R.drawable.msg_out_shadow);
            chat_msgInMediaShadowDrawable = resources.getDrawable(R.drawable.msg_photo_shadow);
            chat_msgOutMediaShadowDrawable = resources.getDrawable(R.drawable.msg_photo_shadow);

            chat_botLinkDrawalbe = resources.getDrawable(R.drawable.bot_link);
            chat_botInlineDrawable = resources.getDrawable(R.drawable.bot_lines);

            chat_systemDrawable = resources.getDrawable(R.drawable.system);

            chat_contextResult_shadowUnderSwitchDrawable = resources.getDrawable(R.drawable.header_shadow).mutate();

            chat_attachButtonDrawables[0] = createCircleDrawableWithIcon(AndroidUtilities.dp(50), R.drawable.attach_gallery);
            chat_attachButtonDrawables[1] = createCircleDrawableWithIcon(AndroidUtilities.dp(50), R.drawable.attach_audio);
            chat_attachButtonDrawables[2] = createCircleDrawableWithIcon(AndroidUtilities.dp(50), R.drawable.attach_file);
            chat_attachButtonDrawables[3] = createCircleDrawableWithIcon(AndroidUtilities.dp(50), R.drawable.attach_contact);
            chat_attachButtonDrawables[4] = createCircleDrawableWithIcon(AndroidUtilities.dp(50), R.drawable.attach_location);
            chat_attachButtonDrawables[5] = createCircleDrawableWithIcon(AndroidUtilities.dp(50), R.drawable.attach_polls);
            chat_attachEmptyDrawable = resources.getDrawable(R.drawable.nophotos3);

            chat_cornerOuter[0] = resources.getDrawable(R.drawable.corner_out_tl);
            chat_cornerOuter[1] = resources.getDrawable(R.drawable.corner_out_tr);
            chat_cornerOuter[2] = resources.getDrawable(R.drawable.corner_out_br);
            chat_cornerOuter[3] = resources.getDrawable(R.drawable.corner_out_bl);

            chat_cornerInner[0] = resources.getDrawable(R.drawable.corner_in_tr);
            chat_cornerInner[1] = resources.getDrawable(R.drawable.corner_in_tl);
            chat_cornerInner[2] = resources.getDrawable(R.drawable.corner_in_br);
            chat_cornerInner[3] = resources.getDrawable(R.drawable.corner_in_bl);

            chat_shareDrawable = resources.getDrawable(R.drawable.share_round);
            chat_shareIconDrawable = resources.getDrawable(R.drawable.share_arrow);
            chat_replyIconDrawable = resources.getDrawable(R.drawable.fast_reply);
            chat_goIconDrawable = resources.getDrawable(R.drawable.message_arrow);

            chat_fileMiniStatesDrawable[0][0] = createCircleDrawableWithIcon(AndroidUtilities.dp(22), R.drawable.audio_mini_arrow);
            chat_fileMiniStatesDrawable[0][1] = createCircleDrawableWithIcon(AndroidUtilities.dp(22), R.drawable.audio_mini_arrow);
            chat_fileMiniStatesDrawable[1][0] = createCircleDrawableWithIcon(AndroidUtilities.dp(22), R.drawable.audio_mini_cancel);
            chat_fileMiniStatesDrawable[1][1] = createCircleDrawableWithIcon(AndroidUtilities.dp(22), R.drawable.audio_mini_cancel);
            chat_fileMiniStatesDrawable[2][0] = createCircleDrawableWithIcon(AndroidUtilities.dp(22), R.drawable.audio_mini_arrow);
            chat_fileMiniStatesDrawable[2][1] = createCircleDrawableWithIcon(AndroidUtilities.dp(22), R.drawable.audio_mini_arrow);
            chat_fileMiniStatesDrawable[3][0] = createCircleDrawableWithIcon(AndroidUtilities.dp(22), R.drawable.audio_mini_cancel);
            chat_fileMiniStatesDrawable[3][1] = createCircleDrawableWithIcon(AndroidUtilities.dp(22), R.drawable.audio_mini_cancel);
            chat_fileMiniStatesDrawable[4][0] = createCircleDrawableWithIcon(AndroidUtilities.dp(22), R.drawable.video_mini_arrow);
            chat_fileMiniStatesDrawable[4][1] = createCircleDrawableWithIcon(AndroidUtilities.dp(22), R.drawable.video_mini_arrow);
            chat_fileMiniStatesDrawable[5][0] = createCircleDrawableWithIcon(AndroidUtilities.dp(22), R.drawable.video_mini_cancel);
            chat_fileMiniStatesDrawable[5][1] = createCircleDrawableWithIcon(AndroidUtilities.dp(22), R.drawable.video_mini_cancel);

            chat_fileIcon = resources.getDrawable(R.drawable.msg_round_file_s).mutate();
            chat_flameIcon = resources.getDrawable(R.drawable.burn).mutate();
            chat_gifIcon = resources.getDrawable(R.drawable.msg_round_gif_m).mutate();

            chat_fileStatesDrawable[0][0] = createCircleDrawableWithIcon(AndroidUtilities.dp(44), R.drawable.msg_round_play_m);
            chat_fileStatesDrawable[0][1] = createCircleDrawableWithIcon(AndroidUtilities.dp(44), R.drawable.msg_round_play_m);
            chat_fileStatesDrawable[1][0] = createCircleDrawableWithIcon(AndroidUtilities.dp(44), R.drawable.msg_round_pause_m);
            chat_fileStatesDrawable[1][1] = createCircleDrawableWithIcon(AndroidUtilities.dp(44), R.drawable.msg_round_pause_m);
            chat_fileStatesDrawable[2][0] = createCircleDrawableWithIcon(AndroidUtilities.dp(44), R.drawable.msg_round_load_m);
            chat_fileStatesDrawable[2][1] = createCircleDrawableWithIcon(AndroidUtilities.dp(44), R.drawable.msg_round_load_m);
            chat_fileStatesDrawable[3][0] = createCircleDrawableWithIcon(AndroidUtilities.dp(44), R.drawable.msg_round_file_s);
            chat_fileStatesDrawable[3][1] = createCircleDrawableWithIcon(AndroidUtilities.dp(44), R.drawable.msg_round_file_s);
            chat_fileStatesDrawable[4][0] = createCircleDrawableWithIcon(AndroidUtilities.dp(44), R.drawable.msg_round_cancel_m);
            chat_fileStatesDrawable[4][1] = createCircleDrawableWithIcon(AndroidUtilities.dp(44), R.drawable.msg_round_cancel_m);
            chat_fileStatesDrawable[5][0] = createCircleDrawableWithIcon(AndroidUtilities.dp(44), R.drawable.msg_round_play_m);
            chat_fileStatesDrawable[5][1] = createCircleDrawableWithIcon(AndroidUtilities.dp(44), R.drawable.msg_round_play_m);
            chat_fileStatesDrawable[6][0] = createCircleDrawableWithIcon(AndroidUtilities.dp(44), R.drawable.msg_round_pause_m);
            chat_fileStatesDrawable[6][1] = createCircleDrawableWithIcon(AndroidUtilities.dp(44), R.drawable.msg_round_pause_m);
            chat_fileStatesDrawable[7][0] = createCircleDrawableWithIcon(AndroidUtilities.dp(44), R.drawable.msg_round_load_m);
            chat_fileStatesDrawable[7][1] = createCircleDrawableWithIcon(AndroidUtilities.dp(44), R.drawable.msg_round_load_m);
            chat_fileStatesDrawable[8][0] = createCircleDrawableWithIcon(AndroidUtilities.dp(44), R.drawable.msg_round_file_s);
            chat_fileStatesDrawable[8][1] = createCircleDrawableWithIcon(AndroidUtilities.dp(44), R.drawable.msg_round_file_s);
            chat_fileStatesDrawable[9][0] = createCircleDrawableWithIcon(AndroidUtilities.dp(44), R.drawable.msg_round_cancel_m);
            chat_fileStatesDrawable[9][1] = createCircleDrawableWithIcon(AndroidUtilities.dp(44), R.drawable.msg_round_cancel_m);

            chat_photoStatesDrawables[0][0] = createCircleDrawableWithIcon(AndroidUtilities.dp(48), R.drawable.msg_round_load_m);
            chat_photoStatesDrawables[0][1] = createCircleDrawableWithIcon(AndroidUtilities.dp(48), R.drawable.msg_round_load_m);
            chat_photoStatesDrawables[1][0] = createCircleDrawableWithIcon(AndroidUtilities.dp(48), R.drawable.msg_round_cancel_m);
            chat_photoStatesDrawables[1][1] = createCircleDrawableWithIcon(AndroidUtilities.dp(48), R.drawable.msg_round_cancel_m);
            chat_photoStatesDrawables[2][0] = createCircleDrawableWithIcon(AndroidUtilities.dp(48), R.drawable.msg_round_gif_m);
            chat_photoStatesDrawables[2][1] = createCircleDrawableWithIcon(AndroidUtilities.dp(48), R.drawable.msg_round_gif_m);
            chat_photoStatesDrawables[3][0] = createCircleDrawableWithIcon(AndroidUtilities.dp(48), R.drawable.msg_round_play_m);
            chat_photoStatesDrawables[3][1] = createCircleDrawableWithIcon(AndroidUtilities.dp(48), R.drawable.msg_round_play_m);

            chat_photoStatesDrawables[4][0] = chat_photoStatesDrawables[4][1] = resources.getDrawable(R.drawable.burn);
            chat_photoStatesDrawables[5][0] = chat_photoStatesDrawables[5][1] = resources.getDrawable(R.drawable.circle);
            chat_photoStatesDrawables[6][0] = chat_photoStatesDrawables[6][1] = resources.getDrawable(R.drawable.photocheck);

            chat_photoStatesDrawables[7][0] = createCircleDrawableWithIcon(AndroidUtilities.dp(48), R.drawable.msg_round_load_m);
            chat_photoStatesDrawables[7][1] = createCircleDrawableWithIcon(AndroidUtilities.dp(48), R.drawable.msg_round_load_m);
            chat_photoStatesDrawables[8][0] = createCircleDrawableWithIcon(AndroidUtilities.dp(48), R.drawable.msg_round_cancel_m);
            chat_photoStatesDrawables[8][1] = createCircleDrawableWithIcon(AndroidUtilities.dp(48), R.drawable.msg_round_cancel_m);
            chat_photoStatesDrawables[9][0] = resources.getDrawable(R.drawable.doc_big).mutate();
            chat_photoStatesDrawables[9][1] = resources.getDrawable(R.drawable.doc_big).mutate();
            chat_photoStatesDrawables[10][0] = createCircleDrawableWithIcon(AndroidUtilities.dp(48), R.drawable.msg_round_load_m);
            chat_photoStatesDrawables[10][1] = createCircleDrawableWithIcon(AndroidUtilities.dp(48), R.drawable.msg_round_load_m);
            chat_photoStatesDrawables[11][0] = createCircleDrawableWithIcon(AndroidUtilities.dp(48), R.drawable.msg_round_cancel_m);
            chat_photoStatesDrawables[11][1] = createCircleDrawableWithIcon(AndroidUtilities.dp(48), R.drawable.msg_round_cancel_m);
            chat_photoStatesDrawables[12][0] = resources.getDrawable(R.drawable.doc_big).mutate();
            chat_photoStatesDrawables[12][1] = resources.getDrawable(R.drawable.doc_big).mutate();

            chat_contactDrawable[0] = createCircleDrawableWithIcon(AndroidUtilities.dp(44), R.drawable.msg_contact);
            chat_contactDrawable[1] = createCircleDrawableWithIcon(AndroidUtilities.dp(44), R.drawable.msg_contact);

            chat_locationDrawable[0] = createRoundRectDrawableWithIcon(AndroidUtilities.dp(2), R.drawable.msg_location);
            chat_locationDrawable[1] = createRoundRectDrawableWithIcon(AndroidUtilities.dp(2), R.drawable.msg_location);

            chat_composeShadowDrawable = context.getResources().getDrawable(R.drawable.compose_panel_shadow);

            try {
                int bitmapSize = AndroidUtilities.roundMessageSize + AndroidUtilities.dp(6);
                Bitmap bitmap = Bitmap.createBitmap(bitmapSize, bitmapSize, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                Paint eraserPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                eraserPaint.setColor(0);
                eraserPaint.setStyle(Paint.Style.FILL);
                eraserPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setShadowLayer(AndroidUtilities.dp(4), 0, 0, 0x5f000000);
                for (int a = 0; a < 2; a++) {
                    canvas.drawCircle(bitmapSize / 2, bitmapSize / 2, AndroidUtilities.roundMessageSize / 2 - AndroidUtilities.dp(1), a == 0 ? paint : eraserPaint);
                }
                try {
                    canvas.setBitmap(null);
                } catch (Exception ignore) {

                }
                chat_roundVideoShadow = new BitmapDrawable(bitmap);
            } catch (Throwable ignore) {

            }

            applyChatTheme(fontsOnly);
        }

        chat_msgTextPaintOneEmoji.setTextSize(AndroidUtilities.dp(28));
        chat_msgTextPaintTwoEmoji.setTextSize(AndroidUtilities.dp(24));
        chat_msgTextPaintThreeEmoji.setTextSize(AndroidUtilities.dp(20));
        chat_msgTextPaint.setTextSize(AndroidUtilities.dp(SharedConfig.fontSize));
        chat_msgGameTextPaint.setTextSize(AndroidUtilities.dp(14));
        chat_msgBotButtonPaint.setTextSize(AndroidUtilities.dp(15));

        if (!fontsOnly && chat_botProgressPaint != null) {
            chat_botProgressPaint.setStrokeWidth(AndroidUtilities.dp(2));
            chat_infoPaint.setTextSize(AndroidUtilities.dp(12));
            chat_docNamePaint.setTextSize(AndroidUtilities.dp(15));
            chat_locationTitlePaint.setTextSize(AndroidUtilities.dp(15));
            chat_locationAddressPaint.setTextSize(AndroidUtilities.dp(13));
            chat_audioTimePaint.setTextSize(AndroidUtilities.dp(12));
            chat_livePaint.setTextSize(AndroidUtilities.dp(12));
            chat_audioTitlePaint.setTextSize(AndroidUtilities.dp(16));
            chat_audioPerformerPaint.setTextSize(AndroidUtilities.dp(15));
            chat_botButtonPaint.setTextSize(AndroidUtilities.dp(15));
            chat_contactNamePaint.setTextSize(AndroidUtilities.dp(15));
            chat_contactPhonePaint.setTextSize(AndroidUtilities.dp(13));
            chat_durationPaint.setTextSize(AndroidUtilities.dp(12));
            chat_timePaint.setTextSize(AndroidUtilities.dp(12));
            chat_adminPaint.setTextSize(AndroidUtilities.dp(13));
            chat_namePaint.setTextSize(AndroidUtilities.dp(14));
            chat_forwardNamePaint.setTextSize(AndroidUtilities.dp(14));
            chat_replyNamePaint.setTextSize(AndroidUtilities.dp(14));
            chat_replyTextPaint.setTextSize(AndroidUtilities.dp(14));
            chat_gamePaint.setTextSize(AndroidUtilities.dp(13));
            chat_shipmentPaint.setTextSize(AndroidUtilities.dp(13));
            chat_instantViewPaint.setTextSize(AndroidUtilities.dp(13));
            chat_instantViewRectPaint.setStrokeWidth(AndroidUtilities.dp(1));
            chat_statusRecordPaint.setStrokeWidth(AndroidUtilities.dp(2));
            chat_actionTextPaint.setTextSize(AndroidUtilities.dp(Math.max(16, SharedConfig.fontSize) - 2));
            chat_contextResult_titleTextPaint.setTextSize(AndroidUtilities.dp(15));
            chat_contextResult_descriptionTextPaint.setTextSize(AndroidUtilities.dp(13));
            chat_radialProgressPaint.setStrokeWidth(AndroidUtilities.dp(3));
            chat_radialProgress2Paint.setStrokeWidth(AndroidUtilities.dp(2));
        }
    }

    public static void applyChatTheme(boolean fontsOnly) {
        if (chat_msgTextPaint == null) {
            return;
        }

        if (chat_msgInDrawable != null && !fontsOnly) {
            chat_gamePaint.setColor(getColor(key_chat_previewGameText));
            chat_durationPaint.setColor(getColor(key_chat_previewDurationText));
            chat_botButtonPaint.setColor(getColor(key_chat_botButtonText));
            chat_urlPaint.setColor(getColor(key_chat_linkSelectBackground));
            chat_botProgressPaint.setColor(getColor(key_chat_botProgress));
            chat_deleteProgressPaint.setColor(getColor(key_chat_secretTimeText));
            chat_textSearchSelectionPaint.setColor(getColor(key_chat_textSelectBackground));
            chat_msgErrorPaint.setColor(getColor(key_chat_sentError));
            chat_statusPaint.setColor(getColor(key_chat_status));
            chat_statusRecordPaint.setColor(getColor(key_chat_status));
            chat_actionTextPaint.setColor(getColor(key_chat_serviceText));
            chat_actionTextPaint.linkColor = getColor(key_chat_serviceLink);
            chat_contextResult_titleTextPaint.setColor(getColor(key_windowBackgroundWhiteBlackText));
            chat_composeBackgroundPaint.setColor(getColor(key_chat_messagePanelBackground));
            chat_timeBackgroundPaint.setColor(getColor(key_chat_mediaTimeBackground));

            setDrawableColorByKey(chat_msgNoSoundDrawable, key_chat_mediaTimeText);
            setDrawableColorByKey(chat_msgInDrawable, key_chat_inBubble);
            setDrawableColorByKey(chat_msgInSelectedDrawable, key_chat_inBubbleSelected);
            setDrawableColorByKey(chat_msgInShadowDrawable, key_chat_inBubbleShadow);
            setDrawableColorByKey(chat_msgOutDrawable, key_chat_outBubble);
            setDrawableColorByKey(chat_msgOutSelectedDrawable, key_chat_outBubbleSelected);
            setDrawableColorByKey(chat_msgOutShadowDrawable, key_chat_outBubbleShadow);
            setDrawableColorByKey(chat_msgInMediaDrawable, key_chat_inBubble);
            setDrawableColorByKey(chat_msgInMediaSelectedDrawable, key_chat_inBubbleSelected);
            setDrawableColorByKey(chat_msgInMediaShadowDrawable, key_chat_inBubbleShadow);
            setDrawableColorByKey(chat_msgOutMediaDrawable, key_chat_outBubble);
            setDrawableColorByKey(chat_msgOutMediaSelectedDrawable, key_chat_outBubbleSelected);
            setDrawableColorByKey(chat_msgOutMediaShadowDrawable, key_chat_outBubbleShadow);
            setDrawableColorByKey(chat_msgOutCheckDrawable, key_chat_outSentCheck);
            setDrawableColorByKey(chat_msgOutCheckSelectedDrawable, key_chat_outSentCheckSelected);
            setDrawableColorByKey(chat_msgOutCheckReadDrawable, key_chat_outSentCheckRead);
            setDrawableColorByKey(chat_msgOutCheckReadSelectedDrawable, key_chat_outSentCheckReadSelected);
            setDrawableColorByKey(chat_msgOutHalfCheckDrawable, key_chat_outSentCheckRead);
            setDrawableColorByKey(chat_msgOutHalfCheckSelectedDrawable, key_chat_outSentCheckReadSelected);
            setDrawableColorByKey(chat_msgOutClockDrawable, key_chat_outSentClock);
            setDrawableColorByKey(chat_msgOutSelectedClockDrawable, key_chat_outSentClockSelected);
            setDrawableColorByKey(chat_msgInClockDrawable, key_chat_inSentClock);
            setDrawableColorByKey(chat_msgInSelectedClockDrawable, key_chat_inSentClockSelected);
            setDrawableColorByKey(chat_msgMediaCheckDrawable, key_chat_mediaSentCheck);
            setDrawableColorByKey(chat_msgMediaHalfCheckDrawable, key_chat_mediaSentCheck);
            setDrawableColorByKey(chat_msgMediaClockDrawable, key_chat_mediaSentClock);
            setDrawableColorByKey(chat_msgStickerCheckDrawable, key_chat_serviceText);
            setDrawableColorByKey(chat_msgStickerHalfCheckDrawable, key_chat_serviceText);
            setDrawableColorByKey(chat_msgStickerClockDrawable, key_chat_serviceText);
            setDrawableColorByKey(chat_msgStickerViewsDrawable, key_chat_serviceText);
            setDrawableColorByKey(chat_shareIconDrawable, key_chat_serviceIcon);
            setDrawableColorByKey(chat_replyIconDrawable, key_chat_serviceIcon);
            setDrawableColorByKey(chat_goIconDrawable, key_chat_serviceIcon);
            setDrawableColorByKey(chat_botInlineDrawable, key_chat_serviceIcon);
            setDrawableColorByKey(chat_botLinkDrawalbe, key_chat_serviceIcon);
            setDrawableColorByKey(chat_msgInViewsDrawable, key_chat_inViews);
            setDrawableColorByKey(chat_msgInViewsSelectedDrawable, key_chat_inViewsSelected);
            setDrawableColorByKey(chat_msgOutViewsDrawable, key_chat_outViews);
            setDrawableColorByKey(chat_msgOutViewsSelectedDrawable, key_chat_outViewsSelected);
            setDrawableColorByKey(chat_msgMediaViewsDrawable, key_chat_mediaViews);
            setDrawableColorByKey(chat_msgInMenuDrawable, key_chat_inMenu);
            setDrawableColorByKey(chat_msgInMenuSelectedDrawable, key_chat_inMenuSelected);
            setDrawableColorByKey(chat_msgOutMenuDrawable, key_chat_outMenu);
            setDrawableColorByKey(chat_msgOutMenuSelectedDrawable, key_chat_outMenuSelected);
            setDrawableColorByKey(chat_msgMediaMenuDrawable, key_chat_mediaMenu);
            setDrawableColorByKey(chat_msgOutInstantDrawable, key_chat_outInstant);
            setDrawableColorByKey(chat_msgInInstantDrawable, key_chat_inInstant);
            setDrawableColorByKey(chat_msgErrorDrawable, key_chat_sentErrorIcon);
            setDrawableColorByKey(chat_muteIconDrawable, key_chat_muteIcon);
            setDrawableColorByKey(chat_lockIconDrawable, key_chat_lockIcon);
            setDrawableColorByKey(chat_msgBroadcastDrawable, key_chat_outBroadcast);
            setDrawableColorByKey(chat_msgBroadcastMediaDrawable, key_chat_mediaBroadcast);
            setDrawableColorByKey(chat_inlineResultFile, key_chat_inlineResultIcon);
            setDrawableColorByKey(chat_inlineResultAudio, key_chat_inlineResultIcon);
            setDrawableColorByKey(chat_inlineResultLocation, key_chat_inlineResultIcon);
            setDrawableColorByKey(chat_msgInCallDrawable, key_chat_inInstant);
            setDrawableColorByKey(chat_msgInCallSelectedDrawable, key_chat_inInstantSelected);
            setDrawableColorByKey(chat_msgOutCallDrawable, key_chat_outInstant);
            setDrawableColorByKey(chat_msgOutCallSelectedDrawable, key_chat_outInstantSelected);

            setDrawableColorByKey(chat_msgCallUpGreenDrawable, key_chat_outGreenCall);
            setDrawableColorByKey(chat_msgCallDownRedDrawable, key_chat_inRedCall);
            setDrawableColorByKey(chat_msgCallDownGreenDrawable, key_chat_inGreenCall);

            setDrawableColorByKey(calllog_msgCallUpRedDrawable, key_calls_callReceivedRedIcon);
            setDrawableColorByKey(calllog_msgCallUpGreenDrawable, key_calls_callReceivedGreenIcon);
            setDrawableColorByKey(calllog_msgCallDownRedDrawable, key_calls_callReceivedRedIcon);
            setDrawableColorByKey(calllog_msgCallDownGreenDrawable, key_calls_callReceivedGreenIcon);

            for (int a = 0; a < 2; a++) {
                setCombinedDrawableColor(chat_fileMiniStatesDrawable[a][0], getColor(key_chat_outLoader), false);
                setCombinedDrawableColor(chat_fileMiniStatesDrawable[a][0], getColor(key_chat_outMediaIcon), true);
                setCombinedDrawableColor(chat_fileMiniStatesDrawable[a][1], getColor(key_chat_outLoaderSelected), false);
                setCombinedDrawableColor(chat_fileMiniStatesDrawable[a][1], getColor(key_chat_outMediaIconSelected), true);

                setCombinedDrawableColor(chat_fileMiniStatesDrawable[2 + a][0], getColor(key_chat_inLoader), false);
                setCombinedDrawableColor(chat_fileMiniStatesDrawable[2 + a][0], getColor(key_chat_inMediaIcon), true);
                setCombinedDrawableColor(chat_fileMiniStatesDrawable[2 + a][1], getColor(key_chat_inLoaderSelected), false);
                setCombinedDrawableColor(chat_fileMiniStatesDrawable[2 + a][1], getColor(key_chat_inMediaIconSelected), true);

                setCombinedDrawableColor(chat_fileMiniStatesDrawable[4 + a][0], getColor(key_chat_mediaLoaderPhoto), false);
                setCombinedDrawableColor(chat_fileMiniStatesDrawable[4 + a][0], getColor(key_chat_mediaLoaderPhotoIcon), true);
                setCombinedDrawableColor(chat_fileMiniStatesDrawable[4 + a][1], getColor(key_chat_mediaLoaderPhotoSelected), false);
                setCombinedDrawableColor(chat_fileMiniStatesDrawable[4 + a][1], getColor(key_chat_mediaLoaderPhotoIconSelected), true);
            }

            for (int a = 0; a < 5; a++) {
                setCombinedDrawableColor(chat_fileStatesDrawable[a][0], getColor(key_chat_outLoader), false);
                setCombinedDrawableColor(chat_fileStatesDrawable[a][0], getColor(key_chat_outMediaIcon), true);
                setCombinedDrawableColor(chat_fileStatesDrawable[a][1], getColor(key_chat_outLoaderSelected), false);
                setCombinedDrawableColor(chat_fileStatesDrawable[a][1], getColor(key_chat_outMediaIconSelected), true);
                setCombinedDrawableColor(chat_fileStatesDrawable[5 + a][0], getColor(key_chat_inLoader), false);
                setCombinedDrawableColor(chat_fileStatesDrawable[5 + a][0], getColor(key_chat_inMediaIcon), true);
                setCombinedDrawableColor(chat_fileStatesDrawable[5 + a][1], getColor(key_chat_inLoaderSelected), false);
                setCombinedDrawableColor(chat_fileStatesDrawable[5 + a][1], getColor(key_chat_inMediaIconSelected), true);
            }
            for (int a = 0; a < 4; a++) {
                setCombinedDrawableColor(chat_photoStatesDrawables[a][0], getColor(key_chat_mediaLoaderPhoto), false);
                setCombinedDrawableColor(chat_photoStatesDrawables[a][0], getColor(key_chat_mediaLoaderPhotoIcon), true);
                setCombinedDrawableColor(chat_photoStatesDrawables[a][1], getColor(key_chat_mediaLoaderPhotoSelected), false);
                setCombinedDrawableColor(chat_photoStatesDrawables[a][1], getColor(key_chat_mediaLoaderPhotoIconSelected), true);
            }
            for (int a = 0; a < 2; a++) {
                setCombinedDrawableColor(chat_photoStatesDrawables[7 + a][0], getColor(key_chat_outLoaderPhoto), false);
                setCombinedDrawableColor(chat_photoStatesDrawables[7 + a][0], getColor(key_chat_outLoaderPhotoIcon), true);
                setCombinedDrawableColor(chat_photoStatesDrawables[7 + a][1], getColor(key_chat_outLoaderPhotoSelected), false);
                setCombinedDrawableColor(chat_photoStatesDrawables[7 + a][1], getColor(key_chat_outLoaderPhotoIconSelected), true);
                setCombinedDrawableColor(chat_photoStatesDrawables[10 + a][0], getColor(key_chat_inLoaderPhoto), false);
                setCombinedDrawableColor(chat_photoStatesDrawables[10 + a][0], getColor(key_chat_inLoaderPhotoIcon), true);
                setCombinedDrawableColor(chat_photoStatesDrawables[10 + a][1], getColor(key_chat_inLoaderPhotoSelected), false);
                setCombinedDrawableColor(chat_photoStatesDrawables[10 + a][1], getColor(key_chat_inLoaderPhotoIconSelected), true);
            }

            setDrawableColorByKey(chat_photoStatesDrawables[9][0], key_chat_outFileIcon);
            setDrawableColorByKey(chat_photoStatesDrawables[9][1], key_chat_outFileSelectedIcon);
            setDrawableColorByKey(chat_photoStatesDrawables[12][0], key_chat_inFileIcon);
            setDrawableColorByKey(chat_photoStatesDrawables[12][1], key_chat_inFileSelectedIcon);

            setCombinedDrawableColor(chat_contactDrawable[0], getColor(key_chat_inContactBackground), false);
            setCombinedDrawableColor(chat_contactDrawable[0], getColor(key_chat_inContactIcon), true);
            setCombinedDrawableColor(chat_contactDrawable[1], getColor(key_chat_outContactBackground), false);
            setCombinedDrawableColor(chat_contactDrawable[1], getColor(key_chat_outContactIcon), true);

            setCombinedDrawableColor(chat_locationDrawable[0], getColor(key_chat_inLocationBackground), false);
            setCombinedDrawableColor(chat_locationDrawable[0], getColor(key_chat_inLocationIcon), true);
            setCombinedDrawableColor(chat_locationDrawable[1], getColor(key_chat_outLocationBackground), false);
            setCombinedDrawableColor(chat_locationDrawable[1], getColor(key_chat_outLocationIcon), true);

            setDrawableColorByKey(chat_composeShadowDrawable, key_chat_messagePanelShadow);

            setCombinedDrawableColor(chat_attachButtonDrawables[0], getColor(key_chat_attachGalleryBackground), false);
            setCombinedDrawableColor(chat_attachButtonDrawables[0], getColor(key_chat_attachGalleryIcon), true);
            setCombinedDrawableColor(chat_attachButtonDrawables[1], getColor(key_chat_attachAudioBackground), false);
            setCombinedDrawableColor(chat_attachButtonDrawables[1], getColor(key_chat_attachAudioIcon), true);
            setCombinedDrawableColor(chat_attachButtonDrawables[2], getColor(key_chat_attachFileBackground), false);
            setCombinedDrawableColor(chat_attachButtonDrawables[2], getColor(key_chat_attachFileIcon), true);
            setCombinedDrawableColor(chat_attachButtonDrawables[3], getColor(key_chat_attachContactBackground), false);
            setCombinedDrawableColor(chat_attachButtonDrawables[3], getColor(key_chat_attachContactIcon), true);
            setCombinedDrawableColor(chat_attachButtonDrawables[4], getColor(key_chat_attachLocationBackground), false);
            setCombinedDrawableColor(chat_attachButtonDrawables[4], getColor(key_chat_attachLocationIcon), true);
            setCombinedDrawableColor(chat_attachButtonDrawables[5], getColor(key_chat_attachPollBackground), false);
            setCombinedDrawableColor(chat_attachButtonDrawables[5], getColor(key_chat_attachPollIcon), true);

            setDrawableColor(chat_attachEmptyDrawable, getColor(key_chat_attachEmptyImage));

            applyChatServiceMessageColor();
        }
    }

    public static void applyChatServiceMessageColor() {
        applyChatServiceMessageColor(null);
    }

    public static void applyChatServiceMessageColor(int[] custom) {
        if (chat_actionBackgroundPaint == null) {
            return;
        }
        Integer serviceColor;
        Integer servicePressedColor;
        serviceMessageColor = serviceMessageColorBackup;
        serviceSelectedMessageColor = serviceSelectedMessageColorBackup;
        if (custom != null && custom.length >= 2) {
            serviceColor = custom[0];
            servicePressedColor = custom[1];
            serviceMessageColor = custom[0];
            serviceSelectedMessageColor = custom[1];
        } else {
            serviceColor = currentColors.get(key_chat_serviceBackground);
            servicePressedColor = currentColors.get(key_chat_serviceBackgroundSelected);
        }
        Integer serviceColor2 = serviceColor;
        Integer servicePressedColor2 = servicePressedColor;

        if (serviceColor == null) {
            serviceColor = serviceMessageColor;
            serviceColor2 = serviceMessage2Color;
        }
        if (servicePressedColor == null) {
            servicePressedColor = serviceSelectedMessageColor;
            servicePressedColor2 = serviceSelectedMessage2Color;
        }
        if (currentColor != serviceColor) {
            chat_actionBackgroundPaint.setColor(serviceColor);
            colorFilter = new PorterDuffColorFilter(serviceColor, PorterDuff.Mode.MULTIPLY);
            colorFilter2 = new PorterDuffColorFilter(serviceColor2, PorterDuff.Mode.MULTIPLY);
            currentColor = serviceColor;
            if (chat_cornerOuter[0] != null) {
                for (int a = 0; a < 4; a++) {
                    chat_cornerOuter[a].setColorFilter(colorFilter);
                    chat_cornerInner[a].setColorFilter(colorFilter);
                }
            }
        }
        if (currentSelectedColor != servicePressedColor) {
            currentSelectedColor = servicePressedColor;
            colorPressedFilter = new PorterDuffColorFilter(servicePressedColor, PorterDuff.Mode.MULTIPLY);
            colorPressedFilter2 = new PorterDuffColorFilter(servicePressedColor2, PorterDuff.Mode.MULTIPLY);
        }
    }

    public static void createProfileResources(Context context) {
        if (profile_verifiedDrawable == null) {
            profile_aboutTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

            Resources resources = context.getResources();

            profile_verifiedDrawable = resources.getDrawable(R.drawable.verified_area).mutate();
            profile_verifiedCheckDrawable = resources.getDrawable(R.drawable.verified_check).mutate();

            applyProfileTheme();
        }

        profile_aboutTextPaint.setTextSize(AndroidUtilities.dp(16));
    }

    private static ColorFilter currentShareColorFilter;
    private static int currentShareColorFilterColor;
    private static ColorFilter currentShareSelectedColorFilter;
    private static  int currentShareSelectedColorFilterColor;
    public static ColorFilter getShareColorFilter(int color, boolean selected) {
        if (selected) {
            if (currentShareSelectedColorFilter == null || currentShareSelectedColorFilterColor != color) {
                currentShareSelectedColorFilterColor = color;
                currentShareSelectedColorFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY);
            }
            return currentShareSelectedColorFilter;
        } else {
            if (currentShareColorFilter == null || currentShareColorFilterColor != color) {
                currentShareColorFilterColor = color;
                currentShareColorFilter = new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY);
            }
            return currentShareColorFilter;
        }
    }

    public static void applyProfileTheme() {
        if (profile_verifiedDrawable == null) {
            return;
        }

        profile_aboutTextPaint.setColor(getColor(key_windowBackgroundWhiteBlackText));
        profile_aboutTextPaint.linkColor = getColor(key_windowBackgroundWhiteLinkText);

        setDrawableColorByKey(profile_verifiedDrawable, key_profile_verifiedBackground);
        setDrawableColorByKey(profile_verifiedCheckDrawable, key_profile_verifiedCheck);
    }

    public static Drawable getThemedDrawable(Context context, int resId, String key) {
        return getThemedDrawable(context, resId, getColor(key));
    }

    public static Drawable getThemedDrawable(Context context, int resId, int color) {
        if (context == null) {
            return null;
        }
        Drawable drawable = context.getResources().getDrawable(resId).mutate();
        drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
        return drawable;
    }

    public static int getDefaultColor(String key) {
        Integer value = defaultColors.get(key);
        if (value == null) {
            if (key.equals(key_chats_menuTopShadow) || key.equals(key_chats_menuTopBackground)) {
                return 0;
            }
            return 0xffff0000;
        }
        return value;
    }

    public static boolean hasThemeKey(String key) {
        return currentColors.containsKey(key);
    }

    public static Integer getColorOrNull(String key) {
        Integer color = currentColors.get(key);
        if (color == null) {
            String fallbackKey = fallbackKeys.get(key);
            if (fallbackKey != null) {
                color = currentColors.get(key);
            }
            if (color == null) {
                color = defaultColors.get(key);
            }
        }
        return color;
    }

    public static void setAnimatingColor(boolean animating) {
        animatingColors = animating ? new HashMap<>() : null;
    }

    public static boolean isAnimatingColor() {
        return animatingColors != null;
    }

    public static void setAnimatedColor(String key, int value) {
        if (animatingColors == null) {
            return;
        }
        animatingColors.put(key, value);
    }

    public static int getColor(String key) {
        return getColor(key, null);
    }

    public static int getColor(String key, boolean[] isDefault) {
        if (animatingColors != null) {
            Integer color = animatingColors.get(key);
            if (color != null) {
                return color;
            }
        }
        if (isCurrentThemeDefault()) {
            if (key.equals(key_chat_serviceBackground)) {
                return serviceMessageColor;
            } else if (key.equals(key_chat_serviceBackgroundSelected)) {
                return serviceSelectedMessageColor;
            }
            return getDefaultColor(key);
        }
        Integer color = currentColors.get(key);
        if (color == null) {
            String fallbackKey = fallbackKeys.get(key);
            if (fallbackKey != null) {
                color = currentColors.get(fallbackKey);
            }
            if (color == null) {
                if (isDefault != null) {
                    isDefault[0] = true;
                }
                if (key.equals(key_chat_serviceBackground)) {
                    return serviceMessageColor;
                } else if (key.equals(key_chat_serviceBackgroundSelected)) {
                    return serviceSelectedMessageColor;
                }
                return getDefaultColor(key);
            }
        }
        return color;
    }

    public static void setColor(String key, int color, boolean useDefault) {
        if (key.equals(key_chat_wallpaper) || key.equals(key_chat_wallpaper_gradient_to)) {
            color = 0xff000000 | color;
        }

        if (useDefault) {
            currentColors.remove(key);
        } else {
            currentColors.put(key, color);
        }

        if (key.equals(key_chat_serviceBackground) || key.equals(key_chat_serviceBackgroundSelected)) {
            applyChatServiceMessageColor();
        } else if (key.equals(key_chat_wallpaper) || key.equals(key_chat_wallpaper_gradient_to)) {
            reloadWallpaper();
        }
    }

    public static void setThemeWallpaper(ThemeInfo themeInfo, Bitmap bitmap, File path) {
        currentColors.remove(key_chat_wallpaper);
        currentColors.remove(key_chat_wallpaper_gradient_to);
        themedWallpaperLink = null;
        MessagesController.getGlobalMainSettings().edit().remove("overrideThemeWallpaper").commit();
        if (bitmap != null) {
            themedWallpaper = new BitmapDrawable(bitmap);
            saveCurrentTheme(themeInfo, false, false, false);
            calcBackgroundColor(themedWallpaper, 0);
            applyChatServiceMessageColor();
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewWallpapper);
        } else {
            themedWallpaper = null;
            wallpaper = null;
            saveCurrentTheme(themeInfo, false, false, false);
            reloadWallpaper();
        }
    }

    public static void setDrawableColor(Drawable drawable, int color) {
        if (drawable == null) {
            return;
        }
        if (drawable instanceof ShapeDrawable) {
            ((ShapeDrawable) drawable).getPaint().setColor(color);
        } else if (drawable instanceof ScamDrawable) {
            ((ScamDrawable) drawable).setColor(color);
        } else {
            drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
        }
    }

    public static void setDrawableColorByKey(Drawable drawable, String key) {
        if (key == null) {
            return;
        }
        setDrawableColor(drawable, getColor(key));
    }

    public static void setEmojiDrawableColor(Drawable drawable, int color, boolean selected) {
        if (drawable instanceof StateListDrawable) {
            try {
                if (selected) {
                    Drawable state = getStateDrawable(drawable, 0);
                    if (state instanceof ShapeDrawable) {
                        ((ShapeDrawable) state).getPaint().setColor(color);
                    } else {
                        state.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                    }
                } else {
                    Drawable state = getStateDrawable(drawable, 1);
                    if (state instanceof ShapeDrawable) {
                        ((ShapeDrawable) state).getPaint().setColor(color);
                    } else {
                        state.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                    }
                }
            } catch (Throwable ignore) {

            }
        }
    }

    public static void setSelectorDrawableColor(Drawable drawable, int color, boolean selected) {
        if (drawable instanceof StateListDrawable) {
            try {
                if (selected) {
                    Drawable state = getStateDrawable(drawable, 0);
                    if (state instanceof ShapeDrawable) {
                        ((ShapeDrawable) state).getPaint().setColor(color);
                    } else {
                        state.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                    }
                    state = getStateDrawable(drawable, 1);
                    if (state instanceof ShapeDrawable) {
                        ((ShapeDrawable) state).getPaint().setColor(color);
                    } else {
                        state.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                    }
                } else {
                    Drawable state = getStateDrawable(drawable, 2);
                    if (state instanceof ShapeDrawable) {
                        ((ShapeDrawable) state).getPaint().setColor(color);
                    } else {
                        state.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                    }
                }
            } catch (Throwable ignore) {

            }
        } else if (Build.VERSION.SDK_INT >= 21 && drawable instanceof RippleDrawable) {
            RippleDrawable rippleDrawable = (RippleDrawable) drawable;
            if (selected) {
                rippleDrawable.setColor(new ColorStateList(
                        new int[][]{StateSet.WILD_CARD},
                        new int[]{color}
                ));
            } else {
                if (rippleDrawable.getNumberOfLayers() > 0) {
                    Drawable drawable1 = rippleDrawable.getDrawable(0);
                    if (drawable1 instanceof ShapeDrawable) {
                        ((ShapeDrawable) drawable1).getPaint().setColor(color);
                    } else {
                        drawable1.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                    }
                }
            }
        }
    }

    public static boolean isThemeWallpaperPublic() {
        return !TextUtils.isEmpty(themedWallpaperLink);
    }

    public static boolean hasWallpaperFromTheme() {
        return currentColors.containsKey(key_chat_wallpaper) || themedWallpaperFileOffset > 0 || !TextUtils.isEmpty(themedWallpaperLink);
    }

    public static boolean isCustomTheme() {
        return isCustomTheme;
    }

    public static int getSelectedColor() {
        return selectedColor;
    }

    public static void reloadWallpaper() {
        wallpaper = null;
        themedWallpaper = null;
        loadWallpaper();
    }

    private static void calcBackgroundColor(Drawable drawable, int save) {
        if (save != 2) {
            int[] result = AndroidUtilities.calcDrawableColor(drawable);
            serviceMessageColor = serviceMessageColorBackup = result[0];
            serviceSelectedMessageColor = serviceSelectedMessageColorBackup = result[1];
            serviceMessage2Color = result[2];
            serviceSelectedMessage2Color = result[3];
        }
    }

    public static int getServiceMessageColor() {
        Integer serviceColor = currentColors.get(key_chat_serviceBackground);
        return serviceColor == null ? serviceMessageColor : serviceColor;
    }

    public static void loadWallpaper() {
        if (wallpaper != null) {
            return;
        }
        Utilities.searchQueue.postRunnable(() -> {
            synchronized (wallpaperSync) {
                SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                boolean overrideTheme = previousTheme == null && preferences.getBoolean("overrideThemeWallpaper", false);
                isWallpaperMotion = preferences.getBoolean("selectedBackgroundMotion", false);
                isPatternWallpaper = preferences.getLong("selectedPattern", 0) != 0;
                if (!overrideTheme) {
                    Integer backgroundColor = currentColors.get(key_chat_wallpaper);
                    if (backgroundColor != null) {
                        Integer gradientToColor = currentColors.get(key_chat_wallpaper_gradient_to);
                        if (gradientToColor == null) {
                            wallpaper = new ColorDrawable(backgroundColor);
                        } else {
                            wallpaper = new BackgroundGradientDrawable(GradientDrawable.Orientation.BL_TR, new int[] { backgroundColor, gradientToColor });
                        }
                        isCustomTheme = true;
                    } else if (themedWallpaperLink != null) {
                        File pathToWallpaper = new File(ApplicationLoader.getFilesDirFixed(), Utilities.MD5(themedWallpaperLink) + ".wp");
                        Bitmap bitmap = BitmapFactory.decodeFile(pathToWallpaper.getAbsolutePath());
                        if (bitmap != null) {
                            themedWallpaper = wallpaper = new BitmapDrawable(bitmap);
                            isCustomTheme = true;
                        }
                    } else if (themedWallpaperFileOffset > 0 && (currentTheme.pathToFile != null || currentTheme.assetName != null)) {
                        FileInputStream stream = null;
                        try {
                            File file;
                            if (currentTheme.assetName != null) {
                                file = Theme.getAssetFile(currentTheme.assetName);
                            } else {
                                file = new File(currentTheme.pathToFile);
                            }
                            stream = new FileInputStream(file);
                            stream.getChannel().position(themedWallpaperFileOffset);
                            Bitmap bitmap = BitmapFactory.decodeStream(stream);
                            if (bitmap != null) {
                                themedWallpaper = wallpaper = new BitmapDrawable(bitmap);
                                isCustomTheme = true;
                            }
                        } catch (Throwable e) {
                            FileLog.e(e);
                        } finally {
                            try {
                                if (stream != null) {
                                    stream.close();
                                }
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    }
                }
                if (wallpaper == null) {
                    int selectedColor = 0;
                    try {
                        long selectedBackground = getSelectedBackgroundId();
                        long selectedPattern = preferences.getLong("selectedPattern", 0);
                        selectedColor = preferences.getInt("selectedColor", 0);
                        if (selectedBackground == DEFAULT_BACKGROUND_ID) {
                            wallpaper = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.background_hd);
                            isCustomTheme = false;
                        } else if (selectedBackground == -1 || selectedBackground < -100 || selectedBackground > 0) {
                            if (selectedColor != 0 && selectedPattern == 0) {
                                wallpaper = new ColorDrawable(selectedColor);
                            } else {
                                File toFile = new File(ApplicationLoader.getFilesDirFixed(), "wallpaper.jpg");
                                long len = toFile.length();
                                if (toFile.exists()) {
                                    wallpaper = Drawable.createFromPath(toFile.getAbsolutePath());
                                    isCustomTheme = true;
                                } else {
                                    wallpaper = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.background_hd);
                                    isCustomTheme = false;
                                }
                            }
                        }
                    } catch (Throwable throwable) {
                        //ignore
                    }
                    if (wallpaper == null) {
                        if (selectedColor == 0) {
                            selectedColor = -2693905;
                        }
                        wallpaper = new ColorDrawable(selectedColor);
                    }
                }
                calcBackgroundColor(wallpaper, 1);
                AndroidUtilities.runOnUIThread(() -> {
                    applyChatServiceMessageColor();
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewWallpapper);
                });
            }
        });
    }

    public static Drawable getThemedWallpaper(boolean thumb) {
        Integer backgroundColor = currentColors.get(key_chat_wallpaper);
        if (backgroundColor != null) {
            Integer gradientToColor = currentColors.get(key_chat_wallpaper_gradient_to);
            if (gradientToColor == null) {
                return new ColorDrawable(backgroundColor);
            } else {
                return new BackgroundGradientDrawable(GradientDrawable.Orientation.BL_TR, new int[]{backgroundColor, gradientToColor});
            }
        } else if (themedWallpaperFileOffset > 0 && (currentTheme.pathToFile != null || currentTheme.assetName != null)) {
            FileInputStream stream = null;
            try {
                int currentPosition = 0;
                File file;
                if (currentTheme.assetName != null) {
                    file = Theme.getAssetFile(currentTheme.assetName);
                } else {
                    file = new File(currentTheme.pathToFile);
                }
                stream = new FileInputStream(file);
                stream.getChannel().position(themedWallpaperFileOffset);
                BitmapFactory.Options opts = new BitmapFactory.Options();
                int scaleFactor = 1;
                if (thumb) {
                    opts.inJustDecodeBounds = true;
                    float photoW = opts.outWidth;
                    float photoH = opts.outHeight;
                    int maxWidth = AndroidUtilities.dp(100);
                    while (photoW > maxWidth || photoH > maxWidth) {
                        scaleFactor *= 2;
                        photoW /= 2;
                        photoH /= 2;
                    }
                }
                opts.inJustDecodeBounds = false;
                opts.inSampleSize = scaleFactor;
                Bitmap bitmap = BitmapFactory.decodeStream(stream, null, opts);
                if (bitmap != null) {
                    return new BitmapDrawable(bitmap);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                try {
                    if (stream != null) {
                        stream.close();
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
        return null;
    }

    public static final long DEFAULT_BACKGROUND_ID = 1000001;
    public static final long THEME_BACKGROUND_ID = -2;
    public static long getSelectedBackgroundId() {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        int background = preferences.getInt("selectedBackground", (int) DEFAULT_BACKGROUND_ID);
        if (background != DEFAULT_BACKGROUND_ID) {
            preferences.edit().putLong("selectedBackground2", background).remove("selectedBackground").commit();
        }
        long id = preferences.getLong("selectedBackground2", DEFAULT_BACKGROUND_ID);
        if (hasWallpaperFromTheme() && !preferences.getBoolean("overrideThemeWallpaper", false)) {
            if (!TextUtils.isEmpty(themedWallpaperLink)) {
                return id;
            } else {
                return THEME_BACKGROUND_ID;
            }
        } else if (id == THEME_BACKGROUND_ID) {
            return DEFAULT_BACKGROUND_ID;
        }
        return id;
    }

    public static Drawable getCachedWallpaper() {
        synchronized (wallpaperSync) {
            if (themedWallpaper != null) {
                return themedWallpaper;
            } else {
                return wallpaper;
            }
        }
    }

    public static Drawable getCachedWallpaperNonBlocking() {
        if (themedWallpaper != null) {
            return themedWallpaper;
        } else {
            return wallpaper;
        }
    }

    public static boolean isWallpaperMotion() {
        return isWallpaperMotion;
    }

    public static boolean isPatternWallpaper() {
        return isPatternWallpaper;
    }
}
