package org.telegram.ui.Components.chat;

import java.util.Arrays;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.ListAnimator;
import me.vkryl.android.animator.ReplaceAnimator;
import me.vkryl.core.BitwiseUtils;

public class ChatActivityBottomViewsVisibilityController implements ReplaceAnimator.Callback {
    private final float[] visibilityValues = new float[32];
    private final Runnable onValuesChanged;

    private final ReplaceAnimator<Integer> replaceAnimator;

    public ChatActivityBottomViewsVisibilityController(Runnable onValuesChanged) {
        this.replaceAnimator = new ReplaceAnimator<>(this, AnimatorUtils.DECELERATE_INTERPOLATOR, 320);
        this.onValuesChanged = onValuesChanged;
    }



    public float getVisibility(int containerId) {
        return visibilityValues[containerId];
    }

    private int visibilityFlags = 1;

    public void setViewVisible(int containerId, boolean isVisible, boolean animated) {
        final int oldPriorityContainerId = getCurrentPriorityContainerId();
        visibilityFlags = BitwiseUtils.setFlag(visibilityFlags, 1 << containerId, isVisible);

        final int newPriorityContainerId = getCurrentPriorityContainerId();
        if (oldPriorityContainerId != newPriorityContainerId) {
            replaceAnimator.replace(newPriorityContainerId, animated);
        }
    }

    public int getCurrentPriorityContainerId() {
        return 31 - Integer.numberOfLeadingZeros(visibilityFlags);
    }


    @Override
    public void onItemChanged(ReplaceAnimator<?> animator) {
        onItemChanged();
    }

    @Override
    public void onForceApplyChanges(ReplaceAnimator<?> animator) {
        onItemChanged();
    }

    private void onItemChanged () {
        Arrays.fill(visibilityValues, 0);
        for (ListAnimator.Entry<Integer> entry: replaceAnimator) {
            final int containerId = entry.item;
            visibilityValues[containerId] = entry.getVisibility();
        }
        onValuesChanged.run();
    }
}
