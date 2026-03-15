package org.telegram.messenger.auto;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioRecord;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.media.CarAudioRecord;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

final class AutoVoiceRecorder {

    private static final int SAMPLE_RATE = CarAudioRecord.AUDIO_CONTENT_SAMPLING_RATE;
    private static final int INPUT_BUFFER_SIZE = CarAudioRecord.AUDIO_CONTENT_BUFFER_SIZE;
    private static final int ENCODER_FRAME_BYTES = 1920;
    private static final long MAX_RECORD_DURATION_MS = 60_000L;

    interface RecordingCallback {
        void onRecordingStarted();
        void onRecordingSending();
        void onRecordingSent();
        void onRecordingDiscarded();
        void onRecordingError(String error);
    }

    private final CarContext carContext;
    private final int currentAccount;
    private final long dialogId;
    private final Handler autoStopHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean encoderStopped = new AtomicBoolean(false);

    private CarAudioRecord carAudioRecord;
    private volatile boolean recording;
    private volatile boolean stopAndSendRequested;
    private volatile boolean cancelRequested;
    private volatile boolean sendStarted;
    private File recordingFile;
    private Thread recordThread;
    private long recordStartTime;
    private long recordedBytes;
    private RecordingCallback callback;
    private TLRPC.TL_document audioDocument;
    private ByteBuffer fileBuffer;

    AutoVoiceRecorder(@NonNull CarContext carContext, int currentAccount, long dialogId) {
        this.carContext = carContext;
        this.currentAccount = currentAccount;
        this.dialogId = dialogId;
    }

    void setCallback(RecordingCallback callback) {
        this.callback = callback;
    }

    void detachCallback() {
        callback = null;
    }

    void startRecording() {
        if (recording) {
            return;
        }
        if (carContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            notifyError("Mic permission denied");
            return;
        }
        try {
            carAudioRecord = CarAudioRecord.create(carContext);
            audioDocument = createAudioDocument();
            recordingFile = new File(
                    FileLoader.getDirectory(FileLoader.MEDIA_DIR_AUDIO),
                    System.currentTimeMillis() + "_" + FileLoader.getAttachFileName(audioDocument));
            FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE).mkdirs();
            if (MediaController.getInstance().startOpusEncode(recordingFile.getPath(), SAMPLE_RATE) == 0) {
                notifyError("Failed to init encoder");
                return;
            }
            fileBuffer = ByteBuffer.allocateDirect(ENCODER_FRAME_BYTES);
            fileBuffer.order(ByteOrder.nativeOrder());
            fileBuffer.rewind();

            carAudioRecord.startRecording();
            recording = true;
            recordStartTime = System.currentTimeMillis();
            recordedBytes = 0;
            autoStopHandler.postDelayed(this::stopAndSend, MAX_RECORD_DURATION_MS);
            notifyStarted();

            recordThread = new Thread(this::recordLoop, "AutoVoiceRecorder");
            recordThread.start();
        } catch (Exception e) {
            FileLog.e(e);
            notifyError(e.getMessage() != null ? e.getMessage() : "Recording failed");
        }
    }

    void stopAndSend() {
        if (!recording || cancelRequested) {
            return;
        }
        stopAndSendRequested = true;
        recording = false;
        autoStopHandler.removeCallbacksAndMessages(null);
        stopRecorder();
    }

    void cancelAndDiscard() {
        cancelRequested = true;
        stopAndSendRequested = false;
        recording = false;
        autoStopHandler.removeCallbacksAndMessages(null);
        stopRecorder();
        stopEncoderIfNeeded();
        deleteRecordingFileIfAllowed();
        notifyDiscarded();
    }

    private void recordLoop() {
        MediaController mediaController = MediaController.getInstance();
        byte[] readBuffer = new byte[INPUT_BUFFER_SIZE];

        try {
            while (recording && !cancelRequested) {
                int bytesRead = carAudioRecord.read(readBuffer, 0, readBuffer.length);
                if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION || bytesRead == AudioRecord.ERROR_BAD_VALUE) {
                    continue;
                }
                if (bytesRead == -1) {
                    if (!cancelRequested && !stopAndSendRequested && recordedBytes > 0) {
                        stopAndSendRequested = true;
                        recording = false;
                    } else {
                        cancelRequested = true;
                        notifyError("Recording cancelled");
                    }
                    break;
                }
                if (bytesRead > 0) {
                    recordedBytes += bytesRead;
                    writePcm(readBuffer, bytesRead, false, mediaController);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
            if (!cancelRequested) {
                notifyError("Recording failed");
            }
        } finally {
            if (!cancelRequested && fileBuffer != null && fileBuffer.position() > 0) {
                writePcm(null, 0, true, mediaController);
            }
            stopEncoderIfNeeded();
            if (cancelRequested) {
                deleteRecordingFileIfAllowed();
                return;
            }
            if (stopAndSendRequested) {
                finishAndSend(System.currentTimeMillis() - recordStartTime);
            } else {
                deleteRecordingFileIfAllowed();
            }
        }
    }

    private void writePcm(byte[] bytes, int length, boolean flush, MediaController mediaController) {
        if (fileBuffer == null) {
            return;
        }
        int offset = 0;
        while (offset < length) {
            int copySize = Math.min(length - offset, fileBuffer.remaining());
            fileBuffer.put(bytes, offset, copySize);
            offset += copySize;
            if (!fileBuffer.hasRemaining()) {
                fileBuffer.rewind();
                mediaController.writeOpusFrame(fileBuffer, fileBuffer.limit());
                fileBuffer.rewind();
            }
        }
        if (flush && fileBuffer.position() > 0) {
            int finalSize = fileBuffer.position();
            fileBuffer.flip();
            mediaController.writeOpusFrame(fileBuffer, finalSize);
            fileBuffer.rewind();
        }
    }

    private void finishAndSend(long durationMs) {
        if (sendStarted || cancelRequested) {
            return;
        }
        sendStarted = true;
        AndroidUtilities.runOnUIThread(() -> {
            notifySending();
            if (recordingFile == null || !recordingFile.exists() || recordingFile.length() == 0) {
                notifyError("Recording file empty");
                return;
            }

            audioDocument.date = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
            audioDocument.size = (int) recordingFile.length();

            TLRPC.TL_documentAttributeAudio audioAttribute = new TLRPC.TL_documentAttributeAudio();
            audioAttribute.voice = true;
            audioAttribute.duration = durationMs / 1000.0;
            audioAttribute.waveform = MediaController.getWaveform(recordingFile.getAbsolutePath());
            if (audioAttribute.waveform != null) {
                audioAttribute.flags |= 4;
            }
            audioDocument.attributes.clear();
            audioDocument.attributes.add(audioAttribute);

            TLRPC.TL_documentAttributeFilename filenameAttribute = new TLRPC.TL_documentAttributeFilename();
            filenameAttribute.file_name = "voice.ogg";
            audioDocument.attributes.add(filenameAttribute);

            AccountInstance.getInstance(currentAccount).getSendMessagesHelper().sendMessage(
                    SendMessagesHelper.SendMessageParams.of(
                            audioDocument, null, recordingFile.getAbsolutePath(),
                            dialogId, null, null, null, null,
                            null, null, true, 0, 0, 0, null, null, false
                    ));
            notifySent();
        });
    }

    private TLRPC.TL_document createAudioDocument() {
        TLRPC.TL_document document = new TLRPC.TL_document();
        document.file_reference = new byte[0];
        document.dc_id = Integer.MIN_VALUE;
        document.id = SharedConfig.getLastLocalId();
        document.user_id = UserConfig.getInstance(currentAccount).getClientUserId();
        document.mime_type = "audio/ogg";
        SharedConfig.saveConfig();
        return document;
    }

    private void stopRecorder() {
        try {
            if (carAudioRecord != null) {
                carAudioRecord.stopRecording();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void stopEncoderIfNeeded() {
        if (encoderStopped.compareAndSet(false, true)) {
            MediaController.getInstance().stopOpusEncode();
        }
    }

    private void deleteRecordingFileIfAllowed() {
        if (sendStarted) {
            return;
        }
        if (recordingFile != null && recordingFile.exists()) {
            recordingFile.delete();
        }
    }

    private void notifyStarted() {
        AndroidUtilities.runOnUIThread(() -> {
            if (callback != null) {
                callback.onRecordingStarted();
            }
        });
    }

    private void notifySending() {
        AndroidUtilities.runOnUIThread(() -> {
            if (callback != null) {
                callback.onRecordingSending();
            }
        });
    }

    private void notifySent() {
        AndroidUtilities.runOnUIThread(() -> {
            if (callback != null) {
                callback.onRecordingSent();
            }
        });
    }

    private void notifyDiscarded() {
        AndroidUtilities.runOnUIThread(() -> {
            if (callback != null) {
                callback.onRecordingDiscarded();
            }
        });
    }

    private void notifyError(String error) {
        AndroidUtilities.runOnUIThread(() -> {
            if (callback != null) {
                callback.onRecordingError(error);
            }
        });
    }
}
