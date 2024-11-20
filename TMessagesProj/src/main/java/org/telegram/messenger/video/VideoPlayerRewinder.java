package org.telegram.messenger.video;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.util.Log;

import com.google.android.exoplayer2.C;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.PhotoViewerWebView;
import org.telegram.ui.Components.SeekSpeedDrawable;
import org.telegram.ui.Components.VideoForwardDrawable;
import org.telegram.ui.Components.VideoPlayer;

public class VideoPlayerRewinder {

    public int rewindCount;
    private boolean rewindForward;
    private boolean fastSeeking;
    public boolean rewindByBackSeek;
    private long startRewindFrom;
    private Runnable updateRewindRunnable;
    private long rewindLastTime;
    private long rewindLastUpdatePlayerTime;
    private long rewindBackSeekLastPlayerPosition;
    private long rewindBackSeekPlayerPosition = -1;
    private float playSpeed = 1f;
    private boolean wasMuted;
    private boolean wasPaused;

    private float value;

    private VideoPlayer videoPlayer;
    private PhotoViewerWebView webView;
    private VideoFramesRewinder framesRewinder;

    public VideoPlayerRewinder(VideoFramesRewinder framesRewinder) {
        this.framesRewinder = framesRewinder;
    }

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

            final long now = System.currentTimeMillis();
            long dt = now - rewindLastTime;
            rewindLastTime = now;
            final float speed = Math.max(0, -getRewindSpeed() * playSpeed);
            dt *= speed;
            rewindBackSeekPlayerPosition -= dt;
            rewindBackSeekPlayerPosition = Utilities.clamp(rewindBackSeekPlayerPosition, duration, 0);
            if (rewindByBackSeek && getCurrentPosition() > rewindBackSeekPlayerPosition && rewindLastTime - rewindLastUpdatePlayerTime > 10) {
                rewindLastUpdatePlayerTime = rewindLastTime;
                if (framesRewinder != null) {
                    framesRewinder.seek(rewindBackSeekPlayerPosition, Math.abs(speed));
                } else {
                    seekTo(rewindBackSeekPlayerPosition, false);
                }
            }

            long timeDiff = rewindBackSeekPlayerPosition - startRewindFrom;
            float progress = rewindBackSeekPlayerPosition / (float) getDuration();
            updateRewindProgressUi(timeDiff, progress, rewindByBackSeek);

            if (rewindBackSeekPlayerPosition == 0 || rewindBackSeekPlayerPosition >= duration) {
                if (rewindByBackSeek) {
                    rewindLastUpdatePlayerTime = rewindLastTime;
                    seekTo(rewindBackSeekPlayerPosition, false);
                }
                cancelRewind();
            }
            if (rewinding && getRewindSpeed() < 0) {
                AndroidUtilities.runOnUIThread(backSeek, 16);
            }
        }
    };

    public boolean rewinding;
    private float x;
    private SeekSpeedDrawable seekSpeedDrawable;

    public void startRewind(PhotoViewerWebView webView, boolean forward, float initialX, float playbackSpeed, SeekSpeedDrawable seekSpeedDrawable) {
        cancelRewind();
        this.videoPlayer = null;
        this.webView = null;
        if (framesRewinder != null) {
            framesRewinder.release();
        }
        rewindByBackSeek = forward;
        rewinding = true;
        rewindBackSeekPlayerPosition = -1;
        this.webView = webView;
        this.seekSpeedDrawable = seekSpeedDrawable;
        this.playSpeed = playbackSpeed;
        this.wasMuted = false;
        this.wasPaused = webView != null && !webView.isPlaying();
        fastSeeking = false;
        rewindLastUpdatePlayerTime = 0;
        x = initialX;
        value = forward ? getValueBySpeed(2.0f) : getValueBySpeed(-2.0f);
        rewindBackSeekLastPlayerPosition = -100;
        if (seekSpeedDrawable != null) {
            seekSpeedDrawable.setSpeed(getRewindSpeed(), false);
            seekSpeedDrawable.setShown(true, true);
        }
    }

    public void startRewind(VideoPlayer videoPlayer, boolean forward, float initialX, float playbackSpeed, SeekSpeedDrawable seekSpeedDrawable) {
        cancelRewind();
        this.videoPlayer = null;
        this.webView = null;
        if (framesRewinder != null) {
            framesRewinder.release();
        }
        rewindByBackSeek = forward;
        rewinding = true;
        rewindBackSeekPlayerPosition = -1;
        this.videoPlayer = videoPlayer;
        this.seekSpeedDrawable = seekSpeedDrawable;
        this.playSpeed = playbackSpeed;
        this.wasMuted = videoPlayer != null && videoPlayer.isMuted();
        this.wasPaused = videoPlayer != null && !videoPlayer.isPlaying();
        fastSeeking = false;
        rewindLastUpdatePlayerTime = 0;
        x = initialX;
        value = forward ? getValueBySpeed(2.0f) : getValueBySpeed(-2.0f);
        rewindBackSeekLastPlayerPosition = -100;
        if (seekSpeedDrawable != null) {
            seekSpeedDrawable.setSpeed(getRewindSpeed(), false);
            seekSpeedDrawable.setShown(true, true);
        }
        updateRewindSpeed();
    }

    public float getRewindSpeed() {
        float v = value;
        v = v < 0.4f ? v - 1.9f : v;
//        v /= 2.0f;
//        v = v * v * v;
        return Utilities.clamp(v, +10.0f, -6.0f);
    }

    public float getValueBySpeed(float speed) {
        float value = speed;
//        value = (float) Math.cbrt(value);
//        value *= 2.0f;
        if (value < -1.5f) {
            value += 1.9f;
        }
        return value;
    }

    public void updateRewindSpeed() {
        final float rewindSpeed = getRewindSpeed();
        if (rewindSpeed < 0) {
            if (!rewindByBackSeek) {
                rewindByBackSeek = true;
                rewindBackSeekPlayerPosition = getCurrentPosition();
                rewindLastTime = System.currentTimeMillis();
                AndroidUtilities.runOnUIThread(backSeek);
                setMuted(true);
                setPaused(true);
                setPlaybackSpeed(playSpeed);
                if (framesRewinder != null && !framesRewinder.isReady() && videoPlayer != null) {
                    framesRewinder.setup(videoPlayer.getLowestFile());
                }
            }
        } else {
            if (rewindByBackSeek) {
                rewindByBackSeek = false;
                AndroidUtilities.cancelRunOnUIThread(backSeek);
                setMuted(wasMuted || wasPaused);
                setPaused(false);
                if (videoPlayer != null && framesRewinder != null && rewindBackSeekPlayerPosition >= 0) {
                    videoPlayer.seekTo(rewindBackSeekPlayerPosition, false, () -> {
                        if (framesRewinder != null) {
                            framesRewinder.clearCurrent();
                        }
                    });
                }
            }
            setPlaybackSpeed(playSpeed * rewindSpeed);
        }
    }

    public void setX(float x) {
        float diff = this.x - x;
        value -= diff / dp(40);
        this.x = x;

        if (seekSpeedDrawable != null) {
            seekSpeedDrawable.setSpeed(getRewindSpeed(), true);
        }

        updateRewindSpeed();
    }

    public void cancelRewind() {
        if (!rewinding) return;

        rewinding = false;
        fastSeeking = false;
        boolean awaitSeek = false;
        if (videoPlayer != null || webView != null) {
            if (rewindByBackSeek) {
                if (videoPlayer != null && framesRewinder != null) {
                    awaitSeek = true;
                    videoPlayer.seekTo(rewindBackSeekPlayerPosition, false, () -> {
                        if (framesRewinder != null) {
                            framesRewinder.release();
                        }
                    });
                } else {
                    seekTo(rewindBackSeekPlayerPosition, false);
                }
            } else {
                seekTo(getCurrentPosition(), false);
            }
            setPlaybackSpeed(playSpeed);
        }
        setMuted(wasMuted);
        setPaused(wasPaused);
        AndroidUtilities.cancelRunOnUIThread(backSeek);
        if (framesRewinder != null && !awaitSeek) {
            framesRewinder.release();
        }

        if (updateRewindRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(updateRewindRunnable);
            updateRewindRunnable = null;
        }

        onRewindCanceled();

        if (seekSpeedDrawable != null) {
            seekSpeedDrawable.setShown(false, true);
        }
    }

    protected void updateRewindProgressUi(long timeDiff, float progress, boolean rewindByBackSeek) {

    }

    protected void onRewindStart(boolean rewindForward) {

    }

    protected void onRewindCanceled() {

    }

    private void seekTo(long position, boolean fast) {
        if (webView != null) {
            webView.seekTo(position);
        } else if (videoPlayer != null) {
            videoPlayer.seekTo(position, fast);
        }
        rewindBackSeekLastPlayerPosition = position;
    }

    private void setMuted(boolean muted) {
        if (videoPlayer != null) {
            videoPlayer.setMute(muted);
        }
    }

    private void setPaused(boolean paused) {
        if (webView != null) {
            if (paused) {
                webView.pauseVideo();
            } else {
                webView.playVideo();
            }
        } else if (videoPlayer != null) {
            if (paused) {
                videoPlayer.pause();
            } else {
                videoPlayer.play();
            }
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
