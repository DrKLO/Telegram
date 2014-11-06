/*
 * This is the source code of Telegram for Android v. 1.4.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Views.ActionBar;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.android.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.AnimationCompat.ViewProxy;

public class ActionBar extends FrameLayout {

    protected ActionBarLayer currentLayer = null;
    protected ActionBarLayer previousLayer = null;
    protected View shadowView = null;
    private boolean isBackOverlayVisible;

    public ActionBar(Context context) {
        super(context);
        createComponents();
    }

    public ActionBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        createComponents();
    }

    public ActionBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        createComponents();
    }

    public void createComponents() {
        shadowView = new View(getContext());
        addView(shadowView);
        shadowView.setVisibility(INVISIBLE);
        ViewGroup.LayoutParams layoutParams = shadowView.getLayoutParams();
        layoutParams.width = AndroidUtilities.dp(2);
        layoutParams.height = LayoutParams.MATCH_PARENT;
        shadowView.setLayoutParams(layoutParams);
        shadowView.setBackgroundResource(R.drawable.shadow);
    }

    public ActionBarLayer createLayer() {
        return new ActionBarLayer(getContext(), this);
    }

    public void detachActionBarLayer(ActionBarLayer layer) {
        if (layer == null) {
            return;
        }
        removeView(layer);
        if (currentLayer == layer) {
            currentLayer = null;
        }
    }

    public void setCurrentActionBarLayer(ActionBarLayer layer) {
        if (layer == null || layer.getParent() != null) {
            return;
        }
        if (currentLayer != null) {
            removeView(currentLayer);
        }
        currentLayer = layer;
        addView(layer);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)layer.getLayoutParams();
        layoutParams.width = LayoutParams.MATCH_PARENT;
        layoutParams.height = LayoutParams.MATCH_PARENT;
        layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        layer.setLayoutParams(layoutParams);
        currentLayer.setBackOverlayVisible(isBackOverlayVisible);
        layer.setAlphaEx(1);
    }

    public void setBackOverlayVisible(boolean visible) {
        isBackOverlayVisible = visible;
        if (currentLayer != null) {
            currentLayer.setBackOverlayVisible(visible);
        }
        if (previousLayer != null) {
            previousLayer.setBackOverlayVisible(visible);
        }
    }

    public void prepareForMoving(ActionBarLayer layer) {
        if (currentLayer == null || layer == null) {
            return;
        }
        previousLayer = layer;
        ViewGroup parent = (ViewGroup) previousLayer.getParent();
        if (parent != null) {
            parent.removeView(previousLayer);
        }
        this.addView(previousLayer, 0);
        ViewGroup.LayoutParams layoutParams = layer.getLayoutParams();
        layoutParams.width = LayoutParams.MATCH_PARENT;
        layoutParams.height = LayoutParams.MATCH_PARENT;
        layer.setLayoutParams(layoutParams);
        ViewProxy.setX(shadowView, -AndroidUtilities.dp(2));
        shadowView.setVisibility(VISIBLE);
        previousLayer.setBackOverlayVisible(isBackOverlayVisible);
    }

    public void stopMoving(boolean backAnimation) {
        if (currentLayer == null) {
            return;
        }

        if (!backAnimation) {
            removeView(currentLayer);
            currentLayer = previousLayer;
            currentLayer.setAlphaEx(1);
            previousLayer = null;
        } else {
            removeView(previousLayer);
            previousLayer = null;
        }
        shadowView.setVisibility(INVISIBLE);
        ViewProxy.setX(currentLayer, 0);
    }

    public void moveActionBarByX(int dx) {
        if (currentLayer == null) {
            return;
        }
        ViewProxy.setX(currentLayer, dx);
        ViewProxy.setX(shadowView, dx - AndroidUtilities.dp(2));
        if (dx != 0) {
            if (previousLayer != null) {
                previousLayer.setAlphaEx(Math.min(1, (float) dx / (float) currentLayer.getMeasuredWidth()));
            }
        } else {
            if (previousLayer != null) {
                previousLayer.setAlphaEx(0);
            }
            currentLayer.setAlphaEx(1);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.getCurrentActionBarHeight(), MeasureSpec.EXACTLY));
    }

    public void onMenuButtonPressed() {
        if (currentLayer != null) {
            currentLayer.onMenuButtonPressed();
        }
    }
}
