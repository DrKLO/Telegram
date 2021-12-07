/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.ActionBar;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.animation.DecelerateInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.MediaActionDrawable;

public class MenuDrawable extends Drawable {

    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint backPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean reverseAngle;
    private long lastFrameTime;
    private boolean animationInProgress;
    private float finalRotation;
    private float currentRotation;
    private int currentAnimationTime;
    private boolean rotateToBack = true;
    private DecelerateInterpolator interpolator = new DecelerateInterpolator();
    private int iconColor;
    private int backColor;

    private RectF rect = new RectF();

    private int type;
    private int previousType;
    private float typeAnimationProgress;

    private float downloadRadOffset;
    private float downloadProgress;
    private float animatedDownloadProgress;
    private float downloadProgressAnimationStart;
    private float downloadProgressTime;
    private boolean miniIcon;

    public static int TYPE_DEFAULT = 0;
    public static int TYPE_UDPATE_AVAILABLE = 1;
    public static int TYPE_UDPATE_DOWNLOADING = 2;

    private int alpha = 255;

    public MenuDrawable() {
        this(TYPE_DEFAULT);
    }

    public MenuDrawable(int type) {
        super();
        paint.setStrokeWidth(AndroidUtilities.dp(2));
        backPaint.setStrokeWidth(AndroidUtilities.density * 1.66f);
        backPaint.setStrokeCap(Paint.Cap.ROUND);
        backPaint.setStyle(Paint.Style.STROKE);
        previousType = TYPE_DEFAULT;
        this.type = type;
        typeAnimationProgress = 1.0f;
    }

    public void setRotateToBack(boolean value) {
        rotateToBack = value;
    }

    public float getCurrentRotation() {
        return currentRotation;
    }

    public void setRotation(float rotation, boolean animated) {
        lastFrameTime = 0;
        if (currentRotation == 1) {
            reverseAngle = true;
        } else if (currentRotation == 0) {
            reverseAngle = false;
        }
        lastFrameTime = 0;
        if (animated) {
            if (currentRotation < rotation) {
                currentAnimationTime = (int) (currentRotation * 200);
            } else {
                currentAnimationTime = (int) ((1.0f - currentRotation) * 200);
            }
            lastFrameTime = SystemClock.elapsedRealtime();
            finalRotation = rotation;
        } else {
            finalRotation = currentRotation = rotation;
        }
        invalidateSelf();
    }

    public void setType(int value, boolean animated) {
        if (type == value) {
            return;
        }
        previousType = type;
        type = value;
        if (animated) {
            typeAnimationProgress = 0.0f;
        } else {
            typeAnimationProgress = 1.0f;
        }
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        long newTime = SystemClock.elapsedRealtime();
        long dt = newTime - lastFrameTime;
        if (currentRotation != finalRotation) {
            if (lastFrameTime != 0) {
                currentAnimationTime += dt;
                if (currentAnimationTime >= 200) {
                    currentRotation = finalRotation;
                } else {
                    if (currentRotation < finalRotation) {
                        currentRotation = interpolator.getInterpolation(currentAnimationTime / 200.0f) * finalRotation;
                    } else {
                        currentRotation = 1.0f - interpolator.getInterpolation(currentAnimationTime / 200.0f);
                    }
                }
            }
            invalidateSelf();
        }
        if (typeAnimationProgress < 1.0f) {
            typeAnimationProgress += dt / 200.0f;
            if (typeAnimationProgress > 1.0f) {
                typeAnimationProgress = 1.0f;
            }
            invalidateSelf();
        }
        lastFrameTime = newTime;

        canvas.save();

        canvas.translate(getIntrinsicWidth() / 2 - AndroidUtilities.dp(9), getIntrinsicHeight() / 2);
        float endYDiff;
        float endXDiff;
        float startYDiff;
        float startXDiff;
        int color1 = iconColor == 0 ? Theme.getColor(Theme.key_actionBarDefaultIcon) : iconColor;
        int backColor1 = backColor == 0 ? Theme.getColor(Theme.key_actionBarDefault) : backColor;

        float diffUp = 0;
        float diffMiddle = 0;
        if (type == TYPE_DEFAULT) {
            if (previousType != TYPE_DEFAULT) {
                diffUp = AndroidUtilities.dp(9) * (1.0f - typeAnimationProgress);
                diffMiddle = AndroidUtilities.dp(7) * (1.0f - typeAnimationProgress);
            }
        } else {
            if (previousType == TYPE_DEFAULT) {
                diffUp = AndroidUtilities.dp(9) * typeAnimationProgress * (1.0f - currentRotation);
                diffMiddle = AndroidUtilities.dp(7) * typeAnimationProgress * (1.0f - currentRotation);
            } else {
                diffUp = AndroidUtilities.dp(9) * (1.0f - currentRotation);
                diffMiddle = AndroidUtilities.dp(7) * (1.0f - currentRotation);
            }
        }
        if (rotateToBack) {
            canvas.rotate(currentRotation * (reverseAngle ? -180 : 180), AndroidUtilities.dp(9), 0);
            paint.setColor(color1);
            paint.setAlpha(alpha);
            canvas.drawLine(0, 0, AndroidUtilities.dp(18) - AndroidUtilities.dp(3.0f) * currentRotation - diffMiddle, 0, paint);
            endYDiff = AndroidUtilities.dp(5) * (1 - Math.abs(currentRotation)) - AndroidUtilities.dp(0.5f) * Math.abs(currentRotation);
            endXDiff = AndroidUtilities.dp(18) - AndroidUtilities.dp(2.5f) * Math.abs(currentRotation);
            startYDiff = AndroidUtilities.dp(5) + AndroidUtilities.dp(2.0f) * Math.abs(currentRotation);
            startXDiff = AndroidUtilities.dp(7.5f) * Math.abs(currentRotation);
        } else {
            canvas.rotate(currentRotation * (reverseAngle ? -225 : 135), AndroidUtilities.dp(9), 0);
            if (miniIcon) {
                paint.setColor(color1);
                paint.setAlpha(alpha);
                canvas.drawLine(AndroidUtilities.dpf2(2) * (1 - Math.abs(currentRotation)) + AndroidUtilities.dp(1) * currentRotation, 0, AndroidUtilities.dpf2(16) * (1f - currentRotation) + AndroidUtilities.dp(17) * currentRotation - diffMiddle, 0, paint);
                endYDiff = AndroidUtilities.dpf2(5) * (1 - Math.abs(currentRotation)) - AndroidUtilities.dpf2(0.5f) * Math.abs(currentRotation);
                endXDiff = AndroidUtilities.dpf2(16) * (1 - Math.abs(currentRotation)) + (AndroidUtilities.dpf2(9)) * Math.abs(currentRotation);
                startYDiff = AndroidUtilities.dpf2(5) + AndroidUtilities.dpf2(3.0f) * Math.abs(currentRotation);
                startXDiff = AndroidUtilities.dpf2(2) + AndroidUtilities.dpf2(7) * Math.abs(currentRotation);

            } else {
                int color2 = Theme.getColor(Theme.key_actionBarActionModeDefaultIcon);
                int backColor2 = Theme.getColor(Theme.key_actionBarActionModeDefault);
                backColor1 = AndroidUtilities.getOffsetColor(backColor1, backColor2, currentRotation, 1.0f);
                paint.setColor(AndroidUtilities.getOffsetColor(color1, color2, currentRotation, 1.0f));
                paint.setAlpha(alpha);
                canvas.drawLine(AndroidUtilities.dp(1) * currentRotation, 0, AndroidUtilities.dp(18) - AndroidUtilities.dp(1) * currentRotation - diffMiddle, 0, paint);
                endYDiff = AndroidUtilities.dp(5) * (1 - Math.abs(currentRotation)) - AndroidUtilities.dp(0.5f) * Math.abs(currentRotation);
                endXDiff = AndroidUtilities.dp(18) - AndroidUtilities.dp(9) * Math.abs(currentRotation);
                startYDiff = AndroidUtilities.dp(5) + AndroidUtilities.dp(3.0f) * Math.abs(currentRotation);
                startXDiff = AndroidUtilities.dp(9) * Math.abs(currentRotation);
            }
        }
        if (miniIcon) {
            canvas.drawLine(startXDiff, -startYDiff, endXDiff, -endYDiff, paint);
            canvas.drawLine(startXDiff, startYDiff, endXDiff, endYDiff, paint);
        } else {
            canvas.drawLine(startXDiff, -startYDiff, endXDiff - diffUp, -endYDiff, paint);
            canvas.drawLine(startXDiff, startYDiff, endXDiff, endYDiff, paint);
        }
        if (type != TYPE_DEFAULT && currentRotation != 1.0f || previousType != TYPE_DEFAULT && typeAnimationProgress != 1.0f) {
            float cx = AndroidUtilities.dp(9 + 8);
            float cy = -AndroidUtilities.dp(4.5f);
            float rad = AndroidUtilities.density * 5.5f;
            canvas.scale(1.0f - currentRotation, 1.0f - currentRotation, cx, cy);
            if (type == TYPE_DEFAULT) {
                rad *= (1.0f - typeAnimationProgress);
            }
            backPaint.setColor(backColor1);
            backPaint.setAlpha(alpha);
            canvas.drawCircle(cx, cy, rad, paint);
            if (type == TYPE_UDPATE_AVAILABLE || previousType == TYPE_UDPATE_AVAILABLE) {
                backPaint.setStrokeWidth(AndroidUtilities.density * 1.66f);
                if (previousType == TYPE_UDPATE_AVAILABLE) {
                    backPaint.setAlpha((int) (alpha * (1.0f - typeAnimationProgress)));
                } else {
                    backPaint.setAlpha(alpha);
                }
                canvas.drawLine(cx, cy - AndroidUtilities.dp(2), cx, cy, backPaint);
                canvas.drawPoint(cx, cy + AndroidUtilities.dp(2.5f), backPaint);
            }
            if (type == TYPE_UDPATE_DOWNLOADING || previousType == TYPE_UDPATE_DOWNLOADING) {
                backPaint.setStrokeWidth(AndroidUtilities.dp(2));
                if (previousType == TYPE_UDPATE_DOWNLOADING) {
                    backPaint.setAlpha((int) (alpha * (1.0f - typeAnimationProgress)));
                } else {
                    backPaint.setAlpha(alpha);
                }
                float arcRad = Math.max(4, 360 * animatedDownloadProgress);
                rect.set(cx - AndroidUtilities.dp(3), cy - AndroidUtilities.dp(3), cx + AndroidUtilities.dp(3), cy + AndroidUtilities.dp(3));
                canvas.drawArc(rect, downloadRadOffset, arcRad, false, backPaint);

                downloadRadOffset += 360 * dt / 2500.0f;
                downloadRadOffset = MediaActionDrawable.getCircleValue(downloadRadOffset);

                float progressDiff = downloadProgress - downloadProgressAnimationStart;
                if (progressDiff > 0) {
                    downloadProgressTime += dt;
                    if (downloadProgressTime >= 200.0f) {
                        animatedDownloadProgress = downloadProgress;
                        downloadProgressAnimationStart = downloadProgress;
                        downloadProgressTime = 0;
                    } else {
                        animatedDownloadProgress = downloadProgressAnimationStart + progressDiff * interpolator.getInterpolation(downloadProgressTime / 200.0f);
                    }
                }
                invalidateSelf();
            }
        }
        canvas.restore();
    }

    public void setUpdateDownloadProgress(float value, boolean animated) {
        if (!animated) {
            animatedDownloadProgress = value;
            downloadProgressAnimationStart = value;
        } else {
            if (animatedDownloadProgress > value) {
                animatedDownloadProgress = value;
            }
            downloadProgressAnimationStart = animatedDownloadProgress;
        }
        downloadProgress = value;
        downloadProgressTime = 0;
        invalidateSelf();
    }

    @Override
    public void setAlpha(int alpha) {
        if (this.alpha != alpha) {
            this.alpha = alpha;
            paint.setAlpha(alpha);
            backPaint.setAlpha(alpha);
            invalidateSelf();
        }
    }

    @Override
    public void setColorFilter(ColorFilter cf) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return AndroidUtilities.dp(24);
    }

    @Override
    public int getIntrinsicHeight() {
        return AndroidUtilities.dp(24);
    }

    public void setIconColor(int iconColor) {
        this.iconColor = iconColor;
    }

    public void setBackColor(int backColor) {
        this.backColor = backColor;
    }

    public void setRoundCap() {
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setMiniIcon(boolean miniIcon) {
        this.miniIcon = miniIcon;
    }
}
