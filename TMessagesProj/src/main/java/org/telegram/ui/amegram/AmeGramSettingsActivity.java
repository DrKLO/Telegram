/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 Umi.
 */
package org.telegram.ui.amegram;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.amegram.AmeGramConfig;
import org.telegram.messenger.amegram.AmeGramSettings;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.FiltersSetupActivity;
import org.telegram.ui.NotificationsSettingsActivity;
import org.telegram.ui.PrivacySettingsActivity;
import org.telegram.ui.ThemeActivity;

import java.util.ArrayList;

/** A small, solid-surface entry point using Telegram's existing navigation stack. */
public final class AmeGramSettingsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private final ArrayList<TextView> bodyViews = new ArrayList<>();
    private final ArrayList<TextView> headingViews = new ArrayList<>();
    private final ArrayList<View> surfaceViews = new ArrayList<>();
    private AmeGramSettings settings;
    private FrameLayout root;
    private LinearLayout content;
    private Switch oledSwitch;

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetNewTheme) applyPalette();
    }

    @Override
    public View createView(Context context) {
        settings = new AmeGramSettings(context);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(LocaleController.getString(R.string.AmeGramSettings));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });
        root = new FrameLayout(context);
        fragmentView = root;
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        bodyViews.clear();
        headingViews.clear();
        surfaceViews.clear();

        addText(context, R.string.AmeGramUnofficial, false);
        addText(context, R.string.AmeGramAppearance, true);
        addLink(context, R.string.AmeGramTelegramAppearance, () -> new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
        oledSwitch = new Switch(context);
        oledSwitch.setText(LocaleController.getString(R.string.AmeGramOledBackground));
        oledSwitch.setTextSize(16);
        oledSwitch.setGravity(Gravity.CENTER_VERTICAL);
        oledSwitch.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
        oledSwitch.setMinHeight(AndroidUtilities.dp(56));
        oledSwitch.setChecked(settings.usesOledDarkBackground());
        content.addView(oledSwitch, new LinearLayout.LayoutParams(-1, -2));
        bodyViews.add(oledSwitch);
        surfaceViews.add(oledSwitch);
        oledSwitch.setOnCheckedChangeListener((button, checked) -> {
            settings.setOledDarkBackground(checked);
            applyPalette();
        });
        addText(context, R.string.AmeGramOledBackgroundInfo, false);

        addText(context, R.string.AmeGramChatOrganization, true);
        addLink(context, R.string.AmeGramTelegramFolders, FiltersSetupActivity::new);
        addText(context, R.string.AmeGramNotifications, true);
        addLink(context, R.string.AmeGramTelegramNotifications, NotificationsSettingsActivity::new);
        addText(context, R.string.AmeGramPrivacy, true);
        addLink(context, R.string.AmeGramTelegramPrivacy, PrivacySettingsActivity::new);
        addText(context, R.string.AmeGramLab, true);
        addText(context, R.string.AmeGramLabUnavailable, false);
        addText(context, R.string.AmeGramAbout, true);
        TextView version = addText(context, R.string.AmeGramUnofficial, false);
        version.setText("AmeGram " + AmeGramConfig.VERSION + " · " + AmeGramConfig.DEVELOPER + "\n"
                + LocaleController.getString(R.string.AmeGramSlogan) + "\n"
                + LocaleController.getString(R.string.AmeGramUnofficial));
        applyPalette();
        return fragmentView;
    }

    private TextView addText(Context context, int stringId, boolean heading) {
        TextView view = new TextView(context);
        view.setText(LocaleController.getString(stringId));
        view.setTextSize(heading ? 16 : 14);
        view.setGravity(Gravity.START);
        view.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(heading ? 20 : 12),
                AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        if (heading) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            androidx.core.view.ViewCompat.setAccessibilityHeading(view, true);
        }
        content.addView(view, new LinearLayout.LayoutParams(-1, -2));
        (heading ? headingViews : bodyViews).add(view);
        return view;
    }

    private void addLink(Context context, int title, java.util.function.Supplier<BaseFragment> factory) {
        TextView view = addText(context, title, false);
        view.setTextSize(16);
        view.setMinHeight(AndroidUtilities.dp(56));
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        view.setFocusable(true);
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName("android.widget.Button");
            }
        });
        view.setOnClickListener(v -> {
            BaseFragment target = factory.get();
            target.setCurrentAccount(currentAccount);
            presentFragment(target);
        });
        surfaceViews.add(view);
    }

    private void applyPalette() {
        if (root == null) return;
        boolean dark = Theme.isCurrentThemeDark();
        boolean oled = settings.usesOledDarkBackground();
        int background = AmeGramPalette.background(dark, oled);
        int surface = AmeGramPalette.surface(dark, oled);
        int accent = AmeGramPalette.accessibleAccent(dark);
        root.setBackgroundColor(background);
        for (TextView view : bodyViews) {
            view.setTextColor(AmeGramPalette.secondaryText(dark));
        }
        for (TextView view : headingViews) view.setTextColor(accent);
        for (View view : surfaceViews) {
            view.setBackground(Theme.createSelectorDrawable(dark ? 0x33A78BFA : 0x227C3AED, 2));
            // Selector + solid backing are drawn together, without blur or translucency of the surface.
            android.graphics.drawable.Drawable selector = view.getBackground();
            view.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{
                    new android.graphics.drawable.ColorDrawable(surface), selector}));
            ((TextView) view).setTextColor(AmeGramPalette.text(dark));
        }
        oledSwitch.setThumbTintList(ColorStateList.valueOf(accent));
        oledSwitch.setTrackTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{accent, AmeGramPalette.secondaryText(dark)}));
        actionBar.setBackgroundColor(surface);
        actionBar.setTitleColor(AmeGramPalette.text(dark));
        actionBar.setItemsColor(accent, false);
        setNavigationBarColor(background);
        if (getParentActivity() != null) AndroidUtilities.setLightStatusBar(getParentActivity(), !dark);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (oledSwitch != null) {
            oledSwitch.setChecked(settings.usesOledDarkBackground());
            applyPalette();
        }
    }

    @Override
    public int getNavigationBarColor() {
        return AmeGramPalette.background(Theme.isCurrentThemeDark(), settings != null && settings.usesOledDarkBackground());
    }

    @Override
    public boolean isLightStatusBar() {
        return !Theme.isCurrentThemeDark();
    }
}
