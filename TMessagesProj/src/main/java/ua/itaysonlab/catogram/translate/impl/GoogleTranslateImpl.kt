package ua.itaysonlab.catogram.translate.impl

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.Components.EditTextCaption
import org.telegram.ui.Components.EditTextEmoji
import ua.itaysonlab.catogram.CatogramConfig
import java.net.URLEncoder

object GoogleTranslateImpl : CoroutineScope by MainScope() {

    private const val api_translate_url = "https://translate.googleapis.com/translate_a/single?client=gtx&dt=t&dj=1&sl=auto"

    @JvmStatic
    fun translateText(txt: String?, e: Boolean, callback: (String) -> Unit) {
        launch {
            try {
                val tl = when {
                    e -> CatogramConfig.trLang
                    else -> when (LocaleController.getString(
                        "LanguageCode",
                        R.string.LanguageCode
                    )) {
                        "zh_hans", "zh_hant" -> "zh"
                        "pt_BR" -> "pt"
                        else -> LocaleController.getString("LanguageCode", R.string.LanguageCode)
                    }
                }

                val request: Request = Request.Builder()
                    .url("$api_translate_url&tl=$tl&q=${URLEncoder.encode(txt, "UTF-8")}").build()
                val sb = StringBuilder()

                withContext(Dispatchers.IO) {
                    val response = OkHttpClient().newCall(request).execute()
                    val arr = JSONObject(response.body!!.string()).getJSONArray("sentences")
                    for (i in 0 until arr.length()) {
                        sb.append(arr.getJSONObject(i).getString("trans"))
                    }
                }

                callback.invoke(sb.toString())
            } catch (ex: Exception) {
                if (!e)
                    callback.invoke(LocaleController.getString("CG_NoInternet", R.string.CG_NoInternet))
            }
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