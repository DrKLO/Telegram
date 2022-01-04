package ua.itaysonlab.catogram.preferences

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Browser
import org.telegram.messenger.BuildVars
import org.telegram.messenger.LocaleController
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.LaunchActivity
import ua.itaysonlab.catogram.CatogramPreferencesNavigator
import ua.itaysonlab.catogram.double_bottom.DoubleBottomBridge
import ua.itaysonlab.catogram.preferences.ktx.category
import ua.itaysonlab.catogram.preferences.ktx.textDetail
import ua.itaysonlab.catogram.preferences.ktx.textIcon
import ua.itaysonlab.catogram.preferences.ktx.tgKitScreen
import ua.itaysonlab.extras.CatogramExtras
import ua.itaysonlab.tgkit.preference.types.TGKitTextIconRow

import android.os.Build

import android.app.assist.AssistContent
import org.telegram.messenger.R
import java.lang.String


class MainPreferencesEntry : BasePreferencesEntry {
    override fun getPreferences(bf: BaseFragment) = tgKitScreen(LocaleController.getString("AdvancedSettings", R.string.AdvancedSettings)) {
        category(LocaleController.getString("AS_Header_Categories", R.string.AS_Header_Categories)) {
            textIcon {
                title = LocaleController.getString("AS_Header_Appearance", R.string.AS_Header_Appearance)
                icon = R.drawable.msg_theme
                listener = TGKitTextIconRow.TGTIListener {
                    it.presentFragment(CatogramPreferencesNavigator.createAppearance())
                }
            }

            textIcon {
                title = LocaleController.getString("AS_Header_Chats", R.string.AS_Header_Chats)
                icon = R.drawable.menu_chats
                listener = TGKitTextIconRow.TGTIListener {
                    it.presentFragment(CatogramPreferencesNavigator.createChats())
                }
            }

            if (DoubleBottomBridge.isDbConfigAvailable()) {
                textIcon {
                    title = LocaleController.getString("AS_Header_DoubleBottom", R.string.AS_Header_DoubleBottom)
                    icon = R.drawable.menu_secret
                    listener = TGKitTextIconRow.TGTIListener {
                        it.presentFragment(CatogramPreferencesNavigator.createDB())
                    }
                }
            }

            textIcon {
                title = LocaleController.getString("AS_Category_Security", R.string.AS_Category_Security)
                icon = R.drawable.menu_secret
                listener = TGKitTextIconRow.TGTIListener {
                    it.presentFragment(CatogramPreferencesNavigator.createSecurity())
                }
            }
            textIcon {
                title = LocaleController.getString("CG_Updates_Category", R.string.CG_Updates_Category)
                icon = R.drawable.outline_send
                listener = TGKitTextIconRow.TGTIListener {
                    it.presentFragment(CatogramPreferencesNavigator.createUpdates())
                }
            }
            textIcon {
                title = LocaleController.getString("CG_Donate", R.string.CG_Donate)
                icon = R.drawable.money_circle_outline_28
                listener = TGKitTextIconRow.TGTIListener {
                    it.presentFragment(CatogramPreferencesNavigator.createDonate())
                }
            }

            category(LocaleController.getString("AS_Header_About", R.string.AS_Header_About)) {
                textDetail {
                    title = "Catogram " + CatogramExtras.CG_VERSION + " [" + BuildVars.BUILD_VERSION_STRING + "]"
                    detail = LocaleController.getString("CG_AboutDesc", R.string.CG_AboutDesc)
                }

                textIcon {
                    title = LocaleController.getString("CG_ToChannel", R.string.CG_ToChannel)
                    value = "@catogram"
                    listener = TGKitTextIconRow.TGTIListener {
                        goToChannel(it)
                    }
                }
                textIcon {
                    title = LocaleController.getString("CG_ToChat", R.string.CG_ToChat)
                    value = "@catogram_en"
                    listener = TGKitTextIconRow.TGTIListener {
                        goToChat(it)
                    }
                }
                textIcon {
                    title = LocaleController.getString("CG_Source", R.string.CG_Source)
                    value = "Github"
                    listener = TGKitTextIconRow.TGTIListener {
                        goToGithub(it)
                    }
                }
                textIcon {
                    title = LocaleController.getString("CG_Crowdin", R.string.CG_Crowdin)
                    value = "Crowdin"
                    listener = TGKitTextIconRow.TGTIListener {
                        goToCrowdin(it)
                    }
                }
            }
        }
    }

    companion object {
        private fun goToChannel(bf: BaseFragment) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/catogram"))
            val componentName = ComponentName(bf.parentActivity.packageName, LaunchActivity::class.java.name)
            intent.component = componentName
            intent.putExtra(Browser.EXTRA_CREATE_NEW_TAB, true)
            intent.putExtra(Browser.EXTRA_APPLICATION_ID, bf.parentActivity.packageName)
            bf.parentActivity.startActivity(intent)
        }

        private fun goToChat(bf: BaseFragment) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/catogram_en"))
            val componentName = ComponentName(bf.parentActivity.packageName, LaunchActivity::class.java.name)
            intent.component = componentName
            intent.putExtra(Browser.EXTRA_CREATE_NEW_TAB, true)
            intent.putExtra(Browser.EXTRA_APPLICATION_ID, bf.parentActivity.packageName)
            bf.parentActivity.startActivity(intent)
        }

        private fun goToCrowdin(bf: BaseFragment) {
            val openURL = Intent(android.content.Intent.ACTION_VIEW)
            openURL.data = Uri.parse("https://crowdin.com/project/catogram")
            bf.parentActivity.startActivity(openURL)
        }

        private fun goToGithub(bf: BaseFragment) {
            val openURL = Intent(android.content.Intent.ACTION_VIEW)
            openURL.data = Uri.parse("https://github.com/Catogram/Catogram")
            bf.parentActivity.startActivity(openURL)
        }
        fun onProvideAssistContent(outContent: AssistContent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                outContent.webUri = Uri.parse(
                    String.format(
                        "https://t.me/catogram"
                    )
                )
            }
        }

    }
}