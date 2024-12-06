package org.telegram.messenger.video;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.messenger.video.audio_input.AudioInput;
import org.telegram.messenger.video.audio_input.BlankAudioInput;
import org.telegram.messenger.video.audio_input.GeneralAudioInput;
import org.telegram.ui.Stories.recorder.CollageLayout;
import org.telegram.ui.Stories.recorder.StoryEntry;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class MediaCodecVideoConvertor {

    private Muxer muxer;
    private MediaExtractor extractor;

    private long endPresentationTime;

    private MediaController.VideoConvertorListener callback;

    private final static int PROCESSOR_TYPE_OTHER = 0;
    private final static int PROCESSOR_TYPE_QCOM = 1;
    private final static int PROCESSOR_TYPE_INTEL = 2;
    private final static int PROCESSOR_TYPE_MTK = 3;
    private final static int PROCESSOR_TYPE_SEC = 4;
    private final static int PROCESSOR_TYPE_TI = 5;

    private static final int MEDIACODEC_TIMEOUT_DEFAULT = 2500;
    private static final int MEDIACODEC_TIMEOUT_INCREASED = 22000;
    private String outputMimeType;

    public boolean convertVideo(ConvertVideoParams convertVideoParams, Handler handler) {
        if (convertVideoParams.isSticker) {
            return WebmEncoder.convert(convertVideoParams, 0);
        }
        this.callback = convertVideoParams.callback;
        return convertVideoInternal(convertVideoParams, false, 0, handler);
    }

    public long getLastFrameTimestamp() {
        return endPresentationTime;
    }

    @TargetApi(18)
    private boolean convertVideoInternal(
        ConvertVideoParams convertVideoParams,
        boolean increaseTimeout,
        int triesCount,
        Handler handler
    ) {
        String videoPath = convertVideoParams.videoPath;
        File cacheFile = convertVideoParams.cacheFile;
        int rotationValue = convertVideoParams.rotationValue;
        boolean isSecret = convertVideoParams.isSecret;
        int originalWidth = convertVideoParams.originalWidth;
        int originalHeight = convertVideoParams.originalHeight;
        int resultWidth = convertVideoParams.resultWidth;
        int resultHeight = convertVideoParams.resultHeight;
        int framerate = convertVideoParams.framerate;
        int bitrate = convertVideoParams.bitrate;
        int originalBitrate = convertVideoParams.originalBitrate;
        long startTime = convertVideoParams.startTime;
        long endTime = convertVideoParams.endTime;
        long avatarStartTime = convertVideoParams.avatarStartTime;
        boolean needCompress = convertVideoParams.needCompress;
        long duration = convertVideoParams.duration;
        MediaController.SavedFilterState savedFilterState = convertVideoParams.savedFilterState;
        String paintPath = convertVideoParams.paintPath;
        String blurPath = convertVideoParams.blurPath;
        ArrayList<VideoEditedInfo.MediaEntity> mediaEntities = convertVideoParams.mediaEntities;
        boolean isPhoto = convertVideoParams.isPhoto;
        MediaController.CropState cropState = convertVideoParams.cropState;
        boolean isRound = convertVideoParams.isRound;
        Integer gradientTopColor = convertVideoParams.gradientTopColor;
        Integer gradientBottomColor = convertVideoParams.gradientBottomColor;
        boolean muted = convertVideoParams.muted;
        float volume = convertVideoParams.volume;
        boolean isStory = convertVideoParams.isStory;
        StoryEntry.HDRInfo hdrInfo = convertVideoParams.hdrInfo;

        FileLog.d("convertVideoInternal original=" + originalWidth + "x" + originalHeight + "  result=" + resultWidth + "x" + resultHeight + " " + avatarStartTime);
        long time = System.currentTimeMillis();
        boolean error = false;
        boolean repeatWithIncreasedTimeout = false;
        boolean isAvatar = avatarStartTime >= 0;
        int videoTrackIndex = -5;
        String selectedEncoderName = null;

        final boolean isWebm = convertVideoParams.isSticker;
        boolean shouldUseHevc = isStory;
        outputMimeType = isWebm ? "video/x-vnd.on2.vp9" : shouldUseHevc ? "video/hevc" : "video/avc";

        boolean canBeBrokenEncoder = false;
        MediaCodec encoder = null;
        MediaCodec decoder = null;
        InputSurface inputSurface = null;
        OutputSurface outputSurface = null;
        MediaCodec.BufferInfo info = null;
        try {
            info = new MediaCodec.BufferInfo();

            long currentPts = 0;
            float durationS = duration / 1000f;
            int prependHeaderSize = 0;
            endPresentationTime = duration * 1000;
            checkConversionCanceled();
            AudioRecoder audioRecoder = null;

            if (isPhoto) {
                try {
                    boolean outputDone = false;
                    boolean decoderDone = false;
                    int framesCount = 0;

                    if (isAvatar) {
                        if (durationS <= 2000) {
                            bitrate = 2600000;
                        } else if (durationS <= 5000) {
                            bitrate = 2200000;
                        } else {
                            bitrate = 1560000;
                        }
                    } else if (bitrate <= 0) {
                        bitrate = 921600;
                    }

                    if (cropState == null || cropState.useMatrix == null) {
                        if (resultWidth % 16 != 0) {
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("changing width from " + resultWidth + " to " + Math.round(resultWidth / 16.0f) * 16);
                            }
                            resultWidth = Math.round(resultWidth / 16.0f) * 16;
                        }
                        if (resultHeight % 16 != 0) {
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("changing height from " + resultHeight + " to " + Math.round(resultHeight / 16.0f) * 16);
                            }
                            resultHeight = Math.round(resultHeight / 16.0f) * 16;
                        }
                    }

                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("create photo encoder " + resultWidth + " " + resultHeight + " duration = " + duration);
                    }

                    if (encoder == null) {
                        encoder = createEncoderForMimeType();
                    }

                    MediaFormat outputFormat = MediaFormat.createVideoFormat(outputMimeType, resultWidth, resultHeight);
                    outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
                    outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
                    outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
                    outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

                    selectedEncoderName = encoder.getName();
                    canBeBrokenEncoder = "c2.qti.avc.encoder".equalsIgnoreCase(selectedEncoderName);
                    FileLog.d("selected encoder " + selectedEncoderName);

                    encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                    inputSurface = new InputSurface(encoder.createInputSurface());
                    inputSurface.makeCurrent();
                    encoder.start();

                    outputSurface = new OutputSurface(inputSurface.getContext(), savedFilterState, videoPath, paintPath, blurPath, mediaEntities, cropState != null && cropState.useMatrix != null ? cropState : null, resultWidth, resultHeight, originalWidth, originalHeight, rotationValue, framerate, true, gradientTopColor, gradientBottomColor, null, convertVideoParams, handler);

                    ByteBuffer[] encoderOutputBuffers = null;
                    ByteBuffer[] encoderInputBuffers = null;
                    if (Build.VERSION.SDK_INT < 21) {
                        encoderOutputBuffers = encoder.getOutputBuffers();
                    }

                    boolean firstEncode = true;

                    checkConversionCanceled();

                    if (isWebm) {
                        muxer = new Muxer(new MediaMuxer(cacheFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM));
                    } else {
                        Mp4Movie movie = new Mp4Movie();
                        movie.setCacheFile(cacheFile);
                        movie.setRotation(0);
                        movie.setSize(resultWidth, resultHeight);
                        muxer = new Muxer(new MP4Builder().createMovie(movie, isSecret, outputMimeType.equals("video/hevc")));
                    }

                    int audioTrackIndex = -1;
                    boolean audioEncoderDone = true;
                    if (!convertVideoParams.soundInfos.isEmpty()) {
                        audioEncoderDone = false;
                        ArrayList<AudioInput> audioInputs = new ArrayList<>();
                        long totalDuration = duration * 1000;
                        BlankAudioInput mainInput = new BlankAudioInput(totalDuration);
                        audioInputs.add(mainInput);
                        applyAudioInputs(convertVideoParams.soundInfos, audioInputs);

                        audioRecoder = new AudioRecoder(audioInputs, totalDuration);
                        audioTrackIndex = muxer.addTrack(audioRecoder.format, true);
                    }
                    while (!outputDone || !audioEncoderDone) {
                        checkConversionCanceled();

                        if (audioRecoder != null) {
                            audioEncoderDone = audioRecoder.step(muxer, audioTrackIndex);
                        }

                        boolean decoderOutputAvailable = !decoderDone;
                        boolean encoderOutputAvailable = true;
                        while (decoderOutputAvailable || encoderOutputAvailable) {
                            checkConversionCanceled();
                            int encoderStatus = encoder.dequeueOutputBuffer(info, increaseTimeout ? MEDIACODEC_TIMEOUT_INCREASED : MEDIACODEC_TIMEOUT_DEFAULT);
                            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                                encoderOutputAvailable = false;
                            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                                if (Build.VERSION.SDK_INT < 21) {
                                    encoderOutputBuffers = encoder.getOutputBuffers();
                                }
                            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                MediaFormat newFormat = encoder.getOutputFormat();
                                if (BuildVars.LOGS_ENABLED) {
                                    FileLog.d("photo encoder new format " + newFormat);
                                }
                                if (videoTrackIndex == -5 && newFormat != null) {
                                    videoTrackIndex = muxer.addTrack(newFormat, false);
                                    if (newFormat.containsKey(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES) && newFormat.getInteger(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES) == 1) {
                                        ByteBuffer spsBuff = newFormat.getByteBuffer("csd-0");
                                        ByteBuffer ppsBuff = newFormat.getByteBuffer("csd-1");
                                        prependHeaderSize = (spsBuff == null ? 0 : spsBuff.limit()) + (ppsBuff == null ? 0 : ppsBuff.limit());
                                    }
                                }
                            } else if (encoderStatus < 0) {
                                throw new RuntimeException("unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
                            } else {
                                ByteBuffer encodedData;
                                if (Build.VERSION.SDK_INT < 21) {
                                    encodedData = encoderOutputBuffers[encoderStatus];
                                } else {
                                    encodedData = encoder.getOutputBuffer(encoderStatus);
                                }
                                if (encodedData == null) {
                                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
                                }
                                if (info.size > 1) {
                                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                        if (prependHeaderSize != 0 && (info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                                            info.offset += prependHeaderSize;
                                            info.size -= prependHeaderSize;
                                        }
                                        if (firstEncode && (info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                                            cutOfNalData(outputMimeType, encodedData, info);
                                            firstEncode = false;
                                        }
                                        long availableSize = muxer.writeSampleData(videoTrackIndex, encodedData, info, true);
                                        if (availableSize != 0) {
                                            if (callback != null) {
                                                if (info.presentationTimeUs > currentPts) {
                                                    currentPts = info.presentationTimeUs;
                                                }
                                                callback.didWriteData(availableSize, (currentPts / 1000f / 1000f) / durationS);
                                            }
                                        }
                                    } else if (videoTrackIndex == -5) {
                                        if (outputMimeType.equals("video/hevc")) {
                                            throw new RuntimeException("unsupported!!");
                                        }
                                        byte[] csd = new byte[info.size];
                                        encodedData.limit(info.offset + info.size);
                                        encodedData.position(info.offset);
                                        encodedData.get(csd);
                                        ByteBuffer sps = null;
                                        ByteBuffer pps = null;
                                        for (int a = info.size - 1; a >= 0; a--) {
                                            if (a > 3) {
                                                if (csd[a] == 1 && csd[a - 1] == 0 && csd[a - 2] == 0 && csd[a - 3] == 0) {
                                                    sps = ByteBuffer.allocate(a - 3);
                                                    pps = ByteBuffer.allocate(info.size - (a - 3));
                                                    sps.put(csd, 0, a - 3).position(0);
                                                    pps.put(csd, a - 3, info.size - (a - 3)).position(0);
                                                    break;
                                                }
                                            } else {
                                                break;
                                            }
                                        }

                                        MediaFormat newFormat = MediaFormat.createVideoFormat(outputMimeType, resultWidth, resultHeight);
                                        if (sps != null && pps != null) {
                                            newFormat.setByteBuffer("csd-0", sps);
                                            newFormat.setByteBuffer("csd-1", pps);
                                        }
                                        videoTrackIndex = muxer.addTrack(newFormat, false);
                                    }
                                }
                                outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                                encoder.releaseOutputBuffer(encoderStatus, false);
                            }
                            if (encoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER) {
                                continue;
                            }

                            if (!decoderDone) {
                                long presentationTime = (long) (framesCount / 30.0f * 1000L * 1000L * 1000L);
                                outputSurface.drawImage(presentationTime);
                                inputSurface.setPresentationTime(presentationTime);
                                inputSurface.swapBuffers();
                                framesCount++;

                                if (framesCount >= duration / 1000.0f * 30) {
                                    decoderDone = true;
                                    decoderOutputAvailable = false;
                                    encoder.signalEndOfInputStream();
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // in some case encoder.dequeueOutputBuffer return IllegalStateException
                    // stable reproduced on xiaomi
                    // fix it by increasing timeout
                    if (e instanceof IllegalStateException && !increaseTimeout) {
                        repeatWithIncreasedTimeout = true;
                    }
                    FileLog.e("bitrate: " + bitrate + " framerate: " + framerate + " size: " + resultHeight + "x" + resultWidth);
                    FileLog.e(e);
                    error = true;
                }

                if (outputSurface != null) {
                    outputSurface.release();
                    outputSurface = null;
                }
                if (inputSurface != null) {
                    inputSurface.release();
                    inputSurface = null;
                }
                if (encoder != null) {
                    encoder.stop();
                    encoder.release();
                    encoder = null;
                }
                if (audioRecoder != null) {
                    audioRecoder.release();
                }
                checkConversionCanceled();
            } else {
                extractor = new MediaExtractor();
                extractor.setDataSource(videoPath);

                int videoIndex = MediaController.findTrack(extractor, false);
                int audioIndex = bitrate != -1 && !muted && volume > 0 ? MediaController.findTrack(extractor, true) : -1;
                boolean needConvertVideo = false;
                if (videoIndex >= 0 && !extractor.getTrackFormat(videoIndex).getString(MediaFormat.KEY_MIME).equals(MediaController.VIDEO_MIME_TYPE)) {
                    needConvertVideo = true;
                }

                if (needCompress || needConvertVideo) {
                    ByteBuffer audioBuffer = null;
                    boolean copyAudioBuffer = true;
                    long lastFramePts = -1;

                    if (videoIndex >= 0) {
                        try {
                            long videoTime = -1;
                            boolean outputDone = false;
                            boolean inputDone = false;
                            boolean decoderDone = false;
                            int swapUV = 0;
                            int audioTrackIndex = -5;
                            long additionalPresentationTime = 0;
                            long minPresentationTime = Integer.MIN_VALUE;
                            long frameDelta = 1000 / framerate * 1000;
                            long frameDeltaFroSkipFrames;
                            if (framerate < 30) {
                                frameDeltaFroSkipFrames = 1000 / (framerate + 5) * 1000;
                            } else {
                                frameDeltaFroSkipFrames = 1000 / (framerate + 1) * 1000;
                            }

                            extractor.selectTrack(videoIndex);
                            MediaFormat videoFormat = extractor.getTrackFormat(videoIndex);
                            String encoderName = null;
                            if (isAvatar) {
                                if (durationS <= 2000) {
                                    bitrate = 2600000;
                                } else if (durationS <= 5000) {
                                    bitrate = 2200000;
                                } else {
                                    bitrate = 1560000;
                                }
                                avatarStartTime = 0;
                                //this encoder work with bitrate better, prevent case when result video max 2MB
                                if (originalBitrate >= 15_000_000) {
                                    encoderName = "OMX.google.h264.encoder";
                                }
                            } else if (bitrate <= 0) {
                                bitrate = 921600;
                            }
                            if (originalBitrate > 0) {
                                bitrate = Math.min(originalBitrate, bitrate);
                            }

                            long trueStartTime;// = startTime < 0 ? 0 : startTime;
                            if (avatarStartTime >= 0/* && trueStartTime == avatarStartTime*/) {
                                avatarStartTime = -1;
                            }

                            if (avatarStartTime >= 0) {
                                extractor.seekTo(avatarStartTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                            } else if (startTime > 0) {
                                extractor.seekTo(startTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                            } else {
                                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                            }

                            int w;
                            int h;
                            if (cropState != null && cropState.useMatrix == null) {
                                if (rotationValue == 90 || rotationValue == 270) {
                                    w = cropState.transformHeight;
                                    h = cropState.transformWidth;
                                } else {
                                    w = cropState.transformWidth;
                                    h = cropState.transformHeight;
                                }
                            } else {
                                w = resultWidth;
                                h = resultHeight;
                            }

                            if (encoderName != null) {
                                try {
                                    encoder = MediaCodec.createByCodecName(encoderName);
                                } catch (Exception e) {

                                }
                            }

                            if (encoder == null) {
                                encoder = createEncoderForMimeType();
                            }

                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("create encoder with w = " + w + " h = " + h + " bitrate = " + bitrate);
                            }
                            MediaFormat outputFormat = MediaFormat.createVideoFormat(outputMimeType, w, h);
                            outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
                            outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
                            if (isAvatar && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                // prevent case when result video max 2MB
                                outputFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
                            }
                            outputFormat.setInteger("max-bitrate", bitrate);
                            outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, framerate);
                            outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

                            boolean hasHDR = false;
                            int colorTransfer = 0, colorStandard = 0, colorRange = 0;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                if (videoFormat.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
                                    colorTransfer = videoFormat.getInteger(MediaFormat.KEY_COLOR_TRANSFER);
                                }
                                if (videoFormat.containsKey(MediaFormat.KEY_COLOR_STANDARD)) {
                                    colorStandard = videoFormat.getInteger(MediaFormat.KEY_COLOR_STANDARD);
                                }
                                if (videoFormat.containsKey(MediaFormat.KEY_COLOR_RANGE)) {
                                    colorRange = videoFormat.getInteger(MediaFormat.KEY_COLOR_RANGE);
                                }
                                if ((colorTransfer == MediaFormat.COLOR_TRANSFER_ST2084 || colorTransfer == MediaFormat.COLOR_TRANSFER_HLG) && colorStandard == MediaFormat.COLOR_STANDARD_BT2020) {
                                    hasHDR = true;
                                }
                            }

                            if (Build.VERSION.SDK_INT < 23 && Math.min(h, w) <= 480 && !isAvatar) {
                                if (bitrate > 921600) {
                                    bitrate = 921600;
                                }
                                outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
                            }

                            selectedEncoderName = encoder.getName();
                            canBeBrokenEncoder = "c2.qti.avc.encoder".equalsIgnoreCase(selectedEncoderName);
                            FileLog.d("selected encoder " + selectedEncoderName);
                            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                            inputSurface = new InputSurface(encoder.createInputSurface());
                            inputSurface.makeCurrent();
                            encoder.start();

                            if (hdrInfo == null && hasHDR) {
                                hdrInfo = new StoryEntry.HDRInfo();
                                hdrInfo.colorTransfer = colorTransfer;
                                hdrInfo.colorStandard = colorStandard;
                                hdrInfo.colorRange = colorRange;
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    outputFormat.setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
                                }
                            }

                            outputSurface = new OutputSurface(inputSurface.getContext(), savedFilterState, null, paintPath, blurPath, mediaEntities, cropState, resultWidth, resultHeight, originalWidth, originalHeight, rotationValue, framerate, false, gradientTopColor, gradientBottomColor, hdrInfo, convertVideoParams, handler);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && hdrInfo != null && hdrInfo.getHDRType() != 0) {
                                outputSurface.changeFragmentShader(
                                        hdrFragmentShader(originalWidth, originalHeight, resultWidth, resultHeight, true, hdrInfo),
                                        hdrFragmentShader(originalWidth, originalHeight, resultWidth, resultHeight, false, hdrInfo),
                                        false
                                );
                            } else if (!isRound && Math.max(resultHeight, resultHeight) / (float) Math.max(originalHeight, originalWidth) < 0.9f) {
                                outputSurface.changeFragmentShader(
                                        createFragmentShader(originalWidth, originalHeight, resultWidth, resultHeight, true, isStory ? 0 : 3),
                                        createFragmentShader(originalWidth, originalHeight, resultWidth, resultHeight, false, isStory ? 0 : 3),
                                        false
                                );
                            }
                            decoder = getDecoderByFormat(videoFormat);
                            decoder.configure(videoFormat, outputSurface.getSurface(), null, 0);
                            decoder.start();

                            ByteBuffer[] decoderInputBuffers = null;
                            ByteBuffer[] encoderOutputBuffers = null;
                            ByteBuffer[] encoderInputBuffers = null;
                            if (Build.VERSION.SDK_INT < 21) {
                                decoderInputBuffers = decoder.getInputBuffers();
                                encoderOutputBuffers = encoder.getOutputBuffers();
                            }

                            int maxBufferSize = 0;

                            if (isWebm) {
                                muxer = new Muxer(new MediaMuxer(cacheFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM));
                            } else {
                                Mp4Movie movie = new Mp4Movie();
                                movie.setCacheFile(cacheFile);
                                movie.setRotation(0);
                                movie.setSize(resultWidth, resultHeight);
                                muxer = new Muxer(new MP4Builder().createMovie(movie, isSecret, outputMimeType.equals("video/hevc")));
                            }

                            if (audioIndex >= 0) {
                                MediaFormat audioFormat = extractor.getTrackFormat(audioIndex);
                                copyAudioBuffer = Math.abs(volume - 1f) < 0.001f && (convertVideoParams.soundInfos.isEmpty() && audioFormat.getString(MediaFormat.KEY_MIME).equals(MediaController.AUDIO_MIME_TYPE) || audioFormat.getString(MediaFormat.KEY_MIME).equals("audio/mpeg"));

                                if (audioFormat.getString(MediaFormat.KEY_MIME).equals("audio/unknown")) {
                                    audioIndex = -1;
                                }

                                if (audioIndex >= 0) {
                                    if (copyAudioBuffer) {
                                        audioTrackIndex = muxer.addTrack(audioFormat, true);
                                        extractor.selectTrack(audioIndex);
                                        try {
                                            maxBufferSize = audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                                        } catch (Exception e) {
                                            FileLog.e(e); //s20 ultra exception
                                        }
                                        if (maxBufferSize <= 0) {
                                            maxBufferSize = 64 * 1024;
                                        }
                                        audioBuffer = ByteBuffer.allocateDirect(maxBufferSize);

                                        if (startTime > 0) {
                                            extractor.seekTo(startTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                                        } else {
                                            extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                                        }
                                    } else {
                                        ArrayList<AudioInput> audioInputs = new ArrayList<>();
                                        GeneralAudioInput mainInput = new GeneralAudioInput(videoPath, audioIndex);
                                        if (endTime > 0) {
                                            mainInput.setEndTimeUs(endTime);
                                        }
                                        if (startTime > 0) {
                                            mainInput.setStartTimeUs(startTime);
                                        }
                                        mainInput.setVolume(volume);
                                        audioInputs.add(mainInput);
                                        applyAudioInputs(convertVideoParams.soundInfos, audioInputs);

                                        audioRecoder = new AudioRecoder(audioInputs, duration);
                                        audioTrackIndex = muxer.addTrack(audioRecoder.format, true);
                                    }
                                }
                            } else if (!convertVideoParams.soundInfos.isEmpty()) {
                                copyAudioBuffer = false;
                                ArrayList<AudioInput> audioInputs = new ArrayList<>();
                                BlankAudioInput mainInput = new BlankAudioInput(duration);
                                audioInputs.add(mainInput);
                                applyAudioInputs(convertVideoParams.soundInfos, audioInputs);

                                audioRecoder = new AudioRecoder(audioInputs, duration);
                                audioTrackIndex = muxer.addTrack(audioRecoder.format, true);
                            }

                            boolean audioEncoderDone = audioRecoder == null;

                            boolean firstEncode = true;

                            checkConversionCanceled();

                            while (!outputDone || (!copyAudioBuffer && !audioEncoderDone)) {
                                checkConversionCanceled();

                                if (audioRecoder != null) {
                                    audioEncoderDone = audioRecoder.step(muxer, audioTrackIndex);
                                }

                                if (!inputDone) {
                                    boolean eof = false;
                                    int index = extractor.getSampleTrackIndex();
                                    if (index == videoIndex) {
                                        int inputBufIndex = decoder.dequeueInputBuffer(MEDIACODEC_TIMEOUT_DEFAULT);
                                        if (inputBufIndex >= 0) {
                                            ByteBuffer inputBuf;
                                            if (Build.VERSION.SDK_INT < 21) {
                                                inputBuf = decoderInputBuffers[inputBufIndex];
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
                                    } else if (copyAudioBuffer && audioIndex != -1 && index == audioIndex) {
                                        if (Build.VERSION.SDK_INT >= 28) {
                                            long size = extractor.getSampleSize();
                                            if (size > maxBufferSize) {
                                                maxBufferSize = (int) (size + 1024);
                                                audioBuffer = ByteBuffer.allocateDirect(maxBufferSize);
                                            }
                                        }
                                        info.size = extractor.readSampleData(audioBuffer, 0);
                                        if (Build.VERSION.SDK_INT < 21) {
                                            audioBuffer.position(0);
                                            audioBuffer.limit(info.size);
                                        }
                                        if (info.size >= 0) {
                                            info.presentationTimeUs = extractor.getSampleTime();
                                            extractor.advance();
                                        } else {
                                            info.size = 0;
                                            inputDone = true;
                                        }
                                        if (info.size > 0 && (endTime < 0 || info.presentationTimeUs < endTime)) {
                                            info.offset = 0;
                                            info.flags = extractor.getSampleFlags();
                                            long availableSize = muxer.writeSampleData(audioTrackIndex, audioBuffer, info, false);
                                            if (availableSize != 0) {
                                                if (callback != null) {
                                                    if (info.presentationTimeUs - startTime > currentPts) {
                                                        currentPts = info.presentationTimeUs - startTime;
                                                    }
                                                    callback.didWriteData(availableSize, (currentPts / 1000f) / durationS);
                                                }
                                            }
                                        }
                                    } else if (index == -1) {
                                        eof = true;
                                    }
                                    if (eof) {
                                        int inputBufIndex = decoder.dequeueInputBuffer(MEDIACODEC_TIMEOUT_DEFAULT);
                                        if (inputBufIndex >= 0) {
                                            decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                            inputDone = true;
                                        }
                                    }
                                }

                                boolean decoderOutputAvailable = !decoderDone;
                                boolean encoderOutputAvailable = true;
                                while (decoderOutputAvailable || encoderOutputAvailable) {
                                    checkConversionCanceled();
                                    int encoderStatus = encoder.dequeueOutputBuffer(info, increaseTimeout ? MEDIACODEC_TIMEOUT_INCREASED : MEDIACODEC_TIMEOUT_DEFAULT);
                                    if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                                        encoderOutputAvailable = false;
                                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                                        if (Build.VERSION.SDK_INT < 21) {
                                            encoderOutputBuffers = encoder.getOutputBuffers();
                                        }
                                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                        MediaFormat newFormat = encoder.getOutputFormat();
                                        if (videoTrackIndex == -5 && newFormat != null) {
                                            videoTrackIndex = muxer.addTrack(newFormat, false);
                                            if (newFormat.containsKey(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES) && newFormat.getInteger(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES) == 1) {
                                                ByteBuffer spsBuff = newFormat.getByteBuffer("csd-0");
                                                ByteBuffer ppsBuff = newFormat.getByteBuffer("csd-1");
                                                prependHeaderSize = (spsBuff == null ? 0 : spsBuff.limit()) + (ppsBuff == null ? 0 : ppsBuff.limit());
                                            }
                                        }
                                    } else if (encoderStatus < 0) {
                                        throw new RuntimeException("unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
                                    } else {
                                        ByteBuffer encodedData;
                                        if (Build.VERSION.SDK_INT < 21) {
                                            encodedData = encoderOutputBuffers[encoderStatus];
                                        } else {
                                            encodedData = encoder.getOutputBuffer(encoderStatus);
                                        }
                                        if (encodedData == null) {
                                            throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
                                        }
                                        if (info.size > 1) {
                                            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                                if (prependHeaderSize != 0 && (info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                                                    info.offset += prependHeaderSize;
                                                    info.size -= prependHeaderSize;
                                                }
                                                if (firstEncode && (info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                                                    cutOfNalData(outputMimeType, encodedData, info);
                                                    firstEncode = false;
                                                }
                                                long availableSize = muxer.writeSampleData(videoTrackIndex, encodedData, info, true);
                                                if (availableSize != 0) {
                                                    if (callback != null) {
                                                        if (info.presentationTimeUs - startTime > currentPts) {
                                                            currentPts = info.presentationTimeUs - startTime;
                                                        }
                                                        callback.didWriteData(availableSize, (currentPts / 1000f) / durationS);
                                                    }
                                                }
                                            } else if (videoTrackIndex == -5) {
                                                byte[] csd = new byte[info.size];
                                                encodedData.limit(info.offset + info.size);
                                                encodedData.position(info.offset);
                                                encodedData.get(csd);
                                                ByteBuffer sps = null;
                                                ByteBuffer pps = null;
                                                for (int a = info.size - 1; a >= 0; a--) {
                                                    if (a > 3) {
                                                        if (csd[a] == 1 && csd[a - 1] == 0 && csd[a - 2] == 0 && csd[a - 3] == 0) {
                                                            sps = ByteBuffer.allocate(a - 3);
                                                            pps = ByteBuffer.allocate(info.size - (a - 3));
                                                            sps.put(csd, 0, a - 3).position(0);
                                                            pps.put(csd, a - 3, info.size - (a - 3)).position(0);
                                                            break;
                                                        }
                                                    } else {
                                                        break;
                                                    }
                                                }

                                                MediaFormat newFormat = MediaFormat.createVideoFormat(outputMimeType, w, h);
                                                if (sps != null && pps != null) {
                                                    newFormat.setByteBuffer("csd-0", sps);
                                                    newFormat.setByteBuffer("csd-1", pps);
                                                }
                                                videoTrackIndex = muxer.addTrack(newFormat, false);
                                            }
                                        }
                                        outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                                        encoder.releaseOutputBuffer(encoderStatus, false);
                                    }
                                    if (encoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER) {
                                        continue;
                                    }

                                    if (!decoderDone) {
                                        int decoderStatus = decoder.dequeueOutputBuffer(info, MEDIACODEC_TIMEOUT_DEFAULT);
                                        if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                                            decoderOutputAvailable = false;
                                        } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {

                                        } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                                            MediaFormat newFormat = decoder.getOutputFormat();
                                            if (BuildVars.LOGS_ENABLED) {
                                                FileLog.d("newFormat = " + newFormat);
                                            }
                                            // TODO: apply hdr static info here
                                        } else if (decoderStatus < 0) {
                                            throw new RuntimeException("unexpected result from decoder.dequeueOutputBuffer: " + decoderStatus);
                                        } else {
                                            boolean doRender = info.size != 0;
                                            long originalPresentationTime = info.presentationTimeUs;
                                            if (endTime > 0 && originalPresentationTime >= endTime) {
                                                inputDone = true;
                                                decoderDone = true;
                                                doRender = false;
                                                info.flags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                                            }
                                            boolean flushed = false;
                                            if (avatarStartTime >= 0 && (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 && Math.abs(avatarStartTime - startTime) > 1000000 / framerate) {
                                                if (startTime > 0) {
                                                    extractor.seekTo(startTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                                                } else {
                                                    extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                                                }
                                                additionalPresentationTime = minPresentationTime + frameDelta;
                                                endTime = avatarStartTime;
                                                avatarStartTime = -1;
                                                inputDone = false;
                                                decoderDone = false;
                                                doRender = false;
                                                info.flags &= ~MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                                                decoder.flush();
                                                flushed = true;
                                            }
                                            if (lastFramePts > 0 && info.presentationTimeUs - lastFramePts < frameDeltaFroSkipFrames && (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0) {
                                                doRender = false;
                                            }
                                            trueStartTime = avatarStartTime >= 0 ? avatarStartTime : startTime;
                                            if (trueStartTime > 0 && videoTime == -1) {
                                                if (originalPresentationTime < trueStartTime) {
                                                    doRender = false;
                                                    if (BuildVars.LOGS_ENABLED) {
                                                        FileLog.d("drop frame startTime = " + trueStartTime + " present time = " + info.presentationTimeUs);
                                                    }
                                                } else {
                                                    videoTime = info.presentationTimeUs;
                                                    if (minPresentationTime != Integer.MIN_VALUE) {
                                                        additionalPresentationTime -= videoTime;
                                                    }
                                                }
                                            }
                                            if (flushed) {
                                                videoTime = -1;
                                            } else {
                                                if (avatarStartTime == -1 && additionalPresentationTime != 0) {
                                                    info.presentationTimeUs += additionalPresentationTime;
                                                }
                                                decoder.releaseOutputBuffer(decoderStatus, doRender);
                                            }
                                            if (doRender) {
                                                lastFramePts = info.presentationTimeUs;
                                                if (avatarStartTime >= 0) {
                                                    minPresentationTime = Math.max(minPresentationTime, info.presentationTimeUs);
                                                }
                                                boolean errorWait = false;
                                                try {
                                                    outputSurface.awaitNewImage();
                                                } catch (Exception e) {
                                                    errorWait = true;
                                                    FileLog.e(e);
                                                }
                                                if (!errorWait) {
                                                    outputSurface.drawImage(info.presentationTimeUs * 1000);
                                                    inputSurface.setPresentationTime(info.presentationTimeUs * 1000);
                                                    inputSurface.swapBuffers();
                                                }
                                            }
                                            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                                decoderOutputAvailable = false;
                                                if (BuildVars.LOGS_ENABLED) {
                                                    FileLog.d("decoder stream end");
                                                }
                                                encoder.signalEndOfInputStream();
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // in some case encoder.dequeueOutputBuffer return IllegalStateException
                            // stable reproduced on xiaomi
                            // fix it by increasing timeout
                            if (e instanceof IllegalStateException && !increaseTimeout) {
                                repeatWithIncreasedTimeout = true;
                            }
                            FileLog.e("bitrate: " + bitrate + " framerate: " + framerate + " size: " + resultHeight + "x" + resultWidth);
                            FileLog.e(e);
                            error = true;
                        }

                        extractor.unselectTrack(videoIndex);
                        if (decoder != null) {
                            decoder.stop();
                            decoder.release();
                        }
                    }
                    if (outputSurface != null) {
                        outputSurface.release();
                        outputSurface = null;
                    }
                    if (inputSurface != null) {
                        inputSurface.release();
                        inputSurface = null;
                    }
                    if (encoder != null) {
                        encoder.stop();
                        encoder.release();
                        encoder = null;
                    }
                    if (audioRecoder != null) {
                        audioRecoder.release();
                    }
                    checkConversionCanceled();
                } else {
                    Mp4Movie movie = new Mp4Movie();
                    movie.setCacheFile(cacheFile);
                    movie.setRotation(0);
                    movie.setSize(resultWidth, resultHeight);
                    muxer = new Muxer(new MP4Builder().createMovie(movie, isSecret, false));
                    readAndWriteTracks(extractor, muxer, info, startTime, endTime, duration, cacheFile, bitrate != -1 && !muted);
                }
            }
        } catch (Throwable e) {
            error = true;
            FileLog.e("bitrate: " + bitrate + " framerate: " + framerate + " size: " + resultHeight + "x" + resultWidth);
            FileLog.e(e);
        } finally {
            if (extractor != null) {
                extractor.release();
            }
            if (muxer != null) {
                try {
                    muxer.finishMovie();
                    endPresentationTime = muxer.getLastFrameTimestamp(videoTrackIndex, info);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
            if (encoder != null) {
                try {
                    encoder.release();
                } catch (Exception ignore) {
                }
                encoder = null;
            }
            if (decoder != null) {
                try {
                    decoder.release();
                } catch (Exception ignore) {
                }
                decoder = null;
            }
            if (outputSurface != null) {
                try {
                    outputSurface.release();
                } catch (Exception ignore) {
                }
                outputSurface = null;
            }
            if (inputSurface != null) {
                try {
                    inputSurface.release();
                } catch (Exception ignore) {
                }
                inputSurface = null;
            }
        }

        if (repeatWithIncreasedTimeout) {
            return convertVideoInternal(convertVideoParams, true, triesCount + 1, handler);
        }

        if (error && canBeBrokenEncoder && triesCount < 3) {
            return convertVideoInternal(convertVideoParams, increaseTimeout, triesCount + 1, handler);
        }

        long timeLeft = System.currentTimeMillis() - time;
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("compression completed time=" + timeLeft + " needCompress=" + needCompress + " w=" + resultWidth + " h=" + resultHeight + " bitrate=" + bitrate + " file size=" + AndroidUtilities.formatFileSize(cacheFile.length()) + " encoder_name=" + selectedEncoderName);
        }
        return error;
    }

    private static void applyAudioInputs(ArrayList<MixedSoundInfo> soundInfos, ArrayList<AudioInput> audioInputs) throws IOException {
        if (soundInfos == null) return;
        for (int i = 0; i < soundInfos.size(); i++) {
            MixedSoundInfo soundInfo = soundInfos.get(i);
            GeneralAudioInput secondAudio;
            try {
                secondAudio = new GeneralAudioInput(soundInfo.audioFile);
            } catch (Exception e) {
                FileLog.e(e);
                continue;
            }
            secondAudio.setVolume(soundInfo.volume);
            long startTimeLocal = 0;
            if (soundInfo.startTime > 0) {
                secondAudio.setStartOffsetUs(soundInfo.startTime);
            }
            if (soundInfo.audioOffset > 0) {
                secondAudio.setStartTimeUs(startTimeLocal = soundInfo.audioOffset);
            }
            if (soundInfo.duration > 0) {
                secondAudio.setEndTimeUs(startTimeLocal + soundInfo.duration);
            }
            audioInputs.add(secondAudio);
        }
    }

    private MediaCodec createEncoderForMimeType() throws IOException {
        MediaCodec encoder = null;//MediaCodec.createEncoderByType(outputMimeType);
        if (outputMimeType.equals("video/hevc") && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            String encoderName = SharedConfig.findGoodHevcEncoder();
            if (encoderName != null) {
                encoder = MediaCodec.createByCodecName(encoderName);
            }
        } else {
            if (outputMimeType.equals("video/hevc")) {
                outputMimeType = "video/avc";
            }
            encoder = MediaCodec.createEncoderByType(outputMimeType);
        }
//        if (encoder != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && "c2.qti.avc.encoder".equals(encoder.getName())) {
//            FileLog.d("searching another encoder to replace c2.qti.avc.encoder");
//            MediaCodecInfo[] infos = new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos();
//            for (MediaCodecInfo codecInfo : infos) {
//                if (codecInfo != null && codecInfo.isEncoder() && !"c2.qti.avc.encoder".equals(codecInfo.getName())) {
//                    String[] types = codecInfo.getSupportedTypes();
//                    boolean found = false;
//                    for (int i = 0; i < types.length; ++i) {
//                        if (types[i] != null && types[i].startsWith(outputMimeType)) {
//                            found = true;
//                            break;
//                        }
//                    }
//                    if (found) {
//                        FileLog.d("blacklisting c2.qti.avc.encoder, replacing it with " + codecInfo.getName());
//                        encoder.release();
//                        encoder = MediaCodec.createByCodecName(codecInfo.getName());
//                        break;
//                    }
//                }
//            }
//        }
        if (encoder == null && outputMimeType.equals("video/hevc")) {
            outputMimeType = "video/avc";
            encoder = MediaCodec.createEncoderByType(outputMimeType);
        }
        return encoder;
    }

    public static void cutOfNalData(String outputMimeType, ByteBuffer encodedData, MediaCodec.BufferInfo info) {
        int maxNalCount = 1;
        if (outputMimeType.equals("video/hevc")) {
            maxNalCount = 3;
        }
        if (info.size > 100) {
            encodedData.position(info.offset);
            byte[] temp = new byte[100];
            encodedData.get(temp);
            int nalCount = 0;
            for (int a = 0; a < temp.length - 4; a++) {
                if (temp[a] == 0 && temp[a + 1] == 0 && temp[a + 2] == 0 && temp[a + 3] == 1) {
                    nalCount++;
                    if (nalCount > maxNalCount) {
                        info.offset += a;
                        info.size -= a;
                        break;
                    }
                }
            }
        }
    }

    private boolean isMediatekAvcEncoder(MediaCodec encoder) {
        return encoder.getName().equals("c2.mtk.avc.encoder");
    }

    public static class Muxer {

        public final MP4Builder mp4Builder;
        public final MediaMuxer mediaMuxer;

        private boolean started = false;

        public Muxer(MP4Builder mp4Builder) {
            this.mp4Builder = mp4Builder;
            this.mediaMuxer = null;
        }
        public Muxer(MediaMuxer mediaMuxer) {
            this.mp4Builder = null;
            this.mediaMuxer = mediaMuxer;
        }

        public int addTrack(MediaFormat format, boolean isAudio) {
            if (mediaMuxer != null) {
                return mediaMuxer.addTrack(format);
            } else if (mp4Builder != null) {
                return mp4Builder.addTrack(format, isAudio);
            }
            return 0;
        }

        public long writeSampleData(int trackIndex, ByteBuffer byteBuf, MediaCodec.BufferInfo bufferInfo, boolean writeLength) throws Exception {
            if (mediaMuxer != null) {
                if (!started) {
                    mediaMuxer.start();
                    started = true;
                }
                mediaMuxer.writeSampleData(trackIndex, byteBuf, bufferInfo);
                return 0;
            } else if (mp4Builder != null) {
                return mp4Builder.writeSampleData(trackIndex, byteBuf, bufferInfo, writeLength);
            }
            return 0;
        }

        public long getLastFrameTimestamp(int trackIndex, MediaCodec.BufferInfo bufferInfo) {
            if (mediaMuxer != null) {
                return bufferInfo.presentationTimeUs;
            } else if (mp4Builder != null) {
                return mp4Builder.getLastFrameTimestamp(trackIndex);
            }
            return 0;
        }

        public void start() {
            if (mediaMuxer != null) {
                mediaMuxer.start();
            } else if (mp4Builder != null) {

            }
        }

        public void finishMovie() throws Exception {
            if (mediaMuxer != null) {
                mediaMuxer.stop();
                mediaMuxer.release();
            } else if (mp4Builder != null) {
                mp4Builder.finishMovie();
            }
        }

    }

    private long readAndWriteTracks(
        MediaExtractor extractor, Muxer mediaMuxer,
        MediaCodec.BufferInfo info, long start, long end, long duration, File file, boolean needAudio
    ) throws Exception {
        int videoTrackIndex = MediaController.findTrack(extractor, false);
        int audioTrackIndex = needAudio ? MediaController.findTrack(extractor, true) : -1;
        int muxerVideoTrackIndex = -1;
        int muxerAudioTrackIndex = -1;
        boolean inputDone = false;

        long currentPts = 0;
        float durationS = duration / 1000f;

        int maxBufferSize = 0;
        if (videoTrackIndex >= 0) {
            extractor.selectTrack(videoTrackIndex);
            MediaFormat trackFormat = extractor.getTrackFormat(videoTrackIndex);
            muxerVideoTrackIndex = mediaMuxer.addTrack(trackFormat, false);
            try {
                maxBufferSize = trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
            } catch (Exception e) {
                FileLog.e(e); //s20 ultra exception
            }

            if (start > 0) {
                extractor.seekTo(start, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            } else {
                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            }
        }
        if (audioTrackIndex >= 0) {
            extractor.selectTrack(audioTrackIndex);
            MediaFormat trackFormat = extractor.getTrackFormat(audioTrackIndex);

            if (trackFormat.getString(MediaFormat.KEY_MIME).equals("audio/unknown")) {
                audioTrackIndex = -1;
            } else {
                muxerAudioTrackIndex = mediaMuxer.addTrack(trackFormat, true);
                try {
                    maxBufferSize = Math.max(trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE), maxBufferSize);
                } catch (Exception e) {
                    FileLog.e(e); //s20 ultra exception
                }
                if (start > 0) {
                    extractor.seekTo(start, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                } else {
                    extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                }
            }
        }
        if (maxBufferSize <= 0) {
            maxBufferSize = 64 * 1024;
        }
        ByteBuffer buffer = ByteBuffer.allocateDirect(maxBufferSize);
        if (audioTrackIndex >= 0 || videoTrackIndex >= 0) {
            long startTime = -1;
            checkConversionCanceled();
            while (!inputDone) {
                checkConversionCanceled();
                boolean eof = false;
                int muxerTrackIndex;
                if (Build.VERSION.SDK_INT >= 28) {
                    long size = extractor.getSampleSize();
                    if (size > maxBufferSize) {
                        maxBufferSize = (int) (size + 1024);
                        buffer = ByteBuffer.allocateDirect(maxBufferSize);
                    }
                }
                info.size = extractor.readSampleData(buffer, 0);
                int index = extractor.getSampleTrackIndex();
                if (index == videoTrackIndex) {
                    muxerTrackIndex = muxerVideoTrackIndex;
                } else if (index == audioTrackIndex) {
                    muxerTrackIndex = muxerAudioTrackIndex;
                } else {
                    muxerTrackIndex = -1;
                }
                if (muxerTrackIndex != -1) {
                    if (Build.VERSION.SDK_INT < 21) {
                        buffer.position(0);
                        buffer.limit(info.size);
                    }
                    if (index != audioTrackIndex) {
                        byte[] array = buffer.array();
                        if (array != null) {
                            int offset = buffer.arrayOffset();
                            int len = offset + buffer.limit();
                            int writeStart = -1;
                            for (int a = offset; a <= len - 4; a++) {
                                if (array[a] == 0 && array[a + 1] == 0 && array[a + 2] == 0 && array[a + 3] == 1 || a == len - 4) {
                                    if (writeStart != -1) {
                                        int l = a - writeStart - (a != len - 4 ? 4 : 0);
                                        array[writeStart] = (byte) (l >> 24);
                                        array[writeStart + 1] = (byte) (l >> 16);
                                        array[writeStart + 2] = (byte) (l >> 8);
                                        array[writeStart + 3] = (byte) l;
                                        writeStart = a;
                                    } else {
                                        writeStart = a;
                                    }
                                }
                            }
                        }
                    }
                    if (info.size >= 0) {
                        info.presentationTimeUs = extractor.getSampleTime();
                    } else {
                        info.size = 0;
                        eof = true;
                    }

                    if (info.size > 0 && !eof) {
                        if (index == videoTrackIndex && start > 0 && startTime == -1) {
                            startTime = info.presentationTimeUs;
                        }
                        if (end < 0 || info.presentationTimeUs < end) {
                            info.offset = 0;
                            info.flags = extractor.getSampleFlags();
                            long availableSize = mediaMuxer.writeSampleData(muxerTrackIndex, buffer, info, false);
                            if (availableSize != 0) {
                                if (callback != null) {
                                    if (info.presentationTimeUs - startTime > currentPts) {
                                        currentPts = info.presentationTimeUs - startTime;
                                    }
                                    callback.didWriteData(availableSize, (currentPts / 1000f) / durationS);
                                }
                            }
                        } else {
                            eof = true;
                        }
                    }
                    if (!eof) {
                        extractor.advance();
                    }
                } else if (index == -1) {
                    eof = true;
                } else {
                    extractor.advance();
                }
                if (eof) {
                    inputDone = true;
                }
            }
            if (videoTrackIndex >= 0) {
                extractor.unselectTrack(videoTrackIndex);
            }
            if (audioTrackIndex >= 0) {
                extractor.unselectTrack(audioTrackIndex);
            }
            return startTime;
        }
        return -1;
    }

    private void checkConversionCanceled() {
        if (callback != null && callback.checkConversionCanceled())
            throw new ConversionCanceledException();
    }

    private static String hdrFragmentShader(
            final int srcWidth,
            final int srcHeight,
            final int dstWidth,
            final int dstHeight,
            boolean external,
            StoryEntry.HDRInfo hdrInfo
    ) {
        if (external) {
            String shaderCode;
            if (hdrInfo.getHDRType() == 1) {
                shaderCode = AndroidUtilities.readRes(R.raw.hdr2sdr_hlg);
            } else {
                shaderCode = AndroidUtilities.readRes(R.raw.hdr2sdr_pq);
            }
            shaderCode = shaderCode.replace("$dstWidth", dstWidth + ".0");
            shaderCode = shaderCode.replace("$dstHeight", dstHeight + ".0");
            // TODO(@dkaraush): use minlum/maxlum
            return shaderCode + "\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "void main() {\n" +
                    "    gl_FragColor = TEX(vTextureCoord);\n" +
                    "}";
        } else {
            return "precision mediump float;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "uniform sampler2D sTexture;\n" +
                    "void main() {\n" +
                    "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
                    "}\n";
        }
    }

    private static String createFragmentShader(
            final int srcWidth,
            final int srcHeight,
            final int dstWidth,
            final int dstHeight, boolean external, int maxKernelRadius) {

        final float kernelSize = Utilities.clamp((float) (Math.max(srcWidth, srcHeight) / (float) Math.max(dstHeight, dstWidth)) * 0.8f, 2f, 1f);
        int kernelRadius = (int) kernelSize;
        if (kernelRadius > 1 && SharedConfig.deviceIsAverage()) {
            kernelRadius = 1;
        }
        kernelRadius = Math.min(maxKernelRadius, kernelRadius);
        FileLog.d("source size " + srcWidth + "x" + srcHeight + "    dest size " + dstWidth + dstHeight + "   kernelRadius " + kernelRadius);
        if (external) {
            return "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "const float kernel = " + kernelRadius + ".0;\n" +
                    "const float pixelSizeX = 1.0 / " + srcWidth + ".0;\n" +
                    "const float pixelSizeY = 1.0 / " + srcHeight + ".0;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "void main() {\n" +
                    "vec3 accumulation = vec3(0);\n" +
                    "vec3 weightsum = vec3(0);\n" +
                    "for (float x = -kernel; x <= kernel; x++){\n" +
                    "   for (float y = -kernel; y <= kernel; y++){\n" +
                    "       accumulation += texture2D(sTexture, vTextureCoord + vec2(x * pixelSizeX, y * pixelSizeY)).xyz;\n" +
                    "       weightsum += 1.0;\n" +
                    "   }\n" +
                    "}\n" +
                    "gl_FragColor = vec4(accumulation / weightsum, 1.0);\n" +
                    "}\n";
        } else {
            return "precision mediump float;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "const float kernel = " + kernelRadius + ".0;\n" +
                    "const float pixelSizeX = 1.0 / " + srcHeight + ".0;\n" +
                    "const float pixelSizeY = 1.0 / " + srcWidth + ".0;\n" +
                    "uniform sampler2D sTexture;\n" +
                    "void main() {\n" +
                    "vec3 accumulation = vec3(0);\n" +
                    "vec3 weightsum = vec3(0);\n" +
                    "for (float x = -kernel; x <= kernel; x++){\n" +
                    "   for (float y = -kernel; y <= kernel; y++){\n" +
                    "       accumulation += texture2D(sTexture, vTextureCoord + vec2(x * pixelSizeX, y * pixelSizeY)).xyz;\n" +
                    "       weightsum += 1.0;\n" +
                    "   }\n" +
                    "}\n" +
                    "gl_FragColor = vec4(accumulation / weightsum, 1.0);\n" +
                    "}\n";
        }
    }


    public class ConversionCanceledException extends RuntimeException {

        public ConversionCanceledException() {
            super("canceled conversion");
        }
    }

    @NonNull
    private MediaCodec getDecoderByFormat(MediaFormat format) {
        if (format == null) {
            throw new RuntimeException("getDecoderByFormat: format is null");
        }
        ArrayList<String> types = new ArrayList<>();
        String mainType = format.getString(MediaFormat.KEY_MIME);
        types.add(mainType);
        if ("video/dolby-vision".equals(mainType)) {
            types.add("video/hevc");
            types.add("video/avc");
        }
        Exception exception = null;
        while (!types.isEmpty()) {
            try {
                String mime = types.remove(0);
                format.setString(MediaFormat.KEY_MIME, mime);
                return MediaCodec.createDecoderByType(mime);
            } catch (Exception e) {
                if (exception == null) {
                    exception = e;
                }
            }
        }
        throw new RuntimeException(exception);
    }

    public static class ConvertVideoParams {
        String videoPath;
        File cacheFile;
        int rotationValue;
        boolean isSecret;
        int originalWidth, originalHeight;
        int resultWidth, resultHeight;
        int framerate;
        int bitrate;
        int originalBitrate;
        long startTime;
        long endTime;
        long avatarStartTime;
        boolean needCompress;
        long duration;
        MediaController.SavedFilterState savedFilterState;
        String paintPath;
        String blurPath;
        String messagePath;
        String messageVideoMaskPath;
        String backgroundPath;
        ArrayList<VideoEditedInfo.MediaEntity> mediaEntities;
        boolean isPhoto;
        MediaController.CropState cropState;
        boolean isRound;
        MediaController.VideoConvertorListener callback;
        Integer gradientTopColor;
        Integer gradientBottomColor;
        boolean muted;
        float volume;
        boolean isStory;
        StoryEntry.HDRInfo hdrInfo;
        public ArrayList<MixedSoundInfo> soundInfos = new ArrayList<MixedSoundInfo>();
        int account;
        boolean isDark;
        long wallpaperPeerId;
        boolean isSticker;
        CollageLayout collage;
        ArrayList<VideoEditedInfo.Part> collageParts;

        private ConvertVideoParams() {

        }

        public static ConvertVideoParams of(String videoPath, File cacheFile,
                                            int rotationValue, boolean isSecret,
                                            int originalWidth, int originalHeight,
                                            int resultWidth, int resultHeight,
                                            int framerate, int bitrate, int originalBitrate,
                                            long startTime, long endTime, long avatarStartTime,
                                            boolean needCompress, long duration,
                                            MediaController.VideoConvertorListener callback,
                                            VideoEditedInfo info) {
            ConvertVideoParams params = new ConvertVideoParams();
            params.videoPath = videoPath;
            params.cacheFile = cacheFile;
            params.rotationValue = rotationValue;
            params.isSecret = isSecret;
            params.originalWidth = originalWidth;
            params.originalHeight = originalHeight;
            params.resultWidth = resultWidth;
            params.resultHeight = resultHeight;
            params.framerate = framerate;
            params.bitrate = bitrate;
            params.originalBitrate = originalBitrate;
            params.startTime = startTime;
            params.endTime = endTime;
            params.avatarStartTime = avatarStartTime;
            params.needCompress = needCompress;
            params.duration = duration;
            params.savedFilterState = info.filterState;
            params.paintPath = info.paintPath;
            params.blurPath = info.blurPath;
            params.mediaEntities = info.mediaEntities;
            params.isPhoto = info.isPhoto;
            params.cropState = info.cropState;
            params.isRound = info.roundVideo;
            params.callback = callback;
            params.gradientTopColor = info.gradientTopColor;
            params.gradientBottomColor = info.gradientBottomColor;
            params.muted = info.muted;
            params.volume = info.volume;
            params.isStory = info.isStory;
            params.hdrInfo = info.hdrInfo;
            params.isDark = info.isDark;
            params.wallpaperPeerId = info.wallpaperPeerId;
            params.account = info.account;
            params.messagePath = info.messagePath;
            params.messageVideoMaskPath = info.messageVideoMaskPath;
            params.backgroundPath = info.backgroundPath;
            params.isSticker = info.isSticker;
            params.collage = info.collage;
            params.collageParts = info.collageParts;
            return params;
        }
    }

    public static class MixedSoundInfo {

        final String audioFile;
        public float volume = 1f;
        public long audioOffset;
        public long startTime;
        public long duration;

        public MixedSoundInfo(String file) {
            this.audioFile = file;
        }
    }

}
