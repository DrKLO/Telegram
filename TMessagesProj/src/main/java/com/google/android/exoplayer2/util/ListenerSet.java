/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.google.android.exoplayer2.util.Assertions.checkState;

import android.os.Looper;
import android.os.Message;
import androidx.annotation.CheckResult;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import java.util.ArrayDeque;
import java.util.concurrent.CopyOnWriteArraySet;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * A set of listeners.
 *
 * <p>Events are guaranteed to arrive in the order in which they happened even if a new event is
 * triggered recursively from another listener.
 *
 * <p>Events are also guaranteed to be only sent to the listeners registered at the time the event
 * was enqueued and haven't been removed since.
 *
 * <p>All methods must be called on the {@link Looper} passed to the constructor unless indicated
 * otherwise.
 *
 * @param <T> The listener type.
 */
public final class ListenerSet<T extends @NonNull Object> {

  /**
   * An event sent to a listener.
   *
   * @param <T> The listener type.
   */
  public interface Event<T> {

    /** Invokes the event notification on the given listener. */
    void invoke(T listener);
  }

  /**
   * An event sent to a listener when all other events sent during one {@link Looper} message queue
   * iteration were handled by the listener.
   *
   * @param <T> The listener type.
   */
  public interface IterationFinishedEvent<T> {

    /**
     * Invokes the iteration finished event.
     *
     * @param listener The listener to invoke the event on.
     * @param eventFlags The combined event {@link FlagSet flags} of all events sent in this
     *     iteration.
     */
    void invoke(T listener, FlagSet eventFlags);
  }

  private static final int MSG_ITERATION_FINISHED = 0;

  private final Clock clock;
  private final HandlerWrapper handler;
  private final IterationFinishedEvent<T> iterationFinishedEvent;
  private final CopyOnWriteArraySet<ListenerHolder<T>> listeners;
  private final ArrayDeque<Runnable> flushingEvents;
  private final ArrayDeque<Runnable> queuedEvents;
  private final Object releasedLock;

  @GuardedBy("releasedLock")
  private boolean released;

  private boolean throwsWhenUsingWrongThread;

  /**
   * Creates a new listener set.
   *
   * @param looper A {@link Looper} used to call listeners on. The same {@link Looper} must be used
   *     to call all other methods of this class unless indicated otherwise.
   * @param clock A {@link Clock}.
   * @param iterationFinishedEvent An {@link IterationFinishedEvent} sent when all other events sent
   *     during one {@link Looper} message queue iteration were handled by the listeners.
   */
  public ListenerSet(Looper looper, Clock clock, IterationFinishedEvent<T> iterationFinishedEvent) {
    this(/* listeners= */ new CopyOnWriteArraySet<>(), looper, clock, iterationFinishedEvent);
  }

  private ListenerSet(
      CopyOnWriteArraySet<ListenerHolder<T>> listeners,
      Looper looper,
      Clock clock,
      IterationFinishedEvent<T> iterationFinishedEvent) {
    this.clock = clock;
    this.listeners = listeners;
    this.iterationFinishedEvent = iterationFinishedEvent;
    releasedLock = new Object();
    flushingEvents = new ArrayDeque<>();
    queuedEvents = new ArrayDeque<>();
    // It's safe to use "this" because we don't send a message before exiting the constructor.
    @SuppressWarnings("nullness:methodref.receiver.bound")
    HandlerWrapper handler = clock.createHandler(looper, this::handleMessage);
    this.handler = handler;
    throwsWhenUsingWrongThread = true;
  }

  /**
   * Copies the listener set.
   *
   * <p>This method can be called from any thread.
   *
   * @param looper The new {@link Looper} for the copied listener set.
   * @param iterationFinishedEvent The new {@link IterationFinishedEvent} sent when all other events
   *     sent during one {@link Looper} message queue iteration were handled by the listeners.
   * @return The copied listener set.
   */
  @CheckResult
  public ListenerSet<T> copy(Looper looper, IterationFinishedEvent<T> iterationFinishedEvent) {
    return copy(looper, clock, iterationFinishedEvent);
  }

  /**
   * Copies the listener set.
   *
   * <p>This method can be called from any thread.
   *
   * @param looper The new {@link Looper} for the copied listener set.
   * @param clock The new {@link Clock} for the copied listener set.
   * @param iterationFinishedEvent The new {@link IterationFinishedEvent} sent when all other events
   *     sent during one {@link Looper} message queue iteration were handled by the listeners.
   * @return The copied listener set.
   */
  @CheckResult
  public ListenerSet<T> copy(
      Looper looper, Clock clock, IterationFinishedEvent<T> iterationFinishedEvent) {
    return new ListenerSet<>(listeners, looper, clock, iterationFinishedEvent);
  }

  /**
   * Adds a listener to the set.
   *
   * <p>If a listener is already present, it will not be added again.
   *
   * <p>This method can be called from any thread.
   *
   * @param listener The listener to be added.
   */
  public void add(T listener) {
    Assertions.checkNotNull(listener);
    synchronized (releasedLock) {
      if (released) {
        return;
      }
      listeners.add(new ListenerHolder<>(listener));
    }
  }

  /**
   * Removes a listener from the set.
   *
   * <p>If the listener is not present, nothing happens.
   *
   * @param listener The listener to be removed.
   */
  public void remove(T listener) {
    verifyCurrentThread();
    for (ListenerHolder<T> listenerHolder : listeners) {
      if (listenerHolder.listener.equals(listener)) {
        listenerHolder.release(iterationFinishedEvent);
        listeners.remove(listenerHolder);
      }
    }
  }

  /** Removes all listeners from the set. */
  public void clear() {
    verifyCurrentThread();
    listeners.clear();
  }

  /** Returns the number of added listeners. */
  public int size() {
    verifyCurrentThread();
    return listeners.size();
  }

  /**
   * Adds an event that is sent to the listeners when {@link #flushEvents} is called.
   *
   * @param eventFlag An integer indicating the type of the event, or {@link C#INDEX_UNSET} to
   *     report this event without flag.
   * @param event The event.
   */
  public void queueEvent(int eventFlag, Event<T> event) {
    verifyCurrentThread();
    CopyOnWriteArraySet<ListenerHolder<T>> listenerSnapshot = new CopyOnWriteArraySet<>(listeners);
    queuedEvents.add(
        () -> {
          for (ListenerHolder<T> holder : listenerSnapshot) {
            holder.invoke(eventFlag, event);
          }
        });
  }

  /** Notifies listeners of events previously enqueued with {@link #queueEvent(int, Event)}. */
  public void flushEvents() {
    verifyCurrentThread();
    if (queuedEvents.isEmpty()) {
      return;
    }
    if (!handler.hasMessages(MSG_ITERATION_FINISHED)) {
      handler.sendMessageAtFrontOfQueue(handler.obtainMessage(MSG_ITERATION_FINISHED));
    }
    boolean recursiveFlushInProgress = !flushingEvents.isEmpty();
    flushingEvents.addAll(queuedEvents);
    queuedEvents.clear();
    if (recursiveFlushInProgress) {
      // Recursive call to flush. Let the outer call handle the flush queue.
      return;
    }
    while (!flushingEvents.isEmpty()) {
      flushingEvents.peekFirst().run();
      flushingEvents.removeFirst();
    }
  }

  /**
   * {@link #queueEvent(int, Event) Queues} a single event and immediately {@link #flushEvents()
   * flushes} the event queue to notify all listeners.
   *
   * @param eventFlag An integer flag indicating the type of the event, or {@link C#INDEX_UNSET} to
   *     report this event without flag.
   * @param event The event.
   */
  public void sendEvent(int eventFlag, Event<T> event) {
    queueEvent(eventFlag, event);
    flushEvents();
  }

  /**
   * Releases the set of listeners immediately.
   *
   * <p>This will ensure no events are sent to any listener after this method has been called.
   */
  public void release() {
    verifyCurrentThread();
    synchronized (releasedLock) {
      released = true;
    }
    for (ListenerHolder<T> listenerHolder : listeners) {
      listenerHolder.release(iterationFinishedEvent);
    }
    listeners.clear();
  }

  /**
   * Sets whether methods throw when using the wrong thread.
   *
   * <p>Do not use this method unless to support legacy use cases.
   *
   * @param throwsWhenUsingWrongThread Whether to throw when using the wrong thread.
   * @deprecated Do not use this method and ensure all calls are made from the correct thread.
   */
  @Deprecated
  public void setThrowsWhenUsingWrongThread(boolean throwsWhenUsingWrongThread) {
    this.throwsWhenUsingWrongThread = throwsWhenUsingWrongThread;
  }

  private boolean handleMessage(Message message) {
    for (ListenerHolder<T> holder : listeners) {
      holder.iterationFinished(iterationFinishedEvent);
      if (handler.hasMessages(MSG_ITERATION_FINISHED)) {
        // The invocation above triggered new events (and thus scheduled a new message). We need
        // to stop here because this new message will take care of informing every listener about
        // the new update (including the ones already called here).
        break;
      }
    }
    return true;
  }

  private void verifyCurrentThread() {
    if (!throwsWhenUsingWrongThread) {
      return;
    }
    checkState(Thread.currentThread() == handler.getLooper().getThread());
  }

  private static final class ListenerHolder<T extends @NonNull Object> {

    public final T listener;

    private FlagSet.Builder flagsBuilder;
    private boolean needsIterationFinishedEvent;
    private boolean released;

    public ListenerHolder(T listener) {
      this.listener = listener;
      this.flagsBuilder = new FlagSet.Builder();
    }

    public void release(IterationFinishedEvent<T> event) {
      released = true;
      if (needsIterationFinishedEvent) {
        needsIterationFinishedEvent = false;
        event.invoke(listener, flagsBuilder.build());
      }
    }

    public void invoke(int eventFlag, Event<T> event) {
      if (!released) {
        if (eventFlag != C.INDEX_UNSET) {
          flagsBuilder.add(eventFlag);
        }
        needsIterationFinishedEvent = true;
        event.invoke(listener);
      }
    }

    public void iterationFinished(IterationFinishedEvent<T> event) {
      if (!released && needsIterationFinishedEvent) {
        // Reset flags before invoking the listener to ensure we keep all new flags that are set by
        // recursive events triggered from this callback.
        FlagSet flagsToNotify = flagsBuilder.build();
        flagsBuilder = new FlagSet.Builder();
        needsIterationFinishedEvent = false;
        event.invoke(listener, flagsToNotify);
      }
    }

    @Override
    public boolean equals(@Nullable Object other) {
      if (this == other) {
        return true;
      }
      if (other == null || getClass() != other.getClass()) {
        return false;
      }
      return listener.equals(((ListenerHolder<?>) other).listener);
    }

    @Override
    public int hashCode() {
      return listener.hashCode();
    }
  }
}
