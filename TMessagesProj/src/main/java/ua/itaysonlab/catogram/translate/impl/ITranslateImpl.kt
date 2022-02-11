package ua.itaysonlab.catogram.translate.impl

import org.telegram.ui.Components.EditTextCaption
import org.telegram.ui.Components.EditTextEmoji

interface ITranslateImpl {
    fun translateText(txt: String?, tl: String, callback: (String /* Text */, String /* From Lang */, String /* To Lang */) -> Unit)
}