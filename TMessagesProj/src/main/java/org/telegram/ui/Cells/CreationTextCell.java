package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;

public class CreationTextCell extends FrameLayout {

    private SimpleTextView textView;
    private ImageView imageView;
    boolean divider;
    public int startPadding;

    public CreationTextCell(Context context) {
        this(context, 70, null);
    }

    public CreationTextCell(Context context, int padding, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.startPadding = padding;

        textView = new SimpleTextView(context);
        textView.setTextSize(16);
        textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText2, resourcesProvider));
        textView.setTag(Theme.key_windowBackgroundWhiteBlueText2);
        addView(textView);

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        addView(imageView);
        setWillNotDraw(false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = AndroidUtilities.dp(48);

        textView.measure(MeasureSpec.makeMeasureSpec(width - AndroidUtilities.dp(71 + 23), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.EXACTLY));
        imageView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50), MeasureSpec.EXACTLY));
        setMeasuredDimension(width, AndroidUtilities.dp(50));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int height = bottom - top;
        int width = right - left;

        int viewLeft;
        int viewTop = (height - textView.getTextHeight()) / 2;
        if (LocaleController.isRTL) {
            viewLeft = getMeasuredWidth() - textView.getMeasuredWidth() - AndroidUtilities.dp(imageView.getVisibility() == VISIBLE ? startPadding : 25);
        } else {
            viewLeft = AndroidUtilities.dp(imageView.getVisibility() == VISIBLE ? startPadding : 25);
        }
        textView.layout(viewLeft, viewTop, viewLeft + textView.getMeasuredWidth(), viewTop + textView.getMeasuredHeight());

        viewLeft = !LocaleController.isRTL ? (AndroidUtilities.dp(startPadding) - imageView.getMeasuredWidth()) / 2 : width - imageView.getMeasuredWidth() - AndroidUtilities.dp(25);
        imageView.layout(viewLeft, 0, viewLeft + imageView.getMeasuredWidth(), imageView.getMeasuredHeight());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (divider) {
            canvas.drawLine(AndroidUtilities.dp(startPadding), getMeasuredHeight() - 1, getMeasuredWidth() + AndroidUtilities.dp(23), getMeasuredHeight(), Theme.dividerPaint);
        }
    }

    public void setTextAndIcon(String text, Drawable icon, boolean divider) {
        textView.setText(text);
        imageView.setImageDrawable(icon);
        this.divider = divider;
    }
}