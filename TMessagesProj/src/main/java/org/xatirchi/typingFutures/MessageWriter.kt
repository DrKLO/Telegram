package org.xatirchi.typingFutures

import org.telegram.messenger.MessageObject
import org.telegram.messenger.SendMessagesHelper
import android.os.Handler
import android.os.Looper

class MessageWriter(var msg: MessageObject, var currentAccount: Int) {

    private var handler = Handler(Looper.getMainLooper())
    private var str = ""
    private var count = 0
    private var stepSymbol = 3
    private var timeStep = 200L
    private var ic_end_str = "▒"

    fun writer() {
        if (msg.messageText.startsWith(".typer")) {
            str = msg.messageText.removePrefix(".typer").trim().toString()
            msg.editingMessage = ""
            msg.messageText = ""
            detectSteps()
            handler.postDelayed(run, 0)
        }
    }

    var run = object : Runnable {
        override fun run() {
            try {
                if (msg.messageText.toString() != "$str$ic_end_str") {
                    count = if (count >= str.length) str.length else count + stepSymbol
                    msg.editingMessage = "${str.subSequence(0, count)}$ic_end_str"
                    updateMsgFromServer(msg)
                    handler.postDelayed(this, timeStep)
                } else {
                    setDefaultValue()
                }
            } catch (e: Exception) {
                setDefaultValue()
            }
        }
    }

    fun detectSteps() {
        if (str.length > 40) {
            timeStep = 250L
            stepSymbol = 3
        } else {
            timeStep = 120L
            stepSymbol = 1
        }
    }

    private fun setDefaultValue() {
        destroyMyRunnableFun()
        msg.editingMessage = str
        updateMsgFromServer(msg)
    }

    private fun updateMsgFromServer(defaultMsg: MessageObject?) {
        if (defaultMsg == null) {
            return
        }
        try {
            SendMessagesHelper.getInstance(currentAccount).editMessage(
                defaultMsg,
                null,
                null,
                null,
                null,
                null,
                false,
                defaultMsg.hasMediaSpoilers(),
                null
            )
        } catch (e: Exception) {
            setDefaultValue()
        }
    }

    fun destroyMyRunnableFun() {
        handler.removeCallbacks(run)
    }

}