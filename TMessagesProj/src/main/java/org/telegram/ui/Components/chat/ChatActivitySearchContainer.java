package org.telegram.ui.Components.chat;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.ui.Components.chat.layouts.ChatActivityFadeView;

@SuppressLint("ViewConstructor")
public class ChatActivitySearchContainer extends FrameLayout {
    private final ChatActivityFadeView fadeView;

    public ChatActivitySearchContainer(@NonNull Context context) {
        super(context);
        fadeView = new ChatActivityFadeView(context);
        addView(fadeView, LayoutHelper.createFrameMatchParent());
    }

    public void setFade(int top, int bottom) {
        fadeView.setFadeZoneTop(top);
        fadeView.setFadeZoneBottom(bottom);
    }

    public void setup(BlurredBackgroundDrawableViewFactory factory, BlurredBackgroundColorProvider colorProvider) {
        fadeView.setup(factory, colorProvider);
        fadeView.setIgnoreFastWay(true);
        fadeView.setFadeHeightTop(dp(48));
        fadeView.setFadeHeightBottom(dp(48));
    }

    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        bringChildToFront(fadeView);
    }

}
