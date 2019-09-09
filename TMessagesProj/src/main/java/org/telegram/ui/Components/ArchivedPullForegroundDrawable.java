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
import android.text.TextPaint;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;


public class ArchivedPullForegroundDrawable {

    public final static float SNAP_HEIGHT = 0.85f;
    public int scrollDy;


    private Paint paintSecondary = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint paintAccent = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint backgroundPaint = new Paint();
    private RectF rectF = new RectF();
    private Paint tooltipTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final ArrowDrawable arrowDrawable = new ArrowDrawable();


    private float textSwappingProgress = 1f;
    private float arrowRotateProgress = 1f;
    private boolean animateToEndText = false;
    private boolean arrowAnimateTo = false;
    private ValueAnimator textSwipingAnimator;

    private float textInProgress = 0f;
    private boolean animateToTextIn = false;
    private ValueAnimator textIntAnimator;
    private ValueAnimator arrowRotateAnimator;

    private AnimatorSet outAnimator;
    public float outProgress = 0f;
    private float bounceProgress = 0f;
    private boolean animateOut = false;
    private boolean bounceIn = false;


    private int startPadding = AndroidUtilities.dp(27);
    private int smallMargin = AndroidUtilities.dp(8);
    private int radius = AndroidUtilities.dp(9);
    private int diameter = AndroidUtilities.dp(18);

    private View dialogCell;
    private View contentView;
    private View listView;

    public float pullProgress;

    public float outCy;
    public float outCx;
    public int outRadius;
    public AvatarDrawable outDrawable;
    public float outOverScroll;

    private String pullTooltip;
    private String releaseTooltip;
    private boolean willDraw;

    private boolean isOut = false;

    private float touchSlop;

    private ValueAnimator.AnimatorUpdateListener textSwappingUpdateListener = animation -> {
        textSwappingProgress = (float) animation.getAnimatedValue();
        if (dialogCell != null) dialogCell.invalidate();
    };

    private ValueAnimator.AnimatorUpdateListener textInUpdateListener = animation -> {
        textInProgress = (float) animation.getAnimatedValue();
        if (dialogCell != null) dialogCell.invalidate();
    };

    public ArchivedPullForegroundDrawable() {
        tooltipTextPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        tooltipTextPaint.setTextAlign(Paint.Align.CENTER);
        tooltipTextPaint.setTextSize(AndroidUtilities.dp(16));

        final ViewConfiguration vc = ViewConfiguration.get(ApplicationLoader.applicationContext);
        touchSlop = vc.getScaledTouchSlop();
    }

    public void setDialogCell(View view) {
        dialogCell = view;

        int primaryColor = Color.WHITE;
        int backgroundColor = Theme.getColor(Theme.key_avatar_backgroundArchivedHidden);

        tooltipTextPaint.setColor(primaryColor);
        paintAccent.setColor(primaryColor);
        paintSecondary.setColor(ColorUtils.setAlphaComponent(primaryColor, 100));
        backgroundPaint.setColor(backgroundColor);
        arrowDrawable.setColor(backgroundColor);

        pullTooltip = LocaleController.getString("SwipeForArchive", R.string.SwipeForArchive);
        releaseTooltip = LocaleController.getString("ReleaseForArchive", R.string.ReleaseForArchive);
    }

    public void setParentViews(View contentView, View listView) {
        this.contentView = contentView;
        this.listView = listView;
    }

    public void drawOverScroll(Canvas canvas) {
        int overscroll = (int) listView.getTranslationY();

        float cX = outCx;
        float cY = outCy + overscroll;

        canvas.save();
        canvas.clipRect(0, 0, contentView.getMeasuredWidth(), overscroll + 1);
        if (outProgress == 0f) {
            canvas.drawPaint(backgroundPaint);
        } else {


            float outBackgroundRadius = outRadius + (dialogCell.getWidth() - outRadius) * (1f - outProgress);
            canvas.drawCircle(cX, cY, outBackgroundRadius, backgroundPaint);

            //clip rect work faster then clip path, and in this case users see no difference
            canvas.clipRect(
                    cX - outBackgroundRadius, cY - outBackgroundRadius,
                    cX + outBackgroundRadius, cY + outBackgroundRadius
            );
        }


        float outProgressHalf = outProgress * 2f;
        if (outProgressHalf > 1f) outProgressHalf = 1f;

        paintSecondary.setAlpha((int) ((1f - outProgressHalf) * 0.4f * 255));
        rectF.set(startPadding, smallMargin , startPadding + diameter, smallMargin + overscroll + radius);
        canvas.drawRoundRect(rectF, radius, radius, paintSecondary);



        canvas.restore();

    }


    public void draw(Canvas canvas) {
        if (!willDraw || isOut || dialogCell == null) return;

        int overscroll = (int) listView.getTranslationY();
        int visibleHeight = (int) (dialogCell.getHeight() * pullProgress);
        int invisibleHeight = dialogCell.getHeight() - visibleHeight;

        float bounceP = bounceIn ? (0.07f * bounceProgress) - 0.05f : 0.02f * bounceProgress;
        bounceP += bounceP * outOverScroll;

        //float bounceP = (0.1f * bounceProgress) - 0.1f;
        updateTextProgress(pullProgress);

        float outProgressHalf = outProgress * 2f;
        if (outProgressHalf > 1f) outProgressHalf = 1f;

        float cX = outCx;
        float cY = outCy;

        float startPullProgress = visibleHeight > diameter + smallMargin * 2 ? 1f :
                (float) visibleHeight / (diameter + smallMargin * 2);

        canvas.save();
        if (outProgress == 0f) {
            canvas.drawPaint(backgroundPaint);
        } else {


            float outBackgroundRadius = outRadius + (dialogCell.getWidth() - outRadius) * (1f - outProgress) + (outRadius * bounceP);
            canvas.drawCircle(cX, cY, outBackgroundRadius, backgroundPaint);

            //clip rect work faster then clip path, and in this case users see no difference
            canvas.clipRect(
                    cX - outBackgroundRadius, cY - outBackgroundRadius,
                    cX + outBackgroundRadius, cY + outBackgroundRadius
            );
        }

        if (visibleHeight > diameter + smallMargin * 2) {
            paintSecondary.setAlpha((int) ((1f - outProgressHalf) * 0.4f * startPullProgress * 255));
            rectF.set(startPadding, dialogCell.getHeight() - visibleHeight + smallMargin - overscroll,
                    startPadding + diameter, dialogCell.getHeight() - smallMargin);
            canvas.drawRoundRect(rectF, radius, radius, paintSecondary);
        }

        if (outProgress == 0f) {
            int x = startPadding + radius;
            int y = dialogCell.getMeasuredHeight() - smallMargin - radius;
            paintAccent.setAlpha((int) (startPullProgress * 255));
            canvas.drawCircle(x, y, radius, paintAccent);

            int ih = arrowDrawable.getIntrinsicHeight();
            int iw = arrowDrawable.getIntrinsicWidth();

            arrowDrawable.setBounds(
                    x - (iw >> 1), y - (ih >> 1),
                    x + (iw >> 1), y + (ih >> 1)
            );

            float rotateProgress = 1f - arrowRotateProgress;
            if (rotateProgress < 0) rotateProgress = 0f;
            rotateProgress = 1f - rotateProgress;
            canvas.save();
            canvas.rotate(180 * rotateProgress, x, y);
            canvas.translate(0, AndroidUtilities.dpf2(1f) * 1f - rotateProgress);
            arrowDrawable.draw(canvas);
            canvas.restore();
        }


        textIn();

        float textY = dialogCell.getHeight() - ((diameter + smallMargin * 2) / 2f) + AndroidUtilities.dp(6);
        tooltipTextPaint.setAlpha((int) (255 * textSwappingProgress * startPullProgress * textInProgress));

        float textCx = dialogCell.getWidth() / 2f - AndroidUtilities.dp(2);

        if (textSwappingProgress > 0 && textSwappingProgress < 1f) {
            canvas.save();
            float scale = 0.8f + 0.2f * textSwappingProgress;
            canvas.scale(scale, scale, textCx, textY + AndroidUtilities.dp(16) * (1f - textSwappingProgress));
        }
        canvas.drawText(pullTooltip, textCx,
                textY + AndroidUtilities.dp(8) * (1f - textSwappingProgress), tooltipTextPaint);

        if (textSwappingProgress > 0 && textSwappingProgress < 1f) {
            canvas.restore();
        }


        if (textSwappingProgress > 0 && textSwappingProgress < 1f) {
            canvas.save();
            float scale = 0.9f + 0.1f * (1f - textSwappingProgress);
            canvas.scale(scale, scale, textCx,
                    textY - AndroidUtilities.dp(8) * (textSwappingProgress));
        }
        tooltipTextPaint.setAlpha((int) (255 * (1f - textSwappingProgress) * startPullProgress * textInProgress));
        canvas.drawText(releaseTooltip, textCx,
                textY - AndroidUtilities.dp(8) * (textSwappingProgress), tooltipTextPaint);

        if (textSwappingProgress > 0 && textSwappingProgress < 1f) {
            canvas.restore();
        }
        canvas.restore();


        if (outProgress > 0) {
            canvas.save();
            int ih = Theme.dialogs_archiveAvatarDrawable.getIntrinsicHeight();
            int iw = Theme.dialogs_archiveAvatarDrawable.getIntrinsicWidth();

            int startCx = startPadding + radius;
            int startCy = dialogCell.getHeight() - smallMargin - radius;

            float scaleStart = (float) AndroidUtilities.dp(24) / iw;
            float scale = scaleStart + (1f - scaleStart) * outProgress + bounceP;


            int x = (int) cX;
            int y = (int) cY;
            canvas.translate((startCx - cX) * (1f - outProgress), (startCy - cY) * (1f - outProgress));
            canvas.scale(scale, scale, cX, cY);


            Theme.dialogs_archiveAvatarDrawable.setProgress(0f);
            if (!Theme.dialogs_archiveAvatarDrawableRecolored) {
                Theme.dialogs_archiveAvatarDrawable.beginApplyLayerColors();
                Theme.dialogs_archiveAvatarDrawable.setLayerColor("Arrow1.**", Theme.getColor(Theme.key_avatar_backgroundArchivedHidden));
                Theme.dialogs_archiveAvatarDrawable.setLayerColor("Arrow2.**", Theme.getColor(Theme.key_avatar_backgroundArchivedHidden));
                Theme.dialogs_archiveAvatarDrawable.commitApplyLayerColors();
                Theme.dialogs_archiveAvatarDrawableRecolored = true;
            }
            Theme.dialogs_archiveAvatarDrawable.setBounds(
                    x - (iw >> 1), y - (ih >> 1),
                    x + (iw >> 1), y + (ih >> 1)
            );
            Theme.dialogs_archiveAvatarDrawable.draw(canvas);

            canvas.restore();
        }
    }


    private void updateTextProgress(float pullProgress) {
        boolean endText = pullProgress > SNAP_HEIGHT;
        if (animateToEndText != endText) {
            animateToEndText = endText;
            if (textInProgress == 0f) {
                if (textSwipingAnimator != null) textSwipingAnimator.cancel();
                textSwappingProgress = endText ? 0f : 1f;
            } else {
                if (textSwipingAnimator != null) textSwipingAnimator.cancel();
                textSwipingAnimator = ValueAnimator.ofFloat(textSwappingProgress, endText ? 0f : 1f);
                textSwipingAnimator.addUpdateListener(textSwappingUpdateListener);
                textSwipingAnimator.setInterpolator(new LinearInterpolator());
                textSwipingAnimator.setDuration(170);
                textSwipingAnimator.start();
            }
        }


        if (endText != arrowAnimateTo) {
            arrowAnimateTo = endText;
            if (arrowRotateAnimator != null) arrowRotateAnimator.cancel();
            arrowRotateAnimator = ValueAnimator.ofFloat(arrowRotateProgress, arrowAnimateTo ? 0f : 1f);
            arrowRotateAnimator.addUpdateListener(animation -> {
                arrowRotateProgress = (float) animation.getAnimatedValue();
                if (dialogCell != null) dialogCell.invalidate();
            });
            arrowRotateAnimator.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
            arrowRotateAnimator.setDuration(250);
            arrowRotateAnimator.start();
        }
    }

    Runnable r = new Runnable() {
        @Override
        public void run() {
            animateToTextIn = true;

            if (textIntAnimator != null) textIntAnimator.cancel();
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
                dialogCell.removeCallbacks(r);
                dialogCell.postDelayed(r, 120);
            }
        }
    }

    public void startOutAnimation() {
        if (animateOut) return;
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
            outProgress = (float) animation.getAnimatedValue();
            if (dialogCell != null) dialogCell.invalidate();
        });

        out.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        out.setDuration(250);

        ValueAnimator bounceIn =
                ValueAnimator.ofFloat(0f, 1f);
        bounceIn.addUpdateListener(animation -> {
            bounceProgress = (float) animation.getAnimatedValue();
            this.bounceIn = true;
            if (dialogCell != null) dialogCell.invalidate();
        });

        bounceIn.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
        bounceIn.setDuration(150);

        ValueAnimator bounceOut =
                ValueAnimator.ofFloat(1f, 0f);
        bounceOut.addUpdateListener(animation -> {
            bounceProgress = (float) animation.getAnimatedValue();
            this.bounceIn = false;
            if (dialogCell != null) dialogCell.invalidate();
        });

        bounceOut.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
        bounceOut.setDuration(135);

        outAnimator = new AnimatorSet();
        outAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (textSwipingAnimator != null) textSwipingAnimator.cancel();
                if (textIntAnimator != null) textIntAnimator.cancel();
                textSwappingProgress = 1f;
                arrowRotateProgress = 1f;
                animateToEndText = false;
                arrowAnimateTo = false;
                animateToTextIn = false;
                wasSendCallback = false;
                textInProgress = 0f;
                isOut = true;

            }
        });

        AnimatorSet bounce = new AnimatorSet();
        bounce.playSequentially(bounceIn, bounceOut);
        bounce.setStartDelay(200);

        outAnimator.playTogether(out, bounce);
        outAnimator.start();
    }

    public void doNotShow() {
        if (outAnimator != null) {
            outAnimator.removeAllListeners();
            outAnimator.cancel();
        }
        outProgress = 1f;
        animateOut = true;
    }

    public void showHidden() {
        if (outAnimator != null) {
            outAnimator.removeAllListeners();
            outAnimator.cancel();
        }
        outProgress = 0f;
        isOut = false;
        animateOut = false;
    }

    public void destroyView() {
        dialogCell = null;
        if (textSwipingAnimator != null) textSwipingAnimator.cancel();
        if (outAnimator != null) {
            outAnimator.removeAllListeners();
            outAnimator.cancel();
        }
    }

    public boolean isDraw() {
        return !(!willDraw || isOut || dialogCell == null);
    }


    public void setWillDraw(boolean b) {
        willDraw = b;
    }

    public void resetText() {
        if (textIntAnimator != null) textIntAnimator.cancel();
        textInProgress = 0f;
        animateToTextIn = false;
        wasSendCallback = false;
    }

    public Paint getBackgroundPaint() {
        return backgroundPaint;
    }

    private class ArrowDrawable extends Drawable {

        Path path = new Path();
        Paint paint = new Paint();

        public ArrowDrawable() {
            int h = AndroidUtilities.dp(18);
            path.moveTo(h >> 1, AndroidUtilities.dpf2(4.58f));
            path.lineTo(AndroidUtilities.dpf2(3.95f), AndroidUtilities.dpf2(9.66f));
            path.lineTo(AndroidUtilities.dpf2(7.06f), AndroidUtilities.dpf2(9.66f));
            path.lineTo(AndroidUtilities.dpf2(7.06f), AndroidUtilities.dpf2(11.6f));
            path.lineTo(h - AndroidUtilities.dpf2(7.06f), AndroidUtilities.dpf2(11.6f));
            path.lineTo(h - AndroidUtilities.dpf2(7.06f), AndroidUtilities.dpf2(9.66f));
            path.lineTo(h - AndroidUtilities.dpf2(3.95f), AndroidUtilities.dpf2(9.66f));
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
            canvas.save();
            canvas.translate(getBounds().left, getBounds().top);
            canvas.drawPath(path, paint);
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
