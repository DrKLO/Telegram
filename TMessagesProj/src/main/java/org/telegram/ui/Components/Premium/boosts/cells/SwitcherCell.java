package org.telegram.ui.Components.Premium.boosts.cells;

import android.content.Context;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;

public class SwitcherCell extends TextCheckCell {
    public static int TYPE_WINNERS = 0;
    public static int TYPE_ADDITION_PRIZE = 1;
    private int type;

    public SwitcherCell(Context context) {
        super(context);
    }

    public SwitcherCell(Context context, int padding) {
        super(context, padding);
    }

    public SwitcherCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);
    }

    public SwitcherCell(Context context, int padding, boolean dialog) {
        super(context, padding, dialog);
    }

    public SwitcherCell(Context context, int padding, boolean dialog, Theme.ResourcesProvider resourcesProvider) {
        super(context, padding, dialog, resourcesProvider);
    }

    public int getType() {
        return type;
    }

    public void setData(CharSequence text, boolean checked, boolean divider, int type) {
        this.type = type;
        setTextAndCheck(text, checked, divider);
    }
}
