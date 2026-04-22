package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.R;

public class PhotoViewerPollAttachButtons extends LinearLayout {
    public final View editButton;
    public final View replaceButton;

    public PhotoViewerPollAttachButtons(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        setPadding(dp(3), dp(3), dp(3), dp(3));

        addView(replaceButton = createButton(R.drawable.msg_replace, getString(R.string.ReplaceAttachedPollMedia)),
            LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

        addView(editButton = createButton(R.drawable.media_button_restore, getString(R.string.Edit)),
            LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));
    }

    private View createButton(int iconRes, String text) {
        Context context = getContext();

        LinearLayout button = new LinearLayout(context);
        button.setOrientation(HORIZONTAL);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(25), dp(7), dp(25), dp(7));

        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        button.addView(icon, LayoutHelper.createLinear(24, 24, 0, 0, 8, 0));

        TextView tv = new TextView(context);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setSingleLine(true);
        tv.setTextColor(Color.WHITE);
        button.addView(tv, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        ScaleStateListAnimator.apply(button); //, 0.02f, 1.2f);
        return button;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final View child1 = editButton;
        final View child2 = replaceButton;

        ViewGroup.LayoutParams lp1 = child1.getLayoutParams();
        ViewGroup.LayoutParams lp2 = child1.getLayoutParams();

        final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        final int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        final int horizontalPadding = getPaddingLeft() + getPaddingRight();
        final int verticalPadding = getPaddingTop() + getPaddingBottom();
        final int availableWidth = Math.max(0, widthSize - horizontalPadding);
        final int availableHeight = Math.max(0, heightSize - verticalPadding);
        final int childHeightSpec = MeasureSpec.makeMeasureSpec(availableHeight, MeasureSpec.EXACTLY);
        final int naturalChildWidthSpec = MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST);
        child1.measure(naturalChildWidthSpec, childHeightSpec);
        child2.measure(naturalChildWidthSpec, childHeightSpec);
        final int naturalWidth1 = child1.getMeasuredWidth();
        final int naturalWidth2 = child2.getMeasuredWidth();
        final int equalWidth = Math.max(naturalWidth1, naturalWidth2);
        final int maxEqualWidthThatFits = availableWidth / 2;
        final int finalChildWidth = Math.min(equalWidth, maxEqualWidthThatFits);
        lp1.width = lp2.width = finalChildWidth;

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
