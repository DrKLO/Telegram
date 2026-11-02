package org.telegram.ui;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Build;

import androidx.annotation.Nullable;

import org.telegram.messenger.FileLog;

import java.util.ArrayList;

/**
 * The single owner of the requested orientation of {@link LaunchActivity}. Screens do not set the orientation
 * themselves: they ask for one with a request and release it when they no longer need it, and the controller
 * decides what to apply from all the current requests and the screen.
 * <p>
 * For now: while there is at least one portrait request, portrait is fixed where Android honors it. With
 * targetSdk 36 (Android 16) a requested orientation is ignored on displays with the smallest width of 600 dp
 * and more, and it never applies in multi-window mode; there the orientation is left free. Without requests,
 * the orientation the activity had before is restored.
 */
public final class OrientationController {

    /** A request of a screen; releasing it twice is harmless. */
    public interface Request {
        void release();
    }

    private static final int LARGE_SCREEN_DP = 600;
    private static final int NONE = Integer.MIN_VALUE;

    private final Activity activity;
    private final ArrayList<Request> portraitRequests = new ArrayList<>();
    private int baseOrientation = NONE;     // the orientation before the controller changed it

    OrientationController(Activity activity) {
        this.activity = activity;
    }

    /** The controller of the activity, or null for an activity that has none. */
    @Nullable
    public static OrientationController of(@Nullable Activity activity) {
        return activity instanceof LaunchActivity ? ((LaunchActivity) activity).getOrientationController() : null;
    }

    /** Asks for portrait; the screen does not need to know whether it can be applied. */
    public Request requestPortrait() {
        final Request request = new Request() {
            @Override
            public void release() {
                if (portraitRequests.remove(this)) {
                    update();
                }
            }
        };
        portraitRequests.add(request);
        update();
        return request;
    }

    /** The screen or the window mode has changed, the decision may change too. */
    void update() {
        final boolean portrait = !portraitRequests.isEmpty() && canFixOrientation();
        try {
            if (portrait) {
                if (baseOrientation == NONE) {
                    baseOrientation = activity.getRequestedOrientation();
                }
                if (activity.getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                }
            } else if (baseOrientation != NONE) {
                activity.setRequestedOrientation(baseOrientation);
                baseOrientation = NONE;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private boolean canFixOrientation() {
        if (activity.getResources().getConfiguration().smallestScreenWidthDp >= LARGE_SCREEN_DP) {
            return false;
        }
        return Build.VERSION.SDK_INT < 24 || !activity.isInMultiWindowMode();
    }
}
