package ua.itaysonlab.catogram

import android.app.Activity
import android.content.SharedPreferences
import android.content.pm.PackageManager
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.MessagesController
import ua.itaysonlab.catogram.preferences.ktx.boolean
import ua.itaysonlab.catogram.preferences.ktx.int
import ua.itaysonlab.catogram.preferences.ktx.string
import ua.itaysonlab.catogram.ui.CatogramToasts
import ua.itaysonlab.catogram.vkui.icon_replaces.BaseIconReplace
import ua.itaysonlab.catogram.vkui.icon_replaces.NoIconReplace
import ua.itaysonlab.catogram.vkui.icon_replaces.VkIconReplace
import kotlin.system.exitProcess

object CatogramConfig {

    private val sharedPreferences: SharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE)
    var trLang by sharedPreferences.string("cg_tr_lang", "en")
    var hideProxySponsor by sharedPreferences.boolean("advanced_hideproxysponsor", true)
    var hidePhoneNumber by sharedPreferences.boolean("advanced_hidephonenumber", true)
    var noRounding by sharedPreferences.boolean("advanced_norounding", false)
    var systemFontsTT by sharedPreferences.boolean("advanced_systemfonts_tt", false)
    var noVibration by sharedPreferences.boolean("advanced_novibration", false)
    var drawerAvatar by sharedPreferences.boolean("cg_drawer_avatar", false)
    var flatActionbar by sharedPreferences.boolean("cg_flat_actionbar", false)
    var drawerBlur by sharedPreferences.boolean("cg_drawer_blur", false)
    var drawerDarken by sharedPreferences.boolean("cg_drawer_darken", false)
    var controversiveNoSecureFlag by sharedPreferences.boolean("cg_ct_flag", false)
    var contactsNever by sharedPreferences.boolean("contactsNever", false)
    var searchInActionbar by sharedPreferences.boolean("cg_chats_searchbar", false)
    var confirmCalls by sharedPreferences.boolean("cg_confirmcalls", false)
    var profiles_noEdgeTapping by sharedPreferences.boolean("cg_prof_edge", false)
    var profiles_openOnTap by sharedPreferences.boolean("cg_prof_open", false)
    var profiles_alwaysExpand by sharedPreferences.boolean("cg_prof_expand", false)
    var hideKeyboardOnScroll by sharedPreferences.boolean("cg_hidekbd", false)
    var newTabs_noUnread by sharedPreferences.boolean("cg_notabnum", false)
    var newTabs_hideAllChats by sharedPreferences.boolean("cg_ntallchats", false)

    var redesign_messageOption by sharedPreferences.int("cg_messageOption", 3)
    var translateOptions by sharedPreferences.int("cg_trOptions", 0)
    var redesign_forceDrawerIconsOption by sharedPreferences.int("cg_drawerIconsOption", 0)
    var redesign_iconOption by sharedPreferences.int("cg_iconoption", 0)
    var redesign_TelegramThemes by sharedPreferences.boolean("cg_redesign_telegramthemes", false)

    var slider_stickerAmplifier by sharedPreferences.int("cg_stickamplifier", 100)
    var archiveOnPull by sharedPreferences.boolean("cg_archonpull", true)
    var syncPins by sharedPreferences.boolean("cg_syncpins", true)
    var rearCam by sharedPreferences.boolean("cg_rearcam", false)
    var audioFocus by sharedPreferences.boolean("cg_audiofocus", false)
    var enableProximity by sharedPreferences.boolean("cg_enableproximity", true)

    var forwardNoAuthorship by sharedPreferences.boolean("cg_forward_no_authorship", false)
    var forwardWithoutCaptions by sharedPreferences.boolean("cg_forward_without_captions", false)
    var forwardNotify by sharedPreferences.boolean("cg_forward_notify", true)
    var msgForwardDate by sharedPreferences.boolean("cg_msg_fwd_date", false)
    var noAuthorship by sharedPreferences.boolean("cg_no_authorship", false)

    var newTabs_iconsV2_mode by sharedPreferences.int("cg_tabs_v2", 0)

    var iconReplacement by sharedPreferences.int("cg_iconpack", 0)
    var oldNotificationIcon by sharedPreferences.boolean("cg_old_notification", false)

    var messageSlideAction by sharedPreferences.int("cg_msgslide_action", 0)
    var enableSwipeToPIP by sharedPreferences.boolean("cg_swipe_to_pip", false)

    var voicesAgc by sharedPreferences.boolean("cg_hq_voices_agc", true)
    var overrideVoipEnhancements by sharedPreferences.boolean("cg_hq_voip_overrideservercfg", true)

    var silenceNonContacts by sharedPreferences.boolean("cg_silence_non_contacts", false)

    var autoOta by sharedPreferences.boolean("cg_auto_ota", true)

    var ghostMode = false

    var privateDir by sharedPreferences.boolean("cg_private_dir", true)
    var sleepTimer by sharedPreferences.boolean("cg_sleep_timer", false)
    var sleepTime by sharedPreferences.int("cg_sleep_time", 30)
    var sleepOp by sharedPreferences.int("cg_op", 30)

    var playVideoOnVolume by sharedPreferences.boolean("cg_play_video_on_volume", false)

    var mentionByName by sharedPreferences.boolean("cg_mention_by_name", false)

    var useMediaStream by sharedPreferences.boolean("cg_media_stream", false)

    var hideStickerTime by sharedPreferences.boolean("cg_hide_stick_time", true)

    var showDc by sharedPreferences.boolean("cg_show_dc", false)

    var hqVoice by sharedPreferences.boolean("cg_hq_voice", true)

    var ignoreArchivedChannels by sharedPreferences.boolean("cg_ignore_archived_channels", false)

    var disableAttachCamera by sharedPreferences.boolean("cg_disable_attach_camera", false)


    fun getIconReplacement(): BaseIconReplace {
        return when (iconReplacement) {
            1 -> NoIconReplace()
            else -> VkIconReplace()
        }
    }

    //var flatChannelStyle by sharedPreferences.boolean("cg_flat_channels", false)
    var flatChannelStyle = true

    init {
        CatogramToasts.init(sharedPreferences)
        fuckOff()
    }

    private fun putBoolean(key: String, value: Boolean) {
        val preferences = MessagesController.getGlobalMainSettings()
        val editor = preferences.edit()
        editor.putBoolean(key, value)
        editor.apply()
    }

    @JvmStatic
    fun shownContactsNever() {
        contactsNever = true
        putBoolean("contactsNever", true)
    }

    private fun fuckOff() {
        val good = "30820311308201f9a0030201020204019cc993300d06092a864886f70d01010b05003039311730150603550403130e4361746f6772616d2044656275673111300f060355040a13084361746f6772616d310b3009060355040613025553301e170d3231303130343139343631395a170d3438303532323139343631395a3039311730150603550403130e4361746f6772616d2044656275673111300f060355040a13084361746f6772616d310b300906035504061302555330820122300d06092a864886f70d01010105000382010f003082010a0282010100b7110aa72a8436c77137971b0dff973799637219f0cfc415a8956309dfd4ea153bd1f8867d981d25d29b7f9e7bc123af03f829520135cee3c90e11338dc9b06f08dac8db85c4232b9daacd76d9d08abecba93981065fbef6e3be979851a843305ae7454fa69c40d174ecc98b6cea0ec95ab83e9de4938c5eca1f689460944ccf13cff85c3db28d276c74a9972ffce529d769bfc39197d39896158fb2c75d536dbc66307c3a100994415685e27a1fa3b6078ba4ce72a689192f8f8433649c4b1ce5a64807d0a5974241b51ab3265d524de544f67fa5cf1c0f9569a041fa5eb64138467d68406cf982f63d0e7c22c22a25518347da1f157a6ba41f3c6e91420ad10203010001a321301f301d0603551d0e0416041491fd9440a4ddf35ebb4e5783baa30e80a4aa0753300d06092a864886f70d01010b050003820101006dbcc10cc3190c4c5f99fac9410dce10d598e494052bc894d4de09b1bf4fc186f53b8a31d3ef47003d65f01b127a0ab9e274ab5b577e2d4bcb9305f1dc0131640e3c0c83a5230df34fa18a693819966540ad80c2e96c66458a5ae4010aa5591a6eb16c96e28dc2ac23a41fd464aed31aa9ee62b0bc755908944f80dcd45f8f81f439cec6a20c1a21a35360ba8dc37b23b98b203716477aca09e9f48c071ba898fceaed278b9f128b2eca7d3172191438a873c84519d5312b71aa1557bba544dae1150928c3bb9152955de7dc7810ef31d1b4e198595a43596fb96c410a5c7604d35acde21c75bde970de79f44c55a9b84a5496539e9f53a83fc7059770929987"
        val info = ApplicationLoader.applicationContext.packageManager.getPackageInfo("ua.itaysonlab.messenger", PackageManager.GET_SIGNATURES).signatures[0].toCharsString()
        if (info != good) {
            exitProcess(0)
        }
    }
}
