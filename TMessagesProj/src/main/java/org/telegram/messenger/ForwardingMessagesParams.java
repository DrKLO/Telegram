package org.telegram.messenger;

import android.text.TextUtils;
import android.util.LongSparseArray;
import android.util.SparseBooleanArray;

import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public class ForwardingMessagesParams {

    public LongSparseArray<MessageObject.GroupedMessages> groupedMessagesMap = new LongSparseArray<>();
    public ArrayList<MessageObject> messages;
    public ArrayList<MessageObject> previewMessages = new ArrayList<>();
    public SparseBooleanArray selectedIds = new SparseBooleanArray();
    public boolean hideForwardSendersName;
    public boolean hideCaption;
    public boolean hasCaption;
    public boolean hasSenders;
    public boolean isSecret;
    public boolean willSeeSenders;
    public boolean multiplyUsers;

    public ArrayList<TLRPC.TL_pollAnswerVoters> pollChoosenAnswers = new ArrayList<>();

    public ForwardingMessagesParams(ArrayList<MessageObject> messages, long newDialogId) {
        this.messages = messages;
        hasCaption = false;
        hasSenders = false;
        isSecret = DialogObject.isEncryptedDialog(newDialogId);
        ArrayList<String> hiddenSendersName = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            MessageObject messageObject = messages.get(i);
            if (!TextUtils.isEmpty(messageObject.caption)) {
                hasCaption = true;
            }
            selectedIds.put(messageObject.getId(), true);

            TLRPC.Message message = new TLRPC.TL_message();
            message.id = messageObject.messageOwner.id;
            message.grouped_id = messageObject.messageOwner.grouped_id;
            message.peer_id = messageObject.messageOwner.peer_id;
            message.from_id = messageObject.messageOwner.from_id;
            message.message = messageObject.messageOwner.message;
            message.media = messageObject.messageOwner.media;
            message.action =  messageObject.messageOwner.action;
            message.edit_date = 0;

            message.out = true;
            message.unread = false;
            message.via_bot_id  =  messageObject.messageOwner.via_bot_id;
            message.reply_markup  =  messageObject.messageOwner.reply_markup;
            message.post = messageObject.messageOwner.post;
            message.legacy = messageObject.messageOwner.legacy;
            message.restriction_reason = messageObject.messageOwner.restriction_reason;

            TLRPC.MessageFwdHeader header = null;

            long clientUserId = UserConfig.getInstance(messageObject.currentAccount).clientUserId;
            if (!isSecret) {
                if (messageObject.messageOwner.fwd_from != null) {
                    header = messageObject.messageOwner.fwd_from;
                    if (!messageObject.isDice()) {
                        hasSenders = true;
                    } else {
                        willSeeSenders = true;
                    }
                    if (header.from_id == null && !hiddenSendersName.contains(header.from_name)) {
                        hiddenSendersName.add(header.from_name);
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
                message.flags |= TLRPC.MESSAGE_FLAG_FWD;
            }
            message.dialog_id = newDialogId;

            MessageObject previewMessage = new MessageObject(messageObject.currentAccount, message, true, false) {
                @Override
                public boolean needDrawForwarded() {
                    if (hideForwardSendersName) {
                        return false;
                    }
                    return super.needDrawForwarded();
                }
            };
            previewMessage.preview = true;
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
                            pollChoosenAnswers.add(newAnswer);
                            newMediaPoll.results.results.add(newAnswer);
                        } else {
                            newMediaPoll.results.results.add(answer);
                        }
                    }
                }
            }
        }

        ArrayList<Long> uids = new ArrayList<>();
        for (int a = 0; a < messages.size(); a++) {
            MessageObject object = messages.get(a);
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
            multiplyUsers = true;
        }
        for (int i = 0; i < groupedMessagesMap.size(); i++) {
            groupedMessagesMap.valueAt(i).calculate();
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

    public class PreviewMediaPoll extends TLRPC.TL_messageMediaPoll {
        public int totalVotersCached;
    }
}
