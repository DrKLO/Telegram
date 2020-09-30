package org.telegram.ui.Components.voip;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

public class VoIPToggleButton extends FrameLayout {

    Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Drawable[] icon = new Drawable[2];
    TextView[] textView = new TextView[2];

    int backgroundColor;
    int animateToBackgroundColor;

    float replaceProgress;
    ValueAnimator replaceAnimator;

    int currentIconRes;
    int currentIconColor;
    int currentBackgroundColor;
    String currentText;
    public int animationDelay;

    private boolean iconChangeColor;
    private int replaceColorFrom;

    private Paint crossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint xRefPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float crossProgress;
    private boolean drawCross;

    private Bitmap iconBitmap;
    private Canvas iconCanvas;

    private float crossOffset;

    Drawable rippleDrawable;

    private Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean checkable;
    private boolean checked;

    public VoIPToggleButton(@NonNull Context context) {
        super(context);
        setWillNotDraw(false);

        for (int i = 0; i < 2; i++) {
            TextView textView = new TextView(context);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
            textView.setTextSize(11f);
            textView.setTextColor(Color.WHITE);
            textView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 58, 0, 0));
            this.textView[i] = textView;
        }
        textView[1].setVisibility(View.GONE);

        xRefPaint.setColor(0xff000000);
        xRefPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        xRefPaint.setStrokeWidth(AndroidUtilities.dp(3));

        crossPaint.setStrokeWidth(AndroidUtilities.dp(2));
        crossPaint.setStrokeCap(Paint.Cap.ROUND);

        rippleDrawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(52), 0, Color.BLACK);
        rippleDrawable.setCallback(this);

        bitmapPaint.setFilterBitmap(true);
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onDraw(Canvas canvas) {
        if (replaceProgress != 0) {
            circlePaint.setColor(ColorUtils.blendARGB(backgroundColor, animateToBackgroundColor, replaceProgress));
        } else {
            circlePaint.setColor(backgroundColor);
        }

        float cx = getWidth() / 2f;
        float cy = AndroidUtilities.dp(52) / 2f;
        float radius = AndroidUtilities.dp(52) / 2f;
        canvas.drawCircle(cx, cy, AndroidUtilities.dp(52) / 2f, circlePaint);
        rippleDrawable.setBounds((int) (cx - radius), (int) (cy - radius), (int) (cx + radius), (int) (cy + radius));
        rippleDrawable.draw(canvas);

        if (drawCross || crossProgress != 0) {
            if (iconChangeColor) {
                int color = ColorUtils.blendARGB(replaceColorFrom, currentIconColor, replaceProgress);
                icon[0].setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                crossPaint.setColor(color);
            }
            icon[0].setAlpha(255);

            if (iconBitmap == null) {
                iconBitmap = Bitmap.createBitmap(AndroidUtilities.dp(32), AndroidUtilities.dp(32), Bitmap.Config.ARGB_8888);
                iconCanvas = new Canvas(iconBitmap);
            } else {
                iconBitmap.eraseColor(Color.TRANSPARENT);
            }
            float x = iconBitmap.getWidth() >> 1;
            float y = iconBitmap.getHeight() >> 1;
            if (replaceProgress != 0 && iconChangeColor) {
                int color = ColorUtils.blendARGB(replaceColorFrom, currentIconColor, replaceProgress);
                icon[0].setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                crossPaint.setColor(color);
            }
            icon[0].setAlpha(255);

            if (drawCross && crossProgress < 1f) {
                crossProgress += 0.08f;
                if (crossProgress > 1f) {
                    crossProgress = 1f;
                } else {
                    invalidate();
                }
            } else if (!drawCross) {
                crossProgress -= 0.08f;
                if (crossProgress < 0) {
                    crossProgress = 0;
                } else {
                    invalidate();
                }
            }
            if (crossProgress > 0) {
                int left = (int) (x - icon[0].getIntrinsicWidth() / 2f);
                int top = (int) (x - icon[0].getIntrinsicHeight() / 2);

                float startX = left + AndroidUtilities.dpf2(8) + crossOffset;
                float startY = top + AndroidUtilities.dpf2(8);

                float endX = startX - AndroidUtilities.dp(1) + AndroidUtilities.dp(17) * CubicBezierInterpolator.DEFAULT.getInterpolation(crossProgress);
                float endY = startY + AndroidUtilities.dp(17) * CubicBezierInterpolator.DEFAULT.getInterpolation(crossProgress);

                icon[0].setBounds(
                        (int) (x - icon[0].getIntrinsicWidth() / 2f), (int) (y - icon[0].getIntrinsicHeight() / 2),
                        (int) (x + icon[0].getIntrinsicWidth() / 2), (int) (y + icon[0].getIntrinsicHeight() / 2)
                );
                icon[0].draw(iconCanvas);

                iconCanvas.drawLine(startX, startY - AndroidUtilities.dp(2f), endX, endY - AndroidUtilities.dp(2f), xRefPaint);
                iconCanvas.drawLine(startX, startY, endX, endY, crossPaint);
                canvas.drawBitmap(iconBitmap, cx - x, cy - y, bitmapPaint);
            } else {
                icon[0].setBounds(
                        (int) (cx - icon[0].getIntrinsicWidth() / 2f), (int) (cy - icon[0].getIntrinsicHeight() / 2),
                        (int) (cx + icon[0].getIntrinsicWidth() / 2), (int) (cy + icon[0].getIntrinsicHeight() / 2)
                );
                icon[0].draw(canvas);
            }
        } else {
            for (int i = 0; i < ((replaceProgress == 0 || iconChangeColor) ? 1 : 2); i++) {
                if (icon[i] != null) {
                    canvas.save();
                    if (replaceProgress != 0 && !iconChangeColor) {
                        float p = i == 0 ? 1f - replaceProgress : replaceProgress;
                        canvas.scale(p, p, cx, cy);
                        icon[i].setAlpha((int) (255 * p));
                    } else {
                        if (iconChangeColor) {
                            int color = ColorUtils.blendARGB(replaceColorFrom, currentIconColor, replaceProgress);
                            icon[i].setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                            crossPaint.setColor(color);
                        }
                        icon[i].setAlpha(255);
                    }
                    icon[i].setBounds(
                            (int) (cx - icon[i].getIntrinsicWidth() / 2f), (int) (cy - icon[i].getIntrinsicHeight() / 2),
                            (int) (cx + icon[i].getIntrinsicWidth() / 2), (int) (cy + icon[i].getIntrinsicHeight() / 2)
                    );
                    icon[i].draw(canvas);

                    canvas.restore();
                }
            }
        }
    }

    public void setData(int iconRes, int iconColor, int backgroundColor, String text, boolean cross, boolean animated) {
        if (getVisibility() != View.VISIBLE) {
            animated = false;
            setVisibility(View.VISIBLE);
        }

        if (currentIconRes == iconRes && currentIconColor == iconColor && currentBackgroundColor == backgroundColor && (currentText != null && currentText.equals(text))) {
            return;
        }

        if (Color.alpha(backgroundColor) == 255 && AndroidUtilities.computePerceivedBrightness(backgroundColor) > 0.5) {
            rippleDrawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(52), 0, ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.1f)));
            rippleDrawable.setCallback(this);
        } else {
            rippleDrawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(52), 0, ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.3f)));
            rippleDrawable.setCallback(this);
        }

        iconChangeColor = currentIconRes == iconRes;
        if (iconChangeColor) {
            replaceColorFrom = currentIconColor;
        }
        currentIconRes = iconRes;
        currentIconColor = iconColor;
        currentBackgroundColor = backgroundColor;
        currentText = text;
        drawCross = cross;

        if (replaceAnimator != null) {
            replaceAnimator.cancel();
        }

        if (!animated) {
            icon[0] = ContextCompat.getDrawable(getContext(), iconRes).mutate();
            icon[0].setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.MULTIPLY));
            crossPaint.setColor(iconColor);
            this.backgroundColor = backgroundColor;
            textView[0].setText(text);
            crossProgress = drawCross ? 1f : 0;
            iconChangeColor = false;
            replaceProgress = 0f;
            invalidate();
        } else {
            if (!iconChangeColor) {
                icon[1] = ContextCompat.getDrawable(getContext(), iconRes).mutate();
                icon[1].setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.MULTIPLY));
            }
            this.animateToBackgroundColor = backgroundColor;

            boolean animateText = !textView[0].getText().toString().equals(text);

            if (!animateText) {
                textView[0].setText(text);
            } else {
                textView[1].setText(text);
                textView[1].setVisibility(View.VISIBLE);
                textView[1].setAlpha(0);
                textView[1].setScaleX(0);
                textView[1].setScaleY(0);
            }
            replaceAnimator = ValueAnimator.ofFloat(0, 1f);
            replaceAnimator.addUpdateListener(valueAnimator -> {
                replaceProgress = (float) valueAnimator.getAnimatedValue();
                invalidate();

                if (animateText) {
                    textView[0].setAlpha(1f - replaceProgress);
                    textView[0].setScaleX(1f - replaceProgress);
                    textView[0].setScaleY(1f - replaceProgress);

                    textView[1].setAlpha(replaceProgress);
                    textView[1].setScaleX(replaceProgress);
                    textView[1].setScaleY(replaceProgress);
                }
            });
            replaceAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animateText) {
                        TextView tv = textView[0];
                        textView[0] = textView[1];
                        textView[1] = tv;
                        textView[1].setVisibility(View.GONE);
                    }

                    if (!iconChangeColor) {
                        icon[0] = icon[1];
                        icon[1] = null;
                    }
                    iconChangeColor = false;
                    VoIPToggleButton.this.backgroundColor = animateToBackgroundColor;
                    replaceProgress = 0f;
                    invalidate();
                }
            });
            replaceAnimator.setDuration(150).start();
            invalidate();
        }
    }

    public void setCrossOffset(float crossOffset) {
        this.crossOffset = crossOffset;
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        rippleDrawable.setState(getDrawableState());
    }

    @Override
    public boolean verifyDrawable(Drawable drawable) {
        return rippleDrawable == drawable || super.verifyDrawable(drawable);
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (rippleDrawable != null) {
            rippleDrawable.jumpToCurrentState();
        }
    }

    public void setCheckable(boolean checkable) {
        this.checkable = checkable;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setText(currentText);
        info.setClassName(Button.class.getName());
        if (checkable) {
            info.setCheckable(true);
            info.setChecked(checked);
        }
    }
}