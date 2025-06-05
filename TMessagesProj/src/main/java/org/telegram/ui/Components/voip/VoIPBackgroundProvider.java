package org.telegram.ui.Components.voip;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.LinearInterpolator;

import org.telegram.ui.Components.BitmapShaderTools;

import java.util.ArrayList;
import java.util.List;

public class VoIPBackgroundProvider {

    public static final float DARK_LIGHT_PERCENT = 0.14f;
    public static final int DARK_LIGHT_DEFAULT_ALPHA = (int) (255 * DARK_LIGHT_PERCENT);
    public static final int REVEAL_SCALE_FACTOR = 4;

    private final BitmapShaderTools lightShaderTools = new BitmapShaderTools(80, 80);
    private final BitmapShaderTools darkShaderTools = new BitmapShaderTools(80, 80);
    private BitmapShaderTools revealShaderTools;
    private BitmapShaderTools revealDarkShaderTools;
    private boolean isReveal;
    private int totalWidth = 0;
    private int totalHeight = 0;
    private int degree;
    private boolean hasVideo;
    private final Paint whiteVideoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint darkVideoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint darkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<View> views = new ArrayList<>();
    public final float scale = 1.12f;

    public VoIPBackgroundProvider() {
        darkShaderTools.setBounds(0, 0, 80, 80);
        lightShaderTools.setBounds(0, 0, 80, 80);
        whiteVideoPaint.setColor(Color.WHITE);
        whiteVideoPaint.setAlpha(DARK_LIGHT_DEFAULT_ALPHA);
        darkVideoPaint.setColor(Color.BLACK);
        darkVideoPaint.setAlpha((int) (255 * 0.4f));
        darkPaint.setColor(Color.BLACK);
        darkPaint.setAlpha(DARK_LIGHT_DEFAULT_ALPHA);
        darkShaderTools.paint.setAlpha(180);
    }

    public void invalidateViews() {
        for (View view : views) {
            view.invalidate();
        }
    }

    public void attach(View view) {
        views.add(view);
    }

    public void detach(View view) {
        views.remove(view);
    }

    public void setHasVideo(boolean hasVideo) {
        if (this.hasVideo && !hasVideo) {
            ValueAnimator valueAnimator = ValueAnimator.ofFloat(1f, 0f);
            valueAnimator.addUpdateListener(animation -> {
                float val = (float) animation.getAnimatedValue();
                darkPaint.setAlpha((int) (DARK_LIGHT_DEFAULT_ALPHA * val));
                darkVideoPaint.setAlpha((int) ((int) (255 * 0.4f) * val));
                whiteVideoPaint.setAlpha((int) (DARK_LIGHT_DEFAULT_ALPHA * val));
                invalidateViews();
            });
            valueAnimator.setInterpolator(new LinearInterpolator());
            valueAnimator.setDuration(80);
            valueAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    VoIPBackgroundProvider.this.hasVideo = false;
                    darkPaint.setAlpha(DARK_LIGHT_DEFAULT_ALPHA);
                    darkVideoPaint.setAlpha((int) (255 * 0.4f));
                    whiteVideoPaint.setAlpha(DARK_LIGHT_DEFAULT_ALPHA);
                    invalidateViews();
                }
            });
            valueAnimator.start();
            ValueAnimator valueAnimator2 = ValueAnimator.ofFloat(0f, 1f);
            valueAnimator2.addUpdateListener(animation -> {
                float val = (float) animation.getAnimatedValue();
                darkShaderTools.paint.setAlpha((int) (180 * val));
                lightShaderTools.paint.setAlpha((int) (255 * val));
                invalidateViews();
            });
            valueAnimator2.setInterpolator(new LinearInterpolator());
            valueAnimator2.setStartDelay(80);
            valueAnimator2.setDuration(80);
            valueAnimator2.start();
        } else {
            this.hasVideo = hasVideo;
        }
        invalidateViews();
    }

    public Canvas getLightCanvas() {
        return lightShaderTools.getCanvas();
    }

    public Canvas getRevealCanvas() {
        return revealShaderTools.getCanvas();
    }

    public Canvas getRevealDrakCanvas() {
        return revealDarkShaderTools.getCanvas();
    }

    public Canvas getDarkCanvas() {
        return darkShaderTools.getCanvas();
    }

    public int getTotalWidth() {
        return totalWidth;
    }

    public void setTotalSize(int totalWidth, int totalHeight) {
        this.totalWidth = totalWidth;
        this.totalHeight = totalHeight;
        this.revealShaderTools = new BitmapShaderTools(totalWidth / REVEAL_SCALE_FACTOR, totalHeight / REVEAL_SCALE_FACTOR);
        this.revealDarkShaderTools = new BitmapShaderTools(totalWidth / REVEAL_SCALE_FACTOR, totalHeight / REVEAL_SCALE_FACTOR);
        this.revealDarkShaderTools.paint.setAlpha(180);
    }

    public int getTotalHeight() {
        return totalHeight;
    }

    public void setTotalHeight(int totalHeight) {
        this.totalHeight = totalHeight;
    }

    public int getDegree() {
        return degree;
    }

    public void setDegree(int degree) {
        this.degree = degree;
        invalidateViews();
    }

    public void setLightTranslation(float x, float y) {
        float finalSize = (float) totalHeight * scale;
        float s = finalSize / (float) lightShaderTools.getBitmap().getHeight();
        float dx = (totalHeight * scale - totalWidth) / 2f;
        float dy = (totalHeight * scale - totalHeight) / 2f;
        lightShaderTools.setMatrix(-x - dx, -y - dy, s, degree);
        revealShaderTools.setBounds(-x, -y, totalWidth - x, totalHeight - y);
    }

    public void setDarkTranslation(float x, float y) {
        float finalSize = (float) totalHeight * scale;
        float s = finalSize / (float) darkShaderTools.getBitmap().getHeight();
        float dx = (finalSize - totalWidth) / 2f;
        float dy = (finalSize - totalHeight) / 2f;
        darkShaderTools.setMatrix(-x - dx, -y - dy, s, degree);
        revealDarkShaderTools.setBounds(-x, -y, totalWidth - x, totalHeight - y);
    }

    public boolean isReveal() {
        return isReveal;
    }

    public void setReveal(boolean clipping) {
        isReveal = clipping;
    }

    public Paint getRevealPaint() {
        return revealShaderTools.paint;
    }

    public Paint getRevealDarkPaint() {
        return revealDarkShaderTools.paint;
    }

    public Paint getLightPaint() {
        if (hasVideo) {
            return whiteVideoPaint;
        }
        return lightShaderTools.paint;
    }

    public Paint getDarkPaint() {
        if (hasVideo) {
            return darkVideoPaint;
        }
        return darkShaderTools.paint;
    }

    public Paint getDarkPaint(boolean ignoreShader) {
        if (ignoreShader) {
            return darkPaint;
        }
        return getDarkPaint();
    }
}
