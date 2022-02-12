package ua.itaysonlab.catogram.tabs

import ua.itaysonlab.catogram.CatogramConfig

object TabIconManager {
    @JvmStatic
    fun injectAppTitle(id: Int, name: String): String {
        //Log.d("TabIconManager", "Requesting tab icon for $id [fb: name]")
        val fallback = CatogramConfig.customChatListTitle
        if (id == Integer.MAX_VALUE) return fallback // All chats
        return when (CatogramConfig.newTabs_iconsV2_mode) {
            1 -> fallback
            2 -> fallback
            3 -> name
            else -> fallback
        }
    }
}