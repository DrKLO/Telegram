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

import android.os.Handler;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Event dispatcher which allows listener registration.
 *
 * @param <T> The type of listener.
 */
public final class EventDispatcher<T> {

  /** Functional interface to send an event. */
  public interface Event<T> {

    /**
     * Sends the event to a listener.
     *
     * @param listener The listener to send the event to.
     */
    void sendTo(T listener);
  }

  /** The list of listeners and handlers. */
  private final CopyOnWriteArrayList<HandlerAndListener<T>> listeners;

  /** Creates event dispatcher. */
  public EventDispatcher() {
    listeners = new CopyOnWriteArrayList<>();
  }

  /** Adds listener to event dispatcher. */
  public void addListener(Handler handler, T eventListener) {
    Assertions.checkArgument(handler != null && eventListener != null);
    removeListener(eventListener);
    listeners.add(new HandlerAndListener<>(handler, eventListener));
  }

  /** Removes listener from event dispatcher. */
  public void removeListener(T eventListener) {
    for (HandlerAndListener<T> handlerAndListener : listeners) {
      if (handlerAndListener.listener == eventListener) {
        listeners.remove(handlerAndListener);
      }
    }
  }

  /**
   * Dispatches an event to all registered listeners.
   *
   * @param event The {@link Event}.
   */
  public void dispatch(Event<T> event) {
    for (HandlerAndListener<T> handlerAndListener : listeners) {
      T eventListener = handlerAndListener.listener;
      handlerAndListener.handler.post(() -> event.sendTo(eventListener));
    }
  }

  private static final class HandlerAndListener<T> {

    public final Handler handler;
    public final T listener;

    public HandlerAndListener(Handler handler, T eventListener) {
      this.handler = handler;
      this.listener = eventListener;
    }
  }
}
