package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.util.LongSparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ReactedUserHolderView;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

public class ReactedUsersListView extends FrameLayout {

    public final static int VISIBLE_ITEMS = 6;
    public final static int ITEM_HEIGHT_DP = 50;

    private final static int USER_VIEW_TYPE = 0;
    private final static int CUSTOM_EMOJI_VIEW_TYPE = 1;

    private int predictiveCount;
    private int currentAccount;
    private MessageObject message;
    private TLRPC.Reaction filter;

    public RecyclerListView listView;
    private RecyclerView.Adapter adapter;

    private FlickerLoadingView loadingView;

    private List<TLRPC.MessagePeerReaction> userReactions = new ArrayList<>();
    private LongSparseArray<ArrayList<TLRPC.MessagePeerReaction>> peerReactionMap = new LongSparseArray<>();
    private String offset;
    public boolean isLoading, isLoaded, canLoadMore = true;
    private boolean onlySeenNow;

    private OnHeightChangedListener onHeightChangedListener;
    private OnProfileSelectedListener onProfileSelectedListener;
    private OnCustomEmojiSelectedListener onCustomEmojiSelectedListener;
    ArrayList<ReactionsLayoutInBubble.VisibleReaction> customReactionsEmoji = new ArrayList<>();
    ArrayList<TLRPC.InputStickerSet> customEmojiStickerSets = new ArrayList<>();
    MessageContainsEmojiButton messageContainsEmojiButton;
    Theme.ResourcesProvider resourcesProvider;

    public ReactedUsersListView(Context context, Theme.ResourcesProvider resourcesProvider, int currentAccount, MessageObject message, TLRPC.ReactionCount reactionCount, boolean addPadding) {
        super(context);
        this.currentAccount = currentAccount;
        this.message = message;
        this.filter = reactionCount == null ? null : reactionCount.reaction;
        this.resourcesProvider = resourcesProvider;
        predictiveCount = reactionCount == null ? VISIBLE_ITEMS : reactionCount.count;
        listView = new RecyclerListView(context, resourcesProvider) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                if (messageContainsEmojiButton != null) {
                    messageContainsEmojiButton.measure(widthSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightSpec), MeasureSpec.UNSPECIFIED));
                }
                super.onMeasure(widthSpec, heightSpec);
                updateHeight();
            }
        };
        final LinearLayoutManager llm = new LinearLayoutManager(context);
        listView.setLayoutManager(llm);
        if (addPadding) {
            listView.setPadding(0, 0, 0, AndroidUtilities.dp(8));
            listView.setClipToPadding(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listView.setVerticalScrollbarThumbDrawable(new ColorDrawable(Theme.getColor(Theme.key_listSelector)));
        }
        listView.setAdapter(adapter = new RecyclerView.Adapter() {

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = null;
                switch (viewType) {
                    case USER_VIEW_TYPE:
                        view = new ReactedUserHolderView(ReactedUserHolderView.STYLE_DEFAULT, currentAccount, context, null);
                        break;
                    default:
                    case CUSTOM_EMOJI_VIEW_TYPE:
                        if (messageContainsEmojiButton != null) {
                            if (messageContainsEmojiButton.getParent() != null) {
                                ((ViewGroup) messageContainsEmojiButton.getParent()).removeView(messageContainsEmojiButton);
                            }
                        } else {
                            updateCustomReactionsButton();
                        }

                        FrameLayout frameLayout = new FrameLayout(context);
                        View gap = new View(context);
                        gap.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuSeparator, resourcesProvider));
                        frameLayout.addView(gap, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 8));
                        frameLayout.addView(messageContainsEmojiButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 0, 8, 0, 0));

                        view = frameLayout;
                        break;
                }
                return new RecyclerListView.Holder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                if (holder.getItemViewType() == USER_VIEW_TYPE) {
                    ReactedUserHolderView rhv = (ReactedUserHolderView) holder.itemView;
                    rhv.setUserReaction(userReactions.get(position));
                }
            }

            @Override
            public int getItemCount() {
                return userReactions.size() + (!customReactionsEmoji.isEmpty() && !MessagesController.getInstance(currentAccount).premiumFeaturesBlocked() ? 1 : 0);
            }

            @Override
            public int getItemViewType(int position) {
                if (position < userReactions.size()) {
                    return USER_VIEW_TYPE;
                }
                return CUSTOM_EMOJI_VIEW_TYPE;
            }
        });
        listView.setOnItemClickListener((view, position) -> {
            int itemViewType = adapter.getItemViewType(position);
            if (itemViewType == USER_VIEW_TYPE) {
                if (onProfileSelectedListener != null) {
                    onProfileSelectedListener.onProfileSelected(this, MessageObject.getPeerId(userReactions.get(position).peer_id), userReactions.get(position));
                }
            } else if (itemViewType == CUSTOM_EMOJI_VIEW_TYPE) {
                if (onCustomEmojiSelectedListener != null) {
                    onCustomEmojiSelectedListener.showCustomEmojiAlert(this, customEmojiStickerSets);
                }
            }
        });
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (isLoaded && canLoadMore && !isLoading && llm.findLastVisibleItemPosition() >= adapter.getItemCount() - 1 - getLoadCount()) {
                    load();
                }
            }
        });
        listView.setVerticalScrollBarEnabled(true);
        listView.setAlpha(0);
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        loadingView = new FlickerLoadingView(context, resourcesProvider) {
            @Override
            public int getAdditionalHeight() {
                return !customReactionsEmoji.isEmpty() && messageContainsEmojiButton != null ? messageContainsEmojiButton.getMeasuredHeight() + AndroidUtilities.dp(8) : 0;
            }
        };
        loadingView.setColors(Theme.key_actionBarDefaultSubmenuBackground, Theme.key_listSelector, -1);

        loadingView.setIsSingleCell(true);
        loadingView.setItemsCount(predictiveCount);
        addView(loadingView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        if (!addPadding && filter != null && filter instanceof TLRPC.TL_reactionCustomEmoji && !MessagesController.getInstance(currentAccount).premiumFeaturesBlocked()) {
            customReactionsEmoji.clear();
            customReactionsEmoji.add(ReactionsLayoutInBubble.VisibleReaction.fromTLReaction(filter));
            updateCustomReactionsButton();
        }
        loadingView.setViewType(customReactionsEmoji.isEmpty() ? FlickerLoadingView.REACTED_TYPE : FlickerLoadingView.REACTED_TYPE_WITH_EMOJI_HINT);
    }

    @SuppressLint("NotifyDataSetChanged")
    public ReactedUsersListView setSeenUsers(List<ReactedHeaderView.UserSeen> users) {
        if (userReactions != null && !userReactions.isEmpty()) {
            for (ReactedHeaderView.UserSeen p : users) {
                TLObject user = p.user;
                if (user != null && p.date > 0) {
                    for (int i = 0; i < userReactions.size(); ++i) {
                        TLRPC.MessagePeerReaction react = userReactions.get(i);
                        if (react != null && react.date <= 0 && MessageObject.getPeerId(react.peer_id) == p.dialogId) {
                            react.date = p.date;
                            react.dateIsSeen = true;
                            break;
                        }
                    }
                }
            }
        }
        List<TLRPC.TL_messagePeerReaction> nr = new ArrayList<>(users.size());
        for (ReactedHeaderView.UserSeen p : users) {
            ArrayList<TLRPC.MessagePeerReaction> userReactions = peerReactionMap.get(p.dialogId);
            if (userReactions != null) {
               continue;
            }
            TLRPC.TL_messagePeerReaction r = new TLRPC.TL_messagePeerReaction();
            r.reaction = null;
            if (p.user instanceof TLRPC.User) {
                r.peer_id = new TLRPC.TL_peerUser();
                r.peer_id.user_id = ((TLRPC.User) p.user).id;
            } else  if (p.user instanceof TLRPC.Chat) {
                r.peer_id = new TLRPC.TL_peerChat();
                r.peer_id.chat_id = ((TLRPC.Chat) p.user).id;
            }
            r.date = p.date;
            r.dateIsSeen = true;
            userReactions = new ArrayList<>();
            userReactions.add(r);
            peerReactionMap.put(MessageObject.getPeerId(r.peer_id), userReactions);
            nr.add(r);
        }
        if (userReactions.isEmpty()) {
            onlySeenNow = true;
        }
        userReactions.addAll(nr);
        Collections.sort(userReactions, Comparator.comparingInt(o -> o.date <= 0 || o.reaction != null ? Integer.MIN_VALUE : -o.date));

        adapter.notifyDataSetChanged();
        updateHeight();
        return this;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isLoaded && !isLoading) {
            load();
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void load() {
        isLoading = true;

        MessagesController ctrl = MessagesController.getInstance(currentAccount);
        TLRPC.TL_messages_getMessageReactionsList getList = new TLRPC.TL_messages_getMessageReactionsList();
        getList.peer = ctrl.getInputPeer(message.getDialogId());
        getList.id = message.getId();
        getList.limit = getLoadCount();
        getList.reaction = filter;
        getList.offset = offset;
        if (filter != null)
            getList.flags |= 1;
        if (offset != null)
            getList.flags |= 2;
        ConnectionsManager.getInstance(currentAccount).sendRequest(getList, (response, error) -> {
            AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).doOnIdle(() -> {
                if (response instanceof TLRPC.TL_messages_messageReactionsList) {
                    TLRPC.TL_messages_messageReactionsList res = (TLRPC.TL_messages_messageReactionsList) response;

                    for (TLRPC.User u : res.users) {
                        MessagesController.getInstance(currentAccount).putUser(u, false);
                    }

                    HashSet<ReactionsLayoutInBubble.VisibleReaction> visibleCustomEmojiReactions = new HashSet<>();
                    for (int i = 0; i < res.reactions.size(); i++) {
                        userReactions.add(res.reactions.get(i));
                        long peerId = MessageObject.getPeerId(res.reactions.get(i).peer_id);
                        ArrayList<TLRPC.MessagePeerReaction> currentUserReactions = peerReactionMap.get(peerId);
                        if (currentUserReactions == null) {
                            currentUserReactions = new ArrayList<>();
                        }
                        for (int k = 0; k < currentUserReactions.size(); k++) {
                            if (currentUserReactions.get(k).reaction == null) {
                                currentUserReactions.remove(k);
                                k--;
                            }
                        }


                        ReactionsLayoutInBubble.VisibleReaction visibleReaction = ReactionsLayoutInBubble.VisibleReaction.fromTLReaction(res.reactions.get(i).reaction);
                        if (visibleReaction.documentId != 0) {
                            visibleCustomEmojiReactions.add(visibleReaction);
                        }
                        currentUserReactions.add(res.reactions.get(i));
                        peerReactionMap.put(peerId, currentUserReactions);
                    }

                    if (filter == null) {
                        customReactionsEmoji.clear();
                        customReactionsEmoji.addAll(visibleCustomEmojiReactions);
                        updateCustomReactionsButton();
                    }

                    Collections.sort(userReactions, Comparator.comparingInt(o -> o.date <= 0 || o.reaction != null ? Integer.MIN_VALUE : -o.date));

                    adapter.notifyDataSetChanged();

                    if (!isLoaded) {
                        ValueAnimator anim = ValueAnimator.ofFloat(0, 1).setDuration(150);
                        anim.setInterpolator(CubicBezierInterpolator.DEFAULT);
                        anim.addUpdateListener(animation -> {
                            float val = (float) animation.getAnimatedValue();
                            listView.setAlpha(val);
                            loadingView.setAlpha(1f - val);
                        });
                        anim.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                loadingView.setVisibility(GONE);
                            }
                        });
                        anim.start();

                        updateHeight();

                        isLoaded = true;
                    }
                    offset = res.next_offset;
                    if (offset == null)
                        canLoadMore = false;
                    isLoading = false;
                } else {
                    isLoading = false;
                }
            }));
        }, ConnectionsManager.RequestFlagInvokeAfter);
    }

    private void updateCustomReactionsButton() {
        customEmojiStickerSets.clear();
        ArrayList<TLRPC.InputStickerSet> sets = new ArrayList<>();
        HashSet<Long> setIds = new HashSet<>();
        for (int i = 0; i < customReactionsEmoji.size(); i++) {
            TLRPC.InputStickerSet stickerSet = MessageObject.getInputStickerSet(AnimatedEmojiDrawable.findDocument(currentAccount, customReactionsEmoji.get(i).documentId));
            if (stickerSet != null && !setIds.contains(stickerSet.id)) {
                sets.add(stickerSet);
                setIds.add(stickerSet.id);
            }
        }
        if (MessagesController.getInstance(currentAccount).premiumFeaturesBlocked()) {
            return;
        }
        customEmojiStickerSets.addAll(sets);
        messageContainsEmojiButton = new MessageContainsEmojiButton(currentAccount, getContext(), resourcesProvider, sets, MessageContainsEmojiButton.REACTIONS_TYPE);
        messageContainsEmojiButton.checkWidth = false;
    }

    private void updateHeight() {
        if (onHeightChangedListener != null) {
            int h;
            int count = userReactions.size();
            if (count == 0) {
                count = predictiveCount;
            }
            int measuredHeight = AndroidUtilities.dp(ITEM_HEIGHT_DP * count);
            if (messageContainsEmojiButton != null) {
                measuredHeight += messageContainsEmojiButton.getMeasuredHeight() + AndroidUtilities.dp(8);
            }
            if (listView.getMeasuredHeight() != 0) {
                h = Math.min(listView.getMeasuredHeight(), measuredHeight);
            } else {
                h = measuredHeight;
            }
            onHeightChangedListener.onHeightChanged(ReactedUsersListView.this, h);
        }
    }

    private int getLoadCount() {
        return filter == null ? 100 : 50;
    }

    public ReactedUsersListView setOnProfileSelectedListener(OnProfileSelectedListener onProfileSelectedListener) {
        this.onProfileSelectedListener = onProfileSelectedListener;
        return this;
    }

    public ReactedUsersListView setOnHeightChangedListener(OnHeightChangedListener onHeightChangedListener) {
        this.onHeightChangedListener = onHeightChangedListener;
        return this;
    }

    public interface OnHeightChangedListener {
        void onHeightChanged(ReactedUsersListView view, int newHeight);
    }

    public interface OnProfileSelectedListener {
        void onProfileSelected(ReactedUsersListView view, long userId, TLRPC.MessagePeerReaction messagePeerReaction);
    }

    public interface OnCustomEmojiSelectedListener {
        void showCustomEmojiAlert(ReactedUsersListView reactedUsersListView, ArrayList<TLRPC.InputStickerSet> stickerSets);
    }

    public void setPredictiveCount(int predictiveCount) {
        this.predictiveCount = predictiveCount;
        loadingView.setItemsCount(predictiveCount);
    }

    public static class ContainerLinerLayout extends LinearLayout {

        public boolean hasHeader;

        public ContainerLinerLayout(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int maxWidth = 0;
            RecyclerListView listView = null;
            if (!hasHeader) {
                for (int k = 0; k < getChildCount(); k++) {
                    if (getChildAt(k) instanceof ReactedUsersListView) {
                        listView = ((ReactedUsersListView) getChildAt(k)).listView;
                        if (listView.getAdapter().getItemCount() == listView.getChildCount()) {
                            int count = listView.getChildCount();
                            for (int i = 0; i < count; i++) {
                                listView.getChildAt(i).measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), MeasureSpec.UNSPECIFIED), heightMeasureSpec);
                                if (listView.getChildAt(i).getMeasuredWidth() > maxWidth) {
                                    maxWidth = listView.getChildAt(i).getMeasuredWidth();
                                }
                            }
                            maxWidth += AndroidUtilities.dp(16);
                        }
                    }
                }
            }
            int size = MeasureSpec.getSize(widthMeasureSpec);
            if (size < AndroidUtilities.dp(240)) {
                size = AndroidUtilities.dp(240);
            }
            if (size > AndroidUtilities.dp(280)) {
                size = AndroidUtilities.dp(280);
            }
            if (size < 0) {
                size = 0;
            }
            if (maxWidth != 0 && maxWidth < size) {
                size = maxWidth;
            }
            if (listView != null) {
                for (int i = 0; i < listView.getChildCount(); i++) {
                    listView.getChildAt(i).measure(MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY), heightMeasureSpec);
                }
            }
            super.onMeasure(MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY), heightMeasureSpec);
        }
    }

    public ReactedUsersListView setOnCustomEmojiSelectedListener(OnCustomEmojiSelectedListener onCustomEmojiSelectedListener) {
        this.onCustomEmojiSelectedListener = onCustomEmojiSelectedListener;
        return this;
    }
}