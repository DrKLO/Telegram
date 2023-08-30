package org.telegram.ui.Components.spoilers;

import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;

import androidx.core.view.GestureDetectorCompat;

import java.util.List;

public class SpoilersClickDetector {
    private GestureDetectorCompat gestureDetector;
    private boolean trackingTap;
    private int horizontalPadding;
    private int verticalPadding;

    public SpoilersClickDetector(View v, List<SpoilerEffect> spoilers, OnSpoilerClickedListener clickedListener) {
        this(v, spoilers, true, clickedListener);
    }

    public SpoilersClickDetector(View v, List<SpoilerEffect> spoilers, boolean offsetPadding, OnSpoilerClickedListener clickedListener) {
        gestureDetector = new GestureDetectorCompat(v.getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                int x = (int) e.getX(), y = (int) e.getY();
                y += v.getScrollY();
                if (offsetPadding) {
                    x -= v.getPaddingLeft();
                    y -= v.getPaddingTop();
                }
                x -= horizontalPadding;
                y -= verticalPadding;
                for (SpoilerEffect eff : spoilers) {
                    if (eff.getBounds().contains(x, y)) {
                        trackingTap = true;
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (trackingTap) {
                    v.playSoundEffect(SoundEffectConstants.CLICK);

                    trackingTap = false;
                    int x = (int) e.getX(), y = (int) e.getY();
                    y += v.getScrollY();
                    if (offsetPadding) {
                        x -= v.getPaddingLeft();
                        y -= v.getPaddingTop();
                    }
                    x -= horizontalPadding;
                    y -= verticalPadding;
                    for (SpoilerEffect eff : spoilers) {
                        if (eff.getBounds().contains(x, y)) {
                            clickedListener.onSpoilerClicked(eff, x, y);
                            return true;
                        }
                    }
                }
                return false;
            }
        });
    }

    public boolean onTouchEvent(MotionEvent ev) {
        return gestureDetector.onTouchEvent(ev);
    }

    public void setAdditionalOffsets(int horizontalPadding, int verticalPadding) {
        this.horizontalPadding = horizontalPadding;
        this.verticalPadding = verticalPadding;
    }

    public interface OnSpoilerClickedListener {
        void onSpoilerClicked(SpoilerEffect spoiler, float x, float y);
    }
}
