package org.telegram.ui.Stars;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;

/** Uses the same settings cells, colours and switch as Privacy and Security. */
public class StarsPrivacyActivity extends BaseFragment {
    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString(R.string.StarsPrivacyTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);

        HeaderCell header = new HeaderCell(context);
        header.setText(LocaleController.getString(R.string.StarsPrivacyTitle));
        header.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        content.addView(header, LayoutHelper.createLinear(-1, -2));
        TextCheckCell toggle = new TextCheckCell(context);
        toggle.setTag("stars_privacy_toggle");
        toggle.setBackground(Theme.getSelectorDrawable(true));
        toggle.setTextAndCheck(LocaleController.getString(R.string.StarsPrivacySwitch), StarsPrivacy.isEnabled(currentAccount), false);
        toggle.setOnClickListener(v -> {
            boolean enabled = !StarsPrivacy.isEnabled(currentAccount);
            StarsPrivacy.setEnabled(currentAccount, enabled);
            toggle.setChecked(enabled);
        });
        content.addView(toggle, LayoutHelper.createLinear(-1, -2));
        info(context, content, R.string.StarsPrivacyDescription);
        info(context, content, R.string.StarsPrivacyScope);
        TextSettingsCell history = new TextSettingsCell(context);
        history.setTag("stars_history_anonymise");
        history.setBackground(Theme.getSelectorDrawable(true));
        history.setTextAndValue(LocaleController.getString(R.string.StarsHistoryAnonymise), LocaleController.getString(R.string.StarsPrivacyPreviewLabel), false);
        history.setOnClickListener(v -> StarsHistoryPrivacy.confirm(context, currentAccount, null, getResourceProvider(), null));
        content.addView(history, LayoutHelper.createLinear(-1, -2));
        info(context, content, R.string.StarsHistoryAnonymiseInfo);
        fragmentView = scroll;
        return fragmentView;
    }

    private void info(Context context, LinearLayout content, int text) {
        TextInfoPrivacyCell cell = new TextInfoPrivacyCell(context);
        cell.setText(LocaleController.getString(text));
        content.addView(cell, LayoutHelper.createLinear(-1, -2));
    }
}
