package ua.itaysonlab.catogram.tabs

import android.content.Context
import org.telegram.messenger.ApplicationLoader
import ua.itaysonlab.catogram.CatogramConfig
import ua.itaysonlab.extras.CatogramExtras
import java.io.File
import java.nio.charset.StandardCharsets

object TabIconManager {
    private const val SEPARATOR = "::"

    private val mappedEmojis = hashMapOf<Int, String>()
    private val file = File(ApplicationLoader.applicationContext.getDir("cg_goodies", Context.MODE_PRIVATE), "CatogramFolderCache.cft")

    init {
        load()
    }

    /*@JvmStatic
    fun injectTabTitle(tab: FilterTabsView.Tab): String {
        val icon = getIconForTab(tab.id)
        return when {
            CatogramConfig.newTabs_iconsV2_replace -> icon
            CatogramConfig.newTabs_iconsV2_append -> "$icon ${tab.title}"
            else -> tab.title
        }
    }*/

    @JvmStatic
    fun injectTabTitle(id: Int, fallback: String): String {
        //Log.d("TabIconManager", "Requesting tab icon for $id [fb: $fallback]")
        if (id == Integer.MAX_VALUE) return fallback // All chats
        val icon = getIconForTab(id)
        return when (CatogramConfig.newTabs_iconsV2_mode) {
            1 -> "$icon $fallback"
            2 -> icon
            else -> fallback
        }
    }

    @JvmStatic
    fun addTab(id: Int, emoji: ByteArray) {
        val eb = String(emoji, StandardCharsets.UTF_8)
        /*Log.d("TabIconManager", "Emoji raw bytes: ${emoji.contentToString()}")
        Log.d("TabIconManager", "Emoji UTF-16+BOM bytes: ${eb.toByteArray(StandardCharsets.UTF_16).contentToString()}")
        Log.d("TabIconManager", "Emoji UTF-16 [BE] bytes: ${eb.toByteArray(StandardCharsets.UTF_16BE).contentToString()}")
        Log.d("TabIconManager", "Emoji UTF-16 [LE] bytes: ${eb.toByteArray(StandardCharsets.UTF_16LE).contentToString()}")
        Log.d("TabIconManager", "Emoji printed here: $eb")*/
        if (emoji.isEmpty()) return
        mappedEmojis[id] = String(emoji, StandardCharsets.UTF_8)
        save()
    }

    fun getIconForTab(id: Int): String {
        return if (mappedEmojis.containsKey(id) && mappedEmojis[id] != null) mappedEmojis[id]!! else CatogramExtras.wrapEmoticon(null)
    }

    private fun clear() {
        mappedEmojis.clear()
        save()
    }

    private fun load() {
        if (!file.exists()) file.createNewFile()
        file.readLines().forEach {
            val splitted = it.split(SEPARATOR)
            mappedEmojis[splitted[0].toInt()] = splitted[1]
        }
    }

    private fun save() {
        if (!file.exists()) file.createNewFile()
        var content = ""
        mappedEmojis.entries.forEach {
            content += "${it.key}$SEPARATOR${it.value}\n"
        }
        file.writeText(content)
    }
}