package ua.itaysonlab.extras

import android.content.ComponentName
import android.content.pm.PackageManager
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.BuildConfig

object IconExtras {
    enum class Icon(val mf: String) {
        OLD("CG_Icon_Old"),
        ALT_BLUE("CG_Icon_Alt_Blue"),
        ALT_ORANGE("CG_Icon_Alt_Orange"),
        CX_BLACK_REG("CX_Black_Reg"),
        CX_BLUE_REG("CX_Blue_Reg"),
        CX_CYAN_REG("CX_Cyan_Reg"),
        CX_GREEN_REG("CX_Green_Reg"),
        CX_ORANGE_REG("CX_Orange_Reg"),
        CX_PINK_REG("CX_Pink_Reg"),
        CX_PURPLE_REG("CX_Purple_Reg"),
        CX_RED_REG("CX_Red_Reg"),
        CX_TAFFY_REG("CX_Taffy_Reg"),
        CX_YELLOW_REG("CX_Yellow_Reg"),
        CX_MONET("CX_Monet")
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