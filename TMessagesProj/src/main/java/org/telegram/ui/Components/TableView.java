package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class TableView extends TableLayout {

    private final Theme.ResourcesProvider resourcesProvider;

    private final Path path = new Path();
    private final float[] radii = new float[8];
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final float w = Math.max(1, dp(.66f));
    private final float hw = w / 2f;

    public TableView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setClipToPadding(false);
        setColumnStretchable(1, true);
    }

    public void addRow(CharSequence title, View content) {
        TableRow row = new TableRow(getContext());
        TableRow.LayoutParams lp;
        lp = new TableRow.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        row.addView(new TableRowTitle(this, title), lp);
        lp = new TableRow.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        row.addView(new TableRowContent(this, content), lp);
        addView(row);
    }

    public void addRowUnpadded(CharSequence title, View content) {
        TableRow row = new TableRow(getContext());
        TableRow.LayoutParams lp;
        lp = new TableRow.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        row.addView(new TableRowTitle(this, title), lp);
        lp = new TableRow.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        row.addView(new TableRowContent(this, content, true), lp);
        addView(row);
    }

    public void addRow(CharSequence title, CharSequence text) {
        TextView textView = new TextView(getContext());
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setText(text);

        TableRow row = new TableRow(getContext());
        TableRow.LayoutParams lp;
        lp = new TableRow.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        row.addView(new TableRowTitle(this, title), lp);
        lp = new TableRow.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        row.addView(new TableRowContent(this, textView), lp);
        addView(row);
    }

    public static class TableRowTitle extends TextView {

        private final TableView table;
        private final Theme.ResourcesProvider resourcesProvider;

        public TableRowTitle(TableView table, CharSequence title) {
            super(table.getContext());
            this.table = table;
            this.resourcesProvider = table.resourcesProvider;

            setPadding(dp(12.66f), dp(9.33f), dp(12.66f), dp(9.33f));
            setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            setTypeface(AndroidUtilities.bold());
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            setText(title);
        }

        private boolean first, last;

        public void setFirstLast(boolean first, boolean last) {
            if (this.first != first || this.last != last) {
                this.first = first;
                this.last = last;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (first || last) {
                final float r = dp(4);
                table.radii[0] = table.radii[1] = first ? r : 0; // top left
                table.radii[2] = table.radii[3] = 0; // top right
                table.radii[4] = table.radii[5] = 0; // bottom right
                table.radii[6] = table.radii[7] = last ? r : 0; // bottom left
                table.path.rewind();
                AndroidUtilities.rectTmp.set(table.hw, table.hw, getWidth() + table.hw, getHeight() + table.hw * dp(last ? -1 : +1));
                table.path.addRoundRect(AndroidUtilities.rectTmp, table.radii, Path.Direction.CW);
                canvas.drawPath(table.path, table.backgroundPaint);
                canvas.drawPath(table.path, table.borderPaint);
            } else {
                canvas.drawRect(table.hw, table.hw, getWidth() + table.hw, getHeight() + table.hw, table.backgroundPaint);
                canvas.drawRect(table.hw, table.hw, getWidth() + table.hw, getHeight() + table.hw, table.borderPaint);
            }
            super.onDraw(canvas);
        }
    }

    public static class TableRowContent extends FrameLayout {

        private final TableView table;
        private final Theme.ResourcesProvider resourcesProvider;

        public TableRowContent(TableView table, View content) {
            this(table, content, false);
        }

        public TableRowContent(TableView table, View content, boolean unpadded) {
            super(table.getContext());
            this.table = table;
            this.resourcesProvider = table.resourcesProvider;

            setWillNotDraw(false);
            if (!unpadded) {
                setPadding(dp(12.66f), dp(9.33f), dp(12.66f), dp(9.33f));
            }
            addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }

        private boolean first, last;

        public void setFirstLast(boolean first, boolean last) {
            if (this.first != first || this.last != last) {
                this.first = first;
                this.last = last;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (first || last) {
                final float r = dp(4);
                table.radii[0] = table.radii[1] = 0; // top left
                table.radii[2] = table.radii[3] = first ? r : 0; // top right
                table.radii[4] = table.radii[5] = last ? r : 0; // bottom right
                table.radii[6] = table.radii[7] = 0; // bottom left
                table.path.rewind();
                AndroidUtilities.rectTmp.set(table.hw, table.hw, getWidth() - table.hw, getHeight() + table.hw * dp(last ? -1f : +1f));
                table.path.addRoundRect(AndroidUtilities.rectTmp, table.radii, Path.Direction.CW);
                canvas.drawPath(table.path, table.borderPaint);
            } else {
                canvas.drawRect(table.hw, table.hw, getWidth() - table.hw, getHeight() + table.hw, table.borderPaint);
            }
            super.onDraw(canvas);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(w);
        borderPaint.setColor(Theme.getColor(Theme.key_table_border, resourcesProvider));
        backgroundPaint.setStyle(Paint.Style.FILL);
        backgroundPaint.setColor(Theme.getColor(Theme.key_table_background, resourcesProvider));

        final int height = getChildCount();
        for (int y = 0; y < height; ++y) {
            if (!(getChildAt(y) instanceof TableRow))
                continue;
            TableRow row = (TableRow) getChildAt(y);
            final int width = row.getChildCount();
            for (int x = 0; x < width; ++x) {
                View child = row.getChildAt(x);
                if (child instanceof TableRowTitle) {
                    ((TableRowTitle) child).setFirstLast(y == 0, y == height - 1);
                } else if (child instanceof TableRowContent) {
                    ((TableRowContent) child).setFirstLast(y == 0, y == height - 1);
                }
            }
        }
    }

}
