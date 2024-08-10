package org.xatirchi.typingFutures

import org.telegram.messenger.MessageObject

object SpecialKeywords {


    var replyMessage: MessageObject? = null
    var lastMessageId: Int? = null
    var userId: Long? = null

    fun process(msg: String): String {
        return when (msg) {
            ".message_id" -> {
                getMessageUrl()
            }

            else -> {
                msg
            }
        }
    }

    private fun getMessageUrl(): String {
        val userId: Long
        val messageId: Int

        if (replyMessage == null) {
            userId = SpecialKeywords.userId ?: 0L
            messageId = lastMessageId ?: 0
        } else {
            userId = replyMessage?.dialogId?:0L //user_id
            messageId = replyMessage?.id?:0 //message_id

            replyMessage = null
        }
        return "tg://openmessage?user_id=$userId&message_id=$messageId"
    }


//    private var infoList = ArrayList<InfoModule>()

//    fun getInfo(): String {
//        val result = infoList.find { it.key == (msg ?: "") }
//        return if (result == null) {
//            msg ?: ""
//        } else {
//            result.data ?: ""
//        }
//    }

//    fun check(): Boolean {
//        return infoList.any { it.key == msg }
//    }

}