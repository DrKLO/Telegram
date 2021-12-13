package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.MenuDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Adapters.DialogsSearchAdapter;
import org.telegram.ui.Adapters.FiltersView;
import org.telegram.ui.Cells.ContextLinkCell;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.SharedAudioCell;
import org.telegram.ui.Cells.SharedDocumentCell;
import org.telegram.ui.Cells.SharedLinkCell;
import org.telegram.ui.Cells.SharedPhotoVideoCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.FilteredSearchView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class SearchViewPager extends ViewPagerFixed implements FilteredSearchView.UiCallback {

    public FrameLayout searchContainer;
    public RecyclerListView searchListView;
    public StickerEmptyView emptyView;
    public DialogsSearchAdapter dialogsSearchAdapter;
    private LinearLayoutManager searchLayoutManager;
    private RecyclerItemsEnterAnimator itemsEnterAnimator;
    private boolean attached;

    private NumberTextView selectedMessagesCountTextView;
    private boolean isActionModeShowed;
    private HashMap<FilteredSearchView.MessageHashId, MessageObject> selectedFiles = new HashMap<>();

    private ArrayList<FiltersView.MediaFilterData> currentSearchFilters = new ArrayList<>();

    private final static String actionModeTag = "search_view_pager";

    public final static int gotoItemId = 200;
    public final static int forwardItemId = 201;

    private ActionBarMenuItem gotoItem;
    private ActionBarMenuItem forwardItem;

    int currentAccount = UserConfig.selectedAccount;

    private boolean lastSearchScrolledToTop;
    BaseFragment parent;

    String lastSearchString;
    private FilteredSearchView.Delegate filteredSearchViewDelegate;
    private FilteredSearchView noMediaFiltersSearchView;
    private int keyboardSize;

    private boolean showOnlyDialogsAdapter;

    ChatPreviewDelegate chatPreviewDelegate;

    private final int folderId;
    int animateFromCount = 0;

    public SearchViewPager(Context context, BaseFragment fragment, int type, int initialDialogsType, int folderId, ChatPreviewDelegate chatPreviewDelegate) {
        super(context);
        this.folderId = folderId;
        parent = fragment;
        this.chatPreviewDelegate = chatPreviewDelegate;
        dialogsSearchAdapter = new DialogsSearchAdapter(context, type, initialDialogsType) {
            @Override
            public void notifyDataSetChanged() {
                int itemCount = getCurrentItemCount();
                super.notifyDataSetChanged();
                if (!lastSearchScrolledToTop && searchListView != null) {
                    searchListView.scrollToPosition(0);
                    lastSearchScrolledToTop = true;
                }
                if (getItemCount() == 0 && itemCount != 0 && !isSearching()) {
                    emptyView.showProgress(false, false);
                }
            }
        };

        searchListView = new RecyclerListView(context);
        searchListView.setPivotY(0);
        searchListView.setAdapter(dialogsSearchAdapter);
        searchListView.setVerticalScrollBarEnabled(true);
        searchListView.setInstantClick(true);
        searchListView.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
        searchListView.setLayoutManager(searchLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        searchListView.setAnimateEmptyView(true, 0);
        searchListView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    AndroidUtilities.hideKeyboard(fragment.getParentActivity().getCurrentFocus());
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                int firstVisibleItem = searchLayoutManager.findFirstVisibleItemPosition();
                int visibleItemCount = Math.abs(searchLayoutManager.findLastVisibleItemPosition() - firstVisibleItem) + 1;
                int totalItemCount = recyclerView.getAdapter().getItemCount();
                if (visibleItemCount > 0 && searchLayoutManager.findLastVisibleItemPosition() == totalItemCount - 1 && !dialogsSearchAdapter.isMessagesSearchEndReached()) {
                    dialogsSearchAdapter.loadMoreSearchMessages();
                }
            }
        });

        noMediaFiltersSearchView = new FilteredSearchView(parent);
        noMediaFiltersSearchView.setUiCallback(SearchViewPager.this);
        noMediaFiltersSearchView.setVisibility(View.GONE);
        noMediaFiltersSearchView.setChatPreviewDelegate(chatPreviewDelegate);

        searchContainer = new FrameLayout(context);


        FlickerLoadingView loadingView = new FlickerLoadingView(context);
        loadingView.setViewType(1);
        emptyView = new StickerEmptyView(context, loadingView, StickerEmptyView.STICKER_TYPE_SEARCH) {
            @Override
            public void setVisibility(int visibility) {
                if (noMediaFiltersSearchView.getTag() != null) {
                    super.setVisibility(View.GONE);
                    return;
                }
                super.setVisibility(visibility);
            }
        };
        emptyView.title.setText(LocaleController.getString("NoResult", R.string.NoResult));
        emptyView.subtitle.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
        emptyView.addView(loadingView, 0);
        emptyView.showProgress(true, false);

        searchContainer.addView(emptyView);
        searchContainer.addView(searchListView);
        searchContainer.addView(noMediaFiltersSearchView);
        searchListView.setEmptyView(emptyView);

        itemsEnterAnimator = new RecyclerItemsEnterAnimator(searchListView, true);

        setAdapter(new ViewPagerFixed.Adapter() {

            @Override
            public String getItemTitle(int position) {
                if (position == 0) {
                    return LocaleController.getString("SearchAllChatsShort", R.string.SearchAllChatsShort);
                } else {
                    return FiltersView.filters[position - 1].title;
                }
            }

            @Override
            public int getItemCount() {
                return showOnlyDialogsAdapter ? 1 : (FiltersView.filters.length + 1);
            }

            @Override
            public View createView(int viewType) {
                if (viewType == 1) {
                    return searchContainer;
                } else {
                    FilteredSearchView filteredSearchView = new FilteredSearchView(parent);
                    filteredSearchView.setChatPreviewDelegate(chatPreviewDelegate);
                    filteredSearchView.setUiCallback(SearchViewPager.this);
                    return filteredSearchView;
                }
            }

            @Override
            public int getItemViewType(int position) {
                if (position == 0) {
                    return 1;
                }
                return 2 + position;
            }

            @Override
            public void bindView(View view, int position, int viewType) {
                search(view, position, lastSearchString, true);
            }
        });
    }

    public void onTextChanged(String text) {
        lastSearchString = text;
        View view = getCurrentView();
        boolean reset = false;
        if (!attached) {
            reset = true;
        }
        search(view, getCurrentPosition(), text, reset);
    }

    private void search(View view, int position, String query, boolean reset) {
        long dialogId = 0;
        long minDate = 0;
        long maxDate = 0;
        boolean includeFolder = false;
        for (int i = 0; i < currentSearchFilters.size(); i++) {
            FiltersView.MediaFilterData data = currentSearchFilters.get(i);
            if (data.filterType == FiltersView.FILTER_TYPE_CHAT) {
                if (data.chat instanceof TLRPC.User) {
                    dialogId = ((TLRPC.User) data.chat).id;
                } else if (data.chat instanceof TLRPC.Chat) {
                    dialogId = -((TLRPC.Chat) data.chat).id;
                }
            } else if (data.filterType == FiltersView.FILTER_TYPE_DATE) {
                minDate = data.dateData.minDate;
                maxDate = data.dateData.maxDate;
            } else if (data.filterType == FiltersView.FILTER_TYPE_ARCHIVE) {
                includeFolder = true;
            }
        }

        if (view == searchContainer) {
            if (dialogId == 0 && minDate == 0 && maxDate == 0) {
                lastSearchScrolledToTop = false;
                dialogsSearchAdapter.searchDialogs(query, includeFolder ? 1 : 0);
                dialogsSearchAdapter.setFiltersDelegate(filteredSearchViewDelegate, false);
                noMediaFiltersSearchView.animate().setListener(null).cancel();
                noMediaFiltersSearchView.setDelegate(null, false);
                if (reset) {
                    emptyView.showProgress(!dialogsSearchAdapter.isSearching(), false);
                    emptyView.showProgress(dialogsSearchAdapter.isSearching(), false);
                } else {
                    if (!dialogsSearchAdapter.hasRecentSearch()) {
                        emptyView.showProgress(dialogsSearchAdapter.isSearching(), true);
                    }
                }
                if (reset) {
                    noMediaFiltersSearchView.setVisibility(View.GONE);
                } else {
                    if (noMediaFiltersSearchView.getVisibility() != View.GONE) {
                        noMediaFiltersSearchView.animate().alpha(0).setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                noMediaFiltersSearchView.setVisibility(View.GONE);
                            }
                        }).setDuration(150).start();
                    }
                }
                noMediaFiltersSearchView.setTag(null);
            } else {
                noMediaFiltersSearchView.setTag(1);
                noMediaFiltersSearchView.setDelegate(filteredSearchViewDelegate, false);
                noMediaFiltersSearchView.animate().setListener(null).cancel();
                if (reset) {
                    noMediaFiltersSearchView.setVisibility(View.VISIBLE);
                    noMediaFiltersSearchView.setAlpha(1f);
                } else {
                    if (noMediaFiltersSearchView.getVisibility() != View.VISIBLE) {
                        noMediaFiltersSearchView.setVisibility(View.VISIBLE);
                        noMediaFiltersSearchView.setAlpha(0f);
                        reset = true;
                    }
                    noMediaFiltersSearchView.animate().alpha(1f).setDuration(150).start();
                }
                noMediaFiltersSearchView.search(dialogId, minDate, maxDate, null, includeFolder, query, reset);
                emptyView.setVisibility(View.GONE);
            }
            emptyView.setKeyboardHeight(keyboardSize, false);
            noMediaFiltersSearchView.setKeyboardHeight(keyboardSize, false);
        } else {
            ((FilteredSearchView)view).setKeyboardHeight(keyboardSize, false);
            ((FilteredSearchView)view).search(dialogId, minDate, maxDate, FiltersView.filters[position - 1], includeFolder, query, reset);
        }
    }

    public void onResume() {
        if (dialogsSearchAdapter != null) {
            dialogsSearchAdapter.notifyDataSetChanged();
        }
    }

    public void removeSearchFilter(FiltersView.MediaFilterData filterData) {
        currentSearchFilters.remove(filterData);
    }

    public ArrayList<FiltersView.MediaFilterData> getCurrentSearchFilters() {
        return currentSearchFilters;
    }

    public void clear() {
        currentSearchFilters.clear();
    }

    public void setFilteredSearchViewDelegate(FilteredSearchView.Delegate filteredSearchViewDelegate) {
        this.filteredSearchViewDelegate = filteredSearchViewDelegate;
    }

    private void showActionMode(boolean show) {
        if (isActionModeShowed == show) {
            return;
        }
        if (show && parent.getActionBar().isActionModeShowed()) {
            return;
        }
        if (show && !parent.getActionBar().actionModeIsExist(actionModeTag)) {
            ActionBarMenu actionMode = parent.getActionBar().createActionMode(true, actionModeTag);

            selectedMessagesCountTextView = new NumberTextView(actionMode.getContext());
            selectedMessagesCountTextView.setTextSize(18);
            selectedMessagesCountTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            selectedMessagesCountTextView.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
            actionMode.addView(selectedMessagesCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 72, 0, 0, 0));
            selectedMessagesCountTextView.setOnTouchListener((v, event) -> true);

            gotoItem = actionMode.addItemWithWidth(gotoItemId, R.drawable.msg_message, AndroidUtilities.dp(54), LocaleController.getString("AccDescrGoToMessage", R.string.AccDescrGoToMessage));
            forwardItem = actionMode.addItemWithWidth(forwardItemId, R.drawable.msg_forward, AndroidUtilities.dp(54), LocaleController.getString("Forward", R.string.Forward));
        }
        if (parent.getActionBar().getBackButton().getDrawable() instanceof MenuDrawable) {
            parent.getActionBar().setBackButtonDrawable(new BackDrawable(false));
        }
        isActionModeShowed = show;
        if (show) {
            AndroidUtilities.hideKeyboard(parent.getParentActivity().getCurrentFocus());
            parent.getActionBar().showActionMode();
            selectedMessagesCountTextView.setNumber(selectedFiles.size(), false);
            gotoItem.setVisibility(View.VISIBLE);
            forwardItem.setVisibility(View.VISIBLE);
        } else {
            parent.getActionBar().hideActionMode();
            selectedFiles.clear();
            for (int i = 0; i < getChildCount(); i++) {
                if (getChildAt(i) instanceof FilteredSearchView) {
                    ((FilteredSearchView)getChildAt(i)).update();
                }
            }
            if (noMediaFiltersSearchView != null) {
                noMediaFiltersSearchView.update();
            }
            int n = viewsByType.size();
            for (int i = 0; i < n; i++) {
                View v = viewsByType.valueAt(i);
                if (v instanceof FilteredSearchView) {
                    ((FilteredSearchView) v).update();
                }
            }
        }
    }

    public void onActionBarItemClick(int id) {
        if (id == gotoItemId) {
            if (selectedFiles.size() != 1) {
                return;
            }
            MessageObject messageObject = selectedFiles.values().iterator().next();
            goToMessage(messageObject);
        } else if (id == forwardItemId) {
            Bundle args = new Bundle();
            args.putBoolean("onlySelect", true);
            args.putInt("dialogsType", 3);
            DialogsActivity fragment = new DialogsActivity(args);
            fragment.setDelegate((fragment1, dids, message, param) -> {
                ArrayList<MessageObject> fmessages = new ArrayList<>();
                Iterator<FilteredSearchView.MessageHashId> idIterator = selectedFiles.keySet().iterator();
                while (idIterator.hasNext()) {
                    FilteredSearchView.MessageHashId hashId = idIterator.next();
                    fmessages.add(selectedFiles.get(hashId));
                }
                selectedFiles.clear();

                showActionMode(false);

                if (dids.size() > 1 || dids.get(0) == AccountInstance.getInstance(currentAccount).getUserConfig().getClientUserId() || message != null) {
                    for (int a = 0; a < dids.size(); a++) {
                        long did = dids.get(a);
                        if (message != null) {
                            AccountInstance.getInstance(currentAccount).getSendMessagesHelper().sendMessage(message.toString(), did, null, null, null, true, null, null, null, true, 0, null);
                        }
                        AccountInstance.getInstance(currentAccount).getSendMessagesHelper().sendMessage(fmessages, did, false,false, true, 0);
                    }
                    fragment1.finishFragment();
                } else {
                    long did = dids.get(0);
                    Bundle args1 = new Bundle();
                    args1.putBoolean("scrollToTopOnResume", true);
                    if (DialogObject.isEncryptedDialog(did)) {
                        args1.putInt("enc_id", DialogObject.getEncryptedChatId(did));
                    } else {
                        if (DialogObject.isUserDialog(did)) {
                            args1.putLong("user_id", did);
                        } else {
                            args1.putLong("chat_id", -did);
                        }
                        if (!AccountInstance.getInstance(currentAccount).getMessagesController().checkCanOpenChat(args1, fragment1)) {
                            return;
                        }
                    }
                    ChatActivity chatActivity = new ChatActivity(args1);
                    fragment1.presentFragment(chatActivity, true);
                    chatActivity.showFieldPanelForForward(true, fmessages);
                }
            });
            parent.presentFragment(fragment);
        }
    }

    public void goToMessage(MessageObject messageObject) {
        Bundle args = new Bundle();
        long dialogId = messageObject.getDialogId();
        if (DialogObject.isEncryptedDialog(dialogId)) {
            args.putInt("enc_id", DialogObject.getEncryptedChatId(dialogId));
        } else if (DialogObject.isUserDialog(dialogId)) {
            args.putLong("user_id", dialogId);
        } else {
            TLRPC.Chat chat = AccountInstance.getInstance(currentAccount).getMessagesController().getChat(-dialogId);
            if (chat != null && chat.migrated_to != null) {
                args.putLong("migrated_to", dialogId);
                dialogId = -chat.migrated_to.channel_id;
            }
            args.putLong("chat_id", -dialogId);
        }
        args.putInt("message_id", messageObject.getId());
        parent.presentFragment(new ChatActivity(args));
        showActionMode(false);
    }

    @Override
    public int getFolderId() {
        return folderId;
    }

    @Override
    public boolean actionModeShowing() {
        return isActionModeShowed;
    }

    public void hideActionMode() {
        showActionMode(false);
    }

    public void toggleItemSelection(MessageObject message, View view, int a) {
        FilteredSearchView.MessageHashId hashId = new FilteredSearchView.MessageHashId(message.getId(), message.getDialogId());
        if (selectedFiles.containsKey(hashId)) {
            selectedFiles.remove(hashId);
        } else {
            if (selectedFiles.size() >= 100) {
                return;
            }
            selectedFiles.put(hashId, message);
        }
        if (selectedFiles.size() == 0) {
            showActionMode(false);
        } else {
            selectedMessagesCountTextView.setNumber(selectedFiles.size(), true);
            if (gotoItem != null) {
                gotoItem.setVisibility(selectedFiles.size() == 1 ? View.VISIBLE : View.GONE);
            }
        }
        if (view instanceof SharedDocumentCell) {
            ((SharedDocumentCell) view).setChecked(selectedFiles.containsKey(hashId), true);
        } else if (view instanceof SharedPhotoVideoCell) {
            ((SharedPhotoVideoCell) view).setChecked(a, selectedFiles.containsKey(hashId), true);
        } else if (view instanceof SharedLinkCell) {
            ((SharedLinkCell) view).setChecked(selectedFiles.containsKey(hashId), true);
        } else if (view instanceof SharedAudioCell) {
            ((SharedAudioCell) view).setChecked(selectedFiles.containsKey(hashId), true);
        } else if (view instanceof ContextLinkCell) {
            ((ContextLinkCell) view).setChecked(selectedFiles.containsKey(hashId), true);
        } else if (view instanceof DialogCell) {
            ((DialogCell) view).setChecked(selectedFiles.containsKey(hashId), true);
        }
    }

    @Override
    public boolean isSelected(FilteredSearchView.MessageHashId messageHashId) {
        return selectedFiles.containsKey(messageHashId);
    }

    @Override
    public void showActionMode() {
        showActionMode(true);
    }

    @Override
    protected void onItemSelected(View currentPage, View oldPage, int position, int oldPosition) {
        if (position == 0) {
            if (noMediaFiltersSearchView.getVisibility() == View.VISIBLE) {
                noMediaFiltersSearchView.setDelegate(filteredSearchViewDelegate, false);
                dialogsSearchAdapter.setFiltersDelegate(null, false);
            } else {
                noMediaFiltersSearchView.setDelegate(null, false);
                dialogsSearchAdapter.setFiltersDelegate(filteredSearchViewDelegate, true);
            }
        } else if (currentPage instanceof FilteredSearchView) {
            boolean update = false;
            if (oldPosition == 0 && noMediaFiltersSearchView.getVisibility() != View.VISIBLE) {
                update = true;
            }
            ((FilteredSearchView) currentPage).setDelegate(filteredSearchViewDelegate, update);
        }
        if (oldPage instanceof FilteredSearchView) {
            ((FilteredSearchView) oldPage).setDelegate(null, false);
        } else {
            dialogsSearchAdapter.setFiltersDelegate(null, false);
            noMediaFiltersSearchView.setDelegate(null, false);
        }
    }

    public void getThemeDescriptors(ArrayList<ThemeDescription> arrayList) {
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) instanceof FilteredSearchView) {
                arrayList.addAll(((FilteredSearchView) getChildAt(i)).getThemeDescriptions());
            }
        }

        int n = viewsByType.size();
        for (int i = 0; i < n; i++) {
            View v = viewsByType.valueAt(i);
            if (v instanceof FilteredSearchView) {
                arrayList.addAll(((FilteredSearchView) v).getThemeDescriptions());
            }
        }
        if (noMediaFiltersSearchView != null) {
            arrayList.addAll(noMediaFiltersSearchView.getThemeDescriptions());
        }

        arrayList.add(new ThemeDescription(emptyView.title, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(emptyView.subtitle, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText));


    }

    public void updateColors() {
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) instanceof FilteredSearchView) {
                RecyclerListView recyclerListView = ((FilteredSearchView) getChildAt(i)).recyclerListView;
                int count = recyclerListView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = recyclerListView.getChildAt(a);
                    if (child instanceof DialogCell) {
                        ((DialogCell) child).update(0);
                    }
                }
            }
        }
        int n = viewsByType.size();
        for (int i = 0; i < n; i++) {
            View v = viewsByType.valueAt(i);
            if (v instanceof FilteredSearchView) {
                RecyclerListView recyclerListView = ((FilteredSearchView) v).recyclerListView;
                int count = recyclerListView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = recyclerListView.getChildAt(a);
                    if (child instanceof DialogCell) {
                        ((DialogCell) child).update(0);
                    }
                }
            }
        }
        if (noMediaFiltersSearchView != null) {
            RecyclerListView recyclerListView = noMediaFiltersSearchView.recyclerListView;
            int count = recyclerListView.getChildCount();
            for (int a = 0; a < count; a++) {
                View child = recyclerListView.getChildAt(a);
                if (child instanceof DialogCell) {
                    ((DialogCell) child).update(0);
                }
            }
        }
    }

    public void reset() {
        setPosition(0);
        if (dialogsSearchAdapter.getItemCount() > 0) {
            searchLayoutManager.scrollToPositionWithOffset(0, 0);
        }
        viewsByType.clear();
    }

    public void setPosition(int position) {
        super.setPosition(position);
        viewsByType.clear();
        if (tabsView != null) {
            tabsView.selectTabWithId(position, 1f);
        }
        invalidate();
    }

    public void setKeyboardHeight(int keyboardSize) {
        this.keyboardSize = keyboardSize;
        boolean animated = getVisibility() == View.VISIBLE && getAlpha() > 0;
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) instanceof FilteredSearchView) {
                ((FilteredSearchView) getChildAt(i)).setKeyboardHeight(keyboardSize, animated);
            } else if (getChildAt(i) == searchContainer) {
                emptyView.setKeyboardHeight(keyboardSize, animated);
                noMediaFiltersSearchView.setKeyboardHeight(keyboardSize, animated);
            }
        }
    }

    public void showOnlyDialogsAdapter(boolean showOnlyDialogsAdapter) {
        this.showOnlyDialogsAdapter = showOnlyDialogsAdapter;
    }

    public void messagesDeleted(long channelId, ArrayList<Integer> markAsDeletedMessages) {
        int n = viewsByType.size();
        for (int i = 0; i < n; i++) {
            View v = viewsByType.valueAt(i);
            if (v instanceof FilteredSearchView) {
               ((FilteredSearchView) v).messagesDeleted(channelId, markAsDeletedMessages);
            }
        }

        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) instanceof FilteredSearchView) {
                ((FilteredSearchView) getChildAt(i)).messagesDeleted(channelId, markAsDeletedMessages);
            }
        }
        noMediaFiltersSearchView.messagesDeleted(channelId, markAsDeletedMessages);
        if (!selectedFiles.isEmpty()) {
            ArrayList<FilteredSearchView.MessageHashId> toRemove = null;
            Iterator<FilteredSearchView.MessageHashId> iterator = selectedFiles.keySet().iterator();
            while (iterator.hasNext()) {
                FilteredSearchView.MessageHashId hashId = iterator.next();
                MessageObject messageObject = selectedFiles.get(hashId);
                long dialogId = messageObject.getDialogId();
                int currentChannelId = dialogId < 0 && ChatObject.isChannel((int) -dialogId, currentAccount) ? (int) -dialogId : 0;
                if (currentChannelId == channelId) {
                    for (int i = 0; i < markAsDeletedMessages.size(); i++) {
                        if (messageObject.getId() == markAsDeletedMessages.get(i)) {
                            toRemove = new ArrayList<>();
                            toRemove.add(hashId);
                        }
                    }
                }
                if (toRemove != null) {
                    for (int a = 0, N = toRemove.size(); a < N; a++) {
                        selectedFiles.remove(toRemove.get(a));
                    }
                    selectedMessagesCountTextView.setNumber(selectedFiles.size(), true);
                    if (gotoItem != null) {
                        gotoItem.setVisibility(selectedFiles.size() == 1 ? View.VISIBLE : View.GONE);
                    }
                }
            }
        }
    }

    public void runResultsEnterAnimation() {
        itemsEnterAnimator.showItemsAnimated(animateFromCount > 0 ? animateFromCount + 1 : 0);
        animateFromCount = dialogsSearchAdapter.getItemCount();
    }


    public TabsView getTabsView() {
        return tabsView;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attached = false;
    }

    public void cancelEnterAnimation() {
        itemsEnterAnimator.cancel();
        searchListView.invalidate();
        animateFromCount = 0;
    }

    public interface ChatPreviewDelegate {
        void startChatPreview(DialogCell cell);
        void move(float dy);
        void finish();
    }
}
