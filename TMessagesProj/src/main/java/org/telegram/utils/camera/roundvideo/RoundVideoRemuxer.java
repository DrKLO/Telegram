package org.telegram.utils.camera.roundvideo;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

final class RoundVideoRemuxer {

    static final class Result {
        final long startMs;
        final long durationMs;

        Result(long startMs, long durationMs) {
            this.startMs = startMs;
            this.durationMs = durationMs;
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
            boolean includeAudio
    ) throws IOException {
        long durationUs = getDurationUs(source);
        long requestedStartUs = Math.max(0, requestedStartMs * 1000L);
        long endUs = Math.min(durationUs, Math.max(requestedStartUs, requestedEndMs * 1000L));
        long startUs = findVideoSyncStart(source, requestedStartUs, endUs);
        if (startUs < 0 || startUs >= endUs) {
            throw new IOException("Trim range has no video sync sample");
        }

        MediaExtractor formatExtractor = new MediaExtractor();
        try {
            formatExtractor.setDataSource(source.getAbsolutePath());
            int videoTrack = findTrack(formatExtractor, true);
            int audioTrack = findTrack(formatExtractor, false);
            if (videoTrack < 0 || includeAudio && audioTrack < 0) {
                throw new IOException("Source file has no required tracks");
            }
            writer.setTrackFormat(true, formatExtractor.getTrackFormat(videoTrack));
            if (includeAudio) writer.setTrackFormat(false, formatExtractor.getTrackFormat(audioTrack));
        } finally {
            formatExtractor.release();
        }

        ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        copyTrack(source, writer, true, startUs, endUs, buffer);
        if (includeAudio) copyTrack(source, writer, false, startUs, endUs, buffer);
        return new Result(startUs / 1000L, (endUs - startUs) / 1000L);
    }

    static long getDurationMs(@NonNull File file) throws IOException {
        return getDurationUs(file) / 1000L;
    }

    static void erase(@NonNull File file) {
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw")) {
            randomAccessFile.setLength(0);
        } catch (IOException ignore) {
        }
        if (file.exists()) file.delete();
    }

    private static void copyTrack(
            @NonNull File source,
            @NonNull RoundVideoMp4Writer writer,
            boolean video,
            long startUs,
            long endUs,
            @NonNull ByteBuffer buffer
    ) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        try {
            extractor.setDataSource(source.getAbsolutePath());
            int track = findTrack(extractor, video);
            if (track < 0) throw new IOException("Source track disappeared");
            extractor.selectTrack(track);
            extractor.seekTo(startUs, video
                    ? MediaExtractor.SEEK_TO_NEXT_SYNC
                    : MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            while (extractor.getSampleTime() >= 0 && extractor.getSampleTime() < startUs) {
                extractor.advance();
            }
            while (extractor.getSampleTime() >= 0 && extractor.getSampleTime() < endUs) {
                buffer.clear();
                int size = extractor.readSampleData(buffer, 0);
                if (size < 0) break;
                info.set(0, size, extractor.getSampleTime(), extractor.getSampleFlags());
                writer.writeSample(video, buffer, info, -startUs);
                extractor.advance();
            }
        } finally {
            extractor.release();
        }
    }

    private static long findVideoSyncStart(
            @NonNull File file,
            long startUs,
            long endUs
    ) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(file.getAbsolutePath());
            int track = findTrack(extractor, true);
            if (track < 0) return -1;
            extractor.selectTrack(track);
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_NEXT_SYNC);
            long sampleTimeUs = extractor.getSampleTime();
            return sampleTimeUs >= 0 && sampleTimeUs < endUs ? sampleTimeUs : -1;
        } finally {
            extractor.release();
        }
    }

    private static long getDurationUs(@NonNull File file) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(file.getAbsolutePath());
            long durationUs = 0;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    durationUs = Math.max(durationUs, format.getLong(MediaFormat.KEY_DURATION));
                }
            }
            return durationUs;
        } finally {
            extractor.release();
        }
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
