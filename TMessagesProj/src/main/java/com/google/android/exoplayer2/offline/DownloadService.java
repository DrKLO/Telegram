/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.offline;

import static com.google.android.exoplayer2.offline.Download.STOP_REASON_NONE;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.google.android.exoplayer2.scheduler.Requirements;
import com.google.android.exoplayer2.scheduler.Requirements.RequirementFlags;
import com.google.android.exoplayer2.scheduler.Scheduler;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.NotificationUtil;
import com.google.android.exoplayer2.util.Util;
import java.util.HashMap;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * A {@link Service} for downloading media.
 *
 * <p>Apps with target SDK 33 and greater need to add the {@code
 * android.permission.POST_NOTIFICATIONS} permission to the manifest and request the permission at
 * runtime before starting downloads. Without that permission granted by the user, notifications
 * posted by this service are not displayed. See <a
 * href="https://developer.android.com/develop/ui/views/notifications/notification-permission">the
 * official UI guide</a> for more detailed information.
 */
public abstract class DownloadService extends Service {

  /**
   * Starts a download service to resume any ongoing downloads. Extras:
   *
   * <ul>
   *   <li>{@link #KEY_FOREGROUND} - See {@link #KEY_FOREGROUND}.
   * </ul>
   */
  public static final String ACTION_INIT =
      "com.google.android.exoplayer.downloadService.action.INIT";

  /** Like {@link #ACTION_INIT}, but with {@link #KEY_FOREGROUND} implicitly set to true. */
  private static final String ACTION_RESTART =
      "com.google.android.exoplayer.downloadService.action.RESTART";

  /**
   * Adds a new download. Extras:
   *
   * <ul>
   *   <li>{@link #KEY_DOWNLOAD_REQUEST} - A {@link DownloadRequest} defining the download to be
   *       added.
   *   <li>{@link #KEY_STOP_REASON} - An initial stop reason for the download. If omitted {@link
   *       Download#STOP_REASON_NONE} is used.
   *   <li>{@link #KEY_FOREGROUND} - See {@link #KEY_FOREGROUND}.
   * </ul>
   */
  public static final String ACTION_ADD_DOWNLOAD =
      "com.google.android.exoplayer.downloadService.action.ADD_DOWNLOAD";

  /**
   * Removes a download. Extras:
   *
   * <ul>
   *   <li>{@link #KEY_CONTENT_ID} - The content id of a download to remove.
   *   <li>{@link #KEY_FOREGROUND} - See {@link #KEY_FOREGROUND}.
   * </ul>
   */
  public static final String ACTION_REMOVE_DOWNLOAD =
      "com.google.android.exoplayer.downloadService.action.REMOVE_DOWNLOAD";

  /**
   * Removes all downloads. Extras:
   *
   * <ul>
   *   <li>{@link #KEY_FOREGROUND} - See {@link #KEY_FOREGROUND}.
   * </ul>
   */
  public static final String ACTION_REMOVE_ALL_DOWNLOADS =
      "com.google.android.exoplayer.downloadService.action.REMOVE_ALL_DOWNLOADS";

  /**
   * Resumes all downloads except those that have a non-zero {@link Download#stopReason}. Extras:
   *
   * <ul>
   *   <li>{@link #KEY_FOREGROUND} - See {@link #KEY_FOREGROUND}.
   * </ul>
   */
  public static final String ACTION_RESUME_DOWNLOADS =
      "com.google.android.exoplayer.downloadService.action.RESUME_DOWNLOADS";

  /**
   * Pauses all downloads. Extras:
   *
   * <ul>
   *   <li>{@link #KEY_FOREGROUND} - See {@link #KEY_FOREGROUND}.
   * </ul>
   */
  public static final String ACTION_PAUSE_DOWNLOADS =
      "com.google.android.exoplayer.downloadService.action.PAUSE_DOWNLOADS";

  /**
   * Sets the stop reason for one or all downloads. To clear the stop reason, pass {@link
   * Download#STOP_REASON_NONE}. Extras:
   *
   * <ul>
   *   <li>{@link #KEY_CONTENT_ID} - The content id of a single download to update with the stop
   *       reason. If omitted, all downloads will be updated.
   *   <li>{@link #KEY_STOP_REASON} - An application provided reason for stopping the download or
   *       downloads, or {@link Download#STOP_REASON_NONE} to clear the stop reason.
   *   <li>{@link #KEY_FOREGROUND} - See {@link #KEY_FOREGROUND}.
   * </ul>
   */
  public static final String ACTION_SET_STOP_REASON =
      "com.google.android.exoplayer.downloadService.action.SET_STOP_REASON";

  /**
   * Sets the requirements that need to be met for downloads to progress. Extras:
   *
   * <ul>
   *   <li>{@link #KEY_REQUIREMENTS} - A {@link Requirements}.
   *   <li>{@link #KEY_FOREGROUND} - See {@link #KEY_FOREGROUND}.
   * </ul>
   */
  public static final String ACTION_SET_REQUIREMENTS =
      "com.google.android.exoplayer.downloadService.action.SET_REQUIREMENTS";

  /** Key for the {@link DownloadRequest} in {@link #ACTION_ADD_DOWNLOAD} intents. */
  public static final String KEY_DOWNLOAD_REQUEST = "download_request";

  /**
   * Key for the {@link String} content id in {@link #ACTION_SET_STOP_REASON} and {@link
   * #ACTION_REMOVE_DOWNLOAD} intents.
   */
  public static final String KEY_CONTENT_ID = "content_id";

  /**
   * Key for the integer stop reason in {@link #ACTION_SET_STOP_REASON} and {@link
   * #ACTION_ADD_DOWNLOAD} intents.
   */
  public static final String KEY_STOP_REASON = "stop_reason";

  /** Key for the {@link Requirements} in {@link #ACTION_SET_REQUIREMENTS} intents. */
  public static final String KEY_REQUIREMENTS = "requirements";

  /**
   * Key for a boolean extra that can be set on any intent to indicate whether the service was
   * started in the foreground. If set, the service is guaranteed to call {@link
   * #startForeground(int, Notification)}.
   */
  public static final String KEY_FOREGROUND = "foreground";

  /** Invalid foreground notification id that can be used to run the service in the background. */
  public static final int FOREGROUND_NOTIFICATION_ID_NONE = 0;

  /** Default foreground notification update interval in milliseconds. */
  public static final long DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL = 1000;

  private static final String TAG = "DownloadService";

  // Maps each concrete DownloadService subclass to a single DownloadManagerHelper instance. This
  // ensures getDownloadManager is only called once per subclass, even if a new instance of the
  // service is created. The DownloadManagerHelper wrapper also takes care of restarting the service
  // when there's no scheduler, and is often able to restart the service faster than the scheduler
  // even when there is one.
  private static final HashMap<Class<? extends DownloadService>, DownloadManagerHelper>
      downloadManagerHelpers = new HashMap<>();

  @Nullable private final ForegroundNotificationUpdater foregroundNotificationUpdater;
  @Nullable private final String channelId;
  @StringRes private final int channelNameResourceId;
  @StringRes private final int channelDescriptionResourceId;

  private @MonotonicNonNull DownloadManagerHelper downloadManagerHelper;
  private int lastStartId;
  private boolean startedInForeground;
  private boolean taskRemoved;
  private boolean isStopped;
  private boolean isDestroyed;

  /**
   * Creates a DownloadService.
   *
   * <p>If {@code foregroundNotificationId} is {@link #FOREGROUND_NOTIFICATION_ID_NONE} then the
   * service will only ever run in the background, and no foreground notification will be displayed.
   *
   * <p>If {@code foregroundNotificationId} is not {@link #FOREGROUND_NOTIFICATION_ID_NONE} then the
   * service will run in the foreground. The foreground notification will be updated at least as
   * often as the interval specified by {@link #DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL}.
   *
   * @param foregroundNotificationId The notification id for the foreground notification, or {@link
   *     #FOREGROUND_NOTIFICATION_ID_NONE} if the service should only ever run in the background.
   */
  protected DownloadService(int foregroundNotificationId) {
    this(foregroundNotificationId, DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL);
  }

  /**
   * Creates a DownloadService.
   *
   * @param foregroundNotificationId The notification id for the foreground notification, or {@link
   *     #FOREGROUND_NOTIFICATION_ID_NONE} if the service should only ever run in the background.
   * @param foregroundNotificationUpdateInterval The maximum interval between updates to the
   *     foreground notification, in milliseconds. Ignored if {@code foregroundNotificationId} is
   *     {@link #FOREGROUND_NOTIFICATION_ID_NONE}.
   */
  protected DownloadService(
      int foregroundNotificationId, long foregroundNotificationUpdateInterval) {
    this(
        foregroundNotificationId,
        foregroundNotificationUpdateInterval,
        /* channelId= */ null,
        /* channelNameResourceId= */ 0,
        /* channelDescriptionResourceId= */ 0);
  }

  /**
   * @deprecated Use {@link #DownloadService(int, long, String, int, int)}.
   */
  @Deprecated
  protected DownloadService(
      int foregroundNotificationId,
      long foregroundNotificationUpdateInterval,
      @Nullable String channelId,
      @StringRes int channelNameResourceId) {
    this(
        foregroundNotificationId,
        foregroundNotificationUpdateInterval,
        channelId,
        channelNameResourceId,
        /* channelDescriptionResourceId= */ 0);
  }

  /**
   * Creates a DownloadService.
   *
   * @param foregroundNotificationId The notification id for the foreground notification, or {@link
   *     #FOREGROUND_NOTIFICATION_ID_NONE} if the service should only ever run in the background.
   * @param foregroundNotificationUpdateInterval The maximum interval between updates to the
   *     foreground notification, in milliseconds. Ignored if {@code foregroundNotificationId} is
   *     {@link #FOREGROUND_NOTIFICATION_ID_NONE}.
   * @param channelId An id for a low priority notification channel to create, or {@code null} if
   *     the app will take care of creating a notification channel if needed. If specified, must be
   *     unique per package. The value may be truncated if it's too long. Ignored if {@code
   *     foregroundNotificationId} is {@link #FOREGROUND_NOTIFICATION_ID_NONE}.
   * @param channelNameResourceId A string resource identifier for the user visible name of the
   *     notification channel. The recommended maximum length is 40 characters. The value may be
   *     truncated if it's too long. Ignored if {@code channelId} is null or if {@code
   *     foregroundNotificationId} is {@link #FOREGROUND_NOTIFICATION_ID_NONE}.
   * @param channelDescriptionResourceId A string resource identifier for the user visible
   *     description of the notification channel, or 0 if no description is provided. The
   *     recommended maximum length is 300 characters. The value may be truncated if it is too long.
   *     Ignored if {@code channelId} is null or if {@code foregroundNotificationId} is {@link
   *     #FOREGROUND_NOTIFICATION_ID_NONE}.
   */
  protected DownloadService(
      int foregroundNotificationId,
      long foregroundNotificationUpdateInterval,
      @Nullable String channelId,
      @StringRes int channelNameResourceId,
      @StringRes int channelDescriptionResourceId) {
    if (foregroundNotificationId == FOREGROUND_NOTIFICATION_ID_NONE) {
      this.foregroundNotificationUpdater = null;
      this.channelId = null;
      this.channelNameResourceId = 0;
      this.channelDescriptionResourceId = 0;
    } else {
      this.foregroundNotificationUpdater =
          new ForegroundNotificationUpdater(
              foregroundNotificationId, foregroundNotificationUpdateInterval);
      this.channelId = channelId;
      this.channelNameResourceId = channelNameResourceId;
      this.channelDescriptionResourceId = channelDescriptionResourceId;
    }
  }

  /**
   * Builds an {@link Intent} for adding a new download.
   *
   * @param context A {@link Context}.
   * @param clazz The concrete download service being targeted by the intent.
   * @param downloadRequest The request to be executed.
   * @param foreground Whether this intent will be used to start the service in the foreground.
   * @return The created intent.
   */
  public static Intent buildAddDownloadIntent(
      Context context,
      Class<? extends DownloadService> clazz,
      DownloadRequest downloadRequest,
      boolean foreground) {
    return buildAddDownloadIntent(context, clazz, downloadRequest, STOP_REASON_NONE, foreground);
  }

  /**
   * Builds an {@link Intent} for adding a new download.
   *
   * @param context A {@link Context}.
   * @param clazz The concrete download service being targeted by the intent.
   * @param downloadRequest The request to be executed.
   * @param stopReason An initial stop reason for the download, or {@link Download#STOP_REASON_NONE}
   *     if the download should be started.
   * @param foreground Whether this intent will be used to start the service in the foreground.
   * @return The created intent.
   */
  public static Intent buildAddDownloadIntent(
      Context context,
      Class<? extends DownloadService> clazz,
      DownloadRequest downloadRequest,
      int stopReason,
      boolean foreground) {
    return getIntent(context, clazz, ACTION_ADD_DOWNLOAD, foreground)
        .putExtra(KEY_DOWNLOAD_REQUEST, downloadRequest)
        .putExtra(KEY_STOP_REASON, stopReason);
  }

  /**
   * Builds an {@link Intent} for removing the download with the {@code id}.
   *
   * @param context A {@link Context}.
   * @param clazz The concrete download service being targeted by the intent.
   * @param id The content id.
   * @param foreground Whether this intent will be used to start the service in the foreground.
   * @return The created intent.
   */
  public static Intent buildRemoveDownloadIntent(
      Context context, Class<? extends DownloadService> clazz, String id, boolean foreground) {
    return getIntent(context, clazz, ACTION_REMOVE_DOWNLOAD, foreground)
        .putExtra(KEY_CONTENT_ID, id);
  }

  /**
   * Builds an {@link Intent} for removing all downloads.
   *
   * @param context A {@link Context}.
   * @param clazz The concrete download service being targeted by the intent.
   * @param foreground Whether this intent will be used to start the service in the foreground.
   * @return The created intent.
   */
  public static Intent buildRemoveAllDownloadsIntent(
      Context context, Class<? extends DownloadService> clazz, boolean foreground) {
    return getIntent(context, clazz, ACTION_REMOVE_ALL_DOWNLOADS, foreground);
  }

  /**
   * Builds an {@link Intent} for resuming all downloads.
   *
   * @param context A {@link Context}.
   * @param clazz The concrete download service being targeted by the intent.
   * @param foreground Whether this intent will be used to start the service in the foreground.
   * @return The created intent.
   */
  public static Intent buildResumeDownloadsIntent(
      Context context, Class<? extends DownloadService> clazz, boolean foreground) {
    return getIntent(context, clazz, ACTION_RESUME_DOWNLOADS, foreground);
  }

  /**
   * Builds an {@link Intent} to pause all downloads.
   *
   * @param context A {@link Context}.
   * @param clazz The concrete download service being targeted by the intent.
   * @param foreground Whether this intent will be used to start the service in the foreground.
   * @return The created intent.
   */
  public static Intent buildPauseDownloadsIntent(
      Context context, Class<? extends DownloadService> clazz, boolean foreground) {
    return getIntent(context, clazz, ACTION_PAUSE_DOWNLOADS, foreground);
  }

  /**
   * Builds an {@link Intent} for setting the stop reason for one or all downloads. To clear the
   * stop reason, pass {@link Download#STOP_REASON_NONE}.
   *
   * @param context A {@link Context}.
   * @param clazz The concrete download service being targeted by the intent.
   * @param id The content id, or {@code null} to set the stop reason for all downloads.
   * @param stopReason An application defined stop reason.
   * @param foreground Whether this intent will be used to start the service in the foreground.
   * @return The created intent.
   */
  public static Intent buildSetStopReasonIntent(
      Context context,
      Class<? extends DownloadService> clazz,
      @Nullable String id,
      int stopReason,
      boolean foreground) {
    return getIntent(context, clazz, ACTION_SET_STOP_REASON, foreground)
        .putExtra(KEY_CONTENT_ID, id)
        .putExtra(KEY_STOP_REASON, stopReason);
  }

  /**
   * Builds an {@link Intent} for setting the requirements that need to be met for downloads to
   * progress.
   *
   * @param context A {@link Context}.
   * @param clazz The concrete download service being targeted by the intent.
   * @param requirements A {@link Requirements}.
   * @param foreground Whether this intent will be used to start the service in the foreground.
   * @return The created intent.
   */
  public static Intent buildSetRequirementsIntent(
      Context context,
      Class<? extends DownloadService> clazz,
      Requirements requirements,
      boolean foreground) {
    return getIntent(context, clazz, ACTION_SET_REQUIREMENTS, foreground)
        .putExtra(KEY_REQUIREMENTS, requirements);
  }

  /**
   * Starts the service if not started already and adds a new download.
   *
   * @param context A {@link Context}.
   * @param clazz The concrete download service to be started.
   * @param downloadRequest The request to be executed.
   * @param foreground Whether the service is started in the foreground.
   */
  public static void sendAddDownload(
      Context context,
      Class<? extends DownloadService> clazz,
      DownloadRequest downloadRequest,
      boolean foreground) {
    Intent intent = buildAddDownloadIntent(context, clazz, downloadRequest, foreground);
    startService(context, intent, foreground);
  }

  /**
   * Starts the service if not started already and adds a new download.
   *
   * @param context A {@link Context}.
   * @param clazz The concrete download service to be started.
   * @param downloadRequest The request to be executed.
   * @param stopReason An initial stop reason for the download, or {@link Download#STOP_REASON_NONE}
   *     if the download should be started.
   * @param foreground Whether the service is started in the foreground.
   */
  public static void sendAddDownload(
      Context context,
      Class<? extends DownloadService> clazz,
      DownloadRequest downloadRequest,
      int stopReason,
      boolean foreground) {
    Intent intent = buildAddDownloadIntent(context, clazz, downloadRequest, stopReason, foreground);
    startService(context, intent, foreground);
  }

  /**
   * Starts the service if not started already and removes a download.
   *
   * @param context A {@link Context}.
   * @param clazz The concrete download service to be started.
   * @param id The content id.
   * @param foreground Whether the service is started in the foreground.
   */
  public static void sendRemoveDownload(
      Context context, Class<? extends DownloadService> clazz, String id, boolean foreground) {
    Intent intent = buildRemoveDownloadIntent(context, clazz, id, foreground);
    startService(context, intent, foreground);
  }

  /**
   * Starts the service if not started already and removes all downloads.
   *
   * @param context A {@link Context}.
   * @param clazz The concrete download service to be started.
   * @param foreground Whether the service is started in the foreground.
   */
  public static void sendRemoveAllDownloads(
      Context context, Class<? extends DownloadService> clazz, boolean foreground) {
    Intent intent = buildRemoveAllDownloadsIntent(context, clazz, foreground);
    startService(context, intent, foreground);
  }

  /**
   * Starts the service if not started already and resumes all downloads.
   *
   * @param context A {@link Context}.
   * @param clazz The concrete download service to be started.
   * @param foreground Whether the service is started in the foreground.
   */
  public static void sendResumeDownloads(
      Context context, Class<? extends DownloadService> clazz, boolean foreground) {
    Intent intent = buildResumeDownloadsIntent(context, clazz, foreground);
    startService(context, intent, foreground);
  }

  /**
   * Starts the service if not started already and pauses all downloads.
   *
   * @param context A {@link Context}.
   * @param clazz The concrete download service to be started.
   * @param foreground Whether the service is started in the foreground.
   */
  public static void sendPauseDownloads(
      Context context, Class<? extends DownloadService> clazz, boolean foreground) {
    Intent intent = buildPauseDownloadsIntent(context, clazz, foreground);
    startService(context, intent, foreground);
  }

  /**
   * Starts the service if not started already and sets the stop reason for one or all downloads. To
   * clear stop reason, pass {@link Download#STOP_REASON_NONE}.
   *
   * @param context A {@link Context}.
   * @param clazz The concrete download service to be started.
   * @param id The content id, or {@code null} to set the stop reason for all downloads.
   * @param stopReason An application defined stop reason.
   * @param foreground Whether the service is started in the foreground.
   */
  public static void sendSetStopReason(
      Context context,
      Class<? extends DownloadService> clazz,
      @Nullable String id,
      int stopReason,
      boolean foreground) {
    Intent intent = buildSetStopReasonIntent(context, clazz, id, stopReason, foreground);
    startService(context, intent, foreground);
  }

  /**
   * Starts the service if not started already and sets the requirements that need to be met for
   * downloads to progress.
   *
   * @param context A {@link Context}.
   * @param clazz The concrete download service to be started.
   * @param requirements A {@link Requirements}.
   * @param foreground Whether the service is started in the foreground.
   */
  public static void sendSetRequirements(
      Context context,
      Class<? extends DownloadService> clazz,
      Requirements requirements,
      boolean foreground) {
    Intent intent = buildSetRequirementsIntent(context, clazz, requirements, foreground);
    startService(context, intent, foreground);
  }

  /**
   * Starts a download service to resume any ongoing downloads.
   *
   * @param context A {@link Context}.
   * @param clazz The concrete download service to be started.
   * @see #startForeground(Context, Class)
   */
  public static void start(Context context, Class<? extends DownloadService> clazz) {
    context.startService(getIntent(context, clazz, ACTION_INIT));
  }

  /**
   * Starts the service in the foreground without adding a new download request. If there are any
   * not finished downloads and the requirements are met, the service resumes downloading. Otherwise
   * it stops immediately.
   *
   * @param context A {@link Context}.
   * @param clazz The concrete download service to be started.
   * @see #start(Context, Class)
   */
  public static void startForeground(Context context, Class<? extends DownloadService> clazz) {
    Intent intent = getIntent(context, clazz, ACTION_INIT, true);
    Util.startForegroundService(context, intent);
  }

  /**
   * Clear all {@linkplain DownloadManagerHelper download manager helpers} before restarting the
   * service.
   *
   * <p>Calling this method is normally only required if an app supports downloading content for
   * multiple users for which different download directories should be used.
   */
  public static void clearDownloadManagerHelpers() {
    downloadManagerHelpers.clear();
  }

  @Override
  public void onCreate() {
    if (channelId != null) {
      NotificationUtil.createNotificationChannel(
          this,
          channelId,
          channelNameResourceId,
          channelDescriptionResourceId,
          NotificationUtil.IMPORTANCE_LOW);
    }
    Class<? extends DownloadService> clazz = getClass();
    @Nullable DownloadManagerHelper downloadManagerHelper = downloadManagerHelpers.get(clazz);
    if (downloadManagerHelper == null) {
      boolean foregroundAllowed = foregroundNotificationUpdater != null;
      // See https://developer.android.com/about/versions/12/foreground-services.
      boolean canStartForegroundServiceFromBackground = Util.SDK_INT < 31;
      @Nullable
      Scheduler scheduler =
          foregroundAllowed && canStartForegroundServiceFromBackground ? getScheduler() : null;
      DownloadManager downloadManager = getDownloadManager();
      downloadManager.resumeDownloads();
      downloadManagerHelper =
          new DownloadManagerHelper(
              getApplicationContext(), downloadManager, foregroundAllowed, scheduler, clazz);
      downloadManagerHelpers.put(clazz, downloadManagerHelper);
    }
    this.downloadManagerHelper = downloadManagerHelper;
    downloadManagerHelper.attachService(this);
  }

  @Override
  public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
    lastStartId = startId;
    taskRemoved = false;
    @Nullable String intentAction = null;
    @Nullable String contentId = null;
    if (intent != null) {
      intentAction = intent.getAction();
      contentId = intent.getStringExtra(KEY_CONTENT_ID);
      startedInForeground |=
          intent.getBooleanExtra(KEY_FOREGROUND, false) || ACTION_RESTART.equals(intentAction);
    }
    // intentAction is null if the service is restarted or no action is specified.
    if (intentAction == null) {
      intentAction = ACTION_INIT;
    }
    DownloadManager downloadManager =
        Assertions.checkNotNull(downloadManagerHelper).downloadManager;
    switch (intentAction) {
      case ACTION_INIT:
      case ACTION_RESTART:
        // Do nothing.
        break;
      case ACTION_ADD_DOWNLOAD:
        @Nullable
        DownloadRequest downloadRequest =
            Assertions.checkNotNull(intent).getParcelableExtra(KEY_DOWNLOAD_REQUEST);
        if (downloadRequest == null) {
          Log.e(TAG, "Ignored ADD_DOWNLOAD: Missing " + KEY_DOWNLOAD_REQUEST + " extra");
        } else {
          int stopReason = intent.getIntExtra(KEY_STOP_REASON, Download.STOP_REASON_NONE);
          downloadManager.addDownload(downloadRequest, stopReason);
        }
        break;
      case ACTION_REMOVE_DOWNLOAD:
        if (contentId == null) {
          Log.e(TAG, "Ignored REMOVE_DOWNLOAD: Missing " + KEY_CONTENT_ID + " extra");
        } else {
          downloadManager.removeDownload(contentId);
        }
        break;
      case ACTION_REMOVE_ALL_DOWNLOADS:
        downloadManager.removeAllDownloads();
        break;
      case ACTION_RESUME_DOWNLOADS:
        downloadManager.resumeDownloads();
        break;
      case ACTION_PAUSE_DOWNLOADS:
        downloadManager.pauseDownloads();
        break;
      case ACTION_SET_STOP_REASON:
        if (!Assertions.checkNotNull(intent).hasExtra(KEY_STOP_REASON)) {
          Log.e(TAG, "Ignored SET_STOP_REASON: Missing " + KEY_STOP_REASON + " extra");
        } else {
          int stopReason = intent.getIntExtra(KEY_STOP_REASON, /* defaultValue= */ 0);
          downloadManager.setStopReason(contentId, stopReason);
        }
        break;
      case ACTION_SET_REQUIREMENTS:
        @Nullable
        Requirements requirements =
            Assertions.checkNotNull(intent).getParcelableExtra(KEY_REQUIREMENTS);
        if (requirements == null) {
          Log.e(TAG, "Ignored SET_REQUIREMENTS: Missing " + KEY_REQUIREMENTS + " extra");
        } else {
          downloadManager.setRequirements(requirements);
        }
        break;
      default:
        Log.e(TAG, "Ignored unrecognized action: " + intentAction);
        break;
    }

    if (Util.SDK_INT >= 26 && startedInForeground && foregroundNotificationUpdater != null) {
      // From API level 26, services started in the foreground are required to show a notification.
      foregroundNotificationUpdater.showNotificationIfNotAlready();
    }

    isStopped = false;
    if (downloadManager.isIdle()) {
      onIdle();
    }
    return START_STICKY;
  }

  @Override
  public void onTaskRemoved(Intent rootIntent) {
    taskRemoved = true;
  }

  @Override
  public void onDestroy() {
    isDestroyed = true;
    Assertions.checkNotNull(downloadManagerHelper).detachService(this);
    if (foregroundNotificationUpdater != null) {
      foregroundNotificationUpdater.stopPeriodicUpdates();
    }
  }

  /**
   * Throws {@link UnsupportedOperationException} because this service is not designed to be bound.
   */
  @Override
  @Nullable
  public final IBinder onBind(Intent intent) {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns a {@link DownloadManager} to be used to downloaded content. For each concrete download
   * service subclass, this is called once in the lifecycle of the process when {@link #onCreate} is
   * called on the first instance of the service. If the service is destroyed and a new instance is
   * created later, the new instance will use the previously returned {@link DownloadManager}
   * without this method being called again.
   */
  protected abstract DownloadManager getDownloadManager();

  /**
   * Returns a {@link Scheduler} to restart the service when requirements for downloads to continue
   * are met.
   *
   * <p>This method is not called on all devices or for all service configurations. When it is
   * called, it's called only once in the life cycle of the process. If a service has unfinished
   * downloads that cannot make progress due to unmet requirements, it will behave according to the
   * first matching case below:
   *
   * <ul>
   *   <li>If the service has {@code foregroundNotificationId} set to {@link
   *       #FOREGROUND_NOTIFICATION_ID_NONE}, then this method will not be called. The service will
   *       remain in the background until the downloads are able to continue to completion or the
   *       service is killed by the platform.
   *   <li>If the device API level is less than 31, a {@link Scheduler} is returned from this
   *       method, and the returned {@link Scheduler} {@link Scheduler#getSupportedRequirements
   *       supports} all of the requirements that have been specified for downloads to continue,
   *       then the service will stop itself and the {@link Scheduler} will be used to restart it in
   *       the foreground when the requirements are met.
   *   <li>If the device API level is less than 31 and either {@code null} or a {@link Scheduler}
   *       that does not {@link Scheduler#getSupportedRequirements support} all of the requirements
   *       is returned from this method, then the service will remain in the foreground until the
   *       downloads are able to continue to completion.
   *   <li>If the device API level is 31 or above, then this method will not be called and the
   *       service will remain in the foreground until the downloads are able to continue to
   *       completion. A {@link Scheduler} cannot be used for this case due to <a
   *       href="https://developer.android.com/about/versions/12/foreground-services">Android 12
   *       foreground service launch restrictions</a>.
   *   <li>
   * </ul>
   */
  @Nullable
  protected abstract Scheduler getScheduler();

  /**
   * Returns a notification to be displayed when this service running in the foreground.
   *
   * <p>Download services that do not wish to run in the foreground should be created by setting the
   * {@code foregroundNotificationId} constructor argument to {@link
   * #FOREGROUND_NOTIFICATION_ID_NONE}. This method is not called for such services, meaning it can
   * be implemented to throw {@link UnsupportedOperationException}.
   *
   * @param downloads The current downloads.
   * @param notMetRequirements Any requirements for downloads that are not currently met.
   * @return The foreground notification to display.
   */
  protected abstract Notification getForegroundNotification(
      List<Download> downloads, @RequirementFlags int notMetRequirements);

  /**
   * Invalidates the current foreground notification and causes {@link
   * #getForegroundNotification(List, int)} to be invoked again if the service isn't stopped.
   */
  protected final void invalidateForegroundNotification() {
    if (foregroundNotificationUpdater != null && !isDestroyed) {
      foregroundNotificationUpdater.invalidate();
    }
  }

  /**
   * Called after the service is created, once the downloads are known.
   *
   * @param downloads The current downloads.
   */
  private void notifyDownloads(List<Download> downloads) {
    if (foregroundNotificationUpdater != null) {
      for (int i = 0; i < downloads.size(); i++) {
        if (needsStartedService(downloads.get(i).state)) {
          foregroundNotificationUpdater.startPeriodicUpdates();
          break;
        }
      }
    }
  }

  /**
   * Called when the state of a download changes.
   *
   * @param download The state of the download.
   */
  private void notifyDownloadChanged(Download download) {
    if (foregroundNotificationUpdater != null) {
      if (needsStartedService(download.state)) {
        foregroundNotificationUpdater.startPeriodicUpdates();
      } else {
        foregroundNotificationUpdater.invalidate();
      }
    }
  }

  /** Called when a download is removed. */
  private void notifyDownloadRemoved() {
    if (foregroundNotificationUpdater != null) {
      foregroundNotificationUpdater.invalidate();
    }
  }

  /** Returns whether the service is stopped. */
  private boolean isStopped() {
    return isStopped;
  }

  private void onIdle() {
    if (foregroundNotificationUpdater != null) {
      // Whether the service remains started or not, we don't need periodic notification updates
      // when the DownloadManager is idle.
      foregroundNotificationUpdater.stopPeriodicUpdates();
    }

    if (!Assertions.checkNotNull(downloadManagerHelper).updateScheduler()) {
      // We failed to schedule the service to restart when requirements that the DownloadManager is
      // waiting for are met, so remain started.
      return;
    }

    // Stop the service, either because the DownloadManager is not waiting for requirements to be
    // met, or because we've scheduled the service to be restarted when they are.
    if (Util.SDK_INT < 28 && taskRemoved) { // See [Internal: b/74248644].
      stopSelf();
      isStopped = true;
    } else {
      isStopped |= stopSelfResult(lastStartId);
    }
  }

  private static boolean needsStartedService(@Download.State int state) {
    return state == Download.STATE_DOWNLOADING
        || state == Download.STATE_REMOVING
        || state == Download.STATE_RESTARTING;
  }

  private static Intent getIntent(
      Context context, Class<? extends DownloadService> clazz, String action, boolean foreground) {
    return getIntent(context, clazz, action).putExtra(KEY_FOREGROUND, foreground);
  }

  private static Intent getIntent(
      Context context, Class<? extends DownloadService> clazz, String action) {
    return new Intent(context, clazz).setAction(action);
  }

  private static void startService(Context context, Intent intent, boolean foreground) {
    if (foreground) {
      Util.startForegroundService(context, intent);
    } else {
      context.startService(intent);
    }
  }

  private final class ForegroundNotificationUpdater {

    private final int notificationId;
    private final long updateInterval;
    private final Handler handler;

    private boolean periodicUpdatesStarted;
    private boolean notificationDisplayed;

    public ForegroundNotificationUpdater(int notificationId, long updateInterval) {
      this.notificationId = notificationId;
      this.updateInterval = updateInterval;
      this.handler = new Handler(Looper.getMainLooper());
    }

    public void startPeriodicUpdates() {
      periodicUpdatesStarted = true;
      update();
    }

    public void stopPeriodicUpdates() {
      periodicUpdatesStarted = false;
      handler.removeCallbacksAndMessages(null);
    }

    public void showNotificationIfNotAlready() {
      if (!notificationDisplayed) {
        update();
      }
    }

    public void invalidate() {
      if (notificationDisplayed) {
        update();
      }
    }

    private void update() {
      DownloadManager downloadManager =
          Assertions.checkNotNull(downloadManagerHelper).downloadManager;
      List<Download> downloads = downloadManager.getCurrentDownloads();
      @RequirementFlags int notMetRequirements = downloadManager.getNotMetRequirements();
      Notification notification = getForegroundNotification(downloads, notMetRequirements);
      if (!notificationDisplayed) {
        startForeground(notificationId, notification);
        notificationDisplayed = true;
      } else {
        // Update the notification via NotificationManager rather than by repeatedly calling
        // startForeground, since the latter can cause ActivityManager log spam.
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
            .notify(notificationId, notification);
      }
      if (periodicUpdatesStarted) {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::update, updateInterval);
      }
    }
  }

  private static final class DownloadManagerHelper implements DownloadManager.Listener {

    private final Context context;
    private final DownloadManager downloadManager;
    private final boolean foregroundAllowed;
    @Nullable private final Scheduler scheduler;
    private final Class<? extends DownloadService> serviceClass;

    @Nullable private DownloadService downloadService;
    private @MonotonicNonNull Requirements scheduledRequirements;

    private DownloadManagerHelper(
        Context context,
        DownloadManager downloadManager,
        boolean foregroundAllowed,
        @Nullable Scheduler scheduler,
        Class<? extends DownloadService> serviceClass) {
      this.context = context;
      this.downloadManager = downloadManager;
      this.foregroundAllowed = foregroundAllowed;
      this.scheduler = scheduler;
      this.serviceClass = serviceClass;
      downloadManager.addListener(this);
      updateScheduler();
    }

    public void attachService(DownloadService downloadService) {
      Assertions.checkState(this.downloadService == null);
      this.downloadService = downloadService;
      if (downloadManager.isInitialized()) {
        // The call to DownloadService.notifyDownloads is posted to avoid it being called directly
        // from DownloadService.onCreate. This is a good idea because it may in turn call
        // DownloadService.getForegroundNotification, and concrete subclass implementations may
        // not anticipate the possibility of this method being called before their onCreate
        // implementation has finished executing.
        Util.createHandlerForCurrentOrMainLooper()
            .postAtFrontOfQueue(
                () -> downloadService.notifyDownloads(downloadManager.getCurrentDownloads()));
      }
    }

    public void detachService(DownloadService downloadService) {
      Assertions.checkState(this.downloadService == downloadService);
      this.downloadService = null;
    }

    /**
     * Schedules or cancels restarting the service, as needed for the current state.
     *
     * @return True if the DownloadManager is not waiting for requirements, or if it is waiting for
     *     requirements and the service has been successfully scheduled to be restarted when they
     *     are met. False if the DownloadManager is waiting for requirements and the service has not
     *     been scheduled for restart.
     */
    public boolean updateScheduler() {
      boolean waitingForRequirements = downloadManager.isWaitingForRequirements();
      if (scheduler == null) {
        return !waitingForRequirements;
      }

      if (!waitingForRequirements) {
        cancelScheduler();
        return true;
      }

      Requirements requirements = downloadManager.getRequirements();
      Requirements supportedRequirements = scheduler.getSupportedRequirements(requirements);
      if (!supportedRequirements.equals(requirements)) {
        cancelScheduler();
        return false;
      }

      if (!schedulerNeedsUpdate(requirements)) {
        return true;
      }

      String servicePackage = context.getPackageName();
      if (scheduler.schedule(requirements, servicePackage, ACTION_RESTART)) {
        scheduledRequirements = requirements;
        return true;
      } else {
        Log.w(TAG, "Failed to schedule restart");
        cancelScheduler();
        return false;
      }
    }

    // DownloadManager.Listener implementation.

    @Override
    public void onInitialized(DownloadManager downloadManager) {
      if (downloadService != null) {
        downloadService.notifyDownloads(downloadManager.getCurrentDownloads());
      }
    }

    @Override
    public void onDownloadChanged(
        DownloadManager downloadManager, Download download, @Nullable Exception finalException) {
      if (downloadService != null) {
        downloadService.notifyDownloadChanged(download);
      }
      if (serviceMayNeedRestart() && needsStartedService(download.state)) {
        // This shouldn't happen unless (a) application code is changing the downloads by calling
        // the DownloadManager directly rather than sending actions through the service, or (b) if
        // the service is background only and a previous attempt to start it was prevented. Try and
        // restart the service to robust against such cases.
        Log.w(TAG, "DownloadService wasn't running. Restarting.");
        restartService();
      }
    }

    @Override
    public void onDownloadRemoved(DownloadManager downloadManager, Download download) {
      if (downloadService != null) {
        downloadService.notifyDownloadRemoved();
      }
    }

    @Override
    public final void onIdle(DownloadManager downloadManager) {
      if (downloadService != null) {
        downloadService.onIdle();
      }
    }

    @Override
    public void onRequirementsStateChanged(
        DownloadManager downloadManager,
        Requirements requirements,
        @RequirementFlags int notMetRequirements) {
      updateScheduler();
    }

    @Override
    public void onWaitingForRequirementsChanged(
        DownloadManager downloadManager, boolean waitingForRequirements) {
      if (!waitingForRequirements
          && !downloadManager.getDownloadsPaused()
          && serviceMayNeedRestart()) {
        // We're no longer waiting for requirements and downloads aren't paused, meaning the manager
        // will be able to resume downloads that are currently queued. If there exist queued
        // downloads then we should ensure the service is started.
        List<Download> downloads = downloadManager.getCurrentDownloads();
        for (int i = 0; i < downloads.size(); i++) {
          if (downloads.get(i).state == Download.STATE_QUEUED) {
            restartService();
            return;
          }
        }
      }
    }

    // Internal methods.

    private boolean schedulerNeedsUpdate(Requirements requirements) {
      return !Util.areEqual(scheduledRequirements, requirements);
    }

    @RequiresNonNull("scheduler")
    private void cancelScheduler() {
      Requirements canceledRequirements = new Requirements(/* requirements= */ 0);
      if (schedulerNeedsUpdate(canceledRequirements)) {
        scheduler.cancel();
        scheduledRequirements = canceledRequirements;
      }
    }

    private boolean serviceMayNeedRestart() {
      return downloadService == null || downloadService.isStopped();
    }

    private void restartService() {
      if (foregroundAllowed) {
        try {
          Intent intent = getIntent(context, serviceClass, DownloadService.ACTION_RESTART);
          Util.startForegroundService(context, intent);
        } catch (IllegalStateException e) {
          // The process is running in the background, and is not allowed to start a foreground
          // service due to foreground service launch restrictions
          // (https://developer.android.com/about/versions/12/foreground-services).
          Log.w(TAG, "Failed to restart (foreground launch restriction)");
        }
      } else {
        // The service is background only. Use ACTION_INIT rather than ACTION_RESTART because
        // ACTION_RESTART is handled as though KEY_FOREGROUND is set to true.
        try {
          Intent intent = getIntent(context, serviceClass, DownloadService.ACTION_INIT);
          context.startService(intent);
        } catch (IllegalStateException e) {
          // The process is classed as idle by the platform. Starting a background service is not
          // allowed in this state.
          Log.w(TAG, "Failed to restart (process is idle)");
        }
      }
    }
  }
}
