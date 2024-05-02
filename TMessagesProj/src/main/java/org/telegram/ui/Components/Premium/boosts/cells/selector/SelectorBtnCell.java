package org.telegram.ui.Components.Premium.boosts.cells.selector;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.RecyclerListView;

@SuppressLint("ViewConstructor")
public class SelectorBtnCell extends LinearLayout {

    private final Theme.ResourcesProvider resourcesProvider;
    private final RecyclerListView listView;
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final AnimatedFloat alpha = new AnimatedFloat(this);

    public SelectorBtnCell(Context context, Theme.ResourcesProvider resourcesProvider, RecyclerListView listView) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.listView = listView;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        dividerPaint.setColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
        if (listView != null) {
            dividerPaint.setAlpha((int) (0xFF * alpha.set(listView.canScrollVertically(1) ? 1 : 0)));
        } else {
            dividerPaint.setAlpha((int) (0xFF * alpha.set(1)));
        }
        canvas.drawRect(0, 0, getWidth(), AndroidUtilities.getShadowHeight(), dividerPaint);
    }
}
