package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ArticleViewer;
import org.telegram.ui.Cells.TextSelectionHelper;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.view.Gravity.AXIS_PULL_AFTER;
import static android.view.Gravity.AXIS_PULL_BEFORE;
import static android.view.Gravity.AXIS_SPECIFIED;
import static android.view.Gravity.AXIS_X_SHIFT;
import static android.view.Gravity.AXIS_Y_SHIFT;
import static android.view.Gravity.HORIZONTAL_GRAVITY_MASK;
import static android.view.Gravity.RELATIVE_LAYOUT_DIRECTION;
import static android.view.Gravity.VERTICAL_GRAVITY_MASK;
import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;
import static java.lang.Math.max;
import static java.lang.Math.min;

public class TableLayout extends View {

    public static final int HORIZONTAL = LinearLayout.HORIZONTAL;
    public static final int VERTICAL = LinearLayout.VERTICAL;

    public static final int UNDEFINED = Integer.MIN_VALUE;

    public static final int ALIGN_BOUNDS = 0;
    public static final int ALIGN_MARGINS = 1;

    static final int MAX_SIZE = 100000;
    static final int UNINITIALIZED_HASH = 0;
    private TextSelectionHelper.ArticleTextSelectionHelper textSelectionHelper;

    private int colCount;

    private static final int DEFAULT_ORIENTATION = HORIZONTAL;
    private static final int DEFAULT_COUNT = UNDEFINED;
    private static final boolean DEFAULT_USE_DEFAULT_MARGINS = false;
    private static final boolean DEFAULT_ORDER_PRESERVED = true;
    private static final int DEFAULT_ALIGNMENT_MODE = ALIGN_MARGINS;

    private final Axis mHorizontalAxis = new Axis(true);
    private final Axis mVerticalAxis = new Axis(false);
    private int mOrientation = DEFAULT_ORIENTATION;
    private boolean mUseDefaultMargins = DEFAULT_USE_DEFAULT_MARGINS;
    private int mAlignmentMode = DEFAULT_ALIGNMENT_MODE;
    private int mDefaultGap;
    private int mLastLayoutParamsHashCode = UNINITIALIZED_HASH;
    private int itemPaddingTop = AndroidUtilities.dp(7);
    private int itemPaddingLeft = AndroidUtilities.dp(8);
    private boolean drawLines;
    private boolean isStriped;
    private boolean isRtl;
    private ArrayList<Child> cellsToFixHeight = new ArrayList<>();
    private ArrayList<Point> rowSpans = new ArrayList<>();

    private Path linePath = new Path();
    private Path backgroundPath = new Path();
    private RectF rect = new RectF();
    private float[] radii = new float[8];

    public class Child {
        private LayoutParams layoutParams;
        public ArticleViewer.DrawingText textLayout;
        private TLRPC.TL_pageTableCell cell;
        private int index;

        public int textWidth;
        public int textHeight;
        public int textX;
        public int textY;
        public int textLeft;
        public int rowspan;

        private int measuredWidth;
        private int measuredHeight;
        private int fixedHeight;
        public int x;
        public int y;
        private int selectionIndex = -1;

        public Child(int i) {
            index = i;
        }

        public LayoutParams getLayoutParams() {
            return layoutParams;
        }

        public int getMeasuredWidth() {
            return measuredWidth;
        }

        public int getMeasuredHeight() {
            return measuredHeight;
        }

        public void measure(int width, int height, boolean first) {
            measuredWidth = width;
            measuredHeight = height;
            if (first) {
                fixedHeight = measuredHeight;
            }
            if (cell != null) {
                if (cell.valign_middle) {
                    textY = (measuredHeight - textHeight) / 2;
                } else if (cell.valign_bottom) {
                    textY = measuredHeight - textHeight - itemPaddingTop;
                } else {
                    textY = itemPaddingTop;
                }

                if (textLayout != null) {
                    int lineCount = textLayout.getLineCount();
                    if (!first && (lineCount > 1 || lineCount > 0 && (cell.align_center || cell.align_right))) {
                        setTextLayout(delegate.createTextLayout(cell, measuredWidth - itemPaddingLeft * 2));
                        fixedHeight = textHeight + itemPaddingTop * 2;
                    }

                    if (textLeft != 0) {
                        textX = -textLeft;
                        if (cell.align_right) {
                            textX += (measuredWidth - textWidth - itemPaddingLeft);
                        } else if (cell.align_center) {
                            textX += Math.round((measuredWidth - textWidth) / 2);
                        } else {
                            textX += itemPaddingLeft;
                        }
                    } else {
                        textX = itemPaddingLeft;
                    }
                }
            }
        }

        public void setTextLayout(ArticleViewer.DrawingText layout) {
            textLayout = layout;

            if (layout != null) {
                textWidth = 0;
                textLeft = 0;
                for (int a = 0, N = layout.getLineCount(); a < N; a++) {
                    float lineLeft = layout.getLineLeft(a);
                    textLeft = a == 0 ? (int) Math.ceil(lineLeft) : Math.min(textLeft, (int) Math.ceil(lineLeft));
                    textWidth = (int) Math.ceil(Math.max(layout.getLineWidth(a), textWidth));
                }
                textHeight = layout.getHeight();
            } else {
                textLeft = 0;
                textWidth = 0;
                textHeight = 0;
            }
        }

        public void layout(int left, int top, int right, int bottom) {
            x = left;
            y = top;
        }

        public int getTextX() {
            return x + textX;
        }

        public int getTextY() {
            return y + textY;
        }

        public void setFixedHeight(int value) {
            measuredHeight = fixedHeight;
            if (cell.valign_middle) {
                textY = (measuredHeight - textHeight) / 2;
            } else if (cell.valign_bottom) {
                textY = measuredHeight - textHeight - itemPaddingTop;
            }
        }

        public void draw(Canvas canvas) {
            if (cell == null) {
                return;
            }

            boolean isLastX = x + measuredWidth == TableLayout.this.getMeasuredWidth();
            boolean isLastY = y + measuredHeight == TableLayout.this.getMeasuredHeight();
            int rad = AndroidUtilities.dp(3);
            if (cell.header || isStriped && layoutParams.rowSpec.span.min % 2 == 0) {
                boolean hasCorners = false;
                if (x == 0 && y == 0) {
                    radii[0] = radii[1] = rad;
                    hasCorners = true;
                } else {
                    radii[0] = radii[1] = 0;
                }
                if (isLastX && y == 0) {
                    radii[2] = radii[3] = rad;
                    hasCorners = true;
                } else {
                    radii[2] = radii[3] = 0;
                }
                if (isLastX && isLastY) {
                    radii[4] = radii[5] = rad;
                    hasCorners = true;
                } else {
                    radii[4] = radii[5] = 0;
                }
                if (x == 0 && isLastY) {
                    radii[6] = radii[7] = rad;
                    hasCorners = true;
                } else {
                    radii[6] = radii[7] = 0;
                }
                if (hasCorners) {
                    rect.set(x, y, x + measuredWidth, y + measuredHeight);
                    backgroundPath.reset();
                    backgroundPath.addRoundRect(rect, radii, Path.Direction.CW);
                    if (cell.header) {
                        canvas.drawPath(backgroundPath, delegate.getHeaderPaint());
                    } else {
                        canvas.drawPath(backgroundPath, delegate.getStripPaint());
                    }
                } else {
                    if (cell.header) {
                        canvas.drawRect(x, y, x + measuredWidth, y + measuredHeight, delegate.getHeaderPaint());
                    } else {
                        canvas.drawRect(x, y, x + measuredWidth, y + measuredHeight, delegate.getStripPaint());
                    }
                }
            }
            if (textLayout != null) {
                canvas.save();
                canvas.translate(getTextX(), getTextY());
                if (selectionIndex >= 0) {
                    textSelectionHelper.draw(canvas, (TextSelectionHelper.ArticleSelectableView) getParent().getParent(), selectionIndex);
                }
                textLayout.draw(canvas);
                canvas.restore();
            }
            if (drawLines) {
                Paint linePaint = delegate.getLinePaint();
                Paint halfLinePaint = delegate.getLinePaint();
                float strokeWidth = linePaint.getStrokeWidth() / 2.0f;
                float halfStrokeWidth = halfLinePaint.getStrokeWidth() / 2.0f;

                float start;
                float end;
                if (x == 0) {
                    start = y;
                    end = y + measuredHeight;
                    if (y == 0) {
                        start += rad;
                    }
                    if (end == TableLayout.this.getMeasuredHeight()) {
                        end -= rad;
                    }
                    canvas.drawLine(x + strokeWidth, start, x + strokeWidth, end, linePaint);
                } else {
                    canvas.drawLine(x - halfStrokeWidth, y, x - halfStrokeWidth, y + measuredHeight, halfLinePaint);
                }
                if (y == 0) {
                    start = x;
                    end = x + measuredWidth;
                    if (x == 0) {
                        start += rad;
                    }
                    if (end == TableLayout.this.getMeasuredWidth()) {
                        end -= rad;
                    }
                    canvas.drawLine(start, y + strokeWidth, end, y + strokeWidth, linePaint);
                } else {
                    canvas.drawLine(x, y - halfStrokeWidth, x + measuredWidth, y - halfStrokeWidth, halfLinePaint);
                }

                if (isLastX && y == 0) {
                    start = y + rad;
                } else {
                    start = y - strokeWidth;
                }
                if (isLastX && isLastY) {
                    end = y + measuredHeight - rad;
                } else {
                    end = y + measuredHeight - strokeWidth;
                }
                canvas.drawLine(x + measuredWidth - strokeWidth, start, x + measuredWidth - strokeWidth, end, linePaint);

                if (x == 0 && isLastY) {
                    start = x + rad;
                } else {
                    start = x - strokeWidth;
                }
                if (isLastX && isLastY) {
                    end = x + measuredWidth - rad;
                } else {
                    end = x + measuredWidth - strokeWidth;
                }
                canvas.drawLine(start, y + measuredHeight - strokeWidth, end, y + measuredHeight - strokeWidth, linePaint);

                if (x == 0 && y == 0) {
                    rect.set(x + strokeWidth, y + strokeWidth, x + strokeWidth + rad * 2, y + strokeWidth + rad * 2);
                    canvas.drawArc(rect, -180, 90, false, linePaint);
                }
                if (isLastX && y == 0) {
                    rect.set(x + measuredWidth - strokeWidth - rad * 2, y + strokeWidth, x + measuredWidth - strokeWidth, y + strokeWidth + rad * 2);
                    canvas.drawArc(rect, 0, -90, false, linePaint);
                }
                if (x == 0 && isLastY) {
                    rect.set(x + strokeWidth, y + measuredHeight - strokeWidth - rad * 2, x + strokeWidth + rad * 2, y + measuredHeight - strokeWidth);
                    canvas.drawArc(rect, 180, -90, false, linePaint);
                }
                if (isLastX && isLastY) {
                    rect.set(x + measuredWidth - strokeWidth - rad * 2, y + measuredHeight - strokeWidth - rad * 2, x + measuredWidth - strokeWidth, y + measuredHeight - strokeWidth);
                    canvas.drawArc(rect, 0, 90, false, linePaint);
                }
            }
        }

        public void setSelectionIndex(int selectionIndex) {
            this.selectionIndex = selectionIndex;
        }

        public int getRow() {
           return rowspan + 10;
        }
    }

    public interface TableLayoutDelegate {
        ArticleViewer.DrawingText createTextLayout(TLRPC.TL_pageTableCell cell, int maxWidth);
        Paint getLinePaint();
        Paint getHalfLinePaint();
        Paint getHeaderPaint();
        Paint getStripPaint();
        void onLayoutChild(ArticleViewer.DrawingText text, int x, int y);
    }

    private TableLayoutDelegate delegate;

    private ArrayList<Child> childrens = new ArrayList<>();

    public void addChild(int x, int y, int colspan, int rowspan) {
        Child child = new Child(childrens.size());
        LayoutParams layoutParams = new LayoutParams();
        layoutParams.rowSpec = new Spec(false, new Interval(y, y + rowspan), FILL, 0.0f);
        layoutParams.columnSpec = new Spec(false, new Interval(x, x + colspan), FILL, 0.0f);
        child.layoutParams = layoutParams;
        child.rowspan = y;
        childrens.add(child);
        invalidateStructure();
    }

    public void addChild(TLRPC.TL_pageTableCell cell, int x, int y, int colspan) {
        if (colspan == 0) {
            colspan = 1;
        }
        Child child = new Child(childrens.size());
        child.cell = cell;
        LayoutParams layoutParams = new LayoutParams();
        layoutParams.rowSpec = new Spec(false, new Interval(y, y + (cell.rowspan != 0 ? cell.rowspan : 1)), FILL, 0.0f);
        layoutParams.columnSpec = new Spec(false, new Interval(x, x + colspan), FILL, 1.0f);
        child.layoutParams = layoutParams;
        child.rowspan = y;
        childrens.add(child);
        if (cell.rowspan > 1) {
            rowSpans.add(new Point(y, y + cell.rowspan));
        }
        invalidateStructure();
    }

    public void setDrawLines(boolean value) {
        drawLines = value;
    }

    public void setStriped(boolean value) {
        isStriped = value;
    }

    public void setRtl(boolean value) {
        isRtl = value;
    }

    public void removeAllChildrens() {
        childrens.clear();
        rowSpans.clear();
        invalidateStructure();
    }

    public int getChildCount() {
        return childrens.size();
    }

    public Child getChildAt(int index) {
        if (index < 0 || index >= childrens.size()) {
            return null;
        }
        return childrens.get(index);
    }

    public TableLayout(Context context, TableLayoutDelegate tableLayoutDelegate, TextSelectionHelper.ArticleTextSelectionHelper textSelectionHelper) {
        super(context);
        this.textSelectionHelper = textSelectionHelper;
        setRowCount(DEFAULT_COUNT);
        setColumnCount(DEFAULT_COUNT);
        setOrientation(DEFAULT_ORIENTATION);
        setUseDefaultMargins(DEFAULT_USE_DEFAULT_MARGINS);
        setAlignmentMode(DEFAULT_ALIGNMENT_MODE);
        setRowOrderPreserved(DEFAULT_ORDER_PRESERVED);
        setColumnOrderPreserved(DEFAULT_ORDER_PRESERVED);
        delegate = tableLayoutDelegate;
    }

    public int getOrientation() {
        return mOrientation;
    }

    public void setOrientation(int orientation) {
        if (this.mOrientation != orientation) {
            this.mOrientation = orientation;
            invalidateStructure();
            requestLayout();
        }
    }

    public int getRowCount() {
        return mVerticalAxis.getCount();
    }

    public void setRowCount(int rowCount) {
        mVerticalAxis.setCount(rowCount);
        invalidateStructure();
        requestLayout();
    }

    public int getColumnCount() {
        return mHorizontalAxis.getCount();
    }

    public void setColumnCount(int columnCount) {
        mHorizontalAxis.setCount(columnCount);
        invalidateStructure();
        requestLayout();
    }

    public boolean getUseDefaultMargins() {
        return mUseDefaultMargins;
    }

    public void setUseDefaultMargins(boolean useDefaultMargins) {
        this.mUseDefaultMargins = useDefaultMargins;
        requestLayout();
    }

    public int getAlignmentMode() {
        return mAlignmentMode;
    }

    public void setAlignmentMode(int alignmentMode) {
        this.mAlignmentMode = alignmentMode;
        requestLayout();
    }

    public boolean isRowOrderPreserved() {
        return mVerticalAxis.isOrderPreserved();
    }

    public void setRowOrderPreserved(boolean rowOrderPreserved) {
        mVerticalAxis.setOrderPreserved(rowOrderPreserved);
        invalidateStructure();
        requestLayout();
    }

    public boolean isColumnOrderPreserved() {
        return mHorizontalAxis.isOrderPreserved();
    }

    public void setColumnOrderPreserved(boolean columnOrderPreserved) {
        mHorizontalAxis.setOrderPreserved(columnOrderPreserved);
        invalidateStructure();
        requestLayout();
    }

    static int max2(int[] a, int valueIfEmpty) {
        int result = valueIfEmpty;
        for (int i = 0, N = a.length; i < N; i++) {
            result = max(result, a[i]);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    static <T> T[] append(T[] a, T[] b) {
        T[] result = (T[]) Array.newInstance(a.getClass().getComponentType(), a.length + b.length);
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    static Alignment getAlignment(int gravity, boolean horizontal) {
        int mask = horizontal ? HORIZONTAL_GRAVITY_MASK : VERTICAL_GRAVITY_MASK;
        int shift = horizontal ? AXIS_X_SHIFT : AXIS_Y_SHIFT;
        int flags = (gravity & mask) >> shift;
        switch (flags) {
            case (AXIS_SPECIFIED | AXIS_PULL_BEFORE):
                return horizontal ? LEFT : TOP;
            case (AXIS_SPECIFIED | AXIS_PULL_AFTER):
                return horizontal ? RIGHT : BOTTOM;
            case (AXIS_SPECIFIED | AXIS_PULL_BEFORE | AXIS_PULL_AFTER):
                return FILL;
            case AXIS_SPECIFIED:
                return CENTER;
            case (AXIS_SPECIFIED | AXIS_PULL_BEFORE | RELATIVE_LAYOUT_DIRECTION):
                return START;
            case (AXIS_SPECIFIED | AXIS_PULL_AFTER | RELATIVE_LAYOUT_DIRECTION):
                return END;
            default:
                return UNDEFINED_ALIGNMENT;
        }
    }

    private int getDefaultMargin(Child c, boolean horizontal, boolean leading) {
        return mDefaultGap / 2;
    }

    private int getDefaultMargin(Child c, boolean isAtEdge, boolean horizontal, boolean leading) {
        return getDefaultMargin(c, horizontal, leading);
    }

    private int getDefaultMargin(Child c, LayoutParams p, boolean horizontal, boolean leading) {
        if (!mUseDefaultMargins) {
            return 0;
        }
        Spec spec = horizontal ? p.columnSpec : p.rowSpec;
        Axis axis = horizontal ? mHorizontalAxis : mVerticalAxis;
        Interval span = spec.span;
        boolean leading1 = (horizontal && isRtl) != leading;
        boolean isAtEdge = leading1 ? (span.min == 0) : (span.max == axis.getCount());

        return getDefaultMargin(c, isAtEdge, horizontal, leading);
    }

    int getMargin1(Child view, boolean horizontal, boolean leading) {
        LayoutParams lp = view.getLayoutParams();
        int margin = horizontal ?
                (leading ? lp.leftMargin : lp.rightMargin) :
                (leading ? lp.topMargin : lp.bottomMargin);
        return margin == UNDEFINED ? getDefaultMargin(view, lp, horizontal, leading) : margin;
    }

    private int getMargin(Child view, boolean horizontal, boolean leading) {
        if (mAlignmentMode == ALIGN_MARGINS) {
            return getMargin1(view, horizontal, leading);
        } else {
            Axis axis = horizontal ? mHorizontalAxis : mVerticalAxis;
            int[] margins = leading ? axis.getLeadingMargins() : axis.getTrailingMargins();
            LayoutParams lp = view.getLayoutParams();
            Spec spec = horizontal ? lp.columnSpec : lp.rowSpec;
            int index = leading ? spec.span.min : spec.span.max;
            return margins[index];
        }
    }

    private int getTotalMargin(Child child, boolean horizontal) {
        return getMargin(child, horizontal, true) + getMargin(child, horizontal, false);
    }

    private static boolean fits(int[] a, int value, int start, int end) {
        if (end > a.length) {
            return false;
        }
        for (int i = start; i < end; i++) {
            if (a[i] > value) {
                return false;
            }
        }
        return true;
    }

    private static void procrusteanFill(int[] a, int start, int end, int value) {
        int length = a.length;
        Arrays.fill(a, min(start, length), min(end, length), value);
    }

    private static void setCellGroup(LayoutParams lp, int row, int rowSpan, int col, int colSpan) {
        lp.setRowSpecSpan(new Interval(row, row + rowSpan));
        lp.setColumnSpecSpan(new Interval(col, col + colSpan));
    }

    private static int clip(Interval minorRange, boolean minorWasDefined, int count) {
        int size = minorRange.size();
        if (count == 0) {
            return size;
        }
        int min = minorWasDefined ? min(minorRange.min, count) : 0;
        return min(size, count - min);
    }

    private void validateLayoutParams() {
        final boolean horizontal = (mOrientation == HORIZONTAL);
        final Axis axis = horizontal ? mHorizontalAxis : mVerticalAxis;
        final int count = (axis.definedCount != UNDEFINED) ? axis.definedCount : 0;

        int major = 0;
        int minor = 0;
        int[] maxSizes = new int[count];

        for (int i = 0, N = getChildCount(); i < N; i++) {
            LayoutParams lp = getChildAt(i).getLayoutParams();

            final Spec majorSpec = horizontal ? lp.rowSpec : lp.columnSpec;
            final Interval majorRange = majorSpec.span;
            final boolean majorWasDefined = majorSpec.startDefined;
            final int majorSpan = majorRange.size();
            if (majorWasDefined) {
                major = majorRange.min;
            }

            final Spec minorSpec = horizontal ? lp.columnSpec : lp.rowSpec;
            final Interval minorRange = minorSpec.span;
            final boolean minorWasDefined = minorSpec.startDefined;
            final int minorSpan = clip(minorRange, minorWasDefined, count);
            if (minorWasDefined) {
                minor = minorRange.min;
            }

            if (count != 0) {
                if (!majorWasDefined || !minorWasDefined) {
                    while (!fits(maxSizes, major, minor, minor + minorSpan)) {
                        if (minorWasDefined) {
                            major++;
                        } else {
                            if (minor + minorSpan <= count) {
                                minor++;
                            } else {
                                minor = 0;
                                major++;
                            }
                        }
                    }
                }
                procrusteanFill(maxSizes, minor, minor + minorSpan, major + majorSpan);
            }

            if (horizontal) {
                setCellGroup(lp, major, majorSpan, minor, minorSpan);
            } else {
                setCellGroup(lp, minor, minorSpan, major, majorSpan);
            }

            minor = minor + minorSpan;
        }
    }

    private void invalidateStructure() {
        mLastLayoutParamsHashCode = UNINITIALIZED_HASH;
        mHorizontalAxis.invalidateStructure();
        mVerticalAxis.invalidateStructure();
        invalidateValues();
    }

    private void invalidateValues() {
        if (mHorizontalAxis != null && mVerticalAxis != null) {
            mHorizontalAxis.invalidateValues();
            mVerticalAxis.invalidateValues();
        }
    }

    private static void handleInvalidParams(String msg) {
        throw new IllegalArgumentException(msg + ". ");
    }

    private void checkLayoutParams(LayoutParams lp, boolean horizontal) {
        String groupName = horizontal ? "column" : "row";
        Spec spec = horizontal ? lp.columnSpec : lp.rowSpec;
        Interval span = spec.span;
        if (span.min != UNDEFINED && span.min < 0) {
            handleInvalidParams(groupName + " indices must be positive");
        }
        Axis axis = horizontal ? mHorizontalAxis : mVerticalAxis;
        int count = axis.definedCount;
        if (count != UNDEFINED) {
            if (span.max > count) {
                handleInvalidParams(groupName + " indices (start + span) mustn't exceed the " + groupName + " count");
            }
            if (span.size() > count) {
                handleInvalidParams(groupName + " span mustn't exceed the " + groupName + " count");
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        for (int i = 0, N = getChildCount(); i < N; i++) {
            Child c = getChildAt(i);
            c.draw(canvas);
        }
    }

    private int computeLayoutParamsHashCode() {
        int result = 1;
        for (int i = 0, N = getChildCount(); i < N; i++) {
            Child c = getChildAt(i);
            LayoutParams lp = c.getLayoutParams();
            result = 31 * result + lp.hashCode();
        }
        return result;
    }

    private void consistencyCheck() {
        if (mLastLayoutParamsHashCode == UNINITIALIZED_HASH) {
            validateLayoutParams();
            mLastLayoutParamsHashCode = computeLayoutParamsHashCode();
        } else if (mLastLayoutParamsHashCode != computeLayoutParamsHashCode()) {
            invalidateStructure();
            consistencyCheck();
        }
    }

    private void measureChildWithMargins2(Child child, int parentWidthSpec, int parentHeightSpec, int childWidth, int childHeight, boolean first) {
        child.measure(getTotalMargin(child, true) + childWidth, getTotalMargin(child, false) + childHeight, first);
    }

    private void measureChildrenWithMargins(int widthSpec, int heightSpec, boolean firstPass) {
        int N = getChildCount();
        //float maxWidth = 0;
        for (int i = 0; i < N; i++) {
            Child c = getChildAt(i);
            LayoutParams lp = c.getLayoutParams();
            if (firstPass) {
                int width = MeasureSpec.getSize(widthSpec);
                int maxCellWidth;
                if (colCount == 2) {
                    maxCellWidth = (int) (width / 2.0f) - itemPaddingLeft * 4;
                } else {
                    maxCellWidth = (int) (width / 1.5f);
                }
                c.setTextLayout(delegate.createTextLayout(c.cell, maxCellWidth));
                if (c.textLayout != null) {
                    lp.width = c.textWidth + itemPaddingLeft * 2;
                    lp.height = c.textHeight + itemPaddingTop * 2;
                } else {
                    lp.width = 0;
                    lp.height = 0;
                }
                measureChildWithMargins2(c, widthSpec, heightSpec, lp.width, lp.height, true);
                //maxWidth = Math.max(maxWidth, c.textWidth);
            } else {
                boolean horizontal = (mOrientation == HORIZONTAL);
                Spec spec = horizontal ? lp.columnSpec : lp.rowSpec;
                if (spec.getAbsoluteAlignment(horizontal) == FILL) {
                    Interval span = spec.span;
                    Axis axis = horizontal ? mHorizontalAxis : mVerticalAxis;
                    int[] locations = axis.getLocations();
                    int cellSize = locations[span.max] - locations[span.min];
                    int viewSize = cellSize - getTotalMargin(c, horizontal);
                    if (horizontal) {
                        measureChildWithMargins2(c, widthSpec, heightSpec, viewSize, lp.height, false);
                    } else {
                        measureChildWithMargins2(c, widthSpec, heightSpec, lp.width, viewSize, false);
                    }
                }
            }
        }
        /*if (firstPass) {
            for (int i = 0; i < N; i++) {
                Child c = getChildAt(i);
                LayoutParams lp = c.getLayoutParams();
                lp.columnSpec.weight = c.textWidth / maxWidth;
            }
        }*/
    }

    static int adjust(int measureSpec, int delta) {
        return makeMeasureSpec(MeasureSpec.getSize(measureSpec + delta), MeasureSpec.getMode(measureSpec));
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        consistencyCheck();

        invalidateValues();

        colCount = 0;
        for (int a = 0, N = getChildCount(); a < N; a++) {
            Child child = getChildAt(a);
            colCount = Math.max(colCount, child.layoutParams.columnSpec.span.max);
        }

        measureChildrenWithMargins(widthSpec, heightSpec, true);

        int widthSansPadding;
        int heightSansPadding;

        if (mOrientation == HORIZONTAL) {
            widthSansPadding = mHorizontalAxis.getMeasure(widthSpec);
            measureChildrenWithMargins(widthSpec, heightSpec, false);
            heightSansPadding = mVerticalAxis.getMeasure(heightSpec);
        } else {
            heightSansPadding = mVerticalAxis.getMeasure(heightSpec);
            measureChildrenWithMargins(widthSpec, heightSpec, false);
            widthSansPadding = mHorizontalAxis.getMeasure(widthSpec);
        }

        int measuredWidth = max(widthSansPadding, MeasureSpec.getSize(widthSpec));
        int measuredHeight = max(heightSansPadding, getSuggestedMinimumHeight());
        setMeasuredDimension(measuredWidth, measuredHeight);


        mHorizontalAxis.layout(measuredWidth);
        mVerticalAxis.layout(measuredHeight);

        int[] hLocations = mHorizontalAxis.getLocations();
        int[] vLocations = mVerticalAxis.getLocations();

        int fixedHeight = measuredHeight;

        cellsToFixHeight.clear();
        measuredWidth = hLocations[hLocations.length - 1];
        for (int i = 0, N = getChildCount(); i < N; i++) {
            Child c = getChildAt(i);
            LayoutParams lp = c.getLayoutParams();
            Spec columnSpec = lp.columnSpec;
            Spec rowSpec = lp.rowSpec;

            Interval colSpan = columnSpec.span;
            Interval rowSpan = rowSpec.span;

            int x1 = hLocations[colSpan.min];
            int y1 = vLocations[rowSpan.min];

            int x2 = hLocations[colSpan.max];
            int y2 = vLocations[rowSpan.max];

            int cellWidth = x2 - x1;
            int cellHeight = y2 - y1;

            int pWidth = getMeasurement(c, true);
            int pHeight = getMeasurement(c, false);

            Alignment hAlign = columnSpec.getAbsoluteAlignment(true);
            Alignment vAlign = rowSpec.getAbsoluteAlignment(false);

            Bounds boundsX = mHorizontalAxis.getGroupBounds().getValue(i);
            Bounds boundsY = mVerticalAxis.getGroupBounds().getValue(i);

            int gravityOffsetX = hAlign.getGravityOffset(c, cellWidth - boundsX.size(true));
            int gravityOffsetY = vAlign.getGravityOffset(c, cellHeight - boundsY.size(true));

            int leftMargin = getMargin(c, true, true);
            int topMargin = getMargin(c, false, true);
            int rightMargin = getMargin(c, true, false);
            int bottomMargin = getMargin(c, false, false);

            int sumMarginsX = leftMargin + rightMargin;
            int sumMarginsY = topMargin + bottomMargin;

            int alignmentOffsetX = boundsX.getOffset(this, c, hAlign, pWidth + sumMarginsX, true);
            int alignmentOffsetY = boundsY.getOffset(this, c, vAlign, pHeight + sumMarginsY, false);

            int width = hAlign.getSizeInCell(c, pWidth, cellWidth - sumMarginsX);
            int height = vAlign.getSizeInCell(c, pHeight, cellHeight - sumMarginsY);

            int dx = x1 + gravityOffsetX + alignmentOffsetX;

            int cx = !isRtl ? leftMargin + dx : measuredWidth - width - rightMargin - dx;
            int cy = y1 + gravityOffsetY + alignmentOffsetY + topMargin;

            if (c.cell != null) {
                if (width != c.getMeasuredWidth() || height != c.getMeasuredHeight()) {
                    c.measure(width, height, false);
                }
                if (c.fixedHeight != 0 && c.fixedHeight != height && c.layoutParams.rowSpec.span.max - c.layoutParams.rowSpec.span.min <= 1) {
                    boolean found = false;
                    for (int a = 0, size = rowSpans.size(); a < size; a++) {
                        Point p = rowSpans.get(a);
                        if (p.x <= c.layoutParams.rowSpec.span.min && p.y > c.layoutParams.rowSpec.span.min) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        cellsToFixHeight.add(c);
                    }
                }
            }
            c.layout(cx, cy, cx + width, cy + height);
        }

        for (int a = 0, N = cellsToFixHeight.size(); a < N; a++) {
            Child child = cellsToFixHeight.get(a);
            boolean skip = false;
            int heightDiff = child.measuredHeight - child.fixedHeight;
            for (int i = child.index + 1, size = childrens.size(); i < size; i++) {
                Child next = childrens.get(i);
                if (child.layoutParams.rowSpec.span.min == next.layoutParams.rowSpec.span.min) {
                     if (child.fixedHeight < next.fixedHeight) {
                         skip = true;
                         break;
                     } else {
                         int diff = next.measuredHeight - next.fixedHeight;
                         if (diff > 0) {
                             heightDiff = Math.min(heightDiff, diff);
                         }
                     }
                } else {
                    break;
                }
            }
            if (!skip) {
                for (int i = child.index - 1; i >= 0; i--) {
                    Child next = childrens.get(i);
                    if (child.layoutParams.rowSpec.span.min == next.layoutParams.rowSpec.span.min) {
                        if (child.fixedHeight < next.fixedHeight) {
                            skip = true;
                            break;
                        } else {
                            int diff = next.measuredHeight - next.fixedHeight;
                            if (diff > 0) {
                                heightDiff = Math.min(heightDiff, diff);
                            }
                        }
                    } else {
                        break;
                    }
                }
            }
            if (skip) {
                continue;
            }

            child.setFixedHeight(child.fixedHeight);
            fixedHeight -= heightDiff;

            for (int i = 0, size = childrens.size(); i < size; i++) {
                Child next = childrens.get(i);
                if (child == next) {
                    continue;
                }
                if (child.layoutParams.rowSpec.span.min == next.layoutParams.rowSpec.span.min) {
                    if (next.fixedHeight != next.measuredHeight) {
                        cellsToFixHeight.remove(next);
                        if (next.index < child.index) {
                            a--;
                        }
                        N--;
                    }
                    next.measuredHeight -= heightDiff;
                    next.measure(next.measuredWidth, next.measuredHeight, true);
                } else if (child.layoutParams.rowSpec.span.min < next.layoutParams.rowSpec.span.min) {
                    next.y -= heightDiff;
                }
            }
        }
        for (int i = 0, N = getChildCount(); i < N; i++) {
            Child c = getChildAt(i);
            delegate.onLayoutChild(c.textLayout, c.getTextX(), c.getTextY());
        }
        setMeasuredDimension(measuredWidth, fixedHeight);
    }

    private int getMeasurement(Child c, boolean horizontal) {
        return horizontal ? c.getMeasuredWidth() : c.getMeasuredHeight();
    }

    final int getMeasurementIncludingMargin(Child c, boolean horizontal) {
        return getMeasurement(c, horizontal) + getTotalMargin(c, horizontal);
    }

    @Override
    public void requestLayout() {
        super.requestLayout();
        invalidateValues();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        consistencyCheck();
    }

    final class Axis {
        private static final int NEW = 0;
        private static final int PENDING = 1;
        private static final int COMPLETE = 2;

        public final boolean horizontal;

        public int definedCount = UNDEFINED;
        private int maxIndex = UNDEFINED;

        PackedMap<Spec, Bounds> groupBounds;
        public boolean groupBoundsValid = false;

        PackedMap<Interval, MutableInt> forwardLinks;
        public boolean forwardLinksValid = false;

        PackedMap<Interval, MutableInt> backwardLinks;
        public boolean backwardLinksValid = false;

        public int[] leadingMargins;
        public boolean leadingMarginsValid = false;

        public int[] trailingMargins;
        public boolean trailingMarginsValid = false;

        public Arc[] arcs;
        public boolean arcsValid = false;

        public int[] locations;
        public boolean locationsValid = false;

        public boolean hasWeights;
        public boolean hasWeightsValid = false;
        public int[] deltas;

        boolean orderPreserved = DEFAULT_ORDER_PRESERVED;

        private MutableInt parentMin = new MutableInt(0);
        private MutableInt parentMax = new MutableInt(-MAX_SIZE);

        private Axis(boolean horizontal) {
            this.horizontal = horizontal;
        }

        private int calculateMaxIndex() {
            int result = -1;
            for (int i = 0, N = getChildCount(); i < N; i++) {
                Child c = getChildAt(i);
                LayoutParams params = c.getLayoutParams();
                Spec spec = horizontal ? params.columnSpec : params.rowSpec;
                Interval span = spec.span;
                result = max(result, span.min);
                result = max(result, span.max);
                result = max(result, span.size());
            }
            return result == -1 ? UNDEFINED : result;
        }

        private int getMaxIndex() {
            if (maxIndex == UNDEFINED) {
                maxIndex = max(0, calculateMaxIndex());
            }
            return maxIndex;
        }

        public int getCount() {
            return max(definedCount, getMaxIndex());
        }

        public void setCount(int count) {
            if (count != UNDEFINED && count < getMaxIndex()) {
                handleInvalidParams((horizontal ? "column" : "row") + "Count must be greater than or equal to the maximum of all grid indices (and spans) defined in the LayoutParams of each child");
            }
            this.definedCount = count;
        }

        public boolean isOrderPreserved() {
            return orderPreserved;
        }

        public void setOrderPreserved(boolean orderPreserved) {
            this.orderPreserved = orderPreserved;
            invalidateStructure();
        }

        private PackedMap<Spec, Bounds> createGroupBounds() {
            Assoc<Spec, Bounds> assoc = Assoc.of(Spec.class, Bounds.class);
            for (int i = 0, N = getChildCount(); i < N; i++) {
                Child c = getChildAt(i);
                LayoutParams lp = c.getLayoutParams();
                Spec spec = horizontal ? lp.columnSpec : lp.rowSpec;
                Bounds bounds = spec.getAbsoluteAlignment(horizontal).getBounds();
                assoc.put(spec, bounds);
            }
            return assoc.pack();
        }

        private void computeGroupBounds() {
            Bounds[] values = groupBounds.values;
            for (int i = 0; i < values.length; i++) {
                values[i].reset();
            }
            for (int i = 0, N = getChildCount(); i < N; i++) {
                Child c = getChildAt(i);
                LayoutParams lp = c.getLayoutParams();
                Spec spec = horizontal ? lp.columnSpec : lp.rowSpec;
                int size = getMeasurementIncludingMargin(c, horizontal) + (spec.weight == 0 ? 0 : deltas[i]);
                groupBounds.getValue(i).include(TableLayout.this, c, spec, this, size);
            }
        }

        public PackedMap<Spec, Bounds> getGroupBounds() {
            if (groupBounds == null) {
                groupBounds = createGroupBounds();
            }
            if (!groupBoundsValid) {
                computeGroupBounds();
                groupBoundsValid = true;
            }
            return groupBounds;
        }

        private PackedMap<Interval, MutableInt> createLinks(boolean min) {
            Assoc<Interval, MutableInt> result = Assoc.of(Interval.class, MutableInt.class);
            Spec[] keys = getGroupBounds().keys;
            for (int i = 0, N = keys.length; i < N; i++) {
                Interval span = min ? keys[i].span : keys[i].span.inverse();
                result.put(span, new MutableInt());
            }
            return result.pack();
        }

        private void computeLinks(PackedMap<Interval, MutableInt> links, boolean min) {
            MutableInt[] spans = links.values;
            for (int i = 0; i < spans.length; i++) {
                spans[i].reset();
            }

            Bounds[] bounds = getGroupBounds().values;
            for (int i = 0; i < bounds.length; i++) {
                int size = bounds[i].size(min);
                MutableInt valueHolder = links.getValue(i);
                valueHolder.value = max(valueHolder.value, min ? size : -size);
            }
        }

        private PackedMap<Interval, MutableInt> getForwardLinks() {
            if (forwardLinks == null) {
                forwardLinks = createLinks(true);
            }
            if (!forwardLinksValid) {
                computeLinks(forwardLinks, true);
                forwardLinksValid = true;
            }
            return forwardLinks;
        }

        private PackedMap<Interval, MutableInt> getBackwardLinks() {
            if (backwardLinks == null) {
                backwardLinks = createLinks(false);
            }
            if (!backwardLinksValid) {
                computeLinks(backwardLinks, false);
                backwardLinksValid = true;
            }
            return backwardLinks;
        }

        private void include(List<Arc> arcs, Interval key, MutableInt size, boolean ignoreIfAlreadyPresent) {
            if (key.size() == 0) {
                return;
            }
            if (ignoreIfAlreadyPresent) {
                for (Arc arc : arcs) {
                    Interval span = arc.span;
                    if (span.equals(key)) {
                        return;
                    }
                }
            }
            arcs.add(new Arc(key, size));
        }

        private void include(List<Arc> arcs, Interval key, MutableInt size) {
            include(arcs, key, size, true);
        }

        Arc[][] groupArcsByFirstVertex(Arc[] arcs) {
            int N = getCount() + 1;
            Arc[][] result = new Arc[N][];
            int[] sizes = new int[N];
            for (Arc arc : arcs) {
                sizes[arc.span.min]++;
            }
            for (int i = 0; i < sizes.length; i++) {
                result[i] = new Arc[sizes[i]];
            }
            Arrays.fill(sizes, 0);
            for (Arc arc : arcs) {
                int i = arc.span.min;
                result[i][sizes[i]++] = arc;
            }

            return result;
        }

        private Arc[] topologicalSort(final Arc[] arcs) {
            return new Object() {
                Arc[] result = new Arc[arcs.length];
                int cursor = result.length - 1;
                Arc[][] arcsByVertex = groupArcsByFirstVertex(arcs);
                int[] visited = new int[getCount() + 1];

                void walk(int loc) {
                    switch (visited[loc]) {
                        case NEW: {
                            visited[loc] = PENDING;
                            for (Arc arc : arcsByVertex[loc]) {
                                walk(arc.span.max);
                                result[cursor--] = arc;
                            }
                            visited[loc] = COMPLETE;
                            break;
                        }
                        case PENDING: {
                            break;
                        }
                        case COMPLETE: {
                            break;
                        }
                    }
                }

                Arc[] sort() {
                    for (int loc = 0, N = arcsByVertex.length; loc < N; loc++) {
                        walk(loc);
                    }
                    return result;
                }
            }.sort();
        }

        private Arc[] topologicalSort(List<Arc> arcs) {
            return topologicalSort(arcs.toArray(new Arc[0]));
        }

        private void addComponentSizes(List<Arc> result, PackedMap<Interval, MutableInt> links) {
            for (int i = 0; i < links.keys.length; i++) {
                Interval key = links.keys[i];
                include(result, key, links.values[i], false);
            }
        }

        private Arc[] createArcs() {
            List<Arc> mins = new ArrayList<>();
            List<Arc> maxs = new ArrayList<>();

            addComponentSizes(mins, getForwardLinks());
            addComponentSizes(maxs, getBackwardLinks());

            if (orderPreserved) {
                for (int i = 0; i < getCount(); i++) {
                    include(mins, new Interval(i, i + 1), new MutableInt(0));
                }
            }

            int N = getCount();
            include(mins, new Interval(0, N), parentMin, false);
            include(maxs, new Interval(N, 0), parentMax, false);

            Arc[] sMins = topologicalSort(mins);
            Arc[] sMaxs = topologicalSort(maxs);

            return append(sMins, sMaxs);
        }

        private void computeArcs() {
            getForwardLinks();
            getBackwardLinks();
        }

        public Arc[] getArcs() {
            if (arcs == null) {
                arcs = createArcs();
            }
            if (!arcsValid) {
                computeArcs();
                arcsValid = true;
            }
            return arcs;
        }

        private boolean relax(int[] locations, Arc entry) {
            if (!entry.valid) {
                return false;
            }
            Interval span = entry.span;
            int u = span.min;
            int v = span.max;
            int value = entry.value.value;
            int candidate = locations[u] + value;
            if (candidate > locations[v]) {
                locations[v] = candidate;
                return true;
            }
            return false;
        }

        private void init(int[] locations) {
            Arrays.fill(locations, 0);
        }

        private boolean solve(Arc[] arcs, int[] locations) {
            return solve(arcs, locations, true);
        }

        private boolean solve(Arc[] arcs, int[] locations, boolean modifyOnError) {
            int N = getCount() + 1;

            for (int p = 0; p < arcs.length; p++) {
                init(locations);

                for (int i = 0; i < N; i++) {
                    boolean changed = false;
                    for (int j = 0, length = arcs.length; j < length; j++) {
                        changed |= relax(locations, arcs[j]);
                    }
                    if (!changed) {
                        return true;
                    }
                }

                if (!modifyOnError) {
                    return false;
                }

                boolean[] culprits = new boolean[arcs.length];
                for (int i = 0; i < N; i++) {
                    for (int j = 0, length = arcs.length; j < length; j++) {
                        culprits[j] |= relax(locations, arcs[j]);
                    }
                }

                for (int i = 0; i < arcs.length; i++) {
                    if (culprits[i]) {
                        Arc arc = arcs[i];
                        if (arc.span.min < arc.span.max) {
                            continue;
                        }
                        arc.valid = false;
                        break;
                    }
                }
            }
            return true;
        }

        private void computeMargins(boolean leading) {
            int[] margins = leading ? leadingMargins : trailingMargins;
            for (int i = 0, N = getChildCount(); i < N; i++) {
                Child c = getChildAt(i);
                LayoutParams lp = c.getLayoutParams();
                Spec spec = horizontal ? lp.columnSpec : lp.rowSpec;
                Interval span = spec.span;
                int index = leading ? span.min : span.max;
                margins[index] = max(margins[index], getMargin1(c, horizontal, leading));
            }
        }

        public int[] getLeadingMargins() {
            if (leadingMargins == null) {
                leadingMargins = new int[getCount() + 1];
            }
            if (!leadingMarginsValid) {
                computeMargins(true);
                leadingMarginsValid = true;
            }
            return leadingMargins;
        }

        public int[] getTrailingMargins() {
            if (trailingMargins == null) {
                trailingMargins = new int[getCount() + 1];
            }
            if (!trailingMarginsValid) {
                computeMargins(false);
                trailingMarginsValid = true;
            }
            return trailingMargins;
        }

        private boolean solve(int[] a) {
            return solve(getArcs(), a);
        }

        private boolean computeHasWeights() {
            for (int i = 0, N = getChildCount(); i < N; i++) {
                final Child child = getChildAt(i);
                LayoutParams lp = child.getLayoutParams();
                Spec spec = horizontal ? lp.columnSpec : lp.rowSpec;
                if (spec.weight != 0) {
                    return true;
                }
            }
            return false;
        }

        private boolean hasWeights() {
            if (!hasWeightsValid) {
                hasWeights = computeHasWeights();
                hasWeightsValid = true;
            }
            return hasWeights;
        }

        public int[] getDeltas() {
            if (deltas == null) {
                deltas = new int[getChildCount()];
            }
            return deltas;
        }

        private void shareOutDelta(int totalDelta, float totalWeight) {
            Arrays.fill(deltas, 0);
            for (int i = 0, N = getChildCount(); i < N; i++) {
                final Child c = getChildAt(i);
                LayoutParams lp = c.getLayoutParams();
                Spec spec = horizontal ? lp.columnSpec : lp.rowSpec;
                float weight = spec.weight;
                if (weight != 0) {
                    int delta = Math.round((weight * totalDelta / totalWeight));
                    deltas[i] = delta;
                    totalDelta -= delta;
                    totalWeight -= weight;
                }
            }
        }

        private void solveAndDistributeSpace(int[] a) {
            Arrays.fill(getDeltas(), 0);
            solve(a);
            int deltaMax = parentMin.value * getChildCount() + 1;
            if (deltaMax < 2) {
                return;
            }
            int deltaMin = 0;

            float totalWeight = calculateTotalWeight();

            int validDelta = -1;
            boolean validSolution = true;
            while (deltaMin < deltaMax) {
                final int delta = (int) (((long) deltaMin + deltaMax) / 2);
                invalidateValues();
                shareOutDelta(delta, totalWeight);
                validSolution = solve(getArcs(), a, false);
                if (validSolution) {
                    validDelta = delta;
                    deltaMin = delta + 1;
                } else {
                    deltaMax = delta;
                }
            }
            if (validDelta > 0 && !validSolution) {
                invalidateValues();
                shareOutDelta(validDelta, totalWeight);
                solve(a);
            }
        }

        private float calculateTotalWeight() {
            float totalWeight = 0f;
            for (int i = 0, N = getChildCount(); i < N; i++) {
                Child c = getChildAt(i);
                LayoutParams lp = c.getLayoutParams();
                Spec spec = horizontal ? lp.columnSpec : lp.rowSpec;
                totalWeight += spec.weight;
            }
            return totalWeight;
        }

        private void computeLocations(int[] a) {
            if (!hasWeights()) {
                solve(a);
            } else {
                solveAndDistributeSpace(a);
            }
            if (!orderPreserved) {
                int a0 = a[0];
                for (int i = 0, N = a.length; i < N; i++) {
                    a[i] = a[i] - a0;
                }
            }
        }

        public int[] getLocations() {
            if (locations == null) {
                int N = getCount() + 1;
                locations = new int[N];
            }
            if (!locationsValid) {
                computeLocations(locations);
                locationsValid = true;
            }
            return locations;
        }

        private int size(int[] locations) {
            return locations[getCount()];
        }

        private void setParentConstraints(int min, int max) {
            parentMin.value = min;
            parentMax.value = -max;
            locationsValid = false;
        }

        private int getMeasure(int min, int max) {
            setParentConstraints(min, max);
            return size(getLocations());
        }

        public int getMeasure(int measureSpec) {
            int mode = MeasureSpec.getMode(measureSpec);
            int size = MeasureSpec.getSize(measureSpec);
            switch (mode) {
                case MeasureSpec.UNSPECIFIED: {
                    return getMeasure(0, MAX_SIZE);
                }
                case EXACTLY: {
                    return getMeasure(size, size);
                }
                case MeasureSpec.AT_MOST: {
                    return getMeasure(0, size);
                }
                default: {
                    return 0;
                }
            }
        }

        public void layout(int size) {
            setParentConstraints(size, size);
            getLocations();
        }

        public void invalidateStructure() {
            maxIndex = UNDEFINED;

            groupBounds = null;
            forwardLinks = null;
            backwardLinks = null;

            leadingMargins = null;
            trailingMargins = null;
            arcs = null;

            locations = null;

            deltas = null;
            hasWeightsValid = false;

            invalidateValues();
        }

        public void invalidateValues() {
            groupBoundsValid = false;
            forwardLinksValid = false;
            backwardLinksValid = false;

            leadingMarginsValid = false;
            trailingMarginsValid = false;
            arcsValid = false;

            locationsValid = false;
        }
    }

    public static class LayoutParams extends ViewGroup.MarginLayoutParams {

        private static final int DEFAULT_WIDTH = WRAP_CONTENT;
        private static final int DEFAULT_HEIGHT = WRAP_CONTENT;
        private static final int DEFAULT_MARGIN = UNDEFINED;
        private static final Interval DEFAULT_SPAN = new Interval(UNDEFINED, UNDEFINED + 1);
        private static final int DEFAULT_SPAN_SIZE = DEFAULT_SPAN.size();

        public Spec rowSpec = Spec.UNDEFINED;
        public Spec columnSpec = Spec.UNDEFINED;

        private LayoutParams(int width, int height, int left, int top, int right, int bottom, Spec rowSpec, Spec columnSpec) {
            super(width, height);
            setMargins(left, top, right, bottom);
            this.rowSpec = rowSpec;
            this.columnSpec = columnSpec;
        }

        public LayoutParams(Spec rowSpec, Spec columnSpec) {
            this(DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_MARGIN, DEFAULT_MARGIN, DEFAULT_MARGIN, DEFAULT_MARGIN, rowSpec, columnSpec);
        }

        public LayoutParams() {
            this(Spec.UNDEFINED, Spec.UNDEFINED);
        }

        public LayoutParams(ViewGroup.LayoutParams params) {
            super(params);
        }

        public LayoutParams(ViewGroup.MarginLayoutParams params) {
            super(params);
        }

        public LayoutParams(LayoutParams source) {
            super(source);
            this.rowSpec = source.rowSpec;
            this.columnSpec = source.columnSpec;
        }

        public void setGravity(int gravity) {
            rowSpec = rowSpec.copyWriteAlignment(getAlignment(gravity, false));
            columnSpec = columnSpec.copyWriteAlignment(getAlignment(gravity, true));
        }

        final void setRowSpecSpan(Interval span) {
            rowSpec = rowSpec.copyWriteSpan(span);
        }

        final void setColumnSpecSpan(Interval span) {
            columnSpec = columnSpec.copyWriteSpan(span);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            LayoutParams that = (LayoutParams) o;

            if (!columnSpec.equals(that.columnSpec)) return false;
            if (!rowSpec.equals(that.rowSpec)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = rowSpec.hashCode();
            result = 31 * result + columnSpec.hashCode();
            return result;
        }
    }

    final static class Arc {
        public final Interval span;
        public final MutableInt value;
        public boolean valid = true;

        public Arc(Interval span, MutableInt value) {
            this.span = span;
            this.value = value;
        }
    }

    final static class MutableInt {
        public int value;

        public MutableInt() {
            reset();
        }

        public MutableInt(int value) {
            this.value = value;
        }

        public void reset() {
            value = Integer.MIN_VALUE;
        }
    }

    final static class Assoc<K, V> extends ArrayList<Pair<K, V>> {
        private final Class<K> keyType;
        private final Class<V> valueType;

        private Assoc(Class<K> keyType, Class<V> valueType) {
            this.keyType = keyType;
            this.valueType = valueType;
        }

        public static <K, V> Assoc<K, V> of(Class<K> keyType, Class<V> valueType) {
            return new Assoc<>(keyType, valueType);
        }

        public void put(K key, V value) {
            add(Pair.create(key, value));
        }

        @SuppressWarnings(value = "unchecked")
        public PackedMap<K, V> pack() {
            int N = size();
            K[] keys = (K[]) Array.newInstance(keyType, N);
            V[] values = (V[]) Array.newInstance(valueType, N);
            for (int i = 0; i < N; i++) {
                keys[i] = get(i).first;
                values[i] = get(i).second;
            }
            return new PackedMap<>(keys, values);
        }
    }

    @SuppressWarnings(value = "unchecked")
    final static class PackedMap<K, V> {
        public final int[] index;
        public final K[] keys;
        public final V[] values;

        private PackedMap(K[] keys, V[] values) {
            this.index = createIndex(keys);

            this.keys = compact(keys, index);
            this.values = compact(values, index);
        }

        public V getValue(int i) {
            return values[index[i]];
        }

        private static <K> int[] createIndex(K[] keys) {
            int size = keys.length;
            int[] result = new int[size];

            Map<K, Integer> keyToIndex = new HashMap<>();
            for (int i = 0; i < size; i++) {
                K key = keys[i];
                Integer index = keyToIndex.get(key);
                if (index == null) {
                    index = keyToIndex.size();
                    keyToIndex.put(key, index);
                }
                result[i] = index;
            }
            return result;
        }

        private static <K> K[] compact(K[] a, int[] index) {
            int size = a.length;
            Class<?> componentType = a.getClass().getComponentType();
            K[] result = (K[]) Array.newInstance(componentType, max2(index, -1) + 1);

            for (int i = 0; i < size; i++) {
                result[index[i]] = a[i];
            }
            return result;
        }
    }

    static class Bounds {
        public int before;
        public int after;
        public int flexibility;

        private Bounds() {
            reset();
        }

        protected void reset() {
            before = Integer.MIN_VALUE;
            after = Integer.MIN_VALUE;
            flexibility = CAN_STRETCH;
        }

        protected void include(int before, int after) {
            this.before = max(this.before, before);
            this.after = max(this.after, after);
        }

        protected int size(boolean min) {
            if (!min) {
                if (canStretch(flexibility)) {
                    return MAX_SIZE;
                }
            }
            return before + after;
        }

        protected int getOffset(TableLayout gl, Child c, Alignment a, int size, boolean horizontal) {
            return before - a.getAlignmentValue(c, size);
        }

        protected final void include(TableLayout gl, Child c, Spec spec, Axis axis, int size) {
            this.flexibility &= spec.getFlexibility();
            boolean horizontal = axis.horizontal;
            Alignment alignment = spec.getAbsoluteAlignment(axis.horizontal);
            int before = alignment.getAlignmentValue(c, size);
            include(before, size - before);
        }
    }

    final static class Interval {

        public final int min;
        public final int max;

        public Interval(int min, int max) {
            this.min = min;
            this.max = max;
        }

        int size() {
            return max - min;
        }

        Interval inverse() {
            return new Interval(max, min);
        }

        @Override
        public boolean equals(Object that) {
            if (this == that) {
                return true;
            }
            if (that == null || getClass() != that.getClass()) {
                return false;
            }

            Interval interval = (Interval) that;

            if (max != interval.max) {
                return false;
            }
            if (min != interval.min) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = min;
            result = 31 * result + max;
            return result;
        }
    }

    public static class Spec {
        static final Spec UNDEFINED = spec(TableLayout.UNDEFINED);
        static final float DEFAULT_WEIGHT = 0;

        final boolean startDefined;
        final Interval span;
        final Alignment alignment;
        float weight;

        private Spec(boolean startDefined, Interval span, Alignment alignment, float weight) {
            this.startDefined = startDefined;
            this.span = span;
            this.alignment = alignment;
            this.weight = weight;
        }

        private Spec(boolean startDefined, int start, int size, Alignment alignment, float weight) {
            this(startDefined, new Interval(start, start + size), alignment, weight);
        }

        private Alignment getAbsoluteAlignment(boolean horizontal) {
            if (alignment != UNDEFINED_ALIGNMENT) {
                return alignment;
            }
            if (weight == 0f) {
                return horizontal ? START : BASELINE;
            }
            return FILL;
        }

        final Spec copyWriteSpan(Interval span) {
            return new Spec(startDefined, span, alignment, weight);
        }

        final Spec copyWriteAlignment(Alignment alignment) {
            return new Spec(startDefined, span, alignment, weight);
        }

        final int getFlexibility() {
            return (alignment == UNDEFINED_ALIGNMENT && weight == 0) ? INFLEXIBLE : CAN_STRETCH;
        }

        @Override
        public boolean equals(Object that) {
            if (this == that) {
                return true;
            }
            if (that == null || getClass() != that.getClass()) {
                return false;
            }

            Spec spec = (Spec) that;

            if (!alignment.equals(spec.alignment)) {
                return false;
            }
            if (!span.equals(spec.span)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = span.hashCode();
            result = 31 * result + alignment.hashCode();
            return result;
        }
    }

    public static Spec spec(int start, int size, Alignment alignment, float weight) {
        return new Spec(start != UNDEFINED, start, size, alignment, weight);
    }

    public static Spec spec(int start, Alignment alignment, float weight) {
        return spec(start, 1, alignment, weight);
    }

    public static Spec spec(int start, int size, float weight) {
        return spec(start, size, UNDEFINED_ALIGNMENT, weight);
    }

    public static Spec spec(int start, float weight) {
        return spec(start, 1, weight);
    }

    public static Spec spec(int start, int size, Alignment alignment) {
        return spec(start, size, alignment, Spec.DEFAULT_WEIGHT);
    }

    public static Spec spec(int start, Alignment alignment) {
        return spec(start, 1, alignment);
    }

    public static Spec spec(int start, int size) {
        return spec(start, size, UNDEFINED_ALIGNMENT);
    }

    public static Spec spec(int start) {
        return spec(start, 1);
    }

    public static abstract class Alignment {
        Alignment() {
        }

        abstract int getGravityOffset(Child view, int cellDelta);

        abstract int getAlignmentValue(Child view, int viewSize);

        int getSizeInCell(Child view, int viewSize, int cellSize) {
            return viewSize;
        }

        Bounds getBounds() {
            return new Bounds();
        }
    }

    static final Alignment UNDEFINED_ALIGNMENT = new Alignment() {
        @Override
        int getGravityOffset(Child view, int cellDelta) {
            return UNDEFINED;
        }

        @Override
        public int getAlignmentValue(Child view, int viewSize) {
            return UNDEFINED;
        }
    };

    private static final Alignment LEADING = new Alignment() {
        @Override
        int getGravityOffset(Child view, int cellDelta) {
            return 0;
        }

        @Override
        public int getAlignmentValue(Child view, int viewSize) {
            return 0;
        }
    };

    private static final Alignment TRAILING = new Alignment() {
        @Override
        int getGravityOffset(Child view, int cellDelta) {
            return cellDelta;
        }

        @Override
        public int getAlignmentValue(Child view, int viewSize) {
            return viewSize;
        }
    };

    public static final Alignment TOP = LEADING;
    public static final Alignment BOTTOM = TRAILING;
    public static final Alignment START = LEADING;
    public static final Alignment END = TRAILING;

    private static Alignment createSwitchingAlignment(final Alignment ltr) {
        return new Alignment() {
            @Override
            int getGravityOffset(Child view, int cellDelta) {
                return ltr.getGravityOffset(view, cellDelta);
            }

            @Override
            public int getAlignmentValue(Child view, int viewSize) {
                return ltr.getAlignmentValue(view, viewSize);
            }
        };
    }

    public static final Alignment LEFT = createSwitchingAlignment(START);
    public static final Alignment RIGHT = createSwitchingAlignment(END);
    public static final Alignment CENTER = new Alignment() {
        @Override
        int getGravityOffset(Child view, int cellDelta) {
            return cellDelta >> 1;
        }

        @Override
        public int getAlignmentValue(Child view, int viewSize) {
            return viewSize >> 1;
        }
    };

    public static final Alignment BASELINE = new Alignment() {
        @Override
        int getGravityOffset(Child view, int cellDelta) {
            return 0;
        }

        @Override
        public int getAlignmentValue(Child view, int viewSize) {
            return UNDEFINED;
        }

        @Override
        public Bounds getBounds() {
            return new Bounds() {
                private int size;

                @Override
                protected void reset() {
                    super.reset();
                    size = Integer.MIN_VALUE;
                }

                @Override
                protected void include(int before, int after) {
                    super.include(before, after);
                    size = max(size, before + after);
                }

                @Override
                protected int size(boolean min) {
                    return max(super.size(min), size);
                }

                @Override
                protected int getOffset(TableLayout gl, Child c, Alignment a, int size, boolean hrz) {
                    return max(0, super.getOffset(gl, c, a, size, hrz));
                }
            };
        }
    };

    public static final Alignment FILL = new Alignment() {
        @Override
        int getGravityOffset(Child view, int cellDelta) {
            return 0;
        }

        @Override
        public int getAlignmentValue(Child view, int viewSize) {
            return UNDEFINED;
        }

        @Override
        public int getSizeInCell(Child view, int viewSize, int cellSize) {
            return cellSize;
        }
    };

    static boolean canStretch(int flexibility) {
        return (flexibility & CAN_STRETCH) != 0;
    }

    private static final int INFLEXIBLE = 0;
    private static final int CAN_STRETCH = 2;
}
