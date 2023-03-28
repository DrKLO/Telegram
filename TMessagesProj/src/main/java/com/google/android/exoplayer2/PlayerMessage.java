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
package com.google.android.exoplayer2;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Renderer.MessageType;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Clock;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.concurrent.TimeoutException;

/**
 * Defines a player message which can be sent with a {@link Sender} and received by a {@link
 * Target}.
 */
public final class PlayerMessage {

  /** A target for messages. */
  public interface Target {

    /**
     * Handles a message delivered to the target.
     *
     * @param messageType The message type.
     * @param message The message payload.
     * @throws ExoPlaybackException If an error occurred whilst handling the message. Should only be
     *     thrown by targets that handle messages on the playback thread.
     */
    void handleMessage(@MessageType int messageType, @Nullable Object message)
        throws ExoPlaybackException;
  }

  /** A sender for messages. */
  public interface Sender {

    /**
     * Sends a message.
     *
     * @param message The message to be sent.
     */
    void sendMessage(PlayerMessage message);
  }

  private final Target target;
  private final Sender sender;
  private final Clock clock;
  private final Timeline timeline;

  private int type;
  @Nullable private Object payload;
  private Looper looper;
  private int mediaItemIndex;
  private long positionMs;
  private boolean deleteAfterDelivery;
  private boolean isSent;
  private boolean isDelivered;
  private boolean isProcessed;
  private boolean isCanceled;

  /**
   * Creates a new message.
   *
   * @param sender The {@link Sender} used to send the message.
   * @param target The {@link Target} the message is sent to.
   * @param timeline The timeline used when setting the position with {@link #setPosition(long)}. If
   *     set to {@link Timeline#EMPTY}, any position can be specified.
   * @param defaultMediaItemIndex The default media item index in the {@code timeline} when no other
   *     media item index is specified.
   * @param clock The {@link Clock}.
   * @param defaultLooper The default {@link Looper} to send the message on when no other looper is
   *     specified.
   */
  public PlayerMessage(
      Sender sender,
      Target target,
      Timeline timeline,
      int defaultMediaItemIndex,
      Clock clock,
      Looper defaultLooper) {
    this.sender = sender;
    this.target = target;
    this.timeline = timeline;
    this.looper = defaultLooper;
    this.clock = clock;
    this.mediaItemIndex = defaultMediaItemIndex;
    this.positionMs = C.TIME_UNSET;
    this.deleteAfterDelivery = true;
  }

  /** Returns the timeline used for setting the position with {@link #setPosition(long)}. */
  public Timeline getTimeline() {
    return timeline;
  }

  /** Returns the target the message is sent to. */
  public Target getTarget() {
    return target;
  }

  /**
   * Sets the message type forwarded to {@link Target#handleMessage(int, Object)}.
   *
   * @param messageType The message type.
   * @return This message.
   * @throws IllegalStateException If {@link #send()} has already been called.
   */
  @CanIgnoreReturnValue
  public PlayerMessage setType(int messageType) {
    Assertions.checkState(!isSent);
    this.type = messageType;
    return this;
  }

  /** Returns the message type forwarded to {@link Target#handleMessage(int, Object)}. */
  public int getType() {
    return type;
  }

  /**
   * Sets the message payload forwarded to {@link Target#handleMessage(int, Object)}.
   *
   * @param payload The message payload.
   * @return This message.
   * @throws IllegalStateException If {@link #send()} has already been called.
   */
  @CanIgnoreReturnValue
  public PlayerMessage setPayload(@Nullable Object payload) {
    Assertions.checkState(!isSent);
    this.payload = payload;
    return this;
  }

  /** Returns the message payload forwarded to {@link Target#handleMessage(int, Object)}. */
  @Nullable
  public Object getPayload() {
    return payload;
  }

  /**
   * @deprecated Use {@link #setLooper(Looper)} instead.
   */
  @CanIgnoreReturnValue
  @Deprecated
  public PlayerMessage setHandler(Handler handler) {
    return setLooper(handler.getLooper());
  }

  /**
   * Sets the {@link Looper} the message is delivered on.
   *
   * @param looper A {@link Looper}.
   * @return This message.
   * @throws IllegalStateException If {@link #send()} has already been called.
   */
  @CanIgnoreReturnValue
  public PlayerMessage setLooper(Looper looper) {
    Assertions.checkState(!isSent);
    this.looper = looper;
    return this;
  }

  /** Returns the {@link Looper} the message is delivered on. */
  public Looper getLooper() {
    return looper;
  }

  /**
   * Returns position in the media item at {@link #getMediaItemIndex()} at which the message will be
   * delivered, in milliseconds. If {@link C#TIME_UNSET}, the message will be delivered immediately.
   * If {@link C#TIME_END_OF_SOURCE}, the message will be delivered at the end of the media item at
   * {@link #getMediaItemIndex()}.
   */
  public long getPositionMs() {
    return positionMs;
  }

  /**
   * Sets a position in the current media item at which the message will be delivered.
   *
   * @param positionMs The position in the current media item at which the message will be sent, in
   *     milliseconds, or {@link C#TIME_END_OF_SOURCE} to deliver the message at the end of the
   *     current media item.
   * @return This message.
   * @throws IllegalStateException If {@link #send()} has already been called.
   */
  @CanIgnoreReturnValue
  public PlayerMessage setPosition(long positionMs) {
    Assertions.checkState(!isSent);
    this.positionMs = positionMs;
    return this;
  }

  /**
   * Sets a position in a media item at which the message will be delivered.
   *
   * @param mediaItemIndex The index of the media item at which the message will be sent.
   * @param positionMs The position in the media item with index {@code mediaItemIndex} at which the
   *     message will be sent, in milliseconds, or {@link C#TIME_END_OF_SOURCE} to deliver the
   *     message at the end of the media item with index {@code mediaItemIndex}.
   * @return This message.
   * @throws IllegalSeekPositionException If the timeline returned by {@link #getTimeline()} is not
   *     empty and the provided media item index is not within the bounds of the timeline.
   * @throws IllegalStateException If {@link #send()} has already been called.
   */
  @CanIgnoreReturnValue
  public PlayerMessage setPosition(int mediaItemIndex, long positionMs) {
    Assertions.checkState(!isSent);
    Assertions.checkArgument(positionMs != C.TIME_UNSET);
    if (mediaItemIndex < 0
        || (!timeline.isEmpty() && mediaItemIndex >= timeline.getWindowCount())) {
      throw new IllegalSeekPositionException(timeline, mediaItemIndex, positionMs);
    }
    this.mediaItemIndex = mediaItemIndex;
    this.positionMs = positionMs;
    return this;
  }

  /** Returns media item index at which the message will be delivered. */
  public int getMediaItemIndex() {
    return mediaItemIndex;
  }

  /**
   * Sets whether the message will be deleted after delivery. If false, the message will be resent
   * if playback reaches the specified position again. Only allowed to be false if a position is set
   * with {@link #setPosition(long)}.
   *
   * @param deleteAfterDelivery Whether the message is deleted after delivery.
   * @return This message.
   * @throws IllegalStateException If {@link #send()} has already been called.
   */
  @CanIgnoreReturnValue
  public PlayerMessage setDeleteAfterDelivery(boolean deleteAfterDelivery) {
    Assertions.checkState(!isSent);
    this.deleteAfterDelivery = deleteAfterDelivery;
    return this;
  }

  /** Returns whether the message will be deleted after delivery. */
  public boolean getDeleteAfterDelivery() {
    return deleteAfterDelivery;
  }

  /**
   * Sends the message. If the target throws an {@link ExoPlaybackException} then it is propagated
   * out of the player as an error using {@link Player.Listener#onPlayerError(PlaybackException)}.
   *
   * @return This message.
   * @throws IllegalStateException If this message has already been sent.
   */
  @CanIgnoreReturnValue
  public PlayerMessage send() {
    Assertions.checkState(!isSent);
    if (positionMs == C.TIME_UNSET) {
      Assertions.checkArgument(deleteAfterDelivery);
    }
    isSent = true;
    sender.sendMessage(this);
    return this;
  }

  /**
   * Cancels the message delivery.
   *
   * @return This message.
   * @throws IllegalStateException If this method is called before {@link #send()}.
   */
  @CanIgnoreReturnValue
  public synchronized PlayerMessage cancel() {
    Assertions.checkState(isSent);
    isCanceled = true;
    markAsProcessed(/* isDelivered= */ false);
    return this;
  }

  /** Returns whether the message delivery has been canceled. */
  public synchronized boolean isCanceled() {
    return isCanceled;
  }

  /**
   * Marks the message as processed. Should only be called by a {@link Sender} and may be called
   * multiple times.
   *
   * @param isDelivered Whether the message has been delivered to its target. The message is
   *     considered as being delivered when this method has been called with {@code isDelivered} set
   *     to true at least once.
   */
  public synchronized void markAsProcessed(boolean isDelivered) {
    this.isDelivered |= isDelivered;
    isProcessed = true;
    notifyAll();
  }

  /**
   * Blocks until after the message has been delivered or the player is no longer able to deliver
   * the message.
   *
   * <p>Note that this method must not be called if the current thread is the same thread used by
   * the message {@link #getLooper() looper} as it would cause a deadlock.
   *
   * @return Whether the message was delivered successfully.
   * @throws IllegalStateException If this method is called before {@link #send()}.
   * @throws IllegalStateException If this method is called on the same thread used by the message
   *     {@link #getLooper() looper}.
   * @throws InterruptedException If the current thread is interrupted while waiting for the message
   *     to be delivered.
   */
  public synchronized boolean blockUntilDelivered() throws InterruptedException {
    Assertions.checkState(isSent);
    Assertions.checkState(looper.getThread() != Thread.currentThread());
    while (!isProcessed) {
      wait();
    }
    return isDelivered;
  }

  /**
   * Blocks until after the message has been delivered or the player is no longer able to deliver
   * the message or the specified timeout elapsed.
   *
   * <p>Note that this method must not be called if the current thread is the same thread used by
   * the message {@link #getLooper() looper} as it would cause a deadlock.
   *
   * @param timeoutMs The timeout in milliseconds.
   * @return Whether the message was delivered successfully.
   * @throws IllegalStateException If this method is called before {@link #send()}.
   * @throws IllegalStateException If this method is called on the same thread used by the message
   *     {@link #getLooper() looper}.
   * @throws TimeoutException If the {@code timeoutMs} elapsed and this message has not been
   *     delivered and the player is still able to deliver the message.
   * @throws InterruptedException If the current thread is interrupted while waiting for the message
   *     to be delivered.
   */
  public synchronized boolean blockUntilDelivered(long timeoutMs)
      throws InterruptedException, TimeoutException {
    Assertions.checkState(isSent);
    Assertions.checkState(looper.getThread() != Thread.currentThread());

    long deadlineMs = clock.elapsedRealtime() + timeoutMs;
    long remainingMs = timeoutMs;
    while (!isProcessed && remainingMs > 0) {
      clock.onThreadBlocked();
      wait(remainingMs);
      remainingMs = deadlineMs - clock.elapsedRealtime();
    }
    if (!isProcessed) {
      throw new TimeoutException("Message delivery timed out.");
    }
    return isDelivered;
  }
}
