package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Xfermode;
import android.text.Layout;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

public class QuoteHighlight extends Path {

    public final int id, start, end;
    public final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final CornerPath path = new CornerPath();

    private final AnimatedFloat t;

    private static class Rect {
        public float left, right;
        public float top, bottom;

        public boolean first, last;
        public float prevTop;
        public float nextBottom;
    }
    private final ArrayList<Rect> rectangles = new ArrayList<>();
    public final ArrayList<Integer> quotesToExpand = new ArrayList<>();

    private float currentOffsetX, currentOffsetY;
    private float minX;

    private Rect lastRect;

    public QuoteHighlight(
        View view, ViewParent parent,
        int id,
        ArrayList<MessageObject.TextLayoutBlock> blocks,
        int start, int end,
        float offsetX
    ) {
        this.t = new AnimatedFloat(0, () -> {
            if (view != null) view.invalidate();
            if (parent instanceof View) ((View) parent).invalidate();
        }, 350, 420, CubicBezierInterpolator.EASE_OUT_QUINT);
        this.id = id;
        this.start = start;
        this.end = end;
        if (blocks == null) return;

        paint.setPathEffect(new CornerPathEffect(dp(4)));

        boolean isRtl = false;
        for (int i = 0; i < blocks.size(); ++i) {
            final MessageObject.TextLayoutBlock block = blocks.get(i);
            if (block == null) continue;
            if (start > block.charactersEnd || end < block.charactersOffset) continue;

            final int blockStart = Math.max(0, start - block.charactersOffset);
            final int blockEnd = Math.min(end - block.charactersOffset, block.charactersEnd - block.charactersOffset);

            currentOffsetX = -offsetX;
            if (block.code && !block.quote) {
                currentOffsetX += dp(10);
            }
            currentOffsetY = block.textYOffset(blocks) + block.padTop;
            minX = block.quote ? dp(10) : 0;

            isRtl = isRtl || AndroidUtilities.isRTL(block.textLayout.getText());
            if (isRtl) {
                block.textLayout.getSelectionPath(blockStart, blockEnd, this);
            } else {
                getSelectionPath(block.textLayout, blockStart, blockEnd);
            }

            if (block.quoteCollapse && block.collapsed()) {
                quotesToExpand.add(block.index);
            }
        }

        if (rectangles.size() > 0) {
            Rect firstRect = rectangles.get(0);
            Rect lastRect = rectangles.get(rectangles.size() - 1);

            firstRect.first = true;
            firstRect.top -= dp(.66f);

            lastRect.last = true;
            lastRect.bottom += dp(.66f);
        }
    }

    private void getSelectionPath(Layout layout, int start, int end) {
        if (start == end) {
            return;
        }

        if (end < start) {
            int temp = end;
            end = start;
            start = temp;
        }

        final int startline = layout.getLineForOffset(start);
        final int endline = layout.getLineForOffset(end);

        for (int line = startline; line <= endline; ++line) {
            final int lineStart = layout.getLineStart(line);
            final int lineEnd = layout.getLineEnd(line);

            if (lineEnd == lineStart)
                continue;
            if (lineStart + 1 == lineEnd && Character.isWhitespace(layout.getText().charAt(lineStart)))
                continue;

            final float left, right;
            if (line == startline && start > lineStart) {
                left = layout.getPrimaryHorizontal(start);
            } else {
                left = layout.getLineLeft(line);
            }
            if (line == endline && end < lineEnd) {
                right = layout.getPrimaryHorizontal(end);
            } else {
                right = layout.getLineRight(line);
            }

            addRect(
                Math.min(left, right),
                layout.getLineTop(line),
                Math.max(left, right),
                layout.getLineBottom(line)
            );
        }
    }

    public float getT() {
        return this.t.set(1);
    }

    public void draw(Canvas canvas, float textX, float textY, android.graphics.Rect bounds, float alpha) {
        final float t = this.t.set(1);

        canvas.save();
        canvas.translate(textX, textY);
        path.rewind();
        for (int i = 0; i < rectangles.size(); ++i) {
            final Rect rect = rectangles.get(i);
            path.addRect(
                lerp(bounds.left - textX, rect.left, t),
                lerp(rect.first ? bounds.top - textY : rect.prevTop, rect.top, t),
                lerp(bounds.right - textX, rect.right, t),
                lerp(rect.last ? bounds.bottom - textY : rect.nextBottom, rect.bottom, t),
                Path.Direction.CW
            );
        }
        path.closeRects();

        int wasAlpha = paint.getAlpha();
        paint.setAlpha((int) (wasAlpha * alpha));
        canvas.drawPath(path, paint);
        paint.setAlpha(wasAlpha);
        canvas.restore();
    }

    public boolean done() {
        return this.t.get() >= 1f;
    }

    @Override
    public void addRect(float left, float top, float right, float bottom, @NonNull Direction dir) {
        addRect(left, top, right, bottom);
    }

    public void addRect(float left, float top, float right, float bottom) {
        if (left >= right) {
            return;
        }
//        if (lastRect != null && Math.abs(lastRect.top - top) < 1f) {
//            lastRect.left = Math.min(lastRect.left, left);
//            lastRect.right = Math.min(lastRect.right, right);
//            return;
//        }

        left = Math.max(minX, left);
        right = Math.max(minX, right);

        left += currentOffsetX;
        top += currentOffsetY;
        right += currentOffsetX;
        bottom += currentOffsetY;

        Rect rect = new Rect();
        rect.left = left - dp(3);
        rect.right = right + dp(3);
        rect.top = top;
        rect.bottom = bottom;
        if (lastRect != null) {
            lastRect.nextBottom = (lastRect.bottom + top) / 2f;
            rect.prevTop = (lastRect.bottom + top) / 2f;
        }
        rectangles.add(rect);
        lastRect = rect;
    }
}
