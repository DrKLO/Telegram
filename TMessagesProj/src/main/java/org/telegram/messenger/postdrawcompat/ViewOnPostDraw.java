package org.telegram.messenger.postdrawcompat;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Trace;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;

import me.vkryl.core.reference.ReferenceList;

class ViewOnPostDraw extends View {

    private final ReferenceList<OnPostDrawListener> callbacks = new ReferenceList<>();
    private final ViewTreeObserver.OnDrawListener onDrawListener = this::forceLayout;
    private ViewTreeObserver observer;

    public ViewOnPostDraw(Context context) {
        super(context);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        observer = getViewTreeObserver();
        observer.addOnDrawListener(onDrawListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (observer != null && observer.isAlive()) {
            observer.removeOnDrawListener(onDrawListener);
        }
        observer = null;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        dispatchOnPostDrawListeners();
    }

    private void dispatchOnPostDrawListeners() {
        Trace.beginSection("dispatchOnPostDrawListeners");
        try {
            for (OnPostDrawListener callback : callbacks) {
                callback.onPostDraw();
            }
        } finally {
            Trace.endSection();
        }
    }

    void addOnPostDrawListener(OnPostDrawListener callback) {
        callbacks.add(callback);
    }

    void removeOnPostDrawListener(OnPostDrawListener callback) {
        callbacks.remove(callback);
    }
}
