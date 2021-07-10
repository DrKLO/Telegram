/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.messenger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.KeyEvent

class MusicPlayerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MEDIA_BUTTON) {
            if (intent.extras == null) {
                return
            }
            val keyEvent = intent.extras!![Intent.EXTRA_KEY_EVENT] as KeyEvent? ?: return
            if (keyEvent.action != KeyEvent.ACTION_DOWN) return
            when (keyEvent.keyCode) {
                KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> if (MediaController.getInstance().isMessagePaused) {
                    MediaController.getInstance()
                        .playMessage(MediaController.getInstance().playingMessageObject)
                } else {
                    MediaController.getInstance()
                        .pauseMessage(MediaController.getInstance().playingMessageObject)
                }
                KeyEvent.KEYCODE_MEDIA_PLAY -> MediaController.getInstance().playMessage(
                    MediaController.getInstance().playingMessageObject
                )
                KeyEvent.KEYCODE_MEDIA_PAUSE -> MediaController.getInstance().pauseMessage(
                    MediaController.getInstance().playingMessageObject
                )
                KeyEvent.KEYCODE_MEDIA_STOP -> {
                }
                KeyEvent.KEYCODE_MEDIA_NEXT -> MediaController.getInstance().playNextMessage()
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> MediaController.getInstance()
                    .playPreviousMessage()
            }
        } else {
            if (intent.action == MusicPlayerService.NOTIFY_PLAY) {
                MediaController.getInstance()
                    .playMessage(MediaController.getInstance().playingMessageObject)
            } else if (intent.action == MusicPlayerService.NOTIFY_PAUSE || intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                MediaController.getInstance()
                    .pauseMessage(MediaController.getInstance().playingMessageObject)
            } else if (intent.action == MusicPlayerService.NOTIFY_NEXT) {
                MediaController.getInstance().playNextMessage()
            } else if (intent.action == MusicPlayerService.NOTIFY_CLOSE) {
                MediaController.getInstance().cleanupPlayer(true, true)
            } else if (intent.action == MusicPlayerService.NOTIFY_PREVIOUS) {
                MediaController.getInstance().playPreviousMessage()
            }
        }
    }
}