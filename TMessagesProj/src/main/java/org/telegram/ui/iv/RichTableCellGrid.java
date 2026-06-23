package org.Tajgram.ui.iv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;

import org.Tajgram.tgnet.tl.TL_iv;
import org.Tajgram.ui.Components.AnimatedFloat;
import org.Tajgram.ui.Components.CubicBezierInterpolator;

import org.Tajgram.messenger.AndroidUtilities;
import org.Tajgram.ui.ActionBar.Theme;

public class RichTableCellGrid extends ViewGroup {

    private TableModel model;
    private Theme.ResourcesProvider resourcesProvider;

    private int[] colWidths = new int[0];
    private int[] rowHeights = new int[0];
    private int[] colStarts = new int[0];
    private int[] rowStarts = new int[0];

    public interface CellSelectionProvider {
        boolean isSelected(TL_iv.pageTableCell cell);
    }

    private CellSelectionProvider selectionProvider;

    public void setSelectionProvider(CellSelectionProvider provider) {
        this.selectionProvider = provider;
        invalidate();
    }

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stripPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF selRect = new RectF();

    private AnimatedFloat selectionFade;
    private int selectedFillBaseAlpha;
    private int selectedStrokeBaseAlpha;

    private static final int MIN_COL_DP = 80;
    public static final int GRID_PADDING_DP = 4;

    public RichTableCellGrid(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setWillNotDraw(false);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(AndroidUtilities.dp(1));
        selectedStrokePaint.setStyle(Paint.Style.STROKE);
        selectedStrokePaint.setStrokeWidth(AndroidUtilities.dp(2));
        selectedStrokePaint.setStrokeJoin(Paint.Join.ROUND);
        selectedStrokePaint.setStrokeCap(Paint.Cap.ROUND);

        selectionFade = new AnimatedFloat(this, 0L, 220L, CubicBezierInterpolator.EASE_OUT_QUINT);

        applyColors();
    }

    public void applyColors() {
        linePaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteInputField, resourcesProvider));
        int tint = Theme.getColor(Theme.key_switchTrack, resourcesProvider);
        int r = Color.red(tint);
        int g = Color.green(tint);
        int b = Color.blue(tint);
        headerPaint.setColor(Color.argb(34, r, g, b));
        stripPaint.setColor(Color.argb(20, r, g, b));
        int sel = Theme.getColor(Theme.key_chat_inTextSelectionHighlight, resourcesProvider);
        selectedFillBaseAlpha = 80;
        selectedStrokeBaseAlpha = 200;
        selectedPaint.setColor(Color.argb(selectedFillBaseAlpha, Color.red(sel), Color.green(sel), Color.blue(sel)));
        selectedStrokePaint.setColor(Color.argb(selectedStrokeBaseAlpha, Color.red(sel), Color.green(sel), Color.blue(sel)));
        invalidate();
    }

    public void setModel(TableModel model) {
        this.model = model;
        rebuildHosts();
    }

    public TableModel getModel() {
        return model;
    }

    public RichTableCellHost hostForAnchor(TL_iv.pageTableCell anchor) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof RichTableCellHost && ((RichTableCellHost) child).cell == anchor) {
                return (RichTableCellHost) child;
            }
        }
        return null;
    }

    private void rebuildHosts() {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (child instanceof RichTableCellHost) removeViewAt(i);
        }
        if (model == null) return;
        for (int i = 0, n = model.anchors().size(); i < n; i++) {
            final TL_iv.pageTableCell cell = model.anchors().get(i);
            final RichTableCellHost host = new RichTableCellHost(getContext(), resourcesProvider);
            host.bind(cell);
            addView(host);
        }
    }

    public void rebindAfterModelChange() {
        rebuildHosts();
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int pad = AndroidUtilities.dp(GRID_PADDING_DP);
        if (model == null || model.rowCount == 0 || model.colCount == 0) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), pad * 2);
            colWidths = new int[0];
            rowHeights = new int[0];
            colStarts = new int[0];
            rowStarts = new int[0];
            return;
        }

        final int parentWidth = MeasureSpec.getSize(widthMeasureSpec) - pad * 2;
        final int rowCount = model.rowCount;
        final int colCount = model.colCount;

        colWidths = new int[colCount];
        rowHeights = new int[rowCount];

        int equalWidth = Math.max(AndroidUtilities.dp(MIN_COL_DP), parentWidth / Math.max(colCount, 1));
        for (int c = 0; c < colCount; c++) colWidths[c] = equalWidth;

        int hostUnspec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (!(child instanceof RichTableCellHost)) continue;
            RichTableCellHost host = (RichTableCellHost) child;
            int aR = model.anchorRowOf(host.cell);
            int aC = model.anchorColOf(host.cell);
            int cs = TableModel.spanCol(host.cell);

            int hostWidth = 0;
            for (int cc = aC; cc < aC + cs && cc < colCount; cc++) hostWidth += colWidths[cc];

            int spec = MeasureSpec.makeMeasureSpec(hostWidth, MeasureSpec.EXACTLY);
            host.measure(spec, hostUnspec);

            int rs = TableModel.spanRow(host.cell);
            if (rs == 1) {
                if (host.getMeasuredHeight() > rowHeights[aR]) {
                    rowHeights[aR] = host.getMeasuredHeight();
                }
            }
        }

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (!(child instanceof RichTableCellHost)) continue;
            RichTableCellHost host = (RichTableCellHost) child;
            int aR = model.anchorRowOf(host.cell);
            int rs = TableModel.spanRow(host.cell);
            if (rs <= 1) continue;
            int totalRows = 0;
            for (int rr = aR; rr < aR + rs && rr < rowCount; rr++) totalRows += rowHeights[rr];
            int needed = host.getMeasuredHeight();
            if (needed > totalRows) {
                int deficit = needed - totalRows;
                int per = deficit / Math.max(rs, 1);
                int rem = deficit % Math.max(rs, 1);
                for (int rr = aR; rr < aR + rs && rr < rowCount; rr++) {
                    rowHeights[rr] += per + (rem > 0 ? 1 : 0);
                    if (rem > 0) rem--;
                }
            }
        }

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (!(child instanceof RichTableCellHost)) continue;
            RichTableCellHost host = (RichTableCellHost) child;
            int aR = model.anchorRowOf(host.cell);
            int aC = model.anchorColOf(host.cell);
            int cs = TableModel.spanCol(host.cell);
            int rs = TableModel.spanRow(host.cell);
            int w = 0;
            for (int cc = aC; cc < aC + cs && cc < colCount; cc++) w += colWidths[cc];
            int h = 0;
            for (int rr = aR; rr < aR + rs && rr < rowCount; rr++) h += rowHeights[rr];
            host.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                         MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        }

        colStarts = new int[colCount + 1];
        colStarts[0] = pad;
        for (int c = 0; c < colCount; c++) colStarts[c + 1] = colStarts[c] + colWidths[c];
        rowStarts = new int[rowCount + 1];
        rowStarts[0] = pad;
        for (int r = 0; r < rowCount; r++) rowStarts[r + 1] = rowStarts[r] + rowHeights[r];

        int totalW = colStarts[colCount] + pad;
        int totalH = rowStarts[rowCount] + pad;

        setMeasuredDimension(Math.max(totalW, parentWidth + pad * 2), totalH);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (model == null) return;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (!(child instanceof RichTableCellHost)) continue;
            RichTableCellHost host = (RichTableCellHost) child;
            int aR = model.anchorRowOf(host.cell);
            int aC = model.anchorColOf(host.cell);
            if (aR < 0 || aC < 0) continue;
            int x = colStarts[aC];
            int y = rowStarts[aR];
            host.layout(x, y, x + host.getMeasuredWidth(), y + host.getMeasuredHeight());
        }
    }

    public int contentRight() {
        if (model == null || model.colCount == 0) return 0;
        return colStarts[model.colCount];
    }

    public int contentBottom() {
        if (model == null || model.rowCount == 0) return 0;
        return rowStarts[model.rowCount];
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        drawCellBackgrounds(canvas);
        drawSelectionFill(canvas);
        super.dispatchDraw(canvas);
        if (model != null && model.block != null && model.block.bordered) {
            drawBorders(canvas);
        }
        drawSelectionOutline(canvas);
    }

    private void drawCellBackgrounds(Canvas canvas) {
        if (model == null) return;
        boolean striped = model.block != null && model.block.striped;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (!(child instanceof RichTableCellHost)) continue;
            RichTableCellHost host = (RichTableCellHost) child;
            int aR = model.anchorRowOf(host.cell);
            int aC = model.anchorColOf(host.cell);
            if (aR < 0 || aC < 0) continue;
            int cs = TableModel.spanCol(host.cell);
            int rs = TableModel.spanRow(host.cell);
            int x = colStarts[aC];
            int y = rowStarts[aR];
            int x2 = colStarts[Math.min(aC + cs, model.colCount)];
            int y2 = rowStarts[Math.min(aR + rs, model.rowCount)];
            if (host.cell.header) {
                canvas.drawRect(x, y, x2, y2, headerPaint);
            } else if (striped && aR % 2 == 0) {
                canvas.drawRect(x, y, x2, y2, stripPaint);
            }
        }
    }

    private boolean isSelected(int r, int c) {
        if (model == null || selectionProvider == null) return false;
        if (r < 0 || r >= model.rowCount || c < 0 || c >= model.colCount) return false;
        return selectionProvider.isSelected(model.grid[r][c]);
    }

    private boolean hasAnySelection() {
        if (model == null || selectionProvider == null) return false;
        for (TL_iv.pageTableCell c : model.anchors()) {
            if (selectionProvider.isSelected(c)) return true;
        }
        return false;
    }

    private void drawSelectionFill(Canvas canvas) {
        if (model == null) return;
        float v = selectionFade.set(hasAnySelection() ? 1f : 0f);
        if (v <= 0.001f) return;
        selectedPaint.setAlpha((int) (selectedFillBaseAlpha * v));
        for (TL_iv.pageTableCell cell : model.anchors()) {
            if (selectionProvider == null || !selectionProvider.isSelected(cell)) continue;
            int aR = model.anchorRowOf(cell);
            int aC = model.anchorColOf(cell);
            if (aR < 0 || aC < 0) continue;
            int cs = TableModel.spanCol(cell);
            int rs = TableModel.spanRow(cell);
            int x = colStarts[aC];
            int y = rowStarts[aR];
            int x2 = colStarts[Math.min(aC + cs, model.colCount)];
            int y2 = rowStarts[Math.min(aR + rs, model.rowCount)];
            canvas.drawRect(x, y, x2, y2, selectedPaint);
        }
    }

    private void drawSelectionOutline(Canvas canvas) {
        if (model == null) return;
        float v = selectionFade.get();
        if (v <= 0.001f) return;
        selectedStrokePaint.setAlpha((int) (selectedStrokeBaseAlpha * v));
        selectedStrokePaint.setStrokeWidth(AndroidUtilities.dp(2) * Math.max(0.4f, v));

        for (int r = 0; r <= model.rowCount; r++) {
            int y = r < model.rowCount ? rowStarts[r] : rowStarts[model.rowCount];
            int xStart = -1;
            for (int c = 0; c < model.colCount; c++) {
                boolean above = isSelected(r - 1, c);
                boolean below = isSelected(r, c);
                if (above != below) {
                    if (xStart < 0) xStart = colStarts[c];
                } else if (xStart >= 0) {
                    canvas.drawLine(xStart, y, colStarts[c], y, selectedStrokePaint);
                    xStart = -1;
                }
            }
            if (xStart >= 0) {
                canvas.drawLine(xStart, y, colStarts[model.colCount], y, selectedStrokePaint);
            }
        }
        for (int c = 0; c <= model.colCount; c++) {
            int x = c < model.colCount ? colStarts[c] : colStarts[model.colCount];
            int yStart = -1;
            for (int r = 0; r < model.rowCount; r++) {
                boolean leftSel = isSelected(r, c - 1);
                boolean rightSel = isSelected(r, c);
                if (leftSel != rightSel) {
                    if (yStart < 0) yStart = rowStarts[r];
                } else if (yStart >= 0) {
                    canvas.drawLine(x, yStart, x, rowStarts[r], selectedStrokePaint);
                    yStart = -1;
                }
            }
            if (yStart >= 0) {
                canvas.drawLine(x, yStart, x, rowStarts[model.rowCount], selectedStrokePaint);
            }
        }
    }

    private void drawBorders(Canvas canvas) {
        float halfStroke = linePaint.getStrokeWidth() / 2f;
        float r = AndroidUtilities.dpf2(3);

        selRect.set(colStarts[0] + halfStroke, rowStarts[0] + halfStroke,
            colStarts[model.colCount] - halfStroke, rowStarts[model.rowCount] - halfStroke);
        canvas.drawRoundRect(selRect, r, r, linePaint);

        for (int c = 1; c < model.colCount; c++) {
            int x = colStarts[c];
            int yStart = -1;
            for (int rr = 0; rr < model.rowCount; rr++) {
                boolean draw = model.grid[rr][c - 1] != model.grid[rr][c];
                if (draw) {
                    if (yStart < 0) yStart = rowStarts[rr];
                } else if (yStart >= 0) {
                    canvas.drawLine(x, yStart, x, rowStarts[rr], linePaint);
                    yStart = -1;
                }
            }
            if (yStart >= 0) {
                canvas.drawLine(x, yStart, x, rowStarts[model.rowCount], linePaint);
            }
        }
        for (int rr = 1; rr < model.rowCount; rr++) {
            int y = rowStarts[rr];
            int xStart = -1;
            for (int c = 0; c < model.colCount; c++) {
                boolean draw = model.grid[rr - 1][c] != model.grid[rr][c];
                if (draw) {
                    if (xStart < 0) xStart = colStarts[c];
                } else if (xStart >= 0) {
                    canvas.drawLine(xStart, y, colStarts[c], y, linePaint);
                    xStart = -1;
                }
            }
            if (xStart >= 0) {
                canvas.drawLine(xStart, y, colStarts[model.colCount], y, linePaint);
            }
        }
    }

    public int columnStart(int c) {
        if (c < 0 || c >= colStarts.length) return 0;
        return colStarts[c];
    }

    public int rowStart(int r) {
        if (r < 0 || r >= rowStarts.length) return 0;
        return rowStarts[r];
    }
}
