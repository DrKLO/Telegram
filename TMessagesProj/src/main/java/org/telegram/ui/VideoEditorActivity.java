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
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.coremedia.iso.IsoFile;
import com.coremedia.iso.boxes.Box;
import com.coremedia.iso.boxes.Container;
import com.coremedia.iso.boxes.MediaBox;
import com.coremedia.iso.boxes.MediaHeaderBox;
import com.coremedia.iso.boxes.SampleSizeBox;
import com.coremedia.iso.boxes.TrackBox;
import com.coremedia.iso.boxes.TrackHeaderBox;
import com.coremedia.iso.boxes.h264.AvcConfigurationBox;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.CroppedTrack;
import com.googlecode.mp4parser.util.Matrix;
import com.googlecode.mp4parser.util.Path;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import org.telegram.android.video.InputSurface;
import org.telegram.android.video.MP4Builder;
import org.telegram.android.video.Mp4Movie;
import org.telegram.android.video.OutputSurface;
import org.telegram.messenger.FileLoader;
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
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

@TargetApi(16)
public class VideoEditorActivity extends BaseFragment implements TextureView.SurfaceTextureListener {

    private final static int OMX_TI_COLOR_FormatYUV420PackedSemiPlanar = 0x7F000100;
    private final static int OMX_QCOM_COLOR_FormatYVU420SemiPlanar = 0x7FA30C00;
    private final static int OMX_QCOM_COLOR_FormatYUV420PackedSemiPlanar64x32Tile2m8ka = 0x7FA30C03;
    private final static int OMX_SEC_COLOR_FormatNV12Tiled = 0x7FC00002;
    private final static int OMX_QCOM_COLOR_FormatYUV420PackedSemiPlanar32m = 0x7FA30C04;
    private final static String MIME_TYPE = "video/avc";

    private final static int PROCESSOR_TYPE_OTHER = 0;
    private final static int PROCESSOR_TYPE_QCOM = 1;

    private MediaPlayer videoPlayer = null;
    private VideoTimelineView videoTimelineView = null;
    private View videoContainerView = null;
    private TextView originalSizeTextView = null;
    private TextView editedSizeTextView = null;
    private View textContainerView = null;
    private ImageView playButton = null;
    private VideoSeekBarView videoSeekBarView = null;
    private TextureView textureView = null;
    private View controlView = null;

    private String videoPath = null;
    private float lastProgress = 0;
    private boolean needSeek = false;
    private VideoEditorActivityDelegate delegate;

    private boolean firstWrite = true;
    //MediaMetadataRetriever TODO

    private int rotationValue = 0;
    private int originalWidth = 0;
    private int originalHeight = 0;
    private int resultWidth = 0;
    private int resultHeight = 0;
    private int bitrate = 0;
    private float videoDuration = 0;
    private long startTime = 0;
    private long endTime = 0;
    private int audioFramesSize = 0;
    private int videoFramesSize = 0;
    private int estimatedSize = 0;
    private long esimatedDuration = 0;

    public interface VideoEditorActivityDelegate {
        public abstract void didStartVideoConverting(String videoPath, String originalPath, long estimatedSize, int duration, int width, int height);
        public abstract void didAppenedVideoData(String videoPath, long finalSize);
    }

    private Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            while (videoPlayer != null && videoPlayer.isPlaying()) {
                AndroidUtilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (videoPlayer.isPlaying()) {
                            float startTime = videoTimelineView.getLeftProgress() * videoDuration;
                            float endTime = videoTimelineView.getRightProgress() * videoDuration;
                            if (startTime == endTime) {
                                startTime = endTime - 0.01f;
                            }
                            float progress = (videoPlayer.getCurrentPosition() - startTime) / (endTime - startTime);
                            float lrdiff = videoTimelineView.getRightProgress() - videoTimelineView.getLeftProgress();
                            progress = videoTimelineView.getLeftProgress() + lrdiff * progress;
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
        if (videoPath == null || !processOpenVideo()) {
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
                        if (videoPlayer != null) {
                            try {
                                videoPlayer.stop();
                                videoPlayer.release();
                                videoPlayer = null;
                            } catch (Exception e) {
                                FileLog.e("tmessages", e);
                            }
                        }
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
            controlView = fragmentView.findViewById(R.id.control_layout);
            TextView titleTextView = (TextView) fragmentView.findViewById(R.id.original_title);
            titleTextView.setText(LocaleController.getString("OriginalVideo", R.string.OriginalVideo));
            titleTextView = (TextView) fragmentView.findViewById(R.id.edited_title);
            titleTextView.setText(LocaleController.getString("EditedVideo", R.string.EditedVideo));

            videoTimelineView = (VideoTimelineView) fragmentView.findViewById(R.id.video_timeline_view);
            videoTimelineView.setVideoPath(videoPath);
            videoTimelineView.setDelegate(new VideoTimelineView.VideoTimelineViewDelegate() {
                @Override
                public void onLeftProgressChanged(float progress) {
                    if (videoPlayer == null) {
                        return;
                    }
                    try {
                        if (videoPlayer.isPlaying()) {
                            videoPlayer.pause();
                            playButton.setImageResource(R.drawable.video_play);
                        }
                        videoPlayer.setOnSeekCompleteListener(null);
                        videoPlayer.seekTo((int) (videoDuration * progress));
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                    needSeek = true;
                    videoSeekBarView.setProgress(videoTimelineView.getLeftProgress());
                    updateVideoEditedInfo();
                }

                @Override
                public void onRifhtProgressChanged(float progress) {
                    if (videoPlayer == null) {
                        return;
                    }
                    try {
                        if (videoPlayer.isPlaying()) {
                            videoPlayer.pause();
                            playButton.setImageResource(R.drawable.video_play);
                        }
                        videoPlayer.setOnSeekCompleteListener(null);
                        videoPlayer.seekTo((int) (videoDuration * progress));
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                    needSeek = true;
                    videoSeekBarView.setProgress(videoTimelineView.getLeftProgress());
                    updateVideoEditedInfo();
                }
            });

            videoSeekBarView = (VideoSeekBarView) fragmentView.findViewById(R.id.video_seekbar);
            videoSeekBarView.delegate = new VideoSeekBarView.SeekBarDelegate() {
                @Override
                public void onSeekBarDrag(float progress) {
                    if (videoPlayer == null) {
                        return;
                    }
                    if (videoPlayer.isPlaying()) {
                        try {
                            float prog = videoTimelineView.getLeftProgress() + (videoTimelineView.getRightProgress() - videoTimelineView.getLeft()) * progress;
                            videoPlayer.seekTo((int) (videoDuration * prog));
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
                    play();
                }
            });

            textureView = (TextureView) fragmentView.findViewById(R.id.video_view);
            textureView.setSurfaceTextureListener(this);

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
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        if (videoPlayer == null) {
            return;
        }
        try {
            Surface s = new Surface(surface);
            videoPlayer.setSurface(s);
            videoPlayer.setDataSource(videoPath);
            videoPlayer.prepare();
            videoPlayer.seekTo((int) (videoTimelineView.getLeftProgress() * videoDuration));
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (videoPlayer == null) {
            return true;
        }
        videoPlayer.setDisplay(null);
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    private void onPlayComplete() {
        playButton.setImageResource(R.drawable.video_play);
        videoSeekBarView.setProgress(videoTimelineView.getLeftProgress());
        try {
            if (videoPlayer != null) {
                videoPlayer.seekTo((int) (videoTimelineView.getLeftProgress() * videoDuration));
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    private void updateVideoOriginalInfo() {
        if (originalSizeTextView == null) {
            return;
        }
        File file = new File(videoPath);
        int width = rotationValue == 90 || rotationValue == 270 ? originalHeight : originalWidth;
        int height = rotationValue == 90 || rotationValue == 270 ? originalWidth : originalHeight;
        String videoDimension = String.format("%dx%d", width, height);
        int minutes = (int)(videoDuration / 1000 / 60);
        int seconds = (int) Math.ceil(videoDuration / 1000) - minutes * 60;
        String videoTimeSize = String.format("%d:%02d, %s", minutes, seconds, Utilities.formatFileSize(file.length()));
        originalSizeTextView.setText(String.format("%s, %s", videoDimension, videoTimeSize));
    }

    private void updateVideoEditedInfo() {
        if (editedSizeTextView == null) {
            return;
        }
        int width = rotationValue == 90 || rotationValue == 270 ? resultHeight : resultWidth;
        int height = rotationValue == 90 || rotationValue == 270 ? resultWidth : resultHeight;
        String videoDimension = String.format("%dx%d", resultWidth, resultHeight);

        esimatedDuration = (long)Math.max(1000, (videoTimelineView.getRightProgress() - videoTimelineView.getLeftProgress()) * videoDuration);
        estimatedSize = calculateEstimatedSize((float)esimatedDuration / videoDuration);
        if (videoTimelineView.getLeftProgress() == 0) {
            startTime = -1;
        } else {
            startTime = (long) (videoTimelineView.getLeftProgress() * videoDuration) * 1000;
        }
        if (videoTimelineView.getRightProgress() == 1) {
            endTime = -1;
        } else {
            endTime = (long) (videoTimelineView.getRightProgress() * videoDuration) * 1000;
        }

        int minutes = (int)(esimatedDuration / 1000 / 60);
        int seconds = (int) Math.ceil(esimatedDuration / 1000) - minutes * 60;
        String videoTimeSize = String.format("%d:%02d, ~%s", minutes, seconds, Utilities.formatFileSize(estimatedSize));
        editedSizeTextView.setText(String.format("%s, %s", videoDimension, videoTimeSize));
    }

    private void fixVideoSize() {
        if (fragmentView == null || getParentActivity() == null) {
            return;
        }
        int viewHeight = 0;
        if (AndroidUtilities.isTablet()) {
            viewHeight = AndroidUtilities.dp(472);
        } else {
            if (!AndroidUtilities.isTablet() && getParentActivity().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                viewHeight = AndroidUtilities.displaySize.y - AndroidUtilities.statusBarHeight - AndroidUtilities.dp(40);
            } else {
                viewHeight = AndroidUtilities.displaySize.y - AndroidUtilities.statusBarHeight - AndroidUtilities.dp(48);
            }
        }

        int width = 0;
        int height = 0;
        if (AndroidUtilities.isTablet()) {
            width = AndroidUtilities.dp(490);
            height = viewHeight - AndroidUtilities.dp(276);
        } else {
            if (getParentActivity().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                width = AndroidUtilities.displaySize.x / 3 - AndroidUtilities.dp(24);
                height = viewHeight - AndroidUtilities.dp(32);
            } else {
                width = AndroidUtilities.displaySize.x;
                height = viewHeight - AndroidUtilities.dp(276);
            }
        }

        int aWidth = width;
        int aHeight = height;
        int vwidth = rotationValue == 90 || rotationValue == 270 ? originalHeight : originalWidth;
        int vheight = rotationValue == 90 || rotationValue == 270 ? originalWidth : originalHeight;
        float wr = (float) width / (float) vwidth;
        float hr = (float) height / (float) vheight;
        float ar = (float) vwidth / (float) vheight;

        if (wr > hr) {
            width = (int) (height * ar);
        } else {
            height = (int) (width / ar);
        }

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)textureView.getLayoutParams();
        layoutParams.width = width;
        layoutParams.height = height;
        layoutParams.leftMargin = 0;
        layoutParams.topMargin = 0;
        textureView.setLayoutParams(layoutParams);
    }

    private void fixLayout() {
        if (originalSizeTextView == null) {
            return;
        }
        originalSizeTextView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                originalSizeTextView.getViewTreeObserver().removeOnPreDrawListener(this);
                if (!AndroidUtilities.isTablet() && getParentActivity().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) videoContainerView.getLayoutParams();
                    layoutParams.topMargin = AndroidUtilities.dp(16);
                    layoutParams.bottomMargin = AndroidUtilities.dp(16);
                    layoutParams.width = AndroidUtilities.displaySize.x / 3 - AndroidUtilities.dp(24);
                    layoutParams.leftMargin = AndroidUtilities.dp(16);
                    videoContainerView.setLayoutParams(layoutParams);

                    layoutParams = (FrameLayout.LayoutParams) controlView.getLayoutParams();
                    layoutParams.topMargin = AndroidUtilities.dp(16);
                    layoutParams.bottomMargin = 0;
                    layoutParams.width = AndroidUtilities.displaySize.x / 3 * 2 - AndroidUtilities.dp(32);
                    layoutParams.leftMargin = AndroidUtilities.displaySize.x / 3 + AndroidUtilities.dp(16);
                    layoutParams.gravity = Gravity.TOP;
                    controlView.setLayoutParams(layoutParams);

                    layoutParams = (FrameLayout.LayoutParams) textContainerView.getLayoutParams();
                    layoutParams.width = AndroidUtilities.displaySize.x / 3 * 2 - AndroidUtilities.dp(32);
                    layoutParams.leftMargin = AndroidUtilities.displaySize.x / 3 + AndroidUtilities.dp(16);
                    layoutParams.rightMargin = AndroidUtilities.dp(16);
                    layoutParams.bottomMargin = AndroidUtilities.dp(16);
                    textContainerView.setLayoutParams(layoutParams);
                } else {
                    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) videoContainerView.getLayoutParams();
                    layoutParams.topMargin = AndroidUtilities.dp(16);
                    layoutParams.bottomMargin = AndroidUtilities.dp(260);
                    layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
                    layoutParams.leftMargin = 0;
                    videoContainerView.setLayoutParams(layoutParams);

                    layoutParams = (FrameLayout.LayoutParams) controlView.getLayoutParams();
                    layoutParams.topMargin = 0;
                    layoutParams.leftMargin = 0;
                    layoutParams.bottomMargin = AndroidUtilities.dp(150);
                    layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
                    layoutParams.gravity = Gravity.BOTTOM;
                    controlView.setLayoutParams(layoutParams);

                    layoutParams = (FrameLayout.LayoutParams) textContainerView.getLayoutParams();
                    layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
                    layoutParams.leftMargin = AndroidUtilities.dp(16);
                    layoutParams.rightMargin = AndroidUtilities.dp(16);
                    layoutParams.bottomMargin = AndroidUtilities.dp(16);
                    textContainerView.setLayoutParams(layoutParams);
                }
                fixVideoSize();
                videoTimelineView.clearFrames();
                return false;
            }
        });
    }

    private void play() {
        if (videoPlayer == null) {
            return;
        }
        if (videoPlayer.isPlaying()) {
            videoPlayer.pause();
            playButton.setImageResource(R.drawable.video_play);
        } else {
            try {
                playButton.setImageDrawable(null);
                lastProgress = 0;
                if (needSeek) {
                    float prog = videoTimelineView.getLeftProgress() + (videoTimelineView.getRightProgress() - videoTimelineView.getLeft()) * videoSeekBarView.getProgress();
                    videoPlayer.seekTo((int) (videoDuration * prog));
                    needSeek = false;
                }
                videoPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
                    @Override
                    public void onSeekComplete(MediaPlayer mp) {
                        float startTime = videoTimelineView.getLeftProgress() * videoDuration;
                        float endTime = videoTimelineView.getRightProgress() * videoDuration;
                        if (startTime == endTime) {
                            startTime = endTime - 0.01f;
                        }
                        lastProgress = (videoPlayer.getCurrentPosition() - startTime) / (endTime - startTime);
                        float lrdiff = videoTimelineView.getRightProgress() - videoTimelineView.getLeftProgress();
                        lastProgress = videoTimelineView.getLeftProgress() + lrdiff * lastProgress;
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

    private void didWriteData(final String videoPath, final long finalSize) {
        AndroidUtilities.RunOnUIThread(new Runnable() {
            @Override
            public void run() {
                if (firstWrite) {
                    delegate.didStartVideoConverting(videoPath, VideoEditorActivity.this.videoPath, estimatedSize, (int)esimatedDuration, resultWidth, resultHeight);
                    firstWrite = false;
                    finishFragment();
                } else {
                    delegate.didAppenedVideoData(videoPath, finalSize);
                }
            }
        });
    }

    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                if (type.equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                return false;
        }
    }

    private static int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isRecognizedFormat(colorFormat)) {
                return colorFormat;
            }
        }
        return 0;
    }

    private long readAndWriteTrack(MediaExtractor extractor, MP4Builder mediaMuxer, MediaCodec.BufferInfo info, long start, long end, File file, boolean isAudio) throws Exception {
        int trackIndex = selectTrack(extractor, isAudio);
        if (trackIndex >= 0) {
            extractor.selectTrack(trackIndex);
            MediaFormat trackFormat = extractor.getTrackFormat(trackIndex);
            int muxerTrackIndex = mediaMuxer.addTrack(trackFormat, isAudio);
            int maxBufferSize = trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
            boolean inputDone = false;
            if (start > 0) {
                extractor.seekTo(start, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            } else {
                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            }
            ByteBuffer buffer = ByteBuffer.allocateDirect(maxBufferSize);
            long startTime = -1;

            while (!inputDone) {
                boolean eof = false;
                int index = extractor.getSampleTrackIndex();
                if (index == trackIndex) {
                    info.size = extractor.readSampleData(buffer, 0);

                    if (info.size < 0) {
                        info.size = 0;
                        eof = true;
                    } else {
                        info.presentationTimeUs = extractor.getSampleTime();
                        if (start > 0 && startTime == -1) {
                            startTime = info.presentationTimeUs;
                        }
                        if (end < 0 || info.presentationTimeUs < end) {
                            info.offset = 0;
                            info.flags = extractor.getSampleFlags();
                            if (!isAudio) {
                                buffer.limit(info.offset + info.size);
                                buffer.position(info.offset);
                                buffer.putInt(info.size - 4);
                            }
                            if (mediaMuxer.writeSampleData(muxerTrackIndex, buffer, info)) {
                                didWriteData(file.toString(), 0);
                            }
                            extractor.advance();
                        } else {
                            eof = true;
                        }
                    }
                } else if (index == -1) {
                    eof = true;
                }
                if (eof) {
                    inputDone = true;
                }
            }

            extractor.unselectTrack(trackIndex);
            return startTime;
        }
        return -1;
    }

    private boolean processOpenVideo() {
        try {
            IsoFile isoFile = new IsoFile(videoPath);
            List<Box> boxes = Path.getPaths(isoFile, "/moov/trak/");
            TrackHeaderBox trackHeaderBox = null;
            for (Box box : boxes) {
                TrackBox trackBox = (TrackBox)box;
                int sampleSizes = 0;
                int trackBitrate = 0;
                try {
                    MediaBox mediaBox = trackBox.getMediaBox();
                    MediaHeaderBox mediaHeaderBox = mediaBox.getMediaHeaderBox();
                    SampleSizeBox sampleSizeBox = mediaBox.getMediaInformationBox().getSampleTableBox().getSampleSizeBox();
                    for (long size : sampleSizeBox.getSampleSizes()) {
                        sampleSizes += size;
                    }
                    videoDuration = mediaHeaderBox.getDuration() / mediaHeaderBox.getTimescale();
                    trackBitrate = (int)(sampleSizes * 8 / videoDuration);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                TrackHeaderBox headerBox = trackBox.getTrackHeaderBox();
                if (headerBox.getWidth() != 0 && headerBox.getHeight() != 0) {
                    trackHeaderBox = headerBox;
                    bitrate = trackBitrate / 100000 * 100000;
                    if (bitrate > 900000) {
                        bitrate = 900000;
                    }
                    videoFramesSize += sampleSizes;
                } else {
                    audioFramesSize += sampleSizes;
                }
            }
            if (trackHeaderBox == null) {
                return false;
            }

            Matrix matrix = trackHeaderBox.getMatrix();
            if (matrix.equals(Matrix.ROTATE_90)) {
                rotationValue = 90;
            } else if (matrix.equals(Matrix.ROTATE_180)) {
                rotationValue = 180;
            } else if (matrix.equals(Matrix.ROTATE_270)) {
                rotationValue = 270;
            }
            resultWidth = originalWidth = (int)trackHeaderBox.getWidth();
            resultHeight = originalHeight = (int)trackHeaderBox.getHeight();

            if (resultWidth > 640 || resultHeight > 640) {
                float scale = resultWidth > resultHeight ? 640.0f / resultWidth : 640.0f / resultHeight;
                resultWidth *= scale;
                resultHeight *= scale;
                if (bitrate != 0) {
                    bitrate *= scale;
                    videoFramesSize = (int)(bitrate / 8 * videoDuration);
                }
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            return false;
        }

        videoDuration *= 1000;

        updateVideoOriginalInfo();
        updateVideoEditedInfo();

        return true;
    }

    private int calculateEstimatedSize(float timeDelta) {
        int size = (int)((audioFramesSize + videoFramesSize) * timeDelta);
        size += size / (32 * 1024) * 16;
        return size;
    }

    private boolean startConvert2() {
        File inputFile = new File(videoPath);
        if (!inputFile.canRead()) {
            return false;
        }

        firstWrite = true;
        File cacheFile = null;
        boolean error = false;
        long videoStartTime = startTime;

        long time = System.currentTimeMillis();

        if (resultWidth != 0 && resultHeight != 0) {
            MP4Builder mediaMuxer = null;
            MediaExtractor extractor = null;

            MediaCodec decoder = null;
            MediaCodec encoder = null;
            InputSurface inputSurface = null;
            OutputSurface outputSurface = null;

            try {
                String fileName = Integer.MIN_VALUE + "_" + UserConfig.lastLocalId + ".mp4";
                UserConfig.lastLocalId--;
                cacheFile = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
                UserConfig.saveConfig(false);

                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                Mp4Movie movie = new Mp4Movie();
                movie.setCacheFile(cacheFile);
                movie.setRotation(rotationValue);
                movie.setSize(resultWidth, resultHeight);
                mediaMuxer = new MP4Builder().createMovie(movie);
                extractor = new MediaExtractor();
                extractor.setDataSource(inputFile.toString());

                if (resultWidth != originalWidth || resultHeight != originalHeight) {
                    int videoIndex = -5;
                    videoIndex = selectTrack(extractor, false);
                    if (videoIndex >= 0) {
                        boolean outputDone = false;
                        boolean inputDone = false;
                        boolean decoderDone = false;
                        int swapUV = 0;
                        int videoTrackIndex = -5;
                        long videoTime = -1;

                        int colorFormat = 0;
                        int processorType = PROCESSOR_TYPE_OTHER;
                        if (Build.VERSION.SDK_INT < 18) {
                            MediaCodecInfo codecInfo = selectCodec(MIME_TYPE);
                            colorFormat = selectColorFormat(codecInfo, MIME_TYPE);
                            if (codecInfo.getName().contains("OMX.qcom.")) {
                                processorType = PROCESSOR_TYPE_QCOM;
                                if (Build.MANUFACTURER.toLowerCase().equals("nokia")) {
                                    swapUV = 1;
                                }
                            }
                            FileLog.e("tmessages", "codec = " + codecInfo.getName());
                        } else {
                            colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
                        }
                        FileLog.e("tmessages", "colorFormat = " + colorFormat);

                        int resultHeightAligned = resultHeight;
                        int padding = 0;
                        int bufferSize = resultWidth * resultHeight * 3 / 2;
                        if (processorType == PROCESSOR_TYPE_OTHER) {
                            if (resultHeight % 16 != 0) {
                                resultHeightAligned += (16 - (resultHeight % 16));
                                padding = resultWidth * (resultHeightAligned - resultHeight);
                                bufferSize += padding * 5 / 4;
                            }
                        } else if (processorType == PROCESSOR_TYPE_QCOM) {
                            int uvoffset = (resultWidth * resultHeight + 2047) & ~2047;
                            padding = uvoffset - (resultWidth * resultHeight);
                            bufferSize += padding;
                        }

                        extractor.selectTrack(videoIndex);
                        if (startTime > 0) {
                            extractor.seekTo(startTime, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                        } else {
                            extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                        }
                        MediaFormat inputFormat = extractor.getTrackFormat(videoIndex);

                        MediaFormat outputFormat = MediaFormat.createVideoFormat(MIME_TYPE, resultWidth, resultHeight);
                        outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
                        outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate != 0 ? bitrate : 921600);
                        outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 25);
                        outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);
                        if (Build.VERSION.SDK_INT < 18) {
                            outputFormat.setInteger("stride", resultWidth);
                            outputFormat.setInteger("slice-height", resultHeightAligned);
                        }

                        encoder = MediaCodec.createEncoderByType(MIME_TYPE);
                        encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                        if (Build.VERSION.SDK_INT >= 18) {
                            inputSurface = new InputSurface(encoder.createInputSurface());
                            inputSurface.makeCurrent();
                        }
                        encoder.start();

                        decoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME));
                        if (Build.VERSION.SDK_INT >= 18) {
                            outputSurface = new OutputSurface();
                        } else {
                            outputSurface = new OutputSurface(resultWidth, resultHeight);
                        }
                        decoder.configure(inputFormat, outputSurface.getSurface(), null, 0);
                        decoder.start();

                        final int TIMEOUT_USEC = 2500;
                        ByteBuffer[] decoderInputBuffers = decoder.getInputBuffers();
                        ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();
                        ByteBuffer[] encoderInputBuffers = null;
                        if (Build.VERSION.SDK_INT < 18) {
                            encoderInputBuffers = encoder.getInputBuffers();
                        }

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
                                    if (videoTrackIndex == -5) {
                                        videoTrackIndex = mediaMuxer.addTrack(newFormat, false);
                                    }
                                } else if (encoderStatus < 0) {
                                    throw new RuntimeException("unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
                                } else {
                                    ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                                    if (encodedData == null) {
                                        throw new RuntimeException("encoderOutputBuffer " + encoderStatus + " was null");
                                    }
                                    if (info.size > 1) {
                                        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                            encodedData.limit(info.offset + info.size);
                                            encodedData.position(info.offset);
                                            encodedData.putInt(Integer.reverseBytes(info.size - 4));
                                            if (mediaMuxer.writeSampleData(videoTrackIndex, encodedData, info)) {
                                                didWriteData(cacheFile.toString(), 0);
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

                                            MediaFormat newFormat = MediaFormat.createVideoFormat(MIME_TYPE, resultWidth, resultHeight);
                                            if (sps != null && pps != null) {
                                                newFormat.setByteBuffer("csd-0", sps);
                                                newFormat.setByteBuffer("csd-1", pps);
                                            }
                                            videoTrackIndex = mediaMuxer.addTrack(newFormat, false);
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
                                        throw new RuntimeException("unexpected result from decoder.dequeueOutputBuffer: " + decoderStatus);
                                    } else {
                                        boolean doRender = false;
                                        if (Build.VERSION.SDK_INT >= 18) {
                                            doRender = info.size != 0;
                                        } else {
                                            doRender = info.size != 0 || info.presentationTimeUs != 0;
                                        }
                                        if (endTime > 0 && info.presentationTimeUs >= endTime) {
                                            inputDone = true;
                                            decoderDone = true;
                                            doRender = false;
                                            info.flags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                                        }
                                        if (startTime > 0 && videoTime == -1) {
                                            if (info.presentationTimeUs < startTime) {
                                                doRender = false;
                                                FileLog.e("tmessages", "drop frame startTime = " + startTime + " present time = " + info.presentationTimeUs);
                                            } else {
                                                videoTime = info.presentationTimeUs;
                                            }
                                        }
                                        decoder.releaseOutputBuffer(decoderStatus, doRender);
                                        if (doRender) {
                                            boolean errorWait = false;
                                            try {
                                                outputSurface.awaitNewImage();
                                            } catch (Exception e) {
                                                errorWait = true;
                                                FileLog.e("tmessages", e);
                                            }
                                            if (!errorWait) {
                                                if (Build.VERSION.SDK_INT >= 18) {
                                                    outputSurface.drawImage(false);
                                                    inputSurface.setPresentationTime(info.presentationTimeUs * 1000);
                                                    inputSurface.swapBuffers();
                                                } else {
                                                    int inputBufIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC);
                                                    if (inputBufIndex >= 0) {
                                                        outputSurface.drawImage(true);
                                                        ByteBuffer rgbBuf = outputSurface.getFrame();
                                                        ByteBuffer yuvBuf = encoderInputBuffers[inputBufIndex];
                                                        yuvBuf.clear();
                                                        Utilities.convertVideoFrame(rgbBuf, yuvBuf, colorFormat, resultWidth, resultHeight, padding, swapUV);
                                                        encoder.queueInputBuffer(inputBufIndex, 0, bufferSize, info.presentationTimeUs, 0);
                                                    } else {
                                                        FileLog.e("tmessages", "input buffer not available");
                                                    }
                                                }
                                            }
                                        }
                                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                            decoderOutputAvailable = false;
                                            FileLog.e("tmessages", "decoder stream end");
                                            if (Build.VERSION.SDK_INT >= 18) {
                                                encoder.signalEndOfInputStream();
                                            } else {
                                                int inputBufIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC);
                                                if (inputBufIndex >= 0) {
                                                    encoder.queueInputBuffer(inputBufIndex, 0, 1, info.presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        extractor.unselectTrack(videoIndex);
                        if (videoTime != -1) {
                            videoStartTime = videoTime;
                        }
                    }
                } else {
                    long videoTime = readAndWriteTrack(extractor, mediaMuxer, info, startTime, endTime, cacheFile, false);
                    if (videoTime != -1) {
                        videoStartTime = videoTime;
                    }
                }
                readAndWriteTrack(extractor, mediaMuxer, info, videoStartTime, endTime, cacheFile, true);
            } catch (Exception e) {
                error = true;
                FileLog.e("tmessages", e);
            } finally {
                if (extractor != null) {
                    extractor.release();
                    extractor = null;
                }
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
        } else {
            return false;
        }
        if (!error && cacheFile != null) {
            didWriteData(cacheFile.toString(), cacheFile.length());
        }
        return true;
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
        File cacheFile = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), fileName);
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
