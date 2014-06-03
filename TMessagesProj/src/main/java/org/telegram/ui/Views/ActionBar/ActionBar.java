/*
 * This is the source code of Telegram for Android v. 1.4.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Views.ActionBar;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;

import java.util.ArrayList;

public class ActionBar extends FrameLayout {

    private static Drawable logoDrawable;
    protected ActionBarLayer currentLayer = null;
    private ActionBarLayer previousLayer = null;
    private View currentBackOverlay;
    private View shadowView = null;
    private int currentBackOverlayWidth;

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
        layoutParams.width = Utilities.dp(2);
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
        updateBackOverlay(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
        if(android.os.Build.VERSION.SDK_INT >= 11) {
            layer.setAlpha(1);
        }
    }

    public void setBackOverlay(View view, int width) {
        if (currentBackOverlay != null) {
            removeView(currentBackOverlay);
        }
        if (view != null) {
            addView(view);
        }
        currentBackOverlayWidth = width;
        currentBackOverlay = view;
        updateBackOverlay(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
    }

    private void updateBackOverlay(int widthMeasureSpec, int heightMeasureSpec) {
        if (currentLayer != null) {
            currentLayer.setBackLayoutVisible(currentBackOverlay == null ? VISIBLE : INVISIBLE);
        }
        if (currentBackOverlay != null) {
            ViewGroup.LayoutParams layoutParams = currentBackOverlay.getLayoutParams();
            if (currentLayer != null) {
                currentLayer.measure(widthMeasureSpec, heightMeasureSpec);
                layoutParams.width = Math.min(currentBackOverlayWidth, currentLayer.getBackLayoutWidth());
            } else {
                layoutParams.width = LayoutParams.WRAP_CONTENT;
            }
            if (layoutParams.width != 0) {
                layoutParams.height = LayoutParams.MATCH_PARENT;
                currentBackOverlay.setLayoutParams(layoutParams);
            }
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
        currentLayer.setBackgroundDrawable(getBackground());
        shadowView.setX(-Utilities.dp(2));
        shadowView.setVisibility(VISIBLE);
    }

    public void stopMoving(boolean backAnimation) {
        if (currentLayer == null) {
            return;
        }
        currentLayer.setBackgroundDrawable(null);
        currentLayer.setX(0);
        if (!backAnimation) {
            removeView(currentLayer);
            currentLayer = previousLayer;
            currentLayer.setAlpha(1);
        } else {
            removeView(previousLayer);
            previousLayer = null;
        }
        shadowView.setVisibility(INVISIBLE);
    }

    public void moveActionBarByX(int dx) {
        if (currentLayer == null) {
            return;
        }
        currentLayer.setX(dx);
        shadowView.setX(dx - Utilities.dp(2));
        if (dx != 0) {
            if (previousLayer != null) {
                previousLayer.setAlpha(Math.min(1, (float) dx / (float) currentLayer.getMeasuredWidth()));
            }
        } else {
            if (previousLayer != null) {
                previousLayer.setAlpha(0);
            }
            currentLayer.setAlpha(1);
        }
    }

    public void setupAnimations(ArrayList<Animator> animators, boolean back) {
        if (back) {
            animators.add(ObjectAnimator.ofFloat(currentLayer, "x", 0));
            animators.add(ObjectAnimator.ofFloat(shadowView, "x", -Utilities.dp(2)));
            animators.add(ObjectAnimator.ofFloat(previousLayer, "alpha", 0));
        } else {
            animators.add(ObjectAnimator.ofFloat(currentLayer, "x", getMeasuredWidth()));
            animators.add(ObjectAnimator.ofFloat(shadowView, "x", getMeasuredWidth() - Utilities.dp(2)));
            animators.add(ObjectAnimator.ofFloat(previousLayer, "alpha", 1.0f));
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        updateBackOverlay(widthMeasureSpec, heightMeasureSpec);
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Utilities.dp(40), MeasureSpec.EXACTLY));
        } else {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Utilities.dp(48), MeasureSpec.EXACTLY));
        }
    }

    public void onMenuButtonPressed() {
        if (currentLayer != null) {
            currentLayer.onMenuButtonPressed();
        }
    }
}
