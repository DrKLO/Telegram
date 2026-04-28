package org.telegram.ui.Components.poll;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.formatWholeNumber;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.math.MathUtils;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.CallLogActivity;
import org.telegram.ui.Components.AvatarsListDrawable;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.MessageSeenView;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.List;

@SuppressLint("ViewConstructor")
public class RecentVotersCell extends FrameLayout {
    public final AvatarsListDrawable avatarsListDrawable;
    public final TextView textView;

    public RecentVotersCell(@NonNull Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        avatarsListDrawable = new AvatarsListDrawable(currentAccount, this, dp(24), dp(10), dpf2(1));

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider));
        textView.setLines(1);
        textView.setSingleLine(true);
        textView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);

        setPadding(dp(16), 0, dp(68), 0);
        addView(textView, LayoutHelper.createFrameMatchParent());
    }

    public void setText(String text) {
        textView.setText(text);
    }

    public void setRecentVoters(List<TLRPC.Peer> peers, boolean animated) {
        avatarsListDrawable.set(peers, animated);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        avatarsListDrawable.attach();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        avatarsListDrawable.detach();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        avatarsListDrawable.setBounds(
            getWidth() - dp(11) - (int) avatarsListDrawable.getAnimatedWidth(),
            dp(12), getWidth() - dp(11), dp(12) + dp(24));
        avatarsListDrawable.draw(canvas);
    }



    private UniversalRecyclerView listView;

    public RecyclerListView createListView(BaseFragment fragment, long dialogId, int msgId, byte[] option, int estimated, Utilities.Callback<Long> onClick) {
        if (listView != null) {
            return listView;
        }

        final VotesList list = new VotesList(fragment.getCurrentAccount(), fragment.getMessagesController().getInputPeer(dialogId), msgId, option, () -> listView.adapter.update(true), onClick);
        AndroidUtilities.runOnUIThread(list::load, 1000);

        listView = new UniversalRecyclerView(fragment, list::fillItems, null, null) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                int width = Math.min(dp(220), MeasureSpec.getSize(widthSpec));
                final int height = MeasureSpec.getSize(heightSpec);
                int listViewTotalHeight = AndroidUtilities.dp(48 * MathUtils.clamp(estimated, 1, 5));
                if (listViewTotalHeight > height) {
                //    listViewTotalHeight = height;
                }

                super.onMeasure(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(listViewTotalHeight, MeasureSpec.EXACTLY));
            }
        };
        listView.adapter.setApplyBackground(false);
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (!list.completed && !list.loading) {
                    final int total = listView.adapter.getItemCount();
                    final int visiblePosition = listView.layoutManager.findLastCompletelyVisibleItemPosition();
                    final int remaining = total - 1 - visiblePosition;
                    if (remaining < 5) {
                        list.load();
                    }
                }
            }
        });

        return listView;
    }

    private static class VotesList {
        public final int currentAccount;
        public final TLRPC.InputPeer peer;
        public final int msgId;
        public final byte[] option;

        private final Runnable onUpdate;
        private final Utilities.Callback<Long> onClick;
        private int count = -1;
        private String nextOffset;
        private boolean completed;
        private boolean loading;

        private ArrayList<TLRPC.MessagePeerVote> votes = new ArrayList<>();

        private VotesList(int currentAccount, TLRPC.InputPeer peer, int msgId, byte[] option, Runnable onUpdate, Utilities.Callback<Long> onClick) {
            this.currentAccount = currentAccount;
            this.peer = peer;
            this.msgId = msgId;
            this.option = option;
            this.onUpdate = onUpdate;
            this.onClick = onClick;
        }

        public void load() {
            if (completed || loading) {
                return;
            }

            loading = true;

            TLRPC.TL_messages_getPollVotes req = new TLRPC.TL_messages_getPollVotes();
            req.limit = nextOffset != null ? 10 : 15;
            req.peer = peer;
            req.id = msgId;
            req.option = option;
            req.offset = nextOffset;

            ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (res, err) -> {
                loading = false;

                if (res != null) {
                    MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                    MessagesController.getInstance(currentAccount).putChats(res.chats, false);

                    nextOffset = res.next_offset;
                    completed = res.next_offset == null;
                    count = res.count;
                    votes.addAll(res.votes);

                    if (onUpdate != null) {
                        onUpdate.run();
                    }
                } else {
                    nextOffset = null;
                    completed = true;
                }
            });
        }

        public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            items.clear();

            for (TLRPC.MessagePeerVote vote : votes) {
                final long dialogId = DialogObject.getPeerDialogId(vote.peer);
                items.add(Factory.of(
                    MessagesController.getInstance(currentAccount).getUserOrChat(dialogId),
                    dialogId, vote.date, v -> { if (onClick != null) { onClick.run(dialogId); }}
                ));
            }

            if (!completed) {
                if (votes.isEmpty()) {
                    items.add(FlickerFactory2.of());
                    items.add(FlickerFactory2.of());
                    items.add(FlickerFactory2.of());
                    items.add(FlickerFactory2.of());
                    items.add(FlickerFactory2.of());
                } else {
                    items.add(FlickerFactory.of());
                }
            }
        }
    }

    public static class FlickerFactory extends UItem.UItemFactory<FlickerLoadingView> {
        static { setup(new FlickerFactory()); }

        public FlickerLoadingView createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            FlickerLoadingView v = new FlickerLoadingView(context);
            v.setViewType(FlickerLoadingView.REACTED_TYPE);
            v.setMinimumHeight(dp(48));
            return v;
        }

        public static UItem of() {
            return UItem.ofFactory(FlickerFactory.class);
        }
    }

    public static class FlickerFactory2 extends UItem.UItemFactory<FlickerLoadingView> {
        static { setup(new FlickerFactory2()); }

        public FlickerLoadingView createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            FlickerLoadingView v = new FlickerLoadingView(context);
            v.setViewType(FlickerLoadingView.REACTED_TYPE);
            v.setMinimumHeight(dp(48));
            return v;
        }

        public static UItem of() {
            return UItem.ofFactory(FlickerFactory2.class);
        }
    }

    public static class Factory extends UItem.UItemFactory<MessageSeenView.UserCell> {
        static { setup(new Factory()); }

        @Override
        public MessageSeenView.UserCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            MessageSeenView.UserCell cell = new MessageSeenView.UserCell(context);
            cell.setBackground(Theme.getSelectorDrawable(false));

            return cell;
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            final TLObject row = (TLObject) item.object;
            final MessageSeenView.UserCell cell = (MessageSeenView.UserCell) view;
            cell.setUser(row, item.intValue, true);
            cell.setOnClickListener(item.clickCallback);
        }

        public static UItem of(TLObject user, long dialogId, int date, View.OnClickListener onImageClick) {
            final UItem item = UItem.ofFactory(Factory.class);
            item.object = user;
            item.longValue = dialogId;
            item.intValue = date;
            item.clickCallback = onImageClick;
            return item;
        }

        @Override
        public boolean equals(UItem a, UItem b) {
            return a.longValue == b.longValue;
        }

        @Override
        public boolean contentsEquals(UItem a, UItem b) {
            return a.longValue == b.longValue;
        }
    }

}
