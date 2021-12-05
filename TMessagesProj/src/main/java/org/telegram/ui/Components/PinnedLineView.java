package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class PinnedLineView extends View {

    int selectedPosition = -1;
    int totalCount = 0;

    int animateToPosition;
    float animateFromPosition;
    int animateFromTotal;
    int animateToTotal;
    boolean animationInProgress;

    boolean replaceInProgress;
    private float startOffsetFrom;
    private float startOffsetTo;
    private int lineHFrom;
    private int lineHTo;

    RectF rectF = new RectF();
    float animationProgress;
    ValueAnimator animator;

    Paint fadePaint;
    Paint fadePaint2;

    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int nextPosition = -1;
    private int color;
    private final Theme.ResourcesProvider resourcesProvider;

    public PinnedLineView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeCap(Paint.Cap.ROUND);

        selectedPaint.setStyle(Paint.Style.FILL);
        selectedPaint.setStrokeCap(Paint.Cap.ROUND);

        fadePaint = new Paint();
        LinearGradient gradient = new LinearGradient(0, 0,0,  AndroidUtilities.dp(6), new int[]{0xffffffff, 0}, new float[]{0f, 1f}, Shader.TileMode.CLAMP);
        fadePaint.setShader(gradient);
        fadePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

        fadePaint2 = new Paint();
        gradient = new LinearGradient(0, 0,0,  AndroidUtilities.dp(6), new int[]{0, 0xffffffff}, new float[]{0f, 1f}, Shader.TileMode.CLAMP);
        fadePaint2.setShader(gradient);
        fadePaint2.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

        updateColors();
    }

    public void updateColors() {
        color = getThemedColor(Theme.key_chat_topPanelLine);
        paint.setColor(ColorUtils.setAlphaComponent(color, (int) ((Color.alpha(color) / 255f) * 112)));
        selectedPaint.setColor(color);
    }

    private void selectPosition(int position) {
        if (replaceInProgress) {
            nextPosition = position;
            return;
        }
        if (animationInProgress) {
            if (animateToPosition == position) {
                return;
            }
            if (animator != null) {
                animator.cancel();
            }
            animateFromPosition = animateFromPosition * (1f - animationProgress) + animateToPosition * animationProgress;
        } else {
            animateFromPosition = selectedPosition;
        }
        if (position != selectedPosition) {
            animateToPosition = position;
            animationInProgress = true;
            animationProgress = 0;
            invalidate();
            animator = ValueAnimator.ofFloat(0, 1f);
            animator.addUpdateListener(valueAnimator -> {
                animationProgress = (float) valueAnimator.getAnimatedValue();
                invalidate();
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animationInProgress = false;
                    selectedPosition = animateToPosition;
                    invalidate();
                    if (nextPosition >= 0) {
                        selectPosition(nextPosition);
                        nextPosition = -1;
                    }
                }
            });
            animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            animator.setDuration(220);
            animator.start();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (selectedPosition < 0 || totalCount == 0) {
            return;
        }
        boolean drawFade = (replaceInProgress ? Math.max(animateFromTotal, animateToTotal) : totalCount) > 3;
        if (drawFade) {
            canvas.saveLayerAlpha(0, 0, getMeasuredWidth(), getMeasuredHeight(), 255, Canvas.ALL_SAVE_FLAG);
        }
        int viewPadding = AndroidUtilities.dp(8);
        float lineH;
        if (replaceInProgress) {
            lineH = lineHFrom * (1f - animationProgress) + lineHTo * animationProgress;
        } else {
            if (totalCount == 0) {
                return;
            }
            lineH = (getMeasuredHeight() - viewPadding * 2) / (float) (Math.min(totalCount, 3));
        }
        if (lineH == 0) {
            return;
        }
        float linePadding = AndroidUtilities.dpf2(0.7f);

        float startOffset;
        if (replaceInProgress) {
            startOffset = startOffsetFrom * (1f - animationProgress) + startOffsetTo * animationProgress;
        } else {
            if (animationInProgress) {
                float offset1 = (animateFromPosition - 1) * lineH;
                float offset2 = (animateToPosition - 1) * lineH;
                startOffset = offset1 * (1f - animationProgress) + offset2 * animationProgress;
            } else {
                startOffset = (selectedPosition - 1) * lineH;
            }

            if (startOffset < 0) {
                startOffset = 0;
            } else if (viewPadding + (totalCount - 1) * lineH - startOffset < getMeasuredHeight() - viewPadding - lineH) {
                startOffset = (viewPadding + (totalCount - 1) * lineH) - (getMeasuredHeight() - viewPadding - lineH);
            }
        }

        float r = getMeasuredWidth() / 2f;

        int start = Math.max(0, (int) ((viewPadding + startOffset) / lineH - 1));
        int end = Math.min(start + 6, replaceInProgress ? Math.max(animateFromTotal, animateToTotal) : totalCount);
        for (int i = start; i < end; i++) {
            float startY = viewPadding + i * lineH - startOffset;
            if (startY + lineH < 0 || startY > getMeasuredHeight()) {
                continue;
            }
            rectF.set(0, startY + linePadding, getMeasuredWidth(), startY + lineH - linePadding);
            if (replaceInProgress && i >= animateToTotal) {
                paint.setColor(ColorUtils.setAlphaComponent(color, (int) ((Color.alpha(color) / 255f) * 76 * (1f - animationProgress))));
                canvas.drawRoundRect(rectF, r, r, paint);
                paint.setColor(ColorUtils.setAlphaComponent(color, (int) ((Color.alpha(color) / 255f) * 76)));
            } else if (replaceInProgress && i >= animateFromTotal) {
                paint.setColor(ColorUtils.setAlphaComponent(color, (int) ((Color.alpha(color) / 255f) * 76 * animationProgress)));
                canvas.drawRoundRect(rectF, r, r, paint);
                paint.setColor(ColorUtils.setAlphaComponent(color, (int) ((Color.alpha(color) / 255f) * 76)));
            } else {
                canvas.drawRoundRect(rectF, r, r, paint);
            }

        }

        if (animationInProgress) {
            float startY = viewPadding + (animateFromPosition * (1f - animationProgress) + animateToPosition * animationProgress) * lineH - startOffset;
            rectF.set(0, startY + linePadding, getMeasuredWidth(), startY + lineH - linePadding);
            canvas.drawRoundRect(rectF, r, r, selectedPaint);
        } else {
            float startY = viewPadding + selectedPosition * lineH - startOffset;
            rectF.set(0, startY + linePadding, getMeasuredWidth(), startY + lineH - linePadding);
            canvas.drawRoundRect(rectF, r, r, selectedPaint);
        }

        if (drawFade) {
            canvas.drawRect(0, 0, getMeasuredWidth(), AndroidUtilities.dp(6), fadePaint);
            canvas.drawRect(0, getMeasuredHeight() - AndroidUtilities.dp(6), getMeasuredWidth(), getMeasuredHeight(), fadePaint);

            canvas.translate(0, getMeasuredHeight() - AndroidUtilities.dp(6));
            canvas.drawRect(0, 0, getMeasuredWidth(), AndroidUtilities.dp(6), fadePaint2);
        }
    }

    public void set(int position, int totalCount, boolean animated) {
        if (selectedPosition < 0 || totalCount == 0 || this.totalCount == 0) {
            animated = false;
        }
        if (!animated) {
            if (animator != null) {
                animator.cancel();
            }
            this.selectedPosition = position;
            this.totalCount = totalCount;
            invalidate();
        } else {
            if (this.totalCount != totalCount || (Math.abs(selectedPosition - position) > 2 && !animationInProgress && !replaceInProgress)) {
                if (animator != null) {
                    nextPosition = 0;
                    animator.cancel();
                }
                int viewPadding = AndroidUtilities.dp(8);
                lineHFrom = (getMeasuredHeight() - viewPadding * 2) / (Math.min(this.totalCount, 3));
                lineHTo = (getMeasuredHeight() - viewPadding * 2) / (Math.min(totalCount, 3));

                startOffsetFrom = (selectedPosition - 1) * lineHFrom;
                if (startOffsetFrom < 0) {
                    startOffsetFrom = 0;
                } else if (viewPadding + (this.totalCount - 1) * lineHFrom - startOffsetFrom < getMeasuredHeight() - viewPadding - lineHFrom) {
                    startOffsetFrom = (viewPadding + (this.totalCount - 1) * lineHFrom) - (getMeasuredHeight() - viewPadding - lineHFrom);
                }

                startOffsetTo = (position - 1) * lineHTo;
                if (startOffsetTo < 0) {
                    startOffsetTo = 0;
                } else if (viewPadding + (totalCount - 1) * lineHTo - startOffsetTo < getMeasuredHeight() - viewPadding - lineHTo) {
                    startOffsetTo = (viewPadding + (totalCount - 1) * lineHTo) - (getMeasuredHeight() - viewPadding - lineHTo);
                }
                animateFromPosition = selectedPosition;
                animateToPosition = position;

                selectedPosition = position;
                animateFromTotal = this.totalCount;
                animateToTotal = totalCount;
                this.totalCount = totalCount;

                replaceInProgress = true;
                animationInProgress = true;
                animationProgress = 0;

                invalidate();
                animator = ValueAnimator.ofFloat(0, 1f);
                animator.addUpdateListener(valueAnimator -> {
                    animationProgress = (float) valueAnimator.getAnimatedValue();
                    invalidate();
                });
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        replaceInProgress = false;
                        animationInProgress = false;
                        invalidate();
                        if (nextPosition >= 0) {
                            selectPosition(nextPosition);
                            nextPosition = -1;
                        }
                    }
                });
                animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                animator.setDuration(220);
                animator.start();
            } else {
                selectPosition(position);
            }
        }
    }

    private int getThemedColor(String key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }
}
