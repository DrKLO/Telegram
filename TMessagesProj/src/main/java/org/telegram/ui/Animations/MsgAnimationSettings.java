package org.telegram.ui.Animations;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.SerializedData;

public class MsgAnimationSettings {

    private static final int defaultDuration = 500;

    public static final int msgAnimParamX = 0;
    public static final int msgAnimParamY = 1;
    public static final int msgAnimParamBubbleShape = 2;
    public static final int msgAnimParamColorChange = 3;
    public static final int msgAnimParamTimeAppears = 4;
    public static final int msgAnimParamTextScale = 5;
    public static final int msgAnimParamEmojiScale = 6;
    public static final int msgAnimParamVoiceScale = 7;
    public static final int msgAnimParamVideoScale = 8;

    public final int id;
    public final String title;
    public final AnimationSettings[] settings;

    private int duration;

    public MsgAnimationSettings(int id, String title, int duration, AnimationSettings[] settings) {
        this.id = id;
        this.title = title;
        this.duration = duration;
        this.settings = settings;
    }

    public void setDuration(int duration) {
        this.duration = duration;
        for (AnimationSettings s : settings) {
            s.setMaxDuration(duration);
        }
    }

    public int getDuration() {
        return duration;
    }

    public SerializedData toSerializedData() {
        SerializedData data = new SerializedData();
        data.writeInt32(id);
        data.writeInt32(duration);
        SerializedData[] s = new SerializedData[settings.length];
        for (int i = 0; i < settings.length; ++i) {
            s[i] = settings[i].toSerializedData();
        }
        data.writeSerializedDataArray(s);
        return data;
    }

    public static MsgAnimationSettings fromSerializedData(SerializedData data, int id, String title) {
        int serializedId = data.readInt32(-1);
        int duration = data.readInt32(defaultDuration);
        SerializedData[] serializedDataArray = data.readSerializedDataArray(true);
        AnimationSettings[] settings = new AnimationSettings[serializedDataArray.length];
        for (int i = 0; i < settings.length; ++i) {
            String settingTitle = getTitleForParam(i);
            settings[i] = AnimationSettings.fromSerializedData(serializedDataArray[i], i, settingTitle);
        }
        return new MsgAnimationSettings(id, title, duration, settings);
    }

    public static String getTitleForParam(int paramId) {
        int resId = -1;
        switch (paramId) {
            case msgAnimParamX: resId = R.string.AnimationSettingsXPosition; break;
            case msgAnimParamY: resId = R.string.AnimationSettingsYPosition; break;
            case msgAnimParamBubbleShape: resId = R.string.AnimationSettingsBubbleShape; break;
            case msgAnimParamColorChange: resId = R.string.AnimationSettingsColorChange; break;
            case msgAnimParamTimeAppears: resId = R.string.AnimationSettingsTimeAppears; break;
            case msgAnimParamTextScale: resId = R.string.AnimationSettingsTextScale; break;
            case msgAnimParamEmojiScale: resId = R.string.AnimationSettingsEmojiScale; break;
            case msgAnimParamVoiceScale: resId = R.string.AnimationSettingsVoiceScale; break;
            case msgAnimParamVideoScale: resId = R.string.AnimationSettingsVideoScale; break;
        }
        return resId == -1 ? "" : LocaleController.getString("", resId);
    }
}
