package ua.itaysonlab.extras

import android.content.ComponentName
import android.content.pm.PackageManager
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BuildConfig

object IconExtras {
    enum class Icon(val mf: String) {
        OLD("CG_Icon_Old"),
        ALT_BLUE("CG_Icon_Alt_Blue"),
        ALT_ORANGE("CG_Icon_Alt_Orange")
    }

    fun setIcon(variant: Int) {
        setIcon(Icon.values()[variant])
    }

    private fun setIcon(icon: Icon) {
        Icon.values().forEach {
            if (it == icon) {
                enableComponent(it.mf)
            } else {
                disableComponent(it.mf)
            }
        }
    }

    private fun enableComponent(name: String) {
        ApplicationLoader.applicationContext.packageManager.setComponentEnabledSetting(
                ComponentName(BuildConfig.APPLICATION_ID, "org.telegram.messenger.$name"),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
        )
    }

    private fun disableComponent(name: String) {
        ApplicationLoader.applicationContext.packageManager.setComponentEnabledSetting(
                ComponentName(BuildConfig.APPLICATION_ID, "org.telegram.messenger.$name"),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
        )
    }
}