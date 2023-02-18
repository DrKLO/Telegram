package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class LiteModeSettingsActivity extends BaseFragment {

    TextCheckCell enableMode;

    ArrayList<TextCheckCell> checkBoxViews = new ArrayList<>();

    SharedConfig.LiteMode liteMode = SharedConfig.getLiteMode();
    LinearLayout contentView;

    TextCheckCell other;
    TextCheckCell animatedEmoji;
    TextCheckCell animatedBackground;
    TextCheckCell topicsInRightMenu;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("LightMode", R.string.LightMode));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        contentView = new LinearLayout(context);
        contentView.setOrientation(LinearLayout.VERTICAL);
        fragmentView = contentView;

        enableMode = new TextCheckCell(context);
        enableMode.setHeight(56);
        enableMode.setTextAndCheck(LocaleController.getString("EnableLightMode", R.string.EnableLightMode), SharedConfig.getLiteMode().enabled, false);
        enableMode.setBackgroundColor(Theme.getColor(enableMode.isChecked() ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked));
        enableMode.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        enableMode.setOnClickListener(v -> {
            SharedConfig.getLiteMode().toggleMode();
            updateEnableMode();
            update();
        });
        contentView.addView(enableMode, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextInfoPrivacyCell textInfoPrivacyCell = new TextInfoPrivacyCell(context);
        textInfoPrivacyCell.setBackground(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
        contentView.addView(textInfoPrivacyCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        animatedEmoji = createCheckCell(getContext());
        animatedEmoji.setOnClickListener(v -> {
            liteMode.animatedEmoji = !liteMode.animatedEmoji;
            update();
        });

        animatedBackground = createCheckCell(getContext());
        animatedBackground.setOnClickListener(v -> {
            liteMode.animatedBackground = !liteMode.animatedBackground;
            update();
        });

        topicsInRightMenu = createCheckCell(getContext());
        topicsInRightMenu.setOnClickListener(v -> {
            liteMode.topicsInRightMenu = !liteMode.topicsInRightMenu;
            update();
        });

        other = createCheckCell(getContext());
        other.setOnClickListener(v -> {
            liteMode.other = !liteMode.other;
            update();
        });
        update();

        update();

        checkBoxViews.add(animatedEmoji);
        checkBoxViews.add(animatedBackground);
        checkBoxViews.add(topicsInRightMenu);
        checkBoxViews.add(other);


        updateEnableMode();

        updateColors();
        return fragmentView;
    }

    private TextCheckCell createCheckCell(Context context) {
        TextCheckCell cell = new TextCheckCell(context);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        contentView.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return cell;
    }

    private void update() {
        animatedEmoji.setTextAndCheck("Animated Emoji", liteMode.animatedEmoji, true);
        animatedBackground.setTextAndCheck("Animated Backgrounds", liteMode.animatedBackground, true);
        other.setTextAndCheck("Other", liteMode.other, true);
        topicsInRightMenu.setTextAndCheck("Topics in Right Menu", liteMode.topicsInRightMenu, true);

    }

    private void updateEnableMode() {
        boolean checked = SharedConfig.getLiteMode().enabled;
        enableMode.setChecked(checked);
        int color = Theme.getColor(checked ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked);
        if (checked) {
            enableMode.setBackgroundColorAnimated(checked, color);
        } else {
            enableMode.setBackgroundColorAnimatedReverse(color);
        }
        for (int i = 0; i < checkBoxViews.size(); i++) {
            checkBoxViews.get(i).setVisibility(checked ? View.VISIBLE : View.GONE);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void updateColors() {
        enableMode.setColors(Theme.key_windowBackgroundCheckText, Theme.key_switchTrackBlue, Theme.key_switchTrackBlueChecked, Theme.key_switchTrackBlueThumb, Theme.key_switchTrackBlueThumbChecked);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        liteMode.savePreference();
        AnimatedEmojiDrawable.updateAll();
        Theme.reloadWallpaper();
    }
}
