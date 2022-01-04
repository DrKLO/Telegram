package ua.itaysonlab.catogram.vkui.icon_replaces

import android.util.SparseIntArray
import androidx.core.util.containsKey

abstract class BaseIconReplace {
    abstract val replaces: SparseIntArray

    fun wrap(id: Int): Int {
        if (replaces.containsKey(id)) return replaces[id]
        return id
    }
}