package org.telegram.utils.recorder.round;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

/**
 * Copies a presentation-time range of a writer-produced MP4. When the range does not start on a
 * sync sample, the frames up to the next sync sample are re-encoded with the recording encoder
 * configuration and the rest is copied, so the output holds no frame from before the range.
 * If the re-encoded frames cannot share the recorded SPS/PPS, the whole range is re-encoded.
 */
final class RoundVideoRemuxer {

    static final class Result {
        final long startMs;
        final long durationMs;
        final String mode;

        Result(long startMs, long durationMs, @NonNull String mode) {
            this.startMs = startMs;
            this.durationMs = durationMs;
            this.mode = mode;
        }
    }

    /** Encoder configuration the source was recorded with. */
    static final class EncoderConfig {
        final int outputSize;
        final int videoBitrate;
        final int videoFrameRate;

        EncoderConfig(int outputSize, int videoBitrate, int videoFrameRate) {
            this.outputSize = outputSize;
            this.videoBitrate = videoBitrate;
            this.videoFrameRate = videoFrameRate;
        }
    }

    private static final int BUFFER_SIZE = 2 * 1024 * 1024;

    private RoundVideoRemuxer() {}

    @NonNull
    static Result copyRange(
            @NonNull File source,
            @NonNull RoundVideoMp4Writer writer,
            long requestedStartMs,
            long requestedEndMs,
            boolean includeAudio,
            @NonNull EncoderConfig config
    ) throws IOException {
        long startUs = Math.max(0, requestedStartMs * 1000L);
        long endUs = Math.max(startUs, requestedEndMs * 1000L);

        MediaFormat videoFormat;
        MediaFormat audioFormat = null;
        MediaExtractor formatExtractor = new MediaExtractor();
        try {
            formatExtractor.setDataSource(source.getAbsolutePath());
            int videoTrack = findTrack(formatExtractor, true);
            int audioTrack = findTrack(formatExtractor, false);
            if (videoTrack < 0 || includeAudio && audioTrack < 0) {
                throw new IOException("Source file has no required tracks");
            }
            videoFormat = formatExtractor.getTrackFormat(videoTrack);
            if (includeAudio) audioFormat = formatExtractor.getTrackFormat(audioTrack);
        } finally {
            formatExtractor.release();
        }
        writer.setEncoderFrameRate(config.videoFrameRate);
        if (audioFormat != null) writer.setTrackFormat(false, audioFormat);

        long[] syncs = findSyncSamples(source, startUs, endUs);
        long previousSyncUs = syncs[0];
        long nextSyncUs = syncs[1];
        if (previousSyncUs == Long.MIN_VALUE) {
            throw new IOException("Trim range has no video sync sample");
        }

        String mode;
        if (previousSyncUs == startUs) {
            writer.setTrackFormat(true, videoFormat);
            copyVideo(source, writer, startUs, startUs, endUs);
            mode = "copy";
        } else {
            long headEndUs = Math.min(nextSyncUs, endUs);
            RoundVideoTranscoder.CollectingSink head = new RoundVideoTranscoder.CollectingSink();
            RoundVideoTranscoder.transcode(
                    source,
                    previousSyncUs,
                    startUs,
                    headEndUs,
                    config.outputSize,
                    config.videoBitrate,
                    config.videoFrameRate,
                    head
            );
            if (head.format == null || head.samples.isEmpty()) {
                throw new IOException("Re-encoded trim start produced no frames");
            }
            boolean sharedConfig = RoundVideoMp4Writer.sameCodecSpecificData(
                    videoFormat,
                    head.format,
                    "csd-0",
                    "csd-1"
            );
            if (headEndUs >= endUs) {
                writer.setTrackFormat(true, sharedConfig ? videoFormat : head.format);
                writeCollected(writer, head, startUs);
                mode = "reencode-range-in-gop";
            } else if (sharedConfig) {
                writer.setTrackFormat(true, videoFormat);
                writeCollected(writer, head, startUs);
                copyVideo(source, writer, nextSyncUs, startUs, endUs);
                mode = "reencode-head";
            } else {
                // A fresh encoder produced other parameter sets, which cannot share one track
                // with the copied frames: re-encode everything instead.
                RoundVideoTranscoder.transcode(
                        source,
                        previousSyncUs,
                        startUs,
                        endUs,
                        config.outputSize,
                        config.videoBitrate,
                        config.videoFrameRate,
                        new RoundVideoTranscoder.Sink() {
                            @Override
                            public void onFormat(@NonNull MediaFormat format) throws IOException {
                                writer.setTrackFormat(true, format);
                            }

                            @Override
                            public void onSample(
                                    @NonNull ByteBuffer data,
                                    @NonNull MediaCodec.BufferInfo info
                            ) throws IOException {
                                writer.writeSample(true, data, info, -startUs);
                            }
                        }
                );
                mode = "reencode-all";
            }
        }
        if (includeAudio) copyAudio(source, writer, startUs, endUs);
        return new Result(startUs / 1000L, writer.getDurationMs(), mode);
    }

    static void erase(@NonNull File file) {
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw")) {
            randomAccessFile.setLength(0);
        } catch (IOException ignore) {
        }
        if (file.exists()) file.delete();
    }

    private static void writeCollected(
            @NonNull RoundVideoMp4Writer writer,
            @NonNull RoundVideoTranscoder.CollectingSink head,
            long startUs
    ) throws IOException {
        for (int i = 0; i < head.samples.size(); i++) {
            writer.writeSample(true, head.samples.get(i), head.infos.get(i), -startUs);
        }
    }

    /** Copies video samples presented in [fromUs, endUs); fromUs must be a sync sample. */
    private static void copyVideo(
            @NonNull File source,
            @NonNull RoundVideoMp4Writer writer,
            long fromUs,
            long startUs,
            long endUs
    ) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        try {
            extractor.setDataSource(source.getAbsolutePath());
            int track = findTrack(extractor, true);
            if (track < 0) throw new IOException("Source track disappeared");
            extractor.selectTrack(track);
            extractor.seekTo(fromUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            while (extractor.getSampleTime() >= 0) {
                long sampleTimeUs = extractor.getSampleTime();
                if (sampleTimeUs >= endUs) break;
                if (sampleTimeUs >= fromUs) {
                    buffer.clear();
                    int size = extractor.readSampleData(buffer, 0);
                    if (size < 0) break;
                    info.set(0, size, sampleTimeUs, extractor.getSampleFlags());
                    writer.writeSample(true, buffer, info, -startUs);
                }
                extractor.advance();
            }
        } finally {
            extractor.release();
        }
    }

    private static void copyAudio(
            @NonNull File source,
            @NonNull RoundVideoMp4Writer writer,
            long startUs,
            long endUs
    ) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        try {
            extractor.setDataSource(source.getAbsolutePath());
            int track = findTrack(extractor, false);
            if (track < 0) throw new IOException("Source track disappeared");
            extractor.selectTrack(track);
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            while (extractor.getSampleTime() >= 0 && extractor.getSampleTime() < startUs) {
                extractor.advance();
            }
            while (extractor.getSampleTime() >= 0 && extractor.getSampleTime() < endUs) {
                buffer.clear();
                int size = extractor.readSampleData(buffer, 0);
                if (size < 0) break;
                info.set(0, size, extractor.getSampleTime(), extractor.getSampleFlags());
                writer.writeSample(false, buffer, info, -startUs);
                extractor.advance();
            }
        } finally {
            extractor.release();
        }
    }

    /**
     * Returns the last video sync sample at or before startUs and the first one after it within
     * the range ({@link Long#MAX_VALUE} when there is none).
     */
    @NonNull
    private static long[] findSyncSamples(
            @NonNull File file,
            long startUs,
            long endUs
    ) throws IOException {
        long previousSyncUs = Long.MIN_VALUE;
        long nextSyncUs = Long.MAX_VALUE;
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(file.getAbsolutePath());
            int track = findTrack(extractor, true);
            if (track >= 0) {
                extractor.selectTrack(track);
                while (extractor.getSampleTime() >= 0) {
                    long sampleTimeUs = extractor.getSampleTime();
                    boolean sync = (extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0;
                    if (sampleTimeUs >= endUs) break;
                    if (sync) {
                        if (sampleTimeUs <= startUs) {
                            previousSyncUs = sampleTimeUs;
                        } else {
                            nextSyncUs = sampleTimeUs;
                            break;
                        }
                    }
                    extractor.advance();
                }
            }
        } finally {
            extractor.release();
        }
        return new long[]{previousSyncUs, nextSyncUs};
    }

    private static int findTrack(@NonNull MediaExtractor extractor, boolean video) {
        String prefix = video ? "video/" : "audio/";
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            String mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith(prefix)) return i;
        }
        return -1;
    }
}
