package org.telegram.messenger;

import android.net.Uri;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.SparseBooleanArray;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.MessagePreviewView;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MessagePreviewParams {

    private static ArrayList<MessageObject> singletonArrayList(MessageObject obj) {
        ArrayList<MessageObject> list = new ArrayList<MessageObject>();
        list.add(obj);
        return list;
    }

    public class Messages {
        private Boolean out;
        private int type;
        private long dialogId;

        public LongSparseArray<MessageObject.GroupedMessages> groupedMessagesMap = new LongSparseArray<>();
        public ArrayList<MessageObject> messages;
        public ArrayList<MessageObject> previewMessages = new ArrayList<>();
        public SparseBooleanArray selectedIds = new SparseBooleanArray();
        public ArrayList<TLRPC.TL_pollAnswerVoters> pollChosenAnswers = new ArrayList<>();
        public boolean hasSpoilers;
        public boolean hasText;

        public Messages(Boolean out, int type, MessageObject message) {
            this(out, type, singletonArrayList(message), message.getDialogId(), null);
        }

        public Messages(Boolean out, int type, MessageObject message, long newDialogId) {
            this(out, type, singletonArrayList(message), newDialogId, null);
        }

        public Messages(Boolean out, int type, ArrayList<MessageObject> messages, long newDialogId, SparseBooleanArray pastSelectedIds) {
            this.out = out;
            this.type = type;
            this.dialogId = newDialogId;
            this.messages = messages;
            if (pastSelectedIds != null) {
                selectedIds = pastSelectedIds;
            }
            for (int i = 0; i < messages.size(); i++) {
                MessageObject messageObject = messages.get(i);
                if (type == 0 && pastSelectedIds == null) {
                    selectedIds.put(messageObject.getId(), true);
                }

                MessageObject previewMessage = toPreviewMessage(messageObject, out, type);
                if (!hasSpoilers) {
                    for (TLRPC.MessageEntity e : previewMessage.messageOwner.entities) {
                        if (e instanceof TLRPC.TL_messageEntitySpoiler) {
                            hasSpoilers = true;
                            break;
                        }
                    }
                }
                previewMessage.messageOwner.dialog_id = newDialogId;
                if (previewMessage.getGroupId() != 0) {
                    MessageObject.GroupedMessages groupedMessages = groupedMessagesMap.get(previewMessage.getGroupId(), null);
                    if (groupedMessages == null) {
                        groupedMessages = new MessageObject.GroupedMessages();
                        groupedMessagesMap.put(previewMessage.getGroupId(), groupedMessages);
                    }
                    groupedMessages.messages.add(previewMessage);
                }
                previewMessages.add(0, previewMessage);

                if (messageObject.isPoll()) {
                    TLRPC.TL_messageMediaPoll mediaPoll = (TLRPC.TL_messageMediaPoll) messageObject.messageOwner.media;
                    PreviewMediaPoll newMediaPoll = new PreviewMediaPoll();
                    newMediaPoll.poll = mediaPoll.poll;
                    newMediaPoll.provider = mediaPoll.provider;
                    newMediaPoll.results = new TLRPC.TL_pollResults();
                    newMediaPoll.totalVotersCached = newMediaPoll.results.total_voters = mediaPoll.results.total_voters;

                    previewMessage.messageOwner.media = newMediaPoll;

                    if (messageObject.canUnvote()) {
                        for (int a = 0, N = mediaPoll.results.results.size(); a < N; a++) {
                            TLRPC.TL_pollAnswerVoters answer = mediaPoll.results.results.get(a);
                            if (answer.chosen) {
                                TLRPC.TL_pollAnswerVoters newAnswer = new TLRPC.TL_pollAnswerVoters();
                                newAnswer.chosen = answer.chosen;
                                newAnswer.correct = answer.correct;
                                newAnswer.flags = answer.flags;
                                newAnswer.option = answer.option;
                                newAnswer.voters = answer.voters;
                                pollChosenAnswers.add(newAnswer);
                                newMediaPoll.results.results.add(newAnswer);
                            } else {
                                newMediaPoll.results.results.add(answer);
                            }
                        }
                    }
                }
            }
            for (int i = 0; i < groupedMessagesMap.size(); i++) {
                groupedMessagesMap.valueAt(i).calculate();
            }
            if (groupedMessagesMap != null && groupedMessagesMap.size() > 0) {
                MessageObject.GroupedMessages group = groupedMessagesMap.valueAt(0);
                hasText = group.findCaptionMessageObject() != null;
            } else if (messages.size() == 1) {
                MessageObject msg = messages.get(0);
                if (msg.type == MessageObject.TYPE_TEXT || msg.type == MessageObject.TYPE_EMOJIS) {
                    hasText = !TextUtils.isEmpty(msg.messageText);
                } else {
                    hasText = !TextUtils.isEmpty(msg.caption);
                }
            }
        }

        public void getSelectedMessages(ArrayList<MessageObject> messagesToForward) {
            messagesToForward.clear();
            for (int i = 0; i < messages.size(); i++) {
                MessageObject messageObject = messages.get(i);
                int id = messageObject.getId();
                if (selectedIds.get(id, false)) {
                    messagesToForward.add(messageObject);
                }
            }
        }

        public Messages checkEdits(ArrayList<MessageObject> replaceMessageObjects) {
            if (messages == null || messages.size() > 1 || replaceMessageObjects == null) {
                return null;
            }

            boolean replaced = false;
            for (int i = 0; i < messages.size(); ++i) {
                MessageObject msg = messages.get(i);
                if (msg == null) continue;
                for (int j = 0; j < replaceMessageObjects.size(); ++j) {
                    MessageObject msg2 = replaceMessageObjects.get(j);
                    if (msg2 == null) continue;
                    if (msg.getId() == msg2.getId() && msg.getDialogId() == msg2.getDialogId()) {
                        messages.set(i, msg2);
                        replaced = true;
                        break;
                    }
                }
            }
            if (replaced) {
                return new Messages(out, type, messages, dialogId, null);
            }
            return null;
        }
    }

    public Messages replyMessage;
    public Messages forwardMessages;
    public Messages linkMessage;

    public TLRPC.WebPage linkMedia;

    public ChatActivity.ReplyQuote quote;
    public int quoteStart, quoteEnd;
    public boolean hasCaption;
    public boolean hasSenders;
    public boolean isSecret;
    public boolean multipleUsers;

    public boolean hideForwardSendersName;
    public boolean hideCaption;
    public boolean willSeeSenders;

    public boolean singleLink;
    public boolean hasMedia;
    public boolean isVideo;
    public boolean webpageSmall;
    public boolean webpageTop;
    public boolean webpagePhoto;

    public boolean noforwards;
    public boolean hasSecretMessages;

    public TLRPC.WebPage webpage;
    public CharacterStyle currentLink;

    public MessagePreviewParams(boolean secret, boolean noforwards) {
        this.isSecret = secret;
        this.noforwards = secret || noforwards;
    }

    public void updateReply(MessageObject replyMessageObject, MessageObject.GroupedMessages group, long dialogId, ChatActivity.ReplyQuote replyQuote) {
        if (isSecret || replyMessageObject == null || replyMessageObject.type == MessageObject.TYPE_DATE || replyMessageObject.type == MessageObject.TYPE_ACTION_PHOTO
                || replyMessageObject.type == MessageObject.TYPE_ACTION_WALLPAPER || replyMessageObject.type == MessageObject.TYPE_SUGGEST_PHOTO
                || replyMessageObject.type == MessageObject.TYPE_GIFT_PREMIUM || replyMessageObject.type == MessageObject.TYPE_GIFT_PREMIUM_CHANNEL || replyMessageObject.type == MessageObject.TYPE_PHONE_CALL) {
            replyMessageObject = null;
            replyQuote = null;
        }
        hasSecretMessages = replyMessageObject != null && (replyMessageObject.isVoiceOnce() || replyMessageObject.isRoundOnce());
        if (replyMessageObject != null || replyQuote != null) {
            if (group != null) {
                replyMessage = new Messages(null, 1, group.messages, dialogId, null);
            } else {
                replyMessage = new Messages(null, 1, replyMessageObject != null ? replyMessageObject : replyQuote.message, dialogId);
            }
            if (!replyMessage.messages.isEmpty()) {
                this.quote = replyQuote;
                if (replyQuote != null) {
                    quoteStart = replyQuote.start;
                    quoteEnd = replyQuote.end;
                }
            } else {
                replyMessage = null;
            }
        } else {
            replyMessage = null;
            quote = null;
        }
    }

    public void updateLinkInvertMedia(boolean invertMedia) {
        webpageTop = invertMedia;
    }

    public void updateLink(int currentAccount, TLRPC.WebPage foundWebpage, CharSequence messageText, MessageObject replyMessageObject, ChatActivity.ReplyQuote replyQuote, MessageObject inherit) {
        hasMedia = false;
        isVideo = false;
        singleLink = true;
        boolean wasDifferent = webpage != foundWebpage;
        webpage = foundWebpage;
        if (TextUtils.isEmpty(messageText) && webpage == null) {
            this.linkMessage = null;
        } else {
            if (messageText == null) {
                messageText = "";
            }

            boolean wasEmpty = linkMessage == null || wasDifferent;
            if (linkMessage == null && inherit != null && inherit.messageOwner != null) {
                webpageTop = inherit.messageOwner.invert_media;
                if (inherit.messageOwner.media != null && inherit.messageOwner.media.force_small_media) {
                    webpageSmall = true;
                }
            }

            TLRPC.Message message = new TLRPC.TL_message();
            messageText = new SpannableStringBuilder(AndroidUtilities.getTrimmedString(messageText));
            CharSequence[] cs = new CharSequence[] { messageText };
            message.peer_id = new TLRPC.TL_peerUser();
            message.peer_id.user_id = UserConfig.getInstance(currentAccount).getClientUserId();
            message.from_id = new TLRPC.TL_peerUser();
            message.from_id.user_id = UserConfig.getInstance(currentAccount).getClientUserId();
            message.entities = MediaDataController.getInstance(currentAccount).getEntities(cs, true);
            message.message = cs[0].toString();
            message.invert_media = webpageTop;
            if (foundWebpage != null) {
                message.flags |= 512;
                message.media = new TLRPC.TL_messageMediaWebPage();
                message.media.webpage = foundWebpage;
                message.media.force_large_media = !webpageSmall;
                message.media.force_small_media = webpageSmall;
                hasMedia = message.media.webpage.photo != null;
                isVideo = MessageObject.isVideoDocument(message.media.webpage.document);
            } else {
                hasMedia = false;
            }
            message.out = true;
            message.unread = false;

            if (replyMessageObject != null) {
                message.replyMessage = replyMessageObject.messageOwner;
                message.reply_to = new TLRPC.TL_messageReplyHeader();
                if (replyQuote != null) {
                    message.reply_to.quote_text = replyQuote.getText();
                    message.reply_to.flags |= 64;

                    message.reply_to.quote_entities = replyQuote.getEntities();
                    if (message.reply_to.quote_entities != null) {
                        message.reply_to.flags |= 128;
                    }
                }
            }

            this.linkMessage = new Messages(true, 2, new MessageObject(currentAccount, message, true, false));
            if (this.linkMessage.messages.isEmpty()) {
                this.linkMessage = null;
            } else {
                final MessageObject msg = this.linkMessage.messages.get(0);
                if (msg.messageText instanceof Spanned && !TextUtils.isEmpty(msg.messageText)) {
                    URLSpan[] links = ((Spanned) msg.messageText).getSpans(0, msg.messageText.length(), URLSpan.class);
                    singleLink = links == null || links.length <= 1;
                } else if (msg.caption instanceof Spanned && !TextUtils.isEmpty(msg.caption)) {
                    URLSpan[] links = ((Spanned) msg.messageText).getSpans(0, msg.caption.length(), URLSpan.class);
                    singleLink = links == null || links.length <= 1;
                }
                hasMedia = msg.hasLinkMediaToMakeSmall();
                if (wasEmpty && inherit != null && inherit.messageOwner != null && inherit.messageOwner.media != null) {
                    webpageSmall = inherit.messageOwner.media.force_small_media || msg.isLinkMediaSmall() && !inherit.messageOwner.media.force_large_media;
                } else if (wasEmpty) {
                    webpageSmall = msg.isLinkMediaSmall();
                }
                if (msg != null && msg.messageOwner != null && msg.messageOwner.media != null) {
                    msg.messageOwner.media.force_large_media = !webpageSmall;
                    msg.messageOwner.media.force_small_media = webpageSmall;
                }
            }
        }

        if (previewView != null) {
            previewView.updateLink();
        }
    }

    public void checkCurrentLink(MessageObject msg) {
        currentLink = null;
        if (msg != null && msg.messageText instanceof Spanned && webpage != null && webpage.url != null) {
            Spanned spanned = (Spanned) msg.messageText;
            URLSpan[] urlSpans = spanned.getSpans(0, spanned.length(), URLSpan.class);

            for (int i = 0; i < urlSpans.length; ++i) {
                if (areUrlsEqual(urlSpans[i].getURL(), webpage.url)) {
                    currentLink = urlSpans[i];
                    break;
                }
            }
        }
    }

    public boolean hasLink(CharSequence text, String url) {
        if (url != null) {
            try {
                Spannable spanned = SpannableString.valueOf(text);
                try {
                    AndroidUtilities.addLinks(spanned, Linkify.WEB_URLS);
                } catch (Exception e2) {
                    FileLog.e(e2);
                }
                URLSpan[] urlSpans = spanned.getSpans(0, spanned.length(), URLSpan.class);

                for (int i = 0; i < urlSpans.length; ++i) {
                    if (areUrlsEqual(urlSpans[i].getURL(), url)) {
                        return true;
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return false;
    }

    public static boolean areUrlsEqual(String url1, String url2) {
        if (url1 == null || url2 == null) {
            return url1 == null;
        }
        Uri uri1 = Uri.parse(url1);
        Uri uri2 = Uri.parse(url2);
        return uri1 == uri2 || uri1 != null && uri2 != null &&
                (uri1.getHost() != null && uri1.getHost().equalsIgnoreCase(uri2.getHost())) &&
                uri1.getPort() == uri2.getPort() &&
                normalizePath(uri1.getPath()).equals(normalizePath(uri2.getPath())) &&
                (uri1.getQuery() == null ? uri2.getQuery() == null : uri1.getQuery().equals(uri2.getQuery()));
    }

    private static String normalizePath(String path) {
        if (path == null) return "";
        return (path.endsWith("/") ? path : path + "/");
    }

    public void updateForward(ArrayList<MessageObject> forwardMessages, long dialogId) {
        hasCaption = false;
        hasSenders = false;
        isSecret = DialogObject.isEncryptedDialog(dialogId);
        multipleUsers = false;

        if (forwardMessages != null) {
            ArrayList<String> hiddenSendersName = new ArrayList<>();
            for (int i = 0; i < forwardMessages.size(); ++i) {
                MessageObject messageObject = forwardMessages.get(i);
                if (!TextUtils.isEmpty(messageObject.caption)) {
                    hasCaption = true;
                }
                if (!isSecret) {
                    if (messageObject.messageOwner.fwd_from != null) {
                        TLRPC.MessageFwdHeader header = messageObject.messageOwner.fwd_from;
                        if (header.from_id == null && !hiddenSendersName.contains(header.from_name)) {
                            hiddenSendersName.add(header.from_name);
                        }
                    }
                }
            }
            this.forwardMessages = new Messages(true, 0, forwardMessages, dialogId, this.forwardMessages != null ? this.forwardMessages.selectedIds : null);
            if (this.forwardMessages.messages.isEmpty()) {
                this.forwardMessages = null;
            }

            ArrayList<Long> uids = new ArrayList<>();
            for (int a = 0; a < forwardMessages.size(); a++) {
                MessageObject object = forwardMessages.get(a);
                long uid;
                if (object.isFromUser()) {
                    uid = object.messageOwner.from_id.user_id;
                } else {
                    TLRPC.Chat chat = MessagesController.getInstance(object.currentAccount).getChat(object.messageOwner.peer_id.channel_id);
                    if (ChatObject.isChannel(chat) && chat.megagroup && object.isForwardedChannelPost()) {
                        uid = -object.messageOwner.fwd_from.from_id.channel_id;
                    } else {
                        uid = -object.messageOwner.peer_id.channel_id;
                    }
                }
                if (!uids.contains(uid)) {
                    uids.add(uid);
                }
            }
            if (uids.size() + hiddenSendersName.size() > 1) {
                multipleUsers = true;
            }
        } else {
            this.forwardMessages = null;
        }
    }

    private MessageObject toPreviewMessage(MessageObject messageObject, Boolean out, final int msgtype) {
        TLRPC.Message message = new TLRPC.TL_message();
        if (msgtype != 1) {
            message.date = ConnectionsManager.getInstance(messageObject.currentAccount).getCurrentTime();
        } else {
            message.date = messageObject.messageOwner.date;
        }
        message.id = messageObject.messageOwner.id;
        message.grouped_id = messageObject.messageOwner.grouped_id;
        message.peer_id = messageObject.messageOwner.peer_id;
        message.from_id = messageObject.messageOwner.from_id;
        message.message = messageObject.messageOwner.message;
        message.media = messageObject.messageOwner.media;
        message.action =  messageObject.messageOwner.action;
        message.edit_date = 0;
        if (messageObject.messageOwner.entities != null) {
            message.entities.addAll(messageObject.messageOwner.entities);
        }

        message.out = out == null ? messageObject.messageOwner.out : out;
        if (message.out) {
            message.from_id = new TLRPC.TL_peerUser();
            message.from_id.user_id = UserConfig.getInstance(messageObject.currentAccount).getClientUserId();
        }
        message.unread = false;
        message.via_bot_id  =  messageObject.messageOwner.via_bot_id;
        message.reply_markup  =  messageObject.messageOwner.reply_markup;
        message.post = messageObject.messageOwner.post;
        message.legacy = messageObject.messageOwner.legacy;
        message.restriction_reason = messageObject.messageOwner.restriction_reason;
        message.replyMessage = messageObject.messageOwner.replyMessage;
        if (message.replyMessage == null && messageObject.replyMessageObject != null) {
            message.replyMessage = messageObject.replyMessageObject.messageOwner;
        }
        message.reply_to = messageObject.messageOwner.reply_to;
        message.invert_media = messageObject.messageOwner.invert_media;

        if (msgtype == 0) {
            TLRPC.MessageFwdHeader header = null;
            long clientUserId = UserConfig.getInstance(messageObject.currentAccount).getClientUserId();
            if (!isSecret) {
                if (messageObject.messageOwner.fwd_from != null) {
                    header = messageObject.messageOwner.fwd_from;
                    if (!messageObject.isDice()) {
                        hasSenders = true;
                    } else {
                        willSeeSenders = true;
                    }
                } else if (messageObject.messageOwner.from_id.user_id == 0 || messageObject.messageOwner.dialog_id != clientUserId || messageObject.messageOwner.from_id.user_id != clientUserId) {
                    header = new TLRPC.TL_messageFwdHeader();
                    header.from_id = messageObject.messageOwner.from_id;
                    if (!messageObject.isDice()) {
                        hasSenders = true;
                    } else {
                        willSeeSenders = true;
                    }
                }
            }
            if (header != null) {
                message.fwd_from = header;
                if (message.fwd_from.date == 0) {
                    message.fwd_from.date = (int)(System.currentTimeMillis() / 1000);
                }
                message.flags |= TLRPC.MESSAGE_FLAG_FWD;
            }
        }

        MessageObject previewMessage = new MessageObject(messageObject.currentAccount, message, true, false) {
            @Override
            public void generateLayout(TLRPC.User fromUser) {
                super.generateLayout(fromUser);
                if (msgtype == 2) {
                    checkCurrentLink(this);
                }
            }

            @Override
            public boolean needDrawForwarded() {
                if (hideForwardSendersName) {
                    return false;
                }
                return super.needDrawForwarded();
            }
        };
        previewMessage.previewForward = msgtype == 0;
//        previewMessage.forceAvatar = msgtype == 1 && !message.out;
        previewMessage.preview = true;
        return previewMessage;
    }

    public static class PreviewMediaPoll extends TLRPC.TL_messageMediaPoll {
        public int totalVotersCached;
    }

    public boolean isEmpty() {
        return (
            (forwardMessages == null || forwardMessages.messages == null || forwardMessages.messages.isEmpty()) &&
            (replyMessage == null ||    replyMessage.messages == null ||    replyMessage.messages.isEmpty()) &&
            (linkMessage == null ||     linkMessage.messages == null ||     linkMessage.messages.isEmpty())
        );
    }

    private MessagePreviewView previewView;
    public void attach(MessagePreviewView previewView) {
        this.previewView = previewView;
    }

    public void checkEdits(ArrayList<MessageObject> replaceMessageObjects) {
        boolean replaced = false;
        if (forwardMessages != null) {
            Messages newForwardMessages = forwardMessages.checkEdits(replaceMessageObjects);
            if (newForwardMessages != null) {
                forwardMessages = newForwardMessages;
                replaced = true;
            }
        }
        if (replyMessage != null) {
            Messages newReplyMessages = replyMessage.checkEdits(replaceMessageObjects);
            if (newReplyMessages != null) {
                replyMessage = newReplyMessages;
                replaced = true;
            }
        }
        if (linkMessage != null) {
            Messages newLinkMessages = linkMessage.checkEdits(replaceMessageObjects);
            if (newLinkMessages != null) {
                linkMessage = newLinkMessages;
                replaced = true;
            }
        }
        if (replaced && previewView != null) {
            previewView.updateAll();
        }
    }
}
