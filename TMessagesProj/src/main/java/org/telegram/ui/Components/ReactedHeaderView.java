package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.List;

public class ReactedHeaderView extends FrameLayout {
    private FlickerLoadingView flickerLoadingView;
    private TextView titleView;
    private AvatarsImageView avatarsImageView;
    private ImageView iconView;
    private BackupImageView reactView;

    private int currentAccount;
    private boolean ignoreLayout;
    private List<UserSeen> seenUsers = new ArrayList<>();
    private List<UserSeen> users = new ArrayList<>();
    private long dialogId;
    private MessageObject message;
    private int fixedWidth;

    private boolean isLoaded;

    private Consumer<List<UserSeen>> seenCallback;

    public ReactedHeaderView(@NonNull Context context, int currentAccount, MessageObject message, long dialogId) {
        super(context);
        this.currentAccount = currentAccount;
        this.message = message;
        this.dialogId = dialogId;

        flickerLoadingView = new FlickerLoadingView(context);
        flickerLoadingView.setColors(Theme.key_actionBarDefaultSubmenuBackground, Theme.key_listSelector, -1);
        flickerLoadingView.setViewType(FlickerLoadingView.MESSAGE_SEEN_TYPE);
        flickerLoadingView.setIsSingleCell(false);
        addView(flickerLoadingView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

        titleView = new TextView(context);
        titleView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setLines(1);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        addView(titleView, LayoutHelper.createFrameRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 40, 0, 62, 0));

        avatarsImageView = new AvatarsImageView(context, false);
        avatarsImageView.setStyle(AvatarsDrawable.STYLE_MESSAGE_SEEN);
        avatarsImageView.setAvatarsTextSize(AndroidUtilities.dp(22));
        addView(avatarsImageView, LayoutHelper.createFrameRelatively(24 + 12 + 12 + 8, LayoutHelper.MATCH_PARENT, Gravity.END | Gravity.CENTER_VERTICAL, 0, 0, 0, 0));

        iconView = new ImageView(context);
        addView(iconView, LayoutHelper.createFrameRelatively(24, 24, Gravity.START | Gravity.CENTER_VERTICAL, 11, 0, 0, 0));
        Drawable drawable = ContextCompat.getDrawable(context, R.drawable.msg_reactions).mutate();
        drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon), PorterDuff.Mode.MULTIPLY));
        iconView.setImageDrawable(drawable);
        iconView.setVisibility(View.GONE);

        reactView = new BackupImageView(context);
        addView(reactView, LayoutHelper.createFrameRelatively(24, 24, Gravity.START | Gravity.CENTER_VERTICAL, 11, 0, 0, 0));

        titleView.setAlpha(0);
        avatarsImageView.setAlpha(0);

        setBackground(Theme.getSelectorDrawable(false));
    }

    public void setSeenCallback(Consumer<List<UserSeen>> seenCallback) {
        this.seenCallback = seenCallback;
    }

    public static class UserSeen {
        public TLObject user;
        long dialogId;
        public int date = 0;

        public UserSeen(TLRPC.User user) {
            this.user = user;
            dialogId = user.id;
        }
        public UserSeen(TLObject user, int date) {
            this.user = user;
            this.date = date;
            if (user instanceof TLRPC.User) {
                dialogId = ((TLRPC.User) user).id;
            } else if (user instanceof TLRPC.Chat) {
                dialogId = -((TLRPC.Chat) user).id;
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!isLoaded) {
            MessagesController ctrl = MessagesController.getInstance(currentAccount);
            TLRPC.Chat chat = ctrl.getChat(message.getChatId());
            TLRPC.ChatFull chatInfo = ctrl.getChatFull(message.getChatId());
            boolean showSeen = chat != null && message.isOutOwner() && message.isSent() && !message.isEditing() && !message.isSending() && !message.isSendError() && !message.isContentUnread() && !message.isUnread() && (ConnectionsManager.getInstance(currentAccount).getCurrentTime() - message.messageOwner.date < 7 * 86400)  && (ChatObject.isMegagroup(chat) || !ChatObject.isChannel(chat)) && chatInfo != null && chatInfo.participants_count <= MessagesController.getInstance(currentAccount).chatReadMarkSizeThreshold && !(message.messageOwner.action instanceof TLRPC.TL_messageActionChatJoinedByRequest);

            if (showSeen) {
                TLRPC.TL_messages_getMessageReadParticipants req = new TLRPC.TL_messages_getMessageReadParticipants();
                req.msg_id = message.getId();
                req.peer = MessagesController.getInstance(currentAccount).getInputPeer(message.getDialogId());
                long fromId = message.messageOwner.from_id != null ? message.messageOwner.from_id.user_id : 0;
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                    if (response instanceof TLRPC.Vector) {
                        List<Long> usersToRequest = new ArrayList<>();
                        List<Integer> dates = new ArrayList<>();
                        TLRPC.Vector v = (TLRPC.Vector) response;
                        for (Object obj : v.objects) {
                            if (obj instanceof Long) {
                                long l = (long) obj;
                                if (fromId != l) {
                                    usersToRequest.add(l);
                                    dates.add(0);
                                }
                            } else if (obj instanceof TLRPC.TL_readParticipantDate) {
                                long userId = ((TLRPC.TL_readParticipantDate) obj).user_id;
                                int date = ((TLRPC.TL_readParticipantDate) obj).date;
                                if (fromId != userId) {
                                    usersToRequest.add(userId);
                                    dates.add(date);
                                }
                            }
                        }
                        usersToRequest.add(fromId);
                        dates.add(0);

                        List<UserSeen> usersRes = new ArrayList<>();
                        Runnable callback = () -> {
                            seenUsers.addAll(usersRes);
                            for (UserSeen p : usersRes) {
                                boolean hasSame = false;
                                for (int i = 0; i < users.size(); i++) {
                                    if (MessageObject.getObjectPeerId(users.get(i).user) == MessageObject.getObjectPeerId(p.user)) {
                                        hasSame = true;
                                        if (p.date > 0) {
                                            users.get(i).date = p.date;
                                        }
                                        break;
                                    }
                                }
                                if (!hasSame) {
                                    users.add(p);
                                }
                            }
                            if (seenCallback != null)
                                seenCallback.accept(usersRes);
                            loadReactions();
                        };
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
                                        int index = usersToRequest.indexOf(user.id);
                                        if (!user.self && index >= 0) {
                                            usersRes.add(new UserSeen(user, dates.get(index)));
                                        }
                                    }
                                }
                                callback.run();
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
                                        int index = usersToRequest.indexOf(user.id);
                                        if (!user.self && index >= 0) {
                                            usersRes.add(new UserSeen(user, dates.get(index)));
                                        }
                                    }
                                }
                                callback.run();
                            }));
                        }
                    }
                }, ConnectionsManager.RequestFlagInvokeAfter);
            } else loadReactions();
        }
    }

    private void loadReactions() {
        MessagesController ctrl = MessagesController.getInstance(currentAccount);
        TLRPC.TL_messages_getMessageReactionsList getList = new TLRPC.TL_messages_getMessageReactionsList();
        getList.peer = ctrl.getInputPeer(message.getDialogId());
        getList.id = message.getId();
        getList.limit = 3;
        getList.reaction = null;
        getList.offset = null;
        ConnectionsManager.getInstance(currentAccount).sendRequest(getList, (response, error) -> {
            if (response instanceof TLRPC.TL_messages_messageReactionsList) {
                TLRPC.TL_messages_messageReactionsList list = (TLRPC.TL_messages_messageReactionsList) response;
                int c = list.count;
                int ic = list.users.size();
                post(() -> {
                    String str;
                    if (seenUsers.isEmpty() || seenUsers.size() < c) {
                        str = LocaleController.formatPluralString("ReactionsCount", c);
                    } else {
                        String countStr;
                        int n;
                        if (c == seenUsers.size()) {
                            countStr = String.valueOf(n = c);
                        } else {
                            countStr = (n = c) + "/" + seenUsers.size();
                        }
                        str = String.format(LocaleController.getPluralString("Reacted", n), countStr);
                    }

                    if (getMeasuredWidth() > 0) {
                        fixedWidth = getMeasuredWidth();
                    }
                    titleView.setText(str);
                    boolean showIcon = true;
                    if (message.messageOwner.reactions != null && message.messageOwner.reactions.results.size() == 1 && !list.reactions.isEmpty()) {
                        for (TLRPC.TL_availableReaction r : MediaDataController.getInstance(currentAccount).getReactionsList()) {
                            if (r.reaction.equals(list.reactions.get(0).reaction)) {
                                reactView.setImage(ImageLocation.getForDocument(r.center_icon), "40_40_lastreactframe", "webp", null, r);
                                reactView.setVisibility(VISIBLE);
                                reactView.setAlpha(0);
                                reactView.animate().alpha(1f).start();
                                iconView.setVisibility(GONE);
                                showIcon = false;
                                break;
                            }
                        }
                    }
                    if (showIcon) {
                        iconView.setVisibility(VISIBLE);
                        iconView.setAlpha(0f);
                        iconView.animate().alpha(1f).start();
                    }
                    for (TLRPC.User u : list.users) {
                        if (message.messageOwner.from_id != null && u.id != message.messageOwner.from_id.user_id) {
                            boolean hasSame = false;
                            for (int i = 0; i < users.size(); i++) {
                                if (users.get(i).dialogId == u.id) {
                                    hasSame = true;
                                    break;
                                }
                            }
                            if (!hasSame) {
                                users.add(new UserSeen(u, 0));
                            }
                        }
                    }
                    for (TLRPC.Chat u : list.chats) {
                        if (message.messageOwner.from_id != null && u.id != message.messageOwner.from_id.user_id) {
                            boolean hasSame = false;
                            for (int i = 0; i < users.size(); i++) {
                                if (users.get(i).dialogId == -u.id) {
                                    hasSame = true;
                                    break;
                                }
                            }
                            if (!hasSame) {
                                users.add(new UserSeen(u, 0));
                            }
                        }
                    }

                    updateView();
                });
            }
        }, ConnectionsManager.RequestFlagInvokeAfter);
    }

    public List<UserSeen> getSeenUsers() {
        return seenUsers;
    }

    private void updateView() {
        setEnabled(users.size() > 0);
        for (int i = 0; i < 3; i++) {
            if (i < users.size()) {
                avatarsImageView.setObject(i, currentAccount, users.get(i).user);
            } else {
                avatarsImageView.setObject(i, currentAccount, null);
            }
        }
        float tX;
        switch (users.size()) {
            case 1:
                tX = AndroidUtilities.dp(24);
                break;
            case 2:
                tX = AndroidUtilities.dp(12);
                break;
            default:
                tX = 0;
        }
        avatarsImageView.setTranslationX(LocaleController.isRTL ? AndroidUtilities.dp(12) : tX);

        avatarsImageView.commitTransition(false);
        titleView.animate().alpha(1f).setDuration(220).start();
        avatarsImageView.animate().alpha(1f).setDuration(220).start();
        flickerLoadingView.animate().alpha(0f).setDuration(220).setListener(new HideViewAfterAnimation(flickerLoadingView)).start();
    }

    @Override
    public void requestLayout() {
        if (ignoreLayout) {
            return;
        }
        super.requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (fixedWidth > 0) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(fixedWidth, MeasureSpec.EXACTLY);
        }
        if (flickerLoadingView.getVisibility() == View.VISIBLE) {
            // Idk what is happening here, but this class is a clone of MessageSeenView, so this might help with something?
            ignoreLayout = true;
            flickerLoadingView.setVisibility(View.GONE);
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            flickerLoadingView.getLayoutParams().width = getMeasuredWidth();
            flickerLoadingView.setVisibility(View.VISIBLE);
            ignoreLayout = false;
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }
}