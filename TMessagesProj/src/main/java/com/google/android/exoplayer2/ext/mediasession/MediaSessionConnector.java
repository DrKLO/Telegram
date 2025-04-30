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
package com.google.android.exoplayer2.ext.mediasession;

import static androidx.media.utils.MediaConstants.PLAYBACK_STATE_EXTRAS_KEY_MEDIA_ID;
import static com.google.android.exoplayer2.Player.COMMAND_SEEK_BACK;
import static com.google.android.exoplayer2.Player.COMMAND_SEEK_FORWARD;
import static com.google.android.exoplayer2.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM;
import static com.google.android.exoplayer2.Player.EVENT_IS_PLAYING_CHANGED;
import static com.google.android.exoplayer2.Player.EVENT_PLAYBACK_PARAMETERS_CHANGED;
import static com.google.android.exoplayer2.Player.EVENT_PLAYBACK_STATE_CHANGED;
import static com.google.android.exoplayer2.Player.EVENT_PLAY_WHEN_READY_CHANGED;
import static com.google.android.exoplayer2.Player.EVENT_REPEAT_MODE_CHANGED;
import static com.google.android.exoplayer2.Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Pair;
import android.view.KeyEvent;
import androidx.annotation.LongDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.ErrorMessageProvider;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;

/**
 * Connects a {@link MediaSessionCompat} to a {@link Player}.
 *
 * <p>This connector does <em>not</em> call {@link MediaSessionCompat#setActive(boolean)}, and so
 * application code is responsible for making the session active when desired. A session must be
 * active for transport controls to be displayed (e.g. on the lock screen) and for it to receive
 * media button events.
 *
 * <p>The connector listens for actions sent by the media session's controller and implements these
 * actions by calling appropriate player methods. The playback state of the media session is
 * automatically synced with the player. The connector can also be optionally extended by providing
 * various collaborators:
 *
 * <ul>
 *   <li>Actions to initiate media playback ({@code PlaybackStateCompat#ACTION_PREPARE_*} and {@code
 *       PlaybackStateCompat#ACTION_PLAY_*}) can be handled by a {@link PlaybackPreparer} passed to
 *       {@link #setPlaybackPreparer(PlaybackPreparer)}.
 *   <li>Custom actions can be handled by passing one or more {@link CustomActionProvider}s to
 *       {@link #setCustomActionProviders(CustomActionProvider...)}.
 *   <li>To enable a media queue and navigation within it, you can set a {@link QueueNavigator} by
 *       calling {@link #setQueueNavigator(QueueNavigator)}. Use of {@link TimelineQueueNavigator}
 *       is recommended for most use cases.
 *   <li>To enable editing of the media queue, you can set a {@link QueueEditor} by calling {@link
 *       #setQueueEditor(QueueEditor)}.
 *   <li>A {@link MediaButtonEventHandler} can be set by calling {@link
 *       #setMediaButtonEventHandler(MediaButtonEventHandler)}. By default media button events are
 *       handled by {@link MediaSessionCompat.Callback#onMediaButtonEvent(Intent)}.
 *   <li>An {@link ErrorMessageProvider} for providing human readable error messages and
 *       corresponding error codes can be set by calling {@link
 *       #setErrorMessageProvider(ErrorMessageProvider)}.
 *   <li>A {@link MediaMetadataProvider} can be set by calling {@link
 *       #setMediaMetadataProvider(MediaMetadataProvider)}. By default the {@link
 *       DefaultMediaMetadataProvider} is used.
 * </ul>
 */
public final class MediaSessionConnector {

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.mediasession");
  }

  /** Playback actions supported by the connector. */
  @LongDef(
      flag = true,
      value = {
        PlaybackStateCompat.ACTION_PLAY_PAUSE,
        PlaybackStateCompat.ACTION_PLAY,
        PlaybackStateCompat.ACTION_PAUSE,
        PlaybackStateCompat.ACTION_SEEK_TO,
        PlaybackStateCompat.ACTION_FAST_FORWARD,
        PlaybackStateCompat.ACTION_REWIND,
        PlaybackStateCompat.ACTION_STOP,
        PlaybackStateCompat.ACTION_SET_REPEAT_MODE,
        PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE,
        PlaybackStateCompat.ACTION_SET_PLAYBACK_SPEED
      })
  @Retention(RetentionPolicy.SOURCE)
  public @interface PlaybackActions {}

  @PlaybackActions
  public static final long ALL_PLAYBACK_ACTIONS =
      PlaybackStateCompat.ACTION_PLAY_PAUSE
          | PlaybackStateCompat.ACTION_PLAY
          | PlaybackStateCompat.ACTION_PAUSE
          | PlaybackStateCompat.ACTION_SEEK_TO
          | PlaybackStateCompat.ACTION_FAST_FORWARD
          | PlaybackStateCompat.ACTION_REWIND
          | PlaybackStateCompat.ACTION_STOP
          | PlaybackStateCompat.ACTION_SET_REPEAT_MODE
          | PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE
          | PlaybackStateCompat.ACTION_SET_PLAYBACK_SPEED;

  /** The default playback actions. */
  @PlaybackActions
  public static final long DEFAULT_PLAYBACK_ACTIONS =
      ALL_PLAYBACK_ACTIONS - PlaybackStateCompat.ACTION_SET_PLAYBACK_SPEED;

  /**
   * The name of the {@link PlaybackStateCompat} float extra with the value of {@code
   * Player.getPlaybackParameters().speed}.
   */
  public static final String EXTRAS_SPEED = "EXO_SPEED";

  private static final long BASE_PLAYBACK_ACTIONS =
      PlaybackStateCompat.ACTION_PLAY_PAUSE
          | PlaybackStateCompat.ACTION_PLAY
          | PlaybackStateCompat.ACTION_PAUSE
          | PlaybackStateCompat.ACTION_STOP
          | PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE
          | PlaybackStateCompat.ACTION_SET_REPEAT_MODE
          | PlaybackStateCompat.ACTION_SET_PLAYBACK_SPEED;
  private static final int BASE_MEDIA_SESSION_FLAGS =
      MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
          | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS;
  private static final int EDITOR_MEDIA_SESSION_FLAGS =
      BASE_MEDIA_SESSION_FLAGS | MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS;

  private static final MediaMetadataCompat METADATA_EMPTY =
      new MediaMetadataCompat.Builder().build();

  /** Receiver of media commands sent by a media controller. */
  public interface CommandReceiver {
    /**
     * See {@link MediaSessionCompat.Callback#onCommand(String, Bundle, ResultReceiver)}. The
     * receiver may handle the command, but is not required to do so.
     *
     * @param player The player connected to the media session.
     * @param command The command name.
     * @param extras Optional parameters for the command, may be null.
     * @param cb A result receiver to which a result may be sent by the command, may be null.
     * @return Whether the receiver handled the command.
     */
    boolean onCommand(
        Player player, String command, @Nullable Bundle extras, @Nullable ResultReceiver cb);
  }

  /** Interface to which playback preparation and play actions are delegated. */
  public interface PlaybackPreparer extends CommandReceiver {

    long ACTIONS =
        PlaybackStateCompat.ACTION_PREPARE
            | PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID
            | PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH
            | PlaybackStateCompat.ACTION_PREPARE_FROM_URI
            | PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
            | PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
            | PlaybackStateCompat.ACTION_PLAY_FROM_URI;

    /**
     * Returns the actions which are supported by the preparer. The supported actions must be a
     * bitmask combined out of {@link PlaybackStateCompat#ACTION_PREPARE}, {@link
     * PlaybackStateCompat#ACTION_PREPARE_FROM_MEDIA_ID}, {@link
     * PlaybackStateCompat#ACTION_PREPARE_FROM_SEARCH}, {@link
     * PlaybackStateCompat#ACTION_PREPARE_FROM_URI}, {@link
     * PlaybackStateCompat#ACTION_PLAY_FROM_MEDIA_ID}, {@link
     * PlaybackStateCompat#ACTION_PLAY_FROM_SEARCH} and {@link
     * PlaybackStateCompat#ACTION_PLAY_FROM_URI}.
     *
     * @return The bitmask of the supported media actions.
     */
    long getSupportedPrepareActions();
    /**
     * See {@link MediaSessionCompat.Callback#onPrepare()}.
     *
     * @param playWhenReady Whether playback should be started after preparation.
     */
    void onPrepare(boolean playWhenReady);
    /**
     * See {@link MediaSessionCompat.Callback#onPrepareFromMediaId(String, Bundle)}.
     *
     * @param mediaId The media id of the media item to be prepared.
     * @param playWhenReady Whether playback should be started after preparation.
     * @param extras A {@link Bundle} of extras passed by the media controller, may be null.
     */
    void onPrepareFromMediaId(String mediaId, boolean playWhenReady, @Nullable Bundle extras);
    /**
     * See {@link MediaSessionCompat.Callback#onPrepareFromSearch(String, Bundle)}.
     *
     * @param query The search query.
     * @param playWhenReady Whether playback should be started after preparation.
     * @param extras A {@link Bundle} of extras passed by the media controller, may be null.
     */
    void onPrepareFromSearch(String query, boolean playWhenReady, @Nullable Bundle extras);
    /**
     * See {@link MediaSessionCompat.Callback#onPrepareFromUri(Uri, Bundle)}.
     *
     * @param uri The {@link Uri} of the media item to be prepared.
     * @param playWhenReady Whether playback should be started after preparation.
     * @param extras A {@link Bundle} of extras passed by the media controller, may be null.
     */
    void onPrepareFromUri(Uri uri, boolean playWhenReady, @Nullable Bundle extras);
  }

  /**
   * Handles queue navigation actions, and updates the media session queue by calling {@code
   * MediaSessionCompat.setQueue()}.
   */
  public interface QueueNavigator extends CommandReceiver {

    long ACTIONS =
        PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM
            | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;

    /**
     * Returns the actions which are supported by the navigator. The supported actions must be a
     * bitmask combined out of {@link PlaybackStateCompat#ACTION_SKIP_TO_QUEUE_ITEM}, {@link
     * PlaybackStateCompat#ACTION_SKIP_TO_NEXT}, {@link
     * PlaybackStateCompat#ACTION_SKIP_TO_PREVIOUS}.
     *
     * @param player The player connected to the media session.
     * @return The bitmask of the supported media actions.
     */
    long getSupportedQueueNavigatorActions(Player player);
    /**
     * Called when the timeline of the player has changed.
     *
     * @param player The player connected to the media session.
     */
    void onTimelineChanged(Player player);

    /**
     * Called when the current media item index changed.
     *
     * @param player The player connected to the media session.
     */
    default void onCurrentMediaItemIndexChanged(Player player) {}
    /**
     * Gets the id of the currently active queue item, or {@link
     * MediaSessionCompat.QueueItem#UNKNOWN_ID} if the active item is unknown.
     *
     * <p>To let the connector publish metadata for the active queue item, the queue item with the
     * returned id must be available in the list of items returned by {@link
     * MediaControllerCompat#getQueue()}.
     *
     * @param player The player connected to the media session.
     * @return The id of the active queue item.
     */
    long getActiveQueueItemId(@Nullable Player player);
    /**
     * See {@link MediaSessionCompat.Callback#onSkipToPrevious()}.
     *
     * @param player The player connected to the media session.
     */
    void onSkipToPrevious(Player player);
    /**
     * See {@link MediaSessionCompat.Callback#onSkipToQueueItem(long)}.
     *
     * @param player The player connected to the media session.
     */
    void onSkipToQueueItem(Player player, long id);
    /**
     * See {@link MediaSessionCompat.Callback#onSkipToNext()}.
     *
     * @param player The player connected to the media session.
     */
    void onSkipToNext(Player player);
  }

  /** Handles media session queue edits. */
  public interface QueueEditor extends CommandReceiver {

    /**
     * See {@link MediaSessionCompat.Callback#onAddQueueItem(MediaDescriptionCompat description)}.
     */
    void onAddQueueItem(Player player, MediaDescriptionCompat description);
    /**
     * See {@link MediaSessionCompat.Callback#onAddQueueItem(MediaDescriptionCompat description, int
     * index)}.
     */
    void onAddQueueItem(Player player, MediaDescriptionCompat description, int index);
    /**
     * See {@link MediaSessionCompat.Callback#onRemoveQueueItem(MediaDescriptionCompat
     * description)}.
     */
    void onRemoveQueueItem(Player player, MediaDescriptionCompat description);
  }

  /** Callback receiving a user rating for the active media item. */
  public interface RatingCallback extends CommandReceiver {

    /** See {@link MediaSessionCompat.Callback#onSetRating(RatingCompat)}. */
    void onSetRating(Player player, RatingCompat rating);

    /** See {@link MediaSessionCompat.Callback#onSetRating(RatingCompat, Bundle)}. */
    void onSetRating(Player player, RatingCompat rating, @Nullable Bundle extras);
  }

  /** Handles requests for enabling or disabling captions. */
  public interface CaptionCallback extends CommandReceiver {

    /** See {@link MediaSessionCompat.Callback#onSetCaptioningEnabled(boolean)}. */
    void onSetCaptioningEnabled(Player player, boolean enabled);

    /**
     * Returns whether the media currently being played has captions.
     *
     * <p>This method is called each time the media session playback state needs to be updated and
     * published upon a player state change.
     */
    boolean hasCaptions(Player player);
  }

  /** Handles a media button event. */
  public interface MediaButtonEventHandler {
    /**
     * See {@link MediaSessionCompat.Callback#onMediaButtonEvent(Intent)}.
     *
     * @param player The {@link Player}.
     * @param mediaButtonEvent The {@link Intent}.
     * @return True if the event was handled, false otherwise.
     */
    boolean onMediaButtonEvent(Player player, Intent mediaButtonEvent);
  }

  /**
   * Provides a {@link PlaybackStateCompat.CustomAction} to be published and handles the action when
   * sent by a media controller.
   */
  public interface CustomActionProvider {
    /**
     * Called when a custom action provided by this provider is sent to the media session.
     *
     * @param player The player connected to the media session.
     * @param action The name of the action which was sent by a media controller.
     * @param extras Optional extras sent by a media controller, may be null.
     */
    void onCustomAction(Player player, String action, @Nullable Bundle extras);

    /**
     * Returns a {@link PlaybackStateCompat.CustomAction} which will be published to the media
     * session by the connector or {@code null} if this action should not be published at the given
     * player state.
     *
     * @param player The player connected to the media session.
     * @return The custom action to be included in the session playback state or {@code null}.
     */
    @Nullable
    PlaybackStateCompat.CustomAction getCustomAction(Player player);
  }

  /** Provides a {@link MediaMetadataCompat} for a given player state. */
  public interface MediaMetadataProvider {
    /**
     * Gets the {@link MediaMetadataCompat} to be published to the session.
     *
     * <p>An app may need to load metadata resources like artwork bitmaps asynchronously. In such a
     * case the app should return a {@link MediaMetadataCompat} object that does not contain these
     * resources as a placeholder. The app should start an asynchronous operation to download the
     * bitmap and put it into a cache. Finally, the app should call {@link
     * #invalidateMediaSessionMetadata()}. This causes this callback to be called again and the app
     * can now return a {@link MediaMetadataCompat} object with all the resources included.
     *
     * @param player The player connected to the media session.
     * @return The {@link MediaMetadataCompat} to be published to the session.
     */
    MediaMetadataCompat getMetadata(Player player);

    /** Returns whether the old and the new metadata are considered the same. */
    default boolean sameAs(MediaMetadataCompat oldMetadata, MediaMetadataCompat newMetadata) {
      if (oldMetadata == newMetadata) {
        return true;
      }
      if (oldMetadata.size() != newMetadata.size()) {
        return false;
      }
      Set<String> oldKeySet = oldMetadata.keySet();
      Bundle oldMetadataBundle = oldMetadata.getBundle();
      Bundle newMetadataBundle = newMetadata.getBundle();
      for (String key : oldKeySet) {
        Object oldProperty = oldMetadataBundle.get(key);
        Object newProperty = newMetadataBundle.get(key);
        if (oldProperty == newProperty) {
          continue;
        }
        if (oldProperty instanceof Bitmap && newProperty instanceof Bitmap) {
          if (!((Bitmap) oldProperty).sameAs(((Bitmap) newProperty))) {
            return false;
          }
        } else if (oldProperty instanceof RatingCompat && newProperty instanceof RatingCompat) {
          RatingCompat oldRating = (RatingCompat) oldProperty;
          RatingCompat newRating = (RatingCompat) newProperty;
          if (oldRating.hasHeart() != newRating.hasHeart()
              || oldRating.isRated() != newRating.isRated()
              || oldRating.isThumbUp() != newRating.isThumbUp()
              || oldRating.getPercentRating() != newRating.getPercentRating()
              || oldRating.getStarRating() != newRating.getStarRating()
              || oldRating.getRatingStyle() != newRating.getRatingStyle()) {
            return false;
          }
        } else if (!Util.areEqual(oldProperty, newProperty)) {
          return false;
        }
      }
      return true;
    }
  }

  /** The wrapped {@link MediaSessionCompat}. */
  public final MediaSessionCompat mediaSession;

  private final Looper looper;
  private final ComponentListener componentListener;
  private final ArrayList<CommandReceiver> commandReceivers;
  private final ArrayList<CommandReceiver> customCommandReceivers;

  private CustomActionProvider[] customActionProviders;
  private Map<String, CustomActionProvider> customActionMap;
  @Nullable private MediaMetadataProvider mediaMetadataProvider;
  @Nullable private Player player;
  @Nullable private ErrorMessageProvider<? super PlaybackException> errorMessageProvider;
  @Nullable private Pair<Integer, CharSequence> customError;
  @Nullable private Bundle customErrorExtras;
  @Nullable private PlaybackPreparer playbackPreparer;
  @Nullable private QueueNavigator queueNavigator;
  @Nullable private QueueEditor queueEditor;
  @Nullable private RatingCallback ratingCallback;
  @Nullable private CaptionCallback captionCallback;
  @Nullable private MediaButtonEventHandler mediaButtonEventHandler;

  private long enabledPlaybackActions;
  private boolean metadataDeduplicationEnabled;
  private boolean dispatchUnsupportedActionsEnabled;
  private boolean clearMediaItemsOnStop;
  private boolean mapIdleToStopped;

  /**
   * Creates an instance.
   *
   * @param mediaSession The {@link MediaSessionCompat} to connect to.
   */
  public MediaSessionConnector(MediaSessionCompat mediaSession) {
    this.mediaSession = mediaSession;
    looper = Util.getCurrentOrMainLooper();
    componentListener = new ComponentListener();
    commandReceivers = new ArrayList<>();
    customCommandReceivers = new ArrayList<>();
    customActionProviders = new CustomActionProvider[0];
    customActionMap = Collections.emptyMap();
    mediaMetadataProvider =
        new DefaultMediaMetadataProvider(
            mediaSession.getController(), /* metadataExtrasPrefix= */ null);
    enabledPlaybackActions = DEFAULT_PLAYBACK_ACTIONS;
    mediaSession.setFlags(BASE_MEDIA_SESSION_FLAGS);
    mediaSession.setCallback(componentListener, new Handler(looper));
    clearMediaItemsOnStop = true;
  }

  /**
   * Sets the player to be connected to the media session. Must be called on the same thread that is
   * used to access the player.
   *
   * @param player The player to be connected to the {@code MediaSession}, or {@code null} to
   *     disconnect the current player.
   */
  public void setPlayer(@Nullable Player player) {
    Assertions.checkArgument(player == null || player.getApplicationLooper() == looper);
    if (this.player != null) {
      this.player.removeListener(componentListener);
    }
    this.player = player;
    if (player != null) {
      player.addListener(componentListener);
    }
    invalidateMediaSessionPlaybackState();
    invalidateMediaSessionMetadata();
  }

  /**
   * Sets the {@link PlaybackPreparer}.
   *
   * @param playbackPreparer The {@link PlaybackPreparer}.
   */
  public void setPlaybackPreparer(@Nullable PlaybackPreparer playbackPreparer) {
    if (this.playbackPreparer != playbackPreparer) {
      unregisterCommandReceiver(this.playbackPreparer);
      this.playbackPreparer = playbackPreparer;
      registerCommandReceiver(playbackPreparer);
      invalidateMediaSessionPlaybackState();
    }
  }

  /**
   * Sets the {@link MediaButtonEventHandler}. Pass {@code null} if the media button event should be
   * handled by {@link MediaSessionCompat.Callback#onMediaButtonEvent(Intent)}.
   *
   * <p>Please note that prior to API 21 MediaButton events are not delivered to the {@link
   * MediaSessionCompat}. Instead they are delivered as key events (see <a
   * href="https://developer.android.com/guide/topics/media-apps/mediabuttons">'Responding to media
   * buttons'</a>). In an {@link android.app.Activity Activity}, media button events arrive at the
   * {@link android.app.Activity#dispatchKeyEvent(KeyEvent)} method.
   *
   * <p>If you are running the player in a foreground service (prior to API 21), you can create an
   * intent filter and handle the {@code android.intent.action.MEDIA_BUTTON} action yourself. See <a
   * href="https://developer.android.com/reference/androidx/media/session/MediaButtonReceiver#service-handling-action_media_button">
   * Service handling ACTION_MEDIA_BUTTON</a> for more information.
   *
   * @param mediaButtonEventHandler The {@link MediaButtonEventHandler}, or null to let the event be
   *     handled by {@link MediaSessionCompat.Callback#onMediaButtonEvent(Intent)}.
   */
  public void setMediaButtonEventHandler(
      @Nullable MediaButtonEventHandler mediaButtonEventHandler) {
    this.mediaButtonEventHandler = mediaButtonEventHandler;
  }

  /**
   * Sets the enabled playback actions.
   *
   * @param enabledPlaybackActions The enabled playback actions.
   */
  public void setEnabledPlaybackActions(@PlaybackActions long enabledPlaybackActions) {
    enabledPlaybackActions &= ALL_PLAYBACK_ACTIONS;
    if (this.enabledPlaybackActions != enabledPlaybackActions) {
      this.enabledPlaybackActions = enabledPlaybackActions;
      invalidateMediaSessionPlaybackState();
    }
  }

  /**
   * Sets the optional {@link ErrorMessageProvider}.
   *
   * @param errorMessageProvider The error message provider.
   */
  public void setErrorMessageProvider(
      @Nullable ErrorMessageProvider<? super PlaybackException> errorMessageProvider) {
    if (this.errorMessageProvider != errorMessageProvider) {
      this.errorMessageProvider = errorMessageProvider;
      invalidateMediaSessionPlaybackState();
    }
  }

  /**
   * Sets the {@link QueueNavigator} to handle queue navigation actions {@code ACTION_SKIP_TO_NEXT},
   * {@code ACTION_SKIP_TO_PREVIOUS} and {@code ACTION_SKIP_TO_QUEUE_ITEM}.
   *
   * @param queueNavigator The queue navigator.
   */
  public void setQueueNavigator(@Nullable QueueNavigator queueNavigator) {
    if (this.queueNavigator != queueNavigator) {
      unregisterCommandReceiver(this.queueNavigator);
      this.queueNavigator = queueNavigator;
      registerCommandReceiver(queueNavigator);
    }
  }

  /**
   * Sets the {@link QueueEditor} to handle queue edits sent by the media controller.
   *
   * @param queueEditor The queue editor.
   */
  public void setQueueEditor(@Nullable QueueEditor queueEditor) {
    if (this.queueEditor != queueEditor) {
      unregisterCommandReceiver(this.queueEditor);
      this.queueEditor = queueEditor;
      registerCommandReceiver(queueEditor);
      mediaSession.setFlags(
          queueEditor == null ? BASE_MEDIA_SESSION_FLAGS : EDITOR_MEDIA_SESSION_FLAGS);
    }
  }

  /**
   * Sets the {@link RatingCallback} to handle user ratings.
   *
   * @param ratingCallback The rating callback.
   */
  public void setRatingCallback(@Nullable RatingCallback ratingCallback) {
    if (this.ratingCallback != ratingCallback) {
      unregisterCommandReceiver(this.ratingCallback);
      this.ratingCallback = ratingCallback;
      registerCommandReceiver(this.ratingCallback);
    }
  }

  /**
   * Sets the {@link CaptionCallback} to handle requests to enable or disable captions.
   *
   * @param captionCallback The caption callback.
   */
  public void setCaptionCallback(@Nullable CaptionCallback captionCallback) {
    if (this.captionCallback != captionCallback) {
      unregisterCommandReceiver(this.captionCallback);
      this.captionCallback = captionCallback;
      registerCommandReceiver(this.captionCallback);
    }
  }

  /**
   * Sets a custom error on the session.
   *
   * <p>This sets the error code via {@link PlaybackStateCompat.Builder#setErrorMessage(int,
   * CharSequence)}. By default, the error code will be set to {@link
   * PlaybackStateCompat#ERROR_CODE_APP_ERROR}.
   *
   * @param message The error string to report or {@code null} to clear the error.
   */
  public void setCustomErrorMessage(@Nullable CharSequence message) {
    int code = (message == null) ? 0 : PlaybackStateCompat.ERROR_CODE_APP_ERROR;
    setCustomErrorMessage(message, code);
  }

  /**
   * Sets a custom error on the session.
   *
   * @param message The error string to report or {@code null} to clear the error.
   * @param code The error code to report. Ignored when {@code message} is {@code null}.
   */
  public void setCustomErrorMessage(@Nullable CharSequence message, int code) {
    setCustomErrorMessage(message, code, /* extras= */ null);
  }

  /**
   * Sets a custom error on the session.
   *
   * @param message The error string to report or {@code null} to clear the error.
   * @param code The error code to report. Ignored when {@code message} is {@code null}.
   * @param extras Extras to include in reported {@link PlaybackStateCompat}.
   */
  public void setCustomErrorMessage(
      @Nullable CharSequence message, int code, @Nullable Bundle extras) {
    customError = (message == null) ? null : new Pair<>(code, message);
    customErrorExtras = (message == null) ? null : extras;
    invalidateMediaSessionPlaybackState();
  }

  /**
   * Sets custom action providers. The order of the {@link CustomActionProvider}s determines the
   * order in which the actions are published.
   *
   * @param customActionProviders The custom action providers, or null to remove all existing custom
   *     action providers.
   */
  public void setCustomActionProviders(@Nullable CustomActionProvider... customActionProviders) {
    this.customActionProviders =
        customActionProviders == null ? new CustomActionProvider[0] : customActionProviders;
    invalidateMediaSessionPlaybackState();
  }

  /**
   * Sets a provider of metadata to be published to the media session. Pass {@code null} if no
   * metadata should be published.
   *
   * @param mediaMetadataProvider The provider of metadata to publish, or {@code null} if no
   *     metadata should be published.
   */
  public void setMediaMetadataProvider(@Nullable MediaMetadataProvider mediaMetadataProvider) {
    if (this.mediaMetadataProvider != mediaMetadataProvider) {
      this.mediaMetadataProvider = mediaMetadataProvider;
      invalidateMediaSessionMetadata();
    }
  }

  /**
   * Sets whether actions that are not advertised to the {@link MediaSessionCompat} will be
   * dispatched either way. Default value is false.
   */
  public void setDispatchUnsupportedActionsEnabled(boolean dispatchUnsupportedActionsEnabled) {
    this.dispatchUnsupportedActionsEnabled = dispatchUnsupportedActionsEnabled;
  }

  /**
   * Sets whether media items are cleared from the playlist when a client sends a {@link
   * MediaControllerCompat.TransportControls#stop()} command.
   */
  public void setClearMediaItemsOnStop(boolean clearMediaItemsOnStop) {
    this.clearMediaItemsOnStop = clearMediaItemsOnStop;
  }

  /**
   * Sets whether {@link Player#STATE_IDLE} should be mapped to {@link
   * PlaybackStateCompat#STATE_STOPPED}. The default is false {@link Player#STATE_IDLE} which maps
   * to {@link PlaybackStateCompat#STATE_NONE}.
   */
  public void setMapStateIdleToSessionStateStopped(boolean mapIdleToStopped) {
    this.mapIdleToStopped = mapIdleToStopped;
  }

  /**
   * Sets whether {@link MediaMetadataProvider#sameAs(MediaMetadataCompat, MediaMetadataCompat)}
   * should be consulted before calling {@link MediaSessionCompat#setMetadata(MediaMetadataCompat)}.
   *
   * <p>Note that this comparison is normally only required when you are using media sources that
   * may introduce duplicate updates of the metadata for the same media item (e.g. live streams).
   *
   * @param metadataDeduplicationEnabled Whether to deduplicate metadata objects on invalidation.
   */
  public void setMetadataDeduplicationEnabled(boolean metadataDeduplicationEnabled) {
    this.metadataDeduplicationEnabled = metadataDeduplicationEnabled;
  }

  /**
   * Updates the metadata of the media session.
   *
   * <p>Apps normally only need to call this method when the backing data for a given media item has
   * changed and the metadata should be updated immediately.
   *
   * <p>The {@link MediaMetadataCompat} which is published to the session is obtained by calling
   * {@link MediaMetadataProvider#getMetadata(Player)}.
   */
  public final void invalidateMediaSessionMetadata() {
    MediaMetadataCompat metadata =
        mediaMetadataProvider != null && player != null
            ? mediaMetadataProvider.getMetadata(player)
            : METADATA_EMPTY;
    @Nullable MediaMetadataProvider mediaMetadataProvider = this.mediaMetadataProvider;
    if (metadataDeduplicationEnabled && mediaMetadataProvider != null) {
      @Nullable MediaMetadataCompat oldMetadata = mediaSession.getController().getMetadata();
      if (oldMetadata != null && mediaMetadataProvider.sameAs(oldMetadata, metadata)) {
        // Do not update if metadata did not change.
        return;
      }
    }
    mediaSession.setMetadata(metadata);
  }

  /**
   * Updates the playback state of the media session.
   *
   * <p>Apps normally only need to call this method when the custom actions provided by a {@link
   * CustomActionProvider} changed and the playback state needs to be updated immediately.
   */
  public final void invalidateMediaSessionPlaybackState() {
    PlaybackStateCompat.Builder builder = new PlaybackStateCompat.Builder();
    @Nullable Player player = this.player;
    if (player == null) {
      builder
          .setActions(buildPrepareActions())
          .setState(
              PlaybackStateCompat.STATE_NONE,
              /* position= */ 0,
              /* playbackSpeed= */ 0,
              /* updateTime= */ SystemClock.elapsedRealtime());

      mediaSession.setRepeatMode(PlaybackStateCompat.REPEAT_MODE_NONE);
      mediaSession.setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_NONE);
      mediaSession.setPlaybackState(builder.build());
      return;
    }

    Map<String, CustomActionProvider> currentActions = new HashMap<>();
    for (CustomActionProvider customActionProvider : customActionProviders) {
      @Nullable
      PlaybackStateCompat.CustomAction customAction = customActionProvider.getCustomAction(player);
      if (customAction != null) {
        currentActions.put(customAction.getAction(), customActionProvider);
        builder.addCustomAction(customAction);
      }
    }
    customActionMap = Collections.unmodifiableMap(currentActions);

    Bundle extras = new Bundle();
    @Nullable PlaybackException playbackError = player.getPlayerError();
    boolean reportError = playbackError != null || customError != null;
    int sessionPlaybackState =
        reportError
            ? PlaybackStateCompat.STATE_ERROR
            : getMediaSessionPlaybackState(player.getPlaybackState(), player.getPlayWhenReady());
    if (customError != null) {
      builder.setErrorMessage(customError.first, customError.second);
      if (customErrorExtras != null) {
        extras.putAll(customErrorExtras);
      }
    } else if (playbackError != null && errorMessageProvider != null) {
      Pair<Integer, String> message = errorMessageProvider.getErrorMessage(playbackError);
      builder.setErrorMessage(message.first, message.second);
    }
    long activeQueueItemId =
        queueNavigator != null
            ? queueNavigator.getActiveQueueItemId(player)
            : MediaSessionCompat.QueueItem.UNKNOWN_ID;
    float playbackSpeed = player.getPlaybackParameters().speed;
    extras.putFloat(EXTRAS_SPEED, playbackSpeed);
    float sessionPlaybackSpeed = player.isPlaying() ? playbackSpeed : 0f;
    @Nullable MediaItem currentMediaItem = player.getCurrentMediaItem();
    if (currentMediaItem != null && !MediaItem.DEFAULT_MEDIA_ID.equals(currentMediaItem.mediaId)) {
      extras.putString(PLAYBACK_STATE_EXTRAS_KEY_MEDIA_ID, currentMediaItem.mediaId);
    }
    builder
        .setActions(buildPrepareActions() | buildPlaybackActions(player))
        .setActiveQueueItemId(activeQueueItemId)
        .setBufferedPosition(player.getBufferedPosition())
        .setState(
            sessionPlaybackState,
            player.getCurrentPosition(),
            sessionPlaybackSpeed,
            /* updateTime= */ SystemClock.elapsedRealtime())
        .setExtras(extras);

    @Player.RepeatMode int repeatMode = player.getRepeatMode();
    mediaSession.setRepeatMode(
        repeatMode == Player.REPEAT_MODE_ONE
            ? PlaybackStateCompat.REPEAT_MODE_ONE
            : repeatMode == Player.REPEAT_MODE_ALL
                ? PlaybackStateCompat.REPEAT_MODE_ALL
                : PlaybackStateCompat.REPEAT_MODE_NONE);
    mediaSession.setShuffleMode(
        player.getShuffleModeEnabled()
            ? PlaybackStateCompat.SHUFFLE_MODE_ALL
            : PlaybackStateCompat.SHUFFLE_MODE_NONE);
    mediaSession.setPlaybackState(builder.build());
  }

  /**
   * Updates the queue of the media session by calling {@link
   * QueueNavigator#onTimelineChanged(Player)}.
   *
   * <p>Apps normally only need to call this method when the backing data for a given queue item has
   * changed and the queue should be updated immediately.
   */
  public final void invalidateMediaSessionQueue() {
    if (queueNavigator != null && player != null) {
      queueNavigator.onTimelineChanged(player);
    }
  }

  /**
   * Registers a custom command receiver for responding to commands delivered via {@link
   * MediaSessionCompat.Callback#onCommand(String, Bundle, ResultReceiver)}.
   *
   * <p>Commands are only dispatched to this receiver when a player is connected.
   *
   * @param commandReceiver The command receiver to register.
   */
  public void registerCustomCommandReceiver(@Nullable CommandReceiver commandReceiver) {
    if (commandReceiver != null && !customCommandReceivers.contains(commandReceiver)) {
      customCommandReceivers.add(commandReceiver);
    }
  }

  /**
   * Unregisters a previously registered custom command receiver.
   *
   * @param commandReceiver The command receiver to unregister.
   */
  public void unregisterCustomCommandReceiver(@Nullable CommandReceiver commandReceiver) {
    if (commandReceiver != null) {
      customCommandReceivers.remove(commandReceiver);
    }
  }

  private void registerCommandReceiver(@Nullable CommandReceiver commandReceiver) {
    if (commandReceiver != null && !commandReceivers.contains(commandReceiver)) {
      commandReceivers.add(commandReceiver);
    }
  }

  private void unregisterCommandReceiver(@Nullable CommandReceiver commandReceiver) {
    if (commandReceiver != null) {
      commandReceivers.remove(commandReceiver);
    }
  }

  private long buildPrepareActions() {
    return playbackPreparer == null
        ? 0
        : (PlaybackPreparer.ACTIONS & playbackPreparer.getSupportedPrepareActions());
  }

  private long buildPlaybackActions(Player player) {
    boolean enableSeeking = player.isCommandAvailable(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM);
    boolean enableRewind = player.isCommandAvailable(COMMAND_SEEK_BACK);
    boolean enableFastForward = player.isCommandAvailable(COMMAND_SEEK_FORWARD);

    boolean enableSetRating = false;
    boolean enableSetCaptioningEnabled = false;
    Timeline timeline = player.getCurrentTimeline();
    if (!timeline.isEmpty() && !player.isPlayingAd()) {
      enableSetRating = ratingCallback != null;
      enableSetCaptioningEnabled = captionCallback != null && captionCallback.hasCaptions(player);
    }

    long playbackActions = BASE_PLAYBACK_ACTIONS;
    if (enableSeeking) {
      playbackActions |= PlaybackStateCompat.ACTION_SEEK_TO;
    }
    if (enableFastForward) {
      playbackActions |= PlaybackStateCompat.ACTION_FAST_FORWARD;
    }
    if (enableRewind) {
      playbackActions |= PlaybackStateCompat.ACTION_REWIND;
    }
    playbackActions &= enabledPlaybackActions;

    long actions = playbackActions;
    if (queueNavigator != null) {
      actions |=
          (QueueNavigator.ACTIONS & queueNavigator.getSupportedQueueNavigatorActions(player));
    }
    if (enableSetRating) {
      actions |= PlaybackStateCompat.ACTION_SET_RATING;
    }
    if (enableSetCaptioningEnabled) {
      actions |= PlaybackStateCompat.ACTION_SET_CAPTIONING_ENABLED;
    }
    return actions;
  }

  @EnsuresNonNullIf(result = true, expression = "player")
  private boolean canDispatchPlaybackAction(long action) {
    return player != null
        && ((enabledPlaybackActions & action) != 0 || dispatchUnsupportedActionsEnabled);
  }

  @EnsuresNonNullIf(result = true, expression = "playbackPreparer")
  private boolean canDispatchToPlaybackPreparer(long action) {
    return playbackPreparer != null
        && ((playbackPreparer.getSupportedPrepareActions() & action) != 0
            || dispatchUnsupportedActionsEnabled);
  }

  @EnsuresNonNullIf(
      result = true,
      expression = {"player", "queueNavigator"})
  private boolean canDispatchToQueueNavigator(long action) {
    return player != null
        && queueNavigator != null
        && ((queueNavigator.getSupportedQueueNavigatorActions(player) & action) != 0
            || dispatchUnsupportedActionsEnabled);
  }

  @EnsuresNonNullIf(
      result = true,
      expression = {"player", "ratingCallback"})
  private boolean canDispatchSetRating() {
    return player != null && ratingCallback != null;
  }

  @EnsuresNonNullIf(
      result = true,
      expression = {"player", "captionCallback"})
  private boolean canDispatchSetCaptioningEnabled() {
    return player != null && captionCallback != null;
  }

  @EnsuresNonNullIf(
      result = true,
      expression = {"player", "queueEditor"})
  private boolean canDispatchQueueEdit() {
    return player != null && queueEditor != null;
  }

  @EnsuresNonNullIf(
      result = true,
      expression = {"player", "mediaButtonEventHandler"})
  private boolean canDispatchMediaButtonEvent() {
    return player != null && mediaButtonEventHandler != null;
  }

  private void seekTo(Player player, int mediaItemIndex, long positionMs) {
    player.seekTo(mediaItemIndex, positionMs);
  }

  private int getMediaSessionPlaybackState(
      @Player.State int exoPlayerPlaybackState, boolean playWhenReady) {
    switch (exoPlayerPlaybackState) {
      case Player.STATE_BUFFERING:
        return playWhenReady
            ? PlaybackStateCompat.STATE_BUFFERING
            : PlaybackStateCompat.STATE_PAUSED;
      case Player.STATE_READY:
        return playWhenReady ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
      case Player.STATE_ENDED:
        return PlaybackStateCompat.STATE_STOPPED;
      case Player.STATE_IDLE:
      default:
        return mapIdleToStopped
            ? PlaybackStateCompat.STATE_STOPPED
            : PlaybackStateCompat.STATE_NONE;
    }
  }

  /**
   * Provides a default {@link MediaMetadataCompat} with properties and extras taken from the {@link
   * MediaDescriptionCompat} of the {@link MediaSessionCompat.QueueItem} of the active queue item.
   */
  public static final class DefaultMediaMetadataProvider implements MediaMetadataProvider {

    private final MediaControllerCompat mediaController;
    private final String metadataExtrasPrefix;

    /**
     * Creates a new instance.
     *
     * @param mediaController The {@link MediaControllerCompat}.
     * @param metadataExtrasPrefix A string to prefix extra keys which are propagated from the
     *     active queue item to the session metadata.
     */
    public DefaultMediaMetadataProvider(
        MediaControllerCompat mediaController, @Nullable String metadataExtrasPrefix) {
      this.mediaController = mediaController;
      this.metadataExtrasPrefix = metadataExtrasPrefix != null ? metadataExtrasPrefix : "";
    }

    @Override
    public MediaMetadataCompat getMetadata(Player player) {
      if (player.getCurrentTimeline().isEmpty()) {
        return METADATA_EMPTY;
      }
      MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
      if (player.isPlayingAd()) {
        builder.putLong(MediaMetadataCompat.METADATA_KEY_ADVERTISEMENT, 1);
      }
      builder.putLong(
          MediaMetadataCompat.METADATA_KEY_DURATION,
          player.isCurrentMediaItemDynamic() || player.getDuration() == C.TIME_UNSET
              ? -1
              : player.getDuration());
      long activeQueueItemId = mediaController.getPlaybackState().getActiveQueueItemId();
      if (activeQueueItemId != MediaSessionCompat.QueueItem.UNKNOWN_ID) {
        List<MediaSessionCompat.QueueItem> queue = mediaController.getQueue();
        for (int i = 0; queue != null && i < queue.size(); i++) {
          MediaSessionCompat.QueueItem queueItem = queue.get(i);
          if (queueItem.getQueueId() == activeQueueItemId) {
            MediaDescriptionCompat description = queueItem.getDescription();
            @Nullable Bundle extras = description.getExtras();
            if (extras != null) {
              for (String key : extras.keySet()) {
                @Nullable Object value = extras.get(key);
                if (value instanceof String) {
                  builder.putString(metadataExtrasPrefix + key, (String) value);
                } else if (value instanceof CharSequence) {
                  builder.putText(metadataExtrasPrefix + key, (CharSequence) value);
                } else if (value instanceof Long) {
                  builder.putLong(metadataExtrasPrefix + key, (Long) value);
                } else if (value instanceof Integer) {
                  builder.putLong(metadataExtrasPrefix + key, (Integer) value);
                } else if (value instanceof Bitmap) {
                  builder.putBitmap(metadataExtrasPrefix + key, (Bitmap) value);
                } else if (value instanceof RatingCompat) {
                  builder.putRating(metadataExtrasPrefix + key, (RatingCompat) value);
                }
              }
            }
            @Nullable CharSequence title = description.getTitle();
            if (title != null) {
              String titleString = String.valueOf(title);
              builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, titleString);
              builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, titleString);
            }
            @Nullable CharSequence subtitle = description.getSubtitle();
            if (subtitle != null) {
              builder.putString(
                  MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, String.valueOf(subtitle));
            }
            @Nullable CharSequence displayDescription = description.getDescription();
            if (displayDescription != null) {
              builder.putString(
                  MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION,
                  String.valueOf(displayDescription));
            }
            @Nullable Bitmap iconBitmap = description.getIconBitmap();
            if (iconBitmap != null) {
              builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, iconBitmap);
            }
            @Nullable Uri iconUri = description.getIconUri();
            if (iconUri != null) {
              builder.putString(
                  MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, String.valueOf(iconUri));
            }
            @Nullable String mediaId = description.getMediaId();
            if (mediaId != null) {
              builder.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, mediaId);
            }
            @Nullable Uri mediaUri = description.getMediaUri();
            if (mediaUri != null) {
              builder.putString(
                  MediaMetadataCompat.METADATA_KEY_MEDIA_URI, String.valueOf(mediaUri));
            }
            break;
          }
        }
      }
      return builder.build();
    }
  }

  private class ComponentListener extends MediaSessionCompat.Callback implements Player.Listener {

    private int currentMediaItemIndex;
    private int currentWindowCount;

    // Player.Listener implementation.

    @Override
    public void onEvents(Player player, Player.Events events) {
      boolean invalidatePlaybackState = false;
      boolean invalidateMetadata = false;
      if (events.contains(Player.EVENT_POSITION_DISCONTINUITY)) {
        if (currentMediaItemIndex != player.getCurrentMediaItemIndex()) {
          if (queueNavigator != null) {
            queueNavigator.onCurrentMediaItemIndexChanged(player);
          }
          invalidateMetadata = true;
        }
        invalidatePlaybackState = true;
      }

      if (events.contains(Player.EVENT_TIMELINE_CHANGED)) {
        int windowCount = player.getCurrentTimeline().getWindowCount();
        int mediaItemIndex = player.getCurrentMediaItemIndex();
        if (queueNavigator != null) {
          queueNavigator.onTimelineChanged(player);
          invalidatePlaybackState = true;
        } else if (currentWindowCount != windowCount || currentMediaItemIndex != mediaItemIndex) {
          // active queue item and queue navigation actions may need to be updated
          invalidatePlaybackState = true;
        }
        currentWindowCount = windowCount;
        invalidateMetadata = true;
      }

      // Update currentMediaItemIndex after comparisons above.
      currentMediaItemIndex = player.getCurrentMediaItemIndex();

      if (events.containsAny(
          EVENT_PLAYBACK_STATE_CHANGED,
          EVENT_PLAY_WHEN_READY_CHANGED,
          EVENT_IS_PLAYING_CHANGED,
          EVENT_REPEAT_MODE_CHANGED,
          EVENT_PLAYBACK_PARAMETERS_CHANGED)) {
        invalidatePlaybackState = true;
      }

      // The queue needs to be updated by the queue navigator first. The queue navigator also
      // delivers the active queue item that is used to update the playback state.
      if (events.containsAny(EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
        invalidateMediaSessionQueue();
        invalidatePlaybackState = true;
      }
      // Invalidate the playback state before invalidating metadata because the active queue item of
      // the session playback state needs to be updated before the MediaMetadataProvider uses it.
      if (invalidatePlaybackState) {
        invalidateMediaSessionPlaybackState();
      }
      if (invalidateMetadata) {
        invalidateMediaSessionMetadata();
      }
    }

    // MediaSessionCompat.Callback implementation.

    @Override
    public void onPlay() {
      if (canDispatchPlaybackAction(PlaybackStateCompat.ACTION_PLAY)) {
        if (player.getPlaybackState() == Player.STATE_IDLE) {
          if (playbackPreparer != null) {
            playbackPreparer.onPrepare(/* playWhenReady= */ true);
          } else {
            player.prepare();
          }
        } else if (player.getPlaybackState() == Player.STATE_ENDED) {
          seekTo(player, player.getCurrentMediaItemIndex(), C.TIME_UNSET);
        }
        Assertions.checkNotNull(player).play();
      }
    }

    @Override
    public void onPause() {
      if (canDispatchPlaybackAction(PlaybackStateCompat.ACTION_PAUSE)) {
        player.pause();
      }
    }

    @Override
    public void onSeekTo(long positionMs) {
      if (canDispatchPlaybackAction(PlaybackStateCompat.ACTION_SEEK_TO)) {
        seekTo(player, player.getCurrentMediaItemIndex(), positionMs);
      }
    }

    @Override
    public void onFastForward() {
      if (canDispatchPlaybackAction(PlaybackStateCompat.ACTION_FAST_FORWARD)) {
        player.seekForward();
      }
    }

    @Override
    public void onRewind() {
      if (canDispatchPlaybackAction(PlaybackStateCompat.ACTION_REWIND)) {
        player.seekBack();
      }
    }

    @Override
    public void onStop() {
      if (canDispatchPlaybackAction(PlaybackStateCompat.ACTION_STOP)) {
        player.stop();
        if (clearMediaItemsOnStop) {
          player.clearMediaItems();
        }
      }
    }

    @Override
    public void onSetShuffleMode(@PlaybackStateCompat.ShuffleMode int shuffleMode) {
      if (canDispatchPlaybackAction(PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE)) {
        boolean shuffleModeEnabled;
        switch (shuffleMode) {
          case PlaybackStateCompat.SHUFFLE_MODE_ALL:
          case PlaybackStateCompat.SHUFFLE_MODE_GROUP:
            shuffleModeEnabled = true;
            break;
          case PlaybackStateCompat.SHUFFLE_MODE_NONE:
          case PlaybackStateCompat.SHUFFLE_MODE_INVALID:
          default:
            shuffleModeEnabled = false;
            break;
        }
        player.setShuffleModeEnabled(shuffleModeEnabled);
      }
    }

    @Override
    public void onSetRepeatMode(@PlaybackStateCompat.RepeatMode int mediaSessionRepeatMode) {
      if (canDispatchPlaybackAction(PlaybackStateCompat.ACTION_SET_REPEAT_MODE)) {
        @Player.RepeatMode int repeatMode;
        switch (mediaSessionRepeatMode) {
          case PlaybackStateCompat.REPEAT_MODE_ALL:
          case PlaybackStateCompat.REPEAT_MODE_GROUP:
            repeatMode = Player.REPEAT_MODE_ALL;
            break;
          case PlaybackStateCompat.REPEAT_MODE_ONE:
            repeatMode = Player.REPEAT_MODE_ONE;
            break;
          case PlaybackStateCompat.REPEAT_MODE_NONE:
          case PlaybackStateCompat.REPEAT_MODE_INVALID:
          default:
            repeatMode = Player.REPEAT_MODE_OFF;
            break;
        }
        player.setRepeatMode(repeatMode);
      }
    }

    @Override
    public void onSetPlaybackSpeed(float speed) {
      if (canDispatchPlaybackAction(PlaybackStateCompat.ACTION_SET_PLAYBACK_SPEED) && speed > 0) {
        player.setPlaybackParameters(player.getPlaybackParameters().withSpeed(speed));
      }
    }

    @Override
    public void onSkipToNext() {
      if (canDispatchToQueueNavigator(PlaybackStateCompat.ACTION_SKIP_TO_NEXT)) {
        queueNavigator.onSkipToNext(player);
      }
    }

    @Override
    public void onSkipToPrevious() {
      if (canDispatchToQueueNavigator(PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)) {
        queueNavigator.onSkipToPrevious(player);
      }
    }

    @Override
    public void onSkipToQueueItem(long id) {
      if (canDispatchToQueueNavigator(PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM)) {
        queueNavigator.onSkipToQueueItem(player, id);
      }
    }

    @Override
    public void onCustomAction(String action, @Nullable Bundle extras) {
      if (player != null && customActionMap.containsKey(action)) {
        customActionMap.get(action).onCustomAction(player, action, extras);
        invalidateMediaSessionPlaybackState();
      }
    }

    @Override
    public void onCommand(String command, @Nullable Bundle extras, @Nullable ResultReceiver cb) {
      if (player != null) {
        for (int i = 0; i < commandReceivers.size(); i++) {
          if (commandReceivers.get(i).onCommand(player, command, extras, cb)) {
            return;
          }
        }
        for (int i = 0; i < customCommandReceivers.size(); i++) {
          if (customCommandReceivers.get(i).onCommand(player, command, extras, cb)) {
            return;
          }
        }
      }
    }

    @Override
    public void onPrepare() {
      if (canDispatchToPlaybackPreparer(PlaybackStateCompat.ACTION_PREPARE)) {
        playbackPreparer.onPrepare(/* playWhenReady= */ false);
      }
    }

    @Override
    public void onPrepareFromMediaId(String mediaId, @Nullable Bundle extras) {
      if (canDispatchToPlaybackPreparer(PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID)) {
        playbackPreparer.onPrepareFromMediaId(mediaId, /* playWhenReady= */ false, extras);
      }
    }

    @Override
    public void onPrepareFromSearch(String query, @Nullable Bundle extras) {
      if (canDispatchToPlaybackPreparer(PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH)) {
        playbackPreparer.onPrepareFromSearch(query, /* playWhenReady= */ false, extras);
      }
    }

    @Override
    public void onPrepareFromUri(Uri uri, @Nullable Bundle extras) {
      if (canDispatchToPlaybackPreparer(PlaybackStateCompat.ACTION_PREPARE_FROM_URI)) {
        playbackPreparer.onPrepareFromUri(uri, /* playWhenReady= */ false, extras);
      }
    }

    @Override
    public void onPlayFromMediaId(String mediaId, @Nullable Bundle extras) {
      if (canDispatchToPlaybackPreparer(PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID)) {
        playbackPreparer.onPrepareFromMediaId(mediaId, /* playWhenReady= */ true, extras);
      }
    }

    @Override
    public void onPlayFromSearch(String query, @Nullable Bundle extras) {
      if (canDispatchToPlaybackPreparer(PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH)) {
        playbackPreparer.onPrepareFromSearch(query, /* playWhenReady= */ true, extras);
      }
    }

    @Override
    public void onPlayFromUri(Uri uri, @Nullable Bundle extras) {
      if (canDispatchToPlaybackPreparer(PlaybackStateCompat.ACTION_PLAY_FROM_URI)) {
        playbackPreparer.onPrepareFromUri(uri, /* playWhenReady= */ true, extras);
      }
    }

    @Override
    public void onSetRating(RatingCompat rating) {
      if (canDispatchSetRating()) {
        ratingCallback.onSetRating(player, rating);
      }
    }

    @Override
    public void onSetRating(RatingCompat rating, @Nullable Bundle extras) {
      if (canDispatchSetRating()) {
        ratingCallback.onSetRating(player, rating, extras);
      }
    }

    @Override
    public void onAddQueueItem(MediaDescriptionCompat description) {
      if (canDispatchQueueEdit()) {
        queueEditor.onAddQueueItem(player, description);
      }
    }

    @Override
    public void onAddQueueItem(MediaDescriptionCompat description, int index) {
      if (canDispatchQueueEdit()) {
        queueEditor.onAddQueueItem(player, description, index);
      }
    }

    @Override
    public void onRemoveQueueItem(MediaDescriptionCompat description) {
      if (canDispatchQueueEdit()) {
        queueEditor.onRemoveQueueItem(player, description);
      }
    }

    @Override
    public void onSetCaptioningEnabled(boolean enabled) {
      if (canDispatchSetCaptioningEnabled()) {
        captionCallback.onSetCaptioningEnabled(player, enabled);
      }
    }

    @Override
    public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
      boolean isHandled =
          canDispatchMediaButtonEvent()
              && mediaButtonEventHandler.onMediaButtonEvent(player, mediaButtonEvent);
      return isHandled || super.onMediaButtonEvent(mediaButtonEvent);
    }
  }
}
