package org.telegram.ui.Components;

import android.content.Context;
import android.text.Layout;

public class CorrectlyMeasuringTextView extends PersianTextView {

    public CorrectlyMeasuringTextView(Context context) {
        super(context);
    }

    public void onMeasure(int wms, int hms) {
        super.onMeasure(wms, hms);
        try {
            Layout l = getLayout();
            if (l.getLineCount() <= 1) {
                return;
            }
            int maxw = 0;
            for (int i = l.getLineCount() - 1; i >= 0; --i) {
                maxw = Math.max(maxw, (int) Math.ceil(l.getLineWidth(i)));
            }
            super.onMeasure(Math.min(maxw + getPaddingLeft() + getPaddingRight(), getMeasuredWidth()) | MeasureSpec.EXACTLY, getMeasuredHeight() | MeasureSpec.EXACTLY);
        } catch (Exception ignore) {

        }
    }
}
