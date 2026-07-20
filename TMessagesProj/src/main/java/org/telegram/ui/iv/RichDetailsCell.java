package org.telegram.ui.iv;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.Layout;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.AnimatedArrowDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

public class RichDetailsCell extends FrameLayout implements Theme.Colorable, TextSelectionHelper.ArticleSelectableView {

    public interface Delegate {
        void onToggle(BlockRow row);
        void onTitleChanged(BlockRow row);
        default void onTitleEnter(BlockRow row) {}
        default void onTitleBackspace(BlockRow row) {}
        default void onSpansChanged(BlockRow row) {}
        default void onRequestWindowFocusable(RichEditText editText, boolean showKeyboard) {}
        default void onLockedInsert(CharSequence text) {}
        default boolean onSelectAll(BlockRow row) { return false; }
        TextSelectionHelper.ArticleTextSelectionHelper getSelectionHelper();
    }

    private final Theme.ResourcesProvider resourcesProvider;
    private final View arrowView;
    private final AnimatedArrowDrawable arrow;
    private final Drawable.Callback arrowCallback;
    private final RichEditText editText;
    private final Paint dividerPaint = new Paint();

    private BlockRow currentRow;
    private Delegate delegate;
    private boolean hijackingSelection;

    public RichDetailsCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setPadding(dp(16), 0, dp(16), 0);
        setClipToPadding(false);
        setWillNotDraw(false);

        arrow = new AnimatedArrowDrawable(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider), true);
        arrowCallback = new Drawable.Callback() {
            @Override public void invalidateDrawable(Drawable who) { arrowView.invalidate(); }
            @Override public void scheduleDrawable(Drawable who, Runnable what, long when) {}
            @Override public void unscheduleDrawable(Drawable who, Runnable what) {}
        };
        arrow.setCallback(arrowCallback);
        arrowView = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                canvas.save();
                canvas.translate(dp(2), (getMeasuredHeight() - dp(13)) / 2f);
                arrow.draw(canvas);
                canvas.restore();
            }
        };
        arrowView.setOnClickListener(v -> {
            if (delegate != null && currentRow != null) delegate.onToggle(currentRow);
        });
        addView(arrowView, LayoutHelper.createFrame(28, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

        editText = new RichEditText(context, resourcesProvider);
        editText.setAllowNewlines(false);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, SharedConfig.fontSize);
        editText.setHint(getString(R.string.ArticleHintDetailsTitle));
        editText.setPadding(dp(2), dp(10), dp(2), dp(10));
        editText.setListener(new RichEditText.Listener() {
            @Override
            public void onTextChanged(RichEditText et, Editable text) {
                if (currentRow != null && currentRow.block instanceof TL_iv.pageBlockDetails) {
                    ((TL_iv.pageBlockDetails) currentRow.block).title = RichTextStyle.fromSpannable(text);
                }
                if (delegate != null && currentRow != null) delegate.onTitleChanged(currentRow);
            }

            @Override
            public void onEnterPressed(RichEditText et) {
                if (delegate != null && currentRow != null) delegate.onTitleEnter(currentRow);
            }

            @Override
            public void onBackspaceOnEmpty(RichEditText et) {
                if (delegate != null && currentRow != null) delegate.onTitleBackspace(currentRow);
            }

            @Override
            public boolean onBackspaceAtStart(RichEditText et) {
                if (delegate != null && currentRow != null && et.length() == 0) {
                    delegate.onTitleBackspace(currentRow);
                    return true;
                }
                return false;
            }

            @Override
            public void onRequestWindowFocusable(RichEditText et, boolean showKeyboard) {
                if (delegate != null) delegate.onRequestWindowFocusable(et, showKeyboard);
            }

            @Override
            public void onLockedInsert(RichEditText et, CharSequence text) {
                if (delegate != null) delegate.onLockedInsert(text);
            }

            @Override
            public boolean onSelectAll(RichEditText et) {
                if (delegate != null && currentRow != null) return delegate.onSelectAll(currentRow);
                return false;
            }

            @Override
            public void onSelectionChanged(RichEditText et, int selStart, int selEnd) {
                if (hijackingSelection || selStart == selEnd || delegate == null) return;
                final TextSelectionHelper.ArticleTextSelectionHelper helper = delegate.getSelectionHelper();
                if (helper == null) return;
                if (helper.isInSelectionMode() && helper.getSelectedCell() == RichDetailsCell.this) return;
                final int s = selStart, e = selEnd;
                post(() -> {
                    if (et.length() < e || et.getSelectionStart() == et.getSelectionEnd()) return;
                    if (helper.selectRangeOf(RichDetailsCell.this, s, e)) {
                        hijackingSelection = true;
                        et.setSelection(e);
                        hijackingSelection = false;
                    }
                });
            }
        });
        editText.setDelegate(() -> {
            if (currentRow != null && currentRow.block instanceof TL_iv.pageBlockDetails) {
                ((TL_iv.pageBlockDetails) currentRow.block).title = RichTextStyle.fromSpannable(editText.getText());
            }
            if (delegate != null && currentRow != null) delegate.onSpansChanged(currentRow);
        });
        addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 28 + 6, 0, 0, 0));

        updateColors();
    }

    public void bind(BlockRow row, Delegate delegate) {
        this.currentRow = row;
        this.delegate = delegate;
        if (!(row.block instanceof TL_iv.pageBlockDetails)) return;
        final TL_iv.pageBlockDetails details = (TL_iv.pageBlockDetails) row.block;
        arrow.setAnimationProgressAnimated(details.open ? 0.0f : 1.0f);
        final CharSequence styled = RichTextStyle.toSpannable(details.title);
        if (!String.valueOf(editText.getText()).equals(RichTextStyle.plainOf(details.title))) {
            editText.setTextSilently(styled);
            editText.invalidateEffects();
        }
    }

    public BlockRow getRow() {
        return currentRow;
    }

    public RichEditText getEditText() {
        return editText;
    }

    public void requestEditFocus() {
        editText.requestEditFocus();
    }

    public void setLocked(boolean locked) {
        editText.setLocked(locked);
    }

    public boolean isPressOnArrow(int localX, int localY) {
        return localX >= arrowView.getLeft() && localX < arrowView.getRight();
    }

    public boolean isPressOnText(int localX, int localY) {
        final Layout layout = editText.getLayout();
        if (layout == null || editText.length() == 0) return false;
        final int textX = localX - (editText.getLeft() + editText.getPaddingLeft());
        final int textY = localY - (editText.getTop() + editText.getPaddingTop());
        if (textY < 0 || textY >= layout.getHeight()) return false;
        final int line = layout.getLineForVertical(textY);
        if (line < 0 || line >= layout.getLineCount()) return false;
        final int extend = dp(24);
        final int contentWidth = Math.max(0, editText.getWidth() - editText.getPaddingLeft() - editText.getPaddingRight());
        final float left = Math.max(0, layout.getLineLeft(line) - extend);
        final float right = Math.min(contentWidth, layout.getLineRight(line) + extend);
        return textX >= left && textX <= right;
    }

    // See RichTextCell#isPressOnEmptyEditText: on an empty title, skip the block drag so the system
    // can show the insertion handle + Paste toolbar.
    public boolean isPressOnEmptyEditText(int localX, int localY) {
        if (editText.length() != 0) return false;
        return localX >= editText.getLeft() && localX <= editText.getLeft() + editText.getWidth()
            && localY >= editText.getTop() && localY <= editText.getTop() + editText.getHeight();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), heightMeasureSpec);
    }

    @Override
    public void updateColors() {
        editText.updateColors();
        arrow.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        dividerPaint.setColor(Theme.getColor(Theme.key_divider, resourcesProvider));
    }

    @Override
    public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> out) {
        final Layout layout = editText.getLayout();
        if (layout == null) return;
        final int textX = editText.getLeft() + editText.getPaddingLeft();
        final int textY = editText.getTop() + editText.getPaddingTop();
        out.add(new TextSelectionHelper.TextLayoutBlock() {
            @Override public Layout getLayout() { return layout; }
            @Override public int getX() { return textX; }
            @Override public int getY() { return textY; }
            @Override public int getRow() { return 0; }
            @Override public CharSequence getText() {
                return currentRow != null && currentRow.block instanceof TL_iv.pageBlockDetails
                    ? RichTextStyle.toSpannable(((TL_iv.pageBlockDetails) currentRow.block).title) : "";
            }
        });
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // The divider only marks a collapsed section's edge; an open one already shows its end view.
        if (currentRow != null && currentRow.block instanceof TL_iv.pageBlockDetails
                && ((TL_iv.pageBlockDetails) currentRow.block).open) {
            return;
        }
        final int y = getMeasuredHeight() - 1;
        canvas.drawRect(0, y, getMeasuredWidth(), y + 1, dividerPaint);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // Selection is drawn by us (TextSelectionHelper), behind the title text, same as RichTextCell.
        final TextSelectionHelper.ArticleTextSelectionHelper helper = delegate != null ? delegate.getSelectionHelper() : null;
        if (helper != null && editText.getLayout() != null) {
            canvas.save();
            canvas.translate(
                editText.getLeft() + editText.getPaddingLeft(),
                editText.getTop() + editText.getPaddingTop()
            );
            helper.draw(canvas, this, 0);
            canvas.restore();
        }
        super.dispatchDraw(canvas);
    }

    public static final class Factory extends UItem.UItemFactory<RichDetailsCell> {
        static { setup(new Factory()); }

        @Override
        public RichDetailsCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new RichDetailsCell(context, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            ((RichDetailsCell) view).bind((BlockRow) item.object, (Delegate) item.object2);
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
