package org.telegram.utils.pip;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Icon;
import android.app.PictureInPictureParams;
import android.app.PictureInPictureUiState;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.util.Rational;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media3.common.Player;
import androidx.media3.session.MediaSession;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.utils.WindowVisibilityManager;

import java.util.ArrayList;

/**
 * System PiP of the whole activity. At most one session at a time.
 * Owns PictureInPictureParams while it has a candidate; otherwise resets them.
 */
final class SystemHost {
    private static final float MIN_RATIO = 0.45f;
    private static final float MAX_RATIO = 2.35f;

    private final PipCoordinator coordinator;

    private @Nullable Activity activity;
    private @Nullable SystemPipLayout layout;
    private @Nullable WindowVisibilityManager.Controller contentVisibility;

    private @Nullable PipSession candidate;
    private @Nullable PipSession active;
    private @Nullable PipTextureView activeTarget;
    private @Nullable View activeSystemView;
    private boolean isInPip;
    private boolean isOwner;

    private @Nullable MediaSession mediaSession;

    private final Rect tmpRect = new Rect();
    private final RectF tmpRectF = new RectF();
    private final int[] tmpLocation = new int[2];

    SystemHost(PipCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    void attach(@NonNull Activity activity, @Nullable WindowVisibilityManager.Controller contentVisibility) {
        this.activity = activity;
        this.contentVisibility = contentVisibility;
        appliedParamsKey = null;
        layout = new SystemPipLayout(activity);
        ((ViewGroup) activity.getWindow().getDecorView()).addView(layout);
    }

    void detach() {
        releaseMediaSession();
        if (receiverRegistered && activity != null) {
            receiverRegistered = false;
            try {
                activity.unregisterReceiver(actionReceiver);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        if (layout != null) {
            AndroidUtilities.removeFromParent(layout);
            layout = null;
        }
        if (contentVisibility != null) {
            contentVisibility.destroy();
            contentVisibility = null;
        }
        activity = null;
        candidate = null;
        active = null;
        activeTarget = null;
        isOwner = false;
    }

    boolean isOwner() {
        return isOwner;
    }

    /* Params */

    void update() {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        if (active != null) {
            // not while entering: the system reads the params during its enter transition, and a
            // change then (auto-enter off, a hint taken from the shrinking window) cancels its animation
            if (!entering) {
                applyParams(active);
            }
            updateMediaSession(active);
            return;
        }
        candidate = findCandidate();
        if (candidate != null) {
            isOwner = true;
            applyParams(candidate);
        } else if (isOwner) {
            isOwner = false;
            appliedParamsKey = null;
            AndroidUtilities.resetPictureInPictureParams(activity);
        }
        updateMediaSession(candidate);
    }

    /**
     * Content shown in its primary UI wins (the user is looking at it); otherwise an in-app
     * floating window with the highest priority, the most recent on a tie.
     */
    private @Nullable PipSession findCandidate() {
        if (!PipEnvironment.canUseSystemPip(activity) || PipEnvironment.canDrawOverlays(activity)) {
            return null;
        }
        final ArrayList<PipSession> sessions = coordinator.getSessions();
        for (int i = sessions.size() - 1; i >= 0; i--) {
            final PipSession session = sessions.get(i);
            if (session.state == PipSession.STATE_PRIMARY && session.content.getFloatingView() == null && session.content.isPrimaryVisible() && session.content.needsPipOutsideApp()) {
                return session;
            }
        }
        PipSession best = null;
        for (int i = sessions.size() - 1; i >= 0; i--) {
            final PipSession session = sessions.get(i);
            if (session.state != PipSession.STATE_FLOATING || !session.content.needsPipOutsideApp()) {
                continue;
            }
            final boolean inAppWindow;
            if (session.floating != null) {
                inAppWindow = !session.floating.isOverlay() && !session.floating.hasFloatingView();
            } else {
                inAppWindow = session.ownFloating && !session.ownFloatingOverlay;
            }
            if (inAppWindow && (best == null || session.content.getPriority() > best.content.getPriority())) {
                best = session;
            }
        }
        if (best != null) {
            return best;
        }
        for (int i = sessions.size() - 1; i >= 0; i--) {
            final PipSession session = sessions.get(i);
            if (session.state == PipSession.STATE_PRIMARY && session.content.canEnterWithoutUi() && session.content.needsPipOutsideApp()
                && (best == null || session.content.getPriority() > best.content.getPriority())) {
                best = session;
            }
        }
        return best;
    }

    /** What was set last: the same params are not sent again (the hint is refreshed often). */
    private @Nullable String appliedParamsKey;

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void applyParams(@NonNull PipSession session) {
        final PictureInPictureParams params = buildParams(session);
        if (paramsKey != null && paramsKey.equals(appliedParamsKey)) {
            return;
        }
        appliedParamsKey = paramsKey;
        AndroidUtilities.setPictureInPictureParams(activity, params);
    }

    /** Between the start of entering system PiP and PiP mode reported by the activity. */
    private boolean entering;

    /** Source rect hint taken while the content was shown in its primary UI or floating window. */
    private final Rect lastHint = new Rect();
    private boolean lastHintValid;

    /** Key of the params built last by {@link #buildParams}. */
    private @Nullable String paramsKey;
    private final StringBuilder keyBuilder = new StringBuilder();

    @RequiresApi(api = Build.VERSION_CODES.O)
    private PictureInPictureParams buildParams(@NonNull PipSession session) {
        final PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();
        keyBuilder.setLength(0);
        final int width = session.content.getContentWidth();
        final int height = session.content.getContentHeight();
        if (width > 0 && height > 0) {
            final float ratio = width / (float) height;
            final Rational rational;
            if (ratio < MIN_RATIO) {
                rational = new Rational(45, 100);
            } else if (ratio > MAX_RATIO) {
                rational = new Rational(235, 100);
            } else {
                rational = new Rational(width, height);
            }
            builder.setAspectRatio(rational);
            keyBuilder.append(rational);
        }
        // the primary UI is gone in PiP: keep the hint taken before, the system may read the
        // params again while the enter transition starts (an empty hint cancels its animation)
        if (session.state != PipSession.STATE_SYSTEM) {
            lastHintValid = getSourceRect(session, lastHint);
        }
        if (lastHintValid) {
            builder.setSourceRectHint(lastHint);
            keyBuilder.append('|').append(lastHint.toShortString());
        }
        final ArrayList<RemoteAction> actions = buildActions(session);
        builder.setActions(actions);
        for (int i = 0; i < actions.size(); i++) {
            final RemoteAction action = actions.get(i);
            keyBuilder.append('|').append(action.getTitle()).append(action.isEnabled());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            final boolean autoEnter = PipEnvironment.useAutoEnter() && session.state != PipSession.STATE_SYSTEM;
            builder.setAutoEnterEnabled(autoEnter);
            keyBuilder.append('|').append(autoEnter);
        }
        paramsKey = keyBuilder.toString();
        return builder.build();
    }

    /* Actions */

    private static final String ACTION_INTENT = "org.telegram.utils.pip.ACTION";
    private static final String EXTRA_ACTION_ID = "action_id";

    private final ArrayList<PipAction> actionsTmp = new ArrayList<>();
    private boolean receiverRegistered;

    private final BroadcastReceiver actionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ACTION_INTENT.equals(intent.getAction())) {
                return;
            }
            final PipSession session = active != null ? active : candidate;
            if (session != null) {
                session.content.onSystemAction(intent.getIntExtra(EXTRA_ACTION_ID, -1));
            }
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.O)
    private ArrayList<RemoteAction> buildActions(@NonNull PipSession session) {
        final ArrayList<RemoteAction> result = new ArrayList<>();
        actionsTmp.clear();
        session.content.getSystemActions(actionsTmp, activity.getMaxNumPictureInPictureActions());
        for (int i = 0; i < actionsTmp.size(); i++) {
            final PipAction action = actionsTmp.get(i);
            final Intent intent = new Intent(ACTION_INTENT);
            intent.setPackage(activity.getPackageName());
            intent.putExtra(EXTRA_ACTION_ID, action.id);
            // the request code is the action id: the same PendingIntent is reused, not piled up
            final PendingIntent pendingIntent = PendingIntent.getBroadcast(activity, action.id, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            final RemoteAction remoteAction = new RemoteAction(Icon.createWithResource(activity, action.iconRes), action.title, action.title, pendingIntent);
            remoteAction.setEnabled(action.enabled);
            result.add(remoteAction);
        }
        actionsTmp.clear();
        if (!result.isEmpty() && !receiverRegistered) {
            receiverRegistered = true;
            final IntentFilter filter = new IntentFilter(ACTION_INTENT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.registerReceiver(actionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                activity.registerReceiver(actionReceiver, filter);
            }
        }
        return result;
    }

    /** Enter system PiP right now, e.g. from a PiP button in the content's own UI. */
    boolean enterNow(@NonNull PipSession session) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O || active != null || !PipEnvironment.canUseSystemPip(activity)) {
            return false;
        }
        final PictureInPictureParams params = buildParams(session);
        startEnter(session);
        try {
            if (activity.enterPictureInPictureMode(params)) {
                return true;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        exitToApp();
        return false;
    }

    /** Source rect hint in activity window coordinates (the decor view). */
    private boolean getSourceRect(@NonNull PipSession session, @NonNull Rect out) {
        if (activity == null) {
            return false;
        }
        if (session.isFloating()) {
            session.floating.getRect(tmpRectF);
            tmpRectF.round(out);
        } else {
            session.content.getPrimaryVideoRect(out);
        }
        if (out.isEmpty()) {
            return false;
        }
        final View decorView = activity.getWindow().getDecorView();
        decorView.getLocationOnScreen(tmpLocation);
        out.offset(-tmpLocation[0], -tmpLocation[1]);
        final float aspect = out.width() / (float) out.height();
        if (!out.intersect(0, 0, decorView.getWidth(), decorView.getHeight())) {
            return false;
        }
        // cut by the window edge (zoomed in): another aspect than the content, the system rejects it
        return Math.abs(out.width() / (float) out.height() / aspect - 1f) < 0.02f;
    }

    /* Media session */

    private void updateMediaSession(@Nullable PipSession session) {
        final Player player = session != null ? session.content.getMediaSessionPlayer() : null;
        if (player == null) {
            releaseMediaSession();
            return;
        }
        if (mediaSession != null && mediaSession.getPlayer() == player) {
            return;
        }
        releaseMediaSession();
        try {
            mediaSession = new MediaSession.Builder(activity, player)
                .setId("utils-pip-media-session")
                .build();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void releaseMediaSession() {
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
    }

    /* Activity callbacks */

    void onUserLeaveHint() {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O || PipEnvironment.useAutoEnter()) {
            return;
        }
        if (active != null) {
            return;
        }
        update();
        if (candidate == null) {
            return;
        }
        final PictureInPictureParams params = buildParams(candidate);
        startEnter(candidate);
        try {
            if (!activity.enterPictureInPictureMode(params)) {
                exitToApp();
            }
        } catch (Exception e) {
            FileLog.e(e);
            exitToApp();
        }
    }

    void onPause() {
        if (activity != null && PipEnvironment.useAutoEnter() && active == null && candidate != null && AndroidUtilities.isInPictureInPictureMode(activity)) {
            startEnter(candidate);
        }
    }

    void onResume() {
        if (active != null && activity != null && !AndroidUtilities.isInPictureInPictureMode(activity)) {
            exitToApp();
        }
    }

    void onStop() {

    }

    void onPictureInPictureUiStateChanged(@NonNull Object state) {
        if (Build.VERSION.SDK_INT >= 35 && state instanceof PictureInPictureUiState) {
            if (((PictureInPictureUiState) state).isTransitioningToPip() && active == null && candidate != null) {
                startEnter(candidate);
            }
        }
    }

    void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        if (active == null) {
            if (isInPictureInPictureMode && candidate != null) {
                startEnter(candidate);
            }
            if (active == null) {
                // nothing of ours is shown: never hide the activity content for an empty window
                return;
            }
        }
        if (isInPictureInPictureMode) {
            completeEnter();
        } else if (coordinator.isActivityStarted()) {
            exitToApp();
        } else {
            closeFromSystem();
        }
    }

    /* Transitions */

    private void startEnter(@NonNull PipSession session) {
        if (layout == null || activity == null) {
            return;
        }
        PipCoordinator.log("system: start enter, from state " + session.state);
        final Rect hint = new Rect();
        if (!getSourceRect(session, hint)) {
            hint.setEmpty();
        }
        active = session;
        candidate = null;
        entering = true;
        session.stateBeforeSystem = session.state;

        final View systemView = session.floating == null ? session.content.createSystemView(activity) : null;
        // Without a hint (calls) the system covers the window with the app icon while it shrinks:
        // the own UI stays until PiP mode is reported, the system view would fill the whole
        // window before that. With a hint the content is hidden now, not when PiP mode is
        // reported: the primary UI (e.g. the viewer's own window) goes away below, and the
        // activity content would show through for a frame.
        final boolean showOnComplete = systemView != null && hint.isEmpty();
        if (contentVisibility != null && !showOnComplete) {
            contentVisibility.setHidden(true);
        }
        final PipTextureView target;
        if (systemView != null) {
            target = null;
            if (showOnComplete) {
                systemView.setVisibility(View.INVISIBLE);
            }
            layout.setTarget(systemView, hint);
            session.content.onSystemEnter(systemView);
        } else if (session.state == PipSession.STATE_FLOATING && session.floating != null) {
            target = session.floating.detachForSystem();
            layout.setTarget(target, hint);
        } else {
            target = new PipTextureView(activity);
            layout.setTarget(target, hint);
            session.content.releasePrimary(target);
        }
        layout.bringToFront();
        activeTarget = target;
        activeSystemView = systemView;
        session.state = PipSession.STATE_SYSTEM;
    }

    private void completeEnter() {
        PipCoordinator.log("system: complete enter");
        isInPip = true;
        if (entering && active != null && activeSystemView != null) {
            active.content.onSystemEntered(activeSystemView);
        }
        if (entering) {
            entering = false;
            if (active != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applyParams(active);
            }
        }
        if (contentVisibility != null) {
            contentVisibility.setHidden(true);
        }
        if (activeSystemView != null) {
            activeSystemView.setVisibility(View.VISIBLE);
        }
        if (layout != null) {
            layout.requestLayout();
        }
    }

    private void exitToApp() {
        final PipSession session = active;
        final PipTextureView target = activeTarget;
        final View systemView = activeSystemView;
        PipCoordinator.log("system: exit to app");
        active = null;
        activeTarget = null;
        activeSystemView = null;
        isInPip = false;
        entering = false;
        if (contentVisibility != null) {
            contentVisibility.setHidden(false);
        }
        if (layout != null) {
            layout.setTarget(null, null);
        }
        if (session != null && systemView != null) {
            session.state = session.stateBeforeSystem;
            session.content.onSystemExit(systemView);
            session.content.releaseSystemView(systemView);
            coordinator.invalidateSystem();
            return;
        }
        if (session == null || target == null) {
            coordinator.invalidateSystem();
            return;
        }
        if (session.stateBeforeSystem == PipSession.STATE_FLOATING && session.floating != null) {
            session.state = PipSession.STATE_FLOATING;
            session.floating.attachAfterSystem(target);
        } else {
            session.state = PipSession.STATE_PRIMARY;
            android.graphics.SurfaceTexture texture = target.takeSurfaceTexture();
            if (texture == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // entering failed before the target was drawn: the content still has to take its
                // UI back (it released it in releasePrimary), with an empty texture
                texture = new android.graphics.SurfaceTexture(false);
            }
            if (texture != null) {
                session.content.restorePrimary(texture);
            }
        }
        coordinator.invalidateSystem();
    }

    private void closeFromSystem() {
        PipCoordinator.log("system: closed by the user");
        isInPip = false;
        entering = false;
        if (contentVisibility != null) {
            contentVisibility.setHidden(false);
        }
        if (active != null) {
            active.dismiss();
        }
    }

    void onSessionDismissed(@NonNull PipSession session, int prevState) {
        if (session == candidate) {
            candidate = null;
        }
        if (session != active) {
            return;
        }
        final PipTextureView target = activeTarget;
        final View systemView = activeSystemView;
        active = null;
        activeTarget = null;
        activeSystemView = null;
        if (layout != null) {
            layout.setTarget(null, null);
        }
        if (target != null) {
            target.releaseSurfaceTexture();
        }
        if (systemView != null) {
            session.content.releaseSystemView(systemView);
        }
        if (activity != null && isInPip && AndroidUtilities.isInPictureInPictureMode(activity)) {
            // the content ended while shown in system PiP
            activity.moveTaskToBack(false);
        }
        isInPip = false;
        if (contentVisibility != null) {
            contentVisibility.setHidden(false);
        }
    }
}
