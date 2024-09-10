/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ReplacementSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.DividerCell;
import org.telegram.ui.Cells.LetterSectionCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class CountrySelectActivity extends BaseFragment {

    public interface CountrySelectActivityDelegate {
        void didSelectCountry(Country country);
    }

    private RecyclerListView listView;
    private EmptyTextProgressView emptyView;
    private CountryAdapter listViewAdapter;
    private CountrySearchAdapter searchListViewAdapter;

    private boolean searchWas;
    private boolean searching;
    private boolean needPhoneCode;

    private boolean disableAnonymousNumbers;

    private CountrySelectActivityDelegate delegate;
    private ArrayList<Country> existingCountries;

    public CountrySelectActivity(boolean phoneCode) {
        this(phoneCode, null);
    }
    public CountrySelectActivity(boolean phoneCode, ArrayList<Country> existingCountries) {
        super();
        if (existingCountries != null && !existingCountries.isEmpty()) {
            this.existingCountries = new ArrayList<>(existingCountries);
        }
        needPhoneCode = phoneCode;
    }

    public void setDisableAnonymousNumbers(boolean disableAnonymousNumbers) {
        this.disableAnonymousNumbers = disableAnonymousNumbers;
    }

    @Override
    public boolean onFragmentCreate() {
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
    }

    @Override
    public boolean isLightStatusBar() {
        int color = Theme.getColor(Theme.key_windowBackgroundWhite, null, true);
        return ColorUtils.calculateLuminance(color) > 0.7f;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setTitle(LocaleController.getString(R.string.ChooseCountry));

        actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarWhiteSelector), false);
        actionBar.setTitleColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem item = menu.addItem(0, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
                searching = true;
            }

            @Override
            public void onSearchCollapse() {
                searchListViewAdapter.search(null);
                searching = false;
                searchWas = false;
                listView.setAdapter(listViewAdapter);
                listView.setFastScrollVisible(true);
            }

            @Override
            public void onTextChanged(EditText editText) {
                String text = editText.getText().toString();
                if (TextUtils.isEmpty(text)) {
                    searchListViewAdapter.search(null);
                    searchWas = false;
                    listView.setAdapter(listViewAdapter);
                    listView.setFastScrollVisible(true);
                    return;
                }

                searchListViewAdapter.search(text);
                if (text.length() != 0) {
                    searchWas = true;
                }
            }
        });
        item.setSearchFieldHint(LocaleController.getString(R.string.Search));

        actionBar.setSearchTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), true);
        actionBar.setSearchTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setSearchCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));

        searching = false;
        searchWas = false;

        listViewAdapter = new CountryAdapter(context, existingCountries, disableAnonymousNumbers);
        searchListViewAdapter = new CountrySearchAdapter(context, listViewAdapter.getCountries());

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        emptyView = new EmptyTextProgressView(context);
        emptyView.showTextView();
        emptyView.setShowAtCenter(true);
        emptyView.setText(LocaleController.getString(R.string.NoResult));
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView = new RecyclerListView(context);
        listView.setSectionsType(RecyclerListView.SECTIONS_TYPE_FAST_SCROLL_ONLY);
        listView.setEmptyView(emptyView);
        listView.setVerticalScrollBarEnabled(false);
        listView.setFastScrollEnabled(RecyclerListView.FastScroll.LETTER_TYPE);
        listView.setFastScrollVisible(true);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(listViewAdapter);
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener((view, position) -> {
            Country country;
            if (searching && searchWas) {
                country = searchListViewAdapter.getItem(position);
            } else {
                int section = listViewAdapter.getSectionForPosition(position);
                int row = listViewAdapter.getPositionInSectionForPosition(position);
                if (row < 0 || section < 0) {
                    return;
                }
                country = listViewAdapter.getItem(section, row);
            }
            if (position < 0) {
                return;
            }
            finishFragment();
            if (country != null && delegate != null) {
                delegate.didSelectCountry(country);
            }
        });

        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                }
            }
        });

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

    public static class Country {
        public String name;
        public String defaultName;
        public String code;
        public String shortname;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Country that = (Country) o;
            return Objects.equals(name, that.name) && Objects.equals(code, that.code);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, code);
        }
    }

    public class CountryAdapter extends RecyclerListView.SectionsAdapter {
        private final static int TYPE_COUNTRY = 0, TYPE_DIVIDER = 1;

        private Context mContext;
        private HashMap<String, ArrayList<Country>> countries = new HashMap<>();
        private ArrayList<String> sortedCountries = new ArrayList<>();

        public CountryAdapter(Context context, ArrayList<Country> exisitingCountries, boolean disableAnonymousNumbers) {
            mContext = context;

            if (exisitingCountries != null) {
                for (int i = 0; i < exisitingCountries.size(); i++) {
                    Country c = exisitingCountries.get(i);
                    String n = c.name.substring(0, 1).toUpperCase();
                    ArrayList<Country> arr = countries.get(n);
                    if (arr == null) {
                        arr = new ArrayList<>();
                        countries.put(n, arr);
                        sortedCountries.add(n);
                    }
                    arr.add(c);
                }
            } else {
                try {
                    InputStream stream = ApplicationLoader.applicationContext.getResources().getAssets().open("countries.txt");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] args = line.split(";");
                        Country c = new Country();
                        c.name = args[2];
                        c.code = args[0];
                        c.shortname = args[1];
                        if (c.shortname.equals("FT") && disableAnonymousNumbers) {
                            continue;
                        }
                        String n = c.name.substring(0, 1).toUpperCase();
                        ArrayList<Country> arr = countries.get(n);
                        if (arr == null) {
                            arr = new ArrayList<>();
                            countries.put(n, arr);
                            sortedCountries.add(n);
                        }
                        arr.add(c);
                    }
                    reader.close();
                    stream.close();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            Comparator<String> comparator;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Collator collator = Collator.getInstance(LocaleController.getInstance().getCurrentLocale() != null ? LocaleController.getInstance().getCurrentLocale() : Locale.getDefault());
                comparator = collator::compare;
            } else {
                comparator = String::compareTo;
            }
            Collections.sort(sortedCountries, comparator);

            for (ArrayList<Country> arr : countries.values()) {
                Collections.sort(arr, (country, country2) -> comparator.compare(country.name, country2.name));
            }
        }

        public HashMap<String, ArrayList<Country>> getCountries() {
            return countries;
        }

        @Override
        public Country getItem(int section, int position) {
            if (section < 0 || section >= sortedCountries.size()) {
                return null;
            }
            ArrayList<Country> arr = countries.get(sortedCountries.get(section));
            if (position < 0 || position >= arr.size()) {
                return null;
            }
            return arr.get(position);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder, int section, int row) {
            ArrayList<Country> arr = countries.get(sortedCountries.get(section));
            return row < arr.size();
        }

        @Override
        public int getSectionCount() {
            return sortedCountries.size();
        }

        @Override
        public int getCountForSection(int section) {
            int count = countries.get(sortedCountries.get(section)).size();
            if (section != sortedCountries.size() - 1) {
                count++;
            }
            return count;
        }

        @Override
        public View getSectionHeaderView(int section, View view) {
            return null;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case TYPE_COUNTRY:
                    view = createSettingsCell(mContext);
                    break;
                case TYPE_DIVIDER:
                default:
                    view = new DividerCell(mContext);
                    view.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(8));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(int section, int position, RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() == TYPE_COUNTRY) {
                ArrayList<Country> arr = countries.get(sortedCountries.get(section));
                Country c = arr.get(position);
                TextSettingsCell settingsCell = (TextSettingsCell) holder.itemView;
                settingsCell.setTextAndValue(Emoji.replaceEmoji(getCountryNameWithFlag(c), settingsCell.getTextView().getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false), needPhoneCode ? "+" + c.code : null, false);
            }
        }

        @Override
        public int getItemViewType(int section, int position) {
            ArrayList<Country> arr = countries.get(sortedCountries.get(section));
            return position < arr.size() ? TYPE_COUNTRY : TYPE_DIVIDER;
        }

        @Override
        public String getLetter(int position) {
            int section = getSectionForPosition(position);
            if (section == -1) {
                section = sortedCountries.size() - 1;
            }
            return sortedCountries.get(section);
        }

        @Override
        public void getPositionForScrollProgress(RecyclerListView listView, float progress, int[] position) {
            position[0] = (int) (getItemCount() * progress);
            position[1] = 0;
        }
    }

    public class CountrySearchAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;
        private Timer searchTimer;
        private ArrayList<Country> searchResult;
        private List<Country> countryList = new ArrayList<>();
        private Map<Country, List<String>> countrySearchMap = new HashMap<>();

        public CountrySearchAdapter(Context context, HashMap<String, ArrayList<Country>> countries) {
            mContext = context;

            for (List<Country> list : countries.values()) {
                for (Country country : list) {
                    countryList.add(country);
                    List<String> keys = new ArrayList<>(Arrays.asList(country.name.split(" ")));
                    if (country.defaultName != null) {
                        keys.addAll(Arrays.asList(country.defaultName.split(" ")));
                    }
                    countrySearchMap.put(country, keys);
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
                    FileLog.e(e);
                }
                searchTimer = new Timer();
                searchTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            searchTimer.cancel();
                            searchTimer = null;
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        processSearch(query);
                    }
                }, 100, 300);
            }
        }

        private void processSearch(final String query) {
            Utilities.searchQueue.postRunnable(() -> {

                String q = query.trim().toLowerCase();
                if (q.length() == 0) {
                    updateSearchResults(new ArrayList<>());
                    return;
                }
                ArrayList<Country> resultArray = new ArrayList<>();
                for (Country country : countryList) {
                    for (String key : countrySearchMap.get(country)) {
                        if (key.toLowerCase().startsWith(q)) {
                            resultArray.add(country);
                            break;
                        }
                    }
                }
                updateSearchResults(resultArray);
            });
        }

        private void updateSearchResults(final ArrayList<Country> arrCounties) {
            AndroidUtilities.runOnUIThread(() -> {
                if (!searching) {
                    return;
                }
                searchResult = arrCounties;
                if (searchWas && listView != null && listView.getAdapter() != searchListViewAdapter) {
                    listView.setAdapter(searchListViewAdapter);
                    listView.setFastScrollVisible(false);
                }
                notifyDataSetChanged();
            });
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public int getItemCount() {
            if (searchResult == null) {
                return 0;
            }
            return searchResult.size();
        }

        public Country getItem(int i) {
            if (searchResult == null || i < 0 || i >= searchResult.size()) {
                return null;
            }
            return searchResult.get(i);
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(createSettingsCell(mContext));
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Country c = searchResult.get(position);
            TextSettingsCell settingsCell = (TextSettingsCell) holder.itemView;
            settingsCell.setTextAndValue(Emoji.replaceEmoji(getCountryNameWithFlag(c), settingsCell.getTextView().getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false), needPhoneCode ? "+" + c.code : null, false);
        }

        @Override
        public int getItemViewType(int i) {
            return 0;
        }
    }

    private static TextSettingsCell createSettingsCell(Context context) {
        TextSettingsCell view = new TextSettingsCell(context);
        view.setPadding(AndroidUtilities.dp(LocaleController.isRTL ? 16 : 12), 0, AndroidUtilities.dp(LocaleController.isRTL ? 12 : 16), 0);
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            private NotificationCenter.NotificationCenterDelegate listener = (id, account, args) -> {
                if (id == NotificationCenter.emojiLoaded) {
                    view.getTextView().invalidate();
                }
            };

            @Override
            public void onViewAttachedToWindow(View v) {
                NotificationCenter.getGlobalInstance().addObserver(listener, NotificationCenter.emojiLoaded);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                NotificationCenter.getGlobalInstance().removeObserver(listener, NotificationCenter.emojiLoaded);
            }
        });
        return view;
    }

    private static CharSequence getCountryNameWithFlag(Country c) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        String flag = LocaleController.getLanguageFlag(c.shortname);
        if (flag != null) {
            sb.append(flag).append(" ");
            sb.setSpan(new ReplacementSpan() {
                @Override
                public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
                    return AndroidUtilities.dp(16);
                }

                @Override
                public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {}
            }, flag.length(), flag.length() + 1, 0);
        }
        sb.append(c.name);
        return sb;
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCH, null, null, null, null, Theme.key_actionBarDefaultSearch));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCHPLACEHOLDER, null, null, null, null, Theme.key_actionBarDefaultSearchPlaceholder));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_FASTSCROLL, null, null, null, null, Theme.key_fastScrollActive));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_FASTSCROLL, null, null, null, null, Theme.key_fastScrollInactive));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_FASTSCROLL, null, null, null, null, Theme.key_fastScrollText));

        themeDescriptions.add(new ThemeDescription(emptyView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_emptyListPlaceholder));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SECTIONS, new Class[]{LetterSectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        return themeDescriptions;
    }
}
