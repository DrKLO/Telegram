package org.telegram.messenger.auto;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.CarToast;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.Pane;
import androidx.car.app.model.PaneTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AccountInstance;

final class AutoComposeActionScreen extends Screen {

    private static final String TAG = "AutoVoiceReturn";

    private final long dialogId;
    private final String title;
    private final AutoComposeSender composeSender;
    private final AutoVoiceRecorderController voiceRecorderController;
    private final AutoAvatarProvider avatarProvider;
    private final int returnPopCount;

    private AutoVoiceRecorderController.State lastVoiceState;

    AutoComposeActionScreen(@NonNull CarContext carContext,
                            long dialogId,
                            @NonNull String title,
                            @NonNull AccountInstance accountInstance,
                            @NonNull AutoVoiceRecorderController voiceRecorderController,
                            @NonNull AutoAvatarProvider avatarProvider,
                            int returnPopCount) {
        super(carContext);
        this.dialogId = dialogId;
        this.title = title;
        this.composeSender = new AutoComposeSender(accountInstance.getCurrentAccount(), accountInstance);
        this.voiceRecorderController = voiceRecorderController;
        this.avatarProvider = avatarProvider;
        this.returnPopCount = returnPopCount;
        this.lastVoiceState = voiceRecorderController.getState();
        voiceRecorderController.setOnRecordingSentCallback(dialogId, this::popBackToMain);
        AutoVoiceRecorderController.Listener recorderListener = () -> {
            AutoVoiceRecorderController.State newState = voiceRecorderController.getState();
            handleVoiceStateChanged(lastVoiceState, newState);
            lastVoiceState = newState;
            invalidate();
        };
        voiceRecorderController.addListener(recorderListener);
        getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onDestroy(@NonNull LifecycleOwner owner) {
                voiceRecorderController.setOnRecordingSentCallback(dialogId, null);
                voiceRecorderController.removeListener(recorderListener);
            }
        });
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        Pane.Builder paneBuilder = new Pane.Builder()
                .addRow(new Row.Builder()
                        .setTitle(buildStatusTitle())
                        .addText(buildStatusLine())
                        .addText("Choose text or voice")
                        .build())
                .addAction(new Action.Builder()
                        .setTitle("Dictate text")
                        .setFlags(Action.FLAG_PRIMARY)
                        .setOnClickListener(() -> getScreenManager().push(
                                new AutoComposeDictationScreen(
                                        getCarContext(),
                                        dialogId,
                                        title,
                                        avatarProvider.getDialogIcon(dialogId),
                                        composeSender)))
                        .build())
                .addAction(new Action.Builder()
                        .setTitle(voiceRecorderController.isRecordingForDialog(dialogId) ? "Stop voice message" : "Voice message")
                        .setOnClickListener(this::toggleVoiceMessage)
                        .build());
        CarIcon avatar = avatarProvider.getDialogIcon(dialogId);
        if (avatar != null) {
            paneBuilder.setImage(avatar);
        }
        return new PaneTemplate.Builder(paneBuilder.build())
                .setTitle(title)
                .setHeaderAction(Action.BACK)
                .build();
    }

    private void toggleVoiceMessage() {
        Log.d(TAG, "toggle voice dialog=" + dialogId
                + " state=" + voiceRecorderController.getState()
                + " activeDialog=" + voiceRecorderController.getActiveDialogId()
                + " stack=" + getScreenManager().getStackSize());
        voiceRecorderController.toggleRecording(dialogId);
        if (voiceRecorderController.isRecordingForDialog(dialogId)) {
            CarToast.makeText(getCarContext(), "Recording voice message", CarToast.LENGTH_SHORT).show();
        }
    }

    private void handleVoiceStateChanged(@NonNull AutoVoiceRecorderController.State oldState,
                                         @NonNull AutoVoiceRecorderController.State newState) {
        Log.d(TAG, "state dialog=" + dialogId + " " + oldState + " -> " + newState
                + " stack=" + getScreenManager().getStackSize());
        if (newState == AutoVoiceRecorderController.State.ERROR) {
            voiceRecorderController.setOnRecordingSentCallback(dialogId, null);
        }
    }

    private String buildStatusTitle() {
        if (voiceRecorderController.isRecordingForDialog(dialogId)) {
            return title;
        }
        return title;
    }

    private String buildStatusLine() {
        if (voiceRecorderController.isRecordingForDialog(dialogId)) {
            return "Voice recording is active";
        }
        AutoVoiceRecorderController.State state = voiceRecorderController.getState();
        if (state == AutoVoiceRecorderController.State.SENDING) {
            return "Sending voice message";
        }
        if (state == AutoVoiceRecorderController.State.ERROR) {
            return "Choose how to send";
        }
        return "Choose how to send";
    }

    private void popBackToMain() {
        AndroidUtilities.runOnUIThread(() -> {
            AutoUiFeedback.showOnMainScreen("Voice message sent");
            int before = getScreenManager().getStackSize();
            Log.d(TAG, "pop start dialog=" + dialogId + " stackBefore=" + before);
            int guard = 0;
            while (getScreenManager().getStackSize() > 1 && guard++ < 8) {
                getScreenManager().pop();
            }
            Log.d(TAG, "pop end dialog=" + dialogId + " stackAfter=" + getScreenManager().getStackSize());
        });
    }
}
