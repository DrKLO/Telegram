/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Adapters.CountryAdapter.Country;
import org.telegram.ui.Cells.TextSettingsCell;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class CountrySearchAdapter extends BaseFragmentAdapter {

    private Context mContext;
    private Timer searchTimer;
    private ArrayList<CountryAdapter.Country> searchResult;
    private HashMap<String, ArrayList<Country>> countries;

    public CountrySearchAdapter(Context context, HashMap<String, ArrayList<Country>> countries) {
        mContext = context;
        this.countries = countries;
    }

    public void search(final String query) {
        if (query == null) {
            searchResult = null;
        } else {
            try {
                if (searchTimer != null) {
                    searchTimer.cancel();
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            searchTimer = new Timer();
            searchTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        searchTimer.cancel();
                        searchTimer = null;
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                    processSearch(query);
                }
            }, 100, 300);
        }
    }

    private void processSearch(final String query) {
        Utilities.searchQueue.postRunnable(new Runnable() {
            @Override
            public void run() {

                String q = query.trim().toLowerCase();
                if (q.length() == 0) {
                    updateSearchResults(new ArrayList<Country>());
                    return;
                }
                ArrayList<Country> resultArray = new ArrayList<>();

                String n = query.substring(0, 1);
                ArrayList<Country> arr = countries.get(n.toUpperCase());
                if (arr != null) {
                    for (Country c : arr) {
                        if (c.name.toLowerCase().startsWith(query)) {
                            resultArray.add(c);
                        }
                    }
                }

                updateSearchResults(resultArray);
            }
        });
    }

    private void updateSearchResults(final ArrayList<Country> arrCounties) {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                searchResult = arrCounties;
                notifyDataSetChanged();
            }
        });
    }

    @Override
    public boolean areAllItemsEnabled() {
        return true;
    }

    @Override
    public boolean isEnabled(int i) {
        return true;
    }

    @Override
    public int getCount() {
        if (searchResult == null) {
            return 0;
        }
        return searchResult.size();
    }

    @Override
    public Country getItem(int i) {
        if (i < 0 || i >= searchResult.size()) {
            return null;
        }
        return searchResult.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        if (view == null) {
            view = new TextSettingsCell(mContext);
        }

        Country c = searchResult.get(i);
        ((TextSettingsCell) view).setTextAndValue(c.name, "+" + c.code, i != searchResult.size() - 1);

        return view;
    }

    @Override
    public int getItemViewType(int i) {
        return 0;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return searchResult == null || searchResult.size() == 0;
    }
}
