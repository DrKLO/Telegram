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
package org.telegram.messenger.exoplayer2.drm;

import android.os.Handler;
import org.telegram.messenger.exoplayer2.Player;
import org.telegram.messenger.exoplayer2.util.Assertions;
import java.util.concurrent.CopyOnWriteArrayList;

/** Listener of {@link DefaultDrmSessionManager} events. */
public interface DefaultDrmSessionEventListener {

  /** Called each time keys are loaded. */
  void onDrmKeysLoaded();

  /**
   * Called when a drm error occurs.
   *
   * <p>This method being called does not indicate that playback has failed, or that it will fail.
   * The player may be able to recover from the error and continue. Hence applications should
   * <em>not</em> implement this method to display a user visible error or initiate an application
   * level retry ({@link Player.EventListener#onPlayerError} is the appropriate place to implement
   * such behavior). This method is called to provide the application with an opportunity to log the
   * error if it wishes to do so.
   *
   * @param error The corresponding exception.
   */
  void onDrmSessionManagerError(Exception error);

  /** Called each time offline keys are restored. */
  void onDrmKeysRestored();

  /** Called each time offline keys are removed. */
  void onDrmKeysRemoved();

  /** Dispatches drm events to all registered listeners. */
  final class EventDispatcher {

    private final CopyOnWriteArrayList<HandlerAndListener> listeners;

    /** Creates event dispatcher. */
    public EventDispatcher() {
      listeners = new CopyOnWriteArrayList<>();
    }

    /** Adds listener to event dispatcher. */
    public void addListener(Handler handler, DefaultDrmSessionEventListener eventListener) {
      Assertions.checkArgument(handler != null && eventListener != null);
      listeners.add(new HandlerAndListener(handler, eventListener));
    }

    /** Removes listener from event dispatcher. */
    public void removeListener(DefaultDrmSessionEventListener eventListener) {
      for (HandlerAndListener handlerAndListener : listeners) {
        if (handlerAndListener.listener == eventListener) {
          listeners.remove(handlerAndListener);
        }
      }
    }

    /** Dispatches {@link DefaultDrmSessionEventListener#onDrmKeysLoaded()}. */
    public void drmKeysLoaded() {
      for (HandlerAndListener handlerAndListener : listeners) {
        final DefaultDrmSessionEventListener listener = handlerAndListener.listener;
        handlerAndListener.handler.post(
            new Runnable() {
              @Override
              public void run() {
                listener.onDrmKeysLoaded();
              }
            });
      }
    }

    /** Dispatches {@link DefaultDrmSessionEventListener#onDrmSessionManagerError(Exception)}. */
    public void drmSessionManagerError(final Exception e) {
      for (HandlerAndListener handlerAndListener : listeners) {
        final DefaultDrmSessionEventListener listener = handlerAndListener.listener;
        handlerAndListener.handler.post(
            new Runnable() {
              @Override
              public void run() {
                listener.onDrmSessionManagerError(e);
              }
            });
      }
    }

    /** Dispatches {@link DefaultDrmSessionEventListener#onDrmKeysRestored()}. */
    public void drmKeysRestored() {
      for (HandlerAndListener handlerAndListener : listeners) {
        final DefaultDrmSessionEventListener listener = handlerAndListener.listener;
        handlerAndListener.handler.post(
            new Runnable() {
              @Override
              public void run() {
                listener.onDrmKeysRestored();
              }
            });
      }
    }

    /** Dispatches {@link DefaultDrmSessionEventListener#onDrmKeysRemoved()}. */
    public void drmKeysRemoved() {
      for (HandlerAndListener handlerAndListener : listeners) {
        final DefaultDrmSessionEventListener listener = handlerAndListener.listener;
        handlerAndListener.handler.post(
            new Runnable() {
              @Override
              public void run() {
                listener.onDrmKeysRemoved();
              }
            });
      }
    }

    private static final class HandlerAndListener {

      public final Handler handler;
      public final DefaultDrmSessionEventListener listener;

      public HandlerAndListener(Handler handler, DefaultDrmSessionEventListener eventListener) {
        this.handler = handler;
        this.listener = eventListener;
      }
    }
  }
}
