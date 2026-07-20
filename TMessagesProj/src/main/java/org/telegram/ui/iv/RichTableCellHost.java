package org.telegram.ui.iv;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class RichTableCellHost extends FrameLayout {

    public final RichEditText editText;
    public TL_iv.pageTableCell cell;

    public RichTableCellHost(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        editText = new RichEditText(context, resourcesProvider);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setAllowNewlines(true);
        editText.setPadding(dp(11), dp(9), dp(11), dp(9));
        addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));
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
        final CharSequence styled = Emoji.replaceEmoji(TableModel.readStyledText(cell), editText.getPaint().getFontMetricsInt(), false);
        editText.setTextSilently(styled);
        editText.invalidateEffects();
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
        if (cell.header) {
            editText.setTypeface(AndroidUtilities.bold());
        } else {
            editText.setTypeface(null);
        }
    }
}
