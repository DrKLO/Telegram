package org.telegram.ui.Animations;

public class MsgAnimationSettings {

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
    }

    public int getDuration() {
        return duration;
    }
}
