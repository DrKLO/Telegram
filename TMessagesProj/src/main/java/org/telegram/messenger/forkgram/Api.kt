package org.telegram.messenger.forkgram

import org.telegram.messenger.AccountInstance
import org.telegram.messenger.MessageObject
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC
import kotlin.math.min

object ForkApi {

@JvmStatic
fun TLRPCMessages(
        currentAccount: Int,
        messages: ArrayList<MessageObject>,
        finish: (ArrayList<TLRPC.Message>, TLRPC.TL_error?) -> Unit) {
    if (messages.count() == 0) {
        return;
    }
    val accountInstance = AccountInstance.getInstance(currentAccount);
    val channelId = messages[0].channelId;
    val api = accountInstance.connectionsManager;
    if (channelId != 0L) {
        val req = TLRPC.TL_channels_getMessages();
        req.channel =
            accountInstance.messagesController.getInputChannel(channelId);
        req.id = ArrayList(messages.map{ it.realId })
        api.sendRequest(req) { msgResponse: TLObject?, msgErr: TLRPC.TL_error? ->
            val msgRes = if (msgResponse != null)
                msgResponse as TLRPC.messages_Messages
                else TLRPC.TL_messages_messagesNotModified();
            finish(msgRes.messages, msgErr);
        };
    } else {
        val req = TLRPC.TL_messages_getMessages();
        req.id = ArrayList(messages.map{ it.realId })
        api.sendRequest(req) { msgResponse: TLObject?, msgErr: TLRPC.TL_error? ->
            val msgRes = if (msgResponse != null)
                msgResponse as TLRPC.messages_Messages
                else TLRPC.TL_messages_messagesNotModified();
            finish(msgRes.messages, msgErr);
        };
    }
}

@JvmStatic
fun FullChannel(
        currentAccount: Int,
        channelId: Long,
        finish: (TLRPC.TL_messages_chatFull) -> Unit) {
    if (channelId == 0L) {
        return;
    }
    val accountInstance = AccountInstance.getInstance(currentAccount);
    val api = accountInstance.connectionsManager;

    val req = TLRPC.TL_channels_getFullChannel();
    req.channel = accountInstance.messagesController.getInputChannel(channelId);

    api.sendRequest(req) { response: TLObject?, msgErr: TLRPC.TL_error? ->
        if (response != null) {
            finish(response as TLRPC.TL_messages_chatFull);
        }
    };
}

@JvmStatic
fun SearchAllMessages(
        currentAccount: Int,
        peer: TLRPC.InputPeer,
        from: TLRPC.InputPeer,
        step: (ArrayList<TLRPC.Message>) -> Unit,
        finish: () -> Unit) {
    val api = AccountInstance.getInstance(currentAccount).connectionsManager;

    val found = ArrayList<TLRPC.Message>();
    val performPtr = ArrayList<(Int) -> Int>();

    val perform = {offsetId: Int ->
        val req = TLRPC.TL_messages_search();
        req.peer = peer;
        req.from_id = from;
        req.limit = 100;
        req.offset_id = offsetId;
        req.filter = TLRPC.TL_inputMessagesFilterEmpty();
        req.q = "";
        req.flags = (0 or 1);

        api.sendRequest(req) { msgResponse: TLObject?, msgErr: TLRPC.TL_error? ->
            val msgRes = if (msgResponse != null)
                msgResponse as TLRPC.messages_Messages
            else TLRPC.TL_messages_messagesNotModified();

            if (msgRes.messages.isEmpty()) {
                finish();
                return@sendRequest;
            }

            var newOffsetId = msgRes.messages.get(0).id;
            for (message in msgRes.messages) {
                found.add(message);
                newOffsetId = min(newOffsetId, message.id);
            }
            step(found);
            found.clear();
            performPtr[0](newOffsetId);
        };
    };

    performPtr.add(perform);
    perform(0);
}


}
