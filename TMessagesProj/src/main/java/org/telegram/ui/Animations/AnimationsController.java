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

    public static final int backAnimIdSendMessage = 0;
    public static final int backAnimIdOpenChat = 1;
    public static final int backAnimIdJump = 2;
    public static final int backAnimCount = 3;

    public static final int msgAnimIdShortText = 0;
    public static final int msgAnimIdLongText = 1;
    public static final int msgAnimIdLink = 2;
    public static final int msgAnimIdEmoji = 3;
    public static final int msgAnimIdPhoto = 4;
    public static final int msgAnimIdSticker = 5;
    public static final int msgAnimIdVoiceMessage = 6;
    public static final int msgAnimIdVideoMessage = 7;
    public static final int msgAnimIdMultiple = 8;
    public static final int msgAnimCount = 9;

    private static final int msgAnimParamX = 0;
    private static final int msgAnimParamY = 1;
    private static final int msgAnimParamBubbleShape = 2;
    private static final int msgAnimParamColorChange = 3;
    private static final int msgAnimParamTimeAppears = 4;
    private static final int msgAnimParamTextScale = 5;
    private static final int msgAnimParamEmojiScale = 6;

    public static final int backPointsCount = 4;
    public static final int[] backDefaultColors = new int[] {
            0xFFFFF6BF, 0xFF76A076, 0xFFF6E477, 0xFF316B4D
    };

    public static final float[] backCoordinates = new float[] {
            0.35f, 0.25f,   0.82f, 0.08f,   0.65f, 0.75f,   0.18f, 0.92f,
            0.27f, 0.58f,   0.59f, 0.16f,   0.76f, 0.42f,   0.41f, 0.84f,
            0.18f, 0.92f,   0.36f, 0.25f,   0.83f, 0.08f,   0.65f, 0.75f,
            0.41f, 0.83f,   0.27f, 0.58f,   0.59f, 0.17f,   0.74f, 0.42f,
            0.65f, 0.75f,   0.18f, 0.92f,   0.36f, 0.28f,   0.83f, 0.08f,
            0.74f, 0.42f,   0.42f, 0.84f,   0.27f, 0.58f,   0.59f, 0.16f,
            0.83f, 0.08f,   0.65f, 0.75f,   0.19f, 0.92f,   0.36f, 0.25f,
            0.59f, 0.17f,   0.76f, 0.42f,   0.41f, 0.86f,   0.27f, 0.58f
    };

    public static final int backPositionsCount = backCoordinates.length / (2 * backPointsCount);

    public static boolean isAnimatedBackgroundEnabled() {
        return true;
    }

    public static float getBackgroundPointX(int animationPosition, int pointIdx) {
        return backCoordinates[animationPosition * backPointsCount * 2 + pointIdx * 2];
    }

    public static float getBackgroundPointY(int animationPosition, int pointIdx) {
        return backCoordinates[animationPosition * backPointsCount * 2 + pointIdx * 2 + 1];
    }


    private static final String keyBackColor = "backColor";
    private static final String keyBackSettings = "back";
    private static final String keyAnimSettings = "anim";

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

    public static AnimationsController getInstance() {
        return getInstance(UserConfig.selectedAccount);
    }


    private final int[] backCurrentColors = new int[backPointsCount];
    private final AnimationSettings[] backAnimSettings = new AnimationSettings[backAnimCount];
    private final MsgAnimationSettings[] msgAnimSettings = new MsgAnimationSettings[msgAnimCount];

    private AnimationsController(int account) {
        super(account);
        SharedPreferences prefs = getPrefs();

        try {
            String data = prefs.getString(keyBackColor, "");
            SerializedData serializedData = SerializedData.fromBase64String(data);
            int[] colors = serializedData.readInt32Array(true);
            System.arraycopy(colors, 0, backCurrentColors, 0, backPointsCount);
        } catch (Exception e) {
            System.arraycopy(backDefaultColors, 0, backCurrentColors, 0, backPointsCount);
        }
        for (int i = 0; i < backAnimCount; ++i) {
            backAnimSettings[i] = getSettingsFromPreferences(prefs, keyBackSettings + i, i);
        }

        for (int i = 0; i < msgAnimCount; ++i) {
            msgAnimSettings[i] = getMsgSettingsFromPreferences(prefs, keyAnimSettings + i, i);
        }
    }

    public int[] getBackgroundColorsCopy() {
        return Arrays.copyOf(backCurrentColors, backCurrentColors.length);
    }

    public int getBackgroundCurrentColor(int colorIdx) {
        return backCurrentColors[colorIdx];
    }

    public void setBackgroundCurrentColor(int colorIdx, @ColorInt int color) {
        backCurrentColors[colorIdx] = color;
        SerializedData data = new SerializedData();
        data.writeInt32Array(backCurrentColors);
        getPrefs().edit()
                .putString(keyBackColor, data.toBase64String())
                .apply();
    }

    public AnimationSettings[] getBackAnimSettings() {
        return backAnimSettings;
    }

    public AnimationSettings getBackgroundAnimationSettings(int settingsId) {
        return backAnimSettings[settingsId];
    }

    public void updateBackgroundSettings(@NonNull AnimationSettings settings) {
        for (int i = 0; i != backAnimSettings.length; ++i) {
            if (settings.id == backAnimSettings[i].id) {
                backAnimSettings[i] = settings;
                updateSettingsInPreferences(getPrefs(), keyBackSettings + i, settings);
                break;
            }
        }
    }


    public MsgAnimationSettings getMsgAnimSettings(int settingsId) {
        return msgAnimSettings[settingsId];
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

    private static MsgAnimationSettings getMsgSettingsFromPreferences(SharedPreferences prefs, String key, int id) {
        String title = getTitleForMsgAnimationSettings(id);
        return new MsgAnimationSettings(id, title, 500, new AnimationSettings[]{
                AnimationSettings.createDefault(0, getTitleForMsgAnimationParam(0)),
                AnimationSettings.createDefault(0, getTitleForMsgAnimationParam(1)),
        });
    }

    private static String getTitleForBackgroundSettings(int settingsId) {
        int resId = -1;
        switch (settingsId) {
            case backAnimIdSendMessage:
                resId = R.string.AnimationSettingsSendMessage;
                break;
            case backAnimIdOpenChat:
                resId = R.string.AnimationSettingsOpenChat;
                break;
            case backAnimIdJump:
                resId = R.string.AnimationSettingsJumpToMessage;
                break;
        }
        return resId == -1 ? "" : LocaleController.getString("", resId);
    }

    private static String getTitleForMsgAnimationSettings(int settingsId) {
        int resId = -1;
        switch (settingsId) {
            case msgAnimIdShortText:
                resId = R.string.AnimationSettingsShortText;
                break;
            case msgAnimIdLongText:
                resId = R.string.AnimationSettingsLongText;
                break;
            case msgAnimIdLink:
                resId = R.string.AnimationSettingsLink;
                break;
            case msgAnimIdEmoji:
                resId = R.string.AnimationSettingsEmoji;
                break;
            case msgAnimIdPhoto:
                resId = R.string.AnimationSettingsPhoto;
                break;
            case msgAnimIdSticker:
                resId = R.string.AnimationSettingsSticker;
                break;
            case msgAnimIdVoiceMessage:
                resId = R.string.AnimationSettingsVoice;
                break;
            case msgAnimIdVideoMessage:
                resId = R.string.AnimationSettingsVideoMessage;
                break;
            case msgAnimIdMultiple:
                resId = R.string.AnimationSettingsMultiple;
                break;
        }
        return resId == -1 ? "" : LocaleController.getString("", resId);
    }

    private static String getTitleForMsgAnimationParam(int paramId) {
        int resId = -1;
        switch (paramId) {
            case msgAnimParamX:
                resId = R.string.AnimationSettingsXPosition;
                break;
            case msgAnimParamY:
                resId = R.string.AnimationSettingsYPosition;
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
