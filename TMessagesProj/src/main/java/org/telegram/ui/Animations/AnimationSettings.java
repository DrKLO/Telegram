package org.telegram.ui.Animations;

public class AnimationSettings {

    public final int id;
    public final String title;
    public int leftDuration;
    public int rightDuration;
    public float topProgress;
    public float botProgress;
    public int maxDuration;

    public AnimationSettings(int id, String title, int leftDuration, int rightDuration, float topProgress, float botProgress, int maxDuration) {
        this.id = id;
        this.title = title;
        this.leftDuration = leftDuration;
        this.rightDuration = rightDuration;
        this.topProgress = topProgress;
        this.botProgress = botProgress;
        this.maxDuration = maxDuration;
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

    public static AnimationSettings createWithDefaultParams(int id, String title) {
        return new AnimationSettings(id, title, 0, 500, 1.0f, 0.5f, 500);
    }
}
