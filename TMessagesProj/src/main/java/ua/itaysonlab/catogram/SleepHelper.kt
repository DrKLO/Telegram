package ua.itaysonlab.catogram

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.telegram.messenger.MediaController

class SleepHelper : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
            if (!MediaController.getInstance().isMessagePaused) {
                MediaController.getInstance()
                    .pauseMessage(MediaController.getInstance().playingMessageObject)
            }
        CatogramConfig.sleepTimer = false
    }
}