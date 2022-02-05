package ua.itaysonlab.catogram.translate

import android.content.Context
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
        callTranslationDialog(msg, act, null, false, null)
    }

    @JvmStatic
    fun callTranslationDialog(msg: String, act: AppCompatActivity, chatActivity: BaseFragment?, b: Boolean, onLinkPress: TranslateAlert.OnLinkPress?) {
        if (CatogramConfig.oldTranslateUI)
            TranslationSheetFragment(msg, b).show(act.supportFragmentManager, null)
        else {
            TranslateAlert.showAlertCato(act, chatActivity, LocaleController.getString("LanguageCode", R.string.LanguageCode), msg, b, onLinkPress)
        }
    }
}
