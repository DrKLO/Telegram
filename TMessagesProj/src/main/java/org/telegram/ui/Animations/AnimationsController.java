package org.telegram.ui.Animations;

import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import org.telegram.messenger.BaseController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.SerializedData;

import java.util.Arrays;

public class AnimationsController extends BaseController {

    public static final int backgroundAnimationIdSendMessage = 0;
    public static final int backgroundAnimationIdOpenChat = 1;
    public static final int backgroundAnimationIdJump = 2;
    public static final int backgroundAnimationsCount = 3;

    public static final int backgroundPointsCount = 4;
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


    private static final String KEY_BACK_SETTINGS = "back";
    private static final String KEY_BACK_COLOR = "backColor";

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
    private final AnimationSettings[] backgroundAnimationSettings = new AnimationSettings[backgroundAnimationsCount];

    private AnimationsController(int account) {
        super(account);
        SharedPreferences prefs = getPrefs();
        try {
            String data = prefs.getString(KEY_BACK_COLOR, "");
            SerializedData serializedData = SerializedData.fromBase64String(data);
            int[] colors = serializedData.readInt32Array(true);
            System.arraycopy(colors, 0, backgroundCurrentColors, 0, backgroundPointsCount);
        } catch (Exception e) {
            System.arraycopy(backgroundDefaultColors, 0, backgroundCurrentColors, 0, backgroundPointsCount);
        }
        for (int i = 0; i < backgroundAnimationsCount; ++i) {
            backgroundAnimationSettings[i] = getSettingsFromPreferences(prefs, KEY_BACK_SETTINGS + i, i);
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
        SerializedData data = new SerializedData();
        data.writeInt32Array(backgroundCurrentColors);
        getPrefs().edit()
                .putString(KEY_BACK_COLOR, data.toBase64String())
                .apply();
    }

    public AnimationSettings[] getBackgroundAnimationSettings() {
        return backgroundAnimationSettings;
    }

    public void updateBackgroundSettings(@NonNull AnimationSettings settings) {
        for (int i = 0; i != backgroundAnimationSettings.length; ++i) {
            if (settings.id == backgroundAnimationSettings[i].id) {
                backgroundAnimationSettings[i] = settings;
                updateSettingsInPreferences(getPrefs(), KEY_BACK_SETTINGS + i, settings);
                break;
            }
        }
    }

    private SharedPreferences getPrefs() {
        return MessagesController.getAnimationsSettings(currentAccount);
    }

    private static void updateSettingsInPreferences(SharedPreferences prefs, String key, AnimationSettings settings) {
        prefs.edit()
                .putString(key, settings.toSerializedData().toBase64String())
                .apply();
    }

    private static AnimationSettings getSettingsFromPreferences(SharedPreferences prefs, String key, int id) {
        String title = getTitleForBackgroundSettings(id);
        try {
            String data = prefs.getString(key, "");
            if (TextUtils.isEmpty(data)) {
                return AnimationSettings.createDefault(id, title);
            }
            SerializedData serializedData = SerializedData.fromBase64String(data);
            return AnimationSettings.fromSerializedData(serializedData, id, title);
        } catch (Exception e) {
            return AnimationSettings.createDefault(id, title);
        }
    }

    private static String getTitleForBackgroundSettings(int settingsId) {
        int resId = -1;
        switch (settingsId) {
            case backgroundAnimationIdSendMessage:
                resId = R.string.AnimationSettingsSendMessage;
                break;
            case backgroundAnimationIdOpenChat:
                resId = R.string.AnimationSettingsOpenChat;
                break;
            case backgroundAnimationIdJump:
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
