package ua.itaysonlab.catogram.tabs

import android.content.Context
import com.google.android.exoplayer2.util.Log
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.UserConfig
import ua.itaysonlab.catogram.CatogramConfig
import ua.itaysonlab.extras.CatogramExtras
import java.io.File
import java.nio.charset.StandardCharsets


// Roughly based on flawed Catogram impl. Phew this is shitty.
object TabIconManager {
    private const val SEPARATOR = "::"

    private var mappedEmojis = hashMapOf<Int, HashMap<Int, String>>()
    private val mappedEmojisCache = hashMapOf<Int, HashMap<Int, String>>()
    private var offset = hashMapOf<Int, Int>()
    private val file = hashMapOf<Int, File>()

    init {
        load(UserConfig.selectedAccount)
    }

    @JvmStatic
    fun injectTabTitle(id: Int, fallback: String): String {
        //Log.d("TabIconManager", "Requesting tab icon for $id [fb: $fallback]")
        if (id == Integer.MAX_VALUE) return fallback // All chats
        val icon = getIconForTab(id, UserConfig.selectedAccount)
        return when (CatogramConfig.newTabs_iconsV2_mode) {
            1 -> "$icon $fallback"
            2 -> icon
            else -> fallback
        }
    }

    @JvmStatic
    fun addTab(id: Int, realid: Int, emoji: ByteArray, selectedAccount: Int) {
        val eb = String(emoji, StandardCharsets.UTF_8)
        /*Log.d("TabIconManager", "Emoji raw bytes: ${emoji.contentToString()}")
        Log.d("TabIconManager", "Emoji UTF-16+BOM bytes: ${eb.toByteArray(StandardCharsets.UTF_16).contentToString()}")
        Log.d("TabIconManager", "Emoji UTF-16 [BE] bytes: ${eb.toByteArray(StandardCharsets.UTF_16BE).contentToString()}")
        Log.d("TabIconManager", "Emoji UTF-16 [LE] bytes: ${eb.toByteArray(StandardCharsets.UTF_16LE).contentToString()}")
        Log.d("TabIconManager", "Emoji printed here $id/$realid: $eb")*/
        if (emoji.isEmpty()) return
        mappedEmojis[selectedAccount]!![id] = eb
        mappedEmojisCache[selectedAccount]!![realid] = eb
        save(selectedAccount)
    }

    @JvmStatic
    fun addTabFiltered(id: Int, realid: Int, emoji: ByteArray, selectedAccount: Int) {
        if (!mappedEmojis.containsKey(selectedAccount))
            mappedEmojis[selectedAccount] = HashMap()
        if (!offset.containsKey(selectedAccount))
            offset[selectedAccount] = 0
        if (!mappedEmojisCache.containsKey(selectedAccount))
            mappedEmojisCache[selectedAccount] = HashMap()
        if (mappedEmojisCache[selectedAccount]!!.containsKey(realid)) {
            offset[selectedAccount] = offset[selectedAccount]!! + 1
            val newMappedEmojis = hashMapOf<Int, String>()
            for (item in mappedEmojis[selectedAccount]!!) {
                if (item.key == 0) continue
                newMappedEmojis[item.key-1] = item.value
            }
            mappedEmojis[selectedAccount] = newMappedEmojis
        }
        addTab(id - offset[selectedAccount]!!, realid, emoji, selectedAccount)
    }

    fun getIconForTab(id: Int, selectedAccount: Int): String {
        if (!mappedEmojis.containsKey(selectedAccount)) load(selectedAccount)
        return if (mappedEmojis[selectedAccount]!!.containsKey(id) && mappedEmojis[selectedAccount]!![id] != null) mappedEmojis[selectedAccount]!![id]!! else CatogramExtras.wrapEmoticon(null)
    }

    private fun load(selectedAccount: Int) {
        if (!file.containsKey(selectedAccount))
            file[selectedAccount] = File(ApplicationLoader.applicationContext.getDir("cg_goodies", Context.MODE_PRIVATE), "$selectedAccount.folder")
        mappedEmojis[selectedAccount] = HashMap()
        mappedEmojisCache[selectedAccount] = HashMap()
        if (!file[selectedAccount]!!.exists()) file[selectedAccount]!!.createNewFile()
        file[selectedAccount]!!.readLines().forEach {
            val splitted = it.split(SEPARATOR)
            mappedEmojis[selectedAccount]!![splitted[0].toInt()] = splitted[1]
        }
    }

    private fun save(selectedAccount: Int) {
        if (!file.containsKey(selectedAccount))
            file[selectedAccount] = File(ApplicationLoader.applicationContext.getDir("cg_goodies", Context.MODE_PRIVATE), "$selectedAccount.folder")
        if (!file[selectedAccount]!!.exists()) file[selectedAccount]!!.createNewFile()
        var content = ""
        mappedEmojis[selectedAccount]!!.entries.forEach {
            content += "${it.key}$SEPARATOR${it.value}\n"
        }
        file[selectedAccount]!!.writeText(content)
    }
}