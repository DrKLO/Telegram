package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;

public class FilledTabsView extends View {

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Text[] tabs;
    private RectF[] bounds;

    public FilledTabsView(Context context) {
        super(context);

        selectedPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OUT));
        selectedPaint.setColor(0xFFFFFFFF);
    }

    public void setTabs(CharSequence ...texts) {
        tabs = new Text[texts.length];
        bounds = new RectF[texts.length];

        for (int i = 0; i < texts.length; ++i) {
            tabs[i] = new Text(texts[i], 14, AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
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

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (tabs == null) {
            return;
        }

        final int W = getWidth();
        final int H = getHeight();

        int w = dp(2) + tabs.length * dp(24) + dp(2);
        for (int i = 0; i < tabs.length; ++i)
            w += tabs[i].getWidth();

        float top = (H - dp(30)) / 2f, bottom = (H + dp(30)) / 2f;
        float x = (W - w) / 2f;

        AndroidUtilities.rectTmp.set(x, top, x + w, bottom);
        canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(15), dp(15), backgroundPaint);

        canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);

        x += dp(2 + 12);
        for (int i = 0; i < tabs.length; ++i) {
            tabs[i].draw(canvas, x, H / 2f, 0xFFFFFFFF, 1f);
            bounds[i].set(x - dp(2 + 12), top, x + tabs[i].getWidth() + dp(12 + 2), bottom);
            x += tabs[i].getWidth() + dp(12 + 12);
        }

        x = (W - w) / 2f + dp(2);
        top = (H - dp(30 - 4)) / 2f;
        bottom = (H + dp(30 - 4)) / 2f;

        final int l = Utilities.clamp((int) Math.floor(selectedTabIndex), tabs.length - 1, 0);
        final int r = Utilities.clamp((int) Math.ceil(selectedTabIndex), tabs.length - 1, 0);
        float left = AndroidUtilities.lerp(bounds[l].left + dp(2), bounds[r].left + dp(2), (float) (selectedTabIndex - Math.floor(selectedTabIndex)));
        float right = AndroidUtilities.lerp(bounds[l].right - dp(2), bounds[r].right - dp(2), (float) (selectedTabIndex - Math.floor(selectedTabIndex)));

        AndroidUtilities.rectTmp.set(left, top, right, bottom);
        canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(15), dp(15), selectedPaint);
        canvas.restore();
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
