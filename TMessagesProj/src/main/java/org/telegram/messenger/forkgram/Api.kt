package org.telegram.messenger.forkgram

import org.telegram.messenger.AccountInstance
import org.telegram.messenger.MessageObject
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC

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

}
