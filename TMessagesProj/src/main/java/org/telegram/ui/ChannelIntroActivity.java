/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

public class ChannelIntroActivity extends BaseFragment {

    private ImageView imageView;
    private TextView createChannelText;
    private TextView whatIsChannelText;
    private TextView descriptionText;

    @Override
    public View createView(Context context) {
        actionBar.setBackgroundColor(Theme.ACTION_BAR_CHANNEL_INTRO_COLOR);
        actionBar.setBackButtonImage(R.drawable.pl_back);
        actionBar.setItemsBackgroundColor(Theme.ACTION_BAR_CHANNEL_INTRO_SELECTOR_COLOR);
        actionBar.setCastShadows(false);
        if (!AndroidUtilities.isTablet()) {
            actionBar.showActionModeTop();
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new ViewGroup(context) {

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = MeasureSpec.getSize(widthMeasureSpec);
                int height = MeasureSpec.getSize(heightMeasureSpec);

                if (width > height) {
                    imageView.measure(MeasureSpec.makeMeasureSpec((int) (width * 0.45f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int) (height * 0.78f), MeasureSpec.EXACTLY));
                    whatIsChannelText.measure(MeasureSpec.makeMeasureSpec((int) (width * 0.6f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                    descriptionText.measure(MeasureSpec.makeMeasureSpec((int) (width * 0.5f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                    createChannelText.measure(MeasureSpec.makeMeasureSpec((int) (width * 0.6f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24), MeasureSpec.EXACTLY));
                } else {
                    imageView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int) (height * 0.44f), MeasureSpec.EXACTLY));
                    whatIsChannelText.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                    descriptionText.measure(MeasureSpec.makeMeasureSpec((int) (width * 0.9f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                    createChannelText.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24), MeasureSpec.EXACTLY));
                }

                setMeasuredDimension(width, height);
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                int width = r - l;
                int height = b - t;

                if (r > b) {
                    int y = (int) (height * 0.05f);
                    imageView.layout(0, y, imageView.getMeasuredWidth(), y + imageView.getMeasuredHeight());
                    int x = (int) (width * 0.4f);
                    y = (int) (height * 0.14f);
                    whatIsChannelText.layout(x, y, x + whatIsChannelText.getMeasuredWidth(), y + whatIsChannelText.getMeasuredHeight());
                    y = (int) (height * 0.61f);
                    createChannelText.layout(x, y, x + createChannelText.getMeasuredWidth(), y + createChannelText.getMeasuredHeight());
                    x = (int) (width * 0.45f);
                    y = (int) (height * 0.31f);
                    descriptionText.layout(x, y, x + descriptionText.getMeasuredWidth(), y + descriptionText.getMeasuredHeight());
                } else {
                    int y = (int) (height * 0.05f);
                    imageView.layout(0, y, imageView.getMeasuredWidth(), y + imageView.getMeasuredHeight());
                    y = (int) (height * 0.59f);
                    whatIsChannelText.layout(0, y, whatIsChannelText.getMeasuredWidth(), y + whatIsChannelText.getMeasuredHeight());
                    y = (int) (height * 0.68f);
                    int x = (int) (width * 0.05f);
                    descriptionText.layout(x, y, x + descriptionText.getMeasuredWidth(), y + descriptionText.getMeasuredHeight());
                    y = (int) (height * 0.86f);
                    createChannelText.layout(0, y, createChannelText.getMeasuredWidth(), y + createChannelText.getMeasuredHeight());
                }
            }
        };
        fragmentView.setBackgroundColor(0xffffffff);
        ViewGroup viewGroup = (ViewGroup) fragmentView;
        viewGroup.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        imageView = new ImageView(context);
        imageView.setImageResource(R.drawable.channelintro);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        viewGroup.addView(imageView);

        whatIsChannelText = new TextView(context);
        whatIsChannelText.setTextColor(0xff212121);
        whatIsChannelText.setGravity(Gravity.CENTER_HORIZONTAL);
        whatIsChannelText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        whatIsChannelText.setText(LocaleController.getString("ChannelAlertTitle", R.string.ChannelAlertTitle));
        viewGroup.addView(whatIsChannelText);

        descriptionText = new TextView(context);
        descriptionText.setTextColor(0xff787878);
        descriptionText.setGravity(Gravity.CENTER_HORIZONTAL);
        descriptionText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        descriptionText.setText(LocaleController.getString("ChannelAlertText", R.string.ChannelAlertText));
        viewGroup.addView(descriptionText);

        createChannelText = new TextView(context);
        createChannelText.setTextColor(0xff4c8eca);
        createChannelText.setGravity(Gravity.CENTER);
        createChannelText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        createChannelText.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        createChannelText.setText(LocaleController.getString("ChannelAlertCreate", R.string.ChannelAlertCreate));
        viewGroup.addView(createChannelText);
        createChannelText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Bundle args = new Bundle();
                args.putInt("step", 0);
                presentFragment(new ChannelCreateActivity(args), true);
            }
        });

        return fragmentView;
    }
}
