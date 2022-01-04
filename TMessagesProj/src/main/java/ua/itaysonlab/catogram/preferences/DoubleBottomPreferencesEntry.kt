package ua.itaysonlab.catogram.preferences

import android.util.Base64
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.BaseFragment
import ua.itaysonlab.catogram.double_bottom.DoubleBottomPasscodeActivity
import ua.itaysonlab.catogram.double_bottom.DoubleBottomStorageBridge
import ua.itaysonlab.catogram.preferences.ktx.*
import ua.itaysonlab.tgkit.preference.types.TGKitTextIconRow

class DoubleBottomPreferencesEntry : BasePreferencesEntry {
    private fun getAccountFName(profile: TLRPC.User): String {
        return "${profile.first_name}${if (profile.last_name != null) " ${profile.last_name}" else ""}${if (profile.username != null) " (${profile.username})" else ""}"
    }

    override fun getPreferences(bf: BaseFragment) = tgKitScreen(LocaleController.getString("AS_Header_DoubleBottom", R.string.AS_Header_DoubleBottom)) {
        category(LocaleController.getString("CG_DB_Accounts", R.string.CG_DB_Accounts)) {
            getProfiles().forEach { profile ->
                textIcon {
                    title = getAccountFName(profile)
                    icon = R.drawable.user_circle_outline_28
                    listener = TGKitTextIconRow.TGTIListener {
                        setupBottomFor(bf, profile)
                    }
                }
            }

            hint(LocaleController.getString("CG_DB_AccountsHint", R.string.CG_DB_AccountsHint))
        }

        category(LocaleController.getString("CG_DB_Prefs", R.string.CG_DB_Prefs)) {
            /*list {
                title = LocaleController.getString("CG_DB_Prefs_Fingerprint", R.string.CG_DB_Prefs_Fingerprint)

                val accs = getProfiles()

                contract({
                    val mpairs = accs.map {
                        Pair(it.id, getAccountFName(it))
                    }

                    val pairs = mutableListOf<Pair<Int, String>>(
                            Pair(-1, LocaleController.getString("CG_DB_Prefs_NotSet", R.string.CG_DB_Prefs_NotSet)),
                    ).also { it.addAll(mpairs) }

                    return@contract pairs
                }, {
                    return@contract if (DoubleBottomStorageBridge.fingerprintAccount != -1) LocaleController.getString("CG_DB_Prefs_NotSet", R.string.CG_DB_Prefs_NotSet) else getAccountFName(accs.first {
                        it.id == DoubleBottomStorageBridge.fingerprintAccount
                    })
                }) {
                    DoubleBottomStorageBridge.fingerprintAccount = it
                }
            }*/

            switch {
                title = LocaleController.getString("CG_DB_Prefs_Hide", R.string.CG_DB_Prefs_Hide)

                contract({
                    return@contract DoubleBottomStorageBridge.hideAccountsInSwitcher
                }) {
                    DoubleBottomStorageBridge.hideAccountsInSwitcher = it
                }
            }
        }
    }

    private fun setupBottomFor(bf: BaseFragment, profile: TLRPC.User) {
        bf.presentFragment(DoubleBottomPasscodeActivity(1) { hash, salt, type ->
            DoubleBottomStorageBridge.storageInstance = DoubleBottomStorageBridge.storageInstance.also {
                it.map[profile.id.toString()] = DoubleBottomStorageBridge.DBAccountData(
                        id = profile.id,
                        type = type,
                        hash = hash,
                        salt = Base64.encodeToString(salt, Base64.DEFAULT)
                )
            }
        })
    }

    private fun getProfiles(): List<TLRPC.User> {
        val list = mutableListOf<TLRPC.User>()

        for (i in 0 until UserConfig.MAX_ACCOUNT_COUNT) {
            val uc = UserConfig.getInstance(i)
            if (uc.isClientActivated) list.add(uc.currentUser)
        }

        return list
    }
}