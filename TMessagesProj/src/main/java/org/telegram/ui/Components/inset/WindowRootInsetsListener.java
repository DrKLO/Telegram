package org.telegram.ui.Components.inset;


import android.view.View;
import android.view.Window;

import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsAnimationCompat;
import androidx.core.view.WindowInsetsCompat;

import org.jspecify.annotations.NonNull;

import java.util.List;

public class WindowRootInsetsListener extends WindowInsetsAnimationCompat.Callback implements OnApplyWindowInsetsListener {

    public WindowRootInsetsListener() {
        super(WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE);
    }

    public void attach(Window window) {
        final View decorView = window.getDecorView();

        ViewCompat.setOnApplyWindowInsetsListener(decorView, this);
        ViewCompat.setWindowInsetsAnimationCallback(decorView, this);
    }

    private WindowInsetsCompat windowInsetsTarget;
    private WindowInsetsCompat windowInsetsAnimated;

    public Insets getAnimatedInset(WindowInsetsCompat insets, int type) {
        if (insets == null) {
            return Insets.NONE;
        }

        final Insets viewInset = insets.getInsets(type);
        if (windowInsetsTarget == null || windowInsetsAnimated == null) {
            return viewInset;
        }

        final Insets rootInset = windowInsetsTarget.getInsets(type);
        final Insets animInset = windowInsetsAnimated.getInsets(type);
        return Insets.of(
            Math.max(0, animInset.left - rootInset.left + viewInset.left),
            Math.max(0, animInset.top - rootInset.top + viewInset.top),
            Math.max(0, animInset.right - rootInset.right + viewInset.right),
            Math.max(0, animInset.bottom - rootInset.bottom + viewInset.bottom)
        );
    }

    @Override
    public @NonNull WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
        this.windowInsetsTarget = insets;
        return insets;
    }

    @Override
    public @NonNull WindowInsetsCompat onProgress(@NonNull WindowInsetsCompat insets, @NonNull List<WindowInsetsAnimationCompat> runningAnimations) {
        this.windowInsetsAnimated = insets;
        return insets;
    }
}
