package org.Tajgram.ui.iv;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;

import org.Tajgram.messenger.AndroidUtilities;
import org.Tajgram.tgnet.tl.TL_iv;
import org.Tajgram.ui.ActionBar.Theme;
import org.Tajgram.ui.Components.LayoutHelper;

public class RichTableCellHost extends FrameLayout {

    public final RichEditText editText;
    public TL_iv.pageTableCell cell;

    public RichTableCellHost(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        editText = new RichEditText(context, resourcesProvider);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(7), AndroidUtilities.dp(8), AndroidUtilities.dp(7));
        addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));
    }

    public void bind(TL_iv.pageTableCell cell) {
        this.cell = cell;
        applyAlignment();
        editText.setTextSilently(TableModel.readPlainText(cell));
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
