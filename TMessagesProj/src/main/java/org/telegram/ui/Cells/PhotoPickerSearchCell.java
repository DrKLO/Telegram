/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import org.telegram.messenger.R;

public class PhotoPickerSearchCell extends LinearLayout {

    public static interface PhotoPickerSearchCellDelegate {
        public abstract void didPressedSearchButton(int index);
    }

    private class SearchButton extends FrameLayout {

        private TextView textView1;
        private TextView textView2;
        private ImageView imageView;
        private View selector;

        public SearchButton(Context context) {
            super(context);

            setBackgroundColor(0xff292929);

            selector = new View(context);
            selector.setBackgroundResource(R.drawable.list_selector);
            addView(selector);
            FrameLayout.LayoutParams layoutParams1 = (FrameLayout.LayoutParams) selector.getLayoutParams();
            layoutParams1.width = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams1.height = FrameLayout.LayoutParams.MATCH_PARENT;
            selector.setLayoutParams(layoutParams1);

            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(HORIZONTAL);
            addView(linearLayout);
            layoutParams1 = (FrameLayout.LayoutParams) linearLayout.getLayoutParams();
            layoutParams1.width = FrameLayout.LayoutParams.WRAP_CONTENT;
            layoutParams1.height = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams1.gravity = Gravity.CENTER;
            linearLayout.setLayoutParams(layoutParams1);

            imageView = new ImageView(context);
            linearLayout.addView(imageView);
            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) imageView.getLayoutParams();
            layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT;
            imageView.setLayoutParams(layoutParams);

            FrameLayout frameLayout = new FrameLayout(context);
            frameLayout.setPadding(AndroidUtilities.dp(4), 0, 0, 0);
            linearLayout.addView(frameLayout);
            layoutParams = (LinearLayout.LayoutParams) frameLayout.getLayoutParams();
            layoutParams.height = LinearLayout.LayoutParams.MATCH_PARENT;
            layoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT;
            frameLayout.setLayoutParams(layoutParams);

            textView1 = new TextView(context);
            textView1.setGravity(Gravity.CENTER_VERTICAL);
            textView1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            textView1.setPadding(0, 0, AndroidUtilities.dp(8), 0);
            textView1.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            textView1.setTextColor(0xffffffff);
            frameLayout.addView(textView1);
            layoutParams1 = (FrameLayout.LayoutParams) textView1.getLayoutParams();
            layoutParams1.width = FrameLayout.LayoutParams.WRAP_CONTENT;
            layoutParams1.height = FrameLayout.LayoutParams.MATCH_PARENT;
            textView1.setLayoutParams(layoutParams1);

            textView2 = new TextView(context);
            textView2.setGravity(Gravity.CENTER_VERTICAL);
            textView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 9);
            textView2.setPadding(0, AndroidUtilities.dp(24), AndroidUtilities.dp(8), 0);
            textView2.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            textView2.setTextColor(0xff464646);
            frameLayout.addView(textView2);
            layoutParams1 = (FrameLayout.LayoutParams) textView2.getLayoutParams();
            layoutParams1.width = FrameLayout.LayoutParams.WRAP_CONTENT;
            layoutParams1.height = FrameLayout.LayoutParams.MATCH_PARENT;
            textView2.setLayoutParams(layoutParams1);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (Build.VERSION.SDK_INT >= 21) {
                selector.drawableHotspotChanged(event.getX(), event.getY());
            }
            return super.onTouchEvent(event);
        }
    }

    private PhotoPickerSearchCellDelegate delegate;

    public PhotoPickerSearchCell(Context context) {
        super(context);
        setOrientation(HORIZONTAL);

        SearchButton searchButton = new SearchButton(context);
        searchButton.textView1.setText(LocaleController.getString("SearchImages", R.string.SearchImages));
        searchButton.imageView.setImageResource(R.drawable.web_search);
        addView(searchButton);
        LayoutParams layoutParams = (LayoutParams) searchButton.getLayoutParams();
        layoutParams.weight = 0.5f;
        layoutParams.topMargin = AndroidUtilities.dp(4);
        layoutParams.height = AndroidUtilities.dp(48);
        layoutParams.width = 0;
        searchButton.setLayoutParams(layoutParams);
        searchButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (delegate != null) {
                    delegate.didPressedSearchButton(0);
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(0);
        addView(frameLayout);
        layoutParams = (LayoutParams) frameLayout.getLayoutParams();
        layoutParams.topMargin = AndroidUtilities.dp(4);
        layoutParams.height = AndroidUtilities.dp(48);
        layoutParams.width = AndroidUtilities.dp(4);
        frameLayout.setLayoutParams(layoutParams);

        searchButton = new SearchButton(context);
        searchButton.textView1.setText(LocaleController.getString("SearchGifs", R.string.SearchGifs));
        searchButton.textView2.setText("GIPHY");
        searchButton.imageView.setImageResource(R.drawable.gif_search);
        addView(searchButton);
        layoutParams = (LayoutParams) searchButton.getLayoutParams();
        layoutParams.weight = 0.5f;
        layoutParams.topMargin = AndroidUtilities.dp(4);
        layoutParams.height = AndroidUtilities.dp(48);
        layoutParams.width = 0;
        searchButton.setLayoutParams(layoutParams);
        searchButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (delegate != null) {
                    delegate.didPressedSearchButton(1);
                }
            }
        });
    }

    public void setDelegate(PhotoPickerSearchCellDelegate delegate) {
        this.delegate = delegate;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(52), MeasureSpec.EXACTLY));
    }
}
