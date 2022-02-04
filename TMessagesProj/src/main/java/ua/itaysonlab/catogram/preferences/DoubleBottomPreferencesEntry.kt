package ua.itaysonlab.catogram.preferences

import android.util.Base64
import com.google.android.exoplayer2.util.Log
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.BulletinFactory
import ua.itaysonlab.catogram.double_bottom.DoubleBottomBridge
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
            textIcon {
                title = LocaleController.getString("CX_DBSettingsPin", R.string.CX_DBSettingsPin)
                icon = R.drawable.settings_outline_28
                listener = object : TGKitTextIconRow.TGTIListener {
                    override fun onClick(bf: BaseFragment) {
                        setupBottomPin(bf)
                    }
                    override fun onLongClick(bf: BaseFragment) {
                        unsetupBottomPin(bf)
                    }
                }
            }
            getProfiles().forEach { profile ->
                textIcon {
                    title = getAccountFName(profile)
                    icon = R.drawable.user_circle_outline_28
                    listener = object : TGKitTextIconRow.TGTIListener {
                        override fun onClick(bf: BaseFragment) {
                            setupBottomFor(bf, profile)
                        }
                        override fun onLongClick(bf: BaseFragment) {
                            unsetupBottomFor(bf, profile)
                        }
                    }
                }
            }

            hint(LocaleController.getString("CX_DB_AccountsHint", R.string.CX_DB_AccountsHint))
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
        val b = DoubleBottomBridge.isDbActivatedForAccount(profile.id)
        bf.presentFragment(DoubleBottomPasscodeActivity((if (b) 3 else 1), bf, profile.id) { unset, _, hash, salt, type ->
            assert(!unset)
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

    private fun setupBottomPin(bf: BaseFragment) {
        val b = DoubleBottomBridge.isDbActivatedForAccount(0L)
        bf.presentFragment(DoubleBottomPasscodeActivity((if (b) 3 else 1), bf, 0L) { unset, _, hash, salt, type ->
            assert(!unset)
            DoubleBottomStorageBridge.storageInstance = DoubleBottomStorageBridge.storageInstance.also {
                it.map[0L.toString()] = DoubleBottomStorageBridge.DBAccountData(
                        id = 0L,
                        type = type,
                        hash = hash,
                        salt = Base64.encodeToString(salt, Base64.DEFAULT)
                )
            }
        })
    }

    private fun unsetupBottomPin(bf: BaseFragment) {
        if (!DoubleBottomBridge.isDbActivatedForAccount(0)) {
            BulletinFactory.of(bf).createErrorBulletin(LocaleController.getString("CX_DBNotEnabled2", R.string.CX_DBNotEnabled2), bf.resourceProvider).show()
            return
        }
        bf.presentFragment(DoubleBottomPasscodeActivity(2, bf, 0) { unset, user, _, _, _ ->
            assert(unset)
            if (0L == user) {
                DoubleBottomStorageBridge.storageInstance = DoubleBottomStorageBridge.storageInstance.also {
                    it.map.remove(0L.toString())
                }
            } else {
                BulletinFactory.of(bf).createErrorBulletin(LocaleController.getString("PasscodeDoNotMatch", R.string.PasscodeDoNotMatch), bf.resourceProvider).show()
            }
        })
    }

    private fun unsetupBottomFor(bf: BaseFragment, profile: TLRPC.User) {
        if (!DoubleBottomBridge.isDbActivatedForAccount(profile.id)) {
            BulletinFactory.of(bf).createErrorBulletin(LocaleController.getString("CX_DBNotEnabled", R.string.CX_DBNotEnabled), bf.resourceProvider).show()
            return
        }
        bf.presentFragment(DoubleBottomPasscodeActivity(2, bf, profile.id) { unset, user, _, _, _ ->
            assert(unset)
            if (profile.id == user) {
                DoubleBottomStorageBridge.storageInstance = DoubleBottomStorageBridge.storageInstance.also {
                    it.map.remove(profile.id.toString())
                }
            } else {
                BulletinFactory.of(bf).createErrorBulletin(LocaleController.getString("PasscodeDoNotMatch", R.string.PasscodeDoNotMatch), bf.resourceProvider).show()
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