package org.Tajgram.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.exoplayer2.util.Consumer;

import org.Tajgram.messenger.AndroidUtilities;
import org.Tajgram.messenger.LocaleController;
import org.Tajgram.messenger.R;
import org.Tajgram.messenger.UserObject;
import org.Tajgram.messenger.Utilities;
import org.Tajgram.messenger.browser.Browser;
import org.Tajgram.tgnet.TLRPC;
import org.Tajgram.ui.ActionBar.AlertDialog;
import org.Tajgram.ui.ActionBar.Theme;
import org.Tajgram.ui.Cells.CheckBoxCell;
import org.Tajgram.ui.Components.LayoutHelper;

public class WebAppDisclaimerAlert {


    private CheckBoxCell cell;
    private CheckBoxCell cell2;
    private AlertDialog alert;
    private TextView positiveButton;

    public static void show(Context context, Consumer<Boolean> consumer, TLRPC.User withSendMessage, Runnable dismissed) {
        WebAppDisclaimerAlert alert = new WebAppDisclaimerAlert();

        AlertDialog.Builder alertDialog = new AlertDialog.Builder(context);
        alertDialog.setTitle(LocaleController.getString(R.string.TermsOfUse));

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        TextView textView = new TextView(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            textView.setLetterSpacing(0.025f);
        }
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        linearLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 24, 0, 24, 0));

        alert.cell = new CheckBoxCell(context, 1, null);
        alert.cell.getTextView().getLayoutParams().width = LayoutHelper.MATCH_PARENT;
        alert.cell.getTextView().setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        linearLayout.addView(alert.cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT, 8, 0, 8, 0));

//        if (withSendMessage != null) {
//            alert.cell2 = new CheckBoxCell(context, 1, null);
//            alert.cell2.getTextView().setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
//            linearLayout.addView(alert.cell2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT, 8, -8, 8, 0));
//            alert.cell2.setText(AndroidUtilities.replaceTags(LocaleController.formatString("OpenUrlOption2", R.string.OpenUrlOption2, UserObject.getUserName(withSendMessage))), "", true, false);
//            alert.cell2.setOnClickListener(v -> {
//                alert.cell2.setChecked(!alert.cell2.isChecked(), true);
//            });
//        }

        final boolean[] dismissing = new boolean[1];
        textView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.BotWebAppDisclaimerSubtitle)));
        alert.cell.setText(AndroidUtilities.replaceSingleTag(LocaleController.getString(R.string.BotWebAppDisclaimerCheck), () -> {
            Browser.openUrl(context, LocaleController.getString(R.string.WebAppDisclaimerUrl));
        }), "", false, false);
        alertDialog.setView(linearLayout);
        alertDialog.setPositiveButton(LocaleController.getString(R.string.Continue), (dialog, which) -> {
            consumer.accept(true);
            dismissing[0] = true;
            dialog.dismiss();
        });
        alertDialog.setNegativeButton(LocaleController.getString(R.string.Cancel), (dialog, which) -> {
            dialog.dismiss();
        });
        alert.alert = alertDialog.create();
        alert.alert.show();
        alert.positiveButton = (TextView) alert.alert.getButton(DialogInterface.BUTTON_POSITIVE);
        alert.positiveButton.setEnabled(false);
        alert.positiveButton.setAlpha(0.5f);
        alert.cell.setOnClickListener(v -> {
            alert.cell.setChecked(!alert.cell.isChecked(), true);
            alert.positiveButton.setEnabled(alert.cell.isChecked());
            alert.positiveButton.animate().alpha(alert.cell.isChecked() ? 1f : 0.5f).start();
        });
        alert.cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ROUNDRECT_6DP));
        alert.alert.setOnDismissListener(d -> {
            if (!dismissing[0]) {
                dismissing[0] = true;
                if (dismissed != null) {
                    dismissed.run();
                }
            }
        });
    }
}
