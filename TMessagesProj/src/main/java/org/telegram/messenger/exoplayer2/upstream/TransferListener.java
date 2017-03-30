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
package org.telegram.messenger.exoplayer2.upstream;

/**
 * A listener of data transfer events.
 */
public interface TransferListener<S> {

  /**
   * Called when a transfer starts.
   *
   * @param source The source performing the transfer.
   * @param dataSpec Describes the data being transferred.
   */
  void onTransferStart(S source, DataSpec dataSpec);

  /**
   * Called incrementally during a transfer.
   *
   * @param source The source performing the transfer.
   * @param bytesTransferred The number of bytes transferred since the previous call to this
   *     method (or if the first call, since the transfer was started).
   */
  void onBytesTransferred(S source, int bytesTransferred);

  /**
   * Called when a transfer ends.
   *
   * @param source The source performing the transfer.
   */
  void onTransferEnd(S source);

}
