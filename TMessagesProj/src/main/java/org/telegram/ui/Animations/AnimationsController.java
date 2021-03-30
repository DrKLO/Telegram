package org.telegram.ui.Animations;

import android.content.SharedPreferences;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import org.telegram.messenger.BaseController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;

import java.util.Arrays;

public class AnimationsController extends BaseController {

    public static final int backgroundPointsCount = 4;
    public static final int backgroundSettingsCount = 3;
    public static final int[] backgroundDefaultColors = new int[] {
            0xFFFFF6BF, 0xFF76A076, 0xFFF6E477, 0xFF316B4D
    };

    public static final float[] backgroundCoordinates = new float[] {
            0.35f, 0.25f,   0.82f, 0.08f,   0.65f, 0.75f,   0.18f, 0.92f,
            0.27f, 0.58f,   0.59f, 0.16f,   0.76f, 0.42f,   0.41f, 0.84f,
            0.18f, 0.92f,   0.36f, 0.25f,   0.83f, 0.08f,   0.65f, 0.75f,
            0.41f, 0.83f,   0.27f, 0.58f,   0.59f, 0.17f,   0.74f, 0.42f,
            0.65f, 0.75f,   0.18f, 0.92f,   0.36f, 0.28f,   0.83f, 0.08f,
            0.74f, 0.42f,   0.42f, 0.84f,   0.27f, 0.58f,   0.59f, 0.16f,
            0.83f, 0.08f,   0.65f, 0.75f,   0.19f, 0.92f,   0.36f, 0.25f,
            0.59f, 0.17f,   0.76f, 0.42f,   0.41f, 0.86f,   0.27f, 0.58f
    };

    public static final int backgroundPositionsCount = backgroundCoordinates.length / (2 * backgroundPointsCount);

    public static boolean isAnimatedBackgroundEnabled() {
        return true;
    }

    public static float getBackgroundPointX(int animationPosition, int pointIdx) {
        return backgroundCoordinates[animationPosition * backgroundPointsCount * 2 + pointIdx * 2];
    }

    public static float getBackgroundPointY(int animationPosition, int pointIdx) {
        return backgroundCoordinates[animationPosition * backgroundPointsCount * 2 + pointIdx * 2 + 1];
    }

    private static final AnimationsController[] instances = new AnimationsController[UserConfig.MAX_ACCOUNT_COUNT];

    private static AnimationsController getInstance(int accountNum) {
        AnimationsController instance = instances[accountNum];
        if (instance == null) {
            synchronized (AnimationsController.class) {
                instance = instances[accountNum];
                if (instance == null) {
                    instances[accountNum] = instance = new AnimationsController(accountNum);
                }
            }
        }
        return instance;
    }

    public static AnimationsController getForCurrentUser() {
        return getInstance(UserConfig.selectedAccount);
    }


    private final int[] backgroundCurrentColors = new int[backgroundPointsCount];

    private final AnimationSettings[] backgroundAnimationSettings = new AnimationSettings[backgroundSettingsCount];

    private AnimationsController(int account) {
        super(account);
        SharedPreferences prefs = MessagesController.getAnimationsSettings(currentAccount);
        for (int i = 0; i < backgroundCurrentColors.length; ++i) {
            backgroundCurrentColors[i] = prefs.getInt("backColor" + i, backgroundDefaultColors[i]);
        }
        for (int i = 0; i < backgroundSettingsCount; ++i) {
            String title = getTitleForBackgroundSettings(i);
            backgroundAnimationSettings[i] = getSettingsFromPreferences(prefs, i, title, "back");
        }
    }

    public int[] getBackgroundColorsCopy() {
        return Arrays.copyOf(backgroundCurrentColors, backgroundCurrentColors.length);
    }

    public int getBackgroundCurrentColor(int colorIdx) {
        return backgroundCurrentColors[colorIdx];
    }

    public void setBackgroundCurrentColor(int colorIdx, @ColorInt int color) {
        backgroundCurrentColors[colorIdx] = color;
        MessagesController.getAnimationsSettings(currentAccount).edit()
                .putInt("backColor" + colorIdx, color)
                .apply();
    }

    public AnimationSettings[] getBackgroundAnimationSettings() {
        return backgroundAnimationSettings;
    }

    public void updateBackgroundSettings(@NonNull AnimationSettings settings) {
        for (int i = 0; i != backgroundAnimationSettings.length; ++i) {
            if (settings.id == backgroundAnimationSettings[i].id) {
                backgroundAnimationSettings[i] = settings;
                updateSettingsInPreferences(MessagesController.getAnimationsSettings(currentAccount), "back", settings);
                break;
            }
        }
    }

    private static void updateSettingsInPreferences(SharedPreferences prefs, String prefix, AnimationSettings settings) {
        prefs.edit()
                .putInt(prefix + "Left" + settings.id, settings.leftDuration)
                .putInt(prefix + "Right" + settings.id, settings.rightDuration)
                .putInt(prefix + "Max" + settings.id, settings.maxDuration)
                .putFloat(prefix + "Top" + settings.id, settings.getTopProgress())
                .putFloat(prefix + "Bot" + settings.id, settings.getBotProgress())
                .apply();
    }

    private static AnimationSettings getSettingsFromPreferences(SharedPreferences prefs, int id, String title, String prefix) {
        int leftDuration = prefs.getInt(prefix + "Left" + id, AnimationSettings.DEFAULT_LEFT_DURATION);
        int rightDuration = prefs.getInt(prefix + "Right" + id, AnimationSettings.DEFAULT_RIGHT_DURATION);
        int maxDuration = prefs.getInt(prefix + "Max" + id, AnimationSettings.DEFAULT_MAX_DURATION);
        float topProgress = prefs.getFloat(prefix + "Top" + id, AnimationSettings.DEFAULT_TOP_PROGRESS);
        float botProgress = prefs.getFloat(prefix + "Bot" + id, AnimationSettings.DEFAULT_BOT_PROGRESS);
        return new AnimationSettings(id, title, leftDuration, rightDuration, topProgress, botProgress, maxDuration);
    }

    private static String getTitleForBackgroundSettings(int settingsId) {
        int resId = -1;
        switch (settingsId) {
            case 0:
                resId = R.string.AnimationSettingsSendMessage;
                break;
            case 1:
                resId = R.string.AnimationSettingsOpenChat;
                break;
            case 2:
                resId = R.string.AnimationSettingsJumpToMessage;
                break;
        }
        return resId == -1 ? "" : LocaleController.getString("", resId);
    }


//    public static final ViewAnimationType[] animationTypes = new ViewAnimationType[] {
//            new ViewAnimationType(0, LocaleController.getString("", R.string.AnimationSettingsShortText)),
//            new ViewAnimationType(1, LocaleController.getString("", R.string.AnimationSettingsLongText)),
//            new ViewAnimationType(2, LocaleController.getString("", R.string.AnimationSettingsLink)),
//            new ViewAnimationType(3, LocaleController.getString("", R.string.AnimationSettingsEmoji)),
//            new ViewAnimationType(4, LocaleController.getString("", R.string.AnimationSettingsPhoto)),
//            new ViewAnimationType(5, LocaleController.getString("", R.string.AnimationSettingsSticker)),
//            new ViewAnimationType(6, LocaleController.getString("", R.string.AnimationSettingsVoice)),
//            new ViewAnimationType(7, LocaleController.getString("", R.string.AnimationSettingsVideoMessage)),
//            new ViewAnimationType(8, LocaleController.getString("", R.string.AnimationSettingsMultiple)),
//    };
//
//
//    public static class ViewAnimationType {
//
//        public final int id;
//        public final String title;
//
//        public ViewAnimationType(int id, String title) {
//            this.id = id;
//            this.title = title;
//        }
//    }
}
