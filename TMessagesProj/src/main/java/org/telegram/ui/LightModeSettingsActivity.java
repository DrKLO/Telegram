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
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class LightModeSettingsActivity extends BaseFragment {

    TextCheckCell enableMode;

    ArrayList<TextCheckCell> checkBoxViews = new ArrayList<>();

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
        LinearLayout contentView = new LinearLayout(context);
        contentView.setOrientation(LinearLayout.VERTICAL);
        fragmentView = contentView;

        enableMode = new TextCheckCell(context);
        enableMode.setHeight(56);
        enableMode.setTextAndCheck(LocaleController.getString("EnableLightMode", R.string.EnableLightMode), SharedConfig.getLiteMode().enabled(), false);
        enableMode.setBackgroundColor(Theme.getColor(enableMode.isChecked() ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked));
        enableMode.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        enableMode.setOnClickListener(v -> {
            SharedConfig.getLiteMode().toggleMode();
            updateEnableMode();
        });
        contentView.addView(enableMode, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextInfoPrivacyCell textInfoPrivacyCell = new TextInfoPrivacyCell(context);
        textInfoPrivacyCell.setBackground(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
        contentView.addView(textInfoPrivacyCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextCheckCell animatedEmoji = new TextCheckCell(context);
        animatedEmoji.setTextAndCheck("Animated Emoji", true, true);
        animatedEmoji.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        contentView.addView(animatedEmoji, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextCheckCell animatedBackground = new TextCheckCell(context);
        animatedBackground.setTextAndCheck("Animated Backgrounds", true, true);
        animatedBackground.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        contentView.addView(animatedBackground, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));


        checkBoxViews.add(animatedEmoji);
        checkBoxViews.add(animatedBackground);

        for (int i = 0; i < checkBoxViews.size(); i++) {
            TextCheckCell view = checkBoxViews.get(i);
            checkBoxViews.get(i).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    view.setChecked(!view.isChecked());
                }
            });
        }
        updateEnableMode();

        updateColors();
        return fragmentView;
    }

    private void updateEnableMode() {
        boolean checked = SharedConfig.getLiteMode().enabled();
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
    }
}
