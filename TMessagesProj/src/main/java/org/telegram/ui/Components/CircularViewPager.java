package org.telegram.ui.Components;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

public class CircularViewPager extends ViewPager {

    private Adapter adapter;

    public CircularViewPager(@NonNull Context context) {
        super(context);
    }

    public CircularViewPager(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    {
        addOnPageChangeListener(new OnPageChangeListener() {

            private int scrollState;

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                if (position == getCurrentItem() && positionOffset == 0f && scrollState == ViewPager.SCROLL_STATE_DRAGGING) {
                    checkCurrentItem();
                }
            }

            @Override
            public void onPageSelected(int position) {
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                if (state == ViewPager.SCROLL_STATE_IDLE) {
                    checkCurrentItem();
                }
                scrollState = state;
            }

            private void checkCurrentItem() {
                if (adapter != null) {
                    final int position = getCurrentItem();
                    final int newPosition = adapter.getExtraCount() + adapter.getRealPosition(position);
                    if (position != newPosition) {
                        setCurrentItem(newPosition, false);
                    }
                }
            }
        });
    }

    @Override
    @Deprecated
    public void setAdapter(@Nullable PagerAdapter adapter) {
        if (adapter instanceof Adapter) {
            setAdapter((Adapter) adapter);
        } else {
            throw new IllegalArgumentException();
        }
    }

    public void setAdapter(Adapter adapter) {
        this.adapter = adapter;
        super.setAdapter(adapter);
        if (adapter != null) {
            setCurrentItem(adapter.getExtraCount(), false);
        }
    }

    public static abstract class Adapter extends PagerAdapter {

        public int getRealPosition(int adapterPosition) {
            final int count = getCount();
            final int extraCount = getExtraCount();
            if (adapterPosition < extraCount) {
                return count - extraCount * 2 - (extraCount - adapterPosition - 1) - 1;
            } else if (adapterPosition >= count - extraCount) {
                return adapterPosition - (count - extraCount);
            } else {
                return adapterPosition - extraCount;
            }
        }

        public abstract int getExtraCount();
    }
}
