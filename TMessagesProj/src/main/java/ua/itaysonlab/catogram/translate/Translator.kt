package ua.itaysonlab.catogram.translate

import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.Components.EditTextCaption
import org.telegram.ui.Components.EditTextEmoji
import ua.itaysonlab.catogram.CatogramConfig
import ua.itaysonlab.catogram.translate.impl.GoogleTranslateImpl
import ua.itaysonlab.catogram.translate.impl.ITranslateImpl

object Translator {
    var impl : ITranslateImpl = GoogleTranslateImpl

    @JvmStatic
    fun ensureImpl() {
        // In the future when there are more APIs, make sure user selected is used here
    }

    @JvmStatic
    fun translateText(txt: String?, e: Boolean, callback: (String) -> Unit) {
        return translateTextWithLangInfo(txt, e) { a: String, _: String, _: String ->
            callback.invoke(a)
        }
    }

    @JvmStatic
    fun translateTextWithLangInfo(txt: String?, isOutgoing: Boolean, callback: (String /* Text */, String /* From Lang */, String /* To Lang */) -> Unit) {
        ensureImpl()
        val tl = when {
            isOutgoing -> CatogramConfig.trLang
            else -> when (LocaleController.getString(
                    "LanguageCode",
                    R.string.LanguageCode
            )) {
                "zh_hans", "zh_hant" -> "zh"
                "pt_BR" -> "pt"
                else -> LocaleController.getString("LanguageCode", R.string.LanguageCode)
            }
        }
        return impl.translateText(txt, tl) { a, b, c ->
            if (!isOutgoing || b != "Error")
                callback.invoke(if (a == "Error") LocaleController.getString("CG_NoInternet", R.string.CG_NoInternet) else a, b, c)
        }
    }

    @JvmStatic
    fun translateEditText(txt: String, editText: EditTextCaption) {
        return translateText(txt, true) { text ->
            editText.setText(text)
        }
    }


    @JvmStatic
    fun translateComment(txt: String, editText: EditTextEmoji) {
        return translateText(txt, true) { text ->
            editText.setText(text)
        }
    }
}