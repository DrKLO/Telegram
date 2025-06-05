package org.telegram.ui.web;

import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.web.AddressBarList.getLink;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.util.Util;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class BookmarksFragment extends UniversalFragment {

    private static HashMap<Integer, AddressBarList.BookmarksList> cachedLists;
    public AddressBarList.BookmarksList list = new AddressBarList.BookmarksList(currentAccount, this::updateWithOffset);
    public AddressBarList.BookmarksList searchList;

    private final Runnable closeToTabs;
    private final Utilities.Callback<String> whenClicked;

    private ActionBarMenuItem searchItem;
    private ActionBarMenuItem gotoItem;
    private String query;

    private NumberTextView selectedCount;
    public HashSet<Integer> selected = new HashSet<>();

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

    public void deleteSelectedMessages() {
        final HashSet<String> selectedLinks = new HashSet<>();
        final ArrayList<MessageObject> messageObjects = new ArrayList<MessageObject>();
        final HashSet<Integer> ids = new HashSet<>();
        for (int id : selected) {
            MessageObject msg = null;
            for (MessageObject m : list.links) {
                if (m != null && m.getId() == id) {
                    msg = m;
                    break;
                }
            }
            if (searchList != null && msg == null) {
                for (MessageObject m : searchList.links) {
                    if (m != null && m.getId() == id) {
                        msg = m;
                        break;
                    }
                }
            }
            if (msg != null) {
                messageObjects.add(msg);
                ids.add(msg.getId());
                selectedLinks.add(getLink(msg));
            }
        }
//        for (String link : selectedLinks) {
//            for (MessageObject m : list.links) {
//                if (m != null && !selected.contains(m.getId()) && TextUtils.equals(getLink(m), link)) {
//                    messageObjects.add(m);
//                    ids.add(m.getId());
//                }
//            }
//            if (searchList != null) {
//                for (MessageObject m : searchList.links) {
//                    if (m != null && !selected.contains(m.getId()) && TextUtils.equals(getLink(m), link)) {
//                        messageObjects.add(m);
//                        ids.add(m.getId());
//                    }
//                }
//            }
//        }
        new AlertDialog.Builder(getContext(), getResourceProvider())
            .setTitle(formatPluralString("DeleteOptionsTitle", ids.size()))
            .setMessage(getString(ids.size() == 1 ? "AreYouSureUnsaveSingleMessage" : "AreYouSureUnsaveFewMessages"))
            .setPositiveButton(getString(R.string.Delete), (di, w) -> {
                final long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
                MessagesController.getInstance(currentAccount).deleteMessages(new ArrayList<>(ids), null, null, selfId, 0, true, 0);
                list.delete(new ArrayList<>(ids));
                if (searchList != null) {
                    searchList.delete(new ArrayList<>(ids));
                }
                selected.clear();
                actionBar.hideActionMode();
                listView.adapter.update(true);
            })
            .setNegativeButton(getString(R.string.Cancel), null)
            .makeRed(AlertDialog.BUTTON_POSITIVE)
            .show();
    }

    public void gotoMessage() {
        if (selected.size() != 1) return;
        final long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
        final int id = selected.iterator().next();
        finishFragment();
        if (closeToTabs != null) {
            closeToTabs.run();
        }
        AndroidUtilities.runOnUIThread(() -> {
            BaseFragment fragment = LaunchActivity.getSafeLastFragment();
            if (fragment != null) {
                fragment.presentFragment(ChatActivity.of(selfId, id));
            }
        }, 80);
    }

    public BookmarksFragment(Runnable closeToTabs, Utilities.Callback<String> whenClicked) {
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
                } else if (id == R.id.menu_delete) {
                    deleteSelectedMessages();
                } else if (id == R.id.menu_link) {
                    gotoMessage();
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

        gotoItem = actionMode.addItemWithWidth(R.id.menu_link, R.drawable.msg_message, AndroidUtilities.dp(54), getString(R.string.AccDescrGoToMessage));
        actionMode.addItemWithWidth(R.id.menu_delete, R.drawable.msg_delete, AndroidUtilities.dp(54), getString(R.string.Delete));

        searchItem = actionBar.createMenu().addItem(0, R.drawable.ic_ab_search, getResourceProvider()).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {}

            @Override
            public void onSearchCollapse() {
                query = null;
                AndroidUtilities.cancelRunOnUIThread(applySearch);
                if (searchList != null) {
                    searchList.detach();
                    searchList = null;
                }
                if (listView != null) {
                    listView.adapter.update(true);
                    listView.layoutManager.scrollToPositionWithOffset(0, 0);
                }
            }

            @Override
            public void onTextChanged(EditText editText) {
                final boolean hadQuery = !TextUtils.isEmpty(query);
                final String query = editText.getText().toString();
                if (!TextUtils.equals(BookmarksFragment.this.query, query)) {
                    BookmarksFragment.this.query = query;
                    if (searchList != null) {
                        searchList.detach();
                    }
                    searchList = new AddressBarList.BookmarksList(currentAccount, query, BookmarksFragment.this::updateWithOffset);
                    searchList.attach();
                    scheduleSearch();
                }
                if (listView != null) {
                    listView.adapter.update(true);
                    if (hadQuery != !TextUtils.isEmpty(query)) {
                        listView.layoutManager.scrollToPositionWithOffset(0, 0);
                    }
                }
            }

            private void scheduleSearch() {
                AndroidUtilities.cancelRunOnUIThread(applySearch);
                AndroidUtilities.runOnUIThread(applySearch, 500);
            }

            private Runnable applySearch = () -> {
                if (searchList != null) {
                    searchList.load();
                }
            };
        });
        searchItem.setSearchFieldHint(getString(R.string.Search));
        searchItem.setContentDescription(getString(R.string.Search));
        EditTextBoldCursor editText = searchItem.getSearchField();
        editText.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setHintTextColor(getThemedColor(Theme.key_player_time));
        editText.setCursorColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));

        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (!listView.canScrollVertically(1)) {
                    if (TextUtils.isEmpty(query)) {
                        list.load();
                    } else if (searchList != null) {
                        searchList.load();
                    }
                }
                if (listView.scrollingByUser) {
                    AndroidUtilities.hideKeyboard(fragmentView);
                }
            }
        });

        StickerEmptyView emptyView = new StickerEmptyView(context, null, StickerEmptyView.STICKER_TYPE_SEARCH);
        emptyView.title.setText(getString(R.string.WebNoBookmarks));
        emptyView.subtitle.setVisibility(View.GONE);
        emptyView.showProgress(false, false);
        emptyView.setAnimateLayoutChange(true);
        ((FrameLayout) fragmentView).addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setEmptyView(emptyView);

        return fragmentView;
    }

    @Override
    public boolean onFragmentCreate() {
        list.attach();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        list.detach();
    }

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.WebBookmarks);
    }

    private final HashSet<String> addedUrls = new HashSet<>();
    private int lastId;

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        addedUrls.clear();
        if (TextUtils.isEmpty(query)) {
            for (MessageObject msg : list.links) {
                final String url = getLink(msg);
                if (TextUtils.isEmpty(url)) continue;
                if (url.startsWith("#") || url.startsWith("$") || url.startsWith("@")) continue;
//                if (addedUrls.contains(url)) continue;
                addedUrls.add(url);
                items.add(AddressBarList.BookmarkView.Factory.as(msg, false).setChecked(isSelected(msg)));
            }
            if (!list.endReached) {
                items.add(UItem.asFlicker(items.size(), FlickerLoadingView.BROWSER_BOOKMARK));
                items.add(UItem.asFlicker(items.size(), FlickerLoadingView.BROWSER_BOOKMARK));
                items.add(UItem.asFlicker(items.size(), FlickerLoadingView.BROWSER_BOOKMARK));
            }
        } else {
            for (MessageObject msg : list.links) {
                final String url = getLink(msg);
                if (TextUtils.isEmpty(url)) continue;
                if (url.startsWith("#") || url.startsWith("$") || url.startsWith("@")) continue;
//                if (addedUrls.contains(url)) continue;
                addedUrls.add(url);
                final String domain = AndroidUtilities.getHostAuthority(url, true);
                final WebMetadataCache.WebMetadata meta = WebMetadataCache.getInstance().get(domain);
                final TLRPC.WebPage webpage = msg != null && msg.messageOwner != null && msg.messageOwner.media != null ? msg.messageOwner.media.webpage : null;
                final String sitename = webpage != null && !TextUtils.isEmpty(webpage.site_name) ? webpage.site_name : (meta == null || TextUtils.isEmpty(meta.sitename) ? null : meta.sitename);
                final String title = webpage != null && !TextUtils.isEmpty(webpage.title) ? webpage.title : null;
                if (!matches(domain, query) && !matches(sitename, query) && !matches(title, query)) {
                    continue;
                }
                items.add(AddressBarList.BookmarkView.Factory.as(msg, false, query).setChecked(isSelected(msg)));
            }
            for (MessageObject msg : searchList.links) {
                final String url = getLink(msg);
                if (TextUtils.isEmpty(url)) continue;
                if (url.startsWith("#") || url.startsWith("$") || url.startsWith("@")) continue;
//                if (addedUrls.contains(url)) continue;
                addedUrls.add(url);
                items.add(AddressBarList.BookmarkView.Factory.as(msg, false, query).setChecked(isSelected(msg)));
            }
            if (!searchList.endReached) {
                items.add(UItem.asFlicker(items.size(), FlickerLoadingView.BROWSER_BOOKMARK));
                items.add(UItem.asFlicker(items.size(), FlickerLoadingView.BROWSER_BOOKMARK));
                items.add(UItem.asFlicker(items.size(), FlickerLoadingView.BROWSER_BOOKMARK));
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
                whenClicked.run(getLink((MessageObject) item.object2));
            }
        }
    }

    public void clickSelect(UItem item, View view) {
        AddressBarList.BookmarkView cell = (AddressBarList.BookmarkView) view;
        MessageObject msg = (MessageObject) item.object2;
        if (isSelected(msg)) {
            setSelected(msg, false);
            cell.setChecked(false);
        } else {
            setSelected(msg, true);
            cell.setChecked(true);
        }
        selectedCount.setNumber(selected.size(), true);
        if (selected.isEmpty()) {
            actionBar.hideActionMode();
        } else {
            actionBar.showActionMode();
        }
        AndroidUtilities.updateViewShow(gotoItem, selected.size() == 1, true, true);
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.instanceOf(AddressBarList.BookmarkView.Factory.class)) {
            clickSelect(item, view);
            return true;
        }
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
