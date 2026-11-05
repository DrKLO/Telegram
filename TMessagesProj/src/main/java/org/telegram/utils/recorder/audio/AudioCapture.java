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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Captures microphone PCM and encodes it into one Ogg/Opus output.
 *
 * <p>Capture and encoding run on separate threads, so the microphone starts immediately while
 * the encoder is still re-encoding a retained prefix. Captured chunks queue up on the encoder
 * thread until it is ready.</p>
 */
final class AudioCapture {

    interface Callback {
        /** Called when the microphone is recording; the encoder may still be preparing. */
        void onStarted();

        /** Called with the raw microphone RMS amplitude at most every 32 ms. */
        void onProgress(long capturedSamples, double amplitude);

        /** Called when the output file has been flushed up to the given size. */
        void onOutputCommitted(long committedFileSize);

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
    private final long prefixStartMs;
    private final long prefixEndMs;
    private final AudioRecordingSession.NoiseSuppression noiseSuppression;
    private final AudioRecorderDiagnostics diagnostics;
    private final Callback callback;
    private final HandlerThread captureThread;
    private final Handler captureHandler;
    private final HandlerThread encoderThread;
    private final Handler encoderHandler;
    private final ConcurrentLinkedQueue<ByteBuffer> chunkPool = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queuedChunks = new AtomicInteger();

    private volatile boolean stopRequested;
    private volatile boolean captureStopped;

    // Capture thread.
    private AudioRecord audioRecord;
    private long capturedSamples;
    private long lastProgressMs;

    // Encoder thread.
    private final ByteBuffer frameBuffer = ByteBuffer.allocateDirect(FRAME_BYTES)
            .order(ByteOrder.nativeOrder());
    private AudioRecorderNative encoder;
    private AudioNoiseSuppressor noiseSuppressor;
    private Exception encoderFailure;
    private boolean encodingFinished;
    private long lastFlushMs;
    private int maximumQueuedChunks;
    private long noiseProcessingCount;
    private long noiseProcessingTotalNs;
    private long noiseProcessingMaxNs;

    /**
     * @param prefixFile    previous output whose range is re-encoded before the new samples
     * @param prefixStartMs start of the retained prefix range
     * @param prefixEndMs   end of the retained prefix range, or a negative value for the whole file
     */
    AudioCapture(
            @NonNull File outputFile,
            @Nullable File prefixFile,
            long prefixStartMs,
            long prefixEndMs,
            @NonNull AudioRecordingSession.NoiseSuppression noiseSuppression,
            @NonNull AudioRecorderDiagnostics diagnostics,
            @NonNull Callback callback
    ) {
        this.outputFile = outputFile;
        this.prefixFile = prefixFile;
        this.prefixStartMs = prefixStartMs;
        this.prefixEndMs = prefixEndMs;
        this.noiseSuppression = noiseSuppression;
        this.diagnostics = diagnostics;
        this.callback = callback;
        captureThread = new HandlerThread("AudioRecorderCapture", Process.THREAD_PRIORITY_AUDIO);
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());
        encoderThread = new HandlerThread("AudioRecorderEncoder", Process.THREAD_PRIORITY_DEFAULT);
        encoderThread.start();
        encoderHandler = new Handler(encoderThread.getLooper());
    }

    void start() {
        encoderHandler.post(this::startEncoder);
        captureHandler.post(this::startCapture);
    }

    void stop() {
        stopRequested = true;
        if (!captureStopped) {
            captureHandler.postAtFrontOfQueue(() -> stopCapture(null));
        }
    }

    private void startCapture() {
        try {
            if (stopRequested) {
                stopCapture(null);
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
            lastProgressMs = SystemClock.elapsedRealtime();
            callback.onStarted();
            captureHandler.post(this::readOnce);
        } catch (Throwable error) {
            stopCapture(asException(error));
        }
    }

    private void readOnce() {
        if (captureStopped) return;
        if (stopRequested) {
            stopCapture(null);
            return;
        }
        ByteBuffer chunk = obtainChunk();
        boolean queued = false;
        try {
            int bytesRead = audioRecord.read(chunk, chunk.capacity());
            if (bytesRead <= 0) {
                throw new IllegalStateException("AudioRecord read failed: " + bytesRead);
            }
            chunk.limit(bytesRead);
            long sum = 0L;
            int sampleCount = bytesRead / 2;
            for (int offset = 0; offset + 1 < bytesRead; offset += 2) {
                int sample = chunk.getShort(offset);
                sum += (long) sample * sample;
            }
            capturedSamples += sampleCount;
            queuedChunks.incrementAndGet();
            encoderHandler.post(() -> encodeChunk(chunk));
            queued = true;

            long nowMs = SystemClock.elapsedRealtime();
            if (nowMs - lastProgressMs >= PROGRESS_INTERVAL_MS) {
                double amplitude = sampleCount == 0 ? 0d : Math.sqrt((double) sum / sampleCount);
                callback.onProgress(capturedSamples, amplitude);
                lastProgressMs = nowMs;
            }
            captureHandler.post(this::readOnce);
        } catch (Throwable error) {
            if (!queued) recycleChunk(chunk);
            stopCapture(stopRequested ? null : asException(error));
        }
    }

    private void stopCapture(@Nullable Exception failure) {
        if (captureStopped) return;
        captureStopped = true;
        stopRequested = true;
        if (failure != null) {
            diagnostics.error("capture failed", failure);
        }
        if (audioRecord != null) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
            } catch (Throwable error) {
                if (failure == null) failure = asException(error);
            } finally {
                audioRecord.release();
                audioRecord = null;
            }
        }
        long samples = capturedSamples;
        Exception captureFailure = failure;
        encoderHandler.post(() -> finishEncoding(samples, captureFailure));
        captureThread.quitSafely();
    }

    private void startEncoder() {
        try {
            long startedAtMs = SystemClock.elapsedRealtime();
            encoder = AudioRecorderNative.create(
                    outputFile,
                    SAMPLE_RATE,
                    prefixFile,
                    prefixStartMs,
                    prefixEndMs
            );
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
                    + (prefixFile == null ? "none" : prefixFile.getName()
                    + " " + prefixStartMs + ".." + prefixEndMs)
                    + ", initialBytes=" + initialFileSize
                    + ", queuedChunks=" + queuedChunks.get()
                    + ", elapsedMs=" + (SystemClock.elapsedRealtime() - startedAtMs));
            lastFlushMs = SystemClock.elapsedRealtime();
            if (initialFileSize > 0L) {
                callback.onOutputCommitted(initialFileSize);
            }
        } catch (Throwable error) {
            failEncoder(error);
        }
    }

    private void encodeChunk(@NonNull ByteBuffer chunk) {
        int queued = queuedChunks.getAndDecrement();
        try {
            if (encoderFailure != null || encodingFinished) return;
            maximumQueuedChunks = Math.max(maximumQueuedChunks, queued);
            copyAndEncode(chunk);
            long nowMs = SystemClock.elapsedRealtime();
            if (nowMs - lastFlushMs >= OUTPUT_FLUSH_INTERVAL_MS) {
                long committedFileSize = encoder.flush();
                lastFlushMs = nowMs;
                if (committedFileSize >= 0L) {
                    callback.onOutputCommitted(committedFileSize);
                }
            }
        } catch (Throwable error) {
            failEncoder(error);
        } finally {
            recycleChunk(chunk);
        }
    }

    private void failEncoder(@NonNull Throwable error) {
        if (encoderFailure != null) return;
        diagnostics.error("encoding failed", error);
        encoderFailure = asException(error);
        stopRequested = true;
    }

    private void copyAndEncode(@NonNull ByteBuffer chunk) {
        chunk.position(0);
        while (chunk.hasRemaining()) {
            int byteCount = Math.min(chunk.remaining(), frameBuffer.remaining());
            int previousLimit = chunk.limit();
            chunk.limit(chunk.position() + byteCount);
            frameBuffer.put(chunk);
            chunk.limit(previousLimit);
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
            } else if (noiseProcessingCount % 100L == 0L) {
                diagnostics.log("RNNoise processing: averageMs="
                        + nsToMs(noiseProcessingTotalNs / noiseProcessingCount)
                        + ", maxMs=" + nsToMs(noiseProcessingMaxNs));
            }
            frameBuffer.position(0);
            frameBuffer.limit(byteCount);
        }
        if (encoder == null || encoder.writeFrame(frameBuffer, byteCount) == 0) {
            throw new IllegalStateException("Native Opus encoding failed");
        }
    }

    private void finishEncoding(long samples, @Nullable Exception captureFailure) {
        if (encodingFinished) return;
        encodingFinished = true;
        Exception failure = encoderFailure != null ? encoderFailure : captureFailure;
        try {
            if (failure == null && encoder != null && frameBuffer.position() > 0) {
                encodeFrame(frameBuffer.position());
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
        diagnostics.log("capture stopped: samples=" + samples
                + ", durationMs=" + samplesToMs(samples)
                + ", bytes=" + finalSize
                + ", maxQueuedChunks=" + maximumQueuedChunks);
        if (failure == null) {
            callback.onStopped(samples, finalSize);
        } else {
            callback.onError(failure);
        }
        encoderThread.quitSafely();
        chunkPool.clear();
    }

    @NonNull
    private ByteBuffer obtainChunk() {
        ByteBuffer chunk = chunkPool.poll();
        if (chunk == null) {
            chunk = ByteBuffer.allocateDirect(FRAME_BYTES).order(ByteOrder.nativeOrder());
        }
        chunk.clear();
        return chunk;
    }

    private void recycleChunk(@NonNull ByteBuffer chunk) {
        if (!encodingFinished) {
            chunkPool.offer(chunk);
        }
    }

    private static long samplesToMs(long samples) {
        return samples * 1000L / SAMPLE_RATE;
    }

    private static double nsToMs(long durationNs) {
        return durationNs / 1_000_000d;
    }

    private static Exception asException(@NonNull Throwable error) {
        return error instanceof Exception ? (Exception) error : new RuntimeException(error);
    }
}
