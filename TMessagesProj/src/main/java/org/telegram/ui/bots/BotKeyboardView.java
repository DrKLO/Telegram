/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.bots;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FragmentContextView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.chat.ChatInputViewsContainer;
import org.telegram.ui.Components.inset.InAppKeyboardInsetView;

import java.util.ArrayList;

import me.vkryl.android.animator.ListAnimator;
import me.vkryl.android.animator.ReplaceAnimator;
import me.vkryl.core.lambda.Destroyable;

@SuppressLint("ViewConstructor")
public class BotKeyboardView extends LinearLayout implements InAppKeyboardInsetView, ReplaceAnimator.Callback {
    private static final int BORDER_MARGIN = 10;
    private static final int MIDDLE_MARGIN = 4;

    private final Theme.ResourcesProvider resourcesProvider;
    private final FrameLayout frameLayout;
    private TLRPC.TL_replyKeyboardMarkup botButtons;
    private BotKeyboardViewDelegate delegate;
    private int panelHeight;
    private boolean isFullSize;
    private int buttonHeight;
    private final ArrayList<TextView> buttonViews = new ArrayList<>();
    private final ArrayList<ImageView> buttonIcons = new ArrayList<>();
    private final ScrollView scrollView;

    public interface BotKeyboardViewDelegate {
        void didPressedButton(TLRPC.KeyboardButton button);
    }

    public BotKeyboardView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        setOrientation(VERTICAL);

        scrollView = new ScrollView(context);
        scrollView.setClipToPadding(false);
        addView(scrollView);

        frameLayout = new FrameLayout(context);
        scrollView.addView(frameLayout);
        updateColors();
    }

    public void updateColors() {
        AndroidUtilities.setScrollViewEdgeEffectColor(scrollView, getThemedColor(Theme.key_chat_emojiPanelBackground));
        // setBackgroundColor(getThemedColor(Theme.key_chat_emojiPanelBackground));
        for (int i = 0; i < buttonViews.size(); i++) {
            buttonViews.get(i).setTextColor(getThemedColor(Theme.key_chat_botKeyboardButtonText));
            buttonViews.get(i).setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(4), getThemedColor(Theme.key_chat_botKeyboardButtonBackground), getThemedColor(Theme.key_chat_botKeyboardButtonBackgroundPressed)));
            buttonIcons.get(i).setColorFilter(getThemedColor(Theme.key_chat_botKeyboardButtonText));
        }
        invalidate();
    }

    public void setDelegate(BotKeyboardViewDelegate botKeyboardViewDelegate) {
        delegate = botKeyboardViewDelegate;
    }

    public void setPanelHeight(int height) {
        panelHeight = height;
        if (isFullSize && botButtons != null && !botButtons.rows.isEmpty()) {
            buttonHeight = !isFullSize ? 42 : (int) Math.max(42, (panelHeight - dp(BORDER_MARGIN * 2) - (botButtons.rows.size() - 1) * dp(MIDDLE_MARGIN)) / botButtons.rows.size() / AndroidUtilities.density);
            final int newHeight = dp(buttonHeight);
            for (ListAnimator.Entry<ButtonsLayout> entry : animator) {
                for (int a = 0, N = entry.item.getChildCount(); a < N; a++) {
                    View v = entry.item.getChildAt(a);
                    LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) v.getLayoutParams();
                    if (layoutParams.height != newHeight) {
                        layoutParams.height = newHeight;
                        v.setLayoutParams(layoutParams);
                    }
                }
            }
        }
    }

    public void invalidateViews() {
        for (int a = 0; a < buttonViews.size(); a++) {
            buttonViews.get(a).invalidate();
            buttonIcons.get(a).invalidate();
        }
    }

    public boolean isFullSize() {
        return isFullSize;
    }

    public void setButtons(TLRPC.TL_replyKeyboardMarkup buttons) {
        botButtons = buttons;
        buttonViews.clear();
        buttonIcons.clear();

        final float offset = scrollView.getScrollY();
        for (ListAnimator.Entry<ButtonsLayout> entry : animator) {
            entry.item.setTranslationY(entry.item.getTranslationY() - offset);
        }

        scrollView.scrollTo(0, 0);

        if (buttons != null && !botButtons.rows.isEmpty()) {
            ButtonsLayout container = new ButtonsLayout(getContext());
            container.setOrientation(VERTICAL);
            container.setAlpha(0);
            frameLayout.addView(container);

            isFullSize = !buttons.resize;
            buttonHeight = !isFullSize ? 42 : (int) Math.max(42, (panelHeight - dp(BORDER_MARGIN * 2) - (botButtons.rows.size() - 1) * dp(MIDDLE_MARGIN)) / botButtons.rows.size() / AndroidUtilities.density);
            for (int a = 0; a < buttons.rows.size(); a++) {
                TLRPC.TL_keyboardButtonRow row = buttons.rows.get(a);

                LinearLayout layout = new LinearLayout(getContext());
                layout.setOrientation(LinearLayout.HORIZONTAL);
                container.addView(layout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, buttonHeight, BORDER_MARGIN, a == 0 ? BORDER_MARGIN : MIDDLE_MARGIN, BORDER_MARGIN, a == buttons.rows.size() - 1 ? BORDER_MARGIN : 0));

                float weight = 1.0f / row.buttons.size();
                for (int b = 0; b < row.buttons.size(); b++) {
                    TLRPC.KeyboardButton button = row.buttons.get(b);
                    Button textView = new Button(getContext(), button);
                    textView.setBackground(b == 0, a == 0, b == row.buttons.size() - 1, a == buttons.rows.size() - 1);

                    FrameLayout frame = new FrameLayout(getContext());
                    frame.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

                    layout.addView(frame, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, weight, 0, 0, b != row.buttons.size() - 1 ? MIDDLE_MARGIN : 0, 0));
                    textView.setOnClickListener(v -> delegate.didPressedButton((TLRPC.KeyboardButton) v.getTag()));
                    ScaleStateListAnimator.apply(textView, 0.02f, 1.5f);
                    buttonViews.add(textView);

                    ImageView icon = new ImageView(getContext());
                    icon.setColorFilter(getThemedColor(Theme.key_chat_botKeyboardButtonText));
                    if (button instanceof TLRPC.TL_keyboardButtonWebView || button instanceof TLRPC.TL_keyboardButtonSimpleWebView) {
                        icon.setImageResource(R.drawable.bot_webview);
                        icon.setVisibility(VISIBLE);
                    } else {
                        icon.setVisibility(GONE);
                    }
                    buttonIcons.add(icon);
                    frame.addView(icon, LayoutHelper.createFrame(12, 12, Gravity.RIGHT | Gravity.TOP, 0, 8, 8, 0));
                }
            }

            animator.replace(container, true);
        } else {
            animator.clear(true);
        }
    }

    private class Button extends TextView {
        public Button(Context context, TLRPC.KeyboardButton button) {
            super(context);

            setTag(button);
            setTextColor(getThemedColor(Theme.key_chat_botKeyboardButtonText));
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            setGravity(Gravity.CENTER);
            setPadding(dp(4), 0, dp(4), 0);
            setText(Emoji.replaceEmoji(button.text, getPaint().getFontMetricsInt(), false));
        }

        public void setBackground(boolean isLeft, boolean isTop, boolean isRight, boolean isBottom) {
            final int br = dp(ChatInputViewsContainer.INPUT_KEYBOARD_RADIUS - BORDER_MARGIN);
            final int dr = dp(4);

            setBackground(Theme.createSimpleSelectorRoundRectDrawable(
                isLeft && isTop ? br : dr,
                isRight && isTop ? br : dr,
                isRight && isBottom ? br : dr,
                isLeft && isBottom ? br : dr,
                getThemedColor(Theme.key_chat_botKeyboardButtonBackground),
                getThemedColor(Theme.key_chat_botKeyboardButtonBackgroundPressed),
                getThemedColor(Theme.key_chat_botKeyboardButtonBackgroundPressed)
            ));
        }

    }

    public int getKeyboardHeight() {
        if (botButtons == null) {
            return 0;
        }
        return isFullSize ? panelHeight : botButtons.rows.size() * dp(buttonHeight) + dp(BORDER_MARGIN * 2) + (botButtons.rows.size() - 1) * dp(MIDDLE_MARGIN);
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }



    @Override
    public void applyNavigationBarHeight(int height) {
        if (scrollView.getPaddingBottom() != height) {
            scrollView.setPadding(0, 0, 0, height);
        }
    }

    @Override
    public void applyInAppKeyboardAnimatedHeight(float height) {

    }



    private final ReplaceAnimator<ButtonsLayout> animator = new ReplaceAnimator<>(this, CubicBezierInterpolator.EASE_OUT_QUINT, 320);

    @Override
    public void onItemChanged(ReplaceAnimator<?> animato) {
        for (ListAnimator.Entry<ButtonsLayout> entry : animator) {
            final float visibility = entry.getVisibility();
            final float scale = lerp(0.7f, 1f, visibility);
            entry.item.setAlpha(visibility);
            entry.item.setScaleX(scale);
            entry.item.setScaleY(scale);
        }
    }

    private static class ButtonsLayout extends LinearLayout implements Destroyable {
        public ButtonsLayout(Context context) {
            super(context);
        }

        @Override
        public void performDestroy() {
            ((ViewGroup) getParent()).removeView(this);
        }
    }

}
