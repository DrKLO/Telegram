package org.telegram.ui.Stories;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.util.Consumer;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.FixedHeightEmptyCell;
import org.telegram.ui.Cells.ReactedUserHolderView;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.CustomPopupMenu;
import org.telegram.ui.Components.EmojiPacksAlert;
import org.telegram.ui.Components.FillLastLinearLayoutManager;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.MessageContainsEmojiButton;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.ui.Components.RecyclerAnimationScrollHelper;
import org.telegram.ui.Components.RecyclerItemsEnterAnimator;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ReplaceableIconDrawable;
import org.telegram.ui.Components.SearchField;
import org.telegram.ui.Components.StickerEmptyView;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.RecyclerListViewScroller;
import org.telegram.ui.Stories.recorder.StoryPrivacyBottomSheet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;

public class SelfStoryViewsPage extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private final View shadowView;
    private final View shadowView2;
    private final FrameLayout topViewsContainer;
    private final RecyclerListViewScroller scroller;
    private int TOP_PADDING = 96;
    private static final int FIRST_PADDING_ITEM = 0;
    private static final int USER_ITEM = 1;
    private static final int BUTTON_PADDING = 3;
    private static final int FLICKER_LOADING_ITEM = 4;
    private static final int EMPTY_VIEW = 5;
    private static final int EMPTY_VIEW_SEARCH = 7;
    private static final int EMPTY_VIEW_NO_CONTACTS = 8;
    private static final int FLICKER_LOADING_ITEM_FULL = 6;
    private static final int LAST_PADDING_VIEW = 9;
    private static final int EMPTY_VIEW_SERVER_CANT_RETURN = 10;
    private static final int SUBSCRIBE_TO_PREMIUM_TEXT_HINT = 11;
    private static final int SERVER_CANT_RETURN_TEXT_HINT = 12;

    private CustomPopupMenu popupMenu;

    private final TextView titleView;
    private int measuerdHeight;

    RecyclerListView recyclerListView;
    RecyclerAnimationScrollHelper scrollHelper;
    Theme.ResourcesProvider resourcesProvider;
    int currentAccount;
    ListAdapter listAdapter;

    public FillLastLinearLayoutManager layoutManager;
    SelfStoryViewsView.StoryItemInternal storyItem;
    ViewsModel currentModel;
    ViewsModel defaultModel;
    private boolean isAttachedToWindow;
    RecyclerItemsEnterAnimator recyclerItemsEnterAnimator;
    StoryViewer storyViewer;
    SearchField searchField;
    final FiltersState sharedFilterState;
    Consumer<SelfStoryViewsPage> onSharedStateChanged;
    final FiltersState state = new FiltersState();
    HeaderView headerView;
    boolean isSearchDebounce;
    private boolean showSearch;
    private boolean showReactionsSort;
    private boolean showContactsFilter;
    Drawable shadowDrawable;
    private boolean checkAutoscroll;
    private boolean showServerErrorText;
    private long dialogId;

    private boolean isStoryShownToUser(TL_stories.StoryView view) {
        if (view == null) {
            return true;
        }

        if (MessagesController.getInstance(currentAccount).getStoriesController().isBlocked(view)) {
            return false;
        }

        if (MessagesController.getInstance(currentAccount).blockePeers.indexOfKey(view.user_id) >= 0) {
            return false;
        }

        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(view.user_id);

        if (storyItem != null) {
            if (storyItem.storyItem != null) {
                if (storyItem.storyItem.parsedPrivacy == null) {
                    storyItem.storyItem.parsedPrivacy = new StoryPrivacyBottomSheet.StoryPrivacy(currentAccount, storyItem.storyItem.privacy);
                }
                return storyItem.storyItem.parsedPrivacy.containsUser(user);
            } else if (storyItem.uploadingStory != null && storyItem.uploadingStory.entry != null && storyItem.uploadingStory.entry.privacy != null) {
                return storyItem.uploadingStory.entry.privacy.containsUser(user);
            }
        }

        return true;
    }

    public SelfStoryViewsPage(StoryViewer storyViewer, @NonNull Context context, FiltersState sharedFilterState, Consumer<SelfStoryViewsPage> onSharedStateChanged) {
        super(context);
        this.sharedFilterState = sharedFilterState;
        //this.sharedFilterState = null;
        this.onSharedStateChanged = onSharedStateChanged;
        this.resourcesProvider = storyViewer.resourcesProvider;
        this.storyViewer = storyViewer;

        // state.set(sharedFilterState);
        currentAccount = storyViewer.currentAccount;

        titleView = new TextView(context);
        titleView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleView.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(6), AndroidUtilities.dp(21), AndroidUtilities.dp(8));

        headerView = new HeaderView(getContext());

        recyclerListView = new RecyclerListViewInner(context, resourcesProvider) {

            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                measuerdHeight = MeasureSpec.getSize(heightSpec);
                super.onMeasure(widthSpec, heightSpec);
            }


        };
        recyclerListView.setClipToPadding(false);
        recyclerItemsEnterAnimator = new RecyclerItemsEnterAnimator(recyclerListView, true);
        recyclerListView.setLayoutManager(layoutManager = new FillLastLinearLayoutManager(context, 0, recyclerListView));
        recyclerListView.setNestedScrollingEnabled(true);
        recyclerListView.setAdapter(listAdapter = new ListAdapter());
        scrollHelper = new RecyclerAnimationScrollHelper(recyclerListView, layoutManager);
        scrollHelper.setScrollListener(new RecyclerAnimationScrollHelper.ScrollListener() {
            @Override
            public void onScroll() {
                SelfStoryViewsPage.this.invalidate();
            }
        });

        addView(recyclerListView);
        scroller = new RecyclerListViewScroller(recyclerListView);
        recyclerListView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                checkLoadMore();
                SelfStoryViewsPage.this.invalidate();
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerListView.SCROLL_STATE_IDLE) {
                    checkAutoscroll = true;
                    invalidate();
                }
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    checkAutoscroll = false;
                    scroller.cancel();
                    AndroidUtilities.hideKeyboard(SelfStoryViewsPage.this);
                }
            }
        });
        recyclerListView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= listAdapter.items.size()) {
                return;
            }
            Item item = listAdapter.items.get(position);
            if (item.view instanceof TL_stories.TL_storyView) {
                storyViewer.presentFragment(ProfileActivity.of(item.view.user_id));
            } else if (item.view instanceof TL_stories.TL_storyViewPublicRepost) {
                storyViewer.fragment.createOverlayStoryViewer().open(getContext(), ((TL_stories.TL_storyViewPublicRepost) item.view).story, StoriesListPlaceProvider.of(recyclerListView));
            } else if (item.reaction instanceof TL_stories.TL_storyReaction) {
                storyViewer.presentFragment(ProfileActivity.of(DialogObject.getPeerDialogId(item.reaction.peer_id)));
            } else if (item.reaction instanceof TL_stories.TL_storyReactionPublicRepost) {
                storyViewer.fragment.createOverlayStoryViewer().open(getContext(), ((TL_stories.TL_storyReactionPublicRepost) item.reaction).story, StoriesListPlaceProvider.of(recyclerListView));
            } else if (item.reaction instanceof TL_stories.TL_storyReactionPublicForward || item.view instanceof TL_stories.TL_storyViewPublicForward) {
                TLRPC.Message message;
                if (item.reaction instanceof TL_stories.TL_storyReactionPublicForward) {
                    message = item.reaction.message;
                } else {
                    message = item.view.message;
                }
                Bundle args = new Bundle();
                long dialogId = DialogObject.getPeerDialogId(message.peer_id);
                if (dialogId >= 0) {
                    args.putLong("user_id", dialogId);
                } else {
                    args.putLong("chat_id", -dialogId);
                }
                args.putInt("message_id", message.id);
                ChatActivity chatActivity = new ChatActivity(args);
                storyViewer.presentFragment(chatActivity);
            }
        });
        recyclerListView.setOnItemLongClickListener(new RecyclerListView.OnItemLongClickListener() {
            @Override
            public boolean onItemClick(View view, int position) {
                if (!(view instanceof ReactedUserHolderView)) {
                    return false;
                }
                ReactedUserHolderView cell = (ReactedUserHolderView) view;
                if (storyViewer == null || storyViewer.containerView == null) {
                    return false;
                }
                TL_stories.StoryView viewUser = listAdapter.items.get(position).view;
                if (viewUser == null) {
                    return false;
                }

                MessagesController messagesController = MessagesController.getInstance(currentAccount);

                TLRPC.User user = messagesController.getUser(viewUser.user_id);
                if (user == null) {
                    return false;
                }

                boolean isBlocked = messagesController.blockePeers.indexOfKey(user.id) >= 0;
                boolean isContact = user != null && (user.contact || ContactsController.getInstance(currentAccount).contactsDict.get(user.id) != null);
                boolean storiesShown = isStoryShownToUser(viewUser);
                boolean storiesBlocked = messagesController.getStoriesController().isBlocked(viewUser);

                String firstName = TextUtils.isEmpty(user.first_name) ? (TextUtils.isEmpty(user.last_name) ? "" : user.last_name) : user.first_name;
                int index;
                if ((index = firstName.indexOf(" ")) > 2) {
                    firstName = firstName.substring(0, index);
                }
                final String firstNameFinal = firstName;

                ItemOptions itemOptions = ItemOptions.makeOptions(storyViewer.containerView, resourcesProvider, view)
                        .setGravity(Gravity.LEFT).ignoreX()
                        .setScrimViewBackground(new ColorDrawable(Theme.getColor(Theme.key_dialogBackground, resourcesProvider)))
                        .setDimAlpha(0x85)
                        .addIf(storiesShown && !storiesBlocked && !isBlocked, R.drawable.msg_stories_myhide, LocaleController.formatString(R.string.StoryHideFrom, firstNameFinal), () -> {
                            messagesController.getStoriesController().updateBlockUser(user.id, true);
                            BulletinFactory.of(SelfStoryViewsPage.this, resourcesProvider)
                                    .createSimpleBulletin(R.raw.ic_ban, LocaleController.formatString(R.string.StoryHidFromToast, firstNameFinal))
                                    .show();
                            cell.animateAlpha(isStoryShownToUser(viewUser) ? 1 : 0.5f, true);
                        }).makeMultiline(false).cutTextInFancyHalf()
                        .addIf(storiesBlocked && !isBlocked, R.drawable.msg_menu_stories, LocaleController.formatString(R.string.StoryShowBackTo, firstNameFinal), () -> {
                            messagesController.getStoriesController().updateBlockUser(user.id, false);
                            BulletinFactory.of(SelfStoryViewsPage.this, resourcesProvider)
                                    .createSimpleBulletin(R.raw.contact_check, LocaleController.formatString(R.string.StoryShownBackToToast, firstNameFinal))
                                    .show();
                            cell.animateAlpha(isStoryShownToUser(viewUser) ? 1 : 0.5f, true);
                        }).makeMultiline(false).cutTextInFancyHalf()
//                        .addIf(!isContact, R.drawable.msg_contact_add, LocaleController.getString(R.string.AddContact), () -> {
//                            messagesController.getStoriesController().updateBlockUser(user.id, false);
//                            ContactsController.getInstance(currentAccount).addContact(user, false);
//                            user.contact = true;
//                            BulletinFactory.of(SelfStoryViewsPage.this, resourcesProvider)
//                                    .createSimpleBulletin(R.raw.contact_check, LocaleController.formatString(R.string.AddContactToast, firstNameFinal))
//                                    .show();
//                            cell.animateAlpha(isStoryShownToUser(viewUser) ? 1 : 0.5f, true);
//                        })
                        .addIf(!isContact && !isBlocked, R.drawable.msg_user_remove, LocaleController.getString(R.string.BlockUser), true, () -> {
                            messagesController.blockPeer(user.id);
                            BulletinFactory.of(SelfStoryViewsPage.this, resourcesProvider).createBanBulletin(true).show();
                            cell.animateAlpha(isStoryShownToUser(viewUser) ? 1 : 0.5f, true);
                        })
                        .addIf(!isContact && isBlocked, R.drawable.msg_block, LocaleController.getString(R.string.Unblock), () -> {
                            messagesController.getStoriesController().updateBlockUser(user.id, false);
                            messagesController.unblockPeer(user.id);
                            BulletinFactory.of(SelfStoryViewsPage.this, resourcesProvider).createBanBulletin(false).show();
                            cell.animateAlpha(isStoryShownToUser(viewUser) ? 1 : 0.5f, true);
                        })
                        .addIf(isContact, R.drawable.msg_user_remove, LocaleController.getString(R.string.StoryDeleteContact), true, () -> {
                            ArrayList<TLRPC.User> users = new ArrayList<>();
                            users.add(user);
                            ContactsController.getInstance(currentAccount).deleteContact(users, false);
                            BulletinFactory.of(SelfStoryViewsPage.this, resourcesProvider)
                                    .createSimpleBulletin(R.raw.ic_ban, LocaleController.formatString(R.string.DeletedFromYourContacts, firstNameFinal))
                                    .show();
                            cell.animateAlpha(isStoryShownToUser(viewUser) ? 1 : 0.5f, true);
                        });

                if (viewUser.reaction instanceof TLRPC.TL_reactionCustomEmoji) {
                    TLRPC.TL_reactionCustomEmoji customEmoji = (TLRPC.TL_reactionCustomEmoji) viewUser.reaction;
                    TLRPC.InputStickerSet inputStickerSet = AnimatedEmojiDrawable.getDocumentFetcher(currentAccount).findStickerSet(customEmoji.document_id);
                    if (inputStickerSet != null) {
                        itemOptions.addGap();
                        ArrayList<TLRPC.InputStickerSet> arr = new ArrayList<TLRPC.InputStickerSet>();
                        arr.add(inputStickerSet);
                        MessageContainsEmojiButton button = new MessageContainsEmojiButton(currentAccount, getContext(), resourcesProvider, arr, MessageContainsEmojiButton.SINGLE_REACTION_TYPE);
                        button.setOnClickListener(v -> {
                            new EmojiPacksAlert(new BaseFragment() {
                                @Override
                                public int getCurrentAccount() {
                                    return currentAccount;
                                }

                                @Override
                                public Context getContext() {
                                    return SelfStoryViewsPage.this.getContext();
                                }

                                @Override
                                public Theme.ResourcesProvider getResourceProvider() {
                                    return resourcesProvider;
                                }
                            }, getContext(), resourcesProvider, arr).show();
                            itemOptions.dismiss();
                        });
                        itemOptions.addView(button);
                    }
                }

                itemOptions.show();

                try {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                } catch (Exception ignore) {
                }

                return true;
            }
        });

        listAdapter.updateRows();

        topViewsContainer = new FrameLayout(getContext());
        shadowView = new View(getContext());
        shadowView.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Theme.getColor(Theme.key_dialogBackground, resourcesProvider), Color.TRANSPARENT}));
        topViewsContainer.addView(shadowView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 8, 0, 0, TOP_PADDING - 8, 0, 0));

        shadowView2 = new View(getContext());
        shadowView2.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
        topViewsContainer.addView(shadowView2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 10, 0, 0, TOP_PADDING - 17, 0, 0));

        topViewsContainer.addView(headerView);
        topViewsContainer.addView(titleView);
        searchField = new SearchField(getContext(), true, 13, resourcesProvider) {
            Runnable runnable;

            @Override
            public void onTextChange(String text) {
                if (runnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(runnable);
                }
                runnable = () -> {
                    runnable = null;
                    isSearchDebounce = false;
                    state.searchQuery = text.toLowerCase();
                    reload();
                    //layoutManager.scrollToPositionWithOffset(0, -recyclerListView.getPaddingTop());
                };
                if (!TextUtils.isEmpty(text)) {
                    AndroidUtilities.runOnUIThread(runnable, 300);
                } else {
                    runnable.run();
                }
                if (runnable != null && !isSearchDebounce) {
                    isSearchDebounce = true;
                    listAdapter.updateRows();
                    layoutManager.scrollToPositionWithOffset(0, -recyclerListView.getPaddingTop());
                }
            }
        };
        searchField.setHint(LocaleController.getString("Search", R.string.Search));
        topViewsContainer.addView(searchField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 36, 0, 0));

        addView(topViewsContainer);
    }


    @Override
    protected void dispatchDraw(Canvas canvas) {
        int minPosition = -1;
        View minView = null;
        for (int i = 0; i < recyclerListView.getChildCount(); i++) {
            View child = recyclerListView.getChildAt(i);
            int childPosition = recyclerListView.getChildLayoutPosition(child);
            if (childPosition < minPosition || minPosition == -1) {
                minPosition = childPosition;
                minView = child;
            }
        }
        int paddingTop;
        if (minPosition == 0) {
            paddingTop = (int) Math.max(0, minView.getY());
        } else if (minPosition > 0) {
            paddingTop = 0;
        } else {
            paddingTop = recyclerListView.getPaddingTop();
        }
        if (topViewsContainer.getTranslationY() != paddingTop) {
            topViewsContainer.setTranslationY(paddingTop);
            onTopOffsetChanged(paddingTop);
        }
        shadowDrawable.setBounds(-AndroidUtilities.dp(6), paddingTop, getMeasuredWidth() + AndroidUtilities.dp(6), getMeasuredHeight());
        shadowDrawable.draw(canvas);
        if (checkAutoscroll) {
            checkAutoscroll = false;
            if (topViewsContainer.getTranslationY() != 0 && topViewsContainer.getTranslationY() != recyclerListView.getPaddingTop()) {
                if (topViewsContainer.getTranslationY() > recyclerListView.getPaddingTop() / 2f) {
                    scroller.smoothScrollBy((int) -(recyclerListView.getPaddingTop() - topViewsContainer.getTranslationY()));
                } else {
                    scroller.smoothScrollBy((int) topViewsContainer.getTranslationY());
                }

            }
        }
        super.dispatchDraw(canvas);
    }

    public void onTopOffsetChanged(int paddingTop) {

    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getY() < topViewsContainer.getTranslationY()) {
            return false;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getY() < topViewsContainer.getTranslationY()) {
            return false;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == recyclerListView) {
            canvas.save();
            canvas.clipRect(0, AndroidUtilities.dp(TOP_PADDING), getMeasuredWidth(), getMeasuredHeight());
            super.drawChild(canvas, child, drawingTime);
            canvas.restore();
            return true;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    private void checkLoadMore() {
        if (currentModel != null && layoutManager.findLastVisibleItemPosition() > listAdapter.getItemCount() - 10) {
            currentModel.loadNext();
        }
    }

    public void setStoryItem(long dialogId, SelfStoryViewsView.StoryItemInternal storyItem) {
        this.dialogId = dialogId;
        this.storyItem = storyItem;
        updateViewsVisibility();
        updateViewState(false);
    }

    private void updateViewsVisibility() {
        this.showSearch = false;
        this.showContactsFilter = false;
        this.showReactionsSort = false;
        boolean forceHideTitle = false;
        if (storyItem.storyItem != null) {
            TL_stories.StoryItem serverItem = storyItem.storyItem;
            if (serverItem.views != null) {
                showSearch = serverItem.views.views_count >= 15;
                showReactionsSort = serverItem.views.reactions_count >= (BuildVars.DEBUG_PRIVATE_VERSION ? 5 : 10);
                showContactsFilter = serverItem.dialogId >= 0 && serverItem.views.views_count >= 20 && !serverItem.contacts && !serverItem.close_friends && !serverItem.selected_contacts;
            }
            SparseArray<ViewsModel> models = MessagesController.getInstance(currentAccount).storiesController.selfViewsModel.get(serverItem.dialogId);
            defaultModel = models != null ? models.get(serverItem.id) : null;
            int totalCount = serverItem.views == null ? 0 : serverItem.views.views_count;
            if (defaultModel == null || !defaultModel.isChannel && defaultModel.totalCount != totalCount) {
                if (defaultModel != null) {
                    defaultModel.release();
                }
                defaultModel = new ViewsModel(currentAccount, dialogId, serverItem, true);
                defaultModel.reloadIfNeed(state, showContactsFilter, showReactionsSort);
                defaultModel.loadNext();
                if (models != null) {
                    models.put(serverItem.id, defaultModel);
                } else {
                    models = new SparseArray<>();
                    models.put(serverItem.id, defaultModel);
                    MessagesController.getInstance(currentAccount).storiesController.selfViewsModel.put(serverItem.dialogId, models);
                }
            } else {
                defaultModel.reloadIfNeed(state, showContactsFilter, showReactionsSort);
            }
            if (currentModel != null) {
                currentModel.removeListener(this);
            }
            currentModel = defaultModel;
            if (currentModel != null && isAttachedToWindow) {
                currentModel.addListener(this);
            }
            if ((currentModel != null && currentModel.isExpiredViews && !UserConfig.getInstance(currentAccount).isPremium()) || (!currentModel.loading && !currentModel.hasNext && currentModel.views.isEmpty() && currentModel.reactions.isEmpty() && TextUtils.isEmpty(currentModel.state.searchQuery))) {
                showSearch = false;
                showReactionsSort = false;
                showContactsFilter = false;
                titleView.setText(LocaleController.getString(currentModel.isChannel ? R.string.Reactions : R.string.Viewers));
                searchField.setVisibility(View.GONE);
                headerView.setVisibility(View.GONE);
                TOP_PADDING = 46;
            } else if (serverItem.views == null || serverItem.views.views_count == 0) {
                showSearch = false;
                showReactionsSort = false;
                showContactsFilter = false;
                titleView.setText(LocaleController.getString(currentModel.isChannel ? R.string.Reactions : R.string.Viewers));
                searchField.setVisibility(View.GONE);
                headerView.setVisibility(View.GONE);
                TOP_PADDING = 46;
            } else {
                headerView.setVisibility(View.VISIBLE);
                if (currentModel.showReactionOnly) {
                    titleView.setText(LocaleController.getString(currentModel.isChannel ? R.string.Reactions : R.string.Viewers));
                    showSearch = false;
                    showReactionsSort = false;
                    showContactsFilter = false;
                } else {
                    if (currentModel.getCount() < 20 && currentModel.getCount() < serverItem.views.views_count && !currentModel.loading && !currentModel.hasNext) {
                        showSearch = false;
                        showReactionsSort = false;
                        showContactsFilter = false;
                        showServerErrorText = true;
                    } else {
                        showSearch = !currentModel.isChannel && serverItem.views.views_count >= 15;
                        showReactionsSort = serverItem.views.reactions_count >= (BuildVars.DEBUG_VERSION ? 5 : 10);
                        showContactsFilter = serverItem.dialogId >= 0 && serverItem.views.views_count >= 20 && !serverItem.contacts && !serverItem.close_friends && !serverItem.selected_contacts;
                    }
                    titleView.setText(LocaleController.getString(currentModel.isChannel ? R.string.Reactions : R.string.Viewers));
                }
                searchField.setVisibility(showSearch ? View.VISIBLE : View.GONE);
                TOP_PADDING = showSearch ? 96 : 46;

                // titleView.setText(LocaleController.formatPluralStringComma("Views", serverItem.views.views_count));
            }
        } else {
            TOP_PADDING = 46;
            titleView.setText(LocaleController.getString("UploadingStory", R.string.UploadingStory));
            searchField.setVisibility(View.GONE);
            headerView.setVisibility(View.GONE);
        }
        headerView.buttonContainer.setVisibility(showReactionsSort ? View.VISIBLE : View.GONE);
        headerView.allViewersView.setVisibility(showContactsFilter ? View.VISIBLE : View.GONE);
        headerView.contactsViewersView.setVisibility(showContactsFilter ? View.VISIBLE : View.GONE);
        if (!showContactsFilter && !forceHideTitle) {
            titleView.setVisibility(View.VISIBLE);
        } else {
            titleView.setVisibility(View.GONE);
        }

        ((MarginLayoutParams) shadowView.getLayoutParams()).topMargin = AndroidUtilities.dp(TOP_PADDING - 8);
        ((MarginLayoutParams) shadowView2.getLayoutParams()).topMargin = AndroidUtilities.dp(TOP_PADDING - 17);
    }

    public static void preload(int currentAccount, long dialogId, TL_stories.StoryItem storyItem) {
        if (storyItem == null) {
            return;
        }
        SparseArray<ViewsModel> models = MessagesController.getInstance(currentAccount).storiesController.selfViewsModel.get(storyItem.dialogId);
        ViewsModel model = models == null ? null : models.get(storyItem.id);
        int totalCount = storyItem.views == null ? 0 : storyItem.views.views_count;
        if (model == null || model.totalCount != totalCount) {
            if (model != null) {
                model.release();
            }
            model = new ViewsModel(currentAccount, dialogId, storyItem, true);
            model.loadNext();
            if (models == null) {
                MessagesController.getInstance(currentAccount).storiesController.selfViewsModel.put(storyItem.dialogId, models = new SparseArray<>());
            }
            models.put(storyItem.id, model);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        isAttachedToWindow = true;
        if (currentModel != null) {
            currentModel.addListener(this);
            currentModel.animateDateForUsers.clear();
        }
        listAdapter.updateRows();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.storiesUpdated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.storiesBlocklistUpdate);
        Bulletin.addDelegate(this, new Bulletin.Delegate() {
            @Override
            public int getBottomOffset(int tag) {
                return recyclerListView.getPaddingBottom();
            }
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        isAttachedToWindow = false;
        if (currentModel != null) {
            currentModel.removeListener(this);
        }
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.storiesUpdated);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.storiesBlocklistUpdate);
        Bulletin.removeDelegate(this);
    }

    public void onDataRecieved(ViewsModel model) {
      //  NotificationCenter.getInstance(currentAccount).doOnIdle(() -> {
            int oldCount = listAdapter.getItemCount();
            if (TextUtils.isEmpty(state.searchQuery) && !state.contactsOnly) {
                updateViewsVisibility();
            }
            listAdapter.updateRows();
            recyclerItemsEnterAnimator.showItemsAnimated(oldCount - 1);
            checkLoadMore();
     //   });
    }

    public void setListBottomPadding(float bottomPadding) {
        if (bottomPadding != recyclerListView.getPaddingBottom()) {
            recyclerListView.setPadding(0, (int) bottomPadding, 0, 0);
            recyclerListView.requestLayout();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.storiesUpdated) {
            if (storyItem.uploadingStory != null) {
                TL_stories.PeerStories stories = MessagesController.getInstance(currentAccount).storiesController.getStories(UserConfig.getInstance(currentAccount).clientUserId);
                if (stories != null) {
                    for (int i = 0; i < stories.stories.size(); i++) {
                        TL_stories.StoryItem storyItem = stories.stories.get(i);
                        if (storyItem.attachPath != null && storyItem.attachPath.equals(this.storyItem.uploadingStory.path)) {
                            this.storyItem.uploadingStory = null;
                            this.storyItem.storyItem = storyItem;
                            setStoryItem(dialogId, this.storyItem);
                            break;
                        }
                    }
                }
            }
        } else if (id == NotificationCenter.storiesBlocklistUpdate) {
            for (int i = 0; i < recyclerListView.getChildCount(); ++i) {
                View child = recyclerListView.getChildAt(i);
                if (child instanceof ReactedUserHolderView) {
                    int position = recyclerListView.getChildAdapterPosition(child);
                    if (position < 0 || position >= listAdapter.items.size()) {
                        continue;
                    }
                    ((ReactedUserHolderView) child).animateAlpha(isStoryShownToUser(listAdapter.items.get(position).view) ? 1 : .5f, true);
                }
            }
        }
    }

    protected void updateSharedState() {
//        if (sharedFilterState != null) {
//            state.sortByReactions = sharedFilterState.sortByReactions;
//            state.contactsOnly = sharedFilterState.contactsOnly;
//            reload();
//            updateViewState(false);
//        }
    }

    public void setShadowDrawable(Drawable shadowDrawable) {
        this.shadowDrawable = shadowDrawable;
    }

    public void onKeyboardShown() {
        recyclerListView.dispatchTouchEvent(AndroidUtilities.emptyMotionEvent());
        if (topViewsContainer.getTranslationY() != 0) {
            scroller.smoothScrollBy((int) topViewsContainer.getTranslationY(), AdjustPanLayoutHelper.keyboardDuration, AdjustPanLayoutHelper.keyboardInterpolator);
        }
    }

    public boolean onBackPressed() {
        if (popupMenu != null && popupMenu.isShowing()) {
            popupMenu.dismiss();
            return true;
        }
        if (Math.abs(topViewsContainer.getTranslationY() - recyclerListView.getPaddingTop()) > AndroidUtilities.dp(2)) {
            recyclerListView.dispatchTouchEvent(AndroidUtilities.emptyMotionEvent());
            recyclerListView.smoothScrollToPosition(0);
            return true;
        }
        return false;
    }

    public float getTopOffset() {
        return topViewsContainer.getTranslationY();
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        ArrayList<Item> items = new ArrayList<>();

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case FIRST_PADDING_ITEM:
                    view = new View(getContext()) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(TOP_PADDING), MeasureSpec.EXACTLY));
                        }
                    };
                    break;
                case BUTTON_PADDING:
                    view = new FixedHeightEmptyCell(getContext(), 70);
                    break;
                case USER_ITEM:
                    view = new ReactedUserHolderView(ReactedUserHolderView.STYLE_STORY, currentAccount, getContext(), resourcesProvider, false) {
                        @Override
                        public void openStory(long dialogId, Runnable onDone) {
                            BaseFragment lastFragment = LaunchActivity.getLastFragment();
                            if (lastFragment == null) {
                                return;
                            }
                            StoryViewer storyViewer1 = lastFragment.createOverlayStoryViewer();
                            storyViewer1.doOnAnimationReady(onDone);
                            storyViewer1.open(getContext(), dialogId, StoriesListPlaceProvider.of(recyclerListView));
                        }
                    };
                    break;
                case FLICKER_LOADING_ITEM:
                    FlickerLoadingView loadingView = new FlickerLoadingView(getContext(), resourcesProvider);
                    loadingView.setIsSingleCell(true);
                    loadingView.setViewType(FlickerLoadingView.SOTRY_VIEWS_USER_TYPE);
                    loadingView.showDate(false);
                    view = loadingView;
                    break;
                case FLICKER_LOADING_ITEM_FULL:
                    loadingView = new FlickerLoadingView(getContext(), resourcesProvider);
                    loadingView.setIsSingleCell(true);
                    loadingView.setIgnoreHeightCheck(true);
                    loadingView.setItemsCount(20);
                    loadingView.setViewType(FlickerLoadingView.SOTRY_VIEWS_USER_TYPE);
                    loadingView.showDate(false);
                    view = loadingView;
                    break;
                case SERVER_CANT_RETURN_TEXT_HINT:
                case SUBSCRIBE_TO_PREMIUM_TEXT_HINT:
                    LinkSpanDrawable.LinksTextView textView = new LinkSpanDrawable.LinksTextView(getContext());
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                    textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
                    textView.setLinkTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText, resourcesProvider));
                    int padding = AndroidUtilities.dp(16);
                    int paddingHorizontal = AndroidUtilities.dp(21);
                    textView.setPadding(paddingHorizontal, padding, paddingHorizontal, padding);
                    textView.setMaxLines(Integer.MAX_VALUE);
                    textView.setGravity(Gravity.CENTER);
                    textView.setDisablePaddingsOffsetY(true);
                    if (viewType == SUBSCRIBE_TO_PREMIUM_TEXT_HINT) {
                        textView.setText(AndroidUtilities.replaceSingleTag(LocaleController.getString("StoryViewsPremiumHint", R.string.StoryViewsPremiumHint), () -> {
                            showPremiumAlert();
                        }));
                    } else {
                        textView.setText(LocaleController.getString("ServerErrorViewersFull", R.string.ServerErrorViewersFull));
                    }
                    textView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    view = textView;
                    break;
                default:
                case LAST_PADDING_VIEW:
                    view = new View(getContext()) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            int h = layoutManager.getLastItemHeight();
                            if (h >= recyclerListView.getPaddingTop() && !showSearch) {
                                h = 0;
                            }
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
                        }
                    };
                    break;
                case EMPTY_VIEW_NO_CONTACTS:
                case EMPTY_VIEW_SEARCH:
                case EMPTY_VIEW:
                case EMPTY_VIEW_SERVER_CANT_RETURN:
                    int stickerType;
                    if (defaultModel.isExpiredViews) {
                        stickerType = StickerEmptyView.STICKER_TYPE_PRIVACY;
                    } else if (viewType == EMPTY_VIEW_SERVER_CANT_RETURN || viewType == EMPTY_VIEW_SEARCH || viewType == EMPTY_VIEW_NO_CONTACTS || viewType == EMPTY_VIEW) {
                        stickerType = StickerEmptyView.STICKER_TYPE_SEARCH;
                    } else {
                        stickerType = StickerEmptyView.STICKER_TYPE_NO_CONTACTS;
                    }
                    StickerEmptyView emptyView = new StickerEmptyView(getContext(), null, stickerType, resourcesProvider) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(measuerdHeight - recyclerListView.getPaddingTop() - AndroidUtilities.dp(TOP_PADDING), MeasureSpec.EXACTLY));
                        }
                    };
                    if (viewType == EMPTY_VIEW_SEARCH) {
                        emptyView.title.setVisibility(View.GONE);
                        emptyView.setSubtitle(LocaleController.getString("NoResult", R.string.NoResult));
                    } else if (viewType == EMPTY_VIEW_NO_CONTACTS) {
                        emptyView.title.setVisibility(View.GONE);
                        emptyView.setSubtitle(LocaleController.getString("NoContactsViewed", R.string.NoContactsViewed));
                    } else if (viewType == EMPTY_VIEW_SERVER_CANT_RETURN) {
                        emptyView.title.setVisibility(View.VISIBLE);
                        emptyView.title.setText(LocaleController.getString("ServerErrorViewersTitle", R.string.ServerErrorViewersTitle));
                        emptyView.setSubtitle(LocaleController.getString("ServerErrorViewers", R.string.ServerErrorViewers));
                    } else if (defaultModel.isExpiredViews) {
                        emptyView.title.setVisibility(View.GONE);
                        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
                        spannableStringBuilder.append(AndroidUtilities.replaceTags(LocaleController.getString("ExpiredViewsStub", R.string.ExpiredViewsStub)));
                        if (!MessagesController.getInstance(currentAccount).premiumFeaturesBlocked()) {
                            spannableStringBuilder.append("\n\n");
                            spannableStringBuilder.append(AndroidUtilities.replaceSingleTag(LocaleController.getString("ExpiredViewsStubPremiumDescription", R.string.ExpiredViewsStubPremiumDescription), SelfStoryViewsPage.this::showPremiumAlert));
                            emptyView.createButtonLayout(LocaleController.getString("LearnMore", R.string.LearnMore), SelfStoryViewsPage.this::showPremiumAlert);
                        }
                        emptyView.subtitle.setText(spannableStringBuilder);
                    } else {
                        emptyView.title.setVisibility(View.VISIBLE);
                        if (defaultModel.isChannel) {
                            emptyView.title.setText(LocaleController.getString(R.string.NoReactions));
                            emptyView.setSubtitle(LocaleController.getString(R.string.NoReactionsStub));
                        } else {
                            emptyView.title.setText(LocaleController.getString(R.string.NoViews));
                            emptyView.setSubtitle(LocaleController.getString(R.string.NoViewsStub));
                        }
                    }
                    emptyView.showProgress(false, false);
                    view = emptyView;
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == USER_ITEM) {
                if (position < 0 || position >= items.size()) return;
                final Item item = items.get(position);
                ReactedUserHolderView view = (ReactedUserHolderView) holder.itemView;

                TLRPC.Peer peer = null;
                if (item.view != null) {
                    if (item.view instanceof TL_stories.TL_storyViewPublicRepost) {
                        peer = item.view.peer_id;
                    } else if (item.view instanceof TL_stories.TL_storyViewPublicForward && item.view.message != null) {
                        peer = item.view.message.peer_id;
                    } else {
                        peer = new TLRPC.TL_peerUser();
                        peer.user_id = item.view.user_id;
                    }
                } else if (item.reaction != null) {
                    peer = item.reaction.peer_id;
                    if (item.reaction instanceof TL_stories.TL_storyReactionPublicForward && item.reaction.message != null) {
                        peer = item.reaction.message.peer_id;
                    }
                }
                long did = DialogObject.getPeerDialogId(peer);
                TLRPC.User user = null;
                TLRPC.Chat chat = null;
                if (did >= 0) {
                    user = MessagesController.getInstance(currentAccount).getUser(did);
                } else {
                    chat = MessagesController.getInstance(currentAccount).getChat(-did);
                }
                boolean animated = defaultModel.animateDateForUsers.remove(did);

                if (item.view != null) {
                    boolean like = false;
                    if (item.view.reaction != null) {
                        ReactionsLayoutInBubble.VisibleReaction visibleReaction = ReactionsLayoutInBubble.VisibleReaction.fromTLReaction(item.view.reaction);
                        if (visibleReaction != null && visibleReaction.emojicon != null && visibleReaction.emojicon.equals("\u2764")) {
                            like = true;
                        }
                    }
                    if (item.view instanceof TL_stories.TL_storyViewPublicRepost) {
                        view.setUserReaction(user, null, null, like, 0, item.view.story, false, true, animated);
                    } else if (item.view instanceof TL_stories.TL_storyViewPublicForward) {
                        view.setUserReaction(user, null, null, like, item.view.message != null ? item.view.message.date : 0, storyItem == null ? null : storyItem.storyItem, true, true, animated);
                    } else {
                        view.setUserReaction(user, null, like ? null : item.view.reaction, like, item.view.date, null, false, true, animated);
                    }
                    int nextItemType = position < items.size() - 1 ? items.get(position + 1).viewType : -1;
                    view.drawDivider = nextItemType == USER_ITEM || nextItemType == SUBSCRIBE_TO_PREMIUM_TEXT_HINT || nextItemType == SERVER_CANT_RETURN_TEXT_HINT;
                    view.animateAlpha(isStoryShownToUser(item.view) ? 1f : .5f, false);
                } else if (item.reaction != null) {
                    TL_stories.StoryReaction peerReaction = item.reaction;

                    if (peerReaction instanceof TL_stories.TL_storyReaction) {
                        TL_stories.TL_storyReaction reaction = (TL_stories.TL_storyReaction) peerReaction;
                        boolean like = false;
                        if (reaction.reaction != null) {
                            ReactionsLayoutInBubble.VisibleReaction visibleReaction = ReactionsLayoutInBubble.VisibleReaction.fromTLReaction(reaction.reaction);
                            if (visibleReaction != null && visibleReaction.emojicon != null && visibleReaction.emojicon.equals("\u2764")) {
                                like = true;
                            }
                        }
                        view.setUserReaction(user, chat, like ? null : reaction.reaction, like, reaction.date, null, false, true, animated);
                    } else if (peerReaction instanceof TL_stories.TL_storyReactionPublicRepost) {
                        TL_stories.TL_storyReactionPublicRepost repost = (TL_stories.TL_storyReactionPublicRepost) peerReaction;
                        view.setUserReaction(user, chat, null, false, 0, repost.story, false, true, animated);
                    } else if (peerReaction instanceof TL_stories.TL_storyReactionPublicForward) {
                        view.setUserReaction(user, chat, null, false, peerReaction.message != null ? peerReaction.message.date : 0, storyItem == null ? null : storyItem.storyItem, true, true, animated);
                    }

                    int nextItemType = position < items.size() - 1 ? items.get(position + 1).viewType : -1;
                    view.drawDivider = nextItemType == USER_ITEM || nextItemType == SUBSCRIBE_TO_PREMIUM_TEXT_HINT || nextItemType == SERVER_CANT_RETURN_TEXT_HINT;
                    view.animateAlpha(1f, false);
                }
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == USER_ITEM;
        }

        public void updateRows() {
            items.clear();
            ViewsModel model = currentModel;
            if (isSearchDebounce) {
                items.add(new Item(FIRST_PADDING_ITEM));
                items.add(new Item(FLICKER_LOADING_ITEM_FULL));
            } else {
                items.add(new Item(FIRST_PADDING_ITEM));
                if (model != null && model.getCount() <= 0 && (model.isExpiredViews || (!model.loading && !model.hasNext))) {
                    if (!TextUtils.isEmpty(model.state.searchQuery)) {
                        items.add(new Item(EMPTY_VIEW_SEARCH));
                    } else if (model.isExpiredViews) {
                        items.add(new Item(EMPTY_VIEW));
                    } else if (model.totalCount > 0 && model.state.contactsOnly) {
                        items.add(new Item(EMPTY_VIEW_NO_CONTACTS));
                    } else if (model.totalCount > 0) {
                        items.add(new Item(EMPTY_VIEW_SERVER_CANT_RETURN));
                    } else {
                        items.add(new Item(EMPTY_VIEW));
                    }
                } else {
                    if (model != null) {
                        if (model.isChannel) {
                            for (int i = 0; i < model.reactions.size(); i++) {
                                items.add(new Item(USER_ITEM, model.reactions.get(i)));
                            }
                        } else {
                            for (int i = 0; i < model.views.size(); i++) {
                                items.add(new Item(USER_ITEM, model.views.get(i)));
                            }
                        }
                    }
                    if (model != null && (model.loading || model.hasNext)) {
                        if (model.getCount() <= 0) {
                            items.add(new Item(FLICKER_LOADING_ITEM_FULL));
                        } else {
                            items.add(new Item(FLICKER_LOADING_ITEM));
                        }
                    } else if (model != null && model.showReactionOnly) {
                        items.add(new Item(SUBSCRIBE_TO_PREMIUM_TEXT_HINT));
                    } else if (model != null && model.getCount() < model.totalCount && TextUtils.isEmpty(model.state.searchQuery) && !model.state.contactsOnly) {
                        items.add(new Item(SERVER_CANT_RETURN_TEXT_HINT));
                    }
                }
            }
            items.add(new Item(LAST_PADDING_VIEW));
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).viewType;
        }
    }

    private void showPremiumAlert() {
        PremiumFeatureBottomSheet sheet = new PremiumFeatureBottomSheet(storyViewer.fragment, PremiumPreviewFragment.PREMIUM_FEATURE_STORIES, false);
        sheet.show();
    }

    private static class Item {
        final int viewType;
        final TL_stories.StoryView view;
        final TL_stories.StoryReaction reaction;

        private Item(int viewType) {
            this.viewType = viewType;
            this.view = null;
            this.reaction = null;
        }

        private Item(int viewType, TL_stories.StoryView view) {
            this.viewType = viewType;
            this.view = view;
            this.reaction = null;
        }

        private Item(int viewType, TL_stories.StoryReaction reaction) {
            this.viewType = viewType;
            this.view = null;
            this.reaction = reaction;
        }
    }

    public static class ViewsModel {

        public int totalCount;
        TL_stories.StoryItem storyItem;
        private long dialogId;
        int currentAccount;
        boolean loading;
        public final boolean isChannel;
        ArrayList<TL_stories.StoryView> views = new ArrayList<>();
        ArrayList<TL_stories.StoryView> originalViews = new ArrayList<>();
        ArrayList<TL_stories.StoryReaction> reactions = new ArrayList<>();
        boolean isExpiredViews;
        boolean showReactionOnly;
        boolean initial;
        boolean hasNext = true;
        String offset;
        int reqId = -1;
        HashSet<Long> animateDateForUsers = new HashSet<>();
        boolean useLocalFilters;

        public int getCount() {
            return isChannel ? reactions.size() : views.size();
        }

        ArrayList<SelfStoryViewsPage> listeners = new ArrayList<>();
        FiltersState state = new FiltersState();

        public ViewsModel(int currentAccount, long dialogId, TL_stories.StoryItem storyItem, boolean isDefault) {
            this.currentAccount = currentAccount;
            this.storyItem = storyItem;
            isChannel = dialogId < 0;
            this.dialogId = dialogId;
            this.totalCount = storyItem.views == null ? 0 : storyItem.views.views_count;
            if (totalCount < 200) {
                useLocalFilters = true;
            }
            isExpiredViews = StoriesUtilities.hasExpiredViews(storyItem) && !UserConfig.getInstance(currentAccount).isPremium();
            if (isExpiredViews && storyItem.views != null && storyItem.views.reactions_count > 0) {
                isExpiredViews = false;
                showReactionOnly = true;
            }
            if (!isExpiredViews) {
                initial = true;
                if (storyItem.views != null && isDefault) {
                    for (int i = 0; i < storyItem.views.recent_viewers.size(); i++) {
                        long uid = storyItem.views.recent_viewers.get(i);
                        if (MessagesController.getInstance(currentAccount).getUser(uid) == null) {
                            continue;
                        }
                        TL_stories.TL_storyView storyView = new TL_stories.TL_storyView();
                        storyView.user_id = uid;
                        storyView.date = 0;
                        views.add(storyView);
                    }
                }
            }
        }

        public void loadNext() {
            if (loading || !hasNext || isExpiredViews) {
                return;
            }
            if (isChannel) {
                TL_stories.TL_getStoryReactionsList req = new TL_stories.TL_getStoryReactionsList();
                req.forwards_first = state.sortByReactions;
                req.id = storyItem.id;
                req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
                req.limit = (initial || reactions.size() < 20) ? 20 : 100;
                req.offset = offset;
                if (req.offset == null) {
                    req.offset = "";
                } else {
                    req.flags |= 2;
                }

                loading = true;
                int[] localReqId = new int[1];
                FileLog.d("SelfStoryViewsPage reactions load next " + storyItem.id + " " + initial + " offset=" + req.offset/* + " q" + req.q + " " + req.just_contacts + " " + req.reactions_first*/);
                localReqId[0] = reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (localReqId[0] != reqId) {
                        FileLog.d("SelfStoryViewsPage reactions " + storyItem.id + " localId != reqId");
                        return;
                    }
                    loading = false;
                    reqId = -1;
                    if (response != null) {
                        TL_stories.TL_storyReactionsList res = (TL_stories.TL_storyReactionsList) response;
                        MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                        MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                        MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, true, false);
                        if (initial) {
                            initial = false;
                            for (int i = 0; i < reactions.size(); i++) {
                                animateDateForUsers.add(DialogObject.getPeerDialogId(reactions.get(i).peer_id));
                            }
                            reactions.clear();
                            originalViews.clear();
                        }
//                        if (useLocalFilters) {
//                            originalReactions.addAll(res.reactions);
//                            applyLocalFilter();
//                        } else {
                            reactions.addAll(res.reactions);
//                        }

                        if (!res.reactions.isEmpty()) {
                            hasNext = true;
                        } else {
                            hasNext = false;
                        }
                        offset = res.next_offset;
                        if (TextUtils.isEmpty(offset)) {
                            hasNext = false;
                        }

                        if (storyItem.views == null) {
                            storyItem.views = new TL_stories.TL_storyViews();
                        }
                        boolean counterUpdated = totalCount != res.count;
                        totalCount = res.count;
                        if (counterUpdated) {
                            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.storiesUpdated);
                        }
                    } else {
                        if (error != null && "MSG_ID_INVALID".equals(error.text)) {
                            totalCount = 0;
                        }
                        hasNext = false;
                    }

                    FileLog.d("SelfStoryViewsPage reactions " + storyItem.id + " response  totalItems " + reactions.size() + " has next " + hasNext);
                    for (int i = 0; i < listeners.size(); i++) {
                        listeners.get(i).onDataRecieved(this);
                    }
                    if (reactions.size() < 20 && hasNext) {
                        loadNext();
                    }
                }));
            } else {
                TL_stories.TL_stories_getStoryViewsList req = new TL_stories.TL_stories_getStoryViewsList();
                req.id = storyItem.id;
                req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
                if (useLocalFilters) {
                    req.q = "";
                    req.just_contacts = false;
                    req.reactions_first = true;
                } else {
                    req.q = state.searchQuery;
                    if (!TextUtils.isEmpty(req.q)) {
                        req.flags |= 2;
                    }
                    req.just_contacts = state.contactsOnly;
                    req.reactions_first = state.sortByReactions;
                }
                req.limit = (initial || views.size() < 20) ? 20 : 100;
                req.offset = offset;
                if (req.offset == null) {
                    req.offset = "";
                }

                loading = true;
                int[] localReqId = new int[1];
                FileLog.d("SelfStoryViewsPage load next " + storyItem.id + " " + initial + " offset=" + req.offset + " q" + req.q + " " + req.just_contacts + " " + req.reactions_first);
                localReqId[0] = reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (localReqId[0] != reqId) {
                        FileLog.d("SelfStoryViewsPage " + storyItem.id + " localId != reqId");
                        return;
                    }
                    loading = false;
                    reqId = -1;
                    if (response != null) {
                        TL_stories.StoryViewsList res = (TL_stories.StoryViewsList) response;
                        MessagesController.getInstance(currentAccount).getStoriesController().applyStoryViewsBlocked(res);
                        MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                        MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                        MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, true, false);
                        if (initial) {
                            initial = false;
                            for (int i = 0; i < views.size(); i++) {
                                animateDateForUsers.add(views.get(i).user_id);
                            }
                            views.clear();
                            originalViews.clear();
                        }
                        if (useLocalFilters) {
                            originalViews.addAll(res.views);
                            applyLocalFilter();
                        } else {
                            views.addAll(res.views);
                        }

                        if (!res.views.isEmpty()) {
                            hasNext = true;
                        } else {
                            hasNext = false;
                        }
                        offset = res.next_offset;
                        if (TextUtils.isEmpty(offset)) {
                            hasNext = false;
                        }

                        if (storyItem.views == null) {
                            storyItem.views = new TL_stories.TL_storyViews();
                        }
                        boolean counterUpdated = false;
                        if (res.count > storyItem.views.views_count) {
                            storyItem.views.recent_viewers.clear();
                            for (int i = 0; i < (Math.min(3, res.users.size())); i++) {
                                storyItem.views.recent_viewers.add(res.users.get(i).id);
                            }
                            storyItem.views.views_count = res.count;
                            counterUpdated = true;
                        }
                        if (storyItem.views.reactions_count != res.reactions_count) {
                            storyItem.views.reactions_count = res.reactions_count;
                            counterUpdated = true;
                        }
                        if (counterUpdated) {
                            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.storiesUpdated);
                        }
                    } else {
                        if (error != null && "MSG_ID_INVALID".equals(error.text)) {
                            totalCount = 0;
                        }
                        hasNext = false;
                    }

                    FileLog.d("SelfStoryViewsPage " + storyItem.id + " response  totalItems " + views.size() + " has next " + hasNext);
                    for (int i = 0; i < listeners.size(); i++) {
                        listeners.get(i).onDataRecieved(this);
                    }
                    if (views.size() < 20 && hasNext) {
                        loadNext();
                    }
                }));
            }
        }

        private void applyLocalFilter() {
            if (isChannel) {
                return;
            }
            views.clear();
            if (state.contactsOnly || !TextUtils.isEmpty(state.searchQuery)) {
                String search1 = null;
                String search2 = null;
                String search3 = null;
                String search4 = null;
                if (!TextUtils.isEmpty(state.searchQuery)) {
                    search1 = state.searchQuery.trim().toLowerCase();
                    search2 = LocaleController.getInstance().getTranslitString(search1);
                    search3 = " " + search1;
                    search4 = " " + search2;
                }
                for (int i = 0; i < originalViews.size(); i++) {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(originalViews.get(i).user_id);
                    boolean canAdd = true;
                    if (state.contactsOnly && (user == null || !user.contact)) {
                        canAdd = false;
                    }
                    if (canAdd && search1 != null) {
                        String name = ContactsController.formatName(user.first_name, user.last_name).toLowerCase();
                        String username = UserObject.getPublicUsername(user);
                        String translitName = AndroidUtilities.translitSafe(name);
                        boolean hit = (
                            name != null && (name.startsWith(search1) || name.contains(search3)) ||
                            translitName != null && (translitName.startsWith(search2) || translitName.contains(search4)) ||
                            username != null && (username.startsWith(search2) || username.contains(search4))
                        );
                        if (!hit) {
                            canAdd = false;
                        }
                    }
                    if (canAdd) {
                        views.add(originalViews.get(i));
                    }
                }
            } else {
                views.addAll(originalViews);
            }
            if (!state.sortByReactions) {
                Collections.sort(views, Comparator.comparingInt(o -> -o.date));
            }
        }

        public void addListener(SelfStoryViewsPage listener) {
            if (!listeners.contains(listener)) {
                listeners.add(listener);
            }
        }

        public void removeListener(SelfStoryViewsPage listener) {
            listeners.remove(listener);
        }

        public void release() {
            if (reqId >= 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, false);
            }
            reqId = -1;
        }

        public void reloadIfNeed(FiltersState state, boolean showContactsFilter, boolean showReactionsSort) {
            FiltersState localState = new FiltersState();
            localState.set(state);
            if (!showContactsFilter) {
                localState.contactsOnly = false;
            }
            if (!showReactionsSort) {
                localState.sortByReactions = true;
            }
            if (this.state.equals(localState)) {
                return;
            }
            this.state.set(localState);
            if (!isChannel && useLocalFilters) {
                applyLocalFilter();
                for (int i = 0; i < listeners.size(); i++) {
                    listeners.get(i).onDataRecieved(this);
                }
            } else {
                release();
                views.clear();
                reactions.clear();
                initial = true;
                loading = false;
                hasNext = true;
                offset = "";
                loadNext();
            }
        }
    }

    private class HeaderView extends FrameLayout {

        private final LinearLayout buttonContainer;
        Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        TextView allViewersView;
        TextView contactsViewersView;

        RectF animateFromRect = new RectF();
        float animateFromAlpha1;
        float animateFromAlpha2;

        RectF rectF = new RectF();
        float animationProgress = 1f;
        int selected;
        boolean lastSortType;
        ReplaceableIconDrawable replacableDrawable;

        public HeaderView(@NonNull Context context) {
            super(context);
            selectedPaint.setColor(Theme.getColor(Theme.key_listSelector, resourcesProvider));

            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.HORIZONTAL);

            allViewersView = new TextView(context);
            allViewersView.setText(LocaleController.getString("AllViewers", R.string.AllViewers));
            allViewersView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            allViewersView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            allViewersView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            allViewersView.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(4), AndroidUtilities.dp(12), AndroidUtilities.dp(4));

            contactsViewersView = new TextView(context);
            contactsViewersView.setText(LocaleController.getString("Contacts", R.string.Contacts));
            contactsViewersView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            contactsViewersView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            contactsViewersView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            contactsViewersView.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(4), AndroidUtilities.dp(12), AndroidUtilities.dp(4));

            linearLayout.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6));
            linearLayout.addView(allViewersView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 13, 0, 0, 0));
            linearLayout.addView(contactsViewersView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0, 0));

            buttonContainer = new LinearLayout(getContext());
            buttonContainer.setPadding(AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6), 0);
            buttonContainer.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(26), Theme.getColor(Theme.key_listSelector, resourcesProvider)));
            buttonContainer.setOrientation(LinearLayout.HORIZONTAL);
            replacableDrawable = new ReplaceableIconDrawable(getContext());
            replacableDrawable.exactlyBounds = true;
            lastSortType = true;
            replacableDrawable.setIcon(R.drawable.menu_views_reactions3, false);
            ImageView imageView = new ImageView(getContext());
            imageView.setScaleType(ImageView.ScaleType.FIT_XY);
            imageView.setImageDrawable(replacableDrawable);
            imageView.setPadding(AndroidUtilities.dp(1), AndroidUtilities.dp(1), AndroidUtilities.dp(1), AndroidUtilities.dp(1));
            buttonContainer.addView(imageView, LayoutHelper.createLinear(26, 26));

            ImageView arrowImage = new ImageView(getContext());
            arrowImage.setImageResource(R.drawable.arrow_more);
            buttonContainer.addView(arrowImage, LayoutHelper.createLinear(16, 26));

            addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            addView(buttonContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT, 13, 6, 13, 6));

            allViewersView.setOnClickListener(v -> {
                if (!state.contactsOnly) {
                    return;
                }
                state.contactsOnly = false;
                updateViewState(true);
                reload();
            });
            contactsViewersView.setOnClickListener(v -> {
                if (state.contactsOnly) {
                    return;
                }
                state.contactsOnly = true;
                updateViewState(true);
                reload();
            });
            buttonContainer.setOnClickListener(v -> {
                popupMenu = new CustomPopupMenu(getContext(), resourcesProvider, false) {
                    @Override
                    protected void onCreate(ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout) {
                        popupLayout.setBackgroundColor(ColorUtils.blendARGB(Color.BLACK, Color.WHITE, 0.18f));
                        final boolean isChannel = currentModel != null && currentModel.isChannel;
                        ActionBarMenuSubItem item = ActionBarMenuItem.addItem(popupLayout, isChannel ? R.drawable.menu_views_reposts : (state.sortByReactions ? R.drawable.menu_views_reactions2 : R.drawable.menu_views_reactions), LocaleController.getString(isChannel ? R.string.SortByReposts : R.string.SortByReactions), false, resourcesProvider);
                        if (!state.sortByReactions) {
                            item.setAlpha(0.5f);
                        }
                        item.setOnClickListener(v -> {
                            if (!state.sortByReactions) {
                                if (sharedFilterState != null) {
                                    sharedFilterState.sortByReactions = state.sortByReactions = true;
                                } else {
                                    state.sortByReactions = true;
                                }
                                updateViewState(true);
                                reload();
                                onSharedStateChanged.accept(SelfStoryViewsPage.this);
                            }
                            if (popupMenu != null) {
                                popupMenu.dismiss();
                            }
                        });

                        item = ActionBarMenuItem.addItem(popupLayout, !state.sortByReactions ? R.drawable.menu_views_recent2 : R.drawable.menu_views_recent, LocaleController.getString("SortByTime", R.string.SortByTime), false, resourcesProvider);
                        if (state.sortByReactions) {
                            item.setAlpha(0.5f);
                        }
                        item.setOnClickListener(v -> {
                            if (state.sortByReactions) {
                                if (sharedFilterState != null) {
                                    sharedFilterState.sortByReactions = state.sortByReactions = false;
                                } else {
                                    state.sortByReactions = false;
                                }
                                updateViewState(true);
                                reload();
                                onSharedStateChanged.accept(SelfStoryViewsPage.this);
                            }
                            if (popupMenu != null) {
                                popupMenu.dismiss();
                            }
                        });
                        ActionBarPopupWindow.GapView gap = new ActionBarPopupWindow.GapView(getContext(), resourcesProvider, Theme.key_actionBarDefaultSubmenuSeparator);
                        gap.setTag(R.id.fit_width_tag, 1);
                        popupLayout.addView(gap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 8));
                        ActionBarMenuItem.addText(popupLayout, LocaleController.getString(isChannel ? R.string.StoryReactionsSortDescription : R.string.StoryViewsSortDescription), resourcesProvider);
                    }

                    @Override
                    protected void onDismissed() {

                    }
                };
                popupMenu.show(buttonContainer, 0, -buttonContainer.getMeasuredHeight() - AndroidUtilities.dp(8));
            });
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (showContactsFilter) {
                float allViewersAlpha, contactsAlpha;
                if (selected == 0) {
                    allViewersView.getHitRect(AndroidUtilities.rectTmp2);
                    allViewersAlpha = 1f;
                    contactsAlpha = 0.5f;
                } else {
                    contactsViewersView.getHitRect(AndroidUtilities.rectTmp2);
                    allViewersAlpha = 0.5f;
                    contactsAlpha = 1f;
                }
                rectF.set(AndroidUtilities.rectTmp2);
                if (animationProgress != 1f) {
                    allViewersAlpha = AndroidUtilities.lerp(animateFromAlpha1, allViewersAlpha, animationProgress);
                    contactsAlpha = AndroidUtilities.lerp(animateFromAlpha2, contactsAlpha, animationProgress);
                    AndroidUtilities.lerp(animateFromRect, rectF, animationProgress, rectF);
                }
                allViewersView.setAlpha(allViewersAlpha);
                contactsViewersView.setAlpha(contactsAlpha);
                float r = rectF.height() / 2f;
                canvas.drawRoundRect(rectF, r, r, selectedPaint);
            }
            super.dispatchDraw(canvas);
        }

        ValueAnimator animator;

        public void setState(boolean contactsOnly, boolean animate) {
            int localSelected = contactsOnly ? 1 : 0;
            if (localSelected == selected && animate) {
                return;
            }
            if (animator != null) {
                animator.removeAllListeners();
                animator.cancel();
            }
            selected = localSelected;
            if (!animate) {
                animationProgress = 1f;
                invalidate();
                return;
            } else {
                animateFromRect.set(rectF);
                animateFromAlpha1 = allViewersView.getAlpha();
                animateFromAlpha2 = contactsViewersView.getAlpha();
                animationProgress = 0;
                invalidate();
                animator = ValueAnimator.ofFloat(0, 1f);
                animator.addUpdateListener(animation -> {
                    animationProgress = (float) animator.getAnimatedValue();
                    invalidate();
                });
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        animator = null;
                        animationProgress = 1f;
                        invalidate();
                    }
                });
                animator.setDuration(250);
                animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                animator.start();
            }
        }
    }

    private void reload() {
        if (currentModel != null) {
            currentModel.removeListener(this);
        }
        currentModel = defaultModel;
        if (currentModel == null) {
            return;
        }
        currentModel.addListener(this);
        currentModel.reloadIfNeed(state, showContactsFilter, showReactionsSort);
        listAdapter.updateRows();
        layoutManager.scrollToPositionWithOffset(0, (int) (getTopOffset() - recyclerListView.getPaddingTop()));
    }

    private void updateViewState(boolean animated) {
        headerView.setState(state.contactsOnly, animated);
        headerView.lastSortType = state.sortByReactions;
        headerView.replacableDrawable.setIcon(state.sortByReactions ? (currentModel != null && currentModel.isChannel ? R.drawable.menu_views_reposts3 : R.drawable.menu_views_reactions3) : R.drawable.menu_views_recent3, animated);
    }

    public static class FiltersState {
        boolean sortByReactions = true; // converts to sortByForwards when showing channel reactions
        boolean contactsOnly;
        String searchQuery;
        String q;

        public boolean isDefault() {
            return sortByReactions && !contactsOnly && TextUtils.isEmpty(searchQuery);
        }

        public void set(FiltersState state) {
            sortByReactions = state.sortByReactions;
            contactsOnly = state.contactsOnly;
            searchQuery = state.searchQuery;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FiltersState that = (FiltersState) o;
            boolean textIsEquals = (TextUtils.isEmpty(searchQuery) && TextUtils.isEmpty(that.searchQuery)) || Objects.equals(searchQuery, that.searchQuery);
            return sortByReactions == that.sortByReactions && contactsOnly == that.contactsOnly && textIsEquals;
        }

        @Override
        public int hashCode() {
            return Objects.hash(sortByReactions, contactsOnly, searchQuery);
        }
    }

    private class RecyclerListViewInner extends RecyclerListView implements StoriesListPlaceProvider.ClippedView {
        public RecyclerListViewInner(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context, resourcesProvider);
        }

        @Override
        public void updateClip(int[] clip) {
            clip[0] = AndroidUtilities.dp(TOP_PADDING);
            clip[1] = getMeasuredHeight();
        }
    }
}
