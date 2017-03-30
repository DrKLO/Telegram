package org.telegram.ui.Components;

import android.content.Context;
import android.text.Layout;
import android.util.AttributeSet;
import android.widget.TextView;

public class CorrectlyMeasuringTextView extends TextView{

    public CorrectlyMeasuringTextView(Context context) {
        super(context);
    }

    public CorrectlyMeasuringTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CorrectlyMeasuringTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void onMeasure(int wms, int hms) {
        super.onMeasure(wms, hms);
        try {
            Layout l = getLayout();
            if (l.getLineCount() <= 1) return;
            int maxw = 0;
            for (int i = l.getLineCount() - 1; i >= 0; --i) {
                maxw = Math.max(maxw, Math.round(l.getPaint().measureText(getText(), l.getLineStart(i), l.getLineEnd(i))));
            }
            super.onMeasure(Math.min(maxw + getPaddingLeft() + getPaddingRight(), getMeasuredWidth()) | MeasureSpec.EXACTLY, getMeasuredHeight() | MeasureSpec.EXACTLY);
        } catch (Exception x) {
        }
    }
}