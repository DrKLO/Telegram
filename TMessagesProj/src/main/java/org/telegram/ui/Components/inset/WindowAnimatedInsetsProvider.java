package org.telegram.ui.Components.inset;

import android.graphics.PointF;
import android.view.View;
import android.view.ViewGroup;

import org.jspecify.annotations.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsAnimationCompat;
import androidx.core.view.WindowInsetsCompat;

import org.telegram.ui.Components.chat.ViewPositionWatcher;

import java.util.List;

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
        dispatchWindowInsetsAnimationChange(insets);
        return insets;
    }

    private int activeAnimationsCounter;

    public boolean hasActiveAnimations() {
        return activeAnimationsCounter > 0;
    }

    @Override
    public WindowInsetsAnimationCompat.@NonNull BoundsCompat onStart(@NonNull WindowInsetsAnimationCompat animation, WindowInsetsAnimationCompat.@NonNull BoundsCompat bounds) {
        activeAnimationsCounter++;
        return super.onStart(animation, bounds);
    }

    @Override
    public void onEnd(@NonNull WindowInsetsAnimationCompat animation) {
        super.onEnd(animation);
        activeAnimationsCounter--;
    }

    private final ReferenceList<Listener> listeners =  new ReferenceList<>();

    public void subscribeToWindowInsetsAnimation(Listener listener) {
        listeners.add(listener);
    }

    public void unsubscribeFromWindowInsetsAnimation(Listener listener) {
        listeners.remove(listener);
    }

    private final PointF tmpPointF = new PointF();

    private void dispatchWindowInsetsAnimationChange(WindowInsetsCompat insets) {
        for (Listener listener: listeners) {
            final View v = listener.getAnimatedInsetsTargetView();
            if (v == null || !ViewPositionWatcher.computeCoordinatesInParent(v, root, tmpPointF)) {
                continue;
            }

            final int left = (int) tmpPointF.x;
            final int top = (int) tmpPointF.y;
            final int right = root.getWidth() - (left + v.getWidth());
            final int bottom = root.getHeight() - (top + v.getHeight());

            listener.onAnimatedInsetsChanged(v, insets.inset(
                Math.max(0, left),
                Math.max(0, top),
                Math.max(0, right),
                Math.max(0, bottom)
            ));
        }
    }

    public interface Listener {
        View getAnimatedInsetsTargetView();
        void onAnimatedInsetsChanged(View view, WindowInsetsCompat insets);
    }
}
