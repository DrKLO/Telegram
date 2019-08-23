package org.telegram.ui.ActionBar.tinter;

import android.graphics.Color;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.telegram.ui.ActionBar.Theme.key_avatar_backgroundBlue;
import static org.telegram.ui.ActionBar.Theme.key_avatar_backgroundCyan;
import static org.telegram.ui.ActionBar.Theme.key_avatar_backgroundGreen;
import static org.telegram.ui.ActionBar.Theme.key_avatar_backgroundOrange;
import static org.telegram.ui.ActionBar.Theme.key_avatar_backgroundPink;
import static org.telegram.ui.ActionBar.Theme.key_avatar_backgroundRed;
import static org.telegram.ui.ActionBar.Theme.key_avatar_backgroundViolet;
import static org.telegram.ui.ActionBar.Theme.key_avatar_nameInMessageBlue;
import static org.telegram.ui.ActionBar.Theme.key_avatar_nameInMessageCyan;
import static org.telegram.ui.ActionBar.Theme.key_avatar_nameInMessageGreen;
import static org.telegram.ui.ActionBar.Theme.key_avatar_nameInMessageOrange;
import static org.telegram.ui.ActionBar.Theme.key_avatar_nameInMessagePink;
import static org.telegram.ui.ActionBar.Theme.key_avatar_nameInMessageRed;
import static org.telegram.ui.ActionBar.Theme.key_avatar_nameInMessageViolet;
import static org.telegram.ui.ActionBar.Theme.key_calls_callReceivedGreenIcon;
import static org.telegram.ui.ActionBar.Theme.key_calls_callReceivedRedIcon;
import static org.telegram.ui.ActionBar.Theme.key_chat_attachAudioBackground;
import static org.telegram.ui.ActionBar.Theme.key_chat_attachContactBackground;
import static org.telegram.ui.ActionBar.Theme.key_chat_attachEmptyImage;
import static org.telegram.ui.ActionBar.Theme.key_chat_attachFileBackground;
import static org.telegram.ui.ActionBar.Theme.key_chat_attachGalleryBackground;
import static org.telegram.ui.ActionBar.Theme.key_chat_attachLocationBackground;
import static org.telegram.ui.ActionBar.Theme.key_chat_attachMediaBanBackground;
import static org.telegram.ui.ActionBar.Theme.key_chat_attachPermissionImage;
import static org.telegram.ui.ActionBar.Theme.key_chat_attachPermissionMark;
import static org.telegram.ui.ActionBar.Theme.key_chat_attachPermissionText;
import static org.telegram.ui.ActionBar.Theme.key_chat_attachPhotoBackground;
import static org.telegram.ui.ActionBar.Theme.key_chat_attachPollBackground;
import static org.telegram.ui.ActionBar.Theme.key_chat_outBroadcast;
import static org.telegram.ui.ActionBar.Theme.key_chat_outGreenCall;
import static org.telegram.ui.ActionBar.Theme.key_chat_recordedVoiceDot;
import static org.telegram.ui.ActionBar.Theme.key_chat_reportSpam;
import static org.telegram.ui.ActionBar.Theme.key_chat_sentError;
import static org.telegram.ui.ActionBar.Theme.key_chats_draft;
import static org.telegram.ui.ActionBar.Theme.key_chats_secretIcon;
import static org.telegram.ui.ActionBar.Theme.key_chats_secretName;
import static org.telegram.ui.ActionBar.Theme.key_chats_sentError;
import static org.telegram.ui.ActionBar.Theme.key_checkbox;
import static org.telegram.ui.ActionBar.Theme.key_contacts_inviteBackground;
import static org.telegram.ui.ActionBar.Theme.key_dialogRedIcon;
import static org.telegram.ui.ActionBar.Theme.key_dialogTextRed;
import static org.telegram.ui.ActionBar.Theme.key_dialogTextRed2;
import static org.telegram.ui.ActionBar.Theme.key_featuredStickers_delButton;
import static org.telegram.ui.ActionBar.Theme.key_featuredStickers_delButtonPressed;
import static org.telegram.ui.ActionBar.Theme.key_location_sendLiveLocationBackground;
import static org.telegram.ui.ActionBar.Theme.key_switch2Track;
import static org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteGreenText;
import static org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteGreenText2;
import static org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteRedText;
import static org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteRedText2;
import static org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteRedText3;
import static org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteRedText4;
import static org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteRedText5;
import static org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhiteRedText6;

public class DarkThemeTinter implements ThemeTinter {

    private static Set<String> notTintingKeys = new HashSet<>();

    static {
        notTintingKeys.add(key_avatar_backgroundRed);
        notTintingKeys.add(key_avatar_backgroundOrange);
        notTintingKeys.add(key_avatar_backgroundViolet);
        notTintingKeys.add(key_avatar_backgroundGreen);
        notTintingKeys.add(key_avatar_backgroundCyan);
        notTintingKeys.add(key_avatar_backgroundBlue);
        notTintingKeys.add(key_avatar_backgroundPink);

        notTintingKeys.add(key_avatar_nameInMessageRed);
        notTintingKeys.add(key_avatar_nameInMessageOrange);
        notTintingKeys.add(key_avatar_nameInMessageViolet);
        notTintingKeys.add(key_avatar_nameInMessageGreen);
        notTintingKeys.add(key_avatar_nameInMessageCyan);
        notTintingKeys.add(key_avatar_nameInMessageBlue);
        notTintingKeys.add(key_avatar_nameInMessagePink);

        notTintingKeys.add(key_calls_callReceivedGreenIcon);
        notTintingKeys.add(key_calls_callReceivedRedIcon);

        notTintingKeys.add(key_chat_attachGalleryBackground);
        notTintingKeys.add(key_chat_attachAudioBackground);
        notTintingKeys.add(key_chat_attachFileBackground);
        notTintingKeys.add(key_chat_attachContactBackground);
        notTintingKeys.add(key_chat_attachLocationBackground);
        notTintingKeys.add(key_chat_attachPollBackground);
        notTintingKeys.add(key_chat_attachMediaBanBackground);

        notTintingKeys.add(key_chat_attachEmptyImage);
        notTintingKeys.add(key_chat_attachPermissionMark);
        notTintingKeys.add(key_chat_attachPermissionImage);
        notTintingKeys.add(key_chat_attachPermissionText);
        notTintingKeys.add(key_chat_attachPhotoBackground);

        notTintingKeys.add(key_chat_recordedVoiceDot);
        notTintingKeys.add(key_chat_reportSpam);
        notTintingKeys.add(key_chat_sentError);
        notTintingKeys.add(key_chat_outBroadcast);
        notTintingKeys.add(key_chat_outGreenCall);

        notTintingKeys.add(key_chats_sentError);
        notTintingKeys.add(key_chats_draft);
        notTintingKeys.add(key_chats_secretIcon);
        notTintingKeys.add(key_chats_secretName);

        notTintingKeys.add(key_dialogRedIcon);
        notTintingKeys.add(key_dialogTextRed);
        notTintingKeys.add(key_dialogTextRed2);

        notTintingKeys.add(key_checkbox);
        notTintingKeys.add(key_contacts_inviteBackground);
        notTintingKeys.add(key_location_sendLiveLocationBackground);
        notTintingKeys.add(key_switch2Track);

        notTintingKeys.add(key_featuredStickers_delButton);
        notTintingKeys.add(key_featuredStickers_delButtonPressed);

        notTintingKeys.add(key_windowBackgroundWhiteGreenText);
        notTintingKeys.add(key_windowBackgroundWhiteGreenText2);

        notTintingKeys.add(key_windowBackgroundWhiteRedText);
        notTintingKeys.add(key_windowBackgroundWhiteRedText2);
        notTintingKeys.add(key_windowBackgroundWhiteRedText3);
        notTintingKeys.add(key_windowBackgroundWhiteRedText4);
        notTintingKeys.add(key_windowBackgroundWhiteRedText5);
        notTintingKeys.add(key_windowBackgroundWhiteRedText6);
    }

    @Override
    public int[] getBaseTintColors() {
        return new int[] {
                Color.parseColor("#3A8BE9"), // blue
                Color.parseColor("#01c1ec"), // lightblue
                Color.parseColor("#28b227"), // green
                Color.parseColor("#ea6ba3"), // pink
                Color.parseColor("#ef8201"), // orange
                Color.parseColor("#9471ee"), // violet
                Color.parseColor("#d23213"), // red
                Color.parseColor("#ecb304"), // yellow
                Color.parseColor("#6d819e"), // gray
        };
    }

    @Override
    public void tint(int tintColor, int themeAccentColor, Map<String, Integer>[] inColors, Map<String, Integer>[] outColors) {
        final float[] tintColorHsv = new float[3];
        Color.colorToHSV(tintColor, tintColorHsv);

        tintColorHsv[1] *= 0.731f;
        tintColorHsv[2] *= 0.59f;

        final float[] themeAccentColorHsv = new float[3];
        Color.colorToHSV(themeAccentColor, themeAccentColorHsv);

        for (int i = 0; i < inColors.length; i++) {
            final Map<String, Integer> in = inColors[i];
            final Map<String, Integer> out = outColors[i];

            final float[] hsv = new float[3];
            for (Map.Entry<String, Integer> entry : in.entrySet()) {
                final String key = entry.getKey();
                final int color = entry.getValue();
                if (!notTintingKeys.contains(key)) {
                    Color.colorToHSV(color, hsv);
                    hsv[0] += tintColorHsv[0] - themeAccentColorHsv[0];
                    hsv[1] *= tintColorHsv[1] / themeAccentColorHsv[1];
                    hsv[2] *= tintColorHsv[2] / themeAccentColorHsv[2];
                    out.put(key, Color.HSVToColor(Color.alpha(color), hsv));
                } else out.put(key, color);
            }
        }
    }
}
