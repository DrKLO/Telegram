package org.telegram.ui.Components.poll;

import android.graphics.Canvas;
import android.view.View;

import androidx.annotation.CallSuper;

import org.telegram.messenger.ImageReceiver;

public abstract class PollAttachedMedia {
    protected final ImageReceiver imageReceiver = new ImageReceiver();

    @CallSuper
    public void attach(View parent) {
        imageReceiver.setParentView(parent);
        imageReceiver.onAttachedToWindow();
    }

    @CallSuper
    public void detach() {
        imageReceiver.onDetachedFromWindow();
    }

    protected void draw(Canvas canvas, int w, int h) {

    }
}
