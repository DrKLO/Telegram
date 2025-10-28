package org.telegram.messenger.pip;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import org.telegram.messenger.pip.source.PipSourceHandlerState2;

@SuppressLint("ViewConstructor")
public class PipSourceContentView extends ViewGroup {
    private final PipSourceHandlerState2 state;

    public PipSourceContentView(Context context, PipSourceHandlerState2 state) {
        super(context);
        this.state = state;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        );

        state.updatePositionViewRect(width, height,
            ((PipActivityContentLayout) getParent()).isViewInPip());

        for (int a = 0; a < getChildCount(); a++) {
            getChildAt(a).measure(
                MeasureSpec.makeMeasureSpec(state.position.width(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(state.position.height(), MeasureSpec.EXACTLY)
            );
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int a = 0; a < getChildCount(); a++) {
            getChildAt(a).layout(
                state.position.left,
                state.position.top,
                state.position.right,
                state.position.bottom
            );
        }
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        state.draw(canvas, super::dispatchDraw);
    }
}
