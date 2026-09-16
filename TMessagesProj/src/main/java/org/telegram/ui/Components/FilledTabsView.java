package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;

public class FilledTabsView extends View {

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path selectedPath = new Path();

    private int textColor = 0xFFFFFFFF;
    private int selectedTextColor = 0xFFFFFFFF;

    private Text[] tabs;
    private RectF[] bounds;

    public FilledTabsView(Context context) {
        super(context);
    }

    public void setTabs(CharSequence ...texts) {
        tabs = new Text[texts.length];
        bounds = new RectF[texts.length];

        for (int i = 0; i < texts.length; ++i) {
            tabs[i] = new Text(texts[i], 14, AndroidUtilities.bold());
            bounds[i] = new RectF();
        }

        invalidate();
    }

    private float selectedTabIndex;

    public void setSelected(float tabIndex) {
        if (Math.abs(tabIndex - selectedTabIndex) > 0.001f) {
            invalidate();
        }
        selectedTabIndex = tabIndex;
    }

    private Utilities.Callback<Integer> onTabClick;
    public FilledTabsView onTabSelected(Utilities.Callback<Integer> onTabClick) {
        this.onTabClick = onTabClick;
        return this;
    }

    @Override
    public void setBackgroundColor(int color) {
        backgroundPaint.setColor(color);
        invalidate();
    }

    public void setSelectedColor(int color) {
        selectedPaint.setColor(color);
        invalidate();
    }

    public void setTextColor(int color) {
        textColor = color;
        invalidate();
    }

    public void setSelectedTextColor(int color) {
        selectedTextColor = color;
        invalidate();
    }

    public void setColors(int backgroundColor, int selectedColor, int textColor, int selectedTextColor) {
        backgroundPaint.setColor(backgroundColor);
        selectedPaint.setColor(selectedColor);
        this.textColor = textColor;
        this.selectedTextColor = selectedTextColor;
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (tabs == null) {
            return;
        }

        final int W = getWidth();
        final int H = getHeight();

        int w = dp(4) + tabs.length * dp(24) + dp(4);
        for (int i = 0; i < tabs.length; ++i)
            w += tabs[i].getWidth();

        float top = (H - dp(36)) / 2f, bottom = (H + dp(36)) / 2f;
        final float contentLeft = (W - w) / 2f;
        final float textLeft = contentLeft + dp(4 + 12);
        float x = contentLeft;

        AndroidUtilities.rectTmp.set(x, top, x + w, bottom);
        canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(18), dp(18), backgroundPaint);

        x += dp(4 + 12);
        for (int i = 0; i < tabs.length; ++i) {
            bounds[i].set(x - dp(4 + 12), top, x + tabs[i].getWidth() + dp(12 + 4), bottom);
            x += tabs[i].getWidth() + dp(12 + 12);
        }

        x = (W - w) / 2f + dp(4);
        top = (H - dp(36 - 8)) / 2f;
        bottom = (H + dp(36 - 8)) / 2f;

        final int l = Utilities.clamp((int) Math.floor(selectedTabIndex), tabs.length - 1, 0);
        final int r = Utilities.clamp((int) Math.ceil(selectedTabIndex), tabs.length - 1, 0);
        float left = AndroidUtilities.lerp(bounds[l].left + dp(4), bounds[r].left + dp(4), (float) (selectedTabIndex - Math.floor(selectedTabIndex)));
        float right = AndroidUtilities.lerp(bounds[l].right - dp(4), bounds[r].right - dp(4), (float) (selectedTabIndex - Math.floor(selectedTabIndex)));

        AndroidUtilities.rectTmp.set(left, top, right, bottom);
        selectedPath.rewind();
        selectedPath.addRoundRect(AndroidUtilities.rectTmp, dp(15), dp(15), Path.Direction.CW);
        canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(15), dp(15), selectedPaint);

        drawTabsText(canvas, textLeft, H, textColor);

        canvas.save();
        canvas.clipPath(selectedPath);
        drawTabsText(canvas, textLeft, H, selectedTextColor);
        canvas.restore();
    }

    private void drawTabsText(Canvas canvas, float x, int height, int color) {
        for (Text tab : tabs) {
            tab.draw(canvas, x, height / 2f, color, 1f);
            x += tab.getWidth() + dp(12 + 12);
        }
    }

    private int lastPressedIndex = -1;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (tabs == null || bounds == null) return false;
        int index = -1;
        for (int i = 0; i < bounds.length; ++i) {
            if (bounds[i].contains(event.getX(), event.getY())) {
                index = i;
                break;
            }
        }

        if (index >= 0 && index != lastPressedIndex) {
            lastPressedIndex = index;
            if (onTabClick != null) {
                onTabClick.run(index);
            }
        }
        if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            lastPressedIndex = -1;
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN && index >= 0) {
            return true;
        }
        return super.onTouchEvent(event);
    }
}
