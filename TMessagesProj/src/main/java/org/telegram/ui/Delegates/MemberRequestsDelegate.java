package org.telegram.ui.Delegates;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.LongSparseArray;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MemberRequestsController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.AvatarPreviewPagerIndicator;
import org.telegram.ui.Cells.MemberRequestCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ProfileGalleryView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.StickerEmptyView;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.MemberRequestsActivity;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MemberRequestsDelegate implements MemberRequestCell.OnClickListener {

    public final boolean isChannel;
    public boolean isNeedRestoreList;

    private final List<TLRPC.TL_chatInviteImporter> currentImporters = new ArrayList<>();
    private final LongSparseArray<TLRPC.User> users = new LongSparseArray<>();

    private final ArrayList<TLRPC.TL_chatInviteImporter> allImporters = new ArrayList<>();
    private final Adapter adapter = new Adapter();
    private final BaseFragment fragment;
    private final FrameLayout layoutContainer;
    private final MemberRequestsController controller;
    private final long chatId;
    private final int currentAccount;
    private final boolean showSearchMenu;

    private FrameLayout rootLayout;
    private StickerEmptyView emptyView;
    private StickerEmptyView searchEmptyView;
    private RecyclerListView recyclerView;
    private FlickerLoadingView loadingView;

    private TLRPC.TL_chatInviteImporter importer;
    private PreviewDialog previewDialog;

    private String query;
    private Runnable searchRunnable;
    private int searchRequestId;
    private boolean isLoading;
    private boolean hasMore;
    private boolean isSearchExpanded;
    private boolean isDataLoaded;
    private boolean isFirstLoading = true;
    private boolean isShowLastItemDivider = true;

    public MemberRequestsDelegate(BaseFragment fragment, FrameLayout layoutContainer, long chatId, boolean showSearchMenu) {
        this.fragment = fragment;
        this.layoutContainer = layoutContainer;
        this.chatId = chatId;
        this.currentAccount = fragment.getCurrentAccount();
        this.isChannel = ChatObject.isChannelAndNotMegaGroup(chatId, currentAccount);
        this.showSearchMenu = showSearchMenu;
        this.controller = MemberRequestsController.getInstance(currentAccount);
    }

    public FrameLayout getRootLayout() {
        if (rootLayout == null) {
            rootLayout = new FrameLayout(fragment.getParentActivity());
            rootLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, fragment.getResourceProvider()));

            loadingView = getLoadingView();
            rootLayout.addView(loadingView, MATCH_PARENT, MATCH_PARENT);

            searchEmptyView = getSearchEmptyView();
            rootLayout.addView(searchEmptyView, MATCH_PARENT, MATCH_PARENT);

            emptyView = getEmptyView();
            rootLayout.addView(emptyView, LayoutHelper.createFrame(MATCH_PARENT, MATCH_PARENT));

            LinearLayoutManager layoutManager = new LinearLayoutManager(fragment.getParentActivity());
            recyclerView = new RecyclerListView(fragment.getParentActivity());
            recyclerView.setAdapter(adapter);
            recyclerView.setLayoutManager(layoutManager);
            recyclerView.setOnItemClickListener(this::onItemClick);
            recyclerView.setOnScrollListener(listScrollListener);
            recyclerView.setSelectorDrawableColor(Theme.getColor(Theme.key_listSelector, fragment.getResourceProvider()));
            rootLayout.addView(recyclerView, MATCH_PARENT, MATCH_PARENT);
        }
        return rootLayout;
    }

    public void setShowLastItemDivider(boolean showLastItemDivider) {
        this.isShowLastItemDivider = showLastItemDivider;
    }

    public Adapter getAdapter() {
        return adapter;
    }

    public FlickerLoadingView getLoadingView() {
        if (loadingView == null) {
            loadingView = new FlickerLoadingView(fragment.getParentActivity(), fragment.getResourceProvider());
            loadingView.setAlpha(0f);
            if (isShowLastItemDivider) {
                loadingView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, fragment.getResourceProvider()));
            }
            loadingView.setColors(Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundGray, null);
            loadingView.setViewType(FlickerLoadingView.MEMBER_REQUESTS_TYPE);
        }
        return loadingView;
    }

    public StickerEmptyView getEmptyView() {
        if (emptyView == null) {
            emptyView = new StickerEmptyView(fragment.getParentActivity(), null, StickerEmptyView.STICKER_TYPE_DONE, fragment.getResourceProvider());
            emptyView.title.setText(isChannel ? LocaleController.getString("NoSubscribeRequests", R.string.NoSubscribeRequests) : LocaleController.getString("NoMemberRequests", R.string.NoMemberRequests));
            emptyView.subtitle.setText(isChannel ? LocaleController.getString("NoSubscribeRequestsDescription", R.string.NoSubscribeRequestsDescription) : LocaleController.getString("NoMemberRequestsDescription", R.string.NoMemberRequestsDescription));
            emptyView.setAnimateLayoutChange(true);
            emptyView.setVisibility(GONE);
        }
        return emptyView;
    }

    public StickerEmptyView getSearchEmptyView() {
        if (searchEmptyView == null) {
            searchEmptyView = new StickerEmptyView(fragment.getParentActivity(), null, StickerEmptyView.STICKER_TYPE_SEARCH, fragment.getResourceProvider());
            if (isShowLastItemDivider) {
                searchEmptyView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, fragment.getResourceProvider()));
            }
            searchEmptyView.title.setText(LocaleController.getString("NoResult", R.string.NoResult));
            searchEmptyView.subtitle.setText(LocaleController.getString("SearchEmptyViewFilteredSubtitle2", R.string.SearchEmptyViewFilteredSubtitle2));
            searchEmptyView.setAnimateLayoutChange(true);
            searchEmptyView.setVisibility(GONE);
        }
        return searchEmptyView;
    }

    public void setRecyclerView(RecyclerListView recyclerView) {
        this.recyclerView = recyclerView;
        recyclerView.setOnItemClickListener(this::onItemClick);
        RecyclerView.OnScrollListener currentScrollListener = recyclerView.getOnScrollListener();
        if (currentScrollListener == null) {
            recyclerView.setOnScrollListener(listScrollListener);
        } else {
            recyclerView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                    super.onScrollStateChanged(recyclerView, newState);
                    currentScrollListener.onScrollStateChanged(recyclerView, newState);
                    listScrollListener.onScrollStateChanged(recyclerView, newState);
                }
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    currentScrollListener.onScrolled(recyclerView, dx, dy);
                    listScrollListener.onScrolled(recyclerView, dx, dy);
                }
            });
        }
    }

    public void onItemClick(View view, int position) {
        if (view instanceof MemberRequestCell) {
            if (isSearchExpanded) {
                AndroidUtilities.hideKeyboard(fragment.getParentActivity().getCurrentFocus());
            }
            MemberRequestCell cell = (MemberRequestCell) view;
            AndroidUtilities.runOnUIThread(() -> {
                importer = cell.getImporter();
                TLRPC.User user = users.get(importer.user_id);
                if (user == null) {
                    return;
                }
                fragment.getMessagesController().putUser(user, false);
                boolean isLandscape = AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y;
                boolean showProfile = user.photo == null || isLandscape;
                if (showProfile) {
                    isNeedRestoreList = true;
                    fragment.dismissCurrentDialog();
                    Bundle args = new Bundle();
                    ProfileActivity profileActivity = new ProfileActivity(args);
                    args.putLong("user_id", user.id);
                    args.putBoolean("removeFragmentOnChatOpen", false);
                    fragment.presentFragment(profileActivity);
                } else if (previewDialog == null) {
                    RecyclerListView parentListView = (RecyclerListView) cell.getParent();
                    previewDialog = new PreviewDialog(fragment.getParentActivity(), parentListView, fragment.getResourceProvider(), isChannel);
                    previewDialog.setImporter(importer, cell.getAvatarImageView());
                    previewDialog.setOnDismissListener(dialog -> previewDialog = null);
                    previewDialog.show();
                }
            }, isSearchExpanded ? 100 : 0);
        }
    }

    public boolean onBackPressed() {
        if (previewDialog != null) {
            previewDialog.dismiss();
            return false;
        } else {
            return true;
        }
    }

    public void setSearchExpanded(boolean isExpanded) {
        isSearchExpanded = isExpanded;
    }

    public void setQuery(String query) {
        if (searchRunnable != null) {
            Utilities.searchQueue.cancelRunnable(searchRunnable);
            searchRunnable = null;
        }
        if (searchRequestId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(searchRequestId, false);
            searchRequestId = 0;
        }

        this.query = query;
        if (isDataLoaded && allImporters.isEmpty()) {
            setViewVisible(loadingView, false, false);
            return;
        }

        if (TextUtils.isEmpty(query)) {
            adapter.setItems(allImporters);
            setViewVisible(recyclerView, true, true);
            setViewVisible(loadingView, false, false);
            if (searchEmptyView != null) {
                searchEmptyView.setVisibility(INVISIBLE);
            }
            if (query == null && showSearchMenu) {
                fragment.getActionBar().createMenu().getItem(MemberRequestsActivity.searchMenuItem).setVisibility(allImporters.isEmpty() ? GONE : VISIBLE);
            }
        } else {
            adapter.setItems(Collections.emptyList());
            setViewVisible(recyclerView, false, false);
            setViewVisible(loadingView, true, true);
            Utilities.searchQueue.postRunnable(searchRunnable = this::loadMembers, 300);
        }
        if (query != null) {
            if (emptyView != null) {
                emptyView.setVisibility(INVISIBLE);
            }
            if (searchEmptyView != null) {
                searchEmptyView.setVisibility(INVISIBLE);
            }
        }
    }

    public void loadMembers() {
        boolean isNeedShowLoading = true;
        if (isFirstLoading) {
            TLRPC.TL_messages_chatInviteImporters firstImporters = controller.getCachedImporters(chatId);
            if (firstImporters != null) {
                isNeedShowLoading = false;
                isDataLoaded = true;
                onImportersLoaded(firstImporters, null, true, true);
            }
        }
        final boolean needShowLoading = isNeedShowLoading;
        AndroidUtilities.runOnUIThread(() -> {
            final boolean isEmptyQuery = TextUtils.isEmpty(query);
            final boolean isEmptyOffset = currentImporters.isEmpty() || isFirstLoading;
            final String lastQuery = query;

            isLoading = true;
            isFirstLoading = false;

            final Runnable showLoadingRunnable = isEmptyQuery && needShowLoading ? () -> setViewVisible(loadingView, true, true) : null;
            if (isEmptyQuery) {
                AndroidUtilities.runOnUIThread(showLoadingRunnable, 300);
            }

            TLRPC.TL_chatInviteImporter lastInvitedUser = !isEmptyQuery && !currentImporters.isEmpty()
                    ? currentImporters.get(currentImporters.size() - 1)
                    : null;
            searchRequestId = controller.getImporters(chatId, lastQuery, lastInvitedUser, users, (response, error) -> {
                AndroidUtilities.runOnUIThread(() -> {
                    isLoading = false;
                    isDataLoaded = true;
                    if (isEmptyQuery) {
                        AndroidUtilities.cancelRunOnUIThread(showLoadingRunnable);
                    }
                    setViewVisible(loadingView, false, false);
                    if (!TextUtils.equals(lastQuery, query)) {
                        return;
                    }
                    if (error == null) {
                        isDataLoaded = true;
                        TLRPC.TL_messages_chatInviteImporters importers = (TLRPC.TL_messages_chatInviteImporters) response;
                        onImportersLoaded(importers, lastQuery, isEmptyOffset, false);
                    }
                });
            });
        });
    }

    private void onImportersLoaded(TLRPC.TL_messages_chatInviteImporters importers, String lastQuery, boolean isEmptyOffset, boolean fromCache) {
        for (int i = 0; i < importers.users.size(); ++i) {
            TLRPC.User user = importers.users.get(i);
            users.put(user.id, user);
        }
        if (isEmptyOffset) {
            adapter.setItems(importers.importers);
        } else {
            adapter.appendItems(importers.importers);
        }
        if (TextUtils.isEmpty(lastQuery)) {
            allImporters.clear();
            allImporters.addAll(importers.importers);
            if (showSearchMenu) {
                fragment.getActionBar().createMenu().getItem(MemberRequestsActivity.searchMenuItem).setVisibility(allImporters.isEmpty() ? GONE : VISIBLE);
            }
        }
        onImportersChanged(lastQuery, fromCache, false);
        hasMore = currentImporters.size() < importers.count;
    }

    @Override
    public void onAddClicked(TLRPC.TL_chatInviteImporter importer) {
        hideChatJoinRequest(importer, true);
    }

    @Override
    public void onDismissClicked(TLRPC.TL_chatInviteImporter importer) {
        hideChatJoinRequest(importer, false);
    }

    public void setAdapterItemsEnabled(boolean adapterItemsEnabled) {
        if (recyclerView != null) {
            int position = adapter.extraFirstHolders();
            if (0 <= position && position < recyclerView.getChildCount()) {
                recyclerView.getChildAt(position).setEnabled(adapterItemsEnabled);
            }
        }
    }

    protected void onImportersChanged(String query, boolean fromCache, boolean fromHide) {
        boolean isListVisible;
        if (TextUtils.isEmpty(query)) {
            isListVisible = !allImporters.isEmpty() || fromCache;
            if (emptyView != null) {
                emptyView.setVisibility(isListVisible ? INVISIBLE : VISIBLE);
            }
            if (searchEmptyView != null) {
                searchEmptyView.setVisibility(INVISIBLE);
            }
        } else {
            isListVisible = !currentImporters.isEmpty() || fromCache;
            if (emptyView != null) {
                emptyView.setVisibility(INVISIBLE);
            }
            if (searchEmptyView != null) {
                searchEmptyView.setVisibility(isListVisible ? INVISIBLE : VISIBLE);
            }
        }
        setViewVisible(recyclerView, isListVisible, true);
        if (allImporters.isEmpty()) {
            if (emptyView != null) {
                emptyView.setVisibility(VISIBLE);
            }
            if (searchEmptyView != null) {
                searchEmptyView.setVisibility(INVISIBLE);
            }
            setViewVisible(loadingView, false, false);
            if (isSearchExpanded && showSearchMenu) {
                fragment.getActionBar().createMenu().closeSearchField(true);
            }
        }
    }

    protected boolean hasAllImporters() {
        return !allImporters.isEmpty();
    }

    private void hideChatJoinRequest(TLRPC.TL_chatInviteImporter importer, boolean isApproved) {
        TLRPC.User user = users.get(importer.user_id);
        if (user == null) {
            return;
        }
        TLRPC.TL_messages_hideChatJoinRequest req = new TLRPC.TL_messages_hideChatJoinRequest();
        req.approved = isApproved;
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(-chatId);
        req.user_id = MessagesController.getInstance(currentAccount).getInputUser(user);
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (error == null) {
                TLRPC.TL_updates updates = (TLRPC.TL_updates) response;
                MessagesController.getInstance(currentAccount).processUpdates(updates, false);
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (fragment == null || fragment.getParentActivity() == null) {
                    return;
                }
                if (error == null) {
                    TLRPC.TL_updates updates = (TLRPC.TL_updates) response;
                    if (!updates.chats.isEmpty()) {
                        TLRPC.Chat chat = updates.chats.get(0);
                        MessagesController.getInstance(currentAccount).loadFullChat(chat.id, 0, true);
                    }
                    for (int i = 0; i < allImporters.size(); ++i) {
                        if (allImporters.get(i).user_id == importer.user_id) {
                            allImporters.remove(i);
                            break;
                        }
                    }
                    adapter.removeItem(importer);
                    onImportersChanged(query, false, true);
                    if (isApproved) {
                        Bulletin.MultiLineLayout layout = new Bulletin.MultiLineLayout(fragment.getParentActivity(), fragment.getResourceProvider());
                        layout.imageView.setRoundRadius(AndroidUtilities.dp(15));
                        layout.imageView.setForUserOrChat(user, new AvatarDrawable(user));
                        String userName = UserObject.getFirstName(user);
                        String message = isChannel
                                ? LocaleController.formatString("HasBeenAddedToChannel", R.string.HasBeenAddedToChannel, userName)
                                : LocaleController.formatString("HasBeenAddedToGroup", R.string.HasBeenAddedToGroup, userName);
                        SpannableStringBuilder stringBuilder = new SpannableStringBuilder(message);
                        int start = message.indexOf(userName);
                        stringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf")), start, start + userName.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                        layout.textView.setText(stringBuilder);
                        if (allImporters.isEmpty()) {
                            Bulletin.make(fragment, layout, Bulletin.DURATION_LONG).show();
                        } else {
                            Bulletin.make(layoutContainer, layout, Bulletin.DURATION_LONG).show();
                        }
                    }
                    ActionBarMenu menu = fragment.getActionBar().createMenu();
                    if (TextUtils.isEmpty(query) && showSearchMenu) {
                        menu.getItem(MemberRequestsActivity.searchMenuItem).setVisibility(allImporters.isEmpty() ? GONE : VISIBLE);
                    }
                } else {
                    AlertsCreator.processError(currentAccount, error, fragment, req);
                }
            });
        });
    }

    private void hidePreview() {
        previewDialog.dismiss();
        importer = null;
    }

    private void setViewVisible(View view, boolean isVisible, boolean isAnimated) {
        if (view == null) {
            return;
        }
        boolean isCurrentVisible = view.getVisibility() == VISIBLE;
        float targetAlpha = isVisible ? 1f : 0f;
        if (isVisible == isCurrentVisible && targetAlpha == view.getAlpha()) {
            return;
        }
        if (isAnimated) {
            if (isVisible) {
                view.setAlpha(0f);
            }
            view.setVisibility(VISIBLE);
            view.animate()
                    .alpha(targetAlpha)
                    .setDuration(150)
                    .start();
        } else {
            view.setVisibility(isVisible ? VISIBLE : INVISIBLE);
        }
    }

    private final RecyclerView.OnScrollListener listScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
            if (hasMore && !isLoading && layoutManager != null) {
                int lastPosition = layoutManager.findLastVisibleItemPosition();
                if (adapter.getItemCount() - lastPosition < 10) {
                    loadMembers();
                }
            }
        }
    };


    private class Adapter extends RecyclerListView.SelectionAdapter {

        @NonNull
        @Override
        public RecyclerListView.Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                default:
                case 0:
                    MemberRequestCell cell = new MemberRequestCell(parent.getContext(), MemberRequestsDelegate.this, isChannel);
                    cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, fragment.getResourceProvider()));
                    view = cell;
                    break;
                case 1:
                    view = new View(parent.getContext());
                    view.setBackground(Theme.getThemedDrawable(parent.getContext(), R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
                case 2:
                    view = new View(parent.getContext()) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(52), MeasureSpec.EXACTLY));
                        }
                    };
                    break;
                case 3:
                    view = new View(parent.getContext());
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == 0) {
                MemberRequestCell cell = (MemberRequestCell) holder.itemView;
                position -= extraFirstHolders();
                cell.setData(users, currentImporters.get(position), position != currentImporters.size() - 1);
            } else if (holder.getItemViewType() == 2) {
                holder.itemView.requestLayout();
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 0;
        }

        @Override
        public int getItemCount() {
            return extraFirstHolders() + currentImporters.size() + extraLastHolders();
        }

        @Override
        public int getItemViewType(int position) {
            if (isShowLastItemDivider) {
                if (position == currentImporters.size() && !currentImporters.isEmpty()) {
                    return 1;
                }
            } else {
                if (position == 0) {
                    return 2;
                } else if (position == getItemCount() - 1) {
                    return 3;
                }
            }
            return 0;
        }

        @SuppressLint("NotifyDataSetChanged")
        public void setItems(List<TLRPC.TL_chatInviteImporter> newItems) {
            for (int i = 0; i < newItems.size(); ++i) {
                long id = newItems.get(i).user_id;
                for (int j = i + 1; j < newItems.size(); ++j) {
                    long iid = newItems.get(j).user_id;
                    if (iid == id) {
                        newItems.remove(i);
                        i--;
                        break;
                    }
                }
            }
            currentImporters.clear();
            currentImporters.addAll(newItems);
            notifyDataSetChanged();
        }

        public void appendItems(List<TLRPC.TL_chatInviteImporter> newItems) {
            for (int i = 0; i < newItems.size(); ++i) {
                long id = newItems.get(i).user_id;
                for (int j = 0; j < currentImporters.size(); ++j) {
                    long iid = currentImporters.get(j).user_id;
                    if (iid == id) {
                        newItems.remove(i);
                        i--;
                        break;
                    }
                }
            }
            currentImporters.addAll(newItems);
            if (currentImporters.size() > newItems.size()) {
                notifyItemChanged(currentImporters.size() - newItems.size() - 1);
            }
            notifyItemRangeInserted(currentImporters.size() - newItems.size(), newItems.size());
        }

        public void removeItem(TLRPC.TL_chatInviteImporter item) {
            int position = -1;
            for (int i = 0; i < currentImporters.size(); ++i) {
                if (currentImporters.get(i).user_id == item.user_id) {
                    position = i;
                    break;
                }
            }
            if (position >= 0) {
                currentImporters.remove(position);
                notifyItemRemoved(position + extraFirstHolders());
                if (currentImporters.isEmpty()) {
                    notifyItemRemoved(1);
                }
            }
        }

        private int extraFirstHolders() {
            return isShowLastItemDivider ? 0 : 1;
        }

        private int extraLastHolders() {
            return isShowLastItemDivider && currentImporters.isEmpty() ? 0 : 1;
        }
    }


    private class PreviewDialog extends Dialog {

        private final int shadowPaddingTop;
        private final int shadowPaddingLeft;
        private final Drawable pagerShadowDrawable = getContext().getResources().getDrawable(R.drawable.popup_fixed_alert2).mutate();
        private final TextView nameText = new TextView(getContext());
        private final TextView bioText = new TextView(getContext());
        private final ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout;
        private final ProfileGalleryView viewPager;
        private final AvatarPreviewPagerIndicator pagerIndicator;

        private TLRPC.TL_chatInviteImporter importer;
        private ValueAnimator animator;
        private BackupImageView imageView;
        private BitmapDrawable backgroundDrawable;
        private float animationProgress;

        public PreviewDialog(@NonNull Context context, @NonNull RecyclerListView parentListView, @NonNull Theme.ResourcesProvider resourcesProvider, boolean isChannel) {
            super(context, R.style.TransparentDialog2);
            setCancelable(true);
            contentView.setVisibility(INVISIBLE);

            int backgroundColor = Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, fragment.getResourceProvider());
            pagerShadowDrawable.setColorFilter(new PorterDuffColorFilter(backgroundColor, PorterDuff.Mode.MULTIPLY));
            pagerShadowDrawable.setCallback(contentView);
            android.graphics.Rect paddingRect = new android.graphics.Rect();
            pagerShadowDrawable.getPadding(paddingRect);
            shadowPaddingTop = paddingRect.top;
            shadowPaddingLeft = paddingRect.left;

            popupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, resourcesProvider);
            popupLayout.setBackgroundColor(backgroundColor);
            contentView.addView(popupLayout);

            pagerIndicator = new AvatarPreviewPagerIndicator(getContext()) {
                @Override
                protected void onDraw(Canvas canvas) {
                    if (profileGalleryView.getRealCount() > 1) {
                        super.onDraw(canvas);
                    }
                }
            };

            viewPager = new ProfileGalleryView(context, fragment.getActionBar(), parentListView, pagerIndicator);
            viewPager.setCreateThumbFromParent(true);
            contentView.addView(viewPager);

            pagerIndicator.setProfileGalleryView(viewPager);
            contentView.addView(pagerIndicator);

            nameText.setMaxLines(1);
            nameText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, fragment.getResourceProvider()));
            nameText.setTextSize(16);
            nameText.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            contentView.addView(nameText);

            bioText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, fragment.getResourceProvider()));
            bioText.setTextSize(14);
            contentView.addView(bioText);

            ActionBarMenuSubItem addCell = new ActionBarMenuSubItem(context, true, false);
            addCell.setColors(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider), Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon, resourcesProvider));
            addCell.setSelectorColor(Theme.getColor(Theme.key_dialogButtonSelector, resourcesProvider));
            addCell.setTextAndIcon(isChannel ? LocaleController.getString("AddToChannel", R.string.AddToChannel) : LocaleController.getString("AddToGroup", R.string.AddToGroup), R.drawable.msg_requests);
            addCell.setOnClickListener((v) -> {
                if (importer != null) {
                    onAddClicked(importer);
                }
                hidePreview();
            });
            popupLayout.addView(addCell);

            ActionBarMenuSubItem sendMsgCell = new ActionBarMenuSubItem(context, false, false);
            sendMsgCell.setColors(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider), Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon, resourcesProvider));
            sendMsgCell.setSelectorColor(Theme.getColor(Theme.key_dialogButtonSelector, resourcesProvider));
            sendMsgCell.setTextAndIcon(LocaleController.getString("SendMessage", R.string.SendMessage), R.drawable.msg_msgbubble3);
            sendMsgCell.setOnClickListener((v) -> {
                if (importer != null) {
                    isNeedRestoreList = true;
                    super.dismiss();
                    fragment.dismissCurrentDialog();
                    Bundle args = new Bundle();
                    args.putLong("user_id", importer.user_id);
                    ChatActivity chatActivity = new ChatActivity(args);
                    fragment.presentFragment(chatActivity);
                }
            });
            popupLayout.addView(sendMsgCell);

            ActionBarMenuSubItem dismissCell = new ActionBarMenuSubItem(context, false, true);
            dismissCell.setColors(Theme.getColor(Theme.key_dialogTextRed, resourcesProvider), Theme.getColor(Theme.key_dialogRedIcon, resourcesProvider));
            dismissCell.setSelectorColor(Theme.getColor(Theme.key_dialogButtonSelector, resourcesProvider));
            dismissCell.setTextAndIcon(LocaleController.getString("DismissRequest", R.string.DismissRequest), R.drawable.msg_remove);
            dismissCell.setOnClickListener((v) -> {
                if (importer != null) {
                    onDismissClicked(importer);
                }
                hidePreview();
            });
            popupLayout.addView(dismissCell);
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getWindow().setWindowAnimations(R.style.DialogNoAnimation);
            setContentView(contentView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            params.dimAmount = 0;
            params.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            params.gravity = Gravity.TOP | Gravity.LEFT;
            if (Build.VERSION.SDK_INT >= 21) {
                params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                        WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;
            }
            if (Build.VERSION.SDK_INT >= 28) {
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
            getWindow().setAttributes(params);
        }

        public void setImporter(TLRPC.TL_chatInviteImporter importer, BackupImageView imageView) {
            this.importer = importer;
            this.imageView = imageView;

            final ImageLocation imageLocation;
            final ImageLocation thumbLocation;
            TLRPC.User currentUser = MessagesController.getInstance(currentAccount).getUser(importer.user_id);
            imageLocation = ImageLocation.getForUserOrChat(currentUser, ImageLocation.TYPE_BIG);
            thumbLocation = ImageLocation.getForUserOrChat(currentUser, ImageLocation.TYPE_SMALL);
            final TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(importer.user_id);
            if (userFull == null) {
                MessagesController.getInstance(currentAccount).loadUserInfo(currentUser, false, 0);
            }
            viewPager.setParentAvatarImage(imageView);
            viewPager.setData(importer.user_id, true);
            viewPager.initIfEmpty(null, imageLocation, thumbLocation, true);
            TLRPC.User user = users.get(importer.user_id);
            nameText.setText(UserObject.getUserName(user));
            bioText.setText(importer.about);
            bioText.setVisibility(TextUtils.isEmpty(importer.about) ? GONE : VISIBLE);
            contentView.requestLayout();
        }

        @Override
        public void show() {
            super.show();
            AndroidUtilities.runOnUIThread(() -> {
                updateBackgroundBitmap();
                runAnimation(true);
            }, 80);
        }

        @Override
        public void dismiss() {
            runAnimation(false);
        }

        private void runAnimation(boolean show) {
            if (animator != null) {
                animator.cancel();
            }

            int[] location = new int[2];
            imageView.getLocationOnScreen(location);
            final float fromScale = imageView.getWidth() * 1f / getContentWidth();
            final float fromRadius = imageView.getWidth() / 2f / fromScale;
            final float xFrom = location[0] - (viewPager.getLeft() + (int)((getContentWidth() * (1f - fromScale) / 2f)));
            final float yFrom = location[1] - (viewPager.getTop() + (int)((getContentHeight() * (1f - fromScale) / 2f)));

            int popupLayoutTranslation = -popupLayout.getTop() / 2;
            animator = ValueAnimator.ofFloat(show ? 0f : 1f, show ? 1f : 0f);
            animator.addUpdateListener(animation -> {
                animationProgress = (float) animation.getAnimatedValue();
                float scale = fromScale + (1f - fromScale) * animationProgress;
                contentView.setScaleX(scale);
                contentView.setScaleY(scale);
                contentView.setTranslationX(xFrom * (1f - animationProgress));
                contentView.setTranslationY(yFrom * (1f - animationProgress));

                int roundRadius = (int) (fromRadius * (1f - animationProgress));
                viewPager.setRoundRadius(roundRadius, roundRadius);

                float alpha = MathUtils.clamp(2 * animationProgress - 1f, 0f, 1f);
                pagerShadowDrawable.setAlpha((int)(255 * alpha));
                nameText.setAlpha(alpha);
                bioText.setAlpha(alpha);
                popupLayout.setTranslationY(popupLayoutTranslation * (1f - animationProgress));
                popupLayout.setAlpha(alpha);
                if (backgroundDrawable != null) {
                    backgroundDrawable.setAlpha((int)(255 * animationProgress));
                }
                pagerIndicator.setAlpha(alpha);
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    super.onAnimationStart(animation);
                    contentView.setVisibility(VISIBLE);
                    if (show) {
                        contentView.setScaleX(fromScale);
                        contentView.setScaleY(fromScale);
                    }
                }
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    if (!show) {
                        PreviewDialog.super.dismiss();
                    }
                }
            });
            animator.setDuration(220);
            animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            animator.start();
        }

        private Bitmap getBlurredBitmap() {
            float factor = 6.0f;
            int width = (int) ((contentView.getMeasuredWidth()) / factor);
            int height = (int) ((contentView.getMeasuredHeight()) / factor);
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.scale(1f / factor, 1f / factor);

            canvas.save();
            ((LaunchActivity) fragment.getParentActivity()).getActionBarLayout().getView().draw(canvas);
            canvas.drawColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.3f)));
            Dialog dialog = fragment.getVisibleDialog();
            if (dialog != null) {
                dialog.getWindow().getDecorView().draw(canvas);
            }
            Utilities.stackBlurBitmap(bitmap, Math.max(7, Math.max(width, height) / 180));

            return bitmap;
        }

        private void updateBackgroundBitmap() {
            int oldAlpha = 255;
            if (backgroundDrawable != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                oldAlpha = backgroundDrawable.getAlpha();
            }
            backgroundDrawable = new BitmapDrawable(getContext().getResources(), getBlurredBitmap());
            backgroundDrawable.setAlpha(oldAlpha);
            getWindow().setBackgroundDrawable(backgroundDrawable);
        }

        private int getContentHeight() {
            int height = viewPager.getMeasuredHeight();
            height += AndroidUtilities.dp(12) + nameText.getMeasuredHeight();
            if (bioText.getVisibility() != GONE) {
                height += AndroidUtilities.dp(4) + bioText.getMeasuredHeight();
            }
            height += AndroidUtilities.dp(12) + popupLayout.getMeasuredHeight();
            return height;
        }

        private int getContentWidth() {
            return viewPager.getMeasuredWidth();
        }

        private final ViewGroup contentView = new ViewGroup(getContext()) {

            private final GestureDetector gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDown(MotionEvent e) {
                    return true;
                }
                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    boolean isTouchInsideContent = pagerShadowDrawable.getBounds().contains((int) e.getX(), (int) e.getY()) ||
                            popupLayout.getLeft() < e.getX() && e.getX() < popupLayout.getRight() &&
                                    popupLayout.getTop() < e.getY() && e.getY() < popupLayout.getBottom();
                    if (!isTouchInsideContent) {
                        dismiss();
                    }
                    return super.onSingleTapUp(e);
                }
            });
            private final Path clipPath = new Path();
            private final RectF rectF = new RectF();
            private boolean firstSizeChange = true;

            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                return gestureDetector.onTouchEvent(event);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                setWillNotDraw(false);
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                int minSize = Math.min(getMeasuredWidth(), getMeasuredHeight());
                int pagerSize = Math.min(minSize, (int)(getMeasuredHeight() * 0.66)) - AndroidUtilities.dp(12) * 2;
                int pagerSpec = MeasureSpec.makeMeasureSpec(pagerSize, MeasureSpec.AT_MOST);
                viewPager.measure(pagerSpec, pagerSpec);
                pagerIndicator.measure(pagerSpec, pagerSpec);
                int textWidthSpec = MeasureSpec.makeMeasureSpec(pagerSize - AndroidUtilities.dp(16) * 2, MeasureSpec.EXACTLY);
                nameText.measure(textWidthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                bioText.measure(textWidthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                popupLayout.measure(View.MeasureSpec.makeMeasureSpec(viewPager.getMeasuredWidth() + shadowPaddingLeft * 2, MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                int top = (getHeight() - getContentHeight()) / 2;
                int left = (getWidth() - viewPager.getMeasuredWidth()) / 2;
                viewPager.layout(left, top, left + viewPager.getMeasuredWidth(), top + viewPager.getMeasuredHeight());
                pagerIndicator.layout(viewPager.getLeft(), viewPager.getTop(), viewPager.getRight(), viewPager.getTop() + pagerIndicator.getMeasuredHeight());
                top += viewPager.getMeasuredHeight() + AndroidUtilities.dp(12);
                nameText.layout(viewPager.getLeft() + AndroidUtilities.dp(16), top, viewPager.getRight() - AndroidUtilities.dp(16), top + nameText.getMeasuredHeight());
                top += nameText.getMeasuredHeight();
                if (bioText.getVisibility() != GONE) {
                    top += AndroidUtilities.dp(4);
                    bioText.layout(nameText.getLeft(), top, nameText.getRight(), top + bioText.getMeasuredHeight());
                    top += bioText.getMeasuredHeight();
                }
                top += AndroidUtilities.dp(12);
                pagerShadowDrawable.setBounds(
                        viewPager.getLeft() - shadowPaddingLeft,
                        viewPager.getTop() - shadowPaddingTop,
                        viewPager.getRight() + shadowPaddingLeft,
                        top + shadowPaddingTop
                );

                left = viewPager.getRight() - popupLayout.getMeasuredWidth() + shadowPaddingLeft;
                popupLayout.layout(left, top, viewPager.getRight() + shadowPaddingLeft, top + popupLayout.getMeasuredHeight());
                popupLayout.setVisibility(popupLayout.getBottom() < b ? VISIBLE : GONE);

                int radius = AndroidUtilities.dp(6);
                rectF.set(viewPager.getLeft(), viewPager.getTop(), viewPager.getRight(), viewPager.getTop() + radius * 2);
                clipPath.reset();
                clipPath.addRoundRect(rectF, radius, radius, Path.Direction.CW);
                rectF.set(l, viewPager.getTop() + radius, r, b);
                clipPath.addRect(rectF, Path.Direction.CW);
            }

            @Override
            protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);
                boolean isLandscape = AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y;
                if (isLandscape) {
                    PreviewDialog.super.dismiss();
                }
                if (w != oldw && h != oldh) {
                    if (!firstSizeChange) {
                        updateBackgroundBitmap();
                    }
                    firstSizeChange = false;
                }
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                canvas.save();
                canvas.clipPath(clipPath);
                super.dispatchDraw(canvas);
                canvas.restore();
            }

            @Override
            protected void onDraw(Canvas canvas) {
//                if (animationProgress < 1f) {
//                    canvas.save();
//                }
                pagerShadowDrawable.draw(canvas);
//                if (animationProgress < 1f) {
//                    canvas.restore();
//                }
                super.onDraw(canvas);
            }

            @Override
            protected boolean verifyDrawable(@NonNull Drawable who) {
                return who == pagerShadowDrawable || super.verifyDrawable(who);
            }
        };
    }
}
