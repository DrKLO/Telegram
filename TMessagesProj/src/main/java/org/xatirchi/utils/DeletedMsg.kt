package org.xatirchi.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.MessageObject
import org.telegram.messenger.SendMessagesHelper
import javax.inject.Singleton

object DeletedMsg {
    private const val TAG = "DeletedMsg"

    const val SIMPLE = 0
    const val YOU = 1
    const val I = 2
    const val YOU_AND_I = 3
    const val DELETE_MARK = "<-------------->"

    private val sharedPreferences =
        ApplicationLoader.applicationContext.getSharedPreferences("db", Context.MODE_PRIVATE)
    private val editor = sharedPreferences.edit()
    private val gson = Gson()
    var myDelete = false

    var list = ArrayList<MessageObject>()

    fun getAllIds(): ArrayList<WhoDeletedMsg> {
        val markMessages = ArrayList<WhoDeletedMsg>()

        val gson = Gson()
        val str = sharedPreferences.getString("markeDelete", "")
        if (str !== "") {
            val type: TypeToken<*> = object : TypeToken<List<WhoDeletedMsg?>?>() {
            }
            val fromJson = gson.fromJson<java.util.ArrayList<WhoDeletedMsg>>(str, type.type)
            for (markId in fromJson) {
                markMessages.add(markId)
            }
        }

        return markMessages
    }

    fun saveDeletedMessagesId(messageIds: ArrayList<WhoDeletedMsg>) {
        val str = gson.toJson(messageIds)
        editor.putString("markeDelete", str)
        editor.commit()
    }

    fun saveCheckType(type: Int) {
        editor.putInt("delete_check_key", type)
        editor.commit()
    }

    fun getCheckType(): Int {
        return sharedPreferences.getInt("delete_check_key", SIMPLE)
    }

    fun deletedMsgEdit(
        iDeleted: Boolean,
        markMessages: ArrayList<Int>,
        messages: ArrayList<MessageObject>,
        currentAccount: Int
    ) {
        var m = ArrayList<Int>()
            m.addAll(markMessages)
        val msgIds = getAllIds()
        for (i in 0 until m.size) {
            for (message in messages) {
                if (m[i] == message.id) {
                    try {
                        val deleteTitle = if (iDeleted) "I Delete" else "Deleted Message"
                        message.editingMessage =
                            "$deleteTitle\n$DELETE_MARK\n${message.messageText}"
                        SendMessagesHelper.getInstance(currentAccount).editMessage(
                            message,
                            null,
                            null,
                            null,
                            null,
                            null,
                            false,
                            message.hasMediaSpoilers(),
                            null
                        )
                        for (j in 0 until msgIds.size) {
                            if (msgIds[j].id == m[i]) {
                                msgIds.remove(msgIds[j])
                                break
                            }
                        }
                    } catch (_: Exception) { }
                }
            }
        }
        saveDeletedMessagesId(msgIds)
    }

    fun msgEdit(
        messages: ArrayList<MessageObject>,
        currentAccount: Int
    ) {
        val msgIds1 = getAllIds()
        val msgIds2 = getAllIds()
        for (markMessage in msgIds1) {
            for (message in messages) {
                if (markMessage.id == message.id) {
                    try {
                        val deleteTitle = if (markMessage.youOrI) "I Delete" else "Deleted Message"
                        message.editingMessage =
                            "$deleteTitle\n$DELETE_MARK\n${message.messageText}"
                        SendMessagesHelper.getInstance(currentAccount).editMessage(
                            message,
                            null,
                            null,
                            null,
                            null,
                            null,
                            false,
                            message.hasMediaSpoilers(),
                            null
                        )
                        msgIds2.remove(markMessage)
                    } catch (_: Exception) {
                    }
                }
            }
        }
        saveDeletedMessagesId(msgIds2)
    }

}

data class WhoDeletedMsg(val id: Int, val youOrI: Boolean)