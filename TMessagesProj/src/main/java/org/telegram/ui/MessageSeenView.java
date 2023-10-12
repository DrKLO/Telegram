package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.AvatarsDrawable;
import org.telegram.ui.Components.AvatarsImageView;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.HideViewAfterAnimation;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MessageSeenCheckDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.StatusBadgeComponent;

import java.util.ArrayList;
import java.util.HashMap;

public class MessageSeenView extends FrameLayout {

    ArrayList<Long> peerIds = new ArrayList<>();
    ArrayList<Integer> dates = new ArrayList<>();
    public ArrayList<TLObject> users = new ArrayList<>();
    AvatarsImageView avatarsImageView;
    SimpleTextView titleView;
    ImageView iconView;
    int currentAccount;
    boolean isVoice;

    FlickerLoadingView flickerLoadingView;

    public MessageSeenView(@NonNull Context context, int currentAccount, MessageObject messageObject, TLRPC.Chat chat) {
        super(context);
        this.currentAccount = currentAccount;
        isVoice = (messageObject.isRoundVideo() || messageObject.isVoice());
        flickerLoadingView = new FlickerLoadingView(context);
        flickerLoadingView.setColors(Theme.key_actionBarDefaultSubmenuBackground, Theme.key_listSelector, -1);
        flickerLoadingView.setViewType(FlickerLoadingView.MESSAGE_SEEN_TYPE);
        flickerLoadingView.setIsSingleCell(false);
        addView(flickerLoadingView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

        titleView = new SimpleTextView(context);
        titleView.setTextSize(16);
        titleView.setEllipsizeByGradient(true);
        titleView.setRightPadding(AndroidUtilities.dp(62));

        addView(titleView, LayoutHelper.createFrame(0, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 40, 0, 0, 0));

        avatarsImageView = new AvatarsImageView(context, false);
        avatarsImageView.setStyle(AvatarsDrawable.STYLE_MESSAGE_SEEN);
        avatarsImageView.setAvatarsTextSize(AndroidUtilities.dp(22));
        addView(avatarsImageView, LayoutHelper.createFrame(24 + 12 + 12 + 8, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 0, 0));

        titleView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));

        TLRPC.TL_messages_getMessageReadParticipants req = new TLRPC.TL_messages_getMessageReadParticipants();
        req.msg_id = messageObject.getId();
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(messageObject.getDialogId());

        iconView = new ImageView(context);
        addView(iconView, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.CENTER_VERTICAL, 11, 0, 0, 0));
        Drawable drawable = ContextCompat.getDrawable(context, isVoice ? R.drawable.msg_played : R.drawable.msg_seen).mutate();
        drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon), PorterDuff.Mode.MULTIPLY));
        iconView.setImageDrawable(drawable);

        avatarsImageView.setAlpha(0);
        titleView.setAlpha(0);
        long fromId = 0;
        if (messageObject.messageOwner.from_id != null) {
            fromId = messageObject.messageOwner.from_id.user_id;
        }
        long finalFromId = fromId;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                TLRPC.Vector vector = (TLRPC.Vector) response;
                ArrayList<Long> unknownUsers = new ArrayList<>();
                ArrayList<Long> unknownChats = new ArrayList<>();
                HashMap<Long, TLObject> usersLocal = new HashMap<>();
                ArrayList<Pair<Long, Integer>> allPeers = new ArrayList<>();
                for (int i = 0, n = vector.objects.size(); i < n; i++) {
                    Object object = vector.objects.get(i);
                    if (object instanceof TLRPC.TL_readParticipantDate) {
                        int date = ((TLRPC.TL_readParticipantDate) object).date;
                        Long peerId = ((TLRPC.TL_readParticipantDate) object).user_id;
                        if (finalFromId == peerId) {
                            continue;
                        }
                        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(peerId);
                        allPeers.add(new Pair<>(peerId, date));
                        if (true || user == null) {
                            unknownUsers.add(peerId);
                        } else {
                            usersLocal.put(peerId, user);
                        }
                    } else if (object instanceof Long) {
                        Long peerId = (Long) object;
                        if (finalFromId == peerId) {
                            continue;
                        }
                        if (peerId > 0) {
                            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(peerId);
                            allPeers.add(new Pair<>(peerId, 0));
                            if (true || user == null) {
                                unknownUsers.add(peerId);
                            } else {
                                usersLocal.put(peerId, user);
                            }
                        } else {
                            TLRPC.Chat chat1 = MessagesController.getInstance(currentAccount).getChat(-peerId);
                            allPeers.add(new Pair<>(peerId, 0));
                            if (true || chat1 == null) {
                                unknownChats.add(peerId);
                            } else {
                                usersLocal.put(peerId, chat1);
                            }
                        }
                    }
                }

                if (unknownUsers.isEmpty()) {
                    for (int i = 0; i < allPeers.size(); i++) {
                        Pair<Long, Integer> pair = allPeers.get(i);
                        peerIds.add(pair.first);
                        dates.add(pair.second);
                        users.add(usersLocal.get(pair.first));
                    }
                    updateView();
                } else {
                    if (ChatObject.isChannel(chat)) {
                        TLRPC.TL_channels_getParticipants usersReq = new TLRPC.TL_channels_getParticipants();
                        usersReq.limit = MessagesController.getInstance(currentAccount).chatReadMarkSizeThreshold;
                        usersReq.offset = 0;
                        usersReq.filter = new TLRPC.TL_channelParticipantsRecent();
                        usersReq.channel = MessagesController.getInstance(currentAccount).getInputChannel(chat.id);
                        ConnectionsManager.getInstance(currentAccount).sendRequest(usersReq, (response1, error1) -> AndroidUtilities.runOnUIThread(() -> {
                            if (response1 != null) {
                                TLRPC.TL_channels_channelParticipants users = (TLRPC.TL_channels_channelParticipants) response1;
                                for (int i = 0; i < users.users.size(); i++) {
                                    TLRPC.User user = users.users.get(i);
                                    MessagesController.getInstance(currentAccount).putUser(user, false);
                                    usersLocal.put(user.id, user);
                                }
                                for (int i = 0; i < allPeers.size(); i++) {
                                    Pair<Long, Integer> pair = allPeers.get(i);
                                    peerIds.add(pair.first);
                                    dates.add(pair.second);
                                    this.users.add(usersLocal.get(pair.first));
                                }
                            }
                            updateView();
                        }));
                    } else {
                        TLRPC.TL_messages_getFullChat usersReq = new TLRPC.TL_messages_getFullChat();
                        usersReq.chat_id = chat.id;
                        ConnectionsManager.getInstance(currentAccount).sendRequest(usersReq, (response1, error1) -> AndroidUtilities.runOnUIThread(() -> {
                            if (response1 != null) {
                                TLRPC.TL_messages_chatFull chatFull = (TLRPC.TL_messages_chatFull) response1;
                                for (int i = 0; i < chatFull.users.size(); i++) {
                                    TLRPC.User user = chatFull.users.get(i);
                                    MessagesController.getInstance(currentAccount).putUser(user, false);
                                    usersLocal.put(user.id, user);
                                }
                                for (int i = 0; i < allPeers.size(); i++) {
                                    Pair<Long, Integer> pair = allPeers.get(i);
                                    peerIds.add(pair.first);
                                    dates.add(pair.second);
                                    this.users.add(usersLocal.get(pair.first));
                                }
                            }
                            updateView();
                        }));
                    }
                }
            } else {
                updateView();
            }
        }));
        setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 6, 0));
        setEnabled(false);
    }

    boolean ignoreLayout;

    @Override
    public void requestLayout() {
        if (ignoreLayout) {
            return;
        }
        super.requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        View parent = (View) getParent();
        if (parent != null && parent.getWidth() > 0) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(parent.getWidth(), MeasureSpec.EXACTLY);
        }
        ignoreLayout = true;
        boolean measureFlicker = flickerLoadingView.getVisibility() == View.VISIBLE;
        titleView.setVisibility(View.GONE);
        if (measureFlicker) {
            flickerLoadingView.setVisibility(View.GONE);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (measureFlicker) {
            flickerLoadingView.getLayoutParams().width = getMeasuredWidth();
            flickerLoadingView.setVisibility(View.VISIBLE);
        }
        titleView.setVisibility(View.VISIBLE);
        titleView.getLayoutParams().width = getMeasuredWidth() - AndroidUtilities.dp(40);
        ignoreLayout = false;

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void updateView() {
        setEnabled(users.size() > 0);
        for (int i = 0; i < 3; i++) {
            if (i < users.size()) {
                avatarsImageView.setObject(i, currentAccount, users.get(i));
            } else {
                avatarsImageView.setObject(i, currentAccount, null);
            }
        }
        if (users.size() == 1) {
            avatarsImageView.setTranslationX(AndroidUtilities.dp(24));
        } else if (users.size() == 2) {
            avatarsImageView.setTranslationX(AndroidUtilities.dp(12));
        } else {
            avatarsImageView.setTranslationX(0);
        }

        titleView.setRightPadding(AndroidUtilities.dp(8 + 24 + Math.min(2, users.size() - 1) * 12 + 6));

        avatarsImageView.commitTransition(false);
        if (peerIds.size() == 1 && users.get(0) != null) {
            titleView.setText(ContactsController.formatName(users.get(0)));
        } else {
            if (peerIds.size() == 0) {
                titleView.setText(LocaleController.getString("NobodyViewed", R.string.NobodyViewed));
            } else {
                titleView.setText(LocaleController.formatPluralString(isVoice ? "MessagePlayed" : "MessageSeen", peerIds.size()));
            }
        }
        titleView.animate().alpha(1f).setDuration(220).start();
        avatarsImageView.animate().alpha(1f).setDuration(220).start();
        flickerLoadingView.animate().alpha(0f).setDuration(220).setListener(new HideViewAfterAnimation(flickerLoadingView)).start();

        if (listView != null && listView.getAdapter() != null) {
//            listView.getAdapter().notifyDataSetChanged();
        }
    }

    private RecyclerListView listView;

    public RecyclerListView createListView() {
        if (listView != null) {
            return listView;
        }
        listView = new RecyclerListView(getContext()) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                int height = MeasureSpec.getSize(heightSpec);
                int listViewTotalHeight = AndroidUtilities.dp(4) + AndroidUtilities.dp(50) * getAdapter().getItemCount();

                if (listViewTotalHeight > height) {
                    listViewTotalHeight = height;
                }

                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(listViewTotalHeight, MeasureSpec.EXACTLY));
            }
        };
        listView.setLayoutManager(new LinearLayoutManager(getContext()));
        listView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                int p = parent.getChildAdapterPosition(view);
                if (p == users.size() - 1) {
                    outRect.bottom = AndroidUtilities.dp(4);
                }
            }
        });
        listView.setAdapter(new RecyclerListView.SelectionAdapter() {

            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return true;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                UserCell userCell = new UserCell(parent.getContext());
                userCell.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(50)));
                return new RecyclerListView.Holder(userCell);
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                UserCell cell = (UserCell) holder.itemView;
                cell.setUser(users.get(position), dates.get(position));
            }

            @Override
            public int getItemCount() {
                return users.size();
            }

        });
        return listView;
    }

    private static class UserCell extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

        private int currentAccount = UserConfig.selectedAccount;

        BackupImageView avatarImageView;
        SimpleTextView nameView;
        TextView readView;
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        StatusBadgeComponent statusBadgeComponent;

        TLObject object;

        private static MessageSeenCheckDrawable seenDrawable = new MessageSeenCheckDrawable(R.drawable.msg_mini_checks, Theme.key_windowBackgroundWhiteGrayText);

        public UserCell(Context context) {
            super(context);
            avatarImageView = new BackupImageView(context);
            avatarImageView.setRoundRadius(AndroidUtilities.dp(18));

            nameView = new SimpleTextView(context);
            nameView.setTextSize(16);
            nameView.setEllipsizeByGradient(!LocaleController.isRTL);
            nameView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            nameView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
            nameView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);

            statusBadgeComponent = new StatusBadgeComponent(this);
            nameView.setDrawablePadding(AndroidUtilities.dp(3));

            readView = new TextView(context);
            readView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            readView.setLines(1);
            readView.setEllipsize(TextUtils.TruncateAt.END);
            readView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            readView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            readView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);

            if (LocaleController.isRTL) {
                addView(avatarImageView, LayoutHelper.createFrame(34, 34, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 10, 0));
                addView(nameView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.TOP, 8, 6.33f, 55, 0));
                addView(readView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.TOP, 13, 20, 55, 0));
            } else {
                addView(avatarImageView, LayoutHelper.createFrame(34, 34, Gravity.LEFT | Gravity.CENTER_VERTICAL, 10f, 0, 0, 0));
                addView(nameView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 55, 6.33f, 8, 0));
                addView(readView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 55, 20, 13, 0));
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50), View.MeasureSpec.EXACTLY));
        }

        public void setUser(TLObject object, int date) {
            this.object = object;
            updateStatus(false);

            if (object != null) {
                avatarDrawable.setInfo(object);
                ImageLocation imageLocation = ImageLocation.getForUserOrChat(object, ImageLocation.TYPE_SMALL);
                avatarImageView.setImage(imageLocation, "50_50", avatarDrawable, object);
                nameView.setText(ContactsController.formatName(object));
            }

            if (date <= 0) {
                readView.setVisibility(GONE);
                nameView.setTranslationY(AndroidUtilities.dp(9));
            } else {
                readView.setText(TextUtils.concat(seenDrawable.getSpanned(getContext(), null), LocaleController.formatSeenDate(date)));
                readView.setVisibility(VISIBLE);
                nameView.setTranslationY(0);
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            String text = LocaleController.formatString("AccDescrPersonHasSeen", R.string.AccDescrPersonHasSeen, nameView.getText());
            if (readView.getVisibility() == VISIBLE) {
                text += " " + readView.getText();
            }
            info.setText(text);
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.userEmojiStatusUpdated) {
                TLRPC.User user = (TLRPC.User) args[0];
                TLRPC.User currentUser = object instanceof TLRPC.User ? (TLRPC.User) object : null;
                if (currentUser != null && user != null && currentUser.id == user.id) {
                    this.object = user;
                    updateStatus(true);
                }
            }
        }

        private void updateStatus(boolean animated) {
            nameView.setRightDrawable(statusBadgeComponent.updateDrawable(object, Theme.getColor(Theme.key_chats_verifiedBackground), animated));
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            statusBadgeComponent.onAttachedToWindow();
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.userEmojiStatusUpdated);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            statusBadgeComponent.onDetachedFromWindow();
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.userEmojiStatusUpdated);
        }
    }
}
