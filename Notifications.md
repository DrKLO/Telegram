# Notifications

Since [Android 8.0 Oreo, Google doesn't allow apps to run in the background anymore](https://developer.android.com/about/versions/oreo/background#services), requiring all apps which were previously keeping background connection to exclusively use its Firebase push messaging service. 

As one can't use Google's push messaging in a FOSS app, Telegram-FOSS has to show you a notification to keep the background service running. Otherwise, you wouldn't be notified about new messages.

Sadly, if the app would set the notification to lower priority (to hide it a bit in the lower part of the notification screen), you would immediately get a system notification about Telegram "using battery", which is confusing and is the reason for this not being the default. Despite Google's misleading warnings, there is no difference in battery usage between v4.6 in "true background" and v4.9+ with notification.

## Make it better

You may still lower the priority of the notification channel or even hide it altogether manually (make a long tap on the notification). You will then receive the misleading system notification, which [may be disabled as well with another long tap](https://9to5google.com/2017/10/26/how-to-disable-android-oreo-using-battery-notification-android-basics/).
