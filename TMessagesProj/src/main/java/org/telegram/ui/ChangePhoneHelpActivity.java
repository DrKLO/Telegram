/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
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
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.LayoutHelper;

public class ChangePhoneHelpActivity extends BaseFragment {

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);

        TLRPC.User user = UserConfig.getCurrentUser();
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

        ImageView imageView = new ImageView(context);
        imageView.setImageResource(R.drawable.phone_change);
        linearLayout.addView(imageView);
        LinearLayout.LayoutParams layoutParams2 = (LinearLayout.LayoutParams) imageView.getLayoutParams();
        layoutParams2.width = LayoutHelper.WRAP_CONTENT;
        layoutParams2.height = LayoutHelper.WRAP_CONTENT;
        layoutParams2.gravity = Gravity.CENTER_HORIZONTAL;
        imageView.setLayoutParams(layoutParams2);

        TextView textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setGravity(Gravity.CENTER_HORIZONTAL);
        textView.setTextColor(0xff212121);

        try {
            textView.setText(AndroidUtilities.replaceTags(LocaleController.getString("PhoneNumberHelp", R.string.PhoneNumberHelp)));
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            textView.setText(LocaleController.getString("PhoneNumberHelp", R.string.PhoneNumberHelp));
        }
        linearLayout.addView(textView);
        layoutParams2 = (LinearLayout.LayoutParams) textView.getLayoutParams();
        layoutParams2.width = LayoutHelper.WRAP_CONTENT;
        layoutParams2.height = LayoutHelper.WRAP_CONTENT;
        layoutParams2.gravity = Gravity.CENTER_HORIZONTAL;
        layoutParams2.leftMargin = AndroidUtilities.dp(20);
        layoutParams2.rightMargin = AndroidUtilities.dp(20);
        layoutParams2.topMargin = AndroidUtilities.dp(56);
        textView.setLayoutParams(layoutParams2);

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        textView.setGravity(Gravity.CENTER_HORIZONTAL);
        textView.setTextColor(0xff4d83b3);
        textView.setText(LocaleController.getString("PhoneNumberChange", R.string.PhoneNumberChange));
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10));
        linearLayout.addView(textView);
        layoutParams2 = (LinearLayout.LayoutParams) textView.getLayoutParams();
        layoutParams2.width = LayoutHelper.WRAP_CONTENT;
        layoutParams2.height = LayoutHelper.WRAP_CONTENT;
        layoutParams2.gravity = Gravity.CENTER_HORIZONTAL;
        layoutParams2.leftMargin = AndroidUtilities.dp(20);
        layoutParams2.rightMargin = AndroidUtilities.dp(20);
        layoutParams2.topMargin = AndroidUtilities.dp(46);
        textView.setLayoutParams(layoutParams2);

        textView.setOnClickListener(new View.OnClickListener() {
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
}
