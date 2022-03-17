package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.LongSparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReactedUsersListView extends FrameLayout {
    public final static int VISIBLE_ITEMS = 6;
    public final static int ITEM_HEIGHT_DP = 48;

    private int currentAccount;
    private MessageObject message;
    private String filter;

    private RecyclerListView listView;
    private RecyclerView.Adapter adapter;

    private FlickerLoadingView loadingView;

    private List<TLRPC.TL_messagePeerReaction> userReactions = new ArrayList<>();
    private LongSparseArray<TLRPC.User> users = new LongSparseArray<>();
    private String offset;
    private boolean isLoading, isLoaded, canLoadMore = true;
    private boolean onlySeenNow;

    private OnHeightChangedListener onHeightChangedListener;
    private OnProfileSelectedListener onProfileSelectedListener;

    public ReactedUsersListView(Context context, Theme.ResourcesProvider resourcesProvider, int currentAccount, MessageObject message, TLRPC.TL_reactionCount reactionCount, boolean addPadding) {
        super(context);
        this.currentAccount = currentAccount;
        this.message = message;
        this.filter = reactionCount == null ? null : reactionCount.reaction;

        listView = new RecyclerListView(context, resourcesProvider) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                super.onMeasure(widthSpec, heightSpec);
                updateHeight();
            }
        };
        LinearLayoutManager llm = new LinearLayoutManager(context);
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
                return new RecyclerListView.Holder(new ReactedUserHolderView(context));
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ReactedUserHolderView rhv = (ReactedUserHolderView) holder.itemView;
                rhv.setUserReaction(userReactions.get(position));
            }

            @Override
            public int getItemCount() {
                return userReactions.size();
            }
        });
        listView.setOnItemClickListener((view, position) -> {
            if (onProfileSelectedListener != null)
                onProfileSelectedListener.onProfileSelected(this, MessageObject.getPeerId(userReactions.get(position).peer_id));
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

        loadingView = new FlickerLoadingView(context, resourcesProvider);
        loadingView.setViewType(FlickerLoadingView.REACTED_TYPE);
        loadingView.setIsSingleCell(true);
        loadingView.setItemsCount(reactionCount == null ? VISIBLE_ITEMS : reactionCount.count);
        addView(loadingView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    @SuppressLint("NotifyDataSetChanged")
    public ReactedUsersListView setSeenUsers(List<TLRPC.User> users) {
        List<TLRPC.TL_messagePeerReaction> nr = new ArrayList<>(users.size());
        for (TLRPC.User u : users) {
            if (this.users.get(u.id) != null) continue;
            this.users.put(u.id, u);
            TLRPC.TL_messagePeerReaction r = new TLRPC.TL_messagePeerReaction();
            r.reaction = null;
            r.peer_id = new TLRPC.TL_peerUser();
            r.peer_id.user_id = u.id;
            nr.add(r);
        }
        if (userReactions.isEmpty())
            onlySeenNow = true;
        userReactions.addAll(nr);
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
            if (response instanceof TLRPC.TL_messages_messageReactionsList) {
                TLRPC.TL_messages_messageReactionsList l = (TLRPC.TL_messages_messageReactionsList) response;

                for (TLRPC.User u : l.users) {
                    MessagesController.getInstance(currentAccount).putUser(u, false);
                    users.put(u.id, u);
                }

                // It's safer to create a new list to prevent inconsistency
                int prev = userReactions.size();
                List<TLRPC.TL_messagePeerReaction> newReactions = new ArrayList<>(userReactions.size() + l.reactions.size());
                newReactions.addAll(userReactions);
                newReactions.addAll(l.reactions);

                if (onlySeenNow) {
                    Collections.sort(newReactions, (o1, o2) -> Integer.compare(o1.reaction != null ? 1 : 0, o2.reaction != null ? 1 : 0));
                }

                AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).doOnIdle(() -> {
                    userReactions = newReactions;
                    if (onlySeenNow) {
                        onlySeenNow = false;
                        adapter.notifyDataSetChanged();
                    } else adapter.notifyItemRangeInserted(prev, l.reactions.size());

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
                    offset = l.next_offset;
                    if (offset == null)
                        canLoadMore = false;
                    isLoading = false;
                }));
            } else isLoading = false;
        }, ConnectionsManager.RequestFlagInvokeAfter);
    }

    private void updateHeight() {
        if (onHeightChangedListener != null) {
            int h;
            if (listView.getMeasuredHeight() != 0) {
                h = Math.min(listView.getMeasuredHeight(), AndroidUtilities.dp(ITEM_HEIGHT_DP * Math.min(userReactions.size(), VISIBLE_ITEMS)));
            } else {
                h = AndroidUtilities.dp(ITEM_HEIGHT_DP * Math.min(userReactions.size(), VISIBLE_ITEMS));
            }
            onHeightChangedListener.onHeightChanged(ReactedUsersListView.this, h);
        }
    }

    private int getLoadCount() {
        return filter == null ? 100 : 50;
    }

    private final class ReactedUserHolderView extends FrameLayout {
        BackupImageView avatarView;
        TextView titleView;
        BackupImageView reactView;
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        View overlaySelectorView;

        ReactedUserHolderView(@NonNull Context context) {
            super(context);
            setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(48)));

            avatarView = new BackupImageView(context);
            avatarView.setRoundRadius(AndroidUtilities.dp(32));
            addView(avatarView, LayoutHelper.createFrameRelatively(36, 36, Gravity.START | Gravity.CENTER_VERTICAL, 8, 0, 0, 0));

            titleView = new TextView(context);
            titleView.setLines(1);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            addView(titleView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 58, 0, 44, 0));

            reactView = new BackupImageView(context);
            addView(reactView, LayoutHelper.createFrameRelatively(24, 24, Gravity.END | Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

            overlaySelectorView = new View(context);
            overlaySelectorView.setBackground(Theme.getSelectorDrawable(false));
            addView(overlaySelectorView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }

        void setUserReaction(TLRPC.TL_messagePeerReaction reaction) {
            TLRPC.User u = users.get(MessageObject.getPeerId(reaction.peer_id));
            avatarDrawable.setInfo(u);
            titleView.setText(UserObject.getUserName(u));
            avatarView.setImage(ImageLocation.getForUser(u, ImageLocation.TYPE_SMALL), "50_50", avatarDrawable, u);

            if (reaction.reaction != null) {
                TLRPC.TL_availableReaction r = MediaDataController.getInstance(currentAccount).getReactionsMap().get(reaction.reaction);
                if (r != null) {
                    SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(r.static_icon.thumbs, Theme.key_windowBackgroundGray, 1.0f);
                    reactView.setImage(ImageLocation.getForDocument(r.static_icon), "50_50", "webp", svgThumb, r);
                } else {
                    reactView.setImageDrawable(null);
                }
            } else {
                reactView.setImageDrawable(null);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(ITEM_HEIGHT_DP), MeasureSpec.EXACTLY));
        }
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
        void onProfileSelected(ReactedUsersListView view, long userId);
    }
}