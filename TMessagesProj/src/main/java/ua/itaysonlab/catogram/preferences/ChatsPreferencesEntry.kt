package ua.itaysonlab.catogram.preferences

import androidx.core.util.Pair
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import ua.itaysonlab.catogram.CGFeatureHooks
import ua.itaysonlab.catogram.CatogramConfig
import ua.itaysonlab.catogram.preferences.ktx.*
import ua.itaysonlab.tgkit.preference.types.TGKitSliderPreference.TGSLContract

class ChatsPreferencesEntry : BasePreferencesEntry {
    override fun getPreferences(bf: BaseFragment) = tgKitScreen(LocaleController.getString("AS_Header_Chats", R.string.AS_Header_Chats)) {
        category(LocaleController.getString("CG_Slider_StickerAmplifier", R.string.CG_Slider_StickerAmplifier)) {
            slider {
                contract = object : TGSLContract {
                    override fun setValue(value: Int) {
                        CatogramConfig.slider_stickerAmplifier = value
                    }

                    override fun getPreferenceValue(): Int {
                        return CatogramConfig.slider_stickerAmplifier
                    }

                    override fun getMin(): Int {
                        return 50
                    }

                    override fun getMax(): Int {
                        return 100
                    }
                }
            }
            switch {
                title = LocaleController.getString("CG_TimeOnStick", R.string.CG_TimeOnStick)

                contract({
                    return@contract CatogramConfig.hideStickerTime
                }) {
                    CatogramConfig.hideStickerTime = it
                }
            }
        }

        category(LocaleController.getString("AS_Header_Record", R.string.AS_Header_Record)) {
            switch {
                title = LocaleController.getString("CG_StreamVoip", R.string.CG_StreamVoip)
                summary = LocaleController.getString("CG_StreamVoip_Desc", R.string.CG_StreamVoip_Desc)

                contract({
                    return@contract CatogramConfig.useMediaStream
                }) {
                    CatogramConfig.useMediaStream = it
                }
            }

            switch {
                title = LocaleController.getString("CG_PlayVideo", R.string.CG_PlayVideo)
                summary = LocaleController.getString("CG_PlayVideo_Desc", R.string.CG_PlayVideo_Desc)

                contract({
                    return@contract CatogramConfig.playVideoOnVolume
                }) {
                    CatogramConfig.playVideoOnVolume = it
                }
            }

            switch {
                title = LocaleController.getString("CG_RearCam", R.string.CG_RearCam)
                summary = LocaleController.getString("CG_RearCam_Desc", R.string.CG_RearCam_Desc)

                contract({
                    return@contract CatogramConfig.rearCam
                }) {
                    CatogramConfig.rearCam = it
                }
            }

            switch {
                title = LocaleController.getString("CG_DisableCam", R.string.CG_DisableCam)
                summary = LocaleController.getString("CG_DisableCam_Desc", R.string.CG_DisableCam_Desc)

                contract({
                    return@contract CatogramConfig.disableAttachCamera
                }) {
                    CatogramConfig.disableAttachCamera = it
                }
            }

            switch {
                title = LocaleController.getString("CG_AudioFocus", R.string.CG_AudioFocus)
                summary = LocaleController.getString("CG_AudioFocus_Desc", R.string.CG_AudioFocus_Desc)

                contract({
                    return@contract CatogramConfig.audioFocus
                }) {
                    CatogramConfig.audioFocus = it
                }
            }

            switch {
                title = LocaleController.getString("CG_Proximity", R.string.CG_Proximity)
                summary = LocaleController.getString("CG_Proximity_Desc", R.string.CG_Proximity_Desc)

                contract({
                    return@contract CatogramConfig.enableProximity
                }) {
                    CatogramConfig.enableProximity = it
                }
            }

            list {
                title = LocaleController.getString("CG_Sleep_Time", R.string.CG_Sleep_Time)

                contract({
                    return@contract listOf(
                        Pair(0, "5"),
                        Pair(1, "10"),
                        Pair(2, "30"),
                        Pair(3, "60"),
                    )
                }, {
                    return@contract when (CatogramConfig.sleepOp) {
                        1 -> "10"
                        2 -> "30"
                        3 -> "60"
                        else -> "5"
                    }
                }) {
                    CatogramConfig.sleepOp = it

                    when (CatogramConfig.sleepOp) {
                        0 -> {
                            CatogramConfig.sleepTime = 5
                        }
                        1 -> {
                            CatogramConfig.sleepTime = 10
                        }
                        2 -> {
                            CatogramConfig.sleepTime = 30
                        }
                        3 -> {
                            CatogramConfig.sleepTime = 60
                        }
                    }
                }
            }

            switch {
                title = LocaleController.getString("CG_VoiceEnhancements", R.string.CG_VoiceEnhancements)
                summary = LocaleController.getString("CG_VoiceEnhancements_Desc", R.string.CG_VoiceEnhancements_Desc)

                contract({
                    return@contract CatogramConfig.voicesAgc
                }) {
                    CatogramConfig.voicesAgc = it
                }
            }

            switch {
                title = LocaleController.getString("CG_Voip_ForceEnhancements", R.string.CG_Voip_ForceEnhancements)
                summary = LocaleController.getString("CG_Voip_ForceEnhancements", R.string.CG_Voip_ForceEnhancements_Desc)

                contract({
                    return@contract CatogramConfig.voicesAgc
                }) {
                    CatogramConfig.voicesAgc = it
                }
            }

            switch {
                title = LocaleController.getString("CG_hq_voice", R.string.CG_hq_voice)
                summary = LocaleController.getString("CG_relaunch", R.string.CG_relaunch)

                contract({
                    return@contract CatogramConfig.hqVoice
                }) {
                    CatogramConfig.hqVoice = it
                }
            }
        }

        category(LocaleController.getString("AS_Header_Notification", R.string.AS_Header_Notification)) {

            switch {
                title = LocaleController.getString("CG_SilenceNonContacts", R.string.CG_SilenceNonContacts)
                summary = LocaleController.getString("CG_SilenceNonContacts_desc", R.string.CG_SilenceNonContacts_desc)


                contract({
                    return@contract CatogramConfig.silenceNonContacts
                }) {
                    CatogramConfig.silenceNonContacts = it
                }
            }
        }

        category(LocaleController.getString("AS_Filters_Header", R.string.AS_Filters_Header)) {
            switch {
                title = LocaleController.getString("CG_NewTabs_NoCounter", R.string.CG_NewTabs_NoCounter)

                contract({
                    return@contract CatogramConfig.newTabs_noUnread
                }) {
                    CatogramConfig.newTabs_noUnread = it
                }
            }

            list {
                title = LocaleController.getString("CG_TabIconMode_Title", R.string.CG_TabIconMode_Title)

                contract({
                    return@contract listOf(
                            Pair(0, LocaleController.getString("CG_TabIconMode_Disabled", R.string.CG_TabIconMode_Disabled)),
                            Pair(1, LocaleController.getString("CG_TabIconMode_Append", R.string.CG_TabIconMode_Append)),
                            Pair(2, LocaleController.getString("CG_TabIconMode_Replace", R.string.CG_TabIconMode_Replace))
                    )
                }, {
                    return@contract when (CatogramConfig.newTabs_iconsV2_mode) {
                        1 -> LocaleController.getString("CG_TabIconMode_Append", R.string.CG_TabIconMode_Append)
                        2 -> LocaleController.getString("CG_TabIconMode_Replace", R.string.CG_TabIconMode_Replace)
                        else -> LocaleController.getString("CG_TabIconMode_Disabled", R.string.CG_TabIconMode_Disabled)
                    }
                }) {
                    CatogramConfig.newTabs_iconsV2_mode = it
                    bf.messagesController.loadRemoteFilters(true)
                }
            }

            switch {
                title = LocaleController.getString("CG_NewTabs_RemoveAllChats", R.string.CG_NewTabs_RemoveAllChats)

                contract({
                    return@contract CatogramConfig.newTabs_hideAllChats
                }) {
                    CatogramConfig.newTabs_hideAllChats = it
                }
            }
            switch {
                title = LocaleController.getString("CG_IgnoreArchivedChats", R.string.CG_IgnoreArchivedChats)
                summary = LocaleController.getString("CG_IgnoreArchivedChats_desc", R.string.CG_IgnoreArchivedChats_desc)

                contract({
                    return@contract CatogramConfig.ignoreArchivedChannels
                }) {
                    CatogramConfig.ignoreArchivedChannels = it
                }
            }
        }

        category(LocaleController.getString("AS_Header_Chats", R.string.AS_Header_Chats)) {

            switch {
                title = LocaleController.getString("CG_MentionByName", R.string.CG_MentionByName)
                summary = LocaleController.getString("CG_MentionByName_Desc", R.string.CG_MentionByName_Desc)
                divider = true

                contract({
                    return@contract CatogramConfig.mentionByName
                }) {
                    CatogramConfig.mentionByName = it
                }
            }

            switch {
                title = LocaleController.getString("CG_archiveOnPull", R.string.CG_ArchiveOnPull)
                summary = LocaleController.getString("CG_archiveOnPull_Desc", R.string.CG_ArchiveOnPull_Desc)

                contract({
                    return@contract CatogramConfig.archiveOnPull
                }) {
                    CatogramConfig.archiveOnPull = it
                }
            }

            switch {
                title = LocaleController.getString("CG_syncPins", R.string.CG_SyncPins)
                summary = LocaleController.getString("CG_syncPins_Desc", R.string.CG_SyncPins_Desc)

                contract({
                    return@contract CatogramConfig.syncPins
                }) {
                    CatogramConfig.syncPins = it
                }
            }

            switch {
                title = LocaleController.getString("CG_HideKbdOnScroll", R.string.CG_HideKbdOnScroll)

                contract({
                    return@contract CatogramConfig.hideKeyboardOnScroll
                }) {
                    CatogramConfig.hideKeyboardOnScroll = it
                }
            }

            switch {
                title = LocaleController.getString("CG_ForwardMsgDate", R.string.CG_ForwardMsgDate)

                contract({
                    return@contract CatogramConfig.msgForwardDate
                }) {
                    CatogramConfig.msgForwardDate = it
                }
            }

            /*switch {
                title = LocaleController.getString("CG_GhostMode", R.string.CG_GhostMode)
                summary = LocaleController.getString("CG_GhostMode_Desc", R.string.CG_GhostMode_Desc)

                contract({
                    return@contract CatogramConfig.ghostMode
                }) {
                    CatogramConfig.ghostMode = it
                }
            }*/

            list {
                title = LocaleController.getString("CG_MsgSlideAction", R.string.CG_MsgSlideAction)

                contract({
                    return@contract listOf(
                            Pair(0, LocaleController.getString("CG_MsgSlideAction_Reply", R.string.CG_MsgSlideAction_Reply)),
                            Pair(1, LocaleController.getString("CG_MsgSlideAction_Save", R.string.CG_MsgSlideAction_Save)),
                            Pair(2, LocaleController.getString("CG_MsgSlideAction_Share", R.string.CG_MsgSlideAction_Share))
                    )
                }, {
                    return@contract when (CatogramConfig.messageSlideAction) {
                        1 -> LocaleController.getString("CG_MsgSlideAction_Save", R.string.CG_MsgSlideAction_Save)
                        2 -> LocaleController.getString("CG_MsgSlideAction_Share", R.string.CG_MsgSlideAction_Share)
                        else -> LocaleController.getString("CG_MsgSlideAction_Reply", R.string.CG_MsgSlideAction_Reply)
                    }
                }) {
                    CatogramConfig.messageSlideAction = it
                    Theme.chat_replyIconDrawable = bf.parentActivity.resources.getDrawable(CGFeatureHooks.getReplyIconDrawable())
                    Theme.setDrawableColorByKey(Theme.chat_replyIconDrawable, Theme.key_chat_serviceIcon)
                }
            }
        }

        category(LocaleController.getString("CG_Header_Profile", R.string.CG_Header_Profile)) {
            switch {
                title = LocaleController.getString("CG_NewAvaHeader_NoEdgeTapping", R.string.CG_NewAvaHeader_NoEdgeTapping)
                summary = LocaleController.getString("CG_NewAvaHeader_NoEdgeTapping_Desc", R.string.CG_NewAvaHeader_NoEdgeTapping_Desc)

                contract({
                    return@contract CatogramConfig.profiles_noEdgeTapping
                }) {
                    CatogramConfig.profiles_noEdgeTapping = it
                }
            }

            switch {
                title = LocaleController.getString("CG_EnableSwipePIP", R.string.CG_EnableSwipePIP)
                summary = LocaleController.getString("CG_EnableSwipePIP_Desc", R.string.CG_EnableSwipePIP_Desc)

                contract({
                    return@contract CatogramConfig.enableSwipeToPIP
                }) {
                    CatogramConfig.enableSwipeToPIP = it
                }
            }

            switch {
                title = LocaleController.getString("CG_ShowDC", R.string.CG_ShowDC)
                contract({
                    return@contract CatogramConfig.showDc
                }) {
                    CatogramConfig.showDc = it
                }
            }
            switch {
                title = LocaleController.getString("CG_NewAvaHeader_OpenOnTap", R.string.CG_NewAvaHeader_OpenOnTap)
                summary = LocaleController.getString("CG_NewAvaHeader_OpenOnTap_Desc", R.string.CG_NewAvaHeader_OpenOnTap_Desc)

                contract({
                    return@contract CatogramConfig.profiles_openOnTap
                }) {
                    CatogramConfig.profiles_openOnTap = it
                }
            }

            switch {
                title = LocaleController.getString("CG_NewAvaHeader_AlwaysExpand", R.string.CG_NewAvaHeader_AlwaysExpand)
                summary = LocaleController.getString("CG_NewAvaHeader_AlwaysExpand_Desc", R.string.CG_NewAvaHeader_AlwaysExpand_Desc)

                contract({
                    return@contract CatogramConfig.profiles_alwaysExpand
                }) {
                    CatogramConfig.profiles_alwaysExpand = it
                }
            }
        }
        category(LocaleController.getString("CG_Tr", R.string.CG_Tr)) {
            list {
                title = LocaleController.getString("CG_TrLang", R.string.CG_TrLang)

                contract({
                    return@contract listOf(
                            Pair(0, LocaleController.getString("CG_TrLang_English", R.string.CG_TrLang_English)),
                            Pair(1, LocaleController.getString("CG_TrLang_Russian", R.string.CG_TrLang_Russian)),
                            Pair(2, LocaleController.getString("CG_TrLang_French", R.string.CG_TrLang_French)),
                            Pair(3, LocaleController.getString("CG_TrLang_Italian", R.string.CG_TrLang_Italian)),
                            Pair(4, LocaleController.getString("CG_TrLang_Spanish", R.string.CG_TrLang_Spanish)),
                            Pair(5, LocaleController.getString("CG_TrLang_Chinese", R.string.CG_TrLang_Chinese)),
                            Pair(6, LocaleController.getString("CG_TrLang_Japanese", R.string.CG_TrLang_Japanese)),
                            Pair(7, LocaleController.getString("CG_TrLang_Hindi", R.string.CG_TrLang_Hindi)),
                            Pair(8, LocaleController.getString("CG_TrLang_German", R.string.CG_TrLang_German)),
                            Pair(9, LocaleController.getString("CG_TrLang_Indonesian", R.string.CG_TrLang_Indonesian)),
                            Pair(10, LocaleController.getString("CG_TrLang_Bosnian", R.string.CG_TrLang_Bosnian)),
                            Pair(11, LocaleController.getString("CG_TrLang_Croatian", R.string.CG_TrLang_Croatian)),
                            Pair(12, LocaleController.getString("CG_TrLang_Serbian", R.string.CG_TrLang_Serbian)),
                    )
                }, {
                    return@contract when (CatogramConfig.translateOptions) {
                        1 -> LocaleController.getString("CG_TrLang_Russian", R.string.CG_TrLang_Russian)
                        2 -> LocaleController.getString("CG_TrLang_French", R.string.CG_TrLang_French)
                        3 -> LocaleController.getString("CG_TrLang_Italian", R.string.CG_TrLang_Italian)
                        4 -> LocaleController.getString("CG_TrLang_Spanish", R.string.CG_TrLang_Spanish)
                        5 -> LocaleController.getString("CG_TrLang_Chinese", R.string.CG_TrLang_Chinese)
                        6 -> LocaleController.getString("CG_TrLang_Japanese", R.string.CG_TrLang_Japanese)
                        7 -> LocaleController.getString("CG_TrLang_Hindi", R.string.CG_TrLang_Hindi)
                        8 -> LocaleController.getString("CG_TrLang_German", R.string.CG_TrLang_German)
                        9 -> LocaleController.getString("CG_TrLang_Indonesian", R.string.CG_TrLang_Indonesian)
                        10 -> LocaleController.getString("CG_TrLang_Bosnian", R.string.CG_TrLang_Bosnian)
                        11 -> LocaleController.getString("CG_TrLang_Croatian", R.string.CG_TrLang_Croatian)
                        12 -> LocaleController.getString("CG_TrLang_Serbian", R.string.CG_TrLang_Serbian)
                        else -> LocaleController.getString("CG_TrLang_English", R.string.CG_TrLang_English)
                    }
                }) {
                    CatogramConfig.translateOptions = it
                    when (CatogramConfig.translateOptions) {
                        0 -> {
                            CatogramConfig.trLang = "en"
                        }
                        1 -> {
                            CatogramConfig.trLang = "ru"
                        }
                        2 -> {
                            CatogramConfig.trLang = "fr"
                        }
                        3 -> {
                            CatogramConfig.trLang = "it"
                        }
                        4 -> {
                            CatogramConfig.trLang = "es"
                        }
                        5 -> {
                            CatogramConfig.trLang = "zh"
                        }
                        6 -> {
                            CatogramConfig.trLang = "ja"
                        }
                        7 -> {
                            CatogramConfig.trLang = "hi"
                        }
                        8 -> {
                            CatogramConfig.trLang = "de"
                        }
                        9 -> {
                            CatogramConfig.trLang = "id"
                        }
                        10 -> {
                            CatogramConfig.trLang = "bs"
                        }
                        11 -> {
                            CatogramConfig.trLang = "hr"
                        }
                        12 -> {
                            CatogramConfig.trLang = "sr"
                        }
                    }
                }
            }
        }
    }
}
