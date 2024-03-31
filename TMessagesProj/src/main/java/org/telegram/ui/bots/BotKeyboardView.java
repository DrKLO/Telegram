/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.bots;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
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
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class BotKeyboardView extends LinearLayout {

    private final Theme.ResourcesProvider resourcesProvider;
    private LinearLayout container;
    private TLRPC.TL_replyKeyboardMarkup botButtons;
    private BotKeyboardViewDelegate delegate;
    private int panelHeight;
    private boolean isFullSize;
    private int buttonHeight;
    private ArrayList<TextView> buttonViews = new ArrayList<>();
    private ArrayList<ImageView> buttonIcons = new ArrayList<>();
    private ScrollView scrollView;

    public interface BotKeyboardViewDelegate {
        void didPressedButton(TLRPC.KeyboardButton button);
    }

    public BotKeyboardView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        setOrientation(VERTICAL);

        scrollView = new ScrollView(context);
        addView(scrollView);
        container = new LinearLayout(context);
        container.setOrientation(VERTICAL);
        scrollView.addView(container);
        updateColors();
    }

    public void updateColors() {
        AndroidUtilities.setScrollViewEdgeEffectColor(scrollView, getThemedColor(Theme.key_chat_emojiPanelBackground));
        setBackgroundColor(getThemedColor(Theme.key_chat_emojiPanelBackground));
        for (int i = 0; i < buttonViews.size(); i++) {
            buttonViews.get(i).setTextColor(getThemedColor(Theme.key_chat_botKeyboardButtonText));
            buttonViews.get(i).setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4), getThemedColor(Theme.key_chat_botKeyboardButtonBackground), getThemedColor(Theme.key_chat_botKeyboardButtonBackgroundPressed)));
            buttonIcons.get(i).setColorFilter(getThemedColor(Theme.key_chat_botKeyboardButtonText));
        }
        invalidate();
    }

    public void setDelegate(BotKeyboardViewDelegate botKeyboardViewDelegate) {
        delegate = botKeyboardViewDelegate;
    }

    public void setPanelHeight(int height) {
        panelHeight = height;
        if (isFullSize && botButtons != null && botButtons.rows.size() != 0) {
            buttonHeight = !isFullSize ? 42 : (int) Math.max(42, (panelHeight - AndroidUtilities.dp(30) - (botButtons.rows.size() - 1) * AndroidUtilities.dp(10)) / botButtons.rows.size() / AndroidUtilities.density);
            int count = container.getChildCount();
            int newHeight = AndroidUtilities.dp(buttonHeight);
            for (int a = 0; a < count; a++) {
                View v = container.getChildAt(a);
                LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) v.getLayoutParams();
                if (layoutParams.height != newHeight) {
                    layoutParams.height = newHeight;
                    v.setLayoutParams(layoutParams);
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
        container.removeAllViews();
        buttonViews.clear();
        buttonIcons.clear();
        scrollView.scrollTo(0, 0);

        if (buttons != null && botButtons.rows.size() != 0) {
            isFullSize = !buttons.resize;
            buttonHeight = !isFullSize ? 42 : (int) Math.max(42, (panelHeight - AndroidUtilities.dp(30) - (botButtons.rows.size() - 1) * AndroidUtilities.dp(10)) / botButtons.rows.size() / AndroidUtilities.density);
            for (int a = 0; a < buttons.rows.size(); a++) {
                TLRPC.TL_keyboardButtonRow row = buttons.rows.get(a);

                LinearLayout layout = new LinearLayout(getContext());
                layout.setOrientation(LinearLayout.HORIZONTAL);
                container.addView(layout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, buttonHeight, 15, a == 0 ? 15 : 10, 15, a == buttons.rows.size() - 1 ? 15 : 0));

                float weight = 1.0f / row.buttons.size();
                for (int b = 0; b < row.buttons.size(); b++) {
                    TLRPC.KeyboardButton button = row.buttons.get(b);
                    Button textView = new Button(getContext(), button);

                    FrameLayout frame = new FrameLayout(getContext());
                    frame.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

                    layout.addView(frame, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, weight, 0, 0, b != row.buttons.size() - 1 ? 10 : 0, 0));
                    textView.setOnClickListener(v -> delegate.didPressedButton((TLRPC.KeyboardButton) v.getTag()));
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
        }
    }

    private class Button extends TextView {
        public Button(Context context, TLRPC.KeyboardButton button) {
            super(context);

            setTag(button);
            setTextColor(getThemedColor(Theme.key_chat_botKeyboardButtonText));
            setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4), getThemedColor(Theme.key_chat_botKeyboardButtonBackground), getThemedColor(Theme.key_chat_botKeyboardButtonBackgroundPressed)));
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            setGravity(Gravity.CENTER);
            setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
            setText(Emoji.replaceEmoji(button.text, getPaint().getFontMetricsInt(), false));
        }
    }

    public int getKeyboardHeight() {
        if (botButtons == null) {
            return 0;
        }
        return isFullSize ? panelHeight : botButtons.rows.size() * AndroidUtilities.dp(buttonHeight) + AndroidUtilities.dp(30) + (botButtons.rows.size() - 1) * AndroidUtilities.dp(10);
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}
