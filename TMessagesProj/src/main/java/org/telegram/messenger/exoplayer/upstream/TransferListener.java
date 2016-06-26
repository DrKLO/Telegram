/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.telegram.messenger.exoplayer.upstream;

/**
 * Interface definition for a callback to be notified of data transfer events.
 */
public interface TransferListener {

  /**
   * Invoked when a transfer starts.
   */
  void onTransferStart();

  /**
   * Called incrementally during a transfer.
   *
   * @param bytesTransferred The number of bytes transferred since the previous call to this
   *     method (or if the first call, since the transfer was started).
   */
  void onBytesTransferred(int bytesTransferred);

  /**
   * Invoked when a transfer ends.
   */
  void onTransferEnd();

}
