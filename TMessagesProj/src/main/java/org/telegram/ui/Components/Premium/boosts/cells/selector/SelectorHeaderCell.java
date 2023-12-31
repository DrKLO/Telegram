package org.telegram.ui.Components.Premium.boosts.cells.selector;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

@SuppressLint("ViewConstructor")
public class SelectorHeaderCell extends FrameLayout {

    private final Theme.ResourcesProvider resourcesProvider;

    private final ImageView closeView;
    private final TextView textView;
    private Runnable onCloseClickListener;
    public BackDrawable backDrawable;

    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public SelectorHeaderCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        textView = new TextView(context);
        textView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        textView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL, LocaleController.isRTL ? 16 : 53, 0, LocaleController.isRTL ? 53 : 16, 0));

        closeView = new ImageView(context);
        closeView.setImageDrawable(backDrawable = new BackDrawable(false));
        backDrawable.setColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        backDrawable.setRotatedColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        backDrawable.setAnimationTime(220);
        addView(closeView, LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 16, 0, 16, 0));
        closeView.setOnClickListener(e -> {
            if (onCloseClickListener != null) {
                onCloseClickListener.run();
            }
        });
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        dividerPaint.setColor(Theme.getColor(Theme.key_divider, resourcesProvider));
        canvas.drawRect(0, getHeight() - AndroidUtilities.getShadowHeight(), getWidth(), getHeight(), dividerPaint);
    }

    public void setText(CharSequence text) {
        textView.setText(text);
    }

    public void setCloseImageVisible(boolean visible) {
        closeView.setVisibility(visible ? View.VISIBLE : View.GONE);
        textView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL, LocaleController.isRTL || !visible ? 22 : 53, 0, LocaleController.isRTL && visible ? 53 : 22, 0));
    }

    public void setBackImage(int resId) {
        closeView.setImageResource(resId);
    }

    public void setOnCloseClickListener(Runnable onCloseClickListener) {
        this.onCloseClickListener = onCloseClickListener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(getHeaderHeight(), MeasureSpec.EXACTLY)
        );
    }

    protected int getHeaderHeight() {
        return dp(56);
    }
}
