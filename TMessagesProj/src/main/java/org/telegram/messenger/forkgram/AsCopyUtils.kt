package org.telegram.messenger.forkgram

import android.widget.Toast
import org.telegram.messenger.*
import org.telegram.tgnet.TLObject
import org.telegram.tgnet.TLRPC
import org.telegram.tgnet.TLRPC.*
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.AlertsCreator
import kotlin.math.min

object ForkUtils {

@JvmStatic
fun HasPhotoOrDocument(messageObject: MessageObject): Boolean {
    return HasPhoto(messageObject) || HasDocument(messageObject);
}

@JvmStatic
fun HasPhoto(messageObject: MessageObject): Boolean {
    val media = messageObject.messageOwner.media;
    return (media != null)
        && (media.photo != null)
        && (media.photo is TLRPC.TL_photo);
}

@JvmStatic
fun HasDocument(messageObject: MessageObject): Boolean {
    val media = messageObject.messageOwner.media;
    return (media != null)
        && (media.document != null)
        && (media.document is TLRPC.TL_document);
}

}

object AsCopy {

@JvmStatic
fun TakeReplyToDraft(
        key: Long,
        // Take a look for forum topic if there is no draft.
        keyTopic: TLRPC.TL_forumTopic?,
        currentAccount: Int,
        cleanDraft: Boolean): Int {
    val accountInstance = AccountInstance.getInstance(currentAccount);
    val draft = accountInstance.mediaDataController.getDraft(key, 0);
    if (draft != null) {
        val copyId = draft.reply_to.reply_to_msg_id;
        if (cleanDraft) {
            accountInstance.mediaDataController.cleanDraft(key, 0, true);
        }
        return copyId;
    }
    return (if (keyTopic != null) keyTopic.id else 0);
}

@JvmStatic
fun PerformForwardFromMyName(
        key: Long,
        keyTopic: TLRPC.TL_forumTopic?,
        text: String?,
        sendingMessageObjects: ArrayList<MessageObject>,
        currentAccount: Int,
        parentFragment: BaseFragment?,
        notify: Boolean) {

    val queue = ArrayList<() -> Unit>();
    val saveOriginalCaptions = (text == null)
    var replaceText = text
    val reply = TakeReplyToDraft(key, keyTopic, currentAccount, false)
    val topicId = if (keyTopic != null) keyTopic.id else 0;
    val currentReplaceText = {
        val temp = replaceText;
        replaceText = "";
        if (saveOriginalCaptions) null else temp;
    };
    var groupedMsgs = ArrayList<MessageObject>()

    val deque = {
        if (queue.isNotEmpty()) {
            val copyLambda = queue[0]
            queue.removeAt(0)
            copyLambda()
        } else {
            val accountInstance = AccountInstance.getInstance(currentAccount);
            accountInstance.mediaDataController.cleanDraft(key, 0, true);
        }
    }

    val sendAsAlbum = {
        val copyGrouped = ArrayList<MessageObject>(groupedMsgs);
        groupedMsgs = ArrayList()
        val copyText = currentReplaceText();
        queue.add {
            SendItemsAsAlbum(
                currentAccount,
                copyGrouped,
                key,
                if (reply == 0) topicId else reply,
                parentFragment,
                copyText,
                notify,
                deque)
        }
    }

    for (msg in sendingMessageObjects) {
        if (msg.groupId != 0L) {
            if (groupedMsgs.isNotEmpty()) {
                if (groupedMsgs[0].groupId != msg.groupId) {
                    sendAsAlbum();
                }
            }
            groupedMsgs.add(msg)
            continue
        }
        if (groupedMsgs.isNotEmpty()) {
            sendAsAlbum();
        }
        val copyMsg = msg;
        val copyText = currentReplaceText();
        queue.add {
            val instance = SendMessagesHelper.getInstance(currentAccount);
            instance.processForwardFromMyName(copyMsg, key, copyText, notify, topicId)
            deque();
        }
    }
    if (groupedMsgs.isNotEmpty()) {
        sendAsAlbum();
    }
    deque();
}

@JvmStatic
fun GroupItemsIntoAlbum(
        key: Long,
        reply: Int,
        text: String?,
        sendingMessageObjects: ArrayList<MessageObject>,
        currentAccount: Int,
        parentFragment: BaseFragment?,
        notify: Boolean) {
    if (sendingMessageObjects.isEmpty()) {
        return;
    }

    val sub = { from: Int, to: Int ->
        ArrayList<MessageObject>(sendingMessageObjects.subList(from, to));
    };

    val objectsToSend = sub(0, min(10, sendingMessageObjects.size));
    val objectsToDelay = sub(objectsToSend.size, sendingMessageObjects.size);

    val finish = {
        GroupItemsIntoAlbum(key, reply, text, objectsToDelay, currentAccount, parentFragment, notify)
    };

    SendItemsAsAlbum(
            currentAccount,
            objectsToSend,
            key,
            reply,
            parentFragment,
            text,
            notify,
            finish)
}

fun inputMediaFromMessageObject(m: MessageObject): InputMedia {
    if (!(m.messageOwner.media != null
        && m.messageOwner.media !is TL_messageMediaEmpty
        && m.messageOwner.media !is TL_messageMediaWebPage
        && m.messageOwner.media !is TL_messageMediaGame
        && m.messageOwner.media !is TL_messageMediaInvoice)) {
        return TL_inputMediaEmpty()
    }
    if (ForkUtils.HasDocument(m)) {
        val document = m.messageOwner.media.document
        val media = TL_inputMediaDocument()
        media.id = TL_inputDocument()
        media.id.id = document.id
        media.id.access_hash = document.access_hash
        media.id.file_reference = document.file_reference
        if (media.id.file_reference == null) {
            media.id.file_reference = ByteArray(0)
        }
        return media
    }
    if (ForkUtils.HasPhoto(m)) {
        val photo = m.messageOwner.media.photo
        val media = TL_inputMediaPhoto()
        media.id = TL_inputPhoto()
        media.id.id = photo.id
        media.id.access_hash = photo.access_hash
        media.id.file_reference = photo.file_reference
        if (media.id.file_reference == null) {
            media.id.file_reference = ByteArray(0)
        }
        return media
    }
    return TL_inputMediaEmpty()
}

@JvmStatic
fun SendItemsAsAlbum(
        currentAccount: Int,
        messages: ArrayList<MessageObject>,
        peer: Long,
        reply: Int,
        fragment: BaseFragment?,
        replaceText: String?,
        notify: Boolean,
        finish: () -> Unit) {
    if (peer == 0L || messages.size > 10 || messages.isEmpty()) {
        return
    }
    val accountInstance = AccountInstance.getInstance(currentAccount)
    val lower_id = peer
    val sendToPeer: InputPeer = 
        (if (lower_id != 0L) accountInstance.messagesController.getInputPeer(lower_id)
            else null)
            ?: return
    val request = TL_messages_sendMultiMedia()
    request.peer = sendToPeer
    request.silent = !notify
    request.reply_to = SendMessagesHelper.getInstance(currentAccount).createReplyInput(reply)
    if (reply != 0) {
        request.flags += 1;
    }

    for (i in 0 until messages.size) {
        val m = messages[i]
        val media: InputMedia = inputMediaFromMessageObject(m)
        if (media is TL_inputMediaEmpty) {
            continue
        }
        val inputSingleMedia = TL_inputSingleMedia()
        inputSingleMedia.random_id = Utilities.random.nextLong()
        inputSingleMedia.media = media
        if (replaceText == null) {
            inputSingleMedia.message = m.messageOwner.message
            val entities = m.messageOwner.entities
            if (entities != null && entities.isNotEmpty()) {
                inputSingleMedia.entities = entities
                inputSingleMedia.flags = inputSingleMedia.flags or 1
            }
        } else {
            inputSingleMedia.message = if (request.multi_media.isEmpty()) replaceText else ""
        }
        request.multi_media.add(inputSingleMedia)
    }

    val showToast = { msg: String ->
        AndroidUtilities.runOnUIThread {
            Toast.makeText(
                ApplicationLoader.applicationContext,
                msg,
                Toast.LENGTH_LONG).show();
        }
    }

    val sendAlbum = sendRequest@{ response: TLObject?, error: TL_error? ->
        if (error == null) {
            accountInstance.messagesController.processUpdates(response as Updates, false)
            AndroidUtilities.runOnUIThread { finish(); }
            return@sendRequest
        }
        if (error != null) {
        }
        if (!FileRefController.isFileRefError(error.text)) {
            showToast("It seems that you want to group incompatible file types.");
            AndroidUtilities.runOnUIThread {
                AlertsCreator.processError(
                    currentAccount,
                    error,
                    fragment,
                    request)
            }
            return@sendRequest
        }
        // FileRefError.

        // Request messages, update file references and resend.
        val handleMessages = handleMessages@{ cloudMessages: ArrayList<TLRPC.Message>, msgErr: TL_error? ->
            if (cloudMessages.isEmpty()) {
                return@handleMessages
            }
            var atLeastOneFileRefUpdated = false;
            for (i in cloudMessages.indices) {
                val cloudMedia = cloudMessages[i].media;
                val localMedia = messages[i].messageOwner.media;
                if (cloudMedia == null) {
                    continue
                }
                if (cloudMedia.document != null && ForkUtils.HasDocument(messages[i])) {
                    atLeastOneFileRefUpdated = true;
                    localMedia.document.file_reference = cloudMedia.document.file_reference
                }
                if (cloudMedia.photo != null && ForkUtils.HasPhoto(messages[i])) {
                    atLeastOneFileRefUpdated = true;
                    localMedia.photo.file_reference = cloudMedia.photo.file_reference
                }
            }
            if (!atLeastOneFileRefUpdated) {
                showToast("Sorry, something went wrong.");
                return@handleMessages
            }
            SendItemsAsAlbum(currentAccount, messages, peer, reply, fragment, replaceText, notify, finish)
        }

        ForkApi.TLRPCMessages(currentAccount, messages, handleMessages);
    };
    accountInstance.connectionsManager.sendRequest(request, sendAlbum)
}

}
