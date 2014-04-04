/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.support.v4.internal.view.SupportMenuItem;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.SearchView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Views.PinnedHeaderListView;
import org.telegram.ui.Views.SectionedBaseAdapter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class CountrySelectActivity extends ActionBarActivity {
    private SupportMenuItem searchItem;
    private SearchView searchView;
    private SectionedBaseAdapter listViewAdapter;
    private PinnedHeaderListView listView;
    private boolean searchWas;
    private boolean searching;
    private BaseAdapter searchListViewAdapter;
    private TextView emptyTextView;
    private HashMap<String, ArrayList<Country>> countries = new HashMap<String, ArrayList<Country>>();
    private ArrayList<String> sortedCountries = new ArrayList<String>();

    private Timer searchTimer;
    public ArrayList<Country> searchResult;

    public static class Country {
        public String name;
        public String code;
        public String shortname;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        searching = false;
        searchWas = false;

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(getResources().getAssets().open("countries.txt")));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] args = line.split(";");
                Country c = new Country();
                c.name = args[2];
                c.code = args[0];
                c.shortname = args[1];
                String n = c.name.substring(0, 1).toUpperCase();
                ArrayList<Country> arr = countries.get(n);
                if (arr == null) {
                    arr = new ArrayList<Country>();
                    countries.put(n, arr);
                    sortedCountries.add(n);
                }
                arr.add(c);
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }

        Collections.sort(sortedCountries, new Comparator<String>() {
            @Override
            public int compare(String lhs, String rhs) {
                return lhs.compareTo(rhs);
            }
        });

        for (ArrayList<Country> arr : countries.values()) {
            Collections.sort(arr, new Comparator<Country>() {
                @Override
                public int compare(Country country, Country country2) {
                    return country.name.compareTo(country2.name);
                }
            });
        }

        setContentView(R.layout.country_select_layout);

        emptyTextView = (TextView)findViewById(R.id.searchEmptyView);
        searchListViewAdapter = new SearchAdapter(this);

        listView = (PinnedHeaderListView)findViewById(R.id.listView);
        listView.setEmptyView(emptyTextView);
        listView.setVerticalScrollBarEnabled(false);

        listView.setAdapter(listViewAdapter = new ListAdapter(this));
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (searching && searchWas) {
                    if (i < searchResult.size()) {
                        Country c = searchResult.get(i);
                        Intent intent = new Intent();
                        intent.putExtra("country", c.name);
                        setResult(RESULT_OK, intent);
                        finish();
                    }
                } else {
                    int section = listViewAdapter.getSectionForPosition(i);
                    int row = listViewAdapter.getPositionInSectionForPosition(i);
                    if (section < sortedCountries.size()) {
                        String n = sortedCountries.get(section);
                        ArrayList<Country> arr = countries.get(n);
                        if (row < arr.size()) {
                            Country c = arr.get(row);
                            Intent intent = new Intent();
                            intent.putExtra("country", c.name);
                            setResult(RESULT_OK, intent);
                            finish();
                        }
                    }
                }
            }
        });

        getWindow().setBackgroundDrawableResource(R.drawable.transparent);
        getWindow().setFormat(PixelFormat.RGB_565);
    }

    public void applySelfActionBar() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayUseLogoEnabled(false);
        actionBar.setDisplayShowCustomEnabled(false);
        actionBar.setCustomView(null);
        actionBar.setSubtitle(null);
        actionBar.setTitle(getString(R.string.ChooseCountry));
        fixBackButton();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applySelfActionBar();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            case android.R.id.home:
                if (searchItem != null) {
                    if (searchItem.isActionViewExpanded()) {
                        searchItem.collapseActionView();
                    }
                }
                finish();
                break;
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        searchItem = (SupportMenuItem)menu.add(Menu.NONE, 0, Menu.NONE, LocaleController.getString("Search", R.string.Search)).setIcon(R.drawable.ic_ab_search);
        searchItem.setShowAsAction(SupportMenuItem.SHOW_AS_ACTION_ALWAYS|SupportMenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
        searchItem.setActionView(searchView = new SearchView(this));

        TextView textView = (TextView) searchView.findViewById(R.id.search_src_text);
        if (textView != null) {
            textView.setTextColor(0xffffffff);
            try {
                Field mCursorDrawableRes = TextView.class.getDeclaredField("mCursorDrawableRes");
                mCursorDrawableRes.setAccessible(true);
                mCursorDrawableRes.set(textView, R.drawable.search_carret);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        ImageView img = (ImageView) searchView.findViewById(R.id.search_close_btn);
        if (img != null) {
            img.setImageResource(R.drawable.ic_msg_btn_cross_custom);
        }

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                search(s);
                if (s.length() != 0) {
                    searchWas = true;
                    if (listView != null) {
                        listView.setPadding(Utilities.dp(16), listView.getPaddingTop(), Utilities.dp(16), listView.getPaddingBottom());
                        listView.setAdapter(searchListViewAdapter);
                        if(android.os.Build.VERSION.SDK_INT >= 11) {
                            listView.setFastScrollAlwaysVisible(false);
                        }
                        listView.setFastScrollEnabled(false);
                        listView.setVerticalScrollBarEnabled(true);
                    }
                    if (emptyTextView != null) {
                        emptyTextView.setText(getString(R.string.NoResult));
                    }
                }
                return true;
            }
        });

        searchItem.setSupportOnActionExpandListener(new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem menuItem) {
                getSupportActionBar().setIcon(R.drawable.ic_ab_search);
                searching = true;
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                searchView.setQuery("", false);
                search(null);
                searching = false;
                searchWas = false;
                ViewGroup group = (ViewGroup) listView.getParent();
                listView.setAdapter(listViewAdapter);
                if (!LocaleController.isRTL) {
                    listView.setPadding(Utilities.dp(16), listView.getPaddingTop(), Utilities.dp(30), listView.getPaddingBottom());
                } else {
                    listView.setPadding(Utilities.dp(30), listView.getPaddingTop(), Utilities.dp(16), listView.getPaddingBottom());
                }
                if (android.os.Build.VERSION.SDK_INT >= 11) {
                    listView.setFastScrollAlwaysVisible(true);
                }
                listView.setFastScrollEnabled(true);
                listView.setVerticalScrollBarEnabled(false);
                applySelfActionBar();

                emptyTextView.setText(getString(R.string.ChooseCountry));
                return true;
            }
        });
        return super.onCreateOptionsMenu(menu);
    }

    public void fixBackButton() {
        if(android.os.Build.VERSION.SDK_INT == 19) {
            //workaround for back button dissapear
            try {
                Class firstClass = getSupportActionBar().getClass();
                Class aClass = firstClass.getSuperclass();
                if (aClass == android.support.v7.app.ActionBar.class) {

                } else {
                    Field field = aClass.getDeclaredField("mActionBar");
                    field.setAccessible(true);
                    android.app.ActionBar bar = (android.app.ActionBar)field.get(getSupportActionBar());

                    field = bar.getClass().getDeclaredField("mActionView");
                    field.setAccessible(true);
                    View v = (View)field.get(bar);
                    aClass = v.getClass();

                    field = aClass.getDeclaredField("mHomeLayout");
                    field.setAccessible(true);
                    v = (View)field.get(v);
                    v.setVisibility(View.VISIBLE);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
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
        Utilities.globalQueue.postRunnable(new Runnable() {
            @Override
            public void run() {

                String q = query.trim().toLowerCase();
                if (q.length() == 0) {
                    updateSearchResults(new ArrayList<Country>());
                    return;
                }
                long time = System.currentTimeMillis();
                ArrayList<Country> resultArray = new ArrayList<Country>();

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
        Utilities.RunOnUIThread(new Runnable() {
            @Override
            public void run() {
                searchResult = arrCounties;
                searchListViewAdapter.notifyDataSetChanged();
            }
        });
    }

    private class SearchAdapter extends BaseAdapter {
        private Context mContext;

        public SearchAdapter(Context context) {
            mContext = context;
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
        public Object getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            if (view == null) {
                LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = li.inflate(R.layout.country_row_layout, viewGroup, false);
            }
            TextView textView = (TextView)view.findViewById(R.id.settings_row_text);
            TextView detailTextView = (TextView)view.findViewById(R.id.settings_row_text_detail);
            View divider = view.findViewById(R.id.settings_row_divider);

            Country c = searchResult.get(i);
            textView.setText(c.name);
            detailTextView.setText("+" + c.code);
            if (i == searchResult.size() - 1) {
                divider.setVisibility(View.GONE);
            } else {
                divider.setVisibility(View.VISIBLE);
            }

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

    private class ListAdapter extends SectionedBaseAdapter {
        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public Object getItem(int section, int position) {
            return null;
        }

        @Override
        public long getItemId(int section, int position) {
            return 0;
        }

        @Override
        public int getSectionCount() {
            return sortedCountries.size();
        }

        @Override
        public int getCountForSection(int section) {
            String n = sortedCountries.get(section);
            ArrayList<Country> arr = countries.get(n);
            return arr.size();
        }

        @Override
        public View getItemView(int section, int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = li.inflate(R.layout.country_row_layout, parent, false);
            }
            TextView textView = (TextView)convertView.findViewById(R.id.settings_row_text);
            TextView detailTextView = (TextView)convertView.findViewById(R.id.settings_row_text_detail);
            View divider = convertView.findViewById(R.id.settings_row_divider);

            String n = sortedCountries.get(section);
            ArrayList<Country> arr = countries.get(n);
            Country c = arr.get(position);
            textView.setText(c.name);
            detailTextView.setText("+" + c.code);
            if (position == arr.size() - 1) {
                divider.setVisibility(View.GONE);
            } else {
                divider.setVisibility(View.VISIBLE);
            }

            return convertView;
        }

        @Override
        public int getItemViewType(int section, int position) {
            return 0;
        }

        @Override
        public int getItemViewTypeCount() {
            return 1;
        }

        @Override
        public int getSectionHeaderViewType(int section) {
            return 0;
        }

        @Override
        public int getSectionHeaderViewTypeCount() {
            return 1;
        }

        @Override
        public View getSectionHeaderView(int section, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater li = (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = li.inflate(R.layout.settings_section_layout, parent, false);
                convertView.setBackgroundColor(0xfffafafa);
            }
            TextView textView = (TextView)convertView.findViewById(R.id.settings_section_text);
            textView.setText(sortedCountries.get(section).toUpperCase());
            return convertView;
        }
    }
}
