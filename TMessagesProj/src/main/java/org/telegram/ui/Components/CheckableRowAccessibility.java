package org.telegram.ui.Components;

import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * A choice made by pressing a row with a check box drawn beside its words. A screen reader read the
 * words and nothing of the box, so it could not be told whether the choice was on or off: the row is
 * a check box to it, and says whether it is ticked.
 */
public final class CheckableRowAccessibility {

    private CheckableRowAccessibility() {}

    public static void apply(View row, CheckBox2 checkBox) {
        checkBox.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName("android.widget.CheckBox");
                info.setCheckable(true);
                info.setChecked(checkBox.isChecked());
            }
        });
    }
}
