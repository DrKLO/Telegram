package org.telegram.ui.Stories.recorder;

import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Size;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

@Deprecated(since = "use insets listener !!!")
public class KeyboardNotifier {

    private final View rootView;
    private View realRootView;
    private final Utilities.Callback<Integer> listener;
    public boolean ignoring;
    private boolean awaitingKeyboard;
    private boolean mUseInsets;
    private boolean mMinusNavBar;

    private final Rect rect = new Rect();

    public KeyboardNotifier(@NonNull View rootView, Utilities.Callback<Integer> listener) {
        this(rootView, false, listener);
    }

    public KeyboardNotifier(@NonNull View rootView, boolean getRootView, Utilities.Callback<Integer> listener) {
        this.rootView = rootView;
        this.listener = listener;
        realRootView = rootView;

        if (this.rootView.isAttachedToWindow()) {
            rootView.getViewTreeObserver().addOnGlobalLayoutListener(onGlobalLayoutListener);
            rootView.addOnLayoutChangeListener(onLayoutChangeListener);
        }
        this.rootView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
                if (getRootView) {
                    realRootView = v.getRootView();
                }
                rootView.getViewTreeObserver().addOnGlobalLayoutListener(onGlobalLayoutListener);
                rootView.addOnLayoutChangeListener(onLayoutChangeListener);
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
                rootView.getViewTreeObserver().removeOnGlobalLayoutListener(onGlobalLayoutListener);
                rootView.removeOnLayoutChangeListener(onLayoutChangeListener);
            }
        });
    }

    public KeyboardNotifier useInsets() {
        mUseInsets = true;
        return this;
    }

    public KeyboardNotifier useMinusNavbar() {
        mMinusNavBar = true;
        return this;
    }

    private final View.OnLayoutChangeListener onLayoutChangeListener = (view, l, t, r, b, ol, ot, or, ob) -> update();
    private final ViewTreeObserver.OnGlobalLayoutListener onGlobalLayoutListener = this::update;

    private int lastKeyboardHeight;
    private int keyboardHeight;

    private void update() {
        if (ignoring) {
            return;
        }

        if (mUseInsets) {
            final WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(realRootView == null ? rootView : realRootView);
            keyboardHeight = insets != null ? insets.getInsets(WindowInsetsCompat.Type.ime()).bottom : 0;
        } else {
            rootView.getWindowVisibleDisplayFrame(rect);
            final int screenHeight = (realRootView == null ? rootView : realRootView).getHeight();
            keyboardHeight = screenHeight - rect.bottom;
        }
        if (mMinusNavBar) {
            keyboardHeight = Math.max(0, keyboardHeight - AndroidUtilities.navigationBarHeight);
        }

        final boolean unique = lastKeyboardHeight != keyboardHeight;
        lastKeyboardHeight = keyboardHeight;

        if (unique) {
            fire();
        }
    }

    public int getKeyboardHeight() {
        return keyboardHeight;
    }

    public boolean keyboardVisible() {
        return keyboardHeight > AndroidUtilities.navigationBarHeight + AndroidUtilities.dp(20) || awaitingKeyboard;
    }

    public void ignore(boolean ignore) {
        ignoring = ignore;
        update();
    }

    public void fire() {
        if (awaitingKeyboard) {
            if (keyboardHeight < AndroidUtilities.navigationBarHeight + AndroidUtilities.dp(20)) {
                return;
            } else {
                awaitingKeyboard = false;
            }
        }

        if (listener != null) {
            listener.run(keyboardHeight);
        }
    }

    public void awaitKeyboard() {
        awaitingKeyboard = true;
    }

}
