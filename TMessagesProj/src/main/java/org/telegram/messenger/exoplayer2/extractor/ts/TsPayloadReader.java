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
package org.telegram.messenger.exoplayer2.extractor.ts;

import android.util.SparseArray;
import org.telegram.messenger.exoplayer2.extractor.ExtractorOutput;
import org.telegram.messenger.exoplayer2.extractor.TimestampAdjuster;
import org.telegram.messenger.exoplayer2.extractor.TrackOutput;
import org.telegram.messenger.exoplayer2.util.ParsableByteArray;

/**
 * Parses TS packet payload data.
 */
public interface TsPayloadReader {

  /**
   * Factory of {@link TsPayloadReader} instances.
   */
  interface Factory {

    /**
     * Returns the initial mapping from PIDs to payload readers.
     * <p>
     * This method allows the injection of payload readers for reserved PIDs, excluding PID 0.
     *
     * @return A {@link SparseArray} that maps PIDs to payload readers.
     */
    SparseArray<TsPayloadReader> createInitialPayloadReaders();

    /**
     * Returns a {@link TsPayloadReader} for a given stream type and elementary stream information.
     * May return null if the stream type is not supported.
     *
     * @param streamType Stream type value as defined in the PMT entry or associated descriptors.
     * @param esInfo Information associated to the elementary stream provided in the PMT.
     * @return A {@link TsPayloadReader} for the packet stream carried by the provided pid.
     *     {@code null} if the stream is not supported.
     */
    TsPayloadReader createPayloadReader(int streamType, EsInfo esInfo);

  }

  /**
   * Holds information associated with a PMT entry.
   */
  final class EsInfo {

    public final int streamType;
    public final String language;
    public final byte[] descriptorBytes;

    /**
     * @param streamType The type of the stream as defined by the
     *     {@link TsExtractor}{@code .TS_STREAM_TYPE_*}.
     * @param language The language of the stream, as defined by ISO/IEC 13818-1, section 2.6.18.
     * @param descriptorBytes The descriptor bytes associated to the stream.
     */
    public EsInfo(int streamType, String language, byte[] descriptorBytes) {
      this.streamType = streamType;
      this.language = language;
      this.descriptorBytes = descriptorBytes;
    }

  }

  /**
   * Generates track ids for initializing {@link TsPayloadReader}s' {@link TrackOutput}s.
   */
  final class TrackIdGenerator {

    private final int firstId;
    private final int idIncrement;
    private int generatedIdCount;

    public TrackIdGenerator(int firstId, int idIncrement) {
      this.firstId = firstId;
      this.idIncrement = idIncrement;
    }

    public int getNextId() {
      return firstId + idIncrement * generatedIdCount++;
    }

  }

  /**
   * Initializes the payload reader.
   *
   * @param timestampAdjuster A timestamp adjuster for offsetting and scaling sample timestamps.
   * @param extractorOutput The {@link ExtractorOutput} that receives the extracted data.
   * @param idGenerator A {@link PesReader.TrackIdGenerator} that generates unique track ids for the
   *     {@link TrackOutput}s.
   */
  void init(TimestampAdjuster timestampAdjuster, ExtractorOutput extractorOutput,
      TrackIdGenerator idGenerator);

  /**
   * Notifies the reader that a seek has occurred.
   * <p>
   * Following a call to this method, the data passed to the next invocation of
   * {@link #consume(ParsableByteArray, boolean)} will not be a continuation of the data that was
   * previously passed. Hence the reader should reset any internal state.
   */
  void seek();

  /**
   * Consumes the payload of a TS packet.
   *
   * @param data The TS packet. The position will be set to the start of the payload.
   * @param payloadUnitStartIndicator Whether payloadUnitStartIndicator was set on the TS packet.
   */
  void consume(ParsableByteArray data, boolean payloadUnitStartIndicator);

}
