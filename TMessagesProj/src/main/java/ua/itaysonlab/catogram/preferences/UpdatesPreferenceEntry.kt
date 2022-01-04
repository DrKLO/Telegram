package ua.itaysonlab.catogram.preferences

import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.BaseFragment
import ua.itaysonlab.catogram.CatogramConfig
import ua.itaysonlab.catogram.OTA
import ua.itaysonlab.catogram.preferences.ktx.*
import ua.itaysonlab.tgkit.preference.types.TGKitTextIconRow

class UpdatesPreferenceEntry : BasePreferencesEntry {
    override fun getPreferences(bf: BaseFragment) = tgKitScreen(LocaleController.getString("CG_Updates_Category", R.string.CG_Updates_Category)) {
        category(LocaleController.getString("CG_Updates_Category", R.string.CG_Updates_Category)) {
            switch {
                title = LocaleController.getString("CG_Auto_Ota", R.string.CG_Auto_Ota)
                summary = LocaleController.getString("CG_Auto_Ota_summary", R.string.CG_Auto_Ota_summary)

                contract({
                    return@contract CatogramConfig.autoOta
                }) {
                    CatogramConfig.autoOta = it
                }
            }
            textIcon {
                title = LocaleController.getString("CG_Ota", R.string.CG_Ota)
                listener = TGKitTextIconRow.TGTIListener { OTA.download(bf.parentActivity, true) }
            }
        }
    }
}