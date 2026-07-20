package org.telegram.ui.iv;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

import ru.noties.jlatexmath.JLatexMathDrawable;

public class RichMathCell extends RichBlockCell
    implements Theme.Colorable, TextSelectionHelper.ArticleSelectableView {

    public interface Delegate {
        TextSelectionHelper.ArticleTextSelectionHelper getSelectionHelper();
    }

    private final Theme.ResourcesProvider resourcesProvider;
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final HorizontalScrollView scrollView;
    private final ImageView image;

    private Bitmap bitmap;
    private int paintColor = 0;

    private Delegate delegate;

    public RichMathCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setWillNotDraw(false);
        setBlockPadding(0, dp(6), 0, dp(6));

        image = new ImageView(context);

        final FrameLayout mathContainer = new FrameLayout(context);
        mathContainer.addView(image, new FrameLayout.LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        scrollView = new HorizontalScrollView(context);
        scrollView.setHorizontalScrollBarEnabled(false);
        scrollView.setClipToPadding(false);
        scrollView.setPadding(dp(16), 0, dp(16), 0);
        scrollView.setFillViewport(true);
        scrollView.addView(mathContainer, new FrameLayout.LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        updateColors();
    }

    public void bind(BlockRow row, Delegate delegate) {
        this.currentRow = row;
        this.delegate = delegate;
        bindBlockInset(row);
        rebuild();
    }

    public BlockRow getRow() {
        return currentRow;
    }

    private String getSource() {
        if (currentRow != null && currentRow.block instanceof TL_iv.pageBlockMath) {
            return ((TL_iv.pageBlockMath) currentRow.block).source;
        }
        return null;
    }

    public void rebuild() {
        bitmap = null;
        scrollView.scrollTo(0, 0);
        final String source = getSource();
        if (!TextUtils.isEmpty(source)) {
            try {
                final JLatexMathDrawable drawable =
                    JLatexMathDrawable.builder(source)
                        .textSize(dp(4 + SharedConfig.fontSize))
                        .build();
                final int w = drawable.getIntrinsicWidth();
                final int h = drawable.getIntrinsicHeight();
                if (w > 0 && h > 0) {
                    final Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8);
                    drawable.setBounds(0, 0, w, h);
                    drawable.draw(new Canvas(bm));
                    bitmap = bm;
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        image.setImageBitmap(bitmap);
        invalidate();
    }

    @Override
    public void updateColors() {
        selectionPaint.setColor(Theme.getColor(Theme.key_chat_inTextSelectionHighlight, resourcesProvider));
        paintColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        image.setColorFilter(new PorterDuffColorFilter(paintColor, PorterDuff.Mode.SRC_IN));
        invalidate();
    }

    private boolean isScrollable() {
        return bitmap != null && image.getWidth() > scrollView.getWidth();
    }

    private void selectionRect(int[] out) {
        final int top = getPaddingTop();
        final int bottom = getHeight() - getPaddingBottom();
        if (isScrollable()) {
            out[0] = getPaddingLeft();
            out[1] = top;
            out[2] = getWidth() - getPaddingRight();
            out[3] = bottom;
        } else {
            final int bw = bitmap != null ? bitmap.getWidth() : Math.max(1, getWidth() / 2);
            final int left = (getWidth() - bw) / 2;
            out[0] = left - dp(4);
            out[1] = top;
            out[2] = left + bw + dp(4);
            out[3] = bottom;
        }
    }

    private final int[] rect = new int[4];

    @Override
    protected void onDraw(Canvas canvas) {
        if (paintColor != Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider)) {
            updateColors();
        }
        if (bitmap != null && isCellSelected()) {
            selectionRect(rect);
            canvas.drawRoundRect(rect[0], rect[1], rect[2], rect[3], dp(4), dp(4), selectionPaint);
        }
    }

    public boolean isPressOnMath(int localX, int localY) {
        if (bitmap == null) return false;
        final int[] r = new int[4];
        selectionRect(r);
        return localX >= r[0] && localX <= r[2] && localY >= r[1] && localY <= r[3];
    }

    private boolean isCellSelected() {
        if (delegate == null) return false;
        TextSelectionHelper.ArticleTextSelectionHelper helper = delegate.getSelectionHelper();
        if (helper == null || !helper.isInSelectionMode()) return false;
        if (!(getParent() instanceof RecyclerView)) return false;
        int myPos = ((RecyclerView) getParent()).getChildAdapterPosition(this);
        if (myPos < 0) return false;
        return myPos >= helper.getStartCell() && myPos <= helper.getEndCell();
    }

    @Override
    public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> out) {
        selectionRect(rect);
        out.add(RichBlockSelection.of(rect[0], rect[1], rect[2], rect[3]));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
    }

    public static final class Factory extends UItem.UItemFactory<RichMathCell> {
        static { setup(new Factory()); }

        @Override
        public RichMathCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new RichMathCell(context, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            ((RichMathCell) view).bind((BlockRow) item.object, (Delegate) item.object2);
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
