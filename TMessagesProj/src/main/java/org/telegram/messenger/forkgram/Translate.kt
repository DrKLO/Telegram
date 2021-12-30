package org.telegram.messenger.forkgram

import org.json.JSONObject
import org.json.JSONTokener
import java.net.URI
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.lang.StringBuilder
import java.net.HttpURLConnection
import java.nio.charset.Charset
import kotlin.math.roundToInt

object ForkTranslate {

// Should be invoked in thread.
@JvmStatic
fun Translate(
        fromLanguage: String,
        toLanguage: String,
        userAgents: Array<String>,
        text: CharSequence
): Array<String> {
    val userAgent = userAgent@{
        return@userAgent userAgents[(Math.random() * (userAgents.size - 1)).roundToInt()];
    };
    val readResponse = readResponse@{ connection: HttpURLConnection ->
        val textBuilder = StringBuilder()
        BufferedReader(InputStreamReader(connection.inputStream, Charset.forName("UTF-8"))).use { reader ->
            var c = 0
            while (reader.read().also { c = it } != -1) {
                textBuilder.append(c.toChar())
            }
        }
        return@readResponse textBuilder.toString()
    };
    val getVqd = getVqd@{
        val uri = URI("https://duckduckgo.com/?q=translate&ia=web");
        val connection = uri.toURL().openConnection() as HttpURLConnection;
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", userAgent())
        val response = readResponse(connection)
        val start = response.indexOf("vqd=")
        val end = response.indexOf(";", start)
        val substring = response.substring(start + "vqd=".length, end);
        return@getVqd Regex("[0-9-]+").find(substring)?.groupValues?.getOrNull(0);
    };

    val fetchTranslate = fetchTranslate@{
        val uri = URI("https://duckduckgo.com/translation.js?vqd=${ getVqd() }&query=translate&to=${ android.net.Uri.encode(toLanguage) }")
        val connection = uri.toURL().openConnection() as HttpURLConnection;
        connection.requestMethod = "POST";
        connection.setRequestProperty("User-Agent", userAgent())
        connection.setRequestProperty("Content-Type", "application/json")

        connection.doOutput = true
        val os: OutputStream = connection.outputStream
        with(os) {
            write(text.toString().toByteArray())
            flush()
            close()
        }
        val response = readResponse(connection)
        val tokener = JSONTokener(response);
        val obj = JSONObject(tokener);
        val source = obj.getString("detected_language");
        val result = obj.getString("translated");
        return@fetchTranslate arrayOf(result, source);
    };

    return fetchTranslate();
}

}
