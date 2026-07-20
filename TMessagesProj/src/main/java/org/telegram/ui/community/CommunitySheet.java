package org.telegram.ui.community;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.utils.FBool;
import org.telegram.messenger.utils.GradientProtectionDrawable;
import org.telegram.messenger.utils.TextWatcherImpl;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.FiltersView;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.TextCheckCell2;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.Components.FragmentSearchField;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.JoinGroupAlert;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SearchViewPager;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.Components.chat.layouts.ChatActivityFadeView;
import org.telegram.ui.FilteredSearchView;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.TopicsFragment;
import org.telegram.ui.community.cells.CommunityPendingRequestCell;
import org.telegram.ui.community.cells.CommunityRequestsCell;
import org.telegram.ui.community.sheet.CommunityAddOptionsSheet;
import org.telegram.ui.community.sheet.CommunityInviteOnlySheet;

import java.util.ArrayList;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

public class CommunitySheet extends BottomSheet implements NotificationCenter.NotificationCenterDelegate, FactorAnimator.Target {

    private static final int ANIMATOR_ID_SEARCH_MESSAGES_VISIBLE = 1;
    private static final int ANIMATOR_ID_SEARCH_CHATS_VISIBLE = 2;

    private final BoolAnimator animatorSearchMessagesVisible = new BoolAnimator(ANIMATOR_ID_SEARCH_MESSAGES_VISIBLE,
        this, CubicBezierInterpolator.EASE_OUT_QUINT, 350);

    private final BoolAnimator animatorSearchChatsVisible = new BoolAnimator(ANIMATOR_ID_SEARCH_CHATS_VISIBLE,
        this, CubicBezierInterpolator.EASE_OUT_QUINT, 350);



    private ViewPagerFixed viewPager;

    private static final int ROW_ID_PENDING_REQUESTS = 100;
    private static final int ROW_ID_TOGGLE_COLLAPSED = 101;
    private static final int ROW_ID_EMPTY_SPACE = 99;

    private static final int PAGE_TYPE_COMMUNITY = 0;
    private static final int PAGE_TYPE_PENDING_REQUESTS = 1;
    private static final int PAGE_TYPE_CHATS_TO_ADD = 2;

    private final long communityId;

    private TLRPC.Chat currentCommunity;
    private TLRPC.ChatFull communityInfo;
    private boolean collapsedInDialogs;
    private ButtonWithCounterView addChatToCommunityButton;
    private ButtonWithCounterView closeChatToCommunityButton;

    private final BaseFragment parentFragment;
    private final CommunityPage communityPage;
    private final PendingRequestsPage requestsPage;
    private final ChatsToAddListPage chatsPage;

    private final FragmentSearchField messagesSearchView;
    private final FragmentSearchField chatsSearchView;
    private final FilteredSearchView filteredSearchView;
    private final UniversalRecyclerView foundChatsView;
    private final FadeView communityPageFadeView;
    private final FadeView chatsPageFadeView;
    private final GradientProtectionDrawable gradientProtectionDrawableTop = new GradientProtectionDrawable(WindowInsetsCompat.Side.TOP);
    private final GradientProtectionDrawable gradientProtectionDrawableBottom = new GradientProtectionDrawable(WindowInsetsCompat.Side.BOTTOM);

    private final View fakeAnchorView;

    private CommunityUtils.PendingRequests pendingRequestsList;

    private final boolean onlyChatsMode;
    private final Utilities.Callback<TLRPC.Chat> chatsToAddCallback;

    public CommunitySheet(BaseFragment fragment, long communityId) {
        this(fragment, communityId, null, null);
    }

    public CommunitySheet(BaseFragment fragment, long communityId,
                          ArrayList<TLRPC.Chat> chatsToAddToCommunity, Utilities.Callback<TLRPC.Chat> chatsToAddCallback
    ) {
        super(fragment.getContext(), true, true, fragment.getResourceProvider());
        AndroidUtilities.enableEdgeToEdge(getWindow());

        parentFragment = fragment;

        this.onlyChatsMode = chatsToAddToCommunity != null;
        this.chatsToAddToCommunity = chatsToAddToCommunity;
        this.chatsToAddCallback = chatsToAddCallback;

        final Context context = fragment.getContext();
        init(context);

        communityPageFadeView = new FadeView(context);
        chatsPageFadeView = new FadeView(context);

        messagesSearchView = new FragmentSearchField(context, resourcesProvider);
        messagesSearchView.setCloseButtonVisible(true);
        messagesSearchView.setWhiteBackground();
        messagesSearchView.editText.setHint(LocaleController.getString(R.string.Search));
        messagesSearchView.editText.addTextChangedListener(new TextWatcherImpl() {
            @Override
            public void afterTextChanged(Editable s) {
                CommunitySheet.this.onMessagesSearchTextChanged(s.toString());
            }
        });
        messagesSearchView.setVisibility(View.GONE);

        chatsSearchView = new FragmentSearchField(context, resourcesProvider);
        chatsSearchView.setCloseButtonVisible(true);
        chatsSearchView.setWhiteBackground();
        chatsSearchView.editText.setHint(LocaleController.getString(R.string.Search));
        chatsSearchView.editText.addTextChangedListener(new TextWatcherImpl() {
            @Override
            public void afterTextChanged(Editable s) {
               CommunitySheet.this.onChatsSearchTextChanged(s.toString());
            }
        });
        chatsSearchView.setVisibility(View.GONE);

        foundChatsView = new UniversalRecyclerView(context, currentAccount, 0, CommunitySheet.this::fillItemsChatsToAddSearch,
                CommunitySheet.this::onClickChatToAdd, null, resourcesProvider);
        foundChatsView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                AndroidUtilities.hideKeyboard(chatsSearchView.editText);
            }
        });
        foundChatsView.setClipToPadding(false);
        foundChatsView.setVisibility(View.GONE);
        foundChatsView.setSections();
        foundChatsView.adapter.setApplyBackground(false);
        foundChatsView.setPadding(0, AndroidUtilities.statusBarHeight + dp(52), 0, AndroidUtilities.navigationBarHeight);

        filteredSearchView = new FilteredSearchView(fragment);
        filteredSearchView.setVisibility(View.GONE);
        filteredSearchView.setBackground(null);
        filteredSearchView.setChatPreviewDelegate(new SearchViewPager.ChatPreviewDelegate() {
            @Override
            public void startChatPreview(RecyclerListView listView, DialogCell cell) {

            }

            @Override
            public void move(float dy) {

            }

            @Override
            public void finish() {

            }
        });
        filteredSearchView.setUiCallback(new FilteredSearchView.UiCallback() {
            @Override
            public void goToMessage(MessageObject messageObject) {
                parentFragment.presentFragment(SearchViewPager.createFragmentFromMessage(currentAccount, messageObject));
                dismiss();
            }

            @Override
            public boolean actionModeShowing() {
                return false;
            }

            @Override
            public void toggleItemSelection(MessageObject item, View view, int a) {

            }

            @Override
            public boolean isSelected(FilteredSearchView.MessageHashId messageHashId) {
                return false;
            }

            @Override
            public void showActionMode() {

            }

            @Override
            public int getFolderId() {
                return 0;
            }
        });
        filteredSearchView.recyclerListView.setClipToPadding(false);

        fakeAnchorView = new View(getContext());
        pendingRequestsList = new CommunityUtils.PendingRequests(getContext(), resourcesProvider, BulletinFactory.of((FrameLayout) containerView, resourcesProvider), currentAccount, communityId);
        pendingRequestsList.setDelegate(new CommunityUtils.PendingRequests.Delegate() {
            @Override
            public void updateAdapter() {
                requestsPage.listView.adapter.update(true);
                communityPage.listView.adapter.update(true);
            }

            @Override
            public void close() {
                viewPager.scrollToPosition(0);
            }

            @Override
            public void onClickGroupOwner(long dialogId) {
                fragment.presentFragment(ChatActivity.of(dialogId));
                dismiss();
            }
        });

        this.communityId = communityId;
        currentCommunity = MessagesController.getInstance(currentAccount).getChat(communityId);
        communityInfo = MessagesController.getInstance(currentAccount).getChatFull(communityId);
        collapsedInDialogs = currentCommunity != null && currentCommunity.collapsed_in_dialogs;


        FiltersView.MediaFilterData filterData = new FiltersView.MediaFilterData(R.drawable.search_users_filled, DialogObject.getShortName(currentCommunity), null, FiltersView.FILTER_TYPE_CHAT);
        filterData.setUser(currentCommunity);
        filterData.removable = false;
        messagesSearchView.addSearchFilter(filterData);

        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));


        requestsPage = new PendingRequestsPage(context);
        communityPage = new CommunityPage(context);
        chatsPage = new ChatsToAddListPage(context);
        viewPager.setAdapter(new ViewPagerFixed.Adapter() {
            @Override
            public int getItemCount() {
                return onlyChatsMode ? 1 : 3;
            }

            @Override
            public View createView(int viewType) {
                return viewType == PAGE_TYPE_CHATS_TO_ADD ? chatsPage : viewType == PAGE_TYPE_COMMUNITY ? communityPage : requestsPage;
            }

            @Override
            public int getItemViewType(int position) {
                if (onlyChatsMode) {
                    return PAGE_TYPE_CHATS_TO_ADD;
                }

                return position == 2 ? PAGE_TYPE_CHATS_TO_ADD :
                    position == 0 ? PAGE_TYPE_COMMUNITY : PAGE_TYPE_PENDING_REQUESTS;
            }

            @Override
            public void bindView(View view, int position, int viewType) {
                ((Page) view).bind(viewType);
            }
        });

        messagesSearchView.setCloseButtonOnClickListener(() -> {
            communityPage.listView.layoutManager.scrollToPositionWithOffset(1, systemInsets.top);
            animatorSearchMessagesVisible.setValue(false, true);
            setAllowNestedScroll(true);
            AndroidUtilities.hideKeyboard(messagesSearchView.editText);
            messagesSearchView.editText.clearFocus();
        });
        chatsSearchView.setCloseButtonOnClickListener(() -> {
            chatsPage.listView.layoutManager.scrollToPositionWithOffset(1, systemInsets.top);
            animatorSearchChatsVisible.setValue(false, true);
            setAllowNestedScroll(true);
            AndroidUtilities.hideKeyboard(chatsSearchView.editText);
            chatsSearchView.editText.clearFocus();
        });

        pendingRequestsList.loadNext();

        MessagesController.getInstance(currentAccount).loadFullChat(communityId, 0, true);

        Bulletin.addDelegate((FrameLayout) containerView, new Bulletin.Delegate() {
            @Override
            public int getBottomOffset(int tag) {
                return AndroidUtilities.navigationBarHeight + dp(12 + 48);
            }
        });

        ViewCompat.setOnApplyWindowInsetsListener(containerView, this::onApplyWindowInsets);
    }

    private void fillItemsCommunity(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asSpace(ROW_ID_EMPTY_SPACE, Math.min(AndroidUtilities.statusBarHeight + dp(56 + 120), (int) (AndroidUtilities.displaySize.y * .25f))));
        items.add(UItem.asSpace(0, dp(56)));

        if (CommunityUtils.COLLAPSED_SUPPORT) {
            items.add(UItem.asSwitchNoIcon(ROW_ID_TOGGLE_COLLAPSED, getString(R.string.CommunityShowAsOneChat)).setChecked(collapsedInDialogs));
            items.add(UItem.asShadow(2, getString(R.string.CommunityShowAsOneChatInfo)));
        }

        if (pendingRequestsList.isSingle()) {
            items.add(UItem.asHeader(3, getString(R.string.CommunityPendingRequest)));
            pendingRequestsList.fillItems(items);
            items.add(UItem.asSpace(5, dp(14.33f)));
        } else if (pendingRequestsList.getTotalCount() > 0) {
            final int totalCount = pendingRequestsList.getTotalCount();
            final int unreadCount = pendingRequestsList.getUnreadCount();

            items.add(CommunityRequestsCell.Factory.of(ROW_ID_PENDING_REQUESTS,
                IconBackgroundColors.BLUE_ALT, R.drawable.filled_requests_24,
                (totalCount == unreadCount) ?
                    getString(R.string.CommunityPendingRequests) :
                    formatPluralString("CommunityPendingRequestsRow", totalCount),
                unreadCount > 0 ? Integer.toString(unreadCount) : null, true));
            items.add(UItem.asSpace(5, dp(14.33f)));
        }

        CommunityUtils.fillLinkedPeers(currentAccount, items, communityId, true);
    }

    private void fillItemsRequests(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asSpace(ROW_ID_EMPTY_SPACE, (int) (AndroidUtilities.displaySize.y * .35f)   /*dp(12)*/));
        items.add(UItem.asSpace(0, dp(48)));

        if (ChatObject.canBlockUsers(currentCommunity)) {
            items.add(UItem.asShadow(1, AndroidUtilities.replaceArrows(AndroidUtilities.replaceSingleTag(getString(R.string.CommunityPendingRequestsInfo), () -> {
                Bundle args = new Bundle();
                args.putLong("community_id", communityId);
                parentFragment.presentFragment(new CommunityEditActivity(args));
                dismiss();
            }), true)));
        } else {
            items.add(UItem.asShadow(1, getString(R.string.CommunityPendingRequestsInfoNoChange)));
        }

        items.add(UItem.asCustom(2, fakeAnchorView));
        items.add(UItem.asHeader(3, LocaleController.formatPluralString("CommunityPendingRequestsSuggestedHeader", pendingRequestsList.getTotalCount())));
        pendingRequestsList.fillItems(items);
    }

    private void fillItemsChatsToAdd(ArrayList<UItem> items, UniversalAdapter adapter) {
        fillItemsChatsToAddImpl(items, adapter, false);
    }

    private void fillItemsChatsToAddSearch(ArrayList<UItem> items, UniversalAdapter adapter) {
        fillItemsChatsToAddImpl(items, adapter, true);
    }

    private void fillItemsChatsToAddImpl(ArrayList<UItem> items, UniversalAdapter adapter, final boolean search) {
        if (!search) {
            items.add(UItem.asSpace(ROW_ID_EMPTY_SPACE, (int) (AndroidUtilities.displaySize.y * .35f)   /*dp(12)*/));
            items.add(UItem.asSpace(0, dp(56)));
        }
        if (chatsToAddToCommunity != null) {
            final String query = search && lastSearchChatsString != null ?
                lastSearchChatsString.toLowerCase() : null;

            for (TLRPC.Chat chat : chatsToAddToCommunity) {
                if (search && !TextUtils.isEmpty(query)) {
                    if (chat.title != null) {
                        String title = chat.title.toLowerCase();
                        if (title.contains(query)) {
                            items.add(UItem.asProfileCell(chat));
                        }
                    }
                } else {
                    items.add(UItem.asProfileCell(chat));
                }
            }
        }
    }

    private void onClickChatToAdd(UItem item, View view, int position, float x, float y) {
        if (item.object instanceof TLRPC.Chat) {
            final TLRPC.Chat chat = (TLRPC.Chat) item.object;
            if (onlyChatsMode) {
                chatsToAddCallback.run(chat);
                dismiss();
                return;
            }
            new CommunityAddOptionsSheet(getContext(), currentCommunity, -chat.id,
                (isHidden) -> linkToCommunity(chat, communityId, isHidden)).show();
        }
    }



    private void onClickCommunity(UItem item, View view, int position, float x, float y) {
        if (checkPendingRequestClick(item)) {
            return;
        }

        if (item.id == ROW_ID_TOGGLE_COLLAPSED) {
            collapsedInDialogs = !collapsedInDialogs;
            MessagesController.getInstance(currentAccount).toggleCommunityCollapsedInDialogs(communityId, collapsedInDialogs);
            if (view instanceof TextCheckCell2) {
                ((TextCheckCell2) view).getCheckBox().setChecked(collapsedInDialogs, true);
            } else {
                communityPage.listView.adapter.update(false);
            }
            return;
        }

        if (item.id == ROW_ID_PENDING_REQUESTS) {
            viewPager.scrollToPosition(1);
            pendingRequestsList.markAsViewed();
            return;
        }


        final TLRPC.Chat chat;
        final TLRPC.User user;
        final boolean isChannel;
        final long dialogId;

        if (item.object instanceof TLRPC.Chat) {
            chat = (TLRPC.Chat) item.object;
            user = null;
            isChannel = ChatObject.isChannelAndNotMegaGroup(chat);
            dialogId = -chat.id;
        } else if (item.object instanceof TLRPC.User) {
            user = (TLRPC.User) item.object;
            chat = null;
            isChannel = false;
            dialogId = user.id;
        } else {
            return;
        }


        final CommunityChatType communityChatType = CommunityUtils.getCommunityChatType(currentAccount, dialogId);
        if (communityChatType == CommunityChatType.YouAreIn || communityChatType == CommunityChatType.YouCanView) {
            if (parentFragment instanceof ChatActivity) {
                TLRPC.Chat parentChat = ((ChatActivity) parentFragment).getCurrentChat();
                TLRPC.User parentUser = ((ChatActivity) parentFragment).getCurrentUser();
                if (parentChat != null && parentChat.id == -dialogId || parentUser != null && parentUser.id == dialogId) {
                    dismiss();
                    return;
                }
            }

            Bundle args = new Bundle();
            if (dialogId > 0) {
                args.putLong("user_id", dialogId);
            } else {
                args.putLong("chat_id", -dialogId);
            }

            if (ChatObject.isForum(chat)) {
                if (ChatObject.areTabsEnabled(chat)) {
                    ChatActivity activity = new ChatActivity(args);
                    ForumUtilities.applyTopic(activity, MessagesStorage.TopicKey.of(dialogId, MessagesController.getInstance(currentAccount).getForumLastTopicId(chat.id)));
                    parentFragment.presentFragment(activity);
                } else {
                    parentFragment.presentFragment(new TopicsFragment(args));
                }
            } else {
                parentFragment.presentFragment(new ChatActivity(args));
            }

            dismiss();
        } else if (communityChatType == CommunityChatType.YouCanSendJoinRequest) {
            new JoinGroupAlert(getContext(), chat, null, parentFragment, resourcesProvider)
                .setBulletinFactory(BulletinFactory.of((FrameLayout) containerView, resourcesProvider))
                .show();
        } else if (communityChatType == CommunityChatType.HiddenUnavailable) {
            BulletinFactory.of((FrameLayout) containerView, resourcesProvider)
                .createSimpleBulletin(R.raw.e_hand_2, getString(isChannel ?
                    R.string.CommunityHiddenChannelUnavailable :
                    R.string.CommunityHiddenGroupUnavailable)
                ).show();
        }

    }

    private boolean onLongClickCommunity(UItem item, View view, int position, float x, float y) {
        final long dialogId;
        final boolean isChannel;
        final boolean isBot;
        final boolean canRemoveChatFromCommunity;

        if (item.object instanceof TLRPC.Chat) {
            final TLRPC.Chat chat = (TLRPC.Chat) item.object;
            dialogId = -chat.id;
            isChannel = ChatObject.isChannelAndNotMegaGroup(chat);
            isBot = false;
            canRemoveChatFromCommunity = ChatObject.canRemoveChatFromCommunity(chat, currentCommunity);
        } else if (item.object instanceof TLRPC.User) {
            final TLRPC.User user = (TLRPC.User) item.object;
            dialogId = user.id;
            isChannel = false;
            isBot = UserObject.isBot(user);
            canRemoveChatFromCommunity = ChatObject.canRemoveBotFromCommunity(user, currentCommunity);
        } else {
            return false;
        }

        if (!canRemoveChatFromCommunity) {
            return false;
        }

        final ItemOptions io = ItemOptions.makeOptions(container, view);
        io.add(R.drawable.msg_cancel, getString(R.string.CommunityMenuRemoveFromCommunity), true, () -> AlertsCreator.showSimpleConfirmAlert(getContext(), resourcesProvider,
            getString(R.string.CommunityMenuRemoveFromCommunity),
            getString(isBot ?
                    R.string.CommunityMenuRemoveBotFromCommunityConfirm : isChannel ?
                    R.string.CommunityMenuRemoveChannelFromCommunityConfirm :
                    R.string.CommunityMenuRemoveGroupFromCommunityConfirm),
            getString(R.string.Remove), true, () -> {
                MessagesController.getInstance(currentAccount).unlinkCommunity(dialogId, communityId, (res, err) -> {
                    if (err != null) {
                        BulletinFactory.of((FrameLayout) containerView, resourcesProvider).showForError(err);
                    }
                });
            }));
        io.setScrimViewBackground(communityPage.listView.getClipBackground(view, true));
        io.show();

        return true;
    }

    private void onClickRequest(UItem item, View view, int position, float x, float y) {
        checkPendingRequestClick(item);
    }

    private boolean checkPendingRequestClick(UItem item) {
        if (item.object instanceof CommunityPendingRequestCell.Data) {
            final CommunityPendingRequestCell.Data data = (CommunityPendingRequestCell.Data) item.object;
            final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-data.dialogToAdd);
            final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(data.dialogToAdd);

            if (user != null) {
                parentFragment.presentFragment(ChatActivity.of(user.id));
            } else if (ChatObject.isPublic(chat) || ChatObject.isInChat(chat)) {
                parentFragment.presentFragment(ChatActivity.of(-chat.id));
            } else {
                new CommunityInviteOnlySheet(getContext(), chat, data.requestFromUser, () -> {
                    parentFragment.presentFragment(ChatActivity.of(data.requestFromUser.id));
                }).show();
            }
            return true;
        }
        return false;
    }


    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.chatInfoDidLoad) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (chatFull.id == communityId) {
                communityInfo = chatFull;
                communityPage.listView.adapter.update(true);
            }
        } else if (id == NotificationCenter.updateInterfaces) {
            Integer mask = (Integer) args[0];
            if ((mask & MessagesController.UPDATE_MASK_CHAT) != 0 || (mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_CHAT_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_CHAT_NAME) != 0) {
                currentCommunity = MessagesController.getInstance(currentAccount).getChat(communityId);
                communityPage.actionBar.setTitle(DialogObject.getName(currentCommunity));
                communityPage.avatarImage.setForUserOrChat(currentCommunity, communityPage.avatarDrawable);
            }
        }
    }



    @Override
    public void onBackPressed() {
        if (viewPager.getCurrentPosition() > 0) {

            if (viewPager.getCurrentPosition() == 2 && animatorSearchChatsVisible.getValue()) {
                chatsPage.listView.layoutManager.scrollToPositionWithOffset(1, systemInsets.top);
                animatorSearchChatsVisible.setValue(false, true);
                setAllowNestedScroll(true);
                AndroidUtilities.hideKeyboard(chatsSearchView.editText);
                chatsSearchView.editText.clearFocus();
                return;
            }

            viewPager.scrollToPosition(0);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected boolean canDismissWithSwipe() {
        if (animatorSearchMessagesVisible.getValue() || animatorSearchChatsVisible.getValue()) {
            return false;
        }

        View currentView = viewPager.getCurrentView();
        if (currentView instanceof Page) {
            return ((Page) currentView).wasAtTop;
        }
        return true;
    }

    @Override
    protected boolean canSwipeToBack(MotionEvent event) {
        return false;
    }


    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private void init(Context context) {
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatInfoDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateInterfaces);

        backgroundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
        fixNavigationBar(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));

        containerView = new ContainerView(context);

        viewPager = new ViewPagerFixed(context) {
            @Override
            protected void onScrollEnd() {
                super.onScrollEnd();
                if (getCurrentPosition() == 1) {
                    communityPage.listView.adapter.update(false);
                }
            }

            @Override
            public void onTabAnimationUpdate(boolean manual) {
                containerView.invalidate();
            }

            @Override
            protected boolean canScrollForward(MotionEvent e) {
                return false;
            }

            @Override
            protected boolean canScrollBackward(MotionEvent e) {
                return getCurrentPosition() != 2;
            }
        };
        viewPager.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
        containerView.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
    }

    @Override
    public void dismissInternal() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatInfoDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateInterfaces);
        super.dismissInternal();
    }


    private class CommunityPage extends Page {
        private BackupImageView avatarImage;
        private AvatarDrawable avatarDrawable;

        public CommunityPage(Context context) {
            super(context);

            listView = new UniversalRecyclerView(context, currentAccount, 0, CommunitySheet.this::fillItemsCommunity,
                CommunitySheet.this::onClickCommunity, CommunitySheet.this::onLongClickCommunity, resourcesProvider);
            listView.setSections();
            listView.adapter.setApplyBackground(false);
            listView.setClipToPadding(false);
            listView.setPadding(0, 0, 0, AndroidUtilities.navigationBarHeight + dp(12 + 48));

            AndroidUtilities.removeFromParent(fadeView);

            contentView.addView(filteredSearchView, LayoutHelper.createFrameMatchParent());
            contentView.addView(listView, LayoutHelper.createFrameMatchParent());
            contentView.addView(communityPageFadeView, LayoutHelper.createFrameMatchParent());


            actionBar = new ActionBar(context, resourcesProvider);
            actionBar.setOccupyStatusBar(false);
            actionBar.setTitleColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_actionBarActionModeDefaultSelector), false);
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setItemsColor(getThemedColor(Theme.key_actionBarActionModeDefaultIcon), false);
            actionBar.setTitle(DialogObject.getName(currentCommunity));
            actionBar.getTitleTextView().setTranslationX(-dp(18));
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == 2) {
                        Bundle args = new Bundle();
                        args.putLong("community_id", communityId);
                        parentFragment.presentFragment(new CommunityEditActivity(args));
                        dismiss();
                    } else if (id == 3) {
                        animatorSearchMessagesVisible.setValue(true, true);
                        setAllowNestedScroll(false);
                        onMessagesSearchTextChanged(null, true);
                        messagesSearchView.editText.getText().clear();
                        messagesSearchView.editText.requestFocus();
                        AndroidUtilities.showKeyboard(messagesSearchView.editText);
                    }
                }
            });

            avatarDrawable = new AvatarDrawable(currentCommunity);
            avatarImage = new BackupImageView(getContext());
            avatarImage.setRoundRadius(dp(9));
            avatarImage.setForUserOrChat(currentCommunity, avatarDrawable);
            actionBar.addView(avatarImage, LayoutHelper.createFrame(27.33f, 27.33f, Gravity.BOTTOM | Gravity.LEFT, 14.33f, 0, 0, 14.33f));

            contentView.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56, Gravity.TOP));
            contentView.addView(messagesSearchView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.TOP, 11, 0, 11, 0));

            ActionBarMenu menu = actionBar.createMenu();
            menu.setGlassMode(true);
            menu.setTranslationX(-dp(7));
            menu.addItem(3, R.drawable.outline_header_search);
            if (ChatObject.hasAdminRights(currentCommunity)) {
                menu.addItem(2, R.drawable.msg_download_settings);
            }

            ButtonWithCounterView bottomButton = new ButtonWithCounterView(getContext(), resourcesProvider);
            bottomButton.setRound();
            if (ChatObject.canAddChatToCommunity(currentCommunity)) {
                final ColoredImageSpan span = new ColoredImageSpan(R.drawable.filled_add_album);
                final SpannableStringBuilder sb = new SpannableStringBuilder("+ ");
                sb.append(getString(R.string.CommunityAddAChatToCommunity));
                sb.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                bottomButton.setText(sb);
            } else {
                bottomButton.setText(getString(R.string.OK));
            }

            addChatToCommunityButton = bottomButton;
            addChatToCommunityButton.setOnClickListener(v -> onAddChatToCommunityButtonClick());

            contentView.addView(bottomButton, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM,
                dp(12), 0, dp(12),
                AndroidUtilities.navigationBarHeight + dp(12)));

            afterInit();
        }

        @Override
        public float top() {
            return super.top() * FBool.not(animatorSearchMessagesVisible.getFloatValue());
        }

        @Override
        public void updateTops() {
            super.updateTops();
            messagesSearchView.setTranslationY(Math.max(AndroidUtilities.statusBarHeight + dp(8), top() + dp(4)));
        }
    }

    private void onAddChatToCommunityButtonClick() {
        if (!ChatObject.canAddChatToCommunity(currentCommunity)) {
            dismiss();
            return;
        }

        loadChatsToAddToCommunity();
    }


    private ArrayList<TLRPC.Chat> chatsToAddToCommunity;

    private void loadChatsToAddToCommunity() {
        if (addChatToCommunityButton.isLoading()) {
            return;
        }

        addChatToCommunityButton.setLoading(true);

        MessagesController.getInstance(currentAccount).fetchChatsToAddToCommunity((res, err) -> {
            addChatToCommunityButton.setLoading(false);
            if (err != null) {
                BulletinFactory.of((FrameLayout) containerView, resourcesProvider).showForError(err);
                return;
            }
            if (res != null) {
                chatsToAddToCommunity = res;
                if (chatsToAddToCommunity.isEmpty()) {
                    BulletinFactory.of((FrameLayout) containerView, resourcesProvider).createSimpleBulletin(R.raw.info, getString(R.string.CommunityNoChatsToAdd)).show();
                } else {
                    chatsPage.listView.adapter.update(false);
                    viewPager.scrollToPosition(2);
                }
            }
        });
    }

    private class ChatsToAddListPage extends Page {
        public ChatsToAddListPage(Context context) {
            super(context);

            AndroidUtilities.removeFromParent(fadeView);

            listView = new UniversalRecyclerView(context, currentAccount, 0, CommunitySheet.this::fillItemsChatsToAdd,
                    CommunitySheet.this::onClickChatToAdd, null, resourcesProvider);
            listView.setSections();
            listView.adapter.setApplyBackground(false);
            listView.setClipToPadding(false);
            listView.setPadding(0, 0, 0, AndroidUtilities.navigationBarHeight + dp(12 + 48));

            contentView.addView(foundChatsView, LayoutHelper.createFrameMatchParent());
            contentView.addView(listView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            contentView.addView(chatsPageFadeView, LayoutHelper.createFrameMatchParent());

            actionBar = new ActionBar(context, resourcesProvider);
            actionBar.setOccupyStatusBar(false);
            actionBar.setTitleColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_actionBarActionModeDefaultSelector), false);
            actionBar.setBackButtonImage(onlyChatsMode ? R.drawable.ic_ab_close : R.drawable.ic_ab_back);
            actionBar.setItemsColor(getThemedColor(Theme.key_actionBarActionModeDefaultIcon), false);
            actionBar.setTitle(getString(R.string.CommunityAddAChatToCommunity));
            actionBar.getTitleTextView().setTranslationX(-dp(18));
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        if (onlyChatsMode) {
                            dismiss();
                        } else {
                            communityPage.listView.adapter.update(false);
                            viewPager.scrollToPosition(0);
                        }
                    } else if (id == 3) {
                        animatorSearchChatsVisible.setValue(true, true);
                        setAllowNestedScroll(false);
                        onChatsSearchTextChanged(null);
                        chatsSearchView.editText.getText().clear();
                        chatsSearchView.editText.requestFocus();
                        AndroidUtilities.showKeyboard(chatsSearchView.editText);
                    }
                }
            });
            contentView.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56, Gravity.TOP));
            contentView.addView(chatsSearchView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.TOP, 11, 0, 11, 0));

            ActionBarMenu menu = actionBar.createMenu();
            menu.setGlassMode(true);
            menu.setTranslationX(-dp(7));
            menu.addItem(3, R.drawable.outline_header_search);

            closeChatToCommunityButton = new ButtonWithCounterView(getContext(), resourcesProvider);
            closeChatToCommunityButton.setRound();
            closeChatToCommunityButton.setText(getString(R.string.OK));
            closeChatToCommunityButton.setOnClickListener(v -> viewPager.scrollToPosition(0));
            if (onlyChatsMode) {
                closeChatToCommunityButton.setVisibility(View.GONE);
            }
            contentView.addView(closeChatToCommunityButton, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM,
                    dp(12), 0, dp(12),
                    AndroidUtilities.navigationBarHeight + dp(12)));

            afterInit();
        }

        @Override
        public void updateTops() {
            super.updateTops();
            chatsSearchView.setTranslationY(Math.max(AndroidUtilities.statusBarHeight + dp(8), top() + dp(4)));
        }

        @Override
        public float top() {
            return super.top() * FBool.not(animatorSearchChatsVisible.getFloatValue());
        }
    }

    private class PendingRequestsPage extends Page {
        public PendingRequestsPage(Context context) {
            super(context);

            listView = new UniversalRecyclerView(context, currentAccount, 0, CommunitySheet.this::fillItemsRequests,
                    CommunitySheet.this::onClickRequest, null, resourcesProvider);
            listView.setSections();
            listView.adapter.setApplyBackground(false);
            listView.setClipToPadding(false);
            listView.setPadding(0, 0, 0, AndroidUtilities.navigationBarHeight + dp(12 + 48));
            listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    pendingRequestsList.checkLoadNext(listView);
                }
            });
            contentView.addView(listView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            actionBar = new ActionBar(context, resourcesProvider);
            actionBar.setOccupyStatusBar(false);
            actionBar.setTitleColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_actionBarActionModeDefaultSelector), false);
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setItemsColor(getThemedColor(Theme.key_actionBarActionModeDefaultIcon), false);
            actionBar.setTitle(getString(R.string.CommunityPendingRequestsTitle));
            actionBar.getTitleTextView().setTranslationX(-dp(18));
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        communityPage.listView.adapter.update(false);
                        viewPager.scrollToPosition(0);
                    }
                }
            });
            contentView.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56, Gravity.TOP));

            LinearLayout buttonsLayout = new LinearLayout(context);
            buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
            buttonsLayout.setPadding(dp(7), 0, dp(7), dp(12));

            ButtonWithCounterView buttonDeclineAllView = new ButtonWithCounterView(context, resourcesProvider);
            buttonDeclineAllView.setNeutral();
            buttonDeclineAllView.setColor(ColorUtils.blendARGB(getThemedColor(Theme.key_windowBackgroundWhite), getThemedColor(Theme.key_windowBackgroundWhiteBlackText), 0.125f));
            buttonDeclineAllView.setText(getString(R.string.CommunityPendingRequestDeclineAll));
            buttonDeclineAllView.setRound();
            buttonDeclineAllView.setOnClickListener(v -> pendingRequestsList.onResolveAllJoinRequests(false));
            buttonsLayout.addView(buttonDeclineAllView, LayoutHelper.createLinear(0, 48, 1f, Gravity.NO_GRAVITY, 4, 0, 4, 0));

            ButtonWithCounterView buttonAddAllView = new ButtonWithCounterView(context, resourcesProvider);
            buttonAddAllView.setText(getString(R.string.CommunityPendingRequestAddAll));
            buttonAddAllView.setRound();
            buttonAddAllView.setOnClickListener(v -> pendingRequestsList.onResolveAllJoinRequests(true));
            buttonsLayout.addView(buttonAddAllView, LayoutHelper.createLinear(0, 48, 1f, Gravity.NO_GRAVITY, 4, 0, 4, 0));

            contentView.addView(buttonsLayout, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 0, 0, 0, AndroidUtilities.navigationBarHeight));

            afterInit();
        }
    }


    private abstract class Page extends FrameLayout {
        public int pageType;

        protected ActionBar actionBar;
        protected ChatActivityFadeView fadeView;
        protected final FrameLayout contentView;
        protected UniversalRecyclerView listView;

        public Page(Context context) {
            super(context);

            contentView = new FrameLayout(context);
            contentView.setPadding(0, 0, 0, 0);
            contentView.setClipToPadding(true);
            addView(contentView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

            fadeView = new ChatActivityFadeView(getContext());
            fadeView.setupColorKey(Theme.key_windowBackgroundGray);
            fadeView.setFadeZoneBottom(dp(72) + AndroidUtilities.navigationBarHeight);
            fadeView.setFadeHeightBottom(dp(24));
            fadeView.setFadeZoneTop(dp(64) + AndroidUtilities.statusBarHeight);
            fadeView.setFadeHeightTop(dp(20), false);
            contentView.addView(fadeView, LayoutHelper.createFrameMatchParent());
        }

        protected void afterInit() {
            listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    containerView.invalidate();
                }

                @Override
                public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        wasAtTop = atTop();
                        wasAtBottom = atBottom();
                    }
                    scrolling = newState != RecyclerView.SCROLL_STATE_IDLE;
                }
            });

            DefaultItemAnimator itemAnimator = new DefaultItemAnimator() {
                @Override
                protected void onMoveAnimationUpdate(RecyclerView.ViewHolder holder) {
                    containerView.invalidate();
                    contentView.invalidate();
                    listView.invalidate();
                }

                @Override
                protected void onChangeAnimationUpdate(RecyclerView.ViewHolder holder) {
                    containerView.invalidate();
                    contentView.invalidate();
                }

                @Override
                protected void onAddAnimationUpdate(RecyclerView.ViewHolder holder) {
                    containerView.invalidate();
                    contentView.invalidate();
                }

                @Override
                protected void onRemoveAnimationUpdate(RecyclerView.ViewHolder holder) {
                    containerView.invalidate();
                    contentView.invalidate();
                }

                @Override
                public boolean canReuseUpdatedViewHolder(RecyclerView.ViewHolder viewHolder) {
                    return true;
                }
            };
            itemAnimator.setDurations(350);
            itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            itemAnimator.setDelayAnimations(false);
            itemAnimator.setSupportsChangeAnimations(false);
            listView.setItemAnimator(itemAnimator);
        }

        public float top() {
            float top = AndroidUtilities.displaySize.y;
            for (int i = 0; i < listView.getChildCount(); ++i) {
                final View child = listView.getChildAt(i);
                final RecyclerView.ViewHolder viewHolder = listView.getChildViewHolder(child);
                if (viewHolder == null) {
                    continue;
                }

                final int position = viewHolder.getAdapterPosition();
                final UItem item = listView.adapter.getItem(position);
                if (item != null && item.id != ROW_ID_EMPTY_SPACE) {
                    top = Math.min(contentView.getPaddingTop() + child.getY(), top);
                }
            }
            return top;
        }

        public void bind(int pageType) {
            this.pageType = pageType;
        }

        public void updateTops() {
            final float top = top();
            if (actionBar != null) {
                actionBar.setTranslationY(Math.max(AndroidUtilities.statusBarHeight, top));
            }
        }



        private boolean scrolling;
        public boolean wasAtTop;
        public boolean atTop() {
            return !listView.canScrollVertically(-1);
        }
        public boolean wasAtBottom;
        public boolean atBottom() {
            return !listView.canScrollVertically(1);
        }
    }

    private class ContainerView extends FrameLayout {

        public ContainerView(Context context) {
            super(context);
        }

        private final AnimatedFloat isActionBar = new AnimatedFloat(this, 250, CubicBezierInterpolator.EASE_OUT_QUINT);
        private float top;

        private final Path path = new Path();

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            View[] views = viewPager.getViewPages();
            top = 0;
            for (int i = 0; i < views.length; ++i) {
                if (views[i] == null) {
                    continue;
                }
                final Page page = (Page) views[i];
                float t = Utilities.clamp(1f - Math.abs(page.getTranslationX() / (float) page.getMeasuredWidth()), 1, 0);
                top += page.top() * t;
                if (page.getVisibility() == View.VISIBLE) {
                    page.updateTops();
                }
            }
            float actionBarT = isActionBar.set(top <= AndroidUtilities.statusBarHeight ? 1f : 0f);

            final float searchFactor = Math.max(animatorSearchMessagesVisible.getFloatValue(), animatorSearchChatsVisible.getFloatValue());

            top = Math.max(AndroidUtilities.statusBarHeight, top)
                - AndroidUtilities.statusBarHeight * actionBarT
                - dp(10) * searchFactor;

            AndroidUtilities.rectTmp.set(backgroundPaddingLeft, top, getWidth() - backgroundPaddingLeft, getHeight() + dp(8));
            final float r = AndroidUtilities.lerp(dp(14), 0, actionBarT);
            canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, backgroundPaint);
            canvas.save();
            path.rewind();
            path.addRoundRect(AndroidUtilities.rectTmp, r, r, Path.Direction.CW);
            canvas.clipPath(path);
            super.dispatchDraw(canvas);
            canvas.restore();
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN && event.getY() < top) {
                dismiss();
                return true;
            }
            return super.dispatchTouchEvent(event);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY)
            );
        }
    }








    private void linkToCommunity(TLRPC.Chat chat, long communityId, boolean isHidden) {
        final long dialogId = -chat.id;
        final boolean isChannel = ChatObject.isChannelAndNotMegaGroup(chat);
        if (!ChatObject.isChannel(chat)) {
            final AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER);
            progressDialog.showDelayed(250);
            MessagesController.getInstance(currentAccount).convertToMegaGroup(getContext(), -dialogId, null, param -> {
                progressDialog.dismiss();
                if (param == 0) {
                    return;
                }

                linkToCommunity(MessagesController.getInstance(currentAccount).getChat(param), communityId, isHidden);
            });
        } else {
            MessagesController.getInstance(currentAccount).linkCommunity(dialogId, communityId, isHidden, (res, err) -> {
                if (err != null) {
                    if (TextUtils.equals("COMMUNITY_REQUEST_CREATED", err.text)) {
                        onLinkSuccess(CommunityUtils.STATUS_PENDING, isChannel);
                        return;
                    }

                    BulletinFactory.of((FrameLayout) containerView, resourcesProvider).showForError(err);
                    return;
                }
                onLinkSuccess(CommunityUtils.STATUS_JOINED, isChannel);
            });
        }
    }

    private void onLinkSuccess(int status, boolean isChannel) {
        CommunityUtils.showCommunityLinkSuccessToast(BulletinFactory.of((FrameLayout) containerView, resourcesProvider), status, isChannel);
        viewPager.scrollToPosition(0);
    }




    private void onChatsSearchTextChanged(String text) {
        lastSearchChatsString = text;
        foundChatsView.adapter.update(true);
    }


    private String lastSearchString;
    private String lastSearchChatsString;

    private void onMessagesSearchTextChanged(String text) {
        onMessagesSearchTextChanged(text, false);
    }

    private void onMessagesSearchTextChanged(String text, boolean forceReset) {
        boolean reset = forceReset;
        if (TextUtils.isEmpty(lastSearchString)) {
            reset = true;
        }
        lastSearchString = text;
        filteredSearchView.search(0, communityId, 0, 0, null, false, text, reset);
    }


    private Insets systemAndImeInsets = Insets.NONE;
    private Insets systemInsets = Insets.NONE;

    @NonNull
    private WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
        systemAndImeInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
        systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());

        filteredSearchView.setPagesPaddings(systemAndImeInsets.top + dp(56), systemAndImeInsets.bottom);
        communityPageFadeView.invalidate();

        return WindowInsetsCompat.CONSUMED;
    }


    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        if (id == ANIMATOR_ID_SEARCH_MESSAGES_VISIBLE) {
            final float lFactor = FBool.not(factor);
            final float aScale = lerp(0.9f, 1, lFactor);

            communityPage.actionBar.setAlpha(lFactor);
            communityPage.actionBar.setScaleX(aScale);
            communityPage.actionBar.setScaleY(aScale);
            communityPage.actionBar.setVisibility(lFactor > 0 ? View.VISIBLE : View.GONE);

            final float scale = lerp(0.9f, 1, factor);
            messagesSearchView.setAlpha(factor);
            messagesSearchView.setScaleX(scale);
            messagesSearchView.setScaleY(scale);
            messagesSearchView.setVisibility(factor > 0 ? View.VISIBLE : View.GONE);

            communityPage.listView.setAlpha(lFactor);
            communityPage.listView.setVisibility(lFactor > 0 ? View.VISIBLE : View.GONE);

            addChatToCommunityButton.setAlpha(lFactor);
            addChatToCommunityButton.setScaleX(lerp(0.95f, 1f, lFactor));
            addChatToCommunityButton.setScaleY(lerp(0.95f, 1f, lFactor));
            addChatToCommunityButton.setVisibility(lFactor > 0 ? View.VISIBLE : View.GONE);

            filteredSearchView.setAlpha(factor);
            filteredSearchView.setVisibility(factor > 0 ? View.VISIBLE : View.GONE);

            containerView.invalidate();
            communityPageFadeView.invalidate();
        }
        if (id == ANIMATOR_ID_SEARCH_CHATS_VISIBLE) {
            final float lFactor = FBool.not(factor);
            final float aScale = lerp(0.9f, 1, lFactor);

            chatsPage.actionBar.setAlpha(lFactor);
            chatsPage.actionBar.setScaleX(aScale);
            chatsPage.actionBar.setScaleY(aScale);
            chatsPage.actionBar.setVisibility(lFactor > 0 ? View.VISIBLE : View.GONE);

            final float scale = lerp(0.9f, 1, factor);
            chatsSearchView.setAlpha(factor);
            chatsSearchView.setScaleX(scale);
            chatsSearchView.setScaleY(scale);
            chatsSearchView.setVisibility(factor > 0 ? View.VISIBLE : View.GONE);

            chatsPage.listView.setAlpha(lFactor);
            chatsPage.listView.setVisibility(lFactor > 0 ? View.VISIBLE : View.GONE);

            if (!onlyChatsMode) {
                closeChatToCommunityButton.setAlpha(lFactor);
                closeChatToCommunityButton.setScaleX(lerp(0.95f, 1f, lFactor));
                closeChatToCommunityButton.setScaleY(lerp(0.95f, 1f, lFactor));
                closeChatToCommunityButton.setVisibility(lFactor > 0 ? View.VISIBLE : View.GONE);
            }

            foundChatsView.setAlpha(factor);
            foundChatsView.setVisibility(factor > 0 ? View.VISIBLE : View.GONE);

            containerView.invalidate();
            chatsPageFadeView.invalidate();
        }
    }



    private class FadeView extends View {

        public FadeView(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);

            final float searchFactor = Math.max(animatorSearchMessagesVisible.getFloatValue(), animatorSearchChatsVisible.getFloatValue());

            gradientProtectionDrawableTop.setInsets(0, systemInsets.top + dp(42), 0, 0);
            gradientProtectionDrawableTop.setBounds(0, 0, getWidth(), systemInsets.top + dp(56));
            gradientProtectionDrawableTop.setColor(Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundGray), lerp(1, 0.8f, searchFactor)));
            gradientProtectionDrawableTop.draw(canvas);


            final float searchFactorBottom = onlyChatsMode ? 1 : searchFactor;
            final int bottomInset = lerp(systemInsets.bottom + dp(48), 0, searchFactorBottom);
            final int bottomHeight = systemInsets.bottom + lerp(dp(72), 0, searchFactorBottom);
            final float bottomAlpha = lerp(0.8f, AndroidUtilities.getNavigationBarThirdButtonsFactor(systemInsets.bottom), searchFactorBottom);

            gradientProtectionDrawableBottom.setInsets(0, 0, 0, bottomInset);
            gradientProtectionDrawableBottom.setBounds(0, getHeight() - bottomHeight, getWidth(), getHeight());
            gradientProtectionDrawableBottom.setColor(Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundGray), bottomAlpha));
            gradientProtectionDrawableBottom.draw(canvas);
        }
    }
}
