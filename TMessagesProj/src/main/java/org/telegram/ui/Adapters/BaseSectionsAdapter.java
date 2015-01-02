/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Adapters;

import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;

public abstract class BaseSectionsAdapter extends BaseFragmentAdapter {

    private SparseArray<Integer> sectionPositionCache;
    private SparseArray<Integer> sectionCache;
    private SparseArray<Integer> sectionCountCache;
    private int sectionCount;
    private int count;

    private void cleanupCache() {
        sectionCache = new SparseArray<>();
        sectionPositionCache = new SparseArray<>();
        sectionCountCache = new SparseArray<>();
        count = -1;
        sectionCount = -1;
    }

    public BaseSectionsAdapter() {
        super();
        cleanupCache();
    }

    @Override
    public void notifyDataSetChanged() {
        cleanupCache();
        super.notifyDataSetChanged();
    }

    @Override
    public void notifyDataSetInvalidated() {
        cleanupCache();
        super.notifyDataSetInvalidated();
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        return isRowEnabled(getSectionForPosition(position), getPositionInSectionForPosition(position));
    }

    @Override
    public final long getItemId(int position) {
        return position;
    }

    @Override
    public final int getCount() {
        if (count >= 0) {
            return count;
        }
        count = 0;
        for (int i = 0; i < internalGetSectionCount(); i++) {
            count += internalGetCountForSection(i);
        }
        return count;
    }

    @Override
    public final Object getItem(int position) {
        return getItem(getSectionForPosition(position), getPositionInSectionForPosition(position));
    }

    @Override
    public final int getItemViewType(int position) {
        return getItemViewType(getSectionForPosition(position), getPositionInSectionForPosition(position));
    }

    @Override
    public final View getView(int position, View convertView, ViewGroup parent) {
        return getItemView(getSectionForPosition(position), getPositionInSectionForPosition(position), convertView, parent);
    }

    private int internalGetCountForSection(int section) {
        Integer cachedSectionCount = sectionCountCache.get(section);
        if (cachedSectionCount != null) {
            return cachedSectionCount;
        }
        int sectionCount = getCountForSection(section);
        sectionCountCache.put(section, sectionCount);
        return sectionCount;
    }

    private int internalGetSectionCount() {
        if (sectionCount >= 0) {
            return sectionCount;
        }
        sectionCount = getSectionCount();
        return sectionCount;
    }

    public final int getSectionForPosition(int position) {
        Integer cachedSection = sectionCache.get(position);
        if (cachedSection != null) {
            return cachedSection;
        }
        int sectionStart = 0;
        for (int i = 0; i < internalGetSectionCount(); i++) {
            int sectionCount = internalGetCountForSection(i);
            int sectionEnd = sectionStart + sectionCount;
            if (position >= sectionStart && position < sectionEnd) {
                sectionCache.put(position, i);
                return i;
            }
            sectionStart = sectionEnd;
        }
        return -1;
    }

    public int getPositionInSectionForPosition(int position) {
        Integer cachedPosition = sectionPositionCache.get(position);
        if (cachedPosition != null) {
            return cachedPosition;
        }
        int sectionStart = 0;
        for (int i = 0; i < internalGetSectionCount(); i++) {
            int sectionCount = internalGetCountForSection(i);
            int sectionEnd = sectionStart + sectionCount;
            if (position >= sectionStart && position < sectionEnd) {
                int positionInSection = position - sectionStart;
                sectionPositionCache.put(position, positionInSection);
                return positionInSection;
            }
            sectionStart = sectionEnd;
        }
        return -1;
    }

    public abstract int getSectionCount();
    public abstract int getCountForSection(int section);
    public abstract boolean isRowEnabled(int section, int row);
    public abstract int getItemViewType(int section, int position);
    public abstract Object getItem(int section, int position);
    public abstract View getItemView(int section, int position, View convertView, ViewGroup parent);
    public abstract View getSectionHeaderView(int section, View convertView, ViewGroup parent);
}
