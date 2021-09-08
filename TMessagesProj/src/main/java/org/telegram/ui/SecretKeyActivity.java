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

import java.util.ArrayList;

public class SecretKeyActivity extends BaseFragment {

    public class SecretKeyCell extends FrameLayout {

        public class GrayRoundedRect extends View {

            private final int SIDE_SIZE = AndroidUtilities.dp(260);

            Paint paint = new Paint();
            RectF rect = new RectF(0, 0, SIDE_SIZE, SIDE_SIZE);

            final Path path = new Path();

            float[] corners = new float[] {
                    30, 30,
                    30, 30,
                    30, 30,
                    30, 30
            };

            public GrayRoundedRect(Context context) {
                super(context);

                paint.setStrokeWidth(0);
                paint.setColor(Theme.getColor(Theme.key_wallet_grayText2));
                path.addRoundRect(rect, corners, Path.Direction.CW);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                setMeasuredDimension(SIDE_SIZE, SIDE_SIZE);
            }

            @Override
            public void onDraw(Canvas canvas) {
                canvas.drawPath(path, paint);
            }
        }

        private TextView secretKeyTextView;
        private String secretValue = "";

        public SecretKeyCell(Context context) {
            super(context);
            addView(new GrayRoundedRect(context), LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

            secretKeyTextView = new TextView(context);
            secretKeyTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 21);
            secretKeyTextView.setTextColor(Theme.getColor(Theme.key_wallet_whiteText));
            secretKeyTextView.setGravity(Gravity.CENTER);

            addView(secretKeyTextView, LayoutHelper.createFrame(AndroidUtilities.dp(74), AndroidUtilities.dp(74), Gravity.CENTER, 20, 20, 20, 20));
        }

        public void setSecretKey(String key) {
            secretValue = key;
            secretKeyTextView.setText(secretValue);
        }

    }

    private TextView helpTextView;
    private SecretKeyCell keyCell;

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
        linearLayout.setGravity(Gravity.CENTER_HORIZONTAL);

        fragmentView.setOnTouchListener((v, event) -> true);

        helpTextView = new TextView(context);
        helpTextView.setFocusable(true);
        helpTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 21);
        helpTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        helpTextView.setGravity(Gravity.CENTER);
        helpTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString("DontShareKey", R.string.DontShareKey)));
        linearLayout.addView(helpTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 80, 60, 80, 0));

        keyCell = new SecretKeyCell(context);
        linearLayout.addView(keyCell, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 90, 0, 0));

        return fragmentView;
    }

    public void setSecretKey(String key) { // todo: api required
        keyCell.setSecretKey(key);
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(helpTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlueText));

        return themeDescriptions;
    }
}
