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

import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSelectionHelper;

import java.util.ArrayList;

class RichCaptionController {

    interface Host {
        BlockRow currentRow();
        TextSelectionHelper.ArticleTextSelectionHelper selectionHelper();
        TextSelectionHelper.ArticleSelectableView cell();
        void onCaptionWillChange(int removed, int added);
        void onCaptionChanged();
        void onCaptionSpansChanged();
        void onCaptionEnter();
        void onRequestWindowFocusable(RichEditText et, boolean showKeyboard);
        void onCaptionLockedInsert(CharSequence text);
        boolean onCaptionSelectAll();
    }

    private static final int H_PADDING_DP = 16;

    final RichEditText editText;
    private final Theme.ResourcesProvider resourcesProvider;
    private final Host host;
    private boolean hijackingSelection;

    RichCaptionController(Context context, Theme.ResourcesProvider resourcesProvider, Host host) {
        this.resourcesProvider = resourcesProvider;
        this.host = host;

        editText = new RichEditText(context, resourcesProvider);
        editText.setPadding(dp(2), dp(4), dp(2), dp(2));
        editText.setAllowNewlines(false);
        editText.setInputType(
            InputType.TYPE_CLASS_TEXT |
            InputType.TYPE_TEXT_FLAG_MULTI_LINE |
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );
        editText.setGravity(Gravity.TOP | Gravity.START);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, Math.max(8, SharedConfig.fontSize - 2));
        editText.setHint("Add caption…");
        editText.setListener(new RichEditText.Listener() {
            @Override public void onEnterPressed(RichEditText et) { host.onCaptionEnter(); }
            @Override public void onTextWillChange(RichEditText et, int removed, int added) { host.onCaptionWillChange(removed, added); }
            @Override public void onTextChanged(RichEditText et, Editable text) {
                persist();
                host.onCaptionChanged();
            }
            @Override public void onRequestWindowFocusable(RichEditText et, boolean showKeyboard) { host.onRequestWindowFocusable(et, showKeyboard); }
            @Override public void onLockedInsert(RichEditText et, CharSequence text) { host.onCaptionLockedInsert(text); }
            @Override public boolean onSelectAll(RichEditText et) { return host.onCaptionSelectAll(); }
            @Override public void onSelectionChanged(RichEditText et, int selStart, int selEnd) {
                if (hijackingSelection || selStart == selEnd) return;
                final TextSelectionHelper.ArticleTextSelectionHelper helper = host.selectionHelper();
                if (helper == null) return;
                if (helper.isInSelectionMode() && helper.getSelectedCell() == host.cell()) return;
                final int s = selStart, e = selEnd;
                et.post(() -> {
                    if (et.length() < e || et.getSelectionStart() == et.getSelectionEnd()) return;
                    if (helper.selectRangeOf(host.cell(), 0, s, e)) {
                        hijackingSelection = true;
                        et.setSelection(e);
                        hijackingSelection = false;
                    }
                });
            }
        });
        editText.setDelegate(() -> {
            persist();
            host.onCaptionSpansChanged();
        });
        applyColors();
    }

    static void ensureCaption(TL_iv.PageBlock block) {
        if (block == null) return;
        if (block.caption == null) {
            block.caption = new TL_iv.PageCaption();
        }
        if (block.caption.text == null) block.caption.text = new TL_iv.textEmpty();
        if (block.caption.credit == null) block.caption.credit = new TL_iv.textEmpty();
    }

    void bind() {
        final BlockRow row = host.currentRow();
        if (row == null || row.block == null) return;
        ensureCaption(row.block);
        final TL_iv.RichText rt = row.block.caption.text;
        final String plain = RichTextStyle.plainOf(rt);
        if (!String.valueOf(editText.getText()).equals(plain)) {
            editText.setTextSilently(RichTextStyle.toSpannable(rt));
            editText.invalidateEffects();
        }
    }

    void persist() {
        final BlockRow row = host.currentRow();
        if (row == null || row.block == null) return;
        ensureCaption(row.block);
        row.block.caption.text = RichTextStyle.fromSpannable(editText.getText());
    }

    void applyColors() {
        editText.updateColors();
        final int color = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        editText.setTextColor(Theme.multAlpha(color, 0.5f));
        editText.setHintTextColor(Theme.multAlpha(color, 0.35f));
    }

    void setLocked(boolean locked) {
        editText.setLocked(locked);
    }

    void hideActionMode() {
        editText.hideActionMode();
    }

    void requestFocus() {
        editText.requestEditFocus();
    }

    int measure(int parentWidthPx) {
        return measure(0, 0, parentWidthPx);
    }

    /** Measures the caption inside the given horizontal insets (start/end), e.g. for a nested block. */
    int measure(int leftInset, int rightInset, int parentWidthPx) {
        final int w = Math.max(0, parentWidthPx - leftInset - rightInset - 2 * dp(H_PADDING_DP));
        editText.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        return editText.getMeasuredHeight();
    }

    void layout(int parentWidthPx, int top) {
        layout(0, 0, parentWidthPx, top);
    }

    void layout(int leftInset, int rightInset, int parentWidthPx, int top) {
        final int left = leftInset + dp(H_PADDING_DP);
        final int right = parentWidthPx - rightInset - dp(H_PADDING_DP);
        editText.layout(left, top, Math.max(left, right), top + editText.getMeasuredHeight());
    }

    void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> out) {
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
                final BlockRow row = host.currentRow();
                return row != null && row.block != null && row.block.caption != null && row.block.caption.text != null
                    ? RichTextStyle.toSpannable(row.block.caption.text) : "";
            }
        });
    }

    void drawSelection(Canvas canvas) {
        final TextSelectionHelper.ArticleTextSelectionHelper helper = host.selectionHelper();
        if (helper == null || editText.getLayout() == null) return;
        canvas.save();
        canvas.translate(editText.getLeft() + editText.getPaddingLeft(), editText.getTop() + editText.getPaddingTop());
        helper.draw(canvas, host.cell(), 0);
        canvas.restore();
    }

    boolean isPressOnCaption(int localX, int localY) {
        final Layout layout = editText.getLayout();
        if (layout == null) return false;
        final int textX = localX - (editText.getLeft() + editText.getPaddingLeft());
        final int textY = localY - (editText.getTop() + editText.getPaddingTop());
        if (textY < 0 || textY >= layout.getHeight()) return false;
        final int line = layout.getLineForVertical(textY);
        if (line < 0 || line >= layout.getLineCount()) return false;
        return textX >= layout.getLineLeft(line) && textX <= layout.getLineRight(line);
    }
}
