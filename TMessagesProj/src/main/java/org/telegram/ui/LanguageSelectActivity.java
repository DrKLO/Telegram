/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.LanguageCell;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Timer;
import java.util.TimerTask;

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

    @Override
    public boolean onFragmentCreate() {
        fillLanguages();
        LocaleController.getInstance().loadRemoteLanguages();
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.suggestedLangpack);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.suggestedLangpack);
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
                }
            }
        });
        item.getSearchField().setHint(LocaleController.getString("Search", R.string.Search));

        listAdapter = new ListAdapter(context, false);
        searchListViewAdapter = new ListAdapter(context, true);

        fragmentView = new FrameLayout(context);
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

        listView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                if (getParentActivity() == null || parentLayout == null) {
                    return;
                }
                LocaleController.LocaleInfo localeInfo = null;
                if (searching && searchWas) {
                    if (position >= 0 && position < searchResult.size()) {
                        localeInfo = searchResult.get(position);
                    }
                } else {
                    if (position >= 0 && position < sortedLanguages.size()) {
                        localeInfo = sortedLanguages.get(position);
                    }
                }
                if (localeInfo != null) {
                    LocaleController.getInstance().applyLanguage(localeInfo, true, false, false, true);
                    parentLayout.rebuildAllFragmentViews(false, false);
                }
                finishFragment();
            }
        });

        listView.setOnItemLongClickListener(new RecyclerListView.OnItemLongClickListener() {
            @Override
            public boolean onItemClick(View view, int position) {
                LocaleController.LocaleInfo localeInfo = null;
                if (searching && searchWas) {
                    if (position >= 0 && position < searchResult.size()) {
                        localeInfo = searchResult.get(position);
                    }
                } else {
                    if (position >= 0 && position < sortedLanguages.size()) {
                        localeInfo = sortedLanguages.get(position);
                    }
                }
                if (localeInfo == null || localeInfo.pathToFile == null || getParentActivity() == null || localeInfo.isRemote()) {
                    return false;
                }
                final LocaleController.LocaleInfo finalLocaleInfo = localeInfo;
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setMessage(LocaleController.getString("DeleteLocalization", R.string.DeleteLocalization));
                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (LocaleController.getInstance().deleteLanguage(finalLocaleInfo)) {
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
                    }
                });
                builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                showDialog(builder.create());
                return true;
            }
        });

        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING && searching && searchWas) {
                    AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                }
            }
        });

        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.suggestedLangpack) {
            if (listAdapter != null) {
                fillLanguages();
                listAdapter.notifyDataSetChanged();
            }
        }
    }

    private void fillLanguages() {
        sortedLanguages = new ArrayList<>(LocaleController.getInstance().languages);
        final LocaleController.LocaleInfo currentLocale = LocaleController.getInstance().getCurrentLocaleInfo();
        Collections.sort(sortedLanguages, new Comparator<LocaleController.LocaleInfo>() {
            @Override
            public int compare(LocaleController.LocaleInfo o, LocaleController.LocaleInfo o2) {
                if (o == currentLocale) {
                    return -1;
                } else if (o2 == currentLocale) {
                    return 1;
                }
                return o.name.compareTo(o2.name);
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
        Utilities.searchQueue.postRunnable(new Runnable() {
            @Override
            public void run() {

                String q = query.trim().toLowerCase();
                if (q.length() == 0) {
                    updateSearchResults(new ArrayList<LocaleController.LocaleInfo>());
                    return;
                }
                long time = System.currentTimeMillis();
                ArrayList<LocaleController.LocaleInfo> resultArray = new ArrayList<>();

                for (int a = 0; a < sortedLanguages.size(); a++) {
                    LocaleController.LocaleInfo c = sortedLanguages.get(a);
                    if (c.name.toLowerCase().startsWith(query) || c.nameEnglish.toLowerCase().startsWith(query)) {
                        resultArray.add(c);
                    }
                }

                updateSearchResults(resultArray);
            }
        });
    }

    private void updateSearchResults(final ArrayList<LocaleController.LocaleInfo> arrCounties) {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                searchResult = arrCounties;
                searchListViewAdapter.notifyDataSetChanged();
            }
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
            return true;
        }

        @Override
        public int getItemCount() {
            if (search) {
                if (searchResult == null) {
                    return 0;
                }
                return searchResult.size();
            } else {
                return sortedLanguages.size();
            }
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new LanguageCell(mContext, false));
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            LanguageCell textSettingsCell = (LanguageCell) holder.itemView;
            LocaleController.LocaleInfo localeInfo;
            boolean last;
            if (search) {
                localeInfo = searchResult.get(position);
                last = position == searchResult.size() - 1;
            } else {
                localeInfo = sortedLanguages.get(position);
                last = position == sortedLanguages.size() - 1;
            }
            if (localeInfo.isLocal()) {
                textSettingsCell.setLanguage(localeInfo, String.format("%1$s (%2$s)", localeInfo.name, LocaleController.getString("LanguageCustom", R.string.LanguageCustom)), !last);
            } else {
                textSettingsCell.setLanguage(localeInfo, null, !last);
            }
            textSettingsCell.setLanguageSelected(localeInfo == LocaleController.getInstance().getCurrentLocaleInfo());
        }

        @Override
        public int getItemViewType(int i) {
            return 0;
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCH, null, null, null, null, Theme.key_actionBarDefaultSearch),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SEARCHPLACEHOLDER, null, null, null, null, Theme.key_actionBarDefaultSearchPlaceholder),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),

                new ThemeDescription(emptyView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_emptyListPlaceholder),

                new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider),

                new ThemeDescription(listView, 0, new Class[]{LanguageCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{LanguageCell.class}, new String[]{"textView2"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3),
                new ThemeDescription(listView, 0, new Class[]{LanguageCell.class}, new String[]{"checkImage"}, null, null, null, Theme.key_featuredStickers_addedIcon),
        };
    }
}
