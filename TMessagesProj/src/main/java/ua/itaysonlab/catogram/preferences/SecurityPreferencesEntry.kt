package ua.itaysonlab.catogram.preferences

import android.os.Environment
import android.widget.Toast
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.BaseFragment
import ua.itaysonlab.catogram.CatogramConfig
import ua.itaysonlab.catogram.preferences.ktx.*
import ua.itaysonlab.tgkit.preference.types.TGKitTextIconRow
import java.io.File

class SecurityPreferencesEntry : BasePreferencesEntry {
    override fun getPreferences(bf: BaseFragment) = tgKitScreen(LocaleController.getString("AS_Category_Security", R.string.AS_Category_Security)) {

        category(LocaleController.getString("AS_Header_Privacy", R.string.AS_Header_Privacy)) {
            switch {
                title = LocaleController.getString("AS_NoProxyPromo", R.string.AS_NoProxyPromo)

                contract({
                    return@contract CatogramConfig.hideProxySponsor
                }) {
                    CatogramConfig.hideProxySponsor = it
                }
            }
            switch {
                title = LocaleController.getString("CG_PrivateDir", R.string.CG_PrivateDir)
                summary = LocaleController.getString("CG_PrivateDir_desc", R.string.CG_PrivateDir_desc)

                contract({
                    return@contract CatogramConfig.privateDir
                }) {
                    CatogramConfig.privateDir = it
                }
            }
            textIcon {
                title = LocaleController.getString("CG_CleanOld", R.string.CG_CleanOld)

                listener = TGKitTextIconRow.TGTIListener {
                    val file = File(Environment.getExternalStorageDirectory(), "Telegram")
                    file.deleteRecursively()
                    Toast.makeText(bf.parentActivity, LocaleController.getString("CG_RemovedS", R.string.CG_RemovedS), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}