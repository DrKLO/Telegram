/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui;

import android.annotation.TargetApi;
import android.content.res.Configuration;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.coremedia.iso.IsoFile;
import com.coremedia.iso.boxes.Container;
import com.coremedia.iso.boxes.TrackBox;
import com.coremedia.iso.boxes.h264.AvcConfigurationBox;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.CroppedTrack;
import com.googlecode.mp4parser.util.Path;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import org.telegram.android.video.InputSurface;
import org.telegram.android.video.MP4Builder;
import org.telegram.android.video.Mp4Movie;
import org.telegram.android.video.OutputSurface;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Views.ActionBar.ActionBarLayer;
import org.telegram.ui.Views.ActionBar.ActionBarMenu;
import org.telegram.ui.Views.ActionBar.BaseFragment;
import org.telegram.ui.Views.VideoSeekBarView;
import org.telegram.ui.Views.VideoTimelineView;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

@TargetApi(18)
public class VideoEditorActivity extends BaseFragment implements SurfaceHolder.Callback {

    private final static int OMX_TI_COLOR_FormatYUV420PackedSemiPlanar = 0x7F000100;
    private final static int OMX_QCOM_COLOR_FormatYVU420SemiPlanar = 0x7FA30C00;
    private final static int OMX_QCOM_COLOR_FormatYUV420PackedSemiPlanar64x32Tile2m8ka = 0x7FA30C03;
    private final static int OMX_SEC_COLOR_FormatNV12Tiled = 0x7FC00002;
    private final static int OMX_QCOM_COLOR_FormatYUV420PackedSemiPlanar32m = 0x7FA30C04;

    private MediaPlayer videoPlayer = null;
    private SurfaceHolder surfaceHolder = null;
    private VideoTimelineView videoTimelineView = null;
    private View videoContainerView = null;
    private TextView originalSizeTextView = null;
    private TextView editedSizeTextView = null;
    private View textContainerView = null;
    private ImageView playButton = null;
    private VideoSeekBarView videoSeekBarView = null;

    private boolean initied = false;
    private String videoPath = null;
    private int videoWidth;
    private int videoHeight;
    private int editedVideoWidth;
    private int editedVideoHeight;
    private int editedVideoDuration;
    private float lastProgress = 0;
    private boolean needSeek = false;
    private VideoEditorActivityDelegate delegate;
    private long esimatedFileSize = 0;

    public interface VideoEditorActivityDelegate {
        public abstract void didStartVideoConverting(String videoPath, String originalPath, long esimatedSize, int duration, int width, int height);
        public abstract void didAppenedVideoData(String videoPath, long finalSize);
    }

    private Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            while (videoPlayer.isPlaying()) {
                AndroidUtilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (videoPlayer.isPlaying()) {
                            float startTime = videoTimelineView.getLeftProgress() * videoPlayer.getDuration();
                            float endTime = videoTimelineView.getRightProgress() * videoPlayer.getDuration();
                            if (startTime == endTime) {
                                startTime = endTime - 0.01f;
                            }
                            float progress = (videoPlayer.getCurrentPosition() - startTime) / (endTime - startTime);
                            if (progress > lastProgress) {
                                videoSeekBarView.setProgress(progress);
                                lastProgress = progress;
                            }
                            if (videoPlayer.getCurrentPosition() >= endTime) {
                                try {
                                    videoPlayer.pause();
                                    onPlayComplete();
                                } catch (Exception e) {
                                    FileLog.e("tmessages", e);
                                }
                            }
                        }
                    }
                });
                try {
                    Thread.sleep(50);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        }
    };

    public VideoEditorActivity(Bundle args) {
        super(args);
        videoPath = args.getString("videoPath");
    }

    @Override
    public boolean onFragmentCreate() {
        if (videoPath == null) {
            return false;
        }
        videoPlayer = new MediaPlayer();
        videoPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                AndroidUtilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        onPlayComplete();
                    }
                });
            }
        });
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        if (videoTimelineView != null) {
            videoTimelineView.destroy();
        }
        super.onFragmentDestroy();
    }

    @Override
    public View createView(LayoutInflater inflater, ViewGroup container) {
        if (fragmentView == null) {
            actionBarLayer.setBackgroundColor(0xff333333);
            actionBarLayer.setItemsBackground(R.drawable.bar_selector_white);
            actionBarLayer.setDisplayHomeAsUpEnabled(true, R.drawable.photo_back);
            actionBarLayer.setTitle(LocaleController.getString("EditVideo", R.string.EditVideo));
            actionBarLayer.setActionBarMenuOnItemClick(new ActionBarLayer.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    } else if (id == 1) {
                        try {
                            //startConvert();
                            VideoEditWrapper.runTest(VideoEditorActivity.this);
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    }
                }
            });

            ActionBarMenu menu = actionBarLayer.createMenu();
            View doneItem = menu.addItemResource(1, R.layout.group_create_done_layout);

            TextView doneTextView = (TextView) doneItem.findViewById(R.id.done_button);
            doneTextView.setText(LocaleController.getString("Done", R.string.Done).toUpperCase());

            fragmentView = inflater.inflate(R.layout.video_editor_layout, container, false);
            originalSizeTextView = (TextView) fragmentView.findViewById(R.id.original_size);
            editedSizeTextView = (TextView) fragmentView.findViewById(R.id.edited_size);
            videoContainerView = fragmentView.findViewById(R.id.video_container);
            textContainerView = fragmentView.findViewById(R.id.info_container);

            videoTimelineView = (VideoTimelineView) fragmentView.findViewById(R.id.video_timeline_view);
            videoTimelineView.setVideoPath(videoPath);
            videoTimelineView.setDelegate(new VideoTimelineView.VideoTimelineViewDelegate() {
                @Override
                public void onLeftProgressChanged(float progress) {
                    try {
                        if (videoPlayer.isPlaying()) {
                            videoPlayer.pause();
                            playButton.setImageResource(R.drawable.video_play);
                        }
                        videoPlayer.setOnSeekCompleteListener(null);
                        videoPlayer.seekTo((int) (videoPlayer.getDuration() * progress));
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                    needSeek = true;
                    videoSeekBarView.setProgress(0);
                    updateVideoEditedInfo();
                }

                @Override
                public void onRifhtProgressChanged(float progress) {
                    try {
                        if (videoPlayer.isPlaying()) {
                            videoPlayer.pause();
                            playButton.setImageResource(R.drawable.video_play);
                        }
                        videoPlayer.setOnSeekCompleteListener(null);
                        videoPlayer.seekTo((int) (videoPlayer.getDuration() * progress));
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                    needSeek = true;
                    videoSeekBarView.setProgress(0);
                    updateVideoEditedInfo();
                }
            });

            videoSeekBarView = (VideoSeekBarView) fragmentView.findViewById(R.id.video_seekbar);
            videoSeekBarView.delegate = new VideoSeekBarView.SeekBarDelegate() {
                @Override
                public void onSeekBarDrag(float progress) {
                    if (videoPlayer.isPlaying()) {
                        try {
                            float prog = videoTimelineView.getLeftProgress() + (videoTimelineView.getRightProgress() - videoTimelineView.getLeft()) * progress;
                            videoPlayer.seekTo((int) (videoPlayer.getDuration() * prog));
                            lastProgress = progress;
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    } else {
                        lastProgress = progress;
                        needSeek = true;
                    }
                }
            };

            playButton = (ImageView) fragmentView.findViewById(R.id.play_button);
            playButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (surfaceHolder.isCreating()) {
                        return;
                    }
                    play();
                }
            });

            SurfaceView surfaceView = (SurfaceView) fragmentView.findViewById(R.id.video_view);
            surfaceHolder = surfaceView.getHolder();
            surfaceHolder.addCallback(this);
            surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
            surfaceHolder.setFixedSize(270, 480);

            updateVideoOriginalInfo();
            updateVideoEditedInfo();
        } else {
            ViewGroup parent = (ViewGroup) fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        fixLayout();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        fixLayout();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        videoPlayer.setDisplay(holder);
        try {
            videoPlayer.setDataSource(videoPath);
            videoPlayer.prepare();
            videoWidth = videoPlayer.getVideoWidth();
            videoHeight = videoPlayer.getVideoHeight();
            fixVideoSize();
            videoPlayer.seekTo((int) (videoTimelineView.getLeftProgress() * videoPlayer.getDuration()));
            initied = true;
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        updateVideoOriginalInfo();
        updateVideoEditedInfo();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        videoPlayer.setDisplay(null);
    }

    private void onPlayComplete() {
        playButton.setImageResource(R.drawable.video_play);
        videoSeekBarView.setProgress(0);
        try {
            videoPlayer.seekTo((int) (videoTimelineView.getLeftProgress() * videoPlayer.getDuration()));
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    private void updateVideoOriginalInfo() {
        if (!initied || originalSizeTextView == null) {
            return;
        }
        File file = new File(videoPath);
        String videoDimension = String.format("%dx%d", videoPlayer.getVideoWidth(), videoPlayer.getVideoHeight());
        int minutes = videoPlayer.getDuration() / 1000 / 60;
        int seconds = (int) Math.ceil(videoPlayer.getDuration() / 1000) - minutes * 60;
        String videoTimeSize = String.format("%d:%02d, %s", minutes, seconds, Utilities.formatFileSize(file.length()));
        originalSizeTextView.setText(String.format("%s: %s, %s", LocaleController.getString("OriginalVideo", R.string.OriginalVideo), videoDimension, videoTimeSize));
    }

    private void updateVideoEditedInfo() {
        if (!initied || editedSizeTextView == null) {
            return;
        }
        File file = new File(videoPath);
        long size = file.length();
        editedVideoWidth = videoPlayer.getVideoWidth();
        editedVideoHeight = videoPlayer.getVideoHeight();
        if (editedVideoWidth > 640 || editedVideoHeight > 640) {
            float scale = editedVideoWidth > editedVideoHeight ? 640.0f / editedVideoWidth : 640.0f / editedVideoHeight;
            editedVideoWidth *= scale;
            editedVideoHeight *= scale;
            size *= (scale * scale) * 1.02f;
        }
        String videoDimension = String.format("%dx%d", editedVideoWidth, editedVideoHeight);
        editedVideoDuration = videoPlayer.getDuration();
        int minutes = editedVideoDuration / 1000 / 60;
        int seconds = (int) Math.ceil(editedVideoDuration / 1000) - minutes * 60;
        String videoTimeSize = String.format("%d:%02d, ~%s", minutes, seconds, Utilities.formatFileSize(size));
        esimatedFileSize = size;
        editedSizeTextView.setText(String.format("%s: %s, %s", LocaleController.getString("EditedVideo", R.string.EditedVideo), videoDimension, videoTimeSize));
    }

    private void fixVideoSize() {
        if (videoWidth == 0 || videoHeight == 0 || fragmentView == null || getParentActivity() == null) {
            return;
        }
        int viewHeight = 0;
        if (!Utilities.isTablet(getParentActivity()) && getParentActivity().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            viewHeight = AndroidUtilities.displaySize.y - AndroidUtilities.statusBarHeight - AndroidUtilities.dp(40);
        } else {
            viewHeight = AndroidUtilities.displaySize.y - AndroidUtilities.statusBarHeight - AndroidUtilities.dp(48);
        }

        int width = 0;
        int height = 0;
        if (getParentActivity().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            width = AndroidUtilities.displaySize.x - AndroidUtilities.displaySize.x / 2 - AndroidUtilities.dp(24);
            height = viewHeight - AndroidUtilities.dp(32);
        } else {
            width = AndroidUtilities.displaySize.x;
            height = viewHeight - AndroidUtilities.dp(176);
        }

        float wr = (float) width / (float) videoWidth;
        float hr = (float) height / (float) videoHeight;
        float ar = (float) videoWidth / (float) videoHeight;

        if (wr > hr) {
            width = (int) (height * ar);
        } else {
            height = (int) (width / ar);
        }

        surfaceHolder.setFixedSize(width, height);
    }

    private void fixLayout() {
        if (originalSizeTextView == null) {
            return;
        }
        originalSizeTextView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                originalSizeTextView.getViewTreeObserver().removeOnPreDrawListener(this);
                if (getParentActivity().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) videoContainerView.getLayoutParams();
                    layoutParams.topMargin = AndroidUtilities.dp(16);
                    layoutParams.bottomMargin = AndroidUtilities.dp(16);
                    layoutParams.width = AndroidUtilities.displaySize.x / 2 - AndroidUtilities.dp(24);
                    layoutParams.leftMargin = AndroidUtilities.dp(16);
                    videoContainerView.setLayoutParams(layoutParams);

                    layoutParams = (FrameLayout.LayoutParams) textContainerView.getLayoutParams();
                    layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
                    layoutParams.width = AndroidUtilities.displaySize.x / 2 - AndroidUtilities.dp(24);
                    layoutParams.leftMargin = AndroidUtilities.displaySize.x / 2 + AndroidUtilities.dp(8);
                    layoutParams.rightMargin = AndroidUtilities.dp(16);
                    layoutParams.topMargin = AndroidUtilities.dp(16);
                    textContainerView.setLayoutParams(layoutParams);
                } else {
                    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) videoContainerView.getLayoutParams();
                    layoutParams.topMargin = AndroidUtilities.dp(16);
                    layoutParams.bottomMargin = AndroidUtilities.dp(160);
                    layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
                    layoutParams.leftMargin = 0;
                    videoContainerView.setLayoutParams(layoutParams);

                    layoutParams = (FrameLayout.LayoutParams) textContainerView.getLayoutParams();
                    layoutParams.height = AndroidUtilities.dp(143);
                    layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
                    layoutParams.leftMargin = 0;
                    layoutParams.rightMargin = 0;
                    layoutParams.topMargin = 0;
                    textContainerView.setLayoutParams(layoutParams);
                }
                fixVideoSize();
                videoTimelineView.clearFrames();
                return false;
            }
        });
    }

    private void play() {
        if (videoPlayer.isPlaying()) {
            videoPlayer.pause();
            playButton.setImageResource(R.drawable.video_play);
        } else {
            try {
                playButton.setImageDrawable(null);
                lastProgress = 0;
                if (needSeek) {
                    float prog = videoTimelineView.getLeftProgress() + (videoTimelineView.getRightProgress() - videoTimelineView.getLeft()) * videoSeekBarView.getProgress();
                    videoPlayer.seekTo((int) (videoPlayer.getDuration() * prog));
                    needSeek = false;
                }
                videoPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
                    @Override
                    public void onSeekComplete(MediaPlayer mp) {
                        float startTime = videoTimelineView.getLeftProgress() * videoPlayer.getDuration();
                        float endTime = videoTimelineView.getRightProgress() * videoPlayer.getDuration();
                        if (startTime == endTime) {
                            startTime = endTime - 0.01f;
                        }
                        lastProgress = (videoPlayer.getCurrentPosition() - startTime) / (endTime - startTime);
                        videoSeekBarView.setProgress(lastProgress);
                    }
                });
                videoPlayer.start();
                new Thread(progressRunnable).start();
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }
    }

    public void setDelegate(VideoEditorActivityDelegate delegate) {
        this.delegate = delegate;
    }

    private int selectTrack(MediaExtractor extractor, boolean audio) {
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (audio) {
                if (mime.startsWith("audio/")) {
                    return i;
                }
            } else {
                if (mime.startsWith("video/")) {
                    return i;
                }
            }
        }
        return -5;
    }

    private static class VideoEditWrapper implements Runnable {
        private VideoEditorActivity mTest;
        private VideoEditWrapper(VideoEditorActivity test) {
            mTest = test;
        }

        @Override
        public void run() {
            mTest.startConvert2();
        }

        public static void runTest(final VideoEditorActivity obj) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        VideoEditWrapper wrapper = new VideoEditWrapper(obj);
                        Thread th = new Thread(wrapper, "encoder");
                        th.start();
                        th.join();
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                }
            }).start();
        }
    }

    private void didWriteData(final String videoPath, final boolean first, final long finalSize) {
        AndroidUtilities.RunOnUIThread(new Runnable() {
            @Override
            public void run() {
                if (first) {
                    delegate.didStartVideoConverting(videoPath, VideoEditorActivity.this.videoPath, esimatedFileSize, editedVideoDuration, editedVideoWidth, editedVideoHeight);
                } else {
                    delegate.didAppenedVideoData(videoPath, finalSize);
                }
            }
        });
    }

    private boolean startConvert2() {
        MediaCodec decoder = null;
        MediaCodec encoder = null;
        MediaExtractor extractor = null;
        InputSurface inputSurface = null;
        OutputSurface outputSurface = null;
        MP4Builder mediaMuxer = null;
        File cacheFile = null;
        long time = System.currentTimeMillis();
        boolean finished = true;
        boolean firstWrite = true;

        class AudioBufferTemp {
            ByteBuffer buffer;
            int flags;
            int size;
            long presentationTimeUs;
        }

        try {
            File inputFile = new File(videoPath);
            if (!inputFile.canRead()) {
                return false;
            }

            boolean outputDone = false;
            boolean inputDone = false;
            boolean decoderDone = false;
            boolean muxerStarted = false;
            int videoTrackIndex = -5;
            int audioTrackIndex = -5;
            int audioIndex = -5;
            int videoIndex = -5;
            int audioBufferSize = 0;
            ByteBuffer audioBuffer = null;
            ArrayList<AudioBufferTemp> audioBuffers = new ArrayList<AudioBufferTemp>();

            MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
            mediaMetadataRetriever.setDataSource(inputFile.toString());
            String rotation = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            int rotationValue = 0;
            if (rotation != null) {
                try {
                    rotationValue = Integer.parseInt(rotation);
                } catch (Exception e) {
                    //don't promt
                }
            }

            extractor = new MediaExtractor();
            extractor.setDataSource(inputFile.toString());

            String fileName = Integer.MIN_VALUE + "_" + UserConfig.lastLocalId + ".mp4";
            UserConfig.lastLocalId--;
            cacheFile = new File(AndroidUtilities.getCacheDir(), fileName);
            UserConfig.saveConfig(false);

            Mp4Movie movie = new Mp4Movie();
            movie.setCacheFile(cacheFile);
            movie.setRotation(rotationValue);
            movie.setSize(640, 360);
            mediaMuxer = new MP4Builder().createMovie(movie);

            videoIndex = selectTrack(extractor, false);
            if (videoIndex < 0) {
                return false;
            }
            extractor.selectTrack(videoIndex);
            MediaFormat inputFormat = extractor.getTrackFormat(videoIndex);
            String mime = inputFormat.getString(MediaFormat.KEY_MIME);

            audioIndex = selectTrack(extractor, true);
            if (audioIndex >= 0) {
                extractor.selectTrack(audioIndex);
                MediaFormat audioFormat = extractor.getTrackFormat(audioIndex);
                audioTrackIndex = mediaMuxer.addTrack(audioFormat, false);
                audioBufferSize = audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
            }

            MediaFormat outputFormat = MediaFormat.createVideoFormat(mime, 640, 360);
            outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, 1000000);
            outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 25);
            outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            encoder = MediaCodec.createEncoderByType(mime);
            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = new InputSurface(encoder.createInputSurface());
            inputSurface.makeCurrent();
            encoder.start();

            decoder = MediaCodec.createDecoderByType(mime);
            outputSurface = new OutputSurface();
            decoder.configure(inputFormat, outputSurface.getSurface(), null, 0);
            decoder.start();

            final int TIMEOUT_USEC = 10000;
            ByteBuffer[] decoderInputBuffers = decoder.getInputBuffers();
            ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

            while (!outputDone) {
                if (!inputDone) {
                    boolean eof = false;
                    int index = extractor.getSampleTrackIndex();
                    if (index == videoIndex) {
                        int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                        if (inputBufIndex >= 0) {
                            ByteBuffer inputBuf = decoderInputBuffers[inputBufIndex];
                            int chunkSize = extractor.readSampleData(inputBuf, 0);
                            if (chunkSize < 0) {
                                decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputDone = true;
                            } else {
                                decoder.queueInputBuffer(inputBufIndex, 0, chunkSize, extractor.getSampleTime(), 0);
                                extractor.advance();
                            }
                        }
                    } else if (index == audioIndex) {
                        if (audioBuffer == null) {
                            audioBuffer = ByteBuffer.allocate(audioBufferSize);
                        }
                        info.size = extractor.readSampleData(audioBuffer, 0);
                        if (info.size < 0) {
                            info.size = 0;
                            eof = true;
                        } else {
                            if (muxerStarted) {
                                info.offset = 0;
                                info.presentationTimeUs = extractor.getSampleTime();
                                info.flags = extractor.getSampleFlags();
                                mediaMuxer.writeSampleData(audioTrackIndex, audioBuffer, info);
                            } else {
                                AudioBufferTemp audioBufferTemp = new AudioBufferTemp();
                                audioBufferTemp.buffer = audioBuffer;
                                audioBufferTemp.presentationTimeUs = extractor.getSampleTime();
                                audioBufferTemp.flags = extractor.getSampleFlags();
                                audioBufferTemp.size = info.size;
                                audioBuffers.add(audioBufferTemp);
                                audioBuffer = null;
                            }
                            extractor.advance();
                        }
                    } else if (index == -1) {
                        eof = true;
                    }
                    if (eof) {
                        int inputBufIndex = decoder.dequeueInputBuffer(TIMEOUT_USEC);
                        if (inputBufIndex >= 0) {
                            decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        }
                    }
                }

                boolean decoderOutputAvailable = !decoderDone;
                boolean encoderOutputAvailable = true;
                while (decoderOutputAvailable || encoderOutputAvailable) {
                    int encoderStatus = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                    if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        encoderOutputAvailable = false;
                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        encoderOutputBuffers = encoder.getOutputBuffers();
                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat newFormat = encoder.getOutputFormat();
                        if (muxerStarted) {
                            throw new RuntimeException("format changed twice");
                        }
                        videoTrackIndex = mediaMuxer.addTrack(newFormat, true);

                        muxerStarted = true;
                        if (!audioBuffers.isEmpty()) {
                            for (AudioBufferTemp audioBufferTemp : audioBuffers) {
                                info.size = audioBufferTemp.size;
                                info.offset = 0;
                                info.presentationTimeUs = audioBufferTemp.presentationTimeUs;
                                info.flags = audioBufferTemp.flags;
                                mediaMuxer.writeSampleData(audioTrackIndex, audioBufferTemp.buffer, info);
                            }
                            audioBuffers.clear();
                        }
                    } else if (encoderStatus < 0) {
                        FileLog.e("tmessages", "unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
                        return false;
                    } else {
                        ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                        if (encodedData == null) {
                            FileLog.e("tmessages", "encoderOutputBuffer " + encoderStatus + " was null");
                            return false;
                        }
                        if (info.size != 0) {
                            if (!muxerStarted) {
                                throw new RuntimeException("muxer hasn't started");
                            }
                            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                encodedData.limit(info.size);
                                encodedData.position(info.offset);
                                encodedData.putInt(Integer.reverseBytes(info.size - 4));
                                mediaMuxer.writeSampleData(videoTrackIndex, encodedData, info);
                                didWriteData(cacheFile.toString(), firstWrite, 0);
                                if (firstWrite) {
                                    firstWrite = false;
                                }
                            }
                        }
                        outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                        encoder.releaseOutputBuffer(encoderStatus, false);
                    }
                    if (encoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER) {
                        continue;
                    }

                    if (!decoderDone) {
                        int decoderStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                        if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            decoderOutputAvailable = false;
                        } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {

                        } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            MediaFormat newFormat = decoder.getOutputFormat();
                        } else if (decoderStatus < 0) {
                            FileLog.e("tmessages", "unexpected result from decoder.dequeueOutputBuffer: " + decoderStatus);
                            return false;
                        } else {
                            boolean doRender = (info.size != 0);
                            decoder.releaseOutputBuffer(decoderStatus, doRender);
                            if (doRender) {
                                outputSurface.awaitNewImage();
                                outputSurface.drawImage();
                                inputSurface.setPresentationTime(info.presentationTimeUs * 1000);
                                inputSurface.swapBuffers();
                            }
                            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                FileLog.e("tmessages", "signaling input EOS");
                                //if (WORK_AROUND_BUGS) {
                                    // Bail early, possibly dropping a frame.
                                //    return;
                                //} else {
                                    encoder.signalEndOfInputStream();
                                //}
                            }
                        }
                    }
                }

                /*if (!outputDone) { without surface
                    int decoderStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                    if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        FileLog.e("tmessages", "no output from decoder available");
                    } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        FileLog.e("tmessages", "decoder output buffers changed");
                        decoderOutputBuffers = decoder.getOutputBuffers();
                    } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        decoderOutputFormat = decoder.getOutputFormat();
                        FileLog.e("tmessages", "decoder output format changed: " + decoderOutputFormat);
                    } else if (decoderStatus < 0) {
                        FileLog.e("tmessages", "unexpected result from decoder.dequeueOutputBuffer: " + decoderStatus);
                        return false;
                    } else {
                        ByteBuffer outputFrame = decoderOutputBuffers[decoderStatus];
                        outputFrame.position(info.offset);
                        outputFrame.limit(info.offset + info.size);
                        if (info.size == 0) {
                            FileLog.e("tmessages", "got empty frame");
                        } else {
                            FileLog.e("tmessages", "decoded, checking frame format = " + decoderOutputFormat + " size = " + outputFrame.limit());
                        }
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            FileLog.e("tmessages", "output EOS");
                            outputDone = true;
                        }
                        decoder.releaseOutputBuffer(decoderStatus, false);
                    }
                }*/
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            finished = false;
        } finally {
            if (outputSurface != null) {
                outputSurface.release();
                outputSurface = null;
            }
            if (inputSurface != null) {
                inputSurface.release();
                inputSurface = null;
            }
            if (decoder != null) {
                decoder.stop();
                decoder.release();
                decoder = null;
            }
            if (encoder != null) {
                encoder.stop();
                encoder.release();
                encoder = null;
            }
            if (extractor != null) {
                extractor.release();
                extractor = null;
            }
            if (mediaMuxer != null) {
                try {
                    mediaMuxer.finishMovie(false);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                mediaMuxer = null;
            }
            FileLog.e("tmessages", "time = " + (System.currentTimeMillis() - time));
        }
        if (finished) {
            didWriteData(cacheFile.toString(), firstWrite, cacheFile.length());
        }
        AndroidUtilities.RunOnUIThread(new Runnable() {
            @Override
            public void run() {
                finishFragment();
            }
        });
        return finished;
    }

    private void startConvert() throws Exception {
        IsoFile isoFile = new IsoFile(videoPath);
        TrackBox trackBox = (TrackBox) Path.getPath(isoFile, "/moov/trak/mdia/minf/stbl/stsd/avc1/../../../../../");
        AvcConfigurationBox avcConfigurationBox = (AvcConfigurationBox) Path.getPath(trackBox, "mdia/minf/stbl/stsd/avc1/avcC");
        avcConfigurationBox.parseDetails();

        Movie movie = MovieCreator.build(videoPath);

        List<Track> tracks = movie.getTracks();
        movie.setTracks(new LinkedList<Track>());

        double startTime = 0;
        double endTime = 0;

        for (Track track : tracks) {
            if (track.getSyncSamples() != null && track.getSyncSamples().length > 0) {
                double duration = (double) track.getDuration() / (double) track.getTrackMetaData().getTimescale();
                startTime = correctTimeToSyncSample(track, videoTimelineView.getLeftProgress() * duration, false);
                endTime = videoTimelineView.getRightProgress() * duration;
                break;
            }
        }

        for (Track track : tracks) {
            long currentSample = 0;
            double currentTime = 0;
            double lastTime = 0;
            long startSample = 0;
            long endSample = -1;

            for (int i = 0; i < track.getSampleDurations().length; i++) {
                long delta = track.getSampleDurations()[i];
                if (currentTime > lastTime && currentTime <= startTime) {
                    startSample = currentSample;
                }
                if (currentTime > lastTime && currentTime <= endTime) {
                    endSample = currentSample;
                }
                lastTime = currentTime;
                currentTime += (double) delta / (double) track.getTrackMetaData().getTimescale();
                currentSample++;
            }
            movie.addTrack(new CroppedTrack(track, startSample, endSample));
        }
        Container out = new DefaultMp4Builder().build(movie);

        String fileName = Integer.MIN_VALUE + "_" + UserConfig.lastLocalId + ".mp4";
        UserConfig.lastLocalId--;
        File cacheFile = new File(AndroidUtilities.getCacheDir(), fileName);
        UserConfig.saveConfig(false);

        FileOutputStream fos = new FileOutputStream(cacheFile);
        FileChannel fc = fos.getChannel();
        out.writeContainer(fc);

        fc.close();
        fos.close();
        if (delegate != null) {
            //delegate.didFinishedVideoConverting(cacheFile.getAbsolutePath());
            finishFragment();
        }
    }

    private static double correctTimeToSyncSample(Track track, double cutHere, boolean next) {
        double[] timeOfSyncSamples = new double[track.getSyncSamples().length];
        long currentSample = 0;
        double currentTime = 0;
        for (int i = 0; i < track.getSampleDurations().length; i++) {
            long delta = track.getSampleDurations()[i];
            if (Arrays.binarySearch(track.getSyncSamples(), currentSample + 1) >= 0) {
                timeOfSyncSamples[Arrays.binarySearch(track.getSyncSamples(), currentSample + 1)] = currentTime;
            }
            currentTime += (double) delta / (double) track.getTrackMetaData().getTimescale();
            currentSample++;
        }
        double previous = 0;
        for (double timeOfSyncSample : timeOfSyncSamples) {
            if (timeOfSyncSample > cutHere) {
                if (next) {
                    return timeOfSyncSample;
                } else {
                    return previous;
                }
            }
            previous = timeOfSyncSample;
        }
        return timeOfSyncSamples[timeOfSyncSamples.length - 1];
    }
}
