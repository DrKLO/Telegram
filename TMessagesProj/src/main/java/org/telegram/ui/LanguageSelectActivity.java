/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.internal.view.SupportMenuItem;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.SearchView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Views.BaseFragment;
import org.telegram.ui.Views.OnSwipeTouchListener;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class LanguageSelectActivity extends BaseFragment {
    private SupportMenuItem searchItem;
    private SearchView searchView;
    private BaseAdapter listAdapter;
    private ListView listView;
    private boolean searchWas;
    private boolean searching;
    private BaseAdapter searchListViewAdapter;
    private TextView emptyTextView;

    private Timer searchTimer;
    public ArrayList<LocaleController.LocaleInfo> searchResult;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (fragmentView == null) {
            fragmentView = inflater.inflate(R.layout.language_select_layout, container, false);
            listAdapter = new ListAdapter(parentActivity);
            listView = (ListView)fragmentView.findViewById(R.id.listView);
            listView.setAdapter(listAdapter);
            emptyTextView = (TextView)fragmentView.findViewById(R.id.searchEmptyView);
            listView.setEmptyView(emptyTextView);
            searchListViewAdapter = new SearchAdapter(parentActivity);

            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (parentActivity == null) {
                        return;
                    }
                    LocaleController.LocaleInfo localeInfo = null;
                    if (searching && searchWas) {
                        if (i >= 0 && i < searchResult.size()) {
                            localeInfo = searchResult.get(i);
                        }
                    } else {
                        if (i >= 0 && i < LocaleController.getInstance().sortedLanguages.size()) {
                            localeInfo = LocaleController.getInstance().sortedLanguages.get(i);
                        }
                    }
                    if (localeInfo != null) {
                        boolean isRTL = LocaleController.isRTL;
                        LocaleController.getInstance().applyLanguage(localeInfo, true);
                        if (isRTL != LocaleController.isRTL) {
                            for (BaseFragment fragment : ApplicationLoader.fragmentsStack) {
                                if (fragment == LanguageSelectActivity.this) {
                                    continue;
                                }
                                if (fragment.fragmentView != null) {
                                    ViewGroup parent = (ViewGroup)fragment.fragmentView.getParent();
                                    if (parent != null) {
                                        parent.removeView(fragment.fragmentView);
                                    }
                                    fragment.fragmentView = null;
                                }
                                fragment.parentActivity = parentActivity;
                            }
                        }
                    }
                    if (searchItem != null && searchItem.isActionViewExpanded()) {
                        searchItem.collapseActionView();
                    }
                    finishFragment();
                }
            });

            listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
                    if (parentActivity == null) {
                        return false;
                    }
                    LocaleController.LocaleInfo localeInfo = null;
                    if (searching && searchWas) {
                        if (i >= 0 && i < searchResult.size()) {
                            localeInfo = searchResult.get(i);
                        }
                    } else {
                        if (i >= 0 && i < LocaleController.getInstance().sortedLanguages.size()) {
                            localeInfo = LocaleController.getInstance().sortedLanguages.get(i);
                        }
                    }
                    if (localeInfo == null || localeInfo.pathToFile == null) {
                        return false;
                    }
                    final LocaleController.LocaleInfo finalLocaleInfo = localeInfo;
                    AlertDialog.Builder builder = new AlertDialog.Builder(parentActivity);
                    builder.setMessage(LocaleController.getString("DeleteLocalization", R.string.DeleteLocalization));
                    builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                    builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            if (LocaleController.getInstance().deleteLanguage(finalLocaleInfo)) {
                                if (searchResult != null) {
                                    searchResult.remove(finalLocaleInfo);
                                }
                                if (listAdapter != null) {
                                    listAdapter.notifyDataSetChanged();
                                }
                                if (searchListViewAdapter != null) {
                                    searchListViewAdapter.notifyDataSetChanged();
                                }
                                applySelfActionBar();
                                if (searchItem != null && searchItem.isActionViewExpanded()) {
                                    searchItem.collapseActionView();
                                }
                            }
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    builder.show().setCanceledOnTouchOutside(true);
                    return true;
                }
            });

            listView.setOnTouchListener(new OnSwipeTouchListener() {
                public void onSwipeRight() {
                    finishFragment(true);
                }
            });

            searching = false;
            searchWas = false;
        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
        return fragmentView;
    }

    @Override
    public void applySelfActionBar() {
        if (parentActivity == null) {
            return;
        }
        ActionBar actionBar = parentActivity.getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayUseLogoEnabled(false);
        actionBar.setDisplayShowCustomEnabled(false);
        actionBar.setCustomView(null);
        actionBar.setSubtitle(null);

        TextView title = (TextView)parentActivity.findViewById(R.id.action_bar_title);
        if (title == null) {
            final int subtitleId = parentActivity.getResources().getIdentifier("action_bar_title", "id", "android");
            title = (TextView)parentActivity.findViewById(subtitleId);
        }
        if (title != null) {
            title.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            title.setCompoundDrawablePadding(0);
        }
        actionBar.setTitle(LocaleController.getString("Language", R.string.Language));
        ((LaunchActivity)parentActivity).fixBackButton();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isFinish) {
            return;
        }
        if (getActivity() == null) {
            return;
        }
        if (!firstStart && listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        firstStart = false;
        ((LaunchActivity)parentActivity).showActionBar();
        ((LaunchActivity)parentActivity).updateActionBar();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (searchItem != null && searchItem.isActionViewExpanded()) {
            searchItem.collapseActionView();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            case android.R.id.home:
                if (searchItem != null && searchItem.isActionViewExpanded()) {
                    searchItem.collapseActionView();
                }
                finishFragment();
                break;
        }
        return true;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        searchItem = (SupportMenuItem)menu.add(Menu.NONE, 0, Menu.NONE, LocaleController.getString("Search", R.string.Search)).setIcon(R.drawable.ic_ab_search);
        searchItem.setShowAsAction(SupportMenuItem.SHOW_AS_ACTION_ALWAYS|SupportMenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW);
        searchItem.setActionView(searchView = new SearchView(parentActivity));

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
                if (parentActivity != null) {
                    parentActivity.getSupportActionBar().setIcon(R.drawable.ic_ab_search);
                }
                searching = true;
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                searchView.setQuery("", false);
                search(null);
                searching = false;
                searchWas = false;
                if (listView != null) {
                    emptyTextView.setVisibility(View.GONE);
                    listView.setAdapter(listAdapter);
                }
                ((LaunchActivity)parentActivity).fixBackButton();
                return true;
            }
        });

        super.onCreateOptionsMenu(menu, inflater);
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
                    updateSearchResults(new ArrayList<LocaleController.LocaleInfo>());
                    return;
                }
                long time = System.currentTimeMillis();
                ArrayList<LocaleController.LocaleInfo> resultArray = new ArrayList<LocaleController.LocaleInfo>();

                for (LocaleController.LocaleInfo c : LocaleController.getInstance().sortedLanguages) {
                    if (c.name.toLowerCase().startsWith(query) || c.nameEnglish.toLowerCase().startsWith(query)) {
                        resultArray.add(c);
                    }
                }

                updateSearchResults(resultArray);
            }
        });
    }

    private void updateSearchResults(final ArrayList<LocaleController.LocaleInfo> arrCounties) {
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
                view = li.inflate(R.layout.settings_row_button_layout, viewGroup, false);
            }
            TextView textView = (TextView)view.findViewById(R.id.settings_row_text);
            View divider = view.findViewById(R.id.settings_row_divider);

            LocaleController.LocaleInfo c = searchResult.get(i);
            textView.setText(c.name);
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

    private class ListAdapter extends BaseAdapter {
        private Context mContext;

        public ListAdapter(Context context) {
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
            if (LocaleController.getInstance().sortedLanguages == null) {
                return 0;
            }
            return LocaleController.getInstance().sortedLanguages.size();
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
                view = li.inflate(R.layout.settings_row_button_layout, viewGroup, false);
            }
            TextView textView = (TextView)view.findViewById(R.id.settings_row_text);
            View divider = view.findViewById(R.id.settings_row_divider);

            LocaleController.LocaleInfo localeInfo = LocaleController.getInstance().sortedLanguages.get(i);
            textView.setText(localeInfo.name);
            if (i == LocaleController.getInstance().sortedLanguages.size() - 1) {
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
            return LocaleController.getInstance().sortedLanguages == null || LocaleController.getInstance().sortedLanguages.size() == 0;
        }
    }
}
