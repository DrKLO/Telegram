package org.telegram.ui.Components.chat.layouts;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.ui.Components.chat.buttons.ChatActivityBlurredRoundButton;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

@SuppressLint("ViewConstructor")
public class ChatActivityChannelButtonsLayout extends FrameLayout implements FactorAnimator.Target {
    public static final int BUTTON_SEARCH = 0;
    public static final int BUTTON_GIFT = 1;
    public static final int BUTTON_DIRECT = 2;
    public static final int BUTTON_GIGA_GROUP_INFO = 3;
    private static final int BUTTONS_COUNT = 4;

    private final ButtonHolder[] buttonHolders = new ButtonHolder[BUTTONS_COUNT];
    private final OnClickListener[] onClickListeners = new OnClickListener[BUTTONS_COUNT];
    private final OnButtonFullyVisibleListener[] onButtonFullyVisible = new OnButtonFullyVisibleListener[BUTTONS_COUNT];
    private OnButtonsTotalWidthChanged onButtonsTotalWidthChanged;
    private final FrameLayout container;

    private static final @DrawableRes int[] buttonIcons = new int[] {
        R.drawable.msg_search,
        R.drawable.input_gift_s,
        R.drawable.input_message,
        R.drawable.msg_help
    };
    private static final int[] buttonsOrderLeft = new int[] {
        BUTTON_SEARCH
    };
    private static final int[] buttonsOrderRight = new int[] {
        BUTTON_GIFT,
        BUTTON_DIRECT,
        BUTTON_GIGA_GROUP_INFO
    };

    private final Theme.ResourcesProvider resourcesProvider;
    private final BlurredBackgroundDrawableViewFactory blurredBackgroundDrawableViewFactory;
    private final BlurredBackgroundColorProvider colorProvider;

    public ChatActivityChannelButtonsLayout(@NonNull Context context,
                                            Theme.ResourcesProvider resourcesProvider,
                                            BlurredBackgroundColorProvider colorProvider,
                                            BlurredBackgroundDrawableViewFactory blurredBackgroundDrawableViewFactory) {
        super(context);
        this.blurredBackgroundDrawableViewFactory = blurredBackgroundDrawableViewFactory;
        this.colorProvider = colorProvider;
        this.resourcesProvider = resourcesProvider;

        container = new FrameLayout(context);
        container.setClipToOutline(true);
        container.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight(), dp(22));
            }
        });
        addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.CENTER_VERTICAL));
    }

    public void updateColors() {
        for (ButtonHolder holder : buttonHolders) {
            if (holder != null) {
                holder.button.updateColors();
            }
        }
    }

    public FrameLayout getContainer() {
        return container;
    }

    public void showButton(final int buttonId, boolean show, boolean animated) {
        if (buttonId < 0 || buttonId >= buttonHolders.length) {
            return;
        }

        if (buttonHolders[buttonId] == null && !show) {
            return;
        }

        if (buttonHolders[buttonId] == null) {
            final int animatorId = (buttonId << 16) | VISIBILITY_ANIMATOR_ID;
            final BoolAnimator visibilityAnimator = new BoolAnimator(animatorId, this,
                CubicBezierInterpolator.EASE_OUT_QUINT, 300);

            final ChatActivityBlurredRoundButton button = ChatActivityBlurredRoundButton.create(getContext(),
                blurredBackgroundDrawableViewFactory, colorProvider, resourcesProvider, buttonIcons[buttonId]);

            ScaleStateListAnimator.apply(button, .13f, 2f);
            button.setVisibility(GONE);
            button.setOnClickListener(v -> {
                if (onClickListeners[buttonId] != null) {
                    onClickListeners[buttonId].onClick(v);
                }
            });
            addView(button, LayoutHelper.createFrame(56, 56));

            buttonHolders[buttonId] = new ButtonHolder(button, visibilityAnimator);
            checkButtonsPositionsAndVisibility();
        }

        buttonHolders[buttonId].visibilityAnimator.setValue(show, animated);
    }

    public boolean isButtonVisible(final int buttonId) {
        if (buttonId < 0 || buttonId >= buttonHolders.length || buttonHolders[buttonId] == null) {
            return false;
        }

        return buttonHolders[buttonId].visibilityAnimator.getValue();
    }

    public void setButtonOnClickListener(int buttonId, View.OnClickListener listener) {
        this.onClickListeners[buttonId] = listener;
    }

    public void setButtonOnFullyVisibleListener(int buttonId, OnButtonFullyVisibleListener listener) {
        this.onButtonFullyVisible[buttonId] = listener;
    }

    public void setOnButtonsTotalWidthChanged(OnButtonsTotalWidthChanged onButtonsTotalWidthChanged) {
        this.onButtonsTotalWidthChanged = onButtonsTotalWidthChanged;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        checkContainerPaddings(false);

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        checkButtonsPositionsAndVisibility();
    }



    private static final int CENTER_ACCENT_BACKGROUND_ANIMATOR_ID = 99;
    private final BoolAnimator animatorCenterAccentBackground = new BoolAnimator(
        CENTER_ACCENT_BACKGROUND_ANIMATOR_ID, this, CubicBezierInterpolator.EASE_OUT_QUINT, 320L);

    public void setCenterAccentBackground(boolean accent, boolean animated) {
        animatorCenterAccentBackground.setValue(accent, animated);
    }

    private static final int VISIBILITY_ANIMATOR_ID = 1;

    private float totalVisibilityFactor;
    public void setTotalVisibilityFactor(float factor) {
        if (totalVisibilityFactor != factor) {
            totalVisibilityFactor = factor;
            checkButtonsPositionsAndVisibility();
            invalidate();
        }
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        if (id == CENTER_ACCENT_BACKGROUND_ANIMATOR_ID) {
            invalidate();
            return;
        }

        final int buttonId = id >> 16;
        final int animatorId = id & 0xFFFF;
        if (buttonId < 0 || buttonId >= buttonHolders.length || buttonHolders[buttonId] == null) {
            return;
        }

        if (animatorId == VISIBILITY_ANIMATOR_ID) {
            checkContainerPaddings(true);
            checkButtonsPositionsAndVisibility();
            invalidate();
        }
    }

    @Override
    public void onFactorChangeFinished(int id, float finalFactor, FactorAnimator callee) {
        if (id == CENTER_ACCENT_BACKGROUND_ANIMATOR_ID) {
            invalidate();
        }

        final int buttonId = id >> 16;
        final int animatorId = id & 0xFFFF;
        if (buttonId < 0 || buttonId >= buttonHolders.length || buttonHolders[buttonId] == null) {
            return;
        }

        final ButtonHolder holder = buttonHolders[buttonId];
        if (animatorId == VISIBILITY_ANIMATOR_ID) {
            if (holder.visibilityAnimator.getValue()) {
                if (onButtonFullyVisible[buttonId] != null) {
                    onButtonFullyVisible[buttonId].onButtonFullyVisible(holder.button, buttonId, !holder.wasShown);
                }
                holder.wasShown = true;
            }
        }
    }

    private float totalWidthLeft, totalWidthRight;

    private void checkContainerPaddings(boolean canRequestLayout) {
        int paddingLeft = dp(7), paddingRight = dp(7);
        for (final int buttonId : buttonsOrderLeft) {
            final ButtonHolder holder = buttonHolders[buttonId];
            if (holder == null) {
                continue;
            }
            paddingLeft += holder.visibilityAnimator.getValue() ? dp(44 + 10) : 0;
        }

        for (final int buttonId : buttonsOrderRight) {
            final ButtonHolder holder = buttonHolders[buttonId];
            if (holder == null) {
                continue;
            }
            paddingRight += holder.visibilityAnimator.getValue() ? dp(44 + 10) : 0;
        }

        final MarginLayoutParams lp = (MarginLayoutParams) container.getLayoutParams();

        if (lp.leftMargin != paddingLeft || lp.rightMargin != paddingRight) {
            lp.leftMargin = paddingLeft;
            lp.rightMargin = paddingRight;
            if (canRequestLayout) {
                container.requestLayout();
            }
        }
    }

    private void checkButtonsPositionsAndVisibility() {
        totalWidthLeft = 0;
        totalWidthRight = 0;

        for (final ButtonHolder holder: buttonHolders) {
            if (holder == null) {
                continue;
            }

            final float visibility = holder.visibilityAnimator.getFloatValue() * totalVisibilityFactor;
            holder.button.setVisibility(visibility > 0 ? VISIBLE : GONE);
            holder.button.setAlpha(visibility);
            holder.button.setScaleX(lerp(0.4f, 1f, visibility));
            holder.button.setScaleY(lerp(0.4f, 1f, visibility));
        }

        for (final int buttonId : buttonsOrderLeft) {
            final ButtonHolder holder = buttonHolders[buttonId];
            if (holder == null) {
                continue;
            }

            final float width = holder.visibilityAnimator.getFloatValue() * dp(44 + 10);    // width + margin
            holder.button.setTranslationX(dp(1) + totalWidthLeft);
            totalWidthLeft += width;
        }

        for (final int buttonId : buttonsOrderRight) {
            final ButtonHolder holder = buttonHolders[buttonId];
            if (holder == null) {
                continue;
            }

            final float width = holder.visibilityAnimator.getFloatValue() * dp(44 + 10);    // width + margin
            holder.button.setTranslationX(getMeasuredWidth() - holder.button.getMeasuredWidth() - dp(1) - totalWidthRight);
            totalWidthRight += width;
        }

        if (totalVisibilityFactor < 1) {
            for (final int buttonId : buttonsOrderLeft) {
                final ButtonHolder holder = buttonHolders[buttonId];
                if (holder == null) {
                    continue;
                }

                holder.button.setTranslationX(holder.button.getTranslationX() - totalWidthLeft * (1 - totalVisibilityFactor));
            }

            for (final int buttonId : buttonsOrderRight) {
                final ButtonHolder holder = buttonHolders[buttonId];
                if (holder == null) {
                    continue;
                }

                holder.button.setTranslationX(holder.button.getTranslationX() + totalWidthRight * (1 - totalVisibilityFactor));
            }

            totalWidthLeft *= totalVisibilityFactor;
            totalWidthRight *= totalVisibilityFactor;
        }

        if (onButtonsTotalWidthChanged != null) {
            onButtonsTotalWidthChanged.onButtonsTotalWidthChanged(totalWidthLeft, totalWidthRight);
        }
    }

    public interface OnButtonsTotalWidthChanged {
        void onButtonsTotalWidthChanged(float left, float right);
    }

    private static final RectF tmpRect = new RectF();
    private final Paint backgroundAccentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public interface OnButtonFullyVisibleListener {
        void onButtonFullyVisible(View v, int buttonId, boolean firstTime);
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        final int accentAlpha = (int) (255 * totalVisibilityFactor * animatorCenterAccentBackground.getFloatValue());
        if (accentAlpha > 0) {
            tmpRect.set(
                totalWidthLeft + dp(10),
                dp(9),
                getMeasuredWidth() - dp(10) - totalWidthRight,
                getMeasuredHeight() - dp(9)
            );
            backgroundAccentPaint.setColor(accentColor);
            backgroundAccentPaint.setAlpha(accentAlpha);
            canvas.drawRoundRect(tmpRect, dp(19), dp(19), backgroundAccentPaint);
        }

        super.dispatchDraw(canvas);
    }

    private int accentColor = 0;

    public void setAccentColor(int accentColor) {
        this.accentColor = accentColor;
    }

    private static class ButtonHolder {
        public final ChatActivityBlurredRoundButton button;
        public final BoolAnimator visibilityAnimator;
        public boolean wasShown;

        private ButtonHolder(ChatActivityBlurredRoundButton button, BoolAnimator visibilityAnimator) {
            this.button = button;
            this.visibilityAnimator = visibilityAnimator;
        }
    }
}
