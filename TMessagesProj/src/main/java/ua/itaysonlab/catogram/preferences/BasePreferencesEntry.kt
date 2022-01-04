package ua.itaysonlab.catogram.preferences

import org.telegram.ui.ActionBar.BaseFragment
import ua.itaysonlab.tgkit.preference.TGKitSettings
import ua.itaysonlab.tgkit.preference.types.TGKitListPreference
import ua.itaysonlab.tgkit.preference.types.TGKitSettingsCellRow
import ua.itaysonlab.tgkit.preference.types.TGKitSwitchPreference
import ua.itaysonlab.tgkit.preference.types.TGKitTextDetailRow

interface BasePreferencesEntry {
    fun getProcessedPrefs(bf: BaseFragment): TGKitSettings {
        return getPreferences(bf).also {
            it.categories.forEach { c ->
                val lastIndex = c.preferences.lastIndex
                c.preferences.forEachIndexed { index, pref ->
                    val isNotLast = index != lastIndex
                    when (pref) {
                        is TGKitListPreference -> {
                            pref.divider = isNotLast
                        }
                        is TGKitSettingsCellRow -> {
                            pref.divider = isNotLast
                        }
                        is TGKitTextDetailRow -> {
                            pref.divider = isNotLast
                        }
                        is TGKitSwitchPreference -> {
                            pref.divider = isNotLast
                        }
                    }
                }
            }
        }
    }

    fun getPreferences(bf: BaseFragment): TGKitSettings
}