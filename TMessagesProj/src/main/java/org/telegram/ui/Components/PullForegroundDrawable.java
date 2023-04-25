package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.TopicsFragment;

public class PullForegroundDrawable {

    public final static float SNAP_HEIGHT = 0.85f;
    public final static float startPullParallax = 0.45f;
    public final static float endPullParallax = 0.25f;
    public final static float startPullOverScroll = 0.2f;
    public final static long minPullingTime = 200L;
    public int scrollDy;

    private int backgroundColorKey = Theme.key_chats_archivePullDownBackground;
    private int backgroundActiveColorKey = Theme.key_chats_archivePullDownBackgroundActive;
    private int avatarBackgroundColorKey = Theme.key_avatar_backgroundArchivedHidden;
    private boolean changeAvatarColor = true;

    private final Paint paintSecondary = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintWhite = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintBackgroundAccent = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint backgroundPaint = new Paint();
    private final RectF rectF = new RectF();
    private final TextPaint tooltipTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final ArrowDrawable arrowDrawable = new ArrowDrawable();
    private int generalTopicDrawableColor;
    private Drawable generalTopicDrawable;
    private final Path circleClipPath = new Path();

    private float textSwappingProgress = 1f;
    private float arrowRotateProgress = 1f;
    private boolean animateToEndText;
    private boolean arrowAnimateTo;
    private ValueAnimator textSwipingAnimator;

    private ValueAnimator accentRevalAnimatorIn;
    private ValueAnimator accentRevalAnimatorOut;
    private float accentRevalProgress = 1f;
    private float accentRevalProgressOut = 1f;

    private float textInProgress;
    private boolean animateToTextIn;
    private ValueAnimator textIntAnimator;
    private ValueAnimator arrowRotateAnimator;

    private AnimatorSet outAnimator;
    public float outProgress;
    private float bounceProgress;
    private boolean animateOut;
    private boolean bounceIn;
    private boolean animateToColorize;

    private View cell;
    private RecyclerListView listView;

    public float pullProgress;

    public float outCy;
    public float outCx;
    public float outRadius;
    public float outImageSize;
    public float outOverScroll;

    private StaticLayout pullTooltipLayout;
    private float pullTooltipLayoutLeft, pullTooltipLayoutWidth;
    private StaticLayout releaseTooltipLayout;
    private float releaseTooltipLayoutLeft, releaseTooltipLayoutWidth;
    private boolean willDraw;

    private boolean isOut;

    private float touchSlop;

    private ValueAnimator.AnimatorUpdateListener textSwappingUpdateListener = animation -> {
        textSwappingProgress = (float) animation.getAnimatedValue();
        if (cell != null) {
            cell.invalidate();
        }
    };

    private ValueAnimator.AnimatorUpdateListener textInUpdateListener = animation -> {
        textInProgress = (float) animation.getAnimatedValue();
        if (cell != null) {
            cell.invalidate();
        }
    };

    public PullForegroundDrawable(CharSequence pullText, CharSequence releaseText) {
        tooltipTextPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
//        tooltipTextPaint.setTextAlign(Paint.Align.CENTER);
        tooltipTextPaint.setTextSize(AndroidUtilities.dp(16));

        final ViewConfiguration vc = ViewConfiguration.get(ApplicationLoader.applicationContext);
        touchSlop = vc.getScaledTouchSlop();

        pullTooltipLayout = new StaticLayout(pullText, 0, pullText.length(), tooltipTextPaint, AndroidUtilities.displaySize.x, Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
        pullTooltipLayoutLeft = pullTooltipLayout.getLineCount() > 0 ? pullTooltipLayout.getLineLeft(0) : 0;
        pullTooltipLayoutWidth = pullTooltipLayout.getLineCount() > 0 ? pullTooltipLayout.getLineWidth(0) : 0;
        releaseTooltipLayout = new StaticLayout(releaseText, 0, releaseText.length(), tooltipTextPaint, AndroidUtilities.displaySize.x, Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
        releaseTooltipLayoutLeft = releaseTooltipLayout.getLineCount() > 0 ? releaseTooltipLayout.getLineLeft(0) : 0;
        releaseTooltipLayoutWidth = releaseTooltipLayout.getLineCount() > 0 ? releaseTooltipLayout.getLineWidth(0) : 0;

        try {
            generalTopicDrawable = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.msg_filled_general).mutate();
        } catch (Exception ignore) {}
    }

    public static int getMaxOverscroll() {
        return AndroidUtilities.dp(72);
    }

    public void setColors(int background, int active) {
        backgroundColorKey = background;
        backgroundActiveColorKey = active;
        changeAvatarColor = false;
        updateColors();
    }

    public void setCell(View view) {
        cell = view;
        updateColors();
    }

    public void updateColors() {
        int primaryColor = Color.WHITE;
        int backgroundColor = Theme.getColor(backgroundColorKey);

        tooltipTextPaint.setColor(primaryColor);
        paintWhite.setColor(primaryColor);
        paintSecondary.setColor(ColorUtils.setAlphaComponent(primaryColor, 100));
        backgroundPaint.setColor(backgroundColor);
        arrowDrawable.setColor(backgroundColor);
        paintBackgroundAccent.setColor(Theme.getColor(avatarBackgroundColorKey));
    }

    public void setListView(RecyclerListView listView) {
        this.listView = listView;
    }

    public void drawOverScroll(Canvas canvas) {
        draw(canvas, true);
    }

    public void draw(Canvas canvas) {
        draw(canvas, false);
    }

    protected float getViewOffset() {
        return 0;
    }

    public void draw(Canvas canvas, boolean header) {
        if (!willDraw || isOut || cell == null || listView == null) {
            return;
        }
        boolean isTopic = cell instanceof TopicsFragment.TopicDialogCell;
        int startPadding = AndroidUtilities.dp(isTopic ? 15 : 28);
        int smallMargin = AndroidUtilities.dp(8);
        int radius = AndroidUtilities.dp(9);
        int diameter = AndroidUtilities.dp(18);

        int overscroll = (int) getViewOffset();
        int visibleHeight = (int) (cell.getHeight() * pullProgress);

        float bounceP = bounceIn ? (0.07f * bounceProgress) - 0.05f : 0.02f * bounceProgress;

        updateTextProgress(pullProgress);

        float outProgressHalf = outProgress * 2f;
        if (outProgressHalf > 1f) {
            outProgressHalf = 1f;
        }

        float cX = outCx;
        float cY = outCy;
        if (header) {
            cY += overscroll;
        }

        int smallCircleX = startPadding + radius;
        int smallCircleY = cell.getMeasuredHeight() - smallMargin - radius;
        if (header) {
            smallCircleY += overscroll;
        }

        float startPullProgress = visibleHeight > diameter + smallMargin * 2 ? 1f : (float) visibleHeight / (diameter + smallMargin * 2);

        canvas.save();

        if (header) {
            canvas.clipRect(0, 0, listView.getMeasuredWidth(), overscroll + 1);
        }
        if (outProgress == 0f) {
            if (!(accentRevalProgress == 1f || accentRevalProgressOut == 1)) {
                canvas.drawPaint(backgroundPaint);
            }
        } else {
            float outBackgroundRadius = outRadius + (outRadius * bounceP) + (cell.getWidth() - outRadius) * (1f - outProgress);

            if (!(accentRevalProgress == 1f || accentRevalProgressOut == 1)) {
                canvas.drawCircle(cX, cY, outBackgroundRadius, backgroundPaint);
            }

            circleClipPath.reset();
            rectF.set(cX - outBackgroundRadius, cY - outBackgroundRadius, cX + outBackgroundRadius, cY + outBackgroundRadius);
            circleClipPath.addOval(rectF, Path.Direction.CW);
            canvas.clipPath(circleClipPath);
        }

        if (animateToColorize) {
            if (accentRevalProgressOut > accentRevalProgress) {
                canvas.save();
                canvas.translate((cX - smallCircleX) * (outProgress), (cY - smallCircleY) * (outProgress));
                canvas.drawCircle(smallCircleX, smallCircleY, cell.getWidth() * accentRevalProgressOut, backgroundPaint);
                canvas.restore();
            }
            if (accentRevalProgress > 0f) {
                canvas.save();
                canvas.translate((cX - smallCircleX) * (outProgress), (cY - smallCircleY) * (outProgress));
                canvas.drawCircle(smallCircleX, smallCircleY, cell.getWidth() * accentRevalProgress, paintBackgroundAccent);
                canvas.restore();
            }
        } else {
            if (accentRevalProgress > accentRevalProgressOut) {
                canvas.save();
                canvas.translate((cX - smallCircleX) * (outProgress), (cY - smallCircleY) * (outProgress));
                canvas.drawCircle(smallCircleX, smallCircleY, cell.getWidth() * accentRevalProgress, paintBackgroundAccent);
                canvas.restore();
            }
            if (accentRevalProgressOut > 0f) {
                canvas.save();
                canvas.translate((cX - smallCircleX) * (outProgress), (cY - smallCircleY) * (outProgress));
                canvas.drawCircle(smallCircleX, smallCircleY, cell.getWidth() * accentRevalProgressOut, backgroundPaint);
                canvas.restore();
            }
        }

        if (visibleHeight > diameter + smallMargin * 2) {
            paintSecondary.setAlpha((int) ((1f - outProgressHalf) * 0.4f * startPullProgress * 255));
            if (header) {
                rectF.set(startPadding, smallMargin, startPadding + diameter, smallMargin + overscroll + radius);
            } else {
                rectF.set(startPadding, cell.getHeight() - visibleHeight + smallMargin - overscroll, startPadding + diameter, cell.getHeight() - smallMargin);
            }
            canvas.drawRoundRect(rectF, radius, radius, paintSecondary);
        }

        if (header) {
            canvas.restore();
            return;
        }

        if (isTopic) {
            smallCircleY -= (cell.getMeasuredHeight() - AndroidUtilities.dp(41)) * outProgress;
        }
        if (outProgress == 0f || isTopic) {
            paintWhite.setAlpha((int) (startPullProgress * 255 * (1f - outProgress)));
            canvas.drawCircle(smallCircleX, smallCircleY, radius, paintWhite);

            int ih = arrowDrawable.getIntrinsicHeight();
            int iw = arrowDrawable.getIntrinsicWidth();

            arrowDrawable.setBounds(smallCircleX - (iw >> 1), smallCircleY - (ih >> 1), smallCircleX + (iw >> 1), smallCircleY + (ih >> 1));

            float rotateProgress = 1f - arrowRotateProgress;
            if (rotateProgress < 0) {
                rotateProgress = 0f;
            }
            rotateProgress = 1f - rotateProgress;
            canvas.save();
            canvas.rotate(180 * rotateProgress, smallCircleX, smallCircleY);
            canvas.translate(0, AndroidUtilities.dpf2(1f) * 1f - rotateProgress);
            arrowDrawable.setColor(animateToColorize ? paintBackgroundAccent.getColor() : Theme.getColor(backgroundColorKey));
            arrowDrawable.setAlpha((int) (255 * (1f - outProgress)));
            arrowDrawable.draw(canvas);
            canvas.restore();
        }

        if (pullProgress > 0f) {
            textIn();
        }

        float textY = cell.getHeight() - ((diameter + smallMargin * 2) / 2f) + AndroidUtilities.dp(6);
        float textCx = cell.getWidth() / 2f - AndroidUtilities.dp(2);

        if (textSwappingProgress > 0 && textSwappingProgress < 1f) {
            canvas.save();
            float scale = 0.8f + 0.2f * textSwappingProgress;
            canvas.scale(scale, scale, textCx, textY + AndroidUtilities.dp(16) * (1f - textSwappingProgress));
        }
        canvas.saveLayerAlpha(0, 0, cell.getMeasuredWidth(), cell.getMeasuredHeight(), (int) (255 * textSwappingProgress * startPullProgress * textInProgress), Canvas.ALL_SAVE_FLAG);
        canvas.translate(textCx - pullTooltipLayoutLeft - pullTooltipLayoutWidth / 2f, textY + AndroidUtilities.dp(8) * (1f - textSwappingProgress) - tooltipTextPaint.getTextSize());
        pullTooltipLayout.draw(canvas);
        canvas.restore();

        if (textSwappingProgress > 0 && textSwappingProgress < 1f) {
            canvas.restore();
        }

        if (textSwappingProgress > 0 && textSwappingProgress < 1f) {
            canvas.save();
            float scale = 0.9f + 0.1f * (1f - textSwappingProgress);
            canvas.scale(scale, scale, textCx, textY - AndroidUtilities.dp(8) * (textSwappingProgress));
        }
        canvas.saveLayerAlpha(0, 0, cell.getMeasuredWidth(), cell.getMeasuredHeight(), (int) (255 * (1f - textSwappingProgress) * startPullProgress * textInProgress), Canvas.ALL_SAVE_FLAG);
        canvas.translate(textCx - releaseTooltipLayoutLeft - releaseTooltipLayoutWidth / 2f, textY + AndroidUtilities.dp(8) * (textSwappingProgress) - tooltipTextPaint.getTextSize());
        releaseTooltipLayout.draw(canvas);
        canvas.restore();

        if (textSwappingProgress > 0 && textSwappingProgress < 1f) {
            canvas.restore();
        }
        canvas.restore();

        if (!isTopic && changeAvatarColor && outProgress > 0) {
            canvas.save();
            int iw = Theme.dialogs_archiveAvatarDrawable.getIntrinsicWidth();

            int startCx = startPadding + radius;
            int startCy = cell.getHeight() - smallMargin - radius;

            float scaleStart = (float) AndroidUtilities.dp(24) / iw;
            float scale = scaleStart + (1f - scaleStart) * outProgress + bounceP;

            int x = (int) cX;
            int y = (int) cY;
            canvas.translate((startCx - cX) * (1f - outProgress), (startCy - cY) * (1f - outProgress));
            canvas.scale(scale, scale, cX, cY);

            Theme.dialogs_archiveAvatarDrawable.setProgress(0f);
            if (!Theme.dialogs_archiveAvatarDrawableRecolored) {
                Theme.dialogs_archiveAvatarDrawable.beginApplyLayerColors();
                Theme.dialogs_archiveAvatarDrawable.setLayerColor("Arrow1.**", Theme.getNonAnimatedColor(avatarBackgroundColorKey));
                Theme.dialogs_archiveAvatarDrawable.setLayerColor("Arrow2.**", Theme.getNonAnimatedColor(avatarBackgroundColorKey));
                Theme.dialogs_archiveAvatarDrawable.commitApplyLayerColors();
                Theme.dialogs_archiveAvatarDrawableRecolored = true;
            }

            Theme.dialogs_archiveAvatarDrawable.setBounds((int) (cX - iw / 2f), (int) (cY - iw / 2f), (int) (cX + iw / 2f), (int) (cY + iw / 2f));
            Theme.dialogs_archiveAvatarDrawable.draw(canvas);

            canvas.restore();
        }

//        if (isTopic) {
//            int color = arrowDrawable.paint.getColor();
//            if (generalTopicDrawableColor != color) {
//                generalTopicDrawable.setColorFilter(new PorterDuffColorFilter(generalTopicDrawableColor = color, PorterDuff.Mode.MULTIPLY));
//            }
//
//            int ih = AndroidUtilities.lerp(AndroidUtilities.dp(14), AndroidUtilities.dp(28), outProgress);
//            int iw = AndroidUtilities.lerp(AndroidUtilities.dp(14), AndroidUtilities.dp(28), outProgress);
//            generalTopicDrawable.setBounds(smallCircleX - (iw >> 1), smallCircleY - (ih >> 1), smallCircleX + (iw >> 1), smallCircleY + (ih >> 1));
//            generalTopicDrawable.setAlpha((int) (255 * outProgress));
//            generalTopicDrawable.draw(canvas);
//        }
    }


    private void updateTextProgress(float pullProgress) {
        boolean endText = pullProgress > SNAP_HEIGHT;
        if (animateToEndText != endText) {
            animateToEndText = endText;
            if (textInProgress == 0f) {
                if (textSwipingAnimator != null) {
                    textSwipingAnimator.cancel();
                }
                textSwappingProgress = endText ? 0f : 1f;
            } else {
                if (textSwipingAnimator != null) {
                    textSwipingAnimator.cancel();
                }
                textSwipingAnimator = ValueAnimator.ofFloat(textSwappingProgress, endText ? 0f : 1f);
                textSwipingAnimator.addUpdateListener(textSwappingUpdateListener);
                textSwipingAnimator.setInterpolator(new LinearInterpolator());
                textSwipingAnimator.setDuration(170);
                textSwipingAnimator.start();
            }
        }

        if (endText != arrowAnimateTo) {
            arrowAnimateTo = endText;
            if (arrowRotateAnimator != null) {
                arrowRotateAnimator.cancel();
            }
            arrowRotateAnimator = ValueAnimator.ofFloat(arrowRotateProgress, arrowAnimateTo ? 0f : 1f);
            arrowRotateAnimator.addUpdateListener(animation -> {
                arrowRotateProgress = (float) animation.getAnimatedValue();
                if (cell != null) {
                    cell.invalidate();
                }
            });
            arrowRotateAnimator.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
            arrowRotateAnimator.setDuration(250);
            arrowRotateAnimator.start();
        }
    }

    public void colorize(boolean colorize) {
        if (animateToColorize != colorize) {
            animateToColorize = colorize;
            if (colorize) {
                if (accentRevalAnimatorIn != null) {
                    accentRevalAnimatorIn.cancel();
                    accentRevalAnimatorIn = null;
                }

                accentRevalProgress = 0f;
                accentRevalAnimatorIn = ValueAnimator.ofFloat(accentRevalProgress, 1f);
                accentRevalAnimatorIn.addUpdateListener(animation -> {
                    accentRevalProgress = (float) animation.getAnimatedValue();
                    if (cell != null) {
                        cell.invalidate();
                    }
                    if (listView != null) {
                        listView.invalidate();
                    }
                });
                accentRevalAnimatorIn.setInterpolator(AndroidUtilities.accelerateInterpolator);
                accentRevalAnimatorIn.setDuration(230);
                accentRevalAnimatorIn.start();
            } else {
                if (accentRevalAnimatorOut != null) {
                    accentRevalAnimatorOut.cancel();
                    accentRevalAnimatorOut = null;
                }

                accentRevalProgressOut = 0f;
                accentRevalAnimatorOut = ValueAnimator.ofFloat(accentRevalProgressOut, 1f);
                accentRevalAnimatorOut.addUpdateListener(animation -> {
                    accentRevalProgressOut = (float) animation.getAnimatedValue();
                    if (cell != null) {
                        cell.invalidate();
                    }
                    if (listView != null) {
                        listView.invalidate();
                    }
                });
                accentRevalAnimatorOut.setInterpolator(AndroidUtilities.accelerateInterpolator);
                accentRevalAnimatorOut.setDuration(230);
                accentRevalAnimatorOut.start();
            }
        }
    }

    Runnable textInRunnable = new Runnable() {
        @Override
        public void run() {
            animateToTextIn = true;
            if (textIntAnimator != null) {
                textIntAnimator.cancel();
            }
            textInProgress = 0f;
            textIntAnimator = ValueAnimator.ofFloat(0f, 1f);
            textIntAnimator.addUpdateListener(textInUpdateListener);
            textIntAnimator.setInterpolator(new LinearInterpolator());
            textIntAnimator.setDuration(150);
            textIntAnimator.start();
        }
    };

    boolean wasSendCallback = false;

    private void textIn() {
        if (!animateToTextIn) {
            if (Math.abs(scrollDy) < touchSlop * 0.5f) {
                if (!wasSendCallback) {
                    textInProgress = 1f;
                    animateToTextIn = true;
                }
            } else {
                wasSendCallback = true;
                cell.removeCallbacks(textInRunnable);
                cell.postDelayed(textInRunnable, 200);
            }
        }
    }

    public void startOutAnimation() {
        if (animateOut || listView == null) {
            return;
        }
        if (outAnimator != null) {
            outAnimator.removeAllListeners();
            outAnimator.cancel();
        }
        animateOut = true;
        bounceIn = true;
        bounceProgress = 0f;
        outOverScroll = listView.getTranslationY() / AndroidUtilities.dp(100);
        ValueAnimator out = ValueAnimator.ofFloat(0f, 1f);
        out.addUpdateListener(animation -> {
            setOutProgress((float) animation.getAnimatedValue());
            if (cell != null) {
                cell.invalidate();
            }
        });

        out.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        out.setDuration(250);

        ValueAnimator bounceIn = ValueAnimator.ofFloat(0f, 1f);
        bounceIn.addUpdateListener(animation -> {
            bounceProgress = (float) animation.getAnimatedValue();
            this.bounceIn = true;
            if (cell != null) {
                cell.invalidate();
            }
        });

        bounceIn.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
        bounceIn.setDuration(150);

        ValueAnimator bounceOut = ValueAnimator.ofFloat(1f, 0f);
        bounceOut.addUpdateListener(animation -> {
            bounceProgress = (float) animation.getAnimatedValue();
            this.bounceIn = false;
            if (cell != null) {
                cell.invalidate();
            }
        });

        bounceOut.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
        bounceOut.setDuration(135);

        outAnimator = new AnimatorSet();
        outAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                doNotShow();
            }
        });

        AnimatorSet bounce = new AnimatorSet();
        bounce.playSequentially(bounceIn, bounceOut);
        bounce.setStartDelay(180);

        outAnimator.playTogether(out, bounce);
        outAnimator.start();
    }

    private void setOutProgress(float value) {
        outProgress = value;
        int color = ColorUtils.blendARGB(Theme.getNonAnimatedColor(avatarBackgroundColorKey), Theme.getNonAnimatedColor(backgroundActiveColorKey), 1f - outProgress);
        paintBackgroundAccent.setColor(color);
        if (changeAvatarColor && isDraw()) {
            Theme.dialogs_archiveAvatarDrawable.beginApplyLayerColors();
            Theme.dialogs_archiveAvatarDrawable.setLayerColor("Arrow1.**", color);
            Theme.dialogs_archiveAvatarDrawable.setLayerColor("Arrow2.**", color);
            Theme.dialogs_archiveAvatarDrawable.commitApplyLayerColors();
            Theme.dialogs_archiveAvatarDrawableRecolored = true;
        }
    }

    public void doNotShow() {
        if (textSwipingAnimator != null) {
            textSwipingAnimator.cancel();
        }
        if (textIntAnimator != null) {
            textIntAnimator.cancel();
        }
        if (cell != null) {
            cell.removeCallbacks(textInRunnable);
        }
        if (accentRevalAnimatorIn != null) {
            accentRevalAnimatorIn.cancel();
        }
        textSwappingProgress = 1f;
        arrowRotateProgress = 1f;
        animateToEndText = false;
        arrowAnimateTo = false;
        animateToTextIn = false;
        wasSendCallback = false;
        textInProgress = 0f;
        isOut = true;
        setOutProgress(1f);
        animateToColorize = false;
        accentRevalProgress = 0f;
    }

    public void showHidden() {
        if (outAnimator != null) {
            outAnimator.removeAllListeners();
            outAnimator.cancel();
        }
        setOutProgress(0f);
        isOut = false;
        animateOut = false;
    }

    public void destroyView() {
        cell = null;
        if (textSwipingAnimator != null) {
            textSwipingAnimator.cancel();
        }
        if (outAnimator != null) {
            outAnimator.removeAllListeners();
            outAnimator.cancel();
        }
    }

    public boolean isDraw() {
        return willDraw && !isOut;
    }


    public void setWillDraw(boolean b) {
        willDraw = b;
    }

    public void resetText() {
        if (textIntAnimator != null) {
            textIntAnimator.cancel();
        }
        if (cell != null) {
            cell.removeCallbacks(textInRunnable);
        }
        textInProgress = 0f;
        animateToTextIn = false;
        wasSendCallback = false;
    }

    public Paint getBackgroundPaint() {
        return backgroundPaint;
    }

    private class ArrowDrawable extends Drawable {

        private Path path = new Path();
        private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float lastDensity;

        public ArrowDrawable() {
            updatePath();
        }

        private void updatePath() {
            int h = AndroidUtilities.dp(18);
            path.reset();
            path.moveTo(h >> 1, AndroidUtilities.dpf2(4.98f));
            path.lineTo(AndroidUtilities.dpf2(4.95f), AndroidUtilities.dpf2(9f));
            path.lineTo(h - AndroidUtilities.dpf2(4.95f), AndroidUtilities.dpf2(9f));
            path.lineTo(h >> 1, AndroidUtilities.dpf2(4.98f));

            paint.setStyle(Paint.Style.FILL_AND_STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(AndroidUtilities.dpf2(1f));
            lastDensity = AndroidUtilities.density;
        }

        public void setColor(int color) {
            paint.setColor(color);
        }

        @Override
        public int getIntrinsicHeight() {
            return AndroidUtilities.dp(18);
        }

        @Override
        public int getIntrinsicWidth() {
            return getIntrinsicHeight();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (lastDensity != AndroidUtilities.density) {
                updatePath();
            }
            canvas.save();
            canvas.translate(getBounds().left, getBounds().top);
            canvas.drawPath(path, paint);
            int h = AndroidUtilities.dp(18);
            canvas.drawRect(AndroidUtilities.dpf2(7.56f), AndroidUtilities.dpf2(8f), h - AndroidUtilities.dpf2(7.56f), AndroidUtilities.dpf2(11.1f), paint);
            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {

        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {

        }

        @Override
        public int getOpacity() {
            return PixelFormat.UNKNOWN;
        }
    }
}
