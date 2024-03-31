package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class TextRightIconCell extends FrameLayout {
    private final ImageView ivIcon;
    private final SimpleTextView textView;
    private final Theme.ResourcesProvider resourcesProvider;
    private boolean needDivider;

    public TextRightIconCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        textView = new SimpleTextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        textView.setTextSize(16);
        textView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER_VERTICAL, 22, 0, 56, 0));

        ivIcon = new ImageView(context);
        ivIcon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText5, resourcesProvider), PorterDuff.Mode.SRC_IN));
        addView(ivIcon, LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | Gravity.END, 0, 0, 16, 0));
        setBackground(Theme.AdaptiveRipple.filledRectByKey(Theme.key_dialogBackground));
    }

    public void setTextAndIcon(CharSequence text, int iconRes) {
        textView.setText(text);
        ivIcon.setImageResource(iconRes);
    }

    public void setDivider(boolean needDivider) {
        this.needDivider = needDivider;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (needDivider) {
            Paint paint = resourcesProvider != null ? resourcesProvider.getPaint(Theme.key_paint_divider) : null;
            if (paint == null) {
                paint = Theme.dividerPaint;
            }
            canvas.drawLine(dp(22), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, paint);
        }
    }
}
