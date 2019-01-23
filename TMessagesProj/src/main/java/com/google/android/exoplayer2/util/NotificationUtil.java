/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.util;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Utility methods for displaying {@link android.app.Notification}s. */
@SuppressLint("InlinedApi")
public final class NotificationUtil {

  /**
   * Notification channel importance levels. One of {@link #IMPORTANCE_UNSPECIFIED}, {@link
   * #IMPORTANCE_NONE}, {@link #IMPORTANCE_MIN}, {@link #IMPORTANCE_LOW}, {@link
   * #IMPORTANCE_DEFAULT} or {@link #IMPORTANCE_HIGH}.
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({
    IMPORTANCE_UNSPECIFIED,
    IMPORTANCE_NONE,
    IMPORTANCE_MIN,
    IMPORTANCE_LOW,
    IMPORTANCE_DEFAULT,
    IMPORTANCE_HIGH
  })
  public @interface Importance {}
  /** @see NotificationManager#IMPORTANCE_UNSPECIFIED */
  public static final int IMPORTANCE_UNSPECIFIED = NotificationManager.IMPORTANCE_UNSPECIFIED;
  /** @see NotificationManager#IMPORTANCE_NONE */
  public static final int IMPORTANCE_NONE = NotificationManager.IMPORTANCE_NONE;
  /** @see NotificationManager#IMPORTANCE_MIN */
  public static final int IMPORTANCE_MIN = NotificationManager.IMPORTANCE_MIN;
  /** @see NotificationManager#IMPORTANCE_LOW */
  public static final int IMPORTANCE_LOW = NotificationManager.IMPORTANCE_LOW;
  /** @see NotificationManager#IMPORTANCE_DEFAULT */
  public static final int IMPORTANCE_DEFAULT = NotificationManager.IMPORTANCE_DEFAULT;
  /** @see NotificationManager#IMPORTANCE_HIGH */
  public static final int IMPORTANCE_HIGH = NotificationManager.IMPORTANCE_HIGH;

  /**
   * Creates a notification channel that notifications can be posted to. See {@link
   * NotificationChannel} and {@link
   * NotificationManager#createNotificationChannel(NotificationChannel)} for details.
   *
   * @param context A {@link Context} to retrieve {@link NotificationManager}.
   * @param id The id of the channel. Must be unique per package. The value may be truncated if it
   *     is too long.
   * @param name A string resource identifier for the user visible name of the channel. You can
   *     rename this channel when the system locale changes by listening for the {@link
   *     Intent#ACTION_LOCALE_CHANGED} broadcast. The recommended maximum length is 40 characters;
   *     the value may be truncated if it is too long.
   * @param importance The importance of the channel. This controls how interruptive notifications
   *     posted to this channel are. One of {@link #IMPORTANCE_UNSPECIFIED}, {@link
   *     #IMPORTANCE_NONE}, {@link #IMPORTANCE_MIN}, {@link #IMPORTANCE_LOW}, {@link
   *     #IMPORTANCE_DEFAULT} and {@link #IMPORTANCE_HIGH}.
   */
  public static void createNotificationChannel(
      Context context, String id, @StringRes int name, @Importance int importance) {
    if (Util.SDK_INT >= 26) {
      NotificationManager notificationManager =
          (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
      NotificationChannel channel =
          new NotificationChannel(id, context.getString(name), importance);
      notificationManager.createNotificationChannel(channel);
    }
  }

  /**
   * Post a notification to be shown in the status bar. If a notification with the same id has
   * already been posted by your application and has not yet been canceled, it will be replaced by
   * the updated information. If {@code notification} is null, then cancels a previously shown
   * notification.
   *
   * @param context A {@link Context} to retrieve {@link NotificationManager}.
   * @param id An identifier for this notification unique within your application.
   * @param notification A {@link Notification} object describing what to show the user. If null,
   *     then cancels a previously shown notification.
   */
  public static void setNotification(Context context, int id, @Nullable Notification notification) {
    NotificationManager notificationManager =
        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    if (notification != null) {
      notificationManager.notify(id, notification);
    } else {
      notificationManager.cancel(id);
    }
  }

  private NotificationUtil() {}
}
