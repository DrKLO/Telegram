package org.telegram.messenger.pip;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Build;
import android.util.Log;
import android.view.Choreographer;

import androidx.annotation.NonNull;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.pip.activity.IPipActivity;
import org.telegram.messenger.pip.activity.IPipActivityActionListener;
import org.telegram.messenger.pip.activity.IPipActivityAnimationListener;
import org.telegram.messenger.pip.activity.IPipActivityHandler;
import org.telegram.messenger.pip.activity.IPipActivityListener;
import org.telegram.messenger.pip.utils.PipActions;
import org.telegram.messenger.pip.utils.PipDuration;
import org.telegram.messenger.pip.utils.PipUtils;

import java.util.ArrayList;
import java.util.HashMap;

class PipActivityHandler implements IPipActivityHandler {
    private final ArrayList<IPipActivityListener> listeners = new ArrayList<>();
    private final ArrayList<IPipActivityAnimationListener> animationListeners = new ArrayList<>();
    private final HashMap<String, ArrayList<IPipActivityActionListener>> actionListeners = new HashMap<>();

    private final Activity activity;

    PipActivityHandler(Activity activity) {
        this.activity = activity;
    }

    void addPipListener(IPipActivityListener listener) {
        listeners.add(listener);
    }

    void removePipListener(IPipActivityListener listener) {
        listeners.remove(listener);
    }

    void addAnimationListener(IPipActivityAnimationListener listener) {
        animationListeners.add(listener);
    }

    void removeAnimationListener(IPipActivityAnimationListener listener) {
        animationListeners.remove(listener);
    }

    void addActionListener(String sourceId, IPipActivityActionListener listener) {
        ArrayList<IPipActivityActionListener> listeners = actionListeners.get(sourceId);
        if (listeners == null) {
            listeners = new ArrayList<>();
            actionListeners.put(sourceId, listeners);
        }
        listeners.add(listener);
    }

    void removeActionListener(String sourceId, IPipActivityActionListener listener) {
        ArrayList<IPipActivityActionListener> listeners = actionListeners.get(sourceId);
        if (listeners == null) {
            return;
        }
        listeners.remove(listener);
        if (listeners.isEmpty()) {
            actionListeners.remove(sourceId);
        }
    }



    /* Activity lifecycle */

    private boolean isActivityStarted;
    private boolean isInPictureInPictureModeInternal;
    private PictureInPictureParams pictureInPictureParams;

    @Override
    public void onPictureInPictureRequested() {
        Log.i(PipUtils.TAG, "[Activity] onPictureInPictureRequested");
        manualEnterPictureInPictureModeInternal();
    }

    @Override
    public void onUserLeaveHint() {
        Log.i(PipUtils.TAG, "[Activity] onUserLeaveHint");
        manualEnterPictureInPictureModeInternal();
    }

    @Override
    public void onStart() {
        Log.i(PipUtils.TAG, "[Activity] onStart");
        isActivityStarted = true;

        IntentFilter filter = new IntentFilter(PipActions.ACTION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(broadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            activity.registerReceiver(broadcastReceiver, filter);
        }
    }

    @Override
    public void onResume() {
        Log.i(PipUtils.TAG, "[Activity] onResume");

        if (isInPictureInPictureModeInternal) {
            dispatchCompleteExitPip(false);
        }
    }

    @Override
    public void onPause() {
        Log.i(PipUtils.TAG, "[Activity] onPause");

        if (AndroidUtilities.isInPictureInPictureMode(activity) && hasContentForPictureInPictureMode()) {
            if (PipUtils.useAutoEnterInPictureInPictureMode()) {
                dispatchStartEnterPip();
            }
        }
    }

    @Override
    public void onStop() {
        Log.i(PipUtils.TAG, "[Activity] onStop");
        isActivityStarted = false;

        if (isInPictureInPictureModeInternal) {
            dispatchStartExitPip(true);
        }
        activity.unregisterReceiver(broadcastReceiver);
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, @NonNull Configuration ignoredNewConfig) {
        Log.i(PipUtils.TAG, "[Activity] onPictureInPictureModeChanged " + isInPictureInPictureMode);

        if (this.isInPictureInPictureModeInternal) {
            if (isInPictureInPictureMode) {
                dispatchCompleteEnterPip();
            } else {
                if (isActivityStarted) {
                    dispatchStartExitPip(false);
                } else {
                    dispatchCompleteExitPip(true);
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        Log.i(PipUtils.TAG, "[Activity] onDestroy");
    }

    @Override
    public void onConfigurationChanged(Configuration ignoredNewConfig) {
        android.util.Log.i(PipUtils.TAG, "[Activity] onConfigurationChanged");
    }

    @Override
    public void setPictureInPictureParams(PictureInPictureParams params) {
        Log.i(PipUtils.TAG, "[Activity] setPictureInPictureParams");
        this.pictureInPictureParams = params;
    }



    /* Internal */

    private boolean hasContentForPictureInPictureMode() {
        if (activity instanceof IPipActivity) {
            return ((IPipActivity) activity).getPipController().hasContentForPictureInPictureMode();
        }

        return false;
    }

    private void manualEnterPictureInPictureModeInternal() {
        if (isInPictureInPictureModeInternal) {
            return;
        }

        if (PipUtils.useAutoEnterInPictureInPictureMode()) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (pictureInPictureParams != null && hasContentForPictureInPictureMode()) {
                dispatchStartEnterPip();
                activity.enterPictureInPictureMode(pictureInPictureParams);
            }
        }
    }



    /* Dispatchers */

    private void dispatchStartEnterPip() {
        isInPictureInPictureModeInternal = true;
        for (IPipActivityListener listener: listeners) {
            listener.onStartEnterToPip();
        }
        dispatchEnterAnimationStart();
    }

    private void dispatchCompleteEnterPip() {
        dispatchEnterAnimationEnd();
        for (IPipActivityListener listener: listeners) {
            listener.onCompleteEnterToPip();
        }
    }

    private void dispatchStartExitPip(boolean byActivityStop) {
        for (IPipActivityListener listener: listeners) {
            listener.onStartExitFromPip(byActivityStop);
        }
        dispatchLeaveAnimationStart();
    }

    private void dispatchCompleteExitPip(boolean byActivityStop) {
        dispatchLeaveAnimationEnd();

        isInPictureInPictureModeInternal = false;
        for (IPipActivityListener listener: listeners) {
            listener.onCompleteExitFromPip(byActivityStop);
        }
    }

    private void dispatchEnterAnimationStart() {
        final long estimated = durationEnter.estimated();
        for (IPipActivityAnimationListener listener: animationListeners) {
            listener.onEnterAnimationStart(estimated);
        }

        dispatchTransitionAnimationProgress(0);

        durationEnter.start();
        subscribeToFrameUpdates();
    }

    private void dispatchEnterAnimationEnd() {
        dispatchTransitionAnimationProgress(1);

        final long duration = durationEnter.end();
        for (IPipActivityAnimationListener listener: animationListeners) {
            listener.onEnterAnimationEnd(duration);
        }

        unsubscribeFromFrameUpdates();
    }

    private void dispatchLeaveAnimationStart() {
        final long estimated = durationLeave.estimated();
        for (IPipActivityAnimationListener listener: animationListeners) {
            listener.onLeaveAnimationStart(estimated);
        }

        dispatchTransitionAnimationProgress(1);

        durationLeave.start();
        subscribeToFrameUpdates();
    }

    private void dispatchLeaveAnimationEnd() {
        dispatchTransitionAnimationProgress(0);
        final long duration = durationLeave.end();
        for (IPipActivityAnimationListener listener: animationListeners) {
            listener.onLeaveAnimationEnd(duration);
        }

        unsubscribeFromFrameUpdates();
    }

    private float lastProgress = -1f;

    private void dispatchTransitionAnimationProgress(float progress) {
        if (progress == lastProgress) return;
        lastProgress = progress;

        for (IPipActivityAnimationListener listener: animationListeners) {
            listener.onTransitionAnimationProgress(progress);
        }
    }

    private void dispatchAction(String sourceId, int actionId) {
        final ArrayList<IPipActivityActionListener> listeners = actionListeners.get(sourceId);
        if (listeners == null) {
            return;
        }

        for (IPipActivityActionListener listener: listeners) {
            listener.onPipAction(actionId);
        }
    }




    /* Frame Callback */

    private final PipDuration durationEnter = new PipDuration("enter");
    private final PipDuration durationLeave = new PipDuration("leave");

    private final Choreographer choreographer = Choreographer.getInstance();
    private final Choreographer.FrameCallback callback = this::onFrameInternal;
    private boolean hasFrameListener;

    private void subscribeToFrameUpdates() {
        if (hasFrameListener) return;
        hasFrameListener = true;

        choreographer.postFrameCallback(callback);
    }

    private void unsubscribeFromFrameUpdates() {
        if (!hasFrameListener) return;
        hasFrameListener = false;
        choreographer.removeFrameCallback(callback);
    }

    private void onFrameInternal(long frameTimeNanos) {
        if (!hasFrameListener) return;

        for (IPipActivityAnimationListener listener: animationListeners) {
            listener.onTransitionAnimationFrame();
        }

        if (durationEnter.isStarted()) {
            float progress = MathUtils.clamp(durationEnter.progress() / 0.95f, 0, 1);
            dispatchTransitionAnimationProgress(progress);
        } else if (durationLeave.isStarted()) {
            float progress = MathUtils.clamp((1f - durationLeave.progress() / 0.95f), 0, 1);
            dispatchTransitionAnimationProgress(progress);
        }

        choreographer.postFrameCallback(callback);
    }



    /* Actions */

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (PipActions.isPipIntent(intent)) {
                final String sourceId = PipActions.getSourceId(intent);
                final int actionId = PipActions.getActionId(intent);
                dispatchAction(sourceId, actionId);
            }
        }
    };
}
