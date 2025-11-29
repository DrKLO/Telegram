package org.telegram.ui.Stories;

import static org.telegram.messenger.SharedConfig.PERFORMANCE_CLASS_HIGH;
import static org.telegram.messenger.SharedConfig.getDevicePerformanceClass;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.ImageReceiver;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarsImageView;

import java.util.HashSet;
import java.util.Set;

class HwFrameLayout extends FrameLayout {
    static final Set<View> hwViews = new HashSet<>();
    static boolean hwEnabled = false;

    private final boolean isFastDevice;

    public HwFrameLayout(@NonNull Context context) {
        super(context);
        isFastDevice = getDevicePerformanceClass() == PERFORMANCE_CLASS_HIGH;
    }

    private void disableHwAcceleration(boolean withLayer) {
        hwEnabled = false;
        if (withLayer) {
            setLayerType(View.LAYER_TYPE_NONE, null);
        }
        for (View view : hwViews) {
            view.invalidate();
        }
        hwViews.clear();
    }

    @UiThread
    public void enableHwAcceleration() {
        hwEnabled = true;
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    @UiThread
    public void disableHwAcceleration() {
        disableHwAcceleration(true);
    }

    /**
     * Used for early transition {@link ImageReceiver#checkAlphaAnimation} from blurred preview to normal for high-end devices.
     * The flag {@link View#LAYER_TYPE_HARDWARE} still keeping. With the flag it will be faster with redraws than without.
     */
    @UiThread
    public void checkHwAcceleration(float progress) {
        if (progress > 0.6f && hwEnabled && isFastDevice) {
            disableHwAcceleration(false);
        }
    }

    @Override
    public void invalidate() {
        if (hwEnabled) {
            hwViews.add(this);
            return;
        }
        super.invalidate();
    }

    @Override
    public void invalidate(int l, int t, int r, int b) {
        if (hwEnabled) {
            hwViews.add(this);
            return;
        }
        super.invalidate(l, t, r, b);
    }
}

class HwTextureView extends TextureView {

    public HwTextureView(@NonNull Context context) {
        super(context);
    }

    @Override
    public void invalidate() {
        if (HwFrameLayout.hwEnabled) {
            HwFrameLayout.hwViews.add(this);
            return;
        }
        super.invalidate();
    }

    @Override
    public void invalidate(int l, int t, int r, int b) {
        if (HwFrameLayout.hwEnabled) {
            HwFrameLayout.hwViews.add(this);
            return;
        }
        super.invalidate(l, t, r, b);
    }
}

@SuppressLint("ViewConstructor")
class HwStoriesViewPager extends StoriesViewPager {

    public HwStoriesViewPager(int account, @NonNull Context context, StoryViewer storyViewer, Theme.ResourcesProvider resourcesProvider) {
        super(account, context, storyViewer, resourcesProvider);
    }

    @Override
    public void invalidate() {
        if (HwFrameLayout.hwEnabled) {
            HwFrameLayout.hwViews.add(this);
            return;
        }
        super.invalidate();
    }

    @Override
    public void invalidate(int l, int t, int r, int b) {
        if (HwFrameLayout.hwEnabled) {
            HwFrameLayout.hwViews.add(this);
            return;
        }
        super.invalidate(l, t, r, b);
    }
}

@SuppressLint("ViewConstructor")
class HwAvatarsImageView extends AvatarsImageView {

    public HwAvatarsImageView(@NonNull Context context, boolean inCall) {
        super(context, inCall);
    }

    @Override
    public void invalidate() {
        if (HwFrameLayout.hwEnabled) {
            HwFrameLayout.hwViews.add(this);
            return;
        }
        super.invalidate();
    }

    @Override
    public void invalidate(int l, int t, int r, int b) {
        if (HwFrameLayout.hwEnabled) {
            HwFrameLayout.hwViews.add(this);
            return;
        }
        super.invalidate(l, t, r, b);
    }
}

@SuppressLint("ViewConstructor")
class HwPeerStoriesView extends PeerStoriesView {

    public HwPeerStoriesView(@NonNull Context context, StoryViewer storyViewer, SharedResources sharedResources, Theme.ResourcesProvider resourcesProvider) {
        super(context, storyViewer, sharedResources, resourcesProvider);
    }

    @Override
    public void invalidate() {
        if (HwFrameLayout.hwEnabled) {
            HwFrameLayout.hwViews.add(this);
            return;
        }
        super.invalidate();
    }

    @Override
    public void invalidate(int l, int t, int r, int b) {
        if (HwFrameLayout.hwEnabled) {
            HwFrameLayout.hwViews.add(this);
            return;
        }
        super.invalidate(l, t, r, b);
    }
}
