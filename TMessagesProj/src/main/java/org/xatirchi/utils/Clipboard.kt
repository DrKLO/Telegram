package org.xatirchi.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

object Clipboard {

    fun copyTextToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("label", text)
        clipboard.setPrimaryClip(clip)
    }

}