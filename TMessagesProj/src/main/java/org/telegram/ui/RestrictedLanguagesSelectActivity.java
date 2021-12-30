/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.LanguageCell;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckbox2Cell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextRadioCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class RestrictedLanguagesSelectActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private ListAdapter searchListViewAdapter;
    private EmptyTextProgressView emptyView;

    private boolean searchWas;
    private boolean searching;

    private Timer searchTimer;
    private ArrayList<LocaleController.LocaleInfo> searchResult;
    private ArrayList<LocaleController.LocaleInfo> sortedLanguages;
//    private ArrayList<LocaleController.LocaleInfo> unofficialLanguages;

    private SharedPreferences preferences;
    private SharedPreferences.OnSharedPreferenceChangeListener listener;
    private HashSet<String> selectedLanguages = null;

    public static HashSet<String> getRestrictedLanguages() {
//        String currentLangCode = LocaleController.getInstance().getCurrentLocaleInfo().pluralLangCode;
//        String[] onlyCurrentLang = new String[] { currentLangCode };
        return new HashSet<>(MessagesController.getGlobalMainSettings().getStringSet("translate_button_restricted_languages", new HashSet<String>(/*Arrays.asList(onlyCurrentLang)*/)));
    }

    @Override
    public boolean onFragmentCreate() {
        preferences = MessagesController.getGlobalMainSettings();
        selectedLanguages = getRestrictedLanguages();
        preferences.registerOnSharedPreferenceChangeListener(listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            public int langPos(String lng) {
                if (lng == null)
                    return -1;
                ArrayList<LocaleController.LocaleInfo> arr = (searching ? searchResult : sortedLanguages);
                if (arr == null)
                    return -1;
                for (int i = 0; i < arr.size(); ++i)
                    if (lng.equals(arr.get(i).pluralLangCode))
                        return i;
                return -1;
            }

            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
                preferences = sharedPreferences;
                HashSet<String> newSelectedLanguages = getRestrictedLanguages();
                if (listView != null && listView.getAdapter() != null) {
                    RecyclerView.Adapter adapter = listView.getAdapter();
                    int offset = !searching ? 1 : 0;
                    for (String lng : selectedLanguages)
                        if (!newSelectedLanguages.contains(lng))
                            adapter.notifyItemChanged(langPos(lng) + offset);
                    for (String lng : newSelectedLanguages)
                        if (!selectedLanguages.contains(lng))
                            adapter.notifyItemChanged(langPos(lng) + offset);
                }
                selectedLanguages = newSelectedLanguages;
            }
        });

        fillLanguages();
        LocaleController.getInstance().loadRemoteLanguages(currentAccount);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.suggestedLangpack);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        preferences.unregisterOnSharedPreferenceChangeListener(listener);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.suggestedLangpack);
    }

    @Override
    public View createView(Context context) {
        searching = false;
        searchWas = false;

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("DoNotTranslate", R.string.DoNotTranslate));

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
                search(null);
                searching = false;
                searchWas = false;
                if (listView != null) {
                    emptyView.setVisibility(View.GONE);
                    listView.setAdapter(listAdapter);
                }
            }

            @Override
            public void onTextChanged(EditText editText) {
                String text = editText.getText().toString();
                search(text);
                if (text.length() != 0) {
                    searchWas = true;
                    if (listView != null) {
                        listView.setAdapter(searchListViewAdapter);
                    }
                } else {
                    searching = false;
                    searchWas = false;
                    if (listView != null) {
                        emptyView.setVisibility(View.GONE);
                        listView.setAdapter(listAdapter);
                    }
                }
            }
        });
        item.setSearchFieldHint(LocaleController.getString("Search", R.string.Search));

        listAdapter = new ListAdapter(context, false);
        searchListViewAdapter = new ListAdapter(context, true);

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        emptyView = new EmptyTextProgressView(context);
        emptyView.setText(LocaleController.getString("NoResult", R.string.NoResult));
        emptyView.showTextView();
        emptyView.setShowAtCenter(true);
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView = new RecyclerListView(context);
        listView.setEmptyView(emptyView);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setAdapter(listAdapter);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener((view, position) -> {
            if (getParentActivity() == null || parentLayout == null || !(view instanceof TextCheckbox2Cell)) {
                return;
            }
            boolean search = listView.getAdapter() == searchListViewAdapter;
            if (!search)
                position--;
            LocaleController.LocaleInfo localeInfo;
            if (search) {
                localeInfo = searchResult.get(position);
            } else {
                localeInfo = sortedLanguages.get(position);
            }
            if (localeInfo != null) {
                LocaleController.LocaleInfo currentLocaleInfo = LocaleController.getInstance().getCurrentLocaleInfo();
                String langCode = localeInfo.pluralLangCode;
                if (langCode != null && langCode.equals(currentLocaleInfo.pluralLangCode)) {
                    AndroidUtilities.shakeView(((TextCheckbox2Cell) view).checkbox, 2, 0);
                    return;
                }
                boolean value = selectedLanguages.contains(langCode);
                HashSet<String> newSelectedLanguages = new HashSet<String>(selectedLanguages);
                if (value)
                    newSelectedLanguages.removeIf(s -> s != null && s.equals(langCode));
                else
                    newSelectedLanguages.add(langCode);
                if (newSelectedLanguages.size() == 1 && newSelectedLanguages.contains(currentLocaleInfo.pluralLangCode))
                    preferences.edit().remove("translate_button_restricted_languages").apply();
                else
                    preferences.edit().putStringSet("translate_button_restricted_languages", newSelectedLanguages).apply();
            }
        });

        listView.setOnItemLongClickListener((view, position) -> {
            if (getParentActivity() == null || parentLayout == null || !(view instanceof TextCheckbox2Cell)) {
                return false;
            }
            boolean search = listView.getAdapter() == searchListViewAdapter;
            if (!search)
                position--;
            LocaleController.LocaleInfo localeInfo;
            if (search) {
                localeInfo = searchResult.get(position);
            } else {
                localeInfo = sortedLanguages.get(position);
            }
            if (localeInfo == null || localeInfo.pathToFile == null || localeInfo.isRemote() && localeInfo.serverIndex != Integer.MAX_VALUE) {
                return false;
            }
            final LocaleController.LocaleInfo finalLocaleInfo = localeInfo;
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString("DeleteLocalizationTitle", R.string.DeleteLocalizationTitle));
            builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("DeleteLocalizationText", R.string.DeleteLocalizationText, localeInfo.name)));
            builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialogInterface, i) -> {
                if (LocaleController.getInstance().deleteLanguage(finalLocaleInfo, currentAccount)) {
                    fillLanguages();
                    if (searchResult != null) {
                        searchResult.remove(finalLocaleInfo);
                    }
                    if (listAdapter != null) {
                        listAdapter.notifyDataSetChanged();
                    }
                    if (searchListViewAdapter != null) {
                        searchListViewAdapter.notifyDataSetChanged();
                    }
                }
            });
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            AlertDialog alertDialog = builder.create();
            showDialog(alertDialog);
            TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (button != null) {
                button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
            }
            return true;
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
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.suggestedLangpack) {
            if (listAdapter != null) {
                fillLanguages();
                listAdapter.notifyDataSetChanged();
            }
        }
    }

    private void fillLanguages() {
        final LocaleController.LocaleInfo currentLocale = LocaleController.getInstance().getCurrentLocaleInfo();
        Comparator<LocaleController.LocaleInfo> comparator = (o, o2) -> {
            if (o == currentLocale) {
                return -1;
            } else if (o2 == currentLocale) {
                return 1;
            } else if (o.serverIndex == o2.serverIndex) {
                return o.name.compareTo(o2.name);
            }
            if (o.serverIndex > o2.serverIndex) {
                return 1;
            } else if (o.serverIndex < o2.serverIndex) {
                return -1;
            }
            return 0;
        };

        sortedLanguages = new ArrayList<>();

        ArrayList<LocaleController.LocaleInfo> arrayList = LocaleController.getInstance().languages;
        for (int a = 0, size = arrayList.size(); a < size; a++) {
            LocaleController.LocaleInfo info = arrayList.get(a);
            if (info != null && info.serverIndex != Integer.MAX_VALUE/* && (info.pluralLangCode == null || !info.pluralLangCode.equals(currentLocale.pluralLangCode))*/) {
                sortedLanguages.add(info);
            }
        }
        Collections.sort(sortedLanguages, comparator);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
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
//            searchTimer = new Timer();
//            searchTimer.schedule(new TimerTask() {
//                @Override
//                public void run() {
//                    try {
//                        searchTimer.cancel();
//                        searchTimer = null;
//                    } catch (Exception e) {
//                        FileLog.e(e);
//                    }
                    processSearch(query);
//                }
//            }, 100, 300);
        }
    }

    private void processSearch(final String query) {
//        Utilities.searchQueue.postRunnable(() -> {

            String q = query.trim().toLowerCase();
            if (q.length() == 0) {
                updateSearchResults(new ArrayList<>());
                return;
            }
            long time = System.currentTimeMillis();
            ArrayList<LocaleController.LocaleInfo> resultArray = new ArrayList<>();

//            for (int a = 0, N = unofficialLanguages.size(); a < N; a++) {
//                LocaleController.LocaleInfo c = unofficialLanguages.get(a);
//                if (c.name.toLowerCase().startsWith(query) || c.nameEnglish.toLowerCase().startsWith(query)) {
//                    resultArray.add(c);
//                }
//            }

            for (int a = 0, N = sortedLanguages.size(); a < N; a++) {
                LocaleController.LocaleInfo c = sortedLanguages.get(a);
                if (c.name.toLowerCase().startsWith(query) || c.nameEnglish.toLowerCase().startsWith(query)) {
                    resultArray.add(c);
                }
            }

            updateSearchResults(resultArray);
//        });
    }

    private void updateSearchResults(final ArrayList<LocaleController.LocaleInfo> arrCounties) {
        AndroidUtilities.runOnUIThread(() -> {
            searchResult = arrCounties;
            searchListViewAdapter.notifyDataSetChanged();
        });
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;
        private boolean search;

        public ListAdapter(Context context, boolean isSearch) {
            mContext = context;
            search = isSearch;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 0;
        }

        @Override
        public int getItemCount() {
            if (search) {
                if (searchResult == null) {
                    return 0;
                }
                return searchResult.size();
            } else {
                int count = sortedLanguages.size();
                return 1 + count;
            }
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0: {
                    view = new TextCheckbox2Cell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                }
                case 2:
                    HeaderCell header = new HeaderCell(mContext);
                    header.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    header.setText(LocaleController.getString("ChooseLanguages", R.string.ChooseLanguages));
                    view = header;
                    break;
                case 1:
                default: {
                    view = new ShadowSectionCell(mContext);
                    break;
                }
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    if (!search)
                        position--;
//                    LanguageCell textSettingsCell = (LanguageCell) holder.itemView;
                    TextCheckbox2Cell textSettingsCell = (TextCheckbox2Cell) holder.itemView;
                    LocaleController.LocaleInfo localeInfo;
                    boolean last;
                    if (search) {
                        localeInfo = searchResult.get(position);
                        last = position == searchResult.size() - 1;
                    } /*else if (!unofficialLanguages.isEmpty() && position >= 0 && position < unofficialLanguages.size()) {
                        localeInfo = unofficialLanguages.get(position);
                        last = position == unofficialLanguages.size() - 1;
                    } */else {
//                        if (!unofficialLanguages.isEmpty()) {
//                            position -= unofficialLanguages.size() + 1;
//                        }
                        localeInfo = sortedLanguages.get(position);
                        last = position == sortedLanguages.size() - 1;
                    }
                    String langCode = localeInfo.pluralLangCode;
                    boolean value = selectedLanguages.contains(langCode);
                    if (localeInfo.isLocal()) {
                        textSettingsCell.setTextAndValue(String.format("%1$s (%2$s)", localeInfo.name, LocaleController.getString("LanguageCustom", R.string.LanguageCustom)), localeInfo.nameEnglish, false, !last);
                    } else {
                        textSettingsCell.setTextAndValue(localeInfo.name, localeInfo.nameEnglish, false, !last);
                    }

                    boolean isCurrent = langCode != null && langCode.equals(LocaleController.getInstance().getCurrentLocaleInfo().pluralLangCode);
                    textSettingsCell.setChecked(value || isCurrent);
                    break;
                }
                case 1: {
                    if (!search)
                        position--;
                    ShadowSectionCell sectionCell = (ShadowSectionCell) holder.itemView;
//                    if (!unofficialLanguages.isEmpty() && position == unofficialLanguages.size()) {
//                        sectionCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
//                    } else {
                        sectionCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
//                    }
                    break;
                }
                case 2: {

                }
            }
        }

        @Override
        public int getItemViewType(int i) {
            if (!search)
                i--;
            if (i == -1)
                return 2;
            if (search) {
                return 0;
            }
//            if (!unofficialLanguages.isEmpty() && (i == unofficialLanguages.size() || i == unofficialLanguages.size() + sortedLanguages.size() + 1) || unofficialLanguages.isEmpty() && i == sortedLanguages.size()) {
//                return 1;
//            }
            return 0;
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{LanguageCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCH, null, null, null, null, Theme.key_actionBarDefaultSearch));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCHPLACEHOLDER, null, null, null, null, Theme.key_actionBarDefaultSearchPlaceholder));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(emptyView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_emptyListPlaceholder));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{LanguageCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{LanguageCell.class}, new String[]{"textView2"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{LanguageCell.class}, new String[]{"checkImage"}, null, null, null, Theme.key_featuredStickers_addedIcon));

        return themeDescriptions;
    }
}
