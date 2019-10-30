package org.telegram.messenger.video;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.Utilities;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.telegram.messenger.MediaController.findTrack;

public class FfmpegVideoConvertor {

    private final ByteBuffer endOfStream;
    private final ByteBuffer errorBufer;

    private MediaCodec decoder;
    private OutputSurface outputSurface;
    private MediaExtractor extractor;

    private long endTime;
    private long startTime;
    private int videoTrackIndex;
    private boolean inputDone;
    private boolean outputDone;

    private MediaController.VideoConvertorListener callback;
    private MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

    public FfmpegVideoConvertor() {
        endOfStream = ByteBuffer.allocateDirect(3);
        endOfStream.put(0, (byte) 0x00);
        endOfStream.put(1, (byte) 0x00);
        endOfStream.put(2, (byte) 0x08);

        errorBufer = ByteBuffer.allocateDirect(3);
        errorBufer.put(0, (byte) 0x00);
        errorBufer.put(1, (byte) 0x00);
        errorBufer.put(2, (byte) 0x12);
    }

    public boolean convertVideo(String videoPath, String outPath,
                                int rotate,
                                int resultWidth, int resultHeight,
                                int framerate, int bitrate,
                                long startTime, long endTime,
                                MediaController.VideoConvertorListener callback) throws IOException {

        this.callback = callback;
        this.endTime = endTime;
        this.startTime = startTime;

        inputDone = false;
        outputDone = false;

        extractor = new MediaExtractor();
        extractor.setDataSource(videoPath);
        videoTrackIndex = findTrack(extractor, false);
        extractor.selectTrack(videoTrackIndex);
        MediaFormat videoFormat = extractor.getTrackFormat(videoTrackIndex);

        decoder = MediaCodec.createDecoderByType(videoFormat.getString(MediaFormat.KEY_MIME));
        outputSurface = new OutputSurface(resultWidth, resultHeight, rotate, true);

        decoder.configure(videoFormat, outputSurface.getSurface(), null, 0);
        decoder.start();

        if (startTime > 0) {
            extractor.seekTo(startTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        } else {
            extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        }

        int rez = Utilities.compressVideo(this,
                videoPath, outPath,
                resultWidth, resultHeight,
                framerate, bitrate,
                startTime / 1000_000f, endTime / 1000_000f
        );

        decoder.stop();
        decoder.release();
        extractor.release();

        return rez < 0;
    }


    public ByteBuffer pullDecoder() {
        ByteBuffer rgbBuf = null;
        if (inputDone && outputDone) return endOfStream;

        try {
            if (!inputDone) {
                int trackIndex;
                while (true) {
                    trackIndex = extractor.getSampleTrackIndex();
                    if (trackIndex < 0) {
                        inputDone = true;
                        break;
                    }
                    if (trackIndex == videoTrackIndex) {
                        break;
                    }
                    extractor.advance();
                }

                if (trackIndex == videoTrackIndex) {
                    int inputBufIndex = decoder.dequeueInputBuffer(0);
                    if (endTime > 0 && extractor.getSampleTime() > endTime) {
                        inputDone = true;
                    } else {
                        if (inputBufIndex >= 0) {
                            ByteBuffer inputBuf;
                            if (Build.VERSION.SDK_INT < 21) {
                                inputBuf = decoder.getInputBuffers()[inputBufIndex];
                            } else {
                                inputBuf = decoder.getInputBuffer(inputBufIndex);
                            }
                            int chunkSize = extractor.readSampleData(inputBuf, 0);

                            if (chunkSize < 0) {
                                decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputDone = true;
                            } else {
                                decoder.queueInputBuffer(inputBufIndex, 0, chunkSize, extractor.getSampleTime(), 0);
                                extractor.advance();
                            }
                        }
                    }
                }
            }

            if (!outputDone) {
                int decoderStatus = decoder.dequeueOutputBuffer(info, 2500);
                if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (inputDone) {
                        outputDone = true;
                        return endOfStream;
                    }
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {

                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {

                } else if (decoderStatus < 0) {
                    throw new RuntimeException("unexpected result from decoder.dequeueOutputBuffer: " + decoderStatus);
                } else {
                    if (info.size > 0) {
                        boolean doRender = info.presentationTimeUs > startTime;
                        decoder.releaseOutputBuffer(decoderStatus, doRender);
                        if (doRender) {
                            try {
                                outputSurface.awaitNewImage();
                                outputSurface.drawImage(false);
                                rgbBuf = outputSurface.getFrame();
                            } catch (Exception e) {

                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
            return errorBufer;
        }
        return rgbBuf;
    }

    public boolean checkConversionCanceled() {
        if (callback != null) return callback.checkConversionCanceled();
        return false;
    }

    public void updateProgress(float progress) {
        if (callback != null) callback.didWriteData(-1, progress);
    }
}
