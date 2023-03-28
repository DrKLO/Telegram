/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.TranslateController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.LanguageCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckbox2Cell;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.TranslateAlert2;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Timer;

public class RestrictedLanguagesSelectActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private ListAdapter searchListViewAdapter;
    private EmptyTextProgressView emptyView;

    private boolean searchWas;
    private boolean searching;

    private Timer searchTimer;
    private int separatorRow = -1;
    private ArrayList<TranslateController.Language> searchResult;
    private ArrayList<TranslateController.Language> allLanguages;

    private SharedPreferences preferences;
    private HashSet<String> firstSelectedLanguages;
    private HashSet<String> selectedLanguages;

    public static HashSet<String> getRestrictedLanguages() {
        String currentLangCode = LocaleController.getInstance().getCurrentLocaleInfo().pluralLangCode;
        String[] onlyCurrentLang = new String[] { currentLangCode };
        return new HashSet<>(MessagesController.getGlobalMainSettings().getStringSet("translate_button_restricted_languages", new HashSet<String>(Arrays.asList(onlyCurrentLang))));
    }

    @Override
    public boolean onFragmentCreate() {
        preferences = MessagesController.getGlobalMainSettings();
        firstSelectedLanguages = getRestrictedLanguages();
        selectedLanguages = getRestrictedLanguages();

        fillLanguages();
        LocaleController.getInstance().loadRemoteLanguages(currentAccount);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.suggestedLangpack);
        return super.onFragmentCreate();
    }

    private void rebind(int position) {
        RecyclerView.Adapter adapter = listView.getAdapter();
        for (int i = 0; i < listView.getChildCount(); ++i) {
            View child = listView.getChildAt(i);
            RecyclerView.ViewHolder holder = listView.getChildViewHolder(child);
            if (holder == null) {
                continue;
            }
            int childPosition = holder.getAdapterPosition();
            if (childPosition == RecyclerView.NO_POSITION) {
                continue;
            }
            if (childPosition == position) {
                adapter.onBindViewHolder(holder, position);
                return;
            }
        }
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.suggestedLangpack);
    }

    public static boolean toggleLanguage(String language, boolean doNotTranslate) {
        if (language == null) {
            return false;
        }
        language = language.toLowerCase();
        LocaleController.LocaleInfo currentLocaleInfo = LocaleController.getInstance().getCurrentLocaleInfo();
        HashSet<String> selectedLanguages = getRestrictedLanguages();
//        if (language != null && language.equals(currentLocaleInfo.pluralLangCode) && doNotTranslate) {
////            AndroidUtilities.shakeViewSpring(view);
////            BotWebViewVibrationEffect.APP_ERROR.vibrate();
//            return false;
//        }
        if (!doNotTranslate) {
            selectedLanguages.remove(language);
        } else {
            selectedLanguages.add(language);
        }
        if (selectedLanguages.size() == 1 && selectedLanguages.contains(currentLocaleInfo.pluralLangCode)) {
            MessagesController.getGlobalMainSettings().edit().remove("translate_button_restricted_languages").commit();
        } else {
            MessagesController.getGlobalMainSettings().edit().putStringSet("translate_button_restricted_languages", selectedLanguages).commit();
        }
        TranslateController.invalidateSuggestedLanguageCodes();
        return true;
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
            final int realPosition = position;
            TranslateController.Language language = null;
            if (search && searchResult != null) {
                language = searchResult.get(position);
            } else {
                if (separatorRow >= 0 && position > separatorRow) {
                    position--;
                }
                if (position >= 0 && position < allLanguages.size()) {
                    language = allLanguages.get(position);
                }
            }
            if (language != null && language.code != null) {
                LocaleController.LocaleInfo currentLocaleInfo = LocaleController.getInstance().getCurrentLocaleInfo();
                String langCode = language.code;
                boolean value = selectedLanguages.contains(langCode);
//                if (langCode != null && langCode.equals(currentLocaleInfo.pluralLangCode) && value) {
//                    AndroidUtilities.shakeViewSpring(view);
//                    BotWebViewVibrationEffect.APP_ERROR.vibrate();
//                    return;
//                }
                if (value) {
                    selectedLanguages.removeIf(s -> s != null && s.equals(langCode));
                } else {
                    selectedLanguages.add(langCode);
                }
                if (selectedLanguages.size() == 1 && selectedLanguages.contains(currentLocaleInfo.pluralLangCode)) {
                    preferences.edit().remove("translate_button_restricted_languages").remove("translate_button_restricted_languages_changed").apply();
                } else {
                    preferences.edit().putStringSet("translate_button_restricted_languages", selectedLanguages).putBoolean("translate_button_restricted_languages_changed", true).apply();
                }

                if (search) {
                    for (int i = 0, p = 0; i < searchResult.size(); ++i, ++p) {
                        if (TextUtils.equals(langCode, searchResult.get(i).code)) {
                            rebind(p);
                        }
                    }
                } else {
                    for (int i = 0, p = 0; i < allLanguages.size(); ++i, ++p) {
                        if (p == separatorRow) {
                            p++;
                        }
                        if (TextUtils.equals(langCode, allLanguages.get(i).code)) {
                            rebind(p);
                        }
                    }
                }

                MessagesController.getInstance(currentAccount).getTranslateController().checkRestrictedLanguagesUpdate();
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
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.suggestedLangpack) {
            if (listAdapter != null) {
                fillLanguages();
                listAdapter.notifyDataSetChanged();
            }
        }
    }

    private void fillLanguages() {
        allLanguages = TranslateController.getLanguages();

        final String currentLanguageCode = LocaleController.getInstance().getCurrentLocaleInfo().pluralLangCode;
        TranslateController.Language currentLanguage = null;
        ArrayList<TranslateController.Language> selectedLanguages = new ArrayList<>();
        ArrayList<String> notAddedSelectedLanguages = new ArrayList<>(firstSelectedLanguages);
        for (int i = 0; i < allLanguages.size(); ++i) {
            TranslateController.Language l = allLanguages.get(i);
            if (TextUtils.equals(l.code, currentLanguageCode)) {
                currentLanguage = l;
                notAddedSelectedLanguages.remove(l.code);
                allLanguages.remove(i);
                i--;
            } else if (firstSelectedLanguages.contains(l.code)) {
                selectedLanguages.add(l);
                notAddedSelectedLanguages.remove(l.code);
                allLanguages.remove(i);
                i--;
            }
        }

        for (int i = 0; i < notAddedSelectedLanguages.size(); ++i) {
            TranslateController.Language lang = new TranslateController.Language();
            lang.code = notAddedSelectedLanguages.get(i);
            lang.ownDisplayName = lang.displayName = lang.code.toUpperCase();
            lang.q = lang.code.toLowerCase();
            selectedLanguages.add(lang);
        }

        separatorRow = 0;
        allLanguages.addAll(0, selectedLanguages);
        separatorRow += selectedLanguages.size();
        if (currentLanguage != null) {
            allLanguages.add(0, currentLanguage);
            separatorRow++;
        }
        if (separatorRow <= 0) {
            separatorRow = -1;
        }
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
            processSearch(query);
        }
    }

    private void processSearch(final String query) {
        String q = query.trim().toLowerCase();

        if (searchResult == null) {
            searchResult = new ArrayList<>();
        } else {
            searchResult.clear();
        }
        for (int i = 0; i < allLanguages.size(); ++i) {
            TranslateController.Language l = allLanguages.get(i);
            if (l.q.startsWith(q)) {
                searchResult.add(0, l);
            } else if (l.q.contains(q)) {
                searchResult.add(l);
            }
        }

        searchListViewAdapter.notifyDataSetChanged();
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
                return (separatorRow >= 0 ? 1 : 0) + allLanguages.size();
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
                    TextCheckbox2Cell textSettingsCell = (TextCheckbox2Cell) holder.itemView;
                    TranslateController.Language language = null;
                    boolean last = false;
                    if (search) {
                        if (position >= 0 && position < searchResult.size()) {
                            language = searchResult.get(position);
                        }
                        last = position == searchResult.size() - 1;
                    } else {
                        if (separatorRow >= 0 && position > separatorRow) {
                            position--;
                        }
                        if (position >= 0 && position < allLanguages.size()) {
                            language = allLanguages.get(position);
                            last = position == allLanguages.size() - 1;
                        }
                    }
                    if (language == null) {
                        return;
                    }
                    String ownDisplayName = language.ownDisplayName == null ? language.displayName : language.ownDisplayName;
                    textSettingsCell.setTextAndValue(ownDisplayName, language.displayName, false, !last);
                    textSettingsCell.setChecked(selectedLanguages.contains(language.code));
                    break;
                }
                case 1: {
                    ShadowSectionCell sectionCell = (ShadowSectionCell) holder.itemView;
                    sectionCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    break;
                }
                case 2: {

                }
            }
        }

        @Override
        public int getItemViewType(int i) {
            if (search) {
                return 0;
            } else if (i == separatorRow) {
                return 1;
            } else {
                return 0;
            }
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

    public static void cleanup() {
        MessagesController.getGlobalMainSettings().edit()
            .remove("translate_button_restricted_languages_changed")
            .remove("translate_button_restricted_languages_version")
            .remove("translate_button_restricted_languages")
            .apply();
        checkRestrictedLanguages(false);
    }

    public static final int LAST_DO_NOT_TRANSLATE_VERSION = 2;
    public static void checkRestrictedLanguages(boolean accountsChanged) {
        boolean manualChanged = MessagesController.getGlobalMainSettings().getBoolean("translate_button_restricted_languages_changed", false);
        int version = MessagesController.getGlobalMainSettings().getInt("translate_button_restricted_languages_version", 0);

        if (version != LAST_DO_NOT_TRANSLATE_VERSION || accountsChanged && !manualChanged) {
            getExtendedDoNotTranslate(languages -> {
                final String currentLangCode = LocaleController.getInstance().getCurrentLocaleInfo().pluralLangCode;

                languages.addAll(getRestrictedLanguages());
                SharedPreferences.Editor edit = MessagesController.getGlobalMainSettings().edit();
                if (languages.size() == 1 && TextUtils.equals(languages.iterator().next(), currentLangCode)) {
                    edit.remove("translate_button_restricted_languages");
                } else {
                    edit.putStringSet("translate_button_restricted_languages", languages);
                }
                edit.putInt("translate_button_restricted_languages_version", LAST_DO_NOT_TRANSLATE_VERSION).apply();

                for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; ++i) {
                    final int account = i;
                    try {
                        MessagesController.getInstance(account).getTranslateController().checkRestrictedLanguagesUpdate();
                    } catch (Exception ignore) {}
                }
            });
        }
    }

    public static void getExtendedDoNotTranslate(Utilities.Callback<HashSet<String>> onDone) {
        if (onDone == null) {
            return;
        }

        final HashSet<String> result = new HashSet<>();

        final HashMap<String, String> countries = new HashMap<>();
//        final HashMap<String, String[]> languages = new HashMap<>();
        final HashMap<String, String> uniquePhoneCodes = new HashMap<>();

//        final Utilities.Callback<String> pushCountry = countryCode -> {
//            if (countryCode == null) {
//                return;
//            }
//            String[] countryLanguages = languages.get(countryCode.toUpperCase());
//            if (countryLanguages == null) {
//                return;
//            }
//            for (int j = 1; j < Math.min(2, countryLanguages.length); ++j) {
//                String language = countryLanguages[j];
//                if (language.contains("-")) {
//                    language = language.split("-")[0];
//                }
//                if (TranslateAlert2.languageName(language) != null) {
//                    result.add(language);
//                }
//            }
//        };

        Utilities.doCallbacks(
            next -> {
                try {
                    String language = LocaleController.getInstance().getCurrentLocaleInfo().pluralLangCode;
                    if (TranslateAlert2.languageName(language) != null) {
                        result.add(language);
                    }
                } catch (Exception e0) {
                    FileLog.e(e0);
                }
                next.run();
            },
            next -> {
                try {
                    String language = Resources.getSystem().getConfiguration().locale.getLanguage();
                    if (TranslateAlert2.languageName(language) != null) {
                        result.add(language);
                    }
                } catch (Exception e1) {
                    FileLog.e(e1);
                }
                next.run();
            },
            next -> {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(ApplicationLoader.applicationContext.getResources().getAssets().open("countries.txt")));
                    ArrayList<String> multipleCodes = new ArrayList<>();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] args = line.split(";");
                        if (args.length >= 3) {
                            countries.put(args[2], args[1]);
                            if (uniquePhoneCodes.containsKey(args[0]) && !"7".equals(args[0])) {
                                multipleCodes.add(args[0]);
                                uniquePhoneCodes.remove(args[0]);
                            } else if (!multipleCodes.contains(args[0])) {
                                uniquePhoneCodes.put(args[0], args[1]);
                            }
                        }
                    }
                    reader.close();

//                    reader = new BufferedReader(new InputStreamReader(ApplicationLoader.applicationContext.getResources().getAssets().open("languages.txt")));
//                    while ((line = reader.readLine()) != null) {
//                        String[] args = line.split(",");
//                        if (args.length >= 2) {
//                            languages.put(args[0], args);
//                        }
//                    }
//                    reader.close();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                next.run();
            },
//            next -> {
//                ArrayList<Utilities.Callback<Runnable>> getAuthorizationsCallbacks = new ArrayList<>();
//                for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; ++i) {
//                    final int account = i;
//                    if (UserConfig.getInstance(account).getClientUserId() != 0 && !ConnectionsManager.getInstance(account).isTestBackend()) {
//                        getAuthorizationsCallbacks.add(nextInternal -> {
//                            try {
//                                ConnectionsManager.getInstance(account).sendRequest(new TLRPC.TL_account_getAuthorizations(), (response, error) -> AndroidUtilities.runOnUIThread(() -> {
//                                    if (error == null) {
//                                        TLRPC.TL_account_authorizations res = (TLRPC.TL_account_authorizations) response;
//                                        if (!res.authorizations.isEmpty()) {
//                                            TLRPC.TL_authorization auth = res.authorizations.get(0);
//                                            String[] separated = auth.country.split(", ");
//                                            if (separated.length > 0) {
//                                                pushCountry.run(countries.get(separated[separated.length - 1]));
//                                            }
//                                        }
//                                    }
//                                    nextInternal.run();
//                                }));
//                            } catch (Exception e2) {
//                                FileLog.e(e2);
//                                nextInternal.run();
//                            }
//                        });
//                    }
//                }
//                getAuthorizationsCallbacks.add(n -> next.run());
//                Utilities.doCallbacks(getAuthorizationsCallbacks.toArray(new Utilities.Callback[0]));
//            },
//            next -> {
//                for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; ++i) {
//                    final int account = i;
//                    try {
//                        TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
//                        if (user != null && user.phone != null) {
//                            for (int j = 4; j > 0; j--) {
//                                String code = user.phone.substring(0, j);
//                                String countryCode = uniquePhoneCodes.get(code);
//                                if (countryCode != null) {
//                                    pushCountry.run(countryCode);
//                                    break;
//                                }
//                            }
//                        }
//                    } catch (Exception e3) {
//                        FileLog.e(e3);
//                    }
//                }
//                next.run();
//            },
            next -> {
                try {
                    InputMethodManager imm = (InputMethodManager) ApplicationLoader.applicationContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                    List<InputMethodInfo> ims = imm.getEnabledInputMethodList();

                    for (InputMethodInfo method : ims) {
                        List<InputMethodSubtype> submethods = imm.getEnabledInputMethodSubtypeList(method, true);
                        for (InputMethodSubtype submethod : submethods) {
                            if ("keyboard".equals(submethod.getMode())) {
                                String currentLocale = submethod.getLocale();
                                if (currentLocale != null && currentLocale.contains("_")) {
                                    currentLocale = currentLocale.split("_")[0];
                                }

                                if (TranslateAlert2.languageName(currentLocale) != null) {
                                    result.add(currentLocale);
                                }
                            }
                        }
                    }
                } catch (Exception e4) {
                    FileLog.e(e4);
                }

                next.run();
            },
            next -> onDone.run(result)
        );
    }
}
