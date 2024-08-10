package org.xatirchi.utils

import android.content.Context
import org.telegram.messenger.ApplicationLoader
import javax.inject.Singleton

@Singleton
object GhostVariable {
    var ghostMode = false

    private var sharedPreferences =
        ApplicationLoader.applicationContext.getSharedPreferences("db", Context.MODE_PRIVATE)
    private var editor = sharedPreferences.edit()

    fun setGhostMode() {
        ghostMode = sharedPreferences.getBoolean("ghost", false)
    }

    fun changeGhostMode(mode:Boolean) {
        ghostMode = mode
        editor.putBoolean("ghost", ghostMode)
        editor.commit()
        MyStatus.setMyStatus()
    }
}