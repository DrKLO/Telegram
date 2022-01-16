package ua.itaysonlab.catogram.preferences

import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.BaseFragment
import ua.itaysonlab.catogram.CatogramConfig
import ua.itaysonlab.catogram.preferences.ktx.category
import ua.itaysonlab.catogram.preferences.ktx.contract
import ua.itaysonlab.catogram.preferences.ktx.switch
import ua.itaysonlab.catogram.preferences.ktx.tgKitScreen

class ExtraPreferencesEntry : BasePreferencesEntry {
		override fun getPreferences(bf: BaseFragment) = tgKitScreen(LocaleController.getString("CX_Extra", R.string.CX_Extra)) {
			category(LocaleController.getString("AS_Header_Appearance", R.string.AS_Header_Appearance)) {

			}
		}
}