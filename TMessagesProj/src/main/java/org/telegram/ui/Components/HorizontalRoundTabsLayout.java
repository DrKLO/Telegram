package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesStorage;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

public class HorizontalRoundTabsLayout extends HorizontalScrollView {
    private final AnimatedFloat selectorStartX;
    private final AnimatedFloat selectorEndX;
    public final LinearLayout linearLayout;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    public HorizontalRoundTabsLayout(Context context) {
        super(context);

        linearLayout = new LinearLayout(context);
        linearLayout.setLayerType(View.LAYER_TYPE_NONE, null);
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);

        addView(linearLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.START));

        textPaint.setTextSize(dp(13));
        textPaint.setTypeface(AndroidUtilities.bold());

        selectorStartX = new AnimatedFloat(() -> {
            invalidate();
            linearLayout.invalidate();
            for (int a = 0; a < linearLayout.getChildCount(); a++) {
                linearLayout.getChildAt(a).invalidate();
            }
        });
        selectorStartX.setDuration(180);

        selectorEndX = new AnimatedFloat(() -> {
            invalidate();
            linearLayout.invalidate();
            for (int a = 0; a < linearLayout.getChildCount(); a++) {
                linearLayout.getChildAt(a).invalidate();
            }
        });
        selectorEndX.setDuration(180);

        setVerticalScrollBarEnabled(false);
        setHorizontalScrollBarEnabled(false);
    }

    public void setTabs(ArrayList<CharSequence> tabs, MessagesStorage.IntCallback onSelect) {
        linearLayout.removeAllViews();

        for (int a = 0; a < tabs.size(); a++) {
            CharSequence text = tabs.get(a);
            RoundTabView tabView = new RoundTabView(getContext());
            int finalA = a;
            tabView.setOnClickListener(v -> {
                selectedIndex = finalA;
                selectorStartX.set(v.getLeft(), false);
                selectorEndX.set(v.getRight(), false);
                onSelect.run(finalA);
            });

            tabView.setPadding(dp(12), dp(5), dp(12), dp(5));

            LinearLayout.LayoutParams lp = LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
            if (a < tabs.size() - 1) {
                lp.rightMargin = dp(4);
            }

            tabView.setText(new Text(text, textPaint));
            linearLayout.addView(tabView, lp);
        }
    }

    private int selectedIndex;

    public void setSelectedIndex(int selectedIndex, boolean animated) {
        this.selectedIndex = selectedIndex;

        selectorStartX.set(linearLayout.getChildAt(selectedIndex).getLeft(), !animated);
        selectorEndX.set(linearLayout.getChildAt(selectedIndex).getRight(), !animated);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        setSelectedIndex(selectedIndex, false);
    }

    private static final RectF tmpRect = new RectF();
    private final Path clipPath = new Path();
    private final Path clipPath2 = new Path();

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        tmpRect.set(
                selectorStartX.getValue(),
                0,
                selectorEndX.getValue(),
                getMeasuredHeight()
        );

        clipPath.rewind();
        clipPath.addRoundRect(tmpRect, dp(13), dp(13), Path.Direction.CW);
        clipPath.close();

        clipPath2.rewind();
        clipPath2.addRect(0, 0, linearLayout.getMeasuredWidth(), getMeasuredHeight(), Path.Direction.CW);
        clipPath2.addRoundRect(tmpRect, dp(13), dp(13), Path.Direction.CCW);
        clipPath2.close();

        bgPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText) & 0x1EFFFFFF);
        canvas.drawPath(clipPath, bgPaint);

        textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        canvas.save();
        canvas.clipPath(clipPath2);
        super.dispatchDraw(canvas);
        canvas.restore();

        textPaint.setColor(Theme.getColor(Theme.key_chats_nameArchived));
        canvas.save();
        canvas.clipPath(clipPath);
        for (int a = 0; a < linearLayout.getChildCount(); a++) {
            View v = linearLayout.getChildAt(a);
            if (tmpRect.right < v.getLeft() || tmpRect.left > v.getRight()) {
                continue;
            }

            canvas.save();
            canvas.translate(v.getLeft(), v.getTop());
            v.draw(canvas);
            canvas.restore();
        }
        canvas.restore();
    }

    private static class RoundTabView extends View {
        private Text text;

        public RoundTabView(Context context) {
            super(context);
            setDrawingCacheEnabled(false);
        }

        public void setText(Text text) {
            this.text = text;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(Math.round(text.getWidth()) + getPaddingLeft() + getPaddingRight(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.max(Math.round(text.getHeight()) + getPaddingTop() + getPaddingBottom(), dp(26)), MeasureSpec.EXACTLY));
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            super.draw(canvas);
            text.draw(canvas, (getMeasuredWidth() - text.getWidth()) / 2, getMeasuredHeight() / 2f);
        }
    }
}
