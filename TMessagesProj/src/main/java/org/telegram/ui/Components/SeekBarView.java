/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.Layout;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Pair;
import android.util.StateSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.Collections;

public class SeekBarView extends FrameLayout {

    private final SeekBarAccessibilityDelegate seekBarAccessibilityDelegate;

    private Paint innerPaint1;
    private Paint outerPaint1;
    private int thumbSize;
    private int selectorWidth;
    private int thumbX;
    private AnimatedFloat animatedThumbX = new AnimatedFloat(this, 0, 80, CubicBezierInterpolator.EASE_OUT);
    private int thumbDX;
    private float progressToSet = -100;
    private boolean pressed, pressedDelayed;
    public SeekBarViewDelegate delegate;
    private boolean reportChanges;
    private float bufferedProgress;
    private Drawable hoverDrawable;
    private long lastUpdateTime;
    private float currentRadius;
    private int[] pressedState = new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed};
    private float transitionProgress = 1f;
    private int transitionThumbX;
    private int separatorsCount;
    private int lineWidthDp = 3;

    private boolean twoSided;
    private final Theme.ResourcesProvider resourcesProvider;

    public interface SeekBarViewDelegate {
        void onSeekBarDrag(boolean stop, float progress);
        void onSeekBarPressed(boolean pressed);
        default CharSequence getContentDescription() {
            return null;
        }
        default int getStepsCount() {
            return 0;
        }
    }

    public SeekBarView(Context context) {
        this(context, null);
    }

    public SeekBarView(Context context, Theme.ResourcesProvider resourcesProvider) {
        this(context, false, resourcesProvider);
    }

    public SeekBarView(Context context, boolean inPercents, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setWillNotDraw(false);
        innerPaint1 = new Paint(Paint.ANTI_ALIAS_FLAG);

        outerPaint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
        outerPaint1.setColor(getThemedColor(Theme.key_player_progress));

        selectorWidth = AndroidUtilities.dp(32);
        thumbSize = AndroidUtilities.dp(24);
        currentRadius = AndroidUtilities.dp(6);

        if (Build.VERSION.SDK_INT >= 21) {
            hoverDrawable = Theme.createSelectorDrawable(ColorUtils.setAlphaComponent(getThemedColor(Theme.key_player_progress), 40), 1, AndroidUtilities.dp(16));
            hoverDrawable.setCallback(this);
            hoverDrawable.setVisible(true, false);
        }

        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setAccessibilityDelegate(seekBarAccessibilityDelegate = new FloatSeekBarAccessibilityDelegate(inPercents) {
            @Override
            public float getProgress() {
                return SeekBarView.this.getProgress();
            }

            @Override
            public void setProgress(float progress) {
                pressed = true;
                SeekBarView.this.setProgress(progress);
                setSeekBarDrag(true, progress);
                pressed = false;
            }

            @Override
            protected float getDelta() {
                final int stepsCount = delegate.getStepsCount();
                if (stepsCount > 0) {
                    return 1f / stepsCount;
                } else {
                    return super.getDelta();
                }
            }

            @Override
            public CharSequence getContentDescription(View host) {
                return delegate != null ? delegate.getContentDescription() : null;
            }
        });
    }

    public void setSeparatorsCount(int separatorsCount) {
        this.separatorsCount = separatorsCount;
    }

    public void setColors(int inner, int outer) {
        innerPaint1.setColor(inner);
        outerPaint1.setColor(outer);
        if (hoverDrawable != null) {
            Theme.setSelectorDrawableColor(hoverDrawable, ColorUtils.setAlphaComponent(outer, 40), true);
        }
    }

    public void setTwoSided(boolean value) {
        twoSided = value;
    }

    public boolean isTwoSided() {
        return twoSided;
    }

    public void setInnerColor(int color) {
        innerPaint1.setColor(color);
    }

    public void setOuterColor(int color) {
        outerPaint1.setColor(color);
        if (hoverDrawable != null) {
            Theme.setSelectorDrawableColor(hoverDrawable, ColorUtils.setAlphaComponent(color, 40), true);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return onTouch(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return onTouch(event);
    }

    public void setReportChanges(boolean value) {
        reportChanges = value;
    }

    public void setDelegate(SeekBarViewDelegate seekBarViewDelegate) {
        delegate = seekBarViewDelegate;
    }

    boolean captured;
    float sx, sy;
    boolean onTouch(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            sx = ev.getX();
            sy = ev.getY();
            return true;
        } else if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
            captured = false;
            if (ev.getAction() == MotionEvent.ACTION_UP) {
                final ViewConfiguration vc = ViewConfiguration.get(getContext());
                if (Math.abs(ev.getY() - sy) < vc.getScaledTouchSlop()) {
                    int additionWidth = (getMeasuredHeight() - thumbSize) / 2;
                    if (!(thumbX - additionWidth <= ev.getX() && ev.getX() <= thumbX + thumbSize + additionWidth)) {
                        thumbX = (int) ev.getX() - thumbSize / 2;
                        if (thumbX < 0) {
                            thumbX = 0;
                        } else if (thumbX > getMeasuredWidth() - selectorWidth) {
                            thumbX = getMeasuredWidth() - selectorWidth;
                        }
                    }
                    thumbDX = (int) (ev.getX() - thumbX);
                    pressed = pressedDelayed = true;
                }
            }
            if (pressed) {
                if (ev.getAction() == MotionEvent.ACTION_UP) {
                    if (twoSided) {
                        float w = (getMeasuredWidth() - selectorWidth) / 2;
                        if (thumbX >= w) {
                            setSeekBarDrag(false, (thumbX - w) / w);
                        } else {
                            setSeekBarDrag(false, -Math.max(0.01f, 1.0f - (w - thumbX) / w));
                        }
                    } else {
                        setSeekBarDrag(true, (float) thumbX / (float) (getMeasuredWidth() - selectorWidth));
                    }
                }
                if (Build.VERSION.SDK_INT >= 21 && hoverDrawable != null) {
                    hoverDrawable.setState(StateSet.NOTHING);
                }
                delegate.onSeekBarPressed(false);
                pressed = false;
                AndroidUtilities.runOnUIThread(() -> pressedDelayed = false, 50);
                invalidate();
                return true;
            }
        } else if (ev.getAction() == MotionEvent.ACTION_MOVE) {
            if (!captured) {
                final ViewConfiguration vc = ViewConfiguration.get(getContext());
                if (Math.abs(ev.getY() - sy) > vc.getScaledTouchSlop()) {
                    return false;
                }
                if (Math.abs(ev.getX() - sx) > vc.getScaledTouchSlop()) {
                    captured = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    int additionWidth = (getMeasuredHeight() - thumbSize) / 2;
                    if (ev.getY() >= 0 && ev.getY() <= getMeasuredHeight()) {
                        if (!(thumbX - additionWidth <= ev.getX() && ev.getX() <= thumbX + thumbSize + additionWidth)) {
                            thumbX = (int) ev.getX() - thumbSize / 2;
                            if (thumbX < 0) {
                                thumbX = 0;
                            } else if (thumbX > getMeasuredWidth() - selectorWidth) {
                                thumbX = getMeasuredWidth() - selectorWidth;
                            }
                        }
                        thumbDX = (int) (ev.getX() - thumbX);
                        pressed = pressedDelayed = true;
                        delegate.onSeekBarPressed(true);
                        if (Build.VERSION.SDK_INT >= 21 && hoverDrawable != null) {
                            hoverDrawable.setState(pressedState);
                            hoverDrawable.setHotspot(ev.getX(), ev.getY());
                        }
                        invalidate();
                        return true;
                    }
                }
            } else {
                if (pressed) {
                    thumbX = (int) (ev.getX() - thumbDX);
                    if (thumbX < 0) {
                        thumbX = 0;
                    } else if (thumbX > getMeasuredWidth() - selectorWidth) {
                        thumbX = getMeasuredWidth() - selectorWidth;
                    }
                    if (reportChanges) {
                        if (twoSided) {
                            float w = (getMeasuredWidth() - selectorWidth) / 2;
                            if (thumbX >= w) {
                                setSeekBarDrag(false, (thumbX - w) / w);
                            } else {
                                setSeekBarDrag(false, -Math.max(0.01f, 1.0f - (w - thumbX) / w));
                            }
                        } else {
                            setSeekBarDrag(false, (float) thumbX / (float) (getMeasuredWidth() - selectorWidth));
                        }
                    }
                    if (Build.VERSION.SDK_INT >= 21 && hoverDrawable != null) {
                        hoverDrawable.setHotspot(ev.getX(), ev.getY());
                    }
                    invalidate();
                    return true;
                }
            }
        }
        return false;
    }

    public void setLineWidth(int dp) {
        lineWidthDp = dp;
    }

    int lastValue;
    private void setSeekBarDrag(boolean stop, float progress) {
        if (delegate != null) {
            delegate.onSeekBarDrag(stop, progress);
        }
        if (separatorsCount > 1) {
            int value = Math.round((separatorsCount - 1) * progress);
            if (!stop && value != lastValue) {
                try {
                    performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                } catch (Exception ignore) {}
            }
            lastValue = value;
        }
    }

    public float getProgress() {
        if (getMeasuredWidth() == 0) {
            return progressToSet;
        }
        return thumbX / (float) (getMeasuredWidth() - selectorWidth);
    }

    public void setProgress(float progress) {
        setProgress(progress, false);
    }

    public void setProgress(float progress, boolean animated) {
        if (getMeasuredWidth() == 0) {
            progressToSet = progress;
            return;
        }
        progressToSet = -100;
        int newThumbX;
        if (twoSided) {
            int w = getMeasuredWidth() - selectorWidth;
            float cx = w / 2;
            if (progress < 0) {
                newThumbX = (int) Math.ceil(cx + w / 2 * -(1.0f + progress));
            } else {
                newThumbX = (int) Math.ceil(cx + w / 2 * progress);
            }
        } else {
            newThumbX = (int) Math.ceil((getMeasuredWidth() - selectorWidth) * progress);
        }
        if (thumbX != newThumbX) {
            if (animated) {
                transitionThumbX = thumbX;
                transitionProgress = 0f;
            }
            thumbX = newThumbX;
            if (thumbX < 0) {
                thumbX = 0;
            } else if (thumbX > getMeasuredWidth() - selectorWidth) {
                thumbX = getMeasuredWidth() - selectorWidth;
            }
            invalidate();
        }
    }

    public void setBufferedProgress(float progress) {
        bufferedProgress = progress;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (progressToSet != -100 && getMeasuredWidth() > 0) {
            setProgress(progressToSet);
            progressToSet = -100;
        }
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who) || who == hoverDrawable;
    }

    public boolean isDragging() {
        return pressed;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int thumbX = this.thumbX;
        if (!twoSided && separatorsCount > 1) {
            float step = (getMeasuredWidth() - selectorWidth) / ((float) separatorsCount - 1f);
            thumbX = (int) animatedThumbX.set(Math.round((thumbX) / step) * step);
        }
        int y = (getMeasuredHeight() - thumbSize) / 2;
        innerPaint1.setColor(getThemedColor(Theme.key_player_progressBackground));

        float centerY = getMeasuredHeight() / 2f;
        float left = selectorWidth / 2f, right = getMeasuredWidth() - selectorWidth / 2;
        float top = centerY - AndroidUtilities.dp(lineWidthDp) / 2f, bottom = centerY + AndroidUtilities.dp(lineWidthDp) / 2f;

        rect.set(left, top, right, bottom);
        drawProgressBar(canvas, rect, innerPaint1);
        if (bufferedProgress > 0) {
            innerPaint1.setColor(getThemedColor(Theme.key_player_progressCachedBackground));
            rect.set(left, top, selectorWidth / 2f + bufferedProgress * (getMeasuredWidth() - selectorWidth), bottom);
            drawProgressBar(canvas, rect, innerPaint1);
        }
        if (twoSided) {
            canvas.drawRect(getMeasuredWidth() / 2 - AndroidUtilities.dp(1), getMeasuredHeight() / 2 - AndroidUtilities.dp(6), getMeasuredWidth() / 2 + AndroidUtilities.dp(1), getMeasuredHeight() / 2 + AndroidUtilities.dp(6), outerPaint1);
            if (thumbX > (getMeasuredWidth() - selectorWidth) / 2) {
                canvas.drawRect(getMeasuredWidth() / 2, getMeasuredHeight() / 2 - AndroidUtilities.dp(1), selectorWidth / 2 + thumbX, getMeasuredHeight() / 2 + AndroidUtilities.dp(1), outerPaint1);
            } else {
                canvas.drawRect(thumbX + selectorWidth / 2, getMeasuredHeight() / 2 - AndroidUtilities.dp(1), getMeasuredWidth() / 2, getMeasuredHeight() / 2 + AndroidUtilities.dp(1), outerPaint1);
            }
        } else {
            rect.set(left, top, selectorWidth / 2 + thumbX, bottom);
            drawProgressBar(canvas, rect, outerPaint1);
        }

        if (hoverDrawable != null) {
            int dx = thumbX + selectorWidth / 2 - AndroidUtilities.dp(16);
            int dy = y + thumbSize / 2 - AndroidUtilities.dp(16);
            hoverDrawable.setBounds(dx, dy, dx + AndroidUtilities.dp(32), dy + AndroidUtilities.dp(32));
            hoverDrawable.draw(canvas);
        }
        boolean needInvalidate = false;
        int newRad = AndroidUtilities.dp(pressed ? 8 : 6);
        long newUpdateTime = SystemClock.elapsedRealtime();
        long dt = newUpdateTime - lastUpdateTime;
        if (dt > 18) {
            dt = 16;
        }
        if (currentRadius != newRad) {
            if (currentRadius < newRad) {
                currentRadius += AndroidUtilities.dp(1) * (dt / 60.0f);
                if (currentRadius > newRad) {
                    currentRadius = newRad;
                }
            } else {
                currentRadius -= AndroidUtilities.dp(1) * (dt / 60.0f);
                if (currentRadius < newRad) {
                    currentRadius = newRad;
                }
            }
            needInvalidate = true;
        }
        if (transitionProgress < 1f) {
            transitionProgress += dt / 225f;
            if (transitionProgress < 1f) {
                needInvalidate = true;
            } else {
                transitionProgress = 1f;
            }
        }

        if (transitionProgress < 1f) {
            final float oldCircleProgress = 1f - Easings.easeInQuad.getInterpolation(Math.min(1f, transitionProgress * 3f));
            final float newCircleProgress = Easings.easeOutQuad.getInterpolation(transitionProgress);
            if (oldCircleProgress > 0f) {
                canvas.drawCircle(transitionThumbX + selectorWidth / 2, y + thumbSize / 2, currentRadius * oldCircleProgress, outerPaint1);
            }
            canvas.drawCircle(thumbX + selectorWidth / 2, y + thumbSize / 2, currentRadius * newCircleProgress, outerPaint1);
        } else {
            canvas.drawCircle(thumbX + selectorWidth / 2, y + thumbSize / 2, currentRadius, outerPaint1);
        }

        drawTimestampLabel(canvas);

        if (needInvalidate) {
            postInvalidateOnAnimation();
        }
    }

    private ArrayList<Pair<Float, CharSequence>> timestamps;
    private CharSequence lastCaption;
    private long lastVideoDuration;

    private float timestampsAppearing = 0;
    private long lastTimestampsAppearingUpdate;
    private final float TIMESTAMP_GAP = 1f;
    private static float[] tmpRadii;
    private static Path tmpPath;

    private int currentTimestamp = -1, lastTimestamp = -1;
    private StaticLayout[] timestampLabel;
    private TextPaint timestampLabelPaint;
    private float timestampChangeT = 1;
    private int timestampChangeDirection;
    private long lastTimestampUpdate;
    private float lastWidth = -1;

    private RectF rect = new RectF();

    public void clearTimestamps() {
        timestamps = null;
        currentTimestamp = -1;
        timestampsAppearing = 0;
        if (timestampLabel != null) {
            timestampLabel[0] = timestampLabel[1] = null;
        }
        lastCaption = null;
        lastVideoDuration = -1;
    }

    public void updateTimestamps(MessageObject messageObject, Long videoDuration) {
        if (messageObject == null) {
            clearTimestamps();
            return;
        }
        if (videoDuration == null) {
            videoDuration = (long) messageObject.getDuration() * 1000L;
        }
        if (videoDuration == null || videoDuration < 0) {
            clearTimestamps();
            return;
        }
        CharSequence text = messageObject.caption;
        if (messageObject.isYouTubeVideo()) {
            if (messageObject.youtubeDescription == null && messageObject.messageOwner.media.webpage.description != null) {
                messageObject.youtubeDescription = SpannableString.valueOf(messageObject.messageOwner.media.webpage.description);
                MessageObject.addUrlsByPattern(messageObject.isOut(), messageObject.youtubeDescription, false, 3, (int) (long) videoDuration, false);
            }
            text = messageObject.youtubeDescription;
        }
        if (text == lastCaption && lastVideoDuration == videoDuration) {
            return;
        }
        lastCaption = text;
        lastVideoDuration = videoDuration;
        if (!(text instanceof Spanned)) {
            timestamps = null;
            currentTimestamp = -1;
            timestampsAppearing = 0;
            if (timestampLabel != null) {
                timestampLabel[0] = timestampLabel[1] = null;
            }
            return;
        }
        Spanned spanned = (Spanned) text;
        URLSpanNoUnderline[] links;
        try {
            links = spanned.getSpans(0, spanned.length(), URLSpanNoUnderline.class);
        } catch (Exception e) {
            FileLog.e(e);
            timestamps = null;
            currentTimestamp = -1;
            timestampsAppearing = 0;
            if (timestampLabel != null) {
                timestampLabel[0] = timestampLabel[1] = null;
            }
            return;
        }
        timestamps = new ArrayList<>();
        timestampsAppearing = 0;
        if (timestampLabelPaint == null) {
            timestampLabelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            timestampLabelPaint.setTextSize(AndroidUtilities.dp(12));
            timestampLabelPaint.setColor(0xffffffff);
        }
        for (int i = 0; i < links.length; ++i) {
            URLSpanNoUnderline link = links[i];
            if (link != null && link.getURL() != null && link.label != null && link.getURL().startsWith("audio?")) {
                Integer seconds = Utilities.parseInt(link.getURL().substring(6));
                if (seconds != null && seconds >= 0) {
                    float position = seconds * 1000L / (float) videoDuration;
                    String label = link.label;
                    SpannableStringBuilder builder = new SpannableStringBuilder(label);
                    Emoji.replaceEmoji(builder, timestampLabelPaint.getFontMetricsInt(), AndroidUtilities.dp(14), false);
                    timestamps.add(new Pair<>(position, builder));
                }
            }
        }
        Collections.sort(timestamps, (a, b) -> {
            if (a.first > b.first) {
                return 1;
            } else if (b.first > a.first) {
                return -1;
            } else {
                return 0;
            }
        });
    }

    private void drawProgressBar(Canvas canvas, RectF rect, Paint paint) {
        float radius = AndroidUtilities.dp(2);
        if (timestamps == null || timestamps.isEmpty()) {
            canvas.drawRoundRect(rect, radius, radius, paint);
        } else {
            float lineWidth = rect.bottom - rect.top;
            float left = selectorWidth / 2f;
            float right = getMeasuredWidth() - selectorWidth / 2f;
            AndroidUtilities.rectTmp.set(rect);
            float halfGap = AndroidUtilities.dp(TIMESTAMP_GAP * timestampsAppearing) / 2f;
            if (tmpPath == null) {
                tmpPath = new Path();
            }
            tmpPath.reset();
            float minDur = AndroidUtilities.dp(4) / (right - left);
            int start = -1, end = -1;
            for (int i = 0; i < timestamps.size(); ++i) {
                if (timestamps.get(i).first >= minDur) {
                    start = i;
                    break;
                }
            }
            if (start < 0) {
                start = 0;
            }
            for (int i = timestamps.size() - 1; i >= 0; --i) {
                if (1f - timestamps.get(i).first >= minDur) {
                    end = i + 1;
                    break;
                }
            }
            if (end < 0) {
                end = timestamps.size();
            }
            boolean first = true;
            for (int i = start; i <= end; ++i) {
                float from = i == start ? 0 : timestamps.get(i - 1).first;
                float to = i == end ? 1 : timestamps.get(i).first;
                while (i != end && i != 0 && i < timestamps.size() - 1 && timestamps.get(i).first - from <= minDur) {
                    i++;
                    to = timestamps.get(i).first;
                }

                AndroidUtilities.rectTmp.left = AndroidUtilities.lerp(left, right, from) + (i > 0 ? halfGap : 0);
                AndroidUtilities.rectTmp.right = AndroidUtilities.lerp(left, right, to) - (i < end ? halfGap : 0);

                boolean last;
                if (last = AndroidUtilities.rectTmp.right > rect.right) {
                    AndroidUtilities.rectTmp.right = rect.right;
                }
                if (AndroidUtilities.rectTmp.right < rect.left) {
                    continue;
                }
                if (AndroidUtilities.rectTmp.left < rect.left) {
                    AndroidUtilities.rectTmp.left = rect.left;
                }

                if (tmpRadii == null) {
                    tmpRadii = new float[8];
                }
                if (i == start || last && AndroidUtilities.rectTmp.left >= rect.left) {
                    tmpRadii[0] = tmpRadii[1] = tmpRadii[6] = tmpRadii[7] = radius;
                    tmpRadii[2] = tmpRadii[3] = tmpRadii[4] = tmpRadii[5] = radius * 0.7f * timestampsAppearing;
                } else if (i >= end) {
                    tmpRadii[0] = tmpRadii[1] = tmpRadii[6] = tmpRadii[7] = radius * 0.7f * timestampsAppearing;
                    tmpRadii[2] = tmpRadii[3] = tmpRadii[4] = tmpRadii[5] = radius;
                } else {
                    tmpRadii[0] = tmpRadii[1] = tmpRadii[6] = tmpRadii[7] =
                    tmpRadii[2] = tmpRadii[3] = tmpRadii[4] = tmpRadii[5] = radius * 0.7f * timestampsAppearing;
                }
                tmpPath.addRoundRect(AndroidUtilities.rectTmp, tmpRadii, Path.Direction.CW);

                if (last) {
                    break;
                }
            }
            canvas.drawPath(tmpPath, paint);
        }
    }

    private void drawTimestampLabel(Canvas canvas) {
        if (timestamps == null || timestamps.isEmpty()) {
            return;
        }

        float progress = getProgress();

        int timestampIndex = -1;
        for (int i = timestamps.size() - 1; i >= 0; --i) {
            if (timestamps.get(i).first - 0.001f <= progress) {
                timestampIndex = i;
                break;
            }
        }

        if (timestampLabel == null) {
            timestampLabel = new StaticLayout[2];
        }

        float left = selectorWidth / 2f;
        float right = getMeasuredWidth() - selectorWidth / 2f;
        float rightPadded = right;
        float width = Math.abs(left - rightPadded) - AndroidUtilities.dp(16 + 50);

        if (lastWidth > 0 && Math.abs(lastWidth - width) > 0.01f) {
            if (timestampLabel[0] != null) {
                timestampLabel[0] = makeStaticLayout(timestampLabel[0].getText(), (int) width);
            }
            if (timestampLabel[1] != null) {
                timestampLabel[1] = makeStaticLayout(timestampLabel[1].getText(), (int) width);
            }
        }
        lastWidth = width;

        if (timestampIndex != currentTimestamp) {
            timestampLabel[1] = timestampLabel[0];
            if (pressed) {
                try {
                    performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                } catch (Exception ignore) {}
            }
            if (timestampIndex >= 0 && timestampIndex < timestamps.size()) {
                CharSequence label = timestamps.get(timestampIndex).second;
                if (label == null) {
                    timestampLabel[0] = null;
                } else {
                    timestampLabel[0] = makeStaticLayout(label, (int) width);
                }
            } else {
                timestampLabel[0] = null;
            }
            timestampChangeT = 0;
            if (timestampIndex == -1) {
                timestampChangeDirection = -1;
            } else if (currentTimestamp == -1) {
                timestampChangeDirection = 1;
            } else if (timestampIndex < currentTimestamp) {
                timestampChangeDirection = -1;
            } else if (timestampIndex > currentTimestamp) {
                timestampChangeDirection = 1;
            }
            lastTimestamp = currentTimestamp;
            currentTimestamp = timestampIndex;
        }
        if (timestampChangeT < 1f) {
            long tx = Math.min(17, Math.abs(SystemClock.elapsedRealtime() - lastTimestampUpdate));
            float duration = timestamps.size() > 8 ? 160f : 220f;
            timestampChangeT = Math.min(timestampChangeT + tx / duration, 1);
            invalidate();
            lastTimestampUpdate = SystemClock.elapsedRealtime();
        }
        if (timestampsAppearing < 1f) {
            long tx = Math.min(17, Math.abs(SystemClock.elapsedRealtime() - lastTimestampUpdate));
            timestampsAppearing = Math.min(timestampsAppearing + tx / 200f, 1);
            invalidate();
            lastTimestampsAppearingUpdate = SystemClock.elapsedRealtime();
        }
        float changeT = CubicBezierInterpolator.DEFAULT.getInterpolation(timestampChangeT);

        canvas.save();
        float bottom = getMeasuredHeight() / 2f + AndroidUtilities.dp(13);
        canvas.translate(left + AndroidUtilities.dp(25), bottom);
        timestampLabelPaint.setColor(getThemedColor(Theme.key_player_time));
        if (timestampLabel[1] != null) {
            canvas.save();
            if (timestampChangeDirection != 0) {
                canvas.translate(AndroidUtilities.dp(8) + AndroidUtilities.dp(16) * -timestampChangeDirection * changeT, 0);
            }
            canvas.translate(0, -timestampLabel[1].getHeight() / 2f);
            timestampLabelPaint.setAlpha((int) (255 * (1f - changeT) * timestampsAppearing));
            timestampLabel[1].draw(canvas);
            canvas.restore();
        }
        if (timestampLabel[0] != null) {
            canvas.save();
            if (timestampChangeDirection != 0) {
                canvas.translate(AndroidUtilities.dp(8) + AndroidUtilities.dp(16) * timestampChangeDirection * (1f - changeT), 0);
            }
            canvas.translate(0, -timestampLabel[0].getHeight() / 2f);
            timestampLabelPaint.setAlpha((int) (255 * changeT * timestampsAppearing));
            timestampLabel[0].draw(canvas);
            canvas.restore();
        }
        canvas.restore();
    }

    private StaticLayout makeStaticLayout(CharSequence text, int width) {
        if (timestampLabelPaint == null) {
            timestampLabelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            timestampLabelPaint.setTextSize(AndroidUtilities.dp(12));
        }
        timestampLabelPaint.setColor(getThemedColor(Theme.key_player_time));
        if (text == null) {
            text = "";
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return StaticLayout.Builder.obtain(text, 0, text.length(), timestampLabelPaint, width)
                    .setMaxLines(1)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .setEllipsizedWidth(Math.min(AndroidUtilities.dp(400), width))
                    .build();
        } else {
            return new StaticLayout(
                    text,
                    0,
                    text.length(),
                    timestampLabelPaint,
                    width,
                    Layout.Alignment.ALIGN_CENTER,
                    1,
                    0,
                    false,
                    TextUtils.TruncateAt.END,
                    Math.min(AndroidUtilities.dp(400), (int) width)
            );
        }
    }

    public SeekBarAccessibilityDelegate getSeekBarAccessibilityDelegate() {
        return seekBarAccessibilityDelegate;
    }

    private int getThemedColor(String key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }
}
