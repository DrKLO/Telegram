/*
 * Copyright 2021 The Android Open Source Project
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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.util.List;

/**
 * A {@link Binder} to transfer a list of {@link Bundle Bundles} across processes by splitting the
 * list into multiple transactions.
 *
 * <p>Note: Using this class causes synchronous binder calls in the opposite direction regardless of
 * the "oneway" property.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Sender
 * List<Bundle> list = ...;
 * IBinder binder = new BundleListRetriever(list);
 * Bundle bundle = new Bundle();
 * bundle.putBinder("list", binder);
 *
 * // Receiver
 * Bundle bundle = ...; // Received from the sender
 * IBinder binder = bundle.getBinder("list");
 * List<Bundle> list = BundleListRetriever.getList(binder);
 * }</pre>
 */
public final class BundleListRetriever extends Binder {

  // Soft limit of an IPC buffer size
  private static final int SUGGESTED_MAX_IPC_SIZE =
      Util.SDK_INT >= 30 ? IBinder.getSuggestedMaxIpcSizeBytes() : 64 * 1024;

  private static final int REPLY_END_OF_LIST = 0;
  private static final int REPLY_CONTINUE = 1;
  private static final int REPLY_BREAK = 2;

  private final ImmutableList<Bundle> list;

  /** Creates a {@link Binder} to send a list of {@link Bundle Bundles} to another process. */
  public BundleListRetriever(List<Bundle> list) {
    this.list = ImmutableList.copyOf(list);
  }

  @Override
  protected boolean onTransact(int code, Parcel data, @Nullable Parcel reply, int flags)
      throws RemoteException {
    if (code != FIRST_CALL_TRANSACTION) {
      return super.onTransact(code, data, reply, flags);
    }

    if (reply == null) {
      return false;
    }

    int count = list.size();
    int index = data.readInt();
    while (index < count && reply.dataSize() < SUGGESTED_MAX_IPC_SIZE) {
      reply.writeInt(REPLY_CONTINUE);
      reply.writeBundle(list.get(index));
      index++;
    }
    reply.writeInt(index < count ? REPLY_BREAK : REPLY_END_OF_LIST);
    return true;
  }

  /**
   * Gets a list of {@link Bundle Bundles} from a {@link BundleListRetriever}.
   *
   * @param binder A binder interface backed by {@link BundleListRetriever}.
   * @return The list of {@link Bundle Bundles}.
   */
  public static ImmutableList<Bundle> getList(IBinder binder) {
    ImmutableList.Builder<Bundle> builder = ImmutableList.builder();

    int index = 0;
    int replyCode = REPLY_CONTINUE;

    while (replyCode != REPLY_END_OF_LIST) {
      Parcel data = Parcel.obtain();
      Parcel reply = Parcel.obtain();
      try {
        data.writeInt(index);
        try {
          binder.transact(FIRST_CALL_TRANSACTION, data, reply, /* flags= */ 0);
        } catch (RemoteException e) {
          throw new RuntimeException(e);
        }
        while ((replyCode = reply.readInt()) == REPLY_CONTINUE) {
          builder.add(checkNotNull(reply.readBundle()));
          index++;
        }
      } finally {
        reply.recycle();
        data.recycle();
      }
    }

    return builder.build();
  }
}
