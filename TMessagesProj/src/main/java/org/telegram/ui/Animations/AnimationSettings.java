package org.telegram.ui.Animations;

import org.telegram.tgnet.SerializedData;
import org.telegram.ui.Components.AnimationsInterpolator;

public class AnimationSettings {

    private static final int DEFAULT_LEFT_DURATION = 0;
    private static final int DEFAULT_RIGHT_DURATION = 500;
    private static final int DEFAULT_MAX_DURATION = 500;
    private static final float DEFAULT_TOP_PROGRESS = 1.0f;
    private static final float DEFAULT_BOT_PROGRESS = 0.5f;

    public final int id;
    public final String title;
    public int leftDuration;
    public int rightDuration;
    public int maxDuration;
    private float topProgress;
    private float botProgress;

    private final AnimationsInterpolator interpolator;

    public AnimationSettings(int id, String title, int leftDuration, int rightDuration, float topProgress, float botProgress, int maxDuration) {
        this.id = id;
        this.title = title;
        this.leftDuration = leftDuration;
        this.rightDuration = rightDuration;
        this.topProgress = topProgress;
        this.botProgress = botProgress;
        this.maxDuration = maxDuration;
        interpolator = new AnimationsInterpolator();
    }

    public float getLeftProgress() {
        return leftDuration * 1f / maxDuration;
    }

    public float getRightProgress() {
        return rightDuration * 1f / maxDuration;
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

    public float getTopProgress() {
        return topProgress;
    }

    public float getBotProgress() {
        return botProgress;
    }

    public AnimationsInterpolator getInterpolator() {
        return interpolator;
    }

    public SerializedData toSerializedData() {
        SerializedData data = new SerializedData();
        data.writeInt32(leftDuration);
        data.writeInt32(rightDuration);
        data.writeInt32(maxDuration);
        data.writeFloat(topProgress);
        data.writeFloat(botProgress);
        return data;
    }

    public static AnimationSettings fromSerializedData(SerializedData data, int id, String title) {
        int leftDuration = data.readInt32(DEFAULT_LEFT_DURATION);
        int rightDuration = data.readInt32(DEFAULT_RIGHT_DURATION);
        int maxDuration = data.readInt32(DEFAULT_MAX_DURATION);
        float topProgress = data.readFloat(DEFAULT_TOP_PROGRESS);
        float botProgress = data.readFloat(DEFAULT_BOT_PROGRESS);
        return new AnimationSettings(id, title, leftDuration, rightDuration, topProgress, botProgress, maxDuration);
    }

    public static AnimationSettings createDefault(int id, String title) {
        return new AnimationSettings(id, title, DEFAULT_LEFT_DURATION, DEFAULT_RIGHT_DURATION, DEFAULT_TOP_PROGRESS, DEFAULT_BOT_PROGRESS, DEFAULT_MAX_DURATION);
    }
}
