package ua.itaysonlab.catogram.translate

import org.telegram.ui.Components.EditTextCaption
import org.telegram.ui.Components.EditTextEmoji
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
        ensureImpl()
        return impl.translateText(txt, e, callback)
    }

    @JvmStatic
    fun translateTextWithLangInfo(txt: String?, e: Boolean, callback: (String /* Text */, String /* From Lang */, String /* To Lang */) -> Unit) {
        ensureImpl()
        return impl.translateTextWithLangInfo(txt, e, callback)
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