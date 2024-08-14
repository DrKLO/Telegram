package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Layout;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class PreviewButtons extends FrameLayout {

    public static final int BUTTON_PAINT = 0;
    public static final int BUTTON_TEXT = 1;
    public static final int BUTTON_STICKER = 2;
    public static final int BUTTON_ADJUST = 3;
    public static final int BUTTON_SHARE = 4;

    private View shadowView;

    private ArrayList<ButtonView> buttons = new ArrayList<>();
    public ShareButtonView shareButton;

    private String shareText;
    private boolean shareArrow = true;

    public PreviewButtons(Context context) {
        super(context);

        shadowView = new View(context);
        shadowView.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[] { 0x66000000, 0x00000000 }));
        addView(shadowView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        addButton(BUTTON_PAINT, R.drawable.media_draw, LocaleController.getString(R.string.AccDescrPaint));
        addButton(BUTTON_STICKER, R.drawable.msg_photo_sticker, LocaleController.getString(R.string.AccDescrStickers));
        addButton(BUTTON_TEXT, R.drawable.msg_photo_text2, LocaleController.getString(R.string.AccDescrPlaceText));
        addButton(BUTTON_ADJUST, R.drawable.msg_photo_settings, LocaleController.getString(R.string.AccDescrPhotoAdjust));

        shareButton = new ShareButtonView(context, shareText = LocaleController.getString(R.string.Send), shareArrow = true);
        shareButton.setContentDescription(LocaleController.getString(R.string.Send));
        addView(shareButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        updateAppearT();
    }

    public void setFiltersVisible(boolean visible) {
        for (int i = 0; i < buttons.size(); ++i) {
            ButtonView button = buttons.get(i);
            if (button.id == BUTTON_ADJUST) {
                button.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
        }
    }

    private boolean isFiltersVisible() {
        for (int i = 0; i < buttons.size(); ++i) {
            ButtonView button = buttons.get(i);
            if (button.id == BUTTON_ADJUST) {
                return button.getVisibility() == View.VISIBLE;
            }
        }
        return false;
    }

    public void setShareText(String text, boolean arrow) {
        if (TextUtils.equals(text, shareText) && arrow == shareArrow) {
            return;
        }
        removeView(shareButton);
        shareButton = new ShareButtonView(getContext(), shareText = text, shareArrow = arrow);
        shareButton.setContentDescription(text);
        addView(shareButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        updateAppearT();
    }

    private void addButton(int id, int resId, CharSequence contentDescription) {
        ButtonView btn = new ButtonView(getContext(), id, resId);
        btn.setContentDescription(contentDescription);
        buttons.add(btn);
        addView(btn);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int w = right - left;
        final int h = bottom - top;

        shadowView.layout(0, 0, w, h);
        shareButton.layout(w - shareButton.getMeasuredWidth(), (h - shareButton.getMeasuredHeight()) / 2, w, (h + shareButton.getMeasuredHeight()) / 2);

        int W = w - dp(10 + 10 + 12.33f) - shareButton.getMeasuredWidth();
        int visibleButtons = 0;
        for (int i = 0; i < buttons.size(); ++i) {
            ButtonView button = buttons.get(i);
            if (button.getVisibility() == View.VISIBLE) {
                visibleButtons++;
            }
        }
        int maxPossibleMargin = visibleButtons < 2 ? 0 : (W - visibleButtons * dp(40)) / (visibleButtons - 1);
        int margin = Math.min(dp(isFiltersVisible() ? 20 : 30), maxPossibleMargin);

        int t = (h - dp(40)) / 2, b = (h + dp(40)) / 2;
        for (int i = 0, x = dp(12.33f) + (!isFiltersVisible() ? (W - visibleButtons * dp(40) - (visibleButtons - 1) * margin) / 2 : 0); i < buttons.size(); ++i) {
            if (buttons.get(i).getVisibility() != View.VISIBLE) continue;
            buttons.get(i).layout(x, t, x + dp(40), b);
            x += dp(40) + margin;
        }
    }

    private Utilities.Callback<Integer> onClickListener;
    public void setOnClickListener(Utilities.Callback<Integer> onClickListener) {
        this.onClickListener = onClickListener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(dp(52), MeasureSpec.EXACTLY)
        );
    }

    private boolean isShareEnabled = true;
    public void setShareEnabled(boolean enabled) {
        if (isShareEnabled != enabled) {
            isShareEnabled = enabled;
            shareButton.enabled = enabled;
            shareButton.invalidate();
        }
    }
    public boolean isShareEnabled() {
        return isShareEnabled;
    }

    private float appearT;
    private boolean appearing;
    private ValueAnimator appearAnimator;

    public void appear(boolean appear, boolean animated) {
        if (appearing == appear) {
            return;
        }
        if (appearAnimator != null) {
            appearAnimator.cancel();
        }
        appearing = appear;
        if (animated) {
            appearAnimator = ValueAnimator.ofFloat(appearT, appear ? 1 : 0);
            appearAnimator.addUpdateListener(anm -> {
                appearT = (float) anm.getAnimatedValue();
                updateAppearT();
            });
            if (appearing) {
                appearAnimator.setDuration(450);
                appearAnimator.setInterpolator(new LinearInterpolator());
            } else {
                appearAnimator.setDuration(350);
                appearAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            }
            appearAnimator.start();
        } else {
            appearT = appear ? 1 : 0;
            updateAppearT();
        }
    }

    private void updateAppearT() {
        shadowView.setAlpha(appearT);
        shadowView.setTranslationY((1f - appearT) * dp(16));
        for (int i = 1; i < getChildCount(); ++i) {
            View child = getChildAt(i);
            float t = appearT;
            if (appearing) {
                t = AndroidUtilities.cascade(appearT, i - 1, getChildCount() - 1, 3);
                t = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(t);
            }
            child.setAlpha(t);
            child.setTranslationY((1f - t) * dp(24));
        }
    }

    private class ShareButtonView extends View {

        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint buttonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint darkenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final StaticLayout staticLayout;
        private float left, width;

        private int w, h;
        private boolean arrow;

        private AnimatedFloat enabledT = new AnimatedFloat(this, 0, 220, CubicBezierInterpolator.EASE_OUT_QUINT);
        public boolean enabled = true;

        public ShareButtonView(Context context, String text, boolean withArrow) {
            super(context);
            this.arrow = withArrow;

//            buttonPaint.setColor(0xffffffff);
            buttonPaint.setColor(0xff199cff);
            darkenPaint.setColor(0x60000000);

            textPaint.setTextSize(dp(13));
            textPaint.setColor(0xffffffff);
            textPaint.setTypeface(AndroidUtilities.bold());
//            textPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

            CharSequence text2;
            if (withArrow) {
                SpannableString arrow = new SpannableString(">");
                Drawable arrowDrawable = getResources().getDrawable(R.drawable.attach_arrow_right).mutate();
                arrowDrawable.setColorFilter(new PorterDuffColorFilter(0xffffffff, PorterDuff.Mode.SRC_IN));
                arrowDrawable.setBounds(0, 0, dp(12), dp(12));
                arrow.setSpan(new ImageSpan(arrowDrawable, ImageSpan.ALIGN_CENTER), 0, arrow.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                if (LocaleController.isRTL) {
                    text2 = new SpannableStringBuilder(arrow).append(" ").append(text.toUpperCase());
                } else {
                    text2 = new SpannableStringBuilder(text.toUpperCase()).append(" ").append(arrow);
                }
            } else {
                text2 = text.toUpperCase();
            }

            staticLayout = new StaticLayout(text2, textPaint, AndroidUtilities.dp(180), Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
            left = staticLayout.getLineCount() > 0 ? staticLayout.getLineLeft(0) : 0;
            width = staticLayout.getLineCount() > 0 ? staticLayout.getLineWidth(0) : 0;

            w = (int) width + AndroidUtilities.dp(16 + 16 + 16);
            if (!withArrow) {
                w = Math.max(dp(80), w);
            }
            h = AndroidUtilities.dp(32 + 8);

            setOnClickListener(e -> {
                if (appearing && onClickListener != null) {
                    onClickListener.run(BUTTON_SHARE);
                }
            });
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (isPressed() && pressedProgress != 1f) {
                pressedProgress += (float) Math.min(40, 1000f / AndroidUtilities.screenRefreshRate) / 80f;
                pressedProgress = Utilities.clamp(pressedProgress, 1f, 0);
                invalidate();
            }

            float alpha = enabledT.set(enabled ? 1 : .5f);
            int restore = canvas.getSaveCount();
            if (alpha < 1) {
                canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) (0xFF * alpha), Canvas.ALL_SAVE_FLAG);
            }

            final float scale = 0.9f + 0.1f * (1f - pressedProgress);
            canvas.save();
            canvas.scale(scale, scale, getWidth() / 2f, getHeight() / 2f);

            canvas.drawRect(dp(25), dp(4), getWidth() - dp(25), getHeight() - dp(4), darkenPaint);

            canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
            AndroidUtilities.rectTmp.set(dp(10), dp(4), getWidth() - dp(10), getHeight() - dp(4));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(20), dp(20), buttonPaint);

            canvas.save();
            canvas.translate((w - width) / 2f + dp(arrow ? 3 : 0) - left, (getHeight() - staticLayout.getHeight()) / 2f);
            staticLayout.draw(canvas);
            canvas.restore();

            canvas.restoreToCount(restore);
        }

        float pressedProgress;
        ValueAnimator backAnimator;

        @Override
        public void setPressed(boolean pressed) {
            if (isPressed() != pressed) {
                super.setPressed(pressed);
                invalidate();
                if (pressed) {
                    if (backAnimator != null) {
                        backAnimator.removeAllListeners();
                        backAnimator.cancel();
                    }
                }
                if (!pressed && pressedProgress != 0) {
                    backAnimator = ValueAnimator.ofFloat(pressedProgress, 0);
                    backAnimator.addUpdateListener(animation -> {
                        pressedProgress = (float) animation.getAnimatedValue();
                        invalidate();
                    });
                    backAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            backAnimator = null;
                        }
                    });
                    backAnimator.setInterpolator(new OvershootInterpolator(1.5f));
                    backAnimator.setDuration(350);
                    backAnimator.start();
                }
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.widget.Button");
        }
    }

    private class ButtonView extends ImageView {
        public final int id;
        public ButtonView(Context context, int id, int resId) {
            super(context);
            this.id = id;

            setBackground(Theme.createSelectorDrawable(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR));
            setScaleType(ScaleType.CENTER);
            setImageResource(resId);
            setColorFilter(new PorterDuffColorFilter(0xffffffff, PorterDuff.Mode.MULTIPLY));

            setOnClickListener(e -> {
                if (appearing && onClickListener != null) {
                    onClickListener.run(id);
                }
            });
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(dp(40), dp(40));
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.widget.Button");
        }
    }
}
