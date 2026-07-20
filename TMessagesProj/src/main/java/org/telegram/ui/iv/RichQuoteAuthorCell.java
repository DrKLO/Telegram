package org.telegram.ui.iv;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.R;
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

import static org.telegram.messenger.LocaleController.getString;

/**
 * Synthetic, presentation-only row shown at the end of a multi-block quote: an author (credit) edit text bound
 * to the quote instance. Not backed by a real {@link BlockRow} in the editor's {@code rows} — its text lives in
 * the list view's {@code quoteAuthors} map, keyed by {@link BlockRow#authorQuoteId}. It rides {@link RichBlockCell}
 * so the quote's start inset positions it in line with the body content, and its {@code quoteIds} let the quote
 * bar/background extend over it.
 */
public class RichQuoteAuthorCell extends RichBlockCell
    implements Theme.Colorable, TextSelectionHelper.ArticleSelectableView {

    public interface Delegate {
        TL_iv.RichText getQuoteAuthor(long qid);
        void setQuoteAuthor(long qid, TL_iv.RichText text);
        default void onQuoteAuthorEnter(BlockRow row) {}
        TextSelectionHelper.ArticleTextSelectionHelper getSelectionHelper();
    }

    private final Theme.ResourcesProvider resourcesProvider;
    final RichEditText authorEditText;
    private Delegate delegate;
    private final ArrayList<TextSelectionHelper.TextLayoutBlock> tmpBlocks = new ArrayList<>();
    private boolean hijackingSelection;

    public RichQuoteAuthorCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setBlockPadding(dp(16), dp(1), dp(16), dp(8));

        authorEditText = new RichEditText(context, resourcesProvider);
        authorEditText.setPadding(dp(2), dp(1), dp(2), dp(1));
        authorEditText.setAllowNewlines(false);
        authorEditText.setInputType(
            InputType.TYPE_CLASS_TEXT |
            InputType.TYPE_TEXT_FLAG_MULTI_LINE |
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );
        authorEditText.setGravity(Gravity.START | Gravity.TOP);
        authorEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, Math.max(8, SharedConfig.fontSize - 2));
        authorEditText.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        authorEditText.setTextColorKey(Theme.key_featuredStickers_addButton);
        authorEditText.setAccentHint(true);
        authorEditText.setHint(getString(R.string.ArticleHintAuthor));
        authorEditText.setListener(new RichEditText.Listener() {
            @Override public void onEnterPressed(RichEditText et) {
                if (delegate != null && currentRow != null) delegate.onQuoteAuthorEnter(currentRow);
            }
            @Override public void onTextChanged(RichEditText et, Editable text) { persist(); }
            @Override public void onSelectionChanged(RichEditText et, int selStart, int selEnd) {
                // Promote an in-field selection to a cross-block article selection so the shared TextSelectionHelper
                // owns it (matches the caption / legacy quote-author behavior). The author is this cell's block 0.
                if (hijackingSelection || selStart == selEnd || delegate == null) return;
                final TextSelectionHelper.ArticleTextSelectionHelper helper = delegate.getSelectionHelper();
                if (helper == null) return;
                final int s = selStart, e = selEnd;
                et.post(() -> {
                    if (et.length() < e || et.getSelectionStart() == et.getSelectionEnd()) return;
                    if (helper.isInSelectionMode()) {
                        hijackingSelection = true; et.setSelection(e); hijackingSelection = false; return;
                    }
                    if (helper.selectRangeOf(RichQuoteAuthorCell.this, 0, s, e)) {
                        hijackingSelection = true; et.setSelection(e); hijackingSelection = false;
                    }
                });
            }
        });
        authorEditText.setDelegate(this::persist);
        addView(authorEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));

        updateColors();
    }

    public void bind(BlockRow row, Delegate delegate) {
        this.currentRow = row;
        this.delegate = delegate;
        // Constant base padding only — the base RichBlockCell applies the quote's depth-aware bottom edge padding
        // (the author row is the last element of the quote) on top of this, so we must NOT add it here too.
        setBlockPadding(dp(16), dp(1), dp(16), dp(8));
        bindBlockInset(row);
        final TL_iv.RichText author = delegate != null ? delegate.getQuoteAuthor(row.authorQuoteId) : null;
        final String plain = RichTextStyle.plainOf(author);
        if (!String.valueOf(authorEditText.getText()).equals(plain)) {
            authorEditText.setTextSilently(Emoji.replaceEmoji(RichTextStyle.toSpannable(author), authorEditText.getPaint().getFontMetricsInt(), false));
            authorEditText.invalidateEffects();
        }
    }

    private void persist() {
        if (delegate == null || currentRow == null) return;
        delegate.setQuoteAuthor(currentRow.authorQuoteId, RichTextStyle.fromSpannable(authorEditText.getText()));
    }

    public BlockRow getRow() {
        return currentRow;
    }

    @Override
    public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> blocks) {
        final Layout layout = authorEditText.getLayout();
        if (layout == null) return;
        final int textX = authorEditText.getLeft() + authorEditText.getPaddingLeft();
        final int textY = authorEditText.getTop() + authorEditText.getPaddingTop();
        blocks.add(new TextSelectionHelper.TextLayoutBlock() {
            @Override public Layout getLayout() { return layout; }
            @Override public int getX() { return textX; }
            @Override public int getY() { return textY; }
            @Override public int getRow() { return 0; }
            @Override public CharSequence getText() { return layout.getText(); }
        });
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        final TextSelectionHelper.ArticleTextSelectionHelper helper = delegate != null ? delegate.getSelectionHelper() : null;
        if (helper != null) {
            tmpBlocks.clear();
            fillTextLayoutBlocks(tmpBlocks);
            for (int i = 0; i < tmpBlocks.size(); i++) {
                final TextSelectionHelper.TextLayoutBlock b = tmpBlocks.get(i);
                canvas.save();
                canvas.translate(b.getX(), b.getY());
                helper.draw(canvas, this, i);
                canvas.restore();
            }
        }
        super.dispatchDraw(canvas);
    }

    @Override
    public void updateColors() {
        authorEditText.updateColors();
        authorEditText.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
        authorEditText.setHintTextColor(Theme.multAlpha(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), 0.5f));
    }

    public static final class Factory extends UItem.UItemFactory<RichQuoteAuthorCell> {
        static { setup(new Factory()); }

        @Override
        public RichQuoteAuthorCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new RichQuoteAuthorCell(context, resourcesProvider);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            ((RichQuoteAuthorCell) view).bind((BlockRow) item.object, (Delegate) item.object2);
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
