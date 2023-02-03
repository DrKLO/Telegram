/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LanguageDetector;
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
        actionBar.setTitle(LocaleController.getString("Language", R.string.Language));

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
        searchItem.setSearchFieldHint(LocaleController.getString("Search", R.string.Search));

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
                        MessagesController.getMainSettings(currentAccount).edit().putBoolean("translate_chat_button", value).apply();
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateSearchSettings);
                        ((TextCheckCell) view).setChecked(value);
                    }
                    final boolean currentFullValue = getContextValue() || getChatValue();
                    if (currentFullValue != prevFullValue) {
                        listAdapter.notifyItemChanged(2);
                        if (currentFullValue) {
                            listAdapter.notifyItemInserted(3);
                        } else {
                            listAdapter.notifyItemRemoved(3);
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
                    position -= (getChatValue() || getContextValue()) ? 7 : 6;
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
                    int reqId = LocaleController.getInstance().applyLanguage(localeInfo, true, false, false, true, currentAccount, () -> {
                        progressDialog.dismiss();
                        if (!sameLang) {
                            actionBar.closeSearchField();
                            updateLanguage();
                        }
                    });
                    if (reqId != 0) {
                        progressDialog.setOnCancelListener(di -> {
                            ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
                        });
                    }

                    String langCode = localeInfo.pluralLangCode,
                            prevLangCode = prevLocale.pluralLangCode;
                    SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                    HashSet<String> selectedLanguages = RestrictedLanguagesSelectActivity.getRestrictedLanguages();
                    HashSet<String> newSelectedLanguages = new HashSet<String>(selectedLanguages);

                    if (selectedLanguages.contains(prevLangCode) && !selectedLanguages.contains(langCode)) {
                        newSelectedLanguages.removeIf(s -> s != null && s.equals(prevLangCode));
                        newSelectedLanguages.add(langCode);
                    }
                    preferences.edit().putStringSet("translate_button_restricted_languages", newSelectedLanguages).apply();
                    MessagesController.getInstance(currentAccount).getTranslateController().checkRestrictedLanguagesUpdate();
                    MessagesController.getInstance(currentAccount).getTranslateController().cleanup();

                    if (!sameLang) {
                        progressDialog.showDelayed(500);
                    }

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
                    position -= (getChatValue() || getContextValue()) ? 7 : 6;
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
//            try {
//                if (searchTimer != null) {
//                    searchTimer.cancel();
//                }
//            } catch (Exception e) {
//                FileLog.e(e);
//            }
//            searchTimer = new Timer();
//            searchTimer.schedule(new TimerTask() {
//                @Override
//                public void run() {
//                try {
//                    searchTimer.cancel();
//                    searchTimer = null;
//                } catch (Exception e) {
//                    FileLog.e(e);
//                }
                processSearch(query);
//                }
//            }, 100, 300);
        }
    }

    private void updateLanguage() {
        if (actionBar != null) {
            actionBar.setTitleAnimated(LocaleController.getString("Language", R.string.Language), true, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
        }
        if (listView != null) {
            for (int i = 0; i < listView.getChildCount(); ++i) {
                View child = listView.getChildAt(i);
                if (child instanceof TranslateSettings || child instanceof HeaderCell) {
                    listAdapter.notifyItemChanged(listView.getChildAdapterPosition(child));
                } else {
                    listAdapter.onBindViewHolder(listView.getChildViewHolder(child), listView.getChildAdapterPosition(child));
                }
            }
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

    private class TranslateSettings extends LinearLayout {
        private SharedPreferences preferences;

        private HeaderCell header;
        private TextCheckCell showButtonCheck;
        private TextCheckCell showChatButtonCheck;
        private TextSettingsCell doNotTranslateCell;
        private TextInfoPrivacyCell info;
        private TextInfoPrivacyCell info2;
        private ValueAnimator doNotTranslateCellAnimation = null;
//        private HeaderCell header2;

//        private float HEIGHT_OPEN = 243;
//        private float HEIGHT_CLOSED = HEIGHT_OPEN - 50;

        private Drawable topShadow, bottomShadow;

        public TranslateSettings(Context context) {
            super(context);
            setFocusable(false);

            setOrientation(VERTICAL);

            topShadow = Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow);
            bottomShadow = Theme.getThemedDrawable(context, R.drawable.greydivider_top, Theme.key_windowBackgroundGrayShadow);

            preferences = MessagesController.getMainSettings(currentAccount);

            header = new HeaderCell(context);
            header.setFocusable(true);
            header.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            header.setText(LocaleController.getString("TranslateMessages", R.string.TranslateMessages));
            header.setContentDescription(LocaleController.getString("TranslateMessages", R.string.TranslateMessages));
            addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            final boolean value = getValue();

            showButtonCheck = new TextCheckCell(context);
            showButtonCheck.setBackground(Theme.createSelectorWithBackgroundDrawable(Theme.getColor(Theme.key_windowBackgroundWhite), Theme.getColor(Theme.key_listSelector)));
            showButtonCheck.setTextAndCheck(
                LocaleController.getString("ShowTranslateButton", R.string.ShowTranslateButton),
                getContextValue(),
                true
            );
            showButtonCheck.setOnClickListener(e -> {
                getMessagesController().getTranslateController().setContextTranslateEnabled(!getContextValue());
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateSearchSettings);
                update();
            });
            addView(showButtonCheck, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            showChatButtonCheck = new TextCheckCell(context);
            showChatButtonCheck.setBackground(Theme.createSelectorWithBackgroundDrawable(Theme.getColor(Theme.key_windowBackgroundWhite), Theme.getColor(Theme.key_listSelector)));
            showChatButtonCheck.setTextAndCheck(
                    LocaleController.getString("ShowTranslateChatButton", R.string.ShowTranslateChatButton),
                    getChatValue(),
                    value
            );
            showChatButtonCheck.setOnClickListener(e -> {
                if (!getUserConfig().isPremium()) {
                    showDialog(new PremiumFeatureBottomSheet(LanguageSelectActivity.this, PremiumPreviewFragment.PREMIUM_FEATURE_TRANSLATIONS, false));
                    return;
                }
                MessagesController.getMainSettings(currentAccount).edit().putBoolean("translate_chat_button", !getChatValue()).apply();
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateSearchSettings);
                update();
            });
            showChatButtonCheck.setCheckBoxIcon(!getUserConfig().isPremium() ? R.drawable.permission_locked : 0);
            addView(showChatButtonCheck, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            doNotTranslateCell = new TextSettingsCell(context);
            doNotTranslateCell.setBackground(Theme.createSelectorWithBackgroundDrawable(Theme.getColor(Theme.key_windowBackgroundWhite), Theme.getColor(Theme.key_listSelector)));
            doNotTranslateCell.setOnClickListener(e -> {
                presentFragment(new RestrictedLanguagesSelectActivity());
                update();
            });
            doNotTranslateCell.setClickable(value && LanguageDetector.hasSupport());
            doNotTranslateCell.setAlpha(value && LanguageDetector.hasSupport() ? 1f : 0f);
            addView(doNotTranslateCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            info = new TextInfoPrivacyCell(context);
            info.setTopPadding(11);
            info.setBottomPadding(16);
            info.setFocusable(true);
            info.setText(LocaleController.getString("TranslateMessagesInfo1", R.string.TranslateMessagesInfo1));
            info.setContentDescription(LocaleController.getString("TranslateMessagesInfo1", R.string.TranslateMessagesInfo1));
            addView(info, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            info2 = new TextInfoPrivacyCell(context);
            info2.setTopPadding(0);
            info2.setBottomPadding(16);
            info2.setFocusable(true);
            info2.setText(LocaleController.getString("TranslateMessagesInfo2", R.string.TranslateMessagesInfo2));
            info2.setContentDescription(LocaleController.getString("TranslateMessagesInfo2", R.string.TranslateMessagesInfo2));
            info2.setAlpha(value ? 0f : 1f);
            addView(info2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

//            header2 = new HeaderCell(context);
//            header2.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
//            header2.setText(LocaleController.getString("Language", R.string.Language));
//            header2.setTranslationY(-Math.max(doNotTranslateCell.getHeight(), info2.getHeight()));
//            addView(header2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

//            setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, height()));
            updateHeight();
            update();
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);

            float topShadowY = Math.max(showChatButtonCheck.getY() + showChatButtonCheck.getHeight(), doNotTranslateCell.getY() + doNotTranslateCell.getHeight() * doNotTranslateCell.getAlpha());
            topShadow.setBounds(0, (int) topShadowY, getWidth(), (int) (topShadowY + AndroidUtilities.dp(12)));
            topShadow.draw(canvas);

            float bottomShadowY = getHeight();
            bottomShadow.setBounds(0, (int) (bottomShadowY - AndroidUtilities.dp(12)), getWidth(), (int) bottomShadowY);
            bottomShadow.draw(canvas);
        }

        public void updateTranslations() {
            header.setText(LocaleController.getString("TranslateMessages", R.string.TranslateMessages));
            showButtonCheck.setTextAndCheck(
                LocaleController.getString("ShowTranslateButton", R.string.ShowTranslateButton), getContextValue(), true
            );
            showChatButtonCheck.setTextAndCheck(
                LocaleController.getString("ShowTranslateChatButton", R.string.ShowTranslateChatButton), getChatValue(), getValue()
            );
            showChatButtonCheck.setCheckBoxIcon(!getUserConfig().isPremium() ? R.drawable.permission_locked : 0);
            showButtonCheck.updateRTL();
            doNotTranslateCell.updateRTL();
            info.setText(LocaleController.getString("TranslateMessagesInfo1", R.string.TranslateMessagesInfo1));
            info.getTextView().setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            info2.setText(LocaleController.getString("TranslateMessagesInfo2", R.string.TranslateMessagesInfo2));
            info2.getTextView().setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            update();
            updateHeight();
        }

        private boolean getValue() {
            return getContextValue() || getChatValue();
        }

        public void update() {
            boolean value = getValue() && LanguageDetector.hasSupport();

            showButtonCheck.setChecked(getContextValue());
            showChatButtonCheck.setChecked(getChatValue());

            if (doNotTranslateCellAnimation != null) {
                doNotTranslateCellAnimation.cancel();
            }

            showChatButtonCheck.setDivider(value);
            HashSet<String> langCodes = RestrictedLanguagesSelectActivity.getRestrictedLanguages();
            final String doNotTranslateCellName = LocaleController.getString("DoNotTranslate", R.string.DoNotTranslate);
            String doNotTranslateCellValue = null;
            try {
                if (langCodes.size() == 1) {
                    doNotTranslateCellValue = TranslateAlert2.capitalFirst(TranslateAlert2.languageName(langCodes.iterator().next()));
                } else {
                    Iterator<String> iterator = langCodes.iterator();
                    boolean first = true;
                    StringBuilder string = new StringBuilder();
                    while (iterator.hasNext()) {
                        String lang = iterator.next();
                        if (!first) {
                            string.append(", ");
                        }
                        string.append(TranslateAlert2.capitalFirst(TranslateAlert2.languageName(lang)));
                        first = false;
                    }
                    doNotTranslateCellValue = string.toString();
                    if (doNotTranslateCell.getValueTextView().getPaint().measureText(doNotTranslateCellValue) >
                        Math.min((AndroidUtilities.displaySize.x - AndroidUtilities.dp(34)) / 2f, AndroidUtilities.displaySize.x - AndroidUtilities.dp(21 * 4) - doNotTranslateCell.getTextView().getPaint().measureText(doNotTranslateCellName))) {
                        doNotTranslateCellValue = null;
                    }
                }
            } catch (Exception ignore) {}
            if (doNotTranslateCellValue == null)
                doNotTranslateCellValue = String.format(LocaleController.getPluralString("Languages", langCodes.size()), langCodes.size());
            doNotTranslateCell.setTextAndValue(doNotTranslateCellName, doNotTranslateCellValue, false, false);
            doNotTranslateCell.setClickable(value);

            info2.setVisibility(View.VISIBLE);
            doNotTranslateCellAnimation = ValueAnimator.ofFloat(doNotTranslateCell.getAlpha(), value ? 1f : 0f);
            doNotTranslateCellAnimation.setInterpolator(CubicBezierInterpolator.DEFAULT);
            doNotTranslateCellAnimation.addUpdateListener(a -> {
                float t = (float) a.getAnimatedValue();
                doNotTranslateCell.setAlpha(t);
                doNotTranslateCell.setTranslationY(-AndroidUtilities.dp(8) * (1f - t));
                info.setTranslationY(-doNotTranslateCell.getHeight() * (1f - t));
                t = 1f;
                info2.setAlpha(1f - t);
                info2.setTranslationY(-doNotTranslateCell.getHeight() * (1f - t));

                translateSettingsBackgroundHeight = header.getMeasuredHeight() + showButtonCheck.getMeasuredHeight() + (int) (doNotTranslateCell.getAlpha() * doNotTranslateCell.getMeasuredHeight());
                invalidate();
            });
            doNotTranslateCellAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    if (doNotTranslateCell.getAlpha() > .5) {
                        info2.setVisibility(View.GONE);
                    } else {
                        info2.setVisibility(View.VISIBLE);
                    }

                    translateSettingsBackgroundHeight = header.getMeasuredHeight() + showButtonCheck.getMeasuredHeight() + (int) (doNotTranslateCell.getAlpha() * doNotTranslateCell.getMeasuredHeight());
                    invalidate();
                }
            });
            doNotTranslateCellAnimation.setDuration((long) (Math.abs(doNotTranslateCell.getAlpha() - (value ? 1f : 0f)) * 200));
            doNotTranslateCellAnimation.start();
//            updateHeight();
        }

        @Override
        protected void onConfigurationChanged(Configuration newConfig) {
            super.onConfigurationChanged(newConfig);
            updateHeight();
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            updateHeight();
            super.onLayout(changed, l, t, r, b);
        }

        void updateHeight() {
            header.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x, MeasureSpec.EXACTLY), MeasureSpec.UNSPECIFIED);
            showButtonCheck.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x, MeasureSpec.EXACTLY), MeasureSpec.UNSPECIFIED);
            showChatButtonCheck.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x, MeasureSpec.EXACTLY), MeasureSpec.UNSPECIFIED);
            doNotTranslateCell.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x, MeasureSpec.EXACTLY), MeasureSpec.UNSPECIFIED);
            info.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x, MeasureSpec.EXACTLY), MeasureSpec.UNSPECIFIED);
            info2.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x, MeasureSpec.EXACTLY), MeasureSpec.UNSPECIFIED);

            int newHeight = searching ? 0 : height();
            if (getLayoutParams() == null) {
                setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, newHeight));
            } else if (getLayoutParams().height != newHeight) {
                RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) getLayoutParams();
                lp.height = newHeight;
                setLayoutParams(lp);
            }
            invalidate();
        }

        int height() {
            return Math.max(AndroidUtilities.dp(40), header.getMeasuredHeight()) +
                   Math.max(AndroidUtilities.dp(50), showButtonCheck.getMeasuredHeight()) +
                   Math.max(AndroidUtilities.dp(50), showChatButtonCheck.getMeasuredHeight()) +
                   Math.max(Math.max(AndroidUtilities.dp(50), doNotTranslateCell.getMeasuredHeight()), (info2.getMeasuredHeight() <= 0 ? AndroidUtilities.dp(51) : info2.getMeasuredHeight())) +
                   (info.getMeasuredHeight() <= 0 ? AndroidUtilities.dp(62) : info.getMeasuredHeight());/* + header2.getHeight()*/
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            updateHeight();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            update();
            updateHeight();
        }
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
                return 5 + (getChatValue() || getContextValue() ? 1 : 0) + 1 + count;
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
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_LANGUAGE: {
                    if (!search) {
                        position -= (getChatValue() || getContextValue()) ? 7 : 6;
                    }
                    TextRadioCell textSettingsCell = (TextRadioCell) holder.itemView;
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
                            textSettingsCell.setTextAndValueAndCheck(String.format("%1$s (%2$s)", localeInfo.name, LocaleController.getString("LanguageCustom", R.string.LanguageCustom)), localeInfo.nameEnglish, false, false, !last);
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
                        sectionCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else {
                        sectionCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
                case VIEW_TYPE_SETTINGS: {
                    TextSettingsCell settingsCell = (TextSettingsCell) holder.itemView;
                    HashSet<String> langCodes = RestrictedLanguagesSelectActivity.getRestrictedLanguages();
                    final String doNotTranslateCellName = LocaleController.getString("DoNotTranslate", R.string.DoNotTranslate);
                    String doNotTranslateCellValue = null;
                    try {
                        if (langCodes.size() == 1) {
                            doNotTranslateCellValue = TranslateAlert2.capitalFirst(TranslateAlert2.languageName(langCodes.iterator().next()));
                        } else {
                            Iterator<String> iterator = langCodes.iterator();
                            boolean first = true;
                            StringBuilder string = new StringBuilder();
                            while (iterator.hasNext()) {
                                String lang = iterator.next();
                                if (!first) {
                                    string.append(", ");
                                }
                                string.append(TranslateAlert2.capitalFirst(TranslateAlert2.languageName(lang)));
                                first = false;
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
                    if (position == 1) {
                        cell.setTextAndCheck(LocaleController.getString("ShowTranslateButton", R.string.ShowTranslateButton), getContextValue(), true);
                    } else if (position == 2) {
                        cell.setTextAndCheck(LocaleController.getString("ShowTranslateChatButton", R.string.ShowTranslateChatButton), getChatValue(), getContextValue() || getChatValue());
                        cell.setCheckBoxIcon(!getUserConfig().isPremium() ? R.drawable.permission_locked : 0);
                    }
                    break;
                }
                case VIEW_TYPE_INFO: {
                    TextInfoPrivacyCell infoCell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == (getContextValue() || getChatValue() ? 4 : 3)) {
                        infoCell.setText(LocaleController.getString("TranslateMessagesInfo1", R.string.TranslateMessagesInfo1));
                        infoCell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        infoCell.setTopPadding(11);
                        infoCell.setBottomPadding(16);
                    } else {
                        infoCell.setTopPadding(0);
                        infoCell.setBottomPadding(16);
                        infoCell.setText(LocaleController.getString("TranslateMessagesInfo2", R.string.TranslateMessagesInfo2));
                        infoCell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_top, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
                case VIEW_TYPE_HEADER: {
                    HeaderCell header = (HeaderCell) holder.itemView;
                    header.setText(position == 0 ? LocaleController.getString("TranslateMessages", R.string.TranslateMessages) : LocaleController.getString("Language", R.string.Language));
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
                if (i-- == 0) return VIEW_TYPE_SWITCH;
                if (getChatValue() || getContextValue()) {
                    if (i-- == 0) return VIEW_TYPE_SETTINGS;
                }
                if (i-- == 0) return VIEW_TYPE_INFO;
                if (i-- == 0) return VIEW_TYPE_INFO;
                if (i-- == 0) return VIEW_TYPE_HEADER;
                if (!unofficialLanguages.isEmpty() && (i == unofficialLanguages.size() || i == unofficialLanguages.size() + 1 + sortedLanguages.size() + 1) || unofficialLanguages.isEmpty() && i == sortedLanguages.size()) {
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
