/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */
package org.telegram.messenger

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import com.google.android.gms.auth.api.phone.SmsRetriever
import java.util.regex.Pattern

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            var message: String? = ""
            val preferences = ApplicationLoader.applicationContext.getSharedPreferences(
                "mainconfig",
                Activity.MODE_PRIVATE
            )
            val hash = preferences.getString("sms_hash", null)
            if (SmsRetriever.SMS_RETRIEVED_ACTION == intent.action) {
                if (!AndroidUtilities.isWaitingForSms()) {
                    return
                }
                val bundle = intent.extras
                message = bundle!![SmsRetriever.EXTRA_SMS_MESSAGE] as String?
            }
            if (TextUtils.isEmpty(message)) {
                return
            }
            val pattern = Pattern.compile("[0-9\\-]+")
            val matcher = pattern.matcher(message)
            if (matcher.find()) {
                val code = matcher.group(0).replace("-", "")
                if (code.length >= 3) {
                    if (hash != null) {
                        preferences.edit().putString("sms_hash_code", "$hash|$code").commit()
                    }
                    AndroidUtilities.runOnUIThread {
                        NotificationCenter.getGlobalInstance()
                            .postNotificationName(NotificationCenter.didReceiveSmsCode, code)
                    }
                }
            }
        } catch (e: Throwable) {
            FileLog.e(e)
        }
    }
}