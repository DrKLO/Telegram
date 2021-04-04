package org.telegram.ui.Animations;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.tgnet.SerializedData;
import org.telegram.ui.Components.AnimationsInterpolator;

public class AnimationSettings {

    private static final int defaultLeftDuration = 0;
    private static final int defaultRightDuration = 500;
    private static final int defaultMaxDuration = 500;
    private static final float defaultTopProgress = 1.0f;
    private static final float defaultBotProgress = 0.5f;

    public final int id;
    public String title;
    public int maxDuration;
    private float topProgress;
    private float botProgress;
    private int leftDuration;
    private int rightDuration;

    private final AnimationsInterpolator interpolator;

    public AnimationSettings(int id, String title, int leftDuration, int rightDuration, float topProgress, float botProgress, int maxDuration) {
        this.id = id;
        this.title = title;
        this.leftDuration = leftDuration;
        this.rightDuration = rightDuration;
        this.topProgress = topProgress;
        this.botProgress = botProgress;
        this.maxDuration = maxDuration;
        interpolator = new AnimationsInterpolator(botProgress, 1f - topProgress, getLeftProgress(), getRightProgress());
    }

    public void setMaxDuration(int newMaxDuration) {
        float factor = newMaxDuration * 1f / maxDuration;
        leftDuration = Math.round(leftDuration * factor);
        rightDuration = Math.round(rightDuration * factor);
        maxDuration = newMaxDuration;
    }

    public void setTopProgress(float topProgress) {
        this.topProgress = topProgress;
        interpolator.setEndX(1f - topProgress);
    }

    public void setBotProgress(float botProgress) {
        this.botProgress = botProgress;
        interpolator.setStartX(botProgress);
    }

    public void setLeftDuration(int leftDuration) {
        this.leftDuration = leftDuration;
        interpolator.setLeftBoundProgress(getLeftProgress());
    }

    public void setRightDuration(int rightDuration) {
        this.rightDuration = rightDuration;
        interpolator.setRightBoundProgress(getRightProgress());
    }

    public float getTopProgress() {
        return topProgress;
    }

    public float getBotProgress() {
        return botProgress;
    }

    public float getLeftProgress() {
        return leftDuration * 1f / maxDuration;
    }

    public float getRightProgress() {
        return rightDuration * 1f / maxDuration;
    }

    public AnimationsInterpolator getInterpolator() {
        return interpolator;
    }

    public SerializedData toSerializedData() {
        SerializedData data = new SerializedData();
        data.writeInt32(id);
        data.writeInt32(leftDuration);
        data.writeInt32(rightDuration);
        data.writeInt32(maxDuration);
        data.writeFloat(topProgress);
        data.writeFloat(botProgress);
        return data;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("left", leftDuration);
        json.put("right", rightDuration);
        json.put("max", maxDuration);
        json.put("top", topProgress);
        json.put("bot", botProgress);
        return json;
    }

    public static AnimationSettings fromSerializedData(SerializedData data, int id, String title) {
        int serializedId = data.readInt32(-1);
        int leftDuration = data.readInt32(defaultLeftDuration);
        int rightDuration = data.readInt32(defaultRightDuration);
        int maxDuration = data.readInt32(defaultMaxDuration);
        float topProgress = data.readFloat(defaultTopProgress);
        float botProgress = data.readFloat(defaultBotProgress);
        return new AnimationSettings(id, title, leftDuration, rightDuration, topProgress, botProgress, maxDuration);
    }

    public static AnimationSettings fromJson(JSONObject json, int id, String title) {
        int serializedId = json.optInt("id", -1);
        int leftDuration = json.optInt("left", defaultLeftDuration);
        int rightDuration = json.optInt("right", defaultRightDuration);
        int maxDuration = json.optInt("max", defaultMaxDuration);
        float topProgress = (float) json.optDouble("top", defaultTopProgress);
        float botProgress = (float) json.optDouble("bot", defaultBotProgress);
        return new AnimationSettings(id, title, leftDuration, rightDuration, topProgress, botProgress, maxDuration);
    }

    public static AnimationSettings createDefault(int id, String title) {
        return new AnimationSettings(id, title, defaultLeftDuration, defaultRightDuration, defaultTopProgress, defaultBotProgress, defaultMaxDuration);
    }
}
