package org.telegram.ui.Stories;

import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.util.SparseIntArray;

import androidx.core.graphics.ColorUtils;

import org.telegram.ui.ActionBar.Theme;

import java.util.HashSet;
import java.util.Objects;

public class DarkThemeResourceProvider implements Theme.ResourcesProvider {

    HashSet<Integer> debugUnknownKeys = new HashSet<>();
    SparseIntArray sparseIntArray = new SparseIntArray();

    Paint dividerPaint = new Paint();
    Paint actionPaint;
    ColorFilter animatedEmojiColorFilter;

    public DarkThemeResourceProvider() {
        sparseIntArray.put(Theme.key_statisticChartSignature, -1214008894);
        sparseIntArray.put(Theme.key_statisticChartSignatureAlpha, -1946157057);
        sparseIntArray.put(Theme.key_statisticChartHintLine, 452984831);
        sparseIntArray.put(Theme.key_statisticChartActiveLine, -665229191);
        sparseIntArray.put(Theme.key_statisticChartInactivePickerChart, -667862461);
        sparseIntArray.put(Theme.key_statisticChartActivePickerChart, -665229191);
        sparseIntArray.put(Theme.key_player_actionBarTitle, Color.WHITE);
        sparseIntArray.put(Theme.key_dialogIcon, Color.WHITE);
        sparseIntArray.put(Theme.key_text_RedBold, 0xFFDB4646);
        sparseIntArray.put(Theme.key_dialogButton, -10177041);
        sparseIntArray.put(Theme.key_chat_gifSaveHintBackground, ColorUtils.blendARGB(Color.BLACK, Color.WHITE, 0.2f));
        sparseIntArray.put(Theme.key_dialogSearchHint, ColorUtils.blendARGB(Color.BLACK, Color.WHITE, 0.5f));
        sparseIntArray.put(Theme.key_dialogSearchIcon, ColorUtils.blendARGB(Color.BLACK, Color.WHITE, 0.5f));
        sparseIntArray.put(Theme.key_dialogSearchBackground, ColorUtils.setAlphaComponent(Color.WHITE, 17));
        sparseIntArray.put(Theme.key_actionBarDefaultSubmenuItem, Color.WHITE);
        sparseIntArray.put(Theme.key_actionBarDefaultSubmenuItemIcon, Color.WHITE);
        sparseIntArray.put(Theme.key_text_RedRegular, -1152913);
        sparseIntArray.put(Theme.key_listSelector, 234881023);
        sparseIntArray.put(Theme.key_dialogButtonSelector, 436207615);
        sparseIntArray.put(Theme.key_chat_emojiPanelTrendingTitle, Color.WHITE);
        sparseIntArray.put(Theme.key_groupcreate_sectionText, 0x99ffffff);
        sparseIntArray.put(Theme.key_windowBackgroundWhiteHintText, ColorUtils.blendARGB(Color.BLACK, Color.WHITE, 0.5f));
        sparseIntArray.put(Theme.key_dialogTextHint, ColorUtils.blendARGB(Color.BLACK, Color.WHITE, 0.5f));
        sparseIntArray.put(Theme.key_sheet_scrollUp, ColorUtils.blendARGB(Color.BLACK, Color.WHITE, 0.2f));

        sparseIntArray.put(Theme.key_dialogTextBlack, -592138);
        sparseIntArray.put(Theme.key_dialogTextGray3, -8553091);
        sparseIntArray.put(Theme.key_windowBackgroundWhiteBlueIcon, Color.WHITE);
        sparseIntArray.put(Theme.key_chat_emojiPanelStickerSetName, 0x73ffffff);
        sparseIntArray.put(Theme.key_chat_emojiPanelStickerSetNameIcon, 0x73ffffff);
        sparseIntArray.put(Theme.key_chat_TextSelectionCursor, Color.WHITE);
        sparseIntArray.put(Theme.key_featuredStickers_addedIcon, Color.WHITE);
        sparseIntArray.put(Theme.key_actionBarDefault, Color.WHITE);
        sparseIntArray.put(Theme.key_chat_gifSaveHintText, Color.WHITE);
        sparseIntArray.put(Theme.key_chat_messagePanelSend, Color.WHITE);

        sparseIntArray.put(Theme.key_chat_emojiSearchBackground, ColorUtils.setAlphaComponent(Color.WHITE, 30));
        sparseIntArray.put(Theme.key_chat_emojiPanelBackground, 0xc0000000);

        sparseIntArray.put(Theme.key_dialogSearchHint, ColorUtils.blendARGB(Color.BLACK, Color.WHITE, 0.5f));
        sparseIntArray.put(Theme.key_dialogSearchBackground, ColorUtils.setAlphaComponent(Color.WHITE, 17));
        sparseIntArray.put(Theme.key_windowBackgroundWhiteGrayText, ColorUtils.setAlphaComponent(Color.WHITE, 127));
        sparseIntArray.put(Theme.key_chat_messagePanelVoiceLockBackground, -14606046);
        sparseIntArray.put(Theme.key_chat_messagePanelVoiceLock, -1);
        sparseIntArray.put(Theme.key_chat_recordedVoiceDot, -1221292);
        sparseIntArray.put(Theme.key_chat_messagePanelVoiceDelete, -1);
        sparseIntArray.put(Theme.key_chat_recordedVoiceBackground, 0xFF1A9CFF);
        sparseIntArray.put(Theme.key_chat_messagePanelVoiceDuration, -1);
        sparseIntArray.put(Theme.key_chat_recordTime, 2030043135);
        sparseIntArray.put(Theme.key_chat_recordVoiceCancel, -10638868);
        sparseIntArray.put(Theme.key_chat_messagePanelCursor, -1);
        sparseIntArray.put(Theme.key_chat_messagePanelHint, 1694498815);
        sparseIntArray.put(Theme.key_chat_inTextSelectionHighlight, -1515107571);
        sparseIntArray.put(Theme.key_chat_messageLinkOut, -5316609);
        sparseIntArray.put(Theme.key_chat_messagePanelText, -1);
        sparseIntArray.put(Theme.key_chat_messagePanelIcons, Color.WHITE);
        sparseIntArray.put(Theme.key_chat_messagePanelBackground, ColorUtils.setAlphaComponent(Color.BLACK, 122));
        sparseIntArray.put(Theme.key_dialogBackground, 0xFF1F1F1F);
        sparseIntArray.put(Theme.key_dialogBackgroundGray, 0xff000000);
        sparseIntArray.put(Theme.key_dialog_inlineProgressBackground, -15393241);
        sparseIntArray.put(Theme.key_windowBackgroundWhite, -15198183);
        sparseIntArray.put(Theme.key_windowBackgroundWhiteBlackText, Color.WHITE);
        sparseIntArray.put(Theme.key_chat_emojiPanelEmptyText, -8553090);
        sparseIntArray.put(Theme.key_progressCircle, -10177027);
        sparseIntArray.put(Theme.key_chat_emojiPanelStickerPackSelector, 181267199);
        sparseIntArray.put(Theme.key_chat_emojiSearchIcon, ColorUtils.setAlphaComponent(Color.WHITE, 125));
        sparseIntArray.put(Theme.key_chat_emojiPanelIcon, 0x80ffffff);
        sparseIntArray.put(Theme.key_chat_emojiBottomPanelIcon, ColorUtils.setAlphaComponent(Color.WHITE, 125));
        sparseIntArray.put(Theme.key_chat_emojiPanelIconSelected, 0xffffffff);
        sparseIntArray.put(Theme.key_chat_emojiPanelStickerPackSelectorLine, -10177041);
        sparseIntArray.put(Theme.key_chat_emojiPanelShadowLine, ColorUtils.setAlphaComponent(Color.BLACK, 30));
        sparseIntArray.put(Theme.key_chat_emojiPanelBackspace, ColorUtils.setAlphaComponent(Color.WHITE, 125));
        sparseIntArray.put(Theme.key_divider, 0xFF000000);
        sparseIntArray.put(Theme.key_chat_editMediaButton, -15033089);
        sparseIntArray.put(Theme.key_dialogFloatingIcon, 0xffffffff);
        sparseIntArray.put(Theme.key_graySection, 0xFF292929);
        sparseIntArray.put(Theme.key_graySectionText, -8158332);
      //  sparseIntArray.put(Theme.key_windowBackgroundGray, 0xFF1F1F1F);
        sparseIntArray.put(Theme.key_windowBackgroundGray, Color.BLACK);
        sparseIntArray.put(Theme.key_windowBackgroundWhiteBlueHeader, 0xFF1A9CFF);
        sparseIntArray.put(Theme.key_windowBackgroundWhiteInputFieldActivated, -10177041);
        sparseIntArray.put(Theme.key_windowBackgroundWhiteInputField, -10177041);
        sparseIntArray.put(Theme.key_windowBackgroundWhiteGrayText3, ColorUtils.blendARGB(Color.WHITE, Color.BLACK, 0.3f));
        sparseIntArray.put(Theme.key_undo_background, 0xFF212426);
        sparseIntArray.put(Theme.key_undo_cancelColor, 0xFF8BC8F5);
        sparseIntArray.put(Theme.key_undo_infoColor, Color.WHITE);
        sparseIntArray.put(Theme.key_actionBarDefaultSubmenuSeparator, 0xF2151515);
        sparseIntArray.put(Theme.key_chat_emojiPanelStickerSetNameHighlight, Color.WHITE);
        sparseIntArray.put(Theme.key_windowBackgroundWhiteGrayText4, 0xFF808080);
        sparseIntArray.put(Theme.key_voipgroup_nameText, 0xffffffff);
        sparseIntArray.put(Theme.key_voipgroup_inviteMembersBackground, 0xff222A33);
        sparseIntArray.put(Theme.key_chats_secretName, -9316522);
        sparseIntArray.put(Theme.key_chats_name, -1446156);
        sparseIntArray.put(Theme.key_chat_serviceBackground, -2110438831);

        sparseIntArray.put(Theme.key_switchTrack, 0xFF636363);
        sparseIntArray.put(Theme.key_switchTrackChecked, 0xFF1A9CFF);
        sparseIntArray.put(Theme.key_dialogRoundCheckBox, 0xFF1A9CFF);
        sparseIntArray.put(Theme.key_dialogRadioBackgroundChecked, 0xFF1A9CFF);
        sparseIntArray.put(Theme.key_dialogTextBlue2, 0xFF1A9CFF);
        sparseIntArray.put(Theme.key_color_red, -832444);
        sparseIntArray.put(Theme.key_checkbox, -12692893);
        sparseIntArray.put(Theme.key_checkboxDisabled, 0xff626262);
        sparseIntArray.put(Theme.key_dialogRoundCheckBoxCheck, 0xffffffff);
        sparseIntArray.put(Theme.key_dialogButtonSelector, 436207615);
        sparseIntArray.put(Theme.key_groupcreate_spanBackground, -13816531);
        sparseIntArray.put(Theme.key_groupcreate_spanDelete, 0xffffffff);
        sparseIntArray.put(Theme.key_groupcreate_spanText, -657931);
        sparseIntArray.put(Theme.key_avatar_text, 0xffffffff);
        sparseIntArray.put(Theme.key_groupcreate_hintText, -8553091);
        sparseIntArray.put(Theme.key_groupcreate_cursor, -10177041);
        sparseIntArray.put(Theme.key_actionBarDefaultSubmenuBackground, 0xF21F1F1F);
        sparseIntArray.put(Theme.key_actionBarDefaultSelector, 385875967);
        sparseIntArray.put(Theme.key_fastScrollInactive, -12500671);
        sparseIntArray.put(Theme.key_fastScrollActive, -13133079);
        sparseIntArray.put(Theme.key_fastScrollText, 0xffffffff);
        sparseIntArray.put(Theme.key_featuredStickers_addButton, 0xFF1A9CFF);
        sparseIntArray.put(Theme.key_dialogTextLink, -10177041);
        sparseIntArray.put(Theme.key_dialogSearchText, Color.WHITE);
        sparseIntArray.put(Theme.key_chat_messageLinkIn, 0xFF46A3EB);
        sparseIntArray.put(Theme.key_dialogTextGray2, -8553091);

        sparseIntArray.put(Theme.key_location_actionIcon, -592138);
        sparseIntArray.put(Theme.key_location_actionBackground, 0xFF1F1F1F);
        sparseIntArray.put(Theme.key_location_actionPressedBackground, 0xFF3F3F3F);
        sparseIntArray.put(Theme.key_location_actionActiveIcon, -8796932);

        sparseIntArray.put(Theme.key_sheet_other, 1140850687);

        sparseIntArray.put(Theme.key_chat_outBubble, ColorUtils.blendARGB(Color.BLACK, Color.WHITE, 0.4f));
        sparseIntArray.put(Theme.key_chat_outBubbleGradient1, 0);
        sparseIntArray.put(Theme.key_chat_outBubbleGradient2, 0);
        sparseIntArray.put(Theme.key_chat_outBubbleGradient3, 0);
        sparseIntArray.put(Theme.key_chat_textSelectBackground, ColorUtils.setAlphaComponent(Color.WHITE, 75));

        appendColors();
        dividerPaint.setColor(getColor(Theme.key_divider));
    }

    public void appendColors() {

    }

    @Override
    public int getColor(int key) {
        int index = sparseIntArray.indexOfKey(key);
        if (index >= 0) {
            return sparseIntArray.valueAt(index);
        }

        if (!debugUnknownKeys.contains(key)) {
            debugUnknownKeys.add(key);
        }
        return Theme.getColor(key);
    }

    Drawable msgOutMedia;

    @Override
    public Drawable getDrawable(String drawableKey) {
        if (Objects.equals(drawableKey, Theme.key_drawable_msgOutMedia)) {
            if (msgOutMedia == null) {
                msgOutMedia = new Theme.MessageDrawable(Theme.MessageDrawable.TYPE_MEDIA, true, false, this);
            }
            return msgOutMedia;
        }
        return Theme.ResourcesProvider.super.getDrawable(drawableKey);
    }

    @Override
    public Paint getPaint(String paintKey) {
        if (paintKey.equals(Theme.key_paint_divider)) {
            return dividerPaint;
        }
        if (paintKey.equals(Theme.key_paint_chatActionBackground)) {
            if (actionPaint == null) {
                actionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                actionPaint.setColor(ColorUtils.blendARGB(Color.BLACK, Color.WHITE, 0.1f));
            }
            return actionPaint;
        }
        return Theme.getThemePaint(paintKey);
    }

    @Override
    public ColorFilter getAnimatedEmojiColorFilter() {
        if (animatedEmojiColorFilter == null) {
            animatedEmojiColorFilter = new PorterDuffColorFilter(getColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN);
        }
        return animatedEmojiColorFilter;
    }
}
