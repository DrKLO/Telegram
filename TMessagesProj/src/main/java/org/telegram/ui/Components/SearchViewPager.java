package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.MenuDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Adapters.DialogsSearchAdapter;
import org.telegram.ui.Adapters.FiltersView;
import org.telegram.ui.Cells.ContextLinkCell;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.HashtagSearchCell;
import org.telegram.ui.Cells.ProfileSearchCell;
import org.telegram.ui.Cells.SharedAudioCell;
import org.telegram.ui.Cells.SharedDocumentCell;
import org.telegram.ui.Cells.SharedLinkCell;
import org.telegram.ui.Cells.SharedPhotoVideoCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.FilteredSearchView;
import org.telegram.ui.PremiumPreviewFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

public class SearchViewPager extends ViewPagerFixed implements FilteredSearchView.UiCallback {

    protected final ViewPagerAdapter viewPagerAdapter;
    public FrameLayout searchContainer;
    public RecyclerListView searchListView;
    public StickerEmptyView emptyView;
    private DefaultItemAnimator itemAnimator;
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
    public final static int deleteItemId = 202;
    public final static int speedItemId = 203;

    private ActionBarMenuItem speedItem;
    private ActionBarMenuItem gotoItem;
    private ActionBarMenuItem forwardItem;
    private ActionBarMenuItem deleteItem;

    private ActionBarMenu actionMode;

    private SearchDownloadsContainer downloadsContainer;

    int currentAccount = UserConfig.selectedAccount;

    private boolean lastSearchScrolledToTop;
    BaseFragment parent;

    String lastSearchString;
    private FilteredSearchView.Delegate filteredSearchViewDelegate;
    private FilteredSearchView noMediaFiltersSearchView;
    private int keyboardSize;

    private boolean showOnlyDialogsAdapter;
    protected boolean includeDownloads() {
        return true;
    }

    ChatPreviewDelegate chatPreviewDelegate;
    SizeNotifierFrameLayout fragmentView;

    private final int folderId;
    int animateFromCount = 0;

    public SearchViewPager(Context context, DialogsActivity fragment, int type, int initialDialogsType, int folderId, ChatPreviewDelegate chatPreviewDelegate) {
        super(context);
        this.folderId = folderId;
        parent = fragment;
        this.chatPreviewDelegate = chatPreviewDelegate;

        itemAnimator = new DefaultItemAnimator();
        itemAnimator.setAddDuration(150);
        itemAnimator.setMoveDuration(350);
        itemAnimator.setChangeDuration(0);
        itemAnimator.setRemoveDuration(0);
        itemAnimator.setMoveInterpolator(new OvershootInterpolator(1.1f));
        itemAnimator.setTranslationInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);

        dialogsSearchAdapter = new DialogsSearchAdapter(context, fragment, type, initialDialogsType, itemAnimator, fragment.getAllowGlobalSearch(), null) {
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
        if (initialDialogsType == DialogsActivity.DIALOGS_TYPE_BOT_REQUEST_PEER) {
            ArrayList<TLRPC.Dialog> dialogs = fragment.getDialogsArray(currentAccount, initialDialogsType, folderId, true);
            ArrayList<Long> dialogIds = new ArrayList<>();
            for (int i = 0; i < dialogs.size(); ++i) {
                dialogIds.add(dialogs.get(i).id);
            }
            dialogsSearchAdapter.setFilterDialogIds(dialogIds);
        }
        fragmentView = (SizeNotifierFrameLayout) fragment.getFragmentView();

        searchListView = new BlurredRecyclerView(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (dialogsSearchAdapter != null && itemAnimator != null && searchLayoutManager != null && dialogsSearchAdapter.showMoreAnimation) {
                    canvas.save();
                    invalidate();
                    final int lastItemIndex = dialogsSearchAdapter.getItemCount() - 1;
                    for (int i = 0; i < getChildCount(); ++i) {
                        View child = getChildAt(i);
                        if (getChildAdapterPosition(child) == lastItemIndex) {
                            canvas.clipRect(0, 0, getWidth(), child.getBottom() + child.getTranslationY());
                            break;
                        }
                    }
                }
                super.dispatchDraw(canvas);
                if (dialogsSearchAdapter != null && itemAnimator != null && searchLayoutManager != null && dialogsSearchAdapter.showMoreAnimation) {
                    canvas.restore();
                }
                if (dialogsSearchAdapter != null && dialogsSearchAdapter.showMoreHeader != null) {
                    canvas.save();
                    canvas.translate(dialogsSearchAdapter.showMoreHeader.getLeft(), dialogsSearchAdapter.showMoreHeader.getTop() + dialogsSearchAdapter.showMoreHeader.getTranslationY());
                    dialogsSearchAdapter.showMoreHeader.draw(canvas);
                    canvas.restore();
                }
            }
        };
        searchListView.setItemAnimator(itemAnimator);
        searchListView.setPivotY(0);
        searchListView.setAdapter(dialogsSearchAdapter);
        searchListView.setVerticalScrollBarEnabled(true);
        searchListView.setInstantClick(true);
        searchListView.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
        searchListView.setLayoutManager(searchLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        searchListView.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA);
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
                int lastVisibleItem = searchLayoutManager.findLastVisibleItemPosition();
                int visibleItemCount = Math.abs(searchLayoutManager.findLastVisibleItemPosition() - firstVisibleItem) + 1;
                int totalItemCount = recyclerView.getAdapter().getItemCount();
                if (visibleItemCount > 0 && !dialogsSearchAdapter.isMessagesSearchEndReached() && (
                    lastVisibleItem == totalItemCount - 1 ||
                    dialogsSearchAdapter.delegate != null && dialogsSearchAdapter.delegate.getSearchForumDialogId() != 0 && dialogsSearchAdapter.localMessagesLoadingRow >= 0 && firstVisibleItem <= dialogsSearchAdapter.localMessagesLoadingRow && lastVisibleItem >= dialogsSearchAdapter.localMessagesLoadingRow
                )) {
                    dialogsSearchAdapter.loadMoreSearchMessages();
                }
                fragmentView.invalidateBlur();
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
        searchListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                fragmentView.invalidateBlur();
            }
        });

        itemsEnterAnimator = new RecyclerItemsEnterAnimator(searchListView, true);

        setAdapter(viewPagerAdapter = new ViewPagerAdapter());
    }

    public ActionBarMenu getActionMode() {
        return actionMode;
    }

    public ActionBarMenuItem getSpeedItem() {
        return speedItem;
    }

    public void onTextChanged(String text) {
        View view = getCurrentView();
        boolean reset = false;
        if (!attached) {
            reset = true;
        }
        if (TextUtils.isEmpty(lastSearchString)) {
            reset = true;
        }
        lastSearchString = text;
        search(view, getCurrentPosition(), text, reset);
    }

    protected long getDialogId(String query) {
        return 0;
    }

    public void updateTabs() {
        viewPagerAdapter.updateItems();
        fillTabs(false);
        if (tabsView != null) {
            tabsView.finishAddingTabs();
        }
    }

    private void search(View view, int position, String query, boolean reset) {
        long forumDialogId = dialogsSearchAdapter.delegate != null ? dialogsSearchAdapter.delegate.getSearchForumDialogId() : 0;
        long dialogId = position == 0 ? 0 : forumDialogId;
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
            if (dialogId == 0 && minDate == 0 && maxDate == 0 || forumDialogId != 0) {
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
        } else if (view instanceof FilteredSearchView) {
            ((FilteredSearchView) view).setUseFromUserAsAvatar(forumDialogId != 0);
            ((FilteredSearchView) view).setKeyboardHeight(keyboardSize, false);
            ViewPagerAdapter.Item item = viewPagerAdapter.items.get(position);
            ((FilteredSearchView) view).search(dialogId, minDate, maxDate, FiltersView.filters[item.filterIndex], includeFolder, query, reset);
        } else if (view instanceof SearchDownloadsContainer) {
            ((SearchDownloadsContainer) view).setKeyboardHeight(keyboardSize, false);
            ((SearchDownloadsContainer) view).search(query);
        }
    }

    @Nullable
    public SearchDownloadsContainer getDownloadsContainer() {
        return downloadsContainer;
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
            actionMode = parent.getActionBar().createActionMode(true, actionModeTag);

            selectedMessagesCountTextView = new NumberTextView(actionMode.getContext());
            selectedMessagesCountTextView.setTextSize(18);
            selectedMessagesCountTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            selectedMessagesCountTextView.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
            actionMode.addView(selectedMessagesCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 72, 0, 0, 0));
            selectedMessagesCountTextView.setOnTouchListener((v, event) -> true);

            speedItem = actionMode.addItemWithWidth(speedItemId, R.drawable.avd_speed, AndroidUtilities.dp(54), LocaleController.getString("AccDescrPremiumSpeed", R.string.AccDescrPremiumSpeed));
            speedItem.getIconView().setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon), PorterDuff.Mode.SRC_IN));
            gotoItem = actionMode.addItemWithWidth(gotoItemId, R.drawable.msg_message, AndroidUtilities.dp(54), LocaleController.getString("AccDescrGoToMessage", R.string.AccDescrGoToMessage));
            forwardItem = actionMode.addItemWithWidth(forwardItemId, R.drawable.msg_forward, AndroidUtilities.dp(54), LocaleController.getString("Forward", R.string.Forward));
            deleteItem = actionMode.addItemWithWidth(deleteItemId, R.drawable.msg_delete, AndroidUtilities.dp(54), LocaleController.getString("Delete", R.string.Delete));
        }
        if (selectedMessagesCountTextView != null) {
            boolean isForumSearch = dialogsSearchAdapter != null && dialogsSearchAdapter.delegate != null && dialogsSearchAdapter.delegate.getSearchForumDialogId() != 0;
            ((MarginLayoutParams) selectedMessagesCountTextView.getLayoutParams()).leftMargin = AndroidUtilities.dp(72 + (isForumSearch ? 56 : 0));
            selectedMessagesCountTextView.setLayoutParams(selectedMessagesCountTextView.getLayoutParams());
        }
        if (parent.getActionBar().getBackButton().getDrawable() instanceof MenuDrawable) {
            BackDrawable backDrawable = new BackDrawable(false);
            parent.getActionBar().setBackButtonDrawable(backDrawable);
            backDrawable.setColorFilter(null);
        }
        isActionModeShowed = show;
        if (show) {
            AndroidUtilities.hideKeyboard(parent.getParentActivity().getCurrentFocus());
            parent.getActionBar().showActionMode();
            selectedMessagesCountTextView.setNumber(selectedFiles.size(), false);
            speedItem.setVisibility(isSpeedItemVisible() ? View.VISIBLE : View.GONE);
            gotoItem.setVisibility(View.VISIBLE);
            forwardItem.setVisibility(View.VISIBLE);
            deleteItem.setVisibility(View.VISIBLE);
        } else {
            parent.getActionBar().hideActionMode();
            selectedFiles.clear();
            for (int i = 0; i < getChildCount(); i++) {
                if (getChildAt(i) instanceof FilteredSearchView) {
                    ((FilteredSearchView)getChildAt(i)).update();
                }
                if (getChildAt(i) instanceof SearchDownloadsContainer) {
                    ((SearchDownloadsContainer) getChildAt(i)).update(true);
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

    private boolean isSpeedItemVisible() {
        if (UserConfig.getInstance(currentAccount).isPremium() || MessagesController.getInstance(currentAccount).premiumFeaturesBlocked()) {
            return false;
        }
        for (MessageObject obj : selectedFiles.values()) {
            if (obj.getDocument() != null && obj.getDocument().size >= 150 * 1024 * 1024) {
                return true;
            }
        }
        return false;
    }

    public void onActionBarItemClick(int id) {
        if (id == deleteItemId) {
            if (parent == null || parent.getParentActivity() == null) {
                return;
            }
            ArrayList<MessageObject> messageObjects = new ArrayList<>(selectedFiles.values());
            AlertDialog.Builder builder = new AlertDialog.Builder(parent.getParentActivity());
            builder.setTitle(LocaleController.formatPluralString("RemoveDocumentsTitle", selectedFiles.size()));

            SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
            spannableStringBuilder
                    .append(AndroidUtilities.replaceTags(LocaleController.formatPluralString("RemoveDocumentsMessage", selectedFiles.size())))
                    .append("\n\n")
                    .append(LocaleController.getString("RemoveDocumentsAlertMessage", R.string.RemoveDocumentsAlertMessage));

            builder.setMessage(spannableStringBuilder);
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), (dialogInterface, i) -> dialogInterface.dismiss());
            builder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dialogInterface, i) -> {
                dialogInterface.dismiss();
                parent.getDownloadController().deleteRecentFiles(messageObjects);
                hideActionMode();
            });
            AlertDialog alertDialog = builder.show();
            TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (button != null) {
                button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
            }

        } else if (id == speedItemId) {
            if (!isSpeedItemVisible()) {
                return;
            }

            parent.showDialog(new PremiumFeatureBottomSheet(parent, PremiumPreviewFragment.PREMIUM_FEATURE_DOWNLOAD_SPEED, true));
        } else if (id == gotoItemId) {
            if (selectedFiles.size() != 1) {
                return;
            }
            MessageObject messageObject = selectedFiles.values().iterator().next();
            goToMessage(messageObject);
        } else if (id == forwardItemId) {
            Bundle args = new Bundle();
            args.putBoolean("onlySelect", true);
            args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
            DialogsActivity fragment = new DialogsActivity(args);
            fragment.setDelegate((fragment1, dids, message, param, topicsFragment) -> {
                ArrayList<MessageObject> fmessages = new ArrayList<>();
                Iterator<FilteredSearchView.MessageHashId> idIterator = selectedFiles.keySet().iterator();
                while (idIterator.hasNext()) {
                    FilteredSearchView.MessageHashId hashId = idIterator.next();
                    fmessages.add(selectedFiles.get(hashId));
                }
                selectedFiles.clear();

                showActionMode(false);

                if (dids.size() > 1 || dids.get(0).dialogId == AccountInstance.getInstance(currentAccount).getUserConfig().getClientUserId() || message != null) {
                    for (int a = 0; a < dids.size(); a++) {
                        long did = dids.get(a).dialogId;
                        if (message != null) {
                            AccountInstance.getInstance(currentAccount).getSendMessagesHelper().sendMessage(SendMessagesHelper.SendMessageParams.of(message.toString(), did, null, null, null, true, null, null, null, true, 0, null, false));
                        }
                        AccountInstance.getInstance(currentAccount).getSendMessagesHelper().sendMessage(fmessages, did, false,false, true, 0);
                    }
                    fragment1.finishFragment();
                } else {
                    long did = dids.get(0).dialogId;
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
                            return true;
                        }
                    }
                    ChatActivity chatActivity = new ChatActivity(args1);
                    fragment1.presentFragment(chatActivity, true);
                    chatActivity.showFieldPanelForForward(true, fmessages);
                }
                return true;
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
            if (speedItem != null) {
                boolean visible = isSpeedItemVisible();
                int v = visible ? View.VISIBLE : View.GONE;
                if (speedItem.getVisibility() != v) {
                    speedItem.setVisibility(v);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        AnimatedVectorDrawable drawable = (AnimatedVectorDrawable) speedItem.getIconView().getDrawable();
                        drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon), PorterDuff.Mode.SRC_IN));
                        if (visible) {
                            drawable.start();
                        } else {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                drawable.reset();
                            } else {
                                drawable.setVisible(false, true);
                            }
                        }
                    }
                }
            }
            if (deleteItem != null) {
                boolean canShowDelete = true;
                Set<FilteredSearchView.MessageHashId> keySet = selectedFiles.keySet();
                for (FilteredSearchView.MessageHashId key : keySet) {
                    if (!selectedFiles.get(key).isDownloadingFile) {
                        canShowDelete = false;
                        break;
                    }
                }
                deleteItem.setVisibility(canShowDelete ? View.VISIBLE : View.GONE);
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

    public void getThemeDescriptions(ArrayList<ThemeDescription> arrayList) {
        for (int i = 0; i < searchListView.getChildCount(); ++i) {
            View child = searchListView.getChildAt(i);
            if (child instanceof ProfileSearchCell || child instanceof DialogCell || child instanceof HashtagSearchCell) {
                arrayList.add(new ThemeDescription(child, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
            }
        }

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
        arrayList.addAll(SimpleThemeDescription.createThemeDescriptions(()-> {
            if (selectedMessagesCountTextView != null) {
                selectedMessagesCountTextView.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
            }
        }, Theme.key_actionBarActionModeDefaultIcon));
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
        if (position < 0) {
            return;
        }
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
            } else if (getChildAt(i) instanceof SearchDownloadsContainer) {
                ((SearchDownloadsContainer) getChildAt(i)).setKeyboardHeight(keyboardSize, animated);
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
            ArrayList<FilteredSearchView.MessageHashId> arrayList = new ArrayList<>(selectedFiles.keySet());
            for (int k = 0; k < arrayList.size(); k++) {
                FilteredSearchView.MessageHashId hashId = arrayList.get(k);
                MessageObject messageObject = selectedFiles.get(hashId);
                if (messageObject != null) {
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

    @Override
    protected void invalidateBlur() {
        fragmentView.invalidateBlur();
    }

    public void cancelEnterAnimation() {
        itemsEnterAnimator.cancel();
        searchListView.invalidate();
        animateFromCount = 0;
    }

    public void showDownloads() {
        setPosition(2);
    }

    public int getPositionForType(int initialSearchType) {
        for (int i = 0; i < viewPagerAdapter.items.size(); i++) {
            if (viewPagerAdapter.items.get(i).type == ViewPagerAdapter.FILTER_TYPE &&  viewPagerAdapter.items.get(i).filterIndex == initialSearchType) {
                return i;
            }
        }
        return -1;
    }

    private class ViewPagerAdapter extends ViewPagerFixed.Adapter {

        ArrayList<Item> items = new ArrayList<>();

        private final static int DIALOGS_TYPE = 0;
        private final static int DOWNLOADS_TYPE = 1;
        private final static int FILTER_TYPE = 2;

        public ViewPagerAdapter() {
            updateItems();
        }

        public void updateItems() {
            items.clear();
            items.add(new Item(DIALOGS_TYPE));
            if (!showOnlyDialogsAdapter) {
                Item item = new Item(FILTER_TYPE);
                item.filterIndex = 0;
                items.add(item);
                if (includeDownloads()) {
                    items.add(new Item(DOWNLOADS_TYPE));
                }
                item = new Item(FILTER_TYPE);
                item.filterIndex = 1;
                items.add(item);
                item = new Item(FILTER_TYPE);
                item.filterIndex = 2;
                items.add(item);
                item = new Item(FILTER_TYPE);
                item.filterIndex = 3;
                items.add(item);
                item = new Item(FILTER_TYPE);
                item.filterIndex = 4;
                items.add(item);
            }
        }

        @Override
        public String getItemTitle(int position) {
            if (items.get(position).type == DIALOGS_TYPE) {
                return LocaleController.getString("SearchAllChatsShort", R.string.SearchAllChatsShort);
            } else if (items.get(position).type == DOWNLOADS_TYPE) {
                return LocaleController.getString("DownloadsTabs", R.string.DownloadsTabs);
            } else {
                return FiltersView.filters[items.get(position).filterIndex].getTitle();
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public View createView(int viewType) {
            if (viewType == 1) {
                return searchContainer;
            } else if (viewType == 2) {
                downloadsContainer = new SearchDownloadsContainer(parent, currentAccount);
                downloadsContainer.recyclerListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                    @Override
                    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                        super.onScrolled(recyclerView, dx, dy);
                        fragmentView.invalidateBlur();
                    }
                });
                downloadsContainer.setUiCallback(SearchViewPager.this);
                return downloadsContainer;
            } else {
                FilteredSearchView filteredSearchView = new FilteredSearchView(parent);
                filteredSearchView.setChatPreviewDelegate(chatPreviewDelegate);
                filteredSearchView.setUiCallback(SearchViewPager.this);
                filteredSearchView.recyclerListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                    @Override
                    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                        super.onScrolled(recyclerView, dx, dy);
                        fragmentView.invalidateBlur();
                    }
                });
                return filteredSearchView;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (items.get(position).type == DIALOGS_TYPE) {
                return 1;
            }
            if (items.get(position).type == DOWNLOADS_TYPE) {
                return 2;
            }
            return items.get(position).type + position;
        }

        @Override
        public void bindView(View view, int position, int viewType) {
            search(view, position, lastSearchString, true);
        }

        private class Item {
            private final int type;
            int filterIndex;

            private Item(int type) {
                this.type = type;
            }
        }
    }

    public interface ChatPreviewDelegate {
        void startChatPreview(RecyclerListView listView, DialogCell cell);
        void move(float dy);
        void finish();
    }
}
