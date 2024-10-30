/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.TranslateController;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.LanguageCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextRadioCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.TranslateAlert2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Timer;

public class LanguageSelectActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private ListAdapter searchListViewAdapter;
    private EmptyTextProgressView emptyView;

    private boolean searchWas;
    private boolean searching;

    private Timer searchTimer;
    private ArrayList<LocaleController.LocaleInfo> searchResult;
    private ArrayList<LocaleController.LocaleInfo> sortedLanguages;
    private ArrayList<LocaleController.LocaleInfo> unofficialLanguages;

    private ActionBarMenuItem searchItem;
    private int translateSettingsBackgroundHeight;

    @Override
    public boolean onFragmentCreate() {
        fillLanguages();
        LocaleController.getInstance().loadRemoteLanguages(currentAccount, false);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.suggestedLangpack);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.suggestedLangpack);
    }

    @Override
    public View createView(Context context) {
        searching = false;
        searchWas = false;

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.Language));

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        searchItem = menu.addItem(0, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
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
        searchItem.setSearchFieldHint(LocaleController.getString(R.string.Search));

        listAdapter = new ListAdapter(context, false);
        searchListViewAdapter = new ListAdapter(context, true);

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        emptyView = new EmptyTextProgressView(context);
        emptyView.setText(LocaleController.getString(R.string.NoResult));
        emptyView.showTextView();
        emptyView.setShowAtCenter(true);
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView = new RecyclerListView(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (getAdapter() == listAdapter && getItemAnimator() != null && getItemAnimator().isRunning()) {
                    int backgroundColor = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
                    drawItemBackground(canvas, 0, translateSettingsBackgroundHeight, backgroundColor);
//                    drawItemBackground(canvas, 1, Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
                    drawSectionBackground(canvas, 1, 2, backgroundColor);
                }
                super.dispatchDraw(canvas);
            }
        };
        listView.setEmptyView(emptyView);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setAdapter(listAdapter);
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator() {
            @Override
            protected void onMoveAnimationUpdate(RecyclerView.ViewHolder holder) {
                listView.invalidate();
                listView.updateSelector();
            }
        };
        itemAnimator.setDurations(400);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        listView.setItemAnimator(itemAnimator);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener((view, position) -> {
            try {
                if (view instanceof TextCheckCell) {
                    final boolean prevFullValue = getContextValue() || getChatValue();
                    if (position == 1) {
                        boolean value = !getContextValue();
                        getMessagesController().getTranslateController().setContextTranslateEnabled(value);
                        ((TextCheckCell) view).setChecked(value);
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateSearchSettings);
                    } else if (position == 2) {
                        boolean value = !getChatValue();
                        if (value && !getUserConfig().isPremium()) {
                            showDialog(new PremiumFeatureBottomSheet(LanguageSelectActivity.this, PremiumPreviewFragment.PREMIUM_FEATURE_TRANSLATIONS, false));
                            return;
                        }
                        getMessagesController().getTranslateController().setChatTranslateEnabled(value);
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateSearchSettings);
                        ((TextCheckCell) view).setChecked(value);
                    }
                    final boolean currentFullValue = getContextValue() || getChatValue();
                    if (currentFullValue != prevFullValue) {
                        int start = 1 + (!getMessagesController().premiumFeaturesBlocked() ? 1 : 0);
                        TextCheckCell last = null;
                        for (int i = 0; i < listView.getChildCount(); ++i) {
                            View child = listView.getChildAt(i);
                            if (listView.getChildAdapterPosition(child) == start && child instanceof TextCheckCell) {
                                last = (TextCheckCell) child;
                            }
                        }
                        if (last != null) {
                            last.setDivider(currentFullValue);
                        }
                        if (currentFullValue) {
                            listAdapter.notifyItemInserted(start + 1);
                        } else {
                            listAdapter.notifyItemRemoved(start + 1);
                        }
                    }
                    return;
                } else if (view instanceof TextSettingsCell) {
                    presentFragment(new RestrictedLanguagesSelectActivity());
                    return;
                }
                if (getParentActivity() == null || parentLayout == null || !(view instanceof TextRadioCell)) {
                    return;
                }
                boolean search = listView.getAdapter() == searchListViewAdapter;
                if (!search) {
                    position -= (7 - (!(getChatValue() || getContextValue()) ? 1 : 0) - (getMessagesController().premiumFeaturesBlocked() ? 1 : 0));
                }
                LocaleController.LocaleInfo localeInfo;
                if (search) {
                    localeInfo = searchResult.get(position);
                } else if (!unofficialLanguages.isEmpty() && position >= 0 && position < unofficialLanguages.size()) {
                    localeInfo = unofficialLanguages.get(position);
                } else {
                    if (!unofficialLanguages.isEmpty()) {
                        position -= unofficialLanguages.size() + 1;
                    }
                    localeInfo = sortedLanguages.get(position);
                }
                if (localeInfo != null) {
                    LocaleController.LocaleInfo prevLocale = LocaleController.getInstance().getCurrentLocaleInfo();
                    boolean sameLang = prevLocale == localeInfo;

                    final AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);
                    if (!sameLang) {
                        progressDialog.showDelayed(500);
                    }
                    int reqId = LocaleController.getInstance().applyLanguage(localeInfo, true, false, false, true, currentAccount, () -> {
                        progressDialog.dismiss();
                        if (!sameLang) {
                            AndroidUtilities.runOnUIThread(() -> {
                                actionBar.closeSearchField();
                                updateLanguage();
                            }, 10);
                        }
                    });
                    if (reqId != 0) {
                        progressDialog.setOnCancelListener(di -> {
                            ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
                        });
                    }

                    String langCode = localeInfo.pluralLangCode,
                            prevLangCode = prevLocale.pluralLangCode;
                    HashSet<String> selectedLanguages = RestrictedLanguagesSelectActivity.getRestrictedLanguages();
                    HashSet<String> newSelectedLanguages = new HashSet<String>(selectedLanguages);

                    if (selectedLanguages.contains(prevLangCode) && !selectedLanguages.contains(langCode)) {
                        newSelectedLanguages.removeIf(s -> s != null && s.equals(prevLangCode));
                    }
                    if (langCode != null && !"null".equals(langCode)) {
                        newSelectedLanguages.add(langCode);
                    }
                    RestrictedLanguagesSelectActivity.updateRestrictedLanguages(newSelectedLanguages, false);
                    MessagesController.getInstance(currentAccount).getTranslateController().checkRestrictedLanguagesUpdate();
                    MessagesController.getInstance(currentAccount).getTranslateController().cleanup();

                    TranslateController.invalidateSuggestedLanguageCodes();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });

        listView.setOnItemLongClickListener((view, position) -> {
            try {
                if (getParentActivity() == null || parentLayout == null || !(view instanceof TextRadioCell)) {
                    return false;
                }
                boolean search = listView.getAdapter() == searchListViewAdapter;
                if (!search) {
                    position -= (7 - (!(getChatValue() || getContextValue()) ? 1 : 0) - (getMessagesController().premiumFeaturesBlocked() ? 1 : 0));
                }
                LocaleController.LocaleInfo localeInfo;
                if (search) {
                    localeInfo = searchResult.get(position);
                } else if (!unofficialLanguages.isEmpty() && position >= 0 && position < unofficialLanguages.size()) {
                    localeInfo = unofficialLanguages.get(position);
                } else {
                    if (!unofficialLanguages.isEmpty()) {
                        position -= unofficialLanguages.size() + 1;
                    }
                    localeInfo = sortedLanguages.get(position);
                }
                if (localeInfo == null || localeInfo.pathToFile == null || localeInfo.isRemote() && localeInfo.serverIndex != Integer.MAX_VALUE) {
                    return false;
                }
                final LocaleController.LocaleInfo finalLocaleInfo = localeInfo;
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString(R.string.DeleteLocalizationTitle));
                builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("DeleteLocalizationText", R.string.DeleteLocalizationText, localeInfo.name)));
                builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialogInterface, i) -> {
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
                builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                AlertDialog alertDialog = builder.create();
                showDialog(alertDialog);
                TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (button != null) {
                    button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
                }
            } catch (Exception e) {
                FileLog.e(e);
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
                AndroidUtilities.runOnUIThread(() -> { listAdapter.notifyDataSetChanged(); });
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
        unofficialLanguages = new ArrayList<>(LocaleController.getInstance().unofficialLanguages);

        ArrayList<LocaleController.LocaleInfo> arrayList = LocaleController.getInstance().languages;
        for (int a = 0, size = arrayList.size(); a < size; a++) {
            LocaleController.LocaleInfo info = arrayList.get(a);
            if (info.serverIndex != Integer.MAX_VALUE) {
                sortedLanguages.add(info);
            } else {
                unofficialLanguages.add(info);
            }
        }
        Collections.sort(sortedLanguages, comparator);
        Collections.sort(unofficialLanguages, comparator);
    }

    @Override
    public void onBecomeFullyVisible() {
        super.onBecomeFullyVisible();
        LocaleController.getInstance().checkForcePatchLangpack(currentAccount, () -> {
            if (!isPaused) {
                updateLanguage();
            }
        });
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
            searching = false;
            searchResult = null;
            if (listView != null) {
                emptyView.setVisibility(View.GONE);
                listView.setAdapter(listAdapter);
            }
        } else {
            processSearch(query);
        }
    }

    private void updateLanguage() {
        if (actionBar != null) {
            String newTitle = LocaleController.getString(R.string.Language);
            if (!TextUtils.equals(actionBar.getTitle(), newTitle)) {
                actionBar.setTitleAnimated(newTitle, true, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
            }
        }
        if (listAdapter != null) {
            listAdapter.notifyItemRangeChanged(0, listAdapter.getItemCount());
//            for (int i = 0; i < listView.getChildCount(); ++i) {
//                View child = listView.getChildAt(i);
//                listAdapter.onBindViewHolder(listView.getChildViewHolder(child), listView.getChildAdapterPosition(child));
//                if (child instanceof TextRadioCell) {
//                    ((TextRadioCell) child).updateRTL();
//                } else if (child instanceof TextInfoPrivacyCell) {
//                    ((TextInfoPrivacyCell) child).updateRTL();
//                }
//            }
        }
    }

    private void processSearch(final String query) {
        Utilities.searchQueue.postRunnable(() -> {

            String q = query.trim().toLowerCase();
            if (q.length() == 0) {
                updateSearchResults(new ArrayList<>());
                return;
            }
            long time = System.currentTimeMillis();
            ArrayList<LocaleController.LocaleInfo> resultArray = new ArrayList<>();

            for (int a = 0, N = unofficialLanguages.size(); a < N; a++) {
                LocaleController.LocaleInfo c = unofficialLanguages.get(a);
                if (c.name.toLowerCase().startsWith(query) || c.nameEnglish.toLowerCase().startsWith(query)) {
                    resultArray.add(c);
                }
            }

            for (int a = 0, N = sortedLanguages.size(); a < N; a++) {
                LocaleController.LocaleInfo c = sortedLanguages.get(a);
                if (c.name.toLowerCase().startsWith(query) || c.nameEnglish.toLowerCase().startsWith(query)) {
                    resultArray.add(c);
                }
            }

            updateSearchResults(resultArray);
        });
    }

    private void updateSearchResults(final ArrayList<LocaleController.LocaleInfo> arrCounties) {
        AndroidUtilities.runOnUIThread(() -> {
            searchResult = arrCounties;
            searchListViewAdapter.notifyDataSetChanged();
        });
    }

    private boolean getContextValue() {
        return getMessagesController().getTranslateController().isContextTranslateEnabled();
    }

    private boolean getChatValue() {
        return getMessagesController().getTranslateController().isFeatureAvailable();
    }

    public static final int VIEW_TYPE_LANGUAGE = 0;
    public static final int VIEW_TYPE_SHADOW = 1;
    public static final int VIEW_TYPE_SWITCH = 2;
    public static final int VIEW_TYPE_HEADER = 3;
    public static final int VIEW_TYPE_SETTINGS = 4;
    public static final int VIEW_TYPE_INFO = 5;

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;
        private boolean search;

        public ListAdapter(Context context, boolean isSearch) {
            mContext = context;
            search = isSearch;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            final int viewType = holder.getItemViewType();
            return viewType == VIEW_TYPE_LANGUAGE || viewType == VIEW_TYPE_SETTINGS || viewType == VIEW_TYPE_SWITCH;
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
                if (count != 0) {
                    count++;
                }
                if (!unofficialLanguages.isEmpty()) {
                    count += unofficialLanguages.size() + 1;
                }
                return 4 + (getMessagesController().premiumFeaturesBlocked() ? 0 : 1) + (getChatValue() || getContextValue() ? 1 : 0) + 1 + count;
            }
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case VIEW_TYPE_LANGUAGE: {
                    view = new TextRadioCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                }
                case VIEW_TYPE_SWITCH:
                    TextCheckCell switchCell = new TextCheckCell(mContext);
                    switchCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    view = switchCell;
                    break;
                case VIEW_TYPE_SETTINGS:
                    TextSettingsCell settingsCell = new TextSettingsCell(mContext);
                    settingsCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    view = settingsCell;
                    break;
                case VIEW_TYPE_HEADER:
                    HeaderCell header = new HeaderCell(mContext);
                    header.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    view = header;
                    break;
                case VIEW_TYPE_INFO:
                    TextInfoPrivacyCell infoCell = new TextInfoPrivacyCell(mContext);
                    view = infoCell;
                    break;
                case VIEW_TYPE_SHADOW:
                default: {
                    view = new ShadowSectionCell(mContext);
                    break;
                }
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof TextRadioCell) {
                ((TextRadioCell) holder.itemView).updateRTL();
            }
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_LANGUAGE: {
                    if (!search) {
                        position -= (7 - (!(getChatValue() || getContextValue()) ? 1 : 0) - (getMessagesController().premiumFeaturesBlocked() ? 1 : 0));
                    }
                    TextRadioCell textSettingsCell = (TextRadioCell) holder.itemView;
                    textSettingsCell.updateRTL();
                    LocaleController.LocaleInfo localeInfo = null;
                    boolean last;
                    if (search) {
                        if (position >= 0 && position < searchResult.size()) {
                            localeInfo = searchResult.get(position);
                        }
                        last = position == searchResult.size() - 1;
                    } else if (!unofficialLanguages.isEmpty() && position >= 0 && position < unofficialLanguages.size()) {
                        localeInfo = unofficialLanguages.get(position);
                        last = position == unofficialLanguages.size() - 1;
                    } else {
                        if (!unofficialLanguages.isEmpty()) {
                            position -= unofficialLanguages.size() + 1;
                        }
                        if (position >= 0 && position < sortedLanguages.size()) {
                            localeInfo = sortedLanguages.get(position);
                        }
                        last = position == sortedLanguages.size() - 1;
                    }
                    if (localeInfo != null) {
                        if (localeInfo.isLocal()) {
                            textSettingsCell.setTextAndValueAndCheck(String.format("%1$s (%2$s)", localeInfo.name, LocaleController.getString(R.string.LanguageCustom)), localeInfo.nameEnglish, false, false, !last);
                        } else {
                            textSettingsCell.setTextAndValueAndCheck(localeInfo.name, localeInfo.nameEnglish, false, false, !last);
                        }
                    }
                    textSettingsCell.setChecked(localeInfo == LocaleController.getInstance().getCurrentLocaleInfo());
                    break;
                }
                case VIEW_TYPE_SHADOW: {
                    if (!search)
                        position--;
                    ShadowSectionCell sectionCell = (ShadowSectionCell) holder.itemView;
                    if (!unofficialLanguages.isEmpty() && position == unofficialLanguages.size()) {
                        sectionCell.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else {
                        sectionCell.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
                case VIEW_TYPE_SETTINGS: {
                    TextSettingsCell settingsCell = (TextSettingsCell) holder.itemView;
                    settingsCell.updateRTL();
                    HashSet<String> langCodes = RestrictedLanguagesSelectActivity.getRestrictedLanguages();
                    final String doNotTranslateCellName = LocaleController.getString(R.string.DoNotTranslate);
                    String doNotTranslateCellValue = null;
                    try {
                        boolean[] accusative = new boolean[1];
                        if (langCodes.size() == 0) {
                            doNotTranslateCellValue = "";
                        } else if (langCodes.size() == 1) {
                            doNotTranslateCellValue = TranslateAlert2.capitalFirst(TranslateAlert2.languageName(langCodes.iterator().next(), accusative));
                        } else {
                            Iterator<String> iterator = langCodes.iterator();
                            boolean first = true;
                            StringBuilder string = new StringBuilder();
                            while (iterator.hasNext()) {
                                String lang = iterator.next();
                                if (!first) {
                                    string.append(", ");
                                }
                                String langName = TranslateAlert2.capitalFirst(TranslateAlert2.languageName(lang, accusative));
                                if (langName != null) {
                                    string.append(langName);
                                    first = false;
                                }
                            }
                            doNotTranslateCellValue = string.toString();
                            if (settingsCell.getValueTextView().getPaint().measureText(doNotTranslateCellValue) > Math.min((AndroidUtilities.displaySize.x - AndroidUtilities.dp(34)) / 2f, AndroidUtilities.displaySize.x - AndroidUtilities.dp(21 * 4) - settingsCell.getTextView().getPaint().measureText(doNotTranslateCellName))) {
                                doNotTranslateCellValue = null;
                            }
                        }
                    } catch (Exception ignore) {}
                    if (doNotTranslateCellValue == null) {
                        doNotTranslateCellValue = String.format(LocaleController.getPluralString("Languages", langCodes.size()), langCodes.size());
                    }
                    settingsCell.setTextAndValue(doNotTranslateCellName, doNotTranslateCellValue, true, false);
                    break;
                }
                case VIEW_TYPE_SWITCH: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    cell.updateRTL();
                    if (position == 1) {
                        cell.setTextAndCheck(LocaleController.getString(R.string.ShowTranslateButton), getContextValue(), true);
                        cell.setCheckBoxIcon(0);
                    } else if (position == 2) {
                        cell.setTextAndCheck(LocaleController.getString(R.string.ShowTranslateChatButton), getChatValue(), getContextValue() || getChatValue());
                        cell.setCheckBoxIcon(!getUserConfig().isPremium() ? R.drawable.permission_locked : 0);
                    }
                    break;
                }
                case VIEW_TYPE_INFO: {
                    TextInfoPrivacyCell infoCell = (TextInfoPrivacyCell) holder.itemView;
                    infoCell.updateRTL();
                    if (position == (!getMessagesController().premiumFeaturesBlocked() && (getContextValue() || getChatValue()) ? 4 : 3)) {
                        infoCell.setText(LocaleController.getString(R.string.TranslateMessagesInfo1));
                        infoCell.setBackground(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        infoCell.setTopPadding(11);
                        infoCell.setBottomPadding(16);
                    } else {
                        infoCell.setTopPadding(0);
                        infoCell.setBottomPadding(16);
                        infoCell.setText(LocaleController.getString(R.string.TranslateMessagesInfo2));
                        infoCell.setBackground(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_top, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
                case VIEW_TYPE_HEADER: {
                    HeaderCell header = (HeaderCell) holder.itemView;
                    header.setText(position == 0 ? LocaleController.getString(R.string.TranslateMessages) : LocaleController.getString(R.string.Language));
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int i) {
            if (search) {
                return VIEW_TYPE_LANGUAGE;
            } else {
                if (i-- == 0) return VIEW_TYPE_HEADER;
                if (i-- == 0) return VIEW_TYPE_SWITCH;
                if (!getMessagesController().premiumFeaturesBlocked()) {
                    if (i-- == 0) return VIEW_TYPE_SWITCH;
                }
                if (getChatValue() || getContextValue()) {
                    if (i-- == 0) return VIEW_TYPE_SETTINGS;
                }
                if (i-- == 0) return VIEW_TYPE_INFO;
                if (i-- == 0) return VIEW_TYPE_INFO;
                if (i-- == 0) return VIEW_TYPE_HEADER;
                if (!unofficialLanguages.isEmpty() && (i == unofficialLanguages.size() || i == unofficialLanguages.size() + sortedLanguages.size() + 1) || unofficialLanguages.isEmpty() && i == sortedLanguages.size()) {
                    return VIEW_TYPE_SHADOW;
                }
                return VIEW_TYPE_LANGUAGE;
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
}
