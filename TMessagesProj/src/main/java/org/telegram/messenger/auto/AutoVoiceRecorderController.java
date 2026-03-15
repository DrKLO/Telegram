package org.telegram.messenger.auto;

import android.Manifest;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.car.app.CarContext;
import androidx.car.app.CarToast;

import org.telegram.messenger.FileLog;

import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;

final class AutoVoiceRecorderController {

    private static final String TAG = "AutoVoiceReturn";

    interface Listener {
        void onStateChanged();
    }

    enum State {
        IDLE,
        STARTING,
        RECORDING,
        STOPPING,
        SENDING,
        ERROR
    }

    private final CarContext carContext;
    private final int currentAccount;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private AutoVoiceRecorder activeRecorder;
    private long activeDialogId;
    private long pendingPermissionDialogId;
    private boolean permissionRequestInFlight;
    private long onRecordingSentDialogId;
    private Runnable onRecordingSentCallback;
    private State state = State.IDLE;

    AutoVoiceRecorderController(@NonNull CarContext carContext, int currentAccount) {
        this.carContext = carContext;
        this.currentAccount = currentAccount;
    }

    void addListener(@NonNull Listener listener) {
        listeners.addIfAbsent(listener);
    }

    void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    State getState() {
        return state;
    }

    long getActiveDialogId() {
        return activeDialogId;
    }

    boolean isRecordingForDialog(long dialogId) {
        return activeDialogId == dialogId && (state == State.RECORDING || state == State.STOPPING || state == State.SENDING);
    }

    void setOnRecordingSentCallback(long dialogId, @Nullable Runnable callback) {
        onRecordingSentDialogId = callback != null ? dialogId : 0;
        onRecordingSentCallback = callback;
        Log.d(TAG, "set callback dialog=" + dialogId + " activeDialog=" + activeDialogId + " hasCallback=" + (callback != null));
    }

    void toggleRecording(long dialogId) {
        switch (state) {
            case IDLE:
            case ERROR:
                startRecording(dialogId);
                return;
            case RECORDING:
                if (activeDialogId == dialogId) {
                    stopRecording();
                } else {
                    showToast("Stop current recording first");
                }
                return;
            case STARTING:
            case STOPPING:
            case SENDING:
                showToast("Please wait");
                return;
        }
    }

    void destroy() {
        clearOnRecordingSentCallback();
        if (activeRecorder == null) {
            return;
        }
        if (state == State.STARTING || state == State.RECORDING || state == State.ERROR) {
            activeRecorder.cancelAndDiscard();
        } else if (state == State.STOPPING || state == State.SENDING) {
            activeRecorder.detachCallback();
        }
        activeRecorder = null;
        activeDialogId = 0;
        updateState(State.IDLE);
    }

    private void startRecording(long dialogId) {
        if (carContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestMicPermission(dialogId);
            return;
        }
        startRecordingInternal(dialogId);
    }

    private void startRecordingInternal(long dialogId) {
        activeDialogId = dialogId;
        updateState(State.STARTING);
        activeRecorder = new AutoVoiceRecorder(carContext, currentAccount, dialogId);
        activeRecorder.setCallback(new AutoVoiceRecorder.RecordingCallback() {
            @Override
            public void onRecordingStarted() {
                updateState(State.RECORDING);
            }

            @Override
            public void onRecordingSending() {
                Log.d(TAG, "onRecordingSending dialog=" + activeDialogId);
                updateState(State.SENDING);
            }

            @Override
            public void onRecordingSent() {
                long sentDialogId = activeDialogId;
                Log.d(TAG, "onRecordingSent dialog=" + sentDialogId + " callbackDialog=" + onRecordingSentDialogId);
                activeRecorder = null;
                activeDialogId = 0;
                updateState(State.IDLE);
                dispatchOnRecordingSent(sentDialogId);
            }

            @Override
            public void onRecordingDiscarded() {
                activeRecorder = null;
                activeDialogId = 0;
                updateState(State.IDLE);
                clearOnRecordingSentCallback();
            }

            @Override
            public void onRecordingError(String error) {
                activeRecorder = null;
                activeDialogId = 0;
                updateState(State.ERROR);
                clearOnRecordingSentCallback();
                showToast(error);
            }
        });
        activeRecorder.startRecording();
    }

    private void requestMicPermission(long dialogId) {
        if (permissionRequestInFlight) {
            showToast("Please wait");
            return;
        }
        pendingPermissionDialogId = dialogId;
        permissionRequestInFlight = true;
        carContext.requestPermissions(Collections.singletonList(Manifest.permission.RECORD_AUDIO),
                (approved, rejected) -> {
                    permissionRequestInFlight = false;
                    long requestedDialogId = pendingPermissionDialogId;
                    pendingPermissionDialogId = 0;
                    if (approved.contains(Manifest.permission.RECORD_AUDIO)) {
                        startRecordingInternal(requestedDialogId);
                    } else {
                        updateState(State.ERROR);
                        showToast("Mic permission denied");
                    }
                });
    }

    private void stopRecording() {
        if (activeRecorder == null) {
            return;
        }
        updateState(State.STOPPING);
        activeRecorder.stopAndSend();
    }

    private void updateState(State state) {
        this.state = state;
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).onStateChanged();
        }
    }

    private void showToast(@NonNull String text) {
        CarToast.makeText(carContext, text, CarToast.LENGTH_SHORT).show();
    }

    private void dispatchOnRecordingSent(long dialogId) {
        Runnable callback = dialogId == onRecordingSentDialogId ? onRecordingSentCallback : null;
        Log.d(TAG, "dispatch dialog=" + dialogId + " callbackDialog=" + onRecordingSentDialogId + " willRun=" + (callback != null));
        clearOnRecordingSentCallback();
        if (callback != null) {
            callback.run();
        }
    }

    private void clearOnRecordingSentCallback() {
        onRecordingSentDialogId = 0;
        onRecordingSentCallback = null;
    }
}
