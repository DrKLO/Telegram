package org.telegram.ui.Stars;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SimpleThemeDescription;

import java.util.ArrayList;

/** Shared native settings layout; no custom switches, typography or motion primitives. */
abstract class StarsPrivacySettingsActivity extends BaseFragment {
    protected FrameLayout root;
    protected LinearLayout content;
    private ScrollView scroll;
    private Bulletin feedback;

    protected void createSettingsView(Context context, int title, boolean preview) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString(title));
        if (preview) actionBar.setSubtitle(LocaleController.getString(R.string.StarsPrivacyPreviewLabel));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });
        root = new FrameLayout(context);
        scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        root.addView(scroll, LayoutHelper.createFrame(-1, -1));
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, AndroidUtilities.dp(24));
        scroll.addView(content);
        fragmentView = root;
    }

    protected void addHeader(int text) {
        HeaderCell cell = new HeaderCell(getContext(), getResourceProvider());
        cell.setText(LocaleController.getString(text));
        content.addView(cell, LayoutHelper.createLinear(-1, -2));
    }

    protected TextCheckCell addToggle(String tag, int text, boolean checked) {
        TextCheckCell cell = new TextCheckCell(getContext(), getResourceProvider());
        cell.setTag(tag);
        cell.setTextAndCheck(LocaleController.getString(text), checked, false);
        content.addView(cell, LayoutHelper.createLinear(-1, -2));
        return cell;
    }

    protected void addInfo(CharSequence text) {
        TextInfoPrivacyCell cell = new TextInfoPrivacyCell(getContext(), getResourceProvider());
        cell.setText(text);
        content.addView(cell, LayoutHelper.createLinear(-1, -2));
    }

    protected void showFeedback(int animation, int text) {
        // A rapid second toggle replaces the first notice instead of leaving stale feedback.
        if (feedback != null) feedback.hide(false, 0);
        feedback = BulletinFactory.of(this).createSimpleBulletin(animation,
            LocaleController.getString(text), 4, 5000);
        feedback.show();
    }

    protected void updateColors() {
        if (root == null) return;
        if (feedback != null) {
            feedback.hide(false, 0);
            feedback = null;
        }
        root.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        actionBar.setBackgroundColor(getThemedColor(Theme.key_actionBarDefault));
        actionBar.setTitleColor(getThemedColor(Theme.key_actionBarDefaultTitle));
        actionBar.setSubtitleColor(getThemedColor(Theme.key_actionBarDefaultSubtitle));
        actionBar.setItemsColor(getThemedColor(Theme.key_actionBarDefaultIcon), false);
        actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_actionBarDefaultSelector), false);
        if (isLastFragment()) updateNavigationContrast();
        for (int i = 0; i < content.getChildCount(); i++) {
            View cell = content.getChildAt(i);
            if (cell instanceof HeaderCell) {
                cell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                ((HeaderCell) cell).setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader));
            } else if (cell instanceof TextCheckCell) {
                cell.setBackground(Theme.getSelectorDrawable(true, getResourceProvider()));
                ((TextCheckCell) cell).setColors(Theme.key_windowBackgroundWhiteBlackText,
                    Theme.key_switchTrack, Theme.key_switchTrackChecked,
                    Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
            } else if (cell instanceof TextSettingsCell) {
                cell.setBackground(Theme.getSelectorDrawable(true, getResourceProvider()));
                ((TextSettingsCell) cell).setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
                ((TextSettingsCell) cell).setTextValueColor(getThemedColor(Theme.key_windowBackgroundWhiteValueText));
            } else if (cell instanceof TextInfoPrivacyCell) {
                cell.setBackground(Theme.getThemedDrawableByKey(getContext(), R.drawable.greydivider,
                    Theme.key_windowBackgroundGrayShadow));
                ((TextInfoPrivacyCell) cell).setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText4));
            }
        }
    }

    @Override public ArrayList<ThemeDescription> getThemeDescriptions() {
        return SimpleThemeDescription.createThemeDescriptions(this::updateColors,
            Theme.key_windowBackgroundGray, Theme.key_windowBackgroundGrayShadow,
            Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhiteBlackText,
            Theme.key_windowBackgroundWhiteBlueHeader, Theme.key_windowBackgroundWhiteGrayText4,
            Theme.key_windowBackgroundWhiteValueText, Theme.key_switchTrack,
            Theme.key_switchTrackChecked, Theme.key_listSelector, Theme.key_actionBarDefault,
            Theme.key_actionBarDefaultTitle, Theme.key_actionBarDefaultSubtitle,
            Theme.key_actionBarDefaultIcon, Theme.key_actionBarDefaultSelector);
    }

    @Override public void onPause() {
        if (feedback != null) feedback.hide(false, 0);
        super.onPause();
    }

    @Override public void onBecomeFullyVisible() {
        super.onBecomeFullyVisible();
        updateNavigationContrast();
    }

    private void updateNavigationContrast() {
        // Also works in the offline host, which does not use LaunchActivity's system-bar delegate.
        AndroidUtilities.setLightNavigationBar(getParentActivity(),
            AndroidUtilities.computePerceivedBrightness(getNavigationBarColor()) >= 0.721f);
    }

}
