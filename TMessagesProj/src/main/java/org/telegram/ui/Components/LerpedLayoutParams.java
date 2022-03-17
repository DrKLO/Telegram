package org.telegram.ui.Components;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.FrameLayout;

public class LerpedLayoutParams extends ViewGroup.MarginLayoutParams {

    private ViewGroup.LayoutParams from;
    private ViewGroup.LayoutParams to;
    public LerpedLayoutParams(
        ViewGroup.LayoutParams from,
        ViewGroup.LayoutParams to
    ) {
        super(from == null ? to : from);
        this.from = from;
        this.to = to;
    }

    public void apply(float t) {
        t = Math.min(Math.max(t, 0), 1);

        this.width = lerpSz(from.width, to.width, t);
        this.height = lerpSz(from.height, to.height, t);
        if (from instanceof ViewGroup.MarginLayoutParams && to instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginFrom = (ViewGroup.MarginLayoutParams) from;
            ViewGroup.MarginLayoutParams marginTo =   (ViewGroup.MarginLayoutParams) to;
            this.topMargin = lerp(marginFrom.topMargin, marginTo.topMargin, t);
            this.leftMargin = lerp(marginFrom.leftMargin, marginTo.leftMargin, t);
            this.rightMargin = lerp(marginFrom.rightMargin, marginTo.rightMargin, t);
            this.bottomMargin = lerp(marginFrom.bottomMargin, marginTo.bottomMargin, t);
        } else if (from instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginFrom = (ViewGroup.MarginLayoutParams) from;
            this.topMargin = marginFrom.topMargin;
            this.leftMargin = marginFrom.leftMargin;
            this.rightMargin = marginFrom.rightMargin;
            this.bottomMargin = marginFrom.bottomMargin;
        } else if (to instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginTo = (ViewGroup.MarginLayoutParams) to;
            this.topMargin = marginTo.topMargin;
            this.leftMargin = marginTo.leftMargin;
            this.rightMargin = marginTo.rightMargin;
            this.bottomMargin = marginTo.bottomMargin;
        }
    }

    private int lerp(int from, int to, float t) {
        return (int) (from + (to - from) * t);
    }
    private int lerpSz(int from, int to, float t) {
        if (from < 0 || to < 0) // MATCH_PARENT or WRAP_CONTENT
            return t < .5f ? from : to;
        return lerp(from, to, t);
    }
}
