package org.telegram.ui.iv;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class RichDividerCell extends RichBlockCell
    implements Theme.Colorable, TextSelectionHelper.ArticleSelectableView {

    public interface Delegate {
        TextSelectionHelper.ArticleTextSelectionHelper getSelectionHelper();
    }

    private final Theme.ResourcesProvider resourcesProvider;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Delegate delegate;
    private boolean blockRtl;

    public RichDividerCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setWillNotDraw(false);
        updateColors();
    }

    @Override
    protected void onBlockInsetChanged(int px) { invalidate(); }

    public void bind(BlockRow row, Delegate delegate) {
        this.currentRow = row;
        this.delegate = delegate;
        blockRtl = RichBlockChrome.rtl();
        bindBlockInset(row);
    }

    private int regionLo() { return blockRtl ? 0 : blockInset(); }
    private int regionHi() { return getMeasuredWidth() - (blockRtl ? blockInset() : 0); }

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
        final int lo = regionLo();
        final int third = Math.max(1, (regionHi() - lo) / 3);
        out.add(RichBlockSelection.of(lo + third - dp(12), dp(2), lo + third * 2 + dp(12), dp(16)));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(18));
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
        final int lo = regionLo();
        final int third = Math.max(1, (regionHi() - lo) / 3);
        final int x1 = lo + third;
        final int x2 = lo + third * 2;
        paint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_chat_inReplyMessageText, resourcesProvider), 0.2f));
        if (isCellSelected()) {
            canvas.drawRoundRect(
                x1 - dp(12), dp(2), x2 + dp(12), dp(16),
                dp(6), dp(6),
                selectionPaint
            );
        }
        AndroidUtilities.rectTmp.set(x1, dp(8), x2, dp(10));
        canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(1), dp(1), paint);
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
