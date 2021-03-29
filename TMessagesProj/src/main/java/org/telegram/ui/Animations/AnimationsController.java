package org.telegram.ui.Animations;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

import java.util.Arrays;

public class AnimationsController {

    private static final AnimationSettings[] backgroundAnimationSettings = new AnimationSettings[] {
            AnimationSettings.createWithDefaultParams(0, LocaleController.getString("", R.string.AnimationSettingsSendMessage)),
            AnimationSettings.createWithDefaultParams(1, LocaleController.getString("", R.string.AnimationSettingsOpenChat)),
            AnimationSettings.createWithDefaultParams(2, LocaleController.getString("", R.string.AnimationSettingsJumpToMessage))
    };

    public static final int backgroundPointsCount = 4;

    public static final int[] backgroundDefaultColors = new int[] {
            0xFFFFF6BF, 0xFF76A076, 0xFFF6E477, 0xFF316B4D
    };

    public static final float[] backgroundCoordinates = new float[] {
            0.35f, 0.25f,   0.82f, 0.08f,   0.65f, 0.75f,   0.18f, 0.92f,
            0.27f, 0.58f,   0.59f, 0.16f,   0.76f, 0.42f,   0.41f, 0.84f,
    };

    public static final int backgroundPositionsCount = backgroundCoordinates.length / (2 * backgroundPointsCount);

    private static final int[] backgroundCurrentColors = backgroundDefaultColors;

    public static boolean isAnimatedBackgroundEnabled() {
        return true;
    }

    public static float getBackgroundPointX(int animationPosition, int pointIdx) {
        return backgroundCoordinates[animationPosition * backgroundPointsCount * 2 + pointIdx * 2];
    }

    public static float getBackgroundPointY(int animationPosition, int pointIdx) {
        return backgroundCoordinates[animationPosition * backgroundPointsCount * 2 + pointIdx * 2 + 1];
    }

    public static int[] getBackgroundColorsCopy() {
        return Arrays.copyOf(backgroundCurrentColors, backgroundCurrentColors.length);
    }

    public static int getBackgroundCurrentColor(int colorIdx) {
        return backgroundCurrentColors[colorIdx];
    }

    public static void setBackgroundCurrentColor(int colorIdx, @ColorInt int color) {
        backgroundCurrentColors[colorIdx] = color;
    }

    public static AnimationSettings[] getBackgroundAnimationSettings() {
        return backgroundAnimationSettings;
    }

    public static void updateBackgroundSettings(@NonNull AnimationSettings settings) {
        for (int i = 0; i != backgroundAnimationSettings.length; ++i) {
            if (settings.id == backgroundAnimationSettings[i].id) {
                backgroundAnimationSettings[i] = settings;
                break;
            }
        }
    }




    public static final ViewAnimationType[] animationTypes = new ViewAnimationType[] {
            new ViewAnimationType(0, LocaleController.getString("", R.string.AnimationSettingsShortText)),
            new ViewAnimationType(1, LocaleController.getString("", R.string.AnimationSettingsLongText)),
            new ViewAnimationType(2, LocaleController.getString("", R.string.AnimationSettingsLink)),
            new ViewAnimationType(3, LocaleController.getString("", R.string.AnimationSettingsEmoji)),
            new ViewAnimationType(4, LocaleController.getString("", R.string.AnimationSettingsPhoto)),
            new ViewAnimationType(5, LocaleController.getString("", R.string.AnimationSettingsSticker)),
            new ViewAnimationType(6, LocaleController.getString("", R.string.AnimationSettingsVoice)),
            new ViewAnimationType(7, LocaleController.getString("", R.string.AnimationSettingsVideoMessage)),
            new ViewAnimationType(8, LocaleController.getString("", R.string.AnimationSettingsMultiple)),
    };


    public static class ViewAnimationType {

        public final int id;
        public final String title;

        public ViewAnimationType(int id, String title) {
            this.id = id;
            this.title = title;
        }
    }
}
