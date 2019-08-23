package org.telegram.ui.ActionBar.tinter;

import android.graphics.Color;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.Utilities;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.telegram.ui.ActionBar.Theme.*;

public class ArcticThemeTinter implements ThemeTinter {

    private static final float LUMINANCE_THRESHOLD = 0.66f;

    private static Set<String> keys = new HashSet<>();
    private static HashMap<String, ColorRule> contentColorRules = new HashMap<>();

    static {
        keys.add(key_chat_outBubble);
        keys.add(key_chat_outBubbleSelected);
        keys.add(key_chat_outBubbleShadow);
        keys.add(key_chats_actionBackground);
        keys.add(key_chats_actionPressedBackground);
        keys.add(key_profile_actionBackground);
        keys.add(key_profile_actionPressedBackground);
        keys.add(key_chat_outMediaIcon);
        keys.add(key_chat_outMediaIconSelected);
        keys.add(key_chat_outContactIcon);
        keys.add(key_player_progress);
        keys.add(key_player_progressBackground);
        keys.add(key_player_progressCachedBackground);
        keys.add(key_chat_inAudioProgress);
        keys.add(key_chat_inAudioSelectedProgress);
        keys.add(key_chat_inAudioCacheSeekbar);
        keys.add(key_chat_inAudioSeekbarFill);
        keys.add(key_chat_inAudioSeekbarSelected);
        keys.add(key_chat_inAudioSeekbar);
        keys.add(key_actionBarTabActiveText);
        keys.add(key_actionBarTabLine);
        keys.add(key_avatar_backgroundInProfileBlue);
        keys.add(key_avatar_backgroundSaved);
        keys.add(key_chat_addContact);
        keys.add(key_chat_attachActiveTab);
        keys.add(key_chat_attachCheckBoxBackground);
        keys.add(key_chat_botSwitchToInlineText);
        keys.add(key_chat_emojiPanelBadgeBackground);
        keys.add(key_chat_emojiPanelIconSelected);
        keys.add(key_chat_emojiPanelMasksIconSelected);
        keys.add(key_chat_emojiPanelNewTrending);
        keys.add(key_chat_emojiPanelStickerPackSelector);
        keys.add(key_chat_emojiPanelStickerPackSelectorLine);
        keys.add(key_chat_emojiPanelStickerSetNameHighlight);
        keys.add(key_chat_fieldOverlayText);
        keys.add(key_chat_goDownButtonCounterBackground);
        keys.add(key_chat_inContactBackground);
        keys.add(key_chat_inForwardedNameText);
        keys.add(key_chat_inFileBackground);
        keys.add(key_chat_inFileBackgroundSelected);
        keys.add(key_chat_inFileSelectedIcon);
        keys.add(key_chat_inFileIcon);
        keys.add(key_chat_inInstant);
        keys.add(key_chat_inInstantSelected);
        keys.add(key_chat_inLoader);
        keys.add(key_chat_inLoaderPhoto);
        keys.add(key_chat_inLoaderSelected);
        keys.add(key_chat_inLoaderPhotoSelected);
        keys.add(key_chat_inLocationBackground);
        keys.add(key_chat_inPreviewInstantSelectedText);
        keys.add(key_chat_inPreviewInstantText);
        keys.add(key_chat_inPreviewLine);
        keys.add(key_chat_inReplyLine);
        keys.add(key_chat_inReplyNameText);
        keys.add(key_chat_inSiteNameText);
        keys.add(key_chat_inViaBotNameText);
        keys.add(key_chat_inVoiceSeekbarFill);
        keys.add(key_chat_inlineResultIcon);
        keys.add(key_chat_linkSelectBackground);
        keys.add(key_chat_messageLinkIn);
        keys.add(key_chat_messagePanelSend);
        keys.add(key_chat_messagePanelVoiceBackground);
        keys.add(key_chat_outLocationBackground);
        keys.add(key_chat_outBroadcast);
        keys.add(key_chat_outFileBackground);
        keys.add(key_chat_outFileIcon);
        keys.add(key_chat_outFileProgress);
        keys.add(key_chat_outMediaIcon);
        keys.add(key_chat_recordedVoiceBackground);
        keys.add(key_chat_recordedVoicePlayPausePressed);
        keys.add(key_chat_recordedVoiceProgress);
        keys.add(key_chat_replyPanelIcons);
        keys.add(key_chat_replyPanelName);
        keys.add(key_chat_status);
        keys.add(key_chat_textSelectBackground);
        keys.add(key_chat_topPanelLine);
        keys.add(key_chat_topPanelTitle);
        keys.add(key_chat_unreadMessagesStartArrowIcon);
        keys.add(key_chat_unreadMessagesStartText);
        keys.add(key_chats_actionBackground);
        keys.add(key_chats_actionMessage);
        keys.add(key_chats_actionPressedBackground);
        keys.add(key_chats_actionUnreadBackground);
        keys.add(key_chats_actionUnreadPressedBackground);
        keys.add(key_chats_archiveBackground);
        keys.add(key_chats_attachMessage);
        keys.add(key_chats_menuCloudBackgroundCats);
        keys.add(key_chats_menuItemCheck);
        keys.add(key_chats_nameMessage);
        keys.add(key_chats_sentCheck);
        keys.add(key_chats_sentClock);
        keys.add(key_chats_unreadCounter);
        keys.add(key_chats_verifiedBackground);
        keys.add(key_checkboxSquareBackground);
        keys.add(key_contextProgressInner1);
        keys.add(key_contextProgressInner2);
        keys.add(key_contextProgressOuter1);
        keys.add(key_contextProgressOuter2);
        keys.add(key_contextProgressOuter4);
        keys.add(key_dialogButton);
        keys.add(key_dialogTextBlue);
        keys.add(key_dialogTextBlue2);
        keys.add(key_dialogTextBlue3);
        keys.add(key_dialogTextBlue4);
        keys.add(key_dialogBadgeBackground);
        keys.add(key_dialogCheckboxSquareBackground);
        keys.add(key_dialogFloatingButton);
        keys.add(key_dialogFloatingButtonPressed);
        keys.add(key_dialogInputFieldActivated);
        keys.add(key_dialogLineProgress);
        keys.add(key_dialogLineProgressBackground);
        keys.add(key_dialogLinkSelection);
        keys.add(key_dialogProgressCircle);
        keys.add(key_dialogRadioBackgroundChecked);
        keys.add(key_dialogRoundCheckBox);
        keys.add(key_dialogTextLink);
        keys.add(key_dialogTopBackground);
        keys.add(key_dialog_liveLocationProgress);
        keys.add(key_fastScrollActive);
        keys.add(key_featuredStickers_addButton);
        keys.add(key_featuredStickers_addButtonPressed);
        keys.add(key_featuredStickers_addedIcon);
        keys.add(key_featuredStickers_unread);
        keys.add(key_groupcreate_cursor);
        keys.add(key_inappPlayerPlayPause);
        keys.add(key_location_liveLocationProgress);
        keys.add(key_location_placeLocationBackground);
        keys.add(key_location_sendLocationBackground);
        keys.add(key_login_progressInner);
        keys.add(key_login_progressOuter);
        keys.add(key_musicPicker_checkbox);
        keys.add(key_musicPicker_buttonBackground);
        keys.add(key_passport_authorizeBackground);
        keys.add(key_passport_authorizeBackgroundSelected);
        keys.add(key_picker_badge);
        keys.add(key_picker_enabledButton);
        keys.add(key_player_buttonActive);
        keys.add(key_profile_actionBackground);
        keys.add(key_profile_actionPressedBackground);
        keys.add(key_profile_creatorIcon);
        keys.add(key_profile_status);
        keys.add(key_profile_verifiedBackground);
        keys.add(key_profile_verifiedCheck);
        keys.add(key_progressCircle);
        keys.add(key_radioBackgroundChecked);
        keys.add(key_returnToCallBackground);
        keys.add(key_sharedMedia_photoPlaceholder);
        keys.add(key_sharedMedia_startStopLoadIcon);
        keys.add(key_switch2TrackChecked);
        keys.add(key_switchTrackBlueChecked);
        keys.add(key_switchTrackChecked);
        keys.add(key_undo_cancelColor);
        keys.add(key_windowBackgroundChecked);
        keys.add(key_windowBackgroundWhiteBlueButton);
        keys.add(key_windowBackgroundWhiteBlueHeader);
        keys.add(key_windowBackgroundWhiteBlueIcon);
        keys.add(key_windowBackgroundWhiteBlueText);
        keys.add(key_windowBackgroundWhiteBlueText2);
        keys.add(key_windowBackgroundWhiteBlueText3);
        keys.add(key_windowBackgroundWhiteBlueText4);
        keys.add(key_windowBackgroundWhiteBlueText5);
        keys.add(key_windowBackgroundWhiteBlueText6);
        keys.add(key_windowBackgroundWhiteBlueText7);
        keys.add(key_windowBackgroundWhiteInputFieldActivated);
        keys.add(key_windowBackgroundWhiteLinkSelection);
        keys.add(key_windowBackgroundWhiteLinkText);
        keys.add(key_windowBackgroundWhiteValueText);
        keys.add(key_windowBackgroundWhiteValueText);
        keys.add(key_chat_outFileBackgroundSelected);
        keys.add(key_chat_outFileSelectedIcon);
        keys.add(key_chat_outFileProgressSelected);
        keys.add(key_chat_outMediaIconSelected);
        keys.add(key_chat_adminSelectedText);
        contentColorRules.put(key_chats_verifiedCheck, ColorRule.PRIMARY);
        contentColorRules.put(key_profile_verifiedCheck, ColorRule.PRIMARY);
        contentColorRules.put(key_chat_attachCheckBoxCheck, ColorRule.PRIMARY);
        contentColorRules.put(key_chat_outAudioSeekbar, ColorRule.TERTIARY);
        contentColorRules.put(key_chat_outAudioSeekbarSelected, ColorRule.TERTIARY);
        contentColorRules.put(key_chat_outAudioSeekbarFill, ColorRule.PRIMARY);
        contentColorRules.put(key_chat_outAudioCacheSeekbar, ColorRule.SECONDARY);
        contentColorRules.put(key_chat_outAudioProgress, ColorRule.PRIMARY);
        contentColorRules.put(key_chat_outAudioSelectedProgress, ColorRule.PRIMARY);
        contentColorRules.put(key_chat_outVoiceSeekbar, ColorRule.SECONDARY);
        contentColorRules.put(key_chat_outVoiceSeekbarSelected, ColorRule.SECONDARY);
        contentColorRules.put(key_chat_outVoiceSeekbarFill, ColorRule.PRIMARY);
        contentColorRules.put(key_chats_unreadCounterText, ColorRule.PRIMARY);
        contentColorRules.put(key_passport_authorizeText, ColorRule.PRIMARY);
        contentColorRules.put(key_avatar_savedIcon, ColorRule.PRIMARY);
        contentColorRules.put(key_chats_actionIcon, ColorRule.PRIMARY);
        contentColorRules.put(key_profile_actionIcon, ColorRule.PRIMARY);
        contentColorRules.put(key_chats_archiveIcon, ColorRule.PRIMARY);
        contentColorRules.put(key_chats_archiveText, ColorRule.PRIMARY);
        contentColorRules.put(key_chat_outGreenCall, ColorRule.PRIMARY);
        contentColorRules.put(key_chat_outLoader, ColorRule.PRIMARY);
        contentColorRules.put(key_chat_outLoaderSelected, ColorRule.PRIMARY);
        contentColorRules.put(key_chat_outContactBackground, ColorRule.PRIMARY);
        contentColorRules.put(key_chat_outLocationIcon, ColorRule.PRIMARY);
        contentColorRules.put(key_chat_inLocationIcon, ColorRule.PRIMARY);
        contentColorRules.put(key_chat_outFileNameText, ColorRule.PRIMARY);
        contentColorRules.put(key_chat_outFileInfoText, ColorRule.SECONDARY);
        contentColorRules.put(key_chat_outFileInfoSelectedText, ColorRule.SECONDARY);
        contentColorRules.put(key_chat_outPreviewInstantText, ColorRule.PRIMARY);
        contentColorRules.put(key_chat_outPreviewInstantSelectedText, ColorRule.PRIMARY);
        contentColorRules.put(key_chat_outInstant, ColorRule.PRIMARY);
        contentColorRules.put(key_chat_outInstantSelected, ColorRule.PRIMARY);
        contentColorRules.put(key_chat_outForwardedNameText, ColorRule.PRIMARY);
        contentColorRules.put(key_chat_outViaBotNameText, ColorRule.PRIMARY);
        contentColorRules.put(key_chat_outPreviewLine, ColorRule.PRIMARY);
        contentColorRules.put(key_chat_outSiteNameText, ColorRule.PRIMARY);
        contentColorRules.put(key_chat_outReplyLine, ColorRule.PRIMARY);
        contentColorRules.put(key_chat_outReplyNameText, ColorRule.PRIMARY);
        contentColorRules.put(key_chat_outReplyMessageText, ColorRule.PRIMARY);
        contentColorRules.put(key_chat_outReplyMediaMessageText, ColorRule.SECONDARY);
        contentColorRules.put(key_chat_outReplyMediaMessageSelectedText, ColorRule.SECONDARY);
        contentColorRules.put(key_chat_outContactNameText, ColorRule.PRIMARY);
        contentColorRules.put(key_chat_outContactPhoneText, ColorRule.SECONDARY);
        contentColorRules.put(key_chat_outContactPhoneSelectedText, ColorRule.SECONDARY);
        contentColorRules.put(key_chat_outAudioPerformerText, ColorRule.PRIMARY);
        contentColorRules.put(key_chat_outAudioPerformerSelectedText, ColorRule.PRIMARY);
        contentColorRules.put(key_chat_outAudioTitleText, ColorRule.PRIMARY);
        contentColorRules.put(key_chat_outAudioDurationText, ColorRule.SECONDARY);
        contentColorRules.put(key_chat_outAudioDurationSelectedText, ColorRule.SECONDARY);
        contentColorRules.put(key_chat_outTimeText, ColorRule.SECONDARY);
        contentColorRules.put(key_chat_outTimeSelectedText, ColorRule.SECONDARY);
        contentColorRules.put(key_chat_outVenueInfoText, ColorRule.SECONDARY);
        contentColorRules.put(key_chat_outVenueInfoSelectedText, ColorRule.SECONDARY);
        contentColorRules.put(key_chat_messageTextOut, ColorRule.PRIMARY);
        contentColorRules.put(key_chat_messageLinkOut, ColorRule.PRIMARY);
        contentColorRules.put(key_chat_outSentCheck, ColorRule.SECONDARY);
        contentColorRules.put(key_chat_outSentCheckSelected, ColorRule.PRIMARY);
        contentColorRules.put(key_chat_outSentClock, ColorRule.SECONDARY);
        contentColorRules.put(key_chat_outSentClockSelected, ColorRule.SECONDARY);
        contentColorRules.put(key_chat_outViews, ColorRule.SECONDARY);
        contentColorRules.put(key_chat_outViewsSelected, ColorRule.SECONDARY);
        contentColorRules.put(key_chat_outMenu, new ColorRule(-5, -0.37f, 0.98f, 0.7f, 0.75f));
        contentColorRules.put(key_chat_outMenuSelected, new ColorRule(-5, -0.37f, 0.98f, 0.7f, 0.75f));
    }

    @Override
    public int[] getBaseTintColors() {
        return new int[] {
                Color.parseColor("#007afe"), // blue
                Color.parseColor("#01c1ec"), // lightblue
                Color.parseColor("#28b227"), // green
                Color.parseColor("#ea6ba3"), // pink
                Color.parseColor("#ef8201"), // orange
                Color.parseColor("#9471ee"), // violet
                Color.parseColor("#d23213"), // red
                Color.parseColor("#ecb304"), // yellow
                Color.parseColor("#6d819e"), // gray
                Color.parseColor("#000000"), // black
        };
    }

    @Override
    public void tint(int tintColor,
                     int themeAccentColor,
                     Map<String, Integer>[] inColors,
                     Map<String, Integer>[] outColors) {
        final float luminance = (0.2126f * Color.red(tintColor) / 255f) +
                (0.7151f * Color.green(tintColor) / 255f) +
                (0.0721f * Color.blue(tintColor) / 255f);
        boolean isContentWhite = luminance < LUMINANCE_THRESHOLD;

        final float[] tintColorHsv = new float[3];
        Color.colorToHSV(tintColor, tintColorHsv);

        final float[] themeAccentColorHsv = new float[3];
        Color.colorToHSV(themeAccentColor, themeAccentColorHsv);

        final float[] hsv = new float[3];
        final float[] hsl = new float[3];
        for (int i = 0; i < inColors.length; i++) {
            final Map<String, Integer> in = inColors[i];
            final Map<String, Integer> out = outColors[i];
            for (Map.Entry<String, Integer> entry : in.entrySet()) {
                final String key = entry.getKey();
                if (keys.contains(key)) {
                    final int color = entry.getValue();
                    Color.colorToHSV(entry.getValue(), hsv);
                    hsv[0] += tintColorHsv[0] - themeAccentColorHsv[0];
                    hsv[1] *= tintColorHsv[1] / themeAccentColorHsv[1];
                    hsv[2] *= tintColorHsv[2] / themeAccentColorHsv[2];
                    out.put(key, Color.HSVToColor(Color.alpha(color), hsv));
                } else if (contentColorRules.containsKey(key)) {
                    final ColorRule rule = contentColorRules.get(key);

                    final float originalSaturation = Math.min(1f, Math.max(0f, themeAccentColorHsv[1] * rule.saturationFactor));
                    final float saturation = Math.min(1f, Math.max(0f, tintColorHsv[1] * rule.saturationFactor));

                    hsv[0] = tintColorHsv[0] + rule.dHue;
                    hsv[1] = saturation;
                    hsv[2] = rule.brightness;

                    int color = Color.HSVToColor(hsv);

                    if (!isContentWhite) {
                        ColorUtils.colorToHSL(color, hsl);
                        hsl[2] = 1f - hsl[2];
                        color = ColorUtils.HSLToColor(hsl);
                    }

                    int alpha;
                    if (originalSaturation > 0f) {
                        alpha = (int) (Utilities.lerp(rule.minAlpha, rule.maxAlpha,
                                Math.min(1f, saturation / originalSaturation)) * 255);
                    } else alpha = (int) rule.maxAlpha * 255;

                    out.put(key, ColorUtils.setAlphaComponent(color, alpha));
                } else out.put(key, entry.getValue());
            }
        }
    }

    private static class ColorRule {

        private final static ColorRule PRIMARY = new ColorRule(0, -1f, 1f);
        private final static ColorRule SECONDARY = new ColorRule(-5, 0.4f, 1f, 0.66f, 0.94f);
        private final static ColorRule TERTIARY = new ColorRule(-1, 0.45f, 1f, 0.25f, 0.35f);

        final int dHue;
        final float saturationFactor;
        final float brightness;

        final float minAlpha;
        final float maxAlpha;

        public ColorRule(int dHue, float saturationFactor, float brightness) {
            this(dHue, saturationFactor, brightness, 1f, 1f);
        }

        public ColorRule(int dHue, float saturationFactor, float brightness,
                         float minAlpha, float maxAlpha) {
            this.dHue = dHue;
            this.saturationFactor = saturationFactor;
            this.brightness = brightness;
            this.minAlpha = minAlpha;
            this.maxAlpha = maxAlpha;
        }
    }
}
