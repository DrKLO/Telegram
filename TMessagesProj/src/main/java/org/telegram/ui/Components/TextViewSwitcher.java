package org.telegram.ui.Components;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ViewSwitcher;

public class TextViewSwitcher extends ViewSwitcher {

    public TextViewSwitcher(Context context) {
        super(context);
    }

    public void setText(CharSequence text) {
        setText(text, true);
    }

    public void setText(CharSequence text, boolean animated) {
        setText(text, animated, false);
    }

    public boolean setText(CharSequence text, boolean animated, boolean forceUpdate) {
        if (forceUpdate || !TextUtils.equals(text, getCurrentView().getText())) {
            if (animated) {
                getNextView().setText(text);
                showNext();
                return true;
            } else {
                getCurrentView().setText(text);
            }
        }
        return false;
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (!(child instanceof TextView)) {
            throw new IllegalArgumentException();
        }
        super.addView(child, index, params);
    }

    @Override
    public TextView getCurrentView() {
        return (TextView) super.getCurrentView();
    }

    @Override
    public TextView getNextView() {
        return (TextView) super.getNextView();
    }

    public void invalidateViews() {
        getCurrentView().invalidate();
        getNextView().invalidate();
    }
}
