/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.view.UserAreaBottomView;
import org.telegram.ui.view.UserAreaInputView;

import java.util.ArrayList;

public class UserAreaActivity extends BaseFragment {

    public class GoldRoundedRect extends View {

        private final int HEIGHT = AndroidUtilities.dp(250);
        private final int WIDTH = AndroidUtilities.dp(360);

        private final Paint paint = new Paint();
        private final RectF rect = new RectF(0, 0, WIDTH, HEIGHT);
        private final Path path = new Path();
        private final float[] corners = new float[] {
                30, 30,
                30, 30,
                30, 30,
                30, 30
        };

        public GoldRoundedRect(Context context) {
            super(context);

            paint.setStrokeWidth(0);
            paint.setColor(Theme.getColor(Theme.key_statisticChartLine_golden));
            path.addRoundRect(rect, corners, Path.Direction.CW);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) { setMeasuredDimension(WIDTH, HEIGHT); }

        @Override
        public void onDraw(Canvas canvas) {
            canvas.drawPath(path, paint);
        }
    }

    public class CardCell extends FrameLayout {

        private TextView partnerIdTextView;
        private TextView clickToCopyTextView;
        private TextView ratingInSystemTextView;
        private TextView ratingTextView;
        private TextView smallRatingTextView;
        private Button boostBtn;

        public void setRating(String rating) {
            ratingTextView.setText(rating);
        }

        public void setRatingSmall(String rating) {
            smallRatingTextView.setText(rating);
        }

        public String setOnCopyClickListener() {
            return ratingTextView.getText().toString();
        }

        public void setOnBoostClickListener(OnClickListener l) {
            boostBtn.setOnClickListener(l);
        }

        public CardCell(Context context) {
            super(context);

            addView(new GoldRoundedRect(context), LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            linearLayout.setGravity(Gravity.CENTER_HORIZONTAL);
            addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

            partnerIdTextView = new TextView(context);
            partnerIdTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            partnerIdTextView.setTextColor(Theme.getColor(Theme.key_wallet_whiteText));
            partnerIdTextView.setGravity(Gravity.CENTER);
            partnerIdTextView.setText(R.string.PartnerId);
            linearLayout.addView(partnerIdTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

            clickToCopyTextView = new TextView(context);
            clickToCopyTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            clickToCopyTextView.setTextColor(Theme.getColor(Theme.key_wallet_whiteText));
            clickToCopyTextView.setGravity(Gravity.CENTER);
            clickToCopyTextView.setText(R.string.ClickToCopy);
            linearLayout.addView(clickToCopyTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

            ratingInSystemTextView = new TextView(context);
            ratingInSystemTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            ratingInSystemTextView.setTextColor(Theme.getColor(Theme.key_wallet_whiteText));
            ratingInSystemTextView.setGravity(Gravity.CENTER);
            ratingInSystemTextView.setText(R.string.RatingIsGold);
            linearLayout.addView(ratingInSystemTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 24, 0, 0));

            ratingTextView = new TextView(context);
            ratingTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 23);
            ratingTextView.setTextColor(Theme.getColor(Theme.key_wallet_whiteText));
            ratingTextView.setGravity(Gravity.CENTER);
            linearLayout.addView(ratingTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

            smallRatingTextView = new TextView(context);
            smallRatingTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            smallRatingTextView.setTextColor(Theme.getColor(Theme.key_wallet_whiteText));
            smallRatingTextView.setGravity(Gravity.CENTER);
            linearLayout.addView(smallRatingTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 16, 0, 0));

            boostBtn = new Button(context);
            boostBtn.setAllCaps(false);
            boostBtn.setBackgroundColor(getResources().getColor(R.color.wallet_holo_blue_light));
            boostBtn.setTextSize(22);
            boostBtn.setText(R.string.Boost);
            linearLayout.addView(boostBtn, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, AndroidUtilities.dp(26), 16, AndroidUtilities.dp(26), 0));
        }
    }

    private CardCell cardCell;
    private UserAreaInputView inputView;
    private UserAreaBottomView bottomView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("YourKey", R.string.YourKey));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        fragmentView = new LinearLayout(context);
        LinearLayout linearLayout = (LinearLayout) fragmentView;
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        fragmentView.setOnTouchListener((v, event) -> true);

        cardCell = new CardCell(context);
        inputView = new UserAreaInputView(context);
        bottomView = new UserAreaBottomView(context);

        linearLayout.addView(cardCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, AndroidUtilities.dp(16), 0, 0));
        linearLayout.addView(inputView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(20), 0));
        linearLayout.addView(bottomView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 0, AndroidUtilities.dp(26), 0, 0));

        return fragmentView;
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        return themeDescriptions;
    }
}