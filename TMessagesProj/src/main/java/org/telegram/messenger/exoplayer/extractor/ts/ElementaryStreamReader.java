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
package org.telegram.messenger.exoplayer.extractor.ts;

import org.telegram.messenger.exoplayer.extractor.TrackOutput;
import org.telegram.messenger.exoplayer.util.ParsableByteArray;

/**
 * Extracts individual samples from an elementary media stream, preserving original order.
 */
/* package */ abstract class ElementaryStreamReader {

  protected final TrackOutput output;

  /**
   * @param output A {@link TrackOutput} to which samples should be written.
   */
  protected ElementaryStreamReader(TrackOutput output) {
    this.output = output;
  }

  /**
   * Notifies the reader that a seek has occurred.
   */
  public abstract void seek();

  /**
   * Invoked when a packet starts.
   *
   * @param pesTimeUs The timestamp associated with the packet.
   * @param dataAlignmentIndicator The data alignment indicator associated with the packet.
   */
  public abstract void packetStarted(long pesTimeUs, boolean dataAlignmentIndicator);

  /**
   * Consumes (possibly partial) data from the current packet.
   *
   * @param data The data to consume.
   */
  public abstract void consume(ParsableByteArray data);

  /**
   * Invoked when a packet ends.
   */
  public abstract void packetFinished();

}
