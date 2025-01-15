package org.telegram.messenger.video;

import com.google.android.exoplayer2.C;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.PhotoViewerWebView;
import org.telegram.ui.Components.VideoForwardDrawable;
import org.telegram.ui.Components.VideoPlayer;

public class OldVideoPlayerRewinder {

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
    private PhotoViewerWebView webView;

    private final Runnable backSeek = new Runnable() {
        @Override
        public void run() {
            if (videoPlayer == null && webView == null) {
                return;
            }
            long duration = getDuration();
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
            if (rewindByBackSeek && rewindLastTime - rewindLastUpdatePlayerTime > 350) {
                rewindLastUpdatePlayerTime = rewindLastTime;
                seekTo(rewindBackSeekPlayerPosition);
            }

            long timeDiff = rewindBackSeekPlayerPosition - startRewindFrom;
            float progress = rewindBackSeekPlayerPosition / (float) getDuration();
            updateRewindProgressUi(timeDiff, progress, rewindByBackSeek);

            if (rewindBackSeekPlayerPosition == 0 || rewindBackSeekPlayerPosition >= duration) {
                if (rewindByBackSeek) {
                    rewindLastUpdatePlayerTime = rewindLastTime;
                    seekTo(rewindBackSeekPlayerPosition);
                }
                cancelRewind();
            }
            if (rewindCount > 0) {
                AndroidUtilities.runOnUIThread(backSeek, 16);
            }
        }
    };

    public void startRewind(PhotoViewerWebView webView, boolean forward, float playbackSpeed) {
        this.webView = webView;
        this.playSpeed = playbackSpeed;
        rewindForward = forward;
        cancelRewind();
        incrementRewindCount();
    }

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

            if (videoPlayer != null || webView != null) {
                if (rewindByBackSeek) {
                    seekTo(rewindBackSeekPlayerPosition);
                } else {
                    long current = getCurrentPosition();
                    seekTo(current);
                }
                setPlaybackSpeed(playSpeed);
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
        if (videoPlayer == null && webView == null) {
            return;
        }
        rewindCount++;
        boolean needUpdate = false;
        if (rewindCount == 1) {
            if (rewindForward && isPlaying()) {
                rewindByBackSeek = false;
            } else {
                rewindByBackSeek = true;
            }
        }
        if (rewindForward && !rewindByBackSeek) {
            if (rewindCount == 1) {
                setPlaybackSpeed(4);
                needUpdate = true;
            } else if (rewindCount == 2) {
                setPlaybackSpeed(7);
                needUpdate = true;
            } else {
                setPlaybackSpeed(13);
            }
        } else {
            if (rewindCount == 1 || rewindCount == 2) {
                needUpdate = true;
            }
        }


        if (rewindCount == 1) {
            rewindBackSeekPlayerPosition = getCurrentPosition();
            rewindLastTime = System.currentTimeMillis();
            rewindLastUpdatePlayerTime = rewindLastTime;
            startRewindFrom = getCurrentPosition();
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

    private void seekTo(long position) {
        if (webView != null) {
            webView.seekTo(position);
        } else {
            if (videoPlayer == null) {
                return;
            }
            videoPlayer.seekTo(position);
        }
    }

    private void setPlaybackSpeed(float speed) {
        if (webView != null) {
            webView.setPlaybackSpeed(speed);
        } else {
            if (videoPlayer == null) {
                return;
            }
            videoPlayer.setPlaybackSpeed(speed);
        }
    }

    private long getCurrentPosition() {
        if (webView != null) {
            return webView.getCurrentPosition();
        } else {
            if (videoPlayer == null) {
                return 0;
            }
            return videoPlayer.getCurrentPosition();
        }
    }

    private long getDuration() {
        if (webView != null) {
            return webView.getVideoDuration();
        } else {
            if (videoPlayer == null) {
                return 0;
            }
            return videoPlayer.getDuration();
        }
    }

    private boolean isPlaying() {
        if (webView != null) {
            return webView.isPlaying();
        } else {
            if (videoPlayer == null) {
                return false;
            }
            return videoPlayer.isPlaying();
        }
    }

    public float getVideoProgress() {
        return rewindBackSeekPlayerPosition / (float) getDuration();
    }
}