package ua.itaysonlab.catogram.translate

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import org.telegram.messenger.LocaleController
import org.telegram.messenger.MessageObject
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ChatActivity
import org.telegram.ui.Components.TranslateAlert
import ua.itaysonlab.catogram.CatogramConfig

object TranslateAPI {
    @JvmStatic
    fun callTranslationDialog(msg: String, act: AppCompatActivity) {
        if (CatogramConfig.oldTranslateUI)
            TranslationSheetFragment(msg, false).show(act.supportFragmentManager, null)
        else {
            TranslateAlert.showAlertCato(act, null, LocaleController.getString("LanguageCode", R.string.LanguageCode), msg, false, null)
        }
    }

    @JvmStatic
    fun callTranslationDialog(msgobj: MessageObject, act: AppCompatActivity, chatActivity: BaseFragment, b: Boolean, onLinkPress: TranslateAlert.OnLinkPress) {
        val msg = extractMessageText(msgobj)
        if (CatogramConfig.oldTranslateUI)
            TranslationSheetFragment(msg, b).show(act.supportFragmentManager, null)
        else {
            TranslateAlert.showAlertCato(act, chatActivity, LocaleController.getString("LanguageCode", R.string.LanguageCode), msg, b, onLinkPress)
        }
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
