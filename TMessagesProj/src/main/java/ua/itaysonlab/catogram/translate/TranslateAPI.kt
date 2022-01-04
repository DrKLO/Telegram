package ua.itaysonlab.catogram.translate

import androidx.appcompat.app.AppCompatActivity
import org.telegram.messenger.MessageObject
import org.telegram.tgnet.TLRPC

object TranslateAPI {
    @JvmStatic
    fun callTranslationDialog(msg: String, act: AppCompatActivity) {
        TranslationSheetFragment(msg).show(act.supportFragmentManager, null)
    }
    @JvmStatic
    fun callTranslationDialog(msg: MessageObject, act: AppCompatActivity) {
        TranslationSheetFragment(extractMessageText(msg)).show(act.supportFragmentManager, null)
    }

    fun extractMessageText(msg: MessageObject): String {
        return when {
            msg.isPoll -> {
                val poll = (msg.messageOwner.media as TLRPC.TL_messageMediaPoll).poll
                "${poll.question}\n\n${poll.answers.joinToString("\n") { "- ${it.text}" }}"
            }
            else -> if (msg.caption != null) msg.caption.toString() else msg.messageText.toString()
        }
    }
}
