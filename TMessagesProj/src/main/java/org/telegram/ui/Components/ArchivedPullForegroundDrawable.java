package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.animation.LinearInterpolator;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;


public class ArchivedPullForegroundDrawable {

    public final static float SNAP_HEIGHT = 0.85f;


    private Paint paintSecondary = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint paintAccent = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint backgroundPaint = new Paint();
    private RectF rectF = new RectF();
    private Paint tooltipTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private Drawable arrowDrawable;


    private float textSwappingProgress = 1f;
    private boolean animateToEndText = false;
    private ValueAnimator textSwipingAnimator;

    private float textInProgress = 1f;
    private boolean animateToTextIn = false;
    private ValueAnimator textIntAnimator;

    private AnimatorSet outAnimator;
    public float outProgress = 0f;
    private float bounceProgress = 0f;
    private boolean animateOut = false;
    private boolean bounceIn = false;


    private int startPadding = AndroidUtilities.dp(27);
    private int smallMargin = AndroidUtilities.dp(8);
    private int radius = AndroidUtilities.dp(9);
    private int diameter = AndroidUtilities.dp(18);

    private DialogCell parent;

    public float pullProgress;

    public float outCy;
    public float outCx;
    public int outRadius;
    public AvatarDrawable outDrawable;

    private String pullTooltip;
    private String releaseTooltip;
    private boolean willDraw;

    private boolean isOut = false;

    private ValueAnimator.AnimatorUpdateListener textSwappingUpdateListener = animation -> {
        textSwappingProgress = (float) animation.getAnimatedValue();
        if (parent != null) parent.invalidate();
    };

    private ValueAnimator.AnimatorUpdateListener textInUpdateListener = animation -> {
        textInProgress = (float) animation.getAnimatedValue();
        if (parent != null) parent.invalidate();
    };

    public ArchivedPullForegroundDrawable() {
        tooltipTextPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        tooltipTextPaint.setTextAlign(Paint.Align.CENTER);
        tooltipTextPaint.setTextSize(AndroidUtilities.dp(16));
    }

    public void setView(DialogCell view) {
        parent = view;

        int primaryColor = Color.WHITE;
        int backgroundColor = Theme.getColor(Theme.key_avatar_backgroundArchivedHidden);

        tooltipTextPaint.setColor(primaryColor);
        paintAccent.setColor(primaryColor);
        paintSecondary.setColor(ColorUtils.setAlphaComponent(primaryColor, 100));
        backgroundPaint.setColor(backgroundColor);
        arrowDrawable = ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.archive_swipe_arrow).mutate();
        arrowDrawable.setColorFilter(new PorterDuffColorFilter(backgroundColor, PorterDuff.Mode.MULTIPLY));

        pullTooltip = LocaleController.getString("SwipeForArchive", R.string.SwipeForArchive);
        releaseTooltip = LocaleController.getString("ReleaseForArchive", R.string.ReleaseForArchive);
    }


    public void draw(Canvas canvas) {
        if (!willDraw || isOut || parent == null) return;

        int visibleHeight = (int) (parent.getHeight() * pullProgress);
        int invisibleHeight = parent.getHeight() - visibleHeight;

        float bounceP = bounceIn ? (0.07f * bounceProgress) - 0.05f : 0.02f * bounceProgress;

        //float bounceP = (0.1f * bounceProgress) - 0.1f;
        updateTextProgress(pullProgress);

        float outProgressHalf = outProgress * 2f;
        if (outProgressHalf > 1f) outProgressHalf = 1f;

        float cX = outCx;
        float cY = outCy;

        canvas.save();
        if (outProgress == 0f) {
            canvas.drawPaint(backgroundPaint);
        } else {


            float outBackgroundRadius = outRadius + (parent.getWidth() - outRadius) * (1f - outProgress) + (outRadius * bounceP);
            canvas.drawCircle(cX, cY, outBackgroundRadius, backgroundPaint);

            //clip rect work faster then clip path, and in this case users see no difference
            canvas.clipRect(
                    cX - outBackgroundRadius, cY - outBackgroundRadius,
                    cX + outBackgroundRadius, cY + outBackgroundRadius
            );
        }

        if (visibleHeight > diameter + smallMargin * 2) {
            paintSecondary.setAlpha((int) ((1f - outProgressHalf) * 0.4f * 255));
            rectF.set(startPadding, parent.getHeight() - visibleHeight + smallMargin, startPadding + diameter, parent.getHeight() - smallMargin);
            canvas.drawRoundRect(rectF, radius, radius, paintSecondary);
        }

        if (outProgress == 0f) {
            int x = startPadding + radius;
            int y = parent.getHeight() - smallMargin - radius;
            canvas.drawCircle(x, y, radius, paintAccent);

            int ih = arrowDrawable.getIntrinsicHeight();
            int iw = arrowDrawable.getIntrinsicWidth();

            arrowDrawable.setBounds(
                    x - (iw >> 1), y - (ih >> 1),
                    x + (iw >> 1), y + (ih >> 1)
            );

            float rotateProgress = (float) (visibleHeight - (diameter + smallMargin * 2)) / (float) (parent.getHeight() - (diameter + smallMargin * 2));
            if (rotateProgress < 0) rotateProgress = 0f;
            rotateProgress = 1f - rotateProgress;
            canvas.save();
            canvas.rotate(180 * rotateProgress, x, y);
            arrowDrawable.draw(canvas);
            canvas.restore();
        }


        textIn(visibleHeight >= diameter + smallMargin * 2 - AndroidUtilities.dp(4));


        float textY = invisibleHeight + (visibleHeight >> 1) + AndroidUtilities.dp(6);
        tooltipTextPaint.setAlpha((int) (255 * textSwappingProgress * textInProgress));
        canvas.drawText(pullTooltip, parent.getWidth() / 2f - AndroidUtilities.dp(2),
                textY + AndroidUtilities.dp(16) * (1f - textSwappingProgress), tooltipTextPaint);

        tooltipTextPaint.setAlpha((int) (255 * (1f - textSwappingProgress) * textInProgress));
        canvas.drawText(releaseTooltip, parent.getWidth() / 2f - AndroidUtilities.dp(2),
                textY - AndroidUtilities.dp(16) * (textSwappingProgress), tooltipTextPaint);

        canvas.restore();


        if (outProgress > 0) {
            canvas.save();
            int ih = Theme.dialogs_archiveAvatarDrawable.getIntrinsicHeight();
            int iw = Theme.dialogs_archiveAvatarDrawable.getIntrinsicWidth();

            int startCx = startPadding + radius;
            int startCy = parent.getHeight() - smallMargin - radius;

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
            if (textSwipingAnimator != null) textSwipingAnimator.cancel();
            textSwipingAnimator = ValueAnimator.ofFloat(textSwappingProgress, endText ? 0f : 1f);
            textSwipingAnimator.addUpdateListener(textSwappingUpdateListener);
            textSwipingAnimator.setInterpolator(new LinearInterpolator());
            textSwipingAnimator.setDuration(150);
            textSwipingAnimator.start();
        }
    }

    private void textIn(boolean in) {
        if (animateToTextIn != in) {
            animateToTextIn = in;
            if (textIntAnimator != null) textIntAnimator.cancel();
            textIntAnimator = ValueAnimator.ofFloat(textInProgress, in ? 1f : 0f);
            textIntAnimator.addUpdateListener(textInUpdateListener);
            textIntAnimator.setInterpolator(new LinearInterpolator());
            textIntAnimator.setDuration(150);
            textIntAnimator.start();
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
        ValueAnimator out = ValueAnimator.ofFloat(0f, 1f);
        out.addUpdateListener(animation -> {
            outProgress = (float) animation.getAnimatedValue();
            if (parent != null) parent.invalidate();
        });

        out.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        out.setDuration(250);

        ValueAnimator bounceIn =
                ValueAnimator.ofFloat(0f, 1f);
        bounceIn.addUpdateListener(animation -> {
            bounceProgress = (float) animation.getAnimatedValue();
            this.bounceIn = true;
            if (parent != null) parent.invalidate();
        });

        bounceIn.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
        bounceIn.setDuration(150);

        ValueAnimator bounceOut =
                ValueAnimator.ofFloat(1f, 0f);
        bounceOut.addUpdateListener(animation -> {
            bounceProgress = (float) animation.getAnimatedValue();
            this.bounceIn = false;
            if (parent != null) parent.invalidate();
        });

        bounceOut.setInterpolator(CubicBezierInterpolator.EASE_BOTH);
        bounceOut.setDuration(135);

        outAnimator = new AnimatorSet();
        outAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (textSwipingAnimator != null) textSwipingAnimator.cancel();
                textSwappingProgress = 1f;
                animateToEndText = false;
                animateToTextIn = false;
                textInProgress = 0f;
                isOut = true;

            }
        });


        AnimatorSet bounce = new AnimatorSet();
        bounce.playSequentially(bounceIn, bounceOut);
        bounce.setStartDelay(200);
        // bounceIn.setStartDelay(200);
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
        parent = null;
        if (textSwipingAnimator != null) textSwipingAnimator.cancel();
        if (outAnimator != null) {
            outAnimator.removeAllListeners();
            outAnimator.cancel();
        }
    }

    public boolean isDraw() {
        return !(!willDraw || isOut || parent == null);
    }


    public void setWillDraw(boolean b) {
        willDraw = b;
    }
}
