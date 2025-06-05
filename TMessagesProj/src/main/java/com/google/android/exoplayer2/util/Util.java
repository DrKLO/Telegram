/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static android.content.Context.UI_MODE_SERVICE;
import static com.google.android.exoplayer2.Player.COMMAND_SEEK_BACK;
import static com.google.android.exoplayer2.Player.COMMAND_SEEK_FORWARD;
import static com.google.android.exoplayer2.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM;
import static com.google.android.exoplayer2.Player.COMMAND_SEEK_TO_DEFAULT_POSITION;
import static com.google.android.exoplayer2.Player.COMMAND_SEEK_TO_MEDIA_ITEM;
import static com.google.android.exoplayer2.Player.COMMAND_SEEK_TO_NEXT;
import static com.google.android.exoplayer2.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM;
import static com.google.android.exoplayer2.Player.COMMAND_SEEK_TO_PREVIOUS;
import static com.google.android.exoplayer2.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.Manifest.permission;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.MediaDrm;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.security.NetworkSecurityPolicy;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Base64;
import android.util.SparseLongArray;
import android.view.Display;
import android.view.SurfaceView;
import android.view.WindowManager;
import androidx.annotation.DoNotInline;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.C.ContentType;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Player.Commands;
import com.google.common.base.Ascii;
import com.google.common.base.Charsets;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Formatter;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.NoSuchElementException;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Inflater;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.compatqual.NullableType;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.PolyNull;

/** Miscellaneous utility methods. */
public final class Util {

  /**
   * Like {@link Build.VERSION#SDK_INT}, but in a place where it can be conveniently overridden for
   * local testing.
   */
  public static final int SDK_INT = Build.VERSION.SDK_INT;

  /**
   * Like {@link Build#DEVICE}, but in a place where it can be conveniently overridden for local
   * testing.
   */
  public static final String DEVICE = Build.DEVICE;

  /**
   * Like {@link Build#MANUFACTURER}, but in a place where it can be conveniently overridden for
   * local testing.
   */
  public static final String MANUFACTURER = Build.MANUFACTURER;

  /**
   * Like {@link Build#MODEL}, but in a place where it can be conveniently overridden for local
   * testing.
   */
  public static final String MODEL = Build.MODEL;

  /** A concise description of the device that it can be useful to log for debugging purposes. */
  public static final String DEVICE_DEBUG_INFO =
      DEVICE + ", " + MODEL + ", " + MANUFACTURER + ", " + SDK_INT;

  /** An empty byte array. */
  public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

  private static final String TAG = "Util";
  private static final Pattern XS_DATE_TIME_PATTERN =
      Pattern.compile(
          "(\\d\\d\\d\\d)\\-(\\d\\d)\\-(\\d\\d)[Tt]"
              + "(\\d\\d):(\\d\\d):(\\d\\d)([\\.,](\\d+))?"
              + "([Zz]|((\\+|\\-)(\\d?\\d):?(\\d\\d)))?");
  private static final Pattern XS_DURATION_PATTERN =
      Pattern.compile(
          "^(-)?P(([0-9]*)Y)?(([0-9]*)M)?(([0-9]*)D)?"
              + "(T(([0-9]*)H)?(([0-9]*)M)?(([0-9.]*)S)?)?$");
  private static final Pattern ESCAPED_CHARACTER_PATTERN = Pattern.compile("%([A-Fa-f0-9]{2})");

  // https://docs.microsoft.com/en-us/azure/media-services/previous/media-services-deliver-content-overview#URLs
  private static final Pattern ISM_PATH_PATTERN =
      Pattern.compile("(?:.*\\.)?isml?(?:/(manifest(.*))?)?", Pattern.CASE_INSENSITIVE);
  private static final String ISM_HLS_FORMAT_EXTENSION = "format=m3u8-aapl";
  private static final String ISM_DASH_FORMAT_EXTENSION = "format=mpd-time-csf";

  // Replacement map of ISO language codes used for normalization.
  @Nullable private static HashMap<String, String> languageTagReplacementMap;

  private Util() {}

  /**
   * Converts the entirety of an {@link InputStream} to a byte array.
   *
   * @param inputStream the {@link InputStream} to be read. The input stream is not closed by this
   *     method.
   * @return a byte array containing all of the inputStream's bytes.
   * @throws IOException if an error occurs reading from the stream.
   */
  public static byte[] toByteArray(InputStream inputStream) throws IOException {
    byte[] buffer = new byte[1024 * 4];
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    int bytesRead;
    while ((bytesRead = inputStream.read(buffer)) != -1) {
      outputStream.write(buffer, 0, bytesRead);
    }
    return outputStream.toByteArray();
  }

  /**
   * Registers a {@link BroadcastReceiver} that's not intended to receive broadcasts from other
   * apps. This will be enforced by specifying {@link Context#RECEIVER_NOT_EXPORTED} if {@link
   * #SDK_INT} is 33 or above.
   *
   * @param context The context on which {@link Context#registerReceiver} will be called.
   * @param receiver The {@link BroadcastReceiver} to register. This value may be null.
   * @param filter Selects the Intent broadcasts to be received.
   * @return The first sticky intent found that matches {@code filter}, or null if there are none.
   */
  @Nullable
  public static Intent registerReceiverNotExported(
      Context context, @Nullable BroadcastReceiver receiver, IntentFilter filter) {
    if (SDK_INT < 33) {
      return context.registerReceiver(receiver, filter);
    } else {
      return context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }
  }

  /**
   * Registers a {@link BroadcastReceiver} that's not intended to receive broadcasts from other
   * apps. This will be enforced by specifying {@link Context#RECEIVER_NOT_EXPORTED} if {@link
   * #SDK_INT} is 33 or above.
   *
   * @param context The context on which {@link Context#registerReceiver} will be called.
   * @param receiver The {@link BroadcastReceiver} to register. This value may be null.
   * @param filter Selects the Intent broadcasts to be received.
   * @param handler Handler identifying the thread that will receive the Intent.
   * @return The first sticky intent found that matches {@code filter}, or null if there are none.
   */
  @Nullable
  public static Intent registerReceiverNotExported(
      Context context, BroadcastReceiver receiver, IntentFilter filter, Handler handler) {
    if (SDK_INT < 33) {
      return context.registerReceiver(receiver, filter, /* broadcastPermission= */ null, handler);
    } else {
      return context.registerReceiver(
          receiver,
          filter,
          /* broadcastPermission= */ null,
          handler,
          Context.RECEIVER_NOT_EXPORTED);
    }
  }

  /**
   * Calls {@link Context#startForegroundService(Intent)} if {@link #SDK_INT} is 26 or higher, or
   * {@link Context#startService(Intent)} otherwise.
   *
   * @param context The context to call.
   * @param intent The intent to pass to the called method.
   * @return The result of the called method.
   */
  @Nullable
  public static ComponentName startForegroundService(Context context, Intent intent) {
    if (SDK_INT >= 26) {
      return context.startForegroundService(intent);
    } else {
      return context.startService(intent);
    }
  }

  /**
   * Checks whether it's necessary to request the {@link permission#READ_EXTERNAL_STORAGE}
   * permission read the specified {@link Uri}s, requesting the permission if necessary.
   *
   * @param activity The host activity for checking and requesting the permission.
   * @param uris {@link Uri}s that may require {@link permission#READ_EXTERNAL_STORAGE} to read.
   * @return Whether a permission request was made.
   */
  public static boolean maybeRequestReadExternalStoragePermission(Activity activity, Uri... uris) {
    if (SDK_INT < 23) {
      return false;
    }
    for (Uri uri : uris) {
      if (maybeRequestReadExternalStoragePermission(activity, uri)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks whether it's necessary to request the {@link permission#READ_EXTERNAL_STORAGE}
   * permission for the specified {@link MediaItem media items}, requesting the permission if
   * necessary.
   *
   * @param activity The host activity for checking and requesting the permission.
   * @param mediaItems {@link MediaItem Media items}s that may require {@link
   *     permission#READ_EXTERNAL_STORAGE} to read.
   * @return Whether a permission request was made.
   */
  public static boolean maybeRequestReadExternalStoragePermission(
      Activity activity, MediaItem... mediaItems) {
    if (SDK_INT < 23) {
      return false;
    }
    for (MediaItem mediaItem : mediaItems) {
      if (mediaItem.localConfiguration == null) {
        continue;
      }
      if (maybeRequestReadExternalStoragePermission(activity, mediaItem.localConfiguration.uri)) {
        return true;
      }
      List<MediaItem.SubtitleConfiguration> subtitleConfigs =
          mediaItem.localConfiguration.subtitleConfigurations;
      for (int i = 0; i < subtitleConfigs.size(); i++) {
        if (maybeRequestReadExternalStoragePermission(activity, subtitleConfigs.get(i).uri)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean maybeRequestReadExternalStoragePermission(Activity activity, Uri uri) {
    return SDK_INT >= 23
        && (isLocalFileUri(uri) || isMediaStoreExternalContentUri(uri))
        && requestExternalStoragePermission(activity);
  }

  private static boolean isMediaStoreExternalContentUri(Uri uri) {
    if (!"content".equals(uri.getScheme()) || !MediaStore.AUTHORITY.equals(uri.getAuthority())) {
      return false;
    }
    List<String> pathSegments = uri.getPathSegments();
    if (pathSegments.isEmpty()) {
      return false;
    }
    String firstPathSegment = pathSegments.get(0);
    return MediaStore.VOLUME_EXTERNAL.equals(firstPathSegment)
        || MediaStore.VOLUME_EXTERNAL_PRIMARY.equals(firstPathSegment);
  }

  /**
   * Returns whether it may be possible to load the URIs of the given media items based on the
   * network security policy's cleartext traffic permissions.
   *
   * @param mediaItems A list of {@link MediaItem media items}.
   * @return Whether it may be possible to load the URIs of the given media items.
   */
  public static boolean checkCleartextTrafficPermitted(MediaItem... mediaItems) {
    if (SDK_INT < 24) {
      // We assume cleartext traffic is permitted.
      return true;
    }
    for (MediaItem mediaItem : mediaItems) {
      if (mediaItem.localConfiguration == null) {
        continue;
      }
      if (isTrafficRestricted(mediaItem.localConfiguration.uri)) {
        return false;
      }
      for (int i = 0; i < mediaItem.localConfiguration.subtitleConfigurations.size(); i++) {
        if (isTrafficRestricted(mediaItem.localConfiguration.subtitleConfigurations.get(i).uri)) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Returns true if the URI is a path to a local file or a reference to a local file.
   *
   * @param uri The uri to test.
   */
  public static boolean isLocalFileUri(Uri uri) {
    String scheme = uri.getScheme();
    return TextUtils.isEmpty(scheme) || "file".equals(scheme);
  }

  /**
   * Tests two objects for {@link Object#equals(Object)} equality, handling the case where one or
   * both may be null.
   *
   * @param o1 The first object.
   * @param o2 The second object.
   * @return {@code o1 == null ? o2 == null : o1.equals(o2)}.
   */
  public static boolean areEqual(@Nullable Object o1, @Nullable Object o2) {
    return o1 == null ? o2 == null : o1.equals(o2);
  }

  /**
   * Tests whether an {@code items} array contains an object equal to {@code item}, according to
   * {@link Object#equals(Object)}.
   *
   * <p>If {@code item} is null then true is returned if and only if {@code items} contains null.
   *
   * @param items The array of items to search.
   * @param item The item to search for.
   * @return True if the array contains an object equal to the item being searched for.
   */
  public static boolean contains(@NullableType Object[] items, @Nullable Object item) {
    for (Object arrayItem : items) {
      if (areEqual(arrayItem, item)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Removes an indexed range from a List.
   *
   * <p>Does nothing if the provided range is valid and {@code fromIndex == toIndex}.
   *
   * @param list The List to remove the range from.
   * @param fromIndex The first index to be removed (inclusive).
   * @param toIndex The last index to be removed (exclusive).
   * @throws IllegalArgumentException If {@code fromIndex} &lt; 0, {@code toIndex} &gt; {@code
   *     list.size()}, or {@code fromIndex} &gt; {@code toIndex}.
   */
  public static <T> void removeRange(List<T> list, int fromIndex, int toIndex) {
    if (fromIndex < 0 || toIndex > list.size() || fromIndex > toIndex) {
      throw new IllegalArgumentException();
    } else if (fromIndex != toIndex) {
      // Checking index inequality prevents an unnecessary allocation.
      list.subList(fromIndex, toIndex).clear();
    }
  }

  /**
   * Casts a nullable variable to a non-null variable without runtime null check.
   *
   * <p>Use {@link Assertions#checkNotNull(Object)} to throw if the value is null.
   */
  @SuppressWarnings({"nullness:contracts.postcondition", "nullness:return"})
  @EnsuresNonNull("#1")
  public static <T> T castNonNull(@Nullable T value) {
    return value;
  }

  /** Casts a nullable type array to a non-null type array without runtime null check. */
  @SuppressWarnings({"nullness:contracts.postcondition", "nullness:return"})
  @EnsuresNonNull("#1")
  public static <T> T[] castNonNullTypeArray(@NullableType T[] value) {
    return value;
  }

  /**
   * Copies and optionally truncates an array. Prevents null array elements created by {@link
   * Arrays#copyOf(Object[], int)} by ensuring the new length does not exceed the current length.
   *
   * @param input The input array.
   * @param length The output array length. Must be less or equal to the length of the input array.
   * @return The copied array.
   */
  @SuppressWarnings({"nullness:argument", "nullness:return"})
  public static <T> T[] nullSafeArrayCopy(T[] input, int length) {
    Assertions.checkArgument(length <= input.length);
    return Arrays.copyOf(input, length);
  }

  /**
   * Copies a subset of an array.
   *
   * @param input The input array.
   * @param from The start the range to be copied, inclusive
   * @param to The end of the range to be copied, exclusive.
   * @return The copied array.
   */
  @SuppressWarnings({"nullness:argument", "nullness:return"})
  public static <T> T[] nullSafeArrayCopyOfRange(T[] input, int from, int to) {
    Assertions.checkArgument(0 <= from);
    Assertions.checkArgument(to <= input.length);
    return Arrays.copyOfRange(input, from, to);
  }

  /**
   * Creates a new array containing {@code original} with {@code newElement} appended.
   *
   * @param original The input array.
   * @param newElement The element to append.
   * @return The new array.
   */
  public static <T> T[] nullSafeArrayAppend(T[] original, T newElement) {
    @NullableType T[] result = Arrays.copyOf(original, original.length + 1);
    result[original.length] = newElement;
    return castNonNullTypeArray(result);
  }

  /**
   * Creates a new array containing the concatenation of two non-null type arrays.
   *
   * @param first The first array.
   * @param second The second array.
   * @return The concatenated result.
   */
  @SuppressWarnings("nullness:assignment")
  public static <T> T[] nullSafeArrayConcatenation(T[] first, T[] second) {
    T[] concatenation = Arrays.copyOf(first, first.length + second.length);
    System.arraycopy(
        /* src= */ second,
        /* srcPos= */ 0,
        /* dest= */ concatenation,
        /* destPos= */ first.length,
        /* length= */ second.length);
    return concatenation;
  }

  /**
   * Copies the contents of {@code list} into {@code array}.
   *
   * <p>{@code list.size()} must be the same as {@code array.length} to ensure the contents can be
   * copied into {@code array} without leaving any nulls at the end.
   *
   * @param list The list to copy items from.
   * @param array The array to copy items to.
   */
  @SuppressWarnings("nullness:toArray.nullable.elements.not.newarray")
  public static <T> void nullSafeListToArray(List<T> list, T[] array) {
    Assertions.checkState(list.size() == array.length);
    list.toArray(array);
  }

  /**
   * Creates a {@link Handler} on the current {@link Looper} thread.
   *
   * @throws IllegalStateException If the current thread doesn't have a {@link Looper}.
   */
  public static Handler createHandlerForCurrentLooper() {
    return createHandlerForCurrentLooper(/* callback= */ null);
  }

  /**
   * Creates a {@link Handler} with the specified {@link Handler.Callback} on the current {@link
   * Looper} thread.
   *
   * <p>The method accepts partially initialized objects as callback under the assumption that the
   * Handler won't be used to send messages until the callback is fully initialized.
   *
   * @param callback A {@link Handler.Callback}. May be a partially initialized class, or null if no
   *     callback is required.
   * @return A {@link Handler} with the specified callback on the current {@link Looper} thread.
   * @throws IllegalStateException If the current thread doesn't have a {@link Looper}.
   */
  public static Handler createHandlerForCurrentLooper(
      @Nullable Handler.@UnknownInitialization Callback callback) {
    return createHandler(Assertions.checkStateNotNull(Looper.myLooper()), callback);
  }

  /**
   * Creates a {@link Handler} on the current {@link Looper} thread.
   *
   * <p>If the current thread doesn't have a {@link Looper}, the application's main thread {@link
   * Looper} is used.
   */
  public static Handler createHandlerForCurrentOrMainLooper() {
    return createHandlerForCurrentOrMainLooper(/* callback= */ null);
  }

  /**
   * Creates a {@link Handler} with the specified {@link Handler.Callback} on the current {@link
   * Looper} thread.
   *
   * <p>The method accepts partially initialized objects as callback under the assumption that the
   * Handler won't be used to send messages until the callback is fully initialized.
   *
   * <p>If the current thread doesn't have a {@link Looper}, the application's main thread {@link
   * Looper} is used.
   *
   * @param callback A {@link Handler.Callback}. May be a partially initialized class, or null if no
   *     callback is required.
   * @return A {@link Handler} with the specified callback on the current {@link Looper} thread.
   */
  public static Handler createHandlerForCurrentOrMainLooper(
      @Nullable Handler.@UnknownInitialization Callback callback) {
    return createHandler(getCurrentOrMainLooper(), callback);
  }

  /**
   * Creates a {@link Handler} with the specified {@link Handler.Callback} on the specified {@link
   * Looper} thread.
   *
   * <p>The method accepts partially initialized objects as callback under the assumption that the
   * Handler won't be used to send messages until the callback is fully initialized.
   *
   * @param looper A {@link Looper} to run the callback on.
   * @param callback A {@link Handler.Callback}. May be a partially initialized class, or null if no
   *     callback is required.
   * @return A {@link Handler} with the specified callback on the current {@link Looper} thread.
   */
  @SuppressWarnings({"nullness:argument", "nullness:return"})
  public static Handler createHandler(
      Looper looper, @Nullable Handler.@UnknownInitialization Callback callback) {
    return new Handler(looper, callback);
  }

  /**
   * Posts the {@link Runnable} if the calling thread differs with the {@link Looper} of the {@link
   * Handler}. Otherwise, runs the {@link Runnable} directly.
   *
   * @param handler The handler to which the {@link Runnable} will be posted.
   * @param runnable The runnable to either post or run.
   * @return {@code true} if the {@link Runnable} was successfully posted to the {@link Handler} or
   *     run. {@code false} otherwise.
   */
  public static boolean postOrRun(Handler handler, Runnable runnable) {
    Looper looper = handler.getLooper();
    if (!looper.getThread().isAlive()) {
      return false;
    }
    if (handler.getLooper() == Looper.myLooper()) {
      runnable.run();
      return true;
    } else {
      return handler.post(runnable);
    }
  }

  /**
   * Posts the {@link Runnable} if the calling thread differs with the {@link Looper} of the {@link
   * Handler}. Otherwise, runs the {@link Runnable} directly. Also returns a {@link
   * ListenableFuture} for when the {@link Runnable} has run.
   *
   * @param handler The handler to which the {@link Runnable} will be posted.
   * @param runnable The runnable to either post or run.
   * @param successValue The value to set in the {@link ListenableFuture} once the runnable
   *     completes.
   * @param <T> The type of {@code successValue}.
   * @return A {@link ListenableFuture} for when the {@link Runnable} has run.
   */
  public static <T> ListenableFuture<T> postOrRunWithCompletion(
      Handler handler, Runnable runnable, T successValue) {
    SettableFuture<T> outputFuture = SettableFuture.create();
    postOrRun(
        handler,
        () -> {
          try {
            if (outputFuture.isCancelled()) {
              return;
            }
            runnable.run();
            outputFuture.set(successValue);
          } catch (Throwable e) {
            outputFuture.setException(e);
          }
        });
    return outputFuture;
  }

  /**
   * Asynchronously transforms the result of a {@link ListenableFuture}.
   *
   * <p>The transformation function is called using a {@linkplain MoreExecutors#directExecutor()
   * direct executor}.
   *
   * <p>The returned Future attempts to keep its cancellation state in sync with that of the input
   * future and that of the future returned by the transform function. That is, if the returned
   * Future is cancelled, it will attempt to cancel the other two, and if either of the other two is
   * cancelled, the returned Future will also be cancelled. All forwarded cancellations will not
   * attempt to interrupt.
   *
   * @param future The input {@link ListenableFuture}.
   * @param transformFunction The function transforming the result of the input future.
   * @param <T> The result type of the input future.
   * @param <U> The result type of the transformation function.
   * @return A {@link ListenableFuture} for the transformed result.
   */
  public static <T, U> ListenableFuture<T> transformFutureAsync(
      ListenableFuture<U> future, AsyncFunction<U, T> transformFunction) {
    // This is a simplified copy of Guava's Futures.transformAsync.
    SettableFuture<T> outputFuture = SettableFuture.create();
    outputFuture.addListener(
        () -> {
          if (outputFuture.isCancelled()) {
            future.cancel(/* mayInterruptIfRunning= */ false);
          }
        },
        MoreExecutors.directExecutor());
    future.addListener(
        () -> {
          U inputFutureResult;
          try {
            inputFutureResult = Futures.getDone(future);
          } catch (CancellationException cancellationException) {
            outputFuture.cancel(/* mayInterruptIfRunning= */ false);
            return;
          } catch (ExecutionException exception) {
            @Nullable Throwable cause = exception.getCause();
            outputFuture.setException(cause == null ? exception : cause);
            return;
          } catch (RuntimeException | Error error) {
            outputFuture.setException(error);
            return;
          }
          try {
            outputFuture.setFuture(transformFunction.apply(inputFutureResult));
          } catch (Throwable exception) {
            outputFuture.setException(exception);
          }
        },
        MoreExecutors.directExecutor());
    return outputFuture;
  }

  /**
   * Returns the {@link Looper} associated with the current thread, or the {@link Looper} of the
   * application's main thread if the current thread doesn't have a {@link Looper}.
   */
  public static Looper getCurrentOrMainLooper() {
    @Nullable Looper myLooper = Looper.myLooper();
    return myLooper != null ? myLooper : Looper.getMainLooper();
  }

  /**
   * Instantiates a new single threaded executor whose thread has the specified name.
   *
   * @param threadName The name of the thread.
   * @return The executor.
   */
  public static ExecutorService newSingleThreadExecutor(String threadName) {
    return Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, threadName));
  }

  /**
   * Closes a {@link Closeable}, suppressing any {@link IOException} that may occur. Both {@link
   * java.io.OutputStream} and {@link InputStream} are {@code Closeable}.
   *
   * @param closeable The {@link Closeable} to close.
   */
  public static void closeQuietly(@Nullable Closeable closeable) {
    try {
      if (closeable != null) {
        closeable.close();
      }
    } catch (IOException e) {
      // Ignore.
    }
  }

  /**
   * Reads an integer from a {@link Parcel} and interprets it as a boolean, with 0 mapping to false
   * and all other values mapping to true.
   *
   * @param parcel The {@link Parcel} to read from.
   * @return The read value.
   */
  public static boolean readBoolean(Parcel parcel) {
    return parcel.readInt() != 0;
  }

  /**
   * Writes a boolean to a {@link Parcel}. The boolean is written as an integer with value 1 (true)
   * or 0 (false).
   *
   * @param parcel The {@link Parcel} to write to.
   * @param value The value to write.
   */
  public static void writeBoolean(Parcel parcel, boolean value) {
    parcel.writeInt(value ? 1 : 0);
  }

  /**
   * Returns the language tag for a {@link Locale}.
   *
   * <p>For API levels &ge; 21, this tag is IETF BCP 47 compliant. Use {@link
   * #normalizeLanguageCode(String)} to retrieve a normalized IETF BCP 47 language tag for all API
   * levels if needed.
   *
   * @param locale A {@link Locale}.
   * @return The language tag.
   */
  public static String getLocaleLanguageTag(Locale locale) {
    return SDK_INT >= 21 ? getLocaleLanguageTagV21(locale) : locale.toString();
  }

  /**
   * Returns a normalized IETF BCP 47 language tag for {@code language}.
   *
   * @param language A case-insensitive language code supported by {@link
   *     Locale#forLanguageTag(String)}.
   * @return The all-lowercase normalized code, or null if the input was null, or {@code
   *     language.toLowerCase()} if the language could not be normalized.
   */
  public static @PolyNull String normalizeLanguageCode(@PolyNull String language) {
    if (language == null) {
      return null;
    }
    // Locale data (especially for API < 21) may produce tags with '_' instead of the
    // standard-conformant '-'.
    String normalizedTag = language.replace('_', '-');
    if (normalizedTag.isEmpty() || normalizedTag.equals(C.LANGUAGE_UNDETERMINED)) {
      // Tag isn't valid, keep using the original.
      normalizedTag = language;
    }
    normalizedTag = Ascii.toLowerCase(normalizedTag);
    String mainLanguage = splitAtFirst(normalizedTag, "-")[0];
    if (languageTagReplacementMap == null) {
      languageTagReplacementMap = createIsoLanguageReplacementMap();
    }
    @Nullable String replacedLanguage = languageTagReplacementMap.get(mainLanguage);
    if (replacedLanguage != null) {
      normalizedTag =
          replacedLanguage + normalizedTag.substring(/* beginIndex= */ mainLanguage.length());
      mainLanguage = replacedLanguage;
    }
    if ("no".equals(mainLanguage) || "i".equals(mainLanguage) || "zh".equals(mainLanguage)) {
      normalizedTag = maybeReplaceLegacyLanguageTags(normalizedTag);
    }
    return normalizedTag;
  }

  /**
   * Returns a new {@link String} constructed by decoding UTF-8 encoded bytes.
   *
   * @param bytes The UTF-8 encoded bytes to decode.
   * @return The string.
   */
  public static String fromUtf8Bytes(byte[] bytes) {
    return new String(bytes, Charsets.UTF_8);
  }

  /**
   * Returns a new {@link String} constructed by decoding UTF-8 encoded bytes in a subarray.
   *
   * @param bytes The UTF-8 encoded bytes to decode.
   * @param offset The index of the first byte to decode.
   * @param length The number of bytes to decode.
   * @return The string.
   */
  public static String fromUtf8Bytes(byte[] bytes, int offset, int length) {
    return new String(bytes, offset, length, Charsets.UTF_8);
  }

  /**
   * Returns a new byte array containing the code points of a {@link String} encoded using UTF-8.
   *
   * @param value The {@link String} whose bytes should be obtained.
   * @return The code points encoding using UTF-8.
   */
  public static byte[] getUtf8Bytes(String value) {
    return value.getBytes(Charsets.UTF_8);
  }

  /**
   * Splits a string using {@code value.split(regex, -1}). Note: this is is similar to {@link
   * String#split(String)} but empty matches at the end of the string will not be omitted from the
   * returned array.
   *
   * @param value The string to split.
   * @param regex A delimiting regular expression.
   * @return The array of strings resulting from splitting the string.
   */
  public static String[] split(String value, String regex) {
    return value.split(regex, /* limit= */ -1);
  }

  /**
   * Splits the string at the first occurrence of the delimiter {@code regex}. If the delimiter does
   * not match, returns an array with one element which is the input string. If the delimiter does
   * match, returns an array with the portion of the string before the delimiter and the rest of the
   * string.
   *
   * @param value The string.
   * @param regex A delimiting regular expression.
   * @return The string split by the first occurrence of the delimiter.
   */
  public static String[] splitAtFirst(String value, String regex) {
    return value.split(regex, /* limit= */ 2);
  }

  /**
   * Returns whether the given character is a carriage return ('\r') or a line feed ('\n').
   *
   * @param c The character.
   * @return Whether the given character is a linebreak.
   */
  public static boolean isLinebreak(int c) {
    return c == '\n' || c == '\r';
  }

  /**
   * Formats a string using {@link Locale#US}.
   *
   * @see String#format(String, Object...)
   */
  public static String formatInvariant(String format, Object... args) {
    return String.format(Locale.US, format, args);
  }

  /**
   * Divides a {@code numerator} by a {@code denominator}, returning the ceiled result.
   *
   * @param numerator The numerator to divide.
   * @param denominator The denominator to divide by.
   * @return The ceiled result of the division.
   */
  public static int ceilDivide(int numerator, int denominator) {
    return (numerator + denominator - 1) / denominator;
  }

  /**
   * Divides a {@code numerator} by a {@code denominator}, returning the ceiled result.
   *
   * @param numerator The numerator to divide.
   * @param denominator The denominator to divide by.
   * @return The ceiled result of the division.
   */
  public static long ceilDivide(long numerator, long denominator) {
    return (numerator + denominator - 1) / denominator;
  }

  /**
   * Constrains a value to the specified bounds.
   *
   * @param value The value to constrain.
   * @param min The lower bound.
   * @param max The upper bound.
   * @return The constrained value {@code Math.max(min, Math.min(value, max))}.
   */
  public static int constrainValue(int value, int min, int max) {
    return max(min, min(value, max));
  }

  /**
   * Constrains a value to the specified bounds.
   *
   * @param value The value to constrain.
   * @param min The lower bound.
   * @param max The upper bound.
   * @return The constrained value {@code Math.max(min, Math.min(value, max))}.
   */
  public static long constrainValue(long value, long min, long max) {
    return max(min, min(value, max));
  }

  /**
   * Constrains a value to the specified bounds.
   *
   * @param value The value to constrain.
   * @param min The lower bound.
   * @param max The upper bound.
   * @return The constrained value {@code Math.max(min, Math.min(value, max))}.
   */
  public static float constrainValue(float value, float min, float max) {
    return max(min, min(value, max));
  }

  /**
   * Returns the sum of two arguments, or a third argument if the result overflows.
   *
   * @param x The first value.
   * @param y The second value.
   * @param overflowResult The return value if {@code x + y} overflows.
   * @return {@code x + y}, or {@code overflowResult} if the result overflows.
   */
  public static long addWithOverflowDefault(long x, long y, long overflowResult) {
    long result = x + y;
    // See Hacker's Delight 2-13 (H. Warren Jr).
    if (((x ^ result) & (y ^ result)) < 0) {
      return overflowResult;
    }
    return result;
  }

  /**
   * Returns the difference between two arguments, or a third argument if the result overflows.
   *
   * @param x The first value.
   * @param y The second value.
   * @param overflowResult The return value if {@code x - y} overflows.
   * @return {@code x - y}, or {@code overflowResult} if the result overflows.
   */
  public static long subtractWithOverflowDefault(long x, long y, long overflowResult) {
    long result = x - y;
    // See Hacker's Delight 2-13 (H. Warren Jr).
    if (((x ^ y) & (x ^ result)) < 0) {
      return overflowResult;
    }
    return result;
  }

  /**
   * Returns the index of the first occurrence of {@code value} in {@code array}, or {@link
   * C#INDEX_UNSET} if {@code value} is not contained in {@code array}.
   *
   * @param array The array to search.
   * @param value The value to search for.
   * @return The index of the first occurrence of value in {@code array}, or {@link C#INDEX_UNSET}
   *     if {@code value} is not contained in {@code array}.
   */
  public static int linearSearch(int[] array, int value) {
    for (int i = 0; i < array.length; i++) {
      if (array[i] == value) {
        return i;
      }
    }
    return C.INDEX_UNSET;
  }

  /**
   * Returns the index of the first occurrence of {@code value} in {@code array}, or {@link
   * C#INDEX_UNSET} if {@code value} is not contained in {@code array}.
   *
   * @param array The array to search.
   * @param value The value to search for.
   * @return The index of the first occurrence of value in {@code array}, or {@link C#INDEX_UNSET}
   *     if {@code value} is not contained in {@code array}.
   */
  public static int linearSearch(long[] array, long value) {
    for (int i = 0; i < array.length; i++) {
      if (array[i] == value) {
        return i;
      }
    }
    return C.INDEX_UNSET;
  }

  /**
   * Returns the index of the largest element in {@code array} that is less than (or optionally
   * equal to) a specified {@code value}.
   *
   * <p>The search is performed using a binary search algorithm, so the array must be sorted. If the
   * array contains multiple elements equal to {@code value} and {@code inclusive} is true, the
   * index of the first one will be returned.
   *
   * @param array The array to search.
   * @param value The value being searched for.
   * @param inclusive If the value is present in the array, whether to return the corresponding
   *     index. If false then the returned index corresponds to the largest element strictly less
   *     than the value.
   * @param stayInBounds If true, then 0 will be returned in the case that the value is smaller than
   *     the smallest element in the array. If false then -1 will be returned.
   * @return The index of the largest element in {@code array} that is less than (or optionally
   *     equal to) {@code value}.
   */
  public static int binarySearchFloor(
      int[] array, int value, boolean inclusive, boolean stayInBounds) {
    int index = Arrays.binarySearch(array, value);
    if (index < 0) {
      index = -(index + 2);
    } else {
      while (--index >= 0 && array[index] == value) {}
      if (inclusive) {
        index++;
      }
    }
    return stayInBounds ? max(0, index) : index;
  }

  /**
   * Returns the index of the largest element in {@code array} that is less than (or optionally
   * equal to) a specified {@code value}.
   *
   * <p>The search is performed using a binary search algorithm, so the array must be sorted. If the
   * array contains multiple elements equal to {@code value} and {@code inclusive} is true, the
   * index of the first one will be returned.
   *
   * @param array The array to search.
   * @param value The value being searched for.
   * @param inclusive If the value is present in the array, whether to return the corresponding
   *     index. If false then the returned index corresponds to the largest element strictly less
   *     than the value.
   * @param stayInBounds If true, then 0 will be returned in the case that the value is smaller than
   *     the smallest element in the array. If false then -1 will be returned.
   * @return The index of the largest element in {@code array} that is less than (or optionally
   *     equal to) {@code value}.
   */
  public static int binarySearchFloor(
      long[] array, long value, boolean inclusive, boolean stayInBounds) {
    int index = Arrays.binarySearch(array, value);
    if (index < 0) {
      index = -(index + 2);
    } else {
      while (--index >= 0 && array[index] == value) {}
      if (inclusive) {
        index++;
      }
    }
    return stayInBounds ? max(0, index) : index;
  }

  /**
   * Returns the index of the largest element in {@code list} that is less than (or optionally equal
   * to) a specified {@code value}.
   *
   * <p>The search is performed using a binary search algorithm, so the list must be sorted. If the
   * list contains multiple elements equal to {@code value} and {@code inclusive} is true, the index
   * of the first one will be returned.
   *
   * @param <T> The type of values being searched.
   * @param list The list to search.
   * @param value The value being searched for.
   * @param inclusive If the value is present in the list, whether to return the corresponding
   *     index. If false then the returned index corresponds to the largest element strictly less
   *     than the value.
   * @param stayInBounds If true, then 0 will be returned in the case that the value is smaller than
   *     the smallest element in the list. If false then -1 will be returned.
   * @return The index of the largest element in {@code list} that is less than (or optionally equal
   *     to) {@code value}.
   */
  public static <T extends Comparable<? super T>> int binarySearchFloor(
      List<? extends Comparable<? super T>> list,
      T value,
      boolean inclusive,
      boolean stayInBounds) {
    int index = Collections.binarySearch(list, value);
    if (index < 0) {
      index = -(index + 2);
    } else {
      while (--index >= 0 && list.get(index).compareTo(value) == 0) {}
      if (inclusive) {
        index++;
      }
    }
    return stayInBounds ? max(0, index) : index;
  }

  /**
   * Returns the index of the largest element in {@code longArray} that is less than (or optionally
   * equal to) a specified {@code value}.
   *
   * <p>The search is performed using a binary search algorithm, so the array must be sorted. If the
   * array contains multiple elements equal to {@code value} and {@code inclusive} is true, the
   * index of the first one will be returned.
   *
   * @param longArray The array to search.
   * @param value The value being searched for.
   * @param inclusive If the value is present in the array, whether to return the corresponding
   *     index. If false then the returned index corresponds to the largest element strictly less
   *     than the value.
   * @param stayInBounds If true, then 0 will be returned in the case that the value is smaller than
   *     the smallest element in the array. If false then -1 will be returned.
   * @return The index of the largest element in {@code array} that is less than (or optionally
   *     equal to) {@code value}.
   */
  public static int binarySearchFloor(
      LongArray longArray, long value, boolean inclusive, boolean stayInBounds) {
    int lowIndex = 0;
    int highIndex = longArray.size() - 1;

    while (lowIndex <= highIndex) {
      int midIndex = (lowIndex + highIndex) >>> 1;
      if (longArray.get(midIndex) < value) {
        lowIndex = midIndex + 1;
      } else {
        highIndex = midIndex - 1;
      }
    }

    if (inclusive && highIndex + 1 < longArray.size() && longArray.get(highIndex + 1) == value) {
      highIndex++;
    } else if (stayInBounds && highIndex == -1) {
      highIndex = 0;
    }

    return highIndex;
  }

  /**
   * Returns the index of the smallest element in {@code array} that is greater than (or optionally
   * equal to) a specified {@code value}.
   *
   * <p>The search is performed using a binary search algorithm, so the array must be sorted. If the
   * array contains multiple elements equal to {@code value} and {@code inclusive} is true, the
   * index of the last one will be returned.
   *
   * @param array The array to search.
   * @param value The value being searched for.
   * @param inclusive If the value is present in the array, whether to return the corresponding
   *     index. If false then the returned index corresponds to the smallest element strictly
   *     greater than the value.
   * @param stayInBounds If true, then {@code (a.length - 1)} will be returned in the case that the
   *     value is greater than the largest element in the array. If false then {@code a.length} will
   *     be returned.
   * @return The index of the smallest element in {@code array} that is greater than (or optionally
   *     equal to) {@code value}.
   */
  public static int binarySearchCeil(
      int[] array, int value, boolean inclusive, boolean stayInBounds) {
    int index = Arrays.binarySearch(array, value);
    if (index < 0) {
      index = ~index;
    } else {
      while (++index < array.length && array[index] == value) {}
      if (inclusive) {
        index--;
      }
    }
    return stayInBounds ? min(array.length - 1, index) : index;
  }

  /**
   * Returns the index of the smallest element in {@code array} that is greater than (or optionally
   * equal to) a specified {@code value}.
   *
   * <p>The search is performed using a binary search algorithm, so the array must be sorted. If the
   * array contains multiple elements equal to {@code value} and {@code inclusive} is true, the
   * index of the last one will be returned.
   *
   * @param array The array to search.
   * @param value The value being searched for.
   * @param inclusive If the value is present in the array, whether to return the corresponding
   *     index. If false then the returned index corresponds to the smallest element strictly
   *     greater than the value.
   * @param stayInBounds If true, then {@code (a.length - 1)} will be returned in the case that the
   *     value is greater than the largest element in the array. If false then {@code a.length} will
   *     be returned.
   * @return The index of the smallest element in {@code array} that is greater than (or optionally
   *     equal to) {@code value}.
   */
  public static int binarySearchCeil(
      long[] array, long value, boolean inclusive, boolean stayInBounds) {
    int index = Arrays.binarySearch(array, value);
    if (index < 0) {
      index = ~index;
    } else {
      while (++index < array.length && array[index] == value) {}
      if (inclusive) {
        index--;
      }
    }
    return stayInBounds ? min(array.length - 1, index) : index;
  }

  /**
   * Returns the index of the smallest element in {@code list} that is greater than (or optionally
   * equal to) a specified value.
   *
   * <p>The search is performed using a binary search algorithm, so the list must be sorted. If the
   * list contains multiple elements equal to {@code value} and {@code inclusive} is true, the index
   * of the last one will be returned.
   *
   * @param <T> The type of values being searched.
   * @param list The list to search.
   * @param value The value being searched for.
   * @param inclusive If the value is present in the list, whether to return the corresponding
   *     index. If false then the returned index corresponds to the smallest element strictly
   *     greater than the value.
   * @param stayInBounds If true, then {@code (list.size() - 1)} will be returned in the case that
   *     the value is greater than the largest element in the list. If false then {@code
   *     list.size()} will be returned.
   * @return The index of the smallest element in {@code list} that is greater than (or optionally
   *     equal to) {@code value}.
   */
  public static <T extends Comparable<? super T>> int binarySearchCeil(
      List<? extends Comparable<? super T>> list,
      T value,
      boolean inclusive,
      boolean stayInBounds) {
    int index = Collections.binarySearch(list, value);
    if (index < 0) {
      index = ~index;
    } else {
      int listSize = list.size();
      while (++index < listSize && list.get(index).compareTo(value) == 0) {}
      if (inclusive) {
        index--;
      }
    }
    return stayInBounds ? min(list.size() - 1, index) : index;
  }

  /**
   * Compares two long values and returns the same value as {@code Long.compare(long, long)}.
   *
   * @param left The left operand.
   * @param right The right operand.
   * @return 0, if left == right, a negative value if left &lt; right, or a positive value if left
   *     &gt; right.
   */
  public static int compareLong(long left, long right) {
    return left < right ? -1 : left == right ? 0 : 1;
  }

  /**
   * Returns the minimum value in the given {@link SparseLongArray}.
   *
   * @param sparseLongArray The {@link SparseLongArray}.
   * @return The minimum value.
   * @throws NoSuchElementException If the array is empty.
   */
  @RequiresApi(18)
  public static long minValue(SparseLongArray sparseLongArray) {
    if (sparseLongArray.size() == 0) {
      throw new NoSuchElementException();
    }
    long min = Long.MAX_VALUE;
    for (int i = 0; i < sparseLongArray.size(); i++) {
      min = min(min, sparseLongArray.valueAt(i));
    }
    return min;
  }

  /**
   * Returns the maximum value in the given {@link SparseLongArray}.
   *
   * @param sparseLongArray The {@link SparseLongArray}.
   * @return The maximum value.
   * @throws NoSuchElementException If the array is empty.
   */
  @RequiresApi(18)
  public static long maxValue(SparseLongArray sparseLongArray) {
    if (sparseLongArray.size() == 0) {
      throw new NoSuchElementException();
    }
    long max = Long.MIN_VALUE;
    for (int i = 0; i < sparseLongArray.size(); i++) {
      max = max(max, sparseLongArray.valueAt(i));
    }
    return max;
  }

  /**
   * Converts a time in microseconds to the corresponding time in milliseconds, preserving {@link
   * C#TIME_UNSET} and {@link C#TIME_END_OF_SOURCE} values.
   *
   * @param timeUs The time in microseconds.
   * @return The corresponding time in milliseconds.
   */
  public static long usToMs(long timeUs) {
    return (timeUs == C.TIME_UNSET || timeUs == C.TIME_END_OF_SOURCE) ? timeUs : (timeUs / 1000);
  }

  /**
   * Converts a time in milliseconds to the corresponding time in microseconds, preserving {@link
   * C#TIME_UNSET} values and {@link C#TIME_END_OF_SOURCE} values.
   *
   * @param timeMs The time in milliseconds.
   * @return The corresponding time in microseconds.
   */
  public static long msToUs(long timeMs) {
    return (timeMs == C.TIME_UNSET || timeMs == C.TIME_END_OF_SOURCE) ? timeMs : (timeMs * 1000);
  }

  /**
   * Parses an xs:duration attribute value, returning the parsed duration in milliseconds.
   *
   * @param value The attribute value to decode.
   * @return The parsed duration in milliseconds.
   */
  public static long parseXsDuration(String value) {
    Matcher matcher = XS_DURATION_PATTERN.matcher(value);
    if (matcher.matches()) {
      boolean negated = !TextUtils.isEmpty(matcher.group(1));
      // Durations containing years and months aren't completely defined. We assume there are
      // 30.4368 days in a month, and 365.242 days in a year.
      String years = matcher.group(3);
      double durationSeconds = (years != null) ? Double.parseDouble(years) * 31556908 : 0;
      String months = matcher.group(5);
      durationSeconds += (months != null) ? Double.parseDouble(months) * 2629739 : 0;
      String days = matcher.group(7);
      durationSeconds += (days != null) ? Double.parseDouble(days) * 86400 : 0;
      String hours = matcher.group(10);
      durationSeconds += (hours != null) ? Double.parseDouble(hours) * 3600 : 0;
      String minutes = matcher.group(12);
      durationSeconds += (minutes != null) ? Double.parseDouble(minutes) * 60 : 0;
      String seconds = matcher.group(14);
      durationSeconds += (seconds != null) ? Double.parseDouble(seconds) : 0;
      long durationMillis = (long) (durationSeconds * 1000);
      return negated ? -durationMillis : durationMillis;
    } else {
      return (long) (Double.parseDouble(value) * 3600 * 1000);
    }
  }

  /**
   * Parses an xs:dateTime attribute value, returning the parsed timestamp in milliseconds since the
   * epoch.
   *
   * @param value The attribute value to decode.
   * @return The parsed timestamp in milliseconds since the epoch.
   * @throws ParserException if an error occurs parsing the dateTime attribute value.
   */
  // incompatible types in argument.
  // dereference of possibly-null reference matcher.group(9)
  @SuppressWarnings({"nullness:argument", "nullness:dereference.of.nullable"})
  public static long parseXsDateTime(String value) throws ParserException {
    Matcher matcher = XS_DATE_TIME_PATTERN.matcher(value);
    if (!matcher.matches()) {
      throw ParserException.createForMalformedContainer(
          "Invalid date/time format: " + value, /* cause= */ null);
    }

    int timezoneShift;
    if (matcher.group(9) == null) {
      // No time zone specified.
      timezoneShift = 0;
    } else if (matcher.group(9).equalsIgnoreCase("Z")) {
      timezoneShift = 0;
    } else {
      timezoneShift =
          ((Integer.parseInt(matcher.group(12)) * 60 + Integer.parseInt(matcher.group(13))));
      if ("-".equals(matcher.group(11))) {
        timezoneShift *= -1;
      }
    }

    Calendar dateTime = new GregorianCalendar(TimeZone.getTimeZone("GMT"));

    dateTime.clear();
    // Note: The month value is 0-based, hence the -1 on group(2)
    dateTime.set(
        Integer.parseInt(matcher.group(1)),
        Integer.parseInt(matcher.group(2)) - 1,
        Integer.parseInt(matcher.group(3)),
        Integer.parseInt(matcher.group(4)),
        Integer.parseInt(matcher.group(5)),
        Integer.parseInt(matcher.group(6)));
    if (!TextUtils.isEmpty(matcher.group(8))) {
      final BigDecimal bd = new BigDecimal("0." + matcher.group(8));
      // we care only for milliseconds, so movePointRight(3)
      dateTime.set(Calendar.MILLISECOND, bd.movePointRight(3).intValue());
    }

    long time = dateTime.getTimeInMillis();
    if (timezoneShift != 0) {
      time -= timezoneShift * 60000L;
    }

    return time;
  }

  /**
   * Scales a large timestamp.
   *
   * <p>Logically, scaling consists of a multiplication followed by a division. The actual
   * operations performed are designed to minimize the probability of overflow.
   *
   * @param timestamp The timestamp to scale.
   * @param multiplier The multiplier.
   * @param divisor The divisor.
   * @return The scaled timestamp.
   */
  public static long scaleLargeTimestamp(long timestamp, long multiplier, long divisor) {
    if (divisor >= multiplier && (divisor % multiplier) == 0) {
      long divisionFactor = divisor / multiplier;
      return timestamp / divisionFactor;
    } else if (divisor < multiplier && (multiplier % divisor) == 0) {
      long multiplicationFactor = multiplier / divisor;
      return timestamp * multiplicationFactor;
    } else {
      double multiplicationFactor = (double) multiplier / divisor;
      return (long) (timestamp * multiplicationFactor);
    }
  }

  /**
   * Applies {@link #scaleLargeTimestamp(long, long, long)} to a list of unscaled timestamps.
   *
   * @param timestamps The timestamps to scale.
   * @param multiplier The multiplier.
   * @param divisor The divisor.
   * @return The scaled timestamps.
   */
  public static long[] scaleLargeTimestamps(List<Long> timestamps, long multiplier, long divisor) {
    long[] scaledTimestamps = new long[timestamps.size()];
    if (divisor >= multiplier && (divisor % multiplier) == 0) {
      long divisionFactor = divisor / multiplier;
      for (int i = 0; i < scaledTimestamps.length; i++) {
        scaledTimestamps[i] = timestamps.get(i) / divisionFactor;
      }
    } else if (divisor < multiplier && (multiplier % divisor) == 0) {
      long multiplicationFactor = multiplier / divisor;
      for (int i = 0; i < scaledTimestamps.length; i++) {
        scaledTimestamps[i] = timestamps.get(i) * multiplicationFactor;
      }
    } else {
      double multiplicationFactor = (double) multiplier / divisor;
      for (int i = 0; i < scaledTimestamps.length; i++) {
        scaledTimestamps[i] = (long) (timestamps.get(i) * multiplicationFactor);
      }
    }
    return scaledTimestamps;
  }

  /**
   * Applies {@link #scaleLargeTimestamp(long, long, long)} to an array of unscaled timestamps.
   *
   * @param timestamps The timestamps to scale.
   * @param multiplier The multiplier.
   * @param divisor The divisor.
   */
  public static void scaleLargeTimestampsInPlace(long[] timestamps, long multiplier, long divisor) {
    if (divisor >= multiplier && (divisor % multiplier) == 0) {
      long divisionFactor = divisor / multiplier;
      for (int i = 0; i < timestamps.length; i++) {
        timestamps[i] /= divisionFactor;
      }
    } else if (divisor < multiplier && (multiplier % divisor) == 0) {
      long multiplicationFactor = multiplier / divisor;
      for (int i = 0; i < timestamps.length; i++) {
        timestamps[i] *= multiplicationFactor;
      }
    } else {
      double multiplicationFactor = (double) multiplier / divisor;
      for (int i = 0; i < timestamps.length; i++) {
        timestamps[i] = (long) (timestamps[i] * multiplicationFactor);
      }
    }
  }

  /**
   * Returns the duration of media that will elapse in {@code playoutDuration}.
   *
   * @param playoutDuration The duration to scale.
   * @param speed The factor by which playback is sped up.
   * @return The scaled duration, in the same units as {@code playoutDuration}.
   */
  public static long getMediaDurationForPlayoutDuration(long playoutDuration, float speed) {
    if (speed == 1f) {
      return playoutDuration;
    }
    return Math.round((double) playoutDuration * speed);
  }

  /**
   * Returns the playout duration of {@code mediaDuration} of media.
   *
   * @param mediaDuration The duration to scale.
   * @return The scaled duration, in the same units as {@code mediaDuration}.
   */
  public static long getPlayoutDurationForMediaDuration(long mediaDuration, float speed) {
    if (speed == 1f) {
      return mediaDuration;
    }
    return Math.round((double) mediaDuration / speed);
  }

  /**
   * Returns the integer equal to the big-endian concatenation of the characters in {@code string}
   * as bytes. The string must be no more than four characters long.
   *
   * @param string A string no more than four characters long.
   */
  public static int getIntegerCodeForString(String string) {
    int length = string.length();
    Assertions.checkArgument(length <= 4);
    int result = 0;
    for (int i = 0; i < length; i++) {
      result <<= 8;
      result |= string.charAt(i);
    }
    return result;
  }

  /**
   * Converts an integer to a long by unsigned conversion.
   *
   * <p>This method is equivalent to {@link Integer#toUnsignedLong(int)} for API 26+.
   */
  public static long toUnsignedLong(int x) {
    // x is implicitly casted to a long before the bit operation is executed but this does not
    // impact the method correctness.
    return x & 0xFFFFFFFFL;
  }

  /**
   * Returns the long that is composed of the bits of the 2 specified integers.
   *
   * @param mostSignificantBits The 32 most significant bits of the long to return.
   * @param leastSignificantBits The 32 least significant bits of the long to return.
   * @return a long where its 32 most significant bits are {@code mostSignificantBits} bits and its
   *     32 least significant bits are {@code leastSignificantBits}.
   */
  public static long toLong(int mostSignificantBits, int leastSignificantBits) {
    return (toUnsignedLong(mostSignificantBits) << 32) | toUnsignedLong(leastSignificantBits);
  }

  /**
   * Truncates a sequence of ASCII characters to a maximum length.
   *
   * <p>This preserves span styling in the {@link CharSequence}. If that's not important, use {@link
   * Ascii#truncate(CharSequence, int, String)}.
   *
   * <p><b>Note:</b> This is not safe to use in general on Unicode text because it may separate
   * characters from combining characters or split up surrogate pairs.
   *
   * @param sequence The character sequence to truncate.
   * @param maxLength The max length to truncate to.
   * @return {@code sequence} directly if {@code sequence.length() <= maxLength}, otherwise {@code
   *     sequence.subsequence(0, maxLength}.
   */
  public static CharSequence truncateAscii(CharSequence sequence, int maxLength) {
    return sequence.length() <= maxLength ? sequence : sequence.subSequence(0, maxLength);
  }

  /**
   * Returns a byte array containing values parsed from the hex string provided.
   *
   * @param hexString The hex string to convert to bytes.
   * @return A byte array containing values parsed from the hex string provided.
   */
  public static byte[] getBytesFromHexString(String hexString) {
    byte[] data = new byte[hexString.length() / 2];
    for (int i = 0; i < data.length; i++) {
      int stringOffset = i * 2;
      data[i] =
          (byte)
              ((Character.digit(hexString.charAt(stringOffset), 16) << 4)
                  + Character.digit(hexString.charAt(stringOffset + 1), 16));
    }
    return data;
  }

  /**
   * Returns a string containing a lower-case hex representation of the bytes provided.
   *
   * @param bytes The byte data to convert to hex.
   * @return A String containing the hex representation of {@code bytes}.
   */
  public static String toHexString(byte[] bytes) {
    StringBuilder result = new StringBuilder(bytes.length * 2);
    for (int i = 0; i < bytes.length; i++) {
      result
          .append(Character.forDigit((bytes[i] >> 4) & 0xF, 16))
          .append(Character.forDigit(bytes[i] & 0xF, 16));
    }
    return result.toString();
  }

  /**
   * Returns a string with comma delimited simple names of each object's class.
   *
   * @param objects The objects whose simple class names should be comma delimited and returned.
   * @return A string with comma delimited simple names of each object's class.
   */
  public static String getCommaDelimitedSimpleClassNames(Object[] objects) {
    StringBuilder stringBuilder = new StringBuilder();
    for (int i = 0; i < objects.length; i++) {
      stringBuilder.append(objects[i].getClass().getSimpleName());
      if (i < objects.length - 1) {
        stringBuilder.append(", ");
      }
    }
    return stringBuilder.toString();
  }

  /**
   * Returns a user agent string based on the given application name and the library version.
   *
   * @param context A valid context of the calling application.
   * @param applicationName String that will be prefix'ed to the generated user agent.
   * @return A user agent string generated using the applicationName and the library version.
   */
  public static String getUserAgent(Context context, String applicationName) {
    String versionName;
    try {
      String packageName = context.getPackageName();
      PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
      versionName = info.versionName;
    } catch (NameNotFoundException e) {
      versionName = "?";
    }
    return applicationName
        + "/"
        + versionName
        + " (Linux;Android "
        + Build.VERSION.RELEASE
        + ") "
        + ExoPlayerLibraryInfo.VERSION_SLASHY;
  }

  /** Returns the number of codec strings in {@code codecs} whose type matches {@code trackType}. */
  public static int getCodecCountOfType(@Nullable String codecs, @C.TrackType int trackType) {
    String[] codecArray = splitCodecs(codecs);
    int count = 0;
    for (String codec : codecArray) {
      if (trackType == MimeTypes.getTrackTypeOfCodec(codec)) {
        count++;
      }
    }
    return count;
  }

  /**
   * Returns a copy of {@code codecs} without the codecs whose track type doesn't match {@code
   * trackType}.
   *
   * @param codecs A codec sequence string, as defined in RFC 6381.
   * @param trackType The {@link C.TrackType track type}.
   * @return A copy of {@code codecs} without the codecs whose track type doesn't match {@code
   *     trackType}. If this ends up empty, or {@code codecs} is null, returns null.
   */
  @Nullable
  public static String getCodecsOfType(@Nullable String codecs, @C.TrackType int trackType) {
    String[] codecArray = splitCodecs(codecs);
    if (codecArray.length == 0) {
      return null;
    }
    StringBuilder builder = new StringBuilder();
    for (String codec : codecArray) {
      if (trackType == MimeTypes.getTrackTypeOfCodec(codec)) {
        if (builder.length() > 0) {
          builder.append(",");
        }
        builder.append(codec);
      }
    }
    return builder.length() > 0 ? builder.toString() : null;
  }

  /**
   * Splits a codecs sequence string, as defined in RFC 6381, into individual codec strings.
   *
   * @param codecs A codec sequence string, as defined in RFC 6381.
   * @return The split codecs, or an array of length zero if the input was empty or null.
   */
  public static String[] splitCodecs(@Nullable String codecs) {
    if (TextUtils.isEmpty(codecs)) {
      return new String[0];
    }
    return split(codecs.trim(), "(\\s*,\\s*)");
  }

  /**
   * Gets a PCM {@link Format} with the specified parameters.
   *
   * @param pcmEncoding The {@link C.PcmEncoding}.
   * @param channels The number of channels, or {@link Format#NO_VALUE} if unknown.
   * @param sampleRate The sample rate in Hz, or {@link Format#NO_VALUE} if unknown.
   * @return The PCM format.
   */
  public static Format getPcmFormat(@C.PcmEncoding int pcmEncoding, int channels, int sampleRate) {
    return new Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_RAW)
        .setChannelCount(channels)
        .setSampleRate(sampleRate)
        .setPcmEncoding(pcmEncoding)
        .build();
  }

  /**
   * Converts a sample bit depth to a corresponding PCM encoding constant.
   *
   * @param bitDepth The bit depth. Supported values are 8, 16, 24 and 32.
   * @return The corresponding encoding. One of {@link C#ENCODING_PCM_8BIT}, {@link
   *     C#ENCODING_PCM_16BIT}, {@link C#ENCODING_PCM_24BIT} and {@link C#ENCODING_PCM_32BIT}. If
   *     the bit depth is unsupported then {@link C#ENCODING_INVALID} is returned.
   */
  public static @C.PcmEncoding int getPcmEncoding(int bitDepth) {
    switch (bitDepth) {
      case 8:
        return C.ENCODING_PCM_8BIT;
      case 16:
        return C.ENCODING_PCM_16BIT;
      case 24:
        return C.ENCODING_PCM_24BIT;
      case 32:
        return C.ENCODING_PCM_32BIT;
      default:
        return C.ENCODING_INVALID;
    }
  }

  /**
   * Returns whether {@code encoding} is one of the linear PCM encodings.
   *
   * @param encoding The encoding of the audio data.
   * @return Whether the encoding is one of the PCM encodings.
   */
  public static boolean isEncodingLinearPcm(@C.Encoding int encoding) {
    return encoding == C.ENCODING_PCM_8BIT
        || encoding == C.ENCODING_PCM_16BIT
        || encoding == C.ENCODING_PCM_16BIT_BIG_ENDIAN
        || encoding == C.ENCODING_PCM_24BIT
        || encoding == C.ENCODING_PCM_32BIT
        || encoding == C.ENCODING_PCM_FLOAT;
  }

  /**
   * Returns whether {@code encoding} is high resolution (&gt; 16-bit) PCM.
   *
   * @param encoding The encoding of the audio data.
   * @return Whether the encoding is high resolution PCM.
   */
  public static boolean isEncodingHighResolutionPcm(@C.PcmEncoding int encoding) {
    return encoding == C.ENCODING_PCM_24BIT
        || encoding == C.ENCODING_PCM_32BIT
        || encoding == C.ENCODING_PCM_FLOAT;
  }

  /**
   * Returns the audio track channel configuration for the given channel count, or {@link
   * AudioFormat#CHANNEL_INVALID} if output is not possible.
   *
   * @param channelCount The number of channels in the input audio.
   * @return The channel configuration or {@link AudioFormat#CHANNEL_INVALID} if output is not
   *     possible.
   */
  @SuppressLint("InlinedApi") // Inlined AudioFormat constants.
  public static int getAudioTrackChannelConfig(int channelCount) {
    switch (channelCount) {
      case 1:
        return AudioFormat.CHANNEL_OUT_MONO;
      case 2:
        return AudioFormat.CHANNEL_OUT_STEREO;
      case 3:
        return AudioFormat.CHANNEL_OUT_STEREO | AudioFormat.CHANNEL_OUT_FRONT_CENTER;
      case 4:
        return AudioFormat.CHANNEL_OUT_QUAD;
      case 5:
        return AudioFormat.CHANNEL_OUT_QUAD | AudioFormat.CHANNEL_OUT_FRONT_CENTER;
      case 6:
        return AudioFormat.CHANNEL_OUT_5POINT1;
      case 7:
        return AudioFormat.CHANNEL_OUT_5POINT1 | AudioFormat.CHANNEL_OUT_BACK_CENTER;
      case 8:
        return AudioFormat.CHANNEL_OUT_7POINT1_SURROUND;
      case 12:
        return AudioFormat.CHANNEL_OUT_7POINT1POINT4;
      default:
        return AudioFormat.CHANNEL_INVALID;
    }
  }

  /**
   * Returns the frame size for audio with {@code channelCount} channels in the specified encoding.
   *
   * @param pcmEncoding The encoding of the audio data.
   * @param channelCount The channel count.
   * @return The size of one audio frame in bytes.
   */
  public static int getPcmFrameSize(@C.PcmEncoding int pcmEncoding, int channelCount) {
    switch (pcmEncoding) {
      case C.ENCODING_PCM_8BIT:
        return channelCount;
      case C.ENCODING_PCM_16BIT:
      case C.ENCODING_PCM_16BIT_BIG_ENDIAN:
        return channelCount * 2;
      case C.ENCODING_PCM_24BIT:
        return channelCount * 3;
      case C.ENCODING_PCM_32BIT:
      case C.ENCODING_PCM_FLOAT:
        return channelCount * 4;
      case C.ENCODING_INVALID:
      case Format.NO_VALUE:
      default:
        throw new IllegalArgumentException();
    }
  }

  /** Returns the {@link C.AudioUsage} corresponding to the specified {@link C.StreamType}. */
  public static @C.AudioUsage int getAudioUsageForStreamType(@C.StreamType int streamType) {
    switch (streamType) {
      case C.STREAM_TYPE_ALARM:
        return C.USAGE_ALARM;
      case C.STREAM_TYPE_DTMF:
        return C.USAGE_VOICE_COMMUNICATION_SIGNALLING;
      case C.STREAM_TYPE_NOTIFICATION:
        return C.USAGE_NOTIFICATION;
      case C.STREAM_TYPE_RING:
        return C.USAGE_NOTIFICATION_RINGTONE;
      case C.STREAM_TYPE_SYSTEM:
        return C.USAGE_ASSISTANCE_SONIFICATION;
      case C.STREAM_TYPE_VOICE_CALL:
        return C.USAGE_VOICE_COMMUNICATION;
      case C.STREAM_TYPE_MUSIC:
      default:
        return C.USAGE_MEDIA;
    }
  }

  /** Returns the {@link C.AudioContentType} corresponding to the specified {@link C.StreamType}. */
  public static @C.AudioContentType int getAudioContentTypeForStreamType(
      @C.StreamType int streamType) {
    switch (streamType) {
      case C.STREAM_TYPE_ALARM:
      case C.STREAM_TYPE_DTMF:
      case C.STREAM_TYPE_NOTIFICATION:
      case C.STREAM_TYPE_RING:
      case C.STREAM_TYPE_SYSTEM:
        return C.AUDIO_CONTENT_TYPE_SONIFICATION;
      case C.STREAM_TYPE_VOICE_CALL:
        return C.AUDIO_CONTENT_TYPE_SPEECH;
      case C.STREAM_TYPE_MUSIC:
      default:
        return C.AUDIO_CONTENT_TYPE_MUSIC;
    }
  }

  /** Returns the {@link C.StreamType} corresponding to the specified {@link C.AudioUsage}. */
  public static @C.StreamType int getStreamTypeForAudioUsage(@C.AudioUsage int usage) {
    switch (usage) {
      case C.USAGE_MEDIA:
      case C.USAGE_GAME:
      case C.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE:
        return C.STREAM_TYPE_MUSIC;
      case C.USAGE_ASSISTANCE_SONIFICATION:
        return C.STREAM_TYPE_SYSTEM;
      case C.USAGE_VOICE_COMMUNICATION:
        return C.STREAM_TYPE_VOICE_CALL;
      case C.USAGE_VOICE_COMMUNICATION_SIGNALLING:
        return C.STREAM_TYPE_DTMF;
      case C.USAGE_ALARM:
        return C.STREAM_TYPE_ALARM;
      case C.USAGE_NOTIFICATION_RINGTONE:
        return C.STREAM_TYPE_RING;
      case C.USAGE_NOTIFICATION:
      case C.USAGE_NOTIFICATION_COMMUNICATION_REQUEST:
      case C.USAGE_NOTIFICATION_COMMUNICATION_INSTANT:
      case C.USAGE_NOTIFICATION_COMMUNICATION_DELAYED:
      case C.USAGE_NOTIFICATION_EVENT:
        return C.STREAM_TYPE_NOTIFICATION;
      case C.USAGE_ASSISTANCE_ACCESSIBILITY:
      case C.USAGE_ASSISTANT:
      case C.USAGE_UNKNOWN:
      default:
        return C.STREAM_TYPE_DEFAULT;
    }
  }

  /**
   * Returns a newly generated audio session identifier, or {@link AudioManager#ERROR} if an error
   * occurred in which case audio playback may fail.
   *
   * @see AudioManager#generateAudioSessionId()
   */
  @RequiresApi(21)
  public static int generateAudioSessionIdV21(Context context) {
    @Nullable
    AudioManager audioManager = ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE));
    return audioManager == null ? AudioManager.ERROR : audioManager.generateAudioSessionId();
  }

  /**
   * Derives a DRM {@link UUID} from {@code drmScheme}.
   *
   * @param drmScheme A UUID string, or {@code "widevine"}, {@code "playready"} or {@code
   *     "clearkey"}.
   * @return The derived {@link UUID}, or {@code null} if one could not be derived.
   */
  @Nullable
  public static UUID getDrmUuid(String drmScheme) {
    switch (Ascii.toLowerCase(drmScheme)) {
      case "widevine":
        return C.WIDEVINE_UUID;
      case "playready":
        return C.PLAYREADY_UUID;
      case "clearkey":
        return C.CLEARKEY_UUID;
      default:
        try {
          return UUID.fromString(drmScheme);
        } catch (Exception e) {
          return null;
        }
    }
  }

  /**
   * Returns a {@link PlaybackException.ErrorCode} value that corresponds to the provided {@link
   * MediaDrm.ErrorCodes} value. Returns {@link PlaybackException#ERROR_CODE_DRM_SYSTEM_ERROR} if
   * the provided error code isn't recognised.
   */
  public static @PlaybackException.ErrorCode int getErrorCodeForMediaDrmErrorCode(
      int mediaDrmErrorCode) {
    switch (mediaDrmErrorCode) {
      case MediaDrm.ErrorCodes.ERROR_PROVISIONING_CONFIG:
      case MediaDrm.ErrorCodes.ERROR_PROVISIONING_PARSE:
      case MediaDrm.ErrorCodes.ERROR_PROVISIONING_REQUEST_REJECTED:
      case MediaDrm.ErrorCodes.ERROR_PROVISIONING_CERTIFICATE:
      case MediaDrm.ErrorCodes.ERROR_PROVISIONING_RETRY:
        return PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED;
      case MediaDrm.ErrorCodes.ERROR_LICENSE_PARSE:
      case MediaDrm.ErrorCodes.ERROR_LICENSE_RELEASE:
      case MediaDrm.ErrorCodes.ERROR_LICENSE_REQUEST_REJECTED:
      case MediaDrm.ErrorCodes.ERROR_LICENSE_RESTORE:
      case MediaDrm.ErrorCodes.ERROR_LICENSE_STATE:
      case MediaDrm.ErrorCodes.ERROR_CERTIFICATE_MALFORMED:
        return PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED;
      case MediaDrm.ErrorCodes.ERROR_LICENSE_POLICY:
      case MediaDrm.ErrorCodes.ERROR_INSUFFICIENT_OUTPUT_PROTECTION:
      case MediaDrm.ErrorCodes.ERROR_INSUFFICIENT_SECURITY:
      case MediaDrm.ErrorCodes.ERROR_KEY_EXPIRED:
      case MediaDrm.ErrorCodes.ERROR_KEY_NOT_LOADED:
        return PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION;
      case MediaDrm.ErrorCodes.ERROR_INIT_DATA:
      case MediaDrm.ErrorCodes.ERROR_FRAME_TOO_LARGE:
        return PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR;
      default:
        return PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR;
    }
  }

  /**
   * @deprecated Use {@link #inferContentTypeForExtension(String)} when {@code overrideExtension} is
   *     non-empty, and {@link #inferContentType(Uri)} otherwise.
   */
  @Deprecated
  public static @ContentType int inferContentType(Uri uri, @Nullable String overrideExtension) {
    return TextUtils.isEmpty(overrideExtension)
        ? inferContentType(uri)
        : inferContentTypeForExtension(overrideExtension);
  }

  /**
   * Makes a best guess to infer the {@link ContentType} from a {@link Uri}.
   *
   * @param uri The {@link Uri}.
   * @return The content type.
   */
  public static @ContentType int inferContentType(Uri uri) {
    @Nullable String scheme = uri.getScheme();
    if (scheme != null && Ascii.equalsIgnoreCase("rtsp", scheme)) {
      return C.CONTENT_TYPE_RTSP;
    }

    @Nullable String lastPathSegment = uri.getLastPathSegment();
    if (lastPathSegment == null) {
      return C.CONTENT_TYPE_OTHER;
    }
    int lastDotIndex = lastPathSegment.lastIndexOf('.');
    if (lastDotIndex >= 0) {
      @C.ContentType
      int contentType = inferContentTypeForExtension(lastPathSegment.substring(lastDotIndex + 1));
      if (contentType != C.CONTENT_TYPE_OTHER) {
        // If contentType is TYPE_SS that indicates the extension is .ism or .isml and shows the ISM
        // URI is missing the "/manifest" suffix, which contains the information used to
        // disambiguate between Smooth Streaming, HLS and DASH below - so we can just return TYPE_SS
        // here without further checks.
        return contentType;
      }
    }

    Matcher ismMatcher = ISM_PATH_PATTERN.matcher(checkNotNull(uri.getPath()));
    if (ismMatcher.matches()) {
      @Nullable String extensions = ismMatcher.group(2);
      if (extensions != null) {
        if (extensions.contains(ISM_DASH_FORMAT_EXTENSION)) {
          return C.CONTENT_TYPE_DASH;
        } else if (extensions.contains(ISM_HLS_FORMAT_EXTENSION)) {
          return C.CONTENT_TYPE_HLS;
        }
      }
      return C.CONTENT_TYPE_SS;
    }

    return C.CONTENT_TYPE_OTHER;
  }

  /**
   * @deprecated Use {@link Uri#parse(String)} and {@link #inferContentType(Uri)} for full file
   *     paths or {@link #inferContentTypeForExtension(String)} for extensions.
   */
  @Deprecated
  public static @ContentType int inferContentType(String fileName) {
    return inferContentType(Uri.parse("file:///" + fileName));
  }

  /**
   * Makes a best guess to infer the {@link ContentType} from a file extension.
   *
   * @param fileExtension The extension of the file (excluding the '.').
   * @return The content type.
   */
  public static @ContentType int inferContentTypeForExtension(String fileExtension) {
    fileExtension = Ascii.toLowerCase(fileExtension);
    switch (fileExtension) {
      case "mpd":
        return C.CONTENT_TYPE_DASH;
      case "m3u8":
        return C.CONTENT_TYPE_HLS;
      case "ism":
      case "isml":
        return C.TYPE_SS;
      default:
        return C.CONTENT_TYPE_OTHER;
    }
  }

  /**
   * Makes a best guess to infer the {@link ContentType} from a {@link Uri} and optional MIME type.
   *
   * @param uri The {@link Uri}.
   * @param mimeType If MIME type, or {@code null}.
   * @return The content type.
   */
  public static @ContentType int inferContentTypeForUriAndMimeType(
      Uri uri, @Nullable String mimeType) {
    if (mimeType == null) {
      return inferContentType(uri);
    }
    switch (mimeType) {
      case MimeTypes.APPLICATION_MPD:
        return C.CONTENT_TYPE_DASH;
      case MimeTypes.APPLICATION_M3U8:
        return C.CONTENT_TYPE_HLS;
      case MimeTypes.APPLICATION_SS:
        return C.CONTENT_TYPE_SS;
      case MimeTypes.APPLICATION_RTSP:
        return C.CONTENT_TYPE_RTSP;
      default:
        return C.CONTENT_TYPE_OTHER;
    }
  }

  /**
   * Returns the MIME type corresponding to the given adaptive {@link ContentType}, or {@code null}
   * if the content type is not adaptive.
   */
  @Nullable
  public static String getAdaptiveMimeTypeForContentType(@ContentType int contentType) {
    switch (contentType) {
      case C.CONTENT_TYPE_DASH:
        return MimeTypes.APPLICATION_MPD;
      case C.CONTENT_TYPE_HLS:
        return MimeTypes.APPLICATION_M3U8;
      case C.CONTENT_TYPE_SS:
        return MimeTypes.APPLICATION_SS;
      case C.CONTENT_TYPE_RTSP:
      case C.CONTENT_TYPE_OTHER:
      default:
        return null;
    }
  }

  /**
   * If the provided URI is an ISM Presentation URI, returns the URI with "Manifest" appended to its
   * path (i.e., the corresponding default manifest URI). Else returns the provided URI without
   * modification. See [MS-SSTR] v20180912, section 2.2.1.
   *
   * @param uri The original URI.
   * @return The fixed URI.
   */
  public static Uri fixSmoothStreamingIsmManifestUri(Uri uri) {
    @Nullable String path = uri.getPath();
    if (path == null) {
      return uri;
    }
    Matcher ismMatcher = ISM_PATH_PATTERN.matcher(path);
    if (ismMatcher.matches() && ismMatcher.group(1) == null) {
      // Add missing "Manifest" suffix.
      return Uri.withAppendedPath(uri, "Manifest");
    }
    return uri;
  }

  /**
   * Returns the specified millisecond time formatted as a string.
   *
   * @param builder The builder that {@code formatter} will write to.
   * @param formatter The formatter.
   * @param timeMs The time to format as a string, in milliseconds.
   * @return The time formatted as a string.
   */
  public static String getStringForTime(StringBuilder builder, Formatter formatter, long timeMs) {
    if (timeMs == C.TIME_UNSET) {
      timeMs = 0;
    }
    String prefix = timeMs < 0 ? "-" : "";
    timeMs = abs(timeMs);
    long totalSeconds = (timeMs + 500) / 1000;
    long seconds = totalSeconds % 60;
    long minutes = (totalSeconds / 60) % 60;
    long hours = totalSeconds / 3600;
    builder.setLength(0);
    return hours > 0
        ? formatter.format("%s%d:%02d:%02d", prefix, hours, minutes, seconds).toString()
        : formatter.format("%s%02d:%02d", prefix, minutes, seconds).toString();
  }

  /**
   * Escapes a string so that it's safe for use as a file or directory name on at least FAT32
   * filesystems. FAT32 is the most restrictive of all filesystems still commonly used today.
   *
   * <p>For simplicity, this only handles common characters known to be illegal on FAT32: &lt;,
   * &gt;, :, ", /, \, |, ?, and *. % is also escaped since it is used as the escape character.
   * Escaping is performed in a consistent way so that no collisions occur and {@link
   * #unescapeFileName(String)} can be used to retrieve the original file name.
   *
   * @param fileName File name to be escaped.
   * @return An escaped file name which will be safe for use on at least FAT32 filesystems.
   */
  public static String escapeFileName(String fileName) {
    int length = fileName.length();
    int charactersToEscapeCount = 0;
    for (int i = 0; i < length; i++) {
      if (shouldEscapeCharacter(fileName.charAt(i))) {
        charactersToEscapeCount++;
      }
    }
    if (charactersToEscapeCount == 0) {
      return fileName;
    }

    int i = 0;
    StringBuilder builder = new StringBuilder(length + charactersToEscapeCount * 2);
    while (charactersToEscapeCount > 0) {
      char c = fileName.charAt(i++);
      if (shouldEscapeCharacter(c)) {
        builder.append('%').append(Integer.toHexString(c));
        charactersToEscapeCount--;
      } else {
        builder.append(c);
      }
    }
    if (i < length) {
      builder.append(fileName, i, length);
    }
    return builder.toString();
  }

  private static boolean shouldEscapeCharacter(char c) {
    switch (c) {
      case '<':
      case '>':
      case ':':
      case '"':
      case '/':
      case '\\':
      case '|':
      case '?':
      case '*':
      case '%':
        return true;
      default:
        return false;
    }
  }

  /**
   * Unescapes an escaped file or directory name back to its original value.
   *
   * <p>See {@link #escapeFileName(String)} for more information.
   *
   * @param fileName File name to be unescaped.
   * @return The original value of the file name before it was escaped, or null if the escaped
   *     fileName seems invalid.
   */
  @Nullable
  public static String unescapeFileName(String fileName) {
    int length = fileName.length();
    int percentCharacterCount = 0;
    for (int i = 0; i < length; i++) {
      if (fileName.charAt(i) == '%') {
        percentCharacterCount++;
      }
    }
    if (percentCharacterCount == 0) {
      return fileName;
    }

    int expectedLength = length - percentCharacterCount * 2;
    StringBuilder builder = new StringBuilder(expectedLength);
    Matcher matcher = ESCAPED_CHARACTER_PATTERN.matcher(fileName);
    int startOfNotEscaped = 0;
    while (percentCharacterCount > 0 && matcher.find()) {
      char unescapedCharacter = (char) Integer.parseInt(checkNotNull(matcher.group(1)), 16);
      builder.append(fileName, startOfNotEscaped, matcher.start()).append(unescapedCharacter);
      startOfNotEscaped = matcher.end();
      percentCharacterCount--;
    }
    if (startOfNotEscaped < length) {
      builder.append(fileName, startOfNotEscaped, length);
    }
    if (builder.length() != expectedLength) {
      return null;
    }
    return builder.toString();
  }

  /** Returns a data URI with the specified MIME type and data. */
  public static Uri getDataUriForString(String mimeType, String data) {
    return Uri.parse(
        "data:" + mimeType + ";base64," + Base64.encodeToString(data.getBytes(), Base64.NO_WRAP));
  }

  /**
   * A hacky method that always throws {@code t} even if {@code t} is a checked exception, and is
   * not declared to be thrown.
   */
  public static void sneakyThrow(Throwable t) {
    sneakyThrowInternal(t);
  }

  @SuppressWarnings("unchecked")
  private static <T extends Throwable> void sneakyThrowInternal(Throwable t) throws T {
    throw (T) t;
  }

  /** Recursively deletes a directory and its content. */
  public static void recursiveDelete(File fileOrDirectory) {
    File[] directoryFiles = fileOrDirectory.listFiles();
    if (directoryFiles != null) {
      for (File child : directoryFiles) {
        recursiveDelete(child);
      }
    }
    fileOrDirectory.delete();
  }

  /** Creates an empty directory in the directory returned by {@link Context#getCacheDir()}. */
  public static File createTempDirectory(Context context, String prefix) throws IOException {
    File tempFile = createTempFile(context, prefix);
    tempFile.delete(); // Delete the temp file.
    tempFile.mkdir(); // Create a directory with the same name.
    return tempFile;
  }

  /** Creates a new empty file in the directory returned by {@link Context#getCacheDir()}. */
  public static File createTempFile(Context context, String prefix) throws IOException {
    return File.createTempFile(prefix, null, checkNotNull(context.getCacheDir()));
  }

  /**
   * Returns the result of updating a CRC-32 with the specified bytes in a "most significant bit
   * first" order.
   *
   * @param bytes Array containing the bytes to update the crc value with.
   * @param start The index to the first byte in the byte range to update the crc with.
   * @param end The index after the last byte in the byte range to update the crc with.
   * @param initialValue The initial value for the crc calculation.
   * @return The result of updating the initial value with the specified bytes.
   */
  public static int crc32(byte[] bytes, int start, int end, int initialValue) {
    for (int i = start; i < end; i++) {
      initialValue =
          (initialValue << 8)
              ^ CRC32_BYTES_MSBF[((initialValue >>> 24) ^ (bytes[i] & 0xFF)) & 0xFF];
    }
    return initialValue;
  }

  /**
   * Returns the result of updating a CRC-8 with the specified bytes in a "most significant bit
   * first" order.
   *
   * @param bytes Array containing the bytes to update the crc value with.
   * @param start The index to the first byte in the byte range to update the crc with.
   * @param end The index after the last byte in the byte range to update the crc with.
   * @param initialValue The initial value for the crc calculation.
   * @return The result of updating the initial value with the specified bytes.
   */
  public static int crc8(byte[] bytes, int start, int end, int initialValue) {
    for (int i = start; i < end; i++) {
      initialValue = CRC8_BYTES_MSBF[initialValue ^ (bytes[i] & 0xFF)];
    }
    return initialValue;
  }

  /** Compresses {@code input} using gzip and returns the result in a newly allocated byte array. */
  public static byte[] gzip(byte[] input) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (GZIPOutputStream os = new GZIPOutputStream(output)) {
      os.write(input);
    } catch (IOException e) {
      // A ByteArrayOutputStream wrapped in a GZipOutputStream should never throw IOException since
      // no I/O is happening.
      throw new IllegalStateException(e);
    }
    return output.toByteArray();
  }

  /**
   * Absolute <i>get</i> method for reading an int value in {@link ByteOrder#BIG_ENDIAN} in a {@link
   * ByteBuffer}. Same as {@link ByteBuffer#getInt(int)} except the buffer's order as returned by
   * {@link ByteBuffer#order()} is ignored and {@link ByteOrder#BIG_ENDIAN} is used instead.
   *
   * @param buffer The buffer from which to read an int in big endian.
   * @param index The index from which the bytes will be read.
   * @return The int value at the given index with the buffer bytes ordered most significant to
   *     least significant.
   */
  public static int getBigEndianInt(ByteBuffer buffer, int index) {
    int value = buffer.getInt(index);
    return buffer.order() == ByteOrder.BIG_ENDIAN ? value : Integer.reverseBytes(value);
  }

  /**
   * Returns the upper-case ISO 3166-1 alpha-2 country code of the current registered operator's MCC
   * (Mobile Country Code), or the country code of the default Locale if not available.
   *
   * @param context A context to access the telephony service. If null, only the Locale can be used.
   * @return The upper-case ISO 3166-1 alpha-2 country code, or an empty String if unavailable.
   */
  public static String getCountryCode(@Nullable Context context) {
    if (context != null) {
      @Nullable
      TelephonyManager telephonyManager =
          (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
      if (telephonyManager != null) {
        String countryCode = telephonyManager.getNetworkCountryIso();
        if (!TextUtils.isEmpty(countryCode)) {
          return Ascii.toUpperCase(countryCode);
        }
      }
    }
    return Ascii.toUpperCase(Locale.getDefault().getCountry());
  }

  /**
   * Returns a non-empty array of normalized IETF BCP 47 language tags for the system languages
   * ordered by preference.
   */
  public static String[] getSystemLanguageCodes() {
    String[] systemLocales = getSystemLocales();
    for (int i = 0; i < systemLocales.length; i++) {
      systemLocales[i] = normalizeLanguageCode(systemLocales[i]);
    }
    return systemLocales;
  }

  /** Returns the default {@link Locale.Category#DISPLAY DISPLAY} {@link Locale}. */
  public static Locale getDefaultDisplayLocale() {
    return SDK_INT >= 24 ? Locale.getDefault(Locale.Category.DISPLAY) : Locale.getDefault();
  }

  /**
   * Uncompresses the data in {@code input}.
   *
   * @param input Wraps the compressed input data.
   * @param output Wraps an output buffer to be used to store the uncompressed data. If {@code
   *     output.data} isn't big enough to hold the uncompressed data, a new array is created. If
   *     {@code true} is returned then the output's position will be set to 0 and its limit will be
   *     set to the length of the uncompressed data.
   * @param inflater If not null, used to uncompressed the input. Otherwise a new {@link Inflater}
   *     is created.
   * @return Whether the input is uncompressed successfully.
   */
  public static boolean inflate(
      ParsableByteArray input, ParsableByteArray output, @Nullable Inflater inflater) {
    if (input.bytesLeft() <= 0) {
      return false;
    }
    if (output.capacity() < input.bytesLeft()) {
      output.ensureCapacity(2 * input.bytesLeft());
    }
    if (inflater == null) {
      inflater = new Inflater();
    }
    inflater.setInput(input.getData(), input.getPosition(), input.bytesLeft());
    try {
      int outputSize = 0;
      while (true) {
        outputSize +=
            inflater.inflate(output.getData(), outputSize, output.capacity() - outputSize);
        if (inflater.finished()) {
          output.setLimit(outputSize);
          return true;
        }
        if (inflater.needsDictionary() || inflater.needsInput()) {
          return false;
        }
        if (outputSize == output.capacity()) {
          output.ensureCapacity(output.capacity() * 2);
        }
      }
    } catch (DataFormatException e) {
      return false;
    } finally {
      inflater.reset();
    }
  }

  /**
   * Returns whether the app is running on a TV device.
   *
   * @param context Any context.
   * @return Whether the app is running on a TV device.
   */
  public static boolean isTv(Context context) {
    // See https://developer.android.com/training/tv/start/hardware.html#runtime-check.
    @Nullable
    UiModeManager uiModeManager =
        (UiModeManager) context.getApplicationContext().getSystemService(UI_MODE_SERVICE);
    return uiModeManager != null
        && uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
  }

  /**
   * Returns whether the app is running on an automotive device.
   *
   * @param context Any context.
   * @return Whether the app is running on an automotive device.
   */
  public static boolean isAutomotive(Context context) {
    return SDK_INT >= 23
        && context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
  }

  /**
   * Gets the size of the current mode of the default display, in pixels.
   *
   * <p>Note that due to application UI scaling, the number of pixels made available to applications
   * (as reported by {@link Display#getSize(Point)} may differ from the mode's actual resolution (as
   * reported by this function). For example, applications running on a display configured with a 4K
   * mode may have their UI laid out and rendered in 1080p and then scaled up. Applications can take
   * advantage of the full mode resolution through a {@link SurfaceView} using full size buffers.
   *
   * @param context Any context.
   * @return The size of the current mode, in pixels.
   */
  public static Point getCurrentDisplayModeSize(Context context) {
    @Nullable Display defaultDisplay = null;
    if (SDK_INT >= 17) {
      @Nullable
      DisplayManager displayManager =
          (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
      // We don't expect displayManager to ever be null, so this check is just precautionary.
      // Consider removing it when the library minSdkVersion is increased to 17 or higher.
      if (displayManager != null) {
        defaultDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
      }
    }
    if (defaultDisplay == null) {
      WindowManager windowManager =
          checkNotNull((WindowManager) context.getSystemService(Context.WINDOW_SERVICE));
      defaultDisplay = windowManager.getDefaultDisplay();
    }
    return getCurrentDisplayModeSize(context, defaultDisplay);
  }

  /**
   * Gets the size of the current mode of the specified display, in pixels.
   *
   * <p>Note that due to application UI scaling, the number of pixels made available to applications
   * (as reported by {@link Display#getSize(Point)} may differ from the mode's actual resolution (as
   * reported by this function). For example, applications running on a display configured with a 4K
   * mode may have their UI laid out and rendered in 1080p and then scaled up. Applications can take
   * advantage of the full mode resolution through a {@link SurfaceView} using full size buffers.
   *
   * @param context Any context.
   * @param display The display whose size is to be returned.
   * @return The size of the current mode, in pixels.
   */
  public static Point getCurrentDisplayModeSize(Context context, Display display) {
    if (display.getDisplayId() == Display.DEFAULT_DISPLAY && isTv(context)) {
      // On Android TVs it's common for the UI to be driven at a lower resolution than the physical
      // resolution of the display (e.g., driving the UI at 1080p when the display is 4K).
      // SurfaceView outputs are still able to use the full physical resolution on such devices.
      //
      // Prior to API level 26, the Display object did not provide a way to obtain the true physical
      // resolution of the display. From API level 26, Display.getMode().getPhysical[Width|Height]
      // is expected to return the display's true physical resolution, but we still see devices
      // setting their hardware compositor output size incorrectly, which makes this unreliable.
      // Hence for TV devices, we try and read the display's true physical resolution from system
      // properties.
      //
      // From API level 28, Treble may prevent the system from writing sys.display-size, so we check
      // vendor.display-size instead.
      @Nullable
      String displaySize =
          SDK_INT < 28
              ? getSystemProperty("sys.display-size")
              : getSystemProperty("vendor.display-size");
      // If we managed to read the display size, attempt to parse it.
      if (!TextUtils.isEmpty(displaySize)) {
        try {
          String[] displaySizeParts = split(displaySize.trim(), "x");
          if (displaySizeParts.length == 2) {
            int width = Integer.parseInt(displaySizeParts[0]);
            int height = Integer.parseInt(displaySizeParts[1]);
            if (width > 0 && height > 0) {
              return new Point(width, height);
            }
          }
        } catch (NumberFormatException e) {
          // Do nothing.
        }
        Log.e(TAG, "Invalid display size: " + displaySize);
      }

      // Sony Android TVs advertise support for 4k output via a system feature.
      if ("Sony".equals(MANUFACTURER)
          && MODEL.startsWith("BRAVIA")
          && context.getPackageManager().hasSystemFeature("com.sony.dtv.hardware.panel.qfhd")) {
        return new Point(3840, 2160);
      }
    }

    Point displaySize = new Point();
    if (SDK_INT >= 23) {
      getDisplaySizeV23(display, displaySize);
    } else if (SDK_INT >= 17) {
      getDisplaySizeV17(display, displaySize);
    } else {
      getDisplaySizeV16(display, displaySize);
    }
    return displaySize;
  }

  /**
   * Returns a string representation of a {@link C.TrackType}.
   *
   * @param trackType A {@link C.TrackType} constant,
   * @return A string representation of this constant.
   */
  public static String getTrackTypeString(@C.TrackType int trackType) {
    switch (trackType) {
      case C.TRACK_TYPE_DEFAULT:
        return "default";
      case C.TRACK_TYPE_AUDIO:
        return "audio";
      case C.TRACK_TYPE_VIDEO:
        return "video";
      case C.TRACK_TYPE_TEXT:
        return "text";
      case C.TRACK_TYPE_IMAGE:
        return "image";
      case C.TRACK_TYPE_METADATA:
        return "metadata";
      case C.TRACK_TYPE_CAMERA_MOTION:
        return "camera motion";
      case C.TRACK_TYPE_NONE:
        return "none";
      case C.TRACK_TYPE_UNKNOWN:
        return "unknown";
      default:
        return trackType >= C.TRACK_TYPE_CUSTOM_BASE ? "custom (" + trackType + ")" : "?";
    }
  }

  /**
   * Returns the current time in milliseconds since the epoch.
   *
   * @param elapsedRealtimeEpochOffsetMs The offset between {@link SystemClock#elapsedRealtime()}
   *     and the time since the Unix epoch, or {@link C#TIME_UNSET} if unknown.
   * @return The Unix time in milliseconds since the epoch.
   */
  public static long getNowUnixTimeMs(long elapsedRealtimeEpochOffsetMs) {
    return elapsedRealtimeEpochOffsetMs == C.TIME_UNSET
        ? System.currentTimeMillis()
        : SystemClock.elapsedRealtime() + elapsedRealtimeEpochOffsetMs;
  }

  /**
   * Moves the elements starting at {@code fromIndex} to {@code newFromIndex}.
   *
   * @param items The list of which to move elements.
   * @param fromIndex The index at which the items to move start.
   * @param toIndex The index up to which elements should be moved (exclusive).
   * @param newFromIndex The new from index.
   */
  @SuppressWarnings("ExtendsObject") // See go/lsc-extends-object
  public static <T extends Object> void moveItems(
      List<T> items, int fromIndex, int toIndex, int newFromIndex) {
    ArrayDeque<T> removedItems = new ArrayDeque<>();
    int removedItemsLength = toIndex - fromIndex;
    for (int i = removedItemsLength - 1; i >= 0; i--) {
      removedItems.addFirst(items.remove(fromIndex + i));
    }
    items.addAll(min(newFromIndex, items.size()), removedItems);
  }

  /** Returns whether the table exists in the database. */
  public static boolean tableExists(SQLiteDatabase database, String tableName) {
    long count =
        DatabaseUtils.queryNumEntries(
            database, "sqlite_master", "tbl_name = ?", new String[] {tableName});
    return count > 0;
  }

  /**
   * Attempts to parse an error code from a diagnostic string found in framework media exceptions.
   *
   * <p>For example: android.media.MediaCodec.error_1 or android.media.MediaDrm.error_neg_2.
   *
   * @param diagnosticsInfo A string from which to parse the error code.
   * @return The parser error code, or 0 if an error code could not be parsed.
   */
  public static int getErrorCodeFromPlatformDiagnosticsInfo(@Nullable String diagnosticsInfo) {
    // TODO (internal b/192337376): Change 0 for ERROR_UNKNOWN once available.
    if (diagnosticsInfo == null) {
      return 0;
    }
    String[] strings = split(diagnosticsInfo, "_");
    int length = strings.length;
    if (length < 2) {
      return 0;
    }
    String digitsSection = strings[length - 1];
    boolean isNegative = length >= 3 && "neg".equals(strings[length - 2]);
    try {
      int errorCode = Integer.parseInt(Assertions.checkNotNull(digitsSection));
      return isNegative ? -errorCode : errorCode;
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /**
   * Returns string representation of a {@link C.FormatSupport} flag.
   *
   * @param formatSupport A {@link C.FormatSupport} flag.
   * @return A string representation of the flag.
   */
  public static String getFormatSupportString(@C.FormatSupport int formatSupport) {
    switch (formatSupport) {
      case C.FORMAT_HANDLED:
        return "YES";
      case C.FORMAT_EXCEEDS_CAPABILITIES:
        return "NO_EXCEEDS_CAPABILITIES";
      case C.FORMAT_UNSUPPORTED_DRM:
        return "NO_UNSUPPORTED_DRM";
      case C.FORMAT_UNSUPPORTED_SUBTYPE:
        return "NO_UNSUPPORTED_TYPE";
      case C.FORMAT_UNSUPPORTED_TYPE:
        return "NO";
      default:
        throw new IllegalStateException();
    }
  }

  /**
   * Returns the {@link Commands} available in the {@link Player}.
   *
   * @param player The {@link Player}.
   * @param permanentAvailableCommands The commands permanently available in the player.
   * @return The available {@link Commands}.
   */
  public static Commands getAvailableCommands(Player player, Commands permanentAvailableCommands) {
    boolean isPlayingAd = player.isPlayingAd();
    boolean isCurrentMediaItemSeekable = player.isCurrentMediaItemSeekable();
    boolean hasPreviousMediaItem = player.hasPreviousMediaItem();
    boolean hasNextMediaItem = player.hasNextMediaItem();
    boolean isCurrentMediaItemLive = player.isCurrentMediaItemLive();
    boolean isCurrentMediaItemDynamic = player.isCurrentMediaItemDynamic();
    boolean isTimelineEmpty = player.getCurrentTimeline().isEmpty();
    return new Commands.Builder()
        .addAll(permanentAvailableCommands)
        .addIf(COMMAND_SEEK_TO_DEFAULT_POSITION, !isPlayingAd)
        .addIf(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, isCurrentMediaItemSeekable && !isPlayingAd)
        .addIf(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, hasPreviousMediaItem && !isPlayingAd)
        .addIf(
            COMMAND_SEEK_TO_PREVIOUS,
            !isTimelineEmpty
                && (hasPreviousMediaItem || !isCurrentMediaItemLive || isCurrentMediaItemSeekable)
                && !isPlayingAd)
        .addIf(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, hasNextMediaItem && !isPlayingAd)
        .addIf(
            COMMAND_SEEK_TO_NEXT,
            !isTimelineEmpty
                && (hasNextMediaItem || (isCurrentMediaItemLive && isCurrentMediaItemDynamic))
                && !isPlayingAd)
        .addIf(COMMAND_SEEK_TO_MEDIA_ITEM, !isPlayingAd)
        .addIf(COMMAND_SEEK_BACK, isCurrentMediaItemSeekable && !isPlayingAd)
        .addIf(COMMAND_SEEK_FORWARD, isCurrentMediaItemSeekable && !isPlayingAd)
        .build();
  }

  /**
   * Returns the sum of all summands of the given array.
   *
   * @param summands The summands to calculate the sum from.
   * @return The sum of all summands.
   */
  public static long sum(long... summands) {
    long sum = 0;
    for (long summand : summands) {
      sum += summand;
    }
    return sum;
  }

  /**
   * Returns a {@link Drawable} for the given resource or throws a {@link
   * Resources.NotFoundException} if not found.
   *
   * @param context The context to get the theme from starting with API 21.
   * @param resources The resources to load the drawable from.
   * @param drawableRes The drawable resource int.
   * @return The loaded {@link Drawable}.
   */
  public static Drawable getDrawable(
      Context context, Resources resources, @DrawableRes int drawableRes) {
    return SDK_INT >= 21
        ? Api21.getDrawable(context, resources, drawableRes)
        : resources.getDrawable(drawableRes);
  }

  /**
   * Returns a string representation of the integer using radix value {@link Character#MAX_RADIX}.
   *
   * @param i An integer to be converted to String.
   */
  public static String intToStringMaxRadix(int i) {
    return Integer.toString(i, Character.MAX_RADIX);
  }

  @Nullable
  private static String getSystemProperty(String name) {
    try {
      @SuppressLint("PrivateApi")
      Class<?> systemProperties = Class.forName("android.os.SystemProperties");
      Method getMethod = systemProperties.getMethod("get", String.class);
      return (String) getMethod.invoke(systemProperties, name);
    } catch (Exception e) {
      Log.e(TAG, "Failed to read system property " + name, e);
      return null;
    }
  }

  @RequiresApi(23)
  private static void getDisplaySizeV23(Display display, Point outSize) {
    Display.Mode mode = display.getMode();
    outSize.x = mode.getPhysicalWidth();
    outSize.y = mode.getPhysicalHeight();
  }

  @RequiresApi(17)
  private static void getDisplaySizeV17(Display display, Point outSize) {
    display.getRealSize(outSize);
  }

  private static void getDisplaySizeV16(Display display, Point outSize) {
    display.getSize(outSize);
  }

  private static String[] getSystemLocales() {
    Configuration config = Resources.getSystem().getConfiguration();
    return SDK_INT >= 24
        ? getSystemLocalesV24(config)
        : new String[] {getLocaleLanguageTag(config.locale)};
  }

  @RequiresApi(24)
  private static String[] getSystemLocalesV24(Configuration config) {
    return split(config.getLocales().toLanguageTags(), ",");
  }

  @RequiresApi(21)
  private static String getLocaleLanguageTagV21(Locale locale) {
    return locale.toLanguageTag();
  }

  private static HashMap<String, String> createIsoLanguageReplacementMap() {
    String[] iso2Languages = Locale.getISOLanguages();
    HashMap<String, String> replacedLanguages =
        new HashMap<>(
            /* initialCapacity= */ iso2Languages.length + additionalIsoLanguageReplacements.length);
    for (String iso2 : iso2Languages) {
      try {
        // This returns the ISO 639-2/T code for the language.
        String iso3 = new Locale(iso2).getISO3Language();
        if (!TextUtils.isEmpty(iso3)) {
          replacedLanguages.put(iso3, iso2);
        }
      } catch (MissingResourceException e) {
        // Shouldn't happen for list of known languages, but we don't want to throw either.
      }
    }
    // Add additional replacement mappings.
    for (int i = 0; i < additionalIsoLanguageReplacements.length; i += 2) {
      replacedLanguages.put(
          additionalIsoLanguageReplacements[i], additionalIsoLanguageReplacements[i + 1]);
    }
    return replacedLanguages;
  }

  @RequiresApi(api = Build.VERSION_CODES.M)
  private static boolean requestExternalStoragePermission(Activity activity) {
    if (Build.VERSION.SDK_INT >= 33) {
      ArrayList<String> permissions = new ArrayList<>();
      if (activity.checkSelfPermission(permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
        permissions.add(permission.READ_MEDIA_VIDEO);
      }
      if (activity.checkSelfPermission(permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
        permissions.add(permission.READ_MEDIA_IMAGES);
      }
      if (activity.checkSelfPermission(permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
        permissions.add(permission.READ_MEDIA_AUDIO);
      }
      if (!permissions.isEmpty()) {
        activity.requestPermissions(permissions.toArray(new String[0]), /* requestCode= */ 0);
        return true;
      }
      return false;
    } else {
      if (activity.checkSelfPermission(permission.READ_EXTERNAL_STORAGE)
          != PackageManager.PERMISSION_GRANTED) {
        activity.requestPermissions(
          new String[]{permission.READ_EXTERNAL_STORAGE}, /* requestCode= */ 0);
        return true;
      }
      return false;
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.N)
  private static boolean isTrafficRestricted(Uri uri) {
    return "http".equals(uri.getScheme())
        && !NetworkSecurityPolicy.getInstance()
            .isCleartextTrafficPermitted(checkNotNull(uri.getHost()));
  }

  private static String maybeReplaceLegacyLanguageTags(String languageTag) {
    for (int i = 0; i < isoLegacyTagReplacements.length; i += 2) {
      if (languageTag.startsWith(isoLegacyTagReplacements[i])) {
        return isoLegacyTagReplacements[i + 1]
            + languageTag.substring(/* beginIndex= */ isoLegacyTagReplacements[i].length());
      }
    }
    return languageTag;
  }

  // Additional mapping from ISO3 to ISO2 language codes.
  private static final String[] additionalIsoLanguageReplacements =
      new String[] {
        // Bibliographical codes defined in ISO 639-2/B, replaced by terminological code defined in
        // ISO 639-2/T. See https://en.wikipedia.org/wiki/List_of_ISO_639-2_codes.
        "alb", "sq",
        "arm", "hy",
        "baq", "eu",
        "bur", "my",
        "tib", "bo",
        "chi", "zh",
        "cze", "cs",
        "dut", "nl",
        "ger", "de",
        "gre", "el",
        "fre", "fr",
        "geo", "ka",
        "ice", "is",
        "mac", "mk",
        "mao", "mi",
        "may", "ms",
        "per", "fa",
        "rum", "ro",
        "scc", "hbs-srp",
        "slo", "sk",
        "wel", "cy",
        // Deprecated 2-letter codes, replaced by modern equivalent (including macrolanguage)
        // See https://en.wikipedia.org/wiki/List_of_ISO_639-1_codes, "ISO 639:1988"
        "id", "ms-ind",
        "iw", "he",
        "heb", "he",
        "ji", "yi",
        // Individual macrolanguage codes mapped back to full macrolanguage code.
        // See https://en.wikipedia.org/wiki/ISO_639_macrolanguage
        "arb", "ar-arb",
        "in", "ms-ind",
        "ind", "ms-ind",
        "nb", "no-nob",
        "nob", "no-nob",
        "nn", "no-nno",
        "nno", "no-nno",
        "tw", "ak-twi",
        "twi", "ak-twi",
        "bs", "hbs-bos",
        "bos", "hbs-bos",
        "hr", "hbs-hrv",
        "hrv", "hbs-hrv",
        "sr", "hbs-srp",
        "srp", "hbs-srp",
        "cmn", "zh-cmn",
        "hak", "zh-hak",
        "nan", "zh-nan",
        "hsn", "zh-hsn"
      };

  // Legacy tags that have been replaced by modern equivalents (including macrolanguage)
  // See https://www.iana.org/assignments/language-subtag-registry/language-subtag-registry.
  private static final String[] isoLegacyTagReplacements =
      new String[] {
        "i-lux", "lb",
        "i-hak", "zh-hak",
        "i-navajo", "nv",
        "no-bok", "no-nob",
        "no-nyn", "no-nno",
        "zh-guoyu", "zh-cmn",
        "zh-hakka", "zh-hak",
        "zh-min-nan", "zh-nan",
        "zh-xiang", "zh-hsn"
      };

  /**
   * Allows the CRC-32 calculation to be done byte by byte instead of bit per bit in the order "most
   * significant bit first".
   */
  private static final int[] CRC32_BYTES_MSBF = {
    0X00000000, 0X04C11DB7, 0X09823B6E, 0X0D4326D9, 0X130476DC, 0X17C56B6B, 0X1A864DB2,
    0X1E475005, 0X2608EDB8, 0X22C9F00F, 0X2F8AD6D6, 0X2B4BCB61, 0X350C9B64, 0X31CD86D3,
    0X3C8EA00A, 0X384FBDBD, 0X4C11DB70, 0X48D0C6C7, 0X4593E01E, 0X4152FDA9, 0X5F15ADAC,
    0X5BD4B01B, 0X569796C2, 0X52568B75, 0X6A1936C8, 0X6ED82B7F, 0X639B0DA6, 0X675A1011,
    0X791D4014, 0X7DDC5DA3, 0X709F7B7A, 0X745E66CD, 0X9823B6E0, 0X9CE2AB57, 0X91A18D8E,
    0X95609039, 0X8B27C03C, 0X8FE6DD8B, 0X82A5FB52, 0X8664E6E5, 0XBE2B5B58, 0XBAEA46EF,
    0XB7A96036, 0XB3687D81, 0XAD2F2D84, 0XA9EE3033, 0XA4AD16EA, 0XA06C0B5D, 0XD4326D90,
    0XD0F37027, 0XDDB056FE, 0XD9714B49, 0XC7361B4C, 0XC3F706FB, 0XCEB42022, 0XCA753D95,
    0XF23A8028, 0XF6FB9D9F, 0XFBB8BB46, 0XFF79A6F1, 0XE13EF6F4, 0XE5FFEB43, 0XE8BCCD9A,
    0XEC7DD02D, 0X34867077, 0X30476DC0, 0X3D044B19, 0X39C556AE, 0X278206AB, 0X23431B1C,
    0X2E003DC5, 0X2AC12072, 0X128E9DCF, 0X164F8078, 0X1B0CA6A1, 0X1FCDBB16, 0X018AEB13,
    0X054BF6A4, 0X0808D07D, 0X0CC9CDCA, 0X7897AB07, 0X7C56B6B0, 0X71159069, 0X75D48DDE,
    0X6B93DDDB, 0X6F52C06C, 0X6211E6B5, 0X66D0FB02, 0X5E9F46BF, 0X5A5E5B08, 0X571D7DD1,
    0X53DC6066, 0X4D9B3063, 0X495A2DD4, 0X44190B0D, 0X40D816BA, 0XACA5C697, 0XA864DB20,
    0XA527FDF9, 0XA1E6E04E, 0XBFA1B04B, 0XBB60ADFC, 0XB6238B25, 0XB2E29692, 0X8AAD2B2F,
    0X8E6C3698, 0X832F1041, 0X87EE0DF6, 0X99A95DF3, 0X9D684044, 0X902B669D, 0X94EA7B2A,
    0XE0B41DE7, 0XE4750050, 0XE9362689, 0XEDF73B3E, 0XF3B06B3B, 0XF771768C, 0XFA325055,
    0XFEF34DE2, 0XC6BCF05F, 0XC27DEDE8, 0XCF3ECB31, 0XCBFFD686, 0XD5B88683, 0XD1799B34,
    0XDC3ABDED, 0XD8FBA05A, 0X690CE0EE, 0X6DCDFD59, 0X608EDB80, 0X644FC637, 0X7A089632,
    0X7EC98B85, 0X738AAD5C, 0X774BB0EB, 0X4F040D56, 0X4BC510E1, 0X46863638, 0X42472B8F,
    0X5C007B8A, 0X58C1663D, 0X558240E4, 0X51435D53, 0X251D3B9E, 0X21DC2629, 0X2C9F00F0,
    0X285E1D47, 0X36194D42, 0X32D850F5, 0X3F9B762C, 0X3B5A6B9B, 0X0315D626, 0X07D4CB91,
    0X0A97ED48, 0X0E56F0FF, 0X1011A0FA, 0X14D0BD4D, 0X19939B94, 0X1D528623, 0XF12F560E,
    0XF5EE4BB9, 0XF8AD6D60, 0XFC6C70D7, 0XE22B20D2, 0XE6EA3D65, 0XEBA91BBC, 0XEF68060B,
    0XD727BBB6, 0XD3E6A601, 0XDEA580D8, 0XDA649D6F, 0XC423CD6A, 0XC0E2D0DD, 0XCDA1F604,
    0XC960EBB3, 0XBD3E8D7E, 0XB9FF90C9, 0XB4BCB610, 0XB07DABA7, 0XAE3AFBA2, 0XAAFBE615,
    0XA7B8C0CC, 0XA379DD7B, 0X9B3660C6, 0X9FF77D71, 0X92B45BA8, 0X9675461F, 0X8832161A,
    0X8CF30BAD, 0X81B02D74, 0X857130C3, 0X5D8A9099, 0X594B8D2E, 0X5408ABF7, 0X50C9B640,
    0X4E8EE645, 0X4A4FFBF2, 0X470CDD2B, 0X43CDC09C, 0X7B827D21, 0X7F436096, 0X7200464F,
    0X76C15BF8, 0X68860BFD, 0X6C47164A, 0X61043093, 0X65C52D24, 0X119B4BE9, 0X155A565E,
    0X18197087, 0X1CD86D30, 0X029F3D35, 0X065E2082, 0X0B1D065B, 0X0FDC1BEC, 0X3793A651,
    0X3352BBE6, 0X3E119D3F, 0X3AD08088, 0X2497D08D, 0X2056CD3A, 0X2D15EBE3, 0X29D4F654,
    0XC5A92679, 0XC1683BCE, 0XCC2B1D17, 0XC8EA00A0, 0XD6AD50A5, 0XD26C4D12, 0XDF2F6BCB,
    0XDBEE767C, 0XE3A1CBC1, 0XE760D676, 0XEA23F0AF, 0XEEE2ED18, 0XF0A5BD1D, 0XF464A0AA,
    0XF9278673, 0XFDE69BC4, 0X89B8FD09, 0X8D79E0BE, 0X803AC667, 0X84FBDBD0, 0X9ABC8BD5,
    0X9E7D9662, 0X933EB0BB, 0X97FFAD0C, 0XAFB010B1, 0XAB710D06, 0XA6322BDF, 0XA2F33668,
    0XBCB4666D, 0XB8757BDA, 0XB5365D03, 0XB1F740B4
  };

  /**
   * Allows the CRC-8 calculation to be done byte by byte instead of bit per bit in the order "most
   * significant bit first".
   */
  private static final int[] CRC8_BYTES_MSBF = {
    0x00, 0x07, 0x0E, 0x09, 0x1C, 0x1B, 0x12, 0x15, 0x38, 0x3F, 0x36, 0x31, 0x24, 0x23, 0x2A,
    0x2D, 0x70, 0x77, 0x7E, 0x79, 0x6C, 0x6B, 0x62, 0x65, 0x48, 0x4F, 0x46, 0x41, 0x54, 0x53,
    0x5A, 0x5D, 0xE0, 0xE7, 0xEE, 0xE9, 0xFC, 0xFB, 0xF2, 0xF5, 0xD8, 0xDF, 0xD6, 0xD1, 0xC4,
    0xC3, 0xCA, 0xCD, 0x90, 0x97, 0x9E, 0x99, 0x8C, 0x8B, 0x82, 0x85, 0xA8, 0xAF, 0xA6, 0xA1,
    0xB4, 0xB3, 0xBA, 0xBD, 0xC7, 0xC0, 0xC9, 0xCE, 0xDB, 0xDC, 0xD5, 0xD2, 0xFF, 0xF8, 0xF1,
    0xF6, 0xE3, 0xE4, 0xED, 0xEA, 0xB7, 0xB0, 0xB9, 0xBE, 0xAB, 0xAC, 0xA5, 0xA2, 0x8F, 0x88,
    0x81, 0x86, 0x93, 0x94, 0x9D, 0x9A, 0x27, 0x20, 0x29, 0x2E, 0x3B, 0x3C, 0x35, 0x32, 0x1F,
    0x18, 0x11, 0x16, 0x03, 0x04, 0x0D, 0x0A, 0x57, 0x50, 0x59, 0x5E, 0x4B, 0x4C, 0x45, 0x42,
    0x6F, 0x68, 0x61, 0x66, 0x73, 0x74, 0x7D, 0x7A, 0x89, 0x8E, 0x87, 0x80, 0x95, 0x92, 0x9B,
    0x9C, 0xB1, 0xB6, 0xBF, 0xB8, 0xAD, 0xAA, 0xA3, 0xA4, 0xF9, 0xFE, 0xF7, 0xF0, 0xE5, 0xE2,
    0xEB, 0xEC, 0xC1, 0xC6, 0xCF, 0xC8, 0xDD, 0xDA, 0xD3, 0xD4, 0x69, 0x6E, 0x67, 0x60, 0x75,
    0x72, 0x7B, 0x7C, 0x51, 0x56, 0x5F, 0x58, 0x4D, 0x4A, 0x43, 0x44, 0x19, 0x1E, 0x17, 0x10,
    0x05, 0x02, 0x0B, 0x0C, 0x21, 0x26, 0x2F, 0x28, 0x3D, 0x3A, 0x33, 0x34, 0x4E, 0x49, 0x40,
    0x47, 0x52, 0x55, 0x5C, 0x5B, 0x76, 0x71, 0x78, 0x7F, 0x6A, 0x6D, 0x64, 0x63, 0x3E, 0x39,
    0x30, 0x37, 0x22, 0x25, 0x2C, 0x2B, 0x06, 0x01, 0x08, 0x0F, 0x1A, 0x1D, 0x14, 0x13, 0xAE,
    0xA9, 0xA0, 0xA7, 0xB2, 0xB5, 0xBC, 0xBB, 0x96, 0x91, 0x98, 0x9F, 0x8A, 0x8D, 0x84, 0x83,
    0xDE, 0xD9, 0xD0, 0xD7, 0xC2, 0xC5, 0xCC, 0xCB, 0xE6, 0xE1, 0xE8, 0xEF, 0xFA, 0xFD, 0xF4,
    0xF3
  };

  @RequiresApi(21)
  private static final class Api21 {
    @DoNotInline
    public static Drawable getDrawable(Context context, Resources resources, @DrawableRes int res) {
      return resources.getDrawable(res, context.getTheme());
    }
  }
}
