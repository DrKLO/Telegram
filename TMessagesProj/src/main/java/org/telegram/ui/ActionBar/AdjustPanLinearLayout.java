package org.telegram.ui.ActionBar;

import android.content.Context;
import android.graphics.Canvas;
import android.widget.LinearLayout;

public class AdjustPanLinearLayout extends LinearLayout {

    private AdjustPanLayoutHelper adjustPanLayoutHelper;

    public AdjustPanLinearLayout(Context context) {
        super(context);
        adjustPanLayoutHelper = new AdjustPanLayoutHelper(this) {
            @Override
            protected void onPanTranslationUpdate(int y) {
                AdjustPanLinearLayout.this.onPanTranslationUpdate(y);
            }
        };
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        adjustPanLayoutHelper.update();
        super.dispatchDraw(canvas);
    }

    protected void onPanTranslationUpdate(int y) {

    }
}
