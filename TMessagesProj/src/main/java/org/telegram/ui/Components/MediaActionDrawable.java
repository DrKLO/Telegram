package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.animation.DecelerateInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class MediaActionDrawable extends Drawable {

    public static final int ICON_PLAY = 0;
    public static final int ICON_PAUSE = 1;
    public static final int ICON_DOWNLOAD = 2;
    public static final int ICON_CANCEL = 3;
    public static final int ICON_NONE = 4;
    public static final int ICON_FILE = 5;
    public static final int ICON_CHECK = 6;
    public static final int ICON_FIRE = 7;
    public static final int ICON_GIF = 8;
    public static final int ICON_SECRETCHECK = 9;
    public static final int ICON_EMPTY = 10;
    public static final int ICON_EMPTY_NOPROGRESS = 11;
    public static final int ICON_CANCEL_NOPROFRESS = 12;
    public static final int ICON_CANCEL_PERCENT = 13;
    public static final int ICON_CANCEL_FILL = 14;

    private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint paint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint paint3 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Path path1 = new Path();
    private Path path2 = new Path();
    private RectF rect = new RectF();
    private ColorFilter colorFilter;
    private float scale = 1.0f;
    private DecelerateInterpolator interpolator = new DecelerateInterpolator();

    private final static float[] playPath1 = new float[] {18, 15, 34, 24, 34, 24, 18, 24, 18, 24};
    private final static float[] playPath2 = new float[] {18, 33, 34, 24, 34, 24, 18, 24, 18, 24};
    private final static float[] playFinalPath = new float[] {18, 15, 34, 24, 18, 33};
    private final static int playRotation = 0;

    private final static float[] pausePath1 = new float[] {16, 17, 32, 17, 32, 22, 16, 22, 16, 19.5f};
    private final static float[] pausePath2 = new float[] {16, 31, 32, 31, 32, 26, 16, 26, 16, 28.5f};
    private final static int pauseRotation = 90;

    private boolean isMini;

    private float transitionAnimationTime = 400.0f;

    private int lastPercent = -1;
    private String percentString;
    private int percentStringWidth;

    private float overrideAlpha = 1.0f;

    private int currentIcon;
    private int nextIcon;
    private float transitionProgress = 1.0f;
    private float savedTransitionProgress;
    private long lastAnimationTime;
    private boolean animatingTransition;

    private float downloadRadOffset;
    private float downloadProgress;
    private float animatedDownloadProgress;
    private float downloadProgressAnimationStart;
    private float downloadProgressTime;

    private final static float EPS = 0.001f;

    private final static float DOWNLOAD_TO_CANCEL_STAGE1 = 0.5f;
    private final static float DOWNLOAD_TO_CANCEL_STAGE2 = 0.2f;
    private final static float DOWNLOAD_TO_CANCEL_STAGE3 = 0.3f;

    private final static float CANCEL_TO_CHECK_STAGE1 = 0.5f;
    private final static float CANCEL_TO_CHECK_STAGE2 = 0.5f;

    private MediaActionDrawableDelegate delegate;

    public interface MediaActionDrawableDelegate {
        void invalidate();
    }

    public MediaActionDrawable() {
        paint.setColor(0xffffffff);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(AndroidUtilities.dp(3));
        paint.setStyle(Paint.Style.STROKE);

        paint3.setColor(0xffffffff);

        textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textPaint.setTextSize(AndroidUtilities.dp(13));
        textPaint.setColor(0xffffffff);

        paint2.setColor(0xffffffff);
        paint2.setPathEffect(new CornerPathEffect(AndroidUtilities.dp(2)));
    }

    @Override
    public void setAlpha(int alpha) {

    }

    public void setOverrideAlpha(float alpha) {
        overrideAlpha = alpha;
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        paint2.setColorFilter(colorFilter);
        paint3.setColorFilter(colorFilter);
        textPaint.setColorFilter(colorFilter);
    }

    public void setColor(int value) {
        paint.setColor(value | 0xff000000);
        paint2.setColor(value | 0xff000000);
        paint3.setColor(value | 0xff000000);
        textPaint.setColor(value | 0xff000000);
        colorFilter = new PorterDuffColorFilter(value, PorterDuff.Mode.MULTIPLY);
    }

    public int getColor() {
        return paint.getColor();
    }

    public void setMini(boolean value) {
        isMini = value;
        paint.setStrokeWidth(AndroidUtilities.dp(isMini ? 2 : 3));
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSPARENT;
    }

    public void setDelegate(MediaActionDrawableDelegate mediaActionDrawableDelegate) {
        delegate = mediaActionDrawableDelegate;
    }

    public boolean setIcon(int icon, boolean animated) {
        if (currentIcon == icon && nextIcon != icon) {
            currentIcon = nextIcon;
            transitionProgress = 1.0f;
        }
        if (animated) {
            if (currentIcon == icon || nextIcon == icon) {
                return false;
            }
            if (currentIcon == ICON_DOWNLOAD && (icon == ICON_CANCEL || icon == ICON_CANCEL_FILL)) {
                transitionAnimationTime = 400.0f;
            } else if (currentIcon != ICON_NONE && icon == ICON_CHECK) {
                transitionAnimationTime = 360.0f;
            } else if (currentIcon == ICON_NONE && icon == ICON_CANCEL_FILL || currentIcon == ICON_CANCEL_FILL && icon == ICON_NONE) {
                transitionAnimationTime = 160.0f;
            } else {
                transitionAnimationTime = 220.0f;
            }
            if (animatingTransition) {
                currentIcon = nextIcon;
            }
            animatingTransition = true;
            nextIcon = icon;
            savedTransitionProgress = transitionProgress;
            transitionProgress = 0.0f;
        } else {
            if (currentIcon == icon) {
                return false;
            }
            animatingTransition = false;
            currentIcon = nextIcon = icon;
            savedTransitionProgress = transitionProgress;
            transitionProgress = 1.0f;
        }
        if (icon == ICON_CANCEL || icon == ICON_CANCEL_FILL) {
            downloadRadOffset = 112;
            animatedDownloadProgress = 0.0f;
            downloadProgressAnimationStart = 0.0f;
            downloadProgressTime = 0.0f;
        }
        invalidateSelf();
        return true;
    }

    public int getCurrentIcon() {
        return nextIcon;
    }

    public int getPreviousIcon() {
        return currentIcon;
    }

    public void setProgress(float value, boolean animated) {
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

    private float getCircleValue(float value) {
        while (value > 360) {
            value -= 360;
        }
        return value;
    }

    public float getProgressAlpha() {
        return 1.0f - transitionProgress;
    }

    public float getTransitionProgress() {
        return animatingTransition ? transitionProgress : 1.0f;
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);
        scale = (right - left) / (float) getIntrinsicWidth();
        if (scale < 0.7f) {
            paint.setStrokeWidth(AndroidUtilities.dp(2));
        }
    }

    @Override
    public void invalidateSelf() {
        super.invalidateSelf();
        if (delegate != null) {
            delegate.invalidate();
        }
    }

    @Override
    public void draw(Canvas canvas) {
        android.graphics.Rect bounds = getBounds();

        int cx = bounds.centerX();
        int cy = bounds.centerY();

        if (nextIcon == ICON_NONE) {
            float progress = 1.0f - transitionProgress;
            canvas.save();
            canvas.scale(progress, progress, cx, cy);
        } else if ((nextIcon == ICON_CHECK || nextIcon == ICON_EMPTY) && currentIcon == ICON_NONE) {
            canvas.save();
            canvas.scale(transitionProgress, transitionProgress, cx, cy);
        }

        int width = AndroidUtilities.dp(3);
        if (currentIcon == ICON_DOWNLOAD || nextIcon == ICON_DOWNLOAD) {
            float yStart = cy - AndroidUtilities.dp(9) * scale;
            float yEnd = cy + AndroidUtilities.dp(9) * scale;
            float yStart2;
            float yEnd2 = cy + AndroidUtilities.dp(12) * scale;

            float transition;
            if ((currentIcon == ICON_CANCEL || currentIcon == ICON_CANCEL_FILL) && nextIcon == ICON_DOWNLOAD) {
                paint.setAlpha((int) (255 * Math.min(1.0f, transitionProgress / 0.5f)));
                transition = transitionProgress;
                yStart2 = cy + AndroidUtilities.dp(12) * scale;
            } else {
                if (nextIcon != ICON_CANCEL && nextIcon != ICON_CANCEL_FILL && nextIcon != ICON_DOWNLOAD) {
                    paint.setAlpha((int) (255 * Math.min(1.0f, savedTransitionProgress / 0.5f) * (1.0f - transitionProgress)));
                    transition = savedTransitionProgress;
                } else {
                    paint.setAlpha(255);
                    transition = transitionProgress;
                }
                yStart2 = cy + AndroidUtilities.dp(1) * scale;
            }

            float y1, y2, x1, x2, y3;
            if (animatingTransition) {
                float progress = transition;
                if (nextIcon == ICON_DOWNLOAD || progress <= DOWNLOAD_TO_CANCEL_STAGE1) {
                    float currentProgress;
                    float currentBackProgress;
                    if (nextIcon == ICON_DOWNLOAD) {
                        currentBackProgress = transition;
                        currentProgress = 1.0f - currentBackProgress;
                    } else {
                        currentProgress = transition / DOWNLOAD_TO_CANCEL_STAGE1;
                        currentBackProgress = 1.0f - currentProgress;
                    }
                    y1 = yStart + (yStart2 - yStart) * currentProgress;
                    y2 = yEnd + (yEnd2 - yEnd) * currentProgress;
                    x1 = cx - AndroidUtilities.dp(8) * currentBackProgress * scale;
                    x2 = cx + AndroidUtilities.dp(8) * currentBackProgress * scale;
                    y3 = y2 - AndroidUtilities.dp(8) * currentBackProgress * scale;
                } else {
                    float currentProgress;
                    float currentProgress2;
                    float currentProgress3;
                    float d = AndroidUtilities.dp(13) * scale;

                    progress -= DOWNLOAD_TO_CANCEL_STAGE1;
                    currentProgress3 = progress / (DOWNLOAD_TO_CANCEL_STAGE2 + DOWNLOAD_TO_CANCEL_STAGE3);
                    if (progress > DOWNLOAD_TO_CANCEL_STAGE2) {
                        progress -= DOWNLOAD_TO_CANCEL_STAGE2;
                        currentProgress = 1.0f;
                        currentProgress2 = progress / DOWNLOAD_TO_CANCEL_STAGE3;
                    } else {
                        currentProgress = progress / DOWNLOAD_TO_CANCEL_STAGE2;
                        currentProgress2 = 0.0f;
                    }
                    rect.set(cx - d, yEnd2 - d / 2, cx, yEnd2 + d / 2);
                    float start = 100 * currentProgress2;
                    canvas.drawArc(rect, start, 104 * currentProgress3 - start, false, paint);

                    y1 = yStart2 + (yEnd2 - yStart2) * currentProgress;
                    y2 = y3 = yEnd2;
                    x1 = x2 = cx;

                    if (currentProgress2 > 0) {
                        float rotation;
                        if (nextIcon == ICON_CANCEL_FILL) {
                            rotation = 0;
                        } else {
                            rotation = -45 * (1.0f - currentProgress2);
                        }
                        d = AndroidUtilities.dp(7) * currentProgress2 * scale;
                        int alpha = (int) (255 * currentProgress2);
                        if (nextIcon != ICON_CANCEL && nextIcon != ICON_CANCEL_FILL && nextIcon != ICON_DOWNLOAD) {
                            float backProgress = (1.0f - Math.min(1.0f, transitionProgress / 0.5f));
                            //d *= backProgress;
                            alpha *= backProgress;
                        }

                        if (rotation != 0) {
                            canvas.save();
                            canvas.rotate(rotation, cx, cy);
                        }
                        if (alpha != 0) {
                            paint.setAlpha(alpha);
                            if (nextIcon == ICON_CANCEL_FILL) {
                                paint3.setAlpha(alpha);
                                rect.set(cx - AndroidUtilities.dp(3.5f), cy - AndroidUtilities.dp(3.5f), cx + AndroidUtilities.dp(3.5f), cy + AndroidUtilities.dp(3.5f));
                                canvas.drawRoundRect(rect, AndroidUtilities.dp(2), AndroidUtilities.dp(2), paint3);

                                paint.setAlpha((int) (alpha * 0.15f));
                                int diff = AndroidUtilities.dp(isMini ? 2 : 4);
                                rect.set(bounds.left + diff, bounds.top + diff, bounds.right - diff, bounds.bottom - diff);
                                canvas.drawArc(rect, 0, 360, false, paint);
                                paint.setAlpha(alpha);
                            } else {
                                canvas.drawLine(cx - d, cy - d, cx + d, cy + d, paint);
                                canvas.drawLine(cx + d, cy - d, cx - d, cy + d, paint);
                            }
                        }
                        if (rotation != 0) {
                            canvas.restore();
                        }
                    }
                }
            } else {
                y1 = yStart;
                y2 = yEnd;
                x1 = cx - AndroidUtilities.dp(8) * scale;
                x2 = cx + AndroidUtilities.dp(8) * scale;
                y3 = y2 - AndroidUtilities.dp(8) * scale;
            }
            if (y1 != y2) {
                canvas.drawLine(cx, y1, cx, y2, paint);
            }
            if (x1 != cx) {
                canvas.drawLine(x1, y3, cx, y2, paint);
                canvas.drawLine(x2, y3, cx, y2, paint);
            }
        }

        if (currentIcon == ICON_CANCEL || currentIcon == ICON_CANCEL_FILL || currentIcon == ICON_NONE && (nextIcon == ICON_CANCEL_FILL || nextIcon == ICON_CANCEL)) {
            float d;
            float rotation;
            float iconScale = 1.0f;
            float iconScaleX = 0;
            float iconScaleY = 0;
            int alpha;
            if (nextIcon == ICON_DOWNLOAD) {
                if (transitionProgress <= DOWNLOAD_TO_CANCEL_STAGE3 + DOWNLOAD_TO_CANCEL_STAGE2) {
                    float progress = transitionProgress / (DOWNLOAD_TO_CANCEL_STAGE3 + DOWNLOAD_TO_CANCEL_STAGE2);
                    float backProgress = 1.0f - progress;
                    d = AndroidUtilities.dp(7) * backProgress * scale;
                    alpha = (int) (255 * backProgress);
                } else {
                    d = 0;
                    alpha = 0;
                }
                rotation = 0;
            } else if (nextIcon == ICON_PLAY || nextIcon == ICON_PAUSE || nextIcon == ICON_FILE || nextIcon == ICON_GIF || nextIcon == ICON_SECRETCHECK || nextIcon == ICON_FIRE || nextIcon == ICON_CHECK) {
                float progress;
                float backProgress;
                if (nextIcon == ICON_CHECK) {
                    progress = Math.min(1.0f, transitionProgress / CANCEL_TO_CHECK_STAGE1);
                    backProgress = 1.0f - progress;
                } else {
                    progress = transitionProgress;
                    backProgress = 1.0f - progress;
                }
                rotation = 45 * progress;
                d = AndroidUtilities.dp(7) * backProgress * scale;
                alpha = (int) (255 * Math.min(1.0f, backProgress * 2.0f));
            } else if (nextIcon == ICON_NONE) {
                float progress = transitionProgress;
                float backProgress = 1.0f - progress;
                d = AndroidUtilities.dp(7) * scale;
                alpha = (int) (255 * backProgress);
                if (currentIcon == ICON_CANCEL_FILL) {
                    rotation = 0;
                    iconScale = backProgress;
                } else {
                    rotation = 45 * progress;
                    iconScale = 1.0f;
                }
            } else if (nextIcon == ICON_CANCEL_FILL || nextIcon == ICON_CANCEL) {
                float progress = transitionProgress;
                float backProgress = 1.0f - progress;
                if (currentIcon == ICON_NONE) {
                    rotation = 0;
                    iconScale = progress;
                } else {
                    rotation = 45 * backProgress;
                    iconScale = 1.0f;
                }
                d = AndroidUtilities.dp(7) * scale;
                alpha = (int) (255 * progress);
                if (nextIcon == ICON_CANCEL_FILL) {
                    iconScaleX = bounds.left;
                    iconScaleY = bounds.top;
                } else {
                    iconScaleX = bounds.centerX();
                    iconScaleY = bounds.centerY();
                }
            } else {
                rotation = 0;
                d = AndroidUtilities.dp(7) * scale;
                alpha = 255;
            }
            if (iconScale != 1.0f) {
                canvas.save();
                canvas.scale(iconScale, iconScale, iconScaleX, iconScaleY);
            }
            if (rotation != 0) {
                canvas.save();
                canvas.rotate(rotation, cx, cy);
            }
            if (alpha != 0) {
                paint.setAlpha((int) (alpha * overrideAlpha));
                if (currentIcon == ICON_CANCEL_FILL || nextIcon == ICON_CANCEL_FILL) {
                    paint3.setAlpha((int) (alpha * overrideAlpha));
                    rect.set(cx - AndroidUtilities.dp(3.5f), cy - AndroidUtilities.dp(3.5f), cx + AndroidUtilities.dp(3.5f), cy + AndroidUtilities.dp(3.5f));
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(2), AndroidUtilities.dp(2), paint3);
                } else {
                    canvas.drawLine(cx - d, cy - d, cx + d, cy + d, paint);
                    canvas.drawLine(cx + d, cy - d, cx - d, cy + d, paint);
                }
            }
            if (rotation != 0) {
                canvas.restore();
            }
            if ((currentIcon == ICON_CANCEL || currentIcon == ICON_CANCEL_FILL || currentIcon == ICON_NONE && (nextIcon == ICON_CANCEL_FILL || nextIcon == ICON_CANCEL)) && alpha != 0) {
                float rad = Math.max(4, 360 * animatedDownloadProgress);
                int diff = AndroidUtilities.dp(isMini ? 2 : 4);
                rect.set(bounds.left + diff, bounds.top + diff, bounds.right - diff, bounds.bottom - diff);

                if (currentIcon == ICON_CANCEL_FILL || currentIcon == ICON_NONE && (nextIcon == ICON_CANCEL_FILL || nextIcon == ICON_CANCEL)) {
                    paint.setAlpha((int) (alpha * 0.15f * overrideAlpha));
                    canvas.drawArc(rect, 0, 360, false, paint);
                    paint.setAlpha(alpha);
                }
                canvas.drawArc(rect, downloadRadOffset, rad, false, paint);
            }
            if (iconScale != 1.0f) {
                canvas.restore();
            }
        } else if (currentIcon == ICON_EMPTY || nextIcon == ICON_EMPTY || currentIcon == ICON_CANCEL_PERCENT) {
            int alpha;
            if (nextIcon == ICON_NONE || nextIcon == ICON_CHECK) {
                float progress = transitionProgress;
                float backProgress = 1.0f - progress;
                alpha = (int) (255 * backProgress);
            } else {
                alpha = 255;
            }

            if (alpha != 0) {
                paint.setAlpha((int) (alpha * overrideAlpha));
                float rad = Math.max(4, 360 * animatedDownloadProgress);
                int diff = AndroidUtilities.dp(isMini ? 2 : 4);
                rect.set(bounds.left + diff, bounds.top + diff, bounds.right - diff, bounds.bottom - diff);
                canvas.drawArc(rect, downloadRadOffset, rad, false, paint);
            }
        }

        Drawable nextDrawable = null;
        Drawable previousDrawable = null;

        float drawableScale;
        float previowsDrawableScale;
        if (currentIcon == nextIcon) {
            drawableScale = previowsDrawableScale = 1.0f;
        } else if (currentIcon == ICON_NONE) {
            drawableScale = transitionProgress;
            previowsDrawableScale = 1.0f - transitionProgress;
        } else {
            drawableScale = Math.min(1.0f, transitionProgress / 0.5f);
            previowsDrawableScale = Math.max(0.0f, 1.0f - transitionProgress / 0.5f);
        }

        if (nextIcon == ICON_FILE) {
            nextDrawable = Theme.chat_fileIcon;
        } else if (currentIcon == ICON_FILE) {
            previousDrawable = Theme.chat_fileIcon;
        }
        if (nextIcon == ICON_FIRE) {
            nextDrawable = Theme.chat_flameIcon;
        } else if (currentIcon == ICON_FIRE) {
            previousDrawable = Theme.chat_flameIcon;
        }
        if (nextIcon == ICON_GIF) {
            nextDrawable = Theme.chat_gifIcon;
        } else if (currentIcon == ICON_GIF) {
            previousDrawable = Theme.chat_gifIcon;
        }


        if (currentIcon == ICON_SECRETCHECK || nextIcon == ICON_SECRETCHECK) {
            paint.setAlpha(currentIcon == nextIcon ? 255 : (int) (transitionProgress * 255));
            int y = cy + AndroidUtilities.dp(7);
            int x = cx - AndroidUtilities.dp(3);
            if (currentIcon != nextIcon) {
                canvas.save();
                canvas.scale(transitionProgress, transitionProgress, cx, cy);
            }
            canvas.drawLine(x - AndroidUtilities.dp(6), y - AndroidUtilities.dp(6), x, y, paint);
            canvas.drawLine(x, y, x + AndroidUtilities.dp(12), y - AndroidUtilities.dp(12), paint);
            if (currentIcon != nextIcon) {
                canvas.restore();
            }
        }
        if (currentIcon == ICON_CANCEL_NOPROFRESS || nextIcon == ICON_CANCEL_NOPROFRESS) {
            float transition;
            if (currentIcon == nextIcon) {
                transition = 1.0f;
            } else if (nextIcon == ICON_CANCEL_PERCENT) {
                transition = transitionProgress;
            } else {
                transition = 1.0f - transitionProgress;
            }

            paint.setAlpha(currentIcon == nextIcon ? 255 : (int) (transition * 255));
            int y = cy + AndroidUtilities.dp(7);
            int x = cx - AndroidUtilities.dp(3);
            if (currentIcon != nextIcon) {
                canvas.save();
                canvas.scale(transition, transition, cx, cy);
            }

            float d = AndroidUtilities.dp(7) * scale;
            canvas.drawLine(cx - d, cy - d, cx + d, cy + d, paint);
            canvas.drawLine(cx + d, cy - d, cx - d, cy + d, paint);

            if (currentIcon != nextIcon) {
                canvas.restore();
            }
        }
        if (currentIcon == ICON_CANCEL_PERCENT || nextIcon == ICON_CANCEL_PERCENT) {
            float transition;
            if (currentIcon == nextIcon) {
                transition = 1.0f;
            } else if (nextIcon == ICON_CANCEL_PERCENT) {
                transition = transitionProgress;
            } else {
                transition = 1.0f - transitionProgress;
            }

            textPaint.setAlpha((int) (transition * 255));
            int y = cy + AndroidUtilities.dp(5);
            int x = cx - percentStringWidth / 2;
            if (currentIcon != nextIcon) {
                canvas.save();
                canvas.scale(transition, transition, cx, cy);
            }
            int newPercent = (int) (animatedDownloadProgress * 100);
            if (percentString == null || newPercent != lastPercent) {
                lastPercent = newPercent;
                percentString = String.format("%d%%", lastPercent);
                percentStringWidth = (int) Math.ceil(textPaint.measureText(percentString));
            }
            canvas.drawText(percentString, x, y, textPaint);
            if (currentIcon != nextIcon) {
                canvas.restore();
            }
        }
        if (currentIcon == ICON_PLAY || currentIcon == ICON_PAUSE || nextIcon == ICON_PLAY || nextIcon == ICON_PAUSE) {
            float p;
            if (currentIcon == ICON_PLAY && nextIcon == ICON_PAUSE || currentIcon == ICON_PAUSE && nextIcon == ICON_PLAY) {
                p = animatingTransition ? interpolator.getInterpolation(transitionProgress) : 0.0f;
            } else {
                p = 0.0f;
            }

            path1.reset();
            path2.reset();

            float[] p1;
            float[] p2;

            float[] p3;
            float[] p4;

            float[] finalPath = null;

            int rotation1;
            int rotation2;

            switch (currentIcon) {
                case ICON_PLAY:
                    p1 = playPath1;
                    p2 = playPath2;
                    finalPath = playFinalPath;
                    rotation1 = playRotation;
                    break;
                case ICON_PAUSE:
                    p1 = pausePath1;
                    p2 = pausePath2;
                    rotation1 = pauseRotation;
                    break;
                default:
                    rotation1 = 0;
                    p1 = p2 = null;
                    break;
            }

            switch (nextIcon) {
                case ICON_PLAY:
                    p3 = playPath1;
                    p4 = playPath2;
                    rotation2 = playRotation;
                    break;
                case ICON_PAUSE:
                    p3 = pausePath1;
                    p4 = pausePath2;
                    rotation2 = pauseRotation;
                    break;
                default:
                    rotation2 = 0;
                    p3 = p4 = null;
                    break;
            }

            if (p1 == null) {
                p1 = p3;
                p2 = p4;
                p3 = null;
                p4 = null;
            }
            if (!animatingTransition && finalPath != null) {
                for (int a = 0; a < finalPath.length / 2; a++) {
                    if (a == 0) {
                        path1.moveTo(AndroidUtilities.dp(finalPath[a * 2]) * scale, AndroidUtilities.dp(finalPath[a * 2 + 1]) * scale);
                        path2.moveTo(AndroidUtilities.dp(finalPath[a * 2]) * scale, AndroidUtilities.dp(finalPath[a * 2 + 1]) * scale);
                    } else {
                        path1.lineTo(AndroidUtilities.dp(finalPath[a * 2]) * scale, AndroidUtilities.dp(finalPath[a * 2 + 1]) * scale);
                        path2.lineTo(AndroidUtilities.dp(finalPath[a * 2]) * scale, AndroidUtilities.dp(finalPath[a * 2 + 1]) * scale);
                    }
                }
            } else if (p3 == null) {
                for (int a = 0; a < 5; a++) {
                    if (a == 0) {
                        path1.moveTo(AndroidUtilities.dp(p1[a * 2]) * scale, AndroidUtilities.dp(p1[a * 2 + 1]) * scale);
                        path2.moveTo(AndroidUtilities.dp(p2[a * 2]) * scale, AndroidUtilities.dp(p2[a * 2 + 1]) * scale);
                    } else {
                        path1.lineTo(AndroidUtilities.dp(p1[a * 2]) * scale, AndroidUtilities.dp(p1[a * 2 + 1]) * scale);
                        path2.lineTo(AndroidUtilities.dp(p2[a * 2]) * scale, AndroidUtilities.dp(p2[a * 2 + 1]) * scale);
                    }
                }
                if (nextIcon == ICON_NONE) {
                    paint2.setAlpha((int) (255 * (1.0f - transitionProgress)));
                } else {
                    paint2.setAlpha(currentIcon == nextIcon ? 255 : (int) (transitionProgress * 255));
                }
            } else {
                for (int a = 0; a < 5; a++) {
                    if (a == 0) {
                        path1.moveTo(AndroidUtilities.dp(p1[a * 2] + (p3[a * 2] - p1[a * 2]) * p) * scale, AndroidUtilities.dp(p1[a * 2 + 1] + (p3[a * 2 + 1] - p1[a * 2 + 1]) * p) * scale);
                        path2.moveTo(AndroidUtilities.dp(p2[a * 2] + (p4[a * 2] - p2[a * 2]) * p) * scale, AndroidUtilities.dp(p2[a * 2 + 1] + (p4[a * 2 + 1] - p2[a * 2 + 1]) * p) * scale);
                    } else {
                        path1.lineTo(AndroidUtilities.dp(p1[a * 2] + (p3[a * 2] - p1[a * 2]) * p) * scale, AndroidUtilities.dp(p1[a * 2 + 1] + (p3[a * 2 + 1] - p1[a * 2 + 1]) * p) * scale);
                        path2.lineTo(AndroidUtilities.dp(p2[a * 2] + (p4[a * 2] - p2[a * 2]) * p) * scale, AndroidUtilities.dp(p2[a * 2 + 1] + (p4[a * 2 + 1] - p2[a * 2 + 1]) * p) * scale);
                    }
                }
                paint2.setAlpha(255);
            }

            path1.close();
            path2.close();

            canvas.save();
            canvas.translate(bounds.left, bounds.top);
            canvas.rotate(rotation1 + (rotation2 - rotation1) * p, cx - bounds.left, cy - bounds.top);
            if (currentIcon != ICON_PLAY && currentIcon != ICON_PAUSE || currentIcon == ICON_NONE) {
                canvas.scale(drawableScale, drawableScale, cx - bounds.left, cy - bounds.top);
            }
            canvas.drawPath(path1, paint2);
            canvas.drawPath(path2, paint2);
            canvas.restore();
        }
        if (currentIcon == ICON_CHECK || nextIcon == ICON_CHECK) {
            float progress1;
            float progress2;
            if (currentIcon != ICON_CHECK) {
                if (transitionProgress > CANCEL_TO_CHECK_STAGE1) {
                    float progress = (transitionProgress - CANCEL_TO_CHECK_STAGE1) / CANCEL_TO_CHECK_STAGE2;
                    progress1 = 1.0f - Math.min(1.0f, progress / 0.5f);
                    progress2 = progress > 0.5f ? ((progress - 0.5f) / 0.5f) : 0.0f;
                } else {
                    progress1 = 1.0f;
                    progress2 = 0.0f;
                }
            } else {
                progress1 = 0.0f;
                progress2 = 1.0f;
            }
            int y = cy + AndroidUtilities.dp(7);
            int x = cx - AndroidUtilities.dp(3);
            paint.setAlpha(255);
            if (progress1 < 1) {
                canvas.drawLine(x - AndroidUtilities.dp(6), y - AndroidUtilities.dp(6), x - AndroidUtilities.dp(6) * progress1, y - AndroidUtilities.dp(6) * progress1, paint);
            }
            if (progress2 > 0) {
                canvas.drawLine(x, y, x + AndroidUtilities.dp(12) * progress2, y - AndroidUtilities.dp(12) * progress2, paint);
            }
        }

        if (previousDrawable != null && previousDrawable != nextDrawable) {
            int w = (int) (previousDrawable.getIntrinsicWidth() * previowsDrawableScale);
            int h = (int) (previousDrawable.getIntrinsicHeight() * previowsDrawableScale);
            previousDrawable.setColorFilter(colorFilter);
            previousDrawable.setAlpha(currentIcon == nextIcon ? 255 : (int) ((1.0f - transitionProgress) * 255));
            previousDrawable.setBounds(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
            previousDrawable.draw(canvas);
        }
        if (nextDrawable != null) {
            int w = (int) (nextDrawable.getIntrinsicWidth() * drawableScale);
            int h = (int) (nextDrawable.getIntrinsicHeight() * drawableScale);
            nextDrawable.setColorFilter(colorFilter);
            nextDrawable.setAlpha(currentIcon == nextIcon ? 255 : (int) (transitionProgress * 255));
            nextDrawable.setBounds(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
            nextDrawable.draw(canvas);
        }

        long newTime = System.currentTimeMillis();
        long dt = newTime - lastAnimationTime;
        if (dt > 17) {
            dt = 17;
        }
        lastAnimationTime = newTime;

        if (currentIcon == ICON_CANCEL || currentIcon == ICON_CANCEL_FILL || currentIcon == ICON_NONE && nextIcon == ICON_CANCEL_FILL || currentIcon == ICON_EMPTY || currentIcon == ICON_CANCEL_PERCENT) {
            downloadRadOffset += 360 * dt / 2500.0f;
            downloadRadOffset = getCircleValue(downloadRadOffset);

            if (nextIcon != ICON_DOWNLOAD) {
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
            }
            invalidateSelf();
        }

        if (animatingTransition) {
            if (transitionProgress < 1.0f) {
                transitionProgress += dt / transitionAnimationTime;
                if (transitionProgress >= 1.0f) {
                    currentIcon = nextIcon;
                    transitionProgress = 1.0f;
                    animatingTransition = false;
                }
                invalidateSelf();
            }
        }
        if (nextIcon == ICON_NONE || (nextIcon == ICON_CHECK || nextIcon == ICON_EMPTY) && currentIcon == ICON_NONE) {
            canvas.restore();
        }
    }

    @Override
    public int getIntrinsicWidth() {
        return AndroidUtilities.dp(48);
    }

    @Override
    public int getIntrinsicHeight() {
        return AndroidUtilities.dp(48);
    }

    @Override
    public int getMinimumWidth() {
        return AndroidUtilities.dp(48);
    }

    @Override
    public int getMinimumHeight() {
        return AndroidUtilities.dp(48);
    }
}
