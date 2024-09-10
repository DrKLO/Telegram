package org.telegram.messenger.video;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaDataSource;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;

import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

public class AudioDecoder {
    private static final int TIMEOUT_USEC = 0000;

    private final MediaExtractor extractor;
    private MediaCodec decoder;
    private int trackIndex;

    private long startTimeUs;
    private long endTimeUs;
    private boolean loopingEnabled;

    private boolean allInputExtracted;
    private boolean decodingDone;
    private int audioIndex = -1;

    public AudioDecoder(String sourcePath) throws IOException {
        extractor = new MediaExtractor();
        extractor.setDataSource(sourcePath);
        init();
    }

    public AudioDecoder(String sourcePath, int audioIndex) throws IOException {
        extractor = new MediaExtractor();
        extractor.setDataSource(sourcePath);
        this.audioIndex = audioIndex;
        init();
    }

    private void init() throws IOException {
        selectTrack();

        MediaFormat inputFormat = extractor.getTrackFormat(trackIndex);
        decoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME));
        decoder.configure(inputFormat, null, null, 0);

        startTimeUs = 0;
        try {
            endTimeUs = extractor.getTrackFormat(trackIndex).getLong(MediaFormat.KEY_DURATION);
        } catch (Exception e) {
            FileLog.e(e);
            endTimeUs = -1;
        }
    }

    private void selectTrack() {
        trackIndex = audioIndex;
        if (trackIndex == -1) {
            int numTracks = extractor.getTrackCount();
            for (int i = 0; i < numTracks; i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    trackIndex = i;
                    break;
                }
            }
        }
        if (trackIndex < 0) {
            throw new RuntimeException("No audio track found in source");
        }
        extractor.selectTrack(trackIndex);
    }

    public MediaFormat getMediaFormat() {
        try {
            if (getOutputMediaFormat() != null) return getOutputMediaFormat();
            return getInputMediaFormat();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    public MediaFormat getInputMediaFormat() {
        try {
            return extractor.getTrackFormat(trackIndex);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    public MediaFormat getOutputMediaFormat() {
        try {
            return decoder.getOutputFormat();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    public long getDurationUs() {
        try {
            return getOutputMediaFormat().getLong(MediaFormat.KEY_DURATION);
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            return getInputMediaFormat().getLong(MediaFormat.KEY_DURATION);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return -1;
    }

    public int getSampleRate() {
        try {
            return getOutputMediaFormat().getInteger(MediaFormat.KEY_SAMPLE_RATE);
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            return getInputMediaFormat().getInteger(MediaFormat.KEY_SAMPLE_RATE);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return -1;
    }

    public int getBitrateRate() {
        try {
            return getOutputMediaFormat().getInteger(MediaFormat.KEY_BIT_RATE);
        } catch (Exception e) {}
        try {
            return getInputMediaFormat().getInteger(MediaFormat.KEY_BIT_RATE);
        } catch (Exception e) {}
        return -1;
    }

    public int getChannelCount() {
        try {
            return getOutputMediaFormat().getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        } catch (Exception e) {
            FileLog.e(e);
        }
        try {
            return getInputMediaFormat().getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return -1;
    }

    public long getStartTimeUs() {
        return startTimeUs;
    }

    public long getEndTimeUs() {
        return endTimeUs;
    }

    public boolean isLoopingEnabled() {
        return loopingEnabled;
    }

    public boolean isDecodingDone() {
        return decodingDone;
    }

    public void setStartTimeUs(long startTimeUs) {
        this.startTimeUs = startTimeUs;

        long durationUs = getDurationUs();
        if (startTimeUs < 0) this.startTimeUs = 0;
        else if (startTimeUs > durationUs) this.startTimeUs = durationUs;
    }

    public void setEndTimeUs(long endTimeUs) {
        this.endTimeUs = endTimeUs;

        long durationUs = getDurationUs();
        if (endTimeUs < 0) {
            this.endTimeUs = 0;
        } else {
            if (endTimeUs > durationUs) this.endTimeUs = durationUs;
        }
    }

    public void setLoopingEnabled(boolean loopingEnabled) {
        this.loopingEnabled = loopingEnabled;
    }

    public void start() {
        if (startTimeUs > endTimeUs) {
            throw new RuntimeException("StartTimeUs(" + startTimeUs + ") must be less than or equal to EndTimeUs(" + endTimeUs + ")");
        }

        extractor.seekTo(startTimeUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        decoder.start();

        allInputExtracted = false;
        decodingDone = false;
    }

    public DecodedBufferData decode() {

        DecodedBufferData data = new DecodedBufferData();

        boolean currentOutputDone = false;
        while (!currentOutputDone && !decodingDone) {

            if (!allInputExtracted) {
                int inBufferId = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (inBufferId >= 0) {
                    ByteBuffer buffer;
                    if (Build.VERSION.SDK_INT >= 21) {
                        buffer = decoder.getInputBuffer(inBufferId);
                    } else {
                        buffer = decoder.getInputBuffers()[inBufferId];
                    }
                    int sampleSize = extractor.readSampleData(buffer, 0);

                    if (sampleSize >= 0 && extractor.getSampleTime() <= endTimeUs) {
                        decoder.queueInputBuffer(inBufferId, 0, sampleSize, extractor.getSampleTime(), extractor.getSampleFlags());
                        extractor.advance();
                    } else {
                        if (loopingEnabled) {
                            decoder.flush();
                            extractor.seekTo(startTimeUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                        } else {
                            decoder.queueInputBuffer(inBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            allInputExtracted = true;
                        }
                    }
                }
            }


            MediaCodec.BufferInfo outputBufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = decoder.dequeueOutputBuffer(outputBufferInfo, TIMEOUT_USEC);

            if (outputBufferIndex >= 0) {
                if (Build.VERSION.SDK_INT >= 21) {
                    data.byteBuffer = decoder.getOutputBuffer(outputBufferIndex);
                } else {
                    data.byteBuffer = decoder.getOutputBuffers()[outputBufferIndex];
                }
                data.index = outputBufferIndex;
                data.size = outputBufferInfo.size;
                data.presentationTimeUs = outputBufferInfo.presentationTimeUs;
                data.flags = outputBufferInfo.flags;
                data.offset = outputBufferInfo.offset;

                // Adjusting start time
                if (data.presentationTimeUs < startTimeUs) {
                    long timeDiff = startTimeUs - data.presentationTimeUs;
                    int bytesForTimeDiff = AudioConversions.usToBytes(timeDiff, getSampleRate(), getChannelCount());
                    int position = data.byteBuffer.position() + bytesForTimeDiff;
                    if (position <= data.byteBuffer.limit()) {
                        data.byteBuffer.position(position);
                    }
                }

                // Adjusting end time
                long nextTime = data.presentationTimeUs + AudioConversions.bytesToUs(data.size, getSampleRate(), getChannelCount());
                if (nextTime > endTimeUs) {
                    int bytesToRemove = AudioConversions.usToBytes(nextTime - endTimeUs, getSampleRate(), getChannelCount());
                    if (bytesToRemove > 0) {
                        int limit = data.byteBuffer.limit() - bytesToRemove;
                        if (limit >= data.byteBuffer.position()) {
                            data.byteBuffer.limit(limit);
                        }
                    }
                }

                // Did we get all output from decoder?
                if ((outputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0)
                    decodingDone = true;
                if (data.byteBuffer.remaining() > 0) currentOutputDone = true;
            }

        }

        return data;
    }

    /**
     * @param index last decoded output buffer's index
     *              <p>
     *              This method must be called each time after decoding and and using the ByteBuffer sample
     */
    public void releaseOutputBuffer(int index) {
        decoder.releaseOutputBuffer(index, false);
    }

    public void stop() {
        decoder.stop();
        decodingDone = true;
    }

    public void release() {
        stop();
        decoder.release();
        extractor.release();
    }

    public static class DecodedBufferData {
        public ByteBuffer byteBuffer = null;
        public int index = -1;
        public int size = 0;
        public long presentationTimeUs = 0;
        public int flags = 0;
        public int offset = 0;
    }
}
