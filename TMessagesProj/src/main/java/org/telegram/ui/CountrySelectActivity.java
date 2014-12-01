/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Adapters.CountryAdapter;
import org.telegram.ui.Adapters.CountryAdapter.Country;
import org.telegram.ui.Adapters.CountrySearchAdapter;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.SectionsListView;

public class CountrySelectActivity extends BaseFragment {

    public static interface CountrySelectActivityDelegate {
        public abstract void didSelectCountry(String name);
    }

    private SectionsListView listView;
    private TextView emptyTextView;
    private CountryAdapter listViewAdapter;
    private CountrySearchAdapter searchListViewAdapter;

    private boolean searchWas;
    private boolean searching;

    private CountrySelectActivityDelegate delegate;

    @Override
    public boolean onFragmentCreate() {
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
    }

    @Override
    public View createView(LayoutInflater inflater, final ViewGroup container) {
        if (fragmentView == null) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setAllowOverlayTitle(true);
            actionBar.setTitle(LocaleController.getString("ChooseCountry", R.string.ChooseCountry));

            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    }
                }
            });

            ActionBarMenu menu = actionBar.createMenu();
            menu.addItem(0, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                @Override
                public void onSearchExpand() {
                    searching = true;
                }

                @Override
                public void onSearchCollapse() {
                    searchListViewAdapter.search(null);
                    searching = false;
                    searchWas = false;
                    ViewGroup group = (ViewGroup) listView.getParent();
                    listView.setAdapter(listViewAdapter);
                    if (android.os.Build.VERSION.SDK_INT >= 11) {
                        listView.setFastScrollAlwaysVisible(true);
                    }
                    listView.setFastScrollEnabled(true);
                    listView.setVerticalScrollBarEnabled(false);

                    emptyTextView.setText(LocaleController.getString("ChooseCountry", R.string.ChooseCountry));
                }

                @Override
                public void onTextChanged(EditText editText) {
                    String text = editText.getText().toString();
                    searchListViewAdapter.search(text);
                    if (text.length() != 0) {
                        searchWas = true;
                        if (listView != null) {
                            listView.setAdapter(searchListViewAdapter);
                            if(android.os.Build.VERSION.SDK_INT >= 11) {
                                listView.setFastScrollAlwaysVisible(false);
                            }
                            listView.setFastScrollEnabled(false);
                            listView.setVerticalScrollBarEnabled(true);
                        }
                        if (emptyTextView != null) {

                        }
                    }
                }
            });

            searching = false;
            searchWas = false;

            listViewAdapter = new CountryAdapter(getParentActivity());
            searchListViewAdapter = new CountrySearchAdapter(getParentActivity(), listViewAdapter.getCountries());

            fragmentView = new FrameLayout(getParentActivity());

            LinearLayout emptyTextLayout = new LinearLayout(getParentActivity());
            emptyTextLayout.setVisibility(View.INVISIBLE);
            emptyTextLayout.setOrientation(LinearLayout.VERTICAL);
            ((FrameLayout) fragmentView).addView(emptyTextLayout);
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) emptyTextLayout.getLayoutParams();
            layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams.gravity = Gravity.TOP;
            emptyTextLayout.setLayoutParams(layoutParams);
            emptyTextLayout.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });

            emptyTextView = new TextView(getParentActivity());
            emptyTextView.setTextColor(0xff808080);
            emptyTextView.setTextSize(20);
            emptyTextView.setGravity(Gravity.CENTER);
            emptyTextView.setText(LocaleController.getString("NoResult", R.string.NoResult));
            emptyTextLayout.addView(emptyTextView);
            LinearLayout.LayoutParams layoutParams1 = (LinearLayout.LayoutParams) emptyTextView.getLayoutParams();
            layoutParams1.width = LinearLayout.LayoutParams.MATCH_PARENT;
            layoutParams1.height = LinearLayout.LayoutParams.MATCH_PARENT;
            layoutParams1.weight = 0.5f;
            emptyTextView.setLayoutParams(layoutParams1);

            FrameLayout frameLayout = new FrameLayout(getParentActivity());
            emptyTextLayout.addView(frameLayout);
            layoutParams1 = (LinearLayout.LayoutParams) frameLayout.getLayoutParams();
            layoutParams1.width = LinearLayout.LayoutParams.MATCH_PARENT;
            layoutParams1.height = LinearLayout.LayoutParams.MATCH_PARENT;
            layoutParams1.weight = 0.5f;
            frameLayout.setLayoutParams(layoutParams1);

            listView = new SectionsListView(getParentActivity());
            listView.setEmptyView(emptyTextLayout);
            listView.setVerticalScrollBarEnabled(false);
            listView.setDivider(null);
            listView.setDividerHeight(0);
            listView.setFastScrollEnabled(true);
            listView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
            listView.setAdapter(listViewAdapter);
            if (Build.VERSION.SDK_INT >= 11) {
                listView.setFastScrollAlwaysVisible(true);
                listView.setVerticalScrollbarPosition(LocaleController.isRTL ? ListView.SCROLLBAR_POSITION_LEFT : ListView.SCROLLBAR_POSITION_RIGHT);
            }
            ((FrameLayout) fragmentView).addView(listView);
            layoutParams = (FrameLayout.LayoutParams) listView.getLayoutParams();
            layoutParams.width = FrameLayout.LayoutParams.MATCH_PARENT;
            layoutParams.height = FrameLayout.LayoutParams.MATCH_PARENT;
            listView.setLayoutParams(layoutParams);

            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    Country country = null;
                    if (searching && searchWas) {
                        country = searchListViewAdapter.getItem(i);
                    } else {
                        int section = listViewAdapter.getSectionForPosition(i);
                        int row = listViewAdapter.getPositionInSectionForPosition(i);
                        if (row < 0 || section < 0) {
                            return;
                        }
                        country = listViewAdapter.getItem(section, row);
                    }
                    if (i < 0) {
                        return;
                    }
                    if (country != null && delegate != null) {
                        delegate.didSelectCountry(country.name);
                    }
                    finishFragment();
                }
            });

            listView.setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView absListView, int i) {
                    if (i == SCROLL_STATE_TOUCH_SCROLL && searching && searchWas) {
                        AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                    }
                }

                @Override
                public void onScroll(AbsListView absListView, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    if (absListView.isFastScrollEnabled()) {
                        AndroidUtilities.clearDrawableAnimation(absListView);
                    }
                }
            });
        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
    }

    public void setCountrySelectActivityDelegate(CountrySelectActivityDelegate delegate) {
        this.delegate = delegate;
    }
}
