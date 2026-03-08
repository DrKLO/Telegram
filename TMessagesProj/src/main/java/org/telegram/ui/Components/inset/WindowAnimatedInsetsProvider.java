package org.telegram.ui.Components.inset;

import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;

import org.jspecify.annotations.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsAnimationCompat;
import androidx.core.view.WindowInsetsCompat;

import org.jspecify.annotations.Nullable;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.chat.ViewPositionWatcher;

import java.util.List;

import me.vkryl.core.BitwiseUtils;
import me.vkryl.core.reference.ReferenceList;

public class WindowAnimatedInsetsProvider extends WindowInsetsAnimationCompat.Callback {
    private final ViewGroup root;

    public WindowAnimatedInsetsProvider(ViewGroup root) {
        super(WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_STOP);
        this.root = root;

        ViewCompat.setWindowInsetsAnimationCallback(root, this);
    }

    @NonNull
    @Override
    public WindowInsetsCompat onProgress(
            @NonNull WindowInsetsCompat insets,
            @NonNull List<WindowInsetsAnimationCompat> runningAnimations) {
        int typeMask = 0;
        for (WindowInsetsAnimationCompat animation : runningAnimations) {
            typeMask |= animation.getTypeMask();
        }

        if (BitwiseUtils.hasFlag(typeMask, WindowInsetsCompat.Type.ime())) {
            dispatchWindowInsetsAnimationChange(insets);
        }
        return insets;
    }

    private int activeAnimationsCounter;

    public boolean hasActiveAnimations() {
        return activeAnimationsCounter > 0;
    }

    @Override
    public WindowInsetsAnimationCompat.@NonNull BoundsCompat onStart(@NonNull WindowInsetsAnimationCompat animation, WindowInsetsAnimationCompat.@NonNull BoundsCompat bounds) {
        if (activeAnimationsCounter == 0) {
            dispatchWindowInsetsAnimationStart();
        }
        activeAnimationsCounter++;
        return super.onStart(animation, bounds);
    }

    @Override
    public void onEnd(@NonNull WindowInsetsAnimationCompat animation) {
        super.onEnd(animation);
        activeAnimationsCounter--;
        if (activeAnimationsCounter == 0) {
            dispatchWindowInsetsAnimationFinish();
        }
    }

    private final ReferenceList<Listener> listeners =  new ReferenceList<>();

    public void subscribeToWindowInsetsAnimation(Listener listener) {
        listeners.add(listener);
    }

    public void unsubscribeFromWindowInsetsAnimation(Listener listener) {
        listeners.remove(listener);
    }

    private static final PointF tmpPointF = new PointF();
    private static final RectF tmpRectF = new RectF();
    private static final Rect tmpRect = new Rect();


    private void dispatchWindowInsetsAnimationStart() {
        for (Listener listener: listeners) {
            listener.onAnimatedInsetsStarted();
        }
    }

    private void dispatchWindowInsetsAnimationFinish() {
        for (Listener listener: listeners) {
            listener.onAnimatedInsetsFinished();
        }
    }

    private void dispatchWindowInsetsAnimationChange(WindowInsetsCompat insets) {
        for (Listener listener: listeners) {
            final View v = listener.getAnimatedInsetsTargetView();
            final WindowInsetsCompat i = calculateWindowInsets(insets, v, root);
            if (i != null) {
                listener.onAnimatedInsetsChanged(v, i);
            }
        }
    }

    @Nullable
    public static WindowInsetsCompat calculateWindowInsets(View view) {
        final WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(view);
        final View rootView = view.getRootView();

        return calculateWindowInsets(rootInsets, view, rootView);
    }

    @Nullable
    public static WindowInsetsCompat calculateWindowInsets(WindowInsetsCompat rootInsets, View view, View rootView) {
        if (view == null || rootView == null || !ViewPositionWatcher.computeRectInParent(view, rootView, tmpRectF)) {
            return null;
        }
        tmpRectF.round(tmpRect);

        final int left = tmpRect.left;
        final int top = tmpRect.top;
        final int right = rootView.getWidth() - tmpRect.right;
        final int bottom = rootView.getHeight() - tmpRect.bottom;

        if (left == 0 && top == 0 && right == 0 && bottom == 0) {
            return rootInsets;
        }

        return rootInsets.inset(
            Math.max(0, left),
            Math.max(0, top),
            Math.max(0, right),
            Math.max(0, bottom)
        );
    }

    public interface Listener {
        View getAnimatedInsetsTargetView();

        default void onAnimatedInsetsStarted() {}

        void onAnimatedInsetsChanged(View view, WindowInsetsCompat insets);

        default void onAnimatedInsetsFinished() {}
    }
}
