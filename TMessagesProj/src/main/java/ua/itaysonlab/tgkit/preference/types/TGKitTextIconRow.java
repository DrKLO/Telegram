package ua.itaysonlab.tgkit.preference.types;

import androidx.annotation.Nullable;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.TextCell;

import ua.itaysonlab.tgkit.preference.TGKitPreference;

public class TGKitTextIconRow extends TGKitPreference {
    public boolean divider = false;
    public int icon = -1;

    @Nullable
    public String value = null;

    @Nullable
    public TGTIListener listener;

    public void bindCell(TextCell cell) {
        if (icon != -1 && value != null) {
            cell.setTextAndValueAndIcon(title, value, icon, divider);
        } else if (value != null) {
            cell.setTextAndValue(title, value, divider);
        } else if (icon != -1) {
            cell.setTextAndIcon(title, icon, divider);
        } else {
            cell.setText(title, divider);
        }
    }

    @Override
    public TGPType getType() {
        return TGPType.TEXT_ICON;
    }

    public interface TGTIListener {
        void onClick(BaseFragment bf);
    }
}
