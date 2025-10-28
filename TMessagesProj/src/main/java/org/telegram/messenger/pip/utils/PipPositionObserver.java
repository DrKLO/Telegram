package org.telegram.messenger.pip.utils;

import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;

public class PipPositionObserver {
    private final ViewTreeObserver.OnGlobalLayoutListener listener;

    public PipPositionObserver(ViewTreeObserver.OnGlobalLayoutListener listener) {
        this.listener = listener;
    }

    public void start(View v) {
        setViewInternal(v);
    }

    public void stop() {
        setViewInternal(null);
    }



    private View mView;
    private ViewTreeObserver mViewTreeObserver;

    private void setViewInternal(View view) {
        if (mView == view) {
            return;
        }

        setViewTreeObserverInternal(null);
        if (mView != null) {
            mView.removeOnAttachStateChangeListener(onAttachStateChangeListener);
        }

        if (view != null) {
            view.addOnAttachStateChangeListener(onAttachStateChangeListener);
            if (view.isAttachedToWindow()) {
                setViewTreeObserverInternal(view.getViewTreeObserver());
            }
        }

        mView = view;
    }

    private final View.OnAttachStateChangeListener onAttachStateChangeListener = new View.OnAttachStateChangeListener() {
        @Override
        public void onViewAttachedToWindow(@NonNull View v) {
            if (v == mView) {
                setViewTreeObserverInternal(v.getViewTreeObserver());
            }

        }

        @Override
        public void onViewDetachedFromWindow(@NonNull View v) {
            if (v == mView) {
                setViewTreeObserverInternal(null);
            }
        }
    };

    private void setViewTreeObserverInternal(ViewTreeObserver observer) {
        if (mViewTreeObserver == observer) {
            return;
        }

        if (mViewTreeObserver != null && mViewTreeObserver.isAlive()) {
            mViewTreeObserver.removeOnGlobalLayoutListener(listener);
        }

        if (observer != null) {
            observer.addOnGlobalLayoutListener(listener);
        }

        mViewTreeObserver = observer;
    }
}
