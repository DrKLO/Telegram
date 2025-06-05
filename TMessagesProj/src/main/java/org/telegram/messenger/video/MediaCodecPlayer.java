package org.telegram.messenger.video;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MediaCodecPlayer {

    private final MediaExtractor extractor;
    private final MediaCodec codec;
    private final Surface outputSurface;

    private final int w, h, o;

    public MediaCodecPlayer(String videoPath, Surface surface) throws IOException {
        this.outputSurface = surface;
        this.extractor = new MediaExtractor();

        // Set up the extractor to read the video file
        extractor.setDataSource(videoPath);
        MediaFormat videoFormat = null;
        int videoTrackIndex = -1;

        // Find the video track
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mimeType = format.getString(MediaFormat.KEY_MIME);
            if (mimeType.startsWith("video/")) {
                videoTrackIndex = i;
                videoFormat = format;
                break;
            }
        }

        if (videoTrackIndex == -1 || videoFormat == null) {
            throw new IllegalArgumentException("No video track found in file.");
        }

        extractor.selectTrack(videoTrackIndex);
        w = videoFormat.getInteger(MediaFormat.KEY_WIDTH);
        h = videoFormat.getInteger(MediaFormat.KEY_HEIGHT);
        if (videoFormat.containsKey(MediaFormat.KEY_ROTATION)) {
            o = videoFormat.getInteger(MediaFormat.KEY_ROTATION);
        } else {
            o = 0;
        }

        codec = MediaCodec.createDecoderByType(videoFormat.getString(MediaFormat.KEY_MIME));
        codec.configure(videoFormat, surface, null, 0);
        codec.start();
    }

    public int getWidth() {
        return w;
    }

    public int getOrientedWidth() {
        return (o / 90) % 2 == 1 ? h : w;
    }

    public int getHeight() {
        return h;
    }

    public int getOrientedHeight() {
        return (o / 90) % 2 == 1 ? w : h;
    }

    public int getOrientation() {
        return o;
    }

    private boolean done;
    private boolean first = true;
    private long lastPositionUs = 0;

    public boolean ensure(long ms) {
        if (done) return false;
        final boolean first = this.first;
        this.first = false;
        final long us = ms * 1000;
        if (!first && us <= lastPositionUs) {
            return false;
        }

        if (extractor.getSampleTime() > us || first && us > 1000 * 1000) {
            extractor.seekTo(us, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        }

        while (true) {
            int inputBufferIndex = codec.dequeueInputBuffer(10000);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = codec.getInputBuffer(inputBufferIndex);
                if (inputBuffer != null) {
                    int sampleSize = extractor.readSampleData(inputBuffer, 0);
                    if (sampleSize > 0) {
                        long sampleTime = extractor.getSampleTime();
                        codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, sampleTime, extractor.getSampleFlags());
                        extractor.advance();
                    } else {
                        codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        release();
                        return false;
                    }
                }
            }

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000);
            if (outputBufferIndex >= 0) {
                if (bufferInfo.presentationTimeUs >= us - 16 * 1000) {
                    lastPositionUs = bufferInfo.presentationTimeUs;
                    codec.releaseOutputBuffer(outputBufferIndex, true);
                    return true;
                } else {
                    codec.releaseOutputBuffer(outputBufferIndex, false);
                }
            }
        }
    }

    public void release() {
        if (done) return;
        done = true;
        if (codec != null) {
            codec.stop();
            codec.release();
        }
        if (extractor != null) {
            extractor.release();
        }
    }

}
