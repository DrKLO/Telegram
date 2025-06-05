package org.telegram.ui.web;

import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.web.AddressBarList.getLink;

import android.content.Context;
import android.text.TextUtils;
import android.util.LongSparseArray;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.util.Util;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.AbstractSerializedData;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.StickerEmptyView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TimeZone;

public class HistoryFragment extends UniversalFragment {

    private final Runnable closeToTabs;
    private final Utilities.Callback<BrowserHistory.Entry> whenClicked;

    private ArrayList<BrowserHistory.Entry> history = BrowserHistory.getHistory(loadedHistory -> {
        history = loadedHistory;
        if (listView.isAttachedToWindow()) {
            listView.adapter.update(true);
        }
    });
    private final ArrayList<BrowserHistory.Entry> searchResults = new ArrayList<>();
    private boolean searchLoading;

    private String query;

    private NumberTextView selectedCount;
    public HashSet<Integer> selected = new HashSet<>();

    private ActionBarMenuItem searchItem;
    private StickerEmptyView emptyView;

    public boolean isSelected(MessageObject msg) {
        return msg != null && selected.contains(msg.getId());
    }

    public void setSelected(MessageObject msg, boolean select) {
        if (msg == null) return;
        if (select) {
            selected.add(msg.getId());
        } else {
            selected.remove(msg.getId());
        }
    }

    public HistoryFragment(Runnable closeToTabs, Utilities.Callback<BrowserHistory.Entry> whenClicked) {
        super();
        this.closeToTabs = closeToTabs;
        this.whenClicked = whenClicked;
    }

    @Override
    public View createView(Context context) {
        fragmentView = super.createView(context);

        actionBar.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        actionBar.setActionModeColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitleColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_actionBarActionModeDefaultSelector), false);
        actionBar.setItemsColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setItemsColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), true);
        actionBar.setCastShadows(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (actionBar.isActionModeShowed()) {
                        actionBar.hideActionMode();
                        selected.clear();
                        AndroidUtilities.forEachViews(listView, child -> {
                            if (child instanceof AddressBarList.BookmarkView) {
                                ((AddressBarList.BookmarkView) child).setChecked(false);
                            }
                        });
                    } else {
                        finishFragment();
                    }
                }
            }
        });

        final ActionBarMenu actionMode = actionBar.createActionMode();

        selectedCount = new NumberTextView(actionMode.getContext());
        selectedCount.setTextSize(18);
        selectedCount.setTypeface(AndroidUtilities.bold());
        selectedCount.setTextColor(getThemedColor(Theme.key_actionBarActionModeDefaultIcon));
        selectedCount.setOnTouchListener((v, event) -> true);
        actionMode.addView(selectedCount, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 65, 0, 0, 0));

        searchItem = actionBar.createMenu().addItem(0, R.drawable.ic_ab_search, getResourceProvider()).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {}

            @Override
            public void onSearchCollapse() {
                query = null;
                searchLoading = false;
                AndroidUtilities.cancelRunOnUIThread(applySearch);

                if (listView != null) {
                    listView.adapter.update(true);
                    listView.layoutManager.scrollToPositionWithOffset(0, 0);
                }

                emptyView.title.setText(getString(TextUtils.isEmpty(query) ? R.string.WebNoHistory : R.string.WebNoSearchedHistory));
            }

            @Override
            public void onTextChanged(EditText editText) {
                final boolean hadQuery = !TextUtils.isEmpty(query);
                final String query = editText.getText().toString();
                if (!TextUtils.equals(HistoryFragment.this.query, query)) {
                    HistoryFragment.this.query = query;
                    scheduleSearch();
                    emptyView.title.setText(getString(TextUtils.isEmpty(query) ? R.string.WebNoHistory : R.string.WebNoSearchedHistory));
                }
                if (listView != null) {
                    listView.adapter.update(true);
                    if (hadQuery != !TextUtils.isEmpty(query)) {
                        listView.layoutManager.scrollToPositionWithOffset(0, 0);
                    }
                }
            }

            private void scheduleSearch() {
                searchLoading = true;
                AndroidUtilities.cancelRunOnUIThread(applySearch);
                AndroidUtilities.runOnUIThread(applySearch, 500);
            }

            private Runnable applySearch = () -> {
                final ArrayList<BrowserHistory.Entry> history = new ArrayList<>(HistoryFragment.this.history);
                final String query = HistoryFragment.this.query;
                Utilities.searchQueue.postRunnable(() -> {
                    final ArrayList<BrowserHistory.Entry> entries = new ArrayList<>();

                    for (int i = 0; i < history.size(); ++i) {
                        BrowserHistory.Entry e = history.get(i);
                        if (matches(e.url, query) || e.meta != null && (matches(e.meta.title, query) || matches(e.meta.sitename, query))) {
                            entries.add(e);
                        }
                    }

                    AndroidUtilities.runOnUIThread(() -> {
                        searchResults.clear();
                        searchResults.addAll(entries);
                        searchLoading = false;

                        if (listView != null) {
                            listView.adapter.update(true);
                        }
                    });
                });
            };

            public boolean matches(String src, String q) {
                if (src == null || q == null) return false;
                final String lsrc = src.toLowerCase();
                final String lq = q.toLowerCase();
                if (lsrc.startsWith(lq) || lsrc.contains(" " + lq) || lsrc.contains("." + lq)) return true;
                final String tsrc = AndroidUtilities.translitSafe(lsrc);
                final String tq = AndroidUtilities.translitSafe(lq);
                return (tsrc.startsWith(tq) || tsrc.contains(" " + tq) || tsrc.contains("." + tq));
            }
        });
        searchItem.setSearchFieldHint(getString(R.string.Search));
        searchItem.setContentDescription(getString(R.string.Search));
        EditTextBoldCursor editText = searchItem.getSearchField();
        editText.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setHintTextColor(getThemedColor(Theme.key_player_time));
        editText.setCursorColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));

        emptyView = new StickerEmptyView(context, null, StickerEmptyView.STICKER_TYPE_SEARCH);
        emptyView.title.setText(getString(TextUtils.isEmpty(query) ? R.string.WebNoHistory : R.string.WebNoSearchedHistory));
        emptyView.subtitle.setVisibility(View.GONE);
        emptyView.showProgress(false, false);
        emptyView.setAnimateLayoutChange(true);
        ((FrameLayout) fragmentView).addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setEmptyView(emptyView);

        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (listView.scrollingByUser) {
                    AndroidUtilities.hideKeyboard(fragmentView);
                }
            }
        });

        return fragmentView;
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.WebHistory);
    }


    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {

        int lastDateKey = 0;
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeZone(TimeZone.getDefault());
        if (TextUtils.isEmpty(query)) {
            if (history != null) {
                for (int i = history.size() - 1; i >= 0; --i) {
                    BrowserHistory.Entry entry = history.get(i);
                    calendar.setTimeInMillis(entry.time);
                    int dateKey = calendar.get(Calendar.YEAR) * 1_00_00 + calendar.get(Calendar.MONTH) * 1_00 + calendar.get(Calendar.DAY_OF_MONTH);
                    if (lastDateKey != dateKey) {
                        lastDateKey = dateKey;
                        items.add(UItem.asGraySection(LocaleController.formatDateChat(entry.time / 1000L)));
                    }
                    items.add(AddressBarList.BookmarkView.Factory.as(entry, query));
                }
            }
        } else {
            for (int i = searchResults.size() - 1; i >= 0; --i) {
                BrowserHistory.Entry entry = searchResults.get(i);
                calendar.setTimeInMillis(entry.time);
                int dateKey = calendar.get(Calendar.YEAR) * 1_00_00 + calendar.get(Calendar.MONTH) * 1_00 + calendar.get(Calendar.DAY_OF_MONTH);
                if (lastDateKey != dateKey) {
                    lastDateKey = dateKey;
                    items.add(UItem.asGraySection(LocaleController.formatDateChat(entry.time / 1000L)));
                }
                items.add(AddressBarList.BookmarkView.Factory.as(entry, query));
            }
            if (searchLoading) {
                items.add(UItem.asFlicker(FlickerLoadingView.BROWSER_BOOKMARK));
                items.add(UItem.asFlicker(FlickerLoadingView.BROWSER_BOOKMARK));
                items.add(UItem.asFlicker(FlickerLoadingView.BROWSER_BOOKMARK));
            }
        }
        if (!items.isEmpty()) {
            items.add(UItem.asShadow(null));
        }
    }

    public static boolean matches(String src, String q) {
        if (src == null || q == null) return false;
        final String lsrc = src.toLowerCase();
        final String lq = q.toLowerCase();
        if (lsrc.startsWith(lq) || lsrc.contains(" " + lq) || lsrc.contains("." + lq)) return true;
        final String tsrc = AndroidUtilities.translitSafe(lsrc);
        final String tq = AndroidUtilities.translitSafe(lq);
        return (tsrc.startsWith(tq) || tsrc.contains(" " + tq) || tsrc.contains("." + tq));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.instanceOf(AddressBarList.BookmarkView.Factory.class)) {
            if (actionBar.isActionModeShowed()) {
                clickSelect(item, view);
            } else {
                finishFragment();
                whenClicked.run((BrowserHistory.Entry) item.object2);
            }
        }
    }

    public void clickSelect(UItem item, View view) {
//        AddressBarList.BookmarkView cell = (AddressBarList.BookmarkView) view;
//        MessageObject msg = (MessageObject) item.object2;
//        if (isSelected(msg)) {
//            setSelected(msg, false);
//            cell.setChecked(false);
//        } else {
//            setSelected(msg, true);
//            cell.setChecked(true);
//        }
//        selectedCount.setNumber(selected.size(), true);
//        if (selected.isEmpty()) {
//            actionBar.hideActionMode();
//        } else {
//            actionBar.showActionMode();
//        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
//        if (item.instanceOf(AddressBarList.BookmarkView.Factory.class)) {
//            clickSelect(item, view);
//            return true;
//        }
        return false;
    }

    @Override
    public boolean isLightStatusBar() {
        return AndroidUtilities.computePerceivedBrightness(getThemedColor(Theme.key_windowBackgroundWhite)) > .721f;
    }

    private void updateWithOffset() {
        int position = -1, offset = 0;
        for (int i = 0; i < listView.getChildCount(); ++i) {
            View child = listView.getChildAt(i);
            position = listView.getChildAdapterPosition(child);
            if (position < 0) continue;
            offset = child.getTop();
            break;
        }
        listView.adapter.update(true);
        if (position >= 0) {
            listView.layoutManager.scrollToPositionWithOffset(position, offset);
        } else {
            listView.layoutManager.scrollToPositionWithOffset(0, 0);
        }
    }

}
