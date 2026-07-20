package org.telegram.ui.iv;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class RichCommandSuggestions {

    // Builds the popup menu anchored correctly for the host: a fragment-based ItemOptions for the
    // full editor, or an alert-container-based one for the attach sheet (so it isn't drawn behind it).
    public interface MenuFactory {
        ItemOptions make(View anchor);
    }

    private final MenuFactory menuFactory;
    private final Theme.ResourcesProvider resourcesProvider;

    private ItemOptions options;
    private LinearLayout content;
    private ArrayList<RichCommand> shown;
    private RichTextCell cell;
    private RichTextCell backgroundCell;

    public RichCommandSuggestions(MenuFactory menuFactory, Theme.ResourcesProvider resourcesProvider) {
        this.menuFactory = menuFactory;
        this.resourcesProvider = resourcesProvider;
    }

    public void update(RichTextCell cell, String query) {
        if (query == null) {
            hide();
            return;
        }
        final ArrayList<RichCommand> matched = RichCommand.match(query);
        if (matched.isEmpty()) {
            hide();
            return;
        }
        setBackgroundCell(cell);
        if (this.cell == cell && matched.equals(shown) && options != null && options.isShown()) {
            return;
        }
        if (this.cell == cell && options != null && options.isShown() && content != null) {
            shown = matched;
            populate(cell, matched);
            options.reposition();
            return;
        }
        hide();
        setBackgroundCell(cell);
        this.cell = cell;
        this.shown = matched;
        show(cell, matched);
    }

    private void show(RichTextCell cell, ArrayList<RichCommand> matched) {
        content = new LinearLayout(cell.getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        populate(cell, matched);

        final ItemOptions o = menuFactory.make(cell.getEditText())
            .dontFocus()
            .setDimAlpha(0)
            .setDrawScrim(false);
        o.addView(content, LayoutHelper.createLinear(220, LayoutHelper.WRAP_CONTENT));
        o.setMaxHeight(dp(48 * 5));
        o.setGravity(Gravity.LEFT);
        o.translate(-dp(12), 0);
        o.setOnDismiss(() -> {
            options = null;
            content = null;
            shown = null;
            this.cell = null;
            setBackgroundCell(null);
        });
        o.followScrimView();
        o.show();
        options = o;
    }

    private void populate(RichTextCell cell, ArrayList<RichCommand> matched) {
        if (content == null) return;
        content.removeAllViews();
        for (RichCommand cmd : matched) {
            final RichCommand.View v = new RichCommand.View(cell.getContext(), cmd, resourcesProvider);
            v.setPadding(dp(8), 0, dp(12), 0);
            v.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), 0, 0));
            v.setOnClickListener(view -> {
                hide();
                cell.selectCommand(cmd);
            });
            content.addView(v, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
        }
    }

    public void hide() {
        setBackgroundCell(null);
        if (options != null) {
            options.dismiss();
            options = null;
        }
        content = null;
        shown = null;
        cell = null;
    }

    private void setBackgroundCell(RichTextCell cell) {
        if (backgroundCell == cell) return;
        if (backgroundCell != null) backgroundCell.setShowCommandBackground(false);
        backgroundCell = cell;
        if (backgroundCell != null) backgroundCell.setShowCommandBackground(true);
    }
}
