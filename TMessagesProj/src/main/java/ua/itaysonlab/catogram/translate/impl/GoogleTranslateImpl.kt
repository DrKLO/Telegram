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

object GoogleTranslateImpl : CoroutineScope by MainScope(), ITranslateImpl  {

    private const val api_translate_url = "https://translate.googleapis.com/translate_a/single?client=gtx&dt=t&dj=1&sl=auto"

    override fun translateText(txt: String?, e: Boolean, callback: (String) -> Unit) {
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
                    val json = JSONObject(response.body!!.string())
                    val arr = json.getJSONArray("sentences")
                    for (i in 0 until arr.length()) {
                        sb.append(arr.getJSONObject(i).getString("trans"))
                    }
                }

                callback.invoke(sb.toString(),)
            } catch (ex: Exception) {
                if (!e)
                    callback.invoke(LocaleController.getString("CG_NoInternet", R.string.CG_NoInternet))
            }
        }
    }

    override fun translateTextWithLangInfo(txt: String?, e: Boolean, callback: (String /* Text */, String /* From Lang */, String /* To Lang */) -> Unit) {
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
                var sl = "UNKNOWN"

                withContext(Dispatchers.IO) {
                    val response = OkHttpClient().newCall(request).execute()
                    val json = JSONObject(response.body!!.string())
                    val arr = json.getJSONArray("sentences")
                    if (json.has("src"))
                        sl = json.getString("src")
                    for (i in 0 until arr.length()) {
                        sb.append(arr.getJSONObject(i).getString("trans"))
                    }
                }

                callback.invoke(sb.toString(), sl.uppercase(), LocaleController.getString("LanguageCode", R.string.LanguageCode))
            } catch (ex: Exception) {
                if (!e)
                    callback.invoke(LocaleController.getString("CG_NoInternet", R.string.CG_NoInternet), "Error", "Error")
            }
        }
    }
}