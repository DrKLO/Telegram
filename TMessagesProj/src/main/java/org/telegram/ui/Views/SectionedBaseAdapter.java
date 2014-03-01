/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.Views;

import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import org.telegram.ui.Adapters.BaseFragmentAdapter;

public abstract class SectionedBaseAdapter extends BaseFragmentAdapter implements PinnedHeaderListView.PinnedSectionedHeaderAdapter {

    /**
     * Holds the calculated values of @{link getPositionInSectionForPosition}
     */
    private SparseArray<Integer> mSectionPositionCache;
    /**
     * Holds the calculated values of @{link getSectionForPosition}
     */
    private SparseArray<Integer> mSectionCache;
    /**
     * Holds the calculated values of @{link getCountForSection}
     */
    private SparseArray<Integer> mSectionCountCache;

    /**
     * Caches the item count
     */
    private int mCount;
    /**
     * Caches the section count
     */
    private int mSectionCount;

    public SectionedBaseAdapter() {
        super();
        mSectionCache = new SparseArray<Integer>();
        mSectionPositionCache = new SparseArray<Integer>();
        mSectionCountCache = new SparseArray<Integer>();
        mCount = -1;
        mSectionCount = -1;
    }

    @Override
    public void notifyDataSetChanged() {
        mSectionCache.clear();
        mSectionPositionCache.clear();
        mSectionCountCache.clear();
        mCount = -1;
        mSectionCount = -1;
        super.notifyDataSetChanged();
    }

    @Override
    public void notifyDataSetInvalidated() {
        mSectionCache.clear();
        mSectionPositionCache.clear();
        mSectionCountCache.clear();
        mCount = -1;
        mSectionCount = -1;
        super.notifyDataSetInvalidated();
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        return !isSectionHeader(position);
    }

    @Override
    public final int getCount() {
        if (mCount >= 0) {
            return mCount;
        }
        int count = 0;
        for (int i = 0; i < internalGetSectionCount(); i++) {
            count += internalGetCountForSection(i);
            count++;
        }
        mCount = count;
        return count;
    }

    @Override
    public final Object getItem(int position) {
        return getItem(getSectionForPosition(position), getPositionInSectionForPosition(position));
    }

    @Override
    public final long getItemId(int position) {
        return getItemId(getSectionForPosition(position), getPositionInSectionForPosition(position));
    }

    @Override
    public final View getView(int position, View convertView, ViewGroup parent) {
        if (isSectionHeader(position)) {
            return getSectionHeaderView(getSectionForPosition(position), convertView, parent);
        }
        return getItemView(getSectionForPosition(position), getPositionInSectionForPosition(position), convertView, parent);
    }

    @Override
    public final int getItemViewType(int position) {
        if (isSectionHeader(position)) {
            return getItemViewTypeCount() + getSectionHeaderViewType(getSectionForPosition(position));
        }
        return getItemViewType(getSectionForPosition(position), getPositionInSectionForPosition(position));
    }

    @Override
    public final int getViewTypeCount() {
        return getItemViewTypeCount() + getSectionHeaderViewTypeCount();
    }

    public final int getSectionForPosition(int position) {
        // first try to retrieve values from cache
        Integer cachedSection = mSectionCache.get(position);
        if (cachedSection != null) {
            return cachedSection;
        }
        int sectionStart = 0;
        for (int i = 0; i < internalGetSectionCount(); i++) {
            int sectionCount = internalGetCountForSection(i);
            int sectionEnd = sectionStart + sectionCount + 1;
            if (position >= sectionStart && position < sectionEnd) {
                mSectionCache.put(position, i);
                return i;
            }
            sectionStart = sectionEnd;
        }
        return 0;
    }

    public int getPositionInSectionForPosition(int position) {
        if (position == 0) {
            position = 1;
        }
        // first try to retrieve values from cache
        Integer cachedPosition = mSectionPositionCache.get(position);
        if (cachedPosition != null) {
            return cachedPosition;
        }
        int sectionStart = 0;
        for (int i = 0; i < internalGetSectionCount(); i++) {
            int sectionCount = internalGetCountForSection(i);
            int sectionEnd = sectionStart + sectionCount + 1;
            if (position >= sectionStart && position < sectionEnd) {
                int positionInSection = position - sectionStart - 1;
                mSectionPositionCache.put(position, positionInSection);
                return positionInSection;
            }
            sectionStart = sectionEnd;
        }
        return 0;
    }

    public final boolean isSectionHeader(int position) {
        int sectionStart = 0;
        for (int i = 0; i < internalGetSectionCount(); i++) {
            if (position == sectionStart) {
                return true;
            } else if (position < sectionStart) {
                return false;
            }
            sectionStart += internalGetCountForSection(i) + 1;
        }
        return false;
    }

    public int getItemViewType(int section, int position) {
        return 0;
    }

    public int getItemViewTypeCount() {
        return 1;
    }

    public int getSectionHeaderViewType(int section) {
        return 0;
    }

    public int getSectionHeaderViewTypeCount() {
        return 1;
    }

    public abstract Object getItem(int section, int position);

    public abstract long getItemId(int section, int position);

    public abstract int getSectionCount();

    public abstract int getCountForSection(int section);

    public abstract View getItemView(int section, int position, View convertView, ViewGroup parent);

    public abstract View getSectionHeaderView(int section, View convertView, ViewGroup parent);

    private int internalGetCountForSection(int section) {
        Integer cachedSectionCount = mSectionCountCache.get(section);
        if (cachedSectionCount != null) {
            return cachedSectionCount;
        }
        int sectionCount = getCountForSection(section);
        mSectionCountCache.put(section, sectionCount);
        return sectionCount;
    }

    private int internalGetSectionCount() {
        if (mSectionCount >= 0) {
            return mSectionCount;
        }
        mSectionCount = getSectionCount();
        return mSectionCount;
    }

}
