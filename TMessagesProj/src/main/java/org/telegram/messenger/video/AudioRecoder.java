package org.telegram.messenger.video;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.video.audio_input.AudioInput;
import org.telegram.messenger.video.audio_input.GeneralAudioInput;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;

public class AudioRecoder {

    private static final int BYTES_PER_SHORT = 2;
    private final int TIMEOUT_USEC = 2500;
    private final int DEFAULT_SAMPLE_RATE = 44100;
    private final int DEFAULT_BIT_RATE = 128000;
    private final int DEFAULT_CHANNEL_COUNT = 2;

    private ByteBuffer[] encoderInputBuffers;
    private ByteBuffer[] encoderOutputBuffers;
    private final MediaCodec.BufferInfo encoderOutputBufferInfo = new MediaCodec.BufferInfo();

    private final MediaCodec encoder;


    private boolean extractorDone = false;
    private boolean decoderDone = false;
    private boolean encoderInputDone = false;

    private int pendingAudioDecoderOutputBufferIndex = -1;

    private int sampleRate = DEFAULT_SAMPLE_RATE;
    private int channelCount = DEFAULT_CHANNEL_COUNT;

    public final MediaFormat format;

    AudioInput mainInput;
    ArrayList<AudioInput> audioInputs;
    private long encoderInputPresentationTimeUs = 0;
    private boolean encoderDone;
    private long totalDurationUs;

    public AudioRecoder(ArrayList<AudioInput> audioInputs, long totalDurationUs) throws IOException {
        this.audioInputs = audioInputs;
        this.totalDurationUs = totalDurationUs;
        mainInput = audioInputs.get(0);

        for (int i = 0; i < audioInputs.size(); i++) {
            if (audioInputs.get(i).getSampleRate() > sampleRate) {
                sampleRate = audioInputs.get(i).getSampleRate();
            }
        }

        encoder = MediaCodec.createEncoderByType(MediaController.AUIDO_MIME_TYPE);
        format = MediaFormat.createAudioFormat(MediaController.AUIDO_MIME_TYPE,
                sampleRate,
                channelCount
        );
        format.setInteger(MediaFormat.KEY_BIT_RATE, 64 * 1024);
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();

        encoderInputBuffers = encoder.getInputBuffers();
        encoderOutputBuffers = encoder.getOutputBuffers();

        for (int i = 0; i < audioInputs.size(); i++) {
            audioInputs.get(i).start(sampleRate, channelCount);
        }
    }

    public void release() {
        try {
            encoder.stop();
            for (int i = 0; i < audioInputs.size(); i++) {
                audioInputs.get(i).release();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public boolean step(MP4Builder muxer, int audioTrackIndex) throws Exception {
        if (!encoderInputDone) {
            int encoderBufferIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC);
            if (encoderBufferIndex >= 0) {
                if (isInputAvailable()) {
                    ShortBuffer encoderBuffer;
                    if (Build.VERSION.SDK_INT >= 21) {
                        encoderBuffer = encoder.getInputBuffer(encoderBufferIndex).asShortBuffer();
                    } else {
                        encoderBuffer = encoder.getInputBuffers()[encoderBufferIndex].asShortBuffer();
                    }
                    // mix the audios and add to encoder input buffer
                    mix(encoderBuffer);

                    encoder.queueInputBuffer(encoderBufferIndex,
                            0,
                            encoderBuffer.position() * BYTES_PER_SHORT,
                            encoderInputPresentationTimeUs,
                            MediaCodec.BUFFER_FLAG_KEY_FRAME);
                    encoderInputPresentationTimeUs += AudioConversions.shortsToUs(encoderBuffer.position(), sampleRate, channelCount);
                } else {
                    encoder.queueInputBuffer(encoderBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    encoderInputDone = true;
                }
            }
        }

        if (!encoderDone) {
            int encoderOutputBufferIndex = encoder.dequeueOutputBuffer(encoderOutputBufferInfo, TIMEOUT_USEC);
            if (encoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                return encoderDone;
            }
            if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                encoderOutputBuffers = encoder.getOutputBuffers();
            }
            if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                return encoderDone;
            }

            ByteBuffer encoderOutputBuffer =
                    encoderOutputBuffers[encoderOutputBufferIndex];
            if ((encoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
                    != 0) {
                encoder.releaseOutputBuffer(encoderOutputBufferIndex, false);
                return encoderDone;
            }

            if (encoderOutputBufferInfo.size != 0) {
                muxer.writeSampleData(audioTrackIndex, encoderOutputBuffer, encoderOutputBufferInfo, false);
            }
            if ((encoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                encoderDone = true;
            }
            encoder.releaseOutputBuffer(encoderOutputBufferIndex, false);
        }

        return encoderDone;
    }

    private void mix(ShortBuffer inputBuffer) {
        final int size = inputBuffer.remaining();

        for (int i = 0; i < size; i++) {
            if (!isInputAvailable()) break;

            boolean put = false;
            short result = 0;

            for (int j = 0; j < audioInputs.size(); j++) {
                if (!isInputAvailable()) break;

                AudioInput input = audioInputs.get(j);
                if (input.hasRemaining()) {
                    short value = input.getNext();
                    //controlling volume
                    value = (short) (value * input.getVolume());
                    result += value / audioInputs.size();
                    put = true;
                }
            }
            if (put) inputBuffer.put(result);
        }
    }

    private boolean isInputAvailable() {
        if (encoderInputPresentationTimeUs > totalDurationUs) {
            return false;
        }
        return mainInput.hasRemaining();
    }
}
