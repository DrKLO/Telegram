package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.ilerp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.Utilities.clamp;
import static org.telegram.messenger.Utilities.clamp01;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

import java.io.File;

public class RecordedAudioPlayerView extends View {

    private final Theme.ResourcesProvider resourcesProvider;
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint darkerBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint waveformPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final PlayPauseDrawable playPauseDrawable;
    private final AnimatedTextView.AnimatedTextDrawable text;

    private VideoPlayer player;

    public RecordedAudioPlayerView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        playPauseDrawable = new PlayPauseDrawable(12);
        playPauseDrawable.setParent(this);
        playPauseDrawable.setCallback(this);
        text = new AnimatedTextView.AnimatedTextDrawable();
        text.setAnimationProperties(0.5f, 0, 200, CubicBezierInterpolator.EASE_OUT_QUINT);
        text.setCallback(this);
        text.setTextSize(dp(12));
        text.setTypeface(AndroidUtilities.bold());
        text.setOverrideFullWidth(AndroidUtilities.displaySize.x);
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return text == who || playPauseDrawable == who || super.verifyDrawable(who);
    }

    public float duration;
    public float left = 0.0f;
    public float right = 1.0f;
    public boolean wasPlaying = false;

    public void init(String audioPath, double duration, byte[] waveform, float left, float right) {
        if (destroyed) return;

        this.duration = (float) duration;
        this.left = left;
        this.right = right;
        this.wasPlaying = false;

        text.setText(AndroidUtilities.formatDuration((int) Math.round(Math.max(1, duration)), false), false);
        playPauseDrawable.setPause(false, false);
        if (player == null) {
            player = new VideoPlayer();
            player.setDelegate(new VideoPlayer.VideoPlayerDelegate() {
                @Override
                public void onStateChanged(boolean playWhenReady, int playbackState) {
                    if (playWhenReady && player.getCurrentPosition() >= 0) {
                        wasPlaying = true;
                    }
                    playPauseDrawable.setPause(playWhenReady);
                    AndroidUtilities.cancelRunOnUIThread(progressUpdate);
                    if (playWhenReady) {
                        AndroidUtilities.runOnUIThread(progressUpdate, 16);
                    }
                }
                @Override
                public void onError(VideoPlayer player, Exception e) {}
                @Override
                public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {}
                @Override
                public void onRenderedFirstFrame() {}
                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {}
                @Override
                public boolean onSurfaceDestroyed(SurfaceTexture surfaceTexture) {
                    return false;
                }
            });
        }
        player.preparePlayer(Uri.fromFile(new File(audioPath)), "other");

        lastWaveformWidth = 0;
        waveformData = waveform;
        invalidate();
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public void setPlaying(boolean play) {
        if (destroyed) play = false;
        if (player != null) {
            final float progress = (float) player.getCurrentPosition() / player.getDuration();
            if (progress < left || progress > right) {
                player.seekTo((long) (left * player.getDuration()));
            }
            player.setPlayWhenReady(play);
        }
        playPauseDrawable.setPause(play);
        AndroidUtilities.cancelRunOnUIThread(this.progressUpdate);
        if (play) {
            AndroidUtilities.runOnUIThread(this.progressUpdate, 16);
        }
    }

    private final Runnable progressUpdate = () -> {
        if (player != null) {
            boolean playing = player.isPlaying();
            final float progress = (float) player.getCurrentPosition() / player.getDuration();
            if (progress < left) {
                player.seekTo((long) (left * player.getDuration()));
            } else if (progress > right) {
                setPlaying(false);
                playing = false;
            }
            if (playing) {
                AndroidUtilities.runOnUIThread(this.progressUpdate, 16);
            }
        }
        invalidate();
    };

    private final RectF backgroundRect = new RectF();
    private final RectF badgeRect = new RectF();
    private final RectF handleRect = new RectF();
    private final Path clipPath = new Path();

    private final RectF badgeClickRect = new RectF();
    private final RectF leftHandleClickRect = new RectF();
    private final RectF rightHandleClickRect = new RectF();

    private int lastWaveformWidth;
    private byte[] waveformData;
    private final Path waveformPath = new Path();

    public void checkWaveform() {
        final int width = getMeasuredWidth() - dp(14 + 13);
        if (lastWaveformWidth == width) return;
        final int count = width / dp(3);
        final int minHeight = dp(2);
        final int maxHeight = dp(12);

        byte minBar = Byte.MAX_VALUE, maxBar = Byte.MIN_VALUE;
        for (int i = 0; i < count; ++i) {
            final byte bar = waveformData == null ? 0 : waveformData[(int) ((float) i / count * waveformData.length)];
            minBar = (byte) Math.min(minBar, bar);
            maxBar = (byte) Math.max(maxBar, bar);
        }

        waveformPath.rewind();
        for (int i = 0; i < count; ++i) {
            final byte bar = waveformData == null ? 0 : waveformData[(int) ((float) i / count * waveformData.length)];
            final float h = lerp(minHeight, maxHeight, clamp01(ilerp(bar, minBar, maxBar)));
            final float x = i * dp(3);
            AndroidUtilities.rectTmp.set(x, -h / 2f, x + dp(2), h / 2f);
            waveformPath.addRoundRect(
                AndroidUtilities.rectTmp,
                dp(1), dp(1),
                Path.Direction.CW
            );
        }
        lastWaveformWidth = width;
    }

    public boolean needsCut() {
        return left > 0 || right < 1;
    }
    public float getAudioLeft() {
        return left;
    }
    public float getAudioRight() {
        return right;
    }

    public long getDuration() {
        if (player == null) return 0L;
        return player.getDuration();
    }
    public long getAudioLeftMs() {
        return (long) (left * getDuration());
    }
    public long getAudioRightMs() {
        return (long) (right * getDuration());
    }
    public double getNewDuration() {
        return (right - left) * getDuration() / 1000.0;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        final float h = dp(32);
        backgroundRect.set(0, (getMeasuredHeight() - h) / 2f, getMeasuredWidth(), (getMeasuredHeight() + h) / 2f);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        final float h = dp(32);
        backgroundRect.set(0, (getHeight() - h) / 2f, getWidth(), (getHeight() + h) / 2f);
    }

    private final AnimatedFloat showDuration = new AnimatedFloat(this, 0, 340, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat showBadge = new AnimatedFloat(this, 0, 340, CubicBezierInterpolator.EASE_OUT_QUINT);

    public void drawIn(Canvas canvas, RectF rect, float transitionProgress) {
        backgroundPaint.setColor(Theme.getColor(Theme.key_chat_recordedVoiceBackground, resourcesProvider));
        darkerBackgroundPaint.setColor(Theme.getColor(Theme.key_chat_recordedVoiceDarkerBackground, resourcesProvider));
        text.setTextColor(Theme.getColor(Theme.key_chat_recordedVoiceProgressInner, resourcesProvider));
        playPauseDrawable.setColor(Theme.getColor(Theme.key_chat_recordedVoicePlayPause, resourcesProvider));
        waveformPaint.setColor(Theme.getColor(Theme.key_chat_recordedVoiceProgress, resourcesProvider));
        handlePaint.setColor(Theme.getColor(Theme.key_chat_recordedVoiceProgressInner, resourcesProvider));

        final int left  = (int) lerp(rect.left + dp(11.33f), rect.right - dp(11.33f), clamp01(this.left));
        final int right = (int) lerp(rect.left + dp(11.33f), rect.right - dp(11.33f), clamp01(this.right));
        checkWaveform();

        canvas.save();
        clipPath.rewind();
        clipPath.addRoundRect(rect, dp(8), dp(8), Path.Direction.CW);
        canvas.clipPath(clipPath);

        canvas.drawRect(rect.left, rect.top, left - dp(1.33f), rect.bottom, darkerBackgroundPaint);
        canvas.drawRect(right + dp(1.33f), rect.top, rect.right, rect.bottom, darkerBackgroundPaint);

        canvas.save();
        canvas.translate(rect.left + dp(14), rect.centerY());
        waveformPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_chat_recordedVoiceProgress, resourcesProvider), 0.3f));
        canvas.drawPath(waveformPath, waveformPaint);
        canvas.restore();

        canvas.drawRect(left, rect.top, right, rect.bottom, backgroundPaint);

        final float progress = progressPressed ? holdProgress : clamp(player != null ? (float) player.getCurrentPosition() / player.getDuration() : 1.0f, this.right, this.left);
        final float progressX = clamp(lerp(rect.left + dp(13), rect.right - dp(14), progress), right, left);
        if (progressX < right) {
            canvas.save();
            canvas.clipRect(progressX, rect.top, right, rect.bottom);
            canvas.translate(rect.left + dp(14), rect.centerY());
            if (!wasPlaying || progress >= this.left || progressPressed) {
                waveformPaint.setColor(Theme.getColor(Theme.key_chat_recordedVoiceProgress, resourcesProvider));
            } else {
                waveformPaint.setColor(Theme.getColor(Theme.key_chat_recordedVoiceProgressInner, resourcesProvider));
            }
            canvas.drawPath(waveformPath, waveformPaint);
            canvas.restore();
        }
        if (progressX > left) {
            canvas.save();
            canvas.clipRect(left, rect.top, progressX, rect.bottom);
            canvas.translate(rect.left + dp(14), rect.centerY());
            if (isPlaying() || wasPlaying || progressPressed) {
                waveformPaint.setColor(Theme.getColor(Theme.key_chat_recordedVoiceProgressInner, resourcesProvider));
            } else {
                waveformPaint.setColor(Theme.getColor(Theme.key_chat_recordedVoiceProgress, resourcesProvider));
            }
            canvas.drawPath(waveformPath, waveformPaint);
            canvas.restore();
        }

        handleRect.set(left - dp(7), rect.centerY() - dp(5.33f), left - dp(5.33f), rect.centerY() + dp(5.33f));
        canvas.drawRoundRect(handleRect, handleRect.width() / 2f, handleRect.width() / 2f, handlePaint);

        handleRect.set(right + dp(5.33f), rect.centerY() - dp(5.33f), right + dp(7), rect.centerY() + dp(5.33f));
        canvas.drawRoundRect(handleRect, handleRect.width() / 2f, handleRect.width() / 2f, handlePaint);

        leftHandleClickRect.set(left - dp(24), 0, left + dp(6), getHeight());
        rightHandleClickRect.set(right - dp(6), 0, right + dp(24), getHeight());

        final float badgeAlpha = showBadge.set(!progressPressed);
        if (badgeAlpha > 0.0f) {
            float badgeWidth = (int) (dp(30) + text.getCurrentWidth());
            final float durationAlpha = showDuration.set(badgeWidth <= (right - left - dp(8)));
            badgeWidth = lerp(dp(24), badgeWidth, durationAlpha);
            final int badgeHeight = dp(20);
            badgeRect.set(
                (left + right - badgeWidth) / 2f,
                rect.centerY() - badgeHeight / 2f,
                (left + right + badgeWidth) / 2f,
                rect.centerY() + badgeHeight / 2f
            );
            final int wasAlpha = darkerBackgroundPaint.getAlpha();
            darkerBackgroundPaint.setAlpha((int) (wasAlpha * badgeAlpha));
            canvas.drawRoundRect(badgeRect, badgeRect.height() / 2f, badgeRect.height() / 2f, darkerBackgroundPaint);
            darkerBackgroundPaint.setAlpha(wasAlpha);

            badgeClickRect.set(badgeRect);
            badgeClickRect.inset(-dp(6), -dp(6));

            canvas.save();
            final int sz = dp(12);
            canvas.translate(lerp(badgeRect.centerX() - sz / 2f, badgeRect.left + dp(6), durationAlpha), badgeRect.centerY());
            playPauseDrawable.setBounds(0, -sz / 2, sz, sz / 2);
            playPauseDrawable.setAlpha((int) (0xFF * badgeAlpha));
            playPauseDrawable.draw(canvas);
            canvas.restore();

            if (durationAlpha > 0) {
                canvas.save();
                canvas.translate(badgeRect.left + dp(21.66f), badgeRect.centerY() - dp(1));
                text.setBounds(-1, -1, 1, 1);
                text.setAlpha((int) (0xFF * durationAlpha * badgeAlpha));
                text.draw(canvas);
                canvas.restore();
            }
        }

        canvas.restore();
    }

    public boolean allowDraw = true;
    public void setAllowDraw(boolean allow) {
        if (this.allowDraw != allow) {
            this.allowDraw = allow;
            invalidate();
        }
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        if (allowDraw) {
            drawIn(canvas, backgroundRect, 1.0f);
        }
    }

    private boolean destroyed;
    public void destroy() {
        destroyed = true;
        if (player != null) {
            player.setPlayWhenReady(false);
            player.releasePlayer(true);
            player = null;
        }
    }


    private boolean leftPressed;
    private boolean rightPressed;
    private boolean playPressed;
    private boolean progressPressed;
    private boolean progressPressedWasPlaying;
    private float holdProgress;

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        final boolean playHit = badgeClickRect.contains(e.getX(), e.getY());
        final boolean leftHit = !playHit && leftHandleClickRect.contains(e.getX(), e.getY());
        final boolean rightHit = !playHit && rightHandleClickRect.contains(e.getX(), e.getY());
        final boolean progressHit = !playHit && !leftHit && !rightHit && e.getX() > leftHandleClickRect.right && e.getX() < rightHandleClickRect.left;
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            playPressed = playHit;
            leftPressed = leftHit;
            rightPressed = rightHit;
            if (leftPressed || rightPressed) {
                progressPressedWasPlaying = isPlaying();
                setPlaying(false);
            }
            if (progressPressed = progressHit) {
                progressPressedWasPlaying = isPlaying();
                holdProgress = player == null ? 1.0f : (float) player.getCurrentPosition() / player.getDuration();
                setPlaying(false);
            }
            if (getParent() != null && (playPressed || leftPressed || rightPressed || progressPressed)) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
        } else if (e.getAction() == MotionEvent.ACTION_MOVE) {
            if (leftPressed) {
                final float minLeft = 0;
                final float maxLeft = clamp01(right - Math.max(1.0f / duration, (float) dp(30) / (backgroundRect.width() - dp(22.66f))));
                left = clamp(ilerp(e.getX(), backgroundRect.left + dp(11.33f), backgroundRect.right - dp(11.33f)), maxLeft, minLeft);
                invalidate();
            } else if (rightPressed) {
                final float minRight = clamp01(left + Math.max(1.0f / duration, (float) dp(30) / (backgroundRect.width() - dp(22.66f))));
                final float maxRight = 1.0f;
                right = clamp(ilerp(e.getX(), backgroundRect.left + dp(11.33f), backgroundRect.right - dp(11.33f)), maxRight, minRight);
                invalidate();
            } else if (progressPressed) {
                if (player != null) {
                    player.seekTo((long) ((holdProgress = clamp(ilerp(e.getX(), backgroundRect.left + dp(11.33f), backgroundRect.right - dp(11.33f)), right, left)) * player.getDuration()));
                }
                invalidate();
            }
            text.setText(AndroidUtilities.formatDuration((int) Math.round(Math.max(1, duration * (right - left))), false), true);
        } else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
            if (e.getAction() == MotionEvent.ACTION_UP && playPressed) {
                setPlaying(!isPlaying());
            } else if (leftPressed && wasPlaying) {
                if (player != null) {
                    player.seekTo((long) (left * player.getDuration()));
                }
                setPlaying(true);
            } else if (rightPressed && wasPlaying) {
                if (player != null) {
                    player.seekTo(Math.max((long) (left * player.getDuration()), (long) (right * player.getDuration()) - 1500L));
                }
                setPlaying(true);
            } else if (progressPressed && !isPlaying()) {
                setPlaying(true);
            }
            playPressed = false;
            leftPressed = false;
            rightPressed = false;
            progressPressed = false;
        }
        return playPressed || leftPressed || rightPressed || progressPressed || super.dispatchTouchEvent(e);
    }
}
