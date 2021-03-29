package org.telegram.ui.Animations;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

import java.util.Arrays;

public class BackgroundAnimationController {

    private static final AnimationSettings[] allSettings = new AnimationSettings[] {
            AnimationSettings.createWithDefaultParams(0, LocaleController.getString("", R.string.AnimationSettingsSendMessage)),
            AnimationSettings.createWithDefaultParams(1, LocaleController.getString("", R.string.AnimationSettingsOpenChat)),
            AnimationSettings.createWithDefaultParams(2, LocaleController.getString("", R.string.AnimationSettingsJumpToMessage))
    };

    public static final int pointsCount = 4;

    public static final int[] defaultColors = new int[] {
            0xFFFFF6BF, 0xFF76A076, 0xFFF6E477, 0xFF316B4D
    };

    public static final float[] pointCoordinates = new float[] {
            0.35f, 0.25f,  0.82f, 0.08f,  0.65f, 0.75f,  0.18f, 0.92f
    };

    private static final int[] currentColors = defaultColors;

    public static boolean isAnimatedBackgroundEnabled() {
        return true;
    }

    public static int[] getColorsCopy() {
        return Arrays.copyOf(currentColors, currentColors.length);
    }

    public static int getCurrentColor(int colorIdx) {
        return currentColors[colorIdx];
    }

    public static void setCurrentColor(int colorIdx, @ColorInt int color) {
        currentColors[colorIdx] = color;
    }

    public static AnimationSettings[] getAllSettings() {
        return allSettings;
    }

    public static void updateSettings(@NonNull AnimationSettings settings) {
        for (int i = 0; i != allSettings.length; ++i) {
            if (settings.id == allSettings[i].id) {
                allSettings[i] = settings;
                break;
            }
        }
    }
}
