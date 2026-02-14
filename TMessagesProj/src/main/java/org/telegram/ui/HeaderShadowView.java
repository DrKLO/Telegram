package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.Components.CubicBezierInterpolator;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

@SuppressLint("ViewConstructor")
public class HeaderShadowView extends View implements FactorAnimator.Target {
    private final INavigationLayout iNavigationLayout;
    private final BoolAnimator shadowVisible = new BoolAnimator(0, this, CubicBezierInterpolator.EASE_OUT_QUINT, 380L, true);

    public HeaderShadowView(Context context, INavigationLayout iNavigationLayout) {
        super(context);
        this.iNavigationLayout = iNavigationLayout;
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        iNavigationLayout.drawHeaderShadow(canvas, 0);
    }

    public void setShadowVisible(boolean visible, boolean animated) {
        shadowVisible.setValue(visible, animated);
    }

    public boolean isShadowVisible() {
        return shadowVisible.getValue();
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        setVisibility(factor > 0 ? VISIBLE : GONE);
        setAlpha(factor);
    }
}
