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
import android.view.View;
import android.widget.ImageView;
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
import org.telegram.ui.Components.RLottieImageView;

import java.util.ArrayList;

@SuppressWarnings("FieldCanBeLocal")
public class DialogsEmptyCell extends LinearLayout {

    private RLottieImageView imageView;
    private TextView emptyTextView1;
    private TextView emptyTextView2;
    private int currentType;

    private int currentAccount = UserConfig.selectedAccount;

    public DialogsEmptyCell(Context context) {
        super(context);

        setGravity(Gravity.CENTER);
        setOrientation(VERTICAL);
        setOnTouchListener((v, event) -> true);

        imageView = new RLottieImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        addView(imageView, LayoutHelper.createFrame(100, 100, Gravity.CENTER, 52, 4, 52, 0));
        imageView.setOnClickListener(v -> {
            if (!imageView.isPlaying()) {
                imageView.setProgress(0.0f);
                imageView.playAnimation();
            }
        });

        emptyTextView1 = new TextView(context);
        emptyTextView1.setTextColor(Theme.getColor(Theme.key_chats_nameMessage_threeLines));
        emptyTextView1.setText(LocaleController.getString("NoChats", R.string.NoChats));
        emptyTextView1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        emptyTextView1.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        emptyTextView1.setGravity(Gravity.CENTER);
        addView(emptyTextView1, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 52, 10, 52, 0));

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
        if (currentType == value) {
            return;
        }
        currentType = value;
        String help;
        int icon;
        if (currentType == 0) {
            icon = 0;
            help = LocaleController.getString("NoChatsHelp", R.string.NoChatsHelp);
            emptyTextView1.setText(LocaleController.getString("NoChats", R.string.NoChats));
        } else if (currentType == 1) {
            icon = 0;
            help = LocaleController.getString("NoChatsContactsHelp", R.string.NoChatsContactsHelp);
            emptyTextView1.setText(LocaleController.getString("NoChats", R.string.NoChats));
        } else if (currentType == 2) {
            imageView.setAutoRepeat(false);
            icon = R.raw.filter_no_chats;
            help = LocaleController.getString("FilterNoChatsToDisplayInfo", R.string.FilterNoChatsToDisplayInfo);
            emptyTextView1.setText(LocaleController.getString("FilterNoChatsToDisplay", R.string.FilterNoChatsToDisplay));
        } else {
            imageView.setAutoRepeat(true);
            icon = R.raw.filter_new;
            help = LocaleController.getString("FilterAddingChatsInfo", R.string.FilterAddingChatsInfo);
            emptyTextView1.setText(LocaleController.getString("FilterAddingChats", R.string.FilterAddingChats));
        }
        if (icon != 0) {
            imageView.setVisibility(VISIBLE);
            imageView.setAnimation(icon, 100, 100);
            imageView.playAnimation();
        } else {
            imageView.setVisibility(GONE);
        }
        if (AndroidUtilities.isTablet() && !AndroidUtilities.isSmallTablet()) {
            help = help.replace('\n', ' ');
        }
        emptyTextView2.setText(help);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        updateLayout();
    }

    @Override
    public void offsetTopAndBottom(int offset) {
        super.offsetTopAndBottom(offset);
        updateLayout();
    }

    public void updateLayout() {
        if (getParent() instanceof View && (currentType == 2 || currentType == 3)) {
            View view = (View) getParent();
            int paddingTop = view.getPaddingTop();
            if (paddingTop != 0) {
                int offset = -(getTop() / 2);
                imageView.setTranslationY(offset);
                emptyTextView1.setTranslationY(offset);
                emptyTextView2.setTranslationY(offset);
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int totalHeight;
        if (getParent() instanceof View) {
            View view = (View) getParent();
            totalHeight = view.getMeasuredHeight();
            if (view.getPaddingTop() != 0 && Build.VERSION.SDK_INT >= 21) {
                totalHeight -= AndroidUtilities.statusBarHeight;
            }
        } else {
            totalHeight = MeasureSpec.getSize(heightMeasureSpec);
        }
        if (totalHeight == 0) {
            totalHeight = AndroidUtilities.displaySize.y - ActionBar.getCurrentActionBarHeight() - (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
        }
        if (currentType == 0 || currentType == 2 || currentType == 3) {
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
