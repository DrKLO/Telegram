package org.telegram.ui.Components;

import android.view.View;

public abstract class IntSeekBarAccessibilityDelegate extends SeekBarAccessibilityDelegate {

    @Override
    protected void doScroll(View host, boolean backward) {
        int delta = getDelta();
        if (backward) {
            delta *= -1;
        }
        setProgress(Math.min(getMaxValue(), Math.max(getMinValue(), getProgress() + delta)));
    }

    @Override
    protected boolean canScrollBackward(View host) {
        return getProgress() > getMinValue();
    }

    @Override
    protected boolean canScrollForward(View host) {
        return getProgress() < getMaxValue();
    }

    protected abstract int getProgress();

    protected abstract void setProgress(int progress);

    protected int getMinValue() {
        return 0;
    }

    protected abstract int getMaxValue();

    protected int getDelta() {
        return 1;
    }
}
