package org.telegram.ui.iv;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

public class RichDetailsEndCell extends View implements Theme.Colorable {

    private final Theme.ResourcesProvider resourcesProvider;
    private final Paint paint = new Paint();
    private BlockRow currentRow;

    public RichDetailsEndCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        updateColors();
    }

    public void bind(BlockRow row) {
        this.currentRow = row;
    }

    public BlockRow getRow() {
        return currentRow;
    }

    @Override
    public void updateColors() {
        paint.setColor(Theme.getColor(Theme.key_divider, resourcesProvider));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(6) + 1);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, dp(6), getMeasuredWidth(), dp(6) + 1, paint);
    }

    public static final class Factory extends UItem.UItemFactory<RichDetailsEndCell> {
        static { setup(new Factory()); }

        @Override
        public RichDetailsEndCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new RichDetailsEndCell(context, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            ((RichDetailsEndCell) view).bind((BlockRow) item.object);
        }

        public static UItem of(BlockRow row) {
            final UItem item = UItem.ofFactory(Factory.class);
            item.object = row;
            return item;
        }

        @Override
        public boolean isClickable() {
            return false;
        }
    }
}
