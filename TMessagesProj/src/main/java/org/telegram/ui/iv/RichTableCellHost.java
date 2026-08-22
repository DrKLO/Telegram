package org.telegram.ui.iv;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import org.telegram.messenger.Emoji;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class RichTableCellHost extends FrameLayout {

    public final RichEditText editText;
    public TL_iv.pageTableCell cell;

    public RichTableCellHost(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        editText = new RichEditText(context, resourcesProvider);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, Math.max(8, SharedConfig.fontSize - 2));
        editText.setAllowNewlines(true);
        setCompact(false);
        addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));
    }

    public void setCompact(boolean compact) {
        if (compact) {
            editText.setPadding(dp(5), dp(5), dp(5), dp(5));
            editText.setMinHeight(dp(18));
        } else {
            editText.setPadding(dp(12), dp(8), dp(12), dp(9));
            editText.setMinHeight(dp(36));
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (editText.getVisibility() == VISIBLE && ev.getActionMasked() != MotionEvent.ACTION_CANCEL) {
            final float x = ev.getX();
            final float y = ev.getY();
            final boolean insideHorz = x >= editText.getLeft() && x < editText.getRight();
            final boolean outsideVert = y < editText.getTop() || y >= editText.getBottom();
            if (insideHorz && outsideVert && editText.getHeight() > 0) {
                final float localX = x - editText.getLeft();
                final float localY = Math.max(0, Math.min(y - editText.getTop(), editText.getHeight() - 1));
                final MotionEvent copy = MotionEvent.obtain(ev);
                copy.setLocation(localX, localY);
                final boolean handled = editText.onTouchEvent(copy);
                copy.recycle();
                return handled;
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    public void bind(TL_iv.pageTableCell cell) {
        this.cell = cell;
        applyAlignment();
        final CharSequence rawStyled = TableModel.readStyledText(cell);
        final boolean autoBold = cell.header && (rawStyled.length() == 0
            || (RichTextStyle.stylesFullyCovering(rawStyled, 0, rawStyled.length()) & RichTextStyle.BOLD) != 0);
        editText.setAutoBold(autoBold);
        final CharSequence styled = Emoji.replaceEmoji(rawStyled, editText.getPaint().getFontMetricsInt(), false);
        editText.setTextSilently(styled);
        editText.invalidateEffects();
    }

    public void applyHeaderWithDefaultBold(boolean header) {
        if (cell == null) return;
        final SpannableStringBuilder styled = new SpannableStringBuilder(editText.getText());
        final boolean fullyBold = styled.length() > 0
            && (RichTextStyle.stylesFullyCovering(styled, 0, styled.length()) & RichTextStyle.BOLD) != 0;
        TableModel.setHeader(cell, header);
        if (header) {
            if (styled.length() > 0) {
                RichTextStyle.setStyle(styled, 0, styled.length(), RichTextStyle.BOLD, true);
            }
        } else if (fullyBold) {
            RichTextStyle.setStyle(styled, 0, styled.length(), RichTextStyle.BOLD, false);
        }
        TableModel.applyStyledText(cell, styled);
        bind(cell);
    }

    public void refreshFromCell() {
        if (cell == null) return;
        applyAlignment();
        invalidate();
    }

    public void setLocked(boolean locked) {
        editText.setLocked(locked);
    }

    private void applyAlignment() {
        FrameLayout.LayoutParams lp = (LayoutParams) editText.getLayoutParams();
        int gravity = Gravity.LEFT;
        if (cell.align_right) gravity = Gravity.RIGHT;
        else if (cell.align_center) gravity = Gravity.CENTER_HORIZONTAL;
        if (cell.valign_middle) gravity |= Gravity.CENTER_VERTICAL;
        else if (cell.valign_bottom) gravity |= Gravity.BOTTOM;
        else gravity |= Gravity.TOP;
        lp.gravity = gravity;
        editText.setLayoutParams(lp);
        int textGravity = Gravity.TOP | Gravity.LEFT;
        if (cell.align_right) textGravity = Gravity.TOP | Gravity.RIGHT;
        else if (cell.align_center) textGravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        editText.setGravity(textGravity);
        // Highlighting applies a real bold span so the user can remove bold from any range.
        editText.setTypeface(null);
    }
}
