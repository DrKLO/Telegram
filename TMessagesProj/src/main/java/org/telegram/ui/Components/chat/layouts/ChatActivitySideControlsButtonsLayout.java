package org.telegram.ui.Components.chat.layouts;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.ui.Components.chat.buttons.ChatActivityBlurredRoundPageDownButton;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

@SuppressLint("ViewConstructor")
public class ChatActivitySideControlsButtonsLayout extends FrameLayout implements FactorAnimator.Target {
    public static final int BUTTON_PAGE_DOWN = 0;
    public static final int BUTTON_MENTION = 1;
    public static final int BUTTON_REACTIONS = 2;
    public static final int BUTTON_SEARCH_DOWN = 3;
    public static final int BUTTON_SEARCH_UP = 4;

    private static final int BUTTONS_COUNT = 5;

    private static final int VISIBILITY_ANIMATOR_ID = 1;

    private static final @DrawableRes int[] buttonIcons = new int[] {
        R.drawable.pagedown,
        R.drawable.mentionbutton,
        R.drawable.reactionbutton,
        R.drawable.pagedown,
        R.drawable.pagedown
    };

    private final String[] buttonDescriptions = new String[] {
        LocaleController.getString(R.string.AccDescrPageDown),
        LocaleController.getString(R.string.AccDescrMentionDown),
        LocaleController.getString(R.string.AccDescrReactionMentionDown),
        LocaleController.getString(R.string.AccDescrSearchPrev),
        LocaleController.getString(R.string.AccDescrSearchNext)
    };

    private final Theme.ResourcesProvider resourcesProvider;
    private final BlurredBackgroundColorProvider colorProvider;
    private final BlurredBackgroundDrawableViewFactory blurredBackgroundDrawableViewFactory;
    private final ButtonHolder[] buttonHolders = new ButtonHolder[BUTTONS_COUNT];

    private ButtonOnClickListener onClickListener;
    private ButtonOnLongClickListener onLongClickListener;

    public ChatActivitySideControlsButtonsLayout(@NonNull Context context,
                                            Theme.ResourcesProvider resourcesProvider,
                                            BlurredBackgroundColorProvider colorProvider,
                                            BlurredBackgroundDrawableViewFactory blurredBackgroundDrawableViewFactory) {
        super(context);
        this.blurredBackgroundDrawableViewFactory = blurredBackgroundDrawableViewFactory;
        this.colorProvider = colorProvider;
        this.resourcesProvider = resourcesProvider;
    }

    public void setOnClickListener(ButtonOnClickListener onClickListener) {
        this.onClickListener = onClickListener;
    }

    public void setOnLongClickListener(ButtonOnLongClickListener onLongClickListener) {
        this.onLongClickListener = onLongClickListener;
    }

    public boolean getButtonLocationInWindow(final int buttonId, int[] loc) {
        final ButtonHolder holder = buttonHolders[buttonId];
        if (holder != null) {
            holder.button.getLocationInWindow(loc);
            return true;
        }
        return false;
    }

    public void updateColors() {
        for (ButtonHolder holder : buttonHolders) {
            if (holder != null) {
                holder.button.updateColors();
            }
        }
    }

    public void showButton(final int buttonId, boolean show, boolean animated) {
        if (buttonHolders[buttonId] == null && !show) {
            return;
        }

        final ButtonHolder holder = getOrCreateButtonHolder(buttonId);
        holder.visibilityAnimator.setValue(show, animated);
    }

    public void setButtonCount(final int buttonId, int count, boolean animated) {
        final ButtonHolder holder = getOrCreateButtonHolder(buttonId);
        holder.button.setCount(count, animated);
    }

    public void setButtonLoading(final int buttonId, boolean loading, boolean animated) {
        final ButtonHolder holder = getOrCreateButtonHolder(buttonId);
        holder.button.showLoading(loading, animated);
    }

    public boolean isButtonVisible(final int buttonId) {
        final ButtonHolder holder = getButtonHolder(buttonId);

        return holder != null && holder.visibilityAnimator.getValue();
    }

    public void setButtonEnabled(final int buttonId, boolean enabled, boolean animated) {
        ButtonHolder holder = getOrCreateButtonHolder(buttonId);
        holder.button.setEnabled(enabled, animated);
    }


    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        final int buttonId = id >> 16;
        final int animatorId = id & 0xFFFF;
        if (buttonId < 0 || buttonId >= buttonHolders.length || buttonHolders[buttonId] == null) {
            return;
        }

        if (animatorId == VISIBILITY_ANIMATOR_ID) {
            checkButtonsPositionsAndVisibility();
        }
    }

    private void checkButtonsPositionsAndVisibility() {
        float totalHeight = 0;

        for (int buttonId = 0; buttonId < buttonHolders.length; buttonId++) {
            ButtonHolder holder = buttonHolders[buttonId];

            if (holder == null) {
                continue;
            }

            final float visibility = holder.visibilityAnimator.getFloatValue();
            holder.button.setVisibility(visibility > 0 ? VISIBLE : GONE);
            holder.button.setAlpha(visibility);
            holder.button.setScaleX(lerp(0.7f, 1f, visibility));
            holder.button.setScaleY(lerp(0.7f, 1f, visibility));
            holder.button.setTranslationY(dp(100) * (1f - visibility) - totalHeight);

            final int height = dp(44);
            final int gap = dp(buttonId == BUTTON_SEARCH_UP || buttonId == BUTTON_SEARCH_DOWN ? 10 : 16);

            totalHeight += (height + gap) * visibility;
        }
    }



    @Nullable
    private ButtonHolder getButtonHolder(final int buttonId) {
        if (buttonId < 0 || buttonId >= buttonHolders.length) {
            return null;
        }

        return buttonHolders[buttonId];
    }

    private ButtonHolder getOrCreateButtonHolder(final int buttonId) {
        if (buttonHolders[buttonId] == null) {

            final int animatorId = (buttonId << 16) | VISIBILITY_ANIMATOR_ID;
            final BoolAnimator visibilityAnimator = new BoolAnimator(animatorId, this,
                    AnimatorUtils.DECELERATE_INTERPOLATOR, 280);

            final ChatActivityBlurredRoundPageDownButton button = ChatActivityBlurredRoundPageDownButton.create(getContext(),
                    resourcesProvider, blurredBackgroundDrawableViewFactory, colorProvider, buttonIcons[buttonId]);

            button.setPivotX(dp(56 / 2f));
            button.setPivotY(dp(56 / 2f + 64 - 56));
            button.setVisibility(GONE);
            button.setContentDescription(buttonDescriptions[buttonId]);
            button.setOnClickListener(v -> {
                if (onClickListener != null) {
                    onClickListener.onClick(buttonId, v);
                }
            });
            button.setOnLongClickListener(v -> {
                if (onLongClickListener != null) {
                    return onLongClickListener.onLongClick(buttonId, v);
                }
                return false;
            });

            if (buttonId == BUTTON_SEARCH_UP) {
                button.reverseIconByY();
            }
            if (buttonId == BUTTON_PAGE_DOWN) {
                button.reverseCounter();
            }

            addView(button, LayoutHelper.createFrame(56, 64, Gravity.LEFT | Gravity.BOTTOM));

            buttonHolders[buttonId] = new ButtonHolder(button, visibilityAnimator);
            checkButtonsPositionsAndVisibility();
        }

        return buttonHolders[buttonId];
    }

    private static class ButtonHolder {
        public final ChatActivityBlurredRoundPageDownButton button;
        public final BoolAnimator visibilityAnimator;

        private ButtonHolder(ChatActivityBlurredRoundPageDownButton button, BoolAnimator visibilityAnimator) {
            this.button = button;
            this.visibilityAnimator = visibilityAnimator;
        }
    }
}
