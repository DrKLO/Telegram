package ua.itaysonlab.catogram.preferences

import android.content.Intent
import android.graphics.Color
import androidx.core.app.ActivityCompat.startActivityForResult
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.messenger.SharedConfig
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.LaunchActivity
import ua.itaysonlab.catogram.CatogramConfig
import ua.itaysonlab.catogram.preferences.ktx.*
import ua.itaysonlab.extras.CatogramExtras
import ua.itaysonlab.extras.IconExtras
import ua.itaysonlab.tgkit.preference.types.TGKitTextIconRow


class AppearancePreferencesEntry : BasePreferencesEntry {
    override fun getPreferences(bf: BaseFragment) = tgKitScreen(LocaleController.getString("AS_Header_Appearance", R.string.AS_Header_Appearance)) {
        category(LocaleController.getString("AS_RedesignCategory", R.string.AS_RedesignCategory)) {
            switch {
                title = LocaleController.getString("CG_TelegramThemes", R.string.CG_TelegramThemes)
                summary = LocaleController.getString("CG_TelegramThemes_Desc", R.string.CG_TelegramThemes_Desc)

                contract({
                    return@contract CatogramConfig.redesign_TelegramThemes
                }) {
                    CatogramConfig.redesign_TelegramThemes = it
                }
            }

            list {
                title = LocaleController.getString("AS_ChangeIcon", R.string.AS_ChangeIcon)

                contractIcons({
                    return@contractIcons listOf(
                            Triple(0, LocaleController.getString("AS_ChangeIcon_Old", R.string.AS_ChangeIcon_Old), R.mipmap.cg_launcher),
                            Triple(1, LocaleController.getString("AS_ChangeIcon_AltBlue", R.string.AS_ChangeIcon_AltBlue), R.mipmap.cg_launcher_alt_blue),
                            Triple(2, LocaleController.getString("AS_ChangeIcon_AltOrange", R.string.AS_ChangeIcon_AltOrange), R.mipmap.cg_launcher_alt_orange),
                            Triple(3, LocaleController.getString("CX_ChangeIcon_Black_Reg", R.string.CX_ChangeIcon_Black_Reg), R.mipmap.cx_launcher_black_reg),
                            Triple(4, LocaleController.getString("CX_ChangeIcon_Blue_Reg", R.string.CX_ChangeIcon_Blue_Reg), R.mipmap.cx_launcher_blue_reg),
                            Triple(5, LocaleController.getString("CX_ChangeIcon_Cyan_Reg", R.string.CX_ChangeIcon_Cyan_Reg), R.mipmap.cx_launcher_cyan_reg),
                            Triple(6, LocaleController.getString("CX_ChangeIcon_Green_Reg", R.string.CX_ChangeIcon_Green_Reg), R.mipmap.cx_launcher_green_reg),
                            Triple(7, LocaleController.getString("CX_ChangeIcon_Orange_Reg", R.string.CX_ChangeIcon_Orange_Reg), R.mipmap.cx_launcher_orange_reg),
                            Triple(8, LocaleController.getString("CX_ChangeIcon_Pink_Reg", R.string.CX_ChangeIcon_Pink_Reg), R.mipmap.cx_launcher_pink_reg),
                            Triple(9, LocaleController.getString("CX_ChangeIcon_Purple_Reg", R.string.CX_ChangeIcon_Purple_Reg), R.mipmap.cx_launcher_purple_reg),
                            Triple(10, LocaleController.getString("CX_ChangeIcon_Red_Reg", R.string.CX_ChangeIcon_Red_Reg), R.mipmap.cx_launcher_red_reg),
                            Triple(11, LocaleController.getString("CX_ChangeIcon_Taffy_Reg", R.string.CX_ChangeIcon_Taffy_Reg), R.mipmap.cx_launcher_taffy_reg),
                            Triple(12, LocaleController.getString("CX_ChangeIcon_Yellow_Reg", R.string.CX_ChangeIcon_Yellow_Reg), R.mipmap.cx_launcher_yellow_reg),
                            Triple(13, LocaleController.getString("CX_ChangeIcon_Monet", R.string.CX_ChangeIcon_Monet), R.mipmap.cx_launcher_monet)
                    )
                }, {
                    return@contractIcons when (CatogramConfig.redesign_iconOption) {
                        0 -> LocaleController.getString("AS_ChangeIcon_Old", R.string.AS_ChangeIcon_Old)
                        1 -> LocaleController.getString("AS_ChangeIcon_AltBlue", R.string.AS_ChangeIcon_AltBlue)
                        2 -> LocaleController.getString("AS_ChangeIcon_AltOrange", R.string.AS_ChangeIcon_AltOrange)
                        3 -> LocaleController.getString("CX_ChangeIcon_Black_Reg", R.string.CX_ChangeIcon_Black_Reg)
                        4 -> LocaleController.getString("CX_ChangeIcon_Blue_Reg", R.string.CX_ChangeIcon_Blue_Reg)
                        5 -> LocaleController.getString("CX_ChangeIcon_Cyan_Reg", R.string.CX_ChangeIcon_Cyan_Reg)
                        6 -> LocaleController.getString("CX_ChangeIcon_Green_Reg", R.string.CX_ChangeIcon_Green_Reg)
                        7 -> LocaleController.getString("CX_ChangeIcon_Orange_Reg", R.string.CX_ChangeIcon_Orange_Reg)
                        8 -> LocaleController.getString("CX_ChangeIcon_Pink_Reg", R.string.CX_ChangeIcon_Pink_Reg)
                        9 -> LocaleController.getString("CX_ChangeIcon_Purple_Reg", R.string.CX_ChangeIcon_Purple_Reg)
                        10 -> LocaleController.getString("CX_ChangeIcon_Red_Reg", R.string.CX_ChangeIcon_Red_Reg)
                        11 -> LocaleController.getString("CX_ChangeIcon_Taffy_Reg", R.string.CX_ChangeIcon_Taffy_Reg)
                        12 -> LocaleController.getString("CX_ChangeIcon_Yellow_Reg", R.string.CX_ChangeIcon_Yellow_Reg)
                        13 -> LocaleController.getString("CX_ChangeIcon_Monet", R.string.CX_ChangeIcon_Monet)
                        else -> LocaleController.getString("CX_ChangeIcon_Black_Reg", R.string.CX_ChangeIcon_Black_Reg)
                    }
                }) {
                    CatogramConfig.redesign_iconOption = it
                    IconExtras.setIcon(it)
                }
            }

            list {
                title = LocaleController.getString("CG_IconReplacements", R.string.CG_IconReplacements)

                contractIcons({
                    return@contractIcons listOf(
                            Triple(0, LocaleController.getString("CG_IconReplacement_VKUI", R.string.CG_IconReplacement_VKUI), R.drawable.settings_outline_28),
                            Triple(1, LocaleController.getString("CG_IconReplacement_Default", R.string.CG_IconReplacement_Default), R.drawable.menu_settings)
                    )
                }, {
                    return@contractIcons when (CatogramConfig.iconReplacement) {
                        1 -> LocaleController.getString("CG_IconReplacement_Default", R.string.CG_IconReplacement_Default)
                        else -> LocaleController.getString("CG_IconReplacement_VKUI", R.string.CG_IconReplacement_VKUI)
                    }
                }) {
                    CatogramConfig.iconReplacement = it
                    (bf.parentActivity as? LaunchActivity)?.reloadResources()
                }
            }
        }

        category(LocaleController.getString("General", R.string.General)) {
            switch {
                title = LocaleController.getString("AS_HideUserPhone", R.string.AS_HideUserPhone)
                summary = LocaleController.getString("AS_HideUserPhoneSummary", R.string.AS_HideUserPhoneSummary)

                contract({
                    return@contract CatogramConfig.hidePhoneNumber
                }) {
                    CatogramConfig.hidePhoneNumber = it
                }
            }

            switch {
                title = LocaleController.getString("AS_NoRounding", R.string.AS_NoRounding)
                summary = LocaleController.getString("AS_NoRoundingSummary", R.string.AS_NoRoundingSummary)

                contract({
                    return@contract CatogramConfig.noRounding
                }) {
                    CatogramConfig.noRounding = it
                }
            }

            switch {
                title = LocaleController.getString("AS_Vibration", R.string.AS_Vibration)

                contract({
                    return@contract CatogramConfig.noVibration
                }) {
                    CatogramConfig.noVibration = it
                }
            }

            switch {
                title = LocaleController.getString("AS_FlatSB", R.string.AS_FlatSB)
                summary = LocaleController.getString("AS_FlatSB_Desc", R.string.AS_FlatSB_Desc)

                contract({
                    return@contract SharedConfig.noStatusBar
                }) {
                    SharedConfig.toggleNoStatusBar()
                    bf.parentActivity.window.statusBarColor = if (Theme.getColor(Theme.key_actionBarDefault, null, true) == Color.WHITE) CatogramExtras.lightStatusbarColor else CatogramExtras.darkStatusbarColor
                }
            }

            switch {
                title = LocaleController.getString("AS_FlatAB", R.string.AS_FlatAB)

                contract({
                    return@contract CatogramConfig.flatActionbar
                }) {
                    CatogramConfig.flatActionbar = it
                }
            }

            switch {
                title = LocaleController.getString("CG_ConfirmCalls", R.string.CG_ConfirmCalls)

                contract({
                    return@contract CatogramConfig.confirmCalls
                }) {
                    CatogramConfig.confirmCalls = it
                }
            }

            switch {
                title = LocaleController.getString("AS_SysEmoji", R.string.AS_SysEmoji)
                summary = LocaleController.getString("AS_SysEmojiDesc", R.string.AS_SysEmojiDesc)

                contract({
                    return@contract SharedConfig.useSystemEmoji
                }) {
                    SharedConfig.toggleSystemEmoji()
                }
            }

            switch {
                title = LocaleController.getString("CX_CustomEmoji", R.string.CX_CustomEmoji)
                summary = LocaleController.getString("CX_CustomEmojiDesc", R.string.CX_CustomEmojiDesc)

                contract({
                    return@contract CatogramConfig.customEmojiFont
                }) {
                    CatogramConfig.customEmojiFont = it
                }
            }

            textIcon {
                title = LocaleController.getString("CX_SetCustomEmojiFont", R.string.CX_SetCustomEmojiFont)
                listener = TGKitTextIconRow.TGTIListener {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                    intent.addCategory(Intent.CATEGORY_OPENABLE)
                    intent.type = "*/*"
                    startActivityForResult(bf.parentActivity, intent, 36654, null)
                }
            }

            switch {
                title = LocaleController.getString("CX_HideSendAsChannel", R.string.CX_HideSendAsChannel)
                summary = LocaleController.getString("CX_HideSendAsChannelDesc", R.string.CX_HideSendAsChannelDesc)

                contract({
                    return@contract CatogramConfig.hideSendAsChannel
                }) {
                    CatogramConfig.hideSendAsChannel = it
                }
            }

            switch {
                title = LocaleController.getString("CX_DisableReactionAnim", R.string.CX_DisableReactionAnim)
                summary = LocaleController.getString("CX_DisableReactionAnimDesc", R.string.CX_DisableReactionAnimDesc)

                contract({
                    return@contract CatogramConfig.disableReactionAnim
                }) {
                    CatogramConfig.disableReactionAnim = it
                }
            }
            
	    /*switch {
                title = LocaleController.getString("CX_SystemFonts", R.string.CX_SystemFonts)
                summary = LocaleController.getString("CX_SystemFontsDesc", R.string.CX_SystemFontsDesc)

                contract({
                    return@contract CatogramConfig.systemFonts
                }) {
                    CatogramConfig.systemFonts = it
                }
            }
        }*/

        category(LocaleController.getString("AS_Header_Notification", R.string.AS_Header_Notification)) {

            switch {
                title = LocaleController.getString("CG_OldNotification", R.string.CG_OldNotification)

                contract({
                    return@contract CatogramConfig.oldNotificationIcon
                }) {
                    CatogramConfig.oldNotificationIcon = it
                }
            }
        }

        category(LocaleController.getString("AS_DrawerCategory", R.string.AS_DrawerCategory)) {

            switch {
                title = LocaleController.getString("AS_DrawerAvatar", R.string.AS_DrawerAvatar)

                contract({
                    return@contract CatogramConfig.drawerAvatar
                }) {
                    CatogramConfig.drawerAvatar = it
                }
            }

            switch {
                title = LocaleController.getString("AS_DrawerBlur", R.string.AS_DrawerBlur)

                contract({
                    return@contract CatogramConfig.drawerBlur
                }) {
                    CatogramConfig.drawerBlur = it
                }
            }

            switch {
                title = LocaleController.getString("AS_DrawerDarken", R.string.AS_DrawerDarken)

                contract({
                    return@contract CatogramConfig.drawerDarken
                }) {
                    CatogramConfig.drawerDarken = it
                }
            }
        }
    }
}
