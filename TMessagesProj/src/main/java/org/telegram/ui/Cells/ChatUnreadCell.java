/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import org.telegram.messenger.FileLog;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;

public class ChatUnreadCell extends FrameLayout implements Theme.Colorable {

    private TextView textView;
    private ImageView imageView;
    private FrameLayout backgroundLayout;
    private Theme.ResourcesProvider resourcesProvider;

    public ChatUnreadCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        backgroundLayout = new FrameLayout(context);
        backgroundLayout.setBackgroundResource(R.drawable.newmsg_divider);
        ThemeBind.drawableColorFilter(backgroundLayout, backgroundLayout.getBackground(), Theme.key_chat_unreadMessagesStartBackground, resourcesProvider, PorterDuff.Mode.MULTIPLY);
        addView(backgroundLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 27, Gravity.LEFT | Gravity.TOP, 0, 7, 0, 0));

        imageView = new ImageView(context);
        imageView.setImageResource(R.drawable.ic_ab_new);
        imageView.setColorFilter(new PorterDuffColorFilter(getColor(Theme.key_chat_unreadMessagesStartArrowIcon), PorterDuff.Mode.MULTIPLY));
        imageView.setPadding(0, AndroidUtilities.dp(2), 0, 0);
        backgroundLayout.addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

        textView = new TextView(context);
        textView.setPadding(0, 0, 0, AndroidUtilities.dp(1));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        ThemeBind.textColor(textView, Theme.isCompactMode(resourcesProvider) ? Theme.key_chat_compact_actionText : Theme.key_chat_unreadMessagesStartText, resourcesProvider);
        textView.setTypeface(AndroidUtilities.bold());
        addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 32, 0, 32, 0));

        if (Theme.isCompactMode(resourcesProvider)) {
            // compact mode: a flat strip like compact action cells, no divider shadow and arrow
            ThemeBind.fillBackground(backgroundLayout, Theme.createRoundRectDrawable(0, getColor(Theme.key_chat_compact_actionBackground)), ThemeBind.of(Theme.key_chat_compact_actionBackground), resourcesProvider);
            imageView.setVisibility(GONE);
        }
    }

    public void setText(String text) {
        textView.setText(text);
    }

    public ImageView getImageView() {
        return imageView;
    }

    public TextView getTextView() {
        return textView;
    }

    public FrameLayout getBackgroundLayout() {
        return backgroundLayout;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(40), MeasureSpec.EXACTLY));
    }

    private int getColor(int key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }

    private int themedArrowColor;

    @Override
    public void updateColors() {
        try {
            final int arrowColor = getColor(Theme.key_chat_unreadMessagesStartArrowIcon);
            if (imageView != null && arrowColor != themedArrowColor) {
                themedArrowColor = arrowColor;
                imageView.setColorFilter(new PorterDuffColorFilter(arrowColor, PorterDuff.Mode.MULTIPLY));
            }
            invalidate();
    
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
