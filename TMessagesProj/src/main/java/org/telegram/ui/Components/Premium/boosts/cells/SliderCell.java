package org.telegram.ui.Components.Premium.boosts.cells;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.boosts.BoostRepository;
import org.telegram.ui.Components.SlideChooseView;

import java.util.List;

@SuppressLint("ViewConstructor")
public class SliderCell extends FrameLayout {

    private final Theme.ResourcesProvider resourcesProvider;
    private final SlideChooseView slideChooseView;
    protected View backgroundView;

    public SliderCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        backgroundView = new View(context);
        addView(backgroundView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        backgroundView.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
        this.slideChooseView = new SlideChooseView(context, resourcesProvider);
        addView(slideChooseView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 0, 0, 0));
        setBackground(Theme.getThemedDrawableByKey(getContext(), R.drawable.greydivider_top, Theme.key_windowBackgroundGrayShadow));
    }

    public void setCallBack(SlideChooseView.Callback callback) {
        slideChooseView.setCallback(callback);
    }

    public void setValues(List<Integer> values, int selected) {
        String[] valuesStr = new String[values.size()];
        for (int i = 0; i < values.size(); i++) {
            Integer val = values.get(i);
            valuesStr[i] = String.valueOf(val);
        }
        slideChooseView.setOptions(selected, valuesStr);
    }
}
