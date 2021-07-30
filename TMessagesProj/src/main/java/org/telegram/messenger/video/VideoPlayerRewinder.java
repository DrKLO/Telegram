package org.telegram.messenger.video;

import com.google.android.exoplayer2.C;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.VideoForwardDrawable;
import org.telegram.ui.Components.VideoPlayer;

public class VideoPlayerRewinder {

    public int rewindCount;
    private boolean rewindForward;
    public boolean rewindByBackSeek;
    private long startRewindFrom;
    private Runnable updateRewindRunnable;
    private long rewindLastTime;
    private long rewindLastUpdatePlayerTime;
    private long rewindBackSeekPlayerPosition;
    private float playSpeed = 1f;

    private VideoPlayer videoPlayer;

    private final Runnable backSeek = new Runnable() {
        @Override
        public void run() {
            if (videoPlayer == null) {
                return;
            }
            long duration = videoPlayer.getDuration();
            if (duration == 0 || duration == C.TIME_UNSET) {
                rewindLastTime = System.currentTimeMillis();
                return;
            }

            long t = System.currentTimeMillis();
            long dt = t - rewindLastTime;
            rewindLastTime = t;
            if (rewindCount == 1) {
                dt *= 3;
            } else if (rewindCount == 2) {
                dt *= 6;
            } else {
                dt *= 12;
            }
            if (rewindForward) {
                rewindBackSeekPlayerPosition += dt;
            } else {
                rewindBackSeekPlayerPosition -= dt;
            }
            if (rewindBackSeekPlayerPosition < 0) {
                rewindBackSeekPlayerPosition = 0;
            } else if (rewindBackSeekPlayerPosition > duration) {
                rewindBackSeekPlayerPosition = duration;
            }
            if (rewindByBackSeek && videoPlayer != null && rewindLastTime - rewindLastUpdatePlayerTime > 350) {
                rewindLastUpdatePlayerTime = rewindLastTime;
                videoPlayer.seekTo(rewindBackSeekPlayerPosition);
            }

            if (videoPlayer != null) {
                long timeDiff = rewindBackSeekPlayerPosition - startRewindFrom;
                float progress = rewindBackSeekPlayerPosition / (float) videoPlayer.getDuration();
                updateRewindProgressUi(timeDiff, progress, rewindByBackSeek);
            }

            if (rewindBackSeekPlayerPosition == 0 || rewindBackSeekPlayerPosition >= duration) {
                if (rewindByBackSeek && videoPlayer != null) {
                    rewindLastUpdatePlayerTime = rewindLastTime;
                    videoPlayer.seekTo(rewindBackSeekPlayerPosition);
                }
                cancelRewind();
            }
            if (rewindCount > 0) {
                AndroidUtilities.runOnUIThread(backSeek, 16);
            }
        }
    };

    public void startRewind(VideoPlayer videoPlayer, boolean forward, float playbackSpeed) {
        this.videoPlayer = videoPlayer;
        this.playSpeed = playbackSpeed;
        rewindForward = forward;
        cancelRewind();
        incrementRewindCount();
    }

    public void cancelRewind() {
        if (rewindCount != 0) {
            rewindCount = 0;

            if (videoPlayer != null) {
                if (rewindByBackSeek) {
                    videoPlayer.seekTo(rewindBackSeekPlayerPosition);
                } else {
                    long current = videoPlayer.getCurrentPosition();
                    videoPlayer.seekTo(current);
                }
                videoPlayer.setPlaybackSpeed(playSpeed);
            }
        }
        AndroidUtilities.cancelRunOnUIThread(backSeek);

        if (updateRewindRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(updateRewindRunnable);
            updateRewindRunnable = null;
        }

        onRewindCanceled();
    }

    private void incrementRewindCount() {
        if (videoPlayer == null) {
            return;
        }
        rewindCount++;
        boolean needUpdate = false;
        if (rewindCount == 1) {
            if (rewindForward && videoPlayer.isPlaying()) {
                rewindByBackSeek = false;
            } else {
                rewindByBackSeek = true;
            }
        }
        if (rewindForward && !rewindByBackSeek) {
            if (rewindCount == 1) {
                videoPlayer.setPlaybackSpeed(4);
                needUpdate = true;
            } else if (rewindCount == 2) {
                videoPlayer.setPlaybackSpeed(7);
                needUpdate = true;
            } else {
                videoPlayer.setPlaybackSpeed(13);
            }
        } else {
            if (rewindCount == 1 || rewindCount == 2) {
                needUpdate = true;
            }
        }


        if (rewindCount == 1) {
            rewindBackSeekPlayerPosition = videoPlayer.getCurrentPosition();
            rewindLastTime = System.currentTimeMillis();
            rewindLastUpdatePlayerTime = rewindLastTime;
            startRewindFrom = videoPlayer.getCurrentPosition();
            onRewindStart(rewindForward);
        }

        AndroidUtilities.cancelRunOnUIThread(backSeek);
        AndroidUtilities.runOnUIThread(backSeek);

        if (needUpdate) {
            if (updateRewindRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(updateRewindRunnable);
            }
            AndroidUtilities.runOnUIThread(updateRewindRunnable = () -> {
                updateRewindRunnable = null;
                incrementRewindCount();
            }, 2000);
        }
    }


    protected void updateRewindProgressUi(long timeDiff, float progress, boolean rewindByBackSeek) {

    }

    protected void onRewindStart(boolean rewindForward) {

    }

    protected void onRewindCanceled() {

    }

    public float getVideoProgress() {
        return rewindBackSeekPlayerPosition / (float) videoPlayer.getDuration();
    }
}
