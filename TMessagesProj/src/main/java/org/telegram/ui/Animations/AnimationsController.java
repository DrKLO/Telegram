package org.telegram.ui.Animations;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

public class AnimationsController {

    public static final AnimationType[] animationTypes = new AnimationType[] {
            new AnimationType(0, LocaleController.getString("", R.string.AnimationSettingsShortText)),
            new AnimationType(1, LocaleController.getString("", R.string.AnimationSettingsLongText)),
            new AnimationType(2, LocaleController.getString("", R.string.AnimationSettingsLink)),
            new AnimationType(3, LocaleController.getString("", R.string.AnimationSettingsEmoji)),
            new AnimationType(4, LocaleController.getString("", R.string.AnimationSettingsPhoto)),
            new AnimationType(5, LocaleController.getString("", R.string.AnimationSettingsSticker)),
            new AnimationType(6, LocaleController.getString("", R.string.AnimationSettingsVoice)),
            new AnimationType(7, LocaleController.getString("", R.string.AnimationSettingsVideoMessage)),
            new AnimationType(8, LocaleController.getString("", R.string.AnimationSettingsMultiple)),
    };

    public static final int pointsCount = 4;

    public static final int[] defaultColors = new int[] {
            0xFFFFF6BF, 0xFF76A076, 0xFFF6E477, 0xFF316B4D
    };

    public static final float[] pointCoords = new float[] {
            0.35f, 0.25f,  0.82f, 0.08f,  0.65f, 0.75f,  0.18f, 0.92f
    };

    public static final int[] currentColors = defaultColors;

    public static boolean isAnimationsEnabled() {
        return true;
    }

    public static class AnimationType {

        public final int id;
        public final String title;

        public AnimationType(int id, String title) {
            this.id = id;
            this.title = title;
        }
    }
}
