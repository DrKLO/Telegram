package org.telegram.messenger.auto;

import android.media.AudioAttributes;
import android.media.AudioManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.Pane;
import androidx.car.app.model.PaneTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;
import androidx.core.graphics.drawable.IconCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Locale;

final class AutoChannelPreviewScreen extends Screen {

    private static final int MAX_UNREAD_MESSAGES = 50;
    private static final String UTTERANCE_PREFIX = "channel_read_";
    private enum ReadState {
        LOADING,
        READING,
        PAUSED,
        COMPLETED,
        STOPPED,
        ERROR,
        EMPTY
    }

    private final AccountInstance accountInstance;
    private final AutoDialogPreviewFormatter previewFormatter = new AutoDialogPreviewFormatter();
    private final long dialogId;
    private final int topMessageId;
    private final String title;
    private final String fallbackMessage;
    private final CarIcon avatar;
    private final AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = focusChange -> { };

    private boolean startedReading;
    private boolean suppressUtteranceCallback;
    private int currentIndex;
    private int completedCount;
    private String statusMessage;
    private TextToSpeech textToSpeech;
    private AudioManager audioManager;
    private ArrayList<String> unreadMessages = new ArrayList<>();
    private ReadState readState = ReadState.LOADING;

    AutoChannelPreviewScreen(@NonNull CarContext carContext,
                             @NonNull AccountInstance accountInstance,
                             long dialogId,
                             int topMessageId,
                             @NonNull String title,
                             @NonNull String fallbackMessage,
                             CarIcon avatar) {
        super(carContext);
        this.accountInstance = accountInstance;
        this.dialogId = dialogId;
        this.topMessageId = topMessageId;
        this.title = title;
        this.fallbackMessage = fallbackMessage;
        this.avatar = avatar;
        this.statusMessage = "Preparing unread messages...";
        getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onDestroy(@NonNull LifecycleOwner owner) {
                shutdownTts();
            }
        });
    }

    @NonNull
    @Override
    public Template onGetTemplate() {
        if (readState == ReadState.LOADING && !startedReading) {
            startedReading = true;
            loadUnreadMessagesAndStart();
        }
        Pane.Builder paneBuilder = new Pane.Builder()
                .addRow(new Row.Builder()
                        .setTitle(buildStatusTitle())
                        .addText(buildUnreadInfoLine())
                        .addText(buildProgressLine())
                        .build())
                .addAction(new Action.Builder()
                        .setTitle(readState == ReadState.PAUSED ? "Resume" : "Pause")
                        .setFlags(Action.FLAG_PRIMARY)
                        .setOnClickListener(readState == ReadState.PAUSED ? this::resumeReading : this::pauseReading)
                        .build())
                .addAction(new Action.Builder()
                        .setTitle("Mark all read")
                        .setOnClickListener(this::markAllRead)
                        .build());
        if (avatar != null) {
            paneBuilder.setImage(avatar);
        }
        return new PaneTemplate.Builder(paneBuilder.build())
                .setTitle(title)
                .setHeaderAction(Action.BACK)
                .setActionStrip(buildActionStrip())
                .build();
    }

    private void loadUnreadMessagesAndStart() {
        accountInstance.getMessagesStorage().getStorageQueue().postRunnable(() -> {
            ArrayList<String> unreadTexts = new ArrayList<>();
            SQLiteCursor cursor = null;
            try {
                cursor = accountInstance.getMessagesStorage().getDatabase()
                        .queryFinalized(
                                "SELECT data, read_state FROM messages_v2 WHERE uid = ? AND read_state IN (0, 2) ORDER BY date ASC, mid ASC LIMIT ?",
                                dialogId, MAX_UNREAD_MESSAGES);
                while (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data == null) {
                        continue;
                    }
                    TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                    data.reuse();
                    if (message == null) {
                        continue;
                    }
                    MessageObject.setUnreadFlags(message, cursor.intValue(1));
                    MessageObject messageObject = new MessageObject(accountInstance.getCurrentAccount(), message, false, false);
                    String preview = previewFormatter.formatCompactChannelPreview(messageObject);
                    if (preview != null) {
                        preview = previewFormatter.compact(preview).trim();
                    }
                    if (preview != null && !preview.isEmpty()) {
                        unreadTexts.add(preview);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
            AndroidUtilities.runOnUIThread(() -> {
                unreadMessages = unreadTexts;
                if (unreadMessages.isEmpty() && fallbackMessage != null && !fallbackMessage.isEmpty()) {
                    unreadMessages.add(fallbackMessage);
                }
                if (unreadMessages.isEmpty()) {
                    readState = ReadState.EMPTY;
                    statusMessage = "No unread posts";
                    invalidate();
                    return;
                }
                currentIndex = 0;
                completedCount = 0;
                readState = ReadState.READING;
                statusMessage = "Preparing unread posts";
                invalidate();
                startSpeaking();
            });
        });
    }

    private void startSpeaking() {
        if (textToSpeech != null) {
            speakCurrentMessage();
            return;
        }
        FileLog.d("[AutoChannelRead] startSpeaking dialog=" + dialogId + " unreadMessages=" + unreadMessages.size());
        audioManager = (AudioManager) getCarContext().getApplicationContext().getSystemService(CarContext.AUDIO_SERVICE);
        requestAudioFocus();
        TextToSpeech[] holder = new TextToSpeech[1];
        holder[0] = new TextToSpeech(getCarContext().getApplicationContext(), status -> {
            if (status != TextToSpeech.SUCCESS || textToSpeech != holder[0]) {
                FileLog.d("[AutoChannelRead] tts init failed dialog=" + dialogId + " status=" + status);
                readState = ReadState.ERROR;
                statusMessage = "Failed to start reading";
                invalidate();
                abandonAudioFocus();
                return;
            }
            int languageResult = textToSpeech.setLanguage(Locale.getDefault());
            textToSpeech.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            FileLog.d("[AutoChannelRead] tts init ok dialog=" + dialogId + " languageResult=" + languageResult);
            textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    FileLog.d("[AutoChannelRead] utterance start dialog=" + dialogId + " id=" + utteranceId);
                }

                @Override
                public void onDone(String utteranceId) {
                    FileLog.d("[AutoChannelRead] utterance done dialog=" + dialogId + " id=" + utteranceId);
                    AndroidUtilities.runOnUIThread(() -> onUtteranceFinished(utteranceId, false));
                }

                @Override
                public void onError(String utteranceId) {
                    FileLog.d("[AutoChannelRead] utterance error dialog=" + dialogId + " id=" + utteranceId);
                    AndroidUtilities.runOnUIThread(() -> onUtteranceFinished(utteranceId, true));
                }
            });
            speakCurrentMessage();
        });
        textToSpeech = holder[0];
    }

    private void speakCurrentMessage() {
        if (textToSpeech == null) {
            return;
        }
        if (currentIndex >= unreadMessages.size()) {
            finishReadingSuccessfully();
            return;
        }
        String text = unreadMessages.get(currentIndex);
        if (text == null || text.isEmpty()) {
            completedCount = Math.max(completedCount, currentIndex + 1);
            currentIndex++;
            speakCurrentMessage();
            return;
        }
        readState = ReadState.READING;
        statusMessage = "Reading";
        invalidate();
        FileLog.d("[AutoChannelRead] speak dialog=" + dialogId + " index=" + currentIndex + " textLength=" + text.length());
        int result = textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceIdFor(currentIndex));
        if (result == TextToSpeech.ERROR) {
            FileLog.d("[AutoChannelRead] speak failed dialog=" + dialogId);
            readState = ReadState.ERROR;
            statusMessage = "Reading failed";
            invalidate();
            abandonAudioFocus();
        }
    }

    private void onUtteranceFinished(@NonNull String utteranceId, boolean error) {
        if (!utteranceId.equals(utteranceIdFor(currentIndex > 0 ? currentIndex : 0)) && !utteranceId.startsWith(UTTERANCE_PREFIX)) {
            return;
        }
        if (suppressUtteranceCallback) {
            suppressUtteranceCallback = false;
            return;
        }
        if (error) {
            readState = ReadState.ERROR;
            statusMessage = "Reading failed";
            invalidate();
            abandonAudioFocus();
            return;
        }
        completedCount = Math.max(completedCount, currentIndex + 1);
        currentIndex++;
        if (currentIndex >= unreadMessages.size()) {
            finishReadingSuccessfully();
        } else if (readState == ReadState.READING) {
            speakCurrentMessage();
        }
    }

    private void pauseReading() {
        if (textToSpeech == null || readState != ReadState.READING) {
            return;
        }
        suppressUtteranceCallback = true;
        readState = ReadState.PAUSED;
        statusMessage = "Paused";
        textToSpeech.stop();
        abandonAudioFocus();
        invalidate();
    }

    private void resumeReading() {
        if (readState != ReadState.PAUSED || unreadMessages.isEmpty()) {
            return;
        }
        requestAudioFocus();
        speakCurrentMessage();
    }

    private void skipToPrevious() {
        if (unreadMessages.isEmpty() || currentIndex <= 0) {
            return;
        }
        if (textToSpeech != null) {
            suppressUtteranceCallback = true;
            textToSpeech.stop();
        }
        currentIndex--;
        completedCount = Math.min(completedCount, currentIndex);
        requestAudioFocus();
        speakCurrentMessage();
    }

    private void skipToNext() {
        if (unreadMessages.isEmpty() || currentIndex >= unreadMessages.size() - 1) {
            finishReadingSuccessfully();
            return;
        }
        if (textToSpeech != null) {
            suppressUtteranceCallback = true;
            textToSpeech.stop();
        }
        completedCount = Math.max(completedCount, currentIndex + 1);
        currentIndex++;
        requestAudioFocus();
        speakCurrentMessage();
    }

    private void markAllRead() {
        if (readState == ReadState.COMPLETED) {
            return;
        }
        if (textToSpeech != null) {
            suppressUtteranceCallback = true;
            textToSpeech.stop();
        }
        finishReadingSuccessfully();
    }

    @NonNull
    private String buildStatusTitle() {
        switch (readState) {
            case READING:
                return "Reading";
            case PAUSED:
                return "Paused";
            case COMPLETED:
                return "Completed";
            case ERROR:
                return "Reading failed";
            case EMPTY:
                return "No unread posts";
            case STOPPED:
                return "Stopped";
            case LOADING:
            default:
                return "Preparing unread messages";
        }
    }

    @NonNull
    private String buildUnreadInfoLine() {
        int total = unreadMessages.size();
        if (readState == ReadState.LOADING || readState == ReadState.EMPTY || total == 0) {
            return "Unread posts: 0";
        }
        return "Unread posts: " + total;
    }

    @NonNull
    private String buildProgressLine() {
        int total = unreadMessages.size();
        if (readState == ReadState.LOADING) {
            return "0 / 0 unread posts";
        }
        if (readState == ReadState.EMPTY || total == 0) {
            return "0 / 0 unread posts";
        }
        int shown = readState == ReadState.READING || readState == ReadState.PAUSED
                ? Math.min(total, currentIndex + 1)
                : Math.min(total, completedCount);
        return shown + " / " + total + " unread posts";
    }

    @NonNull
    private ActionStrip buildActionStrip() {
        ActionStrip.Builder builder = new ActionStrip.Builder();
        builder.addAction(new Action.Builder()
                .setIcon(new CarIcon.Builder(IconCompat.createWithResource(
                        getCarContext(),
                        org.telegram.messenger.R.drawable.ic_action_previous)).build())
                .setOnClickListener(this::skipToPrevious)
                .build());
        builder.addAction(new Action.Builder()
                .setIcon(new CarIcon.Builder(IconCompat.createWithResource(
                        getCarContext(),
                        org.telegram.messenger.R.drawable.ic_action_next)).build())
                .setOnClickListener(this::skipToNext)
                .build());
        return builder.build();
    }

    @NonNull
    private String utteranceIdFor(int index) {
        return UTTERANCE_PREFIX + index;
    }

    private void finishReadingSuccessfully() {
        if (readState == ReadState.COMPLETED) {
            return;
        }
        readState = ReadState.COMPLETED;
        completedCount = unreadMessages.size();
        currentIndex = unreadMessages.size();
        FileLog.d("[AutoChannelRead] finish dialog=" + dialogId + " topMessageId=" + topMessageId);
        statusMessage = "Unread posts marked as read";
        accountInstance.getMessagesController().markDialogAsRead(
                dialogId, topMessageId, topMessageId, 0, false, 0, 0, true, 0);
        abandonAudioFocus();
        getScreenManager().pop();
    }

    private void shutdownTts() {
        if (textToSpeech != null) {
            FileLog.d("[AutoChannelRead] shutdown dialog=" + dialogId);
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        abandonAudioFocus();
    }

    private void requestAudioFocus() {
        if (audioManager == null) {
            return;
        }
        try {
            int result = audioManager.requestAudioFocus(audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            FileLog.d("[AutoChannelRead] audio focus dialog=" + dialogId + " result=" + result);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private void abandonAudioFocus() {
        if (audioManager == null) {
            return;
        }
        try {
            audioManager.abandonAudioFocus(audioFocusChangeListener);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }
}
