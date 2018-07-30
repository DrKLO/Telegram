/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.LayoutHelper;

public class ChangePhoneHelpActivity extends BaseFragment {

    private TextView textView1;
    private TextView textView2;
    private ImageView imageView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);

        TLRPC.User user = UserConfig.getInstance(currentAccount).getCurrentUser();
        String value;
        if (user != null && user.phone != null && user.phone.length() != 0) {
            value = PhoneFormat.getInstance().format("+" + user.phone);
        } else {
            value = LocaleController.getString("NumberUnknown", R.string.NumberUnknown);
        }

        actionBar.setTitle(value);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new RelativeLayout(context);
        fragmentView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        RelativeLayout relativeLayout = (RelativeLayout) fragmentView;

        ScrollView scrollView = new ScrollView(context);
        relativeLayout.addView(scrollView);
        RelativeLayout.LayoutParams layoutParams3 = (RelativeLayout.LayoutParams) scrollView.getLayoutParams();
        layoutParams3.width = LayoutHelper.MATCH_PARENT;
        layoutParams3.height = LayoutHelper.WRAP_CONTENT;
        layoutParams3.addRule(RelativeLayout.CENTER_VERTICAL, RelativeLayout.TRUE);
        scrollView.setLayoutParams(layoutParams3);

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(0, AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20));
        scrollView.addView(linearLayout);
        ScrollView.LayoutParams layoutParams = (ScrollView.LayoutParams) linearLayout.getLayoutParams();
        layoutParams.width = ScrollView.LayoutParams.MATCH_PARENT;
        layoutParams.height = ScrollView.LayoutParams.WRAP_CONTENT;
        linearLayout.setLayoutParams(layoutParams);

        imageView = new ImageView(context);
        imageView.setImageResource(R.drawable.phone_change);
        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_changephoneinfo_image), PorterDuff.Mode.MULTIPLY));
        linearLayout.addView(imageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        textView1 = new TextView(context);
        textView1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView1.setGravity(Gravity.CENTER_HORIZONTAL);
        textView1.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));

        try {
            textView1.setText(AndroidUtilities.replaceTags(LocaleController.getString("PhoneNumberHelp", R.string.PhoneNumberHelp)));
        } catch (Exception e) {
            FileLog.e(e);
            textView1.setText(LocaleController.getString("PhoneNumberHelp", R.string.PhoneNumberHelp));
        }
        linearLayout.addView(textView1, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 20, 56, 20, 0));

        textView2 = new TextView(context);
        textView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        textView2.setGravity(Gravity.CENTER_HORIZONTAL);
        textView2.setTextColor(Theme.getColor(Theme.key_changephoneinfo_changeText));
        textView2.setText(LocaleController.getString("PhoneNumberChange", R.string.PhoneNumberChange));
        textView2.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView2.setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10));
        linearLayout.addView(textView2, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 20, 46, 20, 0));

        textView2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getParentActivity() == null) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                builder.setMessage(LocaleController.getString("PhoneNumberAlert", R.string.PhoneNumberAlert));
                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        presentFragment(new ChangePhoneActivity(), true);
                    }
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                showDialog(builder.create());
            }
        });

        return fragmentView;
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),

                new ThemeDescription(textView1, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(textView2, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_changephoneinfo_changeText),
                new ThemeDescription(imageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_changephoneinfo_image),
        };
    }
}
