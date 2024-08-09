package org.xatirchi.utils

import android.content.Context
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.MessageObject

object EditMessage {

    var editMode = false

    private var sharedPreferences =
        ApplicationLoader.applicationContext.getSharedPreferences("db", Context.MODE_PRIVATE)
    private var editor = sharedPreferences.edit()

    var messages = ArrayList<MessageObject>()

    fun editMessageText(
        messageText: String,
        id: Int
    ): String {
        var str = messageText
        if (editMode) {
            for (message in messages) {
                if (id == message.id && message.messageText != messageText && !messageText.contains(
                        DeletedMsg.DELETE_MARK
                    )
                ) {
                    str = "${message.messageText}\n\n---Edited message---\n\n$messageText"
                }
            }
        }
        return str
    }

    fun editMessage(oldMsg: String, newMsg: String): String {
        return if (editMode) "$oldMsg\n\n---Edited message---\n\n$newMsg" else newMsg
    }

    fun setEditMode() {
        editMode = sharedPreferences.getBoolean("edit", false)
    }

    fun changeEditMode(mode: Boolean) {
        editMode = mode
        editor.putBoolean("edit", editMode)
        editor.commit()
        MyStatus.setMyStatus()
    }

}