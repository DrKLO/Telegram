/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.Views;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.Emoji;
import org.telegram.messenger.R;

import java.util.ArrayList;

public class EmojiView extends LinearLayout {
    private ArrayList<EmojiGridAdapter> adapters = new ArrayList<EmojiGridAdapter>();
    private int[] icons = {
            R.drawable.ic_emoji_recent,
            R.drawable.ic_emoji_smile,
            R.drawable.ic_emoji_flower,
            R.drawable.ic_emoji_bell,
            R.drawable.ic_emoji_car,
            R.drawable.ic_emoji_symbol };
    private Listener listener;
    private ViewPager pager;
    private FrameLayout recentsWrap;
    private ArrayList<GridView> views = new ArrayList<GridView>();

    public EmojiView(Context paramContext) {
        super(paramContext);
        init();
    }

    public EmojiView(Context paramContext, AttributeSet paramAttributeSet) {
        super(paramContext, paramAttributeSet);
        init();
    }

    public EmojiView(Context paramContext, AttributeSet paramAttributeSet, int paramInt) {
        super(paramContext, paramAttributeSet, paramInt);
        init();
    }

    private void addToRecent(long paramLong) {
        if (this.pager.getCurrentItem() == 0) {
            return;
        }
        ArrayList<Long> localArrayList = new ArrayList<Long>();
        long[] currentRecent = Emoji.data[0];
        boolean was = false;
        for (long aCurrentRecent : currentRecent) {
            if (paramLong == aCurrentRecent) {
                localArrayList.add(0, paramLong);
                was = true;
            } else {
                localArrayList.add(aCurrentRecent);
            }
        }
        if (!was) {
            localArrayList.add(0, paramLong);
        }
        Emoji.data[0] = new long[Math.min(localArrayList.size(), 50)];
        for (int q = 0; q < Emoji.data[0].length; q++) {
            Emoji.data[0][q] = localArrayList.get(q);
        }
        adapters.get(0).data = Emoji.data[0];
        adapters.get(0).notifyDataSetChanged();
        saveRecents();
    }

    private String convert(long paramLong) {
        String str = "";
        for (int i = 0; ; i++) {
            if (i >= 4) {
                return str;
            }
            int j = (int)(0xFFFF & paramLong >> 16 * (3 - i));
            if (j != 0) {
                str = str + (char)j;
            }
        }
    }

    private void init() {
        setOrientation(1);
        for (int i = 0; ; i++) {
            if (i >= Emoji.data.length) {
                setBackgroundDrawable(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] { -14145496, -16777216 }));
                pager = new ViewPager(getContext());
                pager.setAdapter(new EmojiPagesAdapter());
                PagerSlidingTabStrip tabs = new PagerSlidingTabStrip(getContext());
                tabs.setViewPager(this.pager);
                tabs.setShouldExpand(true);
                tabs.setIndicatorColor(0xff33b5e5);
                tabs.setIndicatorHeight(Emoji.scale(2.0F));
                tabs.setUnderlineHeight(Emoji.scale(2.0F));
                tabs.setUnderlineColor(1711276032);
                tabs.setTabBackground(0);
                LinearLayout localLinearLayout = new LinearLayout(getContext());
                localLinearLayout.setOrientation(0);
                localLinearLayout.addView(tabs, new LinearLayout.LayoutParams(-1, -1, 1.0F));
                ImageView localImageView = new ImageView(getContext());
                localImageView.setImageResource(R.drawable.ic_emoji_backspace);
                localImageView.setScaleType(ImageView.ScaleType.CENTER);
                localImageView.setBackgroundResource(R.drawable.bg_emoji_bs);
                localImageView.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View paramAnonymousView) {
                        if (EmojiView.this.listener != null) {
                            EmojiView.this.listener.onBackspace();
                        }
                    }
                });
                localImageView.setOnLongClickListener(new View.OnLongClickListener() {
                    public boolean onLongClick(View paramAnonymousView) {
                        EmojiView.this.getContext().getSharedPreferences("emoji", 0).edit().clear().commit();
                        return true;
                    }
                });
                localLinearLayout.addView(localImageView, new LinearLayout.LayoutParams(Emoji.scale(61.0F), -1));
                this.recentsWrap = new FrameLayout(getContext());
                this.recentsWrap.addView(this.views.get(0));
                TextView localTextView = new TextView(getContext());
                localTextView.setText(R.string.NoRecent);
                localTextView.setTextSize(18.0F);
                localTextView.setTextColor(-7829368);
                localTextView.setGravity(17);
                this.recentsWrap.addView(localTextView);
                this.views.get(0).setEmptyView(localTextView);
                addView(localLinearLayout, new LinearLayout.LayoutParams(-1, Emoji.scale(48.0F)));
                addView(this.pager);
                loadRecents();
                return;
            }
            GridView localGridView = new GridView(getContext());
            localGridView.setColumnWidth(Emoji.scale(45.0F));
            localGridView.setNumColumns(-1);
            EmojiGridAdapter localEmojiGridAdapter = new EmojiGridAdapter(Emoji.data[i]);
            localGridView.setAdapter(localEmojiGridAdapter);
            this.adapters.add(localEmojiGridAdapter);
            this.views.add(localGridView);
        }
    }

    private void saveRecents() {
        ArrayList<Long> localArrayList = new ArrayList<Long>();
        long[] arrayOfLong = Emoji.data[0];
        int i = arrayOfLong.length;
        for (int j = 0; ; j++) {
            if (j >= i) {
                getContext().getSharedPreferences("emoji", 0).edit().putString("recents", TextUtils.join(",", localArrayList)).commit();
                return;
            }
            localArrayList.add(arrayOfLong[j]);
        }
    }

    public void loadRecents() {
        String str = getContext().getSharedPreferences("emoji", 0).getString("recents", "");
        String[] arrayOfString = null;
        if ((str != null) && (str.length() > 0)) {
            arrayOfString = str.split(",");
            Emoji.data[0] = new long[arrayOfString.length];
        }
        if (arrayOfString != null) {
            for (int i = 0; i < arrayOfString.length; i++) {
                Emoji.data[0][i] = Long.parseLong(arrayOfString[i]);
            }
            adapters.get(0).data = Emoji.data[0];
            adapters.get(0).notifyDataSetChanged();
        }
    }

    public void onMeasure(int paramInt1, int paramInt2) {
        super.onMeasure(View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.getSize(paramInt1), MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.getSize(paramInt2), MeasureSpec.EXACTLY));
    }

    public void setListener(Listener paramListener) {
        this.listener = paramListener;
    }

    public void invalidateViews() {
        for (GridView gridView : views) {
            if (gridView != null) {
                gridView.invalidateViews();
            }
        }
    }

    private class EmojiGridAdapter extends BaseAdapter {
        long[] data;

        public EmojiGridAdapter(long[] arg2) {
            this.data = arg2;
        }

        public int getCount() {
            return this.data.length;
        }

        public Object getItem(int paramInt)
        {
            return null;
        }

        public long getItemId(int paramInt)
        {
            return this.data[paramInt];
        }

        public View getView(int paramInt, View paramView, ViewGroup paramViewGroup) {
            ImageView localObject;
            if (paramView != null) {
                localObject = (ImageView)paramView;
            } else {
                localObject = new ImageView(EmojiView.this.getContext()) {
                    public void onMeasure(int paramAnonymousInt1, int paramAnonymousInt2) {
                        setMeasuredDimension(View.MeasureSpec.getSize(paramAnonymousInt1), View.MeasureSpec.getSize(paramAnonymousInt1));
                    }
                };
                localObject.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View paramAnonymousView) {
                        if (EmojiView.this.listener != null) {
                            EmojiView.this.listener.onEmojiSelected(EmojiView.this.convert((Long)paramAnonymousView.getTag()));
                        }
                        EmojiView.this.addToRecent((Long)paramAnonymousView.getTag());
                    }
                });
                localObject.setBackgroundResource(R.drawable.list_selector);
                localObject.setScaleType(ImageView.ScaleType.CENTER);
            }

            localObject.setImageDrawable(Emoji.getEmojiBigDrawable(this.data[paramInt]));
            localObject.setTag(this.data[paramInt]);
            return localObject;
        }
    }

    private class EmojiPagesAdapter extends PagerAdapter implements PagerSlidingTabStrip.IconTabProvider {

        private EmojiPagesAdapter() {
        }

        public void destroyItem(ViewGroup paramViewGroup, int paramInt, Object paramObject) {
            View localObject;
            if (paramInt == 0) {
                localObject = EmojiView.this.recentsWrap;
            } else {
                localObject = EmojiView.this.views.get(paramInt);
            }
            paramViewGroup.removeView(localObject);
        }

        public int getCount() {
            return EmojiView.this.views.size();
        }

        public int getPageIconResId(int paramInt) {
            return EmojiView.this.icons[paramInt];
        }

        public Object instantiateItem(ViewGroup paramViewGroup, int paramInt) {
            View localObject;
            if (paramInt == 0) {
                localObject = EmojiView.this.recentsWrap;
            } else {
                localObject = EmojiView.this.views.get(paramInt);
            }
            paramViewGroup.addView(localObject);
            return localObject;
        }

        public boolean isViewFromObject(View paramView, Object paramObject) {
            return paramView == paramObject;
        }
    }

    public static abstract interface Listener {
        public abstract void onBackspace();
        public abstract void onEmojiSelected(String paramString);
    }
}