/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.Locale;

public class RadialProgress2 {

    private RectF progressRect = new RectF();
    private View parent;

    private boolean previousCheckDrawable;

    private boolean drawMiniIcon;
    private int progressColor = 0xffffffff;
    private Paint miniProgressBackgroundPaint;

    private Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint circleMiniPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private MediaActionDrawable mediaActionDrawable;
    private MediaActionDrawable miniMediaActionDrawable;
    private float miniIconScale = 1.0f;
    private int circleColor;
    private int circlePressedColor;
    private int iconColor;
    private int iconPressedColor;
    private String circleColorKey;
    private String circleCrossfadeColorKey;
    private float circleCrossfadeColorProgress;
    private float circleCheckProgress = 1.0f;
    private String circlePressedColorKey;
    private String iconColorKey;
    private String iconPressedColorKey;
    private ImageReceiver overlayImageView;
    private int circleRadius;
    private boolean isPressed;
    private boolean isPressedMini;
    public float overrideCircleAlpha = 1f;

    private int backgroundStroke;

    private boolean drawBackground = true;

    private Bitmap miniDrawBitmap;
    private Canvas miniDrawCanvas;

    private float overrideAlpha = 1.0f;
    private Theme.ResourcesProvider resourcesProvider;
    private int maxIconSize;
    private float overlayImageAlpha = 1f;

    public RadialProgress2(View parentView) {
        this(parentView, null);
    }

    public RadialProgress2(View parentView, Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
        miniProgressBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        parent = parentView;

        overlayImageView = new ImageReceiver(parentView);
        overlayImageView.setInvalidateAll(true);

        mediaActionDrawable = new MediaActionDrawable();
        mediaActionDrawable.setDelegate(parentView::invalidate);

        miniMediaActionDrawable = new MediaActionDrawable();
        miniMediaActionDrawable.setDelegate(parentView::invalidate);
        miniMediaActionDrawable.setMini(true);
        miniMediaActionDrawable.setIcon(MediaActionDrawable.ICON_NONE, false);

        circleRadius = AndroidUtilities.dp(22);
        overlayImageView.setRoundRadius(circleRadius);

        overlayPaint.setColor(0x64000000);
    }

    public void setResourcesProvider(Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
    }

    public void setAsMini() {
        mediaActionDrawable.setMini(true);
    }

    public void setCircleRadius(int value) {
        circleRadius = value;
        overlayImageView.setRoundRadius(circleRadius);
    }

    public void setBackgroundStroke(int value) {
        backgroundStroke = value;
        circlePaint.setStrokeWidth(value);
        circlePaint.setStyle(Paint.Style.STROKE);
        invalidateParent();
    }

    public int getRadius() {
        return circleRadius;
    }

    public void setBackgroundDrawable(Theme.MessageDrawable drawable) {
        mediaActionDrawable.setBackgroundDrawable(drawable);
        miniMediaActionDrawable.setBackgroundDrawable(drawable);
    }

    public void setBackgroundGradientDrawable(LinearGradient drawable) {
        mediaActionDrawable.setBackgroundGradientDrawable(drawable);
        miniMediaActionDrawable.setBackgroundGradientDrawable(drawable);
    }

    public void setImageOverlay(TLRPC.PhotoSize image, TLRPC.Document document, Object parentObject) {
        overlayImageView.setImage(ImageLocation.getForDocument(image, document), String.format(Locale.US, "%d_%d", circleRadius * 2, circleRadius * 2), null, null, parentObject, 1);
    }

    public void setImageOverlay(String url) {
        overlayImageView.setImage(url, url != null ? String.format(Locale.US, "%d_%d", circleRadius * 2, circleRadius * 2) : null, null, null, -1);
    }

    public void onAttachedToWindow() {
        overlayImageView.onAttachedToWindow();
    }

    public void onDetachedFromWindow() {
        overlayImageView.onDetachedFromWindow();
    }

    public void setColors(int circle, int circlePressed, int icon, int iconPressed) {
        circleColor = circle;
        circlePressedColor = circlePressed;
        iconColor = icon;
        iconPressedColor = iconPressed;
        circleColorKey = null;
        circlePressedColorKey = null;
        iconColorKey = null;
        iconPressedColorKey = null;
    }

    public void setColors(String circle, String circlePressed, String icon, String iconPressed) {
        circleColorKey = circle;
        circlePressedColorKey = circlePressed;
        iconColorKey = icon;
        iconPressedColorKey = iconPressed;
    }

    public void setCircleCrossfadeColor(String color, float progress, float checkProgress) {
        circleCrossfadeColorKey = color;
        circleCrossfadeColorProgress = progress;
        circleCheckProgress = checkProgress;
        miniIconScale = 1.0f;
        if (color != null) {
            initMiniIcons();
        }
    }

    public void setDrawBackground(boolean value) {
        drawBackground = value;
    }

    public void setProgressRect(int left, int top, int right, int bottom) {
        progressRect.set(left, top, right, bottom);
    }

    public void setProgressRect(float left, float top, float right, float bottom) {
        progressRect.set(left, top, right, bottom);
    }

    public RectF getProgressRect() {
        return progressRect;
    }

    public void setProgressColor(int color) {
        progressColor = color;
    }

    public void setMiniProgressBackgroundColor(int color) {
        miniProgressBackgroundPaint.setColor(color);
    }

    public void setProgress(float value, boolean animated) {
        if (drawMiniIcon) {
            miniMediaActionDrawable.setProgress(value, animated);
        } else {
            mediaActionDrawable.setProgress(value, animated);
        }
    }

    public float getProgress() {
        return drawMiniIcon ? miniMediaActionDrawable.getProgress() : mediaActionDrawable.getProgress();
    }

    private void invalidateParent() {
        int offset = AndroidUtilities.dp(2);
        parent.invalidate((int) progressRect.left - offset, (int) progressRect.top - offset, (int) progressRect.right + offset * 2, (int) progressRect.bottom + offset * 2);
    }

    public int getIcon() {
        return mediaActionDrawable.getCurrentIcon();
    }

    public int getMiniIcon() {
        return miniMediaActionDrawable.getCurrentIcon();
    }

    public void setIcon(int icon, boolean ifSame, boolean animated) {
        if (ifSame && icon == mediaActionDrawable.getCurrentIcon()) {
            return;
        }
        mediaActionDrawable.setIcon(icon, animated);
        if (!animated) {
            parent.invalidate();
        } else {
            invalidateParent();
        }
    }

    public void setMiniIconScale(float scale) {
        miniIconScale = scale;
    }

    public void setMiniIcon(int icon, boolean ifSame, boolean animated) {
        if (icon != MediaActionDrawable.ICON_DOWNLOAD && icon != MediaActionDrawable.ICON_CANCEL && icon != MediaActionDrawable.ICON_NONE) {
            return;
        }
        if (ifSame && icon == miniMediaActionDrawable.getCurrentIcon()) {
            return;
        }
        miniMediaActionDrawable.setIcon(icon, animated);
        drawMiniIcon = icon != MediaActionDrawable.ICON_NONE || miniMediaActionDrawable.getTransitionProgress() < 1.0f;
        if (drawMiniIcon) {
            initMiniIcons();
        }
        if (!animated) {
            parent.invalidate();
        } else {
            invalidateParent();
        }
    }

    public void initMiniIcons() {
        if (miniDrawBitmap == null) {
            try {
                miniDrawBitmap = Bitmap.createBitmap(AndroidUtilities.dp(48), AndroidUtilities.dp(48), Bitmap.Config.ARGB_8888);
                miniDrawCanvas = new Canvas(miniDrawBitmap);
            } catch (Throwable ignore) {

            }
        }
    }

    public boolean swapIcon(int icon) {
        if (mediaActionDrawable.setIcon(icon, false)) {
            return true;
        }
        return false;
    }

    public void setPressed(boolean value, boolean mini) {
        if (mini) {
            isPressedMini = value;
        } else {
            isPressed = value;
        }
        invalidateParent();
    }

    public void setOverrideAlpha(float alpha) {
        overrideAlpha = alpha;
    }

    public float getOverrideAlpha() {
        return overrideAlpha;
    }

    public void draw(Canvas canvas) {
        if (mediaActionDrawable.getCurrentIcon() == MediaActionDrawable.ICON_NONE && mediaActionDrawable.getTransitionProgress() >= 1.0f || progressRect.isEmpty()) {
            return;
        }

        int currentIcon = mediaActionDrawable.getCurrentIcon();
        int prevIcon = mediaActionDrawable.getPreviousIcon();

        float wholeAlpha;
        if (backgroundStroke != 0) {
            if (currentIcon == MediaActionDrawable.ICON_CANCEL) {
                wholeAlpha = 1.0f - mediaActionDrawable.getTransitionProgress();
            } else if (prevIcon == MediaActionDrawable.ICON_CANCEL) {
                wholeAlpha = mediaActionDrawable.getTransitionProgress();
            } else {
                wholeAlpha = 1.0f;
            }
        } else if ((currentIcon == MediaActionDrawable.ICON_CANCEL || currentIcon == MediaActionDrawable.ICON_CHECK || currentIcon == MediaActionDrawable.ICON_EMPTY || currentIcon == MediaActionDrawable.ICON_GIF || currentIcon == MediaActionDrawable.ICON_PLAY) && prevIcon == MediaActionDrawable.ICON_NONE) {
            wholeAlpha = mediaActionDrawable.getTransitionProgress();
        } else {
            wholeAlpha = currentIcon != MediaActionDrawable.ICON_NONE ? 1.0f : 1.0f - mediaActionDrawable.getTransitionProgress();
        }

        if (isPressedMini && circleCrossfadeColorKey == null) {
            if (iconPressedColorKey != null) {
                miniMediaActionDrawable.setColor(getThemedColor(iconPressedColorKey));
            } else {
                miniMediaActionDrawable.setColor(iconPressedColor);
            }
            if (circlePressedColorKey != null) {
                circleMiniPaint.setColor(getThemedColor(circlePressedColorKey));
            } else {
                circleMiniPaint.setColor(circlePressedColor);
            }
        } else {
            if (iconColorKey != null) {
                miniMediaActionDrawable.setColor(getThemedColor(iconColorKey));
            } else {
                miniMediaActionDrawable.setColor(iconColor);
            }
            if (circleColorKey != null) {
                if (circleCrossfadeColorKey != null) {
                    circleMiniPaint.setColor(AndroidUtilities.getOffsetColor(getThemedColor(circleColorKey), getThemedColor(circleCrossfadeColorKey), circleCrossfadeColorProgress, circleCheckProgress));
                } else {
                    circleMiniPaint.setColor(getThemedColor(circleColorKey));
                }
            } else {
                circleMiniPaint.setColor(circleColor);
            }
        }

        int color;
        if (isPressed) {
            if (iconPressedColorKey != null) {
                mediaActionDrawable.setColor(color = getThemedColor(iconPressedColorKey));
                mediaActionDrawable.setBackColor(getThemedColor(circlePressedColorKey));
            } else {
                mediaActionDrawable.setColor(color = iconPressedColor);
                mediaActionDrawable.setBackColor(circlePressedColor);
            }
            if (circlePressedColorKey != null) {
                circlePaint.setColor(getThemedColor(circlePressedColorKey));
            } else {
                circlePaint.setColor(circlePressedColor);
            }
        } else {
            if (iconColorKey != null) {
                mediaActionDrawable.setColor(color = getThemedColor(iconColorKey));
                mediaActionDrawable.setBackColor(getThemedColor(circleColorKey));
            } else {
                mediaActionDrawable.setColor(color = iconColor);
                mediaActionDrawable.setBackColor(circleColor);
            }
            if (circleColorKey != null) {
                circlePaint.setColor(getThemedColor(circleColorKey));
            } else {
                circlePaint.setColor(circleColor);
            }
        }
        if ((drawMiniIcon || circleCrossfadeColorKey != null) && miniDrawCanvas != null) {
            miniDrawBitmap.eraseColor(0);
        }

        int originalAlpha = circlePaint.getAlpha();
        circlePaint.setAlpha((int) (originalAlpha * wholeAlpha * overrideAlpha * overrideCircleAlpha));
        originalAlpha = circleMiniPaint.getAlpha();
        circleMiniPaint.setAlpha((int) (originalAlpha * wholeAlpha * overrideAlpha));

        boolean drawCircle = true;
        float scale = 1f;
        int centerX;
        int centerY;
        if ((drawMiniIcon || circleCrossfadeColorKey != null) && miniDrawCanvas != null) {
            centerX = (int) Math.ceil(progressRect.width() / 2);
            centerY = (int) Math.ceil(progressRect.height() / 2);
        } else {
            centerX = (int) progressRect.centerX();
            centerY = (int) progressRect.centerY();
        }

        if (overlayImageView.hasBitmapImage()) {
            float alpha = overlayImageView.getCurrentAlpha();
            overlayPaint.setAlpha((int) (0x64 * alpha * wholeAlpha * overrideAlpha));
            int c;
            if (alpha >= 1.0f) {
                drawCircle = false;
                c = 0xffffffff;
            } else {
                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);
                int a = Color.alpha(color);

                int rD = (int) ((0xff - r) * alpha);
                int gD = (int) ((0xff - g) * alpha);
                int bD = (int) ((0xff - b) * alpha);
                int aD = (int) ((0xff - a) * alpha);
                c = Color.argb(a + aD, r + rD, g + gD, b + bD);
            }
            mediaActionDrawable.setColor(c);

            overlayImageView.setImageCoords(centerX - circleRadius, centerY - circleRadius, circleRadius * 2, circleRadius * 2);
        }

        int restore = Integer.MIN_VALUE;
        if (miniDrawCanvas != null && circleCrossfadeColorKey != null && circleCheckProgress != 1.0f) {
            restore = miniDrawCanvas.save();
            float scaleMini = 1.0f - 0.1f * (1.0f - circleCheckProgress);
            miniDrawCanvas.scale(scaleMini, scaleMini, centerX, centerY);
        }
        if (drawCircle && drawBackground) {
            if ((drawMiniIcon || circleCrossfadeColorKey != null) && miniDrawCanvas != null) {
                miniDrawCanvas.drawCircle(centerX, centerY, circleRadius, circlePaint);
            } else {
                if (currentIcon != MediaActionDrawable.ICON_NONE || wholeAlpha != 0) {
                    if (backgroundStroke != 0) {
                        canvas.drawCircle(centerX, centerY, (circleRadius - AndroidUtilities.dp(3.5f)), circlePaint);
                    } else {
                        canvas.drawCircle(centerX, centerY, circleRadius, circlePaint);
                    }
                }
            }
        }
        if (overlayImageView.hasBitmapImage()) {
            overlayImageView.setAlpha(wholeAlpha * overrideAlpha * overlayImageAlpha);

            if ((drawMiniIcon || circleCrossfadeColorKey != null) && miniDrawCanvas != null) {
                overlayImageView.draw(miniDrawCanvas);
                miniDrawCanvas.drawCircle(centerX, centerY, circleRadius, overlayPaint);
            } else {
                overlayImageView.draw(canvas);
                canvas.drawCircle(centerX, centerY, circleRadius, overlayPaint);
            }
        }
        int iconSize = circleRadius;
        if (maxIconSize > 0 && iconSize > maxIconSize) {
            iconSize = maxIconSize;
        }
        mediaActionDrawable.setBounds(centerX - iconSize, centerY - iconSize, centerX + iconSize, centerY + iconSize);
        mediaActionDrawable.setHasOverlayImage(overlayImageView.hasBitmapImage());
        if ((drawMiniIcon || circleCrossfadeColorKey != null)) {
            if (miniDrawCanvas != null) {
                mediaActionDrawable.draw(miniDrawCanvas);
            } else {
                mediaActionDrawable.draw(canvas);
            }
        } else {
            mediaActionDrawable.setOverrideAlpha(overrideAlpha);
            mediaActionDrawable.draw(canvas);
        }
        if (restore != Integer.MIN_VALUE && miniDrawCanvas != null) {
            miniDrawCanvas.restoreToCount(restore);
        }

        if ((drawMiniIcon || circleCrossfadeColorKey != null)) {
            int offset;
            int size;
            float cx;
            float cy;
            if (Math.abs(progressRect.width() - AndroidUtilities.dp(44)) < AndroidUtilities.density) {
                offset = 0;
                size = 20;
                cx = progressRect.centerX() + AndroidUtilities.dp(16 + offset);
                cy = progressRect.centerY() + AndroidUtilities.dp(16 + offset);
            } else {
                offset = 2;
                size = 22;
                cx = progressRect.centerX() + AndroidUtilities.dp(18);
                cy = progressRect.centerY() + AndroidUtilities.dp(18);
            }
            int halfSize = size / 2;

            float alpha;
            if (drawMiniIcon) {
                alpha = miniMediaActionDrawable.getCurrentIcon() != MediaActionDrawable.ICON_NONE ? 1.0f : 1.0f - miniMediaActionDrawable.getTransitionProgress();
                if (alpha == 0.0f) {
                    drawMiniIcon = false;
                }
            } else {
                alpha = 1.0f;
            }

            if (miniDrawCanvas != null) {
                miniDrawCanvas.drawCircle(AndroidUtilities.dp(18 + size + offset), AndroidUtilities.dp(18 + size + offset), AndroidUtilities.dp(halfSize + 1) * alpha * miniIconScale, Theme.checkboxSquare_eraserPaint);
            } else {
                miniProgressBackgroundPaint.setColor(progressColor);
                canvas.drawCircle(cx, cy, AndroidUtilities.dp(12), miniProgressBackgroundPaint);
            }

            if (miniDrawCanvas != null) {
                canvas.drawBitmap(miniDrawBitmap, (int) progressRect.left, (int) progressRect.top, null);
            }

            restore = Integer.MIN_VALUE;
            if (miniIconScale < 1.0f) {
                restore = canvas.save();
                canvas.scale(miniIconScale, miniIconScale, cx, cy);
            }

            canvas.drawCircle(cx, cy, AndroidUtilities.dp(halfSize) * alpha + AndroidUtilities.dp(1) * (1.0f - circleCheckProgress), circleMiniPaint);
            if (drawMiniIcon) {
                miniMediaActionDrawable.setBounds((int) (cx - AndroidUtilities.dp(halfSize) * alpha), (int) (cy - AndroidUtilities.dp(halfSize) * alpha), (int) (cx + AndroidUtilities.dp(halfSize) * alpha), (int) (cy + AndroidUtilities.dp(halfSize) * alpha));
                miniMediaActionDrawable.draw(canvas);
            }
            if (restore != Integer.MIN_VALUE) {
                canvas.restoreToCount(restore);
            }
        }
    }

    public String getCircleColorKey() {
        return circleColorKey;
    }

    private int getThemedColor(String key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }

    public void setMaxIconSize(int maxSize) {
        this.maxIconSize = maxSize;
    }

    public void setOverlayImageAlpha(float overlayImageAlpha) {
        this.overlayImageAlpha = overlayImageAlpha;
    }

    public float getTransitionProgress() {
        return drawMiniIcon ? miniMediaActionDrawable.getTransitionProgress() : mediaActionDrawable.getTransitionProgress();
    }
}
