package org.telegram.ui.Components;

import static org.telegram.messenger.LocaleController.getString;

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
import android.view.Gravity;
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
import org.telegram.messenger.NotificationCenter;
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
import org.telegram.ui.ReportBottomSheet;
import org.telegram.ui.SearchAdsInfoBottomSheet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

public class SearchViewPager extends ViewPagerFixed implements FilteredSearchView.UiCallback, NotificationCenter.NotificationCenterDelegate {

    protected final ViewPagerAdapter viewPagerAdapter;
    public FrameLayout searchContainer;
    public RecyclerListView searchListView;
    public StickerEmptyView emptyView;
    private DefaultItemAnimator itemAnimator;
    public DialogsSearchAdapter dialogsSearchAdapter;
    private LinearLayoutManager searchLayoutManager;
    private RecyclerItemsEnterAnimator itemsEnterAnimator;
    private boolean attached;

    private DefaultItemAnimator channelsItemAnimator;
    public FrameLayout channelsSearchContainer;
    public StickerEmptyView channelsEmptyView;
    private LinearLayoutManager channelsSearchLayoutManager;
    public RecyclerListView channelsSearchListView;
    public DialogsChannelsAdapter channelsSearchAdapter;

    private DefaultItemAnimator botsItemAnimator;
    public FrameLayout botsSearchContainer;
    public StickerEmptyView botsEmptyView;
    private LinearLayoutManager botsSearchLayoutManager;
    public RecyclerListView botsSearchListView;
    public DialogsBotsAdapter botsSearchAdapter;

    public boolean expandedPublicPosts = false;
    private DefaultItemAnimator hashtagItemAnimator;
    public FrameLayout hashtagSearchContainer;
    public StickerEmptyView hashtagEmptyView;
    private LinearLayoutManager hashtagSearchLayoutManager;
    public RecyclerListView hashtagSearchListView;
    public HashtagsSearchAdapter hashtagSearchAdapter;

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

            @Override
            protected void openPublicPosts() {
                hashtagSearchAdapter.setInitialData(dialogsSearchAdapter.publicPostsHashtag, dialogsSearchAdapter.publicPosts, dialogsSearchAdapter.publicPostsLastRate, dialogsSearchAdapter.publicPostsTotalCount);
                expandedPublicPosts = true;
                hashtagSearchLayoutManager.scrollToPositionWithOffset(0, 0);
                updateTabs();
                if (tabsView != null && tabsView.getCurrentTabId() != 1) {
                    tabsView.scrollToTab(1, 1);
                }
                hashtagSearchAdapter.search(lastSearchString);
            }

            @Override
            protected void openBotApp(TLRPC.User bot) {
                if (bot == null) return;
                if (parent instanceof DialogsActivity) {
                    ((DialogsActivity) parent).closeSearching();
                }
                MessagesController.getInstance(currentAccount).openApp(bot, 0);
                putRecentSearch(bot.id, bot);
            }

            @Override
            protected void openSponsoredOptions(ProfileSearchCell cell, TLRPC.TL_sponsoredPeer sponsoredPeer) {
                AndroidUtilities.hideKeyboard(fragment.getParentActivity().getCurrentFocus());
                final ItemOptions o = ItemOptions.makeOptions(fragment, cell, true);
                if (!TextUtils.isEmpty(sponsoredPeer.sponsor_info)) {
                    final ItemOptions oi = o.makeSwipeback()
                        .add(R.drawable.ic_ab_back, getString(R.string.Back), () -> o.closeSwipeback())
                        .addGap()
                        .addText(sponsoredPeer.sponsor_info, 13);
                    o.add(R.drawable.msg_channel, getString(R.string.SponsoredMessageSponsorReportable), () -> {
                        o.openSwipeback(oi);
                    });
                }
                o
                    .add(R.drawable.msg_info, getString(R.string.AboutRevenueSharingAds), () -> {
                        fragment.showDialog(new SearchAdsInfoBottomSheet(context, fragment.getResourceProvider(), () -> {
                            removeAllAds();
                            BulletinFactory.of(fragment)
                                .createAdReportedBulletin(LocaleController.getString(R.string.AdHidden))
                                .show();
                        }));
                        o.dismiss();
                    })
                    .add(R.drawable.msg_block2, getString(R.string.ReportAd), () -> {
                        ReportBottomSheet.openSponsoredPeer(fragment, sponsoredPeer.random_id, fragment.getResourceProvider(), () -> {
                            removeAd(sponsoredPeer);
                        });
                        o.dismiss();
                    })
                    .addGap()
                    .add(R.drawable.msg_cancel, getString(R.string.RemoveAds), () -> {
                        if (UserConfig.getInstance(currentAccount).isPremium()) {
                            fragment.getMessagesController().disableAds(true);
                            removeAllAds();
                            BulletinFactory.of(fragment)
                                .createAdReportedBulletin(LocaleController.getString(R.string.AdHidden))
                                .show();
                        } else {
                            new PremiumFeatureBottomSheet(fragment, PremiumPreviewFragment.PREMIUM_FEATURE_ADS, true).show();
                        }
                        o.dismiss();
                    })
                    .setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT)
                    .setOnTopOfScrim()
                    .setDrawScrim(false)
                    .show();
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
        emptyView.title.setText(getString(R.string.NoResult));
        emptyView.subtitle.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
        emptyView.addView(loadingView, 0);
        emptyView.showProgress(true, false);

        searchContainer = new FrameLayout(context);
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

        channelsSearchContainer = new FrameLayout(context);

        channelsItemAnimator = new DefaultItemAnimator() {
            @Override
            protected void onMoveAnimationUpdate(RecyclerView.ViewHolder holder) {
                super.onMoveAnimationUpdate(holder);
                invalidate();
            }
        };
        channelsItemAnimator.setSupportsChangeAnimations(false);
        channelsItemAnimator.setDelayAnimations(false);
        channelsItemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        channelsItemAnimator.setDurations(350);

        channelsSearchListView = new BlurredRecyclerView(context);
        channelsSearchListView.setItemAnimator(channelsItemAnimator);
        channelsSearchListView.setPivotY(0);
        channelsSearchListView.setVerticalScrollBarEnabled(true);
        channelsSearchListView.setInstantClick(true);
        channelsSearchListView.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
        channelsSearchListView.setLayoutManager(channelsSearchLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        channelsSearchListView.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA);

        loadingView = new FlickerLoadingView(context);
        loadingView.setViewType(1);
        channelsEmptyView = new StickerEmptyView(context, loadingView, StickerEmptyView.STICKER_TYPE_SEARCH) {
            @Override
            public void setVisibility(int visibility) {
                if (noMediaFiltersSearchView.getTag() != null) {
                    super.setVisibility(View.GONE);
                    return;
                }
                super.setVisibility(visibility);
            }
        };
        channelsEmptyView.title.setText(getString(R.string.NoResult));
        channelsEmptyView.subtitle.setVisibility(View.GONE);
        channelsEmptyView.setVisibility(View.GONE);
        channelsEmptyView.addView(loadingView, 0);
        channelsEmptyView.showProgress(true, false);
        channelsSearchContainer.addView(channelsEmptyView);
        channelsSearchContainer.addView(channelsSearchListView);
        channelsSearchListView.setEmptyView(channelsEmptyView);
        channelsSearchListView.setAdapter(channelsSearchAdapter = new DialogsChannelsAdapter(channelsSearchListView, context, currentAccount, folderId, null) {
            @Override
            public void update(boolean animated) {
                super.update(animated);
                channelsEmptyView.showProgress(loadingMessages || loadingChannels || messages == null || !messages.isEmpty() || searchMyChannels == null || !searchMyChannels.isEmpty() || searchChannels == null || !searchChannels.isEmpty() || searchRecommendedChannels == null || !searchRecommendedChannels.isEmpty(), animated);
                if (TextUtils.isEmpty(query)) {
                    channelsEmptyView.title.setText(getString(R.string.NoChannelsTitle));
                    channelsEmptyView.subtitle.setVisibility(View.VISIBLE);
                    channelsEmptyView.subtitle.setText(getString(R.string.NoChannelsMessage));
                } else {
                    channelsEmptyView.title.setText(getString(R.string.NoResult));
                    channelsEmptyView.subtitle.setVisibility(View.GONE);
                }
            }

            @Override
            protected void hideKeyboard() {
                AndroidUtilities.hideKeyboard(fragment.getParentActivity().getCurrentFocus());
            }
        });
        channelsSearchListView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    AndroidUtilities.hideKeyboard(fragment.getParentActivity().getCurrentFocus());
                }
            }
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                channelsSearchAdapter.checkBottom();
            }
        });


        botsSearchContainer = new FrameLayout(context);

        botsItemAnimator = new DefaultItemAnimator() {
            @Override
            protected void onMoveAnimationUpdate(RecyclerView.ViewHolder holder) {
                super.onMoveAnimationUpdate(holder);
                invalidate();
            }
        };
        botsItemAnimator.setSupportsChangeAnimations(false);
        botsItemAnimator.setDelayAnimations(false);
        botsItemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        botsItemAnimator.setDurations(350);

        botsSearchListView = new BlurredRecyclerView(context);
        botsSearchListView.setItemAnimator(botsItemAnimator);
        botsSearchListView.setPivotY(0);
        botsSearchListView.setVerticalScrollBarEnabled(true);
        botsSearchListView.setInstantClick(true);
        botsSearchListView.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
        botsSearchListView.setLayoutManager(botsSearchLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        botsSearchListView.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA);

        loadingView = new FlickerLoadingView(context);
        loadingView.setViewType(1);
        botsEmptyView = new StickerEmptyView(context, loadingView, StickerEmptyView.STICKER_TYPE_SEARCH) {
            @Override
            public void setVisibility(int visibility) {
                if (noMediaFiltersSearchView.getTag() != null) {
                    super.setVisibility(View.GONE);
                    return;
                }
                super.setVisibility(visibility);
            }
        };
        botsEmptyView.title.setText(getString(R.string.NoResult));
        botsEmptyView.subtitle.setVisibility(View.GONE);
        botsEmptyView.setVisibility(View.GONE);
        botsEmptyView.addView(loadingView, 0);
        botsEmptyView.showProgress(true, false);
        botsSearchContainer.addView(botsEmptyView);
        botsSearchContainer.addView(botsSearchListView);
        botsSearchListView.setEmptyView(botsEmptyView);
        botsSearchListView.setAdapter(botsSearchAdapter = new DialogsBotsAdapter(botsSearchListView, context, currentAccount, folderId, false, null) {
            @Override
            public void update(boolean animated) {
                super.update(animated);
                botsEmptyView.showProgress(loadingMessages || loadingBots || searchMessages == null || !searchMessages.isEmpty(), animated);
                botsEmptyView.title.setText(getString(R.string.NoResult));
                botsEmptyView.subtitle.setVisibility(View.GONE);
            }

            @Override
            protected void hideKeyboard() {
                AndroidUtilities.hideKeyboard(fragment.getParentActivity().getCurrentFocus());
            }
        });
        botsSearchListView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    AndroidUtilities.hideKeyboard(fragment.getParentActivity().getCurrentFocus());
                }
            }
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                botsSearchAdapter.checkBottom();
            }
        });

        hashtagSearchContainer = new FrameLayout(context);

        hashtagItemAnimator = new DefaultItemAnimator() {
            @Override
            protected void onMoveAnimationUpdate(RecyclerView.ViewHolder holder) {
                super.onMoveAnimationUpdate(holder);
                invalidate();
            }
        };
        hashtagItemAnimator.setSupportsChangeAnimations(false);
        hashtagItemAnimator.setDelayAnimations(false);
        hashtagItemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        hashtagItemAnimator.setDurations(350);

        hashtagSearchListView = new BlurredRecyclerView(context);
        hashtagSearchListView.setItemAnimator(hashtagItemAnimator);
        hashtagSearchListView.setPivotY(0);
        hashtagSearchListView.setVerticalScrollBarEnabled(true);
        hashtagSearchListView.setInstantClick(true);
        hashtagSearchListView.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
        hashtagSearchListView.setLayoutManager(hashtagSearchLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        hashtagSearchListView.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA);

        loadingView = new FlickerLoadingView(context);
        loadingView.setViewType(1);
        hashtagEmptyView = new StickerEmptyView(context, loadingView, StickerEmptyView.STICKER_TYPE_SEARCH) {
            @Override
            public void setVisibility(int visibility) {
                if (noMediaFiltersSearchView.getTag() != null) {
                    super.setVisibility(View.GONE);
                    return;
                }
                super.setVisibility(visibility);
            }
        };
        hashtagEmptyView.title.setText(getString(R.string.NoResult));
        hashtagEmptyView.subtitle.setVisibility(View.GONE);
        hashtagEmptyView.setVisibility(View.GONE);
        hashtagEmptyView.addView(loadingView, 0);
        hashtagEmptyView.showProgress(true, false);
        hashtagSearchContainer.addView(hashtagEmptyView);
        hashtagSearchContainer.addView(hashtagSearchListView);
        hashtagSearchListView.setEmptyView(hashtagEmptyView);
        hashtagSearchListView.setAdapter(hashtagSearchAdapter = new HashtagsSearchAdapter(hashtagSearchListView, context, currentAccount, folderId, null) {
            @Override
            public void update(boolean animated) {
                super.update(animated);
                hashtagEmptyView.showProgress(false, animated);
                hashtagEmptyView.title.setText(getString(R.string.NoResult));
                hashtagEmptyView.subtitle.setVisibility(View.GONE);
            }

            @Override
            protected void scrollToTop(boolean ifAtTop) {
                if (ifAtTop && hashtagSearchListView.canScrollVertically(-1)) return;
                hashtagSearchLayoutManager.scrollToPositionWithOffset(0, 0);
            }
        });
        hashtagSearchListView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    AndroidUtilities.hideKeyboard(fragment.getParentActivity().getCurrentFocus());
                }
            }
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                hashtagSearchAdapter.checkBottom();
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
        updateTabs(false);
    }

    public void updateTabs(boolean animated) {
        viewPagerAdapter.updateItems();
        fillTabs(animated);
        if (tabsView != null) {
            tabsView.finishAddingTabs();
        }
    }

    public boolean includeFolder() {
        for (int i = 0; i < currentSearchFilters.size(); i++) {
            FiltersView.MediaFilterData data = currentSearchFilters.get(i);
            if (data.filterType == FiltersView.FILTER_TYPE_ARCHIVE) {
                return true;
            }
        }
        return false;
    }

    private void search(View view, int position, String query, boolean reset) {
        if (TextUtils.isEmpty(query)) {
            emptyView.subtitle.setVisibility(View.GONE);
        } else {
            emptyView.subtitle.setVisibility(View.VISIBLE);
            emptyView.subtitle.setText(LocaleController.formatString(R.string.NoResultFoundFor2, query));
        }
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

        if (hashtagSearchAdapter.getHashtag(query) == null) {
            collapsePublicPosts();
        }

        if (view == channelsSearchContainer) {
            MessagesController.getInstance(currentAccount).getChannelRecommendations(0);
            channelsSearchAdapter.search(query);
            channelsEmptyView.setKeyboardHeight(keyboardSize, false);
        } else if (view == botsSearchContainer) {
            botsSearchAdapter.search(query);
            botsEmptyView.setKeyboardHeight(keyboardSize, false);
            if (TextUtils.isEmpty(query)) {
                botsSearchAdapter.checkBottom();
            }
        } else if (view == hashtagSearchContainer) {
            if (hashtagSearchAdapter.getHashtag(query) == null) {
                return;
            }
            if (reset) {
                hashtagSearchLayoutManager.scrollToPositionWithOffset(0, 0);
            }
            hashtagSearchAdapter.search(query);
            hashtagEmptyView.setKeyboardHeight(keyboardSize, false);
        } else if (view == searchContainer) {
            if (dialogId == 0 && minDate == 0 && maxDate == 0 || forumDialogId != 0) {
                lastSearchScrolledToTop = false;
                dialogsSearchAdapter.searchDialogs(query, includeFolder ? 1 : 0, true);
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
        collapsePublicPosts();
    }

    public void collapsePublicPosts() {
        if (!expandedPublicPosts) return;
        expandedPublicPosts = false;
        updateTabs();
        if (tabsView != null && tabsView.getCurrentTabId() != 0) {
            tabsView.scrollToTab(0, 0);
        }
        if (dialogsSearchAdapter != null) {
            dialogsSearchAdapter.searchDialogs(lastSearchString, includeFolder() ? 1 : 0, true);
        }
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
            selectedMessagesCountTextView.setTypeface(AndroidUtilities.bold());
            selectedMessagesCountTextView.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
            actionMode.addView(selectedMessagesCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 72, 0, 0, 0));
            selectedMessagesCountTextView.setOnTouchListener((v, event) -> true);

            speedItem = actionMode.addItemWithWidth(speedItemId, R.drawable.avd_speed, AndroidUtilities.dp(54), getString(R.string.AccDescrPremiumSpeed));
            speedItem.getIconView().setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon), PorterDuff.Mode.SRC_IN));
            gotoItem = actionMode.addItemWithWidth(gotoItemId, R.drawable.msg_message, AndroidUtilities.dp(54), getString(R.string.AccDescrGoToMessage));
            forwardItem = actionMode.addItemWithWidth(forwardItemId, R.drawable.msg_forward, AndroidUtilities.dp(54), getString(R.string.Forward));
            deleteItem = actionMode.addItemWithWidth(deleteItemId, R.drawable.msg_delete, AndroidUtilities.dp(54), getString(R.string.Delete));
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
                    .append(getString(R.string.RemoveDocumentsAlertMessage));

            builder.setMessage(spannableStringBuilder);
            builder.setNegativeButton(getString(R.string.Cancel), (dialogInterface, i) -> dialogInterface.dismiss());
            builder.setPositiveButton(getString(R.string.Delete), (dialogInterface, i) -> {
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
            fragment.setDelegate((fragment1, dids, message, param, notify, scheduleDate, topicsFragment) -> {
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
                        AccountInstance.getInstance(currentAccount).getSendMessagesHelper().sendMessage(fmessages, did, false,false, true, 0, 0);
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
        if (channelsSearchLayoutManager != null) {
            channelsSearchLayoutManager.scrollToPositionWithOffset(0, 0);
        }
        if (botsSearchLayoutManager != null) {
            botsSearchLayoutManager.scrollToPositionWithOffset(0, 0);
        }
        if (hashtagSearchLayoutManager != null) {
            hashtagSearchLayoutManager.scrollToPositionWithOffset(0, 0);
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
            } else if (getChildAt(i) == channelsSearchContainer) {
                channelsEmptyView.setKeyboardHeight(keyboardSize, animated);
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
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.channelRecommendationsLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.dialogDeleted);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.dialogsNeedReload);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.reloadWebappsHints);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.storiesListUpdated);
        attached = true;

        if (channelsSearchAdapter != null) {
            channelsSearchAdapter.update(false);
        }
        if (botsSearchAdapter != null) {
            botsSearchAdapter.update(false);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attached = false;
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.channelRecommendationsLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.dialogDeleted);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.dialogsNeedReload);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.reloadWebappsHints);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.storiesListUpdated);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.channelRecommendationsLoaded) {
            channelsEmptyView.showProgress(MessagesController.getInstance(currentAccount).getChannelRecommendations(0) != null, true);
            channelsSearchAdapter.updateMyChannels();
            channelsSearchAdapter.update(true);
        } else if (id == NotificationCenter.dialogDeleted || id == NotificationCenter.dialogsNeedReload) {
            channelsSearchAdapter.updateMyChannels();
            channelsSearchAdapter.update(true);
        } else if (id == NotificationCenter.reloadWebappsHints) {
            botsSearchAdapter.update(true);
        } else if (id == NotificationCenter.storiesListUpdated) {
            if (args[0] == hashtagSearchAdapter.list) {
                hashtagSearchAdapter.update(true);
            }
        }
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
        setPosition((expandedPublicPosts ? 1 : 0) + 4);
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
        private final static int CHANNELS_TYPE = 1;
        private final static int DOWNLOADS_TYPE = 2;
        private final static int FILTER_TYPE = 3;
        private final static int BOTS_TYPE = 4;
        private final static int PUBLIC_POSTS_TYPE = 5;

        public ViewPagerAdapter() {
            updateItems();
        }

        public void updateItems() {
            items.clear();
            items.add(new Item(DIALOGS_TYPE));
            if (expandedPublicPosts) {
                items.add(new Item(PUBLIC_POSTS_TYPE));
            }
            items.add(new Item(CHANNELS_TYPE));
            items.add(new Item(BOTS_TYPE));
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
                return getString(R.string.SearchAllChatsShort);
            } else if (items.get(position).type == CHANNELS_TYPE) {
                return getString(R.string.ChannelsTab);
            } else if (items.get(position).type == BOTS_TYPE) {
                return getString(R.string.AppsTab);
            } else if (items.get(position).type == DOWNLOADS_TYPE) {
                return getString(R.string.DownloadsTabs);
            } else if (items.get(position).type == PUBLIC_POSTS_TYPE) {
                return getString(R.string.PublicPostsTabs);
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
            } else if (viewType == 3) {
                return channelsSearchContainer;
            } else if (viewType == 4) {
                return botsSearchContainer;
            } else if (viewType == 5) {
                return hashtagSearchContainer;
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
            if (items.get(position).type == CHANNELS_TYPE) {
                return 3;
            }
            if (items.get(position).type == BOTS_TYPE) {
                return 4;
            }
            if (items.get(position).type == DOWNLOADS_TYPE) {
                return 2;
            }
            if (items.get(position).type == PUBLIC_POSTS_TYPE) {
                return 5;
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

    public void onShown() {
        if (dialogsSearchAdapter != null) {
            dialogsSearchAdapter.resetFilter();
        }
    }
}
