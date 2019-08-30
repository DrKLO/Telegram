/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

@SuppressWarnings("FieldCanBeLocal")
public class DialogsEmptyCell extends LinearLayout {

    private TextView emptyTextView1;
    private TextView emptyTextView2;
    private int currentType;

    private int currentAccount = UserConfig.selectedAccount;

    public DialogsEmptyCell(Context context) {
        super(context);

        setGravity(Gravity.CENTER);
        setOrientation(VERTICAL);
        setOnTouchListener((v, event) -> true);

        emptyTextView1 = new TextView(context);
        emptyTextView1.setTextColor(Theme.getColor(Theme.key_chats_nameMessage_threeLines));
        emptyTextView1.setText(LocaleController.getString("NoChats", R.string.NoChats));
        emptyTextView1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        emptyTextView1.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        emptyTextView1.setGravity(Gravity.CENTER);
        addView(emptyTextView1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 52, 4, 52, 0));

        emptyTextView2 = new TextView(context);
        String help = LocaleController.getString("NoChatsHelp", R.string.NoChatsHelp);
        if (AndroidUtilities.isTablet() && !AndroidUtilities.isSmallTablet()) {
            help = help.replace('\n', ' ');
        }
        emptyTextView2.setText(help);
        emptyTextView2.setTextColor(Theme.getColor(Theme.key_chats_message));
        emptyTextView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        emptyTextView2.setGravity(Gravity.CENTER);
        emptyTextView2.setLineSpacing(AndroidUtilities.dp(2), 1);
        addView(emptyTextView2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 52, 7, 52, 0));
    }

    public void setType(int value) {
        currentType = value;
        String help;
        if (currentType == 0) {
            help = LocaleController.getString("NoChatsHelp", R.string.NoChatsHelp);
            if (AndroidUtilities.isTablet() && !AndroidUtilities.isSmallTablet()) {
                help = help.replace('\n', ' ');
            }
        } else {
            help = LocaleController.getString("NoChatsContactsHelp", R.string.NoChatsContactsHelp);
            if (AndroidUtilities.isTablet() && !AndroidUtilities.isSmallTablet()) {
                help = help.replace('\n', ' ');
            }
        }
        emptyTextView2.setText(help);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int totalHeight = MeasureSpec.getSize(heightMeasureSpec);
        if (totalHeight == 0) {
            totalHeight = AndroidUtilities.displaySize.y - ActionBar.getCurrentActionBarHeight() - (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
        }
        if (currentType == 0) {
            ArrayList<TLRPC.RecentMeUrl> arrayList = MessagesController.getInstance(currentAccount).hintDialogs;
            if (!arrayList.isEmpty()) {
                totalHeight -= AndroidUtilities.dp(72) * arrayList.size() + arrayList.size() - 1 + AndroidUtilities.dp(12 + 38);
            }
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(totalHeight, MeasureSpec.EXACTLY));
        } else {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(166), MeasureSpec.EXACTLY));
        }
    }
}
