package org.telegram.ui.Animations;

import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
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
    private static final String keyMsgSettings = "msg";

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
            SerializedData serializedData = new SerializedData(data);
            int[] colors = serializedData.readInt32Array(true);
            System.arraycopy(colors, 0, backCurrentColors, 0, backPointsCount);
        } catch (Exception e) {
            System.arraycopy(backDefaultColors, 0, backCurrentColors, 0, backPointsCount);
        }
        for (int i = 0; i < backAnimCount; ++i) {
            backAnimSettings[i] = getSettingsFromPreferences(prefs, keyBackSettings + i, i);
        }
        for (int i = 0; i < msgAnimCount; ++i) {
            msgAnimSettings[i] = getMsgSettingsFromPreferences(prefs, keyMsgSettings + i, i);
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
        saveColors();
    }

    public AnimationSettings[] getBackAnimSettings() {
        return backAnimSettings;
    }

    public AnimationSettings getBackgroundAnimationSettings(int settingsId) {
        return backAnimSettings[settingsId];
    }

    public void updateBackgroundSettings(@NonNull AnimationSettings settings) {
        for (int i = 0; i < backAnimSettings.length; ++i) {
            if (settings.id == backAnimSettings[i].id) {
                updateSettingsInPreferences(getPrefs(), keyBackSettings + i, settings);
                break;
            }
        }
    }


    public MsgAnimationSettings getMsgAnimSettings(int settingsId) {
        return msgAnimSettings[settingsId];
    }

    public void updateMsgAnimSettings(MsgAnimationSettings settings) {
        for (int i = 0; i < msgAnimSettings.length; ++i) {
            if (settings.id == msgAnimSettings[i].id) {
                updateMsgSettingsInPreferences(getPrefs(), keyMsgSettings + i, settings);
                break;
            }
        }
    }


    public JSONObject serializeAllSettings() {
        try {
            JSONArray colorsJsonArray = new JSONArray();
            for (int i = 0; i < backCurrentColors.length; ++i) {
                colorsJsonArray.put(backCurrentColors[i]);
            }

            JSONArray backSettingsArray = new JSONArray();
            for (AnimationSettings s : backAnimSettings) {
                backSettingsArray.put(s.toJson());
            }

            JSONObject backJsonObject = new JSONObject();
            backJsonObject.put("colors", colorsJsonArray);
            backJsonObject.put("settings", backSettingsArray);

            JSONArray msgJsonArray = new JSONArray();
            for (int i = 0; i < msgAnimCount; ++i) {
                MsgAnimationSettings msgSettings = msgAnimSettings[i];
                msgJsonArray.put(msgSettings.toJson());
            }
            JSONObject msgJsonObject = new JSONObject();
            msgJsonObject.put("items", msgJsonArray);

            JSONObject rootJsonObject = new JSONObject();
            rootJsonObject.put("back", backJsonObject);
            rootJsonObject.put("msg", msgJsonObject);
            return rootJsonObject;
        } catch (Exception e) {
            return null;
        }
    }

    public void loadAllSettings(JSONObject rootJsonObject) throws JSONException {
        JSONObject backJsonObject = rootJsonObject.getJSONObject("back");

        JSONArray colorsJsonArray = backJsonObject.getJSONArray("colors");
        int[] colors = new int[colorsJsonArray.length()];
        for (int i = 0; i < colorsJsonArray.length(); ++i) {
            colors[i] = colorsJsonArray.getInt(i);
        }
        System.arraycopy(colors, 0, backCurrentColors, 0, Math.min(colors.length, backPointsCount));
        saveColors();

        JSONArray settingsJsonArray = backJsonObject.getJSONArray("settings");
        AnimationSettings[] backSettings = new AnimationSettings[settingsJsonArray.length()];
        for (int i = 0; i < settingsJsonArray.length(); ++i) {
            String title = getTitleForBackgroundSettings(i);
            backSettings[i] = AnimationSettings.fromJson(settingsJsonArray.getJSONObject(i), i, title);
            updateBackgroundSettings(backSettings[i]);
        }
        System.arraycopy(backSettings, 0, backAnimSettings, 0, Math.min(backSettings.length, backAnimCount));

        JSONObject msgJsonObject = rootJsonObject.getJSONObject("msg");
        JSONArray itemsJsonArray = msgJsonObject.getJSONArray("items");
        int itemsCount = itemsJsonArray.length();
        MsgAnimationSettings[] msgSettings = new MsgAnimationSettings[itemsCount];
        for (int i = 0; i < itemsJsonArray.length(); ++i) {
            String title = getTitleForMsgAnimationSettings(i);
            JSONObject jsonObject = itemsJsonArray.getJSONObject(i);
            msgSettings[i] = MsgAnimationSettings.fromJson(jsonObject, i, title);
            updateMsgAnimSettings(msgSettings[i]);
        }
        System.arraycopy(msgSettings, 0, msgAnimSettings, 0, Math.min(msgSettings.length, msgAnimCount));
    }

    public void resetSettings() {
        System.arraycopy(backDefaultColors, 0, backCurrentColors, 0, backPointsCount);
        saveColors();
        for (int i = 0; i < backAnimCount; ++i) {
            String title = getTitleForBackgroundSettings(i);
            backAnimSettings[i] = AnimationSettings.createDefault(i, title);
            updateBackgroundSettings(backAnimSettings[i]);
        }
        for (int i = 0; i < msgAnimCount; ++i) {
            String title = getTitleForMsgAnimationSettings(i);
            msgAnimSettings[i] = new MsgAnimationSettings(i, title, 500, getAnimationSettingsForMsg(i));
            updateMsgAnimSettings(msgAnimSettings[i]);
        }
    }


    private SharedPreferences getPrefs() {
        return MessagesController.getAnimationsSettings(currentAccount);
    }

    private void saveColors() {
        SerializedData data = new SerializedData();
        data.writeInt32Array(backCurrentColors);
        getPrefs().edit()
                .putString(keyBackColor, data.toBase64String())
                .apply();
    }

    private static void updateSettingsInPreferences(SharedPreferences prefs, String key, AnimationSettings settings) {
        prefs.edit()
                .putString(key, settings.toSerializedData().toBase64String())
                .apply();
    }

    private static void updateMsgSettingsInPreferences(SharedPreferences prefs, String key, MsgAnimationSettings settings) {
        prefs.edit()
                .putString(key, settings.toSerializedData().toBase64String())
                .apply();
    }

    private static AnimationSettings getSettingsFromPreferences(SharedPreferences prefs, String key, int id) {
        String title = getTitleForBackgroundSettings(id);
        try {
            String data = prefs.getString(key, "");
            if (TextUtils.isEmpty(data)) {
                throw new IllegalArgumentException();
            }
            SerializedData serializedData = new SerializedData(data);
            return AnimationSettings.fromSerializedData(serializedData, id, title);
        } catch (Exception e) {
            return AnimationSettings.createDefault(id, title);
        }
    }

    private static MsgAnimationSettings getMsgSettingsFromPreferences(SharedPreferences prefs, String key, int id) {
        String title = getTitleForMsgAnimationSettings(id);
        try {
            String data = prefs.getString(key, "");
            if (TextUtils.isEmpty(data)) {
                throw new IllegalArgumentException();
            }
            SerializedData serializedData = new SerializedData(data);
            return MsgAnimationSettings.fromSerializedData(serializedData, id, title);
        } catch (Exception e) {
            return new MsgAnimationSettings(id, title, 500, getAnimationSettingsForMsg(id));
        }
    }

    private static AnimationSettings[] getAnimationSettingsForMsg(int id) {
        if (id == msgAnimIdShortText || id == msgAnimIdLongText || id == msgAnimIdLink) {
            return new AnimationSettings[]{
                    AnimationSettings.createDefault(id, MsgAnimationSettings.getTitleForParam(MsgAnimationSettings.msgAnimParamX)),
                    AnimationSettings.createDefault(id, MsgAnimationSettings.getTitleForParam(MsgAnimationSettings.msgAnimParamY)),
                    AnimationSettings.createDefault(id, MsgAnimationSettings.getTitleForParam(MsgAnimationSettings.msgAnimParamBubbleShape)),
                    AnimationSettings.createDefault(id, MsgAnimationSettings.getTitleForParam(MsgAnimationSettings.msgAnimParamTextScale)),
                    AnimationSettings.createDefault(id, MsgAnimationSettings.getTitleForParam(MsgAnimationSettings.msgAnimParamColorChange)),
                    AnimationSettings.createDefault(id, MsgAnimationSettings.getTitleForParam(MsgAnimationSettings.msgAnimParamTimeAppears))
            };
        } else if (id == msgAnimIdEmoji) {
            return new AnimationSettings[] {
                    AnimationSettings.createDefault(id, MsgAnimationSettings.getTitleForParam(MsgAnimationSettings.msgAnimParamX)),
                    AnimationSettings.createDefault(id, MsgAnimationSettings.getTitleForParam(MsgAnimationSettings.msgAnimParamY)),
                    AnimationSettings.createDefault(id, MsgAnimationSettings.getTitleForParam(MsgAnimationSettings.msgAnimParamEmojiScale)),
                    AnimationSettings.createDefault(id, MsgAnimationSettings.getTitleForParam(MsgAnimationSettings.msgAnimParamTimeAppears))
            };
        } else {
            return new AnimationSettings[] {
                    AnimationSettings.createDefault(id, MsgAnimationSettings.getTitleForParam(MsgAnimationSettings.msgAnimParamX)),
                    AnimationSettings.createDefault(id, MsgAnimationSettings.getTitleForParam(MsgAnimationSettings.msgAnimParamY)),
                    AnimationSettings.createDefault(id, MsgAnimationSettings.getTitleForParam(MsgAnimationSettings.msgAnimParamTimeAppears))
            };
        }
    }

    private static String getTitleForBackgroundSettings(int settingsId) {
        int resId = -1;
        switch (settingsId) {
            case backAnimIdSendMessage: resId = R.string.AnimationSettingsSendMessage;  break;
            case backAnimIdOpenChat:    resId = R.string.AnimationSettingsOpenChat; break;
            case backAnimIdJump:        resId = R.string.AnimationSettingsJumpToMessage; break;
        }
        return resId == -1 ? "" : LocaleController.getString("", resId);
    }

    private static String getTitleForMsgAnimationSettings(int settingsId) {
        int resId = -1;
        switch (settingsId) {
            case msgAnimIdShortText:    resId = R.string.AnimationSettingsShortText; break;
            case msgAnimIdLongText:     resId = R.string.AnimationSettingsLongText; break;
            case msgAnimIdLink:         resId = R.string.AnimationSettingsLink; break;
            case msgAnimIdEmoji:        resId = R.string.AnimationSettingsEmoji; break;
            case msgAnimIdPhoto:        resId = R.string.AnimationSettingsPhoto; break;
            case msgAnimIdSticker:      resId = R.string.AnimationSettingsSticker; break;
            case msgAnimIdVoiceMessage: resId = R.string.AnimationSettingsVoice; break;
            case msgAnimIdVideoMessage: resId = R.string.AnimationSettingsVideoMessage; break;
            case msgAnimIdMultiple:     resId = R.string.AnimationSettingsMultiple;break;
        }
        return resId == -1 ? "" : LocaleController.getString("", resId);
    }
}
