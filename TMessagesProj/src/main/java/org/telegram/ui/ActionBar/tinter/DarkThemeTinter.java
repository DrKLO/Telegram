package org.telegram.ui.ActionBar.tinter;

import android.graphics.Color;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.telegram.ui.ActionBar.Theme.*;

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
    public void tint(int tintColor, int themeAccentColor, Map<String, Integer>[] inColors, Map<String, Integer>[] outColors) {
        final float[] tintColorHsv = new float[3];
        Color.colorToHSV(tintColor, tintColorHsv);

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
