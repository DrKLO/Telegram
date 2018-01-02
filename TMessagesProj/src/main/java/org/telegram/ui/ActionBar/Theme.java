/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.ActionBar;

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
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Build;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.StateSet;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.ThemeEditorView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

public class Theme {

    public static class ThemeInfo {
        public String name;
        public String pathToFile;
        public String assetName;

        public JSONObject getSaveJson() {
            try {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("name", name);
                jsonObject.put("path", pathToFile);
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
            } else if ("Dark".equals(name)) {
                return LocaleController.getString("ThemeDark", R.string.ThemeDark);
            }
            return name;
        }

        public static ThemeInfo createWithJson(JSONObject object) {
            if (object == null) {
                return null;
            }
            try {
                ThemeInfo themeInfo = new ThemeInfo();
                themeInfo.name = object.getString("name");
                themeInfo.pathToFile = object.getString("path");
                return themeInfo;
            } catch (Exception e) {
                FileLog.e(e);
            }
            return null;
        }

        public static ThemeInfo createWithString(String string) {
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

    private static Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public static ArrayList<ThemeInfo> themes;
    private static ArrayList<ThemeInfo> otherThemes;
    private static HashMap<String, ThemeInfo> themesDict;
    private static ThemeInfo currentTheme;
    private static ThemeInfo defaultTheme;
    private static ThemeInfo previousTheme;

    public static PorterDuffColorFilter colorFilter;
    public static PorterDuffColorFilter colorPressedFilter;
    private static int selectedColor;
    private static boolean isCustomTheme;
    private static int serviceMessageColor;
    private static int serviceSelectedMessageColor;
    private static int currentColor;
    private static int currentSelectedColor;
    private static Drawable wallpaper;
    private static Drawable themedWallpaper;
    private static int themedWallpaperFileOffset;

    public static Paint dividerPaint;
    public static Paint linkSelectionPaint;
    public static Paint checkboxSquare_eraserPaint;
    public static Paint checkboxSquare_checkPaint;
    public static Paint checkboxSquare_backgroundPaint;
    public static Paint avatar_backgroundPaint;

    public static Drawable listSelector;
    public static Drawable avatar_broadcastDrawable;
    public static Drawable avatar_savedDrawable;
    public static Drawable avatar_photoDrawable;

    public static Paint dialogs_tabletSeletedPaint;
    public static Paint dialogs_pinnedPaint;
    public static Paint dialogs_countPaint;
    public static Paint dialogs_errorPaint;
    public static Paint dialogs_countGrayPaint;
    public static TextPaint dialogs_namePaint;
    public static TextPaint dialogs_nameEncryptedPaint;
    public static TextPaint dialogs_messagePaint;
    public static TextPaint dialogs_messagePrintingPaint;
    public static TextPaint dialogs_timePaint;
    public static TextPaint dialogs_countTextPaint;
    public static TextPaint dialogs_onlinePaint;
    public static TextPaint dialogs_offlinePaint;
    public static Drawable dialogs_checkDrawable;
    public static Drawable dialogs_halfCheckDrawable;
    public static Drawable dialogs_clockDrawable;
    public static Drawable dialogs_errorDrawable;
    public static Drawable dialogs_lockDrawable;
    public static Drawable dialogs_groupDrawable;
    public static Drawable dialogs_broadcastDrawable;
    public static Drawable dialogs_botDrawable;
    public static Drawable dialogs_muteDrawable;
    public static Drawable dialogs_verifiedDrawable;
    public static Drawable dialogs_verifiedCheckDrawable;
    public static Drawable dialogs_pinnedDrawable;
    public static Drawable dialogs_mentionDrawable;

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
    public static Drawable chat_msgOutBroadcastDrawable;
    public static Drawable chat_msgMediaBroadcastDrawable;
    public static Drawable chat_msgOutLocationDrawable;
    public static Drawable chat_msgBroadcastDrawable;
    public static Drawable chat_msgBroadcastMediaDrawable;
    public static Drawable chat_contextResult_shadowUnderSwitchDrawable;
    public static Drawable chat_shareDrawable;
    public static Drawable chat_shareIconDrawable;
    public static Drawable chat_goIconDrawable;
    public static Drawable chat_botLinkDrawalbe;
    public static Drawable chat_botInlineDrawable;
    public static Drawable chat_systemDrawable;
    public static Drawable chat_msgInCallDrawable;
    public static Drawable chat_msgInCallSelectedDrawable;
    public static Drawable chat_msgOutCallDrawable;
    public static Drawable chat_msgOutCallSelectedDrawable;
    public static Drawable chat_msgCallUpRedDrawable;
    public static Drawable chat_msgCallUpGreenDrawable;
    public static Drawable chat_msgCallDownRedDrawable;
    public static Drawable chat_msgCallDownGreenDrawable;
    public static Drawable chat_msgAvatarLiveLocationDrawable;
    public static Drawable[] chat_attachButtonDrawables = new Drawable[8];
    public static Drawable[] chat_locationDrawable = new Drawable[2];
    public static Drawable[] chat_contactDrawable = new Drawable[2];
    public static Drawable[] chat_cornerOuter = new Drawable[4];
    public static Drawable[] chat_cornerInner = new Drawable[4];
    public static Drawable[][] chat_fileStatesDrawable = new Drawable[10][2];
    public static Drawable[][] chat_ivStatesDrawable = new Drawable[4][2];
    public static Drawable[][] chat_photoStatesDrawables = new Drawable[13][2];

    public static final String key_dialogBackground = "dialogBackground";
    public static final String key_dialogBackgroundGray = "dialogBackgroundGray";
    public static final String key_dialogTextBlack = "dialogTextBlack";
    public static final String key_dialogTextLink = "dialogTextLink";
    public static final String key_dialogLinkSelection = "dialogLinkSelection";
    public static final String key_dialogTextRed = "dialogTextRed";
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
    public static final String key_dialogGrayLine = "dialogGrayLine";
    public static final String key_dialogTopBackground = "dialogTopBackground";

    public static final String key_windowBackgroundWhite = "windowBackgroundWhite";
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
    public static final String key_switchThumb = "switchThumb";
    public static final String key_switchTrack = "switchTrack";
    public static final String key_switchThumbChecked = "switchThumbChecked";
    public static final String key_switchTrackChecked = "switchTrackChecked";
    public static final String key_checkboxSquareBackground = "checkboxSquareBackground";
    public static final String key_checkboxSquareCheck = "checkboxSquareCheck";
    public static final String key_checkboxSquareUnchecked = "checkboxSquareUnchecked";
    public static final String key_checkboxSquareDisabled = "checkboxSquareDisabled";
    public static final String key_windowBackgroundGray = "windowBackgroundGray";
    public static final String key_windowBackgroundGrayShadow = "windowBackgroundGrayShadow";
    public static final String key_emptyListPlaceholder = "emptyListPlaceholder";
    public static final String key_divider = "divider";
    public static final String key_graySection = "graySection";
    public static final String key_radioBackground = "radioBackground";
    public static final String key_radioBackgroundChecked = "radioBackgroundChecked";
    public static final String key_checkbox = "checkbox";
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

    public static final String key_avatar_text = "avatar_text";
    public static final String key_avatar_backgroundSaved = "avatar_backgroundSaved";
    public static final String key_avatar_backgroundRed = "avatar_backgroundRed";
    public static final String key_avatar_backgroundOrange = "avatar_backgroundOrange";
    public static final String key_avatar_backgroundViolet = "avatar_backgroundViolet";
    public static final String key_avatar_backgroundGreen = "avatar_backgroundGreen";
    public static final String key_avatar_backgroundCyan = "avatar_backgroundCyan";
    public static final String key_avatar_backgroundBlue = "avatar_backgroundBlue";
    public static final String key_avatar_backgroundPink = "avatar_backgroundPink";
    public static final String key_avatar_backgroundGroupCreateSpanBlue = "avatar_backgroundGroupCreateSpanBlue";
    public static final String key_avatar_backgroundInProfileRed = "avatar_backgroundInProfileRed";
    public static final String key_avatar_backgroundInProfileOrange = "avatar_backgroundInProfileOrange";
    public static final String key_avatar_backgroundInProfileViolet = "avatar_backgroundInProfileViolet";
    public static final String key_avatar_backgroundInProfileGreen = "avatar_backgroundInProfileGreen";
    public static final String key_avatar_backgroundInProfileCyan = "avatar_backgroundInProfileCyan";
    public static final String key_avatar_backgroundInProfileBlue = "avatar_backgroundInProfileBlue";
    public static final String key_avatar_backgroundInProfilePink = "avatar_backgroundInProfilePink";
    public static final String key_avatar_backgroundActionBarRed = "avatar_backgroundActionBarRed";
    public static final String key_avatar_backgroundActionBarOrange = "avatar_backgroundActionBarOrange";
    public static final String key_avatar_backgroundActionBarViolet = "avatar_backgroundActionBarViolet";
    public static final String key_avatar_backgroundActionBarGreen = "avatar_backgroundActionBarGreen";
    public static final String key_avatar_backgroundActionBarCyan = "avatar_backgroundActionBarCyan";
    public static final String key_avatar_backgroundActionBarBlue = "avatar_backgroundActionBarBlue";
    public static final String key_avatar_backgroundActionBarPink = "avatar_backgroundActionBarPink";
    public static final String key_avatar_subtitleInProfileRed = "avatar_subtitleInProfileRed";
    public static final String key_avatar_subtitleInProfileOrange = "avatar_subtitleInProfileOrange";
    public static final String key_avatar_subtitleInProfileViolet = "avatar_subtitleInProfileViolet";
    public static final String key_avatar_subtitleInProfileGreen = "avatar_subtitleInProfileGreen";
    public static final String key_avatar_subtitleInProfileCyan = "avatar_subtitleInProfileCyan";
    public static final String key_avatar_subtitleInProfileBlue = "avatar_subtitleInProfileBlue";
    public static final String key_avatar_subtitleInProfilePink = "avatar_subtitleInProfilePink";
    public static final String key_avatar_nameInMessageRed = "avatar_nameInMessageRed";
    public static final String key_avatar_nameInMessageOrange = "avatar_nameInMessageOrange";
    public static final String key_avatar_nameInMessageViolet = "avatar_nameInMessageViolet";
    public static final String key_avatar_nameInMessageGreen = "avatar_nameInMessageGreen";
    public static final String key_avatar_nameInMessageCyan = "avatar_nameInMessageCyan";
    public static final String key_avatar_nameInMessageBlue = "avatar_nameInMessageBlue";
    public static final String key_avatar_nameInMessagePink = "avatar_nameInMessagePink";
    public static final String key_avatar_actionBarSelectorRed = "avatar_actionBarSelectorRed";
    public static final String key_avatar_actionBarSelectorOrange = "avatar_actionBarSelectorOrange";
    public static final String key_avatar_actionBarSelectorViolet = "avatar_actionBarSelectorViolet";
    public static final String key_avatar_actionBarSelectorGreen = "avatar_actionBarSelectorGreen";
    public static final String key_avatar_actionBarSelectorCyan = "avatar_actionBarSelectorCyan";
    public static final String key_avatar_actionBarSelectorBlue = "avatar_actionBarSelectorBlue";
    public static final String key_avatar_actionBarSelectorPink = "avatar_actionBarSelectorPink";
    public static final String key_avatar_actionBarIconRed = "avatar_actionBarIconRed";
    public static final String key_avatar_actionBarIconOrange = "avatar_actionBarIconOrange";
    public static final String key_avatar_actionBarIconViolet = "avatar_actionBarIconViolet";
    public static final String key_avatar_actionBarIconGreen = "avatar_actionBarIconGreen";
    public static final String key_avatar_actionBarIconCyan = "avatar_actionBarIconCyan";
    public static final String key_avatar_actionBarIconBlue = "avatar_actionBarIconBlue";
    public static final String key_avatar_actionBarIconPink = "avatar_actionBarIconPink";

    public static String[] keys_avatar_background = {key_avatar_backgroundRed, key_avatar_backgroundOrange, key_avatar_backgroundViolet, key_avatar_backgroundGreen, key_avatar_backgroundCyan, key_avatar_backgroundBlue, key_avatar_backgroundPink};
    public static String[] keys_avatar_backgroundInProfile = {key_avatar_backgroundInProfileRed, key_avatar_backgroundInProfileOrange, key_avatar_backgroundInProfileViolet, key_avatar_backgroundInProfileGreen, key_avatar_backgroundInProfileCyan, key_avatar_backgroundInProfileBlue, key_avatar_backgroundInProfilePink};
    public static String[] keys_avatar_backgroundActionBar = {key_avatar_backgroundActionBarRed, key_avatar_backgroundActionBarOrange, key_avatar_backgroundActionBarViolet, key_avatar_backgroundActionBarGreen, key_avatar_backgroundActionBarCyan, key_avatar_backgroundActionBarBlue, key_avatar_backgroundActionBarPink};
    public static String[] keys_avatar_subtitleInProfile = {key_avatar_subtitleInProfileRed, key_avatar_subtitleInProfileOrange, key_avatar_subtitleInProfileViolet, key_avatar_subtitleInProfileGreen, key_avatar_subtitleInProfileCyan, key_avatar_subtitleInProfileBlue, key_avatar_subtitleInProfilePink};
    public static String[] keys_avatar_nameInMessage = {key_avatar_nameInMessageRed, key_avatar_nameInMessageOrange, key_avatar_nameInMessageViolet, key_avatar_nameInMessageGreen, key_avatar_nameInMessageCyan, key_avatar_nameInMessageBlue, key_avatar_nameInMessagePink};
    public static String[] keys_avatar_actionBarSelector = {key_avatar_actionBarSelectorRed, key_avatar_actionBarSelectorOrange, key_avatar_actionBarSelectorViolet, key_avatar_actionBarSelectorGreen, key_avatar_actionBarSelectorCyan, key_avatar_actionBarSelectorBlue, key_avatar_actionBarSelectorPink};
    public static String[] keys_avatar_actionBarIcon = {key_avatar_actionBarIconRed, key_avatar_actionBarIconOrange, key_avatar_actionBarIconViolet, key_avatar_actionBarIconGreen, key_avatar_actionBarIconCyan, key_avatar_actionBarIconBlue, key_avatar_actionBarIconPink};

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
    public static final String key_actionBarDefaultSubmenuBackground = "actionBarDefaultSubmenuBackground";
    public static final String key_chats_unreadCounter = "chats_unreadCounter";
    public static final String key_chats_unreadCounterMuted = "chats_unreadCounterMuted";
    public static final String key_chats_unreadCounterText = "chats_unreadCounterText";
    public static final String key_chats_name = "chats_name";
    public static final String key_chats_secretName = "chats_secretName";
    public static final String key_chats_secretIcon = "chats_secretIcon";
    public static final String key_chats_nameIcon = "chats_nameIcon";
    public static final String key_chats_pinnedIcon = "chats_pinnedIcon";
    public static final String key_chats_message = "chats_message";
    public static final String key_chats_draft = "chats_draft";
    public static final String key_chats_nameMessage = "chats_nameMessage";
    public static final String key_chats_attachMessage = "chats_attachMessage";
    public static final String key_chats_actionMessage = "chats_actionMessage";
    public static final String key_chats_date = "chats_date";
    public static final String key_chats_pinnedOverlay = "chats_pinnedOverlay";
    public static final String key_chats_tabletSelectedOverlay = "chats_tabletSelectedOverlay";
    public static final String key_chats_sentCheck = "chats_sentCheck";
    public static final String key_chats_sentClock = "chats_sentClock";
    public static final String key_chats_sentError = "chats_sentError";
    public static final String key_chats_sentErrorIcon = "chats_sentErrorIcon";
    public static final String key_chats_verifiedBackground = "chats_verifiedBackground";
    public static final String key_chats_verifiedCheck = "chats_verifiedCheck";
    public static final String key_chats_muteIcon = "chats_muteIcon";
    public static final String key_chats_menuTopShadow = "chats_menuTopShadow";
    public static final String key_chats_menuBackground = "chats_menuBackground";
    public static final String key_chats_menuItemText = "chats_menuItemText";
    public static final String key_chats_menuItemIcon = "chats_menuItemIcon";
    public static final String key_chats_menuName = "chats_menuName";
    public static final String key_chats_menuPhone = "chats_menuPhone";
    public static final String key_chats_menuPhoneCats = "chats_menuPhoneCats";
    public static final String key_chats_menuCloud = "chats_menuCloud";
    public static final String key_chats_menuCloudBackgroundCats = "chats_menuCloudBackgroundCats";
    public static final String key_chats_actionIcon = "chats_actionIcon";
    public static final String key_chats_actionBackground = "chats_actionBackground";
    public static final String key_chats_actionPressedBackground = "chats_actionPressedBackground";

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
    public static final String key_chat_muteIcon = "chat_muteIcon";
    public static final String key_chat_lockIcon = "chat_lockIcon";
    public static final String key_chat_outSentCheck = "chat_outSentCheck";
    public static final String key_chat_outSentCheckSelected = "chat_outSentCheckSelected";
    public static final String key_chat_outSentClock = "chat_outSentClock";
    public static final String key_chat_outSentClockSelected = "chat_outSentClockSelected";
    public static final String key_chat_inSentClock = "chat_inSentClock";
    public static final String key_chat_inSentClockSelected = "chat_inSentClockSelected";
    public static final String key_chat_mediaSentCheck = "chat_mediaSentCheck";
    public static final String key_chat_mediaSentClock = "chat_mediaSentClock";
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
    public static final String key_chat_outContactPhoneText = "chat_outContactPhoneText";
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
    public static final String key_chat_inAudioPerfomerText = "chat_inAudioPerfomerText";
    public static final String key_chat_outAudioPerfomerText = "chat_outAudioPerfomerText";
    public static final String key_chat_inAudioTitleText = "chat_inAudioTitleText";
    public static final String key_chat_outAudioTitleText = "chat_outAudioTitleText";
    public static final String key_chat_inAudioDurationText = "chat_inAudioDurationText";
    public static final String key_chat_outAudioDurationText = "chat_outAudioDurationText";
    public static final String key_chat_inAudioDurationSelectedText = "chat_inAudioDurationSelectedText";
    public static final String key_chat_outAudioDurationSelectedText = "chat_outAudioDurationSelectedText";
    public static final String key_chat_inAudioSeekbar = "chat_inAudioSeekbar";
    public static final String key_chat_outAudioSeekbar = "chat_outAudioSeekbar";
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
    public static final String key_chat_inVenueNameText = "chat_inVenueNameText";
    public static final String key_chat_outVenueNameText = "chat_outVenueNameText";
    public static final String key_chat_inVenueInfoText = "chat_inVenueInfoText";
    public static final String key_chat_outVenueInfoText = "chat_outVenueInfoText";
    public static final String key_chat_inVenueInfoSelectedText = "chat_inVenueInfoSelectedText";
    public static final String key_chat_outVenueInfoSelectedText = "chat_outVenueInfoSelectedText";
    public static final String key_chat_mediaInfoText = "chat_mediaInfoText";
    public static final String key_chat_linkSelectBackground = "chat_linkSelectBackground";
    public static final String key_chat_textSelectBackground = "chat_textSelectBackground";
    public static final String key_chat_wallpaper = "chat_wallpaper";
    public static final String key_chat_messagePanelBackground = "chat_messagePanelBackground";
    public static final String key_chat_messagePanelShadow = "chat_messagePanelShadow";
    public static final String key_chat_messagePanelText = "chat_messagePanelText";
    public static final String key_chat_messagePanelHint = "chat_messagePanelHint";
    public static final String key_chat_messagePanelIcons = "chat_messagePanelIcons";
    public static final String key_chat_messagePanelSend = "chat_messagePanelSend";
    public static final String key_chat_messagePanelVoiceLock = "key_chat_messagePanelVoiceLock";
    public static final String key_chat_messagePanelVoiceLockBackground = "key_chat_messagePanelVoiceLockBackground";
    public static final String key_chat_messagePanelVoiceLockShadow = "key_chat_messagePanelVoiceLockShadow";
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
    public static final String key_chat_emojiPanelShadowLine = "chat_emojiPanelShadowLine";
    public static final String key_chat_emojiPanelEmptyText = "chat_emojiPanelEmptyText";
    public static final String key_chat_emojiPanelIcon = "chat_emojiPanelIcon";
    public static final String key_chat_emojiPanelIconSelected = "chat_emojiPanelIconSelected";
    public static final String key_chat_emojiPanelStickerPackSelector = "chat_emojiPanelStickerPackSelector";
    public static final String key_chat_emojiPanelIconSelector = "chat_emojiPanelIconSelector";
    public static final String key_chat_emojiPanelBackspace = "chat_emojiPanelBackspace";
    public static final String key_chat_emojiPanelMasksIcon = "chat_emojiPanelMasksIcon";
    public static final String key_chat_emojiPanelMasksIconSelected = "chat_emojiPanelMasksIconSelected";
    public static final String key_chat_emojiPanelTrendingTitle = "chat_emojiPanelTrendingTitle";
    public static final String key_chat_emojiPanelStickerSetName = "chat_emojiPanelStickerSetName";
    public static final String key_chat_emojiPanelStickerSetNameIcon = "chat_emojiPanelStickerSetNameIcon";
    public static final String key_chat_emojiPanelTrendingDescription = "chat_emojiPanelTrendingDescription";
    public static final String key_chat_botKeyboardButtonText = "chat_botKeyboardButtonText";
    public static final String key_chat_botKeyboardButtonBackground = "chat_botKeyboardButtonBackground";
    public static final String key_chat_botKeyboardButtonBackgroundPressed = "chat_botKeyboardButtonBackgroundPressed";
    public static final String key_chat_emojiPanelNewTrending = "chat_emojiPanelNewTrending";
    public static final String key_chat_editDoneIcon = "chat_editDoneIcon";
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

    public static final String key_profile_creatorIcon = "profile_creatorIcon";
    public static final String key_profile_adminIcon = "profile_adminIcon";
    public static final String key_profile_title = "profile_title";
    public static final String key_profile_actionIcon = "profile_actionIcon";
    public static final String key_profile_actionBackground = "profile_actionBackground";
    public static final String key_profile_actionPressedBackground = "profile_actionPressedBackground";
    public static final String key_profile_verifiedBackground = "profile_verifiedBackground";
    public static final String key_profile_verifiedCheck = "profile_verifiedCheck";

    public static final String key_sharedMedia_startStopLoadIcon = "sharedMedia_startStopLoadIcon";
    public static final String key_sharedMedia_linkPlaceholder = "sharedMedia_linkPlaceholder";
    public static final String key_sharedMedia_linkPlaceholderText = "sharedMedia_linkPlaceholderText";

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

    public static final String key_groupcreate_hintText = "groupcreate_hintText";
    public static final String key_groupcreate_cursor = "groupcreate_cursor";
    public static final String key_groupcreate_sectionShadow = "groupcreate_sectionShadow";
    public static final String key_groupcreate_sectionText = "groupcreate_sectionText";
    public static final String key_groupcreate_onlineText = "groupcreate_onlineText";
    public static final String key_groupcreate_offlineText = "groupcreate_offlineText";
    public static final String key_groupcreate_checkbox = "groupcreate_checkbox";
    public static final String key_groupcreate_checkboxCheck = "groupcreate_checkboxCheck";
    public static final String key_groupcreate_spanText = "groupcreate_spanText";
    public static final String key_groupcreate_spanBackground = "groupcreate_spanBackground";

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

    public static final String key_location_markerX = "location_markerX";
    public static final String key_location_sendLocationBackground = "location_sendLocationBackground";
    public static final String key_location_sendLiveLocationBackground = "location_sendLiveLocationBackground";
    public static final String key_location_sendLocationIcon = "location_sendLocationIcon";
    public static final String key_location_liveLocationProgress = "location_liveLocationProgress";
    public static final String key_location_placeLocationBackground = "location_placeLocationBackground";
    public static final String key_dialog_liveLocationProgress = "location_liveLocationProgress";

    public static final String key_files_folderIcon = "files_folderIcon";
    public static final String key_files_folderIconBackground = "files_folderIconBackground";
    public static final String key_files_iconText = "files_iconText";

    public static final String key_sessions_devicesImage = "sessions_devicesImage";

    public static final String key_calls_callReceivedGreenIcon = "calls_callReceivedGreenIcon";
    public static final String key_calls_callReceivedRedIcon = "calls_callReceivedRedIcon";

    public static final String key_calls_ratingStar = "calls_ratingStar";
    public static final String key_calls_ratingStarSelected = "calls_ratingStarSelected";

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
    public static final String key_player_progress = "player_progress";
    public static final String key_player_placeholder = "player_placeholder";
    public static final String key_player_placeholderBackground = "player_placeholderBackground";
    public static final String key_player_button = "player_button";
    public static final String key_player_buttonActive = "player_buttonActive";

    private static HashMap<String, Integer> defaultColors = new HashMap<>();
    private static HashMap<String, String> fallbackKeys = new HashMap<>();
    private static HashMap<String, Integer> currentColors;

    static {
        defaultColors.put(key_dialogBackground, 0xffffffff);
        defaultColors.put(key_dialogBackgroundGray, 0xfff0f0f0);
        defaultColors.put(key_dialogTextBlack, 0xff212121);
        defaultColors.put(key_dialogTextLink, 0xff2678b6);
        defaultColors.put(key_dialogLinkSelection, 0x3362a9e3);
        defaultColors.put(key_dialogTextRed, 0xffcd5a5a);
        defaultColors.put(key_dialogTextBlue, 0xff2f8cc9);
        defaultColors.put(key_dialogTextBlue2, 0xff3a8ccf);
        defaultColors.put(key_dialogTextBlue3, 0xff3ec1f9);
        defaultColors.put(key_dialogTextBlue4, 0xff19a7e8);
        defaultColors.put(key_dialogTextGray, 0xff348bc1);
        defaultColors.put(key_dialogTextGray2, 0xff757575);
        defaultColors.put(key_dialogTextGray3, 0xff999999);
        defaultColors.put(key_dialogTextGray4, 0xffb3b3b3);
        defaultColors.put(key_dialogTextHint, 0xff979797);
        defaultColors.put(key_dialogIcon, 0xff8a8a8a);
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
        defaultColors.put(key_dialogRoundCheckBox, 0xff3ec1f9);
        defaultColors.put(key_dialogRoundCheckBoxCheck, 0xffffffff);
        defaultColors.put(key_dialogBadgeBackground, 0xff3ec1f9);
        defaultColors.put(key_dialogBadgeText, 0xffffffff);

        defaultColors.put(key_windowBackgroundWhite, 0xffffffff);
        defaultColors.put(key_progressCircle, 0xff527da3);
        defaultColors.put(key_windowBackgroundWhiteGrayIcon, 0xff737373);
        defaultColors.put(key_windowBackgroundWhiteBlueText, 0xff3b84c0);
        defaultColors.put(key_windowBackgroundWhiteBlueText2, 0xff348bc1);
        defaultColors.put(key_windowBackgroundWhiteBlueText3, 0xff2678b6);
        defaultColors.put(key_windowBackgroundWhiteBlueText4, 0xff4d83b3);
        defaultColors.put(key_windowBackgroundWhiteBlueText5, 0xff4c8eca);
        defaultColors.put(key_windowBackgroundWhiteBlueText6, 0xff3a8ccf);
        defaultColors.put(key_windowBackgroundWhiteBlueText7, 0xff377aae);
        defaultColors.put(key_windowBackgroundWhiteGreenText, 0xff26972c);
        defaultColors.put(key_windowBackgroundWhiteGreenText2, 0xff37a919);
        defaultColors.put(key_windowBackgroundWhiteRedText, 0xffcd5a5a);
        defaultColors.put(key_windowBackgroundWhiteRedText2, 0xffdb5151);
        defaultColors.put(key_windowBackgroundWhiteRedText3, 0xffd24949);
        defaultColors.put(key_windowBackgroundWhiteRedText4, 0xffcf3030);
        defaultColors.put(key_windowBackgroundWhiteRedText5, 0xffed3d39);
        defaultColors.put(key_windowBackgroundWhiteRedText6, 0xffff6666);
        defaultColors.put(key_windowBackgroundWhiteGrayText, 0xffa8a8a8);
        defaultColors.put(key_windowBackgroundWhiteGrayText2, 0xff8a8a8a);
        defaultColors.put(key_windowBackgroundWhiteGrayText3, 0xff999999);
        defaultColors.put(key_windowBackgroundWhiteGrayText4, 0xff808080);
        defaultColors.put(key_windowBackgroundWhiteGrayText5, 0xffa3a3a3);
        defaultColors.put(key_windowBackgroundWhiteGrayText6, 0xff757575);
        defaultColors.put(key_windowBackgroundWhiteGrayText7, 0xffc6c6c6);
        defaultColors.put(key_windowBackgroundWhiteGrayText8, 0xff6d6d72);
        defaultColors.put(key_windowBackgroundWhiteGrayLine, 0xffdbdbdb);
        defaultColors.put(key_windowBackgroundWhiteBlackText, 0xff212121);
        defaultColors.put(key_windowBackgroundWhiteHintText, 0xff979797);
        defaultColors.put(key_windowBackgroundWhiteValueText, 0xff2f8cc9);
        defaultColors.put(key_windowBackgroundWhiteLinkText, 0xff2678b6);
        defaultColors.put(key_windowBackgroundWhiteLinkSelection, 0x3362a9e3);
        defaultColors.put(key_windowBackgroundWhiteBlueHeader, 0xff3e90cf);
        defaultColors.put(key_windowBackgroundWhiteInputField, 0xffdbdbdb);
        defaultColors.put(key_windowBackgroundWhiteInputFieldActivated, 0xff37a9f0);
        defaultColors.put(key_switchThumb, 0xffededed);
        defaultColors.put(key_switchTrack, 0xffc7c7c7);
        defaultColors.put(key_switchThumbChecked, 0xff45abef);
        defaultColors.put(key_switchTrackChecked, 0xffa0d6fa);
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
        defaultColors.put(key_graySection, 0xfff2f2f2);
        defaultColors.put(key_contextProgressInner1, 0xffbfdff6);
        defaultColors.put(key_contextProgressOuter1, 0xff2b96e2);
        defaultColors.put(key_contextProgressInner2, 0xffbfdff6);
        defaultColors.put(key_contextProgressOuter2, 0xffffffff);
        defaultColors.put(key_contextProgressInner3, 0xffb3b3b3);
        defaultColors.put(key_contextProgressOuter3, 0xffffffff);
        defaultColors.put(key_fastScrollActive, 0xff52a3db);
        defaultColors.put(key_fastScrollInactive, 0xff636363);
        defaultColors.put(key_fastScrollText, 0xffffffff);

        defaultColors.put(key_avatar_text, 0xffffffff);

        defaultColors.put(key_avatar_backgroundSaved, 0xff66bffa);
        defaultColors.put(key_avatar_backgroundRed, 0xffe56555);
        defaultColors.put(key_avatar_backgroundOrange, 0xfff28c48);
        defaultColors.put(key_avatar_backgroundViolet, 0xff8e85ee);
        defaultColors.put(key_avatar_backgroundGreen, 0xff76c84d);
        defaultColors.put(key_avatar_backgroundCyan, 0xff5fbed5);
        defaultColors.put(key_avatar_backgroundBlue, 0xff549cdd);
        defaultColors.put(key_avatar_backgroundPink, 0xfff2749a);
        defaultColors.put(key_avatar_backgroundGroupCreateSpanBlue, 0xffbfd6ea);
        defaultColors.put(key_avatar_backgroundInProfileRed, 0xffd86f65);
        defaultColors.put(key_avatar_backgroundInProfileOrange, 0xfff69d61);
        defaultColors.put(key_avatar_backgroundInProfileViolet, 0xff8c79d2);
        defaultColors.put(key_avatar_backgroundInProfileGreen, 0xff67b35d);
        defaultColors.put(key_avatar_backgroundInProfileCyan, 0xff56a2bb);
        defaultColors.put(key_avatar_backgroundInProfileBlue, 0xff5085b1);
        defaultColors.put(key_avatar_backgroundInProfilePink, 0xfff37fa6);
        defaultColors.put(key_avatar_backgroundActionBarRed, 0xffca6056);
        defaultColors.put(key_avatar_backgroundActionBarOrange, 0xfff18944);
        defaultColors.put(key_avatar_backgroundActionBarViolet, 0xff7d6ac4);
        defaultColors.put(key_avatar_backgroundActionBarGreen, 0xff56a14c);
        defaultColors.put(key_avatar_backgroundActionBarCyan, 0xff4492ac);
        defaultColors.put(key_avatar_backgroundActionBarBlue, 0xff598fba);
        defaultColors.put(key_avatar_backgroundActionBarPink, 0xff598fba);
        defaultColors.put(key_avatar_subtitleInProfileRed, 0xfff9cbc5);
        defaultColors.put(key_avatar_subtitleInProfileOrange, 0xfffdddc8);
        defaultColors.put(key_avatar_subtitleInProfileViolet, 0xffcdc4ed);
        defaultColors.put(key_avatar_subtitleInProfileGreen, 0xffc0edba);
        defaultColors.put(key_avatar_subtitleInProfileCyan, 0xffb8e2f0);
        defaultColors.put(key_avatar_subtitleInProfileBlue, 0xffd7eafa);
        defaultColors.put(key_avatar_subtitleInProfilePink, 0xffd7eafa);
        defaultColors.put(key_avatar_nameInMessageRed, 0xffca5650);
        defaultColors.put(key_avatar_nameInMessageOrange, 0xffd87b29);
        defaultColors.put(key_avatar_nameInMessageViolet, 0xff4e92cc);
        defaultColors.put(key_avatar_nameInMessageGreen, 0xff50b232);
        defaultColors.put(key_avatar_nameInMessageCyan, 0xff42b1a8);
        defaultColors.put(key_avatar_nameInMessageBlue, 0xff4e92cc);
        defaultColors.put(key_avatar_nameInMessagePink, 0xff4e92cc);
        defaultColors.put(key_avatar_actionBarSelectorRed, 0xffbc4b41);
        defaultColors.put(key_avatar_actionBarSelectorOrange, 0xffe67429);
        defaultColors.put(key_avatar_actionBarSelectorViolet, 0xff735fbe);
        defaultColors.put(key_avatar_actionBarSelectorGreen, 0xff48953d);
        defaultColors.put(key_avatar_actionBarSelectorCyan, 0xff39849d);
        defaultColors.put(key_avatar_actionBarSelectorBlue, 0xff4981ad);
        defaultColors.put(key_avatar_actionBarSelectorPink, 0xff4981ad);
        defaultColors.put(key_avatar_actionBarIconRed, 0xffffffff);
        defaultColors.put(key_avatar_actionBarIconOrange, 0xffffffff);
        defaultColors.put(key_avatar_actionBarIconViolet, 0xffffffff);
        defaultColors.put(key_avatar_actionBarIconGreen, 0xffffffff);
        defaultColors.put(key_avatar_actionBarIconCyan, 0xffffffff);
        defaultColors.put(key_avatar_actionBarIconBlue, 0xffffffff);
        defaultColors.put(key_avatar_actionBarIconPink, 0xffffffff);

        defaultColors.put(key_actionBarDefault, 0xff527da3);
        defaultColors.put(key_actionBarDefaultIcon, 0xffffffff);
        defaultColors.put(key_actionBarActionModeDefault, 0xffffffff);
        defaultColors.put(key_actionBarActionModeDefaultTop, 0x99000000);
        defaultColors.put(key_actionBarActionModeDefaultIcon, 0xff737373);
        defaultColors.put(key_actionBarDefaultTitle, 0xffffffff);
        defaultColors.put(key_actionBarDefaultSubtitle, 0xffd5e8f7);
        defaultColors.put(key_actionBarDefaultSelector, 0xff406d94);
        defaultColors.put(key_actionBarWhiteSelector, 0x2f000000);
        defaultColors.put(key_actionBarDefaultSearch, 0xffffffff);
        defaultColors.put(key_actionBarDefaultSearchPlaceholder, 0x88ffffff);
        defaultColors.put(key_actionBarDefaultSubmenuItem, 0xff212121);
        defaultColors.put(key_actionBarDefaultSubmenuBackground, 0xffffffff);
        defaultColors.put(key_actionBarActionModeDefaultSelector, 0xfff0f0f0);

        defaultColors.put(key_chats_unreadCounter, 0xff4ecc5e);
        defaultColors.put(key_chats_unreadCounterMuted, 0xffc7c7c7);
        defaultColors.put(key_chats_unreadCounterText, 0xffffffff);
        defaultColors.put(key_chats_name, 0xff212121);
        defaultColors.put(key_chats_secretName, 0xff00a60e);
        defaultColors.put(key_chats_secretIcon, 0xff19b126);
        defaultColors.put(key_chats_nameIcon, 0xff242424);
        defaultColors.put(key_chats_pinnedIcon, 0xffa8a8a8);
        defaultColors.put(key_chats_message, 0xff8f8f8f);
        defaultColors.put(key_chats_draft, 0xffdd4b39);
        defaultColors.put(key_chats_nameMessage, 0xff4d83b3);
        defaultColors.put(key_chats_attachMessage, 0xff4d83b3);
        defaultColors.put(key_chats_actionMessage, 0xff4d83b3);
        defaultColors.put(key_chats_date, 0xff999999);
        defaultColors.put(key_chats_pinnedOverlay, 0x08000000);
        defaultColors.put(key_chats_tabletSelectedOverlay, 0x0f000000);
        defaultColors.put(key_chats_sentCheck, 0xff46aa36);
        defaultColors.put(key_chats_sentClock, 0xff75bd5e);
        defaultColors.put(key_chats_sentError, 0xffd55252);
        defaultColors.put(key_chats_sentErrorIcon, 0xffffffff);
        defaultColors.put(key_chats_verifiedBackground, 0xff33a8e6);
        defaultColors.put(key_chats_verifiedCheck, 0xffffffff);
        defaultColors.put(key_chats_muteIcon, 0xffa8a8a8);
        defaultColors.put(key_chats_menuBackground, 0xffffffff);
        defaultColors.put(key_chats_menuItemText, 0xff444444);
        defaultColors.put(key_chats_menuItemIcon, 0xff737373);
        defaultColors.put(key_chats_menuName, 0xffffffff);
        defaultColors.put(key_chats_menuPhone, 0xffffffff);
        defaultColors.put(key_chats_menuPhoneCats, 0xffc2e5ff);
        defaultColors.put(key_chats_menuCloud, 0xffffffff);
        defaultColors.put(key_chats_menuCloudBackgroundCats, 0xff427ba9);
        defaultColors.put(key_chats_actionIcon, 0xffffffff);
        defaultColors.put(key_chats_actionBackground, 0xff6aa1ce);
        defaultColors.put(key_chats_actionPressedBackground, 0xff5792c2);

        defaultColors.put(key_chat_lockIcon, 0xffffffff);
        defaultColors.put(key_chat_muteIcon, 0xffb1cce3);
        defaultColors.put(key_chat_inBubble, 0xffffffff);
        defaultColors.put(key_chat_inBubbleSelected, 0xffe2f8ff);
        defaultColors.put(key_chat_inBubbleShadow, 0xff1d3753);
        defaultColors.put(key_chat_outBubble, 0xffefffde);
        defaultColors.put(key_chat_outBubbleSelected, 0xffd4f5bc);
        defaultColors.put(key_chat_outBubbleShadow, 0xff1e750c);
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
        defaultColors.put(key_chat_selectedBackground, 0x6633b5e5);
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
        defaultColors.put(key_chat_outContactPhoneText, 0xff354234);
        defaultColors.put(key_chat_mediaProgress, 0xffffffff);
        defaultColors.put(key_chat_inAudioProgress, 0xffffffff);
        defaultColors.put(key_chat_outAudioProgress, 0xffefffde);
        defaultColors.put(key_chat_inAudioSelectedProgress, 0xffe2f8ff);
        defaultColors.put(key_chat_outAudioSelectedProgress, 0xffd4f5bc);
        defaultColors.put(key_chat_mediaTimeText, 0xffffffff);
        defaultColors.put(key_chat_inTimeText, 0xffa1aab3);
        defaultColors.put(key_chat_outTimeText, 0xff70b15c);
        defaultColors.put(key_chat_adminText, 0xffc0c6cb);
        defaultColors.put(key_chat_adminSelectedText, 0xff89b4c1);
        defaultColors.put(key_chat_inTimeSelectedText, 0xff89b4c1);
        defaultColors.put(key_chat_outTimeSelectedText, 0xff70b15c);
        defaultColors.put(key_chat_inAudioPerfomerText, 0xff2f3438);
        defaultColors.put(key_chat_outAudioPerfomerText, 0xff354234);
        defaultColors.put(key_chat_inAudioTitleText, 0xff4e9ad4);
        defaultColors.put(key_chat_outAudioTitleText, 0xff55ab4f);
        defaultColors.put(key_chat_inAudioDurationText, 0xffa1aab3);
        defaultColors.put(key_chat_outAudioDurationText, 0xff65b05b);
        defaultColors.put(key_chat_inAudioDurationSelectedText, 0xff89b4c1);
        defaultColors.put(key_chat_outAudioDurationSelectedText, 0xff65b05b);
        defaultColors.put(key_chat_inAudioSeekbar, 0xffe4eaf0);
        defaultColors.put(key_chat_outAudioSeekbar, 0xffbbe3ac);
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
        defaultColors.put(key_chat_inVenueNameText, 0xff4e9ad4);
        defaultColors.put(key_chat_outVenueNameText, 0xff55ab4f);
        defaultColors.put(key_chat_inVenueInfoText, 0xffa1aab3);
        defaultColors.put(key_chat_outVenueInfoText, 0xff65b05b);
        defaultColors.put(key_chat_inVenueInfoSelectedText, 0xff89b4c1);
        defaultColors.put(key_chat_outVenueInfoSelectedText, 0xff65b05b);
        defaultColors.put(key_chat_mediaInfoText, 0xffffffff);
        defaultColors.put(key_chat_linkSelectBackground, 0x3362a9e3);
        defaultColors.put(key_chat_textSelectBackground, 0x6662a9e3);
        defaultColors.put(key_chat_emojiPanelBackground, 0xfff5f6f7);
        defaultColors.put(key_chat_emojiPanelShadowLine, 0xffe2e5e7);
        defaultColors.put(key_chat_emojiPanelEmptyText, 0xff888888);
        defaultColors.put(key_chat_emojiPanelIcon, 0xffa8a8a8);
        defaultColors.put(key_chat_emojiPanelIconSelected, 0xff2b96e2);
        defaultColors.put(key_chat_emojiPanelStickerPackSelector, 0xffe2e5e7);
        defaultColors.put(key_chat_emojiPanelIconSelector, 0xff2b96e2);
        defaultColors.put(key_chat_emojiPanelBackspace, 0xffa8a8a8);
        defaultColors.put(key_chat_emojiPanelMasksIcon, 0xffffffff);
        defaultColors.put(key_chat_emojiPanelMasksIconSelected, 0xff62bfe8);
        defaultColors.put(key_chat_emojiPanelTrendingTitle, 0xff212121);
        defaultColors.put(key_chat_emojiPanelStickerSetName, 0xff838c96);
        defaultColors.put(key_chat_emojiPanelStickerSetNameIcon, 0xffb1b6bc);
        defaultColors.put(key_chat_emojiPanelTrendingDescription, 0xff8a8a8a);
        defaultColors.put(key_chat_botKeyboardButtonText, 0xff36474f);
        defaultColors.put(key_chat_botKeyboardButtonBackground, 0xffe4e7e9);
        defaultColors.put(key_chat_botKeyboardButtonBackgroundPressed, 0xffccd1d4);
        defaultColors.put(key_chat_unreadMessagesStartArrowIcon, 0xffa2b5c7);
        defaultColors.put(key_chat_unreadMessagesStartText, 0xff5695cc);
        defaultColors.put(key_chat_unreadMessagesStartBackground, 0xffffffff);
        defaultColors.put(key_chat_editDoneIcon, 0xff51bdf3);
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
        defaultColors.put(key_chat_searchPanelIcons, 0xff5da5dc);
        defaultColors.put(key_chat_searchPanelText, 0xff4e9ad4);
        defaultColors.put(key_chat_secretChatStatusText, 0xff7f7f7f);
        defaultColors.put(key_chat_fieldOverlayText, 0xff3a8ccf);
        defaultColors.put(key_chat_stickersHintPanel, 0xffffffff);
        defaultColors.put(key_chat_replyPanelIcons, 0xff57a8e6);
        defaultColors.put(key_chat_replyPanelClose, 0xffa8a8a8);
        defaultColors.put(key_chat_replyPanelName, 0xff3a8ccf);
        defaultColors.put(key_chat_replyPanelMessage, 0xff222222);
        defaultColors.put(key_chat_replyPanelLine, 0xffe8e8e8);
        defaultColors.put(key_chat_messagePanelBackground, 0xffffffff);
        defaultColors.put(key_chat_messagePanelText, 0xff000000);
        defaultColors.put(key_chat_messagePanelHint, 0xffb2b2b2);
        defaultColors.put(key_chat_messagePanelShadow, 0xff000000);
        defaultColors.put(key_chat_messagePanelIcons, 0xffa8a8a8);
        defaultColors.put(key_chat_recordedVoicePlayPause, 0xffffffff);
        defaultColors.put(key_chat_recordedVoicePlayPausePressed, 0xffd9eafb);
        defaultColors.put(key_chat_recordedVoiceDot, 0xffda564d);
        defaultColors.put(key_chat_recordedVoiceBackground, 0xff559ee3);
        defaultColors.put(key_chat_recordedVoiceProgress, 0xffa2cef8);
        defaultColors.put(key_chat_recordedVoiceProgressInner, 0xffffffff);
        defaultColors.put(key_chat_recordVoiceCancel, 0xff999999);
        defaultColors.put(key_chat_messagePanelSend, 0xff62b0eb);
        defaultColors.put(key_chat_messagePanelVoiceLock, 0xffa4a4a4);
        defaultColors.put(key_chat_messagePanelVoiceLockBackground, 0xffffffff);
        defaultColors.put(key_chat_messagePanelVoiceLockShadow, 0xff000000);
        defaultColors.put(key_chat_recordTime, 0xff4d4c4b);
        defaultColors.put(key_chat_emojiPanelNewTrending, 0xff4da6ea);
        defaultColors.put(key_chat_gifSaveHintText, 0xffffffff);
        defaultColors.put(key_chat_gifSaveHintBackground, 0xcc111111);
        defaultColors.put(key_chat_goDownButton, 0xffffffff);
        defaultColors.put(key_chat_goDownButtonShadow, 0xff000000);
        defaultColors.put(key_chat_goDownButtonIcon, 0xffa8a8a8);
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
        defaultColors.put(key_chat_topPanelClose, 0xffa8a8a8);
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

        defaultColors.put(key_profile_creatorIcon, 0xff4a97d6);
        defaultColors.put(key_profile_adminIcon, 0xff858585);
        defaultColors.put(key_profile_actionIcon, 0xff737373);
        defaultColors.put(key_profile_actionBackground, 0xffffffff);
        defaultColors.put(key_profile_actionPressedBackground, 0xfff2f2f2);
        defaultColors.put(key_profile_verifiedBackground, 0xffb2d6f8);
        defaultColors.put(key_profile_verifiedCheck, 0xff4983b8);
        defaultColors.put(key_profile_title, 0xffffffff);

        defaultColors.put(key_player_actionBar, 0xffffffff);
        defaultColors.put(key_player_actionBarSelector, 0x2f000000);
        defaultColors.put(key_player_actionBarTitle, 0xff2f3438);
        defaultColors.put(key_player_actionBarTop, 0x99000000);
        defaultColors.put(key_player_actionBarSubtitle, 0xff8a8a8a);
        defaultColors.put(key_player_actionBarItems, 0xff8a8a8a);
        defaultColors.put(key_player_background, 0xffffffff);
        defaultColors.put(key_player_time, 0xff8c9296);
        defaultColors.put(key_player_progressBackground, 0x19000000);
        defaultColors.put(key_player_progress, 0xff23afef);
        defaultColors.put(key_player_placeholder, 0xffa8a8a8);
        defaultColors.put(key_player_placeholderBackground, 0xfff0f0f0);
        defaultColors.put(key_player_button, 0xff333333);
        defaultColors.put(key_player_buttonActive, 0xff4ca8ea);

        defaultColors.put(key_files_folderIcon, 0xff999999);
        defaultColors.put(key_files_folderIconBackground, 0xfff0f0f0);
        defaultColors.put(key_files_iconText, 0xffffffff);

        defaultColors.put(key_sessions_devicesImage, 0xff969696);

        defaultColors.put(key_location_markerX, 0xff808080);
        defaultColors.put(key_location_sendLocationBackground, 0xff6da0d4);
        defaultColors.put(key_location_sendLiveLocationBackground, 0xffff6464);
        defaultColors.put(key_location_sendLocationIcon, 0xffffffff);
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
        defaultColors.put(key_sharedMedia_linkPlaceholder, 0xfff0f0f0);
        defaultColors.put(key_sharedMedia_linkPlaceholderText, 0xffffffff);
        defaultColors.put(key_checkbox, 0xff5ec245);
        defaultColors.put(key_checkboxCheck, 0xffffffff);

        defaultColors.put(key_stickers_menu, 0xffb6bdc5);
        defaultColors.put(key_stickers_menuSelector, 0x2f000000);

        defaultColors.put(key_changephoneinfo_image, 0xffa8a8a8);

        defaultColors.put(key_groupcreate_hintText, 0xffa1aab3);
        defaultColors.put(key_groupcreate_cursor, 0xff52a3db);
        defaultColors.put(key_groupcreate_sectionShadow, 0xff000000);
        defaultColors.put(key_groupcreate_sectionText, 0xff7c8288);
        defaultColors.put(key_groupcreate_onlineText, 0xff4092cd);
        defaultColors.put(key_groupcreate_offlineText, 0xff838c96);
        defaultColors.put(key_groupcreate_checkbox, 0xff5ec245);
        defaultColors.put(key_groupcreate_checkboxCheck, 0xffffffff);
        defaultColors.put(key_groupcreate_spanText, 0xff212121);
        defaultColors.put(key_groupcreate_spanBackground, 0xfff2f2f2);

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

        defaultColors.put(key_calls_ratingStar, 0x80000000);
        defaultColors.put(key_calls_ratingStarSelected, 0xFF4a97d6);

        fallbackKeys.put(key_chat_adminText, key_chat_inTimeText);
        fallbackKeys.put(key_chat_adminSelectedText, key_chat_inTimeSelectedText);

        themes = new ArrayList<>();
        otherThemes = new ArrayList<>();
        themesDict = new HashMap<>();
        currentColors = new HashMap<>();

        ThemeInfo themeInfo = new ThemeInfo();
        themeInfo.name = "Default";
        themes.add(currentTheme = defaultTheme = themeInfo);
        themesDict.put("Default", defaultTheme);

        themeInfo = new ThemeInfo();
        themeInfo.name = "Dark";
        themeInfo.assetName = "dark.attheme";
        themes.add(themeInfo);
        themesDict.put("Dark", themeInfo);

        themeInfo = new ThemeInfo();
        themeInfo.name = "Blue";
        themeInfo.assetName = "bluebubbles.attheme";
        themes.add(themeInfo);
        themesDict.put("Blue", themeInfo);

        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE);
        String themesString = preferences.getString("themes2", null);
        if (!TextUtils.isEmpty(themesString)) {
            try {
                JSONArray jsonArray = new JSONArray(themesString);
                for (int a = 0; a < jsonArray.length(); a++) {
                    themeInfo = ThemeInfo.createWithJson(jsonArray.getJSONObject(a));
                    if (themeInfo != null) {
                        otherThemes.add(themeInfo);
                        themes.add(themeInfo);
                        themesDict.put(themeInfo.name, themeInfo);
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
                        themesDict.put(themeInfo.name, themeInfo);
                    }
                }
            }
            saveOtherThemes();
            preferences.edit().remove("themes").commit();
        }

        sortThemes();

        ThemeInfo applyingTheme = null;
        try {
            preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            String theme = preferences.getString("theme", null);
            if (theme != null) {
                applyingTheme = themesDict.get(theme);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (applyingTheme == null) {
            applyingTheme = defaultTheme;
        }
        applyTheme(applyingTheme, false, false);
    }

    private static Method StateListDrawable_getStateDrawableMethod;
    private static Field BitmapDrawable_mColorFilter;

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

    public static Drawable createCircleDrawableWithIcon(int size, int iconRes) {
        return createCircleDrawableWithIcon(size, iconRes, 0);
    }

    public static Drawable createCircleDrawableWithIcon(int size, int iconRes, int stroke) {
        Drawable drawable = ApplicationLoader.applicationContext.getResources().getDrawable(iconRes).mutate();
        return createCircleDrawableWithIcon(size, drawable, stroke);
    }

    public static Drawable createCircleDrawableWithIcon(int size, Drawable drawable, int stroke) {
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
        drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
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

    public static Drawable getRoundRectSelectorDrawable() {
        if (Build.VERSION.SDK_INT >= 21) {
            Drawable maskDrawable = createRoundRectDrawable(AndroidUtilities.dp(3), 0xffffffff);
            ColorStateList colorStateList = new ColorStateList(
                    new int[][]{StateSet.WILD_CARD},
                    new int[]{getColor(key_dialogButtonSelector)}
            );
            return new RippleDrawable(colorStateList, null, maskDrawable);
        } else {
            StateListDrawable stateListDrawable = new StateListDrawable();
            stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, createRoundRectDrawable(AndroidUtilities.dp(3), getColor(key_dialogButtonSelector)));
            stateListDrawable.addState(new int[]{android.R.attr.state_selected}, createRoundRectDrawable(AndroidUtilities.dp(3), getColor(key_dialogButtonSelector)));
            stateListDrawable.addState(StateSet.WILD_CARD, new ColorDrawable(0x00000000));
            return stateListDrawable;
        }
    }

    public static Drawable getSelectorDrawable(boolean whiteBackground) {
        if (whiteBackground) {
            if (Build.VERSION.SDK_INT >= 21) {
                Drawable maskDrawable = new ColorDrawable(0xffffffff);
                ColorStateList colorStateList = new ColorStateList(
                        new int[][]{StateSet.WILD_CARD},
                        new int[]{getColor(key_listSelector)}
                );
                return new RippleDrawable(colorStateList, new ColorDrawable(getColor(key_windowBackgroundWhite)), maskDrawable);
            } else {
                int color = getColor(key_listSelector);
                StateListDrawable stateListDrawable = new StateListDrawable();
                stateListDrawable.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(color));
                stateListDrawable.addState(new int[]{android.R.attr.state_selected}, new ColorDrawable(color));
                stateListDrawable.addState(StateSet.WILD_CARD, new ColorDrawable(getColor(key_windowBackgroundWhite)));
                return stateListDrawable;
            }
        } else {
            return createSelectorDrawable(getColor(key_listSelector), 2);
        }
    }

    public static Drawable createSelectorDrawable(int color) {
        return createSelectorDrawable(color, 1);
    }

    public static Drawable createSelectorDrawable(int color, int maskType) {
        Drawable drawable;
        if (Build.VERSION.SDK_INT >= 21) {
            Drawable maskDrawable = null;
            if (maskType == 1) {
                maskPaint.setColor(0xffffffff);
                maskDrawable = new Drawable() {
                    @Override
                    public void draw(Canvas canvas) {
                        android.graphics.Rect bounds = getBounds();
                        canvas.drawCircle(bounds.centerX(), bounds.centerY(), AndroidUtilities.dp(18), maskPaint);
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
            return new RippleDrawable(colorStateList, null, maskDrawable);
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
        applyTheme(previousTheme, true, false);
        previousTheme = null;
    }

    private static void sortThemes() {
        Collections.sort(themes, new Comparator<ThemeInfo>() {
            @Override
            public int compare(ThemeInfo o1, ThemeInfo o2) {
                if (o1.pathToFile == null && o1.assetName == null) {
                    return -1;
                } else if (o2.pathToFile == null && o2.assetName == null) {
                    return 1;
                }
                return o1.name.compareTo(o2.name);
            }
        });
    }

    public static ThemeInfo applyThemeFile(File file, String themeName, boolean temporary) {
        try {
            if (themeName.equals("Default") || themeName.equals("Dark") || themeName.equals("Blue")) {
                return null;
            }
            File finalFile = new File(ApplicationLoader.getFilesDirFixed(), themeName);
            if (!AndroidUtilities.copyFile(file, finalFile)) {
                return null;
            }

            boolean newTheme = false;
            ThemeInfo themeInfo = themesDict.get(themeName);
            if (themeInfo == null) {
                newTheme = true;
                themeInfo = new ThemeInfo();
                themeInfo.name = themeName;
                themeInfo.pathToFile = finalFile.getAbsolutePath();
            }
            if (!temporary) {
                if (newTheme) {
                    themes.add(themeInfo);
                    themesDict.put(themeInfo.name, themeInfo);
                    otherThemes.add(themeInfo);
                    sortThemes();
                    saveOtherThemes();
                }
            } else {
                previousTheme = currentTheme;
            }

            applyTheme(themeInfo, !temporary, true);
            return themeInfo;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    public static void applyTheme(ThemeInfo themeInfo) {
        applyTheme(themeInfo, true, true);
    }

    public static void applyTheme(ThemeInfo themeInfo, boolean save, boolean removeWallpaperOverride) {
        if (themeInfo == null) {
            return;
        }
        ThemeEditorView editorView = ThemeEditorView.getInstance();
        if (editorView != null) {
            editorView.destroy();
        }
        try {
            if (themeInfo.pathToFile != null || themeInfo.assetName != null) {
                if (save) {
                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putString("theme", themeInfo.name);
                    if (removeWallpaperOverride) {
                        editor.remove("overrideThemeWallpaper");
                    }
                    editor.commit();
                }
                if (themeInfo.assetName != null) {
                    currentColors = getThemeFileValues(null, themeInfo.assetName);
                } else {
                    currentColors = getThemeFileValues(new File(themeInfo.pathToFile), null);
                }
            } else {
                if (save) {
                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.remove("theme");
                    if (removeWallpaperOverride) {
                        editor.remove("overrideThemeWallpaper");
                    }
                    editor.commit();
                }
                currentColors.clear();
                wallpaper = null;
                themedWallpaper = null;
            }
            currentTheme = themeInfo;
            reloadWallpaper();
            applyCommonTheme();
            applyDialogsTheme();
            applyProfileTheme();
            applyChatTheme(false);
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.didSetNewTheme);
                }
            });
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static void saveOtherThemes() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        JSONArray array = new JSONArray();
        for (int a = 0; a < otherThemes.size(); a++) {
            JSONObject jsonObject = otherThemes.get(a).getSaveJson();
            if (jsonObject != null) {
                array.put(jsonObject);
            }
        }
        editor.putString("themes2", array.toString());
        editor.commit();
    }

    public static HashMap<String, Integer> getDefaultColors() {
        return defaultColors;
    }

    public static String getCurrentThemeName() {
        String text = currentTheme.getName();
        if (text.endsWith(".attheme")) {
            text = text.substring(0, text.lastIndexOf('.'));
        }
        return text;
    }

    public static ThemeInfo getCurrentTheme() {
        return currentTheme != null ? currentTheme : defaultTheme;
    }

    public static boolean deleteTheme(ThemeInfo themeInfo) {
        if (themeInfo.pathToFile == null) {
            return false;
        }
        boolean currentThemeDeleted = false;
        if (currentTheme == themeInfo) {
            applyTheme(defaultTheme, true, false);
            currentThemeDeleted = true;
        }

        otherThemes.remove(themeInfo);
        themesDict.remove(themeInfo.name);
        themes.remove(themeInfo);
        File file = new File(themeInfo.pathToFile);
        file.delete();
        saveOtherThemes();
        return currentThemeDeleted;
    }

    public static void saveCurrentTheme(String name, boolean finalSave) {
        StringBuilder result = new StringBuilder();
        for (HashMap.Entry<String, Integer> entry : currentColors.entrySet()) {
            result.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
        }
        File file = new File(ApplicationLoader.getFilesDirFixed(), name);
        FileOutputStream stream = null;
        try {
            stream = new FileOutputStream(file);
            stream.write(result.toString().getBytes());
            if (themedWallpaper instanceof BitmapDrawable) {
                Bitmap bitmap = ((BitmapDrawable) themedWallpaper).getBitmap();
                if (bitmap != null) {
                    stream.write(new byte[]{'W', 'P', 'S', '\n'});
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 87, stream);
                    stream.write(new byte[]{'\n', 'W', 'P', 'E', '\n'});
                }
                if (finalSave) {
                    wallpaper = themedWallpaper;
                    calcBackgroundColor(wallpaper, 2);
                }
            }
            ThemeInfo newTheme;
            if ((newTheme = themesDict.get(name)) == null) {
                newTheme = new ThemeInfo();
                newTheme.pathToFile = file.getAbsolutePath();
                newTheme.name = name;
                themes.add(newTheme);
                themesDict.put(newTheme.name, newTheme);
                otherThemes.add(newTheme);
                saveOtherThemes();
                sortThemes();
            }
            currentTheme = newTheme;
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString("theme", currentTheme.name);
            editor.commit();
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (Exception e) {
                FileLog.e("tmessage", e);
            }
        }
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
            InputStream in = null;
            try {
                in = ApplicationLoader.applicationContext.getAssets().open(assetName);
                AndroidUtilities.copyFile(in, file);
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (Exception ignore) {

                    }
                }
            }
        }
        return file;
    }

    private static HashMap<String, Integer> getThemeFileValues(File file, String assetName) {
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
            themedWallpaperFileOffset = -1;
            while ((read = stream.read(bytes)) != -1) {
                int previousPosition = currentPosition;
                int start = 0;
                for (int a = 0; a < read; a++) {
                    if (bytes[a] == '\n') {
                        int len = a - start + 1;
                        String line = new String(bytes, start, len - 1, "UTF-8");
                        if (line.startsWith("WPS")) {
                            themedWallpaperFileOffset = currentPosition + len;
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

            avatar_broadcastDrawable = resources.getDrawable(R.drawable.broadcast_w);
            avatar_savedDrawable = resources.getDrawable(R.drawable.bookmark_large);
            avatar_photoDrawable = resources.getDrawable(R.drawable.photo_w);

            applyCommonTheme();
        }
    }

    public static void applyCommonTheme() {
        if (dividerPaint == null) {
            return;
        }
        dividerPaint.setColor(getColor(key_divider));
        linkSelectionPaint.setColor(getColor(key_windowBackgroundWhiteLinkSelection));

        setDrawableColorByKey(avatar_broadcastDrawable, key_avatar_text);
        setDrawableColorByKey(avatar_savedDrawable, key_avatar_text);
        setDrawableColorByKey(avatar_photoDrawable, key_avatar_text);
    }

    public static void createDialogsResources(Context context) {
        createCommonResources(context);
        if (dialogs_namePaint == null) {
            Resources resources = context.getResources();

            dialogs_namePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            dialogs_namePaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            dialogs_nameEncryptedPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            dialogs_nameEncryptedPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            dialogs_messagePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            dialogs_messagePrintingPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            dialogs_timePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            dialogs_countTextPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            dialogs_countTextPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            dialogs_onlinePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            dialogs_offlinePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);

            dialogs_tabletSeletedPaint = new Paint();
            dialogs_pinnedPaint = new Paint();
            dialogs_countPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            dialogs_countGrayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            dialogs_errorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            dialogs_lockDrawable = resources.getDrawable(R.drawable.list_secret);
            dialogs_checkDrawable = resources.getDrawable(R.drawable.list_check);
            dialogs_halfCheckDrawable = resources.getDrawable(R.drawable.list_halfcheck);
            dialogs_clockDrawable = resources.getDrawable(R.drawable.msg_clock).mutate();
            dialogs_errorDrawable = resources.getDrawable(R.drawable.list_warning_sign);
            dialogs_groupDrawable = resources.getDrawable(R.drawable.list_group);
            dialogs_broadcastDrawable = resources.getDrawable(R.drawable.list_broadcast);
            dialogs_muteDrawable = resources.getDrawable(R.drawable.list_mute).mutate();
            dialogs_verifiedDrawable = resources.getDrawable(R.drawable.verified_area);
            dialogs_verifiedCheckDrawable = resources.getDrawable(R.drawable.verified_check);
            dialogs_mentionDrawable = resources.getDrawable(R.drawable.mentionchatslist);
            dialogs_botDrawable = resources.getDrawable(R.drawable.list_bot);
            dialogs_pinnedDrawable = resources.getDrawable(R.drawable.list_pin);

            applyDialogsTheme();
        }

        dialogs_namePaint.setTextSize(AndroidUtilities.dp(17));
        dialogs_nameEncryptedPaint.setTextSize(AndroidUtilities.dp(17));
        dialogs_messagePaint.setTextSize(AndroidUtilities.dp(16));
        dialogs_messagePrintingPaint.setTextSize(AndroidUtilities.dp(16));
        dialogs_timePaint.setTextSize(AndroidUtilities.dp(13));
        dialogs_countTextPaint.setTextSize(AndroidUtilities.dp(13));
        dialogs_onlinePaint.setTextSize(AndroidUtilities.dp(16));
        dialogs_offlinePaint.setTextSize(AndroidUtilities.dp(16));
    }

    public static void applyDialogsTheme() {
        if (dialogs_namePaint == null) {
            return;
        }
        dialogs_namePaint.setColor(getColor(key_chats_name));
        dialogs_nameEncryptedPaint.setColor(getColor(key_chats_secretName));
        dialogs_messagePaint.setColor(dialogs_messagePaint.linkColor = getColor(key_chats_message));
        dialogs_tabletSeletedPaint.setColor(getColor(key_chats_tabletSelectedOverlay));
        dialogs_pinnedPaint.setColor(getColor(key_chats_pinnedOverlay));
        dialogs_timePaint.setColor(getColor(key_chats_date));
        dialogs_countTextPaint.setColor(getColor(key_chats_unreadCounterText));
        dialogs_messagePrintingPaint.setColor(getColor(key_chats_actionMessage));
        dialogs_countPaint.setColor(getColor(key_chats_unreadCounter));
        dialogs_countGrayPaint.setColor(getColor(key_chats_unreadCounterMuted));
        dialogs_errorPaint.setColor(getColor(key_chats_sentError));
        dialogs_onlinePaint.setColor(getColor(key_windowBackgroundWhiteBlueText3));
        dialogs_offlinePaint.setColor(getColor(key_windowBackgroundWhiteGrayText3));

        setDrawableColorByKey(dialogs_lockDrawable, key_chats_secretIcon);
        setDrawableColorByKey(dialogs_checkDrawable, key_chats_sentCheck);
        setDrawableColorByKey(dialogs_halfCheckDrawable, key_chats_sentCheck);
        setDrawableColorByKey(dialogs_clockDrawable, key_chats_sentClock);
        setDrawableColorByKey(dialogs_errorDrawable, key_chats_sentErrorIcon);
        setDrawableColorByKey(dialogs_groupDrawable, key_chats_nameIcon);
        setDrawableColorByKey(dialogs_broadcastDrawable, key_chats_nameIcon);
        setDrawableColorByKey(dialogs_botDrawable, key_chats_nameIcon);
        setDrawableColorByKey(dialogs_pinnedDrawable, key_chats_pinnedIcon);
        setDrawableColorByKey(dialogs_muteDrawable, key_chats_muteIcon);
        setDrawableColorByKey(dialogs_verifiedDrawable, key_chats_verifiedBackground);
        setDrawableColorByKey(dialogs_verifiedCheckDrawable, key_chats_verifiedCheck);
    }

    public static void destroyResources() {
        for (int a = 0; a < chat_attachButtonDrawables.length; a++) {
            if (chat_attachButtonDrawables[a] != null) {
                chat_attachButtonDrawables[a].setCallback(null);
            }
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
            chat_replyLinePaint = new Paint();
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

            chat_msgOutDrawable = resources.getDrawable(R.drawable.msg_out).mutate();
            chat_msgOutSelectedDrawable = resources.getDrawable(R.drawable.msg_out).mutate();

            chat_msgInMediaDrawable = resources.getDrawable(R.drawable.msg_photo).mutate();
            chat_msgInMediaSelectedDrawable = resources.getDrawable(R.drawable.msg_photo).mutate();
            chat_msgOutMediaDrawable = resources.getDrawable(R.drawable.msg_photo).mutate();
            chat_msgOutMediaSelectedDrawable = resources.getDrawable(R.drawable.msg_photo).mutate();

            chat_msgOutCheckDrawable = resources.getDrawable(R.drawable.msg_check).mutate();
            chat_msgOutCheckSelectedDrawable = resources.getDrawable(R.drawable.msg_check).mutate();
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
            chat_msgInCallDrawable = resources.getDrawable(R.drawable.ic_call_white_24dp).mutate();
            chat_msgInCallSelectedDrawable = resources.getDrawable(R.drawable.ic_call_white_24dp).mutate();
            chat_msgOutCallDrawable = resources.getDrawable(R.drawable.ic_call_white_24dp).mutate();
            chat_msgOutCallSelectedDrawable = resources.getDrawable(R.drawable.ic_call_white_24dp).mutate();
            chat_msgCallUpRedDrawable = resources.getDrawable(R.drawable.ic_call_made_green_18dp).mutate();
            chat_msgCallUpGreenDrawable = resources.getDrawable(R.drawable.ic_call_made_green_18dp).mutate();
            chat_msgCallDownRedDrawable = resources.getDrawable(R.drawable.ic_call_received_green_18dp).mutate();
            chat_msgCallDownGreenDrawable = resources.getDrawable(R.drawable.ic_call_received_green_18dp).mutate();
            chat_msgAvatarLiveLocationDrawable = resources.getDrawable(R.drawable.livepin).mutate();

            chat_inlineResultFile = resources.getDrawable(R.drawable.bot_file);
            chat_inlineResultAudio = resources.getDrawable(R.drawable.bot_music);
            chat_inlineResultLocation = resources.getDrawable(R.drawable.bot_location);

            chat_msgInShadowDrawable = resources.getDrawable(R.drawable.msg_in_shadow);
            chat_msgOutShadowDrawable = resources.getDrawable(R.drawable.msg_out_shadow);
            chat_msgInMediaShadowDrawable = resources.getDrawable(R.drawable.msg_photo_shadow);
            chat_msgOutMediaShadowDrawable = resources.getDrawable(R.drawable.msg_photo_shadow);

            chat_botLinkDrawalbe = resources.getDrawable(R.drawable.bot_link);
            chat_botInlineDrawable = resources.getDrawable(R.drawable.bot_lines);

            chat_systemDrawable = resources.getDrawable(R.drawable.system);

            chat_contextResult_shadowUnderSwitchDrawable = resources.getDrawable(R.drawable.header_shadow).mutate();

            chat_attachButtonDrawables[0] = resources.getDrawable(R.drawable.attach_camera_states);
            chat_attachButtonDrawables[1] = resources.getDrawable(R.drawable.attach_gallery_states);
            chat_attachButtonDrawables[2] = resources.getDrawable(R.drawable.attach_video_states);
            chat_attachButtonDrawables[3] = resources.getDrawable(R.drawable.attach_audio_states);
            chat_attachButtonDrawables[4] = resources.getDrawable(R.drawable.attach_file_states);
            chat_attachButtonDrawables[5] = resources.getDrawable(R.drawable.attach_contact_states);
            chat_attachButtonDrawables[6] = resources.getDrawable(R.drawable.attach_location_states);
            chat_attachButtonDrawables[7] = resources.getDrawable(R.drawable.attach_hide_states);

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
            chat_goIconDrawable = resources.getDrawable(R.drawable.message_arrow);

            chat_ivStatesDrawable[0][0] = createCircleDrawableWithIcon(AndroidUtilities.dp(40), R.drawable.msg_round_play_m, 1);
            chat_ivStatesDrawable[0][1] = createCircleDrawableWithIcon(AndroidUtilities.dp(40), R.drawable.msg_round_play_m, 1);
            chat_ivStatesDrawable[1][0] = createCircleDrawableWithIcon(AndroidUtilities.dp(40), R.drawable.msg_round_pause_m, 1);
            chat_ivStatesDrawable[1][1] = createCircleDrawableWithIcon(AndroidUtilities.dp(40), R.drawable.msg_round_pause_m, 1);
            chat_ivStatesDrawable[2][0] = createCircleDrawableWithIcon(AndroidUtilities.dp(40), R.drawable.msg_round_load_m, 1);
            chat_ivStatesDrawable[2][1] = createCircleDrawableWithIcon(AndroidUtilities.dp(40), R.drawable.msg_round_load_m, 1);
            chat_ivStatesDrawable[3][0] = createCircleDrawableWithIcon(AndroidUtilities.dp(40), R.drawable.msg_round_cancel_m, 2);
            chat_ivStatesDrawable[3][1] = createCircleDrawableWithIcon(AndroidUtilities.dp(40), R.drawable.msg_round_cancel_m, 2);

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
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setShadowLayer(AndroidUtilities.dp(4), 0, 0, 0x5f000000);
                canvas.drawCircle(bitmapSize / 2, bitmapSize / 2, AndroidUtilities.roundMessageSize / 2 - AndroidUtilities.dp(1), paint);
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
        chat_msgTextPaint.setTextSize(AndroidUtilities.dp(MessagesController.getInstance().fontSize));
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
            chat_actionTextPaint.setTextSize(AndroidUtilities.dp(Math.max(16, MessagesController.getInstance().fontSize) - 2));
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
            chat_statusPaint.setColor(getColor(key_actionBarDefaultSubtitle));
            chat_statusRecordPaint.setColor(getColor(key_actionBarDefaultSubtitle));
            chat_actionTextPaint.setColor(getColor(key_chat_serviceText));
            chat_actionTextPaint.linkColor = getColor(key_chat_serviceLink);
            chat_contextResult_titleTextPaint.setColor(getColor(key_windowBackgroundWhiteBlackText));
            chat_composeBackgroundPaint.setColor(getColor(key_chat_messagePanelBackground));
            chat_timeBackgroundPaint.setColor(getColor(key_chat_mediaTimeBackground));

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
            setDrawableColorByKey(chat_msgOutHalfCheckDrawable, key_chat_outSentCheck);
            setDrawableColorByKey(chat_msgOutHalfCheckSelectedDrawable, key_chat_outSentCheckSelected);
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
            setDrawableColorByKey(chat_msgCallUpRedDrawable, key_calls_callReceivedRedIcon);
            setDrawableColorByKey(chat_msgCallUpGreenDrawable, key_calls_callReceivedGreenIcon);
            setDrawableColorByKey(chat_msgCallDownRedDrawable, key_calls_callReceivedRedIcon);
            setDrawableColorByKey(chat_msgCallDownGreenDrawable, key_calls_callReceivedGreenIcon);

            for (int a = 0; a < 5; a++) {
                setCombinedDrawableColor(chat_fileStatesDrawable[a][0], getColor(key_chat_outLoader), false);
                setCombinedDrawableColor(chat_fileStatesDrawable[a][0], getColor(key_chat_outBubble), true);
                setCombinedDrawableColor(chat_fileStatesDrawable[a][1], getColor(key_chat_outLoaderSelected), false);
                setCombinedDrawableColor(chat_fileStatesDrawable[a][1], getColor(key_chat_outBubbleSelected), true);
                setCombinedDrawableColor(chat_fileStatesDrawable[5 + a][0], getColor(key_chat_inLoader), false);
                setCombinedDrawableColor(chat_fileStatesDrawable[5 + a][0], getColor(key_chat_inBubble), true);
                setCombinedDrawableColor(chat_fileStatesDrawable[5 + a][1], getColor(key_chat_inLoaderSelected), false);
                setCombinedDrawableColor(chat_fileStatesDrawable[5 + a][1], getColor(key_chat_inBubbleSelected), true);
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

            applyChatServiceMessageColor();
        }
    }

    public static void applyChatServiceMessageColor() {
        if (chat_actionBackgroundPaint == null) {
            return;
        }
        Integer serviceColor = currentColors.get(key_chat_serviceBackground);
        Integer servicePressedColor = currentColors.get(key_chat_serviceBackgroundSelected);
        boolean override;
        if (serviceColor == null) {
            serviceColor = serviceMessageColor;
        }
        if (servicePressedColor == null) {
            servicePressedColor = serviceSelectedMessageColor;
        }
        if (currentColor != serviceColor) {
            chat_actionBackgroundPaint.setColor(serviceColor);
            colorFilter = new PorterDuffColorFilter(serviceColor, PorterDuff.Mode.MULTIPLY);
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
        Drawable drawable = context.getResources().getDrawable(resId).mutate();
        drawable.setColorFilter(new PorterDuffColorFilter(getColor(key), PorterDuff.Mode.MULTIPLY));
        return drawable;
    }

    public static int getDefaultColor(String key) {
        Integer value = defaultColors.get(key);
        if (value == null) {
            if (key.equals(key_chats_menuTopShadow)) {
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

    public static int getColor(String key) {
        return getColor(key, null);
    }

    public static int getColor(String key, boolean[] isDefault) {
        Integer color = currentColors.get(key);
        if (color == null) {
            String fallbackKey = fallbackKeys.get(key);
            if (fallbackKey != null) {
                color = currentColors.get(key);
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
        if (key.equals(key_chat_wallpaper)) {
            color = 0xff000000 | color;
        }

        if (useDefault) {
            currentColors.remove(key);
        } else {
            currentColors.put(key, color);
        }

        if (key.equals(key_chat_serviceBackground) || key.equals(key_chat_serviceBackgroundSelected)) {
            applyChatServiceMessageColor();
        } else if (key.equals(key_chat_wallpaper)) {
            reloadWallpaper();
        }
    }

    public static void setThemeWallpaper(String themeName, Bitmap bitmap, File path) {
        currentColors.remove(key_chat_wallpaper);
        ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).edit().remove("overrideThemeWallpaper").commit();
        if (bitmap != null) {
            themedWallpaper = new BitmapDrawable(bitmap);
            saveCurrentTheme(themeName, false);
            calcBackgroundColor(themedWallpaper, 0);
            applyChatServiceMessageColor();
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.didSetNewWallpapper);
        } else {
            themedWallpaper = null;
            wallpaper = null;
            saveCurrentTheme(themeName, false);
            reloadWallpaper();
        }
    }

    public static void setDrawableColor(Drawable drawable, int color) {
        drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
    }

    public static void setDrawableColorByKey(Drawable drawable, String key) {
        drawable.setColorFilter(new PorterDuffColorFilter(getColor(key), PorterDuff.Mode.MULTIPLY));
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

    public static boolean hasWallpaperFromTheme() {
        return currentColors.containsKey(key_chat_wallpaper) || themedWallpaperFileOffset > 0;
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
            int result[] = AndroidUtilities.calcDrawableColor(drawable);
            serviceMessageColor = result[0];
            serviceSelectedMessageColor = result[1];
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
        Utilities.searchQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                synchronized (wallpaperSync) {
                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                    boolean overrideTheme = preferences.getBoolean("overrideThemeWallpaper", false);
                    if (!overrideTheme) {
                        Integer backgroundColor = currentColors.get(key_chat_wallpaper);
                        if (backgroundColor != null) {
                            wallpaper = new ColorDrawable(backgroundColor);
                            isCustomTheme = true;
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
                            preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                            int selectedBackground = preferences.getInt("selectedBackground", 1000001);
                            selectedColor = preferences.getInt("selectedColor", 0);
                            if (selectedColor == 0) {
                                if (selectedBackground == 1000001) {
                                    wallpaper = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.background_hd);
                                    isCustomTheme = false;
                                } else {
                                    File toFile = new File(ApplicationLoader.getFilesDirFixed(), "wallpaper.jpg");
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
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            applyChatServiceMessageColor();
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.didSetNewWallpapper);
                        }
                    });
                }
            }
        });
    }

    public static Drawable getThemedWallpaper(boolean thumb) {
        Integer backgroundColor = currentColors.get(key_chat_wallpaper);
        if (backgroundColor != null) {
            return new ColorDrawable(backgroundColor);
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

    public static Drawable getCachedWallpaper() {
        synchronized (wallpaperSync) {
            if (themedWallpaper != null) {
                return themedWallpaper;
            } else {
                return wallpaper;
            }
        }
    }
}
