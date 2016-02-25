/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Components;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

public class RadialProgress {

    private long lastUpdateTime = 0;
    private float radOffset = 0;
    private float currentProgress = 0;
    private float animationProgressStart = 0;
    private long currentProgressTime = 0;
    private float animatedProgressValue = 0;
    private RectF progressRect = new RectF();
    private RectF cicleRect = new RectF();
    private View parent = null;
    private float animatedAlphaValue = 1.0f;

    private boolean currentWithRound;
    private boolean previousWithRound;
    private Drawable currentDrawable;
    private Drawable previousDrawable;
    private boolean hideCurrentDrawable;
    private int progressColor = 0xffffffff;

    private static DecelerateInterpolator decelerateInterpolator = null;
    private static Paint progressPaint = null;

    private Paint progressText;
    private long docSize;
    private int docType;

    public RadialProgress(View parentView) {
        if (decelerateInterpolator == null) {
            decelerateInterpolator = new DecelerateInterpolator();
            progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            progressPaint.setStyle(Paint.Style.STROKE);
            progressPaint.setStrokeCap(Paint.Cap.ROUND);
            progressPaint.setStrokeWidth(AndroidUtilities.dp(2));
        }
        progressText = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressText.setColor(Color.BLACK);
        progressText.setTextSize(AndroidUtilities.dp(11));
        progressText.setFakeBoldText(true);
        progressText.setTextAlign(Paint.Align.CENTER);

        parent = parentView;
    }

    public void setProgressRect(int left, int top, int right, int bottom) {
        progressRect.set(left, top, right, bottom);
    }

    private void updateAnimation(boolean progress) {
        long newTime = System.currentTimeMillis();
        long dt = newTime - lastUpdateTime;
        lastUpdateTime = newTime;

        if (progress) {
            if (animatedProgressValue != 1) {
                radOffset += 360 * dt / 3000.0f;
                float progressDiff = currentProgress - animationProgressStart;
                if (progressDiff > 0) {
                    currentProgressTime += dt;
                    if (currentProgressTime >= 300) {
                        animatedProgressValue = currentProgress;
                        animationProgressStart = currentProgress;
                        currentProgressTime = 0;
                    } else {
                        animatedProgressValue = animationProgressStart + progressDiff * decelerateInterpolator.getInterpolation(currentProgressTime / 300.0f);
                    }
                }
                invalidateParent();
            }
            if (animatedProgressValue >= 1 && previousDrawable != null) {
                animatedAlphaValue -= dt / 200.0f;
                if (animatedAlphaValue <= 0) {
                    animatedAlphaValue = 0.0f;
                    previousDrawable = null;
                }
                invalidateParent();
            }
        } else {
            if (previousDrawable != null) {
                animatedAlphaValue -= dt / 200.0f;
                if (animatedAlphaValue <= 0) {
                    animatedAlphaValue = 0.0f;
                    previousDrawable = null;
                }
                invalidateParent();
            }
        }
    }

    public void setProgressColor(int color) {
        progressColor = color;
        SharedPreferences themePrefs = ApplicationLoader.applicationContext.getSharedPreferences(AndroidUtilities.THEME_PREFS, AndroidUtilities.THEME_PREFS_MODE);
        progressText.setColor(themePrefs.getInt(color == 0xff87bf78 || color == 0xff81bd72 ? "chatRTextColor" : "chatLTextColor", 0xff000000));
    }

    public void setHideCurrentDrawable(boolean value) {
        hideCurrentDrawable = value;
    }

    public void setProgress(float value, boolean animated) {
        if (!animated) {
            animatedProgressValue = value;
            animationProgressStart = value;
        } else {
            animationProgressStart = animatedProgressValue;
        }
        currentProgress = value;
        currentProgressTime = 0;
        invalidateParent();
    }
    // plus
    public void setSizeAndType(long size, int type) {
        docSize = size;
        docType = type;
    }
    //
    private void invalidateParent() {
        int offset = AndroidUtilities.dp(2);
        parent.invalidate((int)progressRect.left - offset, (int)progressRect.top - offset, (int)progressRect.right + offset * 2, (int)progressRect.bottom + offset * 2);
    }

    public void setBackground(Drawable drawable, boolean withRound, boolean animated) {
        lastUpdateTime = System.currentTimeMillis();
        if (animated && currentDrawable != drawable) {
            setProgress(1, animated);
            previousDrawable = currentDrawable;
            previousWithRound = currentWithRound;
            animatedAlphaValue = 1.0f;
        } else {
            previousDrawable = null;
            previousWithRound = false;
        }
        currentWithRound = withRound;
        currentDrawable = drawable;
        if (!animated) {
            parent.invalidate();
        } else {
            invalidateParent();
        }
    }

    public boolean swapBackground(Drawable drawable) {
        if (currentDrawable != drawable) {
            currentDrawable = drawable;
            return true;
        }
        return false;
    }

    public float getAlpha() {
        return previousDrawable != null || currentDrawable != null ? animatedAlphaValue : 0.0f;
    }

    public void draw(Canvas canvas) {
        if (previousDrawable != null) {
            previousDrawable.setAlpha((int)(255 * animatedAlphaValue));
            previousDrawable.setBounds((int)progressRect.left, (int)progressRect.top, (int)progressRect.right, (int)progressRect.bottom);
            previousDrawable.draw(canvas);
        }

        if (!hideCurrentDrawable && currentDrawable != null) {
            if (previousDrawable != null) {
                currentDrawable.setAlpha((int)(255 * (1.0f - animatedAlphaValue)));
            } else {
                currentDrawable.setAlpha(255);
            }
            currentDrawable.setBounds((int) progressRect.left, (int) progressRect.top, (int) progressRect.right, (int) progressRect.bottom);
            currentDrawable.draw(canvas);
        }

        if (currentWithRound || previousWithRound) {
            int diff = AndroidUtilities.dp(1);
            progressPaint.setColor(progressColor);
            if (previousWithRound) {
                progressPaint.setAlpha((int)(255 * animatedAlphaValue));
            } else {
                progressPaint.setAlpha(255);
            }
            cicleRect.set(progressRect.left + diff, progressRect.top + diff, progressRect.right - diff, progressRect.bottom - diff);
            canvas.drawArc(cicleRect, -90 + radOffset, Math.max(4, 360 * animatedProgressValue), false, progressPaint);
            if(currentDrawable != null && progressText != null) {
                if (currentProgress < 1.0f && docSize > 0) {
                    if(docType > 0)progressText.setColor(progressColor);
                    if(docType == 1 || docType == 3 || docType == 8){
                        ResourceLoader.mediaBackgroundDrawable.setBounds((int) progressRect.left - AndroidUtilities.dp(20), (int) progressRect.bottom + AndroidUtilities.dp(2), (int) progressRect.right + AndroidUtilities.dp(20) , (int) progressRect.bottom + AndroidUtilities.dp(18));
                        ResourceLoader.mediaBackgroundDrawable.draw(canvas);
                    }
                    String s = AndroidUtilities.formatFileSize((long) (docSize * currentProgress)) + (docType != 0 ? " | " + String.format(docSize * currentProgress < 104857000 ? "%.1f" : "%.0f", currentProgress * 100) + '%' : ""); //AndroidUtilities.formatFileSize(docSize)*/ //String.format("%.1f", currentProgress * 100) + '%'
                    canvas.drawText(s, (int) progressRect.left + (currentDrawable.getIntrinsicWidth() / 2) + AndroidUtilities.dp(1), (int) progressRect.bottom + AndroidUtilities.dp(14), progressText);
                }
            }
            updateAnimation(true);
        } else {
            updateAnimation(false);
        }
    }
}
