package org.telegram.ui.Components.Premium.boosts.cells;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

@SuppressLint("ViewConstructor")
public class ActionBtnCell extends FrameLayout {

    private final ButtonWithCounterView button;
    private final View backgroundView;
    private final Theme.ResourcesProvider resourcesProvider;
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean drawDivider;

    public ActionBtnCell(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        backgroundView = new View(context);
        addView(backgroundView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        button = new ButtonWithCounterView(context, resourcesProvider);

        addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.CENTER, 14, 0, 14, 0));
    }

    @Override
    public void setOnClickListener(@Nullable OnClickListener l) {
        button.setOnClickListener(l);
    }

    public void setStartGiveAwayStyle(int counter, boolean animated) {
        drawDivider = true;
        button.withCounterIcon();
        button.setShowZero(true);
        button.setEnabled(true);
        button.setCount(counter, animated);
        button.setText(LocaleController.formatString("BoostingStartGiveaway", R.string.BoostingStartGiveaway), animated);
        backgroundView.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
    }

    public void setGiftPremiumStyle(int counter, boolean animated, boolean isEnabled) {
        drawDivider = true;
        button.withCounterIcon();
        button.setShowZero(true);
        button.setEnabled(isEnabled);
        button.setCount(counter, animated);
        button.setText(LocaleController.formatString("GiftPremium", R.string.GiftPremium), animated);
        backgroundView.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
    }

    public void setActivateForFreeStyle() {
        drawDivider = true;
        button.setEnabled(true);
        button.setText(LocaleController.formatString("GiftPremiumActivateForFree", R.string.GiftPremiumActivateForFree), false);
        backgroundView.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (drawDivider) {
            dividerPaint.setColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
            dividerPaint.setAlpha(255);
            canvas.drawRect(0, 0, getWidth(), 1, dividerPaint);
        }
    }

    public void updateLoading(boolean isLoading) {
        button.setLoading(isLoading);
    }

    public boolean isLoading() {
        return button.isLoading();
    }

    public void updateCounter(int count) {
        button.setCount(count, true);
    }

    public void setOkStyle(boolean isUsed) {
        drawDivider = false;
        button.setShowZero(false);
        button.setEnabled(true);
        String text = isUsed ? LocaleController.formatString("BoostingUseLink", R.string.BoostingUseLink) : LocaleController.formatString("OK", R.string.OK);
        button.setText(text, false);
    }

    public void setCloseStyle(){
        drawDivider = false;
        button.setShowZero(false);
        button.setEnabled(true);
        button.setText(LocaleController.formatString("Close", R.string.Close), false);
    }

    public void setCloseStyle(boolean needDivider){
        setCloseStyle();
        drawDivider = needDivider;
    }
}
