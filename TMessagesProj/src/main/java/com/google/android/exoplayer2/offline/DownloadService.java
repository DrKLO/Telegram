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

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.util.Log;
import com.google.android.exoplayer2.offline.DownloadManager.TaskState;
import com.google.android.exoplayer2.scheduler.Requirements;
import com.google.android.exoplayer2.scheduler.RequirementsWatcher;
import com.google.android.exoplayer2.scheduler.Scheduler;
import com.google.android.exoplayer2.util.NotificationUtil;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;
import java.util.HashMap;

/** A {@link Service} for downloading media. */
public abstract class DownloadService extends Service {

  /** Starts a download service without adding a new {@link DownloadAction}. */
  public static final String ACTION_INIT =
      "com.google.android.exoplayer.downloadService.action.INIT";

  /** Starts a download service, adding a new {@link DownloadAction} to be executed. */
  public static final String ACTION_ADD = "com.google.android.exoplayer.downloadService.action.ADD";

  /** Reloads the download requirements. */
  public static final String ACTION_RELOAD_REQUIREMENTS =
      "com.google.android.exoplayer.downloadService.action.RELOAD_REQUIREMENTS";

  /** Like {@link #ACTION_INIT}, but with {@link #KEY_FOREGROUND} implicitly set to true. */
  private static final String ACTION_RESTART =
      "com.google.android.exoplayer.downloadService.action.RESTART";

  /** Key for the {@link DownloadAction} in an {@link #ACTION_ADD} intent. */
  public static final String KEY_DOWNLOAD_ACTION = "download_action";

  /** Invalid foreground notification id which can be used to run the service in the background. */
  public static final int FOREGROUND_NOTIFICATION_ID_NONE = 0;

  /**
   * Key for a boolean flag in any intent to indicate whether the service was started in the
   * foreground. If set, the service is guaranteed to call {@link #startForeground(int,
   * Notification)}.
   */
  public static final String KEY_FOREGROUND = "foreground";

  /** Default foreground notification update interval in milliseconds. */
  public static final long DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL = 1000;

  private static final String TAG = "DownloadService";
  private static final boolean DEBUG = false;

  // Keep the requirements helper for each DownloadService as long as there are tasks (and the
  // process is running). This allows tasks to resume when there's no scheduler. It may also allow
  // tasks the resume more quickly than when relying on the scheduler alone.
  private static final HashMap<Class<? extends DownloadService>, RequirementsHelper>
      requirementsHelpers = new HashMap<>();
  private static final Requirements DEFAULT_REQUIREMENTS =
      new Requirements(Requirements.NETWORK_TYPE_ANY, false, false);

  private final @Nullable ForegroundNotificationUpdater foregroundNotificationUpdater;
  private final @Nullable String channelId;
  private final @StringRes int channelName;

  private DownloadManager downloadManager;
  private DownloadManagerListener downloadManagerListener;
  private int lastStartId;
  private boolean startedInForeground;
  private boolean taskRemoved;

  /**
   * Creates a DownloadService.
   *
   * <p>If {@code foregroundNotificationId} is {@link #FOREGROUND_NOTIFICATION_ID_NONE} (value
   * {@value #FOREGROUND_NOTIFICATION_ID_NONE}) then the service runs in the background. No
   * foreground notification is displayed and {@link #getScheduler()} isn't called.
   *
   * <p>If {@code foregroundNotificationId} isn't {@link #FOREGROUND_NOTIFICATION_ID_NONE} (value
   * {@value #FOREGROUND_NOTIFICATION_ID_NONE}) the service runs in the foreground with {@link
   * #DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL}. In that case {@link
   * #getForegroundNotification(TaskState[])} should be overridden in the subclass.
   *
   * @param foregroundNotificationId The notification id for the foreground notification, or {@link
   *     #FOREGROUND_NOTIFICATION_ID_NONE} (value {@value #FOREGROUND_NOTIFICATION_ID_NONE})
   */
  protected DownloadService(int foregroundNotificationId) {
    this(foregroundNotificationId, DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL);
  }

  /**
   * Creates a DownloadService which will run in the foreground. {@link
   * #getForegroundNotification(TaskState[])} should be overridden in the subclass.
   *
   * @param foregroundNotificationId The notification id for the foreground notification, must not
   *     be 0.
   * @param foregroundNotificationUpdateInterval The maximum interval to update foreground
   *     notification, in milliseconds.
   */
  protected DownloadService(
      int foregroundNotificationId, long foregroundNotificationUpdateInterval) {
    this(
        foregroundNotificationId,
        foregroundNotificationUpdateInterval,
        /* channelId= */ null,
        /* channelName= */ 0);
  }

  /**
   * Creates a DownloadService which will run in the foreground. {@link
   * #getForegroundNotification(TaskState[])} should be overridden in the subclass.
   *
   * @param foregroundNotificationId The notification id for the foreground notification. Must not
   *     be 0.
   * @param foregroundNotificationUpdateInterval The maximum interval between updates to the
   *     foreground notification, in milliseconds.
   * @param channelId An id for a low priority notification channel to create, or {@code null} if
   *     the app will take care of creating a notification channel if needed. If specified, must be
   *     unique per package and the value may be truncated if it is too long.
   * @param channelName A string resource identifier for the user visible name of the channel, if
   *     {@code channelId} is specified. The recommended maximum length is 40 characters; the value
   *     may be truncated if it is too long.
   */
  protected DownloadService(
      int foregroundNotificationId,
      long foregroundNotificationUpdateInterval,
      @Nullable String channelId,
      @StringRes int channelName) {
    foregroundNotificationUpdater =
        foregroundNotificationId == 0
            ? null
            : new ForegroundNotificationUpdater(
                foregroundNotificationId, foregroundNotificationUpdateInterval);
    this.channelId = channelId;
    this.channelName = channelName;
  }

  /**
   * Builds an {@link Intent} for adding an action to be executed by the service.
   *
   * @param context A {@link Context}.
   * @param clazz The concrete download service being targeted by the intent.
   * @param downloadAction The action to be executed.
   * @param foreground Whether this intent will be used to start the service in the foreground.
   * @return Created Intent.
   */
  public static Intent buildAddActionIntent(
      Context context,
      Class<? extends DownloadService> clazz,
      DownloadAction downloadAction,
      boolean foreground) {
    return getIntent(context, clazz, ACTION_ADD)
        .putExtra(KEY_DOWNLOAD_ACTION, downloadAction.toByteArray())
        .putExtra(KEY_FOREGROUND, foreground);
  }

  /**
   * Starts the service, adding an action to be executed.
   *
   * @param context A {@link Context}.
   * @param clazz The concrete download service to be started.
   * @param downloadAction The action to be executed.
   * @param foreground Whether the service is started in the foreground.
   */
  public static void startWithAction(
      Context context,
      Class<? extends DownloadService> clazz,
      DownloadAction downloadAction,
      boolean foreground) {
    Intent intent = buildAddActionIntent(context, clazz, downloadAction, foreground);
    if (foreground) {
      Util.startForegroundService(context, intent);
    } else {
      context.startService(intent);
    }
  }

  /**
   * Starts the service without adding a new action. If there are any not finished actions and the
   * requirements are met, the service resumes executing actions. Otherwise it stops immediately.
   *
   * @param context A {@link Context}.
   * @param clazz The concrete download service to be started.
   * @see #startForeground(Context, Class)
   */
  public static void start(Context context, Class<? extends DownloadService> clazz) {
    context.startService(getIntent(context, clazz, ACTION_INIT));
  }

  /**
   * Starts the service in the foreground without adding a new action. If there are any not finished
   * actions and the requirements are met, the service resumes executing actions. Otherwise it stops
   * immediately.
   *
   * @param context A {@link Context}.
   * @param clazz The concrete download service to be started.
   * @see #start(Context, Class)
   */
  public static void startForeground(Context context, Class<? extends DownloadService> clazz) {
    Intent intent = getIntent(context, clazz, ACTION_INIT).putExtra(KEY_FOREGROUND, true);
    Util.startForegroundService(context, intent);
  }

  @Override
  public void onCreate() {
    logd("onCreate");
    if (channelId != null) {
      NotificationUtil.createNotificationChannel(
          this, channelId, channelName, NotificationUtil.IMPORTANCE_LOW);
    }
    downloadManager = getDownloadManager();
    downloadManagerListener = new DownloadManagerListener();
    downloadManager.addListener(downloadManagerListener);
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    lastStartId = startId;
    taskRemoved = false;
    String intentAction = null;
    if (intent != null) {
      intentAction = intent.getAction();
      startedInForeground |=
          intent.getBooleanExtra(KEY_FOREGROUND, false) || ACTION_RESTART.equals(intentAction);
    }
    // intentAction is null if the service is restarted or no action is specified.
    if (intentAction == null) {
      intentAction = ACTION_INIT;
    }
    logd("onStartCommand action: " + intentAction + " startId: " + startId);
    switch (intentAction) {
      case ACTION_INIT:
      case ACTION_RESTART:
        // Do nothing.
        break;
      case ACTION_ADD:
        byte[] actionData = intent.getByteArrayExtra(KEY_DOWNLOAD_ACTION);
        if (actionData == null) {
          Log.e(TAG, "Ignoring ADD action with no action data");
        } else {
          try {
            downloadManager.handleAction(actionData);
          } catch (IOException e) {
            Log.e(TAG, "Failed to handle ADD action", e);
          }
        }
        break;
      case ACTION_RELOAD_REQUIREMENTS:
        stopWatchingRequirements();
        break;
      default:
        Log.e(TAG, "Ignoring unrecognized action: " + intentAction);
        break;
    }

    Requirements requirements = getRequirements();
    if (requirements.checkRequirements(this)) {
      downloadManager.startDownloads();
    } else {
      downloadManager.stopDownloads();
    }
    maybeStartWatchingRequirements(requirements);

    if (downloadManager.isIdle()) {
      stop();
    }
    return START_STICKY;
  }

  @Override
  public void onTaskRemoved(Intent rootIntent) {
    logd("onTaskRemoved rootIntent: " + rootIntent);
    taskRemoved = true;
  }

  @Override
  public void onDestroy() {
    logd("onDestroy");
    if (foregroundNotificationUpdater != null) {
      foregroundNotificationUpdater.stopPeriodicUpdates();
    }
    downloadManager.removeListener(downloadManagerListener);
    maybeStopWatchingRequirements();
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  /**
   * Returns a {@link DownloadManager} to be used to downloaded content. Called only once in the
   * life cycle of the service. The service will call {@link DownloadManager#startDownloads()} and
   * {@link DownloadManager#stopDownloads} as necessary when requirements returned by {@link
   * #getRequirements()} are met or stop being met.
   */
  protected abstract DownloadManager getDownloadManager();

  /**
   * Returns a {@link Scheduler} to restart the service when requirements allowing downloads to take
   * place are met. If {@code null}, the service will only be restarted if the process is still in
   * memory when the requirements are met.
   */
  protected abstract @Nullable Scheduler getScheduler();

  /**
   * Returns requirements for downloads to take place. By default the only requirement is that the
   * device has network connectivity.
   */
  protected Requirements getRequirements() {
    return DEFAULT_REQUIREMENTS;
  }

  /**
   * Should be overridden in the subclass if the service will be run in the foreground.
   *
   * <p>Returns a notification to be displayed when this service running in the foreground.
   *
   * <p>This method is called when there is a task state change and periodically while there are
   * active tasks. The periodic update interval can be set using {@link #DownloadService(int,
   * long)}.
   *
   * <p>On API level 26 and above, this method may also be called just before the service stops,
   * with an empty {@code taskStates} array. The returned notification is used to satisfy system
   * requirements for foreground services.
   *
   * @param taskStates The states of all current tasks.
   * @return The foreground notification to display.
   */
  protected Notification getForegroundNotification(TaskState[] taskStates) {
    throw new IllegalStateException(
        getClass().getName()
            + " is started in the foreground but getForegroundNotification() is not implemented.");
  }

  /**
   * Called when the state of a task changes.
   *
   * @param taskState The state of the task.
   */
  protected void onTaskStateChanged(TaskState taskState) {
    // Do nothing.
  }

  private void maybeStartWatchingRequirements(Requirements requirements) {
    if (downloadManager.getDownloadCount() == 0) {
      return;
    }
    Class<? extends DownloadService> clazz = getClass();
    RequirementsHelper requirementsHelper = requirementsHelpers.get(clazz);
    if (requirementsHelper == null) {
      requirementsHelper = new RequirementsHelper(this, requirements, getScheduler(), clazz);
      requirementsHelpers.put(clazz, requirementsHelper);
      requirementsHelper.start();
      logd("started watching requirements");
    }
  }

  private void maybeStopWatchingRequirements() {
    if (downloadManager.getDownloadCount() > 0) {
      return;
    }
    stopWatchingRequirements();
  }

  private void stopWatchingRequirements() {
    RequirementsHelper requirementsHelper = requirementsHelpers.remove(getClass());
    if (requirementsHelper != null) {
      requirementsHelper.stop();
      logd("stopped watching requirements");
    }
  }

  private void stop() {
    if (foregroundNotificationUpdater != null) {
      foregroundNotificationUpdater.stopPeriodicUpdates();
      // Make sure startForeground is called before stopping. Workaround for [Internal: b/69424260].
      if (startedInForeground && Util.SDK_INT >= 26) {
        foregroundNotificationUpdater.showNotificationIfNotAlready();
      }
    }
    if (Util.SDK_INT < 28 && taskRemoved) { // See [Internal: b/74248644].
      stopSelf();
      logd("stopSelf()");
    } else {
      boolean stopSelfResult = stopSelfResult(lastStartId);
      logd("stopSelf(" + lastStartId + ") result: " + stopSelfResult);
    }
  }

  private void logd(String message) {
    if (DEBUG) {
      Log.d(TAG, message);
    }
  }

  private static Intent getIntent(
      Context context, Class<? extends DownloadService> clazz, String action) {
    return new Intent(context, clazz).setAction(action);
  }

  private final class DownloadManagerListener implements DownloadManager.Listener {
    @Override
    public void onInitialized(DownloadManager downloadManager) {
      maybeStartWatchingRequirements(getRequirements());
    }

    @Override
    public void onTaskStateChanged(DownloadManager downloadManager, TaskState taskState) {
      DownloadService.this.onTaskStateChanged(taskState);
      if (foregroundNotificationUpdater != null) {
        if (taskState.state == TaskState.STATE_STARTED) {
          foregroundNotificationUpdater.startPeriodicUpdates();
        } else {
          foregroundNotificationUpdater.update();
        }
      }
    }

    @Override
    public final void onIdle(DownloadManager downloadManager) {
      stop();
    }
  }

  private final class ForegroundNotificationUpdater implements Runnable {

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
      handler.removeCallbacks(this);
    }

    public void update() {
      TaskState[] taskStates = downloadManager.getAllTaskStates();
      startForeground(notificationId, getForegroundNotification(taskStates));
      notificationDisplayed = true;
      if (periodicUpdatesStarted) {
        handler.removeCallbacks(this);
        handler.postDelayed(this, updateInterval);
      }
    }

    public void showNotificationIfNotAlready() {
      if (!notificationDisplayed) {
        update();
      }
    }

    @Override
    public void run() {
      update();
    }
  }

  private static final class RequirementsHelper implements RequirementsWatcher.Listener {

    private final Context context;
    private final Requirements requirements;
    private final @Nullable Scheduler scheduler;
    private final Class<? extends DownloadService> serviceClass;
    private final RequirementsWatcher requirementsWatcher;

    private RequirementsHelper(
        Context context,
        Requirements requirements,
        @Nullable Scheduler scheduler,
        Class<? extends DownloadService> serviceClass) {
      this.context = context;
      this.requirements = requirements;
      this.scheduler = scheduler;
      this.serviceClass = serviceClass;
      requirementsWatcher = new RequirementsWatcher(context, this, requirements);
    }

    public void start() {
      requirementsWatcher.start();
    }

    public void stop() {
      requirementsWatcher.stop();
      if (scheduler != null) {
        scheduler.cancel();
      }
    }

    @Override
    public void requirementsMet(RequirementsWatcher requirementsWatcher) {
      try {
        notifyService();
      } catch (Exception e) {
        /* If we can't notify the service, don't stop the scheduler. */
        return;
      }
      if (scheduler != null) {
        scheduler.cancel();
      }
    }

    @Override
    public void requirementsNotMet(RequirementsWatcher requirementsWatcher) {
      try {
        notifyService();
      } catch (Exception e) {
        /* Do nothing. The service isn't running anyway. */
      }
      if (scheduler != null) {
        String servicePackage = context.getPackageName();
        boolean success = scheduler.schedule(requirements, servicePackage, ACTION_RESTART);
        if (!success) {
          Log.e(TAG, "Scheduling downloads failed.");
        }
      }
    }

    private void notifyService() throws Exception {
      Intent intent = getIntent(context, serviceClass, DownloadService.ACTION_INIT);
      try {
        context.startService(intent);
      } catch (IllegalStateException e) {
        /* startService will fail if the app is in the background and the service isn't running. */
        throw new Exception(e);
      }
    }
  }
}
