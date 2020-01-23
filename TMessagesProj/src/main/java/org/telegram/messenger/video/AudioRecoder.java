package org.telegram.messenger.video;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;

import java.io.IOException;
import java.nio.ByteBuffer;

public class AudioRecoder {

    private ByteBuffer[] decoderInputBuffers;
    private ByteBuffer[] decoderOutputBuffers;
    private ByteBuffer[] encoderInputBuffers;
    private ByteBuffer[] encoderOutputBuffers;
    private final MediaCodec.BufferInfo decoderOutputBufferInfo = new MediaCodec.BufferInfo();
    private final MediaCodec.BufferInfo encoderOutputBufferInfo = new MediaCodec.BufferInfo();
    private final MediaCodec decoder;
    private final MediaCodec encoder;
    private final MediaExtractor extractor;


    private boolean extractorDone = false;
    private boolean decoderDone = false;
    private boolean encoderDone = false;

    private int pendingAudioDecoderOutputBufferIndex = -1;

    private final int trackIndex;

    private final int TIMEOUT_USEC = 2500;

    public long startTime = 0;
    public long endTime = 0;

    public final MediaFormat format;

    public AudioRecoder(MediaFormat inputAudioFormat, MediaExtractor extractor, int trackIndex) throws IOException {
        this.extractor = extractor;
        this.trackIndex = trackIndex;

        decoder = MediaCodec.createDecoderByType(inputAudioFormat.getString(MediaFormat.KEY_MIME));
        decoder.configure(inputAudioFormat, null, null, 0);
        decoder.start();


        encoder = MediaCodec.createEncoderByType(MediaController.AUIDO_MIME_TYPE);
        format = MediaFormat.createAudioFormat(MediaController.AUIDO_MIME_TYPE,
                inputAudioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                inputAudioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        );
        format.setInteger(MediaFormat.KEY_BIT_RATE, 64 * 1024);
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();


        decoderInputBuffers = decoder.getInputBuffers();
        decoderOutputBuffers = decoder.getOutputBuffers();
        encoderInputBuffers = encoder.getInputBuffers();
        encoderOutputBuffers = encoder.getOutputBuffers();
    }

    public void release() {
        try {
            encoder.stop();
            decoder.stop();
            extractor.unselectTrack(trackIndex);
            extractor.release();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public boolean step(MP4Builder muxer, int audioTrackIndex) throws Exception {
        while (!extractorDone) {
            int decoderInputBufferIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
            if (decoderInputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break;
            }

            ByteBuffer decoderInputBuffer;
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                decoderInputBuffer = decoder.getInputBuffer(decoderInputBufferIndex);
            } else {
                decoderInputBuffer = decoderInputBuffers[decoderInputBufferIndex];
            }
            int size = extractor.readSampleData(decoderInputBuffer, 0);

            long presentationTime = extractor.getSampleTime();
            if (endTime > 0 && presentationTime >= endTime) {
                encoderDone = true;
                decoderOutputBufferInfo.flags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
            }
            if (size >= 0) {
                decoder.queueInputBuffer(
                        decoderInputBufferIndex,
                        0,
                        size,
                        extractor.getSampleTime(),
                        extractor.getSampleFlags());
            }

            extractorDone = !extractor.advance();
            if (extractorDone) {
                decoderInputBufferIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                decoder.queueInputBuffer(
                        decoderInputBufferIndex,
                        0,
                        0,
                        0L,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }
            break;
        }

        while (!decoderDone && pendingAudioDecoderOutputBufferIndex == -1) {
            int decoderOutputBufferIndex =
                    decoder.dequeueOutputBuffer(
                            decoderOutputBufferInfo, TIMEOUT_USEC);
            if (decoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break;
            }
            if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                decoderOutputBuffers = decoder.getOutputBuffers();
                break;
            }
            if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                break;
            }

            if ((decoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
                    != 0) {

                decoder.releaseOutputBuffer(decoderOutputBufferIndex, false);
                break;
            }
            pendingAudioDecoderOutputBufferIndex = decoderOutputBufferIndex;

            break;
        }

        while (pendingAudioDecoderOutputBufferIndex != -1) {
            int encoderInputBufferIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC);
            if (encoderInputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break;
            }

            ByteBuffer encoderInputBuffer = encoderInputBuffers[encoderInputBufferIndex];
            int size = decoderOutputBufferInfo.size;
            long presentationTime = decoderOutputBufferInfo.presentationTimeUs;
            if (size >= 0) {
                ByteBuffer decoderOutputBuffer =
                        decoderOutputBuffers[pendingAudioDecoderOutputBufferIndex]
                                .duplicate();
                decoderOutputBuffer.position(decoderOutputBufferInfo.offset);
                decoderOutputBuffer.limit(decoderOutputBufferInfo.offset + size);
                encoderInputBuffer.position(0);
                encoderInputBuffer.put(decoderOutputBuffer);
                encoder.queueInputBuffer(
                        encoderInputBufferIndex,
                        0,
                        size,
                        presentationTime,
                        decoderOutputBufferInfo.flags);
            }
            decoder.releaseOutputBuffer(pendingAudioDecoderOutputBufferIndex, false);
            pendingAudioDecoderOutputBufferIndex = -1;
            if ((decoderOutputBufferInfo.flags
                    & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                decoderDone = true;
            }
            break;
        }

        while (!encoderDone) {
            int encoderOutputBufferIndex = encoder.dequeueOutputBuffer(
                    encoderOutputBufferInfo, TIMEOUT_USEC);
            if (encoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break;
            }
            if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                encoderOutputBuffers = encoder.getOutputBuffers();
                break;
            }
            if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {

                break;
            }

            ByteBuffer encoderOutputBuffer =
                    encoderOutputBuffers[encoderOutputBufferIndex];
            if ((encoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
                    != 0) {
                encoder.releaseOutputBuffer(encoderOutputBufferIndex, false);
                break;
            }

            if (encoderOutputBufferInfo.size != 0) {
                muxer.writeSampleData(audioTrackIndex, encoderOutputBuffer, encoderOutputBufferInfo, false);
            }
            if ((encoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    != 0) {
                encoderDone = true;
            }
            encoder.releaseOutputBuffer(encoderOutputBufferIndex, false);
            break;
        }

        return encoderDone;
    }
}
