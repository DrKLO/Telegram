package org.telegram.ui.Components.chat.buttons;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.DrawableRes;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CounterView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;


@SuppressLint("ViewConstructor")
public class ChatActivityBlurredRoundPageDownButton extends FrameLayout {
    private final Theme.ResourcesProvider resourcesProvider;

    private ChatActivityBlurredRoundButton buttonView;
    private CounterView counterView;

    public ChatActivityBlurredRoundPageDownButton(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
    }

    public void addButtonView(ChatActivityBlurredRoundButton button) {
        this.buttonView = button;
        addView(button, LayoutHelper.createFrame(56, 56, Gravity.BOTTOM));
        button.setIconPadding(dp(2));
    }


    private boolean reversedCounter;

    public void reverseCounter() {
        reversedCounter = true;
        if (counterView != null) {
            counterView.setReverse(true);
        }
    }

    public void setCount(int count, boolean animated) {
        if (counterView == null) {
            counterView = new CounterView(getContext(), resourcesProvider);
            counterView.setReverse(reversedCounter);
            addView(counterView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 28, Gravity.TOP));
        }

        counterView.setCount(count, animated);
    }





    public void showLoading(boolean loading, boolean animated) {
        buttonView.showLoading(loading, animated);
    }

    @Override
    public void setEnabled(boolean enabled) {
        setEnabled(enabled, false);
    }

    public void setEnabled(boolean enabled, boolean animated) {
        super.setEnabled(enabled);
        buttonView.setEnabled(enabled, animated);
    }

    public void reverseIconByY() {
        buttonView.reverseIconByY();
    }

    public void updateColors() {
        if (buttonView != null) {
            buttonView.updateColors();
            invalidate();
        }
    }

    public static ChatActivityBlurredRoundPageDownButton create(
            Context context,
            Theme.ResourcesProvider resourcesProvider,
            BlurredBackgroundDrawableViewFactory factory,
            BlurredBackgroundColorProvider colorProvider,
            @DrawableRes int res
    ) {
        ChatActivityBlurredRoundPageDownButton button = new ChatActivityBlurredRoundPageDownButton(context, resourcesProvider);
        button.addButtonView(ChatActivityBlurredRoundButton.create(
            context, factory, colorProvider, resourcesProvider, res
        ));
        ScaleStateListAnimator.apply(button, .13f, 2f);

        return button;
    }
}
