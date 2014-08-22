/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui;

import android.content.res.Configuration;
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

import com.coremedia.iso.boxes.Container;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.CroppedTrack;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
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
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class VideoEditorActivity extends BaseFragment implements SurfaceHolder.Callback {

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
    private float lastProgress = 0;
    private boolean needSeek = false;
    private VideoEditorActivityDelegate delegate;

    public interface VideoEditorActivityDelegate {
        public abstract void didFinishedVideoConverting(String videoPath);
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
                            startConvert();
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                    }
                }
            });

            ActionBarMenu menu = actionBarLayer.createMenu();
            View doneItem = menu.addItemResource(1, R.layout.group_create_done_layout);

            TextView doneTextView = (TextView)doneItem.findViewById(R.id.done_button);
            doneTextView.setText(LocaleController.getString("Done", R.string.Done).toUpperCase());

            fragmentView = inflater.inflate(R.layout.video_editor_layout, container, false);
            originalSizeTextView = (TextView)fragmentView.findViewById(R.id.original_size);
            editedSizeTextView = (TextView)fragmentView.findViewById(R.id.edited_size);
            videoContainerView = fragmentView.findViewById(R.id.video_container);
            textContainerView = fragmentView.findViewById(R.id.info_container);

            videoTimelineView = (VideoTimelineView)fragmentView.findViewById(R.id.video_timeline_view);
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
                        videoPlayer.seekTo((int)(videoPlayer.getDuration() * progress));
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
                        videoPlayer.seekTo((int)(videoPlayer.getDuration() * progress));
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                    needSeek = true;
                    videoSeekBarView.setProgress(0);
                    updateVideoEditedInfo();
                }
            });

            videoSeekBarView = (VideoSeekBarView)fragmentView.findViewById(R.id.video_seekbar);
            videoSeekBarView.delegate = new VideoSeekBarView.SeekBarDelegate() {
                @Override
                public void onSeekBarDrag(float progress) {
                    if (videoPlayer.isPlaying()) {
                        try {
                            float prog = videoTimelineView.getLeftProgress() + (videoTimelineView.getRightProgress() - videoTimelineView.getLeft()) * progress;
                            videoPlayer.seekTo((int)(videoPlayer.getDuration() * prog));
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

            playButton = (ImageView)fragmentView.findViewById(R.id.play_button);
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
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
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
        int seconds = (int)Math.ceil(videoPlayer.getDuration() / 1000) - minutes * 60;
        String videoTimeSize = String.format("%d:%02d, %s", minutes, seconds, Utilities.formatFileSize(file.length()));
        originalSizeTextView.setText(String.format("%s: %s, %s", LocaleController.getString("OriginalVideo", R.string.OriginalVideo), videoDimension, videoTimeSize));
    }

    private void updateVideoEditedInfo() {
        if (!initied || editedSizeTextView == null) {
            return;
        }
        File file = new File(videoPath);
        long size = file.length();
        float videoWidth = videoPlayer.getVideoWidth();
        float videoHeight = videoPlayer.getVideoHeight();
        if (videoWidth > 640 || videoHeight > 640) {
            float scale = videoWidth > videoHeight ? 640.0f / videoWidth : 640.0f / videoHeight;
            videoWidth *= scale;
            videoHeight *= scale;
            size *= (scale * scale);
        }
        String videoDimension = String.format("%dx%d", (int)videoWidth, (int)videoHeight);
        int minutes = videoPlayer.getDuration() / 1000 / 60;
        int seconds = (int)Math.ceil(videoPlayer.getDuration() / 1000) - minutes * 60;
        String videoTimeSize = String.format("%d:%02d, ~%s", minutes, seconds, Utilities.formatFileSize(size));
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

        float wr = (float)width / (float)videoWidth;
        float hr = (float)height / (float)videoHeight;
        float ar = (float)videoWidth / (float)videoHeight;

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
                    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)videoContainerView.getLayoutParams();
                    layoutParams.topMargin = AndroidUtilities.dp(16);
                    layoutParams.bottomMargin = AndroidUtilities.dp(16);
                    layoutParams.width = AndroidUtilities.displaySize.x / 2 - AndroidUtilities.dp(24);
                    layoutParams.leftMargin = AndroidUtilities.dp(16);
                    videoContainerView.setLayoutParams(layoutParams);

                    layoutParams = (FrameLayout.LayoutParams)textContainerView.getLayoutParams();
                    layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
                    layoutParams.width = AndroidUtilities.displaySize.x / 2 - AndroidUtilities.dp(24);
                    layoutParams.leftMargin = AndroidUtilities.displaySize.x / 2 + AndroidUtilities.dp(8);
                    layoutParams.rightMargin = AndroidUtilities.dp(16);
                    layoutParams.topMargin = AndroidUtilities.dp(16);
                    textContainerView.setLayoutParams(layoutParams);
                } else {
                    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)videoContainerView.getLayoutParams();
                    layoutParams.topMargin = AndroidUtilities.dp(16);
                    layoutParams.bottomMargin = AndroidUtilities.dp(160);
                    layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
                    layoutParams.leftMargin = 0;
                    videoContainerView.setLayoutParams(layoutParams);

                    layoutParams = (FrameLayout.LayoutParams)textContainerView.getLayoutParams();
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
                    videoPlayer.seekTo((int)(videoPlayer.getDuration() * prog));
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

    private void startConvert() throws Exception {
        Movie movie = MovieCreator.build(videoPath);

        List<Track> tracks = movie.getTracks();
        movie.setTracks(new LinkedList<Track>());

        double startTime = 0;
        double endTime = 0;

        for (Track track : tracks) {
            if (track.getSyncSamples() != null && track.getSyncSamples().length > 0) {
                double duration = (double)track.getDuration() / (double)track.getTrackMetaData().getTimescale();
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
            delegate.didFinishedVideoConverting(cacheFile.getAbsolutePath());
            finishFragment();
        }
    }

//    private void startEncodeVideo() {
//        MediaExtractor mediaExtractor = new MediaExtractor();
//        mediaExtractor.s
//    }

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
