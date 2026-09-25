package org.telegram.utils.camera.roundvideo;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTimestamp;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

final class RoundVideoCodecRecorder implements RoundVideoGlProcessor.FrameTimingListener {
    interface ErrorListener {
        void onError(@NonNull Exception error);
    }

    private static final int AUDIO_SAMPLE_RATE = 48_000;
    private static final int AUDIO_BIT_RATE = 64_000;
    private static final int AUDIO_BYTES_PER_FRAME = 2;
    private static final int AUDIO_FRAMES_PER_INPUT = 1024;
    private static final int AUDIO_BYTES_PER_INPUT =
            AUDIO_FRAMES_PER_INPUT * AUDIO_BYTES_PER_FRAME;
    private static final long CODEC_TIMEOUT_US = 10_000;
    private static final int TIMING_CAPACITY = 256;
    private static final int TIMING_LOG_INTERVAL = 30;

    private final RoundVideoMp4Writer writer;
    private final long timelineOffsetUs;
    private final int outputSize;
    private final int videoBitrate;
    private final int videoFrameRate;
    private final RoundVideoDiagnostics diagnostics;
    private final ErrorListener errorListener;
    private final AtomicBoolean errorReported = new AtomicBoolean();
    private final ArrayList<PendingAudioSample> pendingAudioSamples = new ArrayList<>();
    private final AudioTimestamp audioTimestamp = new AudioTimestamp();
    private final long[] submittedPtsUs = new long[TIMING_CAPACITY];
    private final long[] submittedTimeNs = new long[TIMING_CAPACITY];
    private MediaCodec videoCodec;
    private MediaCodec audioCodec;
    private AudioRecord audioRecord;
    private Surface videoInputSurface;
    private Thread videoDrainThread;
    private Thread audioThread;
    private Runnable audioReadyListener;
    private Runnable videoReadyListener;
    private volatile boolean stopping;
    private boolean stopFinalizationStarted;
    private volatile long stopPresentationTimeUs = Long.MAX_VALUE;
    private volatile long submittedSequence;
    private boolean prepared;
    private boolean started;
    private long timeOriginNs;
    private long videoBytes;
    private long audioBytes;
    private int videoBuffers;
    private int audioBuffers;
    private int latencySamples;
    private long latencyTotalNs;
    private long latencyMaxNs;
    private int prependHeaderSize;
    private volatile long firstVideoPtsUs;
    private long lastVideoPtsUs;
    private long firstAudioPtsUs;
    private long lastAudioPtsUs;
    private long lastKeyframePtsUs;
    private long maximumKeyframeIntervalUs;
    private int videoKeyframes;
    private int nonMonotonicVideoPts;
    private int nonMonotonicAudioPts;
    private int audioReadErrors;
    private int emptyAudioReads;
    private int droppedAudioBuffers;
    private int droppedAudioAfterStop;
    private int droppedVideoAfterStop;
    private boolean audioStartAligned;
    private long alignedAudioStartDeltaUs;

    RoundVideoCodecRecorder(
            @NonNull RoundVideoMp4Writer writer,
            long timelineOffsetUs,
            int outputSize,
            int videoBitrate,
            int videoFrameRate,
            @NonNull RoundVideoDiagnostics diagnostics,
            @NonNull ErrorListener errorListener
    ) {
        this.writer = writer;
        this.timelineOffsetUs = timelineOffsetUs;
        this.outputSize = outputSize;
        this.videoBitrate = videoBitrate;
        this.videoFrameRate = videoFrameRate;
        this.diagnostics = diagnostics;
        this.errorListener = errorListener;
    }

    @NonNull
    public synchronized Surface prepare() throws IOException {
        if (prepared) return videoInputSurface;
        resetStats();
        long startedNs = System.nanoTime();
        try {
            createVideoEncoder();
            createAudioEncoder();
            videoCodec.start();
            prepared = true;
            diagnostics.log("codecs prepared: video=" + videoCodec.getName()
                    + ", audio=" + audioCodec.getName()
                    + ", elapsedMs=" + elapsedMs(startedNs));
            return videoInputSurface;
        } catch (IOException | RuntimeException e) {
            release();
            throw e;
        }
    }

    synchronized void startRecording(
            @NonNull Runnable audioReadyListener,
            @NonNull Runnable videoReadyListener
    ) {
        if (started) return;
        if (!prepared) throw new IllegalStateException("Recorder is not prepared");
        try {
            audioCodec.start();
            audioRecord.startRecording();
            if (audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                throw new IllegalStateException("Unable to start AudioRecord");
            }
            timeOriginNs = SystemClock.elapsedRealtimeNanos();
            this.audioReadyListener = audioReadyListener;
            this.videoReadyListener = videoReadyListener;
            started = true;
            diagnostics.log("audio and video recording started: timeOriginNs=" + timeOriginNs
                    + ", timelineOffsetUs=" + timelineOffsetUs
                    + ", audioSessionId=" + audioRecord.getAudioSessionId());
            videoDrainThread = new Thread(this::drainVideo, "RoundVideoVideoEncoder");
            audioThread = new Thread(this::captureAndEncodeAudio, "RoundVideoAudioEncoder");
            videoDrainThread.start();
            audioThread.start();
        } catch (RuntimeException e) {
            release();
            throw e;
        }
    }

    synchronized boolean isStarted() { return started; }

    long getTimeOriginNs() { return timeOriginNs; }

    @Override
    public void onFrameSubmitted(long presentationTimeUs, long submittedNs) {
        long sequence = submittedSequence;
        int index = (int) (sequence % TIMING_CAPACITY);
        submittedPtsUs[index] = presentationTimeUs;
        submittedTimeNs[index] = submittedNs;
        submittedSequence = sequence + 1;
        notifyVideoReady();
    }

    void stop() {
        requestStop();
        synchronized (this) {
            if (!prepared || stopFinalizationStarted) return;
            if (!started) {
                release();
                return;
            }
            stopFinalizationStarted = true;
        }
        try {
            videoCodec.signalEndOfInputStream();
        } catch (IllegalStateException e) {
            diagnostics.error("video encoder EOS failed", e);
        }
        join(videoDrainThread);
        join(audioThread);
        logSummary();
        release();
    }

    long requestStop() {
        AudioRecord record;
        long cutoffUs;
        synchronized (this) {
            if (!prepared || !started) return Long.MAX_VALUE;
            if (stopping) return stopPresentationTimeUs;
            stopping = true;
            cutoffUs = Math.max(0,
                    (SystemClock.elapsedRealtimeNanos() - timeOriginNs) / 1000L);
            stopPresentationTimeUs = cutoffUs;
            record = audioRecord;
        }
        try {
            if (record != null
                    && record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                record.stop();
            }
        } catch (IllegalStateException e) {
            diagnostics.error("AudioRecord stop failed", e);
        }
        diagnostics.log("recording stop boundary: presentationTimeUs=" + cutoffUs);
        return cutoffUs;
    }

    private void resetStats() {
        stopping = false;
        stopFinalizationStarted = false;
        stopPresentationTimeUs = Long.MAX_VALUE;
        timeOriginNs = 0;
        videoBytes = audioBytes = 0;
        videoBuffers = audioBuffers = 0;
        submittedSequence = 0;
        latencySamples = 0;
        latencyTotalNs = latencyMaxNs = 0;
        firstVideoPtsUs = lastVideoPtsUs = Long.MIN_VALUE;
        firstAudioPtsUs = lastAudioPtsUs = Long.MIN_VALUE;
        lastKeyframePtsUs = Long.MIN_VALUE;
        maximumKeyframeIntervalUs = 0;
        videoKeyframes = 0;
        nonMonotonicVideoPts = nonMonotonicAudioPts = 0;
        audioReadErrors = emptyAudioReads = 0;
        droppedAudioBuffers = 0;
        droppedAudioAfterStop = 0;
        droppedVideoAfterStop = 0;
        audioStartAligned = false;
        alignedAudioStartDeltaUs = Long.MIN_VALUE;
        audioReadyListener = null;
        videoReadyListener = null;
        pendingAudioSamples.clear();
    }

    private void createVideoEncoder() throws IOException {
        MediaFormat format = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                outputSize,
                outputSize
        );
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, videoBitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, videoFrameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        videoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        MediaCodecInfo.VideoCapabilities capabilities = videoCodec.getCodecInfo()
                .getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
                .getVideoCapabilities();
        if (!capabilities.isSizeSupported(outputSize, outputSize)
                || !capabilities.areSizeAndRateSupported(
                outputSize,
                outputSize,
                videoFrameRate
        )) {
            throw new IOException("Video encoder does not support "
                    + outputSize + "x" + outputSize + " at " + videoFrameRate + " fps");
        }
        diagnostics.log("video encoder configure: codec=" + videoCodec.getName()
                + ", format=" + format);
        videoCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        videoInputSurface = videoCodec.createInputSurface();
    }

    private void createAudioEncoder() throws IOException {
        int minBufferSize = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBufferSize <= 0) throw new IllegalStateException("Unsupported audio recording configuration");
        int audioBufferSize = Math.max(minBufferSize * 4, 16_384);
        MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, AUDIO_SAMPLE_RATE, 1);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, audioBufferSize);
        audioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        diagnostics.log("audio encoder configure: codec=" + audioCodec.getName()
                + ", format=" + format
                + ", minBufferSize=" + minBufferSize
                + ", audioBufferSize=" + audioBufferSize);
        audioCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.CAMCORDER, AUDIO_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, audioBufferSize);
        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("Unable to initialize AudioRecord");
    }

    private void drainVideo() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        try {
            boolean ended = false;
            while (!ended) {
                int index = videoCodec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US);
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    setTrackFormat(true, videoCodec.getOutputFormat());
                } else if (index >= 0) {
                    if (isMediaBuffer(info)) {
                        if (info.presentationTimeUs < stopPresentationTimeUs) {
                            videoBuffers++;
                            videoBytes += info.size;
                            recordSampleStats(true, info);
                            recordCodecLatency(info.presentationTimeUs);
                            writeSample(true, videoCodec.getOutputBuffer(index), info);
                        } else {
                            droppedVideoAfterStop++;
                        }
                    }
                    ended = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    videoCodec.releaseOutputBuffer(index, false);
                }
            }
        } catch (RuntimeException e) {
            if (!stopping) reportError(e);
            else diagnostics.error("video drain failed while stopping", e);
        }
    }

    private void captureAndEncodeAudio() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long submittedFrames = 0;
        long audioBasePtsUs = Long.MIN_VALUE;
        boolean inputEnded = false;
        boolean outputEnded = false;
        try {
            while (!outputEnded) {
                outputEnded = drainAudioOutput(info, inputEnded ? CODEC_TIMEOUT_US : 0);
                if (outputEnded) break;
                int index = audioCodec.dequeueInputBuffer(CODEC_TIMEOUT_US);
                if (index < 0) continue;
                if (stopping) {
                    long ptsUs = audioBasePtsUs == Long.MIN_VALUE ? 0 : audioBasePtsUs + framesToDurationUs(submittedFrames);
                    audioCodec.queueInputBuffer(index, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    inputEnded = true;
                    continue;
                }
                ByteBuffer buffer = audioCodec.getInputBuffer(index);
                if (buffer == null) {
                    audioCodec.queueInputBuffer(index, 0, 0, 0, 0);
                    continue;
                }
                buffer.clear();
                int bytesRead = audioRecord.read(
                        buffer,
                        Math.min(buffer.remaining(), AUDIO_BYTES_PER_INPUT)
                );
                if (bytesRead <= 0) {
                    if (bytesRead < 0) {
                        audioReadErrors++;
                        if (bytesRead == AudioRecord.ERROR_DEAD_OBJECT || audioReadErrors >= 3) {
                            throw new IllegalStateException("AudioRecord read failed: " + bytesRead);
                        }
                    } else {
                        emptyAudioReads++;
                    }
                    long ptsUs = audioBasePtsUs == Long.MIN_VALUE
                            ? 0
                            : audioBasePtsUs + framesToDurationUs(submittedFrames);
                    audioCodec.queueInputBuffer(index, 0, 0, ptsUs, 0);
                    continue;
                }
                int framesRead = bytesRead / AUDIO_BYTES_PER_FRAME;
                if (audioBasePtsUs == Long.MIN_VALUE) {
                    audioBasePtsUs = resolveAudioBasePtsUs(framesRead);
                    diagnostics.log("first audio input: basePtsUs=" + audioBasePtsUs
                            + ", frames=" + framesRead
                            + ", timestampSource="
                            + (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                            ? "AudioTimestamp" : "read completion"));
                }
                long ptsUs = audioBasePtsUs + framesToDurationUs(submittedFrames);
                audioCodec.queueInputBuffer(index, 0, framesRead * AUDIO_BYTES_PER_FRAME, ptsUs, 0);
                submittedFrames += framesRead;
                notifyAudioReady();
            }
        } catch (RuntimeException e) {
            if (!stopping) reportError(e);
            else diagnostics.error("audio capture failed while stopping", e);
        }
    }

    private boolean drainAudioOutput(MediaCodec.BufferInfo info, long timeoutUs) {
        boolean ended = false;
        int index = audioCodec.dequeueOutputBuffer(info, timeoutUs);
        while (index >= 0) {
            if (isMediaBuffer(info)) {
                writeOrBufferAudioSample(audioCodec.getOutputBuffer(index), info);
            }
            ended |= (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            audioCodec.releaseOutputBuffer(index, false);
            index = audioCodec.dequeueOutputBuffer(info, 0);
        }
        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) setTrackFormat(false, audioCodec.getOutputFormat());
        if (ended && !audioStartAligned && firstVideoPtsUs != Long.MIN_VALUE) {
            flushPendingAudioSamples(firstVideoPtsUs, true);
        }
        return ended;
    }

    private void notifyAudioReady() {
        Runnable listener;
        synchronized (this) {
            if (audioReadyListener == null || stopping) return;
            listener = audioReadyListener;
            audioReadyListener = null;
        }
        diagnostics.log("audio capture ready; waiting for common A/V start frame");
        listener.run();
    }

    private void notifyVideoReady() {
        Runnable listener;
        synchronized (this) {
            if (videoReadyListener == null || stopping) return;
            listener = videoReadyListener;
            videoReadyListener = null;
        }
        diagnostics.log("first synchronized video frame submitted");
        listener.run();
    }

    private long resolveAudioBasePtsUs(int firstFramesRead) {
        long readCompletionBaseUs = Math.max(
                0,
                (SystemClock.elapsedRealtimeNanos() - timeOriginNs) / 1000L
                        - framesToDurationUs(firstFramesRead)
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && audioRecord.getTimestamp(
                audioTimestamp,
                AudioTimestamp.TIMEBASE_BOOTTIME
        ) == AudioRecord.SUCCESS) {
            long timestampBaseUs = Math.max(
                    0,
                    (audioTimestamp.nanoTime - timeOriginNs) / 1000L
                            - framesToDurationUs(audioTimestamp.framePosition)
            );
            diagnostics.log("audio timestamp alignment: framePosition="
                    + audioTimestamp.framePosition
                    + ", timestampDeltaUs="
                    + (audioTimestamp.nanoTime - timeOriginNs) / 1000L
                    + ", timestampBaseUs=" + timestampBaseUs
                    + ", readCompletionBaseUs=" + readCompletionBaseUs);
            return timestampBaseUs;
        }
        diagnostics.log("audio timestamp unavailable: readCompletionBaseUs="
                + readCompletionBaseUs);
        return readCompletionBaseUs;
    }

    private void writeOrBufferAudioSample(ByteBuffer source, MediaCodec.BufferInfo info) {
        if (source == null) return;
        if (info.presentationTimeUs >= stopPresentationTimeUs) {
            droppedAudioBuffers++;
            droppedAudioAfterStop++;
            return;
        }
        if (audioStartAligned) {
            writeAudioSample(source, info);
            return;
        }
        bufferAudioSample(source, info);
        long videoStartUs = firstVideoPtsUs;
        if (videoStartUs != Long.MIN_VALUE && info.presentationTimeUs >= videoStartUs) {
            flushPendingAudioSamples(videoStartUs, false);
        }
    }

    private void bufferAudioSample(ByteBuffer source, MediaCodec.BufferInfo info) {
        ByteBuffer copy = ByteBuffer.allocateDirect(info.size);
        int oldPosition = source.position();
        int oldLimit = source.limit();
        try {
            source.position(info.offset);
            source.limit(info.offset + info.size);
            copy.put(source).flip();
        } finally {
            source.limit(oldLimit);
            source.position(oldPosition);
        }
        MediaCodec.BufferInfo copyInfo = new MediaCodec.BufferInfo();
        copyInfo.set(0, info.size, info.presentationTimeUs, info.flags);
        pendingAudioSamples.add(new PendingAudioSample(copy, copyInfo));
    }

    private void flushPendingAudioSamples(long videoStartUs, boolean force) {
        if (pendingAudioSamples.isEmpty()) return;
        int nextIndex = -1;
        for (int i = 0; i < pendingAudioSamples.size(); i++) {
            if (pendingAudioSamples.get(i).info.presentationTimeUs >= videoStartUs) {
                nextIndex = i;
                break;
            }
        }
        if (nextIndex < 0) {
            if (!force) return;
            nextIndex = pendingAudioSamples.size() - 1;
        } else if (nextIndex > 0) {
            long previousDelta = videoStartUs
                    - pendingAudioSamples.get(nextIndex - 1).info.presentationTimeUs;
            long nextDelta = pendingAudioSamples.get(nextIndex).info.presentationTimeUs
                    - videoStartUs;
            if (previousDelta <= nextDelta) nextIndex--;
        }
        for (int i = 0; i < nextIndex; i++) {
            droppedAudioBuffers++;
        }
        PendingAudioSample first = pendingAudioSamples.get(nextIndex);
        alignedAudioStartDeltaUs = first.info.presentationTimeUs - videoStartUs;
        for (int i = nextIndex; i < pendingAudioSamples.size(); i++) {
            PendingAudioSample sample = pendingAudioSamples.get(i);
            writeAudioSample(sample.data, sample.info);
        }
        pendingAudioSamples.clear();
        audioStartAligned = true;
        diagnostics.log("A/V start aligned: videoPtsUs=" + videoStartUs
                + ", audioPtsUs=" + first.info.presentationTimeUs
                + ", deltaUs=" + alignedAudioStartDeltaUs);
    }

    private void writeAudioSample(ByteBuffer source, MediaCodec.BufferInfo info) {
        audioBuffers++;
        audioBytes += info.size;
        recordSampleStats(false, info);
        writeSample(false, source, info);
    }

    private void setTrackFormat(boolean video, MediaFormat format) {
        try {
            if (video
                    && format.containsKey(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES)
                    && format.getInteger(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES) == 1) {
                ByteBuffer sps = format.getByteBuffer("csd-0");
                ByteBuffer pps = format.getByteBuffer("csd-1");
                prependHeaderSize = (sps == null ? 0 : sps.remaining())
                        + (pps == null ? 0 : pps.remaining());
            }
            writer.setTrackFormat(video, format);
            diagnostics.log((video ? "video" : "audio") + " output format: " + format);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void writeSample(boolean video, ByteBuffer source, MediaCodec.BufferInfo sourceInfo) {
        if (source == null) return;
        try {
            if (video
                    && prependHeaderSize > 0
                    && (sourceInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                    && sourceInfo.size > prependHeaderSize) {
                sourceInfo.offset += prependHeaderSize;
                sourceInfo.size -= prependHeaderSize;
            }
            writer.writeSample(video, source, sourceInfo, timelineOffsetUs);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private void reportError(@NonNull Exception error) {
        diagnostics.error("codec error" + codecDiagnostic(error), error);
        if (errorReported.compareAndSet(false, true)) errorListener.onError(error);
    }

    private void recordCodecLatency(long ptsUs) {
        long sequence = submittedSequence;
        long first = Math.max(0, sequence - TIMING_CAPACITY);
        for (long i = sequence - 1; i >= first; i--) {
            int index = (int) (i % TIMING_CAPACITY);
            if (submittedPtsUs[index] != ptsUs) continue;
            long latencyNs = System.nanoTime() - submittedTimeNs[index];
            latencySamples++;
            latencyTotalNs += latencyNs;
            latencyMaxNs = Math.max(latencyMaxNs, latencyNs);
            if (latencySamples % TIMING_LOG_INTERVAL == 0) {
                diagnostics.log("codec latency: average="
                        + latencyTotalNs / (float) latencySamples / 1_000_000f
                        + " ms, max=" + latencyMaxNs / 1_000_000f + " ms");
            }
            return;
        }
    }

    private void recordSampleStats(boolean video, @NonNull MediaCodec.BufferInfo info) {
        long ptsUs = info.presentationTimeUs;
        if (video) {
            if (firstVideoPtsUs == Long.MIN_VALUE) firstVideoPtsUs = ptsUs;
            if (lastVideoPtsUs != Long.MIN_VALUE && ptsUs < lastVideoPtsUs) {
                nonMonotonicVideoPts++;
            }
            lastVideoPtsUs = Math.max(lastVideoPtsUs, ptsUs);
            if ((info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                videoKeyframes++;
                if (lastKeyframePtsUs != Long.MIN_VALUE) {
                    maximumKeyframeIntervalUs = Math.max(
                            maximumKeyframeIntervalUs,
                            ptsUs - lastKeyframePtsUs
                    );
                }
                lastKeyframePtsUs = ptsUs;
            }
        } else {
            if (firstAudioPtsUs == Long.MIN_VALUE) firstAudioPtsUs = ptsUs;
            if (lastAudioPtsUs != Long.MIN_VALUE && ptsUs < lastAudioPtsUs) {
                nonMonotonicAudioPts++;
            }
            lastAudioPtsUs = Math.max(lastAudioPtsUs, ptsUs);
        }
    }

    private void logSummary() {
        long startDeltaUs = hasAudioVideoPts() ? firstAudioPtsUs - firstVideoPtsUs : Long.MIN_VALUE;
        long endDeltaUs = hasAudioVideoPts() ? lastAudioPtsUs - lastVideoPtsUs : Long.MIN_VALUE;
        long videoDurationUs = durationUs(firstVideoPtsUs, lastVideoPtsUs);
        long audioDurationUs = durationUs(firstAudioPtsUs, lastAudioPtsUs);
        diagnostics.log("codec summary: videoBuffers=" + videoBuffers
                + ", videoBytes=" + videoBytes
                + ", videoPts=" + ptsRange(firstVideoPtsUs, lastVideoPtsUs)
                + ", actualVideoBitrate=" + bitrate(videoBytes, videoDurationUs)
                + ", keyframes=" + videoKeyframes
                + ", maxKeyframeIntervalUs=" + maximumKeyframeIntervalUs
                + ", nonMonotonicVideoPts=" + nonMonotonicVideoPts
                + ", audioBuffers=" + audioBuffers
                + ", audioBytes=" + audioBytes
                + ", audioPts=" + ptsRange(firstAudioPtsUs, lastAudioPtsUs)
                + ", actualAudioBitrate=" + bitrate(audioBytes, audioDurationUs)
                + ", nonMonotonicAudioPts=" + nonMonotonicAudioPts
                + ", avStartDeltaUs=" + optionalValue(startDeltaUs)
                + ", avEndDeltaUs=" + optionalValue(endDeltaUs)
                + ", audioReadErrors=" + audioReadErrors
                + ", emptyAudioReads=" + emptyAudioReads
                + ", droppedAudioBuffers=" + droppedAudioBuffers
                + ", droppedAudioAfterStop=" + droppedAudioAfterStop
                + ", droppedVideoAfterStop=" + droppedVideoAfterStop
                + ", stopPresentationTimeUs="
                + (stopPresentationTimeUs == Long.MAX_VALUE
                ? "n/a" : stopPresentationTimeUs)
                + ", alignedAudioStartDeltaUs="
                + optionalValue(alignedAudioStartDeltaUs)
                + ", codecLatencyAvgMs="
                + (latencySamples == 0 ? "n/a"
                : latencyTotalNs / (float) latencySamples / 1_000_000f)
                + ", codecLatencyMaxMs=" + latencyMaxNs / 1_000_000f);
    }

    private boolean hasAudioVideoPts() {
        return firstVideoPtsUs != Long.MIN_VALUE
                && lastVideoPtsUs != Long.MIN_VALUE
                && firstAudioPtsUs != Long.MIN_VALUE
                && lastAudioPtsUs != Long.MIN_VALUE;
    }

    @NonNull
    private static String ptsRange(long firstPtsUs, long lastPtsUs) {
        return firstPtsUs == Long.MIN_VALUE ? "n/a" : firstPtsUs + ".." + lastPtsUs;
    }

    @NonNull
    private static String optionalValue(long value) {
        return value == Long.MIN_VALUE ? "n/a" : String.valueOf(value);
    }

    private static long durationUs(long firstPtsUs, long lastPtsUs) {
        return firstPtsUs == Long.MIN_VALUE || lastPtsUs <= firstPtsUs
                ? 0
                : lastPtsUs - firstPtsUs;
    }

    @NonNull
    private static String bitrate(long bytes, long durationUs) {
        return durationUs <= 0 ? "n/a" : String.valueOf(bytes * 8_000_000L / durationUs);
    }

    @NonNull
    private static String codecDiagnostic(@NonNull Exception error) {
        if (error instanceof MediaCodec.CodecException) {
            MediaCodec.CodecException codecError = (MediaCodec.CodecException) error;
            return ": diagnostic=" + codecError.getDiagnosticInfo()
                    + ", recoverable=" + codecError.isRecoverable()
                    + ", transient=" + codecError.isTransient();
        }
        return "";
    }

    private static long elapsedMs(long startedNs) {
        return (System.nanoTime() - startedNs) / 1_000_000L;
    }

    private void release() {
        prepared = false;
        started = false;
        audioReadyListener = null;
        videoReadyListener = null;
        if (audioRecord != null) { audioRecord.release(); audioRecord = null; }
        if (videoInputSurface != null) { videoInputSurface.release(); videoInputSurface = null; }
        releaseCodec(videoCodec);
        releaseCodec(audioCodec);
        videoCodec = audioCodec = null;
        videoDrainThread = audioThread = null;
    }

    private static long framesToDurationUs(long frames) { return frames * 1_000_000L / AUDIO_SAMPLE_RATE; }
    private static boolean isMediaBuffer(MediaCodec.BufferInfo info) { return info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0; }

    private static void releaseCodec(MediaCodec codec) {
        if (codec == null) return;
        try { codec.stop(); } catch (IllegalStateException ignore) {}
        codec.release();
    }

    private void join(Thread thread) {
        if (thread == null) return;
        try {
            thread.join(5000);
            if (thread.isAlive()) diagnostics.log("encoder thread did not stop: " + thread.getName());
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static final class PendingAudioSample {
        final ByteBuffer data;
        final MediaCodec.BufferInfo info;

        PendingAudioSample(ByteBuffer data, MediaCodec.BufferInfo info) {
            this.data = data;
            this.info = info;
        }
    }

}
