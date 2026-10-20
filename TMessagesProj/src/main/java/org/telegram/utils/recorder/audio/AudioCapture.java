package org.telegram.utils.recorder.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class AudioCapture {

    interface Callback {
        void onStarted(long initialFileSize);

        void onProgress(long capturedSamples, double amplitude, long committedFileSize);

        void onStopped(long capturedSamples, long finalFileSize);

        void onError(@NonNull Exception error);
    }

    static final int SAMPLE_RATE = 48_000;
    static final int FRAME_SAMPLES = 960;
    static final int FRAME_BYTES = FRAME_SAMPLES * 2;

    private static final long OUTPUT_FLUSH_INTERVAL_MS = 100L;
    private static final long PROGRESS_INTERVAL_MS = 32L;

    private final File outputFile;
    private final File prefixFile;
    private final AudioRecordingSession.NoiseSuppression noiseSuppression;
    private final AudioRecorderDiagnostics diagnostics;
    private final Callback callback;
    private final HandlerThread thread;
    private final Handler handler;
    private final ByteBuffer readBuffer = ByteBuffer.allocateDirect(FRAME_BYTES)
            .order(ByteOrder.nativeOrder());
    private final ByteBuffer frameBuffer = ByteBuffer.allocateDirect(FRAME_BYTES)
            .order(ByteOrder.nativeOrder());

    private volatile boolean stopRequested;
    private AudioRecord audioRecord;
    private AudioRecorderNative encoder;
    private AudioNoiseSuppressor noiseSuppressor;
    private boolean finished;
    private Exception terminalFailure;
    private long capturedSamples;
    private long lastFlushMs;
    private long lastProgressMs;
    private double latestProcessedAmplitude = -1d;
    private long noiseProcessingCount;
    private long noiseProcessingTotalNs;
    private long noiseProcessingMaxNs;

    AudioCapture(
            @NonNull File outputFile,
            @Nullable File prefixFile,
            @NonNull AudioRecordingSession.NoiseSuppression noiseSuppression,
            @NonNull AudioRecorderDiagnostics diagnostics,
            @NonNull Callback callback
    ) {
        this.outputFile = outputFile;
        this.prefixFile = prefixFile;
        this.noiseSuppression = noiseSuppression;
        this.diagnostics = diagnostics;
        this.callback = callback;
        thread = new HandlerThread("AudioRecorderCapture", Process.THREAD_PRIORITY_AUDIO);
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    void start() {
        handler.post(this::startInternal);
    }

    void stop() {
        stopRequested = true;
        handler.postAtFrontOfQueue(this::finishInternal);
    }

    private void startInternal() {
        try {
            if (stopRequested) {
                finishInternal();
                return;
            }
            long encoderStartedAtMs = SystemClock.elapsedRealtime();
            encoder = AudioRecorderNative.create(outputFile, SAMPLE_RATE, prefixFile);
            if (encoder == null) {
                throw new IllegalStateException("Unable to initialize native Opus recorder");
            }
            if (noiseSuppression == AudioRecordingSession.NoiseSuppression.RNNOISE) {
                noiseSuppressor = AudioNoiseSuppressor.create();
                if (noiseSuppressor == null) {
                    diagnostics.log("RNNoise unavailable; recording unprocessed PCM");
                } else {
                    diagnostics.log("RNNoise started: sampleRate=" + SAMPLE_RATE
                            + ", captureFrameSamples=" + FRAME_SAMPLES);
                }
            }
            long initialFileSize = encoder.flush();
            diagnostics.log("Opus encoder started: prefix="
                    + (prefixFile == null ? "none" : prefixFile.getName())
                    + ", initialBytes=" + initialFileSize
                    + ", elapsedMs=" + (SystemClock.elapsedRealtime() - encoderStartedAtMs));
            if (stopRequested) {
                finishInternal();
                return;
            }

            int minimumBufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );
            if (minimumBufferSize <= 0) {
                minimumBufferSize = FRAME_BYTES * 4;
            }
            int audioRecordBufferSize = Math.max(minimumBufferSize * 2, FRAME_BYTES * 4);
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.DEFAULT,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    audioRecordBufferSize
            );
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                throw new IllegalStateException("Unable to initialize AudioRecord");
            }
            audioRecord.startRecording();
            if (audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                throw new IllegalStateException("Unable to start AudioRecord");
            }
            diagnostics.log("AudioRecord started: minBufferBytes=" + minimumBufferSize
                    + ", bufferBytes=" + audioRecordBufferSize
                    + ", sessionId=" + audioRecord.getAudioSessionId());
            lastFlushMs = lastProgressMs = SystemClock.elapsedRealtime();
            callback.onStarted(Math.max(0L, initialFileSize));
            handler.post(this::readOnce);
        } catch (Throwable error) {
            fail(error);
        }
    }

    private void readOnce() {
        if (finished) return;
        if (stopRequested) {
            finishInternal();
            return;
        }
        try {
            readBuffer.clear();
            int bytesRead = audioRecord.read(readBuffer, readBuffer.capacity());
            if (bytesRead <= 0) {
                throw new IllegalStateException("AudioRecord read failed: " + bytesRead);
            }
            readBuffer.limit(bytesRead);
            long sum = 0L;
            int sampleCount = bytesRead / 2;
            for (int offset = 0; offset + 1 < bytesRead; offset += 2) {
                int sample = readBuffer.getShort(offset);
                sum += (long) sample * sample;
            }
            capturedSamples += sampleCount;

            latestProcessedAmplitude = -1d;
            copyAndEncode(bytesRead);

            long nowMs = SystemClock.elapsedRealtime();
            long committedFileSize = -1L;
            if (nowMs - lastFlushMs >= OUTPUT_FLUSH_INTERVAL_MS) {
                committedFileSize = encoder.flush();
                lastFlushMs = nowMs;
            }
            if (nowMs - lastProgressMs >= PROGRESS_INTERVAL_MS) {
                double rawAmplitude = sampleCount == 0
                        ? 0d
                        : Math.sqrt((double) sum / sampleCount);
                double amplitude = latestProcessedAmplitude >= 0d
                        ? latestProcessedAmplitude
                        : rawAmplitude;
                callback.onProgress(capturedSamples, amplitude, committedFileSize);
                lastProgressMs = nowMs;
            } else if (committedFileSize >= 0L) {
                callback.onProgress(capturedSamples, -1d, committedFileSize);
            }
            handler.post(this::readOnce);
        } catch (Throwable error) {
            if (stopRequested) {
                finishInternal();
            } else {
                fail(error);
            }
        }
    }

    private void copyAndEncode(int bytesRead) {
        readBuffer.position(0);
        while (readBuffer.hasRemaining()) {
            int byteCount = Math.min(readBuffer.remaining(), frameBuffer.remaining());
            int previousLimit = readBuffer.limit();
            readBuffer.limit(readBuffer.position() + byteCount);
            frameBuffer.put(readBuffer);
            readBuffer.limit(previousLimit);
            if (!frameBuffer.hasRemaining()) {
                encodeFrame(FRAME_BYTES);
                frameBuffer.clear();
            }
        }
    }

    private void encodeFrame(int byteCount) {
        frameBuffer.position(0);
        frameBuffer.limit(byteCount);
        if (noiseSuppressor != null) {
            long startedAtNs = System.nanoTime();
            boolean processed = noiseSuppressor.process(frameBuffer, byteCount / 2);
            long elapsedNs = System.nanoTime() - startedAtNs;
            noiseProcessingCount++;
            noiseProcessingTotalNs += elapsedNs;
            noiseProcessingMaxNs = Math.max(noiseProcessingMaxNs, elapsedNs);
            if (!processed) {
                diagnostics.log("RNNoise processing failed; recording unprocessed PCM");
                noiseSuppressor.recycle();
                noiseSuppressor = null;
            } else {
                latestProcessedAmplitude = calculateAmplitude(frameBuffer, byteCount);
                if (noiseProcessingCount % 100L == 0L) {
                    diagnostics.log("RNNoise processing: averageMs="
                            + nsToMs(noiseProcessingTotalNs / noiseProcessingCount)
                            + ", maxMs=" + nsToMs(noiseProcessingMaxNs));
                }
            }
            frameBuffer.position(0);
            frameBuffer.limit(byteCount);
        }
        if (encoder == null || encoder.writeFrame(frameBuffer, byteCount) == 0) {
            throw new IllegalStateException("Native Opus encoding failed");
        }
    }

    private void finishInternal() {
        if (finished) return;
        finished = true;
        Exception failure = terminalFailure;
        try {
            if (audioRecord != null) {
                try {
                    if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecord.stop();
                    }
                } finally {
                    audioRecord.release();
                    audioRecord = null;
                }
            }
            if (encoder != null && frameBuffer.position() > 0) {
                int remainingBytes = frameBuffer.position();
                encodeFrame(remainingBytes);
                frameBuffer.clear();
            }
        } catch (Throwable error) {
            failure = asException(error);
        } finally {
            if (noiseSuppressor != null) {
                noiseSuppressor.recycle();
                noiseSuppressor = null;
            }
            if (encoder != null) {
                try {
                    if (!encoder.finish() && failure == null) {
                        failure = new IllegalStateException("Unable to finalize native Opus recorder");
                    }
                } catch (Throwable error) {
                    if (failure == null) failure = asException(error);
                }
                encoder.recycle();
                encoder = null;
            }
        }

        long finalSize = outputFile.isFile() ? outputFile.length() : 0L;
        if (noiseProcessingCount > 0L) {
            diagnostics.log("RNNoise summary: frames=" + noiseProcessingCount
                    + ", averageMs=" + nsToMs(noiseProcessingTotalNs / noiseProcessingCount)
                    + ", maxMs=" + nsToMs(noiseProcessingMaxNs));
        }
        diagnostics.log("capture stopped: samples=" + capturedSamples
                + ", durationMs=" + samplesToMs(capturedSamples)
                + ", bytes=" + finalSize);
        if (failure == null) {
            callback.onStopped(capturedSamples, finalSize);
        } else {
            callback.onError(failure);
        }
        thread.quitSafely();
    }

    private void fail(@NonNull Throwable error) {
        diagnostics.error("capture failed", error);
        stopRequested = true;
        terminalFailure = asException(error);
        try {
            finishInternal();
        } catch (Throwable cleanupError) {
            terminalFailure.addSuppressed(cleanupError);
        }
    }

    private static long samplesToMs(long samples) {
        return samples * 1000L / SAMPLE_RATE;
    }

    private static double calculateAmplitude(@NonNull ByteBuffer buffer, int byteCount) {
        long sum = 0L;
        int sampleCount = byteCount / 2;
        for (int offset = 0; offset + 1 < byteCount; offset += 2) {
            int sample = buffer.getShort(offset);
            sum += (long) sample * sample;
        }
        return sampleCount == 0 ? 0d : Math.sqrt((double) sum / sampleCount);
    }

    private static double nsToMs(long durationNs) {
        return durationNs / 1_000_000d;
    }

    private static Exception asException(@NonNull Throwable error) {
        return error instanceof Exception ? (Exception) error : new RuntimeException(error);
    }
}

