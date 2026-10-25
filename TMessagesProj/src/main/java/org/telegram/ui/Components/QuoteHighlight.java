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
import org.telegram.messenger.RichMessageLayout;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.TextSelectionHelper;

import java.util.ArrayList;

public class QuoteHighlight extends Path {

    public final ChatMessageCell cell;
    public final int id, start, end;
    public final boolean todo;
    public final boolean poll;
    public byte[] pollOptionId;

    private int cornerPathEffectSize;
    public final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final CornerPath path = new CornerPath();

    private final AnimatedFloat t;

    private static class Rect {
        public float left, right;
        public float top, bottom;

        public boolean first, last;
        public float prevTop;
        public float nextBottom;

        // rich messages: rects of each text block are a separate group, following the block's position (e.g. a scrolled table)
        public int group;
        public TextSelectionHelper.TextLayoutBlock block;
        public float blockX, blockY;
    }
    private final ArrayList<Rect> rectangles = new ArrayList<>();
    public final ArrayList<Integer> quotesToExpand = new ArrayList<>();

    private float currentOffsetX, currentOffsetY;
    private float minX;

    private Rect lastRect;

    private int currentGroup;
    private TextSelectionHelper.TextLayoutBlock currentBlock;
    private int groupsCount = 1;
    private final ArrayList<CornerPath> groupPaths = new ArrayList<>();

    public QuoteHighlight(
        ChatMessageCell cell,
        int id,
        int taskId
    ) {
        this.cell = cell;
        this.t = new AnimatedFloat(0, () -> {
            if (cell != null) cell.invalidate();
            if (cell.getParent() instanceof View) ((View) cell.getParent()).invalidate();
        }, 350, 420, CubicBezierInterpolator.EASE_OUT_QUINT);
        this.id = id;
        this.start = -taskId;
        this.end = -taskId;
        this.todo = true;
        this.poll = false;

        paint.setPathEffect(new CornerPathEffect(cornerPathEffectSize = dp(4)));
    }

    public QuoteHighlight(
            ChatMessageCell cell,
            int id,
            byte[] taskId
    ) {
        this.cell = cell;
        this.t = new AnimatedFloat(0, () -> {
            if (cell != null) cell.invalidate();
            if (cell.getParent() instanceof View) ((View) cell.getParent()).invalidate();
        }, 350, 420, CubicBezierInterpolator.EASE_OUT_QUINT);
        this.id = id;
        this.pollOptionId = taskId;
        this.start = 0; //-taskId;
        this.end = 0; //-taskId;
        this.todo = false;
        this.poll = true;

        paint.setPathEffect(new CornerPathEffect(cornerPathEffectSize = dp(4)));
    }

    public QuoteHighlight(
        View view, ViewParent parent,
        int id,
        ArrayList<MessageObject.TextLayoutBlock> blocks,
        int start, int end,
        float offsetX
    ) {
        this.cell = null;
        this.t = new AnimatedFloat(0, () -> {
            if (view != null) view.invalidate();
            if (parent instanceof View) ((View) parent).invalidate();
        }, 350, 420, CubicBezierInterpolator.EASE_OUT_QUINT);
        this.id = id;
        this.start = start;
        this.end = end;
        this.todo = false;
        this.poll = false;
        if (blocks == null) return;

        paint.setPathEffect(new CornerPathEffect(cornerPathEffectSize = dp(4)));

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

        finishRects();
    }

    /** start and end are offsets in RichMessageLayout.joinedText */
    public QuoteHighlight(
        View view, ViewParent parent,
        int id,
        RichMessageLayout rich,
        int start, int end
    ) {
        this.cell = null;
        this.t = new AnimatedFloat(0, () -> {
            if (view != null) view.invalidate();
            if (parent instanceof View) ((View) parent).invalidate();
        }, 350, 420, CubicBezierInterpolator.EASE_OUT_QUINT);
        this.id = id;
        this.start = start;
        this.end = end;
        this.todo = false;
        this.poll = false;
        if (rich == null) return;

        paint.setPathEffect(new CornerPathEffect(cornerPathEffectSize = dp(4)));

        minX = -Float.MAX_VALUE;
        for (int i = 0; i < rich.textBlocks.size(); ++i) {
            final TextSelectionHelper.TextLayoutBlock block = rich.textBlocks.get(i);
            final Layout layout = block == null ? null : block.getLayout();
            if (layout == null || layout.getText() == null) continue;
            final int charOffset = rich.textBlockCharOffsets.get(i);
            final int length = layout.getText().length();
            final int blockStart = Utilities.clamp(start - charOffset, length, 0);
            final int blockEnd = Utilities.clamp(end - charOffset, length, 0);
            if (blockStart >= blockEnd) continue;

            currentOffsetX = block.getX();
            currentOffsetY = block.getY();
            currentBlock = block;
            lastRect = null;
            final int rectsBefore = rectangles.size();
            if (AndroidUtilities.isRTL(layout.getText())) {
                layout.getSelectionPath(blockStart, blockEnd, this);
            } else {
                getSelectionPath(layout, blockStart, blockEnd);
            }
            if (rectangles.size() > rectsBefore) {
                currentGroup++;
            }
        }
        groupsCount = Math.max(1, currentGroup);

        finishRects();
    }

    private void finishRects() {
        for (int i = 0; i < rectangles.size(); ++i) {
            final Rect rect = rectangles.get(i);
            if (i == 0 || rectangles.get(i - 1).group != rect.group) {
                rect.first = true;
                rect.top -= dp(.66f);
            }
            if (i == rectangles.size() - 1 || rectangles.get(i + 1).group != rect.group) {
                rect.last = true;
                rect.bottom += dp(.66f);
            }
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
        if (poll) {
            final int cornerRadius = lerp(dp(4), 0, t);
            if (cornerPathEffectSize != cornerRadius) {
                paint.setPathEffect(new CornerPathEffect(cornerPathEffectSize = cornerRadius));
            }
            path.rewind();
            final int index = cell.getPollIndex(pollOptionId);
            AndroidUtilities.rectTmp.set(cell.getBackgroundDrawableLeft(), cell.getPollButtonTop(index), cell.getBackgroundDrawableRight(), cell.getPollButtonBottom(index));
            lerp(bounds, AndroidUtilities.rectTmp, t, AndroidUtilities.rectTmp);
            path.addRect(AndroidUtilities.rectTmp, Path.Direction.CW);
            path.closeRects();
        } else if (todo) {
            final int cornerRadius = lerp(dp(4), 0, t);
            if (cornerPathEffectSize != cornerRadius) {
                paint.setPathEffect(new CornerPathEffect(cornerPathEffectSize = cornerRadius));
            }
            path.rewind();
            final int index = cell.getTodoIndex(-start);
            AndroidUtilities.rectTmp.set(cell.getBackgroundDrawableLeft(), cell.getPollButtonTop(index), cell.getBackgroundDrawableRight(), cell.getPollButtonBottom(index));
            lerp(bounds, AndroidUtilities.rectTmp, t, AndroidUtilities.rectTmp);
            path.addRect(AndroidUtilities.rectTmp, Path.Direction.CW);
            path.closeRects();
        } else if (groupsCount > 1) {
            // every group grows out of the bubble on its own, overlaps are composed in a layer so they aren't drawn twice
            canvas.translate(textX, textY);
            int groupIndex = -1;
            CornerPath groupPath = null;
            for (int i = 0; i < rectangles.size(); ++i) {
                final Rect rect = rectangles.get(i);
                if (groupPath == null || rect.first) {
                    if (groupPath != null) groupPath.closeRects();
                    groupIndex++;
                    if (groupIndex >= groupPaths.size()) {
                        groupPaths.add(new CornerPath());
                    }
                    groupPath = groupPaths.get(groupIndex);
                    groupPath.rewind();
                }
                addRect(groupPath, rect, textX, textY, bounds, t);
            }
            if (groupPath != null) groupPath.closeRects();

            final int wasAlpha = paint.getAlpha();
            canvas.saveLayerAlpha(bounds.left - textX, bounds.top - textY, bounds.right - textX, bounds.bottom - textY, (int) (wasAlpha * alpha), Canvas.ALL_SAVE_FLAG);
            paint.setAlpha(0xFF);
            for (int i = 0; i <= groupIndex; ++i) {
                canvas.drawPath(groupPaths.get(i), paint);
            }
            paint.setAlpha(wasAlpha);
            canvas.restore();
            canvas.restore();
            return;
        } else {
            canvas.translate(textX, textY);
            path.rewind();
            for (int i = 0; i < rectangles.size(); ++i) {
                addRect(path, rectangles.get(i), textX, textY, bounds, t);
            }
            path.closeRects();
        }

        int wasAlpha = paint.getAlpha();
        paint.setAlpha((int) (wasAlpha * alpha));
        canvas.drawPath(path, paint);
        paint.setAlpha(wasAlpha);
        canvas.restore();
    }

    /** keeps the appearance progress of the previous highlight of the same message, so it isn't animated again */
    public void continueFrom(QuoteHighlight prev) {
        if (prev != null && prev.id == id && !prev.todo && !prev.poll) {
            t.set(prev.t.get(), true);
        }
    }

    private static void addRect(Path path, Rect rect, float textX, float textY, android.graphics.Rect bounds, float t) {
        final float dx = rect.block != null ? rect.block.getX() - rect.blockX : 0;
        final float dy = rect.block != null ? rect.block.getY() - rect.blockY : 0;
        path.addRect(
            lerp(bounds.left - textX, rect.left + dx, t),
            lerp(rect.first ? bounds.top - textY : rect.prevTop + dy, rect.top + dy, t),
            lerp(bounds.right - textX, rect.right + dx, t),
            lerp(rect.last ? bounds.bottom - textY : rect.nextBottom + dy, rect.bottom + dy, t),
            Path.Direction.CW
        );
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
        rect.group = currentGroup;
        rect.block = currentBlock;
        if (currentBlock != null) {
            rect.blockX = currentBlock.getX();
            rect.blockY = currentBlock.getY();
        }
        if (lastRect != null) {
            lastRect.nextBottom = (lastRect.bottom + top) / 2f;
            rect.prevTop = (lastRect.bottom + top) / 2f;
        }
        rectangles.add(rect);
        lastRect = rect;
    }
}
