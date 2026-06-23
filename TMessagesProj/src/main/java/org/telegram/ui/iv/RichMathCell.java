package org.Tajgram.ui.iv;

import static org.Tajgram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.RecyclerView;

import org.Tajgram.messenger.FileLog;
import org.Tajgram.tgnet.tl.TL_iv;
import org.Tajgram.ui.ActionBar.Theme;
import org.Tajgram.ui.Cells.TextSelectionHelper;
import org.Tajgram.ui.Components.LayoutHelper;
import org.Tajgram.ui.Components.RecyclerListView;
import org.Tajgram.ui.Components.UItem;
import org.Tajgram.ui.Components.UniversalAdapter;
import org.Tajgram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

import ru.noties.jlatexmath.JLatexMathDrawable;

public class RichMathCell extends FrameLayout
    implements Theme.Colorable, TextSelectionHelper.ArticleSelectableView {

    public interface Delegate {
        void onEditMath(BlockRow row);
        TextSelectionHelper.ArticleTextSelectionHelper getSelectionHelper();
    }

    private static final int PLACEHOLDER_HEIGHT_DP = 64;

    private final Theme.ResourcesProvider resourcesProvider;
    private final Paint mathPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint stubPaint = new TextPaint();
    private final TextPaint placeholderPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final View clickView;

    private Layout stubLayout;
    private Bitmap bitmap;
    private int paintColor = 0;

    private BlockRow currentRow;
    private Delegate delegate;

    public RichMathCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setWillNotDraw(false);

        stubPaint.setTextSize(1);
        placeholderPaint.setTextSize(dp(15));
        placeholderPaint.setTextAlign(Paint.Align.CENTER);

        setPadding(0, dp(6), 0, dp(6));

        clickView = new View(context);
        clickView.setOnClickListener(v -> {
            if (currentRow != null && delegate != null) {
                delegate.onEditMath(currentRow);
            }
        });
        addView(clickView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        updateColors();
    }

    public void bind(BlockRow row, Delegate delegate) {
        this.currentRow = row;
        this.delegate = delegate;
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
        final String source = getSource();
        if (!TextUtils.isEmpty(source)) {
            try {
                final JLatexMathDrawable drawable =
                    JLatexMathDrawable.builder(source)
                        .textSize(dp(20))
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
        requestLayout();
        invalidate();
    }

    @Override
    public void updateColors() {
        backgroundPaint.setColor(Theme.getColor(Theme.key_chat_inFileBackground, resourcesProvider));
        selectionPaint.setColor(Theme.getColor(Theme.key_chat_inTextSelectionHighlight, resourcesProvider));
        placeholderPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), 0.5f));
        paintColor = 0;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h;
        if (bitmap != null) {
            h = bitmap.getHeight() + getPaddingTop() + getPaddingBottom();
        } else {
            h = dp(PLACEHOLDER_HEIGHT_DP) + getPaddingTop() + getPaddingBottom();
        }
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int contentTop = getPaddingTop();
        final int contentBottom = getMeasuredHeight() - getPaddingBottom();

        if (bitmap != null) {
            if (paintColor != Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider)) {
                mathPaint.setColor(paintColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            }
            canvas.drawBitmap(bitmap, (getMeasuredWidth() - bitmap.getWidth()) / 2f, contentTop, mathPaint);
        } else {
            canvas.drawRect(dp(16), contentTop, getMeasuredWidth() - dp(16), contentBottom, backgroundPaint);
            final String hint = "Tap to add an equation";
            final float ty = contentTop + (contentBottom - contentTop) / 2f - (placeholderPaint.descent() + placeholderPaint.ascent()) / 2f;
            canvas.drawText(hint, getMeasuredWidth() / 2f, ty, placeholderPaint);
        }

        if (isCellSelected()) {
            canvas.drawRect(dp(16), contentTop, getMeasuredWidth() - dp(16), contentBottom, selectionPaint);
        }
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
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        stubLayout = null;
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
