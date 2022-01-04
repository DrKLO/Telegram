package ua.itaysonlab.catogram

import android.os.CountDownTimer
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import org.telegram.messenger.*
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.ActionBarPopupWindow
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.AvatarPreviewer
import org.telegram.ui.ChatActivity
import org.telegram.ui.ChatRightsEditActivity
import org.telegram.ui.Components.ShareAlert

// I've created this so CG features can be injected in a source file with 1 line only (maybe)
// Because manual editing of drklo's sources harms your mental health.
object CGFeatureHooks {
    @JvmStatic
    fun colorFeedWidgetItem(rv: RemoteViews) {
        rv.setTextColor(R.id.feed_widget_item_text, Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
    }

    @JvmStatic
    fun switchNoAuthor(b: Boolean) {
        // ...
        CatogramConfig.noAuthorship = b
    }

    private var currentPopup: ActionBarPopupWindow? = null

    @JvmStatic
    fun showForwardMenu(sa: ShareAlert, field: FrameLayout) {
        currentPopup = CGFeatureJavaHooks.createPopupWindow(sa.container, field, sa.context, listOf(
                CGFeatureJavaHooks.PopupItem(
                        if (CatogramConfig.forwardNoAuthorship) {
                            LocaleController.getString("CG_FwdMenu_DisableNoForward", R.string.CG_FwdMenu_DisableNoForward)
                        } else {
                            LocaleController.getString("CG_FwdMenu_EnableNoForward", R.string.CG_FwdMenu_EnableNoForward)
                        },
                        R.drawable.msg_forward
                ) {
                    // Toggle!
                    CatogramConfig.forwardNoAuthorship = !CatogramConfig.forwardNoAuthorship
                    currentPopup?.dismiss()
                    currentPopup = null
                },
            CGFeatureJavaHooks.PopupItem(
                if (CatogramConfig.forwardWithoutCaptions) {
                    LocaleController.getString("CG_FwdMenu_EnableCaptions", R.string.CG_FwdMenu_EnableCaptions)
                } else {
                    LocaleController.getString("CG_FwdMenu_DisableCaptions", R.string.CG_FwdMenu_DisableCaptions)
                },
                R.drawable.msg_edit
            ) {
                // Toggle!
                CatogramConfig.forwardWithoutCaptions = !CatogramConfig.forwardWithoutCaptions
                currentPopup?.dismiss()
                currentPopup = null
            },
            CGFeatureJavaHooks.PopupItem(
                if (CatogramConfig.forwardNotify) {
                    LocaleController.getString("CG_FwdMenu_NoNotify", R.string.CG_FwdMenu_NoNotify)
                } else {
                    LocaleController.getString("CG_FwdMenu_Notify", R.string.CG_FwdMenu_Notify)
                },
                R.drawable.input_notify_on
            ) {
                // Toggle!
                CatogramConfig.forwardNotify = !CatogramConfig.forwardNotify
                currentPopup?.dismiss()
                currentPopup = null
            },
        ))
    }

    @JvmStatic
    fun getProperNotificationIcon(): Int {
        return if (CatogramConfig.oldNotificationIcon) R.drawable.notification else R.drawable.cg_notification
    }

    @JvmStatic
    fun getReplyIconDrawable(): Int {
        return when (CatogramConfig.messageSlideAction) {
            1 -> R.drawable.menu_saved_cg
            2 -> R.drawable.share_arrow
            else -> R.drawable.fast_reply
        }
    }

    @JvmStatic
    fun injectChatActivityMsgSlideAction(cf: ChatActivity, msg: MessageObject, isChannel: Boolean, classGuid: Int) {
        when (CatogramConfig.messageSlideAction) {
            0 -> {
                // Reply (default)
                cf.showFieldPanelForReply(msg)
            }
            1 -> {
                // Save to PM
                cf.sendMessagesHelper.sendMessage(arrayListOf(msg), UserConfig.getInstance(UserConfig.selectedAccount).clientUserId.toLong(), true, false, true, 0)
            }
            2 -> {
                // Share
                cf.showDialog(object : ShareAlert(cf.parentActivity, arrayListOf(msg), null, isChannel, null, false) {
                    override fun dismissInternal() {
                        super.dismissInternal()
                        AndroidUtilities.requestAdjustResize(cf.parentActivity, classGuid)
                        if (cf.chatActivityEnterView.visibility == View.VISIBLE) {
                            cf.fragmentView.requestLayout()
                        }
                        //cf.hideActionMode()
                        cf.updatePinnedMessageView(true)
                    }
                })

                AndroidUtilities.setAdjustResizeToNothing(cf.parentActivity, classGuid)
                cf.fragmentView.requestLayout()
            }
        }
    }

    @JvmStatic
    fun injectChatActivityAvatarArraySize(cf: ChatActivity): Int {
        var objs = 0

        if (ChatObject.canBlockUsers(cf.currentChat)) objs++
        if (ChatObject.hasAdminRights(cf.currentChat)) objs++
        if (ChatObject.canAddAdmins(cf.currentChat)) objs++

        return objs
    }

    @JvmStatic
    fun injectChatActivityAvatarArrayItems(cf: ChatActivity, arr: Array<AvatarPreviewer.MenuItem>, enableMention: Boolean) {
        var startPos = if (enableMention) 3 else 2

        if (ChatObject.canBlockUsers(cf.currentChat)) {
            arr[startPos] = AvatarPreviewer.MenuItem.CG_KICK
            startPos++
        }

        if (ChatObject.hasAdminRights(cf.currentChat)) {
            arr[startPos] = AvatarPreviewer.MenuItem.CG_CHANGE_PERMS
            startPos++
        }

        if (ChatObject.canAddAdmins(cf.currentChat)) {
            arr[startPos] = AvatarPreviewer.MenuItem.CG_CHANGE_ADMIN_PERMS
            startPos++
        }
    }

    @JvmStatic
    fun injectChatActivityAvatarOnClick(cf: ChatActivity, item: AvatarPreviewer.MenuItem, user: TLRPC.User) {
        when (item) {
            AvatarPreviewer.MenuItem.CG_KICK -> {
                cf.messagesController.deleteParticipantFromChat(cf.currentChat.id, cf.messagesController.getUser(user.id), cf.currentChatInfo)
            }
            AvatarPreviewer.MenuItem.CG_CHANGE_PERMS, AvatarPreviewer.MenuItem.CG_CHANGE_ADMIN_PERMS -> {
                val action = if (item == AvatarPreviewer.MenuItem.CG_CHANGE_PERMS) 1 else 0 // 0 - change admin rights

                val chatParticipant = cf.currentChatInfo.participants.participants.filter {
                    it.user_id == user.id
                }[0]

                var channelParticipant: TLRPC.ChannelParticipant? = null

                var canEditAdmin: Boolean
                val allowKick: Boolean
                val canRestrict: Boolean
                val editingAdmin: Boolean

                if (ChatObject.isChannel(cf.currentChat)) {
                    channelParticipant = (chatParticipant as TLRPC.TL_chatChannelParticipant).channelParticipant

                    canEditAdmin = ChatObject.canAddAdmins(cf.currentChat)
                    if (canEditAdmin && (channelParticipant is TLRPC.TL_channelParticipantCreator || channelParticipant is TLRPC.TL_channelParticipantAdmin && !channelParticipant.can_edit)) {
                        canEditAdmin = false
                    }

                    allowKick = ChatObject.canBlockUsers(cf.currentChat) && (!(channelParticipant is TLRPC.TL_channelParticipantAdmin || channelParticipant is TLRPC.TL_channelParticipantCreator) || channelParticipant.can_edit)
                    canRestrict = allowKick
                    editingAdmin = channelParticipant is TLRPC.TL_channelParticipantAdmin
                } else {
                    allowKick = cf.currentChat.creator || chatParticipant is TLRPC.TL_chatParticipant && (ChatObject.canBlockUsers(cf.currentChat) || chatParticipant.inviter_id == cf.userConfig.getClientUserId())
                    canEditAdmin = cf.currentChat.creator
                    canRestrict = cf.currentChat.creator
                    editingAdmin = chatParticipant is TLRPC.TL_chatParticipantAdmin
                }


                val frag = ChatRightsEditActivity(
                        user.id,
                        cf.currentChatInfo.id,
                        channelParticipant?.admin_rights,
                        cf.currentChat.default_banned_rights,
                        channelParticipant?.banned_rights,
                        channelParticipant?.rank,
                        action,
                        true,
                        false
                )

                cf.presentFragment(frag)
            }
        }
    }
}