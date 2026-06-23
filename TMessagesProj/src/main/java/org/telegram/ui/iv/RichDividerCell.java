package org.Tajgram.ui.iv;

import static org.Tajgram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

import org.Tajgram.ui.ActionBar.Theme;
import org.Tajgram.ui.Cells.TextSelectionHelper;
import org.Tajgram.ui.Components.RecyclerListView;
import org.Tajgram.ui.Components.UItem;
import org.Tajgram.ui.Components.UniversalAdapter;
import org.Tajgram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class RichDividerCell extends View
    implements Theme.Colorable, TextSelectionHelper.ArticleSelectableView {

    public interface Delegate {
        TextSelectionHelper.ArticleTextSelectionHelper getSelectionHelper();
    }

    private final Theme.ResourcesProvider resourcesProvider;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint stubPaint = new TextPaint();
    private Layout stubLayout;

    private BlockRow currentRow;
    private Delegate delegate;

    public RichDividerCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        stubPaint.setTextSize(1);
        updateColors();
    }

    public void bind(BlockRow row, Delegate delegate) {
        this.currentRow = row;
        this.delegate = delegate;
    }

    public BlockRow getRow() {
        return currentRow;
    }

    @Override
    public void updateColors() {
        paint.setColor(Theme.getColor(Theme.key_divider, resourcesProvider));
        selectionPaint.setColor(Theme.getColor(Theme.key_chat_inTextSelectionHighlight, resourcesProvider));
    }

    @Override
    public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> out) {
        if (stubLayout == null) {
            int w = Math.max(1, getMeasuredWidth());
            stubLayout = new StaticLayout("•", stubPaint, w, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
        }
        final Layout layout = stubLayout;
        out.add(new TextSelectionHelper.TextLayoutBlock() {
            @Override public Layout getLayout() { return layout; }
            @Override public int getX() { return 0; }
            @Override public int getY() { return 0; }
            @Override public int getRow() { return 0; }
        });
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(24));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        stubLayout = null;
    }

    private boolean isCellSelected() {
        if (delegate == null) return false;
        TextSelectionHelper.ArticleTextSelectionHelper helper = delegate.getSelectionHelper();
        if (helper == null || !helper.isInSelectionMode()) return false;
        if (!(getParent() instanceof androidx.recyclerview.widget.RecyclerView)) return false;
        int myPos = ((androidx.recyclerview.widget.RecyclerView) getParent()).getChildAdapterPosition(this);
        if (myPos < 0) return false;
        return myPos >= helper.getStartCell() && myPos <= helper.getEndCell();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isCellSelected()) {
            canvas.drawRoundRect(
                dp(8), dp(2), getMeasuredWidth() - dp(8), getMeasuredHeight() - dp(2),
                dp(6), dp(6),
                selectionPaint
            );
        }
        int cy = getMeasuredHeight() / 2;
        canvas.drawRect(dp(16), cy - 1, getMeasuredWidth() - dp(16), cy + 1, paint);
    }

    public static final class Factory extends UItem.UItemFactory<RichDividerCell> {
        static { setup(new Factory()); }

        @Override
        public RichDividerCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new RichDividerCell(context, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            ((RichDividerCell) view).bind((BlockRow) item.object, (Delegate) item.object2);
        }

        public static UItem of(BlockRow row, Delegate delegate) {
            final UItem item = UItem.ofFactory(Factory.class);
            item.object = row;
            item.object2 = delegate;
            return item;
        }

        @Override
        public boolean isClickable() {
            return false;
        }
    }
}
