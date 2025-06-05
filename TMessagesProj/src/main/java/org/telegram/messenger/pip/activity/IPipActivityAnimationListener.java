package org.telegram.messenger.pip.activity;

public interface IPipActivityAnimationListener {
    default void onEnterAnimationStart(long estimatedDuration) {}

    default void onEnterAnimationEnd(long duration) {}

    default void onLeaveAnimationStart(long estimatedDuration) {}

    default void onLeaveAnimationEnd(long duration) {}

    default void onTransitionAnimationFrame() {}

    default void onTransitionAnimationProgress(float estimatedProgress) {}
}
