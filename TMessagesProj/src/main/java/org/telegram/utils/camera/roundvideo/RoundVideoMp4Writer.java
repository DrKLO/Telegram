package org.telegram.utils.camera.roundvideo;

import android.media.MediaCodec;
import android.media.MediaFormat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.video.MP4Builder;
import org.telegram.messenger.video.Mp4Movie;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

final class RoundVideoMp4Writer {

    interface Listener {
        void onBytesAvailable(long availableSize);
    }

    private final File file;
    private final int outputSize;
    private final boolean includeAudio;
    private final RoundVideoDiagnostics diagnostics;
    private final Listener listener;
    private final ArrayList<PendingSample> pendingSamples = new ArrayList<>();
    private final TrackTimeline videoTimeline = new TrackTimeline(33_333L);
    private final TrackTimeline audioTimeline = new TrackTimeline(21_333L);

    private MP4Builder builder;
    private MediaFormat videoFormat;
    private MediaFormat audioFormat;
    private int videoTrack = -1;
    private int audioTrack = -1;
    private long availableSize;
    private long videoSamples;
    private long audioSamples;
    private long liveSegmentOffsetUs = Long.MIN_VALUE;
    private long liveSegmentBaseUs;
    private ByteBuffer videoConversionBuffer;
    private final MediaCodec.BufferInfo convertedVideoInfo = new MediaCodec.BufferInfo();
    private boolean suppressAvailableNotifications;
    private boolean finished;

    RoundVideoMp4Writer(
            @NonNull File file,
            int outputSize,
            boolean includeAudio,
            @NonNull RoundVideoDiagnostics diagnostics,
            @NonNull Listener listener
    ) {
        this.file = file;
        this.outputSize = outputSize;
        this.includeAudio = includeAudio;
        this.diagnostics = diagnostics;
        this.listener = listener;
    }

    @NonNull
    File getFile() {
        return file;
    }

    synchronized void setTrackFormat(boolean video, @NonNull MediaFormat format) throws IOException {
        if (finished) return;
        if (builder != null) {
            MediaFormat expected = video ? videoFormat : audioFormat;
            if ((video && !sameCodecSpecificData(expected, format, "csd-0", "csd-1"))
                    || (!video && includeAudio
                    && !sameCodecSpecificData(expected, format, "csd-0"))) {
                throw new IOException((video ? "Video" : "Audio")
                        + " codec configuration changed between segments");
            }
            return;
        }
        if (video) {
            videoFormat = format;
        } else if (includeAudio) {
            audioFormat = format;
        }
        initializeIfReady();
    }

    synchronized void writeSample(
            boolean video,
            @NonNull ByteBuffer source,
            @NonNull MediaCodec.BufferInfo sourceInfo,
            long timelineOffsetUs
    ) throws IOException {
        if (finished || !video && !includeAudio || sourceInfo.size <= 0) return;
        MediaCodec.BufferInfo info = copyInfo(video, sourceInfo, timelineOffsetUs);
        if (builder == null) {
            ByteBuffer copy = ByteBuffer.allocateDirect(sourceInfo.size);
            source.position(sourceInfo.offset);
            source.limit(sourceInfo.offset + sourceInfo.size);
            copy.put(source).flip();
            info.offset = 0;
            pendingSamples.add(new PendingSample(video, copy, info));
            return;
        }
        writeSampleInternal(video, source, info);
    }

    synchronized void createPreview(@NonNull File previewFile) throws IOException {
        requireBuilder();
        long startedNs = System.nanoTime();
        try {
            builder.finishMovie(previewFile);
            diagnostics.log("MP4 preview written: file=" + previewFile.getName()
                    + ", size=" + previewFile.length()
                    + ", elapsedMs=" + elapsedMs(startedNs));
        } catch (Exception e) {
            throw asIOException("Unable to create preview MP4", e);
        }
    }

    synchronized void finish() throws IOException {
        if (finished) return;
        requireBuilder();
        long startedNs = System.nanoTime();
        try {
            builder.finishMovie();
            finished = true;
            notifyAvailable(file.length());
            diagnostics.log("MP4 finalized: file=" + file.getName()
                    + ", size=" + file.length()
                    + ", videoSamples=" + videoSamples
                    + ", audioSamples=" + audioSamples
                    + ", elapsedMs=" + elapsedMs(startedNs));
        } catch (Exception e) {
            throw asIOException("Unable to finish MP4", e);
        }
    }

    synchronized void closeForReplacement() throws IOException {
        suppressAvailableNotifications = true;
        try {
            finish();
        } finally {
            suppressAvailableNotifications = false;
        }
    }

    private void initializeIfReady() throws IOException {
        if (videoFormat == null || includeAudio && audioFormat == null) return;
        Mp4Movie movie = new Mp4Movie();
        movie.setCacheFile(file);
        movie.setSize(outputSize, outputSize);
        try {
            builder = new MP4Builder().createMovie(movie, true, false);
            videoTrack = builder.addTrack(videoFormat, false);
            if (includeAudio) audioTrack = builder.addTrack(audioFormat, true);
            diagnostics.log("MP4 initialized: file=" + file.getName()
                    + ", output=" + outputSize + "x" + outputSize
                    + ", includeAudio=" + includeAudio
                    + ", pendingSamples=" + pendingSamples.size());
            for (int i = 0; i < pendingSamples.size(); i++) {
                PendingSample sample = pendingSamples.get(i);
                writeSampleInternal(sample.video, sample.data, sample.info);
            }
            pendingSamples.clear();
        } catch (Exception e) {
            throw asIOException("Unable to initialize MP4", e);
        }
    }

    private void writeSampleInternal(
            boolean video,
            @NonNull ByteBuffer source,
            @NonNull MediaCodec.BufferInfo info
    ) throws IOException {
        try {
            ByteBuffer data = source;
            MediaCodec.BufferInfo sampleInfo = info;
            boolean writeLength = video;
            if (video) {
                int normalization = normalizeVideoSample(source, info);
                if (normalization == VIDEO_CONVERTED) {
                    data = videoConversionBuffer;
                    sampleInfo = convertedVideoInfo;
                    writeLength = false;
                } else if (normalization == VIDEO_ALREADY_AVCC) {
                    writeLength = false;
                }
            }
            long size = builder.writeSampleData(
                    video ? videoTrack : audioTrack,
                    data,
                    sampleInfo,
                    writeLength
            );
            if (video) videoSamples++;
            else audioSamples++;
            if (size > 0) notifyAvailable(size);
        } catch (Exception e) {
            throw asIOException("Unable to write MP4 sample", e);
        }
    }

    private void notifyAvailable(long size) {
        if (size <= availableSize) return;
        availableSize = size;
        if (suppressAvailableNotifications) return;
        listener.onBytesAvailable(size);
    }

    private void requireBuilder() throws IOException {
        if (builder == null) throw new IOException("MP4 tracks are not initialized");
    }

    private static long elapsedMs(long startedNs) {
        return (System.nanoTime() - startedNs) / 1_000_000L;
    }

    @NonNull
    private MediaCodec.BufferInfo copyInfo(
            boolean video,
            @NonNull MediaCodec.BufferInfo source,
            long timelineOffsetUs
    ) {
        long presentationTimeUs;
        TrackTimeline timeline = video ? videoTimeline : audioTimeline;
        if (timelineOffsetUs < 0) {
            presentationTimeUs = Math.max(0, timelineOffsetUs + source.presentationTimeUs);
        } else {
            if (liveSegmentOffsetUs != timelineOffsetUs) {
                liveSegmentOffsetUs = timelineOffsetUs;
                liveSegmentBaseUs = Math.max(
                        timelineOffsetUs,
                        Math.max(videoTimeline.nextPresentationTimeUs(),
                                audioTimeline.nextPresentationTimeUs())
                );
                videoTimeline.startSegment();
                audioTimeline.startSegment();
            }
            if (timeline.firstSourcePresentationTimeUs == Long.MIN_VALUE) {
                timeline.firstSourcePresentationTimeUs = source.presentationTimeUs;
            }
            presentationTimeUs = liveSegmentBaseUs
                    + Math.max(0, source.presentationTimeUs
                    - timeline.firstSourcePresentationTimeUs);
        }
        timeline.add(source.presentationTimeUs, presentationTimeUs);
        MediaCodec.BufferInfo result = new MediaCodec.BufferInfo();
        result.set(
                source.offset,
                source.size,
                presentationTimeUs,
                source.flags
        );
        return result;
    }

    private static final int VIDEO_ALREADY_AVCC = 0;
    private static final int VIDEO_SINGLE_ANNEX_B_NAL = 1;
    private static final int VIDEO_CONVERTED = 2;

    private int normalizeVideoSample(
            @NonNull ByteBuffer source,
            @NonNull MediaCodec.BufferInfo info
    ) {
        int end = info.offset + info.size;
        int firstStart = findStartCode(source, info.offset, end);
        if (firstStart != info.offset) {
            return VIDEO_ALREADY_AVCC;
        }
        int firstPrefix = startCodeLength(source, firstStart, end);
        int nextStart = findStartCode(source, firstStart + firstPrefix, end);
        if (firstPrefix == 4 && nextStart < 0) {
            return VIDEO_SINGLE_ANNEX_B_NAL;
        }

        int nalCount = 0;
        int outputSize = 0;
        int start = firstStart;
        while (start >= 0) {
            int prefix = startCodeLength(source, start, end);
            int nalStart = start + prefix;
            int next = findStartCode(source, nalStart, end);
            int nalEnd = next < 0 ? end : next;
            if (nalEnd > nalStart) {
                nalCount++;
                outputSize += 4 + nalEnd - nalStart;
            }
            start = next;
        }
        if (nalCount == 0) {
            return VIDEO_ALREADY_AVCC;
        }

        ensureVideoConversionCapacity(outputSize);
        videoConversionBuffer.clear();
        start = firstStart;
        int oldPosition = source.position();
        int oldLimit = source.limit();
        try {
            while (start >= 0) {
                int prefix = startCodeLength(source, start, end);
                int nalStart = start + prefix;
                int next = findStartCode(source, nalStart, end);
                int nalEnd = next < 0 ? end : next;
                if (nalEnd > nalStart) {
                    videoConversionBuffer.putInt(nalEnd - nalStart);
                    source.position(nalStart);
                    source.limit(nalEnd);
                    videoConversionBuffer.put(source);
                }
                start = next;
            }
        } finally {
            source.limit(oldLimit);
            source.position(oldPosition);
        }
        videoConversionBuffer.flip();
        convertedVideoInfo.set(
                0,
                videoConversionBuffer.remaining(),
                info.presentationTimeUs,
                info.flags
        );
        return VIDEO_CONVERTED;
    }

    private void ensureVideoConversionCapacity(int size) {
        if (videoConversionBuffer != null && videoConversionBuffer.capacity() >= size) return;
        videoConversionBuffer = ByteBuffer.allocateDirect(size);
    }

    private static int findStartCode(@NonNull ByteBuffer buffer, int start, int end) {
        for (int i = start; i + 2 < end; i++) {
            if (buffer.get(i) != 0 || buffer.get(i + 1) != 0) continue;
            if (buffer.get(i + 2) == 1) return i;
            if (i + 3 < end && buffer.get(i + 2) == 0 && buffer.get(i + 3) == 1) return i;
        }
        return -1;
    }

    private static int startCodeLength(@NonNull ByteBuffer buffer, int start, int end) {
        return start + 3 < end && buffer.get(start + 2) == 0 ? 4 : 3;
    }

    private static boolean sameCodecSpecificData(
            @Nullable MediaFormat first,
            @NonNull MediaFormat second,
            @NonNull String... keys
    ) {
        if (first == null) return false;
        for (String key : keys) {
            ByteBuffer a = first.getByteBuffer(key);
            ByteBuffer b = second.getByteBuffer(key);
            if (!sameBytes(a, b)) return false;
        }
        return true;
    }

    private static boolean sameBytes(@Nullable ByteBuffer first, @Nullable ByteBuffer second) {
        if (first == null || second == null) return first == second;
        int firstOffset = codecSpecificDataOffset(first);
        int secondOffset = codecSpecificDataOffset(second);
        int firstSize = first.limit() - firstOffset;
        int secondSize = second.limit() - secondOffset;
        if (firstSize != secondSize) return false;
        for (int i = 0; i < firstSize; i++) {
            if (first.get(firstOffset + i) != second.get(secondOffset + i)) return false;
        }
        return true;
    }

    private static int codecSpecificDataOffset(@NonNull ByteBuffer buffer) {
        if (buffer.limit() >= 4
                && buffer.get(0) == 0
                && buffer.get(1) == 0
                && buffer.get(2) == 0
                && buffer.get(3) == 1) {
            return 4;
        }
        if (buffer.limit() >= 3
                && buffer.get(0) == 0
                && buffer.get(1) == 0
                && buffer.get(2) == 1) {
            return 3;
        }
        return 0;
    }

    @NonNull
    private static IOException asIOException(@NonNull String message, @NonNull Exception error) {
        return error instanceof IOException
                ? (IOException) error
                : new IOException(message, error);
    }

    private static final class PendingSample {
        final boolean video;
        final ByteBuffer data;
        final MediaCodec.BufferInfo info;

        PendingSample(boolean video, ByteBuffer data, MediaCodec.BufferInfo info) {
            this.video = video;
            this.data = data;
            this.info = info;
        }
    }

    private static final class TrackTimeline {
        final long defaultDurationUs;
        long firstSourcePresentationTimeUs = Long.MIN_VALUE;
        long previousSourcePresentationTimeUs = Long.MIN_VALUE;
        long lastPresentationTimeUs = Long.MIN_VALUE;
        long sampleDurationUs;

        TrackTimeline(long defaultDurationUs) {
            this.defaultDurationUs = defaultDurationUs;
            sampleDurationUs = defaultDurationUs;
        }

        void startSegment() {
            firstSourcePresentationTimeUs = Long.MIN_VALUE;
            previousSourcePresentationTimeUs = Long.MIN_VALUE;
        }

        void add(long sourcePresentationTimeUs, long outputPresentationTimeUs) {
            if (previousSourcePresentationTimeUs != Long.MIN_VALUE) {
                long delta = sourcePresentationTimeUs - previousSourcePresentationTimeUs;
                if (delta > 0 && delta < 1_000_000L) sampleDurationUs = delta;
            }
            previousSourcePresentationTimeUs = sourcePresentationTimeUs;
            lastPresentationTimeUs = Math.max(lastPresentationTimeUs, outputPresentationTimeUs);
        }

        long nextPresentationTimeUs() {
            return lastPresentationTimeUs == Long.MIN_VALUE
                    ? 0
                    : lastPresentationTimeUs + Math.max(1, sampleDurationUs);
        }
    }

}
