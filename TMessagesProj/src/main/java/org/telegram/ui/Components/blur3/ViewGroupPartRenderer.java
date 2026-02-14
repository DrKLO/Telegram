package org.telegram.ui.Components.blur3;

import android.graphics.Canvas;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import org.telegram.ui.Components.blur3.capture.IBlur3Capture;
import org.telegram.ui.Components.blur3.capture.IBlur3Hash;
import org.telegram.ui.Components.chat.ViewPositionWatcher;

public class ViewGroupPartRenderer implements IBlur3Capture {

    private final RectF tmpDrawListViewRectF = new RectF();
    private final PointF tmpDrawListViewPointF = new PointF();

    public interface DrawChildMethod {
        boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime);
    }


    private final ViewGroup listView;
    private final DrawChildMethod listViewDrawChildMethod;
    private final ViewGroup listViewParent;

    public ViewGroupPartRenderer(ViewGroup listView, ViewGroup listViewParent, DrawChildMethod listViewDrawChildMethod) {
        this.listView = listView;
        this.listViewDrawChildMethod = listViewDrawChildMethod;
        this.listViewParent = listViewParent;
    }

    private final RectF savedPos = new RectF();
    public boolean ignoreBlurCap;

    @Override
    public void capture(Canvas canvas, RectF position) {
        final long drawingTime = SystemClock.uptimeMillis();

        if (!ViewPositionWatcher.computeCoordinatesInParent(listView, listViewParent, tmpDrawListViewPointF)) {
            return;
        }

        canvas.save();
        canvas.clipRect(position);
        canvas.translate(tmpDrawListViewPointF.x, tmpDrawListViewPointF.y);

        if (listView instanceof IBlur3Capture && !ignoreBlurCap) {
            IBlur3Capture capture = (IBlur3Capture) listView;

            savedPos.set(position);
            position.offset(-tmpDrawListViewPointF.x, -tmpDrawListViewPointF.y);
            capture.capture(canvas, position);
            position.set(savedPos);
        } else {
            for (int i = 0; i < listView.getChildCount(); i++) {
                final View child = listView.getChildAt(i);
                if (!ViewPositionWatcher.computeRectInParent(child, listViewParent, tmpDrawListViewRectF)) {
                    continue;
                }

                if (!tmpDrawListViewRectF.intersect(position)) {
                    continue;
                }

                listViewDrawChildMethod.drawChild(canvas, child, drawingTime);
            }
        }
        canvas.restore();
    }

    @Override
    public void captureCalculateHash(IBlur3Hash builder, RectF position) {
        if (!ViewPositionWatcher.computeCoordinatesInParent(listView, listViewParent, tmpDrawListViewPointF)) {
            builder.unsupported();
            return;
        }


        if (listView instanceof IBlur3Capture && !ignoreBlurCap) {
            builder.addF(tmpDrawListViewPointF.x);
            builder.addF(tmpDrawListViewPointF.y);

            IBlur3Capture capture = (IBlur3Capture) listView;

            savedPos.set(position);
            position.offset(-tmpDrawListViewPointF.x, -tmpDrawListViewPointF.y);
            capture.captureCalculateHash(builder, position);
            position.set(savedPos);
        } else {
            builder.unsupported();
        }
    }
}
