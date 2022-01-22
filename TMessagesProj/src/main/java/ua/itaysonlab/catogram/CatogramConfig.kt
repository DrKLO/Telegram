package ua.itaysonlab.catogram

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Build
import org.telegram.messenger.*
import ua.itaysonlab.catogram.preferences.ktx.boolean
import ua.itaysonlab.catogram.preferences.ktx.int
import ua.itaysonlab.catogram.preferences.ktx.string
import ua.itaysonlab.catogram.ui.CatogramToasts
import ua.itaysonlab.catogram.vkui.icon_replaces.BaseIconReplace
import ua.itaysonlab.catogram.vkui.icon_replaces.NoIconReplace
import ua.itaysonlab.catogram.vkui.icon_replaces.VkIconReplace
import java.io.BufferedReader
import java.io.FileReader
import java.util.regex.Pattern

object CatogramConfig {

    private val sharedPreferences: SharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE)
    var trLang by sharedPreferences.string("cg_tr_lang", "en")
    var hideProxySponsor by sharedPreferences.boolean("advanced_hideproxysponsor", true)
    var hidePhoneNumber by sharedPreferences.boolean("advanced_hidephonenumber", true)
    var noRounding by sharedPreferences.boolean("advanced_norounding", false)
    var noVibration by sharedPreferences.boolean("advanced_novibration", false)
    var drawerAvatar by sharedPreferences.boolean("cg_drawer_avatar", false)
    var flatActionbar by sharedPreferences.boolean("cg_flat_actionbar", false)
    var drawerBlur by sharedPreferences.boolean("cg_drawer_blur", false)
    var drawerDarken by sharedPreferences.boolean("cg_drawer_darken", false)
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
    var redesign_iconOption by sharedPreferences.int("cg_iconoption", 1)
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

    /* CX 4.1.0 */
    var oldTranslateUI by sharedPreferences.boolean("cx_old_translate_ui", true)
    var dnd by sharedPreferences.boolean("cx_dnd", false)
    var hideSendAsChannel by sharedPreferences.boolean("cx_hide_send_as_channel", false)
    var customEmojiFont by sharedPreferences.boolean("cx_custom_emoji_font", false)
    var customEmojiFontPat by sharedPreferences.string("cx_custom_emoji_font_path", "")
    var disableReactionAnim by sharedPreferences.boolean("cx_disable_reaction_anim", false)

    /* CX 4.1.2 */
    var customChatListTitle by sharedPreferences.string("cx_chat_list_title", LocaleController.getString("WidgetChats", R.string.WidgetChats))
    var systemFonts by sharedPreferences.boolean("cx_system_fonts", false)
    var magiKeyboard by sharedPreferences.boolean("cx_magikeyboard", false)//Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)

    // Emojis
    var loadSystemEmojiFailed = false
    var sysEmojiTypeface: Typeface? = null
    var custEmojiTypeface: Typeface? = null
    private const val EMOJI_FONT_AOSP = "NotoColorEmoji.ttf"

    fun getSystemEmojiTypeface(): Typeface? {
        if (!loadSystemEmojiFailed && sysEmojiTypeface == null) {
            try {
                val p = Pattern.compile(">(.*emoji.*)</font>", Pattern.CASE_INSENSITIVE)
                val br = BufferedReader(FileReader("/system/etc/fonts.xml"))
                var line: CharSequence
                while (br.readLine().also { line = it } != null) {
                    val m = p.matcher(line)
                    if (m.find()) {
                        sysEmojiTypeface = Typeface.createFromFile("/system/fonts/" + m.group(1))
                        FileLog.d("emoji font file fonts.xml = " + m.group(1))
                        break
                    }
                }
                br.close()
            } catch (e: Exception) {
                FileLog.e(e)
            }
            if (sysEmojiTypeface == null) {
                try {
                    sysEmojiTypeface = Typeface.createFromFile("/system/fonts/$EMOJI_FONT_AOSP")
                    FileLog.d("emoji font file = $EMOJI_FONT_AOSP")
                } catch (e: Exception) {
                    FileLog.e(e)
                    loadSystemEmojiFailed = true
                }
            }
        }
        return sysEmojiTypeface
    }

    fun setCustomEmojiFontPath(path: String): Boolean {
        val typeface: Typeface?
        typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Typeface.Builder(path)
                    .build()
        } else {
            Typeface.createFromFile(path)
        }
        if (typeface == null || typeface == Typeface.DEFAULT) {
            return false
        }
        custEmojiTypeface = typeface
        customEmojiFontPat = path
        return true
    }

    fun getCustomEmojiTypeface(): Typeface? {
        if (custEmojiTypeface == null) {
            try {
                custEmojiTypeface = Typeface.createFromFile(customEmojiFontPat)
            } catch (e: Exception) {
                FileLog.e(e)
                custEmojiTypeface = null
                if (customEmojiFont) customEmojiFont = false
                setCustomEmojiFontPath("")
            }
        }
        return custEmojiTypeface

    }

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
}
