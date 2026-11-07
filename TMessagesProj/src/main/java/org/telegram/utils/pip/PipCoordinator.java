package org.telegram.utils.pip;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.utils.WindowVisibilityManager;

import java.util.ArrayList;

/**
 * Process-wide owner of all PiP sessions and the only bridge between PiP and the activity.
 *
 * Host rules:
 * - overlay permission → floating window above everything, it never moves to system PiP;
 * - no overlay → in-app floating window while the app is visible; on leaving the app the content
 *   moves to system PiP if it needs to be shown outside the app ({@link PipContent#needsPipOutsideApp})
 *   and system PiP is allowed, otherwise it is paused.
 */
public final class PipCoordinator {
    static final String TAG = "PIP_DEBUG";

    private static PipCoordinator instance;

    public static PipCoordinator getInstance() {
        if (instance == null) {
            instance = new PipCoordinator();
        }
        return instance;
    }

    private final ArrayList<PipSession> sessions = new ArrayList<>();
    private final ArrayList<Runnable> onResumeActions = new ArrayList<>();
    private final SystemHost systemHost = new SystemHost(this);

    private @Nullable Activity activity;
    private boolean activityStarted;
    private boolean activityResumed;

    private PipCoordinator() {}

    public @NonNull PipSession register(@NonNull PipContent content) {
        final PipSession session = new PipSession(this, content);
        sessions.add(session);
        log("register " + content);
        invalidateSystem();
        return session;
    }

    public @Nullable PipSession findFloating() {
        for (int i = 0; i < sessions.size(); i++) {
            final PipSession session = sessions.get(i);
            if (session.isFloating()) {
                return session;
            }
        }
        return null;
    }

    @Nullable Activity getActivity() {
        return activity;
    }

    ArrayList<PipSession> getSessions() {
        return sessions;
    }

    boolean isActivityStarted() {
        return activityStarted;
    }

    void dismissOtherFloating(PipSession except) {
        for (int i = sessions.size() - 1; i >= 0; i--) {
            final PipSession session = sessions.get(i);
            // only the host's own windows: content with its own floating window (calls) stays
            if (session != except && session.isFloating()) {
                session.dismiss();
            }
        }
    }

    void onSessionDismissed(PipSession session, int prevState) {
        log("dismissed, prevState=" + prevState);
        sessions.remove(session);
        systemHost.onSessionDismissed(session, prevState);
        invalidateSystem();
    }

    boolean enterSystemPip(PipSession session) {
        return systemHost.enterNow(session);
    }

    public static boolean canEnterSystemPip() {
        return PipEnvironment.canUseSystemPip(ApplicationLoader.applicationContext);
    }

    void invalidateSystem() {
        systemHost.update();
    }

    void runWhenAppResumed(Runnable action) {
        if (activityResumed && activity != null && !AndroidUtilities.isInPictureInPictureMode(activity)) {
            action.run();
            return;
        }
        onResumeActions.add(action);
        if (activity != null && !activityStarted) {
            try {
                final Intent intent = new Intent(ApplicationLoader.applicationContext, activity.getClass());
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ApplicationLoader.applicationContext.startActivity(intent);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    /* Activity bridge, called by LaunchActivity */

    /** @param contentVisibility hides the activity content while it is in system PiP */
    public void onActivityCreate(@NonNull Activity activity, @Nullable WindowVisibilityManager.Controller contentVisibility) {
        if (this.activity != null && this.activity != activity) {
            // recreated: callbacks of the old instance (onStop, onDestroy) may come after the new onStart
            systemHost.detach();
        }
        this.activity = activity;
        activityStarted = false;
        activityResumed = false;
        systemHost.attach(activity, contentVisibility);
        PipEnvironment.startWatching(ApplicationLoader.applicationContext, this::onPermissionsChanged);
    }

    private void onPermissionsChanged() {
        for (int i = 0; i < sessions.size(); i++) {
            final PipSession session = sessions.get(i);
            if (session.floating != null && session.state == PipSession.STATE_FLOATING) {
                session.floating.checkWindowType();
            }
        }
        invalidateSystem();
    }

    public void onActivityStart(@NonNull Activity activity) {
        if (activity != this.activity) {
            return;
        }
        activityStarted = true;
        for (int i = 0; i < sessions.size(); i++) {
            final PipSession session = sessions.get(i);
            if (session.state == PipSession.STATE_FLOATING && session.floating != null && !session.floating.isOverlay()) {
                session.floating.onAppShown();
            }
        }
    }

    public void onActivityResume(@NonNull Activity activity) {
        if (activity != this.activity) {
            return;
        }
        activityResumed = true;
        systemHost.onResume();
        for (int i = 0; i < sessions.size(); i++) {
            final PipSession session = sessions.get(i);
            if (session.floating != null && session.state == PipSession.STATE_FLOATING) {
                session.floating.checkWindowType();
            }
        }
        if (!onResumeActions.isEmpty()) {
            final ArrayList<Runnable> actions = new ArrayList<>(onResumeActions);
            onResumeActions.clear();
            for (Runnable action : actions) {
                action.run();
            }
        }
        invalidateSystem();
    }

    public void onActivityPause(@NonNull Activity activity) {
        if (activity != this.activity) {
            return;
        }
        activityResumed = false;
        systemHost.onPause();
        // still visible here: keep the last frame of in-app windows that stay in the app
        for (int i = 0; i < sessions.size(); i++) {
            final PipSession session = sessions.get(i);
            if (session.state == PipSession.STATE_FLOATING && session.floating != null && !session.floating.isOverlay()) {
                session.floating.onAppHidden();
            }
        }
    }

    public void onActivityStop(@NonNull Activity activity) {
        if (activity != this.activity) {
            return;
        }
        activityStarted = false;
        systemHost.onStop();
        // in-app windows are not visible outside the app: pause, keep the window
        for (int i = 0; i < sessions.size(); i++) {
            final PipSession session = sessions.get(i);
            if (session.state == PipSession.STATE_FLOATING && session.floating != null && !session.floating.isOverlay()) {
                session.content.pause();
                session.invalidate();
            }
        }
    }

    public void onActivityDestroy(@NonNull Activity activity) {
        if (this.activity != activity) {
            return;
        }
        for (int i = sessions.size() - 1; i >= 0; i--) {
            sessions.get(i).dismiss();
        }
        systemHost.detach();
        this.activity = null;
        onResumeActions.clear();
    }

    public void onUserLeaveHint() {
        systemHost.onUserLeaveHint();
    }

    public void onPictureInPictureRequested() {
        systemHost.onUserLeaveHint();
    }

    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        systemHost.onPictureInPictureModeChanged(isInPictureInPictureMode);
    }

    public void onPictureInPictureUiStateChanged(@NonNull Object pipState) {
        systemHost.onPictureInPictureUiStateChanged(pipState);
    }

    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        if (activity != null && AndroidUtilities.isInPictureInPictureMode(activity)) {
            // the PiP window size is not the display size
            return;
        }
        for (int i = 0; i < sessions.size(); i++) {
            final PipSession session = sessions.get(i);
            if (session.floating != null) {
                session.floating.onConfigurationChanged();
            }
        }
    }

    static void log(String message) {
        Log.d(TAG, "[utils.pip] " + message);
    }
}
