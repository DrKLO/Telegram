package org.telegram.messenger.auto;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

final class AutoComposeDictationScreen extends Screen {

    private enum State {
        IDLE,
        LISTENING,
        PROCESSING,
        READY_TO_SEND,
        ERROR
    }

    private final long dialogId;
    private final String title;
    private final CarIcon avatar;
    private final AutoComposeSender composeSender;

    private SpeechRecognizer speechRecognizer;
    private State state = State.IDLE;
    private String recognizedText;
    private boolean recognitionStarted;
    private boolean permissionRequestInFlight;
    private boolean destroyed;

    AutoComposeDictationScreen(@NonNull CarContext carContext,
                               long dialogId,
                               @NonNull String title,
                               CarIcon avatar,
                               @NonNull AutoComposeSender composeSender) {
        super(carContext);
        this.dialogId = dialogId;
        this.title = title;
        this.avatar = avatar;
        this.composeSender = composeSender;
        getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onDestroy(@NonNull LifecycleOwner owner) {
                destroyed = true;
                releaseRecognition();
            }
        });
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        if (!recognitionStarted && state == State.IDLE) {
            recognitionStarted = true;
            AndroidUtilities.runOnUIThread(AutoComposeDictationScreen.this::startRecognitionIfNeeded);
        }

        Pane.Builder paneBuilder = new Pane.Builder()
                .addRow(new Row.Builder()
                        .setTitle(statusTitle())
                        .addText(statusText())
                        .addText(footerText())
                        .build())
                .addAction(primaryAction())
                .addAction(secondaryAction());
        if (avatar != null) {
            paneBuilder.setImage(avatar);
        }
        return new PaneTemplate.Builder(paneBuilder.build())
                .setTitle(title)
                .setHeaderAction(Action.BACK)
                .build();
    }

    private void startRecognitionIfNeeded() {
        if (destroyed || state == State.LISTENING || state == State.PROCESSING) {
            return;
        }
        if (getCarContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestMicPermission();
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(getCarContext().getApplicationContext())) {
            state = State.ERROR;
            recognizedText = null;
            CarToast.makeText(getCarContext(), "Speech recognition unavailable", CarToast.LENGTH_SHORT).show();
            invalidate();
            return;
        }
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getCarContext().getApplicationContext());
            speechRecognizer.setRecognitionListener(new DictationListener());
        }
        recognizedText = null;
        state = State.LISTENING;
        invalidate();
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        speechRecognizer.startListening(intent);
    }

    private void requestMicPermission() {
        if (permissionRequestInFlight) {
            return;
        }
        permissionRequestInFlight = true;
        getCarContext().requestPermissions(Collections.singletonList(Manifest.permission.RECORD_AUDIO),
                (approved, rejected) -> {
                    permissionRequestInFlight = false;
                    if (destroyed) {
                        return;
                    }
                    if (approved.contains(Manifest.permission.RECORD_AUDIO)) {
                        startRecognitionIfNeeded();
                    } else {
                        state = State.ERROR;
                        CarToast.makeText(getCarContext(), "Mic permission denied", CarToast.LENGTH_SHORT).show();
                        invalidate();
                    }
                });
    }

    private Action primaryAction() {
        switch (state) {
            case LISTENING:
            case PROCESSING:
                return new Action.Builder()
                        .setTitle("Stop listening")
                        .setFlags(Action.FLAG_PRIMARY)
                        .setOnClickListener(this::stopRecognition)
                        .build();
            case READY_TO_SEND:
                return new Action.Builder()
                        .setTitle("Send")
                        .setFlags(Action.FLAG_PRIMARY)
                        .setOnClickListener(this::sendRecognizedText)
                        .build();
            case ERROR:
                return new Action.Builder()
                        .setTitle("Try again")
                        .setFlags(Action.FLAG_PRIMARY)
                        .setOnClickListener(this::retryRecognition)
                        .build();
            case IDLE:
            default:
                return new Action.Builder()
                        .setTitle("Start dictation")
                        .setFlags(Action.FLAG_PRIMARY)
                        .setOnClickListener(this::retryRecognition)
                        .build();
        }
    }

    private Action secondaryAction() {
        switch (state) {
            case READY_TO_SEND:
                return new Action.Builder()
                        .setTitle("Retry")
                        .setOnClickListener(this::retryRecognition)
                        .build();
            case LISTENING:
            case PROCESSING:
            case ERROR:
            case IDLE:
            default:
                return new Action.Builder()
                        .setTitle("Cancel")
                        .setOnClickListener(this::cancelFlow)
                        .build();
        }
    }

    private String statusTitle() {
        switch (state) {
            case LISTENING:
                return "Listening";
            case PROCESSING:
                return "Processing";
            case READY_TO_SEND:
                return "Review message";
            case ERROR:
                return "Couldn't recognize speech";
            case IDLE:
            default:
                return "Dictate text";
        }
    }

    private String statusText() {
        switch (state) {
            case LISTENING:
                return "Speak now";
            case PROCESSING:
                return "Finishing recognition";
            case READY_TO_SEND:
                return TextUtils.isEmpty(recognizedText) ? "No text recognized" : recognizedText;
            case ERROR:
                return "Try again or cancel";
            case IDLE:
            default:
                return "Start text dictation";
        }
    }

    private String footerText() {
        switch (state) {
            case LISTENING:
            case PROCESSING:
                return "Text dictation";
            case READY_TO_SEND:
                return "Send or retry";
            case ERROR:
                return "Recognition failed";
            case IDLE:
            default:
                return "Choose an action";
        }
    }

    private void stopRecognition() {
        if (speechRecognizer == null) {
            return;
        }
        state = State.PROCESSING;
        invalidate();
        speechRecognizer.stopListening();
    }

    private void retryRecognition() {
        releaseRecognition();
        state = State.IDLE;
        recognitionStarted = false;
        recognizedText = null;
        invalidate();
        AndroidUtilities.runOnUIThread(this::startRecognitionIfNeeded);
    }

    private void cancelFlow() {
        releaseRecognition();
        getScreenManager().pop();
    }

    private void sendRecognizedText() {
        if (TextUtils.isEmpty(recognizedText)) {
            CarToast.makeText(getCarContext(), "Nothing to send", CarToast.LENGTH_SHORT).show();
            return;
        }
        releaseRecognition();
        composeSender.sendText(dialogId, 0, recognizedText, () -> {
            AutoUiFeedback.showOnMainScreen("Message sent");
            int guard = 0;
            while (getScreenManager().getStackSize() > 1 && guard++ < 8) {
                getScreenManager().pop();
            }
        });
    }

    private void releaseRecognition() {
        if (speechRecognizer == null) {
            return;
        }
        try {
            speechRecognizer.cancel();
        } catch (Exception ignore) {
        }
        speechRecognizer.destroy();
        speechRecognizer = null;
    }

    private final class DictationListener implements RecognitionListener {
        @Override
        public void onReadyForSpeech(Bundle params) {
        }

        @Override
        public void onBeginningOfSpeech() {
        }

        @Override
        public void onRmsChanged(float rmsdB) {
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
        }

        @Override
        public void onEndOfSpeech() {
            if (!destroyed) {
                state = State.PROCESSING;
                invalidate();
            }
        }

        @Override
        public void onError(int error) {
            if (destroyed) {
                return;
            }
            recognizedText = null;
            state = State.ERROR;
            invalidate();
        }

        @Override
        public void onResults(Bundle results) {
            if (destroyed) {
                return;
            }
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                String text = matches.get(0) == null ? null : matches.get(0).trim();
                if (!TextUtils.isEmpty(text)) {
                    recognizedText = text;
                    state = State.READY_TO_SEND;
                    invalidate();
                    return;
                }
            }
            recognizedText = null;
            state = State.ERROR;
            invalidate();
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
        }
    }
}
