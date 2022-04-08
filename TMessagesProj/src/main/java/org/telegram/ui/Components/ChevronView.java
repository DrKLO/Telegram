package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import androidx.annotation.IntDef;

import org.telegram.messenger.AndroidUtilities;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class ChevronView extends View {
    public final static int DIRECTION_RIGHT = 0, DIRECTION_LEFT = 1;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            DIRECTION_RIGHT,
            DIRECTION_LEFT
    })
    public @interface Direction {}

    @Direction
    private int direction;

    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Path instead of Canvas#drawLine allows to render even transparent colors
    private Path path = new Path();

    public ChevronView(Context context, @Direction int direction) {
        super(context);

        paint.setStrokeWidth(AndroidUtilities.dp(1.75f));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);

        this.direction = direction;
    }

    public void setColor(int color) {
        paint.setColor(color);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float stroke = paint.getStrokeWidth();

        path.rewind();
        switch (direction) {
            case DIRECTION_RIGHT:
                path.moveTo(stroke + getPaddingLeft(), stroke + getPaddingTop());
                path.lineTo(getWidth() - stroke - getPaddingRight(), getHeight() / 2f);
                path.lineTo(stroke + getPaddingLeft(), getHeight() - stroke - getPaddingBottom());
                break;
            case DIRECTION_LEFT:
                path.moveTo(getWidth() - stroke - getPaddingRight(), stroke + getPaddingTop());
                path.lineTo(stroke + getPaddingLeft(), getHeight() / 2f);
                path.lineTo(getWidth() - stroke - getPaddingBottom(), getHeight() - stroke - getPaddingBottom());
                break;
        }
        canvas.drawPath(path, paint);
    }
}
